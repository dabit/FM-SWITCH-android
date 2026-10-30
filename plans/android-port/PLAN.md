# Port FM-SWITCH to Android

- Tracker: kata
- Project: fm-switch-android
- Issue: none
- Issue URL: none
- Created: 2026-10-30
- Started: 2026-10-30
- Base commit: main (local; origin has no branches yet) @ aa665298b155bf1505c95502cfa72440e0ac0da1
- Source app: ../FM-SWITCH @ 67ee8c24078d8ae23e4331c9e830e17f174f598f
- Summary: First Android release of FM-SWITCH: Kotlin protocol core, simulated FM-1, firmware list and offline library, and the Standard look's core flows.

## Decisions

- Tracking: no issue for now — Butler default, not asked (the question was put and not answered; an issue can be linked later).
- Base: first commit on local `main`, unpushed — asked and answered ("create first commit with AGENTS.md and jdi config first").
- Look: Standard look (`REDESIGN.md`) first; Retro in a later plan — Butler default, not asked (offered, no objection).
- Scope: protocol in Kotlin, simulated FM-1, firmware list and offline library, core flows (change firmware, resume, saved copies), app skeleton (no CI — see below). Deferred: Retro, tip jar, alternate icon, tablet two-pane layout, backups, factory-voice restore — Butler default, not asked (offered, no objection).
- Platform: minSdk 26, distributed by sideloading / GitHub releases — Butler default, not asked (offered, no objection).
- Protocol source: port FM1Kit 0.4.5 (MIT) to Kotlin, keeping its notice and M-Flasher's MIT notice on `ApplicationArea` — Butler default, not asked (research §0 P2).
- Firmware list: published `firmwares.json` only; no on-device rebuild fallback (AGENTS.md) — Butler default, not asked.
- Errors: plain Standard-look messages; FM1Advice not ported — Butler default, not asked.
- V15-over-other-bootloaders refusal: ported, as a safety check — Butler default, not asked.
- Foreground service type `connectedDevice` for the flash — Butler default, not asked (`shortService` caps at ~3 min, too close for a chained swap).
- Out of scope here as well: Help, Starred, update notices, the release/build picker, the "newer at source" check, screenshots — Butler default, not asked.
- Android copy: new plain-words strings following REDESIGN.md, replacing iOS-specific ones ("takes a Mac", "Files", "iPhone") — Butler default, not asked.
- applicationId `dev.drewr.fmswitch`, same as the iOS app — asked and answered ("the ios app id is fine").
- compileSdk 37, targetSdk 36: latest stable Compose BOM (2026.09.00) and Navigation (2.10.2) need compileSdk ≥ 37; targetSdk stays 36 so runtime behaviour (foreground-service rules) is what research covered — Butler default, not asked (task 01 blocker, 2026-10-30).
- No CI: the local check script is the gate; plan step 16 (GitHub Actions) is dropped — asked and answered ("no ci").

## References

- `plans/android-port/research.md` — full findings: protocol constants, byte layouts, timing, test vectors, simulator behaviour, app model, toolchain. Read this first.
- `doc/architecture.md` — draft architecture ([fact]/[proposal] marked).
- FM1Kit 0.4.5 (88b609c), MIT: https://github.com/ocean/FM1Kit/tree/0.4.5 — primary protocol source; clone it at that tag when porting. Key files: `Sources/FM1Kit/Protocol/SevenBit.swift:11-50` (LSB-first bit stream packing), `Protocol/FM1Message.swift`, `Protocol/FM1Capability.swift:99-135` (Open arming), `Package/FirmwareContainer.swift:81-169`, `Updater/FM1Updater.swift:162-294`, `Updater/UpdaterTiming.swift:9-39`, `Updater/InstallCheck.swift:37-49`, `Updater/WriteProgress.swift:15-110`, `Transport/MIDITransport.swift:107-218`, `Package/SamplePackage.swift:45-149`, `Tests/FM1KitTests/ProtocolTests.swift`.
- `../FM-SWITCH/App/Model/DeviceController.swift:208-215` — each step resumes if a loader answers, else installs.
- `../FM-SWITCH/App/Model/DeviceController+States.swift:74-81` — `canResume` covers `loaderNotFound`.
- `../FM-SWITCH/App/Model/FirmwareLibrary.swift:150-169,186-216` — which packages can finish a loader; download validation.
- `../FM-SWITCH/App/Model/InstallReadiness.swift:73-75,115-123` — switching checks (first step only).
- `../FM-SWITCH/App/Model/CatalogueStore.swift:157-164` — the on-device rebuild fallback not being ported.
- `../FM-SWITCH/App/Catalogue/CatalogueBuilder.swift:4-27` — `MergedCatalogue`, `currentFormat` 1.
- `../FM-SWITCH/App/Standard/Firmwares/BeforeYouStartView.swift:123-163,178-194` — Standard-look confirmation copy (the "SWITCH FIRMWARE?" wording in AGENTS.md is Retro's).
- `../FM-SWITCH/REDESIGN.md` — Standard look rules; `../FM-SWITCH/AppTests/` — behaviour to port as tests.
- Android MIDI: https://developer.android.com/reference/android/media/midi/package-summary ; foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Published list: https://fm-1.drewr.dev/firmwares.json (formatVersion 1, 270 entries on 2026-10-30).

## Implementation Plan

Conventions used below:
- `FM1Kit <path>:<line>` = FM1Kit tag `0.4.5`, commit `88b609cd8c631b884d977b7954cb0b21351f2f04`, path under
  `Sources/FM1Kit/` unless it starts with `Tests/`. `../FM-SWITCH/<path>:<line>` = source app @ `67ee8c2`.
- **Getting FM1Kit (every step that ports from it):** clone it **outside the repository** (a scratch or temp dir), never
  into the working tree, and check the commit:
  `git clone --depth 1 --branch 0.4.5 https://github.com/ocean/FM1Kit "$SCRATCH/FM1Kit" && git -C "$SCRATCH/FM1Kit" rev-parse HEAD`
  → must print `88b609cd8c631b884d977b7954cb0b21351f2f04`. Nothing from the clone is committed except the two MIT notices (step 2).
- Modules (this **changes** the draft architecture's two-module [proposal] to three):
  - `:fm1` — pure Kotlin/JVM, package `dev.drewr.fm1` (`protocol`, `pkg`, `transport`, `updater`, `testing`). No Android deps.
  - `:model` — pure Kotlin/JVM, package `dev.drewr.fmswitch.model`: the app's data layer and state machines
    (DeviceController, FirmwareLibrary, CatalogueStore, InstallReadiness, …). Exists so the ported AppTests run as plain JVM
    tests under virtual time, with no Robolectric and no AGP unit-test quirks, and so data work can run in parallel with the UI.
  - `:app` — Android application `dev.drewr.fmswitch` (Compose, minSdk 26): Android MIDI transport, HTTP fetch, DataStore
    settings, foreground service, screens.
- **Virtual-time rule (binding on steps 3–8):** every suspending wait in `:fm1` and `:model` is `delay` /
  `withTimeoutOrNull` / `Channel.receive` on an **injected** `CoroutineScope` or dispatcher. No hard-coded `Dispatchers.*`,
  no `Thread.sleep`, no `System.nanoTime`/`currentTimeMillis` for timing decisions, no `runBlocking`. File I/O goes through an
  injected `ioDispatcher` (production `Dispatchers.IO`, tests the test dispatcher). One violation turns every updater test into
  a real-time (or hanging) test.
- **Semantic-drift rule:** port by meaning, not by name (research §3.8). Where a step names a ported type, the meaning
  stated in the step is the binding part.
- **Copy rule:** user-facing words are taken from the cited source string. Change only iOS-specific words
  (iPhone/iPad → phone/tablet, Files → "your files", Safari → browser, Mac-only features stated plainly per
  `../FM-SWITCH/App/Views/SafetyNoticeView.swift:42-51`). Never "VM, BTIF, hex, staging or loader" in the main flow
  (`../FM-SWITCH/REDESIGN.md:24-27`). Strings live in `app/src/main/res/values/strings_<area>.xml`, one file per step, so
  parallel steps don't edit the same file. Model-layer text that tests assert (error descriptions, refusal messages, stage
  names, summary lines) lives in `:model`/`:fm1` Kotlin, as it does in Swift.

### Step 1 — Gradle skeleton, toolchain, format/lint, check script
Create: `settings.gradle.kts` (modules `:fm1`, `:model`, `:app`), root `build.gradle.kts`, `gradle/libs.versions.toml`,
Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), `gradle.properties`, `.gitignore` (`.gradle/`, `build/`,
`local.properties`, `.idea/`, `*.iml`, `.kotlin/`), `.editorconfig`, `fm1/build.gradle.kts`, `model/build.gradle.kts`,
`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, a placeholder `MainActivity` showing one Compose `Text`,
`scripts/check.sh`.
- Versions: AGP **9.5.0** with Gradle **≥ 9.6.0** (developer.android.com/build/releases/about-agp, read 2026-10-30).
  Generate the wrapper with the mise Gradle (`mise exec gradle@9.7.1 -- gradle wrapper --gradle-version <≥9.6.0>`).
  VERIFY-AT-EXECUTION: Kotlin, Compose BOM, Compose compiler plugin, kotlinx-coroutines(+test), kotlinx-serialization,
  DataStore, androidx.browser, activity-compose, navigation-compose, Spotless — latest stable, recorded in the catalog.
  AGP 9 applies Kotlin to `:app` itself ("built-in Kotlin"); the `kotlin("jvm")`, `plugin.serialization` and
  `plugin.compose` versions in the catalog must equal the Kotlin version AGP 9.5 brings (read it from
  `./gradlew buildEnvironment`), or configuration fails. Follow developer.android.com/build/migrate-to-built-in-kotlin; do not
  apply `org.jetbrains.kotlin.android`.
- JDK: Kotlin `jvmToolchain(21)` in all three modules; **no toolchain auto-provisioning** (no foojay). `scripts/check.sh`
  resolves the JDK itself: use `JAVA_HOME` if set; else if `mise` exists, `export JAVA_HOME="$(mise where java@temurin-21)"`;
  else fail with "Set JAVA_HOME to a JDK 21." It must not rely on `java` on PATH (system java here is 25).
  `ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"`; fail with a plain message if that dir has no `platforms/`.
  These are properties of *this* machine; another machine must set them the same way.
- Android: `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`, `applicationId = "dev.drewr.fmswitch"` (matches the iOS
  bundle id, `../FM-SWITCH/project.yml:97`). Manifest from the start: `<uses-feature android:name="android.software.midi"
  android:required="true"/>`, `INTERNET`.
- Warnings as errors: Kotlin `allWarningsAsErrors = true` in all modules; Android lint `warningsAsErrors = true`,
  `abortOnError = true`, `checkDependencies = true` — and apply `com.android.lint` to `:fm1` and `:model` so lint
  actually analyses them (VERIFY-AT-EXECUTION: the plugin id and that `:app:lintDebug` reports their issues). Formatting: Spotless with ktlint over `**/*.kt` and `**/*.kts`.
- Tests: JUnit 4 via `kotlin("test-junit")` in all modules (one runner everywhere; Android instrumented tests are JUnit 4
  anyway) + `kotlinx-coroutines-test`. `:model`'s and `:fm1`'s `tasks.test` set
  `systemProperty("fm1.v15", rootProject.file("app/src/main/assets/firmware/FM-1_015.fwsc").absolutePath)` — the one copy of
  the bundled V15 (step 2) that JVM tests read.
- `scripts/check.sh`, modelled on `../FM-SWITCH/scripts/check.sh:1-45,138-170` (named groups, `step()` headers,
  unknown-group error, "Checks passed: …"): groups `lint` (`spotlessCheck`, `:app:lintDebug`), `build`
  (`:app:assembleDebug` + `:fm1:assemble :model:assemble`), `test` (`:fm1:test :model:test :app:testDebugUnitTest`),
  `device` (`:app:connectedDebugAndroidTest`, fails if `adb devices` lists none). No arguments = `lint build test`;
  `device` runs only when named. Human-readable output to stdout is fine: nothing captures it.
- Done: `./gradlew help` succeeds; `scripts/check.sh` passes on this machine with JAVA_HOME unset; the placeholder app
  installs with `./gradlew :app:assembleDebug`.

### Step 2 — Licence notices and the bundled V15 (depends on 1)
- Copy FM1Kit `LICENSE` to `fm1/LICENSE-FM1Kit.txt` and to `app/src/main/assets/licences/FM1Kit-LICENSE.txt`.
- Copy the M-Flasher notice (FM1Kit `Package/ApplicationArea.swift:1-19`, Julius Vitkauskas, MIT) verbatim to
  `app/src/main/assets/licences/M-Flasher-LICENSE.txt`; step 4 also puts it at the top of `ApplicationArea.kt`.
