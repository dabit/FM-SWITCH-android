status: pending
# 04 — `:fm1`: full identity and flash chip in the handshake; finish when the firmware answers; simulator options

Depends on: 02

## Why
Current FM-1 firmwares can shorten their identity and report a flash chip, and they can restart without asking to
finish. Without the full-identity query a good write can read as `IdentityMismatch`, and without the return check it can
stall into `Stalled(WRITING)`. Task 08's controller and the screens read the chip from here. The task edits
`Fm1Identity.kt`, `Fm1Updater.kt` and `SimulatedFm1.kt` after task 02, and reads its `Fm1FlashChip` and
`Fm1Identity.isOfficial`. Rests on: D5, P-D8.

## Description
Implement **PLAN.md › Implementation Plan › Task 04, steps 1–9**, verbatim; they are binding. It ports FM1Kit cee1a84's
protocol half and fc809ca. Read `research-v16.md` §1 and §7.6. FK is at
`/tmp/claude-1000/-home-dabit-git-FM-SWITCH-android/5e980def-0c27-4c1a-9352-a41b3172dfbb/scratchpad/FM1Kit` (@ `53f8511`).

The **virtual-time rule** binds step 5. Quiet time is measured by the durations of the task's own
`withTimeoutOrNull`/`delay` windows, never by `System.nanoTime`, `currentTimeMillis`, `TimeSource` or a clock.

`Fm1FlashChip` (`P25Q80`, `UC25HQ80`) exists from task 02; don't recreate it.

**What later tasks read from this one** (observation 0654): `Fm1DeviceSummary(identity, flashChipId)` with
`hasNewerFlashChip`; `Fm1Updater.summary()`; `UpdaterTiming.fullIdentityWait` and `returnCheckAfter`; the
`SimulatedFm1.Options` fields `flashChipId`, `shortensIdentity`, `reportsFlashChipOnlyWhenAsked`,
`restartsWithoutFinishing` and `keepsConnectionThroughRestart`; `Fm1Identity.handshakeReply(flashChipId)` and
`Fm1Identity.flashChipId(inHandshakeReply)`; `Fm1Message.FULL_IDENTITY_QUERY`; `WriteProgress.isNearlyWritten`.

**Test placement** (the plan left it open; fixed here so `## Files` is complete):
- `FlashChipTest` (protocol, pure): T04-chip-bytes, T04-no-chip, T04-may-tell-more, T04-queries.
- `ShortenedIdentityTest` (updater, against `SimulatedFm1` with the `UpdaterTest` harness): T04-found-carries-chip,
  T04-shortened, T04-chip-when-asked, T04-three-digits-stay.
- `ReturnTest`: T04-return-not-lost, T04-return-lost, T04-loader-back.
- `WriteProgressTest`: T04-nearly-written.

**Knock-on.** Every handshake with a non-official identity now also sends `FULL_IDENTITY_QUERY` and waits
`fullIdentityWait` when the answer carries no chip. Existing scripted `:fm1` tests whose transports answer a fixed
script may need it answered or ignored. Fix them in the files listed below, and say which ones you changed. Step 8's
`:model` `FlashingTest` advances are this task's to fix too (T04-model-knock-on).

**Test method names are binding:** use exactly the names in Verification step 2, and keep every surviving base case listed there through any rewrite. Where a name differs from the Testing Strategy's wording, step 2 wins; UAT step 3 re-checks the same names.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Tasks 03, 06 and 09 run alongside; 03 edits `:fm1` and 06 edits `:model`. A compile or test error whose path is outside
this task's `## Files` comes from a sibling mid-edit: wait and re-run. Risk 8 (whether `AndroidMidiTransport.open`
throws `MidiTransportException` on a busy device) is **not** this task's. Note it in the hand-back if you see it, and
don't edit `:app`.

