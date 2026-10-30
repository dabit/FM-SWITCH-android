status: pending
# 12 — `:model` device and install state

Depends on: 08, 09

## Why
The screens show what the device controller decides: which steps an install takes, whether each step resumes or
installs, what must be confirmed, how progress moves and what a failure says. Porting `DeviceController` and its states
as plain JVM code lets the iOS `FlashingTests` run here under virtual time against the simulated FM-1. Rests on: Scope;
Errors; V15-over-other-bootloaders refusal; Look.

## Description
Plan step 8. Read research.md §3.1, §3.4, §3.5 and §3.8 first. Virtual-time rule binds: everything runs on the injected
`scope`.

**Ports** (`../FM-SWITCH` @ 67ee8c2): `App/Model/DeviceController.swift:15-260,354-376` and
`App/Model/DeviceController+States.swift:5-120`, `App/Model/InstallReadiness.swift:16-124`,
`App/Model/InstallProgress.swift:7-96` (the **app's** type: shares 0–0.10/0.12/0.85/0.97/1.0, spread across steps,
`Tracker` never goes backwards and freezes on failure), `App/Model/DeviceSummary.swift:8-77` (the **app's** plain-words
summary, not `FM1DeviceSummary`), and the Before-you-start enabling rule from
`App/Standard/Firmwares/BeforeYouStartView.swift:178-194` as a pure `BeforeYouStartState`.

- `DeviceController(library, history, settings, scope, transportFactory)`; `StateFlow`s for identity, ports, isScanning,
  job, log, transportError; a public way to append a line to the log (task 13 feeds Android device callbacks into it).
  `configure(kind)`, `scan()` (3 one-second retries), `plan(target, requiresOfficial, alwaysViaOfficial)`, `check(item)`,
  `flash(steps, confirmsSwitchingFirmware, confirmsSameIdentity)`, `resume()`, `dismissFlash()`. Each step: `identify()`;
  **if any loader answers → `resume`, else `install`** (`DeviceController.swift:208-215`). Cable pull for the simulator at
  **0.45**; m-bootloader option = `bootloaderVersion "1.2"` + `comparesBootArea`.
- Named semantics: `FlashJob.canResume(after)` is true for `Disconnected(WRITING)`, `Stalled(WRITING)` **and
  `LoaderNotFound`** (`DeviceController+States.swift:74-81`); `isRefusal` = BootloaderMismatch | PackageRefused;
  `mightBeRefused` = `Stalled(STAGING)`. `InstallReadiness.isSwitching` is true for **any chain of more than one step** or a
  found `switchingFirmware` (`InstallReadiness.swift:73-75`); only the **first** step is checked (`:115-123`);
  `isSameIdentity` = !isSwitching && found sameIdentity; `isAlreadyInstalled` = found reinstall && one step.
  `FlashPhase.verifying` ↔ `UpdateEvent.Checking` ↔ stage "Checking".
