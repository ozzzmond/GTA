import java.security.KeyStore
import java.security.PrivateKey

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
        versionCode = 73
        versionName = "app v1.0.71"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    val releaseStoreFilePath = System.getenv("KEYSTORE_FILE")
        ?: (project.findProperty("KEYSTORE_FILE") as? String)
        ?: System.getenv("KEYSTORE_PATH")
        ?: System.getenv("RELEASE_STORE_FILE")
        ?: (project.findProperty("KEYSTORE_PATH") as? String)
    val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
        ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
        ?: System.getenv("KEY_STORE_PASSWORD")
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

    val validateReleaseCredentials = tasks.register("validateReleaseCredentials") {
        doLast {
            check(isReleaseSigningConfigured) {
                "Release signing requires valid KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD."
            }
            val keystoreFile = file(releaseStoreFilePath!!)
            check(keystoreFile.name != "debug.keystore" && releaseKeyAlias != "androiddebugkey") {
                "Debug signing credentials cannot be used for release builds."
            }
            try {
                val store = KeyStore.getInstance(keystoreFile, releaseStorePassword!!.toCharArray())
                check(store.getKey(releaseKeyAlias, releaseKeyPassword!!.toCharArray()) is PrivateKey)
                check(store.getCertificate(releaseKeyAlias) != null)
            } catch (e: Exception) {
                throw GradleException("Invalid release keystore, alias or signing passwords.", e)
            }
        }
    }
    tasks.configureEach {
        // Analysis and unit-test tasks must not require production credentials.
        if (name in setOf("packageRelease", "assembleRelease", "bundleRelease",
                "packageReleaseBundle", "signReleaseBundle")) {
            dependsOn(validateReleaseCredentials)
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
            manifestPlaceholders["appName"] = "GTAR"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev.2"
            manifestPlaceholders["appName"] = "GTAR-Dev"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "GTA_${versionName}.apk"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("com.google.api-client:google-api-client-android:2.7.2") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20240509-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.46.3")

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
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.json:json:20240303")
}
