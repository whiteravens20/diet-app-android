// Root build file — plugins are declared here and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    // Kotlin itself is built into AGP 9 — no standalone kotlin.android plugin.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