- Install survival doesn't depend on Android device callbacks: `Fm1Updater.waitFor` polls.
- Failure copy: `DeviceController.swift:354-369` refusal messages ("Install V15 first" / "Install MIDIMON 0.4 or LOOM-8
  first"); otherwise the `Fm1UpdateError` message.

**Tests** — `### :model — step 8` in `PLAN.md`'s Testing Strategy, plus `aPackageThatAcceptsV15IsntFlagged` from the
step-7 list (it tests `InstallReadiness`). `FlashingTest` runs `DeviceController` on `backgroundScope` with the simulated
transport and **standard** timings (virtual). Method names:
- `FlashingTest` (`AppTests/FlashingTests.swift:49-140`; refusal cases `AppTests/StartUpTests.swift:52-90`):
  `flashesAFirmwareAndRecordsIt`, `flashesThroughALoaderThatReadsBackwardsAndVerifies`, `resumesAfterTheCableIsPulled`,
  `refusesTheSameIdentityUnconfirmed`, `installsTheSameIdentityOnceConfirmed`, `refusesToSwitchUnconfirmed`,
  `eachStepResumesWhenALoaderAnswers`, `loaderNotFoundIsResumable`, `planGoesViaOfficialUnlessAlreadyOnIt`,
  `aRefusedInstallIsExplainedPlainly`, `aRefusalWithMVavesStartUpSuggestsV15First`,
  `aStallBeforeTheInstallStartsMightBeARefusal`, `bothRefusalsAreRecognised`.
- `ReadinessTest`: `aChainIsAlwaysSwitching`, `onlyTheFirstStepIsChecked`, `alreadyInstalledBlocksStart`,
  `finishingUpdateCanStart`, `startUpMustBeChosenFirst`, `aPackageThatAcceptsV15IsntFlagged`
  (`AppTests/StartUpTests.swift:32-37`).
- `StandardLookTest` (`AppTests/StandardLookTests.swift:20-75`): `progressRunsThroughTheStagesInOrder`,
  `progressSpreadsAcrossTheSteps`, `trackerNeverGoesBackwards`, `summarySaysWhatTheFm1IsDoing`.
- `BeforeYouStartStateTest` (the plan's single `BeforeYouStartState` case, one method per rule):
  `switchingNeedsTheSavedTick` (reddened by enabling without `hasSaved` when switching), `untestedNeedsItsTick`,
  `confirmationsFollowTheTicks` (`confirmsSwitchingFirmware = isSwitching && hasSaved`,
  `confirmsSameIdentity = isSameIdentity`, the button titles of `BeforeYouStartView.swift:182-184`).

## Files
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/DeviceController.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/FlashJob.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/FlashPhase.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/InstallReadiness.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/InstallProgress.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/DeviceSummary.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/device/BeforeYouStartState.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/FlashingTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/ReadinessTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/StandardLookTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/device/BeforeYouStartStateTest.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`R=model/build/test-results/test`. This task runs alone in its wave; tasks 10 and 11 may still be finishing under `app/`,
which `:model` does not compile.

1. `./gradlew :model:test --rerun` — expected: `BUILD SUCCESSFUL` (task 08's tests stay green), test time in seconds
   (virtual time).
   red when: a test fails, warns, or runs in real time / hangs to `runTest`'s timeout.
2. `comm -23 <(printf '%s\n' flashesAFirmwareAndRecordsIt flashesThroughALoaderThatReadsBackwardsAndVerifies resumesAfterTheCableIsPulled refusesTheSameIdentityUnconfirmed installsTheSameIdentityOnceConfirmed refusesToSwitchUnconfirmed eachStepResumesWhenALoaderAnswers loaderNotFoundIsResumable planGoesViaOfficialUnlessAlreadyOnIt aRefusedInstallIsExplainedPlainly aRefusalWithMVavesStartUpSuggestsV15First aStallBeforeTheInstallStartsMightBeARefusal bothRefusalsAreRecognised aChainIsAlwaysSwitching onlyTheFirstStepIsChecked alreadyInstalledBlocksStart finishingUpdateCanStart startUpMustBeChosenFirst aPackageThatAcceptsV15IsntFlagged progressRunsThroughTheStagesInOrder progressSpreadsAcrossTheSteps trackerNeverGoesBackwards summarySaysWhatTheFm1IsDoing switchingNeedsTheSavedTick untestedNeedsItsTick confirmationsFollowTheTicks | sort -u) <(cat $R/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u)`
   — expected: no output (all 26 methods ran).
   red when: a step-8 case is missing or renamed.
   control: add `zzNoSuchCase` → prints `zzNoSuchCase`.
   at base: none — written by this task.
3. `grep -ho 'failures="[0-9]*"' $R/TEST-*.xml | sort -u` — expected: only `failures="0"`.
   red when: any class recorded a failure.
   at base: none — written by this task.
4. `grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|Instant\.now|runBlocking|Dispatchers\.' model/src/main/kotlin/dev/drewr/fmswitch/model/device/*.kt`
   — expected: no output.
   red when: the controller escapes the injected scope.
   control: `printf 'scope.launch(Dispatchers.Default) {}\n' | grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|Instant\.now|runBlocking|Dispatchers\.'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
5. Mutation — `model/src/main/kotlin/dev/drewr/fmswitch/model/device/DeviceController.kt`, the per-step run: always call
   `install` (drop the loader check); `./gradlew :model:test --rerun --tests '*FlashingTest'` → `FAILED` on
   `eachStepResumesWhenALoaderAnswers`; revert, re-run → green.
   red when: resume-on-loader is unguarded.
6. Mutation — `model/src/main/kotlin/dev/drewr/fmswitch/model/device/FlashJob.kt`, `canResume`: drop `LoaderNotFound`;
   `./gradlew :model:test --rerun --tests '*FlashingTest'` → `FAILED` on `loaderNotFoundIsResumable`; revert, re-run →
   green.
   red when: the iOS `canResume` semantics drift silently.
7. Mutation — `model/src/main/kotlin/dev/drewr/fmswitch/model/device/InstallReadiness.kt`, `isSwitching`: only a found
   `SWITCHING_FIRMWARE`; `./gradlew :model:test --rerun --tests '*ReadinessTest'` → `FAILED` on `aChainIsAlwaysSwitching`;
   revert, re-run → green.
   red when: a multi-step chain could skip the "saved anything" tick.
