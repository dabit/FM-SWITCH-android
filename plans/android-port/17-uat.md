status: pending
# 17 — UAT: the Android port, end to end

Depends on: 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13, 14, 15, 16

## Why
There is no issue, so the plan's own goals are the acceptance criteria: a Kotlin protocol core, a simulated FM-1, the
published firmware list with an offline library, the Standard look's core flows (change firmware, resume, saved copies)
and an app skeleton whose local check script is the only gate. This task proves each one on the committed branch, and on
real hardware where only hardware can. Rests on: Scope; Look; No CI; Platform; applicationId; Foreground service type.

## Description
Walked **before** any pull request; it never merges or pushes (committing, pushing and releasing are David's call,
AGENTS.md "Issues"). Every earlier task is committed when this runs, so every automated check reads **the commit**, not
the working tree: build and test in a detached worktree of `HEAD`, and read files with `git show` / `git grep HEAD`.
The earlier tasks' **mutation** steps are deliberately not re-run: they edit source files, and this task reads the commit;
they were each task's evidence that its tests bite. Every other check an earlier task ran is restated below.

Write the record to `plans/android-port/uat.md`: one row per Verification step below — step number, command or scenario,
expected, observed (paste the output or what the human saw), pass / fail / not run + reason — then the goal map below with
each goal's verdict.

**Goal map** (plan Summary and Decision "Scope"; "must not break" in the second half):

| Goal | Proved by |
|---|---|
| Protocol in Kotlin (`:fm1`, ported from FM1Kit 0.4.5) | steps 7, 8, 9, 16, 17 |
| Simulated FM-1 | step 9 (`SimulatedFm1Test` and the updater against it), step 11, U0 (step 23) |
| Firmware list (published `firmwares.json` only) and offline library | steps 10, 20, U0 (list, save for later, offline) |
| Change firmware (Standard confirmations) | steps 11, 18, U0, U2 (step 25) |
| Resume an interrupted install | step 11 (`resumesAfterTheCableIsPulled`, `eachStepResumesWhenALoaderAnswers`), U0, U3 (step 26) |
| Saved copies (My firmwares) | step 10 (library), U0 |
| App skeleton, minSdk 26, id `dev.drewr.fmswitch`, no CI — the check script is the gate | steps 1, 3, 4, 13, 28 |
| Licences shipped (FM1Kit, M-Flasher, V15 Apache-2.0) | steps 2, 5, 6, 12 |
| Install survives leaving the app (FGS `connectedDevice`) | step 15, U4 (step 27) |
| **Must not:** SysEx to non-FM-1 gear; V15 reinstalled over V15 | step 9 (`neverSendsToOtherGear`, `v15IsNotReinstalled`), step 21 |
| **Must not:** Retro's dialogs on the Standard look; probe in release; on-device list building; real-time waits in `:fm1`/`:model`; CI files | steps 18, 14, 20, 16, 1 |

**Human steps.** U0 needs eyes on an emulator or phone (no FM-1). U1–U4 need **a human with an FM-1 and an Android phone
(API 26+; API 34+ for U4)**. If no such human is available when this task is walked, record those steps as "not run — no
hardware", and report to the Butler: the mechanisms are covered by steps 7–11 and 15 (simulator, framing, port matching,
manifest), but Android's real naming, reboot and FGS behaviour (Blocker records B1, B2) stay unobserved until they run.
The Butler and the user decide whether the task can close without them.

**After the merge — deferred** (not steps; excluded from marking this task done):
- Record U1's probe findings (device `PROPERTY_*` names, VID:PID, reboot as remove + add, permission dialog, `onSend`
  chunk sizes) in `doc/architecture.md` and settle premises P4(a)–(d) — picked up by a follow-up docs commit once U1 has
  been run (task 06 deliberately left it out).
- There is no CI pipeline (Decision "No CI"), so nothing waits on a remote run.

## Files
- plans/android-port/uat.md

