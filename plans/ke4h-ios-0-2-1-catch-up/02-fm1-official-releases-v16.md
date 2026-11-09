status: pending
# 02 — `:fm1`: official releases, V16 identity, reinstalling the official firmware, error texts, flash chip IDs

Depends on: None

## Why
V16 must be known by its hash and identity as an official release that is never redistributed, and FM1Kit 0.6.0's
reinstall rule and safer error texts must reach `:fm1` before any guide, library or screen can use them. Rests on: D1,
D5, D9.

## Description
Implement **PLAN.md › Implementation Plan › Task 02, steps 1–8**, verbatim; they are binding. It ports FM1Kit fa6c8a7,
7712928 and e1fa970, plus the `:fm1` texts of 36905bb and 9a0eecf. Read `research-v16.md` §1–§2 first. FM1Kit 0.6.0 is
cloned at `/tmp/claude-1000/-home-dabit-git-FM-SWITCH-android/5e980def-0c27-4c1a-9352-a41b3172dfbb/scratchpad/FM1Kit`
(@ `53f8511`); read the Swift files from there.

The split adds one item, moved here from task 03 step 2 so that tasks 03 and 04 can run side by side:

- **Fm1FlashChip.** New `fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1FlashChip.kt`:
  `object Fm1FlashChip { const val P25Q80 = 0x856014; const val UC25HQ80 = 0xB36014; fun name(id: Int): String? }`,
  ported from FK `Sources/FM1Kit/Protocol/FM1FlashChip.swift`. JEDEC IDs are Kotlin `Int` (Conventions). Task 03's
  `FlashChipSupport` and task 04's `Fm1DeviceSummary.hasNewerFlashChip` read it.

**What later tasks read from this one** (observation 0654; each must exist with exactly these names):
`OfficialFirmware.V16`, `OfficialFirmware.identity`, `OfficialFirmware.isRedistributable`, `OfficialFirmware.official`
(the list `[V15, V16]`) and `OfficialFirmware.official(packageFile)`; `Fm1Identity.officialV16` and
`Fm1Identity.isOfficial`; `InstallCheck.canReinstallOfficial`; `InstallOptions.confirmsReinstallingOfficial` (appended
last); `Fm1UpdateError.AlreadyInstalled.identity`; `Fm1FlashChip.P25Q80`/`UC25HQ80`/`name`.

Every `:fm1` file touched gets the header `// Ported from FM1Kit 0.6.0 …` (was 0.4.5); the new file carries the same
MIT line. No `:model` or `:app` file needs a change: every new parameter is appended with a default (Conventions).

**Test method names are binding:** use exactly the names in Verification step 2, and keep every surviving base case listed there through any rewrite. Where a name differs from the Testing Strategy's wording, step 2 wins; UAT step 3 re-checks the same names.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit; the
Butler commits exactly the paths in `## Files`. Use JDK 21. Tasks 05 and 13 run alongside this one. A compile or test
error whose path is outside this task's `## Files` comes from a sibling mid-edit: wait and re-run. Only errors in this
task's paths are its red. If a symbol you need is missing, stop and report it rather than adding it outside `## Files`.

