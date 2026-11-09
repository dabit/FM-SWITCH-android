status: done
# 07 — `:model` switching guides, switching words, readiness, MIDIMON first, saved guides

Depends on: 02, 03, 05

## Why
Before you start must warn before and after V16 in Android's words, tell TransitFM1 from V15, flag firmwares that only
know the older flash chip, and put MIDIMON on first when the running firmware can't install the pick. That needs
FM1Kit's leaving guides (task 03), V16's official identity (task 02), and `StartUp.V16`/`HubFlashChip` (task 05). This
task also shares `Settings.kt` and `StartUpTest.kt` with task 05. Rests on: D1, D6, P-D1, P-D3, P-D6.

## Description
Implement **PLAN.md › Implementation Plan › Task 07, steps 1–9**, verbatim; they are binding. It ports the readiness
half of iOS 76293d0 and 3a5467c's `SwitchingCheck` part. Read `research-v16.md` §2–§4 and §7.3 first. The iOS source is
`/home/dabit/git/FM-SWITCH` @ `fcf4a78`. Copy comes from **PLAN.md › Appendix — Verbatim copy**: `BEFORE_V16_WARNING`,
`AFTER_V16_WARNING` and `STILL_MONO` in Android's wording, and everything else verbatim from the cited Swift, with
"this iPhone"/"iPhone or iPad" → "this phone".

Split changes and gaps the plan left:
- **`HubFlashChip.support`** (moved here from task 05): `val HubFlashChip.support: FlashChipSupport`
  (`Old → FIRST_CHIP_ONLY`, `New → BOTH_CHIPS`, `Any → NO_CHECK`; iOS `App/Catalogue/HubFlashChip.swift:17-23`). It
  goes in `InstallReadiness.kt`, next to `SwitchingCheck.from`, which uses it through `listedChip`. The test is
  `readsWhichChipsAFirmwareKnows` in `SwitchingCheckTest`.
- **`OutcomeScreens.kt` imports `FeluccaUpdateCode`** for `FeluccaUpdateCode.wayRound`. The plan's step 7 names only
  the sheet. Deleting `FeluccaUpdateCode` (step 6) breaks it, so this task makes the mechanical fix:
  `FeluccaUpdateCode.wayRound` → `FixedFirmware.MIDIMON`. Change nothing else there; task 12 owns the rest.
- **`InstallPresentation.kt` / `InstallPresentationTest.kt`.** The test builds `InstallReadiness.fromTarget(…,
  refusesV15OverOtherBootloaders = false, …)`, and `showsStartUpWarning(refusesV15, …)` takes the superseded flag. Make
  the mechanical fix: pass `switching = SwitchingCheck()`, and drive the start-up warning from
  `switching.dependsOnStartUp`. If a test of the removed flag no longer applies, delete it and name it in the hand-back.
- `canBeSavedWithTheFM1` (FK `LeavingGuideTests`, moved from task 03) is a `FlashHistoryTest` round trip of
  `SavedGuide`.
- `refusesV15OverOtherBootloaders` survives in `:fm1`'s `ApplicationArea.kt` with task 03's meaning. Only the
  `InstallReadiness` flag, parameter and function of that name are removed here.

**What later tasks read from this one** (observation 0654; exactly these names):
- `SwitchingCheck`, all fields and `needsFirstStep`/`warnings`; `SwitchingCheck.from(…)` and `SwitchingCheck.read(…)`;
  `SwitchingCheck.IF_CHANGED`.
- `InstallReadiness.switching`, `needsStartUp`, `isMissingFirstStep`, `isSwitching`, `isSameIdentity`,
  `canReinstallOfficial`, `knowsItIsntOfficial`, `reinstallsOfficial`, `isAlreadyInstalled` and
  `canStart(confirmsSwitch, acceptsUntested, confirmsNotOfficial)`; `InstallReadiness.UNTESTED_WARNING`.
- `BeforeYouStartState(…, confirmsNotOfficial)` and `BeforeYouStartState.confirmsReinstallingOfficial(readiness)`.
- `SwitchingGuide.running(…)`, `SwitchingGuide.guide(…)`, `SwitchingGuide.installing(…)`, `destinationOf(packageFile,
  official)`, `StartUp.bootArea` and `startUpOf(bootArea)`.
- `SwitchingWords.warnings`, `firstStep`, `missingFirstStep`, `alreadyOfficial`, `notReallyOfficial`,
  `notReallyOfficialDetail`, `replacesLookAlike`, `installFirst`, `installFirstDetail`, `warningTitle`, `decline`,
  `STILL_MONO`, `BEFORE_V16_WARNING` and `AFTER_V16_WARNING`.
- `FixedFirmware.MIDIMON`, `FixedFirmware.CATALOGUE_IDS`, `FixedFirmware.find(library, entries, fetch)` and
  `FixedFirmware.Failure`.
