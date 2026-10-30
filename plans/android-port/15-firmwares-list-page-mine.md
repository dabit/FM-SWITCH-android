status: pending
# 15 — Firmwares list, firmware page and My firmwares

Depends on: 13

## Why
Choosing a firmware — from the published list or a file of the user's own — and keeping copies on the phone for offline
installs is half of the core flow; the list comes only from the published `firmwares.json`, and the release/build picker,
stars and screenshots are out of scope. Rests on: Firmware list; Scope; Out of scope here as well; Look; Android copy.

## Description
Plan step 14. Replace task 04's placeholders `ui/firmwares/FirmwareListScreen.kt`, `FirmwarePageScreen.kt` and
`MyFirmwaresScreen.kt` (keep their signatures; read state through task 13's app-model hand-off). New UI files go under
`ui/firmwares/`; strings in `strings_firmwares.xml` only. Copy rule as in `PLAN.md` conventions; technical facts belong
behind the Details disclosure (REDESIGN "Plain words", `../FM-SWITCH/REDESIGN.md:24-27`).

**Ports** (`../FM-SWITCH` @ 67ee8c2):
- `App/Standard/Firmwares/FirmwareListView.swift`: search, scope chips, Name/Updated order, summary line, "+ Add a
  firmware file" via SAF `ACTION_OPEN_DOCUMENT` `*/*` then parse (task 05's `FwscPackage`, saved through the library).
- `App/Standard/Firmwares/FirmwarePageView.swift:109-288`: title band, author, description, untested / from-file warning,
  Install / Save to this phone for later / Remove, links in Custom Tabs (androidx.browser), Details disclosure —
  **without** the star button, newer-release note, release choice, build choice and screenshots. An entry with
  `needsBuildChoice` and no saved copy keeps Install and Save disabled, as the source does (`:203`, `:212`), with a
  one-line plain note.
- `App/Standard/Firmwares/MyFirmwaresView.swift`: "Saved on this phone" only (no Starred).
- Badges: port `App/Model/FirmwareBadgeDesign.swift` as pure Kotlin **into `:model`**
  (`model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareBadgeDesign.kt`) and
  `App/Standard/Firmwares/FirmwareBadge.swift` as a composable under `ui/firmwares/`.
- Install hands off to Before you start (task 16) through the `AppNavigator` method task 04 defined.

**Tests** — the Badges block of the Testing Strategy's `:app` section (pure `:model` code, so a `:model` test;
`../FM-SWITCH/AppTests/StandardLookTests.swift:77-116`): `badgeLettersComeFromTheName`, `badgesKeepToTheirNames`,
`badgesComeIn896Designs`, `theOfficialBadgeShowsItsVersion`.

## Files
Every new file goes under one of these paths — nothing at a package root or elsewhere, or the per-task commit
(made by exactly these paths) leaves it out.
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/
- app/src/main/res/values/strings_firmwares.xml
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareBadgeDesign.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareBadgeDesignTest.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
Tasks 14 and 16 build in the same `:app` module beside this one (and task 11 may still be finishing under
`app/src/debug/`): a compile, lint or format error in a path outside this task's `## Files` is a sibling — wait and re-run.

1. `./gradlew :model:test --rerun --tests '*FirmwareBadgeDesignTest'` — expected: `BUILD SUCCESSFUL`; then
   `comm -23 <(printf '%s\n' badgeLettersComeFromTheName badgesKeepToTheirNames badgesComeIn896Designs theOfficialBadgeShowsItsVersion | sort) <(grep -o 'testcase name="[^"]*"' model/build/test-results/test/TEST-*FirmwareBadgeDesignTest.xml | sed 's/testcase name="//;s/"$//' | sort -u)`
   prints nothing.
   red when: a badge case fails or is missing.
   control: add `zzNoSuchCase` to the list → prints `zzNoSuchCase`.
   at base: none — written by this task.
2. Mutation — `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareBadgeDesign.kt`: take the letters from the
   entry id instead of the name; re-run step 1's Gradle command → `FAILED` on `badgeLettersComeFromTheName`; revert,
   re-run → green.
   red when: the badge test can't tell id from name.
3. `./gradlew :app:assembleDebug :app:lintDebug spotlessCheck` — expected: `BUILD SUCCESSFUL`, no issue in this task's
   paths.
   red when: the screens don't build or lint flags them.
4. `grep -rniE 'star|screenshot|release choice|newer release|choose a build' app/src/main/res/values/strings_firmwares.xml`
   — expected: no output except the one-line build-choice note (quote it).
   red when: an out-of-scope control (stars, screenshots, release or build picker, newer-release note) was ported.
   control: `grep -ciE 'star' ../FM-SWITCH/App/Standard/Firmwares/FirmwarePageView.swift` → ≥ `1`.
   at base: none — written by this task.
   compliant hits: the plain note shown when an entry needs a build choice.
5. `grep -rn 'ACTION_OPEN_DOCUMENT\|OpenDocument' app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/; grep -rn 'CustomTabsIntent' app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/`
   — expected: ≥ 1 line each.
   red when: file import doesn't use SAF, or links open some other way.
   at base: none — written by this task.
6. `grep -rniwE 'iPhone|iPad|Safari|Files' app/src/main/res/values/strings_firmwares.xml` — expected: no output.
   red when: iOS words reach the Android copy.
   control: `printf '<string name="x">Open it in Safari</string>\n' | grep -niwE 'iPhone|iPad|Safari|Files'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
