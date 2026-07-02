plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clave de API de Last.fm integrada en el APK para que el usuario final solo
// tenga que tocar "Autorizar" (flujo web). Se toma de -PlastfmApiKey=... o de
// las variables de entorno LASTFM_API_KEY / LASTFM_API_SECRET al compilar
// (en CI vienen de los secrets del repo). Si faltan, la app pide la clave.
val lastfmApiKey: String =
    (project.findProperty("lastfmApiKey") as String?) ?: System.getenv("LASTFM_API_KEY") ?: ""
val lastfmApiSecret: String =
    (project.findProperty("lastfmApiSecret") as String?) ?: System.getenv("LASTFM_API_SECRET") ?: ""

android {
    namespace = "app.tidalshelf.scrobbler"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.tidalshelf.scrobbler"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"$lastfmApiSecret\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
