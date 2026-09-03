plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFile = System.getenv("MYADV_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("MYADV_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("MYADV_KEY_ALIAS")
val releaseKeyPassword = System.getenv("MYADV_KEY_PASSWORD")

android {
    namespace = "com.example.kbawelfaremessenger"
    compileSdk = 35

    defaultConfig {
        // Keep the applicationId unchanged so existing MyAdv/KBAWelfareMessenger
        // installations keep the same Android app identity.
        applicationId = "com.example.kbawelfaremessenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
    }

    signingConfigs {
        create("myAdvRelease") {
            if (releaseStoreFile.isNullOrBlank() || releaseStorePassword.isNullOrBlank() ||
                releaseKeyAlias.isNullOrBlank() || releaseKeyPassword.isNullOrBlank()
            ) {
                throw GradleException(
                    "MyAdv release signing is not configured. Set MYADV_KEYSTORE_FILE, " +
                        "MYADV_STORE_PASSWORD, MYADV_KEY_ALIAS and MYADV_KEY_PASSWORD."
                )
            }
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("myAdvRelease")
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
