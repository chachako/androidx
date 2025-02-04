/*
 * Copyright 2020 The Android Open Source Project
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

@file:Suppress("UnstableApiUsage") // Incubating AGP APIs

package androidx.build.testConfiguration

import androidx.build.AndroidXExtension
import androidx.build.AndroidXImplPlugin
import androidx.build.AndroidXImplPlugin.Companion.ZIP_CONSTRAINED_TEST_CONFIGS_WITH_APKS_TASK
import androidx.build.AndroidXImplPlugin.Companion.ZIP_TEST_CONFIGS_WITH_APKS_TASK
import androidx.build.asFilenamePrefix
import androidx.build.dependencyTracker.AffectedModuleDetector
import androidx.build.getConstrainedTestConfigDirectory
import androidx.build.getSupportRootFolder
import androidx.build.getTestConfigDirectory
import androidx.build.hasAndroidTestSourceCode
import androidx.build.hasBenchmarkPlugin
import androidx.build.isPresubmitBuild
import com.android.build.api.artifact.Artifacts
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTest
import com.android.build.gradle.BaseExtension
import java.io.File
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType

/**
 * Creates and configures the test config generation task for a project. Configuration includes
 * populating the task with relevant data from the first 4 params, and setting whether the task
 * is enabled.
 *
 * @param overrideProject Allows the config task for one project to get registered to an
 * alternative project. Default is for the project to register the new config task to itself
 */
fun Project.createTestConfigurationGenerationTask(
    variantName: String,
    artifacts: Artifacts,
    minSdk: Int,
    testRunner: String,
    overrideProject: Project = this
) {
    val xmlName = "${path.asFilenamePrefix()}$variantName.xml"
    val jsonName = "_${path.asFilenamePrefix()}$variantName.json"
    rootProject.tasks.named("createModuleInfo").configure {
        it as ModuleInfoGenerator
        it.testModules.add(
            TestModule(
                name = xmlName,
                path = listOf(projectDir.toRelativeString(getSupportRootFolder()))
            )
        )
    }
    val generateTestConfigurationTask = overrideProject.tasks.register(
        "${AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK}$variantName",
        GenerateTestConfigurationTask::class.java
    ) { task ->
        val androidXExtension = extensions.getByType<AndroidXExtension>()

        task.testFolder.set(artifacts.get(SingleArtifact.APK))
        task.testLoader.set(artifacts.getBuiltArtifactsLoader())
        task.outputTestApk.set(
            File(getTestConfigDirectory(), "${path.asFilenamePrefix()}-$variantName.apk")
        )
        task.constrainedOutputTestApk.set(
            File(
                getConstrainedTestConfigDirectory(),
                "${path.asFilenamePrefix()}-$variantName.apk"
            )
        )
        task.additionalApkKeys.set(androidXExtension.additionalDeviceTestApkKeys)
        task.additionalTags.set(androidXExtension.additionalDeviceTestTags)
        task.outputXml.fileValue(File(getTestConfigDirectory(), xmlName))
        task.outputJson.fileValue(File(getTestConfigDirectory(), jsonName))
        task.constrainedOutputXml.fileValue(File(getConstrainedTestConfigDirectory(), xmlName))
        task.presubmit.set(isPresubmitBuild())
        // Disable work tests on < API 18: b/178127496
        if (path.startsWith(":work:")) {
            task.minSdk.set(maxOf(18, minSdk))
        } else {
            task.minSdk.set(minSdk)
        }
        val hasBenchmarkPlugin = hasBenchmarkPlugin()
        task.hasBenchmarkPlugin.set(hasBenchmarkPlugin)
        task.testRunner.set(testRunner)
        task.testProjectPath.set(path)
        val detector = AffectedModuleDetector.getInstance(project)
        task.affectedModuleDetectorSubset.set(
            project.provider {
                detector.getSubset(task)
            }
        )
        AffectedModuleDetector.configureTaskGuard(task)
    }
    // Disable xml generation for projects that have no test sources
    // or explicitly don't want to run device tests
    afterEvaluate {
        val androidXExtension = extensions.getByType<AndroidXExtension>()
        generateTestConfigurationTask.configure {
            it.enabled = hasAndroidTestSourceCode() && !androidXExtension.disableDeviceTests
        }
    }
    this.rootProject.tasks.findByName(ZIP_TEST_CONFIGS_WITH_APKS_TASK)!!
        .dependsOn(generateTestConfigurationTask)
    this.rootProject.tasks.findByName(ZIP_CONSTRAINED_TEST_CONFIGS_WITH_APKS_TASK)!!
        .dependsOn(generateTestConfigurationTask)
}

