plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
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
            implementation(compose.foundation)
            implementation(compose.material3)
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
            implementation(libs.kotlinx.datetime)
            // Zero Trust / offline-first (zie PLAN.md): lokale SQLite-database die als
            // single source of truth dient voor de UI. Elke oefening wordt eerst hier
            // weggeschreven (werkt altijd, ook zonder netwerk); SyncManager stuurt de wachtrij
            // pas door naar Supabase zodra er weer verbinding is.
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
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
            implementation(libs.androidx.browser)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.sqldelight.android.driver)
            // Firebase Cloud Messaging: FcmTokenProvider.android.kt leeft hier (in shared, niet
            // composeApp), dus de dependency moet ook hier staan. Hardcoded coördinaten i.p.v.
            // de version catalog — zie de uitleg in composeApp/build.gradle.kts (catalog-
            // accessors breken hier op "Cannot convert the provided notation to an object of
            // type Dependency").
            implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation("com.google.firebase:firebase-messaging-ktx")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.core)
        }
    }
}

android {
    namespace = "io.github.xavitje.signcoach.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("SignCoachDatabase") {
            packageName.set("io.github.xavitje.signcoach.db")
        }
    }
}
