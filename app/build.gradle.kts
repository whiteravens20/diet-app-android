// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "net.whiteravens.dietapp"
    // androidx.core 1.19 / compose-bom 2026.05 require compileSdk >= 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "net.whiteravens.dietapp"
        minSdk = 29
        targetSdk = 37
        // A build handed out names its version and counts it: `-PappVersionName` and
        // `-PappVersionCode`, which a later build of the same application must raise.
        versionCode = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("appVersionName").getOrElse("0.0.0")
        // The version of diet-app's API this build was checked against (contract/API_VERSION):
        // a server that answers another one is named on the screen.
        val checkedApiVersion = providers
            .fileContents(rootProject.layout.projectDirectory.file("contract/API_VERSION"))
            .asText.get().trim()
        buildConfigField("String", "API_VERSION", "\"$checkedApiVersion\"")

        // The screens' tests run against a diet-app instance at the debug build's address
        // (.github/workflows/test.yml starts one for them), each test on an app with
        // no data of its own.
        testInstrumentationRunner = "net.whiteravens.dietapp.testing.DietAppTestRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        // The screenshots the tests keep are written through the test storage service, which the
        // plugin lets write to the device's storage, and copies off it after the run, only when asked.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }

    // The schemas of the cache's versions: the screens' tests open a cache of an older version as the
    // app migrates it.
    sourceSets.getByName("androidTest").assets.srcDir("schemas")

    // The application speaks English and Polish: the libraries' texts in other languages stay out of
    // the package, so that a phone in another language shows the libraries' English beside the app's.
    androidResources {
        localeFilters += listOf("en", "pl")
    }

    // The server a build starts with: the emulator's host in a debug build, and none in a release
    // build, whose first screen asks for one. `-PapiBaseUrl=<address>` gives another, such as a
    // backend on a phone's own network: http://<host-lan-ip>:4000/api/.
    // `PLAIN_HTTP`: whether the build talks plain http as well, which a backend on a developer's
    // own network or the test instance has; a release build talks HTTPS only.
    val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
    val testInstance = apiBaseUrl.getOrElse("http://10.0.2.2:4000/api/")
    // Where a build handed out asks for its newest version (`UPDATES_URL`, a version.json the
    // workflow that builds the APK writes) and the addresses it may download a build from
    // (`UPDATES_FROM`). A build for development and tests asks nowhere.
    val releases = "https://github.com/whiteravens20/diet-app-android/releases/"
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$testInstance\"")
            buildConfigField("boolean", "PLAIN_HTTP", "true")
            buildConfigField("String", "UPDATES_URL", "\"\"")
            buildConfigField("String", "UPDATES_FROM", "\"\"")
        }
        release {
            isMinifyEnabled = true
            // The resources nothing reads leave the package with the code nothing calls;
            // res/raw/keep.xml keeps what is read by its name.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.getOrElse("")}\"")
            buildConfigField("boolean", "PLAIN_HTTP", "false")
            buildConfigField("String", "UPDATES_URL", "\"${releases}latest/download/version.json\"")
            buildConfigField("String", "UPDATES_FROM", "\"$releases\"")
        }
        // The release build as R8 shrinks it, signed with the debug key and pointed at the test
        // instance over plain http, for the screens' tests to run on: what R8 takes out of a
        // release shows there rather than on a tester's phone. It keeps what the tests call.
        create("minified") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            proguardFile("proguard-minified.pro")
            testProguardFile("proguard-minified-test.pro")
            buildConfigField("String", "API_BASE_URL", "\"$testInstance\"")
            buildConfigField("boolean", "PLAIN_HTTP", "true")
            buildConfigField("String", "UPDATES_URL", "\"\"")
            buildConfigField("String", "UPDATES_FROM", "\"\"")
        }
    }
    // `-Pedge` makes the release build Diet App Edge, the build the testers try: an application of
    // its own beside the release, named so on the phone and signed with the edge key, which the
    // workflow gives the build in EDGE_KEYSTORE and EDGE_KEYSTORE_PASSWORD (.github/workflows/edge-apk.yml).
    // It is set after the build types, so that the minified build does not take it from the release.
    if (providers.gradleProperty("edge").isPresent) {
        val password = providers.environmentVariable("EDGE_KEYSTORE_PASSWORD").get()
        signingConfigs.create("edge") {
            storeFile = file(providers.environmentVariable("EDGE_KEYSTORE").get())
            storePassword = password
            keyAlias = "edge"
            keyPassword = password
        }
        val pages = "https://whiteravens20.github.io/diet-app-android/"
        buildTypes.getByName("release") {
            applicationIdSuffix = ".edge"
            signingConfig = signingConfigs.getByName("edge")
            buildConfigField("String", "UPDATES_URL", "\"${pages}version.json\"")
            buildConfigField("String", "UPDATES_FROM", "\"$pages\"")
        }
        sourceSets.getByName("release").res.srcDir("src/edge/res")
    }
    // The build type the screens' tests run on: `-PtestBuildType=minified` for the shrunk one.
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")

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
 * the copy of its list of codes (contract/errors.json, beside the contract): string resources named
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
        @Suppress("UNCHECKED_CAST")
        val list = groovy.json.JsonSlurper().parse(codes.get().asFile) as Map<String, Map<String, Any?>>
        val rows = list.map { (code, entry) ->
            val messages = entry["messages"] as Map<String, String?>
            fun text(language: String) = checkNotNull(messages[language]) { "contract/errors.json: $code has no text in $language" }
            Triple(code, text("en"), text("pl"))
        }
        check(rows.isNotEmpty()) { "contract/errors.json lists no code" }
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
                    appendLine("<!-- Written by the build from contract/errors.json. -->")
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
                appendLine("// Written by the build from contract/errors.json.")
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
    codes.set(rootProject.layout.projectDirectory.file("contract/errors.json"))
}

