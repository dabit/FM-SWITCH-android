status: done
# 12 — `:app` Before you start, installing and outcomes: V16 warnings, chains, look-alike reinstall, keep-it-on copy, still-mono card

Depends on: 05, 07, 08, 09

## Why
Before you start must show the switching chain, iOS's notices in Android's wording (before V16, after V16, older flash
chip, route), the look-alike tick, and the 36905bb keep-it-on ticks. The installing and outcome screens must say
"don't switch it off", show the still-mono card after V16→V15, and carry on with MIDIMON first after a refusal. It
renders task 07's `SwitchingCheck`/`SwitchingWords`/`FixedFirmware`, task 08's job and controller additions, task 09's
`FlashChipNotice`, and task 05's `StartUp.V16`. Rests on: D1, D6, P-D1, P-D2.

## Description
Implement **PLAN.md › Implementation Plan › Task 12, steps 1–5**, verbatim; they are binding. It ports iOS
`App/Standard/Firmwares/BeforeYouStartView.swift:67-291`, `InstallingView.swift:67-68`, `InstallOutcomeViews.swift` and
`App/Views/InstallWords.swift:6-18` @ `fcf4a78`. Read `research-v16.md` §3–§5 first. Copy is verbatim from
`InstallWords`/`SwitchingWords`, with the V16 notices and still-mono text from the `:model` constants (PLAN.md Appendix).
Factory voices are out of scope (D6): the still-mono card has **no** factory-voices button, and its wording says so.

- The step line becomes `InstallWords.darkScreen`, "Its screen stays dark until it's done. Keep it plugged in and
  switched on.", replacing the `installing_step`/`installing_keep_plugged` copy. Delete whichever of them goes unused
  (lint).
- `before_start_untested` is replaced by `InstallReadiness.UNTESTED_WARNING`; delete the string.
- The sheet passes `fetch` (`HttpFetch(Dispatchers.IO)::invoke`, as `AppModel.kt` does) and the controller's
  `flashChipId` to `SwitchingCheck.read`. Install passes `confirmsReinstallingOfficial`.
- "Install MIDIMON first": `FixedFirmware.find`, then `device.installFirst(it)` when the job confirmed the switch, else
  open MIDIMON's page through `model.requestPage(RequestedPage.Firmware(…))`, as the existing `ProblemAction.MIDIMON`
  does. This replaces the page jump only where iOS does. The navigator files aren't touched.

**Symbols read** (if one is missing, stop and report):
- From task 07: `SwitchingCheck` (all fields); `InstallReadiness.canReinstallOfficial`/`knowsItIsntOfficial`/
  `reinstallsOfficial`/`isMissingFirstStep`/`needsStartUp`/`canStart(…, confirmsNotOfficial)`/`UNTESTED_WARNING`;
  `BeforeYouStartState(…, confirmsNotOfficial)`/`confirmsReinstallingOfficial`; `SwitchingWords.*`;
  `FixedFirmware.find`.
- From task 08: `FlashJob.startedOnV16`/`needsAStepFirst`/`confirmsSwitchingFirmware`;
  `DeviceController.flash(…, confirmsReinstallingOfficial, startedOnV16)`/`installFirst`/`flashChipId`.
- `StartUp.V16` (task 05) and `FlashChipNotice` (task 09).

**Screens (visual review protocol, orchestrator, on the host, before commit):** use the simulated FM-1 and the live
list, in light and dark:
- Before you start for V16 over `FM-1_900` (the before-V16 notice);
- V15 after V16 was installed on the simulator in the same session (the after-V16 notice);
- a Felucca-code firmware with start-up unknown (the question, including the V16 row, and the switching warnings card);
- Done after V16→V15 (the still-mono card);
- the interrupted screen (cable pull at 45%).

The PNGs are `build/shots/t12-<screen>-<light|dark>.png`. The app downloads V16 itself; **never** copy it into the repo
(D9). The Executor can't run an emulator: report this step as not run.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Tasks 11 (firmware page) and 13 (docs) run alongside. A compile or lint error whose path is outside this task's
`## Files` comes from a sibling mid-edit: wait and re-run.

