plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cmhr.box"
    compileSdk = 37

    defaultConfig {
        applicationId = property("cmhr.applicationId") as String
        minSdk = 24
        targetSdk = 37
        versionCode = (property("cmhr.versionCode") as String).toInt()
        versionName = property("cmhr.versionName") as String
        missingDimensionStrategy("distribution", "playstore")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Moved from :v2rayng (library modules have no locale config); keep in sync with upstream.
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en",
            "zh-rCN",
            "zh-rTW",
            "vi",
            "ru",
            "fa",
            "ar",
            "az",
            "bn",
            "bqi-rIR"
        )
    }

    packaging {
        jniLibs {
            // Same as upstream: libhevsockstun.so is run as an executable from nativeLibraryDir.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":engine-api"))
    implementation(project(":cmhr-core"))
    implementation(project(":tgui"))
    // Only the DI wiring may reference engine-v2rayng.
    implementation(project(":engine-v2rayng"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
