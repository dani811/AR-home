plugins {
    id("com.android.application")
}

val isolatedInstall = providers.gradleProperty("isolatedInstall").orNull == "true"

android {
    namespace = "io.arhome.capabilities"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.arhome.capabilities"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "AR Home Localizer"
    }

    buildTypes {
        getByName("debug") {
            if (isolatedInstall) {
                applicationIdSuffix = ".validation"
                versionNameSuffix = "-depth"
                manifestPlaceholders["appLabel"] = "AR Home Localizer"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.10.5")
    implementation("com.google.ar:core:1.54.0")
    implementation("org.opencv:opencv:4.14.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
