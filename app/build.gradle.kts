plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.bhaskar.litelauncher"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.bhaskar.litelauncher"
        minSdk = 21
        targetSdk = 28
        versionCode = 2
        versionName = "1.1"
    }
    // Fixed key so every GitHub build has the same signature and installs as an update
    signingConfigs {
        create("fixed") {
            storeFile = file("litelauncher.keystore")
            storePassword = "android"
            keyAlias = "litelauncher"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
        getByName("release") {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
        }
    }
    // targetSdk 28 is intentional (old Mi TV, lets the launcher list all apps and use storage freely)
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
