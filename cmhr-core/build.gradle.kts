// Business logic: invite code, subscription, announcement, app control, usage parsing.
// Must not import com.v2ray.ang.*.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cmhr.box.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    api(project(":engine-api"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(cmhrLibs.junit4)
    testImplementation(cmhrLibs.org.json)
    testImplementation(libs.kotlinx.coroutines.core)
}
