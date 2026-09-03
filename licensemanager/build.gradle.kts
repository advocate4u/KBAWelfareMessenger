plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myadvlicensemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myadvlicensemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("MYADV_KEYSTORE_FILE")
            val storePassword = System.getenv("MYADV_STORE_PASSWORD")
            val keyAlias = System.getenv("MYADV_KEY_ALIAS")
            val keyPassword = System.getenv("MYADV_KEY_PASSWORD")

            if (!keystoreFile.isNullOrBlank() && !storePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                storeFile = file(keystoreFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    val signingPrivateKey = System.getenv("MYADV_LICENSE_PRIVATE_KEY_B64") ?: ""

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "MYADV_SIGNING_PRIVATE_KEY_B64", "\\\"${signingPrivateKey}\\\"")
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
}
