# ke4h UAT results

> **This is an agent's smoke test, not user acceptance.** An agent (the orchestrator's helper) ran steps 1–13, 15 and 16 of
> `14-uat.md` on the host and an emulator, on 2026-11-09. Step 14 (hardware) is David's and has not been run, so ke4h
> is not accepted until he runs H1–H5.

- **Commit:** `fa5d473edaf68747e927327b652990343b1deec7` (branch `ke4h-ios-0-2-1-catch-up`), read from a detached
  worktree `$W=/tmp/claude-1000/ke4h-uat-head`. Base `$B=/tmp/claude-1000/ke4h-uat-base` at `f5b950537b5e498ce5e89a96206b31ea4e63a166`.
- **When:** 2026-11-09, 11:32 to 11:59 −06:00. JDK `temurin-21.0.11+10.0.LTS` (mise).
- **Emulator:** AVD `fmswitch-smoke`, headless, 320×640. APK built in `$W` (`:app:assembleDebug`). App data was cleared
  (`pm clear`) before the walkthrough, because an earlier session had left V16 saved.
- **Screenshots:** `/tmp/claude-1000/ke4h-uat-shots/*.png`. They're outside the repo, and the `build/shots/` path the
  task names was in the worktree, which has been removed.
- Both worktrees were removed at the end (`git worktree remove`).

## Summary

| Step | Result |
|---|---|
| 1 Base tests still run | **Pass** |
| 2 Test counts | **Pass** |
| 3 Planned cases ran | **Pass** |
| 4 Superseded names gone | **Fails as written.** One hit, a simulator log line; see below |
| 5 No V16 bytes in repo | **Pass** |
| 6 FM1Kit 0.6.0 headers | **Pass** |
| 7 Architecture doc names | **Pass** |
| 8 AGENTS/NOTICE edits | **Pass** |
| 9 Android mono wording | **Pass** |
| 10 A1 live list keys | **Pass** |
| 11 A2 list features E1–E8 | **Pass**, except: E5's filtered-empty text can't be reached; E7's delete leaves the page out of date (defect D1) |
| 12 A3 V16 on the simulator | 12.1 and 12.2 **pass**. 12.3: installing V15 itself can't be reached from the UI. The after-V16 notice **was** reached another way. The still-mono Done card **can't be reached** |
| 13 Must not break | **Pass**, through a workaround for the V15 install and the cable pull (see below) |
| 14 A5 hardware | **Not run — needs David** |
| 15 Gate, script finds the JDK | **Pass** |
| 16 Gate, verbatim | **Pass** |

No crashes: `adb logcat -d -b crash` was empty, and there was no `FATAL EXCEPTION` from `dev.drewr.fmswitch`.

## Automated steps (1–10)

1. **Pass.** Both `--rerun-tasks` runs finished with exit 0. `comm -23` printed exactly `aPackageThatAcceptsV15IsntFlagged`,
   `onlyKnownFirmwaresAreFlagged`, `refusesAnOfficialEntryThatIsntV15`, `savesAndReplacesAnOlderRelease` and
   `theWarningNamesTheOthersAndHedgesWhenUnsure`, the five retirements. `start-up warning requires a non-M-VAVE answer`
   is not in the output, so it still runs. Control: `231`.
2. **Pass.** `:fm1` `tests=153 failures=0 errors=0 skipped=0`; `:model` `tests=157 failures=0 errors=0 skipped=0`;
   `:app` `tests=16 failures=0 errors=0 skipped=0`. The `:app` count is ≥ 14; base was 15.
