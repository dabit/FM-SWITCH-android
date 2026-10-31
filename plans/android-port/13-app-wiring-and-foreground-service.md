status: done
# 13 — App wiring and the foreground service

Depends on: 04, 10, 12

## Why
The screens need one long-lived app model (settings, catalogue, library, history, device controller) that outlives any
screen, and an install must keep running when the user leaves the app — a `connectedDevice` foreground service with a
wake lock, with loader resume as the safety net. Rests on: Foreground service type; Platform; Scope.

## Description
Plan step 11, plus feeding app state into task 04's UI root (this is why it depends on 04: it edits `MainActivity.kt`
and `ui/navigation/AppRoot.kt`, which task 04 wrote; nothing else here needs 04).

- `AppModel` created once in `FmSwitchApplication` (manual DI; keep its `ProbeHooks.start(this)` call): `DataStoreAppSettings`
  (task 10), `CatalogueStore` with `PublishedList(HttpFetch)` (task 08/10), `FirmwareLibrary` reading the asset
  `firmware/FM-1_015.fwsc`, `FlashHistory`, and the `DeviceController` (task 12) with an application `CoroutineScope`
  (`SupervisorJob() + Dispatchers.Main.immediate`), `ioDispatcher = Dispatchers.IO`, and a `transportFactory` giving
  `AndroidMidiTransport` (or `UnavailableTransport`) for "FM-1 over USB" and task 07's `SimulatedFm1` for "Simulated FM-1"
  per settings. Append `AndroidMidiTransport`'s device log (task 10) to the controller's log. `requestedPage` as
  `StateFlow<RequestedPage?>`, cleared after use.
- UI hand-off: provide the `AppModel` to composables (a `LocalAppModel` CompositionLocal declared in `AppModel.kt` and provided in `MainActivity`), and in
  `AppRoot.kt` feed the theme's appearance from settings, show the welcome notice until it has been acknowledged (task 08's
  `acknowledgedNoticeVersion`), and show `InstallingOverlay` while a job is running. Don't change the route placeholders' signatures.
- `FlashService`: declared `<service android:name=".service.FlashService" android:exported="false"
  android:foregroundServiceType="connectedDevice"/>` (API 34+ rejects a `startForeground` type the manifest doesn't
  declare); started when a job starts, stopped when it ends; `startForeground` with type `connectedDevice`;
  notification "Installing <name>… keep it plugged in" on its own channel; partial `WakeLock` with a 20-min timeout,
  released on stop. Manifest adds `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`,
  `POST_NOTIFICATIONS`, and **`CHANGE_NETWORK_STATE`** — the API 34+ runtime prerequisite for `connectedDevice` that needs
  no prompt (developer.android.com/develop/background-work/services/fgs/service-types, read 2026-10-30). Wrap
  `startForeground` in `try/catch` for `SecurityException`/`ForegroundServiceStartNotAllowedException`: on failure, log a
  line to the job log ("couldn't start the service…") and carry on.
  VERIFY-AT-EXECUTION: whether to request `POST_NOTIFICATIONS` at the first install on API 33+ (the service runs without it;
  only the notification is hidden) — record the choice in your report.
- Notification strings in `app/src/main/res/values/strings_service.xml`. This is the **only** later task that edits
  `app/src/main/AndroidManifest.xml`.
- FGS behaviour on a phone is UAT U4 (Blocker record B2). Optionally, if you can create and boot an AVD from the installed
  `android-37` system image, run `scripts/check.sh device` and record the result; if you can't boot one, say so.

## Files
Every file this task creates is one of these paths; nothing else.
- app/src/main/kotlin/dev/drewr/fmswitch/FmSwitchApplication.kt
- app/src/main/kotlin/dev/drewr/fmswitch/AppModel.kt
- app/src/main/kotlin/dev/drewr/fmswitch/MainActivity.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/navigation/AppRoot.kt
- app/src/main/kotlin/dev/drewr/fmswitch/service/FlashService.kt
- app/src/main/AndroidManifest.xml
- app/src/main/res/values/strings_service.xml

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
Task 11 (`app/src/debug/`) has no dependency path to this one and may still be finishing: a compile, lint or format
error in a path outside this task's `## Files` is that sibling — wait and re-run; only errors in this task's paths are
this task's red.

1. `env -u JAVA_HOME -u ANDROID_HOME scripts/check.sh` — expected: ends `Checks passed: lint build test.`, exit 0.
   red when: the wiring breaks the build, lint, formatting or any existing test.
2. `"$HOME/Android/Sdk/build-tools/37.0.0/aapt2" dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk | grep -E 'FOREGROUND_SERVICE|WAKE_LOCK|POST_NOTIFICATIONS|CHANGE_NETWORK_STATE|FlashService|foregroundServiceType'`
   — expected: lines for `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`,
   `CHANGE_NETWORK_STATE`, the `.service.FlashService` component and a `foregroundServiceType` attribute (value `0x10`,
   `connectedDevice`).
   red when: a permission or the declared service type is missing from the merged manifest.
   at base: none — the APK is written by task 01 and this task.
3. `grep -nE 'FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE|ForegroundServiceStartNotAllowedException|SecurityException|PARTIAL_WAKE_LOCK' app/src/main/kotlin/dev/drewr/fmswitch/service/FlashService.kt`
   — expected: a hit for each of the four patterns.
   red when: the service starts with another type, doesn't guard `startForeground`, or holds no wake lock.
   at base: none — written by this task.
4. `grep -n 'ProbeHooks.start' app/src/main/kotlin/dev/drewr/fmswitch/FmSwitchApplication.kt` — expected: one line (the
   probe hook survives the extension).
   red when: the debug probe stops recording from launch.
   at base: none — written by task 01.
5. `grep -rnE 'Dispatchers\.(Main|IO)' app/src/main/kotlin/dev/drewr/fmswitch/AppModel.kt app/src/main/kotlin/dev/drewr/fmswitch/FmSwitchApplication.kt`
   — expected: at least one hit (production dispatchers are injected **here**, the only place they belong).
   red when: production passes no real dispatcher (the app would run on a test dispatcher or none).
   at base: none — written by this task.
