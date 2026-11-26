import java.io.File

plugins {
    id("com.android.application") version "9.5.0"
    id("com.google.devtools.ksp") version "2.3.12"
    // CVE scan of the dependency tree: gradle dependencyCheckAnalyze
    // NOTE: run with --no-configuration-cache — the OWASP plugin uses
    // Task.project at execution time, which the configuration cache forbids.
    id("org.owasp.dependencycheck") version "12.1.0"
}

dependencyCheck {
    // Scan only what the APK actually ships (runtime classpaths). Scanning
    // build-time configurations produced endless filename-heuristic false
    // positives on AGP-embedded Kotlin tooling that never ships.
    scanConfigurations = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    analyzers {
        assemblyEnabled = false
    }
    failBuildOnCVSS = 9.0f
    failOnError = false
    suppressionFile = rootProject.file("owasp-suppressions.xml").absolutePath
    // Free key from https://nvd.nist.gov/developers/request-an-api-key
    // Set via environment; never hardcode it.
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
}


// kotlin-stdlib-jdk7/jdk8 are empty compatibility stubs since Kotlin 1.8
// (their content merged into kotlin-stdlib). Exclude the legacy empty jars —
// they trip CVE version-range heuristics without shipping any code.
configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

android {
    namespace = "dev.schrammel.personal_running_tracker"

    compileSdk = 37

    defaultConfig {
        applicationId = "dev.schrammel.personal_running_tracker"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
                versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = File(System.getenv("RELEASE_KEYSTORE_FILE") ?: "release.keystore")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "personal_running_tracker"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "do-not-leak"
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "do-not-leak"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.maplibre.gl:android-sdk:13.8.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.robolectric:robolectric:4.18")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
