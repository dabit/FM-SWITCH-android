status: done
# 09 — `:fm1` updater

Depends on: 07

## Why
The updater is the state machine that actually installs and resumes firmware — find exactly one FM-1, check the
package, stage, wait for the loader, serve the write, verify the identity — ported from FM1Kit so it behaves as the iOS
app's does, with FM1Kit's error text and without `FM1Advice`. Rests on: Protocol source; Errors; Scope.

## Description
Plan step 6. Read research.md §2.7–2.10 and §3.8 first; the **virtual-time** and **semantic-drift** rules bind here
most of all.

**Getting FM1Kit:** clone it **outside the repository** and check the commit:
`git clone --depth 1 --branch 0.4.5 https://github.com/ocean/FM1Kit "$SCRATCH/FM1Kit" && git -C "$SCRATCH/FM1Kit" rev-parse HEAD`
→ `88b609cd8c631b884d977b7954cb0b21351f2f04`. Paths are under `Sources/FM1Kit/` unless they start with `Tests/`.

**Ports:** `Updater/UpdaterTiming.swift:9-39` (`standard` at `:45`), `Updater/UpdateEvent.swift:2-25`,
`Updater/WriteProgress.swift:15-110`, `Updater/InstallCheck.swift:2-89`, `Updater/FM1UpdateError.swift:7-253` (the
in-scope cases **with their description text, `errorDescription` at `:96` onwards**; the deferred cases listed in
research §2.10 are omitted), `Updater/FM1Updater.swift:77-384` (`findAll` `:77`, `findOnly` `:94`, `identify` `:110`,
`waitFor` `:117`, `handshake` `:131`, `install` `:162`, `resume` `:238-247`, `write` `:251`, `serve` `:323`, the
`finishSettle` loop `:374-384`), `Updater/FM1Updater+Checks.swift:15-98`, `Updater/FM1Updater+Refusals.swift:6-17`.
**Not ported:** `FM1Advice` (Decision "Errors"), FM1Kit's `InstallProgress` (the app's is task 12's), `FM1DeviceSummary`,
voices, backup, bootloader installer.

- `Fm1UpdateError` is a sealed class whose `message` is FM1Kit's description text ("confirm the switch" `:105`, "already
  reports" `:111`). `InstallKind.REINSTALL` means **already installed → refuse with `AlreadyInstalled`**;
  `SAME_IDENTITY` is the one that reinstalls, and only with `confirmsSameIdentity`.
- `Fm1Updater(transport, timing, scope)`; calls serialised with a `Mutex`. Idle limits without a clock: `serve` wraps
  "wait for the next **read request**" in `withTimeoutOrNull(idleLimit)` around a loop of `receive(1 s)` that skips
  non-requests and checks `isLost` on each empty receive — so other messages don't reset idle (FM1Kit `:323-372`).
- `resume(package)` accepts any loader for the model and does **not** check the armed prefix (`:238-247`).
- `WriteProgress`: `java.util.BitSet` for written/checked over the application area, not `Set<Int>`.
- Cancellation is cooperative; a `CancellationException` is always rethrown, never mapped to an `Fm1UpdateError`.
- Each ported file starts with the FM1Kit notice line used in task 03.

**Test harness.** Use `runTest`, `backgroundScope` for the simulator, FM1Kit's `fastTiming`/`fastOptions`
(`Tests/FM1KitTests/UpdaterTests.swift:12-35`) or standard timings. Copy `ScriptedTransport` (task 07) for the scripted
cases, **but it has one port and one connection** (`PackageCheckTests.swift:120-134`), so it cannot carry
`neverSendsToOtherGear` (an FM-1 port **and** an `Arturia KeyStep` port, with per-port open/send records) or the
two-FM-1 cases (two simulators on one transport). For those, add `MultiPortTransport` in
`fm1/src/test/kotlin/dev/drewr/fm1/updater/` — it merges several transports' ports and records, per port id, whether it
was opened and every byte sent.

**Tests** — every case under `### :fm1 — step 6` in `PLAN.md`'s Testing Strategy, plus
`readsOutsideTheRegionLeaveProgressUnchanged` / `readsAcrossTheRegionCountOnlyTheirInsidePart` from the step-4 list
(they test `WriteProgress`). Where the plan says "see above (same method)", it is one method. Method names:
- `UpdaterTest`: `identifiesTheDevice`, `installsAndVerifiesTheIdentity`, `resumesAfterTheCableIsPulledMidWrite`,
  `finishesFromRecoveryMode`, `refusesARequestBeyondTheImage` (`UpdaterTests.swift:84-97`), `reportsEachStepInOrder`,
  `resumeNeedsTheLoader`, `v15IsNotReinstalled`, `refusesAPackageForAnotherModel`, `rejectsPackagesForOtherModels`,
  `standardTimingsAddNoPauses` (the 13 in-scope values of research §2.8), `neverSendsToOtherGear`.
