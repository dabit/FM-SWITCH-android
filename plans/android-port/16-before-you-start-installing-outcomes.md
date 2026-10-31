status: done
# 16 — Before you start, Installing, Done and Problem

Depends on: 13

## Why
Changing firmware and resuming an interrupted install are the core flows: Before you start asks the Standard look's
confirmations (the "I've saved anything I want to keep" tick when switching, an info notice for the same identity) and
the start-up question that protects against the V15-over-other-bootloaders refusal; Installing, Done and Problem follow
the job, with errors in plain words. Rests on: Look; V15-over-other-bootloaders refusal; Errors; Out of scope here as
well; Android copy.

## Description
Plan step 15. Replace task 04's placeholders `ui/install/BeforeYouStartSheet.kt`, `InstallingOverlay.kt` and
`OutcomeScreens.kt` (keep their signatures; read state through task 13's app-model hand-off). New UI files go under
`ui/install/`; strings in `strings_install.xml` only. The enabling logic is task 12's pure `BeforeYouStartState` — call
it, don't re-implement it.

**Ports** (`../FM-SWITCH` @ 67ee8c2):
- `App/Standard/Firmwares/BeforeYouStartView.swift:63-207`: plan header (`:63`), missing-official and untested notices,
  the start-up question (`App/Standard/Firmwares/StartUpViews.swift` + `App/Model/StartUp.swift:15-40,77-101`), status
  notices (`:106-144`), checklist (`:146-176`), button (`:178-207`).
- The confirmations are the **Standard** ones, not Retro's "SWITCH FIRMWARE?" / "REINSTALL OR UPDATE?": the "I've saved
  anything I want to keep" tick when `isSwitching` (`:157-162`) and the same-identity **info notice** (`:129-137`), with
  `flash(steps, confirmsSwitchingFirmware = isSwitching && hasSaved, confirmsSameIdentity = isSameIdentity)`
  (`:186-190`).
- The Mac warning card (`FeluccaUpdateCode.Warning`, `StartUp.swift:77`) keeps its facts — going back to V15 after such a
  firmware needs the Mac app's Backup & rescue — in Android words; its "help" link and the Problem screen's "What can I
  do?" (`App/Standard/Install/InstallOutcomeViews.swift:87,90,98`) are **dropped** (Help is out). "Install MIDIMON",
  "Carry on installing" (`:94`), "Show the details" (`:138`), "Copy the details" (`:144`) stay.
- `App/Standard/Install/InstallingView.swift:52-92` (screen lines per phase) and `InstallOutcomeViews.swift:5-158`
  (Done from `:5`, Problem). On Done the Factory sounds offer is hidden. The Installing screen sets
  `FLAG_KEEP_SCREEN_ON`; the overlay blocks back (`BackHandler`) while a job runs.
- Done means U0's install flows work on the simulated FM-1 (walked in task 17).

## Files
Every new file goes under one of these paths — nothing at a package root or elsewhere, or the per-task commit
(made by exactly these paths) leaves it out.
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/
- app/src/main/res/values/strings_install.xml

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
Tasks 14 and 15 build in the same `:app` module beside this one (and task 11 may still be finishing under
`app/src/debug/`): a compile, lint or format error in a path outside this task's `## Files` is a sibling — wait and re-run.

1. `./gradlew :app:assembleDebug :app:lintDebug spotlessCheck` — expected: `BUILD SUCCESSFUL`, no issue in this task's
   paths.
   red when: the install screens don't build or lint flags them.
2. `grep -niE 'SWITCH FIRMWARE\?|REINSTALL OR UPDATE\?' app/src/main/res/values/strings_install.xml` — expected: no
   output (Retro's dialogs are not the Standard look's).
   red when: Retro's confirmation wording was ported.
   control: `grep -c 'SWITCH FIRMWARE' ../FM-SWITCH/App/Views/FlashConfirmView.swift` → `1`.
   at base: none — written by this task.
   compliant hits: none.
3. `grep -ci 'saved anything I want to keep' app/src/main/res/values/strings_install.xml` — expected: `1` (the Standard
   switch tick; the apostrophe is escaped in XML, so the pattern starts after it).
   red when: the switch confirmation tick is missing.
   at base: none — written by this task (the source has it once: `BeforeYouStartView.swift:160`).
4. `grep -rn 'confirmsSwitchingFirmware\|confirmsSameIdentity' app/src/main/kotlin/dev/drewr/fmswitch/ui/install/` —
   expected: the values passed to `flash` come from task 12's `BeforeYouStartState` (quote the call).
   red when: the screen hard-codes a confirmation or computes it differently from `BeforeYouStartView.swift:186-190`.
   at base: none — written by this task.
5. `grep -rnE 'FLAG_KEEP_SCREEN_ON|keepScreenOn' app/src/main/kotlin/dev/drewr/fmswitch/ui/install/; grep -rn 'BackHandler' app/src/main/kotlin/dev/drewr/fmswitch/ui/install/`
   — expected: ≥ 1 line each.
   red when: the screen can sleep mid-install, or back leaves the install overlay.
   at base: none — written by this task.
6. `grep -niE 'What can I do|Factory sounds|iPhone|iPad|Safari' app/src/main/res/values/strings_install.xml` — expected:
   no output.
   red when: a dropped action or an iOS word reached the install copy.
   control: `grep -c 'What can I do' ../FM-SWITCH/App/Standard/Install/InstallOutcomeViews.swift` → `3`.
   at base: none — written by this task.
   compliant hits: none.
7. `for w in 'Carry on installing' 'Show the details' 'Copy the details'; do grep -qi "$w" app/src/main/res/values/strings_install.xml || echo "MISSING $w"; done`
   — expected: no output.
   red when: an action the plan keeps was dropped ("Install MIDIMON" is built from the entry name at
   `InstallOutcomeViews.swift:113`, so it is checked in U0, not here).
   control: add `zzNoSuchWords` → prints `MISSING zzNoSuchWords`.
   at base: none — written by this task.
