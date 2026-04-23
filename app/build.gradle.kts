plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mimo.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mimo.keyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.9.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // FIX: Added lifecycle-runtime-compose for LocalLifecycleOwner in Compose
    // (replaces deprecated androidx.lifecycle.LocalLifecycleOwner)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Activity Compose for setContent
    implementation("androidx.activity:activity-compose:1.8.2")

    // SavedState support for ComposeView in InputMethodService
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