/**
 * Further configures the test config generation task for a project. This only gets called when
 * there is a test app in addition to the instrumentation app, and the only thing it configures is
 * the location of the testapp.
 */
fun Project.addAppApkToTestConfigGeneration() {
    if (isMacrobenchmarkTarget()) {
        extensions.getByType<ApplicationAndroidComponentsExtension>().apply {
            onVariants(selector().withBuildType("release")) { appVariant ->
                getOrCreateMacrobenchmarkConfigTask().configure { configTask ->
                    configTask.appFolder.set(appVariant.artifacts.get(SingleArtifact.APK))
                    configTask.appLoader.set(appVariant.artifacts.getBuiltArtifactsLoader())
                    configTask.outputAppApk.set(
                        File(
                            getTestConfigDirectory(),
                            "${path.asFilenamePrefix()}-${appVariant.name}.apk"
                        )
                    )
                    configTask.constrainedOutputAppApk.set(
                        File(
                            getConstrainedTestConfigDirectory(),
                            "${path.asFilenamePrefix()}-${appVariant.name}.apk"
                        )
                    )
                }
                if (path == ":benchmark:integration-tests:macrobenchmark-target") {
                    // Ugly workaround for b/188699825 where we hardcode that
                    // :benchmark:integration-tests:macrobenchmark-target needs to be installed
                    // for :benchmark:benchmark-macro tests to work.
                    project(MACRO_PROJECT).tasks.withType(
                        GenerateTestConfigurationTask::class.java
                    ).named(
                        "${AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK}debugAndroidTest"
                    ).configure { configTask ->
                        configTask.appFolder.set(appVariant.artifacts.get(SingleArtifact.APK))
                        configTask.appLoader.set(appVariant.artifacts.getBuiltArtifactsLoader())
                        configTask.outputAppApk.set(
                            File(
                                getTestConfigDirectory(),
                                "${MACRO_PROJECT.asFilenamePrefix()}-${appVariant.name}.apk"
                            )
                        )
                        configTask.constrainedOutputAppApk.set(
                            File(
                                getConstrainedTestConfigDirectory(),
                                "${MACRO_PROJECT.asFilenamePrefix()}-${appVariant.name}.apk"
                            )
                        )
                    }
                }
            }
        }
        return
    }

    extensions.getByType<ApplicationAndroidComponentsExtension>().apply {
        onVariants(selector().withBuildType("debug")) { appVariant ->
            tasks.named(
                AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK +
                    "${appVariant.name}AndroidTest"
            ) { configTask ->
                configTask as GenerateTestConfigurationTask
                configTask.appFolder.set(appVariant.artifacts.get(SingleArtifact.APK))
                configTask.appLoader.set(appVariant.artifacts.getBuiltArtifactsLoader())
                configTask.outputAppApk.set(
                    File(
                        getTestConfigDirectory(),
                        "${path.asFilenamePrefix()}-${appVariant.name}.apk"
                    )
                )
                configTask.constrainedOutputAppApk.set(
                    File(
                        getConstrainedTestConfigDirectory(),
                        "${path.asFilenamePrefix()}-${appVariant.name}.apk"
                    )
                )
            }
        }
    }
}

private fun getOrCreateMediaTestConfigTask(project: Project, isMedia2: Boolean):
    TaskProvider<GenerateMediaTestConfigurationTask> {
        val mediaPrefix = getMediaConfigTaskPrefix(isMedia2)
        val parentProject = project.parent!!
        if (!parentProject.tasks.withType(GenerateMediaTestConfigurationTask::class.java)
            .names.contains(
                    "support-$mediaPrefix-test${
                    AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK
                    }"
                )
        ) {
            val task = parentProject.tasks.register(
                "support-$mediaPrefix-test${AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK}",
                GenerateMediaTestConfigurationTask::class.java
            ) { task ->
                AffectedModuleDetector.configureTaskGuard(task)
                val detector = AffectedModuleDetector.getInstance(project)
                task.affectedModuleDetectorSubset.set(
                    project.provider {
                        detector.getSubset(task)
                    }
                )
            }
            project.rootProject.tasks.findByName(ZIP_TEST_CONFIGS_WITH_APKS_TASK)!!
                .dependsOn(task)
            project.rootProject.tasks.findByName(ZIP_CONSTRAINED_TEST_CONFIGS_WITH_APKS_TASK)!!
                .dependsOn(task)
            return task
        } else {
            return parentProject.tasks.withType(GenerateMediaTestConfigurationTask::class.java)
                .named(
                    "support-$mediaPrefix-test${
                    AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK
                    }"
                )
        }
    }

