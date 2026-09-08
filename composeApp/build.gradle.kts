plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // shared moet hier expliciet geëxporteerd worden zodat de Kotlin-types die de iOS-brug
            // gebruikt (HandPose, TrackerStatus, IosPlatformHooks, ...) ook zichtbaar zijn in het
            // gegenereerde Objective-C/Swift-headerbestand van ComposeApp.framework. Zonder export()
            // ziet Swift in iosApp/ deze symbolen niet, ook al compileert de Kotlin-kant prima.
            export(project(":shared"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api (niet implementation) is vereist om export(project(":shared")) hierboven te
            // laten werken - de Kotlin Multiplatform cocoapods/framework-export-mechaniek staat
            // alleen 'api'-afhankelijkheden toe als export-kandidaat.
            api(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(libs.navigation.compose)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
        }
    }
}

android {
    namespace = "com.example.signcoach"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.signcoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    packageOfResClass = "com.example.signcoach.resources"
}
