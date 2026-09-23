plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Push-notificaties (Firebase Cloud Messaging): het google-services-plugin genereert de
// FirebaseApp-configuratie uit google-services.json, maar dat bestand bestaat pas zodra de
// gebruiker zelf een Firebase-project heeft aangemaakt (zie PLAN.md / taak "Firebase setup").
// Zonder dit bestand faalt het plugin de build meteen, dus passen we het alleen toe als het
// bestand er al is. FcmTokenProvider.android.kt vangt de ontbrekende configuratie zelf ook
// netjes af, zodat push-registratie tot die tijd gewoon stilletjes niets doet.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
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
            // Firebase Cloud Messaging voor push-notificaties (zie FcmTokenProvider.android.kt /
            // SignCoachFirebaseMessagingService.kt). De BOM lijnt alle Firebase-libversies uit;
            // werkt ook zonder google-services.json te compileren, alleen de daadwerkelijke
            // FirebaseApp-initialisatie faalt dan netjes at runtime (afgevangen met try/catch).
            // Directe string-dependencies i.p.v. via de version catalog: een catalog-accessor
            // (ook mét eigen versie, en ook via platform()) laat Gradle's dependency-notatie
            // stuklopen binnen dit KMP sourceSets{}-blok ("Cannot convert the provided
            // notation to an object of type Dependency: map(valueof(DependencyValueSource))") —
            // vandaar hardcoded coördinaten hier in plaats van libs.firebase.bom / libs.firebase.messaging.ktx.
            implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation("com.google.firebase:firebase-messaging-ktx")
        }
    }
}

android {
    namespace = "io.github.xavitje.signcoach"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.xavitje.signcoach"
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
    packageOfResClass = "io.github.xavitje.signcoach.resources"
}
