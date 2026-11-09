status: pending
# 14 — UAT: the list and V16 against iOS 0.2.1, on the committed branch

Depends on: 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13

## Why
ke4h closes only when each acceptance clause is observed. The clauses are: the live list kept whole and tolerant, the
list's features matching iOS `fcf4a78`, V16 downloaded and installed on the simulated FM-1 with its warnings, the gate
green, and David's hardware UAT. Nothing that worked at `f5b9505` may break. Rests on: D1, D6, D7, D9, D10, P-D1,
P-D5, P-D7.

## Description
Walk this on the **committed** branch, after tasks 02–13 are committed, and before `/jdi:pr`. Every read is of a
detached worktree at `HEAD`, never the working tree or the index. Automated steps come first, then the orchestrator's
emulator walkthrough, then David's hardware steps. Record every result, with its date and the commit, in
`plans/ke4h-ios-0-2-1-catch-up/uat.md`. Hardware steps stay open (kata `work.attention needs-human`) until David runs
them.

**Who runs what.** Steps 1–9 and 15–16 can be run by an Executor or the orchestrator, on the host (worktrees need a
writable `.git`, so not the Codex sandbox). Steps 10–13 are the **orchestrator's, on the host** (network and emulator).
Step 14 is **David's**, on his FM-1.

**Acceptance map** (`kata show ke4h`, "## Acceptance", verbatim):

| Clause | Proved by |
|---|---|
| "The live list loads, keeping every field the list carries; unknown enum values don't break it." | Step 3 (T05-key-set, round-trip, unknown-enums ran green); step 10 (the live list's keys ⊆ the fixture's, so the key-set test saw every live key). |
| "The list's features match iOS fcf4a78." | Step 11, walkthrough E1–E8, each against its iOS citation; step 3 (T05/T06 cases). |
| "V16 downloads from the list and installs on the simulated FM-1, with the warnings." | Step 12 (A3 walkthrough); step 3 (T02/T06/T07/T08 V16 cases). Resume across an app restart can't be shown on the simulator (it lives in the app process, and its loader reports `ota-FM-1_9…`): T06-stock-v16 (step 3) and hardware H3 are the evidence. |
| "The gate (`scripts/check.sh`) is green." | Steps 15 and 16, verbatim, on the committed tree; step 2 (counts ≥ base). |
| "David's UAT on hardware." | Step 14, H1–H5, David's. Each starts with a backup. |
| *Must not break* (implied): V15 installs, cable-pull resume, every base test still runs | Step 1 (each base test still runs, except the five named retirements); step 13 (V15 install and 45 % resume on the simulator). |

**Human steps.** Step 14 needs David, an FM-1 and an Android phone. Back up the FM-1's projects and samples first. Know
that V16 → V15 leaves V15 playing one note at a time, which only a Mac can clear (stage R3). If step 14 hasn't been run
when this task is walked, record H1–H5 as "not run — needs David". The Butler sets `work.attention needs-human` on ke4h
with that message. The mechanisms are covered by steps 3 and 12, but V16's real loader identity, the mono symptom, and
Android MIDI's reopen during a write (Risk 8) stay unobserved until he runs them.

**After the merge — deferred** (not steps; excluded from marking this task done):
- There is no CI pipeline (AGENTS.md: "there is no CI"), so nothing waits on a remote run. Steps 15–16 are the gate.
- Any upstream report on the stock loader answering `ota-FM-1_016` (D7) or on FM1Kit 0.6.0 is David's call. It isn't a
  UAT step.

## Files
- plans/ke4h-ios-0-2-1-catch-up/uat.md

