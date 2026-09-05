plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.MyAdvocate.licensemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.MyAdvocate.licensemanager"
        minSdk = 26
        targetSdk = 35

        val configuredVersionCode = providers.gradleProperty("VERSION_CODE").orElse("6").get().toInt()
        val configuredVersionName = providers.gradleProperty("APP_VERSION").orElse("2.6").get()
        versionCode = configuredVersionCode
        versionName = configuredVersionName
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("MYADV_KEYSTORE_FILE")
            val storePassword = System.getenv("MYADV_STORE_PASSWORD")
            val keyAlias = System.getenv("MYADV_KEY_ALIAS")
            val keyPassword = System.getenv("MYADV_KEY_PASSWORD")

            require(!keystoreFile.isNullOrBlank()) { "MYADV_KEYSTORE_FILE is required for release signing" }
            require(!storePassword.isNullOrBlank()) { "MYADV_STORE_PASSWORD is required for release signing" }
            require(!keyAlias.isNullOrBlank()) { "MYADV_KEY_ALIAS is required for release signing" }
            require(!keyPassword.isNullOrBlank()) { "MYADV_KEY_PASSWORD is required for release signing" }
            require(file(keystoreFile).isFile) { "Release keystore not found: $keystoreFile" }

            storeFile = file(keystoreFile)
            this.storePassword = storePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
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
}
