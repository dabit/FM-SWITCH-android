status: done
# 11 — `:app` firmware page: styled description, older-chip notice, Installs and Saves on, saved versions

Depends on: 05, 06, 08, 09, 10

## Why
The firmware page must match iOS 0.2.1:
- the description read as Markdown;
- the older-flash-chip notice, unless the FM-1 says it has the first chip;
- the "Installs" and "Saves on" details;
- a switcher between saved releases, with a danger "Delete this saved version".

It reads task 05's `installs`/`flashChip`, task 06's `savedCopies`, task 08's `DeviceController.flashChipId`, and task
09's `FlashChipNotice`, `DangerButton` and `annotated`. The dependency on task 10 exists **only** because both edit
`strings_firmwares.xml`, which lint (`UnusedResources`) makes unsafe to edit concurrently. Rests on: D4.

## Description
Implement **PLAN.md › Implementation Plan › Task 11, steps 1–3**, verbatim; they are binding. It ports iOS
`App/Standard/Firmwares/FirmwarePageView.swift` and `FirmwarePageView+Saving.swift:6-70` @ `fcf4a78`. Read
`research-list.md` "UI copy (iOS, exact)".

- **New strings go in a new `strings_firmware_page.xml`** (this task owns it). They include:
  - "Installs"/"%s on The Firmware Hub", with the count formatted by `NumberFormat.getIntegerInstance()`;
  - "Saves on", with its value from `HubFlashChip.savesOn`;
  - "Saved, ready for offline install";
  - "Delete this saved version";
  - the version-suffixed install and save labels;
  - "Newest".
- **In `strings_firmwares.xml`** (after task 10): `firmware_untested_warning` becomes the iOS page text, "In testing: not
  yet known to work on a real FM-1. It's only listed in aroum's FM1 Resource Hub or on mutant.cloud, not on The
  Firmware Hub or fm1-editor.com." (`research-list.md` "Page untested notice"; XML apostrophes `\'`). Delete
  `firmware_install`, `firmware_save_later` and `firmware_remove` if the new labels and the danger button replace them.
  Android keeps "phone" in the save label.
- The chip notice shows after the untested notice when `entry.flashChip == HubFlashChip.Old && flashChipId !=
  Fm1FlashChip.P25Q80`.

**Symbols read** (if one is missing, stop and report): `CatalogueEntry.installs`/`flashChip`, `HubFlashChip.savesOn`,
`FirmwareDescription.runs` (05); `FirmwareLibrary.savedCopies`/`remove` (06); `DeviceController.flashChipId` (08);
`FlashChipNotice`, `DangerButton`, `annotated`, `StandardColors.danger` (09); `Fm1FlashChip.P25Q80` (02).

**Screens (visual review protocol, orchestrator, on the host, before commit):** each in light and dark:
- an `old`-chip entry, with the notice and "How would I know?" open;
- an entry with the Installs and Saves on rows;
- an entry with two saved versions (the control and the danger button);
- the `aisling` entry (bold text).

The PNGs are `build/shots/t11-<screen>-<light|dark>.png`. The Executor can't run an emulator: report this step as not
run.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Tasks 12 (install sheet and outcomes) and 13 (docs) run alongside. A compile or lint error whose path is outside this
task's `## Files` comes from a sibling mid-edit: wait and re-run.

## Files
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageSaving.kt
- app/src/main/res/values/strings_firmware_page.xml
- app/src/main/res/values/strings_firmwares.xml

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.

1. `./gradlew :app:compileDebugKotlin` — expected: `BUILD SUCCESSFUL`, with no warnings.
   red when: a producer symbol is missing, or a deprecated or experimental API was used.
   at base: `BUILD SUCCESSFUL`.
2. `grep -n "R.string.firmware_remove" app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageScreen.kt app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageSaving.kt; grep -c "DangerButton(" app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageScreen.kt app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageSaving.kt`
   — expected: no output from the first; ≥ `1` in total from the second.
   red when: "Remove" survived, or delete isn't the danger button.
   control: `grep -n "fun DangerButton" app/src/main/kotlin/dev/drewr/fmswitch/ui/components/ButtonStyles.kt` prints one
   line (task 09).
   at base: one hit, `Text(stringResource(R.string.firmware_remove))`; no `DangerButton` anywhere.
   compliant hits: none.
3. `grep -c "FlashChipNotice(" app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageScreen.kt; grep -c "flashChipId" app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwarePageScreen.kt`
   — expected: ≥ `1` each.
   red when: the page shows no chip notice, or shows it without consulting the FM-1's reported chip.
   at base: `0` and `0`.
   compliant hits: none.
4. `for s in "Delete this saved version" "Saved, ready for offline install" "on The Firmware Hub" ">Saves on<" ">Newest<"; do grep -cF "$s" app/src/main/res/values/strings_firmware_page.xml; done; grep -cF "or on mutant.cloud, not on The Firmware Hub or fm1-editor.com." app/src/main/res/values/strings_firmwares.xml`
   — expected: `1` for each, and `1` for the untested text.
   red when: page copy is missing or differs from iOS's.
   at base: the first file doesn't exist; the untested text reads "…not on the firmware hub or the FM-1 editor website."
   (`0`).
5. `./gradlew spotlessCheck :app:lintDebug` — expected: `BUILD SUCCESSFUL`.
   red when: a string is orphaned (for example a `firmware_install` still defined but unused) or a lint warning sits in
   this task's paths.
   at base: `BUILD SUCCESSFUL`.
6. Screens (orchestrator, on the host): the visual review protocol for the screens in the Description. Expected:
   - the chip notice and "How would I know?" text match `FlashChipWords`;
   - Installs reads e.g. "1,234 on The Firmware Hub" and Saves on reads iOS's value;
   - the version control lists the saved copies newest first, and "Delete this saved version" is in the danger colour
     in both themes;
   - `aisling`'s description shows bold, not asterisks.
   red when: any PNG shows raw Markdown, clipped rows, an illegible danger colour, or a missing notice.
   at base: no PNG. The page shows raw `**` and a plain "Remove" button.
