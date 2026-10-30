status: pending
# 02 — Licence notices and the bundled V15

Depends on: 01

## Why
The protocol is a port of FM1Kit (MIT) and `ApplicationArea` a port of M-Flasher (MIT), so both notices must ship,
and M-VAVE's V15 is bundled unmodified with its Apache-2.0 notice; the `:fm1` and `:model` tests read that one copy.
Rests on: Protocol source; Scope.

## Description
Plan step 2, plus its `LicenceAssetsTest` (Testing Strategy, `:app` section), moved here from step 13 so this task
verifies itself.

**Getting FM1Kit:** clone it **outside the repository** (your scratch dir — never into the working tree) and check the
commit: `git clone --depth 1 --branch 0.4.5 https://github.com/ocean/FM1Kit "$SCRATCH/FM1Kit" && git -C "$SCRATCH/FM1Kit" rev-parse HEAD`
→ must print `88b609cd8c631b884d977b7954cb0b21351f2f04`. Nothing from the clone is committed except the notices below.

- Copy FM1Kit `LICENSE` (MIT, "Copyright (c) 2026 Drew Robinson", line 3) to `fm1/LICENSE-FM1Kit.txt` and to
  `app/src/main/assets/licences/FM1Kit-LICENSE.txt`, byte for byte.
- The M-Flasher notice is FM1Kit `Sources/FM1Kit/Package/ApplicationArea.swift:1-19` (attribution, then "MIT License,
  Copyright (c) 2026 Julius Vitkauskas" and the permission text). Write it to
  `app/src/main/assets/licences/M-Flasher-LICENSE.txt` with the leading `// ` (or `//`) comment marker removed from each
  line and nothing else changed. Task 05 also puts it at the top of `ApplicationArea.kt`.
- Copy `../FM-SWITCH/App/Resources/Firmware/FM-1_015.fwsc` **unmodified** to `app/src/main/assets/firmware/FM-1_015.fwsc`
  and `FM-1_015-LICENCE.txt` beside it.
- Root `NOTICE.md`: the app's own licence status (none chosen yet — say so, don't invent one), FM1Kit MIT (the `:fm1`
  module), M-Flasher MIT (`ApplicationArea.kt` only), V15 Apache-2.0 (the asset only).
- `app/src/test/kotlin/dev/drewr/fmswitch/LicenceAssetsTest.kt` — one method per assertion: `FM1Kit-LICENSE.txt` contains
  "Drew Robinson" and "MIT"; `M-Flasher-LICENSE.txt` contains "Vitkauskas"; `firmware/FM-1_015-LICENCE.txt` exists. Paths
  are relative to the module dir (`src/main/assets/…`; Gradle runs unit tests with the module dir as cwd).
  **reddened by:** deleting a notice file.

## Files
- fm1/LICENSE-FM1Kit.txt
- app/src/main/assets/licences/FM1Kit-LICENSE.txt
- app/src/main/assets/licences/M-Flasher-LICENSE.txt
- app/src/main/assets/firmware/FM-1_015.fwsc
- app/src/main/assets/firmware/FM-1_015-LICENCE.txt
- NOTICE.md
- app/src/test/kotlin/dev/drewr/fmswitch/LicenceAssetsTest.kt

## Verification
Run from the repo root. Before any `./gradlew` step: `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
A compile error reported in a path outside this task's `## Files` is a sibling task mid-edit (tasks 03 and 04 run beside
this one): wait and re-run; only failures in this task's paths are this task's red.

1. `sha256sum app/src/main/assets/firmware/FM-1_015.fwsc; stat -c %s app/src/main/assets/firmware/FM-1_015.fwsc` —
   expected: `db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a` and `699956`.
   red when: the asset was modified, truncated or is another file.
   at base: none in this repo — written by this task; the source `../FM-SWITCH/App/Resources/Firmware/FM-1_015.fwsc`
   gave `db1642b2…edb8a` and `699956` (measured 2026-10-30).
2. `sha256sum app/src/main/assets/firmware/FM-1_015-LICENCE.txt` — expected:
   `ea85b580ebc7f5d84d120a192c3c90b74df6c365a29dd6718562e81783e07297`.
   red when: the V15 licence was edited or is missing.
   at base: none in this repo; the source file's hash is `ea85b580…07297` (measured 2026-10-30).
3. `sha256sum fm1/LICENSE-FM1Kit.txt app/src/main/assets/licences/FM1Kit-LICENSE.txt` — expected: both
   `e5f36c927a6848f690912cd553e426ce7935b4390b9743e55bae236f934a1eee`.
   red when: either copy differs from FM1Kit 0.4.5's `LICENSE`.
   at base: none in this repo; FM1Kit 0.4.5 `LICENSE` hashed to `e5f36c92…1eee` (measured 2026-10-30).
4. `grep -c 'Julius Vitkauskas' app/src/main/assets/licences/M-Flasher-LICENSE.txt; grep -c '^//' app/src/main/assets/licences/M-Flasher-LICENSE.txt`
   — expected: `1`, then `0`.
   red when: the notice is missing its copyright line, or still carries Swift comment markers.
   control: `sed -n 1,19p "$SCRATCH/FM1Kit/Sources/FM1Kit/Package/ApplicationArea.swift" | grep -c '^//'` → `19`.
   at base: none — written by this task.
5. `for w in FM1Kit M-Flasher ApplicationArea Apache-2.0 FM-1_015; do grep -q -- "$w" NOTICE.md || echo "MISSING $w"; done`
   — expected: no output.
   red when: NOTICE.md omits one of the four notices or the asset it covers.
   control: the same loop with `zzNoSuchWord` appended to the word list → prints `MISSING zzNoSuchWord`.
   at base: none — written by this task.
6. `./gradlew :app:testDebugUnitTest --rerun --tests 'dev.drewr.fmswitch.LicenceAssetsTest'` — expected:
   `BUILD SUCCESSFUL`; then `grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-dev.drewr.fmswitch.LicenceAssetsTest.xml`
   prints `failures="0"` with `tests` ≥ 3.
   red when: the test fails, or finds fewer than three methods.
   at base: none — written by this task.
7. Mutation (proves the test reads the real files): `mv app/src/main/assets/licences/M-Flasher-LICENSE.txt /tmp/mf.txt`,
   re-run step 6 → `FAILED` naming the M-Flasher method; then `mv /tmp/mf.txt app/src/main/assets/licences/M-Flasher-LICENSE.txt`
   and re-run step 6 → green.
   red when: the test stays green with the notice gone.
8. `find . -path ./.git -prune -o -iname 'fm1kit' -print` — expected: no output (no clone in the tree).
   red when: the FM1Kit clone landed inside the repository.
   control: `find "$SCRATCH" -maxdepth 1 -iname 'fm1kit'` → prints your clone's path.
   at base: no output (measured 2026-10-30).
