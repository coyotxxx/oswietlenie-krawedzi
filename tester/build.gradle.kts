// Aplikacja TYLKO do testów na emulatorze: wysyła każdy rodzaj powiadomienia na żądanie (adb).
// Nie trafia do wydań.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "pl.krawedzie.tester"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.krawedzie.tester"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
