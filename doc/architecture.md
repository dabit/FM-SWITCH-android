# FM-SWITCH for Android: architecture

Status: 2026-11-09. This update is against the iOS app (`../FM-SWITCH` @
`fcf4a78`) and FM1Kit 0.6.0 (`53f8511`, MIT). Items marked **[fact]** come
from those sources or the Android sources; **[decided]** records a plan
decision; **[proposal]** remains open. The Android app has the Standard look
only and has minSdk 26.

## 1. Modules

```
:fm1   pure Kotlin/JVM library, no Android dependencies            [decided]
  pkg/        FwscPackage, FirmwareContainer, Crc16, Decipher, OfficialFirmware,
               ApplicationArea, LeavingGuide, FlashChipSupport
  protocol/   SevenBit, Fm1Message, ReadRequest, Fm1Identity, Fm1Capability, BuildId,
               Fm1FlashChip
  transport/  MidiTransport, MidiConnection, PortDescriptor, SysexInbox, SysexFramer,
               SimulatedFm1 (+ Options)
  updater/    Fm1Updater, Fm1DeviceSummary, UpdaterTiming, UpdateEvent, WriteProgress,
               InstallCheck/InstallKind, InstallOptions, Fm1UpdateError
:model pure Kotlin/JVM data layer                                  [decided]
  catalogue/  CatalogueStore, PublishedList, FirmwareFilter, HubFlashChip,
               FlashChipWords, FirmwareDescription and catalogue types
  library/    FirmwareLibrary
  device/     DeviceController, FlashJob/FlashPhase, InstallReadiness,
               InstallProgress(+Tracker), SwitchingGuide, SwitchingWords, FixedFirmware,
               DeviceSummary
  history/    FlashHistory
  settings/   Settings and StartUp
:app   Android application (Compose, minSdk 26)
  midi/       AndroidMidiTransport (android.media.midi), UnavailableTransport
  service/    FlashService (foreground service + wake lock)
  ui/         Standard screens, including FilteredFirmwareList, theme (Palette, Motion),
               and FM1Drawing
```

- `:fm1` mirrors FM1Kit's package, protocol, transport and updater split, so it runs under
  `./gradlew :fm1:test` on a JVM without an emulator. It retains FM1Kit's MIT notice; M-Flasher's
  MIT notice applies if `ApplicationArea` is ported.
- The package layer distinguishes official V15 and V16 identities. V16 is recognised and may be
  downloaded, but is never bundled or committed: M-VAVE has not published it under a licence.
  V15 remains the Apache-2.0 asset shipped with the app. **[decided]**
- `LeavingGuide` determines the route from a saved firmware's update code and boot area;
  `FixedFirmware` finds MIDIMON when an install must first pass through a fixed-check firmware.
  `FlashChipSupport` reads a package's chip support, and `Fm1FlashChip` names the two chip IDs.
- `Fm1DeviceSummary(identity, flashChipId)` is the updater's full handshake result. Its
  `hasNewerFlashChip` feeds the catalogue's old-chip warning.
- `SamplePackage` remains main-source `:fm1/testing`, rather than Gradle test fixtures, so model
  and app tests can make package fixtures. **[decided]**
- **Virtual-time rule [decided]:** `:fm1` and `:model` use `delay`, `withTimeoutOrNull`, and
  channel waits on injected coroutine machinery. They do not derive timing decisions from a clock,
  block with a thread sleep, hard-code a dispatcher, or use `runBlocking`; file I/O has an injected
  `ioDispatcher`. The full-identity and post-write quiet windows are timeout/delay windows, so
  coroutine tests advance them with virtual time rather than wall time.

## 2. Transport

```kotlin
data class PortDescriptor(val id: String, val name: String, val serialNumber: String?,
                          val usbVendorId: Int? = null, val usbProductId: Int? = null)
interface MidiTransport {
    suspend fun ports(): List<PortDescriptor>
    suspend fun open(port: PortDescriptor): MidiConnection
    fun changes(): Flow<Unit>
}
interface MidiConnection : AutoCloseable {
    val port: PortDescriptor
    val isLost: Boolean
    fun send(sysex: ByteArray)
    suspend fun receive(timeout: Duration): ByteArray?
    fun drain()
}
```

- Probe only ports whose names identify compatible FM-1 hardware; no SysEx goes to unrelated MIDI
  equipment. USB normal and loader IDs are `4c4a:c755` and `4d4a:4155` respectively. **[fact]**
- `SysexInbox` has one reader, bounded drop-oldest buffering, timed receive, drain, and close
  wake-up. Android receives arbitrary byte chunks, so `SysexFramer` collects complete `F0...F7`
  messages, ignores realtime bytes, abandons malformed frames and caps frames at 64 KiB.
- `SimulatedFm1` reproduces the loader protocol with injectable delays, backwards/out-of-order
  reads, optional verification and a cable pull. It refuses an official V15 or V16 over itself;
  a look-alike such as TransitFM1 is distinct from the official firmware despite an official-looking
  identity.

## 3. Updater

The updater performs the two phases: stage through the running firmware, wait for the loader, and
then write through the loader. Progress uses the ranges served by `WriteProgress`, never the last
address, because loaders may read out of order. Cancellation or loss during writing leaves the
loader available for resume.

