// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.whiteravens.dietapp"
    // androidx.core 1.19 / compose-bom 2026.05 require compileSdk >= 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "net.whiteravens.dietapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.0"
        // Backend base URL. Default targets the emulator's host loopback; for a
        // physical device pass -PapiBaseUrl=http://<host-lan-ip>:4000/api/ (or
        // set apiBaseUrl in ~/.gradle/gradle.properties).
        val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
            .getOrElse("http://10.0.2.2:4000/api/")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        // The version of diet-app's API this build was checked against (contract/API_VERSION):
        // a server that answers another one is named on the screen.
        val checkedApiVersion = providers
            .fileContents(rootProject.layout.projectDirectory.file("contract/API_VERSION"))
            .asText.get().trim()
        buildConfigField("String", "API_VERSION", "\"$checkedApiVersion\"")

        // The screens' tests run against a diet-app instance at that address
        // (.github/workflows/test.yml starts one for them), each test on an app with
        // no data of its own.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // The tests run on the JVM: an Android call they do not stand in for answers its default.
        unitTests.isReturnDefaultValues = true
        // Every test of the screens in an instrumentation of its own, so that clearPackageData
        // can wipe what the one before it left.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

// The copy of the API's contract the client is checked against (contract/README.md);
// `-PcontractDir=<dir>` checks it against another, as the weekly comparison does.
val contract = rootProject.layout.projectDirectory.dir(providers.gradleProperty("contractDir").getOrElse("contract"))

tasks.withType<Test>().configureEach {
    // A test that waits for something that never comes ends the build instead of holding it.
    timeout.set(Duration.ofMinutes(10))
    inputs.dir(contract)
    systemProperty("contract.dir", contract.asFile.absolutePath)
}

// Compiled for Java 17 by whichever JDK runs Gradle (17 or newer): no toolchain, so
// nothing is downloaded that the build has not pinned.
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

/**
 * Writes the API's own words for every code it refuses a request with, in English and Polish, from
 * the copy of its list of codes (contract/errors.md, beside the contract): string resources named
 * `error_code_<code>` and a map from each code to its text, which `errorMessage` reads.
 */
abstract class WriteErrorTexts : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val codes: RegularFileProperty

    @get:OutputDirectory
    abstract val resources: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlin: DirectoryProperty

    @TaskAction
    fun write() {
        val rows = codes.get().asFile.readLines().filter { it.startsWith("| `") }.map { line ->
            val cells = line.split("|").map(String::trim)
            check(cells.size == 6) { "contract/errors.md: a row of other than four cells: $line" }
            Triple(cells[1].trim('`'), cells[3], cells[4])
        }
        check(rows.isNotEmpty()) { "contract/errors.md lists no code" }
        fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
            .let { if (it.startsWith("@") || it.startsWith("?")) "\\$it" else it }
        fun name(code: String) = "error_code_" + code.lowercase()
        for ((folder, column) in listOf("values" to 1, "values-pl" to 2)) {
            val file = resources.get().dir(folder).file("errors.xml").asFile
            file.parentFile.mkdirs()
            file.writeText(
                buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                    appendLine("<!-- Written by the build from contract/errors.md. -->")
                    appendLine("<resources>")
                    rows.forEach { row ->
                        val text = if (column == 1) row.second else row.third
                        appendLine("    <string name=\"${name(row.first)}\" formatted=\"false\">${xml(text)}</string>")
                    }
                    appendLine("</resources>")
                },
            )
        }
        val source = kotlin.get().file("net/whiteravens/dietapp/ui/common/ErrorTexts.kt").asFile
        source.parentFile.mkdirs()
        source.writeText(
            buildString {
                appendLine("// Written by the build from contract/errors.md.")
                appendLine("package net.whiteravens.dietapp.ui.common")
                appendLine()
                appendLine("import net.whiteravens.dietapp.R")
                appendLine()
                appendLine("/** The text of every code the API refuses a request with, in the API's own words. */")
                appendLine("internal val ERROR_TEXTS: Map<String, Int> = mapOf(")
                rows.forEach { row -> appendLine("    \"${row.first}\" to R.string.${name(row.first)},") }
                appendLine(")")
            },
        )
    }
}

val writeErrorTexts = tasks.register<WriteErrorTexts>("writeErrorTexts") {
    codes.set(rootProject.layout.projectDirectory.file("contract/errors.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(writeErrorTexts, WriteErrorTexts::resources)
        variant.sources.kotlin?.addGeneratedSourceDirectory(writeErrorTexts, WriteErrorTexts::kotlin)
    }
}

// Every version the build resolves, transitive ones included, is written in gradle.lockfile:
// a build that would resolve another fails, and a change to one shows in the pull request.
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
