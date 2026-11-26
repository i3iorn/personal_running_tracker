plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // OWASP dependency-check: CVE scanning for the dependency tree.
    // Run with: gradle dependencyCheckAnalyze  (first run downloads the NVD database)
    id("org.owasp.dependencycheck") version "12.1.0" apply false
}
