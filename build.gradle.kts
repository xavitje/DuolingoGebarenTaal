plugins {
    kotlin("multiplatform") version "2.1.21" apply false
    kotlin("plugin.serialization") version "2.1.21" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
    // Wordt alleen daadwerkelijk toegepast in composeApp/build.gradle.kts zodra
    // google-services.json aanwezig is (zie de conditionele apply() daar) — anders zou de
    // build meteen falen met "File google-services.json is missing" vóórdat de gebruiker
    // zijn Firebase-project heeft aangemaakt.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
