import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // New to this toolchain (opendash and Rustify use neither). If the first
    // sync fails on these two lines, see openfuel-docs/build/checklist-primer-build.md §2.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.varuna.openfuel"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.varuna.openfuel"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        // Two languages from the start. Adding one later means auditing every
        // string, and it never gets done.
        androidResources.localeFilters += setOf("en", "es")
    }

    // MapLibre ships native code for every ABI; keep the APKs to the two that matter.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // Per-app language needs every language inside the installed APK.
    bundle {
        language {
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
    }

    buildTypes {
        release {
            // Same as Rustify: R8 plus resource shrinking. Most of the weight is
            // material-icons-extended, of which the app uses a dozen icons.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        disable += "OldTargetApi"
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Exported schemas are committed: they are what a future migration is written against.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.AMAZON)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.maplibre.android)

    testImplementation(libs.junit)
}
