// AGP 9 ships Kotlin support built in, so org.jetbrains.kotlin.android must NOT be applied -- doing
// so is a hard error. The Compose compiler plugin is still separate and still required whenever
// buildFeatures.compose is on.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.przunk.protracktor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.przunk.protracktor"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Stated explicitly rather than left to whatever the NDK defaults to that month, because
        // native decoder builds are the expensive part of this project and the ABI list drives
        // that cost directly. 32-bit x86 is omitted: it exists only on emulators, and there is no
        // emulator in this environment anyway.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