private fun getMediaConfigTaskPrefix(isMedia2: Boolean): String {
    return if (isMedia2) "media2" else "media"
}

fun Project.createOrUpdateMediaTestConfigurationGenerationTask(
    variantName: String,
    artifacts: Artifacts,
    minSdk: Int,
    testRunner: String,
    isMedia2: Boolean
) {
    val mediaPrefix = getMediaConfigTaskPrefix(isMedia2)
    val mediaTask = getOrCreateMediaTestConfigTask(this, isMedia2)
    mediaTask.configure {
        it as GenerateMediaTestConfigurationTask
        if (this.name.contains("client")) {
            if (this.name.contains("previous")) {
                it.clientPreviousFolder.set(artifacts.get(SingleArtifact.APK))
                it.clientPreviousLoader.set(artifacts.getBuiltArtifactsLoader())
            } else {
                it.clientToTFolder.set(artifacts.get(SingleArtifact.APK))
                it.clientToTLoader.set(artifacts.getBuiltArtifactsLoader())
            }
        } else {
            if (this.name.contains("previous")) {
                it.servicePreviousFolder.set(artifacts.get(SingleArtifact.APK))
                it.servicePreviousLoader.set(artifacts.getBuiltArtifactsLoader())
            } else {
                it.serviceToTFolder.set(artifacts.get(SingleArtifact.APK))
                it.serviceToTLoader.set(artifacts.getBuiltArtifactsLoader())
            }
        }
        it.jsonClientPreviousServiceToTClientTests.fileValue(
            File(
                this.getTestConfigDirectory(),
                "_${mediaPrefix}ClientPreviousServiceToTClientTests$variantName.json"
            )
        )
        it.jsonClientPreviousServiceToTServiceTests.fileValue(
            File(
                this.getTestConfigDirectory(),
                "_${mediaPrefix}ClientPreviousServiceToTServiceTests$variantName.json"
            )
        )
        it.jsonClientToTServicePreviousClientTests.fileValue(
            File(
                this.getTestConfigDirectory(),
                "_${mediaPrefix}ClientToTServicePreviousClientTests$variantName.json"
            )
        )
        it.jsonClientToTServicePreviousServiceTests.fileValue(
            File(
                this.getTestConfigDirectory(),
                "_${mediaPrefix}ClientToTServicePreviousServiceTests$variantName.json"
            )
        )
        it.jsonClientToTServiceToTClientTests.fileValue(
            File(
                this.getTestConfigDirectory(),
                "_${mediaPrefix}ClientToTServiceToTClientTests$variantName.json"
            )
        )
        it.jsonClientToTServiceToTServiceTests.fileValue(
            File(
                this.getTestConfigDirectory(),
                "_${mediaPrefix}ClientToTServiceToTServiceTests$variantName.json"
            )
        )
        it.totClientApk.fileValue(
            File(getTestConfigDirectory(), "${mediaPrefix}ClientToT$variantName.apk")
        )
        it.previousClientApk.fileValue(
            File(getTestConfigDirectory(), "${mediaPrefix}ClientPrevious$variantName.apk")
        )
        it.totServiceApk.fileValue(
            File(getTestConfigDirectory(), "${mediaPrefix}ServiceToT$variantName.apk")
        )
        it.previousServiceApk.fileValue(
            File(getTestConfigDirectory(), "${mediaPrefix}ServicePrevious$variantName.apk")
        )
        it.minSdk.set(minSdk)
        it.testRunner.set(testRunner)
        it.presubmit.set(isPresubmitBuild())
        AffectedModuleDetector.configureTaskGuard(it)
    }
}

private fun Project.getOrCreateMacrobenchmarkConfigTask():
    TaskProvider<GenerateTestConfigurationTask> {
    val parentProject = this.parent!!
    return try {
        parentProject.tasks.withType(GenerateTestConfigurationTask::class.java)
            .named(AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK)
    } catch (e: UnknownTaskException) {
        parentProject.tasks.register(
            AndroidXImplPlugin.GENERATE_TEST_CONFIGURATION_TASK,
            GenerateTestConfigurationTask::class.java
        )
    }
}

