// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

// The versions the plugins bring with them are locked as well, in buildscript-gradle.lockfile.
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
        // Libraries the plugins bring that have known vulnerabilities are lifted to the releases
        // that fix them. None of them is in the app.
        resolutionStrategy.eachDependency {
            val fixed = when ("${requested.group}:${requested.name}") {
                "org.bouncycastle:bcprov-jdk18on", "org.bouncycastle:bcpkix-jdk18on", "org.bouncycastle:bcutil-jdk18on" -> "1.86"
                "org.jdom:jdom2" -> "2.0.6.1"
                "org.bitbucket.b_c:jose4j" -> "0.9.6"
                "org.apache.commons:commons-lang3" -> "3.18.0"
                "com.google.guava:guava" -> "33.7.2-jre"
                else -> null
            }
            if (fixed != null) useVersion(fixed)
        }
    }
}

// Root build file — plugins are declared here and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    // Kotlin itself is built into AGP 9 — no standalone kotlin.android plugin.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}
