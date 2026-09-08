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
            // api (niet implementation): AssetTemplateRepository's publieke constructor heeft een
            // Json-parameter, dus modules die shared gebruiken (composeApp) moeten deze dependency
            // ook zien - anders faalt hun compilatie met 'Cannot access class Json'.
            api(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.ui)
            // Dunne REST-client naar Supabase (PostgREST + Storage) voor SyncingTemplateRepository:
            // sync-bij-opstarten van goedgekeurde referentievarianten, met lokale cache als
            // offline-fallback (zie PLAN.md/gesprek: "database met referenties").
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            // Lokale sessie-opslag (access/refresh token) voor auth-persistentie.
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.activity.compose)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.mediapipe.tasks.vision)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.core)
        }
    }
}

android {
    namespace = "com.example.signcoach.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
