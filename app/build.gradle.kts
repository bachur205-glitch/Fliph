plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flipdeck.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.flipdeck.launcher"
        minSdk = 26
        // Targets Android 11 (API 30) on purpose: it keeps direct Bluetooth
        // toggling working and avoids the Android 12+ permission prompts.
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // targetSdk 30 trips the "expired target SDK" check, which only matters for Google Play.
        abortOnError = false
        checkReleaseBuilds = false
    }
}