- `Fm1Updater.summary()` sends the normal handshake and, when appropriate, the full-identity
  query to form `Fm1DeviceSummary`. A full-identity quiet window uses
  `UpdaterTiming.fullIdentityWait`; it is a virtual-time timeout, not a clock comparison.
- Once nearly all application bytes have been served, a silent loader gets a return check after
  `UpdaterTiming.returnCheckAfter`; this too is measured by the updater's own virtual-time quiet
  window. A returned target identity completes the install even when the endpoint does not report
  itself lost.
- Resume selects a compatible loader. Under P-D5, an `ota-FM-1_0NN` loader is stock only when its
  hardware model is `FM-1` and its version is one of the official identities FM1Kit knows (15 or
  16). Custom loaders, including `ota-FM-1_916`, continue to match only their own package. This
  makes an interrupted V16-to-V15 write resumable after the app restarts.
- The app represents the safety-critical second pass as `FlashPhase.SecondPass`, labelled
  `WRITING PASS 2`; it is counted as `InstallProgress.Stage.Installing`, not a checking phase.
  The copy tells a person not to switch the FM-1 off during this pass. **[decided]**

## 4. App state and data

```
AppModel (application scoped)
 ├─ AppSettings      DataStore<Preferences>
 ├─ CatalogueStore   PublishedList → filesDir/catalogue.json
 ├─ FirmwareLibrary  filesDir/Firmwares/<name>.fwsc + .details/<sha256>.json; V15 asset
 ├─ FlashHistory     newest-first records, including a saved guide
 └─ DeviceController owns transport, Fm1Updater, scan loop and FlashJob StateFlows
```

- `DeviceController` exposes scanning, identity, `flashChipId`, the running guide and flash-job
  state. It resumes when a matching loader answers; otherwise it installs. A foreground
  `FlashService`, wake lock and keep-screen-on flag keep an Android install alive while the
  screen remains open.
- `FirmwareLibrary` validates size, SHA-256 and product before adding a download or import. It
  retains every saved release of a firmware rather than replacing older saved releases. V15 is
  unremovable and stays in assets; saved official V16 is removable. Imports use SAF
  `ACTION_OPEN_DOCUMENT`.
- Catalogue entries retain the list's fields, including install count, flash-chip requirement and
  listing flags. Both production JSON decoders enable `coerceInputValues` as well as ignoring
  unknown keys. Android deliberately turns an unknown enum value into its nullable/default value
  rather than rejecting the whole catalogue, because unlike iOS it has no on-device catalogue
  builder as a fallback. **[decided: D10]**
- The published catalogue is the default and only refresh source. It rejects a newer format or an
  empty list, retains a cache after failure, and does not make roughly 90 GitHub requests to build
  a list on the device.
- `AppSettings` persists `firmwareScope`, `myFirmwaresOrder`, and `myFirmwaresScope` in addition
  to the main firmware order. `FirmwareScope.storedValue` and `fromStored` make a vanished picked
  kind fall back to All. `FilteredFirmwareList` shares the ordering, wrapped filters and pinned
  selection presentation between the catalogue and My firmwares.
- `StartUp.V16` records that M-VAVE's V16 installed its own boot area. Its name is “Changed by
  V16” and it informs return routing and V16-to-V15 warnings.
- `SwitchingGuide` and `SwitchingWords` present a safe route before flashing. `FixedFirmware`
  supplies MIDIMON first when a guide requires it, and `FlashHistory` saves the guide with the
  completed install.

## 5. UI

- The Android app is a single-activity Compose Standard experience with Home, Firmwares,
  Firmware, My firmwares and Settings destinations. `FilteredFirmwareList` uses a wrapping scope
  row and pins the selected filter with the order when filters scroll away.
- Before-you-start is a modal sheet or full-screen dialog; Installing is a full-screen overlay
  that blocks back navigation while a flash is active. A confirmation is required for switching
  firmware. An apparent official identity asks the person to confirm a look-alike when appropriate.
- V16 is available from the catalogue but never from bundled bytes. `SwitchingWords.BEFORE_V16_WARNING`,
  `SwitchingWords.AFTER_V16_WARNING`, and `SwitchingWords.STILL_MONO` use the exact Android copy
  in the plan appendix, including “V16 isn't included with this app; it's downloaded from M-VAVE.”
  The V15 recovery path is FM-SWITCH's Backup & rescue on a Mac; this app cannot clear the data
  V16 leaves behind or put back the factory sounds.

## 6. What does not port

- Retro look, tip jar, alternate icon and tablet/desktop two-pane layout are out of scope.
- Open-firmware backup/project library, factory-voice restore, FM1Rescue/download mode,
  bootloader installer, root flash tool and ROM bootloader recovery have no Android equivalent.
- iCloud sharing and background update notifications are out of scope. The app stores imported
  files in app storage and does not build the catalogue on device.

## 7. Testing

- `:fm1` ports FM1Kit package, protocol, updater and transport tests using `SimulatedFm1` and
  `SamplePackage` under virtual time. Its tests include chip reporting, short identities, quiet
  return detection, out-of-order progress and loader resume.
- `:model` tests cover catalogue decoding, retained saved releases, switching guides, start-up
  state and controller progress. Compose and hardware USB MIDI checks are separate.
- There is no CI. `scripts/check.sh` is the local lint, build and unit-test gate.
