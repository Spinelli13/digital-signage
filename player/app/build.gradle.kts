import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// URL do backend embutida no APK. Pode ser trocada sem recompilar (veja player/README.md), mas definir aqui
// poupa digitar na TV. Ordem: -PserverUrl=... > player/local.properties (signage.serverUrl) > padrão.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val serverUrl: String = (project.findProperty("serverUrl") as String?)
    ?: localProperties.getProperty("signage.serverUrl")
    ?: "http://localhost:8080"

android {
    namespace = "com.digitalsignage.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.digitalsignage.player"
        minSdk = 21
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$serverUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Permite http:// (teste na rede local, ex.: http://192.168.0.10:8080).
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            isMinifyEnabled = false
            // Produção exige https://.
            manifestPlaceholders["usesCleartext"] = "false"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // No android.jar as classes org.json são stubs; nos testes JVM precisamos da implementação real.
    testImplementation("org.json:json:20240303")
}
