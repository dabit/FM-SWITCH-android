status: done
# 11 — Hardware probe screen (debug builds only)

Depends on: 07

## Why
Which names Android gives the FM-1, whether its reboot into the loader shows as remove + add, whether a permission
prompt appears and how `onSend` fragments replies are all unchecked; a debug-only probe lets a human record them on a
real phone (UAT U1) without risking the device. Rests on: Platform; Scope.

## Description
Plan step 10. Debug source set only; release builds carry none of it.

Depends on task 07, not just 03 as the plan's step 10 says: the probe decides which devices get a "Send handshake" button
with task 07's `PortMatching.isProbeable` and `PortDescriptor`, so U1 exercises the same matching rule production uses
instead of a copy of it. It also uses task 03's `Fm1Message.HANDSHAKE_QUERY`, `SysexFramer` and `Fm1Identity`.

- Hook-up: task 01 created `app/src/debug/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt` as a no-op. Fill it in:
  `start(context)` starts `MidiProbeRecorder` (recording from app launch); `probeIntent(context)` returns the intent for the
  probe activity (task 14's Settings row "USB MIDI probe" uses it). Don't touch the release `ProbeHooks.kt` or
  `FmSwitchApplication.kt`.
- `app/src/debug/AndroidManifest.xml`: a second launcher activity labelled "FM-1 probe" (label in
  `app/src/debug/res/values/strings_probe.xml`).
- Records, with monotonic timestamps: every `MidiDeviceInfo` at launch and on each add/remove — id, type, every
  `PROPERTY_*` value incl. USB VID:PID, port counts and names. For each device matching **either** a fragment or the
  VID:PID (i.e. `isProbeable`), a button "Send handshake" that opens it, sends `HANDSHAKE_QUERY` once, and logs the size
  of **every** raw `onSend` chunk (fragmentation, premise P4(d)), the framed messages and the parsed `Fm1Identity`.
  Whether any permission dialog appeared is noted by the human. It **never** sends UPGRADE and never sends to a
  non-matching device.
- Output: on-screen scrolling log **and** Logcat tag `FM1Probe` **and** a "Copy log" button (`ClipboardManager`).
  On-screen + copy are primary: the phone's one USB port holds the FM-1, so `adb logcat` over USB isn't available during
  a test.
- The human run is UAT U1 (task 17).

## Files
- app/src/debug/kotlin/dev/drewr/fmswitch/probe/ProbeHooks.kt
- app/src/debug/kotlin/dev/drewr/fmswitch/probe/MidiProbeRecorder.kt
- app/src/debug/kotlin/dev/drewr/fmswitch/probe/ProbeScreen.kt
- app/src/debug/kotlin/dev/drewr/fmswitch/probe/ProbeActivity.kt
- app/src/debug/AndroidManifest.xml
- app/src/debug/res/values/strings_probe.xml

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
Tasks 09 (`fm1/.../updater/`) and 10 (`app/src/main/.../midi|data/`) may be mid-edit beside this one: a compile, lint or
format error in a path outside this task's `## Files` is a sibling — wait and re-run.

1. `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug spotlessCheck` — expected: `BUILD SUCCESSFUL`.
   red when: the debug probe or the release no-op fails to build, or lint flags this task's files.
2. Control first: `unzip -p app/build/outputs/apk/debug/app-debug.apk 'classes*.dex' | grep -ac 'dev/drewr/fmswitch/probe/MidiProbeRecorder'`
   — expected: ≥ `1`. Then the arm: `unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -ac 'dev/drewr/fmswitch/probe/MidiProbeRecorder'`
   — expected: `0`.
   red when: the probe ships in release builds (or the instrument can't see it in debug).
   at base: none — both APKs are written by tasks 01/11.
3. `grep -rnE 'UPGRADE' app/src/debug/kotlin/dev/drewr/fmswitch/probe/` — expected: no output (the probe never sends
   UPGRADE).
   red when: the probe can start an install.
   control: `grep -c 'upgradeCommand' fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Message.kt` → ≥ `1` (task 03 named the UPGRADE builder `upgradeCommand`).
   at base: none — written by this task.
   compliant hits: none (a comment saying "never UPGRADE" would match — word it otherwise).
4. `grep -rn 'isProbeable' app/src/debug/kotlin/dev/drewr/fmswitch/probe/; grep -rnE '"FM1Probe"|ClipboardManager|HANDSHAKE_QUERY' app/src/debug/kotlin/dev/drewr/fmswitch/probe/`
   — expected: ≥ 1 `isProbeable` line, then hits for all three of the second pattern.
   red when: the probe uses its own matching rule, lacks a Logcat tag or copy button, or sends something other than the
   handshake query.
   at base: none — written by this task.
5. `"$HOME/Android/Sdk/build-tools/37.0.0/aapt2" dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk | grep -c 'android.intent.category.LAUNCHER'`
   — expected: `2` (the app and "FM-1 probe"); the same command on `app/build/outputs/apk/release/app-release-unsigned.apk`
   → `1`.
   red when: the probe has no launcher entry in debug.
   at base: none — written by tasks 01/11.
