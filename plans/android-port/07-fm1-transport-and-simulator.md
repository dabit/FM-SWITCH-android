status: pending
# 07 — `:fm1` transport contracts, port matching and the simulated FM-1

Depends on: 05

## Why
The updater, the Android MIDI adapter and the probe all talk through one transport contract, SysEx may only ever reach
ports that look like an FM-1, and a simulated FM-1 is required so the app can be built and tested without hardware.
Rests on: Protocol source; Scope.

## Description
Plan step 5. Read research.md §2.1 and §2.11 (the simulator's behaviour list) first.

**Getting FM1Kit:** clone it **outside the repository** and check the commit:
`git clone --depth 1 --branch 0.4.5 https://github.com/ocean/FM1Kit "$SCRATCH/FM1Kit" && git -C "$SCRATCH/FM1Kit" rev-parse HEAD`
→ `88b609cd8c631b884d977b7954cb0b21351f2f04`. Paths are under `Sources/FM1Kit/` unless they start with `Tests/`.

**Ports:** `Transport/MIDITransport.swift:5-78` (shapes: `MIDIPortDescriptor` at `:5`, the connection contract ending
with `close()` at `:76-77`), `Updater/FM1Updater.swift:23-27` (the ten probe fragments) and `:48-51` (`isProbeable`),
`Transport/SimulatedFM1.swift` (`Options` `:20`, `generation` `:98`, `loaderIdentity` `:119`, `prepare(for:)` `:150`,
`handle` `:189`, `exchange` `:312`, `reboot(into:)` `:331`), `Transport/SimulatedFM1+Transport.swift`,
`Transport/SimulatedFM1+Commands.swift:7-27` (capability and arm only). **Not ported:** `SimulatedFM1+Library.swift`,
`SimulatedFM1+Bootloader.swift`, voices (`+Commands.swift:30` onwards), project slots, workspace, sample bank.

- `PortDescriptor(id, names: List<String>, serialNumber, usbVendorId, usbProductId)` — `names` holds every string
  Android may expose (device name/product/manufacturer, port names); which one Android uses for the FM-1 is UNCHECKED.
  `PortMatching.isProbeable` = any name, lower-cased, contains one of the ten fragments, **or** VID:PID is `4c4a:c755` /
  `4d4a:4155`.
- `SimulatedFm1(options, scope: CoroutineScope)`: every behaviour in research §2.11 with injectable timings; one port
  named `FM-1 Midi (simulated)`; a generation counter kills connections on reboot; loader `ota-FM-1_9` or
  `ota-FM1C_<prefix>`; `prepare(package)`; `currentIdentity`. `exchange` compares the host's response
  **byte-for-byte** (`contentEquals`) and stops on mismatch — this is what makes a green install prove the encoders.
  All waits are `delay` on the injected scope (virtual-time rule).
- `MidiTransport`: `suspend ports()`, `suspend open(port)`, `changes(): Flow<Unit>`. `MidiConnection`: `isLost`,
  `send`, `suspend receive(timeout)`, `drain`, `close`.
- Test double `ScriptedTransport` — port of FM1Kit `Tests/FM1KitTests/PackageCheckTests.swift:120-170` (one scripted
  connection on one port named `FM-1 Midi`; records sends). Task 09 adds a multi-port fake beside it; keep this one
  single-port as the source is.

**Tests** — `### :fm1 — step 5` in `PLAN.md`'s Testing Strategy. Method names:
- `PortMatchingTest`: `probesOnlyMatchingPorts` (names `Felucca Update`, `FM-1`, `USB-MIDI`, `M-VAVE SINCO` match;
  `Arturia KeyStep`, `IAC Bus` don't — reddened by a case-sensitive `contains`) and `probesByUsbIdWhateverTheName` (a
  device named `USB Audio` with VID:PID `4c4a:c755` matches — reddened by ignoring VID:PID); the plan's
  `probesOnlyMatchingPorts` "first method / second method", named.
- `SimulatedFm1Test`: `simulatorAnswersTheHandshake`, `connectionsDieOnReboot`, `armedLoaderIsNamedAfterTheBuild`.

## Files
- fm1/src/main/kotlin/dev/drewr/fm1/transport/MidiTransport.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/MidiConnection.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/PortDescriptor.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/PortMatching.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/ScriptedTransport.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/PortMatchingTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/SimulatedFm1Test.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`R=fm1/build/test-results/test`. Task 08 runs beside this one and writes only under `model/`.

1. `./gradlew :fm1:test --rerun` — expected: `BUILD SUCCESSFUL` (tasks 03 and 05 tests stay green).
   red when: any `:fm1` test fails or the module warns.
2. `comm -23 <(printf '%s\n' probesOnlyMatchingPorts probesByUsbIdWhateverTheName simulatorAnswersTheHandshake connectionsDieOnReboot armedLoaderIsNamedAfterTheBuild | sort -u) <(cat $R/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u)`
   — expected: no output.
   red when: a step-5 case is missing.
   control: add `zzNoSuchCase` to the list → prints `zzNoSuchCase`.
   at base: none — written by this task.
3. `grep -ho 'failures="[0-9]*"' $R/TEST-*.xml | sort -u` — expected: only `failures="0"`.
   red when: any class recorded a failure.
   at base: none — written by this task.
4. `grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.' fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/PortMatching.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/MidiTransport.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/MidiConnection.kt`
   — expected: no output.
   red when: the simulator waits in real time.
   control: `printf 'Thread.sleep(4)\n' | grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
5. `for f in fm-1 fm1 felucca ota composite usb-midi simulated sloop clipper sinco; do grep -q "\"$f\"" fm1/src/main/kotlin/dev/drewr/fm1/transport/PortMatching.kt || echo "MISSING $f"; done`
   — expected: no output (all ten fragments of FM1Kit `FM1Updater.swift:23-27`).
   red when: a fragment is dropped or misspelt.
   control: add `zzfrag` to the loop → prints `MISSING zzfrag`.
   at base: none — written by this task.
6. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/transport/PortMatching.kt`, `isProbeable`: drop the `lowercase()`;
   `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.transport.PortMatchingTest'` → `FAILED` on
   `probesOnlyMatchingPorts`; revert, re-run → green.
   red when: case-sensitive matching passes.
7. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/transport/SimulatedFm1.kt`, `reboot`: don't bump the generation;
   `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.transport.SimulatedFm1Test'` → `FAILED` on
   `connectionsDieOnReboot`; revert, re-run → green.
   red when: connections survive a simulated reboot unnoticed.
8. Mutation — `SimulatedFm1.kt`, the response compare in `exchange`: replace `contentEquals` with `==`; run step 7's
   command → `FAILED` on `armedLoaderIsNamedAfterTheBuild` (the staging exchange stops, no armed loader appears);
   revert, re-run → green.
   red when: the simulator accepts responses without comparing their bytes.