## Files
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/BeforeYouStartSheet.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/InstallingOverlay.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/OutcomeScreens.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/InstallPresentation.kt
- app/src/test/kotlin/dev/drewr/fmswitch/ui/install/InstallPresentationTest.kt
- app/src/main/kotlin/dev/drewr/fmswitch/AppModel.kt
- app/src/main/res/values/strings_install.xml

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; P=app/build/test-results/testDebugUnitTest`.
Use the helper `tally`, defined exactly as in task 02's Verification setup.

1. `./gradlew :app:testDebugUnitTest --rerun` — expected: `BUILD SUCCESSFUL`; `tally $P` shows `failures=0 errors=0
   skipped=0` and `tests=` at least as many as after task 07 (≥ 14).
   red when: `:app` doesn't compile, a presentation test fails, or tests were dropped.
   at base: `tests=15 failures=0 errors=0 skipped=0`.
2. `for s in "Its screen stays dark until it" "leave it plugged in and switched on until it" "Don\\'t switch it off. If switched off now, it may not start again." "Plug it back in, if it came out."; do grep -cF "$s" app/src/main/res/values/strings_install.xml; done`
   — expected: `1` for each (`InstallWords` darkScreen, staysOn, dontSwitchOff and plugBackIn). The look-alike tick's
   label is `SwitchingWords.notReallyOfficial` in `:model`, checked in step 4.
   red when: a keep-it-on line is missing or reworded (Risk 1).
   at base: `0` for each.
3. `grep -rn "before_start_untested" app/src` — expected: no output (`InstallReadiness.UNTESTED_WARNING` replaces it).
   red when: the old untested copy, which names only the Resource Hub, survived.
   control: `grep -rn "UNTESTED_WARNING" model/src/main/kotlin` prints the constant (task 07).
   at base: 2 hits, the sheet's `Notice(stringResource(R.string.before_start_untested), …)` and the string in
   `strings_install.xml`.
   compliant hits: none.
4. `grep -c "FlashChipNotice(\|BEFORE_V16_WARNING\|AFTER_V16_WARNING\|confirmsReinstallingOfficial\|notReallyOfficial" app/src/main/kotlin/dev/drewr/fmswitch/ui/install/BeforeYouStartSheet.kt; grep -c "STILL_MONO\|installFirst\|FixedFirmware.find" app/src/main/kotlin/dev/drewr/fmswitch/ui/install/OutcomeScreens.kt`
   — expected: ≥ `5`, then ≥ `3`.
   red when: a notice, the look-alike tick, the reinstall confirmation, the still-mono card or the install-first action
   isn't wired.
   at base: `0`, then `0`.
   compliant hits: a comment naming one of these would also count. Steps 6 and 7 are the real proof.
5. `./gradlew spotlessCheck :app:lintDebug` — expected: `BUILD SUCCESSFUL`.
   red when: a string is orphaned or a lint warning sits in this task's paths.
   at base: `BUILD SUCCESSFUL`.
6. Screens (orchestrator, on the host): the visual review protocol for the screens in the Description. Expected:
   - before-V16 shows the appendix text and no Mac-only promise;
   - after-V16 shows on V15 over `FM-1_016`;
   - the start-up question lists "Changed by V16";
   - Done after V16→V15 shows the still-mono card;
   - the interrupted screen says "Don't switch it off…" and "Plug it back in, if it came out.";
   - nothing clips in either theme.
   red when: any PNG lacks a notice, shows iOS's Mac wording, or clips.
   at base: no PNG. Before you start shows the Mac-only "computer recovery" line and no V16 notices.
7. (orchestrator, on the host) In the same emulator session, turn on Settings ▸ Simulated FM-1 ▸ "Has m-bootloader, and
   refuses M-VAVE's packages", then install V15 with the switch confirmed. Expected: the refusal's problem screen offers
   "Install MIDIMON first" with `installFirstDetail`. Tapping it carries on the stopped install with MIDIMON first
   (P-D1): the installing overlay reads "Step 1 of 2". If the simulator can't reach a refusal that needs a step, record
   "not reachable on the simulator"; T08-needs-a-step and `FixedFirmwareTest` are then the evidence.
   red when: the button jumps to a page where iOS carries on, or MIDIMON is fetched without the route needing it
   (Risk 10).
   at base: the problem screen's button opens MIDIMON's page (`ProblemAction.MIDIMON`).