## Verification
Setup (once): `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`;
`W="$SCRATCH/uat-head"; git worktree add --detach "$W" HEAD`; run Gradle and the check script **inside `$W`**. Helper:
`missing() { d=$1; shift; comm -23 <(printf '%s\n' "$@" | sort -u) <(cat "$d"/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u); }`
— prints every expected test method that did not run. When done: `git worktree remove "$W"`.

1. `git ls-tree -r --name-only HEAD | grep -c '^\.github/'` — expected: `0` (no CI, Decision "No CI").
   red when: a workflow file was committed.
   control: `git ls-tree -r --name-only HEAD | grep -c '^\.jdi/'` → `1`.
   at base: `0` (`git ls-files` at aa66529 → `.jdi/config.yml AGENTS.md CLAUDE.md`).
2. `git ls-tree -r --name-only HEAD | grep -i 'fm1kit'` — expected: exactly `app/src/main/assets/licences/FM1Kit-LICENSE.txt`
   and `fm1/LICENSE-FM1Kit.txt` (task 02's step 8, restated over the commit).
   red when: any part of an FM1Kit clone was committed.
   at base: no output.
3. `(cd "$W" && scripts/check.sh nonsense; echo "exit=$?")`, then control `adb devices | sed 1d | grep -c 'device$'` and,
   if it printed `0`, `(cd "$W" && scripts/check.sh device; echo "exit=$?")` — expected: `Unknown check group: nonsense` +
   `exit=1`; then a plain no-device message and a non-zero exit (task 01, steps 3–4). If a device or AVD is attached,
   record the `device` group's real result instead.
   red when: the script accepts unknown groups or passes `device` with nothing attached.
   at base: the `adb devices` control printed `0` (measured 2026-10-30).
4. `git grep -n 'applicationId' HEAD -- '*.kts'` and, after step 13's build,
   `"$ANDROID_HOME/build-tools/37.0.0/aapt2" dump packagename "$W/app/build/outputs/apk/debug/app-debug.apk"` —
   expected: one hit, `HEAD:app/build.gradle.kts:<n>: applicationId = "dev.drewr.fmswitch"`, and `dev.drewr.fmswitch`
   (task 01, steps 5–6).
   red when: the id is duplicated, missing or differs in the APK.
   at base: no `.kts` files.
   compliant hits: none.
5. `git show HEAD:app/src/main/assets/firmware/FM-1_015.fwsc | sha256sum; git cat-file -s HEAD:app/src/main/assets/firmware/FM-1_015.fwsc`
   — expected: `db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a` and `699956` (task 02, step 1).
   red when: the committed V15 differs from M-VAVE's file.
   at base: none in this repo; the source copy measured `db1642b2…edb8a` / `699956`.
6. `git show HEAD:app/src/main/assets/licences/M-Flasher-LICENSE.txt | grep -c 'Julius Vitkauskas'; git show HEAD:fm1/src/main/kotlin/dev/drewr/fm1/pkg/ApplicationArea.kt | head -25 | grep -c 'Julius Vitkauskas'; for w in FM1Kit M-Flasher ApplicationArea Apache-2.0 FM-1_015; do git show HEAD:NOTICE.md | grep -q -- "$w" || echo "MISSING $w"; done`
   — expected: `1`, `1`, no `MISSING` line (tasks 02 steps 4–5, 05 step 5).
   red when: a required notice is missing from the commit.
   control: the loop with `zzNoSuchWord` added → prints `MISSING zzNoSuchWord`.
   at base: none — written by tasks 02 and 05.
7. `(cd "$W" && ./gradlew :fm1:test :model:test :app:testDebugUnitTest --rerun)` — expected: `BUILD SUCCESSFUL`; then
   `grep -ho 'failures="[0-9]*"' "$W"/fm1/build/test-results/test/TEST-*.xml "$W"/model/build/test-results/test/TEST-*.xml "$W"/app/build/test-results/testDebugUnitTest/TEST-*.xml | sort -u`
   → only `failures="0"` (tasks 03, 05, 07, 09, 08, 12, 15, 02, 04 — each task's steps 1 and 3).
   red when: any test fails on the committed tree.
   at base: none — test results are build outputs of the committed tree.
8. `missing "$W/fm1/build/test-results/test" packsLeastSignificantBitFirst roundTripsEveryLength handshakeQueryUnpacksToTheIdentityHeader sendsTheDocumentedCommands readRequestEncodesTheDocumentedBytes responseEncodesTheDocumentedBytes finishResponseIsSuccessAndANul readRequestRoundTrips readRequestWithBadChecksumIsIgnored responseDeclaresTheFullLength readsARealDevicesHandshakeReply rejectsAReplyWithABadChecksum identityRoundTripsAndRecognisesTheLoader splitsAtTheLastUnderscore rejectsUnreadableIdentities equalityIsOnTheText openFirmwareIsFm1KitsPredicate armedLoaderParses capabilityLayoutAndCommandsRoundTrip framerReassemblesAcrossChunks framerSkipsRealTimeBytes framerAbortsOnOtherStatus newStartDiscardsUnfinished dataWithoutStartIsIgnored dropsOver64KiB deliversInOrder nextTimesOutAfterOneSecond aWaiterWakesOnArrival closeWakesTheWaiterAndIgnoresLaterSends drainEmptiesTheInbox cancellingEndsTheWait aWaitCancelledBeforeItStartsEndsAtOnce keepsTheNewest256 decodesProductAndStripsMarkers rejectsOtherFiles crc16IsXModem decipheringTwiceGivesBackTheOriginal readsTheFirmwareImageAndLoader aFirmwareImageNoBiggerThanTheBootAreaIsAllApplication refusesADamagedHeader refusesADamagedTable refusesAnotherChip refusesADamagedPart doesNotCheckEncipheredParts refusesAPackageWithoutALoader refusesAFirmwareImageThatIsNotWholeSectors refusesOverlappingParts acceptsPaddingPastTheEnd refusesAPartPastTheEnd refusesAFileTooLargeForAnFM1 aSampleFilePassesEveryCheck bundledV15IsTheOfficialFirmware refusesV15WhenFeluccasUpdateCodeLacksTheFix takesV15WhenFixed takesV15WhenHashed plainSampleDoesNotUseFeluccasCode`
   — expected: no output (tasks 03 step 2 and 05 step 2: 56 methods).
   red when: a protocol or package case is missing from the committed suite.
   control: `missing "$W/fm1/build/test-results/test" zzNoSuchCase` → prints `zzNoSuchCase`.
   at base: none — written by tasks 03 and 05.
9. `missing "$W/fm1/build/test-results/test" probesOnlyMatchingPorts probesByUsbIdWhateverTheName simulatorAnswersTheHandshake connectionsDieOnReboot armedLoaderIsNamedAfterTheBuild identifiesTheDevice installsAndVerifiesTheIdentity resumesAfterTheCableIsPulledMidWrite finishesFromRecoveryMode refusesARequestBeyondTheImage reportsEachStepInOrder resumeNeedsTheLoader v15IsNotReinstalled refusesAPackageForAnotherModel rejectsPackagesForOtherModels standardTimingsAddNoPauses neverSendsToOtherGear answersARepeatedFinishBeforeClosing finishOfTheOtherPhaseStops finishWithLengthOtherThan8Stops buildIDsReadHexInEitherCase commandsRoundTrip capabilitiesRoundTripAndRejectOtherLayouts recognisesOpenFirmwareIdentities readsAnArmedOpenFirmwareLoader armsOpenFirmwareAndChecksTheBuildAfterwards armsWithThePackageHashWithoutABuildID theRunningBuildIsAReinstall leavingOpenFirmwareIsASwitch aBusyDeviceIsNotArmed aRefusedArmingStopsBeforeStaging openFirmwareThatDoesNotAnswerIsNotUpdated otherFirmwaresAreNotAskedAboutCapability officialReleasesCarryTheirBuildIDs switchingNeedsConfirmingAndSendsNothingUntilThen aDeviceIsCheckedWithoutChangingIt refusesToInstallWithTwoFM1sConnected refusesToResumeWithTwoLoadersWaiting findAllReportsEveryDevice otherMessagesDontKeepAStalledPhaseWaiting ignoresRepliesThatAreNotHandshakes otherMessagesAreNotReadRequests responsesWithAWrongLengthOrChecksumAreRejected storesAnIdentityAsItsText hashesTheFileNotTheImage recognisesOnlyOfficialReleases aCustomFirmwaresOwnIdentityNeedsConfirming sameIdentityNeedsConfirming errorTextIsFm1Kits aForwardsPassRisesSteadilyToTheEnd aBackwardsPassRisesSteadilyRatherThanJumping headerRereadsAtTheStartAreStillWriting shortRereadsAtTheEndAreStillWriting aSecondPassIsReportedAsChecking aRereadAtTheEndOfAPassIsStillWriting newGroundDuringCheckingGoesBackToWriting aLoaderThatSkipsPartOfTheImageEndsShortOfOne aShortFinalReadIsCounted aBackwardsLoaderReportsSteadyProgress aCheckingLoaderReportsCheckingAfterWriting readsOutsideTheRegionLeaveProgressUnchanged readsAcrossTheRegionCountOnlyTheirInsidePart`
   — expected: no output (tasks 07 step 2 and 09 step 2: 62 methods; covers "never sends to other gear" and "V15 is
   not reinstalled").
   red when: a transport, simulator or updater case is missing.
   control: `missing "$W/fm1/build/test-results/test" zzNoSuchCase` → prints `zzNoSuchCase`.
   at base: none — written by tasks 07 and 09.
10. `missing "$W/model/build/test-results/test" decodesThePublishedShape triesAgainAfterTheNetworkFails givesUpAfterItsLastWait doesNotTryAgainWhenTheListIsMissing aCancelledDownloadIsNotAFailure refusesANewerFormat refusesAnEmptyList dropsARepeatedIdFromASavedList sortsOfficialFirst keepsTheSavedListOnFailureAndSaysSoWhenOld keepsTheSavedListOnFailureAndSaysSoWhenRecent comesWithTheOfficialFirmwareAndKeepsItWhenCleared readsTheBuiltInCopyRatherThanSavingIt cannotRemoveTheBuiltInCopy savesPackagesUnderTheirNamesWithTheirDetailsBeside reloadPicksUpFilesWrittenElsewhere picksThePackagesThatCanFinishALoadersUpdate anyPackageCarryingTheStockLoaderCanFinishIt picksThePackageAnArmedLoaderIsWaitingFor armedLoaderMatchesAnImportBySha refusesAWrongHash refusesAWrongProduct refusesAnOfficialEntryThatIsntV15 savesAndReplacesAnOlderRelease theAnswerIsRemembered onlyKnownFirmwaresAreFlagged theWarningNamesTheOthersAndHedgesWhenUnsure listsEachKindOnceTheMostCommonFirst matchesAKindWhateverItsCase keepsTheNameOrder putsTheNewestReleaseFirstAndUndatedLast countsTheFirmwareHubAndTheOfficialFirmwareAsTested marksFirmwaresOnlyInTheResourceHubAsUntested dropsTheHubsFlagFromAPlainVersion marksAnUntaggedBuildAsATest isBuiltFromSourceNeedsTheSameSha historyKeepsThirtyNewestFirst lastInstallOnlyIfNewestMatches badgeLettersComeFromTheName badgesKeepToTheirNames badgesComeIn896Designs theOfficialBadgeShowsItsVersion`
    — expected: no output (tasks 08 step 2 and 15 step 1: 42 methods).
    red when: a data-layer or badge case is missing.
    control: `missing "$W/model/build/test-results/test" zzNoSuchCase` → prints `zzNoSuchCase`.
    at base: none — written by tasks 08 and 15.
11. `missing "$W/model/build/test-results/test" flashesAFirmwareAndRecordsIt flashesThroughALoaderThatReadsBackwardsAndVerifies resumesAfterTheCableIsPulled refusesTheSameIdentityUnconfirmed installsTheSameIdentityOnceConfirmed refusesToSwitchUnconfirmed eachStepResumesWhenALoaderAnswers loaderNotFoundIsResumable planGoesViaOfficialUnlessAlreadyOnIt aRefusedInstallIsExplainedPlainly aRefusalWithMVavesStartUpSuggestsV15First aStallBeforeTheInstallStartsMightBeARefusal bothRefusalsAreRecognised aChainIsAlwaysSwitching onlyTheFirstStepIsChecked alreadyInstalledBlocksStart finishingUpdateCanStart startUpMustBeChosenFirst aPackageThatAcceptsV15IsntFlagged progressRunsThroughTheStagesInOrder progressSpreadsAcrossTheSteps trackerNeverGoesBackwards summarySaysWhatTheFm1IsDoing switchingNeedsTheSavedTick untestedNeedsItsTick confirmationsFollowTheTicks`
    — expected: no output (task 12 step 2: 26 methods).
    red when: a device-controller or readiness case is missing.
    control: `missing "$W/model/build/test-results/test" zzNoSuchCase` → prints `zzNoSuchCase`.
    at base: none — written by task 12.
12. `grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' "$W"/app/build/test-results/testDebugUnitTest/TEST-dev.drewr.fmswitch.LicenceAssetsTest.xml "$W"/app/build/test-results/testDebugUnitTest/TEST-dev.drewr.fmswitch.ui.theme.ContrastTest.xml`
    — expected: two lines, `failures="0"`, `tests` ≥ 3 and ≥ 2 (tasks 02 step 6, 04 step 1).
    red when: the licence or contrast tests are missing or failing.
    at base: none — written by tasks 02 and 04.
13. `(cd "$W" && ./gradlew :app:assembleDebug :app:assembleRelease)` — expected: `BUILD SUCCESSFUL` (inputs for steps 4,
    14, 15).
    red when: either variant fails to build from the commit.
14. Control first: `unzip -p "$W/app/build/outputs/apk/debug/app-debug.apk" 'classes*.dex' | grep -ac 'dev/drewr/fmswitch/probe/MidiProbeRecorder'`
    → ≥ `1`; then `unzip -p "$W/app/build/outputs/apk/release/app-release-unsigned.apk" 'classes*.dex' | grep -ac 'dev/drewr/fmswitch/probe/MidiProbeRecorder'`
    → `0` (task 11, step 2).
    red when: the probe ships in release.
    at base: none — APKs are build outputs.
15. `"$ANDROID_HOME/build-tools/37.0.0/aapt2" dump xmltree --file AndroidManifest.xml "$W/app/build/outputs/apk/debug/app-debug.apk" | grep -E 'FOREGROUND_SERVICE|WAKE_LOCK|POST_NOTIFICATIONS|CHANGE_NETWORK_STATE|FlashService|foregroundServiceType|android.software.midi|INTERNET'`
    — expected: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`,
    `CHANGE_NETWORK_STATE`, `INTERNET`, the MIDI feature, `.service.FlashService` and `foregroundServiceType` `0x10`
    (tasks 01 step 7, 13 step 2).
    red when: the merged manifest lacks the FGS prerequisites or the MIDI feature.
    at base: none — APKs are build outputs.
16. `git grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|Instant\.now|runBlocking|Dispatchers\.|ContinuousClock|TimeSource\.Monotonic' HEAD -- fm1/src/main model/src/main`
    — expected: no output (virtual-time rule; tasks 03 step 4, 05 step 7, 07 step 4, 08 step 4, 09 step 4, 12 step 4).
    red when: real time or a hard-coded dispatcher entered `:fm1` or `:model`.
    control: `git grep -nE 'Dispatchers\.' HEAD -- app/src/main` → ≥ 1 hit (production dispatchers live in `:app`, task 13).
    at base: none — written by tasks 03–12.
    compliant hits: none.
17. `git grep -L 'Ported from FM1Kit 0.4.5' HEAD -- 'fm1/src/main/kotlin/dev/drewr/fm1/protocol/*.kt' 'fm1/src/main/kotlin/dev/drewr/fm1/pkg/*.kt' 'fm1/src/main/kotlin/dev/drewr/fm1/updater/*.kt' fm1/src/main/kotlin/dev/drewr/fm1/transport/SysexInbox.kt fm1/src/main/kotlin/dev/drewr/fm1/testing/SamplePackage.kt`
    — expected: no output (tasks 03 step 5, 05 step 6, 09 step 6).
    red when: a ported file lost FM1Kit's attribution.
    control: `git grep -L 'Ported from FM1Kit 0.4.5' HEAD -- fm1/build.gradle.kts` → prints it.
    at base: none — written by tasks 03, 05, 09.
18. `git show HEAD:app/src/main/res/values/strings_install.xml | grep -ciE 'SWITCH FIRMWARE\?|REINSTALL OR UPDATE\?|What can I do'; git show HEAD:app/src/main/res/values/strings_install.xml | grep -ci 'saved anything I want to keep'`
    — expected: `0`, then `1` (task 16, steps 2, 3, 6).
    red when: Retro's dialogs or the dropped Help action are on the Standard look, or the switch tick is missing.
    control: `grep -c 'SWITCH FIRMWARE' ../FM-SWITCH/App/Views/FlashConfirmView.swift` → `1`.
    at base: none — written by task 16.
19. `git show HEAD:AGENTS.md | grep -c 'no Gradle project'; git show HEAD:AGENTS.md | grep -n 'SWITCH FIRMWARE?'; (cd "$W" && ./gradlew :fm1:test --tests 'dev.drewr.fm1.protocol.SevenBitTest')`
    — expected: `0`; every `SWITCH FIRMWARE?` line also says `Retro`; the documented single-test command succeeds (task
    06, steps 1, 3, 4).
    red when: the docs still describe a missing project or Retro's dialog as the app's, or a documented command fails.
    control: `git show aa66529:AGENTS.md | grep -c 'no Gradle project'` → `1`.
    at base: `1` (AGENTS.md:15).
20. `git grep -nE 'CatalogueBuilder|api\.github\.com' HEAD -- model/src/main app/src/main` — expected: no output (no
    on-device list building; task 08 step 5).
    red when: the rebuild fallback was ported.
    control: `grep -c 'CatalogueBuilder' ../FM-SWITCH/App/Model/CatalogueStore.swift` → ≥ `1`.
    at base: none — written by tasks 08–16.
    compliant hits: none.
21. `git grep -nE 'getDevicesForTransport' HEAD -- app/src; git grep -n 'isProbeable' HEAD -- app/src/main/kotlin/dev/drewr/fmswitch/midi app/src/debug`
    — expected: no output, then ≥ 1 hit in each of `midi/AndroidMidiTransport.kt` and the debug probe (tasks 10 steps
    2–3, 11 step 4).
    red when: an API-33 call slipped in, or a device can be opened without port matching.
    control: `printf 'getDevicesForTransport\n' | grep -c getDevicesForTransport` → `1`.
    at base: none — written by tasks 10 and 11.
22. `git log --oneline aa665298b155bf1505c95502cfa72440e0ac0da1..HEAD` — expected: one commit per task 01–16 (plus the
    plan-folder commits), each subject describing its task's work (subjects don't carry task numbers).
    red when: a task's work is missing from the branch or two tasks' work landed in one commit.
23. **U0 — simulated walkthrough (human; emulator or any phone, no FM-1).** If no phone: create an AVD from
    `system-images;android-37.2;google_apis_playstore_ps16k;x86_64` with `avdmanager`, boot it, install
    `$W/app/build/outputs/apk/debug/app-debug.apk`. Settings ▸ Connection ▸ Simulated FM-1: the welcome notice's OK
    unlocks after 4 s; Home shows `FM-1_900`; the firmware list loads; save a firmware "to this phone for later", then in
    airplane mode it is still in My firmwares and the list shows the saved copy with the "Couldn't refresh…" note; install
    a saved or downloaded firmware through Before you start — the "I've saved anything I want to keep" tick appears and
    the button stays disabled until ticked; Installing runs to Done; with "Pull the cable at 45%": "The install was
    interrupted" → Carry on installing → Done; with "Has m-bootloader…" and a Felucca-code firmware: the start-up question
    and the warning appear; back is blocked during the install; Settings ▸ About shows the three notices and the V15
    licence; the debug "USB MIDI probe" row opens the probe. Expected: each item as stated.
    red when: any item differs (record which).
24. **U1 — probe (human with FM-1 and phone).** Debug build ▸ "FM-1 probe": plug in the FM-1; record its `PROPERTY_*`
    values, VID:PID and port names; tap Send handshake → an identity is shown; note any permission dialog; note `onSend`
    chunk sizes. Same session: start an install from the app, come back; the log shows whether the reboot into the loader
    was remove + add, and the loader's names/VID:PID. Copy the log into `uat.md`. Expected: a handshake identity and a
    complete log. red when: the FM-1 is not offered a handshake button, or no identity parses.
25. **U2 — install (human with FM-1).** V15 over the running firmware (or a custom firmware), real cable → Done; "Show the
    details" has the device add/remove lines. red when: the install fails or the log lacks the device lines.
26. **U3 — resume (human with FM-1).** Pull the cable during Installing; re-plug; Home shows the finish card; Carry on
    installing → Done. Watch for the stock loader's unsolicited identity mid-write (research §2.9). red when: no finish card,
    or resume fails.
27. **U4 — staying alive (human with FM-1, phone on API 34+).** Start an install, press Home, lock the screen for 60 s; the
    install completes; the notification shows (or, without notification permission, the job log has no "couldn't start the
    service" line). red when: the install stalls in the background, or the service failed to start.
28. Last — the project's gate, verbatim, in the committed tree with no JDK or SDK exported:
    `(cd "$W" && env -u JAVA_HOME -u ANDROID_HOME scripts/check.sh)` — expected: the `lint`, `build` and `test` groups run
    and the last line is `Checks passed: lint build test`; exit 0.
    red when: any group fails or warns on the branch as committed.

## Results so far (2026-10-30, orchestrator)

- Automated steps 1–22 and 28: all pass at `ed9421c` (steps 19, 22 and 28 failed first on wording; 19 was fixed in
  AGENTS.md, 22 and 28 were corrected in this file).
- U0, partly, on a headless API 37 emulator (`fmswitch-smoke` AVD), debug build. Each of these worked:
  - welcome notice → Home;
  - the firmware list loads (219 entries);
  - Settings → Simulated FM-1 (its toggles appear at once);
  - Home shows "Connected, reporting FM-1_900";
  - V15 page → Before you start (three ticks) → Installing → "FM-1 V15 is ready to play".

  `FlashService` ran in the foreground with type `connectedDevice` (0x10) and stopped after Done. No crash-buffer
  entries. The walkthrough found that Settings couldn't be reached on a phone; fixed in `1e67192`.
- U1 and U2 on real hardware (2026-10-31): Samsung SM-F971B, Android 17 (API 37), debug build over wireless adb; the
  FM-1 was running Felucca. The owner backed it up with Felucca's web editor first.
  - U1, the probe:
    - The FM-1 enumerates as USB `1209:0001` ("Hügelton Instruments Felucca"), not M-VAVE's `4c4a:c755`.
    - Its ports have empty names (`0:input:`, `0:output:`), so it is matched only by the device name containing
      "felucca".
    - No USB permission dialog appeared; the phone had seen this FM-1 before.
    - Seven handshakes each returned identity `FM-1_900`. One reply arrived in two parts (21 + 20 bytes) and was
      reassembled.
  - U2, the install: Felucca 1.7.0 went over the running Felucca and reached "Felucca is ready to play", reporting
    `FM-1_917`. It took about 45 s.
    - Before you start showed the switch confirmation (the backup tick) and no start-up question.
    - The loader reboot was a remove and an add: the device became "Hügelton Instruments Felucca Update", then
      "Felucca" again, with a new MIDI device id at each step.
    - Step 25's "Show the details" exists only on the problem screen, in both the iOS app and this port, so the device
      lines were read from logcat (`FM1Midi`) instead. Step 25's wording is wrong, not the app.
  - Bugs found and fixed:
    - `8b58b4b`: the app rescanned on every MIDI status change. Its own handshake caused those changes, so it sent
      about ten handshakes a second.
    - `59afb35`: the debug probe screen kept its session open after a handshake, which left Home "Not connected"
      until the app restarted. It now closes the session 2 s later and shows the last answer at the top. Checked on
      the phone (2026-10-31 11:56): "Last answer: FM-1_917", the session closed 2 s after the handshake, then Home
      showed "Connected, reporting FM-1_917" without a restart.
- U4, on the same phone and FM-1 (2026-10-31): passed, with one Samsung caveat.
  - The run was Felucca 1.7.0 over itself. The FM-1 now reported `FM-1_917`, so Before you start showed the
    same-firmware notice rather than the backup tick.
  - Sequence: start the install, press Home, lock the screen at 09:49:13. The loader appeared at 09:49:13.6, just
    before the lock, and the write finished while locked (the loader left at 09:49:38).
  - `FlashService` stayed in the foreground (`types=0x10`), and the partial wake lock `dev.drewr.fmswitch:flash` was held
    for 49 s.
  - POST_NOTIFICATIONS was not granted, so no notification was visible. The log has no "couldn't start the service" line.
  - Caveat: the phone has Samsung's "block USB while locked" setting on (`settings secure block_usb_lock=1`). It
    didn't enumerate the FM-1 after it rebooted out of the loader until the phone was unlocked (`USER_PRESENT` and
    `usbDeviceAdded` both at 09:50:01). Then the app finished by itself: "Felucca is ready to play", the wake lock was
    released, and the service stopped.
  - On such phones, an install pauses at each reboot while the screen is locked and carries on when it's unlocked. If
    the lock lands before the loader appears, the write itself waits.
  - `InstallingOverlay` keeps the screen on, so this only happens when someone locks the phone by hand.
- U3, on the same phone and FM-1 (2026-10-31): passed. The run was Felucca 1.7.0 over itself, with the phone unlocked.
  The log shows the cable disconnected twice while the FM-1 was in its loader ("Felucca Update", `1209:0002`), with
  the USB root hubs gone both times:
  - 09:51:46, 0.2 s after the loader appeared, and back at 09:51:52;
  - 09:52:01, mid-write, and back at 09:52:08.

  The owner saw the carry-on screen, plugged back in and tapped Carry on installing. The write finished at 09:52:26,
  and the FM-1 came back as "Felucca" (`1209:0001`) at 09:52:33. The app ended on "Felucca is ready to play",
  `FM-1_917`. No crash.
- Remaining by hand (no FM-1 needed): the simulator's refusal paths, TalkBack and dark mode. Also the rest of U0, by hand: the cable-pull resume and the refusal paths in
  the simulator, TalkBack, and dark mode.
