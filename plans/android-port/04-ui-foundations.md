status: pending
# 04 — UI foundations: theme, drawing, navigation, welcome notice and route placeholders

Depends on: 01

## Why
The Standard look's theme, the drawn FM-1, phone navigation and the welcome/safety notices are shared by every screen;
building them, with one placeholder file per route, lets the Home, Firmwares and Install tasks later replace only their
own files without touching the navigation. Rests on: Look; Android copy; Scope.

## Description
Plan step 12. Standard look only (no Retro, no 5-tap easter egg, no sidebar/tablet layout).

**Ports** (`../FM-SWITCH` @ 67ee8c2): `App/Standard/Theme/Palette.swift` and `App/Standard/Theme/Motion.swift`;
`REDESIGN.md:52-97` (motion building blocks) and `:175-186` (colours, 4.5:1 pairs); `App/Standard/FM1Drawing.swift`;
`App/Standard/StandardRootView.swift:21-160` (the `Page` enum at `:21` onwards — phone layout only);
`App/Standard/Home/WelcomeNoticeView.swift` (OK unlocks after 4 s); `App/Views/SafetyNoticeView.swift:4-67` with an
Android variant of the iOS-only lines `:20-27` and `:42-51`. Copy rule (`PLAN.md` conventions): words come from the cited
source string; change only iOS-specific words; never "VM, BTIF, hex, staging or loader" in the main flow
(`../FM-SWITCH/REDESIGN.md:24-27`).

- Palette as plain ARGB `Long`s in a Kotlin object (light/dark pairs as in `Palette.swift:26-31`…), so contrast is
  JVM-testable; Compose `Color`s are derived from it. Material 3 with custom colours, **no dynamic colour**;
  appearance System/Light/Dark as a parameter of the theme composable (task 13 feeds it from settings).
- Navigation (navigation-compose) with one placeholder composable **per route**, each in its own file and each rendering
  a title only: `ui/home/HomeScreen.kt`, `ui/firmwares/FirmwareListScreen.kt`, `ui/firmwares/FirmwarePageScreen.kt`,
  `ui/firmwares/MyFirmwaresScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/install/BeforeYouStartSheet.kt`,
  `ui/install/InstallingOverlay.kt`, `ui/install/OutcomeScreens.kt`. Fix each placeholder's **signature** now (route
  arguments such as the firmware entry id, plus an `AppNavigator` interface in `ui/navigation/` with one method per
  destination and `back()`), because tasks 14, 15 and 16 replace the bodies and must not touch the nav graph. Screens
  will read app state themselves (task 13 provides it); the placeholders take no model parameter.
- The root composable lives in `ui/navigation/AppRoot.kt` (task 13 edits that one file to feed it app state) and shows
  `InstallingOverlay` above the nav host (the placeholder renders nothing; task 13 decides when
  it shows; the overlay itself blocks back with `BackHandler` once task 16 fills it in) and the welcome notice (a pure
  composable with an `onOk` callback; task 13 decides when it shows).
- `MainActivity` (from task 01) hosts the root with the theme.
- Strings this task adds go in `app/src/main/res/values/strings_ui.xml` only.
- **Test** `app/src/test/kotlin/dev/drewr/fmswitch/ui/theme/ContrastTest.kt` — Testing Strategy `:app` section,
  `ContrastTest`: every palette pair the Swift `AppTests/ContrastTests.swift` lists (`:14-17` onwards) is ≥ 4.5:1 for text
  and ≥ 3:1 for graphics, in light and dark. One method per list (`textPairsPassFourAndAHalf`,
  `graphicPairsPassThree`). **reddened by:** lightening `Palette.secondaryInk` below the ratio.