## Verification
Setup, once, on the host: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; W=/tmp/claude-1000/ke4h-uat-head; B=/tmp/claude-1000/ke4h-uat-base; git worktree add --detach "$W" HEAD; git worktree add --detach "$B" f5b950537b5e498ce5e89a96206b31ea4e63a166`.
Run Gradle and the check script **inside `$W`** (and `$B` for step 1's baseline).
Helpers: `names() { cat "$@" | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u; }`, plus
`ran` and `tally`, defined exactly as in task 02's Verification setup.
When done: `git worktree remove "$W"; git worktree remove "$B"`.

1. `(cd "$B" && ./gradlew :fm1:test :model:test :app:testDebugUnitTest --rerun-tasks -q) && (cd "$W" && ./gradlew :fm1:test :model:test :app:testDebugUnitTest --rerun-tasks -q) && comm -23 <(names "$B"/*/build/test-results/*/TEST-*.xml) <(names "$W"/*/build/test-results/*/TEST-*.xml)`
   — expected: exactly `aPackageThatAcceptsV15IsntFlagged`, `onlyKnownFirmwaresAreFlagged`,
   `refusesAnOfficialEntryThatIsntV15`, `savesAndReplacesAnOlderRelease` and
   `theWarningNamesTheOthersAndHedgesWhenUnsure` (retired by tasks 06 and 07), plus
   `start-up warning requires a non-M-VAVE answer` only if task 07's hand-back named its deletion.
   red when: any other base test no longer runs, which is a regression hidden by a deletion.
   control: `comm -23 <(names "$B"/*/build/test-results/*/TEST-*.xml) /dev/null | wc -l` prints `231` (the base run is
   live).
   at base: the Splitter's run at `f5b9505` (2026-11-09T09:20-06:00) found 231 test methods: `:fm1` 124, `:model` 92 and
   `:app` 15.
2. `for m in fm1/build/test-results/test model/build/test-results/test app/build/test-results/testDebugUnitTest; do (cd "$W" && tally $m); done`
   — expected: `:fm1` `tests=` ≥ 153 (124 + 5 + 10 + 14); `:model` ≥ 157 (92 + 21 + 6 + 26 + 12; tasks 06 and 07 net of their retirements); `:app` ≥ 14; every
   line has `failures=0 errors=0 skipped=0`.
   red when: a module lost tests, or any failure or skip.
   at base: `tests=124`, `tests=92` and `tests=15`, all with 0 failures, errors and skips.
3. `cd "$W"; ran fm1/build/test-results/test onlyV15MayBeIncludedInAnApp v16IsTreatedAsTheOfficialFirmwareToo reinstallsTheOfficialFirmwareOverALookAlikeOnceConfirmed v16RefusesItsOwnIdentity errorTextsSayKeepItOnAndNameMidimonFirst readsHowAFirmwareInstallsOthers sendsPeopleThroughAFixedFirmwareFromTheOriginalCheckOnM_Bootloader letsTheOriginalCheckTakeOnlyTheBootloaderTheFM1Has letsAFixedFirmwareGoAnywhere warnsThatTransitFM1ReportsV15sIdentityAndKeepsTheBootloader keepsTheBootloaderWhenAFirmwareComesWithItsOwnLoader letsV15InstallAnything readsWhichChipsTheCodeChecksFor saysNothingForAnInstaller transitFm1TakesV15 readsTheChipAFirmwareReportsAfterItsIdentity findsNoChipInAReplyWithoutOne readsNoChipFromAnIdentityThatRunsIntoIt theQueriesAreTheDocumentedOnes asksNoMoreOfAWholeIdentityThatCarriesTheChip aFoundDeviceCarriesTheChip asksForTheWholeIdentityAfterAShortenedOne asksForTheFlashChipWhenThePlainAnswerLeavesItOut keepsAThreeDigitIdentityWhenNothingMoreComes finishesWhenTheFirmwareAnswersOnAConnectionThatNeverShowsAsLost finishesWhenTheDeviceRestartsWithoutAskingToFinish aCableCutNearTheEndStopsWhenTheLoaderComesBack countsNearlyAllOfTheApplicationAreaAsNearlyWritten aSecondPassCountsOnlyOnceItIsNearlyDone; ran model/build/test-results/test everyFieldTheLiveListCarriesIsKept reEncodingKeepsEveryValue unknownValuesBecomeNull theSavedListKeepsEveryValue aSavedListWithUnknownValuesStillLoads listedOnNamesMutantCloudAndTheFirmwareHub mutantCloudAloneIsInTesting mostInstalledSortsByCountWithUncountedLast keepsThePickedFilter savesOnSaysWhichFm1sKeepChanges leavesTheChipUnknownWhenTheFeedSaysNothingUseful keepsTheChipWhenTheListIsSaved namesOneFirmwareThatKnowsOnlyTheOlderChip namesSeveralFirmwaresAsAList saysWhenTheFm1HasTheNewerChip dropsTheMarkdownMarks keepsALinksTextButNotTheLink leavesPlainTextAlone readsBoldItalicAndCodeAsRuns leavesAnUnmatchedStarAlone v16IsAStartUp keepsEachReleaseItSaves otherBuildNeedsTheSameVersion downloadsTheOfficialEntryCheckingItsHashNotTheList refusesAnOfficialEntryThatIsntV15OrV16 downloadingV15FromTheListReturnsTheBuiltInCopy addRecognisesV16ByHash aStockLoaderReportingV16CanBeFinishedByV15 aCustomLoaderStillMatchesOnlyItsOwnPackage mVavesFirmwaresAreKnownByTheirIdentity aSavedGuideTellsTransitFM1FromV15 aSavedGuideForAnotherIdentityIsIgnored theStartUpFillsInABootloaderTheGuideDoesntKnow nothingIsKnownAboutAnUpdateLoaderOrAnUnknownFirmware startUpsAndBootloadersMatch v15IsADestinationOfItsOwn v15ThroughTransitFM1KeepsTheBootloader aV16PackageIsADestinationOfItsOwn anUnknownBootloaderAsksTheStartUp anUnknownBootloaderGivesTheWarningsForAChangedOne transitFM1WarnsAboutV16AndItsIdentity installingV16WarnsAboutGoingBack v15AfterV16WarnsAboutMono onlyFirstChipFirmwaresAreFlaggedUnlessTheFm1SaysItHasThatChip readsWhichChipsAFirmwareKnows canReinstallOfficialOnlyForOneOfficialReinstall theNotOfficialTickMakesItASwitch aSavedTransitGuideKnowsItIsntOfficial aMissingFirstStepBlocksStart findsASavedFixedCopyWithoutFetching downloadsMidimonWhenNoneIsSaved refusesADownloadWithoutTheFixedCheck saysWhenMidimonIsntListed theNotOfficialTickTurnsAReinstallIntoASwitch confirmsReinstallingOfficialFollowsTheTicks readsHistorySavedBeforeGuides canBeSavedWithTheFM1 forgetsAGuideItCannotRead looksAgainWhenTheFM1DidNotReturn looksAgainWhenTheWriteStoppedNearTheEnd doesNotLookAgainPartWayThrough doesNotLookAgainForAReinstallThatStopped doesNotLookAgainForOtherErrors aLateLossStillFinishesWhenTheFm1ComesBackRunningIt anInstallFromV16IsRemembered anInstallSavesItsGuide installingV15SetsMVavesStartUp installingV16SetsV16sStartUp aRefusalNeedsMIDIMONUnlessTheFirmwareRunningTakesV15 scanningKeepsTheFlashChip`
   — expected: no output from either `ran`. This restates the case lists in steps 2 of tasks 02–08: every Testing
   Strategy case, T02 to T08.
   red when: a planned case never ran on the committed tree.
   control: `ran model/build/test-results/test zzNoSuchCase` prints `zzNoSuchCase`.
   at base: every name is printed (none exist at `f5b9505`).
4. `cd "$W"; grep -rn "FeluccaUpdateCode" model/src app/src; grep -rn "refusesV15OverOtherBootloaders" model/src app/src; grep -rnE 'FlashPhase\.Verifying|Stage\.Checking|install_stage_checking' model/src app/src; grep -rn "before_start_computer_recovery\|before_start_untested\|stays in update mode" app/src fm1/src; grep -rn "otherBuild" app/src/main/kotlin; grep -rnE "ExperimentalLayoutApi|stickyHeader" app/src/main/kotlin`
   — expected: no output. This restates task 07 steps 5–6, task 08 step 4, task 12 step 3, task 02 step 5, and task 10
   steps 2 and 4.
   red when: a superseded rule, phase, string or API survived on the branch.
   control: `grep -rln "refusesV15OverOtherBootloaders" fm1/src/main` prints `ApplicationArea.kt`. The `:fm1` property is
   kept.
   at base: hits in `Settings.kt`, `InstallReadiness.kt`, `FlashPhase.kt`, `InstallProgress.kt`, `DeviceController.kt`,
   `strings_install.xml`, `Fm1UpdateError.kt`, `HomeScreen.kt`, `MyFirmwaresScreen.kt`, `FirmwareListScreen.kt` and
   others, as recorded in those tasks.
   compliant hits: none.
5. `cd "$W"; git ls-tree -r -l HEAD | awk '$4 == 718196'; git ls-tree -r --name-only HEAD | grep -ci "FM-1_016\|v16.*\.fwsc"; git log --format=%H f5b950537b5e498ce5e89a96206b31ea4e63a166..HEAD -- '*.fwsc'`
   — expected: no output, `0`, and no output.
   red when: V16's file, or any `.fwsc` change, entered the repo (D9: never bundled or committed).
   control: `git ls-tree -r -l HEAD | awk '$4 == 699956'` prints the bundled V15 (`FM-1_015.fwsc`).
   at base: no output, `0`, and (empty range) no output.
6. `cd "$W"; grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/pkg/OfficialFirmware.kt fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1FlashChip.kt fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Message.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/InstallCheck.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1UpdateError.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdateEvent.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdaterTiming.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/WriteProgress.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt fm1/src/main/kotlin/dev/drewr/fm1/pkg/FwscPackage.kt fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt fm1/src/main/kotlin/dev/drewr/fm1/pkg/LeavingGuide.kt fm1/src/main/kotlin/dev/drewr/fm1/pkg/FlashChipSupport.kt`
   — expected: no output. This restates the header steps of tasks 02, 03 and 04.
   red when: a ported `:fm1` file misstates its FM1Kit version.
   control: `grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/protocol/SevenBit.kt` prints that path.
   at base: lists the twelve existing files.
7. `cd "$W"; for s in LeavingGuide FlashChipSupport Fm1FlashChip Fm1DeviceSummary SwitchingGuide SwitchingWords FixedFirmware FilteredFirmwareList; do printf '%s doc:%s code:%s\n' "$s" "$(grep -c "$s" doc/architecture.md)" "$(grep -rlE "(class|object|fun) $s\b" fm1/src/main model/src/main app/src/main | wc -l)"; done`
   — expected: every line shows `doc:` ≥ 1 and `code:` ≥ 1.
   red when: task 13 documented a name the code doesn't have, which happens when docs written in wave 1 drift from the
   code.
   at base: every line shows `doc:0 code:0`.
8. `cd "$W"; grep -c "Refuse V15 over V15 and V16 over V16" AGENTS.md; grep -c "MPL-2.0" NOTICE.md; grep -c "has no licence chosen yet" NOTICE.md`
   — expected: `1`, ≥ `1`, `1`. This restates task 13 steps 1–2.
   red when: the docs edit is missing, or a licence was chosen (D11).
   at base: `0`, `0`, `1`.
9. `cd "$W"; for s in "Going back to V15 later can't be fully done from this phone." "After V16, V15 plays one note at a time" "V15 is on, but after V16 it plays one note at a time"; do grep -cF "$s" model/src/main/kotlin/dev/drewr/fmswitch/model/device/SwitchingWords.kt; done; grep -rn "M-Flasher" model/src/main/kotlin/dev/drewr/fmswitch/model/device/SwitchingWords.kt`
   — expected: `1`, `1`, `1`, then no output. This restates task 07 step 7; Risk 6 forbids naming a non-Mac tool as able
   to clear the data.
   red when: the Android wording is missing, or the copy promises a fix the phone can't give.
   control: `grep -c "M-Flasher" app/src/main/res/values/strings_settings.xml` prints ≥ 1 (the name occurs elsewhere on
   purpose).
   at base: the file doesn't exist.
   compliant hits: none.
10. A1 live diff (orchestrator, on the host; needs network): `curl -sS https://fm-1.drewr.dev/firmwares.json -o /tmp/claude-1000/ke4h-uat-live.json && comm -23 <(jq -r '[.entries[]|keys[]]|unique[]' /tmp/claude-1000/ke4h-uat-live.json) <(jq -r '[.entries[]|keys[]]|unique[]' "$W"/model/src/test/resources/firmwares-live-trimmed.json)`
    — expected: no output. Every key in today's live list is in the fixture, so T05-key-set saw it. Record the list's
    `builtAt` and the date in `uat.md`.
    red when: the live list has gained a field the fixture (and so the model) doesn't cover (observation 0655).
    control: `comm -23 <(jq -r '[.entries[]|keys[]]|unique[]' /tmp/claude-1000/ke4h-uat-live.json) <(printf 'id\n')`
    prints the other keys (non-empty).
    at base: the plan-time list (`/tmp/claude-1000/fw.json`, built 2026-11-09T02:10:54Z) has keys
    `author beta builds date flashChip id installs isOfficial kinds licence links listing loader name packageOrigin packageURL product releaseURL repo sha256 size sourceBuild stars version what whatSource`.
    Of those, `flashChip` and `installs` aren't `CatalogueEntry` fields at `f5b9505`.