3. **Pass.** Both `ran` calls printed nothing. Control: `zzNoSuchCase`.
4. **Fails as written: one hit.**
   `fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt:280: log("Cable pulled during writing; the loader stays in update mode.")`.
   Every other grep printed nothing. Control: `ApplicationArea.kt`.
   This line is the simulated FM-1's own debug log, not text the app shows. It has been there since `f2a807a`
   (base line 245). The step says it restates task 02 step 5, but that step only greps
   `fm1/.../updater/Fm1UpdateError.kt`, which is clean. The UAT's wider grep over `fm1/src` catches the simulator.
   The rule it protects (no 0.4.5 text that invites switching the FM-1 off) holds. It's the orchestrator's call
   whether to word the log line differently or narrow the grep.
5. **Pass.** No file of 718,196 bytes; `0` names matched; no `.fwsc` change in `f5b9505..HEAD`. Control:
   `app/src/main/assets/firmware/FM-1_015.fwsc`, 699956 bytes.
6. **Pass.** No output. Control: `SevenBit.kt` printed.
7. **Pass.** `LeavingGuide doc:2 code:1`, `FlashChipSupport doc:2 code:1`, `Fm1FlashChip doc:2 code:1`,
   `Fm1DeviceSummary doc:3 code:1`, `SwitchingGuide doc:2 code:1`, `SwitchingWords doc:4 code:1`,
   `FixedFirmware doc:3 code:1`, `FilteredFirmwareList doc:3 code:1`.
8. **Pass.** `1`, `1`, `1`.
9. **Pass.** `1`, `1`, `1`, and no `M-Flasher` in `SwitchingWords.kt`. Control: `2`.
10. **Pass.** The live list was fetched at 2026-11-09T11:33−06:00, with `builtAt` `2026-11-09T02:10:54Z` and 499 entries.
    `comm -23` against the fixture printed nothing. The control printed the 24 non-`id` keys, the same set as at plan
    time, `flashChip` and `installs` included. The list's official entry is `mvave-official`, V16, 718196 bytes.

## A2: the list's features (step 11)

The app showed "406 firmwares" from the 499 entries; test versions and firmwares without a download are hidden by
default. The PNGs below are in `/tmp/claude-1000/ke4h-uat-shots/`.

- **E1 Pass.** `fm1wheel` (`listing` 8): Details shows "Listed on: mutant.cloud". The In testing notice reads "…only
  listed in aroum's FM1 Resource Hub or on mutant.cloud, not on The Firmware Hub or fm1-editor.com." Screenshots:
  `uat-E1-light.png`, `uat-E1-dark.png`.
- **E2 Pass.** Under Most installed, the summary shows "Install counts are from The Firmware Hub's site." The note
  "These aren't on The Firmware Hub, so they have no install count." comes right before the first uncounted entry
  (M-VAVE official). Screenshots: `uat-E2-source-light.png`, `uat-E2-uncounted-light.png`, `uat-E2-light.png`.
  The iOS divider above the note isn't visible; that's a nit. No dark PNG was taken for E2.
- **E3 Pass.** The sort and 11 filter pills wrap over 5 rows at 320 px. Synths was picked, then the app was
  force-stopped and relaunched: Synths was still picked (150 firmwares), and Most installed was kept too. Screenshots:
  `uat-E3-picked-light.png`, `uat-E3-relaunch-light.png`, `uat-E3-dark.png`.
- **E4 Pass.** Scrolling down pins "Most installed" and the picked "Synths" pill under the title. Tapping the pill
  scrolls back to the filters. With All picked, nothing is pinned, which matches iOS `FilteredFirmwareList.swift`
  (`isPinned = areFiltersHidden && picked != .all`). Screenshots: `uat-E4-pinned-light.png`,
  `uat-E4-back-to-filters-light.png`, `uat-E4-dark.png`.
- **E5 Pass, partly.** My firmwares has its own order (Name, while the list was on Most installed), changed to Updated.
  It has its own filters too (All / Synths, from the saved entries). Screenshots: `uat-E5-myfw2-light.png`,
  `uat-E5-sorted-light.png`, `uat-E5-dark.png`.
  The filtered-empty text ("None of these match the filter picked.", which is in `strings_firmwares.xml`) **couldn't be
  reached**. The filters are worked out from the saved entries themselves, and Android has no Starred group. On iOS
  that text shows when the Saved group is empty but Starred matches the filter. After the only Synth was deleted, the
  picked Synths fell back to All, not to an empty list (`uat-E5-after-delete-light.png`).