Do not edit any build file, `strings.xml`, or `AndroidManifest.xml` (task 01's rule).

## Files
Every new file goes under one of these paths — nothing at a package root or elsewhere, or the per-task commit
(made by exactly these paths) leaves it out.
- app/src/main/kotlin/dev/drewr/fmswitch/MainActivity.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/theme/
- app/src/main/kotlin/dev/drewr/fmswitch/ui/components/
- app/src/main/kotlin/dev/drewr/fmswitch/ui/navigation/
- app/src/main/kotlin/dev/drewr/fmswitch/ui/FM1Drawing.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/welcome/
- app/src/main/kotlin/dev/drewr/fmswitch/ui/home/HomeScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwareListScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/MyFirmwaresScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/settings/SettingsScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/BeforeYouStartSheet.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/InstallingOverlay.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/OutcomeScreens.kt
- app/src/main/res/values/strings_ui.xml
- app/src/main/res/values/themes.xml
- app/src/test/kotlin/dev/drewr/fmswitch/ui/theme/ContrastTest.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`:app` compiles `:fm1` and `:model`, which tasks 02, 03, 05 and 06 may be writing while this runs: a compile, lint or
format error located in a path **outside** this task's `## Files` is a sibling mid-edit — wait and re-run; only errors in
this task's paths are this task's red.

1. `./gradlew :app:testDebugUnitTest --rerun --tests 'dev.drewr.fmswitch.ui.theme.ContrastTest'` — expected:
   `BUILD SUCCESSFUL`; then `grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-dev.drewr.fmswitch.ui.theme.ContrastTest.xml`
   shows `failures="0"` and `tests` ≥ 2.
   red when: a pair fails its ratio, or the test isn't found.
   at base: none — written by this task.
2. Mutation — `app/src/main/kotlin/dev/drewr/fmswitch/ui/theme/` Palette object, `secondaryInk` light value: set it to
   `0xFFB0B0B0`; re-run step 1 → `FAILED`; revert and re-run → green.
   red when: the contrast test passes with an unreadable secondary ink.
3. `./gradlew :app:assembleDebug :app:lintDebug spotlessCheck` — expected: `BUILD SUCCESSFUL`, lint "No issues found"
   for this task's files.
   red when: a warning, lint issue or format error is reported in this task's paths.
4. `for f in home/HomeScreen firmwares/FirmwareListScreen firmwares/FirmwarePageScreen firmwares/MyFirmwaresScreen settings/SettingsScreen install/BeforeYouStartSheet install/InstallingOverlay install/OutcomeScreens; do test -f "app/src/main/kotlin/dev/drewr/fmswitch/ui/$f.kt" || echo "MISSING $f"; done`
   — expected: no output.
   red when: a route placeholder later tasks replace doesn't exist.
   control: the same loop with `zz/NoSuchScreen` added → prints `MISSING zz/NoSuchScreen`.
   at base: none — written by this task.
5. `grep -rlE 'HomeScreen\(|FirmwareListScreen\(|FirmwarePageScreen\(|MyFirmwaresScreen\(|SettingsScreen\(|InstallingOverlay\(' app/src/main/kotlin/dev/drewr/fmswitch/ui/navigation/`
   — expected: at least one file listed (the nav graph calls the placeholders).
   red when: a placeholder exists but no route reaches it.
   at base: none — written by this task.
6. `grep -rniE 'dynamicColor|dynamicLightColorScheme|dynamicDarkColorScheme' app/src/main/kotlin/dev/drewr/fmswitch/ui/`
   — expected: no output.
   red when: Material You dynamic colour overrides the Standard palette.
   control: `printf 'dynamicLightColorScheme(ctx)\n' | grep -niE 'dynamicColor|dynamicLightColorScheme|dynamicDarkColorScheme'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
7. `grep -rniwE 'iPhone|iPad|Files app|Safari|VM|BTIF|hex|staging|loader' app/src/main/res/values/strings_ui.xml` —
   expected: no output (copy rule).
   red when: an iOS word or a banned technical word reaches the main flow.
   control: `printf '<string name="x">Plug in your iPhone</string>\n' | grep -niwE 'iPhone|iPad|Files app|Safari|VM|BTIF|hex|staging|loader'` → 1 line.
   at base: none — written by this task.
