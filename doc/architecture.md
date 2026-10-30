# FM-SWITCH for Android: architecture (draft)

Status: **draft**, 2026-10-30, from `plans/android-port/research.md`. Items marked **[fact]** are
taken from the iOS app (`../FM-SWITCH` @ 67ee8c2), FM1Kit 0.4.5 (88b609c, MIT) or the Android
sources; items marked **[proposal]** are design choices for the plan to confirm or change. Scope
follows `plans/android-port/PLAN.md` Decisions: Standard look only, minSdk 26.

## 1. Modules

```
:fm1   pure Kotlin/JVM library, no Android dependencies            [proposal]
  package/    FwscPackage, FirmwareContainer, Crc16, Decipher, OfficialFirmware,
              ApplicationArea (refusesV15OverOtherBootloaders), SamplePackage (test fixtures)
  protocol/   SevenBit, Fm1Message, ReadRequest, Fm1Identity, Fm1Capability, BuildId
  transport/  MidiTransport, MidiConnection, PortDescriptor, SysexInbox, SysexFramer,
              SimulatedFm1 (+ Options)
  updater/    Fm1Updater, UpdaterTiming, UpdateEvent, WriteProgress, InstallCheck/InstallKind,
              InstallOptions, Fm1UpdateError (+ copy), [Fm1Advice optional]
:app   Android application (Compose, minSdk 26)
  midi/       AndroidMidiTransport (android.media.midi), UnavailableTransport
  data/       CatalogueStore, PublishedList, FirmwareLibrary, FlashHistory, AppSettings,
              StarredFirmwares (if in scope)
  model/      DeviceController (+ FlashJob/FlashPhase), InstallReadiness, InstallProgress(+Tracker),
              DeviceSummary, FirmwareFilter, StartUp/FeluccaUpdateCode, EntryDescriptions
  service/    FlashService (foreground service + wake lock)
  ui/         Standard screens, theme (Palette, Motion), FM1Drawing
```

- `:fm1` mirrors FM1Kit's split (package, protocol, transport, updater) **[fact: AGENTS.md
  "Architecture constraints"]** so its tests run with `./gradlew :fm1:test` on any JVM, CI
  included, without an emulator. It's a port of FM1Kit (MIT): keep FM1Kit's copyright and
  permission notice in the module and in the app's notices, and M-Flasher's (Vitkauskas, MIT)
  if `ApplicationArea` is ported **[fact: FM1Kit LICENSE; Package/ApplicationArea.swift:1-19]**.
- Deferred FM1Kit parts not ported: voices, open-firmware backup/project library, bootloader
  installer, FM1Rescue/download mode, `fm1probe`. **[fact: PLAN.md Decisions]**
- Coroutines in `:fm1` (`kotlinx-coroutines-core` is JVM-only, fine) **[proposal]**: suspend
  functions for I/O, `Flow` for device changes, a `(UpdateEvent) -> Unit` callback or a
  `Flow<UpdateEvent>` for progress, injected `UpdaterTiming` and a `TimeSource`/`delay` so tests run
  under `kotlinx-coroutines-test` virtual time.

## 2. Transport

```kotlin
// [proposal] — shapes follow FM1Kit Transport/MIDITransport.swift:5-78
data class PortDescriptor(val id: String, val name: String, val serialNumber: String?,
                          val usbVendorId: Int? = null, val usbProductId: Int? = null)
interface MidiTransport {
    suspend fun ports(): List<PortDescriptor>
    suspend fun open(port: PortDescriptor): MidiConnection   // throws PortGone / Unavailable
    fun changes(): Flow<Unit>
}
interface MidiConnection : AutoCloseable {
    val port: PortDescriptor
    val isLost: Boolean
    fun send(sysex: ByteArray)                                 // complete F0..F7
    suspend fun receive(timeout: Duration): ByteArray?          // null on timeout/closed
    fun drain()
}
```