- **E6 Pass.** AISLING's description shows **visualiser** in bold, with no asterisks. Screenshots:
  `uat-E6-light.png`, `uat-E6-dark.png`.
- **E7 Pass, with defect D1.** There's no live entry with two releases. AISLING 1.2 was saved through the app, then a
  synthetic older release was added to the app's data through `run-as`: a copy of the file plus a details JSON with
  version `1.1` and a fake hash `aaaa…`. Both releases stayed ("2 saved" in My firmwares). The page shows a `1.2 | 1.1`
  control, newest first, and "Install AISLING 1.1" when 1.1 is picked. "Delete this saved version" is outlined in the
  danger colour (`uat-E7-light.png`). Deleting with 1.1 shown removed only 1.1's file and details (checked with
  `run-as ls`), and 1.2 stayed. See **D1** for the page not updating. No dark PNG was taken for E7.
- **E8 Pass.** GHOULBOX (`flashChip` old) shows "GHOULBOX only knows the older FM-1 flash chip. On an FM-1 with the
  newer chip, it usually keeps changes only until it's switched off, and it often can't install another firmware."
  "How would I know?" opens "FM-1s sold since autumn 2026 have the newer chip. The easiest way to tell so far is the
  M-VAVE logo…", matching `FlashChipWords.swift`. Details shows "Installs: 1,667 on The Firmware Hub" and "Saves on: Older
  FM-1s only"; AISLING's shows "Installs: 85 on The Firmware Hub" and "Saves on: All FM-1s". Screenshots:
  `uat-E8-light.png`, `uat-E8-howknow2-light.png`, `uat-E8-rows-light.png`, `uat-E8-rows-aisling-light.png`,
  `uat-E8-dark.png`.

## A3: V16 on the simulated FM-1 (step 12)

The simulated FM-1 was picked in Settings ▸ Connection; it reported `FM-1_900`.

1. **Pass.** On M-VAVE's entry, Save to this phone for later downloaded the real file from M-VAVE. It showed "Saved,
   ready for offline install", and Details reads "Reports: FM-1_016 · Size: 718 kB · File from: M-VAVE's downloads".
   The file is stored as `FM-1 V16 Official.fwsc`. My firmwares lists it as "M-VAVE official firmware V16". It was not
   refused as NotOfficial and not saved as V15. Screenshots: `uat-A3-saved-light.png`, `uat-A3-official-details-light.png`.
   It was only saved on the emulator, never copied to the host.
