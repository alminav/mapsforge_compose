plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.almica.mapsforge_compose"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.almica.mapsforge_compose"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation.layout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Google Play Services for Location & Activity Recognition
    implementation(libs.play.services.location)
    implementation(libs.googleMapsUtils)

    // Mapsforge
    implementation(libs.mapsforge.android)
    implementation(libs.mapsforge.themes)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // AndroidX & Material
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Logging
    implementation(libs.timber)

    // Android Maps Utils

    // Preference
    implementation(libs.androidx.preference.ktx)

    // Gson
    implementation(libs.gson)

    // GraphHopper
    implementation(project(":graphhopper"))
    // Charts
    implementation(project(":composecharts"))
}
