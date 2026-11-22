import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
}

// wynik komendy git, np. liczba commitów; null, jeśli gita nie ma
fun git(vararg args: String): String? = try {
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
} catch (e: Exception) {
    null
}

android {
    namespace = "com.example.paragonik"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.paragonik"
        minSdk = 26
        targetSdk = 37
        // Numer wersji = liczba commitów. Każda wersja po nowym commicie ma większy numer,
        // więc telefon instaluje ją jako aktualizację i nie trzeba o tym pamiętać.
        val commits = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        versionCode = commits
        versionName = "1.$commits"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"https://api-paragonik.oskarwitek.com/\"")
    }

    signingConfigs {
        // Wspólny klucz zespołu zamiast osobnego na każdym komputerze, żeby nowa wersja instalowała się
        // jako aktualizacja. Nie ma go w repo (tak jak google-services.json), każdy trzyma kopię u siebie.
        // Bez pliku build też działa, tylko podpisze się kluczem z tego komputera.
        val teamKey = file("debug.keystore")
        if (teamKey.exists()) {
            getByName("debug") {
                storeFile = teamKey
            }
        }
    }

    buildTypes {
        release {
            // na razie wersje testowe podpisujemy tym samym kluczem; do Google Play trzeba będzie osobnego
            signingConfig = signingConfigs.getByName("debug")
            // R8 wyłączony: z nim aplikacja wywalała się przy starcie (IllegalAccessError w klasach Kotlina)
            optimization {
                enable = false
            }
            // wysyłka do Firebase App Distribution: gradlew assembleRelease appDistributionUploadRelease
            firebaseAppDistribution {
                artifactType = "APK"
                groups = "testerzy" // grupa testerów założona w konsoli Firebase
                releaseNotes = git("log", "-1", "--pretty=%s").orEmpty() // opis zmian = ostatni commit
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.core)
    // OCR na telefonie (model jest w APK, działa offline)
    implementation(libs.mlkit.text.recognition)
    // skaner dokumentów z Google Play services
    implementation(libs.mlkit.document.scanner)
    // backend: REST + JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    // powiadomienia push
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // kolejka próśb bez internetu i zapis sesji
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
