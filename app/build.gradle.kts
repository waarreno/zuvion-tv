plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Versão vem do CI (tag vX.Y.Z) ou usa o padrão local.
val appVersionName: String = (project.findProperty("versionName") as String?) ?: "1.0.0"
val appVersionCode: Int = (project.findProperty("versionCode") as String?)?.toInt() ?: run {
    val p = appVersionName.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    p[0] * 10000 + p[1] * 100 + p[2]
}
val githubRepo: String = (project.findProperty("githubRepo") as String?) ?: "waarreno/zuvion-tv"

android {
    namespace = "com.zuvion.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zuvion.tv"
        minSdk = 21
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        // Chaves padrão das APIs: vêm dos secrets do GitHub na compilação (nunca do código).
        fun secret(name: String) = (System.getenv(name) ?: "").replace("\"", "")
        buildConfigField("String", "TMDB_KEY", "\"${secret("ZUVION_TMDB_KEY")}\"")
        buildConfigField("String", "INTRODB_KEY", "\"${secret("ZUVION_INTRODB_KEY")}\"")
        vectorDrawables { useSupportLibrary = true }
        // APK único para ARM 32 e 64 bits: cobre todos os celulares, TV boxes e Google TV.
        // (x86 ficaria com +42 MB e falta espaço para instalar em TVs com pouco armazenamento.)
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("ZUVION_KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("ZUVION_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ZUVION_KEY_ALIAS")
                keyPassword = System.getenv("ZUVION_KEY_PASSWORD")
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = true }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-effect:$media3")

    implementation("org.videolan.android:libvlc-all:3.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
