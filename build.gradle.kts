// AGP and Kotlin versions come from the upstream v2rayNG catalog for the whole build.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    dependencies {
        // Applied by :v2rayng as id("com.jaredsburrows.license").
        classpath(libs.gradle.license.plugin)
    }
}
