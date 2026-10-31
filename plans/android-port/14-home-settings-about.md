status: done
# 14 — Home, Settings and About

Depends on: 13

## Why
Home is where the user sees their FM-1 and starts every task (change firmware, my firmwares, finish an interrupted
install, connect), and Settings is where the simulated FM-1, appearance, firmware-list options and the licence notices
live — the Standard look's core flows minus the features this plan defers. Rests on: Look; Scope; Out of scope here
as well; Android copy; Protocol source.

## Description
Plan step 13. Replace task 04's placeholders `ui/home/HomeScreen.kt` and `ui/settings/SettingsScreen.kt` (keep their
signatures; read app state through task 13's app-model hand-off). New files go under `ui/home/` and `ui/settings/`;
strings in `strings_home.xml` and `strings_settings.xml` only. Never edit build files, `strings.xml`, the manifest, the nav
graph or another area's files. Copy rule: words from the cited Swift strings; only iOS-specific words change; never "VM,
BTIF, hex, staging or loader" in the main flow (`../FM-SWITCH/REDESIGN.md:24-27`).

**Ports** (`../FM-SWITCH` @ 67ee8c2):
- `App/Standard/Home/HomeView.swift:84-201` (from `mineCard` at `:84`): the drawing, status pill, Change firmware, My
  firmwares — "N saved" only, **no starred count** (Starred is out); finish card "Finish installing <first package that can
  finish>" (from `FirmwareLibrary.packagesThatCanFinish`); Connect your FM-1 card with the transport error + Try again.
  **Dropped:** Save my FM-1, Factory sounds, Help card.
- `App/Standard/Settings/StandardSettingsView.swift:31-195` (from `appearance` at `:31`): Appearance; firmware-list
  toggles (test versions, without a download, always official first, start-up picker; **no** update notifications);
  Connection: FM-1 over USB / Simulated FM-1 + the six simulated toggles + Reset (`:176-187`); Safety and recovery (task
  04's safety notice); About with the FM1Kit, M-Flasher and V15 notices read from `assets/licences/` and the V15 licence
  from `assets/firmware/FM-1_015-LICENCE.txt` (task 02). The firmwares/backups location row is dropped (app storage).
- In debug builds a "USB MIDI probe" row opens `ProbeHooks.probeIntent(context)` (task 01's API) — show the row only when
  it returns non-null; this task does not depend on task 11.
- Done means U0's Home and Settings items work on the simulated FM-1 (walked in task 17).

## Files
Every new file goes under one of these paths — nothing at a package root or elsewhere, or the per-task commit
(made by exactly these paths) leaves it out.
- app/src/main/kotlin/dev/drewr/fmswitch/ui/home/
- app/src/main/kotlin/dev/drewr/fmswitch/ui/settings/
- app/src/main/res/values/strings_home.xml
- app/src/main/res/values/strings_settings.xml

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
Tasks 15 and 16 build in the same `:app` module beside this one (and task 11 may still be finishing under
`app/src/debug/`): a compile, lint or format error in a path outside this task's `## Files` is a sibling — wait and re-run;
only errors under `ui/home/`, `ui/settings/` or this task's two strings files are this task's red.

1. `./gradlew :app:assembleDebug :app:lintDebug spotlessCheck` — expected: `BUILD SUCCESSFUL`, no issue in this task's
   paths.
   red when: the screens don't build, or lint flags them.
2. `grep -niwE 'iPhone|iPad|Safari|Mac|VM|BTIF|hex|staging|loader|starred|tip' app/src/main/res/values/strings_home.xml app/src/main/res/values/strings_settings.xml`
   — expected: no output, except Mac in strings that state a Mac-only feature plainly (`SafetyNoticeView.swift:42-51`
   style) — list any such hit in your report.
   red when: an iOS word, a banned technical word or a dropped feature (Starred, tip jar) reaches the copy.
   control: `printf '<string name="x">Starred firmwares</string>\n' | grep -niwE 'iPhone|iPad|Safari|Mac|VM|BTIF|hex|staging|loader|starred|tip'` → 1 line.
   at base: none — written by this task.
   compliant hits: a plainly worded Mac-only statement, if the source has one here.
3. `grep -rniE 'Save my FM-1|Factory sounds|>Help<|update notification' app/src/main/res/values/strings_home.xml app/src/main/res/values/strings_settings.xml`
   — expected: no output (dropped by Decision).
   red when: an out-of-scope card or setting was ported.
   control: `grep -ciE 'Factory sounds|Save my FM-1' ../FM-SWITCH/App/Standard/Home/HomeView.swift` → `2` (measured
   2026-10-30: the source has them, this port must not).
   at base: none — written by this task.
   compliant hits: none (`>Help<` matches only a string that is exactly "Help").
4. `grep -rn 'probeIntent' app/src/main/kotlin/dev/drewr/fmswitch/ui/settings/; grep -rnE 'licences/(FM1Kit|M-Flasher)-LICENSE\.txt|FM-1_015-LICENCE\.txt' app/src/main/kotlin/dev/drewr/fmswitch/ui/settings/`
   — expected: ≥ 1 line, then lines naming all three notice files (or one directory listing of `licences/` plus the V15
   licence — quote it).
   red when: the probe row is wired another way, or About doesn't show a notice the licences require.
   at base: none — written by this task.