- `FlashRecord.guide`, `SavedGuide` (with `toGuide()` and `from(guide)`) and `FlashHistory.record(item, guide)`.

Task 08 calls `FlashHistory.record(item, guide)`, `SwitchingGuide.running` and `FixedFirmware`. Task 12 reads the rest.
Until task 12, the sheet passes `flashChipId = null` to `SwitchingCheck.read`.

**Test method names are binding:** use exactly the names in Verification step 2, and keep every surviving base case listed there through any rewrite. Where a name differs from the Testing Strategy's wording, step 2 wins; UAT step 3 re-checks the same names.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Task 10 (`:app` list screens) runs alongside. A compile or lint error whose path is outside this task's `## Files`
comes from a sibling mid-edit: wait and re-run. A string this task stops using in `strings_install.xml` must be
deleted here (lint `UnusedResources`).

## Files
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/SwitchingGuide.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/SwitchingWords.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/InstallReadiness.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/BeforeYouStartState.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/history/FlashHistory.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/settings/Settings.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/SwitchingGuideTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/SwitchingCheckTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/ReadinessTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/BeforeYouStartStateTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/FixedFirmwareTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/history/FlashHistoryTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/settings/StartUpTest.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/BeforeYouStartSheet.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/OutcomeScreens.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/install/InstallPresentation.kt
- app/src/test/kotlin/dev/drewr/fmswitch/ui/install/InstallPresentationTest.kt
- app/src/main/res/values/strings_install.xml

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; M=model/build/test-results/test; P=app/build/test-results/testDebugUnitTest`.
Use the helpers `ran` (prints every expected test method that did not run) and `tally`, defined exactly as in task 02's
Verification setup.
Base: `:model` 92 and `:app` 15 tests, 0 failures (Splitter, `f5b9505`). Task 05 (a dependency) adds 21 `:model`
tests.

1. `./gradlew :model:test --rerun` — expected: `BUILD SUCCESSFUL`.
   red when: any `:model` test fails or the module doesn't compile.
   at base: `BUILD SUCCESSFUL`, 92 tests.
2. `ran $M mVavesFirmwaresAreKnownByTheirIdentity aSavedGuideTellsTransitFM1FromV15 aSavedGuideForAnotherIdentityIsIgnored theStartUpFillsInABootloaderTheGuideDoesntKnow nothingIsKnownAboutAnUpdateLoaderOrAnUnknownFirmware startUpsAndBootloadersMatch v15IsADestinationOfItsOwn v15ThroughTransitFM1KeepsTheBootloader aV16PackageIsADestinationOfItsOwn anUnknownBootloaderAsksTheStartUp anUnknownBootloaderGivesTheWarningsForAChangedOne transitFM1WarnsAboutV16AndItsIdentity installingV16WarnsAboutGoingBack v15AfterV16WarnsAboutMono onlyFirstChipFirmwaresAreFlaggedUnlessTheFm1SaysItHasThatChip readsWhichChipsAFirmwareKnows canReinstallOfficialOnlyForOneOfficialReinstall theNotOfficialTickMakesItASwitch aSavedTransitGuideKnowsItIsntOfficial aMissingFirstStepBlocksStart findsASavedFixedCopyWithoutFetching downloadsMidimonWhenNoneIsSaved refusesADownloadWithoutTheFixedCheck saysWhenMidimonIsntListed theNotOfficialTickTurnsAReinstallIntoASwitch confirmsReinstallingOfficialFollowsTheTicks readsHistorySavedBeforeGuides canBeSavedWithTheFM1 forgetsAGuideItCannotRead aChainIsAlwaysSwitching alreadyInstalledBlocksStart finishingUpdateCanStart onlyTheFirstStepIsChecked startUpMustBeChosenFirst theAnswerIsRemembered historyKeepsThirtyNewestFirst`
   — expected: no output. Covers T07-running, v16-destination, before-v16, after-v16, chip (plus the `support` case
   moved from T05-hub-chip), depends-on-startup, transit, readiness, fixed-firmware, state and history-compat, plus the
   base cases that must survive.
   red when: a T07 case is missing or renamed, or a surviving base case was dropped in the rewrite.
   control: `ran $M zzNoSuchCase` prints `zzNoSuchCase`.
   at base: prints the 29 new names; the seven base names ran.
3. `ran $M onlyKnownFirmwaresAreFlagged theWarningNamesTheOthersAndHedgesWhenUnsure aPackageThatAcceptsV15IsntFlagged`
   — expected: prints **all three** names. They test `FeluccaUpdateCode` and the removed readiness flag, and are
   deleted (P-D3).
   red when: a test of the superseded rule still runs.
   control: step 2 printed nothing over the same results directory, so the directory holds this run.
   at base: no output. All three ran.
4. `tally $M` — expected: `tests=` ≥ 139 (92 + 21 from task 05 + 29 − 3), `failures=0 errors=0 skipped=0`.
   red when: other tests were lost, or a failure or skip was recorded.
   at base: `tests=92 failures=0 errors=0 skipped=0`.
5. `grep -rn "FeluccaUpdateCode" model/src app/src; grep -rn "refusesV15OverOtherBootloaders" model/src app/src` —
   expected: no output from either.
   red when: the superseded `:model` rule, or a consumer of it, survived.
   control: `grep -rln "refusesV15OverOtherBootloaders" fm1/src/main` prints `ApplicationArea.kt`, where the `:fm1`
   property is kept on purpose.
   at base: `FeluccaUpdateCode` is in `Settings.kt`, `InstallReadiness.kt`, `StartUpTest.kt`, `OutcomeScreens.kt` and
   `BeforeYouStartSheet.kt`. `refusesV15OverOtherBootloaders` is in `InstallReadiness.kt`, `ReadinessTest.kt`,
   `BeforeYouStartStateTest.kt`, `BeforeYouStartSheet.kt` and `InstallPresentationTest.kt`.
   compliant hits: none (a KDoc naming either would match, so word it otherwise).
6. `grep -rn "before_start_computer_recovery" app/src` — expected: no output (the switching warnings card replaces it).
   red when: the string or its use survived, which would also keep the Mac-only wording.
   control: `grep -c before_start_computer_recovery plans/ke4h-ios-0-2-1-catch-up/PLAN.md` prints ≥ 1.
   at base: 2 hits, the sheet's `stringResource(R.string.before_start_computer_recovery)` and the string in
   `strings_install.xml`.
   compliant hits: none.
7. `for s in "Going back to V15 later can't be fully done from this phone." "After V16, V15 plays one note at a time, as V16 leaves data behind that V15 misreads." "V15 is on, but after V16 it plays one note at a time until its saved data is cleared, which can't be done from this phone." "this app can't put back the factory sounds yet."; do grep -cF "$s" model/src/main/kotlin/dev/drewr/fmswitch/model/device/SwitchingWords.kt; done`
   — expected: `1`, `1`, `1`, `2`. These are the appendix's Android copy: before-V16, after-V16, still-mono, and the
   shared factory-sounds clause.
   red when: the copy was reworded away from the appendix, or iOS's Mac wording was kept.
   at base: none. `SwitchingWords.kt` is written by this task.
8. Mutation T07-after-v16. In `model/src/main/kotlin/dev/drewr/fmswitch/model/device/InstallReadiness.kt`, drop the
   `running.bootArea == V16` clause from `followsV16` in `SwitchingCheck.from`. Then run
   `./gradlew :model:test --rerun --tests '*SwitchingCheckTest'`: expected `FAILED` on `v15AfterV16WarnsAboutMono`.
   Revert: green.
   red when: the after-V16 warning over a saved guide is unguarded.
   at base: none. The test is written by this task.
9. Mutation T07-chip. In `InstallReadiness.kt`, prefer the listed chip (`listed ?: package.flashChipSupport`). Then run
   `./gradlew :model:test --rerun --tests '*SwitchingCheckTest'`: expected `FAILED` on
   `onlyFirstChipFirmwaresAreFlaggedUnlessTheFm1SaysItHasThatChip` (the `NO_CHECK` + `Old` row). Revert: green.
   red when: "the package wins" is unguarded.
   at base: none. The test is written by this task.
10. Mutation T07-fixed-firmware. In `model/src/main/kotlin/dev/drewr/fmswitch/model/device/SwitchingGuide.kt`, skip the
    `updateCode == FELUCCA_FIXED_CHECK` check on saved copies in `FixedFirmware.find`. Then run
    `./gradlew :model:test --rerun --tests '*FixedFirmwareTest'`: expected `FAILED` on
    `findsASavedFixedCopyWithoutFetching` (a saved MIDIMON without `AC79BLID` is returned). Revert: green.
    red when: MIDIMON-first could install a MIDIMON that can't take V15.
    at base: none. The test is written by this task.
11. Mutation T07-history-compat. In `model/src/main/kotlin/dev/drewr/fmswitch/model/history/FlashHistory.kt`, remove
    `FlashRecord.guide`'s default. Then run `./gradlew :model:test --rerun --tests '*FlashHistoryTest'`: expected
    `FAILED` on `readsHistorySavedBeforeGuides`. Revert: green.
    red when: an old `flash-history.json` could stop loading (Risk 15).
    at base: none. The test is written by this task.
12. `./gradlew :app:testDebugUnitTest --rerun && ./gradlew :app:lintDebug` — expected: `BUILD SUCCESSFUL` twice; `tally $P`
    shows `tests=` ≥ 14 and `failures=0`. That's 15 at base, less at most the one start-up-warning case if it no longer
    applies; the hand-back names any deletion.
    red when: the sheet, outcome screen or presentation helper no longer compile, or a string is orphaned (lint).
    at base: `BUILD SUCCESSFUL`; `tests=15 failures=0 errors=0 skipped=0`.