- Copy `../FM-SWITCH/App/Resources/Firmware/FM-1_015.fwsc` **unmodified** to `app/src/main/assets/firmware/FM-1_015.fwsc`
  and `FM-1_015-LICENCE.txt` beside it. Verify with `sha256sum` → `db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a`
  and size 699,956.
- Root `NOTICE.md`: the app's own licence status (none chosen yet — say so, don't invent one), FM1Kit MIT, M-Flasher MIT
  (`ApplicationArea.kt` only), V15 Apache-2.0 (asset only).
- Done: files present, hash matches. Tests in step 4 (`OfficialFirmwareTest`) and step 13 (`LicenceAssetsTest`).

### Step 3 — `:fm1` protocol, framing and inbox (depends on 1)
Ports: FM1Kit `Protocol/SevenBit.swift:11-50`, `Protocol/FM1Message.swift` (all), `Protocol/FM1Identity.swift`,
`Protocol/FM1Capability.swift` (+ `FM1BuildID`), `Transport/MIDITransport.swift:107-218` (`SysexInbox`). New:
`SysexFramer` (byte stream → `F0…F7` messages) replacing `Protocol/UMPSysex7.swift` (CoreMIDI's UMP form, **not** ported).
Files: `fm1/src/main/kotlin/dev/drewr/fm1/protocol/{SevenBit,Fm1Message,ReadRequest,Fm1Identity,Fm1Capability,BuildId}.kt`,
`.../transport/{SysexFramer,SysexInbox}.kt`; each ported file starts with
`// Ported from FM1Kit 0.4.5 <path> — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt`.
- Bytes are `ByteArray`/`UByte`-safe: mask with `and 0xFF` on every widen. Equality of byte arrays is `contentEquals`,
  never `==` (Kotlin `==` on `ByteArray` is reference equality; the simulator would silently stall).
- `SevenBit`: LSB-first bit stream, **not** "MSB byte + 7" (research §2.2).
- `Fm1Identity`: text parse splits at the **last** `_`; `ota-FM1C` armed form; equality and serialization by **text**
  (`FM-1_015` ≠ `FM-1_15`); `officialV15` built from text. `isOpenFirmware` = FM1Kit's predicate (8,000,000…8,000,999 or
  `FM1_COMMUNITY`) — **not** the app's `OpenFirmware.isOpen` regex, which is Retro-only and is not ported.
- `SysexFramer`: feed arbitrary chunks; emit complete messages; ignore data before an `F0`; a new `F0` discards an
  unfinished message; bytes `F8–FF` inside a message are skipped; any other status byte (≥ `0x80`, not `F7`) aborts the
  message; drop a message over 64 KiB. Copies input (Android's buffer is valid only during the call).
- `SysexInbox`: single reader, `Channel(256, BufferOverflow.DROP_OLDEST)`, `suspend fun next(timeout): ByteArray?`
  (null on timeout or closed), `drain()`, `close()` wakes the reader with null and later sends are ignored.
- Done: step-3 tests in the Testing Strategy green.

### Step 4 — `:fm1` package checks (depends on 3)
Ports: FM1Kit `Package/FwscPackage.swift`, `Package/FirmwareContainer.swift:81-191`, `Package/ApplicationArea.swift:20-158`
(with its second MIT notice at the top), official firmware constants (`FwscPackage.swift:195-239`),
`Package/SamplePackage.swift:45-149`. Test-only builder ported from FM1Kit `Tests/FM1RescueTests/SampleFlash.swift:65-115`
(`applicationArea(code:regions:)`, `record`, `sign` only — not the boot area, not FM1Rescue).
Files: `fm1/src/main/kotlin/dev/drewr/fm1/pkg/{FwscPackage,FirmwareContainer,Crc16,Decipher,OfficialFirmware,ApplicationArea}.kt`,
`fm1/src/main/kotlin/dev/drewr/fm1/testing/SamplePackage.kt` (in **main**, mirroring FM1Kit's `@_spi(Testing)`, so `:model`
and `:app` tests can build packages — no `java-test-fixtures`), `fm1/src/test/kotlin/.../SampleApplicationArea.kt`.
- `sha256` is of the **file**; the logical image drops the 20 marker bytes; `applicationRange` = flash offset + 0x4000 ..< end.
- `refusesV15OverOtherBootloaders` (and `usesFeluccaUpdateCode`) ported for the Before-you-start start-up question (Decision:
  ported as a safety check).
- Done: step-4 tests green.

### Step 5 — `:fm1` transport contracts, port matching, simulated FM-1 (depends on 4)
Ports: FM1Kit `Transport/MIDITransport.swift:5-78` (shapes), `Updater/FM1Updater.swift:23-27,48-51` (probe fragments),
`Transport/SimulatedFM1.swift`, `SimulatedFM1+Transport.swift`, `SimulatedFM1+Commands.swift:7-27` (capability/arm).
Not ported: `SimulatedFM1+Library.swift`, voices, project slots, workspace, sample bank.
Files: `fm1/src/main/kotlin/dev/drewr/fm1/transport/{MidiTransport,MidiConnection,PortDescriptor,PortMatching,SimulatedFm1}.kt`;
test double `fm1/src/test/kotlin/.../ScriptedTransport.kt` (port of FM1Kit `Tests/FM1KitTests/PackageCheckTests.swift:120-170`).
- `PortDescriptor(id, names: List<String>, serialNumber, usbVendorId, usbProductId)` — `names` holds every string Android
  may expose (device name/product/manufacturer, port names); which one Android uses for the FM-1 is UNCHECKED.
  `isProbeable` = any name, lower-cased, contains one of the ten FM1Kit fragments, **or** VID:PID is `4c4a:c755` / `4d4a:4155`.
- `SimulatedFm1(options, scope: CoroutineScope)`: every behaviour in research §2.11 with injectable timings; one port named
  `FM-1 Midi (simulated)`, generation counter kills connections on reboot; loader `ota-FM-1_9` or `ota-FM1C_<prefix>`;
  `prepare(package)`; `currentIdentity`. `exchange` compares the host's response **byte-for-byte** (`contentEquals`) and stops
  on mismatch — this is what makes a green install prove the encoders.
- `MidiTransport.changes(): Flow<Unit>`. `MidiConnection.isLost`, `send`, `suspend receive(timeout)`, `drain`, `close`.
- Done: step-5 tests green.

### Step 6 — `:fm1` updater (depends on 5)
Ports: FM1Kit `Updater/UpdaterTiming.swift:9-39`, `UpdateEvent.swift:2-25`, `WriteProgress.swift:15-110`,
`InstallCheck.swift:2-89`, `FM1UpdateError.swift:7-253` (in-scope cases **with their description text :96-253**; the deferred
cases listed in research §2.10 are omitted), `FM1Updater.swift:77-384`, `FM1Updater+Checks.swift:15-98`,
`FM1Updater+Refusals.swift:6-17`. **Not ported:** `FM1Advice` (Decision), FM1Kit's `InstallProgress` (the app's is ported in
step 8 — different shares, multi-step), `FM1DeviceSummary`, voices, backup, bootloader installer.
Files: `fm1/src/main/kotlin/dev/drewr/fm1/updater/{UpdaterTiming,UpdateEvent,WriteProgress,InstallCheck,Fm1UpdateError,Fm1Updater,Fm1UpdaterChecks}.kt`.
- `Fm1UpdateError` is a sealed class whose `message` is FM1Kit's description text — FlashingTests assert
  "already reports FM-1_900" and "confirm the switch" (`FM1UpdateError.swift:105,111`). Error semantics, named:
  `InstallKind.REINSTALL` means **already installed → refuse with `AlreadyInstalled`**; `SAME_IDENTITY` is the one that
  reinstalls, and only with `confirmsSameIdentity`.
- `Fm1Updater(transport, timing, scope)`; calls serialised with a `Mutex`. Idle limits without a clock: `serve` wraps
  "wait for the next **read request**" in `withTimeoutOrNull(idleLimit)` around a loop of `receive(1 s)` that skips
  non-requests and checks `isLost` on each empty receive — so other messages don't reset idle (FM1Kit `:323-372`).
- `resume(package)` accepts any loader for the model and does **not** check the armed prefix (FM1Kit `:238-247`); picking the
  package is `FirmwareLibrary.packagesThatCanFinish` (step 7).
- `WriteProgress`: `java.util.BitSet` for written/checked over the application area (~0x8F000 bytes), not `Set<Int>`.
- Cancellation is cooperative; a `CancellationException` is always rethrown, never mapped to an `Fm1UpdateError`.
- Done: step-6 tests green.

### Step 7 — `:model` data layer (depends on 4; parallel with 5–6)
Ports: `../FM-SWITCH/App/Catalogue/FirmwareCatalogue.swift:10-177` (`CatalogueEntry`, `Listing` as an Int bitmask,
`requiresOfficialFirmware` = `loader == "mvave"` — meaning "install the official firmware first", not the update loader),
`CatalogueBuilder.swift:4-27` (`MergedCatalogue` only, `CURRENT_FORMAT = 1`; the builder is **not** ported),
`App/Model/PublishedList.swift:5-43`, `CatalogueStore.swift:41-59,151-217,257-269` **minus** the on-device rebuild fallback
`:157-164` and minus `checkSource`/screenshots/`chosenBuilds`/`chosenTracks`, `FirmwareLibrary.swift` (+`+Failure.swift:4-37`)
minus iCloud/`move(to:)`/`downloadOfficialFromMVAVE`, `FlashHistory.swift:8-48`, `AppSettings.swift` (in-scope keys of research
§3.6), `StartUp.swift:5-102`, `FirmwareFilter.swift:5-131`, `AddedFirmwares.swift:3-38`, `HardwareTesting.swift:13-19`,
`EntryDescriptions.swift`, `OtherRelease.swift` only as far as decoding needs it (the release picker is out).
Files under `model/src/main/kotlin/dev/drewr/fmswitch/model/{catalogue,library,settings,history}/`.
- JSON with kotlinx.serialization, `ignoreUnknownKeys = true`, ISO-8601 dates (`java.time.Instant`) in **both** the published
  list and the cache (no Foundation-epoch format).
- `PublishedList(fetch: suspend (String) -> ByteArray, waits = [2 s, 4 s, 8 s])`; `BadStatus(code)` exception;
  status < 500 → `Unusable` at once; unreadable / `formatVersion > 1` / empty → `Unusable`; network failure → wait and retry,
  then `Unreachable`; `CancellationException` is rethrown, never a failure.
- `CatalogueStore(directory, list, ioDispatcher, now: () -> Instant)`: cache `catalogue.json`, dedupe by id on load, sort
  official first then name; on failure keep the cached list; message text from `CatalogueStore.swift:176-181` (> 1 day old vs not).
- `FirmwareLibrary(directory, bundledOfficial: () -> InputStream, ioDispatcher)`: production reads the asset, tests read the
  file at system property `fm1.v15`. Bundled V15 is never copied into `directory`, can't be removed, survives `clear()`.
  `StoredFirmware.id` = file SHA-256; `version` is the literal "Official" for V15 (finding 8). `packagesThatCanFinish(loader)`
  = FirmwareLibrary.swift:150-169 **with all three clauses** (armed prefix; `ota-`+product; stock loader `ota-FM-1_015` +
  loader-part SHA-256). Download checks: size, SHA-256, product, `notOfficial`, removes older releases (`:186-216`).
- `AppSettings`: interface + `InMemoryAppSettings` (tests); the DataStore implementation is in `:app` (step 9). Keys are new
  (no iOS compatibility). Out: look/Retro keys, update notices, tip nudges, chosen builds/tracks.
- Done: step-7 tests green.

