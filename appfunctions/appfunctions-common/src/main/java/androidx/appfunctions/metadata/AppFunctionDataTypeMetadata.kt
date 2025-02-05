/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,h
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appfunctions.metadata

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.appsearch.annotation.Document

@IntDef(
    AppFunctionDataTypeMetadata.TYPE_UNIT,
    AppFunctionDataTypeMetadata.TYPE_BOOLEAN,
    AppFunctionDataTypeMetadata.TYPE_BYTES,
    AppFunctionDataTypeMetadata.TYPE_OBJECT,
    AppFunctionDataTypeMetadata.TYPE_DOUBLE,
    AppFunctionDataTypeMetadata.TYPE_FLOAT,
    AppFunctionDataTypeMetadata.TYPE_LONG,
    AppFunctionDataTypeMetadata.TYPE_INT,
    AppFunctionDataTypeMetadata.TYPE_STRING,
    AppFunctionDataTypeMetadata.TYPE_PENDING_INTENT,
    AppFunctionDataTypeMetadata.TYPE_ARRAY
)
@Retention(AnnotationRetention.SOURCE)
internal annotation class AppFunctionDataType

@IntDef(
    AppFunctionDataTypeMetadata.TYPE_UNIT,
    AppFunctionDataTypeMetadata.TYPE_BOOLEAN,
    AppFunctionDataTypeMetadata.TYPE_BYTES,
    AppFunctionDataTypeMetadata.TYPE_DOUBLE,
    AppFunctionDataTypeMetadata.TYPE_FLOAT,
    AppFunctionDataTypeMetadata.TYPE_LONG,
    AppFunctionDataTypeMetadata.TYPE_INT,
    AppFunctionDataTypeMetadata.TYPE_STRING,
    AppFunctionDataTypeMetadata.TYPE_PENDING_INTENT,
)
@Retention(AnnotationRetention.SOURCE)
internal annotation class AppFunctionPrimitiveType

/** Base class for defining the schema of an input or output type. */
public abstract class AppFunctionDataTypeMetadata
internal constructor(
    /** Whether the data type is nullable. */
    public val isNullable: Boolean,
) {
    public companion object {
        /** Void type. */
        public const val TYPE_UNIT: Int = 0
        /** Boolean type. */
        public const val TYPE_BOOLEAN: Int = 1
        /** Byte array type. */
        public const val TYPE_BYTES: Int = 2
        /**
         * Object type. The schema of the object is defined in a [AppFunctionObjectTypeMetadata].
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public const val TYPE_OBJECT: Int = 3
        /** Double type. */
        public const val TYPE_DOUBLE: Int = 4
        /** Float type. */
        public const val TYPE_FLOAT: Int = 5
        /** Long type. */
        public const val TYPE_LONG: Int = 6
        /** Integer type. */
        public const val TYPE_INT: Int = 7
        /** String type. */
        public const val TYPE_STRING: Int = 8
        /** [android.app.PendingIntent] type. */
        public const val TYPE_PENDING_INTENT: Int = 9
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        /** Array type. The schema of the array is defined in a [AppFunctionArrayTypeMetadata] */
        public const val TYPE_ARRAY: Int = 10
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppFunctionDataTypeMetadata

        if (isNullable != other.isNullable) return false

        return true
    }

    override fun hashCode(): Int {
        return isNullable.hashCode()
    }

    override fun toString(): String {
        return "AppFunctionDataTypeMetadata(isNullable=$isNullable)"
    }
}

/** Defines the schema of an array data type. */
public class AppFunctionArrayTypeMetadata(
    /** The type of items in the array. */
    public val itemType: AppFunctionDataTypeMetadata
) : AppFunctionDataTypeMetadata(isNullable = false) {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is AppFunctionArrayTypeMetadata) return false

        if (itemType != other.itemType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + itemType.hashCode()
        return result
    }

    override fun toString(): String {
        return "AppFunctionArrayTypeMetadataDocument(itemType=$itemType) ${super.toString()}"
    }
}

