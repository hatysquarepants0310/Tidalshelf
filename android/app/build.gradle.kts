import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clave de API de Last.fm integrada en el APK para que el usuario final solo
// tenga que tocar "Autorizar" (flujo web). Prioridad: -PlastfmApiKey=... >
// variables de entorno > android/lastfm.properties (commiteado en el repo).
val lastfmProps = Properties().apply {
    val file = rootProject.file("lastfm.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val lastfmApiKey: String =
    (project.findProperty("lastfmApiKey") as String?)
        ?: System.getenv("LASTFM_API_KEY")?.takeIf { it.isNotEmpty() }
        ?: lastfmProps.getProperty("apiKey", "")
val lastfmApiSecret: String =
    (project.findProperty("lastfmApiSecret") as String?)
        ?: System.getenv("LASTFM_API_SECRET")?.takeIf { it.isNotEmpty() }
        ?: lastfmProps.getProperty("apiSecret", "")

android {
    namespace = "app.tidalshelf.scrobbler"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.tidalshelf.scrobbler"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.2.2"
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"$lastfmApiSecret\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Llave commiteada en el repo para que TODOS los builds (CI, local, de
    // cualquier colaborador) firmen igual y las actualizaciones se instalen
    // encima sin desinstalar. Mismo modelo de confianza que la clave de API:
    // el ancla es el repo, no el secreto.
    signingConfigs {
        create("shared") {
            storeFile = file("../keystore.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
