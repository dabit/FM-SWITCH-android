status: done
# 06 — `:model` library: keep every release, V16 by hash, the save trap, stock-loader resume

Depends on: 02

## Why
Today Android downloads the list's M-VAVE entry (V16), then refuses it. Saving any official package hands back the
built-in V15. Older saved releases are deleted. After an app restart mid V16→V15, V15 isn't offered to finish the
loader. This task makes the library recognise V15 and V16 by hash and keep every release. It reads
`OfficialFirmware.official`, `V16` and `identity` from task 02. Rests on: D1, D7, D9, P-D4, P-D5, P-D6.

## Description
Implement **PLAN.md › Implementation Plan › Task 06, steps 1–7**, verbatim; they are binding. It ports iOS 38c0837 and
the V16 half of 76293d0's `FirmwareLibrary.swift`. Read `research-list.md` §2 and `research-v16.md` P1, §2 and §7.2.
The iOS source is `/home/dabit/git/FM-SWITCH` @ `fcf4a78`.

- `isSaved(entry)` and `savedCopies(entry)` are **members of `FirmwareLibrary`**, in `FirmwareLibrary.kt`. The plan's
  phrase "`FirmwareFilter.isSaved` is task 06's" means iOS's `FirmwareFilter.isSaved` lands here. Don't edit
  `FirmwareFilter.kt`: task 05 owns it, and the two tasks run side by side.
- `library.official` stays V15-only (P-D4). V16 is a removable saved item with `source = Official` and name
  "FM-1 V16".
- P-D5: a stock loader is recognised only when `hardwareModel == "FM-1"` and its version is an official release's.
  Custom loaders keep matching only their own package.
- P-D6: the trailing constructor parameter `officialOf: (FwscPackage) -> OfficialFirmware? = OfficialFirmware::official`
  is a test seam. Production never passes it.
- `otherBuild`'s meaning changes (same version only). Its `:app` callers (`HomeScreen.kt`, `MyFirmwaresScreen.kt`,
  `FirmwareListScreen.kt`) still compile and are rewired in task 10. Don't edit them here.

**What later tasks read from this one** (observation 0654): `FirmwareLibrary.savedCopies(entry)`,
`FirmwareLibrary.isSaved(entry)`, the narrowed `otherBuild(entry)`, the `officialOf` constructor parameter, and
`FirmwareLibrary.Failure.NotOfficial`'s new text.

**Test method names are binding:** use exactly the names in Verification step 2, and keep every surviving base case listed there through any rewrite. Where a name differs from the Testing Strategy's wording, step 2 wins; UAT step 3 re-checks the same names.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Tasks 03, 04 and 09 run alongside, and 04 edits `:model`'s `FlashingTest.kt`. A compile or test error whose path is
outside this task's `## Files` comes from a sibling mid-edit: wait and re-run.

## Files
- model/src/main/kotlin/dev/drewr/fmswitch/model/library/FirmwareLibrary.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/library/LibraryTest.kt

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; M=model/build/test-results/test`.
Use the helpers `ran` (prints every expected test method that did not run) and `tally`, defined exactly as in task 02's
Verification setup.
Base: `LibraryTest` 18 tests, `:model` 92, 0 failures (Splitter, `f5b9505`).

1. `./gradlew :model:test --rerun --tests '*LibraryTest'` — expected: `BUILD SUCCESSFUL`.
   red when: a library test fails or `:model` doesn't compile in this task's paths.
   at base: `BUILD SUCCESSFUL`, 18 tests.
2. `ran $M keepsEachReleaseItSaves otherBuildNeedsTheSameVersion downloadsTheOfficialEntryCheckingItsHashNotTheList refusesAnOfficialEntryThatIsntV15OrV16 downloadingV15FromTheListReturnsTheBuiltInCopy addRecognisesV16ByHash aStockLoaderReportingV16CanBeFinishedByV15 aCustomLoaderStillMatchesOnlyItsOwnPackage anyPackageCarryingTheStockLoaderCanFinishIt readsTheBuiltInCopyRatherThanSavingIt`
   — expected: no output. These are T06-keeps-releases, other-build, v16-download, not-official, v15-is-built-in,
   add-v16, stock-v16 and custom-loader-narrow, plus two base guards.
   red when: a T06 case is missing or renamed.
   control: `ran $M zzNoSuchCase` prints `zzNoSuchCase`.
   at base: prints the eight new names; the two guards ran.
3. `ran $M savesAndReplacesAnOlderRelease refusesAnOfficialEntryThatIsntV15` — expected: prints **both** names. They
   were deleted or renamed by this task: one is inverted into `keepsEachReleaseItSaves`, the other renamed to
   `…IsntV15OrV16`.
   red when: the test asserting the removed "replace older releases" behaviour still runs.
   control: step 2 printed nothing over the same results directory. That proves the directory holds this run, so
   "absent" here isn't an empty instrument.
   at base: no output. Both ran at `f5b9505`.
4. `grep -c "<testcase" $M/TEST-dev.drewr.fmswitch.model.library.LibraryTest.xml` — expected: ≥ 24 (18 − 2 + 8).
   red when: other library tests were dropped.
   at base: `18`.
5. Mutation T06-v16-download (b). In `model/src/main/kotlin/dev/drewr/fmswitch/model/library/FirmwareLibrary.kt`,
   restore `save()`'s `builtIn?.takeIf { source == StoredFirmware.Source.Official }?.let { return it }`. Then run
   `./gradlew :model:test --rerun --tests '*LibraryTest'`: expected `FAILED` on
   `downloadsTheOfficialEntryCheckingItsHashNotTheList`. Revert: green.
   red when: the save trap (Risk 3) is unguarded.
   at base: none. The test is written by this task.
6. Mutation T06-keeps-releases. In `FirmwareLibrary.kt`, restore the "replace older releases" delete loop in
   `download`. Then run `./gradlew :model:test --rerun --tests '*LibraryTest'`: expected `FAILED` on
   `keepsEachReleaseItSaves`. Revert: green.
   red when: keeping every release is unguarded.
   at base: none. The test is written by this task.
7. Mutation T06-stock-v16. In `FirmwareLibrary.kt`, revert `packagesThatCanFinish`'s stock-loader predicate to
   `loader.text == STOCK_LOADER_IDENTITY`. Then run `./gradlew :model:test --rerun --tests '*LibraryTest'`: expected
   `FAILED` on `aStockLoaderReportingV16CanBeFinishedByV15`. Revert. Then widen the predicate to any `ota-FM-1_*`:
   expected `FAILED` on `aCustomLoaderStillMatchesOnlyItsOwnPackage`. Revert: green.
   red when: P-D5 is unguarded in either direction.
   at base: none. The tests are written by this task.
8. `grep -n "only an unmodified V15 or V16 FM-1.fwsc is accepted" model/src/main/kotlin/dev/drewr/fmswitch/model/library/FirmwareLibrary.kt`
   — expected: one hit (`Failure.NotOfficial`'s text).
   red when: the refusal text still names V15 only.
   at base: no output. The text reads "(only an unmodified V15 FM-1.fwsc is accepted)".
   compliant hits: none.
9. `./gradlew :app:compileDebugKotlin` — expected: `BUILD SUCCESSFUL`. `otherBuild`'s signature is unchanged.
   red when: a library signature change broke an `:app` caller.
   at base: `BUILD SUCCESSFUL`.
