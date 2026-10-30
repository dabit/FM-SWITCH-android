plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

lint {
    warningsAsErrors = true
    abortOnError = true
    checkDependencies = true
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    systemProperty("fm1.v15", rootProject.file("app/src/main/assets/firmware/FM-1_015.fwsc").absolutePath)
}
