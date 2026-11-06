plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.gifwidgets"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.gifwidgets"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8 + resource shrinking (smaller APK). Test the release variant once: library, editor, widget, Settings.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // personal use only: not a store signature
        }
    }

    // Keep only the languages the app has (Spanish default + English); libraries ship dozens of translations.
    androidResources {
        localeFilters += listOf("es", "en")
    }

    // No Google "dependency metadata" blob inside the APK (it is an opaque, signed block; F-Droid rejects it).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Only the @Preview annotation. NOT ui-tooling, whose debug variant adds an exported activity.
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Pure-Kotlin unit tests only.
    testImplementation(libs.junit)
}