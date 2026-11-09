# Catch the firmware list and V16 support up with iOS 0.2.1

- Tracker: kata
- Project: fm-switch-android
- Issue: ke4h
- Issue URL: none (kata is local; ref fm-switch-android#ke4h)
- Created: 2026-11-09
- Started: 2026-11-09
- Base commit: origin/android-port @ f5b950537b5e498ce5e89a96206b31ea4e63a166
- Source app: ../FM-SWITCH @ fcf4a7881ed7f5d5229a77edbc6a81cdc164d424 (0.2.1); previous port baseline 67ee8c2
- Protocol source: FM1Kit 0.6.0 @ 53f8511df7eeba8de0d338609985a163a22473a2 (previous port baseline 0.4.5 @ 88b609c)
- Summary: Port every iOS firmware-list change since 67ee8c2, and V16 support (FM1Kit 0.6.0 identity and install path, iOS warnings reworded for Android).

## Decisions

- **D1** Support V16 like iOS, asked and answered (David, AskUserQuestion 2026-11-09: "Support V16 like iOS").
- **D2** Track in kata as ke4h, created per AGENTS.md's search-then-create workflow (Butler, not asked).
- **D3** Branch from origin/android-port, the remote's default branch; upstream unset so nothing pushes by accident (Butler default, not asked).
- **D4** Android has only the Standard look (doc/architecture.md:5-6; plans/android-port/PLAN.md:307), so iOS's Retro-side changes don't apply (research-list §0).
- **D5** The V16 path takes the FM1Kit 0.4.5→0.6.0 changes that V16 needs: fa6c8a7, the leaving guides (0172d0e, 9a0eecf, 31f12a5), and e1fa970. It also takes the ones any install needs to be safe on current FM-1s: 36905bb (don't switch off during pass 2; no "Checking" stage), 7712928, fc809ca (finish when the new firmware answers), and cee1a84 (full-identity query and flash chip ID). Mac-only FM1Rescue work, a63df5b, e41ce90, 0cf72d5, 07e8d6e, dd3d84a and bf4a3e4 (choosing between FM-1s) stay out. Butler default, not asked; reasons are in research-v16 §1.
- **D6** Factory-voice restore stays out. After V16→V15, the warnings say plainly that this phone can't clear the mono data or put back the factory sounds. Butler default, not asked.
- **D7** Resume after an app restart mid V16→V15: treat an official loader answering `ota-FM-1_0NN` as the stock loader. This is an Android fix only; any upstream report is David's call. Butler default, not asked.
- **D8** Starring in My firmwares, a gap since the first port, stays out. Butler default, not asked.
- **D9** V16 is never bundled or committed (FM1Kit fa6c8a7: not redistributable). Tests use synthetic packages and the published hash constants.
- **D10** The JSON decoders become more lenient than iOS (`coerceInputValues` on both), because Android has no fallback to building the list itself. Butler default, not asked.
- **D11** The iOS app has been MPL-2.0 since aac72a9 (2026-11-08), and these ports derive from it. The NOTICE/licence decision is David's: the plan records the facts in NOTICE.md and chooses no licence.

Planner decisions (all "Planner default, not asked"; moved here from the Implementation Plan at split time):

- **P-D1 MIDIMON goes on first automatically, like iOS.** iOS's `FixedFirmware` (`S/App/Model/SwitchingGuide.swift:141-190`)
  and `SwitchingCheck.firstStep` are ported: when the guide says the running firmware can't install the pick
  (`LeavingRoute.THROUGH_FIXED_FIRMWARE`), a saved MIDIMON/LOOM-8 with the fixed check, or MIDIMON downloaded from the
  list, goes on first; the post-refusal "Install MIDIMON first" button carries on the stopped install
  (`installFirst`). Reason: iOS's `SwitchingWords.warnings` promise "FM-SWITCH puts MIDIMON on first", and V16 from
  Felucca 1.6.1/TransitFM1 is refused at the header (RV §1, §7.3); without the chain the Android copy would be false.
  If David prefers not to, drop task 07 step 5 and task 12 step 3's install-first button, and use FM1Kit's own
  `LeavingGuide.warnings` text instead of `SwitchingWords.warnings`.
- **P-D2 `FlashPhase.Verifying` is renamed `SecondPass`** (label `"WRITING PASS 2"`) and counts as
  `InstallProgress.Stage.Installing`; `Stage.Checking` is deleted (36905bb; `S/App/Model/InstallProgress.swift` diff).
- **P-D3 `FeluccaUpdateCode` (`KM/settings/Settings.kt:34-72`) is deleted**, superseded by leaving guides; the catalogue-id
  fallback when a package can't be read is dropped (iOS has none: `SwitchingCheck.read` returns an unread check,
  `S/App/Model/InstallReadiness.swift:77`).
- **P-D4 `library.official` stays V15-only** (`KM/library/FirmwareLibrary.kt:82-86`, as iOS `FirmwareLibrary.swift:90-92`).
  V16 is a removable saved item with `source = Official`. "Via official" always means V15. Nobody "fixes" this.
- **P-D5 Stock-loader resume** recognises `ota-FM-1_0NN` only for the official releases FM1Kit knows: a loader whose
  `hardwareModel == "FM-1"` and whose `version` equals the version of an `OfficialFirmware.official` identity (15, 16).
  Custom loaders such as `ota-FM-1_916` keep matching only their own package.
- **P-D6 Test seam for official recognition.** `FirmwareLibrary` gains a trailing constructor parameter
  `officialOf: (FwscPackage) -> OfficialFirmware? = OfficialFirmware::official` and `LeavingDestination.installing`
  takes `official: OfficialFirmware? = OfficialFirmware.official(package)`; production never passes them. Without the
  V16 file nothing else can drive the V16 branches.
- **P-D7 No "start on V16" simulator switch** (iOS 0.2.1 has none, RV P5). Simulator UAT reaches the V16 states by
  installing V16 on the simulated FM-1 and then V15, in one session.
- **P-D8 `Fm1Updater.summary()`** follows Android's `identify()` (first FM-1 found), not FK's `findOnly` (choosing between
  FM-1s is out of scope).

## References

Full reports: `research-list.md` (firmware list) and `research-v16.md` (V16 and FM1Kit 0.6.0), both in this folder. They are the source for every task. `FK` = FM1Kit 0.6.0 cloned outside the repo (`git clone https://github.com/ocean/FM1Kit && git checkout 53f8511`). `../FM-SWITCH` is @ fcf4a78.

Spot-checked by the Butler: FirmwareLibrary.kt:168-173 and :236-239, PublishedList.kt:53-57, LibraryTest.kt:179, fm1 InstallCheck.kt:32, FK HARDWARE_TESTING.md:919-936.

**List**
- `../FM-SWITCH/App/Catalogue/FirmwareCatalogue.swift:12-19,95,102,266-267,302-303,359-360`; `HubFlashChip.swift:8-33`; `FirmwareDescription.swift:7-21`
- `../FM-SWITCH/App/Model/FirmwareFilter.swift:27-30,94-114,126-173`; `AppSettings.swift:86-98,171-175,227-230`; `EntryDescriptions.swift:4-14,27`; `FirmwareLibrary+Releases.swift:1-15`; `FlashChipWords.swift:8-36`
- `../FM-SWITCH/App/Standard/Firmwares/FilteredFirmwareList.swift:8-115`; `FirmwareListView.swift:55-70,108-128,241-260`; `MyFirmwaresView.swift:12-45,77-82`; `FirmwarePageView.swift:81-82,114-143,207-215,250-276,338-351`; `FirmwarePageView+Saving.swift:6-70`; `FlashChipNotice.swift:5-29`
- iOS tests: `AppTests/FirmwareOrderTests.swift:23-36`, `KindFilterTests.swift:28-36`, `FlashChipTests.swift:19-45`, `FirmwareDescriptionTests.swift:8-20`, `LibraryTests.swift` (keepsEachReleaseItSaves)
- Android: `model/.../catalogue/Catalogue.kt:41-67,73,105-143`; `FirmwareFilter.kt:61-95`; `CatalogueStore.kt:46,64,93`; `PublishedList.kt:38,53-57`; `model/.../library/FirmwareLibrary.kt:108-125,236-239,406`; `model/.../settings/Settings.kt:74-106`; `app/.../data/DataStoreAppSettings.kt:76-78,108-143`; `app/.../ui/firmwares/FirmwareListScreen.kt:91-108,252-368,380,422`; `MyFirmwaresScreen.kt:51-63`; `FirmwarePageScreen.kt:153-240,318-330`; `app/.../ui/install/BeforeYouStartSheet.kt:79,216-218`; `strings_firmwares.xml:9-10,15-22,36`; `strings_install.xml:10`; `strings_settings.xml:7-8`; tests `FilterTest.kt`, `PublishedListTest.kt` + `firmwares-sample.json`, `CatalogueStoreTest.kt`, `LibraryTest.kt:179`, `app/src/test/.../ui/theme/ContrastTest.kt`
- Live list copy at plan time: /tmp/claude-1000/fw.json (499 entries, built 2026-11-09T02:10:54Z). Trim a fixture from https://fm-1.drewr.dev/firmwares.json.

**V16 and protocol**
- `FK/Sources/FM1Kit/Package/FwscPackage.swift:212,232-262`; `Protocol/FM1Identity.swift:24-35,155-212`; `Package/LeavingGuide.swift:1-285`; `Updater/InstallCheck.swift:40-113`; `Updater/FM1Updater.swift:150-160`; `Updater/FM1Updater+Writing.swift:1-76`; `Updater/FM1UpdateError.swift:98-293`; `Transport/SimulatedFM1.swift:321`; `HARDWARE_TESTING.md:915-980` (stages R and S)
- FK tests to port: `Tests/FM1KitTests/OfficialReleaseTests.swift`, `ReturnTests.swift`, `ProtocolTests.swift:125-210`, `Tests/FM1RescueTests/LeavingGuideTests.swift`
- `../FM-SWITCH/App/Model/InstallReadiness.swift:16-124,146-170`; `SwitchingGuide.swift:10-190`; `StartUp.swift:1-45`; `DeviceController.swift:171-190,273-321`; `DeviceController+States.swift:55-72,112-150`; `FirmwareLibrary.swift:90-92,147-169,184-215`; `OfficialFirmware+Saved.swift:1-8`; `App/Catalogue/PackageDiscovery.swift:75-100`; `App/Views/SwitchingWords.swift:9-98`; `App/Standard/Firmwares/BeforeYouStartView.swift:111-122`; `App/Standard/Install/InstallOutcomeViews.swift:25-50`; tests `AppTests/SwitchingGuideTests.swift`, `InstallFinishTests.swift`, `OfficialPackageTests.swift`
- Android fm1: `pkg/OfficialFirmware.kt:6-33`; `protocol/Fm1Identity.kt:25-30,43`; `updater/InstallCheck.kt:28-47`; `updater/Fm1UpdaterChecks.kt:29`; `updater/Fm1Updater.kt:82,152-177,190-192,252-314`; `updater/Fm1UpdateError.kt:13-26,72-142`; `pkg/ApplicationArea.kt:96-109`; `transport/SimulatedFm1.kt:26-45,212`; `updater/WriteProgress.kt`
- Android model/app: `library/FirmwareLibrary.kt:166-176,207-235,254-293,383-389`; `device/InstallReadiness.kt:27-122`; `device/DeviceController.kt:142-309`; `device/FlashJob.kt:7-15`; `device/FlashHistory.kt:14-19`; `device/DeviceSummary.kt:73`; `device/InstallProgress.kt:13,42`; `settings/Settings.kt:11-71`; `app/.../ui/install/BeforeYouStartSheet.kt:206-256`; `OutcomeScreens.kt:60-93`; `strings_install.xml:17`; `HomeScreen.kt:88`
- Docs: `doc/architecture.md`, dated 2026-10-30 against 67ee8c2 and FM1Kit 0.4.5; stale and to be updated. Past plan: `plans/android-port/` (08 model data layer, 15 firmware list/page/mine, 17 UAT).

## Implementation Plan

### Conventions (bind every task)

- **Roots.** `A` = `/home/dabit/git/FM-SWITCH-android` (base `f5b9505`); `S` = `/home/dabit/git/FM-SWITCH` @ `fcf4a78`;
  `FK` = FM1Kit 0.6.0 @ `53f8511`, cloned **outside the repo** (`git clone https://github.com/ocean/FM1Kit "$SCRATCH/FM1Kit" &&
  git -C "$SCRATCH/FM1Kit" checkout 53f8511`; a copy already exists at
  `/tmp/claude-1000/-home-dabit-git-FM-SWITCH-android/5e980def-0c27-4c1a-9352-a41b3172dfbb/scratchpad/FM1Kit`).
  `KF/` = `A/fm1/src/main/kotlin/dev/drewr/fm1/`, `KFT/` = `A/fm1/src/test/kotlin/dev/drewr/fm1/`,
  `KM/` = `A/model/src/main/kotlin/dev/drewr/fmswitch/model/`, `KMT/` = `A/model/src/test/kotlin/dev/drewr/fmswitch/model/`,
  `KA/` = `A/app/src/main/kotlin/dev/drewr/fmswitch/`, `KAT/` = `A/app/src/test/kotlin/dev/drewr/fmswitch/`,
  `RES/` = `A/app/src/main/res/values/`. `RL` = `plans/ke4h-ios-0-2-1-catch-up/research-list.md`, `RV` = `research-v16.md`.
- **Gate.** `JAVA_HOME="$(mise where java@temurin-21)" scripts/check.sh` (lint build test). Lint runs with
  `warningsAsErrors = true` in all three modules (`app/build.gradle.kts:50-52`, `model/build.gradle.kts:14-16`,
  `fm1/build.gradle.kts:13-15`), so **a task that stops using a string resource deletes it in the same task**
  (UnusedResources), and `app` compiles with `allWarningsAsErrors` (no deprecated or un-opted-in experimental calls).
  Every task leaves the gate green; a producer task that changes a signature makes the *mechanical* compile fix in its
  consumers in the same task and says so.
- **Headers.** Every `:fm1` file a task touches gets `// Ported from FM1Kit 0.6.0 …` (was 0.4.5); new `:fm1` files carry
  the same MIT line. New `:model`/`:app` files ported from `S` say `// Ported from FM-SWITCH App/….swift.`
- **Port by meaning, Kotlin idioms.** Swift `UInt32` JEDEC IDs are Kotlin **`Int`** everywhere (24-bit values fit; one
  choice for every task). Swift `Codable` on FM1Kit types does **not** come across: `:fm1` has no serialization plugin
  (`fm1/build.gradle.kts` plugins = kotlin.jvm + lint); persistence is a `:model` surrogate (task 07 step 8).
- **Virtual time** (`doc/architecture.md` §1 "Virtual-time rule"): no clock reads or `Thread.sleep` in `:fm1`/`:model`.
  Where FK compares `ContinuousClock.now`, the port measures time by the durations of its own `withTimeoutOrNull`/`delay`
  windows (spelled out in task 04).
- **Copy.** iOS strings verbatim where the premise holds on a phone; reworded only where it says Mac/iPhone, and then from
  the appendix (§ Verbatim copy) — never invented in a task. Text a test asserts lives in Kotlin constants in `:model`
  (precedent `KM/settings/Settings.kt:22-32`); composable-only labels live in `RES/strings_*.xml`, one owner per file.
- **Retro is not ported** (Decisions; `doc/architecture.md:5-6`). Device choice (`S/App/Model/DeviceChoice.swift`,
  `SimulatedPair`), install log files (`InstallLogFile.swift`), Backup & rescue, factory voices, starring: out.
- **V16 bytes never enter the repo.** Tests use synthetic packages (`KF/testing/SamplePackage.kt`) and the published
  constants. Real V16 recognition is exercised only through the app's own download in UAT (task 14).
- **Visual review protocol (host-only; a Codex sandbox has no emulator).** Before committing any task that changes a
  screen (09–12), the orchestrator runs, from `A`:
  ```
  "$ANDROID_HOME/emulator/emulator" -avd fmswitch-smoke -no-window -no-audio -no-snapshot-save &
  adb wait-for-device; adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
  ./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb shell cmd uimode night no        # then again with `yes` for the dark set
  adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml build/shots/ui.xml   # find tap bounds
  adb shell input tap X Y ; adb shell input swipe X1 Y1 X2 Y2 300
  mkdir -p build/shots && adb exec-out screencap -p > build/shots/tNN-<screen>-<light|dark>.png
  ```
  then **reads every PNG** (the Read tool shows images) against the task's "Screens" list before committing. The AVD
  exists (`~/.config/.android/avd/fmswitch-smoke.avd`, found 2026-11-09); android-port verified U0 on it
  (`plans/android-port/17-uat.md:204`). The simulated FM-1 is Settings ▸ Connection ▸ Simulated FM-1. `build/` is
  git-ignored. Behaviour-only checks have missed layout defects before (observation 0656; android-port `406c949`).

### Planner decisions

P-D1–P-D8 are in `## Decisions`.

### Shared surfaces and owners (observation 0654)

Reconciled with the task files at split time; the task files' `## Files` are authoritative.

| Shared file | Producer (only writer) | Consumers |
|---|---|---|
| `KF/**` | 02, then 03 and 04 side by side (file-disjoint: 03 owns `pkg/`+`testing/`, 04 owns `protocol/Fm1Message.kt`, `Fm1Identity.kt`, `updater/`, `transport/`) | 05–12 |
| new `KF/protocol/Fm1FlashChip.kt` | 02 (moved from 03 step 2 so 04 needn't wait for 03) | 03, 04, 11 |
| `KF/testing/SamplePackage.kt`, `KF/pkg/FwscPackage.kt` (`loaderBytes`) | 03 | 07, 08 tests |
| `KM/catalogue/Catalogue.kt`, `FirmwareFilter.kt`, `PublishedList.kt`, `CatalogueStore.kt`, new `HubFlashChip.kt`/`FlashChipWords.kt`/`FirmwareDescription.kt` | 05 | 07, 09, 10, 11, 12 |
| `HubFlashChip.support` (an extension in `KM/device/InstallReadiness.kt`) | 07 (moved from 05 so 05 needs nothing from `:fm1`) | 07 |
| `KM/settings/Settings.kt`, `KMT/settings/StartUpTest.kt` | 05 (`StartUp.V16`, keys), then 07 (deletes `FeluccaUpdateCode`) | 07–12 |
| `KA/data/DataStoreAppSettings.kt` | 05 | — |
| `KA/ui/settings/SettingsScreen.kt`, `RES/strings_settings.xml` | 05 (mechanical: `StartUp.V16` row in the exhaustive `startUpName`), then 09 | — |
| `KM/library/FirmwareLibrary.kt` (incl. `isSaved`, `savedCopies`) | 06 | 10, 11 |
| new `KM/device/SwitchingGuide.kt`, `SwitchingWords.kt`; `InstallReadiness.kt`, `BeforeYouStartState.kt`, `KM/history/FlashHistory.kt` | 07 | 08, 12 |
| `KA/ui/install/OutcomeScreens.kt`, `InstallPresentation.kt`, `KAT/ui/install/InstallPresentationTest.kt` | 07 (mechanical: `FeluccaUpdateCode.wayRound`, `fromTarget`), then 12 | — |
| `FlashJob.kt`, `FlashPhase.kt`, `InstallProgress.kt`, `DeviceController.kt`, `DeviceSummary.kt` | 08 | 11, 12 |
| `KMT/device/FlashingTest.kt` | 04 (time advances), then 08 | — |
| `KMT/device/SwitchingGuideTest.kt` | 07, then 08 (controller cases) | — |
| `KA/ui/theme/Palette.kt`, `FmSwitchTheme.kt` (`StandardColors.danger`), `KA/ui/components/ButtonStyles.kt`, new `KA/ui/firmwares/FlashChipNotice.kt`, `DescriptionText.kt` | 09 | 10, 11, 12 |
| `FirmwareListScreen.kt`, `MyFirmwaresScreen.kt`, new `FilteredFirmwareList.kt`, `KA/ui/home/HomeScreen.kt` | 10 | — |
| `RES/strings_firmwares.xml` | 10, then 11 (sequential) | — |
| `FirmwarePageScreen.kt`, new `FirmwarePageSaving.kt`, new `RES/strings_firmware_page.xml` | 11 | — |
| `RES/strings_install.xml`, `BeforeYouStartSheet.kt` | 07, then 08 (`InstallingOverlay.kt` too), then 12 | — |
| `KA/AppModel.kt` | 12 | — |
| `doc/architecture.md`, `NOTICE.md`, `AGENTS.md` | 13 | — |

Waves are derived from each task file's `Depends on:`; see `## Tasks`.

---

### Phase 0

#### Task 01 — Record the base gate (no code)
**Split:** no task file. The Splitter observed the base itself (2026-11-09T09:20-06:00, `f5b9505`, `--rerun-tasks`:
`:fm1` 124, `:model` 92, `:app` 15 tests, 0 failures/errors/skips), and every task's `at base:` lines carry it.
1. On branch `ke4h-ios-0-2-1-catch-up` at `f5b9505`, run
   `JAVA_HOME="$(mise where java@temurin-21)" scripts/check.sh` and then
   `./gradlew :fm1:test :model:test :app:testDebugUnitTest --rerun-tasks` (the first run's test tasks may be
   `UP-TO-DATE`), and record in `plans/ke4h-ios-0-2-1-catch-up/base-gate.md`: date, commit, the last line of each run, and
   the per-module test counts from `*/build/test-results/**/TEST-*.xml` (`grep -ho 'tests="[0-9]*"' … | paste`).
   Planner probe 2026-11-09T08:59:25-06:00 at `f5b9505`: `scripts/check.sh` → `Checks passed: lint build test`, exit 0,
   but every test task was `UP-TO-DATE`, so the fresh counts are this task's job.
2. Every later task's "suite passes" compares against those counts (counts may only grow, except where a task names a
   test it deletes or inverts).

### Phase A — `:fm1` (FM1Kit 0.4.5 → 0.6.0, scoped)

#### Task 02 — Official releases, V16 identity, reinstalling the official firmware, error texts
Ports fa6c8a7, 7712928, e1fa970, and the `:fm1` texts of 36905bb and 9a0eecf.
1. `KF/pkg/OfficialFirmware.kt` (current: `data class OfficialFirmware(name, size, sha256, buildId)`, `:6-11`;
   `known = listOf(V15, OPEN_V12)` `:30`). Superset: add `val identity: Fm1Identity` and
   `val isRedistributable: Boolean` (FK `Package/FwscPackage.swift:206-212`); `V15` → identity `Fm1Identity.officialV15`,
   redistributable `true`; new `V16` (`"FM-1 V16"`, `718_196`,
   `"d017fb65e70aca97ee29d944470c3515a74581df5c0576bf70de917c7b2b61d5"`, buildId = same hex, identity
   `Fm1Identity.officialV16`, `false`) (FK `:227-239`); `OPEN_V12` identity `Fm1Identity.of("FM-1", 8_000_012)`, `false`;
   `known = listOf(V15, V16, OPEN_V12)`; new `val official = listOf(V15, V16)`; new
   `fun official(packageFile: FwscPackage): OfficialFirmware? = matching(packageFile)?.takeIf { it in official }`
   (iOS `S/App/Model/OfficialFirmware+Saved.swift:5-7`, moved into `:fm1` so `:model` and tests share one definition).
2. `KF/protocol/Fm1Identity.kt` (`officialV15` at `:43`): add `val officialV16 = parse("FM-1_016")!!` and
   `val isOfficial: Boolean get() = this == officialV15 || this == officialV16` (FK `Protocol/FM1Identity.swift:24-35`).
   Mind the companion initialisation order: `officialV15`/`officialV16` are companion vals and `isOfficial` reads them
   lazily (a getter), so no cycle.
3. `KF/updater/InstallCheck.kt`: `:32` → `device == packageFile.identity && device.isOfficial -> InstallKind.REINSTALL`;
   add `val canReinstallOfficial: Boolean get() = kind == InstallKind.REINSTALL && capability == null`
   (FK `Updater/InstallCheck.swift:60`). `InstallOptions` current signature
   `(confirmsSwitchingFirmware: Boolean = false, confirmsSameIdentity: Boolean = false, buildId: BuildId? = null)` →
   append `val confirmsReinstallingOfficial: Boolean = false` **at the end** (every caller is named or ≤2 positional:
   `grep -rn "InstallOptions(" fm1 model app` 2026-11-09).
4. `KF/updater/Fm1Updater.kt:190-192`: before the `REINSTALL -> throw AlreadyInstalled` branch, let
   `check.canReinstallOfficial && options.confirmsReinstallingOfficial` fall through to staging (FK
   `Updater/FM1Updater.swift` 7712928 hunk `case .reinstall where check.canReinstallOfficial && …: break`).
5. `KF/updater/Fm1UpdateError.kt`: texts verbatim from FK `Updater/FM1UpdateError.swift` @53f8511:
   `AlreadyInstalled` gains the official branch (`:107-112`, use `Fm1Identity.parse(identity)?.isOfficial == true`),
   `BootloaderMismatch` (`:194-205`), `PackageRefused` (`:206-216`), `Stalled(WRITING)`/`Disconnected(WRITING)` (36905bb
   hunk, `:228-241`), `UnexpectedFinish(WRITING)` (`:257-262`). Expose `val identity: String` on `AlreadyInstalled`
   (needed by task 08's failure handling and the tests).
6. `KF/transport/SimulatedFm1.kt:212`: `currentIdentity == Fm1Identity.officialV15` → `currentIdentity?.isOfficial == true`
   (RV P5). Comment on `:245`: drop "the loader stays in update mode" wording only if it's user-visible (it's a log line;
   leave it).
7. `KF/updater/UpdateEvent.kt`: doc comments for `Writing`/`Checking` per 36905bb (`UpdateEvent.swift` hunk). No API change.
8. Tests (see Testing Strategy T02-*): new `KFT/pkg/OfficialReleaseTest.kt`; additions in `KFT/updater/UpdaterTest.kt`,
   `KFT/transport/SimulatedFm1Test.kt`, `KFT/updater/EdgeCaseTest.kt` (texts). Update `KFT/pkg/OfficialFirmwareTest.kt`
   only if it asserts `known.size`.

#### Task 03 — Update codes, leaving guides, flash chip support (+ sample-package enabling step)
**Split:** step 2 (`Fm1FlashChip`) moved to task 02.
Ports 0172d0e, 9a0eecf, 31f12a5 and cee1a84's package half. FM1Kit, not FM1Rescue (RV §1).
1. **Enabling (tests need it; no seam exists today).** `KFT/pkg/SampleApplicationArea.kt` is test-only and hard-codes
   product `"FM-1_378001"` (`:23`), so neither other products nor `:model` tests can build a firmware whose code carries
   markers. Move its builder into `KF/testing/SamplePackage.kt` (main source set, as architecture.md §1 decided for
   `SamplePackage`): `fun firmwareWithCode(code: ByteArray, product: String = "FM-1_378001"): ByteArray` (file bytes) and
   `fun firmwareWithAppRecord(code, address, length, product)`; keep `SampleApplicationArea.packageWith*` as thin
   delegates so `ApplicationAreaTest` is untouched. Also add `fun withLoader(product: String, loader: ByteArray): ByteArray`
   = `file(image(listOf(Part.flash(), Part.loader(loader.size)), contents = mapOf(1 to loader)), product)` (the
   `contents` map already exists, `SamplePackage.kt:33-50`) — task 08 builds a synthetic `FM-1_016` that carries
   **V15's real loader** so its `loaderSha256 == STOCK_LOADER_SHA256`. Add `val FwscPackage.loaderBytes: ByteArray`
   (public, in `KF/pkg/FwscPackage.kt`, from `container.loader`) so a `:model` test can lift V15's loader from the bundled
   file (`System.getProperty("fm1.v15")`, precedent `KMT/library/LibraryTest.kt:25-30`). VERIFY-AT-EXECUTION: that
   `loaderSha256` is the SHA-256 of exactly those bytes (read `FwscPackage.kt` init); if not, expose whatever range it
   hashes.
2. New `KF/protocol/Fm1FlashChip.kt`: `object Fm1FlashChip { const val P25Q80 = 0x856014; const val UC25HQ80 = 0xB36014;
   fun name(id: Int): String? }` (FK `Protocol/FM1FlashChip.swift`).
3. New `KF/pkg/LeavingGuide.kt`, port of FK `Package/LeavingGuide.swift:1-285`:
   `enum class UpdateCode(val raw: String) { M_VAVE("mVave"), FELUCCA_ORIGINAL_CHECK("feluccaOriginalCheck"),
   FELUCCA_FIXED_CHECK("feluccaFixedCheck"), RECOGNISES_V15("recognisesV15"), OWN_LOADER("ownLoader"), UNKNOWN("unknown") }`;
   `enum class Fm1BootArea(val raw)` {V15, V16, OPEN_FIRMWARE, M_BOOTLOADER}; `enum class LeavingDestination` {V15, V16,
   OPEN_FIRMWARE, FELUCCA_PLATFORM, M_BOOTLOADER} with internal `name`/`bootArea`; `enum class LeavingRoute` {DIRECT,
   THROUGH_FIXED_FIRMWARE, UNCERTAIN}; `data class LeavingGuide(firmwareName, identity: Fm1Identity, updateCode,
   bootArea: Fm1BootArea?)` with `companion fun from(packageFile, name: String? = null, bootArea: Fm1BootArea?,
   installedBy: UpdateCode? = null): LeavingGuide?` (Swift's failable init, `:117-131`), `route(to)`, `warnings`,
   `instructions(to)`, `FIXED_FIRMWARES = "MIDIMON 0.4 or LOOM-8"`; strings verbatim (`:171-218,246-259`).
   `val FwscPackage.updateCode: UpdateCode?` and `internal val FwscPackage.bootAreaBrought` (`:262-284`).
4. `KF/pkg/ApplicationArea.kt:96-109`: `firmwareCode` and the marker constants are `private` there; make
   `firmwareCode` `internal` (keep it in this file) and add `OWN_LOADER_MARKER = "LOADER NOT STAGED"`;
   `refusesV15OverOtherBootloaders` becomes `updateCode == UpdateCode.FELUCCA_ORIGINAL_CHECK` (FK
   `ApplicationArea.swift` @53f8511 `refusesV15OverOtherBootloaders`). Keep `usesFeluccaUpdateCode`.
5. New `KF/pkg/FlashChipSupport.kt` (FK `Package/FwscPackage+FlashChip.swift`): `enum class FlashChipSupport
   { BOTH_CHIPS, FIRST_CHIP_ONLY, NO_CHECK }`, `val FwscPackage.flashChipSupport: FlashChipSupport?` searching
   `firmwareCode` for each ID as 4 little-endian bytes.
6. Tests T03-*: new `KFT/pkg/LeavingGuideTest.kt` (FK `Tests/FM1RescueTests/LeavingGuideTests.swift`, all 8; the
   `canBeSavedWithTheFM1` case moves to task 07 as a `:model` round trip), new `KFT/pkg/FlashChipSupportTest.kt` (FK
   `FlashChipSupportTests.swift`; the installer case uses the installer sample `PackageCheckTest` already builds — find it
   with `grep -n "NotFirmware\|installer" KFT/pkg/PackageCheckTest.kt`), and one new case in `ApplicationAreaTest`.

#### Task 04 — Full identity and flash chip in the handshake; finish when the firmware answers; simulator options
Ports cee1a84's protocol half and fc809ca.
1. `KF/protocol/Fm1Message.kt`: `val FULL_IDENTITY_QUERY = command(identityCommand, byteArrayOf(1))` (FK
   `FM1Message.swift:16-21`; `command(…)` exists at `:43`).
2. `KF/protocol/Fm1Identity.kt`: current `fun handshakeReply(): ByteArray` (`:25-30`) → superset
   `fun handshakeReply(flashChipId: Int = 0): ByteArray` writing the 3 big-endian bytes at body offset
   `headerLength + 24` when `text.length < 24` (FK `:165-177`); `companion fun flashChipId(inHandshakeReply: ByteArray): Int?`
   (`:145-154`); `internal val mayBeShortened`, `internal fun mayTellMore(thanAnswerWith: Int?)` (`:107-128`).
3. `KF/updater/UpdaterTiming.kt` (current 15 params, `:8-24`; `UpdaterTest.fastTiming()` passes the first 12
   positionally): append **after** `handshakeAttempts`: `val fullIdentityWait: Duration = 300.milliseconds`,
   `val returnCheckAfter: Duration = 5.seconds`.
4. `KF/updater/Fm1Updater.kt`: `FoundDevice(connection, identity)` (`:27-30`) → `FoundDevice(connection, identity,
   flashChipId: Int? = null)`; `handshake()` (`:152-177`) becomes `handshakeAnswer()` returning identity + chip and, when
   `identity.mayTellMore(chip)`, sends `FULL_IDENTITY_QUERY` and waits up to `fullIdentityWait` for a reply whose text
   starts with the short one (FK `FM1Updater.swift` cee1a84 hunk `:80-121`); both `findUnlocked` and `findAllUnlocked`
   carry the chip. New `data class Fm1DeviceSummary(identity, flashChipId: Int?) { val hasNewerFlashChip get() =
   flashChipId == Fm1FlashChip.UC25HQ80 }` and `suspend fun summary(): Fm1DeviceSummary?` (P-D8: `findUnlocked`, like
   `identify()` `:47-50`).
5. fc809ca: `ServeOutcome` gains `var returned: FoundDevice? = null`; `serve(…)` gains
   `lookingAfterQuiet: Duration? = null` and `lookForReturn: suspend () -> FoundDevice? = { null }`; split
   `writeUnlocked` (`:252-314`) into `serveLoader` + `returnedDevice` as FK `Updater/FM1Updater+Writing.swift:1-76`.
   **Quiet time without a clock:** restructure the request wait in `serve` (`:346-363`) into windows: while waiting,
   `withTimeoutOrNull(window)` where `window = minOf(lookingAfterQuiet ?: idleLimit, idleLimit - quiet)`; a window that
   returns null adds `window` to `quiet` (that much virtual time has passed) and, when `lookingAfterQuiet != null`, calls
   `lookForReturn()`; any read request resets `quiet = ZERO`; `quiet >= idleLimit` ends the phase as today. Never read
   `System.nanoTime`/`currentTimeMillis`. `lookForReturn` = `progress.isNearlyWritten` then
   `findOnlyUnlocked { it == packageFile.identity }` (FK `:31-35`; already inside the mutex, so the *Unlocked* variant),
   catching `Fm1UpdateError.MultipleDevices` (and any non-cancellation exception) as `null`, as FK's `try?` does.
   After a lost connection with `isNearlyWritten`: `waitFor(returnSearch) { it == loader.identity || it == packageFile.identity }`
   (FK `:41-50`).
6. `KF/updater/WriteProgress.kt`: `val isNearlyWritten` and `NEARLY_WRITTEN_THRESHOLD = 0.95`; `checkedFraction` as FK
   `WriteProgress.swift` fc809ca hunk `:377-403`; doc comments per 36905bb.
7. `KF/transport/SimulatedFm1.kt` `Options` (`:26-45`): add `flashChipId: Int = 0`, `shortensIdentity = false`,
   `reportsFlashChipOnlyWhenAsked = false`, `restartsWithoutFinishing = false`,
   `keepsConnectionThroughRestart = false`. Handshake handling → `answerHandshake(asksForFullIdentity, connection)` (FK
   `SimulatedFM1+Commands.swift` cee1a84 hunk); `write()` skips the finish exchange when `restartsWithoutFinishing`
   (`:261-268`); `SimulatedConnection.isLost` (`:369-370`) = `closed || (!device.options.keepsConnectionThroughRestart &&
   !device.isCurrent(generation))` while `send` still throws `PortGone` when not current (FK `SimulatedFM1+Transport.swift`).
8. **Knock-on in `:model` tests (this task fixes them):** every handshake with a non-official identity now also sends the
   full-identity query and waits `fullIdentityWait` (300 ms) when the answer carries no chip — the default simulated
   `FM-1_900` included. `KMT/device/FlashingTest.kt` advances virtual time by 1 ms before asserting identity
   (`configureAppliesEverySimulatorSetting`, `:104-140`); bump those advances to
   `UpdaterTiming.standard.fullIdentityWait + 1.milliseconds` (or `advanceUntilIdle()` where the precedent allows). Run
   `:model:test` in this task.
9. Tests T04-*: `KFT/protocol/MessageTest.kt` (or new `FlashChipTest.kt`/`ShortenedIdentityTest.kt`), new
   `KFT/updater/ReturnTest.kt`, `KFT/updater/WriteProgressTest.kt`, `KFT/transport/SimulatedFm1Test.kt`.

### Phase B — `:model` producers

#### Task 05 — Catalogue fields, lenient decoding, orders/scopes, list settings, words
**Split:** `HubFlashChip.support` moved to task 07; this task adds the mechanical `StartUp.V16` row to `SettingsScreen.kt`
(`startUpName` is an exhaustive `when`) with `settings_startup_v16` in `strings_settings.xml`.
Ports 8b84ab1 (app side), 4b4b66e, ecc88d6 and b45179e (model side), bfab3cb + d171b96, 3a5467c (words), e32be0e
(description). RL §1, §3, §4, §5, §6, §7, §8.
1. `KM/catalogue/Catalogue.kt` (`CatalogueEntry` `:40-68`, superset): add `val installs: Int? = null`,
   `val flashChip: HubFlashChip? = null`. New `@Serializable enum class HubFlashChip { @SerialName("old") Old,
   @SerialName("new") New, @SerialName("any") Any }` with `support` (→ `FlashChipSupport`, `S/App/Catalogue/HubFlashChip.swift:17-23`)
   and `savesOn` (`:26-32`). `Listing.MUTANT_CLOUD = 8` (`:139-143`). `listedOn` (`:105-118`): "The Firmware Hub",
   "fm1-editor.com", "FM1 Resource Hub", "mutant.cloud" joined " + " (`S/App/Model/EntryDescriptions.swift:4-14`);
   `packageSource` Hub → "The Firmware Hub mirror" (`:27`). Every new enum-typed property stays `? = null` (the coercion
   in step 2 needs a default to coerce to).
2. `KM/catalogue/PublishedList.kt:53-57` and `KM/catalogue/CatalogueStore.kt:93`: `Json { ignoreUnknownKeys = true;
   coerceInputValues = true }` (Decision: more lenient than iOS). Make `CatalogueStore`'s `json` `internal` so the
   round-trip test reaches the production instance.
3. `KM/catalogue/FirmwareFilter.kt`: `FirmwareOrder` (`:78-95`) gains `MostInstalled` (`installs ?: -1` descending, stable
   on index), a `val label` is **not** added (labels stay in XML); `companion fun firstUncounted(entries): Int?`
   (`S/App/Model/FirmwareFilter.swift:133,160-172`). `FirmwareScope` gains `storedValue` (`"all"`, `"kind:<k>"`,
   `"builtFromSource"`), `companion fun fromStored(value: String?): FirmwareScope?`, and `fun offered(in: List<FirmwareScope>):
   FirmwareScope` (falls back to `All`) (`:94-114`). `FirmwareFilter.isSaved` is task 06's (it needs the library).
4. New `KM/catalogue/FlashChipWords.kt` (verbatim `S/App/Model/FlashChipWords.swift:8-35`; `ListFormatter` en →
   "A", "A and B", "A, B, and C"). New `KM/catalogue/FirmwareDescription.kt`: `fun runs(text): List<Run>` with
   `data class Run(text, bold, italic, code)` — inline Markdown only (`**`/`__`, `*`/`_`, `` ` ``, `[text](url)` → text),
   whitespace preserved; `fun plain(text) = runs(text).joinToString("") { it.text }` (`S/App/Catalogue/FirmwareDescription.swift:7-21`;
   RL §8 scope evidence: 1 of 499 uses bold). The `AnnotatedString` mapping is UI (task 09).
5. `KM/settings/Settings.kt` (interface `:74-89`, superset): add `var firmwareScope: String?`, `var myFirmwaresOrder:
   FirmwareOrder`, `var myFirmwaresScope: String?` (scopes stored as `storedValue` strings); `InMemoryAppSettings`
   defaults `null`/`ByName`/`null`. `StartUp` (`:11-20`): insert `V16("Changed by V16")` after `MVave`, detail
   "M-VAVE's V16 has been installed on it." (`S/App/Model/StartUp.swift:8,20,31`). `START_UP_QUESTION_DETAIL` stays.
6. `KA/data/DataStoreAppSettings.kt`: three string keys `firmwareScope`, `myFirmwaresOrder`, `myFirmwaresScope` (precedent
   `FIRMWARE_ORDER` `:76-78,135-143`); `startUp` is stored by enum name (`:72-74`), so `V16` needs no migration.
7. Fixture: `A/model/src/test/resources/firmwares-live-trimmed.json`, ~15 entries cut from a fresh copy of the live list.
   **The orchestrator fetches it before briefing** (a Codex sandbox has no network, observation 0656):
   `curl -sS https://fm-1.drewr.dev/firmwares.json -o /tmp/claude-1000/ke4h-firmwares.json` (the plan-time copy
   `/tmp/claude-1000/fw.json` may be gone), and names that path in the brief. Cover
   RL "Tests and fixture" bullets (listing 8 and 15; `flashChip` old/new/any/absent; `installs` present/absent; every
   `packageOrigin` + null; `whatSource`; `builds`; `sourceBuild`; `aisling`). Keep the top-level object shape
   (`formatVersion`, `builtAt`, `entries`, `problems`, as `CatalogueStoreTest.kt:11-13`). Record the source URL and fetch date in the commit message.
8. Tests T05-*: `KMT/catalogue/PublishedListTest.kt`, `CatalogueStoreTest.kt`, `FilterTest.kt`, new
   `FlashChipWordsTest.kt`, `FirmwareDescriptionTest.kt`, `HubFlashChipTest.kt`; `KMT/settings/StartUpTest.kt` (V16 row).

#### Task 06 — Library: keep every release, V16 by hash, the save trap, stock-loader resume
Ports 38c0837 and the V16 half of 76293d0's `FirmwareLibrary.swift` (RL §2; RV P1, §2, §7.2).
1. `KM/library/FirmwareLibrary.kt`: delete the "replace older releases" loop (`:236-239`); add
   `fun savedCopies(entry) = items.filter { it.catalogueID == entry.id }` and `fun isSaved(entry) =
   savedCopies(entry).isNotEmpty() && outdated(entry) == null` (`S/App/Model/FirmwareLibrary+Releases.swift:5-7`;
   `S/App/Model/FirmwareFilter.swift:28-30`); `otherBuild` (`:124-125`) → `if (stored(entry) != null) null else
   items.firstOrNull { it.catalogueID == entry.id && !ReleaseVersion.differs(it.version, entry.version) }` (iOS diff);
   `ReleaseVersion.differs(a: String, b: String?)` (`:406`; port from `S` — `grep -rn "func differs" S/App`).
   `outdated` gains iOS's `entry.version.map { … } ?? true` (`S/App/Model/FirmwareLibrary.swift:114-120`).
2. Constructor (current `(directory, bundledOfficial, ioDispatcher, now)` `:42-47`) → append
   `private val officialOf: (FwscPackage) -> OfficialFirmware? = OfficialFirmware::official` (P-D6).
3. `download` (`:192-252`): skip the size/sha (`:207-212`) and product (`:214-218`) checks when `entry.isOfficial`
   (`S/App/Model/FirmwareLibrary.swift:192-199`); `officialOf(packageFile)?.let { saveOfficial(…, name = it.name,
   catalogueID = entry.id, packageURL = address) }`; then `if (entry.isOfficial) throw NotOfficial`. `add` (`:254-293`):
   any `officialOf(...)` is saved as Official with its own name; FM1 Open stays Imported/"M-VAVE" as today.
4. **The save trap.** `save()` `:306` returns the bundled V15 for *any* `Source.Official` save:
   `builtIn?.takeIf { source == StoredFirmware.Source.Official }?.let { return it }`. Saving V16 would hand back V15 and
   write nothing. Replace with iOS's `existing(package)`: `items.firstOrNull { it.sha256 == packageFile.sha256 }?.let
   { return it }` (`S/App/Model/FirmwareLibrary.swift:296,309-311`), which still returns the built-in for V15.
5. `Failure.NotOfficial` text (`:383-385`) → "That file isn't a recognised official FM-1 firmware (only an unmodified V15
   or V16 FM-1.fwsc is accepted)." (iOS `FirmwareLibrary+Failure.swift:28`).
6. Resume (P-D5): `packagesThatCanFinish` `:172` `loader.text == STOCK_LOADER_IDENTITY` →
   `isStockLoader(loader)` = `loader.isLoader && loader.armedBuildPrefix == null && loader.hardwareModel == "FM-1" &&
   OfficialFirmware.official.any { it.identity.version == loader.version }`. Keep `STOCK_LOADER_IDENTITY` only if still
   referenced (else delete). `HomeScreen.kt:88` needs no change.
7. Tests T06-*: `KMT/library/LibraryTest.kt` (invert `:179`, new cases).

#### Task 07 — Switching guides, words, readiness (and MIDIMON first)
**Split:** also owns `HubFlashChip.support`, and the mechanical fixes in `OutcomeScreens.kt` (`FeluccaUpdateCode.wayRound`),
`InstallPresentation.kt` and `InstallPresentationTest.kt` (`fromTarget(…, refusesV15OverOtherBootloaders = …)`).
Ports the readiness half of 76293d0 and 3a5467c's `SwitchingCheck` part. RV §2.
1. New `KM/device/SwitchingGuide.kt` (port `S/App/Model/SwitchingGuide.swift:10-139`): `object SwitchingGuide { fun running(identity,
   saved: LeavingGuide?, startUp: StartUp?): LeavingGuide?; fun guide(installing, named, over, startUp); fun installing(…):
   Pair<LeavingGuide?, StartUp?>; fun filling(guide, startUp); fun dependsOnBootArea(code); fun reportsOfficialIdentity(guide) }`;
   `fun LeavingDestination.Companion.installing(packageFile, official: OfficialFirmware? = OfficialFirmware.official(packageFile))`
   — `LeavingDestination` is an fm1 enum, so this is a top-level `fun destinationOf(packageFile, official = …)` in this file;
   `val StartUp.bootArea: Fm1BootArea?` and `fun startUpOf(bootArea)` (`:118-139`, `V16`↔`V16`).
2. New `KM/device/SwitchingWords.kt`: port `S/App/Views/SwitchingWords.swift:4-97` minus `#if os(macOS)` branches; Android
   copy for `stillMono`, `beforeV16Warning`, `afterV16Warning` from the **Verbatim copy appendix** (RV §4 proposals);
   `warnings`, `firstStep`, `missingFirstStep`, `alreadyOfficial`, `notReallyOfficial(Detail)`, `replacesLookAlike`,
   `installFirst(Detail)`, `warningTitle`, `decline` verbatim.
3. `KM/device/InstallReadiness.kt`: new `data class SwitchingCheck(isRead=false, running: LeavingGuide?=null, installed:
   LeavingGuide?=null, route: LeavingRoute?=null, destination: LeavingDestination?=null, firstStep: StoredFirmware?=null,
   firstStepProblem: String?=null, dependsOnStartUp=false, followsV16=false, installsV16=false,
   knowsOnlyFirstChip: List<String> = emptyList(), hasNewerFlashChip=false)` with `needsFirstStep`, `warnings` and
   `IF_CHANGED` (`S/App/Model/InstallReadiness.swift:16-65`). Split iOS `read()` (`:70-123`) into
   - a pure `companion fun from(planned: List<Pair<StoredFirmware, FwscPackage>>, identity: Fm1Identity?, flashChipId: Int?,
     saved: LeavingGuide?, startUp: StartUp?, listedChip: (StoredFirmware) -> HubFlashChip?, firstStep: Pair<StoredFirmware, FwscPackage>?,
     firstStepProblem: String?, officialOf: (FwscPackage) -> OfficialFirmware? = OfficialFirmware::official): SwitchingCheck`
     (every branch of `:80-122`; `installsV16`/`followsV16` use `destinationOf(pkg, officialOf(pkg))`), and
   - `suspend fun read(target, requiresOfficial, alwaysViaOfficial, device: DeviceController, library, savedGuide:
     LeavingGuide?, flashChipId: Int?, startUp: StartUp?, entries: List<CatalogueEntry>, fetch: suspend (String) -> ByteArray):
     SwitchingCheck` — `device` only for `plan()` and `identity`, which exist at HEAD; `savedGuide` and `flashChipId` are plain
     parameters because their producers are this task's step 8 (`FlashRecord.guide`) and task 08 (`DeviceController.flashChipId`).
     It loads packages (any failure → return `SwitchingCheck(isRead = true)`, iOS `:77`), finds the first step only when
     `needsFirstStep`, and calls `from`. Until task 12, the sheet passes `flashChipId = null`.
   `InstallReadiness` current `(steps, checked, identity, isMissingOfficial=false, isUntested=false,
   refusesV15OverOtherBootloaders=false, startUp=null)` → **intended removal** of `refusesV15OverOtherBootloaders`
   (superseded), add `switching: SwitchingCheck = SwitchingCheck()`; members per iOS `:193-255`: `needsStartUp`,
   `isMissingFirstStep`, `isSwitching` (`|| reinstallsOfficial`), `isSameIdentity`, `canReinstallOfficial`,
   `knowsItIsntOfficial`, `reinstallsOfficial`, `isAlreadyInstalled`, `canStart(confirmsSwitch, acceptsUntested,
   confirmsNotOfficial: Boolean = false)` (current has the first two only). `fromTarget` (current 9 params `:63-87`):
   replace `refusesV15OverOtherBootloaders: Boolean` with `switching: SwitchingCheck`; `steps = listOfNotNull(switching.firstStep)
   + device.plan(…)`. Delete `refusesV15OverOtherBootloaders(target, library)` (`:111-121`, P-D3). Constants
   `UNTESTED_WARNING` (verbatim `:145-150`) for the sheet.
4. `KM/device/BeforeYouStartState.kt` (current 8 params): append `confirmsNotOfficial: Boolean = false`; `isSwitching`
   for ticking = `isSwitching || (readiness.canReinstallOfficial && confirmsNotOfficial)` (iOS `BeforeYouStartView.swift:255-257`);
   add `fun confirmsReinstallingOfficial(readiness) = readiness.knowsItIsntOfficial || (readiness.canReinstallOfficial &&
   confirmsNotOfficial)` (`:269-271`); `isEnabled` passes `confirmsNotOfficial` to `canStart`. Because `isSwitching` now
   depends on readiness, turn `isSwitching` into a function of readiness or pass the combined value from the sheet —
   Executor's choice, documented in KDoc.
5. `FixedFirmware` (in `SwitchingGuide.kt`; `S/App/Model/SwitchingGuide.swift:141-176`): `MIDIMON = "midimon"`,
   `CATALOGUE_IDS = listOf("midimon", "loom")`, `sealed class Failure : Exception` with verbatim texts,
   `suspend fun find(library, entries, fetch): StoredFirmware` (saved fixed-check copy first; else download the
   `midimon` entry and check `updateCode == FELUCCA_FIXED_CHECK`). (P-D1)
6. `KM/settings/Settings.kt`: delete `FeluccaUpdateCode` (`:34-72`, P-D3) and `KMT/settings/StartUpTest.kt` cases 2–3 that
   test it (`onlyKnownFirmwaresAreFlagged`, `theWarningNamesTheOthersAndHedgesWhenUnsure`); case 1 stays.
7. **Mechanical consumer fix (owned here, before task 12):** `KA/ui/install/BeforeYouStartSheet.kt` stops importing
   `FeluccaUpdateCode`; its `LaunchedEffect` (`:81-98`) computes `SwitchingCheck.read(…)` instead of
   `refusesV15OverOtherBootloaders`, `asksStartUp = asksStartUp || (switching.dependsOnStartUp && startUp != StartUp.MVave)`
   (iOS `:54-57`); the warning card (`:242-255`) shows `readiness.switching.warnings` with `SwitchingWords.warningTitle`;
   the "Don't install" button shows when `switching.warnings` is non-empty (iOS `:285-289`). Delete string
   `before_start_computer_recovery` (lint) or reword per appendix if still shown. Everything else in the sheet is task 12.
8. `KM/history/FlashHistory.kt` (moved here from task 08 so `read` has its input): `FlashRecord` (current `(name, version,
   product, date)` `:13-19`) appends `val guide: SavedGuide? = null`; new `@Serializable data class SavedGuide(firmwareName,
   identity: String, updateCode: String, bootArea: String?)` with `toGuide(): LeavingGuide?` (unknown raw → `null`) and
   `companion fun from(guide)`, raw values = FK's Swift case names (`UpdateCode.raw`, `Fm1BootArea.raw`). Old
   `flash-history.json` without `guide` must still load (default). `record(item)` → `record(item, guide: LeavingGuide? =
   null)` (existing callers unchanged). The sheet reads `history.lastInstall(identity.text)?.guide?.toGuide()`.
9. Tests T07-*: new `KMT/device/SwitchingGuideTest.kt`, rewritten `ReadinessTest.kt`, `BeforeYouStartStateTest.kt`,
   new `FixedFirmwareTest.kt`, `KMT/history/FlashHistoryTest.kt` (T07-history-compat).

#### Task 08 — Flash job, controller, history, progress, summary
Ports the controller half of 76293d0 and fc809ca's app side (`mightHaveFinished`). RV §2, §7.
1. `KM/device/FlashPhase.kt`: rename `Verifying` → `SecondPass`, label `"WRITING PASS 2"` (P-D2).
2. `KM/device/InstallProgress.kt`: delete `Stage.Checking`; `SecondPass` → `point(0.85, 0.97, f) to Stage.Installing`
   (`S/App/Model/InstallProgress.swift` diff). `Tracker.update` compares `stage.ordinal` — still monotone with three stages.
   Mechanical consumers: `KA/ui/install/InstallingOverlay.kt:284` (drop the branch), delete `install_stage_checking`
   from `RES/strings_install.xml:45` (lint).
3. `KM/device/FlashJob.kt` (current 7 params `:7-15`): append `confirmsReinstallingOfficial = false`,
   `guide: LeavingGuide? = null`, `startedOnV16 = false`, `writtenFraction = 0.0`; companion `RETURN_SEARCH = 20.seconds`,
   `mightHaveFinished(error, written, reinstalling)`, `needsAStepFirst(error, item, running: LeavingGuide?, startUp)`,
   `failureMessage(error, item, running: String?, needsAStepFirst)` (`S/App/Model/DeviceController+States.swift:55-169`).
4. History: uses task 07's `FlashHistory.record(item, guide)` and `FlashRecord.guide`; no edit to `FlashHistory.kt`.
5. `KM/device/DeviceController.kt`:
   - `_flashChipId: MutableStateFlow<Int?>` + `flashChipId: StateFlow<Int?>`; `scan()` (`:103-119`) uses `updater.summary()`
     and sets identity and chip together.
   - `runningGuide` (iOS `DeviceController+Plans.swift:17-21`).
   - `flash(steps, confirmsSwitchingFirmware=false, confirmsSameIdentity=false)` (`:142-146`) → append
     `confirmsReinstallingOfficial = false, startedOnV16 = false`; `FlashJob(…, guide = runningGuide, startedOnV16 =
     startedOnV16 || identity.value == Fm1Identity.officialV16)` (iOS `:173-190`).
   - `run()` (`:189-240`): options carry `confirmsReinstallingOfficial`; record `before = updater.identify()`; on failure
     go through `confirmed(result, package, reinstalling = before == package.identity)` → `returned(running:)` searching
     `RETURN_SEARCH` with `delay(2.seconds)` steps (iOS `:257-311`; virtual time: `withTimeoutOrNull(RETURN_SEARCH)`, no
     clock); `record(item, package)` saves the guide and, when the install brought a new bootloader, sets
     `settings.startUp` (iOS `:313-321`); failures use `FlashJob.needsAStepFirst(…, job.guide, settings.startUp)` and
     `FlashJob.failureMessage` (replacing `:289-309`).
   - `apply()` (`:242-283`): `Writing` sets `writtenFraction`; `Checking` → `SecondPass`, `writtenFraction = 1.0`, log
     "Written once; the loader is going over it again." (iOS `:338-341`).
   - `installFirst(fixedFirmware)` (iOS `DeviceController+Plans.swift:41-49`).
   - `configure()` (`:60-74`): no new simulator option (P-D7).
6. `KM/device/DeviceSummary.kt:73-79`: V16 → "the official firmware (V16)"; `screenTitle` (`:49`) replaces both
   "(V15)"/"(V16)" (iOS `DeviceSummary.swift` diff, regex `the official firmware \((V1[56])\)` → `$1`).
7. Tests T08-*: `KMT/device/FlashingTest.kt`, new `InstallFinishTest.kt`, `StandardLookTest.kt` (rename/stage),
   `SwitchingGuideTest.kt` (controller cases).

### Phase C — `:app` consumers

Each task below lists the producer symbols it reads; if one is missing, stop and report (don't add it — the producer's
file belongs to an earlier task).

#### Task 09 — Shared UI pieces, settings
**Split:** also edits `KA/ui/theme/FmSwitchTheme.kt`, where `StandardColors` (and so `danger`) lives.
Reads: `HubFlashChip`, `FlashChipWords`, `FirmwareDescription.runs`.
1. `KA/ui/theme/Palette.kt`: `danger` light `0xA8380A`, dark `0xFF9259` (RL §8; `S/App/Standard/Theme/Palette.swift` diff);
   `KAT/ui/theme/ContrastTest.kt`: add the pair (precedent `S/AppTests/ContrastTests.swift` +1 row).
2. `KA/ui/components/ButtonStyles.kt`: a `DangerButton` (text in `danger`, same shape as `SecondaryButton`).
3. New `KA/ui/firmwares/FlashChipNotice.kt` (`S/App/Standard/Firmwares/FlashChipNotice.swift:5-29`):
   `@Composable fun FlashChipNotice(names: List<String>, hasNewerFlashChip: Boolean)` — warning box with
   `FlashChipWords.warning(...)`, and when `!hasNewerFlashChip` a "How would I know?" disclosure showing
   `FlashChipWords.HOW_TO_TELL`. Copy from `:model` only, so no strings file.
4. New `KA/ui/firmwares/DescriptionText.kt`: `fun annotated(runs): AnnotatedString` (bold → `FontWeight.Bold`, italic →
   `FontStyle.Italic`, code → `FontFamily.Monospace`), used by tasks 10 and 11.
5. `RES/strings_settings.xml:7-8` and `KA/ui/settings/SettingsScreen.kt`: sources copy verbatim from
   `S/App/Standard/Settings/StandardSettingsView.swift:133-134` (mutant.cloud, The Firmware Hub); the simulated
   "verifies" toggle label → "Go over it twice, like Felucca" (iOS diff of the same file). Also any "firmware hub" in
   `settings_safety_phone`/`settings_rescue_tools` (RL §1) → "The Firmware Hub".
6. Screens (visual protocol): Settings (light, dark).

#### Task 10 — Firmware list and My firmwares
Reads: `FirmwareOrder.MostInstalled`, `FirmwareOrder.firstUncounted`, `FirmwareScope.storedValue/fromStored/offered`,
`AppSettings.firmwareScope/myFirmwaresOrder/myFirmwaresScope`, `FirmwareLibrary.savedCopies/isSaved`,
`CatalogueEntry.installs`, `FirmwareDescription.runs`, `annotated()`.
1. New `KA/ui/firmwares/FilteredFirmwareList.kt` (port `S/App/Standard/Firmwares/FilteredFirmwareList.swift:8-115`): the
   scope row moves here from `FirmwareListScreen.kt:299-368` and becomes a **`FlowRow`** — use the overload
   *without* `overflow`: in the resolved `foundation-layout-android:1.12.1` (BOM 2026.09.00) that overload carries no
   `@ExperimentalLayoutApi`, while the `overflow` one is `@Deprecated` and experimental (verified with `javap` on the
   cached AAR's `FlowLayoutKt`, 2026-11-09), so no opt-in and no deprecation warning. The sort menu iterates
   `FirmwareOrder.entries` (labels `firmware_order_*`; add `firmware_order_most_installed` "Most installed"), with
   content description "Sort by %s". **Pinning without experimental APIs:** a `derivedStateOf {
   listState.firstVisibleItemIndex > filtersIndex }` shows an overlay row under the title with the order menu and, when the
   pick isn't All, the picked pill; tapping it `animateScrollToItem(filtersIndex)`, semantics hint "Shows the filters"
   (iOS `:43-54,74`). Not `stickyHeader`.
2. `KA/ui/firmwares/FirmwareListScreen.kt`: scope from `settings.firmwareScope` through `fromStored`/`offered`, replacing
   `rememberSaveable`+`scopeKey`/`savedScope` (`:91,252-266`); Most installed: summary source line "Install counts are from
   The Firmware Hub's site." and the in-list note before `firstUncounted` "These aren't on The Firmware Hub, so they have no
   install count." only when some are counted (`S/.../FirmwareListView.swift:57-59,108-128`); card tag `isSaved` and
   plural "%d saved" when `savedCopies.size > 1` (`:248-251`); description via `annotated(runs(…))` (`:422`).
3. `KA/ui/firmwares/MyFirmwaresScreen.kt:42-115`: its own order and scope (`myFirmwaresOrder/Scope`) through
   `FilteredFirmwareList`, scopes computed from the saved entries; saved test `library.savedCopies(it).isNotEmpty()`
   (`:51-56`); filtered-empty "None of these match the filter picked." Starred stays out.
4. `KA/ui/home/HomeScreen.kt:79-81`: `savedCount` uses `savedCopies(it).isNotEmpty()` (the `otherBuild` meaning changed in
   task 06).
5. `RES/strings_firmwares.xml`: new list strings only. Page strings in this file (`firmware_untested_warning` `:22`,
   `firmware_install`/`firmware_save_later`/`firmware_remove` `:24`) are task 11's, which runs after this one.
6. Screens: list (All; Most installed with the source line and uncounted note; scrolled so the pinned bar shows with a
   kind picked; filters wrapped over two lines), My firmwares (sorted, filtered, filtered-empty), each light and dark.

#### Task 11 — Firmware page
Reads: `CatalogueEntry.installs/flashChip`, `HubFlashChip.savesOn`, `FlashChipNotice`, `DangerButton`,
`FirmwareLibrary.savedCopies/remove`, `DeviceController.flashChipId`, `annotated()`.
1. `KA/ui/firmwares/FirmwarePageScreen.kt` and new `FirmwarePageSaving.kt` (port `S/.../FirmwarePageView+Saving.swift:6-70`,
   `FirmwarePageView.swift:81-82,114-143,207-215,250-276,338-351`): description styled; chip notice after the untested
   notice when `entry.flashChip == HubFlashChip.Old && flashChipId != Fm1FlashChip.P25Q80` (`:136-143`); Details rows
   "Installs" (reuse `firmware_installs` label; value "%s on The Firmware Hub" with
   `NumberFormat.getIntegerInstance()`) and "Saves on" after "Tested on an FM-1"; versions: a segmented control of the
   saved copies, newest first (label `entry.version ?: "Newest"`), "Saved, ready for offline install", buttons
   "Install <name><shownVersion>" / "Save<shownVersion> to this phone for later" (Android keeps "phone",
   `firmware_save_later`), "Delete this saved version" as `DangerButton` replacing "Remove" (`:225-236`).
2. New `RES/strings_firmware_page.xml` (owner) for every new page string. This task also edits the page strings that
   live in `RES/strings_firmwares.xml` (after task 10 lands): `firmware_untested_warning` (`:22`) → iOS page text (RL "Page
   untested notice"), and deletes `firmware_install`/`firmware_save_later`/`firmware_remove` if the version-suffixed labels
   and the danger button replace them (lint `UnusedResources`).
3. Screens: an `old`-chip entry (notice + "How would I know?" open), an entry with installs and saves-on rows, an entry
   with two saved versions (control + danger button), the `aisling` entry (bold), light and dark.

#### Task 12 — Before you start, installing, outcomes
Reads: `SwitchingCheck` (all fields), `InstallReadiness.canReinstallOfficial/knowsItIsntOfficial/reinstallsOfficial/
isMissingFirstStep/needsStartUp/canStart(…, confirmsNotOfficial)`, `InstallReadiness.UNTESTED_WARNING`,
`BeforeYouStartState(…, confirmsNotOfficial)`, `SwitchingWords.*`, `FixedFirmware.find`, `FlashJob.startedOnV16/
needsAStepFirst`, `DeviceController.flash(…, confirmsReinstallingOfficial, startedOnV16)/installFirst/flashChipId`,
`StartUp.V16`, `FlashChipNotice`.
1. `KA/ui/install/BeforeYouStartSheet.kt` (port `S/App/Standard/Firmwares/BeforeYouStartView.swift:67-291`): plan line
   with `SwitchingWords.firstStep`; notices in order — missing official, missing first step, untested
   (`UNTESTED_WARNING`), `FlashChipNotice(switching.knowsOnlyFirstChip, switching.hasNewerFlashChip)`, before-V16,
   after-V16 (appendix wording), route ("Why?" disclosure for `THROUGH_FIXED_FIRMWARE` with a first step; info notice
   for `UNCERTAIN`); status adds `replacesLookAlike` / `alreadyOfficial`; checklist adds the "It's really running another
   firmware" tick when `canReinstallOfficial && !knowsItIsntOfficial`, and the 36905bb ticks ("I'll leave it plugged in and
   switched on until it's done" + detail; footnote) from `S/App/Views/InstallWords.swift:6-12`; start-up radio rows now
   include `StartUp.V16` (it's in `StartUp.entries`); the Install button passes `confirmsReinstallingOfficial` and the
   sheet passes `fetch` (`HttpFetch(Dispatchers.IO)::invoke`, as `KA/AppModel.kt:117`) to `SwitchingCheck.read`.
2. `KA/ui/install/InstallingOverlay.kt`: step line "Its screen stays dark until it's done. Keep it plugged in and
   switched on." (`InstallWords.darkScreen`; iOS `InstallingView.swift:67-68`), replacing `installing_step`/
   `installing_keep_plugged` copy.
3. `KA/ui/install/OutcomeScreens.kt`: `InstallDone` (`:57-93`) — when `reported == "FM-1_015"` and `job.startedOnV16`,
   a card with `SwitchingWords.STILL_MONO` (Android wording; no factory-voices button: factory voices are out of scope,
   and the wording says so). Problem screen: interrupted steps "Don't switch it off…" / "Plug it back in, if it came out."
   (`InstallWords.swift:15-18`); when `job.needsAStepFirst` the button "Install MIDIMON first" calls `FixedFirmware.find`
   then `device.installFirst(it)` if `job.confirmsSwitchingFirmware`, else opens MIDIMON's page (iOS
   `InstallOutcomeViews.swift:91-132`, `SwitchingGuide.swift:182-189`); its detail `installFirstDetail`. This replaces
   the current `ProblemAction.MIDIMON` page jump only where iOS does.
4. `RES/strings_install.xml` (owner): new/changed strings; delete any that stop being used (lint). `before_start_untested`
   (`:10`) is replaced by the `:model` constant.
5. Screens (simulated FM-1, live list): Before you start for V16 over `FM-1_900` (before-V16 notice), V15 after V16
   installed on the simulator in the same session (after-V16 notice), a Felucca-code firmware with start-up unknown
   (question with the V16 row; switching warnings card), Done after V16→V15 (still-mono card), the interrupted screen
   (cable pull at 45%), light and dark.

### Phase D

#### Task 13 — Docs
1. `doc/architecture.md`: dated "Status: 2026-11-xx" update against `S` @ fcf4a78 and FM1Kit 0.6.0 @ 53f8511: module map
   (`LeavingGuide`, `FlashChipSupport`, `Fm1FlashChip`, `Fm1DeviceSummary`, `SwitchingGuide`/`SwitchingWords`/
   `FixedFirmware`, `FilteredFirmwareList`), lenient decoding and why, keeping every release, the three new settings keys,
   `StartUp.V16`, V16 never bundled, stock-loader resume rule (P-D5), the virtual-time quiet windows (task 04).
2. `NOTICE.md`: record the fact that `S` is MPL-2.0 since `aac72a9` (2026-11-08) and these ports derive from it; choose
   no licence (Decision).
3. `AGENTS.md`: "Refuse V15 over V15" → "Refuse V15 over V15 and V16 over V16, unless the person says the FM-1 is really
   running a look-alike (TransitFM1)".

#### Task 14 — UAT
Map every ke4h acceptance clause (`kata show ke4h`, "## Acceptance") to scenarios; automated steps first, then the
orchestrator's emulator walkthrough, then David's hardware steps.
- **A1 "The live list loads, keeping every field the list carries; unknown enum values don't break it."** — automated:
  T05-key-set, T05-round-trip, T05-unknown-enums green; plus a live diff, **orchestrator-run on the host** (network):
  `curl -sS https://fm-1.drewr.dev/firmwares.json | jq -r '[.entries[]|keys[]]|unique[]'` ⊆ the field names printed by a
  one-off test (`CatalogueEntry.serializer().descriptor.elementNames`), recorded with date.
- **A2 "The list's features match iOS fcf4a78."** — emulator walkthrough E1–E8, each against its iOS citation: mutant.cloud
  "Listed on", Most installed (+notes), wrapped and remembered filter (kill and relaunch the app: the pick stays), pinned
  sort/pill, My firmwares sort/filter, Markdown description, two saved releases with the switcher and Delete, Installs /
  Saves on rows, older-chip notice.
- **A3 "V16 downloads from the list and installs on the simulated FM-1, with the warnings."** — emulator: Settings ▸
  Simulated FM-1; open M-VAVE's entry (official), Save for later → saved as "FM-1 V16" (the real file, downloaded by the
  app; never copied into the repo); Before you start shows the before-V16 notice; install → Done "…ready to play",
  reporting `FM-1_016`; then V15 (built-in) → after-V16 notice → Done shows the still-mono card; Settings ▸ start-up
  now reads "Changed by V16" after the V16 install. (Resume across an app restart can't be shown on the simulator: it
  lives in the app process and its loader reports `ota-FM-1_9`, `SimulatedFm1.kt:352`; T06-stock-v16 and hardware H3 are
  the evidence.)
- **A4 "The gate (`scripts/check.sh`) is green."** — last automated step, verbatim, on the committed tree with
  `env -u JAVA_HOME -u ANDROID_HOME scripts/check.sh` (precedent `plans/android-port/17-uat.md:195-197`), plus test
  counts ≥ task 01's.
- **A5 "David's UAT on hardware."** — David's, each preceded by **backing up the FM-1's projects and samples** (and noting
  the V16 mono outcome is only reversible from a Mac):
  - H1 V15 → V16 from the list on a real FM-1 (`ota-FM-1_016`? record the loader identity seen in the job details).
  - H2 V16 → V15: installs; record whether V15 plays one note at a time (RV P4, FK stage R3) and that the Done card says so.
  - H3 Resume across an app restart mid V16→V15 write: Home offers V15 (P-D5).
  - H4 a felucca-bsp firmware built since Nov 2026 (if David has one): Home shows the full identity, not three digits;
    chip notice behaviour on his FM-1 (record chip ID if reported).
  - H5 the Android-MIDI reopen during a write (fc809ca `lookForReturn`): an install of a Felucca firmware still finishes
    normally (no new stall/disconnect). Blocker for automation: the FM-1 holds the phone's one USB port.
- Record results in `plans/ke4h-ios-0-2-1-catch-up/uat.md`; hardware steps stay open (needs-human) until David runs them.

## Testing Strategy

Harness facts (2026-11-09, `f5b9505`): test deps are `kotlin("test-junit")` and `kotlinx-coroutines-test` only, in all
three modules (`*/build.gradle.kts` `testImplementation` lines); no Robolectric, Compose UI test, Roborazzi/Paparazzi or
mocking library (`grep -n -i "ui-test\|robolectric\|roborazzi\|paparazzi\|mockk\|mockito" app/build.gradle.kts
gradle/libs.versions.toml` → no output). `:model` tests get the real V15 file through `System.getProperty("fm1.v15")`
(`model/build.gradle.kts:29`). `:fm1` updater tests drive `SimulatedFm1` under `runTest` with `requestInterval = ZERO`,
`rebootDuration = ZERO` and `fastTiming()` (`KFT/updater/UpdaterTest.kt:33-63`) — the precedent for every new `:fm1`
behaviour case; `:model` controller tests use `FlashingTest`'s `DeviceController` + `transportFactory` + `ControllableTransport`
(`KMT/device/FlashingTest.kt:37-140`), which can construct the simulator with any `Options`. Base gate: green at
`f5b9505`, 2026-11-09T08:59:25-06:00 (`scripts/check.sh` → `Checks passed: lint build test`; tests were cached — task 01
re-runs them). "red" below for a new test means: written against `f5b9505` code it fails (or doesn't compile because the
symbol is missing, noted). Every negative-only case carries a positive delivery assertion.

### Task 02 (`:fm1` official releases)
- **T02-official-list** `OfficialReleaseTest.onlyV15MayBeIncludedInAnApp` — `official == [V15, V16]`,
  `known.filter { it.isRedistributable } == [V15]`, identities `["FM-1_015","FM-1_016"]`, all `isOfficial`;
  `OPEN_V12.identity.isOfficial == false` (FK `OfficialReleaseTests.swift:7-14`).
  **at HEAD:** red (doesn't compile: no `V16`, `identity`, `isRedistributable`). **reddened by:** `isRedistributable = true`
  on `V16` in `KF/pkg/OfficialFirmware.kt`. **reaches the guard:** pure constants; no I/O.
- **T02-v16-reinstall** `OfficialReleaseTest.v16IsTreatedAsTheOfficialFirmwareToo` — sim `installedIdentity =
  Fm1Identity.officialV16`, package `FwscPackage(SamplePackage.file("FM-1_016"))`, `sim.prepare(pkg)`:
  `check(pkg).kind == REINSTALL`, `canReinstallOfficial`, `install(pkg, InstallOptions(true, true))` throws
  `AlreadyInstalled` whose `identity == "FM-1_016"` and message contains "TransitFM1".
  **at HEAD:** red (kind is `SAME_IDENTITY`: `InstallCheck.kt:32` only knows V15). **reddened by:** reverting
  `InstallCheck.of`'s branch to `device == Fm1Identity.officialV15`. **reaches the guard:** `check()` →
  `findOnlyUnlocked { !isLoader }` finds the sim (identity `FM-1_016` is not a loader) → `checkUnlocked` → capability is
  `null` (not open firmware, `Fm1UpdaterChecks.kt:33`) → `InstallCheck.of`; the positive `kind == REINSTALL` assertion
  proves the stimulus arrived before the install assertion.
- **T02-reinstall-look-alike** `UpdaterTest.reinstallsTheOfficialFirmwareOverALookAlikeOnceConfirmed` — sim
  `installedIdentity = officialV15`, `refusesRunningFirmware = false` (a TransitFM1 look-alike), package `FM-1_015`
  synthetic: without `confirmsReinstallingOfficial` → `AlreadyInstalled`; with it → returns `FM-1_015` and events contain
  `UpdateEvent.Connected`. **at HEAD:** red (doesn't compile: no option). **reddened by:** deleting the
  `canReinstallOfficial && confirmsReinstallingOfficial` fall-through in `Fm1Updater.installUnlocked`. 
  **reaches the guard:** `refusesRunningFirmware = false` is required or the sim refuses at staging (`SimulatedFm1.kt:210-215`) and the
  test would fail for the wrong reason; `Connected` is emitted only after the kind gate (`Fm1Updater.kt:217`).
- **T02-sim-refuses-v16** `SimulatedFm1Test.v16RefusesItsOwnIdentity` — sim on `officialV16`, package `FM-1_016`,
  `InstallOptions(confirmsReinstallingOfficial = true)` → throws some `Fm1UpdateError` (an unfinished staging may surface
  as `Stalled`, `BootloaderMismatch` or `PackageRefused`, by the last read's address, `Fm1Updater.kt:230-240,441-459` — don't
  assert which), no `UpdateEvent.WaitingForLoader` was emitted, and sim `events` contains "already running" (the positive).
  **at HEAD:** red (doesn't compile: no option; with it, the sim stages it — `:212` compares with V15 only — and the install
  succeeds). **reddened by:** reverting `SimulatedFm1.kt:212`. **reaches the guard:** the confirm option gets past the
  updater's own gate so the sim's check is what's exercised; its log line proves staging reached it.
- **T02-texts** `EdgeCaseTest.errorTextsSayKeepItOnAndNameMidimonFirst` — `Stalled(WRITING, 3).message` contains
  "don't switch it off"; `Disconnected(WRITING, 3)` contains "without switching it off"; `UnexpectedFinish(WRITING)`
  contains "switched on"; `BootloaderMismatch(1)` contains "MIDIMON 0.4 or LOOM-8" before "from a Mac" and "V16";
  `PackageRefused(1)` contains "TransitFM1"; `AlreadyInstalled("FM-1_015")` contains "TransitFM1",
  `AlreadyInstalled("FM-1_916")` doesn't. **at HEAD:** red (old texts, `Fm1UpdateError.kt:93,106,140`). **reddened by:**
  restoring any one 0.4.5 text in `KF/updater/Fm1UpdateError.kt`. **reaches the guard:** direct construction.
- Existing `UpdaterTest`/`EdgeCaseTest`/`OpenFirmwareTest`/`FinishTest`: **at HEAD:** green — guards no regression in the
  V15/open-firmware paths, not the fix. **reddened by:** e.g. making `isOfficial` true for open firmware
  (`OpenFirmwareTest` reinstall cases). **reaches the guard:** unchanged precedent harness.

### Task 03 (`:fm1` guides and chips)
- **T03-update-code** `LeavingGuideTest.readsHowAFirmwareInstallsOthers` (FK `:13-22`) — markers via
  `SamplePackage.firmwareWithCode("..<product>..<markers>..".encodeToByteArray() + ByteArray(0x3000) { 0x5A }, product)`:
  `FELUCCA-LOADER-1` → `FELUCCA_ORIGINAL_CHECK`; `+AC79BLID` → `FELUCCA_FIXED_CHECK`; `+LOADER NOT STAGED` → `OWN_LOADER`;
  `BAUD` → `UNKNOWN`; `""` with product `FM-1_015` → `M_VAVE`; installer → `null` and `LeavingGuide.from(installer, …) == null`.
  **at HEAD:** red (doesn't compile). **reddened by:** dropping the `OWN_LOADER_MARKER` branch from `FwscPackage.updateCode`
  (the TransitFM1 row reads `FELUCCA_ORIGINAL_CHECK`) — and, separately, dropping the `identity.isOfficial ||` clause (the
  `FM-1_015` row turns `UNKNOWN`). **reaches the guard:** the code is
  enciphered with `FM1_CHIP_KEY` exactly as `ApplicationArea.code()` deciphers (the moved builder, step 1), so the markers
  are found only through the real decipher path.
- **T03-routes** the six route/warning/instruction cases (FK `:24-96`), names kept, instruction strings asserted
  verbatim. **at HEAD:** red (doesn't compile). **reddened by:** in `LeavingGuide.routeByBootArea`, returning `DIRECT` for
  V15 whatever the boot area (the m-bootloader case expects `THROUGH_FIXED_FIRMWARE`); or `OWN_LOADER` routing V16
  `DIRECT` (the TransitFM1 case). **reaches the guard:** pure; guides built from packages through `LeavingGuide.from`, so
  `writesBootArea` (stock-loader SHA) is on the path — `keepsTheBootloaderWhenAFirmwareComesWithItsOwnLoader` asserts the
  synthetic `FM-1_015` package (fake loader) keeps `M_BOOTLOADER`.
- **T03-transit-takes-v15** `ApplicationAreaTest.transitFm1TakesV15` — `"..FELUCCA-LOADER-1..LOADER NOT STAGED.."`:
  `refusesV15OverOtherBootloaders == false`. **at HEAD:** red (0.4.5 rule says it refuses). **reddened by:** reverting
  `refusesV15OverOtherBootloaders` to the marker rule at `ApplicationArea.kt:97-103`. **reaches the guard:** the positive
  twin `refusesV15WhenFeluccasUpdateCodeLacksTheFix` (existing, green — guards P-D3's unchanged meaning) proves the sample
  decodes.
- **T03-chip-support** `FlashChipSupportTest` (FK `FlashChipSupportTests.swift`) — constants at code offset `0x100 + 8i`
  as 4 LE bytes: both → `BOTH_CHIPS`, P25Q80 alone → `FIRST_CHIP_ONLY`, none → `NO_CHECK`; installer → `null`.
  **at HEAD:** red (doesn't compile). **reddened by:** big-endian constants in `FlashChipSupport.kt`. **reaches the guard:**
  real decipher path, as T03-update-code.

### Task 04 (`:fm1` handshake and finishing)
- **T04-chip-bytes** `FlashChipTest.readsTheChipAFirmwareReportsAfterItsIdentity` (FK `ProtocolTests.swift:125-135`) —
  `Fm1Identity.parse("FM-1_378000")!!.handshakeReply(0xB36014)`: parses back, `flashChipId(reply) == 0xB36014`,
  `Fm1Message.unframe(reply)!![30..32] == B3 60 14`. **at HEAD:** red (doesn't compile). **reddened by:** writing the
  chip at `headerLength + 23` in `Fm1Identity.handshakeReply` (the byte-slice assertion fails even though round trip
  passes). **reaches the guard:** pure.
- **T04-no-chip** `findsNoChipInAReplyWithoutOne` + `readsNoChipFromAnIdentityThatRunsIntoIt` — recorded real reply
  (`KFT/protocol/RecordedDeviceTest.kt`) → `null`; `ota-FM1C_db1642b2b6fa5c2c` with a chip → `null`. **at HEAD:** red
  (doesn't compile). **reddened by:** dropping the `text.length < 24` guard in `flashChipId`. **reaches the guard:** the
  same call returns the identity (positive) before the `null` is asserted.
- **T04-found-carries-chip** `aFoundDeviceCarriesTheChip` — sim `flashChipId = 0xB36014`: `summary()!!.hasNewerFlashChip`;
  default sim → `false`. **at HEAD:** red (doesn't compile). **reddened by:** constructing `FoundDevice(connection,
  identity)` without the chip in `Fm1Updater.findUnlocked`. **reaches the guard:** the sim's identity `FM-1_900` has three
  digits, so the full-identity query is sent and unanswered (sim answers it only with `shortensIdentity` or
  `reportsFlashChipOnlyWhenAsked`); the chip must survive `answer.flashChipId` fallback (FK `:442`). Runs in virtual time;
  the 300 ms wait costs nothing.
- **T04-shortened** `asksForTheWholeIdentityAfterAShortenedOne` — sim `FM-1_1234567`, `shortensIdentity`, chip UC25HQ80 →
  found identity `FM-1_1234567`, chip present. **at HEAD:** red (doesn't compile). **reddened by:** `mayTellMore` always
  `false` in `Fm1Identity` (identity stays `FM-1_123`). **reaches the guard:** the sim shortens only with the option
  (`answerHandshake`), so the plain answer really is `FM-1_123`; chip asserted as the positive.
- **T04-chip-when-asked** `asksForTheFlashChipWhenThePlainAnswerLeavesItOut` — `reportsFlashChipOnlyWhenAsked` →
  `summary().hasNewerFlashChip`. **at HEAD:** red. **reddened by:** dropping `|| flashChipId == null` from `mayTellMore`.
  **reaches the guard:** 6-digit identity, so only the missing-chip clause can trigger the query.
- **T04-may-tell-more** (pure, FK `:180-186`) and **T04-queries** `FULL_IDENTITY_QUERY` parses as command `0x11`, payload
  `[1]`. **at HEAD:** red (doesn't compile). **reddened by:** payload `[0]` in `Fm1Message`; `mayTellMore` true for
  official. **reaches the guard:** pure — `FULL_IDENTITY_QUERY` is a constant and `mayTellMore` a function of its
  arguments; no updater on the path.
- **T04-three-digits-stay** `keepsAThreeDigitIdentityWhenNothingMoreComes` — `identify() == FM-1_900`. **at HEAD:** green —
  guards that an unanswered full query keeps the short identity (task 04 step 4), not the fix. **reddened by:** returning
  `null` when `fullIdentity` times out. **reaches the guard:** default sim never answers the full query.
- **T04-return-not-lost** `ReturnTest.finishesWhenTheFirmwareAnswersOnAConnectionThatNeverShowsAsLost` — sim
  `restartsWithoutFinishing`, `keepsConnectionThroughRestart`; timing `fastTiming().copy(returnCheckAfter = 200.milliseconds)`;
  result `FM-1_901`; last two events `[Rebooting, Finished(FM-1_901)]`; virtual time taken (`testScheduler.currentTime`
  delta) `< writeIdleLimit`. **at HEAD:** red (doesn't compile: options). **reddened by:** `lookForReturn` returning
  `null` (or the quiet-window branch removed from `serve`) → `Stalled(WRITING)` after `writeIdleLimit`. 
  **reaches the guard:** the sim reads every chunk, so `WriteProgress` reaches 1.0 (`isNearlyWritten`); with
  `keepsConnectionThroughRestart` the loader connection never reports lost and the sim never asks to finish, so only the
  quiet-window look can end the phase; `Rebooting` before `Finished` is the positive.
- **T04-return-lost** `finishesWhenTheDeviceRestartsWithoutAskingToFinish` — only `restartsWithoutFinishing` → `Finished`
  last. **at HEAD:** red. **reddened by:** deleting the post-loss `waitFor(returnSearch) { loader || firmware }` in
  `serveLoader`. **reaches the guard:** the connection goes lost on the sim's reboot (generation bump), `progress` ≥ 0.95.
- **T04-loader-back** `aCableCutNearTheEndStopsWhenTheLoaderComesBack` — `unplugDuringWriteAt = 0.97` → `Disconnected(WRITING)`.
  **at HEAD:** green — guards the "loader came back, resume" branch of fc809ca, not the fix (HEAD already throws this).
  **reddened by:** accepting any device found after the loss as `returned` (dropping `back.identity == firmware`).
  **reaches the guard:** the sim reboots into its loader (`SimulatedFm1.kt:241-247`), `progress` ≥ 0.95 so the new search
  runs and finds the loader.
- **T04-nearly-written** `WriteProgressTest.countsNearlyAllOfTheApplicationAreaAsNearlyWritten` and
  `aSecondPassCountsOnlyOnceItIsNearlyDone` (FK `ReturnTests.swift:60-83`). **at HEAD:** red (doesn't compile).
  **reddened by:** `NEARLY_WRITTEN_THRESHOLD = 0.5`; using `writtenFraction` while checking. **reaches the guard:** pure.
- **T04-model-knock-on** existing `FlashingTest` cases with bumped advances: **at HEAD:** green — guards that the extra
  300 ms doesn't break scanning, not the fix. **reddened by:** `fullIdentityWait` raised above the advance. **reaches the guard:** the precedent tests configure the
  real simulator through `DeviceController.configure` and assert `identity.value` after the advance, so the handshake with
  its unanswered full-identity query is on the path.

### Task 05 (`:model` catalogue)
- **T05-key-set** `PublishedListTest.everyFieldTheLiveListCarriesIsKept` — for each entry object in
  `firmwares-live-trimmed.json` (parsed with `Json.parseToJsonElement`, independent of the model),
  `keys ⊆ CatalogueEntry.serializer().descriptor.elementNames`. **at HEAD:** red (`installs`, `flashChip` missing).
  **reddened by:** removing `installs` from `CatalogueEntry` (`Catalogue.kt`). **reaches the guard:** the fixture is cut
  from the live list and must contain both keys (assert that first: some entry has `installs`, some has `flashChip` — the
  positive).
- **T05-round-trip** `PublishedListTest.reEncodingKeepsEveryValue` and `CatalogueStoreTest.theSavedListKeepsEveryValue` —
  decode with `PublishedList.json`; encode with `Json(PublishedList.json) { encodeDefaults = true }` (the encodeDefaults
  trap, RL "Trap"); for every source key, re-encoded value == source value (`JsonElement` equality). Cache path:
  `CatalogueStore(dir, PublishedList(fetch = { fixtureBytes }), Dispatchers.Unconfined) { now }.refresh()` writes
  `catalogue.json` (precedent `CatalogueStoreTest.kt:15-18`); read it back and assert `flashChip`/`installs` are in it. **at HEAD:** red. **reddened by:** marking `flashChip` `@Transient`.
  **reaches the guard:** comparing values (not only keys) catches a field kept under the wrong name/type.
- **T05-unknown-enums** `PublishedListTest.unknownValuesBecomeNull` + `CatalogueStoreTest.aSavedListWithUnknownValuesStillLoads`
  — inline JSON with `flashChip:"future"`, `packageOrigin:"zzz"`, `whatSource:"x"`, `otherRelease.packageOrigin:"zzz"`
  plus one normal entry: decodes; those fields `null`; the normal entry intact (positive). **at HEAD:** red
  (`SerializationException`). **reddened by:** removing `coerceInputValues` from `PublishedList.kt:53-57` (first test) or
  `CatalogueStore.kt:93` (second — it reads a pre-written `catalogue.json`, so it uses the store's own `json`).
  **reaches the guard:** each test goes through the production `Json` instance, not a test copy.
- **T05-listed-on** `FilterTest.listedOnNamesMutantCloudAndTheFirmwareHub` — listing 8 → "mutant.cloud"; 15 → all four
  joined " + " in order; `packageSource` Hub → "The Firmware Hub mirror". **at HEAD:** red (blank for 8). **reddened by:**
  dropping the `MUTANT_CLOUD` clause in `listedOn`. **reaches the guard:** pure.
- **T05-listing8-untested** listing 8 → `isHardwareTested == false`. **at HEAD:** green — guards RL P2's "In testing"
  meaning, not the fix. **reddened by:** adding `MUTANT_CLOUD` to `isHardwareTested` (`Catalogue.kt:73`). **reaches the guard:** pure property
  on an entry built with `listing = 8`.
- **T05-hub-chip** `HubFlashChipTest` (iOS `FlashChipTests.swift:19-34`) — old/new/any → support and `savesOn` strings
  (split: the `support` half is task 07's `SwitchingCheckTest.readsWhichChipsAFirmwareKnows`);
  absent/"future" → `null`; survives `CatalogueStore` save/load. **at HEAD:** red. **reddened by:** `Old.support =
  BOTH_CHIPS`. **reaches the guard:** decodes through `PublishedList.json`.
- **T05-chip-words** `FlashChipWordsTest` (iOS `:36-45`) — one name, two names, newer chip. **at HEAD:** red (doesn't
  compile). **reddened by:** "know" for one name in `FlashChipWords.warning`. **reaches the guard:** pure string
  function; prefix and `contains` assertions as the iOS precedent.
- **T05-most-installed** `FilterTest.mostInstalledSortsByCountWithUncountedLast` (iOS `FirmwareOrderTests.swift:23-36`) —
  order, stability on name index, `firstUncounted`, `null` when none counted. **at HEAD:** red. **reddened by:**
  `installs ?: Int.MAX_VALUE`. **reaches the guard:** pure.
- **T05-stored-scope** `FilterTest.keepsThePickedFilter` (iOS `KindFilterTests.swift:28-36`) — `storedValue` round trip for
  All/Kind/BuiltFromSource; `offered` falls back to All when the kind isn't offered; `InMemoryAppSettings` keeps it.
  **at HEAD:** red (doesn't compile). **reddened by:** `offered` returning the stored scope unchecked. 
  **reaches the guard:** pure over `FirmwareScope`; `offered` gets a scope list built by `FirmwareFilter.scopes(entries)` from entries
  that lack the stored kind (precedent `FilterTest.kt` scopes cases).
- **T05-description** `FirmwareDescriptionTest` (iOS `FirmwareDescriptionTests.swift:8-20`) — `**bold**`, `_it_`,
  `` `code` ``, `[text](https://x)` → runs; `plain` keeps link text, drops the URL; whitespace kept; unmatched `*` stays
  literal. **at HEAD:** red. **reddened by:** emitting the link target. **reaches the guard:** pure.
- **T05-startup-v16** `StartUpTest.v16IsAStartUp` — `StartUp.V16.label == "Changed by V16"`, detail verbatim,
  `StartUp.entries` order `MVave, V16, MBootloader, OpenFirmware, NotSure`. **at HEAD:** red (doesn't compile).
  **reddened by:** dropping `V16` from `StartUp` (or moving it last: the radio order). **reaches the guard:** pure enum;
  precedent `StartUpTest.theAnswerIsRemembered`.
- **Blocker record — DataStore keys (step 6)** (at HEAD: n/a, nothing to run; reddened by: n/a; reaches the guard: n/a —
  a record, not a case): no unit test can observe `DataStoreAppSettings`:
  `grep -rln "DataStoreAppSettings" app/src/test model/src/test` → no output; no Robolectric/instrumented dep (harness
  facts above); 2026-11-09, `f5b9505`. Proven instead by compile (it implements the interface) and UAT A2 (relaunch keeps
  the filter).

### Task 06 (`:model` library)
- **T06-keeps-releases** `LibraryTest.keepsEachReleaseItSaves` — inverts `savesAndReplacesAnOlderRelease` (`:179-208`):
  both items present, `savedCopies(entry).size == 2`. **at HEAD:** red; the test it replaces is green at HEAD asserting
  the behaviour being removed, and is **deleted** in this task (counted). **reddened by:** restoring the delete loop at
  `FirmwareLibrary.kt:236-239`. **reaches the guard:** two downloads with distinct bytes (`seed = 14`), as the precedent.
- **T06-other-build** `otherBuildNeedsTheSameVersion` — a saved `1.0` is `outdated` for a `1.1` entry and not `otherBuild`;
  a saved `1.1` other build is. `isSaved` false when outdated. **at HEAD:** red (a saved `1.0` counts as `otherBuild`
  only when not outdated today, and `isSaved` doesn't exist). **reddened by:** dropping `!ReleaseVersion.differs(...)`
  from `otherBuild`. **reaches the guard:** items saved through `download` with distinct bytes and versions, as
  T06-keeps-releases (which this task makes possible: at HEAD the second download deletes the first).
- **T06-v16-download** `downloadsTheOfficialEntryCheckingItsHashNotTheList` — library built with `officialOf = { if
  (it.product == "FM-1_016") OfficialFirmware.V16 else OfficialFirmware.official(it) }`; entry `isOfficial = true` with
  `size`/`sha256`/`product` of a *different* file (the list lagging M-VAVE); fetch returns `SamplePackage.file("FM-1_016")`:
  returned item `source == Official`, `name == "FM-1 V16"`, `version == "Official"`, `sha256 ==` the synthetic file's,
  its file exists in the library directory; `library.official!!.sha256 == OfficialFirmware.V15.sha256`.
  **at HEAD:** red (`HashMismatch`). **reddened by:** (a) restoring the size/sha check for official entries → `HashMismatch`;
  (b) restoring `save()`'s `builtIn?.takeIf { source == Official }` short-circuit → returns the built-in V15 (sha
  assertion fails). **reaches the guard:** the seam (P-D6) is the only way to make a synthetic package official; the
  library is loaded with the real bundled V15, so `builtIn != null` and the trap is live.
- **T06-not-official** `refusesAnOfficialEntryThatIsntV15OrV16` (renamed `:158-177`; iOS `OfficialPackageTests` unknown
  `FM-1_017`). **at HEAD:** green — guards "official only by hash", not the fix. **reddened by:** saving any
  `isOfficial` entry as Official. **reaches the guard:** after the checks are skipped, `officialOf` (production) returns
  `null` for `FM-1_017`, so `NotOfficial` is the hash gate's doing; message contains "V15 or V16" (red at HEAD — text).
- **T06-v15-is-built-in** `downloadingV15FromTheListReturnsTheBuiltInCopy` — fetch returns the real V15 bytes: returns the
  built-in item, no new file. **at HEAD:** green — guards the narrowed `save()` still deduplicates, not the fix.
  **reddened by:** removing the `existing` check from `saveOfficial`. **reaches the guard:** production `officialOf`
  recognises the real V15 bytes (the bundled file via `fm1.v15`), so the official branch runs; "no new file" is checked by
  listing the library directory (positive: the returned item `== library.official`).
- **T06-add-v16** `addRecognisesV16ByHash` (seam as T06-v16-download) → Official "FM-1 V16", file saved. **at HEAD:** red
  (doesn't compile: no seam; `add` only knows V15). **reddened by:** `add` comparing with `OfficialFirmware.V15` instead of
  `officialOf(…)`. **reaches the guard:** the seam makes the synthetic package official; `builtIn` is loaded, so the save
  trap is on this path too.
- **T06-stock-v16** `aStockLoaderReportingV16CanBeFinishedByV15` — `libraryWithStockLoader()` (`:32-44`):
  `finishers(ota-FM-1_016)` contains the stock item. **at HEAD:** red (`:172` compares with `ota-FM-1_015` only).
  **reddened by:** reverting to `loader.text == STOCK_LOADER_IDENTITY`. **reaches the guard:** the stock item's real V15
  file has the stock loader SHA, so only the identity predicate decides.
- **T06-custom-loader-narrow** `aCustomLoaderStillMatchesOnlyItsOwnPackage` — library with the stock item and an `FM-1_916`
  import: `finishers(ota-FM-1_916) == [the FM-1_916 item]`. **at HEAD:** green — guards P-D5's narrowness, not the fix.
  **reddened by:** widening the predicate to any `ota-FM-1_*`. **reaches the guard:** the stock item is present, so a
  wide predicate would add it.

### Task 07 (`:model` switching and readiness)
- **T07-running** `SwitchingGuideTest` ports of iOS `SwitchingGuideTests.swift:28-98` (`mVavesFirmwaresAreKnownByTheirIdentity`,
  `aSavedGuideTellsTransitFM1FromV15`, `aSavedGuideForAnotherIdentityIsIgnored`, `theStartUpFillsInABootloader…`,
  `nothingIsKnownAboutAnUpdateLoaderOrAnUnknownFirmware`, `startUpsAndBootloadersMatch` (V16↔V16),
  `v15IsADestinationOfItsOwn` (real V15 via `fm1.v15`), `v15ThroughTransitFM1KeepsTheBootloader`).
  **at HEAD:** red (doesn't compile). **reddened by:** mapping `StartUp.V16.bootArea` to `V15`; `running()` ignoring
  `startUp == MBootloader`. **reaches the guard:** pure over fm1 types.
- **T07-v16-destination** `aV16PackageIsADestinationOfItsOwn` — `destinationOf(SamplePackage FM-1_016, official =
  OfficialFirmware.V16) == V16`; with production default → `FELUCCA_PLATFORM` (no hash). **at HEAD:** red.
  **reddened by:** matching on `package.identity` instead of the official hash in `destinationOf` (the production-default
  row turns `V16`). **reaches the guard:** the synthetic package reports `FM-1_016`, so only the hash (the `official`
  argument) can tell; both rows use the same bytes.
- **T07-before-v16** `SwitchingCheckTest.installingV16WarnsAboutGoingBack` — `SwitchingCheck.from(planned = [(item,
  FM-1_016 synthetic)], identity = FM-1_900, officialOf = { V16 })`: `installsV16`; with production `officialOf` → false.
  **at HEAD:** red. **reddened by:** computing `installsV16` from `destination == V15`. **reaches the guard:** pure; the
  `false` twin proves the seam, not the identity, decides.
- **T07-after-v16** `v15AfterV16WarnsAboutMono` — real V15 package; (a) identity `officialV16`, no saved guide →
  `followsV16`; (b) identity `FM-1_916`, saved guide `bootArea = V16` → `followsV16`; (c) identity `FM-1_916`, guide
  `bootArea = V15` → not. **at HEAD:** red. **reddened by:** dropping `|| running.bootArea == V16` (b fails) or the
  identity clause (a fails). **reaches the guard:** (c) is the positive control that the V15 destination is detected.
- **T07-chip** `onlyFirstChipFirmwaresAreFlaggedUnlessTheFm1SaysItHasThatChip` — a synthetic package holding P25Q80 only →
  listed in `knowsOnlyFirstChip` when `flashChipId` is `null` or UC25HQ80 (`hasNewerFlashChip` true only then), not when
  P25Q80; a package whose code can't be read (`flashChipSupport == null`: the overflowing app record of
  `ApplicationAreaTest.rejectsAnOverflowingApplicationRange`, via `SamplePackage.firmwareWithAppRecord`) with listed chip
  `Old` → flagged (fallback); a package with no constant (`NO_CHECK`) and listed `Old` → **not** flagged (the package
  wins, iOS `:88`). **at HEAD:** red. **reddened by:** `listed ?: package.flashChipSupport` (the `NO_CHECK`+`Old` row
  flags). **reaches the guard:** packages built with `firmwareWithCode` so `flashChipSupport` is read through the real
  decipher.
- **T07-depends-on-startup** `anUnknownBootloaderAsksTheStartUp` + `anUnknownBootloaderGivesTheWarningsForAChangedOne`
  (iOS `:99-114`) — Felucca original-check package over `FM-1_900` with `startUp == null` → `dependsOnStartUp`, warnings
  include the `IF_CHANGED` prefixed line. **at HEAD:** red. **reddened by:** `dependsOnBootArea` false for
  `FELUCCA_ORIGINAL_CHECK`. **reaches the guard:** the package is built with `firmwareWithCode("..FELUCCA-LOADER-1..")`
  so `updateCode` is read through the real decipher; `startUp == null` and identity `FM-1_900` (no saved guide) leave the
  boot area unknown, which is the only state that asks.
- **T07-transit** `transitFM1WarnsAboutV16AndItsIdentity` (iOS `SwitchingGuideTests.swift:115-123`) — a TransitFM1 guide
  (`OWN_LOADER`, `officialV15`, boot area `V16`): `SwitchingWords.warnings(guide)` is two lines, the first "…can't install
  V16 straight away. FM-SWITCH puts MIDIMON on first…", the second "TransitFM1 reports itself as V15…". **at HEAD:** red
  (doesn't compile). **reddened by:** dropping the `reportsOfficialIdentity` warning in `SwitchingWords.warnings`.
  **reaches the guard:** pure over a hand-built `LeavingGuide`, as iOS's static `transit` guide.
- **T07-readiness** `ReadinessTest` rewrite: chain is switching; already-installed blocks; `canReinstallOfficial` only for
  a single step, `REINSTALL`, official identity; `confirmsNotOfficial` enables and makes it switching; `knowsItIsntOfficial`
  from a saved TransitFM1 guide; `isMissingFirstStep` blocks; `needsStartUp` from `switching.dependsOnStartUp`.
  **at HEAD:** red (doesn't compile). **reddened by:** `isAlreadyInstalled` ignoring `knowsItIsntOfficial`. 
  **reaches the guard:** `InstallReadiness` is a plain data class; precedent `ReadinessTest.kt:38-49` builds it directly.
- **T07-fixed-firmware** `FixedFirmwareTest` — saved `midimon` with `AC79BLID` code → returned without fetching (fetch
  counter 0); no saved copy → downloads the `midimon` entry (fetch counter 1, positive) and returns it; downloaded package
  without the fixed check → `Failure.NotFixed`; no entry → `NotListed`. **at HEAD:** red (doesn't compile). 
  **reddened by:** skipping the `updateCode == FELUCCA_FIXED_CHECK` check on saved copies (a saved MIDIMON without `AC79BLID` is
  returned). **reaches the guard:** a real `FirmwareLibrary` in a temp dir (precedent `LibraryTest.library()`), packages
  from `SamplePackage.firmwareWithCode`, and a counting `fetch` lambda — the counter is the positive.
- **T07-state** `BeforeYouStartStateTest` — the not-official tick turns a reinstall into a switch needing the backup tick;
  `confirmsReinstallingOfficial`. **at HEAD:** red (doesn't compile). **reddened by:** omitting `confirmsNotOfficial` from
  `isEnabled`. **reaches the guard:** plain data classes; precedent `BeforeYouStartStateTest.kt` builds both directly with
  `checked = Found(REINSTALL)` and identity `officialV15`.
- **Blocker record — sheet wiring (task 07 step 7)** (at HEAD / reddened by / reaches the guard: n/a — a record): no Compose UI test dep (harness facts; same grep, 2026-11-09,
  `f5b9505`). Proven by the task 12 screens and UAT A3.

### Task 08 (`:model` controller)
- **T08-might-have-finished** `InstallFinishTest` (iOS `InstallFinishTests.swift:12-40`, five cases). **at HEAD:** red
  (doesn't compile). **reddened by:** `written >= 0.5` in `FlashJob.mightHaveFinished` (the part-way case flips); not
  excluding reinstalls. **reaches the guard:** pure function of an error and two values; the `DeviceDidNotReturn` case is
  the positive control.
- **T08-late-return** `FlashingTest.aLateLossStillFinishesWhenTheFm1ComesBackRunningIt` — `ControllableTransport`: unplug
  when the job phase becomes `Rebooting`, advance past fm1's `firmwareBoot + returnSearch` (43 s virtual) so the updater
  throws `DeviceDidNotReturn`, plug back in before `FlashJob.RETURN_SEARCH` (20 s) ends: job `Succeeded("FM-1_916")`, log
  contains "Looking for the FM-1", history recorded. **at HEAD:** red (job `Failed`). **reddened by:** removing the
  `confirmed()` fallback in `DeviceController.run`. **reaches the guard:** the sim has already written the package
  (identity `FM-1_916` after reboot) before the unplug; `DeviceDidNotReturn` is a `mightHaveFinished` error; the log line
  is the positive that the search ran.
- **T08-started-on-v16** `anInstallFromV16IsRemembered` — transport factory sets `installedIdentity = officialV16`; flash
  the built-in V15 with `confirmsSwitchingFirmware`: `job.startedOnV16`, phase `Succeeded("FM-1_015")`. **at HEAD:** red
  (doesn't compile). **reddened by:** dropping `|| identity.value == officialV16` in `flash()`. **reaches the guard:**
  `configure()` + advance so `identity.value == FM-1_016` before `flash` (assert it first: positive).
- **T08-records-guide-and-startup** `anInstallSavesItsGuide` / `installingV15SetsMVavesStartUp` (iOS `:173-198`) and
  `installingV16SetsV16sStartUp` — the V16 case flashes `SamplePackage.withLoader("FM-1_016", <V15's loaderBytes>)` added
  through `library.add` (Imported is fine: `LeavingGuide.from` keys the boot area on the identity and the stock-loader SHA,
  FK `LeavingGuide.swift:124,278-283`): afterwards `settings.startUp == StartUp.V16` and
  `history.records.first().guide!!.bootArea == "v16"`. **at HEAD:** red. **reddened by:** not calling
  `settings.startUp = …` in `record()`; or writing the boot area only when `installedBy == null`. **reaches the guard:**
  the synthetic package carries V15's real loader so `writesBootArea` is true; the sim installs it (switch confirmed).
- **T08-needs-a-step** `aRefusalNeedsMIDIMONUnlessTheFirmwareRunningTakesV15` (iOS `:124-153`) replacing
  `aRefusalWithMVavesStartUpSuggestsV15First` (`FlashingTest.kt:576-608`) where the guide now decides. **at HEAD:** red for
  the guide branches; the start-up branch is green — guards the fallback. **reddened by:** ignoring the guide in
  `FlashJob.needsAStepFirst`. **reaches the guard:** the pure cases call `FlashJob.needsAStepFirst` with a refusal error
  and hand-built guides (iOS `:124-153`); the controller case keeps the precedent's simulator options `comparesBootArea = true` (+ m-bootloader version)
  (`FlashingTest.kt:541-608`), which makes the sim refuse at the boot-area compare.
- **T07-history-compat** (task 07) `FlashHistoryTest.readsHistorySavedBeforeGuides` — a pre-change `flash-history.json` (no `guide`)
  loads; a guide round-trips (FK `canBeSavedWithTheFM1`); an unknown `updateCode` raw → `guide == null`, record kept.
  **at HEAD:** red (doesn't compile). **reddened by:** making `guide` non-defaulted in `FlashRecord`. 
  **reaches the guard:** `FlashHistory(dir, …).load()` reads a hand-written file, precedent `FlashHistoryTest.kt`; the old record's
  `name` is asserted (positive).
- **T08-progress** `StandardLookTest.progressRunsThroughTheStagesInOrder` updated: `SecondPass(.5)` → `Stage.Installing`;
  stages are three. **at HEAD:** green asserting `Checking` — updated in this task (counted). **reddened by:** mapping
  `SecondPass` to `Restarting` in `InstallProgress.position`. **reaches the guard:** pure over `FlashJob` values built by the
  precedent's `job(...)` helper (`StandardLookTest.kt:27-37`).
- **T08-v16-name** `StandardLookTest.summarySaysWhatTheFm1IsDoing` + V16 → "Connected, running the official firmware (V16)",
  title "V16". **at HEAD:** red. **reddened by:** the V15-only replace in `DeviceSummary.screenTitle`. 
  **reaches the guard:** `DeviceSummary.from(officialV16, emptyHistory, emptyList())`, precedent in the same test; empty history and list
  so the official-name branch, not a history name, produces the text.
- **T08-chip-state** `FlashingTest.scanningKeepsTheFlashChip` — sim `flashChipId = UC25HQ80` → `controller.flashChipId.value`.
  **at HEAD:** red (doesn't compile). **reddened by:** `scan()` using `identify()` (chip stays `null`). 
  **reaches the guard:** `configure(Simulated)` with a transport factory that sets the option, then an advance of
  `fullIdentityWait + 1 ms`; `identity.value == FM-1_900` is asserted first (positive).

### Tasks 09–12 (`:app`)
- **T09-contrast** `ContrastTest` gains the danger pair (light/dark on card). **at HEAD:** red (no `danger`). 
  **reddened by:** a light value that fails 4.5:1. **reaches the guard:** precedent harness.
- **Blocker record — composables (tasks 09–12)** (at HEAD / reddened by / reaches the guard: n/a — a record): Compose UI isn't testable here — the grep above
  (2026-11-09, `f5b9505`) shows no Compose test, Robolectric or screenshot dependency, and adding one is a dev-dependency
  decision for David, not this plan. Proven instead by: the visual protocol's PNGs per task ("Screens" lists), read by the
  orchestrator before commit; UAT A2/A3; logic under them is unit-tested in tasks 05–08
  (`InstallPresentationTest` stays the only `:app` logic test; extend it only where a pure helper is added).

### Task 13/14
- Docs: no test; reviewed against the task 13 checklist. UAT steps carry their own expected/red-when lines (task 14).

## Risks

1. **Switching off during pass 2 can leave an FM-1 that never starts** (RV §7.1, FK stage W3). Today's texts say "it
   stays in update mode until one finishes" (`Fm1UpdateError.kt:93,106,140`). Mitigation: 36905bb texts (task 02,
   guarded by T02-texts), the checklist tick, Installing line and interrupted steps (task 12), "Checking" stage removed
   (task 08). UAT walks the interrupted screen.
2. **Stranded mid V16→V15 after an app restart** (RV §7.2): the stock loader answers `ota-FM-1_016`, which
   `packagesThatCanFinish` doesn't match, so V15 is never offered. Mitigation: P-D5 (task 06), T06-stock-v16 and
   T06-custom-loader-narrow; hardware H3. Not filed upstream (David's call).
3. **The save trap** (`FirmwareLibrary.kt:306`): saving V16 returns the built-in V15 silently — a later "install V16"
   would install V15. Mitigation: task 06 step 4, T06-v16-download (b).
4. **Wrong routing** (RV §7.3): V16 picked on Felucca 1.6.1/TransitFM1 is refused at the header (harmless); V15 picked on
   an original-check firmware over V16's bootloader is refused, and today's `needsAStepFirst` (`DeviceController.kt:304-309`)
   says "V15 first", which is wrong after V16. Mitigation: guides (03), `StartUp.V16` recorded after V16 (08,
   T08-records-guide-and-startup), guide-based `needsAStepFirst` (T08-needs-a-step), MIDIMON first (P-D1). Routes over
   V16's bootloader are FK's expectations, not all tried on hardware (FK `LeavingGuide.swift:91-93`): the copy for
   `UNCERTAIN` says "try it; if refused, nothing changes".
5. **V16 over V16** (RV §7.4) reached staging as SAME_IDENTITY; now REINSTALL (T02-v16-reinstall), reinstallable only
   through the look-alike tick. Whether V16 refuses itself is UNVERIFIED-EXTERNAL — no copy promises either way.
6. **Mono V15 and broken voices with no fix on a phone** (RV P4, §7.5). Mitigation: before-V16, after-V16 and still-mono
   copy say so plainly (appendix); no copy names M-Flasher or any non-Mac tool as able to clear 0xFBD80
   (UNVERIFIED-EXTERNAL). David's H2 records the symptom. Back up before H1/H2.
7. **Shortened identities read as `IdentityMismatch` after a good write** (RV §7.6), which could prompt a retry or a
   power cut. Mitigation: task 04 full-identity query (T04-shortened). Whether MIDIMON shortens is UNVERIFIED-EXTERNAL;
   the fallback keeps the short identity (T04-three-digits-stay), so the worst case is today's behaviour.
8. **Re-opening the FM-1 mid-write on Android MIDI** (fc809ca `lookForReturn` probes ports while the loader connection is
   open). Whether `AndroidMidiTransport.open()` succeeds or fails cleanly then is unknown. Mitigation: it runs only at
   ≥95 % and after 5 s of silence; `findAllUnlocked` skips ports whose `open` throws `MidiTransportException`
   (`Fm1Updater.kt:114-119`) — VERIFY-AT-EXECUTION that `AndroidMidiTransport.open` throws that type (not
   `IllegalStateException`) on a busy device, else wrap it. Hardware H5.
9. **Every custom-firmware handshake now waits up to 300 ms more** (full-identity query unanswered). Scans and model
   tests slow by that; `FlashingTest` advances must cover it (task 04 step 8). On real devices the extra query is a
   SysEx the firmware ignores (FK: "Other firmwares and the update loader don't answer it"); only sent to probe-matched
   ports (AGENTS.md).
10. **MIDIMON downloaded automatically** from Before you start (P-D1): a network fetch and a firmware the person didn't
    pick. Mitigation: only when the guide says the route needs it; the plan line says why (`SwitchingWords.firstStep`);
    failure blocks Install with the reason (`isMissingFirstStep`). If David rejects P-D1, drop task 07 step 5 and
    task 12's install-first button.
11. **Lenient decoding hides bad data.** `coerceInputValues` also turns a JSON `null` for a non-null defaulted field into
    the default. Acceptable (the list is machine-built); the key-set test (T05-key-set) still catches new fields iOS adds
    (observation 0655).
12. **UI tasks and lint.** `UnusedResources` is an error, so a task that orphans a string must delete it; 10 and 11 share
    `strings_firmwares.xml` and run in sequence, 12 owns `strings_install.xml` and runs beside them.
13. **Licence.** New Kotlin derived from `S` 0.2.1 files (MPL-2.0 since `aac72a9`), including verbatim strings. NOTICE
    records facts only; the licence choice is David's (Decision). Flag in the final report.
14. **Real V16 recognition has no automated test** (the file can't be in the repo). Constants are guarded by
    T02-official-list; recognition of the real bytes is UAT A3 (the app downloads it from M-VAVE at run time).
15. **History format change.** `FlashRecord.guide` is additive with a default; T07-history-compat guards old files.
16. **iOS's c15c992 (restart the simulated FM-1 when any switch changes) is not ported**; U0 notes already say switches
    apply after "Reset the simulated FM-1". Out of scope; mention in UAT.

## Appendix — Verbatim copy (Android wording)

Each string is one physical line in Kotlin; the fences below wrap only for this document. `'` is a plain apostrophe in
Kotlin constants and `\'` in XML. iOS sources are given so tasks can diff.

- `SwitchingWords.BEFORE_V16_WARNING` (replaces `S/App/Model/InstallReadiness.swift:153-156`):
  > Going back to V15 later can't be fully done from this phone. V15 goes back on over USB, but after V16 it plays one note at a time until its saved data is cleared, which needs a computer: FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. V16 isn't included with this app; it's downloaded from M-VAVE.
- `SwitchingWords.AFTER_V16_WARNING` (replaces `:165-169`):
  > After V16, V15 plays one note at a time, as V16 leaves data behind that V15 misreads. This phone can't clear it: that needs FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. Some of V15's sounds may also be wrong or silent, and this app can't put back the factory sounds yet.
- `SwitchingWords.STILL_MONO` (replaces `S/App/Views/SwitchingWords.swift:78-82`; shown alone, as Android has no
  factory-sounds card):
  > V15 is on, but after V16 it plays one note at a time until its saved data is cleared, which can't be done from this phone. On a Mac, install V15 again in FM-SWITCH's Backup & rescue, with Start the new firmware fresh ticked. Some of its sounds may also be wrong or silent, and this app can't put back the factory sounds yet.
- `StartUp.V16`: label "Changed by V16"; detail "M-VAVE's V16 has been installed on it." (verbatim iOS).
- Everything else in `SwitchingWords`, `InstallWords` (`S/App/Views/InstallWords.swift:6-18`), `FlashChipWords`,
  `InstallReadiness.untestedWarning`, the list/page copy in RL "UI copy (iOS, exact)", and FK texts: verbatim from the
  cited Swift, with "this iPhone"/"iPhone or iPad" → "this phone" and `ThisDevice.name` → "phone".
- `before_start_computer_recovery` (`RES/strings_install.xml:17`) is deleted in task 07 (the switching warnings card
  replaces it).

## Open questions

None blocking. P-D1 (automatic MIDIMON) is the one decision David may want to reverse; the plan says what to drop.

## Tasks

**Wave 1** — run together
- [x] 02 — `:fm1`: official releases, V16 identity, reinstalling the official firmware, error texts, flash chip IDs (depends on: none)
- [x] 05 — `:model`: catalogue fields, lenient decoding, orders and scopes, list settings, words, descriptions, `StartUp.V16` (depends on: none)
- [x] 13 — Docs: architecture, NOTICE facts on MPL-2.0, AGENTS confirmation rule (depends on: none)

**Wave 2** — run together
- [ ] 03 — `:fm1`: update codes, leaving guides, flash chip support, sample packages with code (depends on: 02)
- [ ] 04 — `:fm1`: full identity and flash chip in the handshake; finish when the firmware answers (depends on: 02)
- [ ] 06 — `:model` library: keep every release, V16 by hash, the save trap, stock-loader resume (depends on: 02)
- [ ] 09 — `:app` shared UI pieces: danger colour and button, flash chip notice, styled descriptions, Settings copy (depends on: 05)

**Wave 3** — run together
- [ ] 07 — `:model` switching guides, words, readiness, MIDIMON first, saved guides (depends on: 02, 03, 05)
- [ ] 10 — `:app` firmware list and My firmwares (depends on: 05, 06, 09)

**Wave 4**
- [ ] 08 — `:model` flash job and controller: pass 2, late returns, guides and start-up, flash chip state (depends on: 04, 07)

**Wave 5** — run together
- [ ] 11 — `:app` firmware page (depends on: 05, 06, 08, 09, 10)
- [ ] 12 — `:app` Before you start, installing and outcomes (depends on: 05, 07, 08, 09)

**Wave 6**
- [ ] 14 — UAT (depends on: 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13)

### Shape

- Six waves; the widest are waves 2 (03, 04, 06, 09) and 1 (02, 05, 13). Critical path: 02 → 03 → 07 → 08 → 12 → 14
  (equally 02 → 04 → 08 and 05 → 07 → 08). The Planner's chain was 13 waves long.
- **No task 01.** The base gate is observed, not executed: the Splitter ran `scripts/check.sh`'s tests with
  `--rerun-tasks` at `f5b9505` (`:fm1` 124, `:model` 92, `:app` 15, all passing), and every task carries it in `at base:`.
  Running it as a wave-1 task beside 02 would have measured a half-edited tree. Numbers 02–14 are kept so the Implementation
  Plan and Testing Strategy IDs (T02-…, T14) still line up; 08 is numbered before 09 although it runs a wave later — every
  `Depends on:` still points at a lower number.
- **Moved for parallelism:** `Fm1FlashChip` 03 → 02 (04 no longer waits for 03); `HubFlashChip.support` 05 → 07 (05 needs
  nothing from `:fm1` and joins wave 1); docs (13) depend on nothing — they use the symbol names the task files fix, and UAT
  step 7 checks every cited name exists.
- **Shared-file-only edge:** 10 → 11 (`strings_firmwares.xml`; lint's `UnusedResources` makes concurrent edits unsafe). It
  costs nothing: 11 already waits for 08 in wave 4.
- **Same Gradle tree.** Same-wave tasks build one checkout; concurrent `./gradlew` runs wait on each other's locks, and a
  compile error in a sibling's half-edited path is "wait and re-run" (each task says so). Separate worktrees per Executor
  are the Butler's call.
- **Visual review timing.** Tasks 09–12 build the whole app for screenshots, so the orchestrator runs the visual protocol
  after every Executor in that wave has handed back and before the wave's commits, or in a worktree holding only that
  task's edits. A sibling's half-edited `:fm1`/`:model`/`:app` path would otherwise break `:app:assembleDebug`.
