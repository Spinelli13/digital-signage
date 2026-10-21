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

// Onde o Player procura versões novas de si mesmo (latest.json do site de downloads).
val updateUrl: String = (project.findProperty("updateUrl") as String?)
    ?: localProperties.getProperty("signage.updateUrl")
    ?: "https://signage-downloads.pages.dev/latest.json"

// Assinatura de release. A chave NUNCA vai para o repositório: fica em player/keystore.properties (ignorado
// pelo git) ou nas variáveis SIGNAGE_KEYSTORE_*. Todas as versões precisam ser assinadas com a MESMA chave,
// senão o Android recusa a atualização por cima de uma versão já instalada.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(property: String, env: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(env)

val releaseStoreFile = signingValue("storeFile", "SIGNAGE_KEYSTORE_FILE")
val hasReleaseKey = releaseStoreFile != null && file(releaseStoreFile).exists()

android {
    namespace = "com.digitalsignage.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.digitalsignage.player"
        minSdk = 21
        targetSdk = 36
        // O script/CI de release passa -PversionCode/-PversionName (ver scripts/release-player.mjs). Em desenvolvimento
        // fica 1 / 0.0.0-dev (e a atualização automática vem desligada nas builds debug, ver Settings).
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.0.0-dev"

        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "DEFAULT_UPDATE_URL", "\"$updateUrl\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "SIGNAGE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNAGE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNAGE_KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        // Os modelos dos widgets (frontend/public/widgets) são os MESMOS da pré-visualização do painel.
        getByName("main") {
            assets.srcDir("../../frontend/public/widgets")
        }
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
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    // No android.jar as classes org.json são stubs; nos testes JVM precisamos da implementação real.
    testImplementation("org.json:json:20240303")
}
