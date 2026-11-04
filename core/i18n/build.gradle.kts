plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "com.ehviewer.core.i18n"
        androidResources.enable = true
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.resources)
            }
        }
    }

    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    resourcesPackage = "com.ehviewer.core.i18n"
}
