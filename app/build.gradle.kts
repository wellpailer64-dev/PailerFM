import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// keystore.properties + keystore/pailer-release.jks (fora do git, ver .gitignore) ficam
// prontos pra uma assinatura de release "de verdade" no dia que fizer sentido publicar
// (ex.: Play Store). Ate la, a build release assina com a mesma chave de debug (ver
// buildTypes.release abaixo) de proposito, pra instalar por cima da build debug ja em
// uso sem pedir desinstalar e perder dados locais. Ver docs/RELEASE.md.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

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

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DGGML_OPENMP=OFF",
                )
            }
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
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Assinatura da release:
            //  - COM `keystore.properties` (a chave dedicada `pailer-release.jks`): usa ela. E o
            //    caso do CI (o workflow materializa keystore.properties a partir dos secrets) e
            //    da maquina do dev que ja tem o arquivo. APKs assim instalam por cima uns dos
            //    outros sem desinstalar.
            //  - SEM `keystore.properties` (dev novo, sem a chave): cai na chave de debug local,
            //    como sempre foi (ADR-012) - build funciona offline, sem precisar do .jks.
            // Trocar de uma familia de assinatura pra outra num aparelho que ja tem o app exige
            // UM desinstall (assinatura muda) - ver docs/RELEASE.md.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.6.aar"))
    implementation("com.google.guava:guava:33.2.0-android")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
