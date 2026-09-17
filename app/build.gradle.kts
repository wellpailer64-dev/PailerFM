import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// keystore.properties + keystore/pailer-release.jks (fora do git, ver .gitignore) - chave de
// release dedicada, usada pela build release tanto local quanto no CI (ver buildTypes.release
// abaixo e ADR-032 em docs/DECISIONS.md). Ate 15/09/2026 a build local assinava com a chave de
// debug de proposito (ADR-012) pra nunca pedir desinstalar no aparelho de teste - mas isso deixava
// a build local com uma assinatura DIFERENTE da que o CI publica (push/tag na master, ver
// build-apk.yml), entao quem recebia um APK de um lado e depois do outro (ex.: um amigo pra quem o
// app e distribuido) caia em "nao consegue atualizar, precisa desinstalar" toda vez. ADR-032
// unificou as duas em uma so - custo de UM desinstalar na migracao, resolvido depois disso.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// CI (ver .github/workflows/build-apk.yml) nao tem keystore.properties (fora do git de
// proposito) - materializa o .jks decodificado do secret RELEASE_KEYSTORE_BASE64 num caminho
// fixo dentro do checkout e passa senha/alias via env var, so em push/tag/workflow_dispatch
// (nunca em pull_request, pra chave real nao ficar exposta num build de PR). RELEASE_KEYSTORE_PATH
// vazio/ausente localmente = cai no bloco keystoreProperties acima, comportamento inalterado.
val releaseKeystorePathFromEnv = System.getenv("RELEASE_KEYSTORE_PATH")
val hasCiSigningConfig = !releaseKeystorePathFromEnv.isNullOrBlank()

android {
    namespace = "com.pailer.localtune"
    compileSdk = 34
    // Fixado pra CI e maquina local usarem o mesmo NDK (ver CLAUDE.md / .github/workflows/build-apk.yml).
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.pailer.localtune"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"

        // Tag da release do GitHub que gerou ESSE build (ver build-apk.yml, "Calcula tag da
        // build" - roda ANTES do assembleRelease e passa por essa env var) - e o que
        // UpdateCheckRepository compara contra a ultima release publicada pra saber se tem
        // atualizacao disponivel. "local-dev" (builds locais, sem CI) desliga a checagem sozinho -
        // ver LocalTuneViewModel.checkForUpdate.
        buildConfigField("String", "RELEASE_TAG", "\"${System.getenv("APP_RELEASE_TAG") ?: "local-dev"}\"")

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        } else if (hasCiSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePathFromEnv!!)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Chave de release dedicada sempre que uma fonte dela existir - local
            // (keystore.properties, so na maquina que tem o arquivo) OU CI (secrets do repo,
            // so fora de pull_request - ver build-apk.yml). So cai pra "debug" quando NENHUMA
            // das duas existe (ex.: clonando o repo sem keystore.properties nem secrets) - ver
            // ADR-032 em docs/DECISIONS.md pro porque de unificar em vez de manter a build local
            // na chave de debug (ADR-012, superada).
            signingConfig = if (keystorePropertiesFile.exists() || hasCiSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Cast pra TV (Chromecast/Google TV) - receptor padrao do Google, sem app de TV proprio.
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // O dialogo de selecao de dispositivo do Cast (MediaRouteButton/CastButtonFactory) exige uma
    // FragmentActivity - MainActivity precisou trocar de ComponentActivity puro pra isso.
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    // MediaRouteChooserDialog e um AppCompatDialog - exige que o tema do app herde de
    // Theme.AppCompat (ver AppTheme em styles.xml), senao quebra ao abrir o seletor de Cast.
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    implementation("com.google.guava:guava:33.2.0-android")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
