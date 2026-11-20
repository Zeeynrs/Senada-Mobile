import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
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
            implementation(compose.material)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.resources {
    packageOfResClass = "senada.resources"
}

android {
    namespace = "senada.platform"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.zeeynrs.senada"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk.abiFilters += listOf("arm64-v8a", "x86_64")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
