plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.drewr.fmswitch"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.drewr.fmswitch"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
                "OldTargetApi",
            )
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":fm1"))
    implementation(project(":model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
