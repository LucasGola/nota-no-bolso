plugins {
    // AGP 9 compila Kotlin nativamente: o plugin org.jetbrains.kotlin.android não é mais aplicado.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// No CI, versionCode = número da execução, para que todo APK novo instale por cima do anterior.
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

// Assinatura de release: só configurada quando as variáveis existem (CI com secrets).
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "dev.lucasgola.financas"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.lucasgola.financas"
        minSdk = 26
        targetSdk = 37
        versionCode = ciRunNumber ?: 1
        versionName = "0.1.${ciRunNumber ?: 0}"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.code.scanner)

    testImplementation(libs.junit)
}
