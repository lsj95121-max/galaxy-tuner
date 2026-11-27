plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sangjun.galaxytuner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sangjun.galaxytuner"
        minSdk = 29
        targetSdk = 34
        versionCode = 12
        versionName = "1.0.11"
        vectorDrawables { useSupportLibrary = true }
    }

    // Two installable builds of the same app:
    //   sdk34 - normal build (targetSdk 34), package com.sangjun.galaxytuner
    //   sdk22 - no-connection refresh rate build (targetSdk 22, package ...sdk22) so
    //           WRITE_SETTINGS is granted at install time and Settings.System writes work
    //           without Shizuku, while Android 14 still allows the install with
    //           --bypass-low-target-sdk-block.
    flavorDimensions += "sdk"
    productFlavors {
        create("sdk34") {
            dimension = "sdk"
            minSdk = 29
            targetSdk = 34
            resValue("string", "app_name", "Galaxy Tuner")
            buildConfigField("String", "FLAVOR_NAME", "\"sdk34\"")
            buildConfigField("int", "COMPAT_TARGET_SDK", "34")
        }
        create("sdk22") {
            dimension = "sdk"
            minSdk = 29
            targetSdk = 22
            applicationIdSuffix = ".sdk22"
            versionNameSuffix = ""
            resValue("string", "app_name", "Galaxy Tuner (SDK22)")
            buildConfigField("String", "FLAVOR_NAME", "\"sdk22\"")
            buildConfigField("int", "COMPAT_TARGET_SDK", "22")
        }
    }

    signingConfigs {
        getByName("debug") {
            // Committed on purpose: debug signing only, so local and CI builds are identical.
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        aidl = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // extractNativeLibs=true: the sdk22 flavor targets API 22, where native libraries are
        // loaded from the extracted lib dir (the ADB pairing helper ships libspake2.so).
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        // The sdk22 flavor targets API 22 on purpose; that lint warning is expected.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Built-in wireless debugging (ADB pairing + shell). Apache-2.0.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // libadb pairs over TLS 1.3 and exports keying material through Conscrypt. The platform copy
    // (com.android.org.conscrypt) has no public exportKeyingMaterial, which is what made Android 17
    // pairing fail with NoSuchMethodException; this library ships org.conscrypt with the method.
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
