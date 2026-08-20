
plugins {
    alias(libs.plugins.android.application)
    // alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.registraspese"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.registraspese"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }



    buildFeatures {
        compose = true
    }
}

// Allineiamo il linguaggio per prevenire conflitti con Java
kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.firebase.auth)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    // Nuove librerie ufficiali: Credential Manager per il login
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(libs.googleid)
    // Libreria ufficiale per il database condiviso Firebase Cloud Firestore
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Aggiungi questa riga per far funzionare il comando viewModel()
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // --- LIBRERIE DATABASE ROOM ---
    // AGGIORNAMENTO: Versione 2.8.4 per risolvere il bug della "jvm signature V" con KSP2
    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // Generazione del database tramite KSP
    ksp("androidx.room:room-compiler:$room_version")
    // Libreria ufficiale per le icone estese di Material Design in Jetpack Compose
    implementation("androidx.compose.material:material-icons-extended")
}