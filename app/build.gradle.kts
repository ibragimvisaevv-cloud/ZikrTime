plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zikrtime.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zikrtime.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.1"
    }

    signingConfigs {
        // Постоянный ключ: с ним новые версии устанавливаются поверх старых.
        // Для Google Play позже создадим отдельный ключ загрузки и будем хранить его вне репозитория.
        create("zikr") {
            storeFile = rootProject.file("keystore/zikrtime.jks")
            storePassword = "zikrtime-dev-key"
            keyAlias = "zikrtime"
            keyPassword = "zikrtime-dev-key"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("zikr")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("zikr")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
