plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.kbawelfaremessenger"
    compileSdk = 35

    defaultConfig {
        // Keep the applicationId unchanged so existing MyAdv/KBAWelfareMessenger
        // installations can receive updates instead of becoming a second app.
        applicationId = "com.example.kbawelfaremessenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
    }

    buildTypes {
        release {
            // Sideload/update build: use the same debug signing key as the
            // existing debug APK so an already-installed debug build can update
            // without a signature-conflict error. For Play Store publishing,
            // replace this with a permanent release keystore.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
