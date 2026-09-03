plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.kbawelfaremessenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kbawelfaremessenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.2"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("MYADV_KEYSTORE_FILE")
            val storePassword = System.getenv("MYADV_STORE_PASSWORD")
            val keyAlias = System.getenv("MYADV_KEY_ALIAS")
            val keyPassword = System.getenv("MYADV_KEY_PASSWORD")

            if (!keystoreFile.isNullOrBlank()) {
                storeFile = file(keystoreFile)
            }

            if (!storePassword.isNullOrBlank()) {
                this.storePassword = storePassword
            }

            if (!keyAlias.isNullOrBlank()) {
                this.keyAlias = keyAlias
            }

            if (!keyPassword.isNullOrBlank()) {
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
