plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
}

android {
    namespace = "com.ew.handscript"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ew.handscript"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 后端API基础地址 - 生产环境
            buildConfigField("String", "API_BASE_URL", "https://api.handcraft.font/v1/")
            buildConfigField("String", "CDN_BASE_URL", "https://cdn.handcraft.font/")
        }
        debug {
            isDebuggable = true
            // 后端API基础地址 - 开发环境
            buildConfigField("String", "API_BASE_URL", "https://dev-api.handcraft.font/v1/")
            buildConfigField("String", "CDN_BASE_URL", "https://dev-cdn.handcraft.font/")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose Bundle
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)
    androidTestImplementation(libs.bundles.compose.test)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Database - Room
    implementation(libs.bundles.database)
    kapt(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)

    // Dependency Injection - Hilt
    implementation(libs.bundles.dependency.injection)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.work)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // OpenCV - 图像处理核心库
    implementation(libs.opencv)

    // ML Kit - 本地OCR识别
    implementation(libs.ml.kit.text.recognition)
    implementation(libs.ml.kit.text.recognition.chinese)

    // Networking
    implementation(libs.bundles.networking)

    // Coil Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Timber Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
