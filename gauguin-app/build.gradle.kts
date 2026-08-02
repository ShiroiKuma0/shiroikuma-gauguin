
import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("io.github.takahirom.roborazzi")
    alias(libs.plugins.ktlint)
}

// --- shiroikuma fork: per-build version tail ---
// Upstream declares its version in the manifest (android:versionCode / android:versionName), so we
// read it straight from there — the values then flow in automatically on every upstream rebase and
// are never edited by hand. On top of that base:
//     versionName = "<upstreamName>+<N zero-padded to 3>"   e.g. 0.52.0+001
//     versionCode = <upstreamCode> * 10000 + N              e.g. 76 * 10000 + 1 = 760001
// where N = BUILD_NUMBER from gradle.properties (bumped by buildFork, reset to 1 on upstream sync).
val upstreamManifest = file("src/main/AndroidManifest.xml").readText()
val upstreamVersionCode =
    Regex("""android:versionCode="(\d+)"""").find(upstreamManifest)?.groupValues?.get(1)?.toInt()
        ?: error("No android:versionCode in gauguin-app/src/main/AndroidManifest.xml")
val upstreamVersionName =
    Regex("""android:versionName="([^"]+)"""").find(upstreamManifest)?.groupValues?.get(1)
        ?: error("No android:versionName in gauguin-app/src/main/AndroidManifest.xml")
val forkBuildNumber = (project.findProperty("BUILD_NUMBER") as String?)?.trim()?.toIntOrNull() ?: 1
val forkVersionName = "$upstreamVersionName+${forkBuildNumber.toString().padStart(3, '0')}"
val forkVersionCode = upstreamVersionCode * 10000 + forkBuildNumber

val keystoreProperties = Properties()
val keystoreExists = rootProject.file("keystore.properties").exists()

if (keystoreExists) {
    // Create a variable called keystorePropertiesFile, and initialize it to your
    // keystore.properties file, in the rootProject folder.
    val keystorePropertiesFile = rootProject.file("keystore.properties")

    // Load your keystore.properties file into the keystoreProperties object.
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    compileSdkVersion = "android-37"
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // shiroikuma fork: own install identity, so it sits beside stock Gauguin.
        // The code namespace stays org.piepmeyer.gauguin (see `namespace` below) — never rename it.
        applicationId = "shiroikuma.gauguin"
        minSdk = 24
        targetSdk = 37
        versionCode = forkVersionCode
        versionName = forkVersionName
    }

    if (keystoreExists) {
        signingConfigs {
            register("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    applicationVariants.all {
        this.resValue("string", "versionName", this.versionName)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true

        unitTests.all {
            it.useJUnitPlatform {
                if (!project.hasProperty("screenshot")) {
                    excludeTags("org.piepmeyer.gauguin.ScreenshotTest")
                }
            }

            // Do not run out of memory when running Roborazzi tests for different api levels
            it.jvmArgs = listOf("-Xmx2g")

            // Enable running tests in parallel
            if (project.hasProperty("parallel")) {
                it.maxParallelForks = Runtime.getRuntime().availableProcessors() / 2
            }

            // Enable hardware rendering to display shadows and elevation. Still experimental
            // Supported only on API 31+
            it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
        }
    }

    buildTypes {
        release {
            if (keystoreExists) {
                signingConfig = signingConfigs.getByName("release")
            }

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.txt",
            )
            resValue("bool", "debuggable", "false")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("bool", "debuggable", "true")
        }
    }

    buildFeatures {
        viewBinding = true
        resValues = true
    }

    lint {
        disable += listOf("ExpiredTargetSdkVersion", "MissingTranslation")
    }
    namespace = "org.piepmeyer.gauguin"

    androidResources {
        generateLocaleConfig = true
    }
}

repositories {
    google()
    mavenLocal()
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

roborazzi {
    outputDir.set(File("src/test/resources/screenshots"))
}

dependencies {
    implementation(project(":gauguin-core"))
    implementation(project(":gauguin-human-solver"))
    implementation(project(":gauguin-grid-creation-via-merge"))

    implementation(libs.logging.logback.android)

    implementation(libs.koin.android)

    implementation(libs.android.material)

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)

    implementation(libs.thirdparty.konfetti)
    implementation(libs.thirdparty.ferriswheel)
    implementation(libs.thirdparty.navigationdrawer)
    implementation(libs.thirdparty.balloon)
    implementation(libs.thirdparty.vico)
    implementation(libs.thirdparty.androidplot)

    implementation(libs.bundles.koin)

    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)

    // debugImplementation(libs.thirdparty.leakcanary)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.koin.test)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.screenshotTests)
    testImplementation(libs.bundles.androidx.test)

    testImplementation(testFixtures(project(":gauguin-core")))

    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(testFixtures(project(":gauguin-core")))
}

sonarqube {
    properties {
        property("sonar.androidLint.reportPaths", "$projectDir/build/reports/lint-results-debug.xml")
    }
}

// --- shiroikuma fork: one-shot build + deliver + bump ---
// Configuration-cache-safe: every project-derived value is captured HERE (configuration time);
// the doLast lambda touches nothing but plain locals.
tasks.register("buildFork") {
    group = "build"
    description = "Build the signed release APK, copy it to ~/tmp, and bump BUILD_NUMBER for next time."
    dependsOn("assembleRelease")
    val apkName = "shiroikuma-gauguin_$forkVersionName.apk"
    val outputDirProvider = layout.buildDirectory.dir("outputs/apk/release")
    val propsFile = rootProject.file("gradle.properties")
    val versionCode = forkVersionCode
    val nextBuildNumber = forkBuildNumber + 1
    doLast {
        val outputDir = outputDirProvider.get().asFile
        val targetDir = File(System.getProperty("user.home"), "tmp").apply { mkdirs() }
        val apk = outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()
            ?: throw GradleException("No APK found in $outputDir")
        val target = File(targetDir, apkName)
        apk.copyTo(target, overwrite = true)
        println("\u001b[1;36m>>> ${target.absolutePath}\u001b[0m")
        println("\u001b[1;36m>>> versionCode $versionCode\u001b[0m")

        val text = propsFile.readText()
        propsFile.writeText(
            if (Regex("(?m)^BUILD_NUMBER=").containsMatchIn(text)) {
                text.replace(Regex("(?m)^BUILD_NUMBER=.*$"), "BUILD_NUMBER=$nextBuildNumber")
            } else {
                text.trimEnd() + "\n\n# shiroikuma fork: per-build version tail\nBUILD_NUMBER=$nextBuildNumber\n"
            },
        )
        println("\u001b[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber\u001b[0m")
    }
}