## Files
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Message.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdaterTiming.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/WriteProgress.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt
- fm1/src/test/kotlin/dev/drewr/fm1/protocol/FlashChipTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/protocol/MessageTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/protocol/RecordedDeviceTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/ShortenedIdentityTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/ReturnTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/WriteProgressTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/UpdaterTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/EdgeCaseTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/FinishTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/OpenFirmwareTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/MultiPortTransport.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/ScriptedTransport.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/SimulatedFm1Test.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/FlashingTest.kt

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; F=fm1/build/test-results/test; M=model/build/test-results/test`.
Use the helpers `ran` (prints every expected test method that did not run) and `tally`, defined exactly as in task 02's
Verification setup.
Base: `:fm1` tests=124, `:model` 92, all passing (Splitter, `f5b9505`).

1. `./gradlew :fm1:test --rerun` — expected: `BUILD SUCCESSFUL`.
   red when: any `:fm1` test fails, the knock-on wasn't absorbed, or the module doesn't compile.
   at base: `BUILD SUCCESSFUL`, 124 tests.
2. `ran $F readsTheChipAFirmwareReportsAfterItsIdentity findsNoChipInAReplyWithoutOne readsNoChipFromAnIdentityThatRunsIntoIt theQueriesAreTheDocumentedOnes asksNoMoreOfAWholeIdentityThatCarriesTheChip aFoundDeviceCarriesTheChip asksForTheWholeIdentityAfterAShortenedOne asksForTheFlashChipWhenThePlainAnswerLeavesItOut keepsAThreeDigitIdentityWhenNothingMoreComes finishesWhenTheFirmwareAnswersOnAConnectionThatNeverShowsAsLost finishesWhenTheDeviceRestartsWithoutAskingToFinish aCableCutNearTheEndStopsWhenTheLoaderComesBack countsNearlyAllOfTheApplicationAreaAsNearlyWritten aSecondPassCountsOnlyOnceItIsNearlyDone identifiesTheDevice resumesAfterTheCableIsPulledMidWrite`
   — expected: no output. These are the 14 T04 cases, plus two base updater guards.
   red when: a T04 case is missing or renamed, or a base guard was deleted.
   control: `ran $F zzNoSuchCase` prints `zzNoSuchCase`.
   at base: prints the 14 new names; the two guards ran.
3. `tally $F` — expected: `tests=` ≥ 138 (124 + 14), `failures=0 errors=0 skipped=0`.
   red when: a base `:fm1` test was deleted to absorb the knock-on, or a failure or skip was recorded.
   at base: `tests=124 failures=0 errors=0 skipped=0`.
4. `./gradlew :model:test --rerun --tests '*FlashingTest'` — expected: `BUILD SUCCESSFUL`, then
   `ran $M configureAppliesEverySimulatorSetting watchLoopFollowsPlugAndUnplug flashesAFirmwareAndRecordsIt` prints
   nothing. This is T04-model-knock-on.
   red when: the extra 300 ms handshake wait broke scanning in the controller's tests.
   control: `ran $M zzNoSuchCase` prints `zzNoSuchCase`.
   at base: `BUILD SUCCESSFUL`; `FlashingTest` has 19 tests and all three names ran.
5. `grep -rnE "System\.nanoTime|currentTimeMillis|Thread\.sleep|TimeSource|Clock\." fm1/src/main/kotlin` — expected:
   no output.
   red when: the quiet-time windows read a clock instead of their own timeout durations.
   control: `printf 'val t = System.nanoTime()\n' | grep -nE "System\.nanoTime|currentTimeMillis|Thread\.sleep|TimeSource|Clock\."` prints 1 line.
   at base: no output.
   compliant hits: none (a comment naming `System.nanoTime` would match, so word it otherwise).
6. Mutation T04-chip-bytes. In `fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt`, make `handshakeReply` write
   the chip at `headerLength + 23`. Then run `./gradlew :fm1:test --rerun --tests '*FlashChipTest'`: expected `FAILED`
   on `readsTheChipAFirmwareReportsAfterItsIdentity`. Revert: green.
   red when: the byte offset is unguarded.
   at base: none. The test is written by this task.
7. Mutation T04-shortened. In `Fm1Identity.kt`, make `mayTellMore` always return `false`. Then run
   `./gradlew :fm1:test --rerun --tests '*ShortenedIdentityTest'`: expected `FAILED` on
   `asksForTheWholeIdentityAfterAShortenedOne` and `asksForTheFlashChipWhenThePlainAnswerLeavesItOut`. Revert: green.
   red when: the full-identity query is never exercised.
   at base: none. The tests are written by this task.
8. Mutation T04-return-not-lost. In `fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt`, make `lookForReturn`
   always return `null`. Then run `./gradlew :fm1:test --rerun --tests '*ReturnTest'`: expected `FAILED` on
   `finishesWhenTheFirmwareAnswersOnAConnectionThatNeverShowsAsLost` (it ends `Stalled(WRITING)`). Revert: green.
   red when: the quiet-window return check is unguarded.
   at base: none. The test is written by this task.
9. Mutation T04-nearly-written. In `fm1/src/main/kotlin/dev/drewr/fm1/updater/WriteProgress.kt`, set
   `NEARLY_WRITTEN_THRESHOLD = 0.5`. Then run `./gradlew :fm1:test --rerun --tests '*WriteProgressTest'`: expected
   `FAILED` on `countsNearlyAllOfTheApplicationAreaAsNearlyWritten`. Revert: green.
   red when: the threshold is unguarded.
   at base: none. The test is written by this task.
10. `grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Message.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdaterTiming.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/WriteProgress.kt fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt`
    — expected: no output.
    red when: a touched file still claims 0.4.5.
    control: `grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/protocol/SevenBit.kt` prints that path.
    at base: lists all six files. After task 02, only the first three are listed.
