plugins {
    id("com.android.application")
}

android {
    namespace = "com.soundbox"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.soundbox"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
