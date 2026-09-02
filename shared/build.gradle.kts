plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.activity.compose)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.mediapipe.tasks.vision)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.core)
        }
    }
}

android {
    namespace = "com.example.signcoach.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