- **Probing [fact]:** send only to ports whose lower-cased name contains `fm-1, fm1, felucca,
  ota, composite, usb-midi, simulated, sloop, clipper, sinco` (FM1Kit `FM1Updater.swift:23-27`).
  **[proposal]** also accept USB `4c4a:c755` / `4d4a:4155` from `MidiDeviceInfo.PROPERTY_USB_DEVICE`;
  never send to anything matching neither. Which string Android exposes as the "name" (device
  `PROPERTY_NAME`/`PROPERTY_PRODUCT` vs `PortInfo.getName()`) is **unverified** — match against all of them.
- **SysexInbox [fact]:** single reader, timed wait, capacity 256 dropping the oldest, `drain`,
  `close` wakes the reader with null (FM1Kit `MIDITransport.swift:107-218`). Kotlin: a
  `Channel<ByteArray>` with a bounded buffer and `withTimeoutOrNull` is the natural equivalent
  **[proposal]**; keep the drop-oldest policy (`BufferOverflow.DROP_OLDEST`).
- **AndroidMidiTransport [proposal]:**
  - `MidiManager` from `Context.MIDI_SERVICE`; check `PackageManager.FEATURE_MIDI`; declare
    `<uses-feature android:name="android.software.midi" android:required="true"/>` **[fact: AOSP package.html]**.
  - `ports()` = `getDevices()` (API 23; `getDevicesForTransport` is newer — API 33 per the developer docs, not re-verified) filtered to
    `TYPE_USB` (and virtual devices only in debug), one descriptor per device with ≥1 input and
    ≥1 output port; `id` = `MidiDeviceInfo.getId()`; serial = `PROPERTY_SERIAL_NUMBER`.
  - `open()` = `openDevice` (callback → suspend), `openInputPort(0)` for sending (the device's
    input) and `openOutputPort(0)` + `connect(receiver)` for receiving **[fact: direction rule,
    AOSP package.html]**.
  - `send()` calls `MidiInputPort.send(bytes, 0, n)`; it splits above the 1015-byte packet limit
    by itself, and the largest message is 605 bytes **[fact: AOSP MidiReceiver.java:89-106,
    MidiPortImpl.java:39-54]**.
  - Receive: `MidiReceiver.onSend` gives byte chunks with no message boundaries and a buffer valid
    only during the call **[fact: AOSP MidiReceiver.java:44-61]** → copy into a `SysexFramer`:
    collect `F0…F7`, ignore real-time bytes F8–FF, abort on any other status byte, a new `F0`
    discards an unfinished message, drop > 64 KiB **[rules from FM1Kit UMPSysex7/EdgeCaseTests]**.
  - `changes()` = `registerDeviceCallback(callback, Handler(Looper.getMainLooper()))` as a
    `callbackFlow`. Whether the loader reboot shows as remove + add is **unverified**.
  - `isLost` = the device's id no longer in `getDevices()`, or `onDeviceRemoved` seen for it, or
    the `MidiDevice` closed.
  - USB permission prompts: **unverified**; none expected for class-compliant devices via
    `MidiManager`.
- **SimulatedFm1 [fact: behaviour; proposal: Kotlin shape]:** reproduces FM1Kit
  `SimulatedFM1` — one port `FM-1 Midi (simulated)`, generation-numbered so connections die on
  reboot, loader `ota-FM-1_9` (or `ota-FM1C_<prefix>` once armed), staging 74 reads then
  `0xE0000000`, writing 512-byte chunks (optionally backwards, optionally twice), cable pull once
  at a fraction, V15-over-V15 refused, open firmware ignores UPGRADE until armed, m-bootloader
  boot-area refusal, header refusal; every response checked byte-for-byte. Uses coroutine `delay`
  for request interval (4 ms), reboot (1.5 s) and response timeout (5 s), all injectable. The app
  exposes it through Settings ▸ Connection.

## 3. Updater

