/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appfunctions.compiler.processors

import androidx.annotation.VisibleForTesting
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.compiler.AppFunctionCompiler
import androidx.appfunctions.compiler.core.AnnotatedAppFunctionSerializable
import androidx.appfunctions.compiler.core.AnnotatedAppFunctions.Companion.SUPPORTED_ARRAY_PRIMITIVE_TYPES
import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionSerializableAnnotation
import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionSerializableFactoryClass
import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionSerializableFactoryClass.FromAppFunctionDataMethod.APP_FUNCTION_DATA_PARAM_NAME
import androidx.appfunctions.compiler.core.ProcessingException
import androidx.appfunctions.compiler.core.ensureQualifiedTypeName
import androidx.appfunctions.compiler.core.isOfType
import androidx.appfunctions.compiler.core.logException
import androidx.appfunctions.compiler.core.resolveListParameterizedType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock

/**
 * Generates a factory class with methods to convert classes annotated with
 * [androidx.appfunctions.AppFunctionSerializable] to [androidx.appfunctions.AppFunctionData], and
 * vice-versa.
 *
 * **Example:**
 *
 * ```
 * @AppFunctionSerializable
 * class Location(val latitude: Double, val longitude: Double)
 * ```
 *
 * A corresponding `LocationFactory` class will be generated:
 * ```
 * @Generated("androidx.appfunctions.compiler.AppFunctionCompiler")
 * public class LocationFactory : AppFunctionSerializableFactory<Location> {
 *   override fun fromAppFunctionData(appFunctionData: AppFunctionData): Location {
 *     val latitude = appFunctionData.getDouble("latitude")
 *     val longitude = appFunctionData.getDouble("longitude")
 *
 *     return Location(latitude, longitude)
 *   }
 *
 *   override fun toAppFunctionData(location: Location): AppFunctionData {
 *     val builder = AppFunctionData.Builder("")
 *
 *     builder.setDouble("latitude", location.latitude)
 *     builder.setDouble("longitude", location.longitude)
 *
 *     return builder.build()
 *   }
 * }
 * ```
 */
class AppFunctionSerializableProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        try {
            val entityClasses = resolveAppFunctionSerializables(resolver)
            for (entity in entityClasses) {
                buildAppFunctionSerializableFactoryClass(entity)
            }
        } catch (e: ProcessingException) {
            logger.logException(e)
        }
        return emptyList()
    }

    fun resolveAppFunctionSerializables(
        resolver: Resolver
    ): List<AnnotatedAppFunctionSerializable> {
        val annotatedAppFunctionSerializables =
            resolver.getSymbolsWithAnnotation(
                AppFunctionSerializableAnnotation.CLASS_NAME.canonicalName
            )
        return annotatedAppFunctionSerializables
            .map {
                if (it !is KSClassDeclaration) {
                    throw ProcessingException(
                        "Only classes can be annotated with @AppFunctionSerializable",
                        it
                    )
                }
                AnnotatedAppFunctionSerializable(it).validate()
            }
            .toList()
    }

    // TODO(b/392587953): handle AppFunctionSerializable types
    // TODO(b/392587953): handle nullable types
    private fun buildAppFunctionSerializableFactoryClass(
        annotatedClass: AnnotatedAppFunctionSerializable
    ) {
        val superInterfaceClass =
            AppFunctionSerializableFactoryClass.CLASS_NAME.parameterizedBy(
                listOf(annotatedClass.originalClassName)
            )

        val generatedFactoryClassName = "${annotatedClass.originalClassName.simpleName}Factory"
        val fileSpec =
            FileSpec.builder(annotatedClass.originalClassName.simpleName, generatedFactoryClassName)
                .addType(
                    TypeSpec.classBuilder(generatedFactoryClassName)
                        .addAnnotation(AppFunctionCompiler.GENERATED_ANNOTATION)
                        .addSuperinterface(superInterfaceClass)
                        .addFunction(buildFromAppFunctionDataFunction(annotatedClass))
                        .addFunction(buildToAppFunctionDataFunction(annotatedClass))
                        .build()
                )
                .build()
        codeGenerator
            .createNewFile(
                Dependencies(
                    aggregating = false,
                    checkNotNull(annotatedClass.appFunctionSerializableClass.containingFile)
                ),
                annotatedClass.originalClassName.packageName,
                generatedFactoryClassName
            )
            .bufferedWriter()
            .use { fileSpec.writeTo(it) }
    }

    private fun buildFromAppFunctionDataFunction(
        annotatedClass: AnnotatedAppFunctionSerializable
    ): FunSpec {
        return FunSpec.builder(
                AppFunctionSerializableFactoryClass.FromAppFunctionDataMethod.METHOD_NAME
            )
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(
                ParameterSpec.builder(APP_FUNCTION_DATA_PARAM_NAME, AppFunctionData::class).build()
            )
            .addCode(
                buildCodeBlock {
                    val classProperties =
                        annotatedClass.appFunctionSerializableClass.getProperties()
                    for (property in classProperties) {
                        appendAppFunctionDataGetterStatement(property)
                    }
                    appendGetterReturnStatement(annotatedClass.originalClassName, classProperties)
                }
            )
            .returns(annotatedClass.originalClassName)
            .build()
    }

    private fun buildToAppFunctionDataFunction(
        annotatedClass: AnnotatedAppFunctionSerializable
    ): FunSpec {
        val methodParamName =
            annotatedClass.originalClassName.simpleName.replaceFirstChar { it -> it.lowercase() }
        return FunSpec.builder(
                AppFunctionSerializableFactoryClass.ToAppFunctionDataMethod.METHOD_NAME
            )
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(
                ParameterSpec.builder(methodParamName, annotatedClass.originalClassName).build()
            )
            .addCode(
                buildCodeBlock {
                    val classProperties =
                        annotatedClass.appFunctionSerializableClass.getProperties()
                    add("val builder = AppFunctionData.Builder(\"\")\n\n")
                    for (property in classProperties) {
                        appendAppFunctionDataSetterStatement(property, methodParamName)
                    }
                    add("\nreturn builder.build()")
                }
            )
            .returns(AppFunctionData::class.asTypeName())
            .build()
    }

    private fun CodeBlock.Builder.appendAppFunctionDataGetterStatement(param: KSValueParameter) {
        val typeName = param.type.ensureQualifiedTypeName().getShortName()
        val defaultValuePostfix =
            if (param.type.isStringList()) {
                " ?: emptyList()"
            } else if (param.type.isPrimitiveArray()) {
                " ?: ${typeName}(0)"
            } else {
                ""
            }

        val formatStringMap =
            mapOf<String, Any>(
                "param_name" to checkNotNull(param.name).asString(),
                "type_name" to typeName,
                "app_function_data_param_name" to APP_FUNCTION_DATA_PARAM_NAME,
                "getter_name" to if (param.type.isStringList()) "getStringList" else "get$typeName",
                "default_value_postfix" to defaultValuePostfix
            )

        addNamed(
            "val %param_name:L = %app_function_data_param_name:L.%getter_name:L(\"%param_name:L\")%default_value_postfix:L\n",
            formatStringMap
        )
    }

    private fun CodeBlock.Builder.appendAppFunctionDataSetterStatement(
        param: KSValueParameter,
        annotatedClassInstanceName: String
    ) {
        val formatStringMap =
            mapOf<String, Any>(
                "param_name" to checkNotNull(param.name).asString(),
                "setter_name" to
                    if (param.type.isStringList()) "setStringList"
                    else "set${param.type.ensureQualifiedTypeName().getShortName()}",
            )
        addNamed(
            "builder.%setter_name:L(\"%param_name:L\", ${annotatedClassInstanceName}.%param_name:L)\n",
            formatStringMap
        )
    }

    private fun CodeBlock.Builder.appendGetterReturnStatement(
        originalClassName: ClassName,
        params: List<KSValueParameter>
    ) {
        val formatStringMap =
            mapOf<String, Any>(
                "original_class_name" to originalClassName,
                "params_list" to
                    params.joinToString(", ") { param -> checkNotNull(param.name).asString() }
            )

        addNamed("\nreturn %original_class_name:T(%params_list:L)", formatStringMap)
    }

    private fun KSTypeReference.isStringList(): Boolean {
        return isOfType(LIST) &&
            resolveListParameterizedType().ensureQualifiedTypeName().asString() ==
                String::class.qualifiedName
    }

    private fun KSTypeReference.isPrimitiveArray(): Boolean {
        return SUPPORTED_ARRAY_PRIMITIVE_TYPES.contains(ensureQualifiedTypeName().asString())
    }

    private fun KSClassDeclaration.getProperties(): List<KSValueParameter> {
        return checkNotNull(primaryConstructor).parameters
    }

    @VisibleForTesting
    class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return AppFunctionSerializableProcessor(environment.codeGenerator, environment.logger)
        }
    }
}
