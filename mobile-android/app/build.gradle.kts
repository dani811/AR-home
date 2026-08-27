plugins {
    id("com.android.application")
}

android {
    namespace = "io.arhome.capabilities"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.arhome.capabilities"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.ar:core:1.54.0")
}
