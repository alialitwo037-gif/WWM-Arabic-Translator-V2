plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wwm.arabictranslator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wwm.arabictranslator"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.4"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
}
