plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.joel.gta"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joel.gta"
        minSdk = 26
        targetSdk = 35
        versionCode = 41
        versionName = "1.0.40"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    val releaseStoreFilePath = System.getenv("KEYSTORE_PATH")
        ?: System.getenv("RELEASE_STORE_FILE")
        ?: (project.findProperty("KEYSTORE_PATH") as? String)
    val releaseStorePassword = System.getenv("KEY_STORE_PASSWORD")
        ?: (project.findProperty("KEY_STORE_PASSWORD") as? String)
    val releaseKeyAlias = System.getenv("ALIAS")
        ?: System.getenv("KEY_ALIAS")
        ?: (project.findProperty("ALIAS") as? String)
        ?: (project.findProperty("KEY_ALIAS") as? String)
    val releaseKeyPassword = System.getenv("KEY_PASSWORD")
        ?: (project.findProperty("KEY_PASSWORD") as? String)

    val isReleaseSigningConfigured = !releaseStoreFilePath.isNullOrBlank()
        && file(releaseStoreFilePath).exists()
        && !releaseStorePassword.isNullOrBlank()
        && !releaseKeyAlias.isNullOrBlank()
        && !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        create("release") {
            if (isReleaseSigningConfigured) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (isReleaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jsoup:jsoup:1.18.3")

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
