import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val ndkVersionName = "26.3.11579264"
val ndkDir = File(System.getProperty("user.home"), "Library/Android/sdk/ndk/$ndkVersionName")
val hasUsableNdk = File(ndkDir, "source.properties").exists()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val releaseStorePath = keystoreProperties.getProperty("storeFile")?.trim().orEmpty()
val releaseStoreFile = releaseStorePath.takeIf { it.isNotEmpty() }?.let { rootProject.file(it) }
val hasReleaseSigning =
    releaseStoreFile?.exists() == true &&
        !keystoreProperties.getProperty("storePassword").isNullOrBlank() &&
        !keystoreProperties.getProperty("keyAlias").isNullOrBlank() &&
        !keystoreProperties.getProperty("keyPassword").isNullOrBlank()

android {
    namespace = "com.example.myapplication"
    compileSdk = 35
    buildToolsVersion = "35.0.1"
    if (hasUsableNdk) {
        ndkVersion = ndkVersionName
    }

    defaultConfig {
        applicationId = "com.local.stzb.random"
        minSdk = 33
        targetSdk = 35
        versionCode = 10001
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (hasUsableNdk) {
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }

            externalNativeBuild {
                ndkBuild {
                    arguments += listOf(
                        "APP_CFLAGS+=-DPKGNAME=hev/sockstun -ffile-prefix-map=${rootDir}=.",
                        "APP_LDFLAGS+=-Wl,--build-id=none"
                    )
                }
            }
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    if (hasUsableNdk) {
        externalNativeBuild {
            ndkBuild {
                path = file("../third_party/sockstun/app/src/main/jni/Android.mk")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