/**
 * Where Room writes the schema of each version of the cache's database, kept in the repository: a
 * migration between two versions is generated, and checked, from their two schemas.
 */
class RoomSchemas(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val directory: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("room.schemaLocation=${directory.path}")
}

ksp { arg(RoomSchemas(file("schemas"))) }

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(writeErrorTexts, WriteErrorTexts::resources)
        variant.sources.kotlin?.addGeneratedSourceDirectory(writeErrorTexts, WriteErrorTexts::kotlin)
    }
}

// The tools the Android plugin runs bring libraries with known vulnerabilities: its test platform
// (netty, Commons Lang, Guava), Lint (Bouncy Castle, HttpClient, Commons Lang, Guava) and the
// annotation processors (Guava). They are lifted to the releases that fix them. None of them is in
// the app.
configurations.matching {
    it.name.startsWith("unified-test-platform") || it.name.startsWith("androidLint") ||
        it.name.contains("ksp", ignoreCase = true) || it.name.startsWith("hiltAnnotationProcessor")
}.configureEach {
    resolutionStrategy.eachDependency {
        val fixed = when {
            requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true &&
                !requested.name.startsWith("netty-tcnative") -> "4.1.139.Final"
            requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> "1.86"
            requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> "4.5.14"
            requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> "3.18.0"
            requested.group == "com.google.guava" && requested.name == "guava" -> "33.7.2-jre"
            else -> null
        }
        if (fixed != null) useVersion(fixed)
    }
}

// The libraries the release ships and their licences, for the legal notices, read from the build's own
// dependency graph as it builds. Nothing is asked of the network: the texts of the licences are in
// aboutlibraries/licenses, so that a build repeats and fetches nothing the verification does not cover.
aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("aboutlibraries")
        filterVariants.add("release")
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
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    // QR codes drawn on the phone, for an authenticator app on another device.
    implementation(libs.zxing.core)

    // The libraries' licences, listed for the legal notices.
    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.services.storage)
    // Answers a window of the platform's, such as where to keep a file, as a person would.
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "minifiedImplementation"(libs.androidx.compose.ui.test.manifest)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)

    constraints {
        // Compose's test library brings Espresso 3.5.0, which finds InputManager.getInstance by
        // reflection; Android 17 no longer has it. 3.7.0 asks the system for the service instead.
        androidTestImplementation(libs.androidx.test.espresso.core)
    }
}
