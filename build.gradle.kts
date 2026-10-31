plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        // Compose functions are UpperCamelCase (Compose lint requires it); set in .editorconfig.
        ktlint().setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint()
    }
}
