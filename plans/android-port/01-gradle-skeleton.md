status: done
# 01 — Gradle skeleton, toolchain, format/lint and the check script

Depends on: None

## Why
Every later task needs a building three-module Gradle project (`:fm1`, `:model`, `:app`, minSdk 26, applicationId
`dev.drewr.fmswitch`) and one local check script that is the project's only gate, since there is no CI. This task
also creates every shared build file with its final content, so no two later tasks ever edit the same one.
Rests on: Platform; applicationId; No CI; Scope.

## Description
Plan step 1 (`PLAN.md` § Implementation Plan, "Step 1"), plus the shared files later tasks would otherwise contend for.

**Create** `settings.gradle.kts` (modules `:fm1`, `:model`, `:app`), root `build.gradle.kts`,
`gradle/libs.versions.toml`, the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), `gradle.properties`,
`.gitignore` (`.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.kotlin/`), `.editorconfig`,
`fm1/build.gradle.kts`, `model/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, a
placeholder `MainActivity` showing one Compose `Text`, `scripts/check.sh`.

- **Versions:** AGP **9.5.0** with Gradle **≥ 9.6.0**. Generate the wrapper with the mise Gradle:
  `mise exec gradle@9.7.1 -- gradle wrapper --gradle-version <≥9.6.0>` (mise has 9.7.1 installed).
  VERIFY-AT-EXECUTION and record in the catalog: Kotlin, Compose BOM, Compose compiler plugin, kotlinx-coroutines
  (+test), kotlinx-serialization, DataStore Preferences, androidx.browser, activity-compose, navigation-compose,
  Material 3, Spotless — latest stable. AGP 9 applies Kotlin to `:app` itself ("built-in Kotlin"): the
  `kotlin("jvm")`, `plugin.serialization` and `plugin.compose` versions must equal the Kotlin version AGP 9.5 brings
  (read it from `./gradlew buildEnvironment`), or configuration fails. Follow
  developer.android.com/build/migrate-to-built-in-kotlin; **do not** apply `org.jetbrains.kotlin.android`.
- **JDK:** Kotlin `jvmToolchain(21)` in all three modules; **no toolchain auto-provisioning** (no foojay).
  `scripts/check.sh` resolves the JDK itself: `JAVA_HOME` if set; else if `mise` exists,
  `export JAVA_HOME="$(mise where java@temurin-21)"`; else fail with "Set JAVA_HOME to a JDK 21." It must not rely on
  `java` on PATH (system java here is 25). `ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"`; fail with a plain message
  if that dir has no `platforms/`. These are properties of this machine.
- **Android:** `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`, `namespace = "dev.drewr.fmswitch"`,
  `applicationId = "dev.drewr.fmswitch"` — written **once**, in `app/build.gradle.kts` (Decision "applicationId",
  confirmed by the user; matches the iOS bundle id at `../FM-SWITCH/project.yml:97`). `buildFeatures { compose = true;
  buildConfig = true }`. Manifest: `<uses-feature android:name="android.software.midi" android:required="true"/>`,
  `INTERNET`, `<application android:name=".FmSwitchApplication" …>` and the launcher `MainActivity`. Task 13 is the
  only later task that edits this manifest (service + permissions).
- **Warnings as errors:** Kotlin `allWarningsAsErrors = true` in all modules; Android lint `warningsAsErrors = true`,
  `abortOnError = true`, `checkDependencies = true`; apply `com.android.lint` to `:fm1` and `:model` so lint analyses
  them (VERIFY-AT-EXECUTION: the plugin id, and that `:app:lintDebug` reports their issues). Formatting: Spotless with
  ktlint over `**/*.kt` and `**/*.kts`.
- **Tests:** JUnit 4 via `kotlin("test-junit")` in all modules + `kotlinx-coroutines-test`. `:fm1`'s and `:model`'s
  `tasks.test` set `systemProperty("fm1.v15", rootProject.file("app/src/main/assets/firmware/FM-1_015.fwsc").absolutePath)`
  (the file arrives in task 02).