2. **Pass.** Before you start ("FM-1 V16 Official") shows the before-V16 notice: "Going back to V15 later can't be fully
   done from this phone. V15 goes back on over USB, but after V16 it plays one note at a time until its saved data is
   cleared, which needs a computer: FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. V16
   isn't included with this app; it's downloaded from M-VAVE." The install showed three stages (Getting ready,
   Installing, Restarting) with no Checking. Done reads "FM-1 V16 is ready to play" and "Your FM-1 says it's running
   FM-1_016." Settings ▸ start-up then had "Changed by V16" picked. Screenshots: `uat-A3-v16-before-light.png`,
   `uat-A3-v16-before2-light.png`, `uat-A3-v16-installing-light.png`, `uat-A3-v16-done-light.png`,
   `uat-A3-startup-after-v16-light.png`.
   - The same page also showed the flash-chip notice: "FM-1 V16 only knows the older FM-1 flash chip…". The list has
     `flashChip: null` for V16, so the notice comes from scanning the real file's code (`FlashChipSupport`). Worth
     checking against iOS and David's FM-1.
   - Trying to install V16 again over V16 gives the refusal notice ("Your FM-1 says it's running V16, so there's
     nothing to install, unless…"), with a "It's really running another firmware" tick (`uat-A3-v16-over-v16b-light.png`).
3. **Can't be reached as written.**
   - Installing **V15 itself** can't be done from the UI, as expected. The bundled V15 doesn't show in My firmwares
     (only V16 does, `uat-A3-myfw-after-import-light.png`). It isn't on the official entry's page either, which has no
     release control (`uat-A3-official-page2-light.png`). It isn't on Home either. Importing `FM-1_015.fwsc` with
     + Add a firmware file says "Added M-VAVE's official firmware." (`uat-A3-import-v15-light.png`), but the file then
     doesn't show anywhere, because the library treats it as the built-in copy. The built-in V15 `StoredFirmware` has no
     `catalogueID`, so `savedCopies(mvave-official)` never includes it. iOS 0.2.1's Standard look behaves the same way.
   - The **after-V16 notice was reached** through Settings ▸ "Always install the official firmware first". With the FM-1
     on V16, installing AISLING gives "FM-1 V15 goes on first, as AISLING installs from it." It then shows "After V16,
     V15 plays one note at a time, as V16 leaves data behind that V15 misreads. This phone can't clear it: that needs
     FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. Some of V15's sounds may also be
     wrong or silent, and this app can't put back the factory sounds yet." The job ran "Step 1 of 2" (FM-1 V15), then
     AISLING, and finished with "AISLING is ready to play". Screenshots: `uat-A3-via-official-before-light.png`,
     `uat-A3-via-official-installing-light.png`, `uat-A3-via-official-done-light.png`.
   - The **still-mono Done card** can't be reached, because V15 is never the last firmware a job installs. It's
     covered by `app` `InstallPresentationTest."V15 after V16 tells the person it is still monophonic"`. The notice and
     the logic behind it are covered by `model` `SwitchingCheckTest.v15AfterV16WarnsAboutMono` and
     `FlashingTest.anInstallFromV16IsRemembered`, all green in steps 1–3.

## Must not break (step 13)

- **V15 install: Pass, through a workaround.** The built-in V15 can't be picked as the firmware to install (see 12.3).
  So the app was restarted to get a fresh `FM-1_900`, with "Always install the official firmware first" on, and
  Felucca [Salt] was installed. Before you start said "FM-1 V15 goes on first…", and the switching tick ("I've saved
  anything I want to keep") was ticked. Step 1 of 2 installed FM-1 V15, then Salt, and it ended with "Felucca [Salt]
  is ready to play", `FM-1_909`. Start-up then read "As it came from M-VAVE". The overlay showed three stages, with no
  Checking. Screenshots: `uat-13-v15-before-light.png`, `uat-13-v15-installing-light.png`, `uat-13-v15-done-light.png`.
- **Cable pull at 45% and resume: Pass, after a reset.** "Pull the cable at 45%" was turned on and Felucca 8 Patterns
  installed straight to Done, with **no** cable pull (`uat-13-interrupted-light.png` was first saved over this run's
  Done screen, then replaced). The setting only reaches the simulator in `DeviceController.configure()`, and the
  simulator only pulls the cable once per instance. After Settings ▸ "Reset the simulated FM-1", installing Felucca
  [Salt] stopped at 45%. The interrupted screen reads "The install was interrupted", "Your FM-1 is waiting to finish
  installing Felucca [Salt], so nothing is lost.", then "1 Don't switch it off. If switched off now, it may not start
  again. 2 Plug it back in, if it came out. 3 Tap Carry on. It picks up where it stopped." Carry on installing finished
  with "Felucca [Salt] is ready to play", `FM-1_909`. Screenshots: `uat-13-interrupted-light.png`,
  `uat-13-interrupted-scrolled-light.png`, `uat-13-resuming-light.png`, `uat-13-resume-done-light.png`.
  This wasn't compared with base. The configure-time read is probably older than this branch; it's a usability note,
  not a regression.

## Hardware (step 14)

- **H1** not run — needs David.
- **H2** not run — needs David.
- **H3** not run — needs David.
- **H4** not run — needs David.
- **H5** not run — needs David.

V16's real loader identity, the mono symptom on a real FM-1, and Android MIDI's reopen during a write (Risk 8) are
still unobserved. ke4h needs `work.attention needs-human` until David runs them; this helper didn't touch kata.

## Gate (steps 15–16)

15. **Pass.** `env -u JAVA_HOME -u ANDROID_HOME scripts/check.sh` in `$W` exited 0 with `Checks passed: lint build test`.
16. **Pass.** `JAVA_HOME="$(mise where java@temurin-21)" scripts/check.sh` in `$W` exited 0 with
    `Checks passed: lint build test` (finished 11:59−06:00).

## Defects and notes

- **D1. After "Delete this saved version", the firmware page doesn't update.** This happened both with two saved
  copies (1.1 shown) and with a single real copy (AISLING 1.2, saved through the app, no synthetic data). The file and
  its details are deleted from disk, and My firmwares updates when you go back. But the page keeps showing the deleted
  release: "Install AISLING 1.1" (or "Install AISLING"), "Saved, ready for offline install" and the Delete button.
  Screenshots: `uat-E7-stale-after-delete-light.png`, `uat-E7-single-delete-light.png`.
  Likely cause: `FirmwarePageScreen.kt:84` collects `saved` from `library.itemsFlow`, but only reads it in the fallback
  for imported files (line 89). `stored` (line 98) and `savedCopies` (line 182) read `model.library` directly, so the
  page never subscribes to library changes. Base has the same pattern (base line 82/87/98, delete at 229), so it's
  probably older than this branch. Not fixed here.
- **N1. The "Firmwares" title wraps to "Firmw / ares"** at 320 px wide, next to "+ Add a firmware file", in light and
  dark (`list-light-top.png`, `uat-E3-dark.png`). Not checked against base.
- **N2.** Long names are cut short on list cards, for example "M-VAVE official firm…" and "Ac… 0.1.60-stoppedtrace".
  `fm1wheel` shows "This release comes in several builds and can't be installed from here yet", with the Install and
  Save buttons shown disabled.
- **N3.** Switching between light and dark recreates the page and collapses an open Details section, and the search
  text is lost too.
- **N4.** Step 4's grep catches the simulator's log line; see step 4 above.
- **N5.** "Pull the cable at 45%" needs "Reset the simulated FM-1" (or picking Simulated FM-1 again) before it takes
  effect; see step 13.
- AISLING's page lists "Source code" and "Project site" twice. That's faithful to the list, which has two links of each
  label with different URLs; not a defect.

## After the UAT (orchestrator, 2026-11-09, `4654dfc`)

- **V15 on the official page** (David's decision): the page now shows a V16 | V15 switcher. V15 reads "Install M-VAVE
  official firmware V15" and "Comes with the app, so it installs even offline." With V16 saved, the button reads
  "…V16", not "…Official". Checked on the emulator. This makes 12.3's V15-over-V16 path reachable from the page.
- **D1 fixed:** after "Delete this saved version", the page updates straight away (emulator).
- **Step 4:** the simulated FM-1's log line no longer says the loader "stays in update mode".
- **Gate:** `scripts/check.sh` at `4654dfc`: `Checks passed: lint build test`. Tests: `:fm1` 154, `:model` 160, `:app` 16.
- **Still owed:**
  - On the emulator: the V15-over-V16 Before you start (after-V16 warning) and Done (still-mono card) screens
    through the new V15 option.
  - The "Install MIDIMON first" flow (task 12, step 7).
  - David's H1–H5.
- **Noted, not fixed:** the "Firmwares" title wraps at 320 px next to "+ Add a firmware file". It's older than this
  branch.