11. A2 walkthrough (orchestrator, on the host): the visual review protocol (PLAN.md › Conventions), with an APK built in
    `$W`, light and dark, against the live list. PNGs go in `build/shots/uat-E<n>-<light|dark>.png`; read each one.
    - **E1** an entry listed only on mutant.cloud: "Listed on" reads "mutant.cloud" (iOS `EntryDescriptions.swift:4-14`).
    - **E2** Most installed: the source line, and the uncounted note before the first uncounted entry
      (`FirmwareListView.swift:57-59,108-128`).
    - **E3** filters wrap over two lines. Pick a kind, kill the app (`adb shell am force-stop dev.drewr.fmswitch`),
      relaunch: the pick stays. This also covers T05's DataStore blocker record.
    - **E4** scroll down: the pinned bar shows the order and the picked pill. Tapping it scrolls back to the filters.
    - **E5** My firmwares: its own sort and filter, and the filtered-empty text.
    - **E6** `aisling`'s description shows bold, not asterisks.
    - **E7** save two releases of one entry: both stay; the page's version control lists both; "Delete this saved
      version" deletes the shown one.
    - **E8** an `old`-chip entry shows the notice and "How would I know?", plus the Installs and Saves on rows.
    Expected: each matches its iOS citation.
    red when: any E-step differs from iOS `fcf4a78`, clips, or loses the pick on relaunch.
    at base: E1 shows a blank "Listed on"; E2–E8 don't exist.
