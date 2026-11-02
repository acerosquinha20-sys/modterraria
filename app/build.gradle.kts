plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.terrariaesp"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.terrariaesp"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