## Files
- fm1/src/main/kotlin/dev/drewr/fm1/pkg/OfficialFirmware.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1FlashChip.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/InstallCheck.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1UpdateError.kt
- fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdateEvent.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/OfficialReleaseTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/pkg/OfficialFirmwareTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/UpdaterTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/updater/EdgeCaseTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/SimulatedFm1Test.kt

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; F=fm1/build/test-results/test`.
Helpers: `ran() { d=$1; shift; comm -23 <(printf '%s\n' "$@" | sort -u) <(cat "$d"/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u); }`
prints every expected test method that did not run, and
`tally() { cat "$1"/TEST-*.xml | grep -oE '(tests|failures|errors|skipped)="[0-9]+"' | awk -F'"' '{split($1,k,"=");s[k[1]]+=$2}END{printf "tests=%d failures=%d errors=%d skipped=%d\n",s["tests"],s["failures"],s["errors"],s["skipped"]}'; }`.
Base (observed by the Splitter at `f5b9505`, 2026-11-09T09:20-06:00, `--rerun-tasks`): `:fm1` tests=124, `:model` 92,
`:app` 15, failures, errors and skips all 0.

1. `./gradlew :fm1:test --rerun` — expected: `BUILD SUCCESSFUL`.
   red when: any `:fm1` test fails, or `:fm1` doesn't compile (warnings are errors).
   at base: `BUILD SUCCESSFUL`, 124 tests, 0 failures.
2. `ran $F onlyV15MayBeIncludedInAnApp v16IsTreatedAsTheOfficialFirmwareToo reinstallsTheOfficialFirmwareOverALookAlikeOnceConfirmed v16RefusesItsOwnIdentity errorTextsSayKeepItOnAndNameMidimonFirst v15IsNotReinstalled bundledV15IsTheOfficialFirmware`
   — expected: no output. These are T02-official-list, T02-v16-reinstall, T02-reinstall-look-alike, T02-sim-refuses-v16
   and T02-texts, plus two base guards.
   red when: a Testing Strategy case for task 02 is missing or renamed, or a base guard was deleted.
   control: `ran $F zzNoSuchCase` prints `zzNoSuchCase`.
   at base: prints the five new names (none exist at `f5b9505`); the two guards ran.
3. `tally $F` — expected: `tests=` ≥ 129 (124 + 5), `failures=0 errors=0 skipped=0`.
   red when: a base test was deleted, or a failure or skip was recorded.
   at base: `tests=124 failures=0 errors=0 skipped=0`.
4. `grep -rn "Fm1Identity.officialV15" fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/InstallCheck.kt`
   — expected: no output (both now use `isOfficial`).
   red when: the simulator or `InstallCheck.of` still treats only V15 as official.
   control: the same pattern over `fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt` prints the `officialV15`
   declaration (`val officialV15 = parse("FM-1_015")!!`).
   at base: 2 hits: the simulator's `currentIdentity == Fm1Identity.officialV15` and `InstallCheck`'s
   `device == packageFile.identity && device == Fm1Identity.officialV15 -> InstallKind.REINSTALL`.
   compliant hits: none (a KDoc that mentions `Fm1Identity.officialV15` would match, so word comments otherwise).
5. `grep -n "stays in update mode" fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1UpdateError.kt` — expected: no output
   (36905bb texts replace it).
   red when: a 0.4.5 text that invites switching the FM-1 off survived.
   control: `grep -c "stays in update mode" plans/ke4h-ios-0-2-1-catch-up/PLAN.md` prints ≥ 1.
   at base: 3 hits, in the `Stalled`, `Disconnected` and `UnexpectedFinish` texts.
   compliant hits: none.
6. `grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/pkg/OfficialFirmware.kt fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1FlashChip.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/InstallCheck.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1UpdateError.kt fm1/src/main/kotlin/dev/drewr/fm1/updater/UpdateEvent.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt`
   — expected: no output.
   red when: a touched file still claims FM1Kit 0.4.5.
   control: `grep -L "Ported from FM1Kit 0.6.0" fm1/src/main/kotlin/dev/drewr/fm1/protocol/SevenBit.kt` prints that path
   (an untouched file keeps 0.4.5).
   at base: lists the seven existing files (`Fm1FlashChip.kt` doesn't exist yet; grep reports it missing on stderr).
7. Mutation T02-official-list. In `fm1/src/main/kotlin/dev/drewr/fm1/pkg/OfficialFirmware.kt`, set
   `isRedistributable = true` on `V16`. Then run `./gradlew :fm1:test --rerun --tests '*OfficialReleaseTest'`: expected
   `FAILED` on `onlyV15MayBeIncludedInAnApp`. Revert and re-run: green.
   red when: the redistributable guard doesn't bite.
   at base: none. The test is written by this task.
8. Mutation T02-v16-reinstall. In `fm1/src/main/kotlin/dev/drewr/fm1/updater/InstallCheck.kt`, change `InstallCheck.of`'s
   REINSTALL branch back to `device == Fm1Identity.officialV15`. Then run
   `./gradlew :fm1:test --rerun --tests '*OfficialReleaseTest'`: expected `FAILED` on
   `v16IsTreatedAsTheOfficialFirmwareToo`. Revert: green.
   red when: V16-over-V16 could reach staging as SAME_IDENTITY unnoticed.
   at base: none. The test is written by this task.
9. Mutation T02-reinstall-look-alike. In `fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1Updater.kt`, delete the
   `canReinstallOfficial && confirmsReinstallingOfficial` fall-through in the install path. Then run
   `./gradlew :fm1:test --rerun --tests '*UpdaterTest'`: expected `FAILED` on
   `reinstallsTheOfficialFirmwareOverALookAlikeOnceConfirmed`. Revert: green.
   red when: the look-alike reinstall is unguarded.
   at base: none. The test is written by this task.
10. Mutation T02-texts. In `fm1/src/main/kotlin/dev/drewr/fm1/updater/Fm1UpdateError.kt`, restore the 0.4.5 `Stalled`
    text. Then run `./gradlew :fm1:test --rerun --tests '*EdgeCaseTest'`: expected `FAILED` on
    `errorTextsSayKeepItOnAndNameMidimonFirst`. Revert: green.
    red when: the texts are unguarded.
    at base: none. The test is written by this task.
11. `./gradlew :model:compileTestKotlin :app:compileDebugUnitTestKotlin` — expected: `BUILD SUCCESSFUL`. Appended
    defaults mean no consumer changes.
    red when: a signature change broke a `:model` or `:app` caller, which this task must then fix and add to `## Files`.
    at base: `BUILD SUCCESSFUL`.
