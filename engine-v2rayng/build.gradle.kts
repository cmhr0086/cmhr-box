// Adapter: the only module allowed to import com.v2ray.ang.*.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cmhr.box.engine.v2rayng"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        missingDimensionStrategy("distribution", "playstore")
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
    implementation(project(":v2rayng"))
    // :v2rayng keeps these as implementation deps; the bootstrap calls them directly.
    implementation(libs.androidx.core.ktx)
    implementation(libs.work.runtime.ktx)
}