State machine **[fact: FM1Kit Updater/FM1Updater.swift:162-294]**:

```
install(package, options):
  findOnly(!loader)  ─none→ DeviceNotFound   ─>1→ MultipleDevices
  check → reinstall→AlreadyInstalled | switching&!confirmed→SwitchNotConfirmed
                    | sameIdentity&!confirmed→SameIdentityNotConfirmed
  emit Connected → [open firmware: capability 0x31, arm 0x32] → UPGRADE
  serve(staging, idle 8 s) ─finish 0xE0000000─┐
     └ stopped: boot-area read→BootloaderMismatch | header read & connected→PackageRefused
                | lost→Disconnected(staging) | idle→Stalled(staging)
  emit WaitingForLoader, wait 3 s, waitFor(30 s, loader for model [+armed prefix]) ─none→ LoaderNotFound
  write()
resume(package): findOnly(loader for model) ─none→ LoaderNotFound; write()
write(): emit LoaderFound → UPGRADE → serve(writing, idle 60 s, WriteProgress) ─finish 0xF0000000─┐
     └ lost→Disconnected(writing) | idle→Stalled(writing)    (device stays in its loader: resumable)
  emit Rebooting, wait 3 s, waitFor(40 s, !loader) ─none→ DeviceDidNotReturn
  identity ≠ package → IdentityMismatch; open firmware build ≠ expected → BuildMismatch
  emit Finished(identity)
serve(): requests > 512 or past the image → BadRequest; the other phase's finish or length≠8 →
  UnexpectedFinish; finish → reply "success\0", settle 600 ms re-answering repeats.
```

- Timing table: research §2.8. Progress: `WriteProgress` over the application area, out-of-order
  safe (research §2.9). Cancellation leaves the device in its loader.
- `Fm1Updater` serialises its own calls (FM1Kit uses an actor) **[proposal: a `Mutex` or a
  single-threaded dispatcher]**.

## 4. App state and data

```
AppModel (Application-scoped, created once)                        [proposal: manual DI]
 ├─ AppSettings      DataStore<Preferences>                          [proposal]
 ├─ CatalogueStore   PublishedList → filesDir/catalogue.json (ISO-8601 dates)
 ├─ FirmwareLibrary  filesDir/Firmwares/<name>.fwsc + .details/<sha256>.json; V15 from assets
 ├─ FlashHistory     newest-first, 30 records (DataStore or a JSON file)
 ├─ StarredFirmwares (if in scope)
 └─ DeviceController owns the transport, Fm1Updater, scan loop, FlashJob; StateFlows
```

- **DeviceController [fact: behaviour, DeviceController.swift]:** `identity`, `ports`, `isScanning`,
  `job`, `log`, `transportError` as `StateFlow`s; `configure(kind, settings)`, `scan()` (3 retries),
  `plan(...)` (via V15 when required / always-via-official, unless already on V15), `check(item)`,
  `flash(steps, confirmsSwitchingFirmware, confirmsSameIdentity)`, `resume()`, `dismissFlash()`;
  each step resumes if any loader answers, else installs. Events map to `FlashPhase`
  (preparing → staging → waitingForLoader → writing → verifying → rebooting → succeeded/failed(canResume)).