12. A3 walkthrough (orchestrator, on the host): Settings ▸ Connection ▸ Simulated FM-1 (`FM-1_900`), then:
    1. Open M-VAVE's entry (official) and Save for later. It's saved as "FM-1 V16". The app downloads the real file;
       never copy it into the repo.
    2. Install V16. Before you start shows the before-V16 notice. Install. Done says "…ready to play", reporting
       `FM-1_016`. Settings ▸ start-up now reads "Changed by V16".
    3. Install V15 (built in). Before you start shows the after-V16 notice. Done shows the still-mono card, with no
       factory-voices button.
    Record PNGs of each notice and of Done.
    Expected: as listed. Since `SimulatedFm1`'s identity changes with each install, steps 2–3 run in one session
    (P-D7).
    red when: V16 is refused (`NotOfficial`) or saved as V15, a warning is missing, or the start-up isn't updated.
    at base: Save for later on M-VAVE's entry fails with "That file isn't a recognised official FM-1 firmware (only an
    unmodified V15 FM-1.fwsc is accepted)."
13. Must not break (orchestrator, on the host): on a fresh simulated `FM-1_900`, install the built-in V15 with the switch
    ticked: Done. Then turn on "Pull the cable at 45%" and install a Felucca firmware: the interrupted screen says
    "Don't switch it off…", and resume finishes.
    Expected: both finish. The installing overlay shows three stages, with no "Checking".
    red when: the V15 path or resume regressed, or a "Checking" stage appears (P-D2, Risk 1).
    at base: both finish. The overlay shows four stages, including "Checking".