### Step 8 — `:model` device and install state (depends on 6, 7)
Ports: `../FM-SWITCH/App/Model/DeviceController.swift:15-260,354-376` and `DeviceController+States.swift:5-120`,
`InstallReadiness.swift:16-124`, `InstallProgress.swift:7-96` (the **app's** type: shares 0–0.10/0.12/0.85/0.97/1.0, spread
across steps, `Tracker` never goes backwards and freezes on failure), `DeviceSummary.swift:8-77` (the **app's** plain-words
summary, not `FM1DeviceSummary`), the Before-you-start enabling rule from `BeforeYouStartView.swift:178-194` as a pure
`BeforeYouStartState`.
Files: `model/src/main/kotlin/dev/drewr/fmswitch/model/device/{DeviceController,FlashJob,FlashPhase,InstallReadiness,InstallProgress,DeviceSummary,BeforeYouStartState}.kt`.
- `DeviceController(library, history, settings, scope, transportFactory)`; `StateFlow`s for identity, ports, isScanning,
  job, log, transportError. `configure(kind)`, `scan()` (3 one-second retries), `plan(target, requiresOfficial,
  alwaysViaOfficial)`, `check(item)`, `flash(steps, confirmsSwitchingFirmware, confirmsSameIdentity)`, `resume()`,
  `dismissFlash()`. Each step: `identify()`; **if any loader answers → `resume`, else `install`** (`DeviceController.swift:208-215`).
  Cable pull for the simulator at **0.45**; m-bootloader option = `bootloaderVersion "1.2"` + `comparesBootArea`.
- Named semantics: `FlashJob.canResume(after)` is true for `Disconnected(WRITING)`, `Stalled(WRITING)` **and
  `LoaderNotFound`** (`+States.swift:74-81`); `isRefusal` = BootloaderMismatch | PackageRefused; `mightBeRefused` =
  `Stalled(STAGING)`. `InstallReadiness.isSwitching` is true for **any chain of more than one step** or a found
  `switchingFirmware`; only the **first** step is checked (`:115-123`); `isSameIdentity` = !isSwitching && found sameIdentity;
  `isAlreadyInstalled` = found reinstall && one step. `FlashPhase.verifying` ↔ `UpdateEvent.Checking` ↔ stage "Checking".
- Install survival doesn't depend on Android device callbacks: `Fm1Updater.waitFor` polls `ports()` + handshake every
  `scanInterval`, so whether the reboot shows up as remove + add (UNCHECKED) affects only how fast Home rescans, never the
  install. That is why the probe (step 10) stays off the critical path.