- **The run outlives the screen [proposal]:** `DeviceController` lives in the Application; a flash
  starts `FlashService` (foreground, notification "Installing <name>… keep it plugged in", partial
  wake lock) and stops it when the job ends. The install screen also sets `FLAG_KEEP_SCREEN_ON`.
  FGS type: `connectedDevice` (needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` plus an API 34
  prerequisite — see research §3.7) or `shortService` (~3 min cap). Decide in the plan. Loader resume
  remains the safety net either way.
- **FirmwareLibrary [fact: rules, FirmwareLibrary.swift]:** checks size/SHA-256/product on download,
  recognises V15 by size+hash, keeps the bundled V15 unremovable and never copies it, `packages(thatCanFinish:)`
  for loader identities (stock `ota-FM-1_015` via loader hash; armed `ota-FM1C_` via build-ID prefix).
  Imports via SAF `ACTION_OPEN_DOCUMENT` **[proposal]**. Bundle `FM-1_015.fwsc` unmodified in
  `assets/` with `FM-1_015-LICENCE.txt` **[fact: AGENTS.md]**.
- **CatalogueStore [fact: rules, CatalogueStore.swift/PublishedList.swift]:** refresh downloads
  `https://fm-1.drewr.dev/firmwares.json`, retries after 2/4/8 s on network errors, refuses a list with
  `formatVersion > 1` or no entries, keeps the cached list on failure and only complains if it's > 1 day
  old. **[proposal]** no on-device list building (AGENTS.md). Decoding with kotlinx.serialization,
  `ignoreUnknownKeys = true`, `listing` as an Int bitmask.
- **InstallReadiness, InstallProgress(+Tracker), DeviceSummary, FirmwareFilter, StartUp** are pure
  Kotlin and unit-tested like their Swift counterparts **[proposal; fact: rules in research §3]**.

## 5. UI

- Jetpack Compose, single Activity, Navigation Compose with routes Home, Firmwares, Firmware(id),
  MyFirmwares, Settings, (Help) **[proposal]**. Before you start is a modal bottom sheet or full-screen
  dialog; Installing is a full-screen overlay that blocks back while a job runs (`BackHandler`), as
  iOS's cover can't be swiped away **[fact: StandardRootView.swift:66-78; REDESIGN.md:80-82]**.
- Screens observe `StateFlow`s with `collectAsStateWithLifecycle()`; one-shot navigation requests
  (`requestedPage`) as a `StateFlow<RequestedPage?>` cleared after use, as on iOS **[proposal]**.
- Theme: Palette light/dark values and Motion springs from REDESIGN (`:52-66,175-186`); Material 3
  with custom colours, no dynamic colour; appearance setting System/Light/Dark **[proposal]**.
- Haptics: `HapticFeedbackConstants` CONFIRM/REJECT/CLOCK_TICK for success/error/selection, matching
  REDESIGN's four feedback kinds **[proposal]**.
- Copy: reuse the strings listed in research §3.9; adapt "iPhone/iPad/Files/Mac" wording.
- Links open in Custom Tabs **[proposal]**.

## 6. What doesn't port

- Retro look, tip jar, alternate icon, tablet/desktop two-pane layout **[fact: deferred by PLAN.md]**.
- Save my FM-1 (open-firmware backups) and factory-voice restore **[deferred]**; their simulator options are dropped.
- Mac Backup & rescue / download mode, the root flash tool, ROM bootloader recovery: no Android
  equivalent. Say so plainly where the app would offer them, as the iOS app does **[fact: AGENTS.md;
  SafetyNoticeView.swift:42-51]**.
- iCloud Drive sharing: files stay in app storage **[proposal]**.
- On-device catalogue building (~90 GitHub requests) **[proposal: drop]**.
- Background update notifications **[out of scope unless the plan adds them]**.

## 7. Testing

- `:fm1` unit tests port FM1Kit's ProtocolTests, PackageCheckTests, WriteProgressTests, UpdaterTests,
  OpenFirmwareTests, EdgeCaseTests against `SimulatedFm1` and `SamplePackage` under virtual time.
- `:app` JVM unit tests port FlashingTests, LibraryTests, PublishedListTests, StartUpTests,
  StandardLookTests (research §4.1), with the simulator, a temp directory, and a fake fetch.
- Compose UI tests and a device run for USB MIDI are separate; the real-hardware probe in research
  §0 P4 comes first.
- CI [proposal]: GitHub Actions on Ubuntu, JDK 17 or 21 via `actions/setup-java`, `./gradlew check`
  (lint + unit tests), no emulator.
