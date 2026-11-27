import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SenadaShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.haze)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("SenadaDatabase") {
            packageName.set("senada.db")
        }
    }
}

compose.resources {
    packageOfResClass = "senada.resources"
}

android {
    namespace = "senada.platform"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zeeynrs.senada"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        // Akhiran -beta menaruh build rilis di kanal Beta, karena rilis GitHub Senada masih pre-release.
        versionName = "1.0.2-beta"
        ndk.abiFilters += listOf("arm64-v8a", "x86_64")
    }

    buildFeatures {
        // isDebugBuild() membaca BuildConfig.DEBUG untuk menyembunyikan lagu contoh di build rilis.
        buildConfig = true
    }

    // Kunci rilis tetap dari GitHub Secrets (workflow Android release). Tanpa variabelnya, build rilis
    // tidak ditandatangani; kunci yang sama wajib dipakai agar update di aplikasi bisa memasang di atas versi lama.
    val releaseKeystore = System.getenv("SENADA_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SENADA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SENADA_KEY_ALIAS")
                keyPassword = System.getenv("SENADA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
