package com.ehviewer

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.tooling.KotlinToolingMetadata
import org.jetbrains.kotlin.tooling.toJsonString

fun Project.configureKotlinToolingMetadata() {
    val android = extensions.getByType<ApplicationExtension>()
    val kotlin = extensions.getByType<KotlinAndroidExtension>()
    extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
        check(kotlin.target is KotlinAndroidTarget)
        val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
        val metadata = tasks.register<GenerateKotlinToolingMetadataTask>("generate${variantName}KotlinToolingMetadata") {
            kotlinVersion.set(getKotlinPluginVersion())
            gradleVersion.set(gradle.gradleVersion)
            javaSourceCompatibility.set(android.compileOptions.sourceCompatibility.toString())
            javaTargetCompatibility.set(android.compileOptions.targetCompatibility.toString())
        }
        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(metadata)
            .toAppend(ScopedArtifact.JAVA_RES) { it.outputDirectory }
    }
}

/** Standard Kotlin tooling metadata for AGP's built-in Kotlin base API plugin. */
@CacheableTask
abstract class GenerateKotlinToolingMetadataTask : DefaultTask() {
    @get:Input
    abstract val kotlinVersion: Property<String>

    @get:Input
    abstract val gradleVersion: Property<String>

    @get:Input
    abstract val javaSourceCompatibility: Property<String>

    @get:Input
    abstract val javaTargetCompatibility: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val metadata = KotlinToolingMetadata(
            schemaVersion = KotlinToolingMetadata.currentSchemaVersion,
            buildSystem = "Gradle",
            buildSystemVersion = gradleVersion.get(),
            buildPlugin = KotlinBaseApiPlugin::class.java.name,
            buildPluginVersion = kotlinVersion.get(),
            projectSettings = KotlinToolingMetadata.ProjectSettings(
                isHmppEnabled = true,
                isCompatibilityMetadataVariantEnabled = false,
                isKPMEnabled = false,
            ),
            projectTargets = listOf(
                KotlinToolingMetadata.ProjectTargetMetadata(
                    target = KotlinAndroidTarget::class.java.name,
                    platformType = "androidJvm",
                    extras = KotlinToolingMetadata.ProjectTargetMetadata.Extras(
                        android = KotlinToolingMetadata.ProjectTargetMetadata.AndroidExtras(
                            sourceCompatibility = javaSourceCompatibility.get(),
                            targetCompatibility = javaTargetCompatibility.get(),
                        ),
                    ),
                ),
            ),
        )
        outputDirectory.file("kotlin-tooling-metadata.json").get().asFile.apply {
            parentFile.mkdirs()
            writeText(metadata.toJsonString())
        }
    }
}
