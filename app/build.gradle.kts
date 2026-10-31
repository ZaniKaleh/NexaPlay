plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.nexaplay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexaplay"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // NexaPlay ditargetkan untuk Infinix Note 12 G96 (ARM64).
        // Membatasi ABI mengurangi ukuran APK dan paket Python.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

chaquopy {
    defaultConfig {
        version = "3.11"
        // setup-python di GitHub Actions menyediakan python3.11 di PATH.
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")

    // ExoPlayer / Media3: pemutaran native MP4, MKV, MP3, AAC, M4A, FLAC, WAV.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
}