- **Every dependency any later task needs, now** — later tasks never edit a build file:
  `:model` → `implementation(project(":fm1"))`, kotlinx-serialization-json (+ the serialization plugin),
  coroutines-core; `:fm1` → coroutines-core; `:app` → `:fm1`, `:model`, Compose BOM + ui + material3 + tooling,
  activity-compose, navigation-compose, DataStore Preferences, androidx.browser, coroutines-android; test deps in all
  three. If a later Executor finds one missing, that is a defect in this task to report, not an edit to make.
- **Shared app files, created here so the tasks that fill them in never collide:**
  - `app/src/main/kotlin/dev/drewr/fmswitch/FmSwitchApplication.kt` — an `Application` whose `onCreate` only calls
    `ProbeHooks.start(this)`. Task 13 extends it with the `AppModel`.
  - `app/src/debug/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt` and `app/src/release/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt`
    — `object ProbeHooks { fun start(context: Context) {}; fun probeIntent(context: Context): Intent? = null }`, both
    no-ops here. Task 11 fills in the debug one (starts the recorder, returns the probe activity's intent); the release
    one stays a no-op forever. Task 14's Settings row "USB MIDI probe" shows only when `probeIntent` is non-null, so it
    needs nothing from task 11. (This replaces plan step 10's "until step 11 exists, step 10 adds a minimal
    `FmSwitchApplication`", which would have put one file in two concurrent tasks.)
  - `app/src/main/res/values/strings.xml` (`app_name` "FM-SWITCH" only) and `app/src/main/res/values/themes.xml` (a
    `Theme.FmSwitch` with no action bar, referenced from the manifest's `android:theme` so lint has no unused resource). Each later task adds its own `strings_<area>.xml`; none edits these two except
    task 04 (themes, if it must).
  - Launcher icon: `app/src/main/res/mipmap-anydpi/ic_launcher.xml` + `app/src/main/res/drawable/ic_launcher_foreground.xml`
    (a plain placeholder vector), so lint has no missing-icon warning.
- **`scripts/check.sh`**, modelled on `../FM-SWITCH/scripts/check.sh` (the `step()` header at `:24`, the group loop and
  unknown-group error at `:138-170`, the closing `Checks passed: …` line at the end of the file): groups `lint` (`spotlessCheck`, `:app:lintDebug`),
  `build` (`:app:assembleDebug` + `:fm1:assemble :model:assemble`), `test` (`:fm1:test :model:test
  :app:testDebugUnitTest`), `device` (`:app:connectedDebugAndroidTest`; fails with a plain message if `adb devices`
  lists none). No arguments = `lint build test`; `device` runs only when named. Human-readable output to stdout is
  fine: nothing captures it. With no test sources yet, the test tasks report NO-SOURCE; that is a pass.
- **Rule for every later task (repeat it in your hand-back):** never edit `settings.gradle.kts`,
  `gradle/libs.versions.toml`, `gradle.properties`, any `build.gradle.kts`, `scripts/check.sh`, `strings.xml` or
  `src/main/AndroidManifest.xml` — except task 13's manifest edit.

## Files
- settings.gradle.kts
- build.gradle.kts
- gradle.properties
- gradle/libs.versions.toml
- gradle/wrapper/gradle-wrapper.jar
- gradle/wrapper/gradle-wrapper.properties
- gradlew
- gradlew.bat
- .gitignore
- .editorconfig
- fm1/build.gradle.kts
- model/build.gradle.kts
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/kotlin/dev/drewr/fmswitch/MainActivity.kt
- app/src/main/kotlin/dev/drewr/fmswitch/FmSwitchApplication.kt
- app/src/debug/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt
- app/src/release/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/values/themes.xml
- app/src/main/res/mipmap-anydpi/ic_launcher.xml
- app/src/main/res/drawable/ic_launcher_foreground.xml
- scripts/check.sh

## Verification
Run from `/home/dabit/git/FM-SWITCH-android`. Steps that call `./gradlew` directly first need
`export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"` — neither is set in this shell and
system `java` is 25 (measured 2026-10-30: `echo $JAVA_HOME` empty, `java -version` → 25.0.4.1).

1. `./gradlew help` — expected: `BUILD SUCCESSFUL`.
   red when: configuration fails (Kotlin/AGP version skew, a `kotlin.android` plugin applied, a missing catalog entry).
2. `env -u JAVA_HOME -u ANDROID_HOME scripts/check.sh; echo "exit=$?"` — expected: the `lint`, `build` and `test`
   step headers, a final `Checks passed: lint build test` line, `exit=0`.
   red when: the script relies on `java` on PATH (25) or on an exported `ANDROID_HOME`, or any group fails or warns.
3. `scripts/check.sh nonsense; echo "exit=$?"` — expected: a line containing `Unknown check group: nonsense`, `exit=1`.
   red when: an unknown group is ignored or silently runs the default groups.
4. Control first: `adb devices | sed 1d | grep -c 'device$'` — expected `0` (at base: `0`; `adb devices` printed only
   `List of devices attached`). Then the arm: `scripts/check.sh device; echo "exit=$?"` — expected: a plain message that
   no device is attached and a non-zero exit, without starting `connectedDebugAndroidTest`.
   red when: `device` passes with nothing attached, or runs although not named (step 2's output must not contain it).
5. `"$HOME/Android/Sdk/build-tools/37.0.0/aapt2" dump packagename app/build/outputs/apk/debug/app-debug.apk` — expected:
   `dev.drewr.fmswitch`.
   red when: the APK carries any other package name.
   at base: none — the APK is written by this task (build-tools 37.0.0 is installed: `ls ~/Android/Sdk/build-tools` →
   `34.0.0 36.0.0 36.1.0 37.0.0`).
6. `grep -rn --include='*.kts' 'applicationId' .` — expected: exactly one hit,
   `./app/build.gradle.kts:<n>:    applicationId = "dev.drewr.fmswitch"`.
   red when: the id is set in two places, or is missing.
   at base: none — no `.kts` file exists (`ls -A` → `.git .jdi AGENTS.md CLAUDE.md doc plans`).
   compliant hits: none.
7. `grep -nE 'android\.software\.midi|android\.permission\.INTERNET|android:name="\.FmSwitchApplication"' app/src/main/AndroidManifest.xml`
   — expected: three lines, one per pattern.
   red when: the MIDI feature, INTERNET or the application class is missing.
   at base: none — written by this task.
8. `grep -rnE 'foojay|toolchainManagement|org\.jetbrains\.kotlin\.android' settings.gradle.kts build.gradle.kts fm1/build.gradle.kts model/build.gradle.kts app/build.gradle.kts gradle/libs.versions.toml`
   — expected: no output.
   red when: toolchain auto-provisioning or the separate Kotlin Android plugin is configured.
   control: `printf 'id("org.gradle.toolchains.foojay-resolver-convention")\nid("org.jetbrains.kotlin.android")\n' | grep -nE 'foojay|toolchainManagement|org\.jetbrains\.kotlin\.android'` → 2 lines.
   at base: none — the files are written by this task.
   compliant hits: none (a comment naming foojay would match — don't write one).
9. `grep -c 'jvmToolchain(21)' fm1/build.gradle.kts model/build.gradle.kts app/build.gradle.kts` and
   `grep -c 'allWarningsAsErrors' fm1/build.gradle.kts model/build.gradle.kts app/build.gradle.kts` — expected: `1` for
   each file in both (or the same settings applied once from the root `build.gradle.kts` to all three — then show that
   block instead and say so).
   red when: a module compiles with another JDK or lets warnings through.
   at base: none — written by this task.
10. `grep -c 'fm1.v15' fm1/build.gradle.kts model/build.gradle.kts` — expected: `1` and `1`.
    red when: `:fm1` or `:model` tests can't find the bundled V15.
    at base: none — written by this task.
11. `git check-ignore local.properties .gradle/x build/x app/build/x .idea/x .kotlin/x x.iml` — expected: all seven
    paths printed.
    red when: any of them would be committed.
12. `grep -n 'fun ' app/src/debug/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt app/src/release/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt`
    — expected: both files declare `start(context: Context)` and `probeIntent(context: Context): Intent?`.
    red when: the two source sets disagree on the API (the release build or task 14 won't compile).
    at base: none — written by this task.
