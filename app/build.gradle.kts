plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.gruzblank.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.gruzblank.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
