/**
 * ================================================================
 * 蚯蚓.手书修仙传 — app/build.gradle.kts (final-1.2)
 * 日期: 2026-06-10
 * 修复: 清理错误OpenCV本地模块引用，改用Maven依赖
 * ================================================================
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)          // Kotlin 1.9.24 绑定，与 toml 严格一致
    alias(libs.plugins.hilt)          // Hilt 2.51.1，与 toml 严格一致
}

android {
    namespace = "com.ew.handscript"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ew.handscript"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += setOf("arm64-v8a")  // 华为 Mate 30 (麒麟 990)
        }
    }

    androidResources {
        noCompress += listOf("nb", "tflite")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets["main"].assets.srcDirs("src/main/assets")
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
    correctErrorTypes = true
}

val schemaDir = file("$projectDir/schemas")
if (!schemaDir.exists()) {
    schemaDir.mkdirs()
}

dependencies {

    // ================================================================
    // 1. Kotlin 基础
    // ================================================================
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ================================================================
    // 2. Jetpack Compose — BOM 2024.02.00
    // ================================================================
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ================================================================
    // 3. Room 2.6.1 + Kapt
    // ================================================================
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // ================================================================
    // 4. Hilt 2.51.1 + WorkManager
    // ================================================================
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    // ================================================================
    // 5. TFLite 2.14.0 + support 0.4.4
    // ================================================================
    implementation(libs.tflite)
    implementation(libs.tflite.gpu)
    implementation(libs.tflite.support)

    // ================================================================
    // 6. Paddle-Lite — OCR 管线（暂不启用）
    // ================================================================
    // implementation(libs.paddle.lite.java)

    // ================================================================
    // 7. OpenCV 4.5.3 — L3 灵石提纯层（Maven依赖，非本地模块）
    // ================================================================
    // 旧本地模块引用已移除：implementation(project(":opencv"))
    implementation(libs.opencv)

    // ================================================================
    // 8. Timber 5.0.1 — 日志框架
    // ================================================================
    implementation(libs.timber)

    // ================================================================
    // 9. CameraX 1.3.1 — 手稿拍照
    // ================================================================
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ================================================================
    // 10. AndroidX 基础
    // ================================================================
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.datastore.preferences)

    // ================================================================
    // 11. 图片加载
    // ================================================================
    implementation(libs.coil.compose)

    // ================================================================
    // 12. 测试
    // ================================================================
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}