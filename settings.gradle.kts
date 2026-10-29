pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    versionCatalogs {
        // Upstream v2rayNG catalog, so its build.gradle.kts resolves libs.* unchanged.
        create("libs") {
            from(files("upstream/v2rayNG/V2rayNG/gradle/libs.versions.toml"))
        }
        create("cmhrLibs") {
            from(files("gradle/cmhrLibs.versions.toml"))
        }
    }
}

rootProject.name = "cmhr-box"

include(":app", ":engine-api", ":engine-v2rayng", ":cmhr-core")

// Patched copy produced by scripts/apply-patches.sh; the submodule itself stays untouched.
val patchedV2rayNg = file(".patched/v2rayNG/V2rayNG/app")
check(patchedV2rayNg.resolve("build.gradle.kts").isFile) {
    "Missing $patchedV2rayNg: run scripts/apply-patches.sh"
}
include(":v2rayng")
project(":v2rayng").projectDir = patchedV2rayNg

include(":tgui")
project(":tgui").projectDir = file("upstream/telegram-11-ui/tgui")
