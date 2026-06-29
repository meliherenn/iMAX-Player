import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

val defaultUpdateManifestUrl = "https://github.com/meliherenn/iMAX-Player/releases/latest/download/latest.json"

val versionProperties = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) load(file.inputStream())
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val appVersionCode = providers.gradleProperty("VERSION_CODE").orNull
    ?: System.getenv("VERSION_CODE")
    ?: versionProperties.getProperty("VERSION_CODE", "1")

val appVersionName = providers.gradleProperty("VERSION_NAME").orNull
    ?: System.getenv("VERSION_NAME")
    ?: versionProperties.getProperty("VERSION_NAME", "1.0.0")

val parsedAppVersionCode = appVersionCode.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("VERSION_CODE must be a positive integer, but was '$appVersionCode'")

val parsedAppVersionName = appVersionName.trim()
    .takeIf(String::isNotEmpty)
    ?: error("VERSION_NAME must not be blank")

val updateManifestUrl = providers.gradleProperty("UPDATE_MANIFEST_URL").orNull
    ?: System.getenv("UPDATE_MANIFEST_URL")
    ?: localProperties.getProperty("UPDATE_MANIFEST_URL", defaultUpdateManifestUrl)

val remoteSetupApiBaseUrl = providers.gradleProperty("REMOTE_SETUP_API_BASE_URL").orNull
    ?: System.getenv("REMOTE_SETUP_API_BASE_URL")
    ?: localProperties.getProperty("REMOTE_SETUP_API_BASE_URL", "")

val remoteSetupWebBaseUrl = providers.gradleProperty("REMOTE_SETUP_WEB_BASE_URL").orNull
    ?: System.getenv("REMOTE_SETUP_WEB_BASE_URL")
    ?: localProperties.getProperty("REMOTE_SETUP_WEB_BASE_URL", "")

val remoteSetupEnabled = remoteSetupApiBaseUrl.startsWith("https://") &&
    remoteSetupWebBaseUrl.startsWith("https://")

val releaseStoreFile = providers.gradleProperty("IMAX_RELEASE_STORE_FILE").orNull
    ?: System.getenv("IMAX_RELEASE_STORE_FILE")
    ?: localProperties.getProperty("IMAX_RELEASE_STORE_FILE")

val releaseStorePassword = providers.gradleProperty("IMAX_RELEASE_STORE_PASSWORD").orNull
    ?: System.getenv("IMAX_RELEASE_STORE_PASSWORD")
    ?: localProperties.getProperty("IMAX_RELEASE_STORE_PASSWORD")

val releaseKeyAlias = providers.gradleProperty("IMAX_RELEASE_KEY_ALIAS").orNull
    ?: System.getenv("IMAX_RELEASE_KEY_ALIAS")
    ?: localProperties.getProperty("IMAX_RELEASE_KEY_ALIAS")

val releaseKeyPassword = providers.gradleProperty("IMAX_RELEASE_KEY_PASSWORD").orNull
    ?: System.getenv("IMAX_RELEASE_KEY_PASSWORD")
    ?: localProperties.getProperty("IMAX_RELEASE_KEY_PASSWORD")

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.imax.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.imax.player"
        minSdk = 26
        targetSdk = 35
        versionCode = parsedAppVersionCode
        versionName = parsedAppVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TMDB_API_KEY", buildConfigString(localProperties.getProperty("TMDB_API_KEY", "")))
        buildConfigField("boolean", "SELF_HOSTED_UPDATES_ENABLED", "false")
        buildConfigField("String", "UPDATE_MANIFEST_URL", buildConfigString(""))
        buildConfigField("boolean", "REMOTE_SETUP_ENABLED", remoteSetupEnabled.toString())
        buildConfigField("String", "REMOTE_SETUP_API_BASE_URL", buildConfigString(remoteSetupApiBaseUrl.trimEnd('/')))
        buildConfigField("String", "REMOTE_SETUP_WEB_BASE_URL", buildConfigString(remoteSetupWebBaseUrl.trimEnd('/')))

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                // Force an invariant locale for the test JVM. On a Turkish-locale machine,
                // conscrypt (pulled in by Robolectric) lowercases "LINUX" to "lınux" (dotless ı)
                // when resolving its bundled native lib, which then fails to load.
                test.systemProperty("user.language", "en")
                test.systemProperty("user.country", "US")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += setOf(
                "lib/*/libc++_shared.so"
            )
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("selfHostedRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "SELF_HOSTED_UPDATES_ENABLED", "true")
            buildConfigField("String", "UPDATE_MANIFEST_URL", buildConfigString(updateManifestUrl))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.splashscreen)
    implementation(libs.appcompat)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // WorkManager + Hilt integration
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Security Crypto (EncryptedSharedPreferences for PIN storage)
    implementation(libs.security.crypto)
    implementation(libs.zxing.core)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Playback
    implementation(libs.libvlc)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ffmpeg.decoder)

    // Image Loading
    implementation(libs.coil)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.test.core.ktx)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
