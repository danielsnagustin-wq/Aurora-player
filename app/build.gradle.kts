// Aurora Player — app/build.gradle.kts (Etapa 1)
// Package neutro: com.aurora.player (sin nombre personal del usuario).
// Motor: Media3 / ExoPlayer. UI: Jetpack Compose + Material 3.
// Persistencia: Room (playlists, favoritos, historial, ajustes).
// Sin permiso de internet: reproductor 100% local.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.aurora.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aurora.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val media3Version = "1.4.1"
    val composeBom = "2024.09.00"
    val roomVersion = "2.6.1"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    // Fase 2 — Spotify Canvas: PlayerView para el vídeo en bucle.
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Cast (Fase 2 app): framework de Google Cast para enviar
    // las pistas online al Chromecast / TV. Versión 21.4.0: la 23.0.0
    // trae mediarouter 1.9.0-beta01 + core 1.17.0, que exigen
    // compileSdk 36/37 y AGP 9.1.0 (run #459, checkDebugAarMetadata);
    // el proyecto compila contra SDK 34 con AGP 8.5.2. La 21.4.0 trae
    // mediarouter 1.6.0-beta01 + core 1.10.1, compatibles, y tiene
    // las mismas APIs que usa la Serie B (verificado con javap).
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
    // Inicio de sesión de YouTube Music con la cuenta del teléfono
    // (Google Sign-In + GoogleAuthUtil, 2026-11-10). PIN 21.2.0:
    // la 22.0.0 ELIMINÓ GoogleSignIn/GoogleSignInClient (verificado
    // en su classes.jar; la 21.2.0 sí los trae) y tumba los runs
    // #490-#493 con Unresolved reference.
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Guava: la necesita el cargador de portadas de la notificación
    // (SettableFuture para el BitmapLoader de Media3).
    implementation("com.google.guava:guava:33.3.1-android")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    // Mockito 5 (mock maker en línea por defecto): para mockear
    // clases de Android (Uri) en los tests JVM, donde son stubs.
    testImplementation("org.mockito:mockito-core:5.11.0")
}