14. A5 hardware (David's; each preceded by backing up the FM-1's projects and samples):
    - **H1** V15 → V16 from the list on a real FM-1. Record the loader identity in the job details
      (`ota-FM-1_016`?).
    - **H2** V16 → V15 installs. Record whether V15 plays one note at a time (RV P4, FK stage R3), and that the Done card
      says so.
    - **H3** Restart the app mid V16→V15 write: Home offers V15 to finish (P-D5).
    - **H4** If David has a felucca-bsp firmware built since Nov 2026: Home shows the full identity, not three digits.
      Record the chip notice behaviour, and the chip ID if reported.
    - **H5** A Felucca install still finishes normally, with no new stall or disconnect from the reopen during a write
      (Risk 8, fc809ca `lookForReturn`).
    Expected: as listed; each result recorded in `uat.md`.
    red when: any H-step fails, or is marked passed without David running it.
    at base: not run. H1 can't start at `f5b9505`, which refuses V16.
15. `cd "$W"; env -u JAVA_HOME -u ANDROID_HOME scripts/check.sh` — expected: `Checks passed: lint build test`, exit 0.
    This is A4 as the plan states it (the script finds JDK 21 itself, precedent `plans/android-port/17-uat.md`).
    red when: the gate fails on the committed tree.
    at base: `Checks passed: lint build test` (Planner probe 2026-11-09T08:59:25-06:00 at `f5b9505`).
16. `cd "$W"; JAVA_HOME="$(mise where java@temurin-21)" scripts/check.sh` — expected: `Checks passed: lint build test`,
    exit 0. This is the gate's own command (PLAN.md › Conventions › Gate), verbatim. There is no CI, so this is the gate
    the branch faces.
    red when: lint, the build or any unit test fails on the committed branch.
    at base: `Checks passed: lint build test`, exit 0.