- `FinishTest` (`PackageCheckTests.swift:179`, `:189-198`): `answersARepeatedFinishBeforeClosing`,
  `finishOfTheOtherPhaseStops`, `finishWithLengthOtherThan8Stops`.
- `OpenFirmwareTest` (`Tests/FM1KitTests/OpenFirmwareTests.swift:18-238`): `buildIDsReadHexInEitherCase`,
  `commandsRoundTrip`, `capabilitiesRoundTripAndRejectOtherLayouts`, `recognisesOpenFirmwareIdentities`,
  `readsAnArmedOpenFirmwareLoader`, `armsOpenFirmwareAndChecksTheBuildAfterwards`, `armsWithThePackageHashWithoutABuildID`,
  `theRunningBuildIsAReinstall`, `leavingOpenFirmwareIsASwitch`, `aBusyDeviceIsNotArmed`, `aRefusedArmingStopsBeforeStaging`,
  `openFirmwareThatDoesNotAnswerIsNotUpdated`, `otherFirmwaresAreNotAskedAboutCapability`,
  `officialReleasesCarryTheirBuildIDs`, `switchingNeedsConfirmingAndSendsNothingUntilThen`,
  `aDeviceIsCheckedWithoutChangingIt`, `refusesToInstallWithTwoFM1sConnected`, `refusesToResumeWithTwoLoadersWaiting`,
  `findAllReportsEveryDevice` (the plan's `twoFm1sAreRefused` ×2 and `findAllListsBoth`, under FM1Kit's names).
- `EdgeCaseTest` (`Tests/FM1KitTests/EdgeCaseTests.swift:18-88,322,348,384`): `otherMessagesDontKeepAStalledPhaseWaiting`,
  `ignoresRepliesThatAreNotHandshakes`, `otherMessagesAreNotReadRequests`, `responsesWithAWrongLengthOrChecksumAreRejected`,
  `storesAnIdentityAsItsText`, `hashesTheFileNotTheImage`, `recognisesOnlyOfficialReleases`,
  `aCustomFirmwaresOwnIdentityNeedsConfirming`, `sameIdentityNeedsConfirming`, `errorTextIsFm1Kits`.
- `WriteProgressTest` (`Tests/FM1KitTests/WriteProgressTests.swift`, a 64-block region): `aForwardsPassRisesSteadilyToTheEnd`,
  `aBackwardsPassRisesSteadilyRatherThanJumping`, `headerRereadsAtTheStartAreStillWriting`,
  `shortRereadsAtTheEndAreStillWriting`, `aSecondPassIsReportedAsChecking`, `aRereadAtTheEndOfAPassIsStillWriting`,
  `newGroundDuringCheckingGoesBackToWriting`, `aLoaderThatSkipsPartOfTheImageEndsShortOfOne`, `aShortFinalReadIsCounted`,
  `aBackwardsLoaderReportsSteadyProgress`, `aCheckingLoaderReportsCheckingAfterWriting`,
  `readsOutsideTheRegionLeaveProgressUnchanged`, `readsAcrossTheRegionCountOnlyTheirInsidePart`.

## Files
- fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdaterTiming.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdateEvent.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/WriteProgress.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/InstallCheck.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1UpdateError.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1UpdaterChecks.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/MultiPortTransport.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/UpdaterTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/FinishTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/OpenFirmwareTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/EdgeCaseTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/WriteProgressTest.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`R=fm1/build/test-results/test`. Siblings 10 and 11 write only under `app/`.

1. `./gradlew :fm1:test --rerun` — expected: `BUILD SUCCESSFUL` in well under a minute of test time (virtual time).
   red when: any `:fm1` test fails, warns, or a test hangs until `runTest`'s timeout (a real-time wait leaked).
