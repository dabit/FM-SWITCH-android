status: done
# 09 — `:app` shared UI pieces: danger colour and button, flash chip notice, styled descriptions, Settings copy

Depends on: 05

## Why
The list, the firmware page and Before you start share a danger button, an older-flash-chip notice and Markdown-styled
descriptions, and Settings must name mutant.cloud and "The Firmware Hub" as iOS 0.2.1 does. The notice and the
description styling render task 05's `FlashChipWords` and `FirmwareDescription.runs`. This task also edits
`SettingsScreen.kt` and `strings_settings.xml` after task 05's mechanical V16 row. Rests on: D4.

## Description
Implement **PLAN.md › Implementation Plan › Task 09, steps 1–6**, verbatim; they are binding. iOS source:
`/home/dabit/git/FM-SWITCH` @ `fcf4a78`. Read `research-list.md` §1 and §8.

Exact values and copy:
- **Danger colour.** Light `0xA8380A`, dark `0xFF9259` (iOS `App/Standard/Theme/Palette.swift`
  `Shades.danger`). Add `Palette.DANGER_LIGHT`/`DANGER_DARK`, and a `danger` colour in `StandardColors`
  (`ui/theme/FmSwitchTheme.kt`). The plan's step 1 didn't name that file, but `LocalStandardColors` is defined there.
  `ContrastTest.textPairsPassFourAndAHalf` gains the danger-on-card pairs, light and dark (T09-contrast).
- **`DangerButton`** in `ButtonStyles.kt`: text in `danger`, the same shape as `SecondaryButton`.
- **`FlashChipNotice(names: List<String>, hasNewerFlashChip: Boolean)`** in new `ui/firmwares/FlashChipNotice.kt`. Its
  text is `FlashChipWords.warning(names, hasNewerFlashChip)`. When `!hasNewerFlashChip`, a disclosure titled
  `FlashChipWords.HOW_TO_TELL_QUESTION` shows `FlashChipWords.HOW_TO_TELL`. Copy comes from `:model` only, with no
  strings.
- **`annotated(runs: List<FirmwareDescription.Run>): AnnotatedString`** in new `ui/firmwares/DescriptionText.kt`: bold →
  `FontWeight.Bold`, italic → `FontStyle.Italic`, code → `FontFamily.Monospace`.
- **Settings copy** (`strings_settings.xml`). `settings_about_sources` becomes "Firmware lists: thefirmwarehub.com,
  fm1-editor.com, aroum's FM1 Resource Hub and mutant.cloud. Files come from The Firmware Hub, GitHub releases,
  publishers' own sites and M-VAVE." (iOS `StandardSettingsView.swift:130-134`). `settings_verifies` becomes "Go over it
  twice, like Felucca". Each lower-case "firmware hub" in `settings_safety_phone` and `settings_rescue_tools` becomes
  "The Firmware Hub", or "the Firmware Hub" mid-sentence where iOS reads that way. In XML, `'` is `\'`.
- `settings_startup_v16` and `startUpName`'s V16 branch are task 05's. Keep them.

**What later tasks read from this one** (observation 0654): `StandardColors.danger`, `DangerButton`, `FlashChipNotice`
and `annotated`.

**Screens (visual review protocol, orchestrator, on the host, before commit):** Settings, light and dark. Run the
PLAN.md › Conventions › Visual review protocol and read every PNG
(`build/shots/t09-settings-<light|dark>.png`). The Executor can't run an emulator: report this step as not run.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Tasks 03, 04 and 06 run alongside (`:fm1`/`:model`). A compile or lint error whose path is outside this task's
`## Files` comes from a sibling mid-edit: wait and re-run.

## Files
- app/src/main/kotlin/dev/drewr/fmswitch/ui/theme/Palette.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/theme/FmSwitchTheme.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/components/ButtonStyles.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FlashChipNotice.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/DescriptionText.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/settings/SettingsScreen.kt
- app/src/main/res/values/strings_settings.xml
- app/src/test/kotlin/dev/drewr/fmswitch/ui/theme/ContrastTest.kt

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; P=app/build/test-results/testDebugUnitTest`.
Use the helper `tally`, defined exactly as in task 02's Verification setup.
Base: `:app` 15 tests (`ContrastTest` 3), 0 failures (Splitter, `f5b9505`).

1. `./gradlew :app:testDebugUnitTest --rerun` — expected: `BUILD SUCCESSFUL`; `tally $P` shows `tests=15` or more and
   `failures=0 errors=0 skipped=0`.
   red when: the danger pair fails 4.5:1, or `:app` doesn't compile.
   at base: `BUILD SUCCESSFUL`; `tests=15 failures=0 errors=0 skipped=0`.
2. `grep -cE "DANGER_(LIGHT|DARK) = 0xFF(A8380A|FF9259)" app/src/main/kotlin/dev/drewr/fmswitch/ui/theme/Palette.kt; grep -c "DANGER_" app/src/test/kotlin/dev/drewr/fmswitch/ui/theme/ContrastTest.kt`
   — expected: `2`, then ≥ `2`.
   red when: the colour differs from iOS's, or isn't contrast-checked.
   at base: `0`, then `0`.
   compliant hits: none.
3. Mutation T09-contrast. In `app/src/main/kotlin/dev/drewr/fmswitch/ui/theme/Palette.kt`, set `DANGER_LIGHT =
   0xFFFF9259` (fails 4.5:1 on white). Then run `./gradlew :app:testDebugUnitTest --rerun --tests '*ContrastTest'`:
   expected `FAILED` on `textPairsPassFourAndAHalf`. Revert: green.
   red when: the danger pair isn't actually in the contrast harness.
   at base: `textPairsPassFourAndAHalf` passes; there is no danger pair.
4. `grep -c "firmware hub" app/src/main/res/values/strings_settings.xml; grep -c "mutant.cloud" app/src/main/res/values/strings_settings.xml; grep -c '>Go over it twice, like Felucca<' app/src/main/res/values/strings_settings.xml`
   — expected: `0`, `1`, `1`.
   red when: the old lower-case name, the missing source or the old toggle label survived.
   at base: `2`, `0`, `0`.
   compliant hits: none.
5. `grep -n "fun DangerButton\|fun FlashChipNotice\|fun annotated" app/src/main/kotlin/dev/drewr/fmswitch/ui/components/ButtonStyles.kt app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FlashChipNotice.kt app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/DescriptionText.kt`
   — expected: three hits, one per file.
   red when: a symbol tasks 10–12 read is missing or misnamed (observation 0654).
   at base: no output. The two new files don't exist, and `ButtonStyles.kt` has no `DangerButton`.
6. `./gradlew spotlessCheck :app:lintDebug` — expected: `BUILD SUCCESSFUL`.
   red when: a lint warning (warnings are errors) or a formatting violation in this task's paths.
   at base: `BUILD SUCCESSFUL` (`scripts/check.sh` passed at `f5b9505`, 2026-11-09T08:59).
7. Screens (orchestrator, on the host): the visual review protocol for Settings, light and dark. Expected: the About
   text names mutant.cloud and The Firmware Hub; the simulated toggle reads "Go over it twice, like Felucca"; the
   start-up rows include "Changed by V16"; nothing clips in either theme.
   red when: a PNG shows the old copy, clipped text, or illegible contrast.
   at base: no PNG. The Settings screen shows "thefirmwarehub.com, FM-1 Editor and aroum's FM-1 Resource Hub" and
   "Check after writing, like Felucca".