- Failure copy: `DeviceController.swift:354-369` refusal messages (they name "Install V15 first" / "Install MIDIMON 0.4 or
  LOOM-8 first"); otherwise the `Fm1UpdateError` message.
- Done: step-8 tests green (ported FlashingTests run under virtual time).

### Step 9 — `:app` platform adapters (depends on 5, 7)
Files: `app/src/main/kotlin/dev/drewr/fmswitch/midi/{AndroidMidiTransport,UnavailableTransport}.kt`,
`.../data/{HttpFetch,DataStoreAppSettings}.kt`.
- `AndroidMidiTransport(context)`: `MidiManager` from `Context.MIDI_SERVICE`; `UnavailableTransport` + plain error when
  `FEATURE_MIDI` is missing or the manager is null. `ports()` = `getDevices()` (not `getDevicesForTransport`, API 33),
  `TYPE_USB`, ≥ 1 input and ≥ 1 output port; `PortDescriptor.names` = `PROPERTY_NAME`, `PROPERTY_PRODUCT`,
  `PROPERTY_MANUFACTURER` and every `PortInfo.getName()`; VID:PID from `PROPERTY_USB_DEVICE`. Only `isProbeable` devices are
  ever opened. `open()` = `openDevice` (callback → `suspendCancellableCoroutine`), `openInputPort(0)` to send,
  `openOutputPort(0)` + `connect(receiver)` to receive (AOSP direction rule). `send` = `MidiInputPort.send(bytes, 0, n)`
  (AOSP `MidiReceiver.java:89-106` splits > 1015). Receiver `onSend` copies its bytes into a `SysexFramer` feeding the
  `SysexInbox`. `changes()` = `registerDeviceCallback` as a `callbackFlow`. `isLost` = `onDeviceRemoved` for this device id
  **or** the id no longer in `getDevices()` (both, because which fires is UNCHECKED). Every device callback is also logged
  to Logcat tag `FM1Midi` and appended to `DeviceController`'s log, so a real install's "Show the details" records the reboot.
- `HttpFetch`: `HttpURLConnection` inside `runInterruptible(ioDispatcher) { … }` (a blocking read only stops on interrupt),
  connect/read timeouts 15 s, throws `BadStatus(code)` for non-2xx — no new HTTP dependency.
- `DataStoreAppSettings`: `AppSettings` over DataStore Preferences.
- Done: `scripts/check.sh` green; behaviour against a real FM-1 is the UAT's (see Testing Strategy, Blocker record B1).

### Step 10 — Hardware probe screen (debug builds only; depends on 1, 3; off the critical path)
Files: `app/src/debug/kotlin/dev/drewr/fmswitch/probe/{MidiProbeRecorder,ProbeScreen}.kt`,
`app/src/debug/AndroidManifest.xml` (a second launcher activity "FM-1 probe"). It records from launch through a
source-set hook, not a second `Application`: the one `FmSwitchApplication` (step 11) calls `ProbeHooks.start(context)`;
`app/src/debug/.../ProbeHooks.kt` starts `MidiProbeRecorder`, `app/src/release/.../ProbeHooks.kt` is a no-op. Until step 11
exists, step 10 adds a minimal `FmSwitchApplication` that only calls the hook (step 11 extends it).
- Records, with monotonic timestamps: every `MidiDeviceInfo` at launch and on each add/remove — id, type, every
  `PROPERTY_*` value incl. USB VID:PID, port counts and names; for each device matching **either** a fragment or the VID:PID,
  a button "Send handshake" that opens it, sends `Fm1Message.HANDSHAKE_QUERY` once, and logs the size of **every** raw
  `onSend` chunk (fragmentation, premise P4(d)), the framed messages, and the parsed `Fm1Identity`; whether any permission
  dialog appeared is noted by the human. It never sends UPGRADE and never sends to a non-matching device.
- Output streams: on-screen scrolling log **and** Logcat tag `FM1Probe` **and** a "Copy log" button (ClipboardManager).
  On-screen + copy are primary: the phone's one USB port holds the FM-1, so `adb logcat` over USB isn't available during a
  test (wireless debugging is the only Logcat route).
- Done: builds in debug only (`assembleRelease` has no probe classes); the human run is UAT U1.

### Step 11 — App wiring and foreground service (depends on 8, 9)
Files: `app/src/main/kotlin/dev/drewr/fmswitch/{FmSwitchApplication,AppModel}.kt`, `.../service/FlashService.kt`, manifest.
- `AppModel` created once in `Application` (manual DI): settings, catalogue, library, history, device controller with an
  application `CoroutineScope` (`SupervisorJob() + Dispatchers.Main.immediate`); `requestedPage` as `StateFlow<RequestedPage?>`.
- `FlashService`: declared `<service android:name=".service.FlashService" android:exported="false"
  android:foregroundServiceType="connectedDevice"/>` (API 34+ rejects a `startForeground` type the manifest doesn't declare);
  started when a job starts, stopped when it ends; `startForeground` with type `connectedDevice`
  (Decision), notification "Installing <name>… keep it plugged in" on its own channel, partial `WakeLock` with a timeout of
  20 min released on stop. Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`,
  `POST_NOTIFICATIONS`, and **`CHANGE_NETWORK_STATE`** — the API 34+ runtime prerequisite for `connectedDevice` that needs no
  prompt (developer.android.com/develop/background-work/services/fgs/service-types, read 2026-10-30; the other routes are
  Bluetooth/UWB runtime grants or `UsbManager.requestPermission()`, which would interact with the UNCHECKED USB-permission
  question). Wrap `startForeground` in `try/catch` for `SecurityException`/`ForegroundServiceStartNotAllowedException`:
  on failure, log a line to the job log and carry on — loader resume is the safety net.
  VERIFY-AT-EXECUTION: whether to request `POST_NOTIFICATIONS` at the first install on API 33+ (the service runs without it;
  only the notification is hidden).
- Done: check script green; FGS behaviour on a phone is UAT U4 (Blocker record B2).

### Step 12 — UI foundations (depends on 1; parallel with 3–11)
Ports: `../FM-SWITCH/App/Standard/Theme/{Palette,Motion}.swift`, `REDESIGN.md:52-97,175-186`,
`App/Standard/FM1Drawing.swift`, `StandardRootView.swift:21-160` (phone layout only), `Home/WelcomeNoticeView.swift`,
`App/Views/SafetyNoticeView.swift:4-67` (Android variant of the iOS-only lines `:20-27,42-51`).
Files: `app/src/main/kotlin/dev/drewr/fmswitch/ui/{theme,components,navigation}/…`, `ui/FM1Drawing.kt`, `ui/welcome/…`,
one placeholder composable file per route (`ui/home/HomeScreen.kt`, `ui/firmwares/{FirmwareListScreen,FirmwarePageScreen,MyFirmwaresScreen}.kt`,
`ui/settings/SettingsScreen.kt`, `ui/install/{BeforeYouStartSheet,InstallingOverlay,OutcomeScreens}.kt`) so steps 13–15
replace files without touching the nav graph. Palette as plain ARGB `Long`s in a Kotlin object so contrast is JVM-testable.
- Material 3 with custom colours, no dynamic colour; appearance System/Light/Dark. Install overlay blocks back
  (`BackHandler`) while a job runs. No Retro, no 5-tap easter egg, no sidebar layout.
- Done: navigation reaches every placeholder; contrast tests green.

### Step 13 — Home, Settings, About (depends on 11, 12)
Ports: `Home/HomeView.swift:84-201` (drawing, status pill, Change firmware, My firmwares — "N saved" only, **no starred
count** (Starred is out), finish card "Finish installing <first package that can finish>", Connect your FM-1 card with
transport error + Try again); **dropped:** Save my FM-1, Factory sounds, Help card (Decisions). `Settings/StandardSettingsView.swift:31-195`:
Appearance; firmware list toggles (test versions, without a download, always official first, start-up picker; **no**
update notifications); Connection: FM-1 over USB / Simulated FM-1 + the six simulated toggles + Reset (`:176-187`); Safety and
recovery; About with FM1Kit, M-Flasher and V15 notices from `assets/licences` and the V15 licence. The firmwares/backups
location row is dropped (app storage). In debug builds, a "USB MIDI probe" row opens step 10's screen.
- Done: check green; simulated walkthrough U0 items for Home/Settings.

### Step 14 — Firmwares list, firmware page, My firmwares (depends on 11, 12)
Ports: `Firmwares/FirmwareListView.swift` (search, scope chips, Name/Updated order, summary line, "+ Add a firmware file" via
SAF `ACTION_OPEN_DOCUMENT` `*/*` then parse), `FirmwarePageView.swift:109-288` (title band, author, description, untested /
from-file warning, Install / Save to this phone for later / Remove, links in Custom Tabs, Details disclosure) **without** the
star button, newer-release note, release choice, build choice and screenshots (Decisions). An entry with
`needsBuildChoice` and no saved copy keeps Install and Save disabled, as the source does (`:203,212`), with a one-line
plain note. `MyFirmwaresView.swift` "Saved on this phone" only (no Starred). Badges: port `App/Model/FirmwareBadgeDesign.swift`
(pure, into `:model`) and `Firmwares/FirmwareBadge.swift`.
- Done: check green; U0 list/page items.

### Step 15 — Before you start, Installing, Done, Problem (depends on 8, 11, 12)
Ports: `Firmwares/BeforeYouStartView.swift:63-207` (plan header, missing-official and untested notices, start-up question
`StartUpViews.swift` + `StartUp.swift:15-40,77-101`, status notices `:106-144`, checklist `:146-176`, button `:178-207`);
`Install/InstallingView.swift:52-92`; `InstallOutcomeViews.swift:5-158`.
- The confirmations are the **Standard** ones, not Retro's "SWITCH FIRMWARE?"/"REINSTALL OR UPDATE?": the "I've saved anything
  I want to keep" tick when `isSwitching` (`:157-162`), and the same-identity **info notice** (`:129-137`), with
  `flash(steps, confirmsSwitchingFirmware = isSwitching && hasSaved, confirmsSameIdentity = isSameIdentity)` (`:186-190`).
- The Mac warning card (`FeluccaUpdateCode.Warning`) keeps its facts — going back to V15 after such a firmware needs the
  Mac app's Backup & rescue — in Android words; its "help" link and the Problem screen's "What can I do?" are **dropped**
  (Help is out). "Install MIDIMON", "Carry on installing", "Show the details", "Copy the details" stay.
- Done (also on Done screen): Factory sounds offer hidden. Installing screen sets `FLAG_KEEP_SCREEN_ON`.
- Done: check green; U0 install flows on the simulated FM-1.

### Step 16 — Dropped: no CI
The user declined CI (see Decisions). `scripts/check.sh` run locally is the gate; nothing creates `.github/`.

### Step 17 — Docs: AGENTS.md and architecture (depends on all)
- `AGENTS.md`: replace "there is no Gradle project…" (`:11-14`) with the real commands — build `./gradlew :app:assembleDebug`,
  lint `./gradlew spotlessCheck :app:lintDebug` (format: `spotlessApply`), test `./gradlew :fm1:test :model:test
  :app:testDebugUnitTest`, single test `./gradlew :fm1:test --tests 'dev.drewr.fm1.protocol.SevenBitTest'`, the check script
  `scripts/check.sh [lint|build|test|device]`, and the JDK 21 / `ANDROID_HOME` notes. Correct `:50-52`: on the Standard look the
  switch confirmation is the "I've saved anything I want to keep" tick (`../FM-SWITCH/App/Standard/Firmwares/BeforeYouStartView.swift:157-162`)
  and the same identity is an info notice (`:129-137`) whose install passes `confirmsSameIdentity`; "SWITCH FIRMWARE?" /
  "REINSTALL OR UPDATE?" are Retro's (`../FM-SWITCH/App/Views/FlashConfirmView.swift:33`). Correct `:42-43`: list all ten probe
  fragments plus the VID:PID rule. Replace "FM1Kit … isn't checked out on this machine" with the clone command above.
- `doc/architecture.md`: three modules; mark the settled [proposal]s as decided (FGS `connectedDevice` + `CHANGE_NETWORK_STATE`,
  no on-device list building, `SamplePackage` in main, virtual-time rule), and record U1's findings once the human has run it.
- Done: docs match `scripts/check.sh` and the module tree.

**Parallel groups**
- After 1: {2}, {3 → 4 → 5 → 6}, {10 after 3}, {12}, {16}.
- After 4: {7} in parallel with {5 → 6}.
- After 5 + 7: {9}. After 6 + 7: {8}. After 8 + 9: {11}.
- After 11 + 12 (and 8): {13}, {14}, {15} in parallel (disjoint files: own screen files, own `strings_<area>.xml`;
  only step 14 adds `FirmwareBadgeDesign` to `:model`).
- Last: {17}.

**Critical path:** 1 → 3 → 4 → 5 → 6 → 8 → 11 → 15 → 17 (then the hardware UAT).

## Testing Strategy

Every case below is absent at HEAD — the repo at `aa66529` has no Gradle project and no test file (`ls -A` →
`.jdi/ doc/ plans/ AGENTS.md CLAUDE.md`, 2026-10-30). The field is repeated per case as required. Each case is its own
test method (fail-fast assertions: one mutation reddens one method). Byte vectors marked **(V)** are in the Appendix,
verbatim. Mutation targets are the Kotlin definition sites this plan creates.

### `:fm1` — step 3 (`SevenBitTest`, `MessageTest`, `RecordedDeviceTest`, `SysexFramerTest`, `SysexInboxTest`)
- `packsLeastSignificantBitFirst` — `pack([0xFF]) == [0x7F,0x01]` and `pack([00,59,30]) == [00,32,41,01]`. **at HEAD:** absent.
  **reddened by:** MSB-first or "MSB byte + 7" packing in `SevenBit.pack`. **reaches the guard:** calls `pack` directly; no
  early return; vectors are FM1Kit `Tests/FM1KitTests/ProtocolTests.swift:7-8` and re-derived by the appendix script.
- `roundTripsEveryLength` — lengths 0…40, `bytes[i] = (i*37+11) & 0xFF`, unpack(pack) prefix equals input, all < 0x80.
  **at HEAD:** absent. **reddened by:** dropping the trailing partial septet in `pack` (`if bits > 0` branch). **reaches the guard:**
   lengths not multiple of 7 hit the branch; round trip loses the last byte.
- `handshakeQueryUnpacksToTheIdentityHeader` — unpack of the query's data bytes starts `00 59 11`. **at HEAD:** absent.
  **reddened by:** wrong constant in `Fm1Message.HANDSHAKE_QUERY` or `IDENTITY_COMMAND`. **reaches the guard:** pure constant check.
- `sendsTheDocumentedCommands` — query and upgrade bytes equal FM1Kit `ProtocolTests.swift:119-120`. **at HEAD:** absent.
  **reddened by:** `UPGRADE_COMMAND` ending `7F 7F F7` instead of `7F F7` in `Fm1Message`. **reaches the guard:** literal comparison.
- `readRequestEncodesTheDocumentedBytes` — `ReadRequest(flashType=1, address=0x00012340, length=481).encoded()` equals **(V1)**.
  **at HEAD:** absent. **reddened by:** big-endian address or length in `ReadRequest.body`, or `bodyLength` not `data+8`.
  **reaches the guard:** a round trip can't see a symmetric layout error; this golden vector comes from an independent
  implementation of FM1Kit's layout (appendix script), so it does.
- `responseEncodesTheDocumentedBytes` — `ReadRequest(0, 0x40, 4).response([1,2,3,4])` equals **(V2)**. **at HEAD:** absent.
  **reddened by:** checksum over the whole body instead of `body[6..]` in `ReadRequest.body`. **reaches the guard:** golden vector.
- `finishResponseIsSuccessAndANul` — `ReadRequest(0, 0xF0000000, 8).finishResponse()` equals **(V3)**. **at HEAD:** absent.
  **reddened by:** dropping the trailing `0` from `Fm1Message.FINISH_PAYLOAD`. **reaches the guard:** golden vector.
- `readRequestRoundTrips`, `readRequestWithBadChecksumIsIgnored`, `responseDeclaresTheFullLength` — FM1Kit
  `ProtocolTests.swift:27-49`. **at HEAD:** absent. **reddened by:** (respectively) dropping `flashType` in `ReadRequest.parse`;
  removing the checksum check in `ReadRequest.parse`; `parseResponse` accepting `bodyLength != length + 8`. **reaches the guard:**
   each feeds the exact message to the parser; the bad-checksum case flips `body[14]` after a valid encode.
- `readsARealDevicesHandshakeReply` — recorded 41-byte reply → `FM-1_009`, and re-encoding equals it. **at HEAD:** absent.
  **reddened by:** checksum as plain sum (no `~`) in `Fm1Message.checksum`. **reaches the guard:** the recorded bytes come
  from a real device; the appendix script confirms the checksum `~sum(body[6..33]) & 0xFF` matches them.
- `rejectsAReplyWithABadChecksum` — FM1Kit `:112-116`. **at HEAD:** absent. **reddened by:** removing the identity checksum
  check in `Fm1Identity.fromHandshakeReply`. **reaches the guard:** positive counterpart above proves the parser accepts the
  untouched reply, so the nil here is the checksum, not a framing failure.
- `identityRoundTripsAndRecognisesTheLoader` — `ota-FM-1_009` is loader, hardwareModel `FM-1`, version 9; `FM-1_91035`; `nonsense` null.
  **at HEAD:** absent. **reddened by:** `isLoader` testing case-sensitive `ota-`. **reaches the guard:** direct parse.
- `splitsAtTheLastUnderscore` — `FM1_COMMUNITY_5` parses model `FM1_COMMUNITY`; `FM1_OFFICIAL_OPEN` does not parse.
  **at HEAD:** absent. **reddened by:** `indexOf('_')` instead of `lastIndexOf` in `Fm1Identity.parse`. **reaches the guard:** a
  first-underscore split yields model `FM1` / digits `COMMUNITY_5` → null, so the positive assertion fails.
- `rejectsUnreadableIdentities` — each of `_5, FM-1_, FM-1_12a, FM-1, "", FM-1_１２ (full-width), FM-1_-5, FM-1_+5, FM 1_5,
  FM-1_5\n` is null (FM1Kit `EdgeCaseTests.swift:35`). **at HEAD:** absent. **reddened by:** `String.toIntOrNull()` for the
  digits in `Fm1Identity.parse` (accepts `-5`, `+5`). **reaches the guard:** each input reaches the digit check after a
  non-empty model; positive `FM-1_5` asserted first in the same file proves the parser accepts the plain form.
- `equalityIsOnTheText` — `Fm1Identity.parse("FM-1_015") != Fm1Identity(model="FM-1", version=15)`. **at HEAD:** absent.
  **reddened by:** data-class equality on (model, version) in `Fm1Identity`. **reaches the guard:** both have model FM-1,
  version 15; only text differs.
- `openFirmwareIsFm1KitsPredicate` — `FM-1_8000012` and `FM1_COMMUNITY_3` open; `FM-1_8010300` (DUB-1) **not** open.
  **at HEAD:** absent. **reddened by:** porting the app's regex `FM-1_8\d{6}` into `Fm1Identity.isOpenFirmware`. **reaches the guard:**
   `8010300` matches the regex but not 8,000,000…8,000,999.
- `armedLoaderParses` — `ota-FM1C_DB1642B2B6FA5C2C` → armed prefix `db1642b2b6fa5c2c`, hardwareModel `FM-1`; 15 hex → null.
  **at HEAD:** absent. **reddened by:** not lower-casing in `Fm1Identity.parse`. **reaches the guard:** upper-case input.
- `capabilityLayoutAndCommandsRoundTrip` — FM1Kit `OpenFirmwareTests` capability/command cases (47-byte reply; `[1,family]+u32le+32`).
  **at HEAD:** absent. **reddened by:** `maxRead` read big-endian in `Fm1Capability.parse`. **reaches the guard:** reply built
  by a test helper from the documented field order, parsed by the subject.
- `framerReassemblesAcrossChunks` — a 605-byte response fed in chunks of 1, 7 and 64 bytes yields exactly one message equal
  to the input. **at HEAD:** absent. **reddened by:** emitting per chunk in `SysexFramer.feed`. **reaches the guard:** chunk
  sizes split inside the message.
- `framerSkipsRealTimeBytes` — `F8`/`FE` inside a message are dropped, message intact. **at HEAD:** absent. **reddened by:**
  treating `F8–FF` as aborting status bytes in `SysexFramer.feed`. **reaches the guard:** bytes injected mid-message.
- `framerAbortsOnOtherStatus` — a `90` inside a message drops it; the next well-formed message is still emitted (positive).
  **at HEAD:** absent. **reddened by:** appending status bytes as data in `SysexFramer.feed`. **reaches the guard:** positive
  second message proves the stimulus was processed.
- `newStartDiscardsUnfinished`, `dataWithoutStartIsIgnored`, `dropsOver64KiB` — FM1Kit `EdgeCaseTests.swift:89-127` rules.
  **at HEAD:** absent. **reddened by:** (resp.) concatenating across `F0` in `SysexFramer`; emitting leading data; removing
  the 65,536 cap. **reaches the guard:** each ends with a valid message that must be the only one emitted.
- `inboxOrderTimeoutWakeCloseDrainCancelCapacity` — eight methods porting FM1Kit `EdgeCaseTests.swift:138-171,293-320` under
  `runTest`: order; `next(1 s)` null after virtual 1 s; a waiter wakes on arrival; `close` wakes a waiting `next` with null and
  later sends are ignored; `drain` empties; cancelling ends the wait; a wait cancelled before it starts ends at once; 300 sends
  keep the newest 256. **at HEAD:** absent. **reddened by:** (capacity case)
  `BufferOverflow.DROP_LATEST` in `SysexInbox`; (close case) `close` not closing the channel; each method one mutation.
  **reaches the guard:** virtual time — `currentTime` asserted == 1000 in the timeout case proves the wait elapsed.

### `:fm1` — step 4 (`PackageCheckTest`, `OfficialFirmwareTest`, `ApplicationAreaTest`)
- `decodesProductAndStripsMarkers` — FM1Kit `ProtocolTests.swift:85-92`. **at HEAD:** absent. **reddened by:** marker char
  `marker - block` (missing `- 1`) in `FwscPackage.parse`. **reaches the guard:** sample file built with `+ i + 1`.
- `rejectsOtherFiles` — tooShort for 3 bytes; a failure for 4,000×`0x41`. **at HEAD:** absent. **reddened by:** removing the
  `> 20*0x30` length check. **reaches the guard:** direct parse.
- `crc16IsXModem` — `crc16("123456789") == 0x31C3`. **at HEAD:** absent. **reddened by:** init `0xFFFF` in `Crc16`.
  **reaches the guard:** the standard XModem check value; init 0xFFFF gives 0x29B1.
- PackageCheck — one method each, ported from FM1Kit `Tests/FM1KitTests/PackageCheckTests.swift`; every one starts from a
  `SamplePackage` that is valid by construction (proved by `readsTheFirmwareImageAndLoader`) and corrupts one field, so the
  failure is the guard under test. Every case below is absent at HEAD.
  - `decipheringTwiceGivesBackTheOriginal` — **at HEAD:** absent. **reddened by:** stepping the key from the *output* byte in `Decipher` (breaks
    symmetry). **reaches the guard:** random block deciphered twice.
  - `readsTheFirmwareImageAndLoader` — **at HEAD:** absent. **reddened by:** reading entry `offset` at byte 12 instead of 8 in `FirmwareContainer.parse`.
    **reaches the guard:** asserts the flash and loader byte ranges of the sample.
  - `aFirmwareImageNoBiggerThanTheBootAreaIsAllApplication` — **at HEAD:** absent. **reddened by:** dropping the `≤ 0x4000` branch of
    `FirmwareContainer.applicationRange`. **reaches the guard:** sample with a 16 KiB flash.
  - `refusesADamagedHeader` — **at HEAD:** absent. **reddened by:** skipping the header CRC compare. **reaches the guard:** one header byte flipped.
  - `refusesADamagedTable` — **at HEAD:** absent. **reddened by:** skipping the table CRC (`u16@2`). **reaches the guard:** one table byte flipped.
  - `refusesAnotherChip` — **at HEAD:** absent. **reddened by:** removing the `AC791N` check. **reaches the guard:** header re-signed with chip `AC790N`.
  - `refusesADamagedPart` — **at HEAD:** absent. **reddened by:** skipping the per-part CRC. **reaches the guard:** one loader byte flipped.
  - `doesNotCheckEncipheredParts` — **at HEAD:** absent. **reddened by:** applying the CRC check to enciphered parts. **reaches the guard:** a USR
    part marked enciphered with a deliberately wrong checksum is accepted.
  - `refusesAPackageWithoutALoader` — **at HEAD:** absent. **reddened by:** dropping the "exactly one loader" rule. **reaches the guard:** sample
    with a flash part only.
  - `refusesAFirmwareImageThatIsNotWholeSectors` — **at HEAD:** absent. **reddened by:** dropping `size % 4096 == 0`. **reaches the guard:** flash 20,000 bytes.
  - `refusesOverlappingParts` — **at HEAD:** absent. **reddened by:** removing the overlap check. **reaches the guard:** two parts sharing bytes.
  - `acceptsPaddingPastTheEnd` — **at HEAD:** absent. **reddened by:** not clamping stored ranges to the image. **reaches the guard:** last part's
    `storedSize` runs past the end, `size` doesn't.
  - `refusesAPartPastTheEnd` — **at HEAD:** absent. **reddened by:** dropping `offset + size ≤ image.size`. **reaches the guard:** size past the end.
  - `refusesAFileTooLargeForAnFM1` — **at HEAD:** absent. **reddened by:** raising the 2,097,152 cap in `FwscPackage`. **reaches the guard:** 2,097,153-byte file.
  - `readsOutsideTheRegionLeaveProgressUnchanged` / `readsAcrossTheRegionCountOnlyTheirInsidePart` (step 6 code, kept with
    their source file) — **at HEAD:** absent. **reddened by:** not clamping the read to the region in `WriteProgress.record` (one method each:
    "outside" checks the repeated event, "across" checks the counted bytes). **reaches the guard:** reads placed outside / across
    the application range of the sample.
  - `aSampleFilePassesEveryCheck` (from OpenFirmwareTests) — **at HEAD:** absent. **reddened by:** a `SamplePackage` that signs the table before
    enciphering. **reaches the guard:** the builder's output goes through `FwscPackage.parse` and `refusesV15…`/arming paths.
- `bundledV15IsTheOfficialFirmware` — the file at system property `fm1.v15`: size 699,956, SHA-256 `db1642b2…edb8a`,
  `OfficialFirmware.V15.matches`, product `FM-1_015`, loader-part SHA-256 = `ef1f2db8…4541`. **at HEAD:** absent.
  **reddened by:** hashing the logical image instead of the file in `FwscPackage.sha256`. **reaches the guard:** the
  real file; image hash differs from `db1642b2…`. Also guards step 2's copy (a modified asset fails).
- `refusesV15WhenFeluccasUpdateCodeLacksTheFix` — code `"..FM-1_8010300..FELUCCA-LOADER-1.."` + 0x3000×`0x5A` through
  `SampleApplicationArea` → `refusesV15OverOtherBootloaders == true`. **at HEAD:** absent. **reddened by:** chip key
  `0x980F` without `^ (block >> 2)` in `ApplicationArea.bytes`. **reaches the guard:** the builder enciphers with the
  per-block key; a wrong key yields no `app.bin` → false. Port of FM1Rescue `DownloadModeTests.swift:155-161`.
- `takesV15WhenFixedOrHashed` — `…AC79BLID…` → false; code with the 32 V15 image-hash bytes → false; both
  `usesFeluccaUpdateCode == true` (positive). **at HEAD:** absent. **reddened by:** dropping the `AC79BLID` clause (first
  method) / the hash clause (second method) in `FwscPackage.refusesV15OverOtherBootloaders`. **reaches the guard:** the
  positive `usesFeluccaUpdateCode` proves `app.bin` was found and deciphered, so the false is the clause.
- `plainSampleDoesNotUseFeluccasCode` — default `SampleApplicationArea` code → both false. **at HEAD:** absent.
  **reddened by:** dropping the `contains(FELUCCA-LOADER-1)` clause in `FwscPackage.refusesV15OverOtherBootloaders` (so any
  readable `app.bin` refuses). **reaches the guard:** the builder writes a valid `app.bin` without the marker; this case does
  not exercise the decipher key — `refusesV15WhenFeluccasUpdateCodeLacksTheFix` does.

### `:fm1` — step 5 (`PortMatchingTest`, `SimulatedFm1Test`)
- `probesOnlyMatchingPorts` — names `Felucca Update`, `FM-1`, `USB-MIDI`, `M-VAVE SINCO` match; `Arturia KeyStep`,
  `IAC Bus` don't; a device named `USB Audio` with VID:PID `4c4a:c755` matches. **at HEAD:** absent. **reddened by:**
  case-sensitive `contains` in `PortMatching.isProbeable` (first method) / ignoring VID:PID (second). **reaches the guard:**
  pure function. No test asserts what Android actually names the FM-1 — that's UAT U1.
- `simulatorAnswersTheHandshake` — **at HEAD:** absent. **reddened by:** replying only while `isRebooting` is true in
  `SimulatedFm1.handle`. **reaches the guard:** a fresh sim is online; the reply must parse as `FM-1_900`.
- `connectionsDieOnReboot` — a connection opened before reboot reports `isLost`, the port list is empty for `rebootDuration`,
  then a new port id appears (positive). **at HEAD:** absent. **reddened by:** not bumping the generation in
  `SimulatedFm1.reboot`. **reaches the guard:** reboot triggered by a staging finish from a scripted host.
- `armedLoaderIsNamedAfterTheBuild` — after a valid arm + UPGRADE + staging, the loader reports `ota-FM1C_<16 hex>`.
  **at HEAD:** absent. **reddened by:** ignoring the armed build in `SimulatedFm1.loaderIdentity`. **reaches the guard:** open
  firmware options; the arm reply `[0]` is asserted first.

### `:fm1` — step 6 (`UpdaterTest`, `FinishTest`, `OpenFirmwareTest`, `EdgeCaseTest`, `WriteProgressTest`)
All use `runTest`, `backgroundScope` for the simulator, FM1Kit's `fastTiming`/`fastOptions`
(`Tests/FM1KitTests/UpdaterTests.swift:12-35`) or standard timings (virtual time makes either cheap).
- `identifiesTheDevice` → `FM-1_900`. **at HEAD:** absent. **reddened by:** `handshakeAttempts` loop sending zero times.
  **reaches the guard:** simulator answers only a sent query.
- `installsAndVerifiesTheIdentity` → `FM-1_916`, `device.currentIdentity == FM-1_916`. **at HEAD:** absent.
  **reddened by:** a `ByteArray` `==` (reference equality) in `SimulatedFm1.exchange`'s data compare — the simulator's exchange stops,
  the install ends `Stalled`. **reaches the guard:** confirmed options; a full staging + reboot + write + return.
- `resumesAfterTheCableIsPulledMidWrite` — `unplugDuringWriteAt = 0.5`, install throws, device in loader, `resume` → `FM-1_916`.
  **at HEAD:** absent. **reddened by:** `resume` searching for `!isLoader` in `Fm1Updater.resume`. **reaches the guard:**
  assertion that `currentIdentity.isLoader` before resume proves the stimulus.
- `finishesFromRecoveryMode` — install throws `DeviceNotFound`, resume succeeds. **at HEAD:** absent. **reddened by:**
  `install` accepting a loader in `findOnly`. **reaches the guard:** sim starts in loader.
- `refusesARequestBeyondTheImage` — `ScriptedTransport` scripts a 512-byte read at `image − 256` → `BadRequest(addr, 512)`.
  **at HEAD:** absent. **reddened by:** dropping `addr + length <= image.size` in `Fm1Updater.serve`. **reaches the guard:**
  `ScriptedTransport` delivers the scripted read after UPGRADE; FM1Kit `UpdaterTests.swift:84-97`.
- `answersARepeatedFinishBeforeClosing` — `ScriptedTransport` sends the write finish twice; two finish replies recorded.
  **at HEAD:** absent. **reddened by:** removing the 600 ms settle loop in `Fm1Updater.serve`. **reaches the guard:** the
  repeat arrives inside `finishSettle`; FM1Kit `PackageCheckTests.swift:179`.
- `finishOfTheOtherPhaseStops` / `finishWithLengthOtherThan8Stops` — `UnexpectedFinish(phase)`. **at HEAD:** absent.
  **reddened by:** dropping the address (first) / `length == 8` (second) check in `Fm1Updater.serve`. **reaches the guard:**
  ScriptedTransport script at FM1Kit `PackageCheckTests.swift:189-198`.
- `otherMessagesDontKeepAStalledPhaseWaiting` — ScriptedTransport sends identity replies every 500 ms forever after UPGRADE;
  install throws `Stalled(STAGING)` by virtual `stagingIdleLimit + 1 s` (the test wraps the install in a virtual
  `withTimeout(30 s)` so a regression fails fast instead of spinning); at least one identity was delivered (positive).
  **at HEAD:** absent. **reddened by:** restarting the `withTimeoutOrNull(idleLimit)` per received message in
  `Fm1Updater.serve`. **reaches the guard:** without the guard the outer `withTimeout` fires (port of FM1Kit
  `EdgeCaseTests.swift:384`); the delivered count proves the stimulus.
- `standardTimingsAddNoPauses` — `UpdaterTiming.STANDARD` matches research §2.8 table (the 13 in-scope values; voice/library waits are not ported). **at HEAD:** absent.
  **reddened by:** `finishSettle = 0` in `UpdaterTiming.STANDARD`. **reaches the guard:** literal comparison.
- `neverSendsToOtherGear` — a fake transport with an FM-1 port and an `Arturia KeyStep` port: after `identify()`, the FM-1
  port received exactly one handshake (positive) and the other port received **zero bytes and was never opened**. **at HEAD:**
  absent. **reddened by:** removing the `isProbeable` filter in `Fm1Updater.findAll`. **reaches the guard:** the positive
  count proves discovery ran.
- `twoFm1sAreRefused` (install and resume, two methods) → `MultipleDevices(2)`; `findAllListsBoth`. **at HEAD:** absent.
  **reddened by:** returning the first answerer in `Fm1Updater.findOnly`. **reaches the guard:** two simulators on one fake
  transport, both answer.
- `v15IsNotReinstalled` — sim running `FM-1_015`, package V15 sample product → throws `AlreadyInstalled`, nothing but the
  handshake sent. **at HEAD:** absent. **reddened by:** mapping `InstallKind.REINSTALL` to an install in `Fm1Updater.install`
  (the name-drift trap). **reaches the guard:** `check(package).kind == REINSTALL` asserted first.
- `switchingNeedsConfirmingAndSendsNothingUntilThen` / `sameIdentityNeedsConfirming` — ScriptedTransport records sends: only
  the handshake was sent; error `SwitchNotConfirmed` / `SameIdentityNotConfirmed`, message contains "confirm the switch" /
  "already reports". **at HEAD:** absent. **reddened by:** sending UPGRADE before the confirmation check (first) / omitting
  the description text in `Fm1UpdateError` (message assertion — separate method `errorTextIsFm1Kits`). **reaches the guard:**
  `check().kind` asserted first.
- `aDeviceIsCheckedWithoutChangingIt` — after `check()`, the transport recorded only handshake (+ capability for open
  firmware) sends and the identity is unchanged. **at HEAD:** absent. **reddened by:** `check` sending UPGRADE.
  **reaches the guard:** positive: one handshake recorded.
- `refusesAPackageForAnotherModel` → `ModelMismatch` / `rejectsPackagesForOtherModels` (package parse). **at HEAD:** absent.
  **reddened by:** skipping the model compare in `Fm1UpdaterChecks.check` (first) / the `FM-1` model rule in `FwscPackage`
  (second). **reaches the guard:** a sample whose product is `FM-2_5`, and a sim reporting `FM-2_1`.
- `resumeNeedsTheLoader` — firmware running, `resume` → `LoaderNotFound`. **at HEAD:** absent. **reddened by:** `resume`
  accepting a non-loader. **reaches the guard:** sim online in firmware; identify asserted first.
- `reportsEachStepInOrder` — events from a full simulated install equal connected, staging…, waitingForLoader, loaderFound,
  writing…, rebooting, finished. **at HEAD:** absent. **reddened by:** emitting `Rebooting` before the write finish in
  `Fm1Updater.write`. **reaches the guard:** positive `Finished` last.
- `ignoresRepliesThatAreNotHandshakes` / `otherMessagesAreNotReadRequests` / `responsesWithAWrongLengthOrChecksumAreRejected`
  / `storesAnIdentityAsItsText` / `hashesTheFileNotTheImage` / `recognisesOnlyOfficialReleases` (FM1Kit `EdgeCaseTests.swift:18-88,322`).
  **at HEAD:** absent. **reddened by:** (resp.) taking the first reply in `Fm1Updater.handshake`; `ReadRequest.parse` not
  checking the command byte; `parseResponse` not checking length; serializing identity as an object; hashing the image;
  `OfficialFirmware.matches` comparing size only. **reaches the guard:** each feeds the subject the exact message/file; one
  method each.
- `aCustomFirmwaresOwnIdentityNeedsConfirming` (FM1Kit `EdgeCaseTests.swift:348`). **at HEAD:** absent. **reddened by:**
  treating `SAME_IDENTITY` as `UPDATE` in `Fm1Updater.install`. **reaches the guard:** `check().kind == SAME_IDENTITY` first.
- Open firmware — FM1Kit `Tests/FM1KitTests/OpenFirmwareTests.swift`, one method each, sim with open-firmware options (it
  ignores UPGRADE until armed and checks the arm payload, `SimulatedFM1+Commands.swift:7-27`). Every case below is absent at HEAD.
  - `buildIDsReadHexInEitherCase` — **at HEAD:** absent. **reddened by:** case-sensitive hex parse in `BuildId`. **reaches the guard:** upper-case input.
  - `commandsRoundTrip` — **at HEAD:** absent. **reddened by:** checksum over header+payload in `Fm1Message.command`. **reaches the guard:** parse of the encode.
  - `capabilitiesRoundTripAndRejectOtherLayouts` — **at HEAD:** absent. **reddened by:** not checking `"FMC1"` in `Fm1Capability.parse`.
    **reaches the guard:** a reply with magic `"FMC2"`.
  - `recognisesOpenFirmwareIdentities` — **at HEAD:** absent. **reddened by:** the app's regex in `Fm1Identity.isOpenFirmware` (DUB-1 case).
    **reaches the guard:** `FM-1_8010300` in the inputs.
  - `readsAnArmedOpenFirmwareLoader` — **at HEAD:** absent. **reddened by:** accepting 15 hex digits. **reaches the guard:** both lengths given.
  - `armsOpenFirmwareAndChecksTheBuildAfterwards` — **at HEAD:** absent. **reddened by:** u32 image length big-endian in `Fm1Capability.armPayload`
    (sim refuses → `ArmRefused`). **reaches the guard:** green install requires the sim to accept the arm.
  - `armsWithThePackageHashWithoutABuildID` — **at HEAD:** absent. **reddened by:** sending zero build ID when none is known. **reaches the guard:**
    sim records the armed build; asserted equal to the file SHA-256.
  - `theRunningBuildIsAReinstall` — **at HEAD:** absent. **reddened by:** comparing build IDs by reference (`==` on arrays) in `InstallCheck`.
    **reaches the guard:** sim's `installedBuildID` set to the package's.
  - `leavingOpenFirmwareIsASwitch` — **at HEAD:** absent. **reddened by:** treating any package as `UPDATE` when capability exists. **reaches the guard:**
     a non-open package.
  - `aBusyDeviceIsNotArmed` — **at HEAD:** absent. **reddened by:** dropping `state == 0` → `DeviceBusy`. **reaches the guard:** sim `capabilityState = 1`.
  - `aRefusedArmingStopsBeforeStaging` — no read served. **at HEAD:** absent. **reddened by:** ignoring a non-`[0]` arm reply. **reaches the guard:**
     sim `armReply = [3]`; error code 3 asserted.
  - `openFirmwareThatDoesNotAnswerIsNotUpdated` — **at HEAD:** absent. **reddened by:** treating no capability reply as "not open".
    **reaches the guard:** sim open identity with capability disabled → `CapabilityUnavailable`.
  - `otherFirmwaresAreNotAskedAboutCapability` — **at HEAD:** absent. **reddened by:** always sending `0x31`. **reaches the guard:** recorded sends
    contain the handshake (positive) and no capability query.
  - `officialReleasesCarryTheirBuildIDs` — **at HEAD:** absent. **reddened by:** a wrong Open V12 build ID literal. **reaches the guard:** constants.
  - `switchingNeedsConfirmingAndSendsNothingUntilThen` — see step-6 bullet above (same method).
  - `aDeviceIsCheckedWithoutChangingIt` — see above (same method).
  - `refusesToInstallWithTwoFM1sConnected` / `refusesToResumeWithTwoLoadersWaiting` / `findAllReportsEveryDevice` — see
    `twoFm1sAreRefused` above (same methods).
  - `aSampleFilePassesEveryCheck` — listed under step 4.
- WriteProgress — FM1Kit `Tests/FM1KitTests/WriteProgressTests.swift`, a 64-block region, reads fed straight to
  `WriteProgress.record` (pure; no early returns except "outside the region"). Every case below is absent at HEAD.
  - `aForwardsPassRisesSteadilyToTheEnd` — **at HEAD:** absent. **reddened by:** computing the event before adding the read to `written` in
    `WriteProgress.record` (first fraction 0, not 1/64). **reaches the guard:** first event asserted `== 1/64`.
  - `aBackwardsPassRisesSteadilyRatherThanJumping` — **at HEAD:** absent. **reddened by:** progress from the furthest address. **reaches the guard:**
     first read is the last block → would show 100%.
  - `headerRereadsAtTheStartAreStillWriting` — **at HEAD:** absent. **reddened by:** counting *any* full reread (not requiring written ≥ 50%)
    toward checking. **reaches the guard:** 3 rereads of blocks 0–2 at < 50%.
  - `shortRereadsAtTheEndAreStillWriting` — **at HEAD:** absent. **reddened by:** counting short reads toward the checking streak. **reaches the guard:**
     10- and 16-byte reads after a full pass.
  - `aSecondPassIsReportedAsChecking` — **at HEAD:** absent. **reddened by:** requiring 4 rereads to start checking. **reaches the guard:** second
    forward pass; first checking fraction asserted.
  - `aRereadAtTheEndOfAPassIsStillWriting` — **at HEAD:** absent. **reddened by:** requiring 2 instead of 3 consecutive rereads. **reaches the guard:**
     two rereads then new ground.
  - `newGroundDuringCheckingGoesBackToWriting` — **at HEAD:** absent. **reddened by:** not clearing `checked` on new ground. **reaches the guard:**
    checking pass interrupted by an unread block.
  - `aLoaderThatSkipsPartOfTheImageEndsShortOfOne` — **at HEAD:** absent. **reddened by:** snapping to 1 at the finish. **reaches the guard:** 60
    of 64 blocks read.
  - `aShortFinalReadIsCounted` — **at HEAD:** absent. **reddened by:** counting only full 512-byte reads as written. **reaches the guard:** last
    read 100 bytes.
  - `aBackwardsLoaderReportsSteadyProgress` / `aCheckingLoaderReportsCheckingAfterWriting` — **at HEAD:** absent. **reddened by:** (resp.) using
    `Set<Int>` of block starts instead of byte coverage (`BitSet`) — overlapping unaligned reads double-count; checking
    fraction over the region instead of over `written`. **reaches the guard:** real-shaped read orders (V15 backwards then
    header; Felucca forwards ×2).

### `:model` — step 7 (`PublishedListTest`, `CatalogueStoreTest`, `LibraryTest`, `StartUpTest`, `FilterTest`, `FlashHistoryTest`)
- `decodesThePublishedShape` — a committed test resource `model/src/test/resources/firmwares-sample.json`, trimmed once from
  the live list at execution time (never fetched by the test) (`formatVersion 1`, `listing` Ints 1–7, `builds` on
  one entry, unknown key present) decodes; `listing == 7` has hub+editor+resourceHub. **at HEAD:** absent. **reddened by:**
  `ignoreUnknownKeys = false`. **reaches the guard:** fixture carries an unknown key.
- `triesAgainAfterTheNetworkFails` (2 IOExceptions → loaded, 3 fetches), `givesUpAfterItsLastWait` (→ Unreachable, 4 fetches),
  `doesNotTryAgainWhenTheListIsMissing` (404 → Unusable, 1 fetch), `aCancelledDownloadIsNotAFailure` (→ CancellationException,
  1 fetch) — `../FM-SWITCH/AppTests/PublishedListTests.swift:11-34`. **at HEAD:** absent. **reddened by:** (resp.) no retry;
  an extra wait; retrying on status < 500; `catch (e: Exception)` swallowing `CancellationException` in `PublishedList.load`.
  **reaches the guard:** fetch-count assertion is the positive stimulus count in each.
- `refusesANewerFormat` → Unusable. **at HEAD:** absent. **reddened by:** dropping `formatVersion <= 1` in `PublishedList.load`.
  **reaches the guard:** fixture with `formatVersion: 2` and one entry; fetch count 1 (positive).
- `refusesAnEmptyList` → Unusable. **at HEAD:** absent. **reddened by:** dropping the `entries.isNotEmpty()` check.
  **reaches the guard:** fixture with format 1 and no entries.
- `dropsARepeatedIdFromASavedList` (`CatalogueTests.swift:216`). **at HEAD:** absent. **reddened by:** dedupe removed in
  `CatalogueStore.load`. **reaches the guard:** cache file written with a duplicate id.
- `sortsOfficialFirst`. **at HEAD:** absent. **reddened by:** sorting by name only. **reaches the guard:** official entry named "M-VAVE…"
  sorted after "Amb…" by name.
- `keepsTheSavedListOnFailureAndSaysSoWhenOld` / `…WhenRecent` (two methods, `now` injected, copy `CatalogueStore.swift:176-181`).
  **at HEAD:** absent. **reddened by:** (resp.) comparing age in the wrong direction; clearing entries on failure. **reaches the guard:**
   a saved list 2 days / 1 hour old, then a failing fetch; entries asserted unchanged (positive).
- Library basics — `LibraryTests.swift:14-64` (iCloud wording dropped; `move(to:)` not ported), real V15 via `fm1.v15`.
  Every case below is absent at HEAD.
  - `comesWithTheOfficialFirmwareAndKeepsItWhenCleared` — **at HEAD:** absent. **reddened by:** `clear()` dropping the built-in item.
    **reaches the guard:** official present before clear (positive), then after.
  - `readsTheBuiltInCopyRatherThanSavingIt` — **at HEAD:** absent. **reddened by:** copying V15 into `directory` in `FirmwareLibrary.init`.
    **reaches the guard:** directory listing asserted empty while `items == [official]`.
  - `cannotRemoveTheBuiltInCopy` — **at HEAD:** absent. **reddened by:** `canRemove` true for official. **reaches the guard:** `remove(official)` then still present.
  - `savesPackagesUnderTheirNamesWithTheirDetailsBeside` — **at HEAD:** absent. **reddened by:** naming the file by sha. **reaches the guard:** an
    imported `FM-1_916` sample; `.details/<sha>.json` exists, removed with the item.
  - `reloadPicksUpFilesWrittenElsewhere` — **at HEAD:** absent. **reddened by:** `reload()` not re-reading `.details`. **reaches the guard:** a
    second library instance on the same directory saves the item.
- `picksThePackagesThatCanFinishALoadersUpdate` — `ota-FM-1_015` → [official]; `ota-FM-1_900` → []. **at HEAD:** absent.
  **reddened by:** making the clauses match on `hardwareModel` alone (then `ota-FM-1_900` returns V15). Removing either name
  clause alone leaves it green, because V15 matches `ota-`+product *and* the stock-loader SHA (verified at plan time, below).
  **reaches the guard:** real V15; this case does not isolate the stock-loader clause — the next case does.
- `anyPackageCarryingTheStockLoaderCanFinishIt` — a `SamplePackage` with product `FM-1_916` whose loader part is the **V15
  loader bytes** (extracted from the `fm1.v15` file's container) is imported; `packagesThatCanFinish(ota-FM-1_015)` contains it.
  **at HEAD:** absent. **reddened by:** removing the stock-loader SHA-256 clause in `FirmwareLibrary.packagesThatCanFinish`.
  **reaches the guard:** the item's product doesn't match `ota-FM-1_015`, so only that clause can include it; asserting its
  `loaderSha256 == OfficialFirmware.STOCK_LOADER_SHA256` first proves the fixture. Predicate evaluated at plan time
  (2026-10-30, appendix script `v15.py` over the bundled file): V15's `ota.bin` is part type 100 at image offset `0xA5F00`,
  19,969 bytes, **not enciphered**, CRC OK, SHA-256 `ef1f2db89b011fa53e71df363f0122de0ea896618c53fb53e8da676322004541` = the stock
  loader — so the fixture can be built from those bytes and is within the 1…32,767 loader size rule.
- `picksThePackageAnArmedLoaderIsWaitingFor` — `ota-FM1C_db1642b2b6fa5c2c` → [official]; `ota-FM1C_0123456789abcdef` → []
  (`LibraryTests.swift:85-93`). **at HEAD:** absent. **reddened by:** removing the armed-prefix branch of
  `FirmwareLibrary.packagesThatCanFinish` (falling through to the name clauses). **reaches the guard:** `ota-FM1C_…` never
  equals `ota-` + any product and isn't `ota-FM-1_015`, so only the armed branch can return V15.
- `armedLoaderMatchesAnImportBySha` — an imported `FM-1_916` sample is returned for `ota-FM1C_<first 16 hex of its sha256>`.
  **at HEAD:** absent. **reddened by:** using only `OfficialFirmware.known` build IDs (no sha fallback) in
  `FirmwareLibrary.buildIdOf`. **reaches the guard:** the sample isn't a known official release, so only the sha fallback matches.
- Download checks (`FirmwareLibrary.swift:186-216`, fake fetch returning sample bytes), one method each. Every case below is absent at HEAD.
  - `refusesAWrongHash` → `HashMismatch` — **at HEAD:** absent. **reddened by:** skipping the sha check when size matches. **reaches the guard:**
    entry with the right size and a different sha.
  - `refusesAWrongProduct` → `ProductMismatch` — **at HEAD:** absent. **reddened by:** skipping the product compare. **reaches the guard:** entry
    lists `FM-1_917`, file is `FM-1_916`, size/sha match.
  - `refusesAnOfficialEntryThatIsntV15` → `NotOfficial` — **at HEAD:** absent. **reddened by:** trusting `isOfficial`. **reaches the guard:**
    `isOfficial: true` entry serving a sample.
  - `savesAndReplacesAnOlderRelease` — **at HEAD:** absent. **reddened by:** not removing older releases of the same catalogueID. **reaches the guard:**
     two downloads with the same catalogueID; the first file is gone, the second present (positive).
- StartUp — `../FM-SWITCH/AppTests/StartUpTests.swift`; pure ones here, controller ones in step 8. Every case below is absent at HEAD.
  - `theAnswerIsRemembered` — **at HEAD:** absent. **reddened by:** `InMemoryAppSettings`/`DataStoreAppSettings` not persisting `startUp` (here: a
    fresh `InMemoryAppSettings` sharing a backing map). **reaches the guard:** nil → set → re-read.
  - `onlyKnownFirmwaresAreFlagged` — **at HEAD:** absent. **reddened by:** `FeluccaUpdateCode.isKnown` matching by prefix (`fm1-editor-estampie`).
    **reaches the guard:** the six inputs at `:24-29`.
  - `aPackageThatAcceptsV15IsntFlagged` — **at HEAD:** absent. **reddened by:** `InstallReadiness.refusesV15OverOtherBootloaders` falling back to
    `isKnown` when the package loads. **reaches the guard:** a saved sample whose catalogueID is a known one but whose code
    accepts V15.
  - `theWarningNamesTheOthersAndHedgesWhenUnsure` — **at HEAD:** absent. **reddened by:** listing the target among "the others". **reaches the guard:**
     AMB-1 sure / DUB-1 unsure inputs; strings from `StartUp.swift:77-101`.
- Filters and catalogue helpers — one method each, pure. Every case below is absent at HEAD.
  - `listsEachKindOnceTheMostCommonFirst` — **at HEAD:** absent. **reddened by:** sorting kinds by name. **reaches the guard:** kinds with unequal counts.
  - `matchesAKindWhateverItsCase` — **at HEAD:** absent. **reddened by:** case-sensitive compare. **reaches the guard:** `Drum` vs `drum`.
  - `keepsTheNameOrder` / `putsTheNewestReleaseFirstAndUndatedLast` — **at HEAD:** absent. **reddened by:** undated first. **reaches the guard:** one undated entry.
  - `countsTheFirmwareHubAndTheOfficialFirmwareAsTested` / `marksFirmwaresOnlyInTheResourceHubAsUntested` — **at HEAD:** absent. **reddened by:**
    testing `listing != 0` instead of the hub/editor bits. **reaches the guard:** `listing = 4` entry.
  - `dropsTheHubsFlagFromAPlainVersion` / `marksAnUntaggedBuildAsATest` (TestFlagTests) — **at HEAD:** absent. **reddened by:** removing
    `correctingTestFlags`. **reaches the guard:** fixture entries from `TestFlagTests.swift`.
  - `isBuiltFromSourceNeedsTheSameSha` — **at HEAD:** absent. **reddened by:** `sourceBuild != null` alone. **reaches the guard:** entry whose
    `sourceBuild.sha256` differs from `sha256`.
- `historyKeepsThirtyNewestFirst`. **at HEAD:** absent. **reddened by:** appending instead of prepending in
  `FlashHistory.record`. **reaches the guard:** 31 records written; first is the newest, count 30.
- `lastInstallOnlyIfNewestMatches`. **at HEAD:** absent. **reddened by:** searching all records in `lastInstall(product)`.
  **reaches the guard:** an older record matches, the newest doesn't → null.

### `:model` — step 8 (`FlashingTest`, `ReadinessTest`, `StandardLookTest`, `BeforeYouStartStateTest`)
`FlashingTest` ports `../FM-SWITCH/AppTests/FlashingTests.swift:49-140` (voice tests deferred) under `runTest`:
`DeviceController` on `backgroundScope`, simulated transport with **standard** timings (4 ms/request etc. — virtual).
`waitUntil` becomes `advanceUntilIdle`/a virtual-time poll; the watch loop runs forever, which is why it lives in
`backgroundScope`.
- `flashesAFirmwareAndRecordsIt` — succeeded `FM-1_916`, identity, last log `✓ Finished.`, history. **at HEAD:** absent.
  **reddened by:** not calling `history.record` in `DeviceController.run`. **reaches the guard:** confirmed switch.
- `flashesThroughALoaderThatReadsBackwardsAndVerifies` — exactly one "reading it back" line, before "Rebooting". **at HEAD:**
  absent. **reddened by:** logging on every `Checking` event in `DeviceController.apply`. **reaches the guard:** sim with
  `writesBackwards` + `checksAfterWriting` emits many checking events.
- `resumesAfterTheCableIsPulled` — `failed(canResume = true)`, then `resume()` → succeeded. **at HEAD:** absent. **reddened by:**
   `canResume` excluding `Disconnected(WRITING)`. **reaches the guard:** pull at 0.45 is during writing.
- `refusesTheSameIdentityUnconfirmed` — check kind `SAME_IDENTITY` first; failed, !canResume, message contains
  "already reports FM-1_900", identity unchanged. **at HEAD:** absent. **reddened by:** passing `confirmsSameIdentity = true`
  by default in `DeviceController.flash`. **reaches the guard:** check asserted first.
- `installsTheSameIdentityOnceConfirmed`. **at HEAD:** absent. **reddened by:** dropping `confirmsSameIdentity` from the
  `InstallOptions` built in `DeviceController.run`. **reaches the guard:** succeeded `FM-1_900` and history records it.
- `refusesToSwitchUnconfirmed` ("confirm the switch", !canResume, identity unchanged). **at HEAD:** absent. **reddened by:**
  defaulting `confirmsSwitchingFirmware = true` in `DeviceController.flash`. **reaches the guard:** check kind
  `SWITCHING_FIRMWARE` asserted first.
- `eachStepResumesWhenALoaderAnswers` — sim starts in recovery mode with the saved package prepared; `flash([item])`
  succeeds without a "Connected:" line (resume path). **at HEAD:** absent. **reddened by:** always calling `install` in
  `DeviceController.run`. **reaches the guard:** install would throw `DeviceNotFound` (firmware absent).
- `loaderNotFoundIsResumable` — `FlashJob.canResume(LoaderNotFound) == true`. **at HEAD:** absent. **reddened by:** omitting
  it from `FlashJob.canResume`. **reaches the guard:** pure.
- `planGoesViaOfficialUnlessAlreadyOnIt` — requiresOfficial → [V15, target]; device on `FM-1_015` → [target]. **at HEAD:**
  absent. **reddened by:** dropping the identity clause in `DeviceController.plan`. **reaches the guard:** sim set to V15 identity.
- `aRefusedInstallIsExplainedPlainly` / `aRefusalWithMVavesStartUpSuggestsV15First` — sim with m-bootloader options and a
  Felucca-code sample → failed with the MIDIMON/LOOM-8 message, `needsAStepFirst`; with start-up M-VAVE the message says
  "Install V15 first". **at HEAD:** absent. **reddened by:** (resp.) `failureMessage` not branching on `isRefusal`; ignoring
  `startUp` in `needsAStepFirst`. **reaches the guard:** the sim refuses in the boot area → `BootloaderMismatch` (asserted in the log).
- `aStallBeforeTheInstallStartsMightBeARefusal` / `bothRefusalsAreRecognised` — **at HEAD:** absent. **reddened by:**
  (resp.) `mightBeRefused` true for `Stalled(WRITING)`; `isRefusal` missing `PackageRefused`. **reaches the guard:** pure.
- Readiness (pure `InstallReadiness` state), one method each. Every case below is absent at HEAD.
  - `aChainIsAlwaysSwitching` — 2 steps + found `UPDATE` → `isSwitching`. **at HEAD:** absent. **reddened by:** `isSwitching` = found-switching only.
    **reaches the guard:** the found kind is not switching.
  - `onlyTheFirstStepIsChecked` — **at HEAD:** absent. **reddened by:** checking the target instead of `steps.first` in `InstallReadiness.check`.
    **reaches the guard:** fake checker records which item it was asked about.
  - `alreadyInstalledBlocksStart` — **at HEAD:** absent. **reddened by:** `canStart` ignoring `isAlreadyInstalled`. **reaches the guard:** found
    `REINSTALL`, one step, everything ticked.
  - `finishingUpdateCanStart` — **at HEAD:** absent. **reddened by:** treating a loader identity as `checking`. **reaches the guard:** identity `ota-FM-1_015`.
  - `startUpMustBeChosenFirst` — **at HEAD:** absent. **reddened by:** `canStart` ignoring `needsStartUp`. **reaches the guard:** a refusing
    package, `startUp == null`.
- StandardLook (`StandardLookTests.swift:20-75`), one method each. Every case below is absent at HEAD.
  - `progressRunsThroughTheStagesInOrder` — **at HEAD:** absent. **reddened by:** mapping `verifying` to stage `INSTALLING` in `InstallProgress.position`.
    **reaches the guard:** stages asserted for each phase.
  - `progressSpreadsAcrossTheSteps` — **at HEAD:** absent. **reddened by:** ignoring `index` (`within` only). **reaches the guard:** 2 steps,
    step 1 succeeded → 0.5.
  - `trackerNeverGoesBackwards` — **at HEAD:** absent. **reddened by:** `Tracker.update` taking the new fraction unconditionally. **reaches the guard:**
     writing 0.6 → preparing → writing 0.1 → failed.
  - `summarySaysWhatTheFm1IsDoing` — **at HEAD:** absent. **reddened by:** naming a unique catalogue match before the last install. **reaches the guard:**
     history and catalogue disagree; strings from `DeviceSummary.swift:51-76`.
- `BeforeYouStartState`: button title and enabling per `BeforeYouStartView.swift:178-194` — switching needs the saved tick;
  untested needs its tick; `confirmsSwitchingFirmware = isSwitching && hasSaved`, `confirmsSameIdentity = isSameIdentity`.
  **at HEAD:** absent. **reddened by:** enabling without `hasSaved` when switching. **reaches the guard:** pure.

### `:app` — steps 2, 9, 12–15 (JVM unit tests in `app/src/test`, no Android types)
- `LicenceAssetsTest` — `src/main/assets/licences/FM1Kit-LICENSE.txt` contains "Drew Robinson" and "MIT"; `M-Flasher-LICENSE.txt`
  contains "Vitkauskas"; `firmware/FM-1_015-LICENCE.txt` exists. **at HEAD:** absent. **reddened by:** deleting a notice file.
  **reaches the guard:** Gradle runs unit tests with the module dir as cwd.
- `ContrastTest` — palette pairs ≥ 4.5:1 text / 3:1 graphics (`../FM-SWITCH/AppTests/ContrastTests.swift`). **at HEAD:** absent.
  **reddened by:** lightening `Palette.secondaryInk` below the ratio. **reaches the guard:** ARGB Longs, pure.
- Badges (`StandardLookTests.swift:77-116`), pure `:model` code from step 14. Every case below is absent at HEAD.
  - `badgeLettersComeFromTheName` — **at HEAD:** absent. **reddened by:** taking letters from the id. **reaches the guard:** id ≠ name.
  - `badgesKeepToTheirNames` — **at HEAD:** absent. **reddened by:** seeding the design with `hashCode()` of a mutable/`Random`. **reaches the guard:**
     same name twice, two runs.
  - `badgesComeIn896Designs` — **at HEAD:** absent. **reddened by:** a design count other than 896. **reaches the guard:** enumerates all.
  - `theOfficialBadgeShowsItsVersion` — **at HEAD:** absent. **reddened by:** official treated as a normal entry. **reaches the guard:** V15 entry.

### Steps whose behaviour no automated test can observe (Blocker records)
Commands run 2026-10-30 at `aa66529`: `ls ~/.android/avd` → `ls: cannot access '/home/dabit/.android/avd': No such file or
directory`; `adb devices` → `List of devices attached` (empty); `ls -A /home/dabit/git/FM-SWITCH-android` → `.jdi/ doc/
plans/ AGENTS.md CLAUDE.md` (no test suite, no test doubles installed).
- **B1 — step 9 `AndroidMidiTransport` against a real FM-1, and step 10 probe output.** No FM-1, no phone, no Android MIDI
  test double for `MidiManager`. Proved instead by: framing/port-matching logic in `:fm1` tests above; UAT U1–U3.
- **B2 — step 11 `startForeground(connectedDevice)` on API 34+, wake lock, notification.** No device or AVD. Proved by UAT
  U4. The Executor may create an AVD from the installed `android-37.x` image and run `scripts/check.sh device`; if it can't
  boot one, record that as an addendum to this record.
- **B3 — Compose rendering of steps 12–15.** Their logic is in `:model` and tested above; the rendering is proved by the
  simulated walkthrough U0 (emulator or phone, no FM-1 needed). Optional instrumented smoke tests in `app/src/androidTest`
  run under the `device` group only.
- **B4 — dropped** with step 16 (no CI).

### Manual UAT — needs a human
**U0 (emulator or any phone, no FM-1)** — Settings ▸ Connection ▸ Simulated FM-1: welcome notice OK after 4 s; Home shows
`FM-1_900`; install a sample/downloaded firmware through Before you start (switch tick appears), Installing runs to Done;
cable pull at 45% → "The install was interrupted" → Carry on installing → Done; m-bootloader + a Felucca-code firmware → the
start-up question and warning; back is blocked during the install.

**U1–U4 need a human with an FM-1 and an Android phone (API 26+; one API 34+ for U4):**
- **U1 probe** (debug build ▸ "FM-1 probe"): plug in the FM-1; record the device's `PROPERTY_*` values, VID:PID, port names;
  tap Send handshake → identity shown; note any permission dialog; note `onSend` chunk sizes. Then (same session) start an
  install from the app and come back: the probe log shows whether the reboot into the loader was remove + add and the loader's
  names/VID:PID. Copy the log into the plan. Settles premises P4(a)–(d).
- **U2 install**: V15 over the running firmware (or a custom firmware), real cable; Done screen; "Show the details" log has
  the device add/remove lines.
- **U3 resume**: pull the cable during "Installing"; re-plug; Home shows the finish card; Carry on installing → Done. Watch
  for the stock loader's unsolicited identity mid-write (research §2.9).
- **U4 staying alive**: on API 34+, start an install, press Home, lock the screen 60 s; the install completes; the
  notification shows (or, without notification permission, the job log has no "couldn't start the service" line).

## Risks

- **AGP 9 built-in Kotlin vs `kotlin("jvm")` version skew** breaks configuration. Mitigation: step 1 pins the catalog's
  Kotlin to the version AGP 9.5 brings and makes `./gradlew help` part of "done".
- **Android Studio uses its bundled JBR 25 as Gradle JDK by default**; with no provisioning, `jvmToolchain(21)` fails there.
  Mitigation: AGENTS.md (step 17) says to set Studio's Gradle JDK to the mise JDK 21.
- **JDK selection is per machine.** System java is 25; mise has 21; other machines differ. Mitigation: the check script picks
  the JDK and fails plainly; no auto-provisioning (observation: a machine property is not a job property).
- **Virtual-time leaks** (a hard-coded dispatcher, `Thread.sleep`, wall clock) make tests slow or hang. Mitigation: the
  binding rule above; `runTest`'s timeout turns a leak into a red test, and `inboxTimeout` asserts `currentTime`.
- **Symmetric encoding errors** pass round trips and the simulator (which shares the encoder). Mitigation: golden vectors
  (V1–V3) from an independent implementation, plus the real recorded reply.
- **`ByteArray ==` is identity** in Kotlin — a port slip makes every simulated install stall and looks like a timing bug.
  Mitigation: stated in steps 3 and 5; Spotless/ktlint won't catch it, review will.
- **Android port naming and reboot semantics are UNCHECKED.** Mitigation: match on every exposed name **and** VID:PID; `isLost`
  on either signal; installs never depend on device callbacks (they poll). A generic fragment (`usb-midi`, `composite`) can
  match other gear — same exposure as iOS; only a handshake is ever sent, and only to matching devices.
- **USB permission prompt** may appear on some phones (P4(c)). Mitigation: `openDevice` failure surfaces as the connect
  card's error + Try again; U1 records it.
- **FGS prerequisite is documented, not run.** Mitigation: `CHANGE_NETWORK_STATE` route + catch around `startForeground` +
  loader resume; U4.
- **No adb while the FM-1 occupies the phone's port.** Mitigation: on-screen + copyable logs (probe, job details).
- **Copy drift**: Android wording for Mac-only recovery must stay true (going back to V15 after a refusing firmware needs
  the Mac app). Mitigation: step 15 keeps the facts of `FeluccaUpdateCode.Warning`; Drew reviews the changed strings.
- **Help is out**, so "What can I do?" and the warning card's link are dropped; the refusal message itself names the fix.
  Entries needing a build choice can't be installed (the picker is out) — matches the source's disabled button.
- **`applicationId` is permanent once released** (`dev.drewr.fmswitch` chosen to match iOS). Change before the first
  GitHub release if Drew wants otherwise.
- **Real throughput** of ~1,200 × 605-byte replies over Android USB MIDI is unmeasured; `writeIdleLimit` 60 s gives margin.
- **`SamplePackage` ships in the APK** (main source set, as FM1Kit's `@_spi(Testing)` does). Small; R8 can strip it in release.

## Appendix — verbatim byte vectors

Each vector is one line of space-separated upper-case hex, complete, no elisions; the line breaks between vectors are this
document's, not part of the bytes. Generated 2026-10-30 by a Python implementation of FM1Kit's layout
(`FM1Message.swift:89-120,228-239`, `SevenBit.swift:11-50`), which also reproduces the recorded real reply and its checksum.

- V1 `ReadRequest(flashType=1, address=0x00012340, length=481).encoded()` (20 bytes):
  `F0 00 32 41 41 00 00 40 00 40 46 04 00 10 3C 00 00 38 01 F7`
- V2 `ReadRequest(flashType=0, address=0x40, length=4).response([01 02 03 04])`:
  `F0 00 32 41 61 00 00 00 00 40 00 00 00 40 00 00 00 01 04 0C 20 10 16 F7`
- V3 `ReadRequest(flashType=0, address=0xF0000000, length=8).finishResponse()`:
  `F0 00 32 41 01 01 00 00 00 00 00 00 00 0F 01 00 00 73 6A 0D 1B 56 6C 5C 39 00 1C 00 F7`

- `v15.py` (plan-time check of the bundled V15's container, run 2026-10-30 against `../FM-SWITCH/App/Resources/Firmware/FM-1_015.fwsc`):
  product `FM-1_015`, logical image 699,936 bytes, 8 parts, chip `AC791N`; `flash.bin` type 0 at `0x400`, 602,112 bytes, SHA-256
  `c1068a403c04a6378a626afb66cc217f72d13f10aa200e983b7a9817cc1dac03` (= FM1Kit's "V15 flash image" constant); `ota.bin` type 100 at
  `0xA5F00`, 19,969 bytes, not enciphered, CRC OK, SHA-256 `ef1f2db89b011fa53e71df363f0122de0ea896618c53fb53e8da676322004541`
  (= the stock loader). The Executor may re-derive these with a few lines of Kotlin in `bundledV15IsTheOfficialFirmware`.

## Tasks

**Wave 1**
- [x] 01 — Gradle skeleton, toolchain, format/lint and the check script (depends on: none)

**Wave 2** — run together
- [x] 02 — Licence notices and the bundled V15 (depends on: 01)
- [x] 03 — `:fm1` protocol, framing and inbox (depends on: 01)
- [ ] 04 — UI foundations: theme, drawing, navigation, welcome notice and route placeholders (depends on: 01)

**Wave 3** — run together
- [x] 05 — `:fm1` package checks (depends on: 02, 03)
- [ ] 06 — Docs: real commands in AGENTS.md, Standard-look confirmations, settled architecture (depends on: 01, 03)

**Wave 4** — run together
- [ ] 07 — `:fm1` transport contracts, port matching and the simulated FM-1 (depends on: 05)
- [ ] 08 — `:model` data layer: published list, catalogue cache, firmware library, settings, history, start-up (depends on: 05)

**Wave 5** — run together
- [ ] 09 — `:fm1` updater (depends on: 07)
- [ ] 10 — `:app` platform adapters: Android MIDI transport, HTTP fetch, DataStore settings (depends on: 07, 08)
- [ ] 11 — Hardware probe screen (debug builds only) (depends on: 07)

**Wave 6**
- [ ] 12 — `:model` device and install state (depends on: 08, 09)

**Wave 7**
- [ ] 13 — App wiring and the foreground service (depends on: 04, 10, 12)

**Wave 8** — run together
- [ ] 14 — Home, Settings and About (depends on: 13)
- [ ] 15 — Firmwares list, firmware page and My firmwares (depends on: 13)
- [ ] 16 — Before you start, Installing, Done and Problem (depends on: 13)

**Wave 9**
- [ ] 17 — UAT: the Android port, end to end (depends on: 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13, 14, 15, 16)

### Shape

17 tasks in 9 waves; widest waves 2, 5 and 8 (three tasks each). Critical path: 01 → 03 → 05 → 07 → 09 → 12 → 13 →
16 → 17 (16 is interchangeable with 14 or 15). Differences from the Implementation Plan's step graph:
- Step 16 (CI) is dropped (Decision "No CI"); the check script is the gate and the UAT's last step.
- Step 4 (task 05) also depends on step 2 (task 02): `bundledV15IsTheOfficialFirmware` reads the bundled V15.
- Step 10 (task 11) depends on step 5 (task 07), not just 3, so the probe uses production's `PortMatching.isProbeable`.
- Step 11 (task 13) also depends on step 12 (task 04): it feeds app state into task 04's `MainActivity` and `AppRoot.kt`
  — a shared-file dependency, off the critical path.
- Step 17 (task 06) runs early (after 01 and 03) instead of last; recording U1's findings moved to the UAT's deferred list.
- Task 01 creates every shared build file, the main manifest, `FmSwitchApplication` and both `ProbeHooks`, so steps
  10/11/13 never share a file; `LicenceAssetsTest` moved from step 13 to task 02, `aPackageThatAcceptsV15IsntFlagged`
  from step 7 to task 12, the two `WriteProgress` region cases from step 4 to task 09.
