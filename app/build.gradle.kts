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
        versionCode = 14
        versionName = "0.8.2"
        // Ключ Яндекс Карт (MapKit): из gradle.properties или переменной окружения YANDEX_MAPKIT_KEY
        val mapkitKey = (project.findProperty("yandexMapkitKey") as String?) ?: System.getenv("YANDEX_MAPKIT_KEY") ?: ""
        buildConfigField("String", "MAPKIT_KEY", "\"$mapkitKey\"")
    }

    signingConfigs {
        // Ключ выпуска хранится в репозитории только в зашифрованном виде (keystore/release.jks.enc).
        // GitHub расшифровывает его паролем из секрета RELEASE_KEY_PASSWORD перед сборкой.
        val releaseKey = rootProject.file("keystore/release.jks")
        val releasePass = System.getenv("RELEASE_KEY_PASSWORD").orEmpty()
        if (releaseKey.exists() && releasePass.isNotEmpty()) {
            create("zikr") {
                storeFile = releaseKey
                storePassword = releasePass
                keyAlias = "zikrtime"
                keyPassword = releasePass
            }
        }
    }

    buildTypes {
        debug {
            // + x86_64 для эмулятора в автоматической проверке
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            // Карты Яндекса содержат нативные библиотеки: оставляем только процессоры реальных телефонов
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Без ключа выпуска (сборка на своём компьютере) подписываем отладочным ключом.
            signingConfig = signingConfigs.findByName("zikr") ?: signingConfigs.getByName("debug")
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
    // Яндекс Карты (бесплатная лайт-версия: до 1000 пользователей карты в день)
    implementation("com.yandex.android:maps.mobile:4.45.0-lite")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
