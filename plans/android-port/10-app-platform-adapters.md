status: pending
# 10 — `:app` platform adapters: Android MIDI transport, HTTP fetch, DataStore settings

Depends on: 07, 08

## Why
The protocol core and data layer are pure JVM; on a phone they need an `android.media.midi` transport that only ever
opens FM-1-looking devices, an HTTP fetch for the published list and downloads, and persistent settings. Rests on:
Platform; Firmware list; Scope.

## Description
Plan step 9. Read research.md §3.7 and `doc/architecture.md` §2 (AndroidMidiTransport) first. Do not edit any build
file, `strings.xml` or the manifest (task 01's rule; the MIDI feature and INTERNET are already declared).

- `AndroidMidiTransport(context)` (implements task 07's `MidiTransport`): `MidiManager` from `Context.MIDI_SERVICE`;
  `UnavailableTransport` + a plain error when `PackageManager.FEATURE_MIDI` is missing or the manager is null (Swift
  counterpart `../FM-SWITCH/App/Model/UnavailableTransport.swift`). `ports()` = `getDevices()` (**not**
  `getDevicesForTransport`, API 33), `TYPE_USB`, ≥ 1 input and ≥ 1 output port; `PortDescriptor.names` =
  `PROPERTY_NAME`, `PROPERTY_PRODUCT`, `PROPERTY_MANUFACTURER` and every `PortInfo.getName()`; VID:PID from
  `PROPERTY_USB_DEVICE`. **Only `PortMatching.isProbeable` devices are ever opened.** `open()` = `openDevice` (callback →
  `suspendCancellableCoroutine`), `openInputPort(0)` to send, `openOutputPort(0)` + `connect(receiver)` to receive (AOSP
  direction rule). `send` = `MidiInputPort.send(bytes, 0, n)` (AOSP `MidiReceiver.java:89-106` splits > 1015). The
  receiver's `onSend` copies its bytes into a `SysexFramer` feeding the `SysexInbox` (task 03). `changes()` =
  `registerDeviceCallback` as a `callbackFlow`. `isLost` = `onDeviceRemoved` for this device id **or** the id no longer in
  `getDevices()` (both, because which fires is UNCHECKED). Every device callback is logged to Logcat tag `FM1Midi` and
  exposed as a `deviceLog: Flow<String>` (or a `(String) -> Unit` hook) — task 13 appends it to `DeviceController`'s log
  so a real install's "Show the details" records the reboot (`DeviceController` is task 12's and may not exist yet).
- `HttpFetch`: `HttpURLConnection` inside `runInterruptible(ioDispatcher) { … }` (a blocking read only stops on
  interrupt), connect/read timeouts 15 s, throws task 08's `BadStatus(code)` for non-2xx — no new HTTP dependency.
- `DataStoreAppSettings`: task 08's `AppSettings` over DataStore Preferences.
- Behaviour against a real FM-1 is the UAT's (Testing Strategy, Blocker record B1; U1–U3).

## Files
- app/src/main/kotlin/dev/drewr/fmswitch/midi/AndroidMidiTransport.kt
- app/src/main/kotlin/dev/drewr/fmswitch/midi/UnavailableTransport.kt
- app/src/main/kotlin/dev/drewr/fmswitch/data/HttpFetch.kt
- app/src/main/kotlin/dev/drewr/fmswitch/data/DataStoreAppSettings.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`:app` compiles `:fm1` and `:model`; task 09 (`fm1/.../updater/`) and task 11 (`app/src/debug/`) may be mid-edit beside
this one: a compile, lint or format error in a path outside this task's `## Files` is a sibling — wait and re-run; only
errors in this task's four files are this task's red.

1. `./gradlew :app:assembleDebug :app:lintDebug spotlessCheck` — expected: `BUILD SUCCESSFUL`, no lint issue or warning
   in this task's files.
   red when: the adapters don't compile against the `:fm1`/`:model` contracts, or lint flags an API above minSdk 26.
2. `grep -n 'getDevicesForTransport' app/src/main/kotlin/dev/drewr/fmswitch/midi/AndroidMidiTransport.kt` — expected:
   no output.
   red when: an API-33 call is used on a minSdk-26 app.
   control: `printf 'midiManager.getDevicesForTransport(t)\n' | grep -n 'getDevicesForTransport'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
3. `grep -n 'isProbeable' app/src/main/kotlin/dev/drewr/fmswitch/midi/AndroidMidiTransport.kt; grep -n 'openDevice' app/src/main/kotlin/dev/drewr/fmswitch/midi/AndroidMidiTransport.kt`
   — expected: at least one `isProbeable` line, and every `openDevice` call sits behind it (quote both in your report).
   red when: a device is opened without the port-matching check.
   at base: none — written by this task.
4. `grep -nE '"FM1Midi"|PROPERTY_USB_DEVICE|PROPERTY_PRODUCT|PROPERTY_MANUFACTURER|onDeviceRemoved' app/src/main/kotlin/dev/drewr/fmswitch/midi/AndroidMidiTransport.kt`
   — expected: a hit for each of the five patterns.
   red when: the Logcat tag, a name source, the USB id or the removal signal is missing.
   at base: none — written by this task.
5. `grep -n 'runInterruptible' app/src/main/kotlin/dev/drewr/fmswitch/data/HttpFetch.kt; grep -rnE 'okhttp|ktor|retrofit' app/src/main/kotlin/dev/drewr/fmswitch/data/`
   — expected: one or more `runInterruptible` lines, then no output.
   red when: the blocking read can't be cancelled, or a new HTTP library is used.
   control: `printf 'import okhttp3.OkHttpClient\n' | grep -nE 'okhttp|ktor|retrofit'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