private fun Project.configureMacrobenchmarkConfigTask(
    variantName: String,
    artifacts: Artifacts,
    minSdk: Int,
    testRunner: String
) {
    val configTask = getOrCreateMacrobenchmarkConfigTask()
    configTask.configure { task ->
        val androidXExtension = extensions.getByType<AndroidXExtension>()
        val fileNamePrefix = path.asFilenamePrefix()
        task.testFolder.set(artifacts.get(SingleArtifact.APK))
        task.testLoader.set(artifacts.getBuiltArtifactsLoader())
        task.outputTestApk.set(
            File(getTestConfigDirectory(), "${path.asFilenamePrefix()}-$variantName.apk")
        )
        task.constrainedOutputTestApk.set(
            File(
                getConstrainedTestConfigDirectory(),
                "${path.asFilenamePrefix()}-$variantName.apk"
            )
        )
        task.additionalApkKeys.set(androidXExtension.additionalDeviceTestApkKeys)
        task.additionalTags.set(androidXExtension.additionalDeviceTestTags)
        task.outputXml.fileValue(
            File(getTestConfigDirectory(), "$fileNamePrefix$variantName.xml")
        )
        task.outputJson.fileValue(
            File(getTestConfigDirectory(), "_$fileNamePrefix$variantName.json")
        )
        task.constrainedOutputXml.fileValue(
            File(
                getTestConfigDirectory(),
                "${path.asFilenamePrefix()}$variantName.xml"
            )
        )
        task.minSdk.set(minSdk)
        task.hasBenchmarkPlugin.set(hasBenchmarkPlugin())
        task.testRunner.set(testRunner)
        task.testProjectPath.set(path)
        task.presubmit.set(isPresubmitBuild())
        val detector = AffectedModuleDetector.getInstance(project)
        task.affectedModuleDetectorSubset.set(
            project.provider {
                detector.getSubset(task)
            }
        )

        AffectedModuleDetector.configureTaskGuard(task)
    }
    // Disable xml generation for projects that have no test sources
    afterEvaluate {
        val androidXExtension = extensions.getByType<AndroidXExtension>()
        configTask.configure {
            it.enabled = hasAndroidTestSourceCode() && !androidXExtension.disableDeviceTests
        }
    }
    rootProject.tasks.findByName(ZIP_TEST_CONFIGS_WITH_APKS_TASK)!!
        .dependsOn(configTask)
    rootProject.tasks.findByName(ZIP_CONSTRAINED_TEST_CONFIGS_WITH_APKS_TASK)!!
        .dependsOn(configTask)
}

/**
 * Tells whether this project is the macrobenchmark-target project
 */
fun Project.isMacrobenchmarkTarget(): Boolean {
    return path.endsWith("macrobenchmark-target")
}

fun Project.configureTestConfigGeneration(baseExtension: BaseExtension) {
    if (isMacrobenchmarkTarget()) {
        // macrobenchmark target projects use special setup. See addAppApkToTestConfigGeneration
        return
    }
    extensions.getByType(AndroidComponentsExtension::class.java).apply {
        onVariants { variant ->
            var name: String? = null
            var artifacts: Artifacts? = null
            when {
                variant is HasAndroidTest -> {
                    name = variant.androidTest?.name
                    artifacts = variant.androidTest?.artifacts
                }
                project.plugins.hasPlugin("com.android.test") -> {
                    name = variant.name
                    artifacts = variant.artifacts
                }
            }
            if (name == null || artifacts == null) {
                return@onVariants
            }
            when {
                path.contains("media2:media2-session:version-compat-tests:") -> {
                    createOrUpdateMediaTestConfigurationGenerationTask(
                        name,
                        artifacts,
                        baseExtension.defaultConfig.minSdk!!,
                        baseExtension.defaultConfig.testInstrumentationRunner!!,
                        isMedia2 = true
                    )
                }
                path.contains("media:version-compat-tests:") -> {
                    createOrUpdateMediaTestConfigurationGenerationTask(
                        name,
                        artifacts,
                        baseExtension.defaultConfig.minSdk!!,
                        baseExtension.defaultConfig.testInstrumentationRunner!!,
                        isMedia2 = false
                    )
                }
                path.endsWith("macrobenchmark") -> {
                    configureMacrobenchmarkConfigTask(
                        name,
                        artifacts,
                        baseExtension.defaultConfig.minSdk!!,
                        baseExtension.defaultConfig.testInstrumentationRunner!!
                    )
                }
                else -> {
                    createTestConfigurationGenerationTask(
                        name,
                        artifacts,
                        baseExtension.defaultConfig.minSdk!!,
                        baseExtension.defaultConfig.testInstrumentationRunner!!
                    )
                }
            }
        }
    }
}

private const val MACRO_PROJECT = ":benchmark:benchmark-macro"