/** Defines the schema of a object type. */
public class AppFunctionObjectTypeMetadata(
    /** The schema of the properties of the object. */
    public val properties: Map<String, AppFunctionDataTypeMetadata>,
    /** A list of required properties' names. */
    public val required: List<String>,
    /** Whether this data type is nullable. */
    isNullable: Boolean,
) : AppFunctionDataTypeMetadata(isNullable = isNullable) {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is AppFunctionObjectTypeMetadata) return false

        if (properties != other.properties) return false
        if (required != other.required) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + required.hashCode()
        return result
    }

    override fun toString(): String {
        return "AppFunctionObjectTypeMetadata(properties=$properties, required=$required) ${super.toString()}"
    }

    /** Converts this [AppFunctionObjectTypeMetadata] to a [AppFunctionDataTypeMetadataDocument]. */
    // TODO: Handle non-primitive and collections.
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun toAppFunctionDataTypeMetadataDocument(): AppFunctionDataTypeMetadataDocument {
        val properties =
            properties.map { (name, dataTypeMetadata) ->
                AppFunctionPropertyMetadataDocument(
                    name = checkNotNull(name),
                    dataTypeMetadata =
                        (dataTypeMetadata as AppFunctionPrimitiveTypeMetadata)
                            .toAppFunctionDataTypeMetadataDocument()
                )
            }
        return AppFunctionDataTypeMetadataDocument(
            type = TYPE_OBJECT,
            properties = properties,
            required = required
        )
    }
}

/**
 * Represents a type that reference a data type that is defined in [AppFunctionComponentsMetadata].
 */
public class AppFunctionReferenceTypeMetadata(
    /** The string referencing a data type defined in [AppFunctionComponentsMetadata]. */
    public val referenceDataType: String,
    /** Whether the data type is nullable. */
    isNullable: Boolean
) : AppFunctionDataTypeMetadata(isNullable = isNullable) {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is AppFunctionReferenceTypeMetadata) return false

        if (referenceDataType != other.referenceDataType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + referenceDataType.hashCode()
        return result
    }

    override fun toString(): String {
        return "AppFunctionReferenceTypeMetadata(referenceDataType=$referenceDataType) ${super.toString()}"
    }
}

/** Defines the schema of a primitive data type. */
public class AppFunctionPrimitiveTypeMetadata(
    @AppFunctionPrimitiveType public val type: Int,
    isNullable: Boolean
) : AppFunctionDataTypeMetadata(isNullable) {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is AppFunctionDataTypeMetadata) return false

        if (isNullable != other.isNullable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + isNullable.hashCode()
        return result
    }

    override fun toString(): String {
        return "AppFunctionPrimitiveTypeMetadata(isNullable=$isNullable) ${super.toString()}"
    }

    /**
     * Converts this [AppFunctionPrimitiveTypeMetadata] to a [AppFunctionDataTypeMetadataDocument].
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun toAppFunctionDataTypeMetadataDocument(): AppFunctionDataTypeMetadataDocument {
        return AppFunctionDataTypeMetadataDocument(type = type)
    }
}

/** Represents the persistent storage format of the schema of a property and its name. */
@Document
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AppFunctionPropertyMetadataDocument(
    @Document.Namespace public val namespace: String = APP_FUNCTION_NAMESPACE,
    /** The id of the data type. */
    @Document.Id public val id: String = APP_FUNCTION_ID_EMPTY,
    /** The name of the property. */
    @Document.StringProperty public val name: String,
    /** The schema of the property type. */
    @Document.DocumentProperty public val dataTypeMetadata: AppFunctionDataTypeMetadataDocument,
)

/** Represents the persistent storage format of [AppFunctionDataTypeMetadata]. */
@Document
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AppFunctionDataTypeMetadataDocument(
    @Document.Namespace public val namespace: String = APP_FUNCTION_NAMESPACE,
    /** The id of the data type. */
    @Document.Id public val id: String = APP_FUNCTION_ID_EMPTY,
    /** The data type. */
    @Document.LongProperty @AppFunctionDataType public val type: Int,

    /**
     * If the [type] is [AppFunctionDataTypeMetadata.TYPE_ARRAY], this specifies the array content
     * data type.
     */
    @Document.DocumentProperty public val itemType: AppFunctionDataTypeMetadataDocument? = null,
    /**
     * If the [type] is [AppFunctionDataTypeMetadata.TYPE_OBJECT], this specified the object's
     * properties.
     */
    @Document.DocumentProperty
    public val properties: List<AppFunctionPropertyMetadataDocument> = emptyList(),
    /**
     * If the [type] is [AppFunctionDataTypeMetadata.TYPE_OBJECT], this specified the object's
     * required properties' names.
     */
    @Document.StringProperty public val required: List<String> = emptyList(),
    /** If the [type] is [AppFunctionDataTypeMetadata.TYPE_OBJECT], this specified the reference */
    @Document.StringProperty public val dataTypeReference: String? = null,
    /** Whether the type is nullable. */
    @Document.BooleanProperty public val isNullable: Boolean = false,
)
