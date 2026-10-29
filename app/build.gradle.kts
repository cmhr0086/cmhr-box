import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Service addresses come from -P, env or the git-ignored local.properties only; none are
// committed (see buildSrc EndpointConfiguration). Unset means the feature is off.
val endpointProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
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

        EndpointConfiguration.names.forEach { name ->
            val endpoint = EndpointConfiguration.resolve(
                name,
                providers.gradleProperty(name).orNull,
                providers.environmentVariable(name).orNull,
                endpointProperties.getProperty(name),
            )
            buildConfigField("String", name, EndpointConfiguration.javaString(endpoint))
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Release signing from the git-ignored local.properties only; without it the release APK is unsigned.
    val signingStore = endpointProperties.getProperty("CMHR_SIGNING_STORE_FILE")
    val releaseSigning = signingStore?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storePassword = endpointProperties.getProperty("CMHR_SIGNING_STORE_PASSWORD")
            keyAlias = endpointProperties.getProperty("CMHR_SIGNING_KEY_ALIAS")
            keyPassword = endpointProperties.getProperty("CMHR_SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
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
        // AGP's default ignore list plus tgui's emoji sprites (~5 MB): our text uses system emoji,
        // and tgui's Emoji loader tolerates the missing files.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:<dir>emoji"
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
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