2. `comm -23 <(printf '%s\n' identifiesTheDevice installsAndVerifiesTheIdentity resumesAfterTheCableIsPulledMidWrite finishesFromRecoveryMode refusesARequestBeyondTheImage reportsEachStepInOrder resumeNeedsTheLoader v15IsNotReinstalled refusesAPackageForAnotherModel rejectsPackagesForOtherModels standardTimingsAddNoPauses neverSendsToOtherGear answersARepeatedFinishBeforeClosing finishOfTheOtherPhaseStops finishWithLengthOtherThan8Stops buildIDsReadHexInEitherCase commandsRoundTrip capabilitiesRoundTripAndRejectOtherLayouts recognisesOpenFirmwareIdentities readsAnArmedOpenFirmwareLoader armsOpenFirmwareAndChecksTheBuildAfterwards armsWithThePackageHashWithoutABuildID theRunningBuildIsAReinstall leavingOpenFirmwareIsASwitch aBusyDeviceIsNotArmed aRefusedArmingStopsBeforeStaging openFirmwareThatDoesNotAnswerIsNotUpdated otherFirmwaresAreNotAskedAboutCapability officialReleasesCarryTheirBuildIDs switchingNeedsConfirmingAndSendsNothingUntilThen aDeviceIsCheckedWithoutChangingIt refusesToInstallWithTwoFM1sConnected refusesToResumeWithTwoLoadersWaiting findAllReportsEveryDevice otherMessagesDontKeepAStalledPhaseWaiting ignoresRepliesThatAreNotHandshakes otherMessagesAreNotReadRequests responsesWithAWrongLengthOrChecksumAreRejected storesAnIdentityAsItsText hashesTheFileNotTheImage recognisesOnlyOfficialReleases aCustomFirmwaresOwnIdentityNeedsConfirming sameIdentityNeedsConfirming errorTextIsFm1Kits aForwardsPassRisesSteadilyToTheEnd aBackwardsPassRisesSteadilyRatherThanJumping headerRereadsAtTheStartAreStillWriting shortRereadsAtTheEndAreStillWriting aSecondPassIsReportedAsChecking aRereadAtTheEndOfAPassIsStillWriting newGroundDuringCheckingGoesBackToWriting aLoaderThatSkipsPartOfTheImageEndsShortOfOne aShortFinalReadIsCounted aBackwardsLoaderReportsSteadyProgress aCheckingLoaderReportsCheckingAfterWriting readsOutsideTheRegionLeaveProgressUnchanged readsAcrossTheRegionCountOnlyTheirInsidePart | sort -u) <(cat $R/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u)`
   — expected: no output (all 57 methods ran).
   red when: a step-6 case is missing or renamed.
   control: add `zzNoSuchCase` → prints `zzNoSuchCase`.
   at base: none — written by this task.
3. `grep -ho 'failures="[0-9]*"' $R/TEST-*.xml | sort -u` — expected: only `failures="0"`.
   red when: any class recorded a failure.
   at base: none — written by this task.
4. `grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.|ContinuousClock|TimeSource\.Monotonic' fm1/src/main/kotlin/dev/drewr/fm1/updater/*.kt`
   — expected: no output (FM1Kit's `ContinuousClock` deadline at `FM1Updater.swift:377` must become a virtual-time wait).
   red when: the updater measures real time.
   control: `printf 'val d = TimeSource.Monotonic.markNow()\n' | grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.|ContinuousClock|TimeSource\.Monotonic'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
5. `grep -nE 'Advice' fm1/src/main/kotlin/dev/drewr/fm1/updater/*.kt` — expected: no output (`FM1Advice` not ported).
   red when: advice text was ported against the Decision.
   control: `grep -c 'Advice' "$SCRATCH/FM1Kit/Sources/FM1Kit/Updater/FM1Advice.swift"` → non-zero.
   at base: none — written by this task.
6. `grep -L 'Ported from FM1Kit 0.4.5' fm1/src/main/kotlin/dev/drewr/fm1/updater/*.kt` — expected: no output.
   red when: a ported file lacks FM1Kit's attribution.
   control: `grep -L 'Ported from FM1Kit 0.4.5' fm1/build.gradle.kts` → prints it.
   at base: none — written by this task.
7. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt`, `install`: map `InstallKind.REINSTALL` to an
   install instead of `AlreadyInstalled`; `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.updater.UpdaterTest'` →
   `FAILED` on `v15IsNotReinstalled`; revert, re-run → green.
   red when: the name-drift trap goes unguarded.
8. Mutation — `Fm1Updater.kt`, `serve`: restart the `withTimeoutOrNull(idleLimit)` for every received message;
   `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.updater.EdgeCaseTest'` → `FAILED` on
   `otherMessagesDontKeepAStalledPhaseWaiting` (by its virtual `withTimeout(30 s)`, not a hang); revert, re-run → green.
   red when: chatter can keep a stalled phase alive.
9. Mutation — `Fm1Updater.kt`, `findAll`: remove the `PortMatching.isProbeable` filter;
   `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.updater.UpdaterTest'` → `FAILED` on `neverSendsToOtherGear`;
   revert, re-run → green.
   red when: SysEx could reach other MIDI gear unnoticed.
10. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/updater/WriteProgress.kt`, `record`: compute the event before adding the
    read to `written`; `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.updater.WriteProgressTest'` → `FAILED` on
    `aForwardsPassRisesSteadilyToTheEnd`; revert, re-run → green.
    red when: progress ordering is unguarded.
