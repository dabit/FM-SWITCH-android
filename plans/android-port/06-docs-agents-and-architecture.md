status: done
# 06 — Docs: real commands in AGENTS.md, Standard-look confirmations, settled architecture

Depends on: 01, 03

## Why
AGENTS.md still says there is no Gradle project and describes Retro's confirmations as the app's, and the draft
architecture still lists choices this plan has settled; agents working in the repo need the real commands and the
decisions as made. Rests on: Look; No CI; Foreground service type; Firmware list; Scope.

## Description
Plan step 17, narrowed by the Butler on 2026-10-30: **AGENTS.md's current content is the base** (the Butler has edited
its authorship lines and Issues section — keep every line you are not told to change). In AGENTS.md change only:

1. **Commands.** Replace the paragraph that begins "The repository is new: there is no Gradle project, build, lint or
   test command yet." with the real commands, as created by task 01 (read `scripts/check.sh` and the build files, don't
   copy from the plan blindly):
   build `./gradlew :app:assembleDebug`; lint `./gradlew spotlessCheck :app:lintDebug` (format: `./gradlew spotlessApply`);
   test `./gradlew :fm1:test :model:test :app:testDebugUnitTest`; single test
   `./gradlew :fm1:test --tests 'dev.drewr.fm1.protocol.SevenBitTest'`; the check script
   `scripts/check.sh [lint|build|test|device]` (no arguments = `lint build test`; it is the project's gate — there is no
   CI, Decision "No CI"). Add the JDK notes these need: JDK 21 (`jvmToolchain(21)`, no auto-provisioning;
   `JAVA_HOME="$(mise where java@temurin-21)"` here, where system java is 25), `ANDROID_HOME` defaulting to
   `$HOME/Android/Sdk`, and that Android Studio's Gradle JDK must be set to that JDK 21 (its bundled JBR 25 fails the
   toolchain — `PLAN.md` Risks).
2. **Confirmations.** Correct the bullet that begins "**Confirmations follow `PLAN.md`:** SWITCH FIRMWARE? …": on the
   Standard look the switch confirmation is the "I've saved anything I want to keep" tick
   (`../FM-SWITCH/App/Standard/Firmwares/BeforeYouStartView.swift:157-162`) and the same identity is an info notice
   (`:129-137`) whose install passes `confirmsSameIdentity` (`:186-190`); "SWITCH FIRMWARE?" / "REINSTALL OR UPDATE?" are
   Retro's (`../FM-SWITCH/App/Views/FlashConfirmView.swift:33`). Keep "refuse V15 over V15" and the arming sentence.

Leave the probe-fragment bullet and the "isn't checked out on this machine" sentence as they are (Butler's narrowing).

In `doc/architecture.md` (untracked at base; this task's commit adds it): three modules (`:fm1`, `:model`, `:app` — the
plan's Implementation Plan conventions, which change the draft's two-module §1), and mark as **decided** the settled
`[proposal]`s: FGS `connectedDevice` + `CHANGE_NETWORK_STATE` (plan step 11), no on-device list building, `SamplePackage`
in `:fm1` main, the virtual-time rule, and §"CI [proposal]" replaced by "no CI; `scripts/check.sh` is the gate" (Decision
"No CI"). U1's findings are **not** recorded here — no human has run U1 yet; that is in task 17's deferred list.

## Files
- AGENTS.md
- doc/architecture.md

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
Task 05 runs beside this one and writes only under `fm1/src`; if step 3's compile fails in a `fm1/src/main/kotlin/dev/drewr/fm1/pkg/` path, that is task 05 mid-edit — wait and re-run.

1. `grep -c 'no Gradle project' AGENTS.md` — expected: `0`.
   red when: the "no Gradle project" paragraph survives.
   control: `git show aa66529:AGENTS.md | grep -c 'no Gradle project'` → `1`.
   at base: `1` (AGENTS.md:15, "The repository is new: there is no Gradle project, build, lint or test command yet.").
2. `grep -cF "./gradlew :fm1:test --tests 'dev.drewr.fm1.protocol.SevenBitTest'" AGENTS.md; grep -cF 'scripts/check.sh' AGENTS.md; grep -cF 'temurin-21' AGENTS.md`
   — expected: `1`, ≥ `1`, ≥ `1`.
   red when: the single-test command, the check script or the JDK note is missing.
   at base: `0`, `0`, `0`.
3. `./gradlew :fm1:test --tests 'dev.drewr.fm1.protocol.SevenBitTest'` (the documented single-test command, verbatim) —
   expected: `BUILD SUCCESSFUL`.
   red when: the documented command doesn't run (wrong module, package or class name).
4. `grep -n 'SWITCH FIRMWARE?' AGENTS.md` — expected: every line printed also contains `Retro`.
   red when: AGENTS.md still presents Retro's dialog as the app's confirmation.
   at base: AGENTS.md:54 `- **Confirmations follow \`PLAN.md\`:** SWITCH FIRMWARE? plus an "I've backed up" tick when` (no `Retro`).
5. `grep -cF "I've saved anything I want to keep" AGENTS.md; grep -c 'confirmsSameIdentity' AGENTS.md` — expected: ≥ `1`
   and ≥ `1`.
   red when: the Standard-look tick or the same-identity confirmation flag is not described.
   at base: `0` and `0`.
6. `grep -c '^## Issues' AGENTS.md; grep -c 'maintained by' AGENTS.md` — expected: `1` and `1` (the Butler's sections
   survive).
   red when: this task rewrote content outside its two changes.
   at base: `1` and `1` — AGENTS.md:82 `## Issues (kata)` and AGENTS.md:12 "This Android port is maintained by David",
   re-measured after the Butler's edit (2026-10-30); if the Butler edits AGENTS.md again before dispatch, re-measure.
7. `grep -c ':model' doc/architecture.md; grep -c 'CHANGE_NETWORK_STATE' doc/architecture.md; grep -c 'CI \[proposal\]' doc/architecture.md`
   — expected: ≥ `1`, ≥ `1`, `0`.
   red when: the doc still describes two modules, omits the FGS prerequisite, or still proposes CI.
   at base: `0`, `0`, `1` (doc/architecture.md:202 "- CI [proposal]: GitHub Actions on Ubuntu, …").
8. `grep -c '\[proposal\]' doc/architecture.md` — expected: fewer than at base, and each remaining one is a choice this plan
   did not settle (name them in your report).
   red when: settled choices are still marked as proposals.
   at base: `18`.
