status: pending
# 03 — `:fm1` protocol, framing and inbox

Depends on: 01

## Why
The FM-1's SysEx protocol (7-bit packing, read requests and responses, identities, open-firmware capability) and the
byte-stream framing Android needs are the base every other `:fm1` piece and the probe build on; they are ported from
FM1Kit 0.4.5 rather than rewritten from the spec. Rests on: Protocol source; Scope.

## Description
Plan step 3. Read `PLAN.md` § Implementation Plan conventions (the FM1Kit clone, the **virtual-time rule**, the
**semantic-drift rule**) and research.md §2.2–2.4, §2.6, §2.12 first.

**Getting FM1Kit:** clone it **outside the repository** (your scratch dir) and check the commit:
`git clone --depth 1 --branch 0.4.5 https://github.com/ocean/FM1Kit "$SCRATCH/FM1Kit" && git -C "$SCRATCH/FM1Kit" rev-parse HEAD`
→ `88b609cd8c631b884d977b7954cb0b21351f2f04`. Nothing from the clone is committed. Paths below are under
`Sources/FM1Kit/` unless they start with `Tests/`.

**Ports:** `Protocol/SevenBit.swift:11-50` (`pack`/`unpack`), `Protocol/FM1Message.swift` (all: constants `:12-66`,
`checksum` `:89`, `ReadRequest` with `init?(sysex:)` `:149`, `encoded()` `:173`, `response(with:)` `:183`,
`finishResponse()` `:190`, `body(…)` `:228-239`), `Protocol/FM1Identity.swift` (`init?(text:)` `:46`, the last-`_` split
`:48`, `isOpenFirmware` `:112-117`, `init?(handshakeReply:)` `:139`), `Protocol/FM1Capability.swift` (`FM1BuildID` `:5`,
`init?(hex:)` `:17`, capability parse `:99-135`, arming), `Transport/MIDITransport.swift:107-218` (`SysexInbox`).
**New:** `SysexFramer` (byte stream → `F0…F7` messages), replacing `Protocol/UMPSysex7.swift` (CoreMIDI's UMP form —
**not** ported).

Each ported file starts with
`// Ported from FM1Kit 0.4.5 <path> — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt`.

- Bytes are `ByteArray`/`UByte`-safe: mask with `and 0xFF` on every widen. Byte-array equality is `contentEquals`,
  never `==` (Kotlin `==` on `ByteArray` is reference equality).
- `SevenBit`: LSB-first bit stream, **not** "MSB byte + 7" (research §2.2).
- `Fm1Identity`: text parse splits at the **last** `_`; `ota-FM1C` armed form; equality and serialization by **text**
  (`FM-1_015` ≠ `FM-1_15`); `officialV15` built from text. `isOpenFirmware` = FM1Kit's predicate (8,000,000…8,000,999
  or `FM1_COMMUNITY`) — **not** the app's `OpenFirmware.isOpen` regex (Retro-only, not ported).
- `SysexFramer`: feed arbitrary chunks; emit complete messages; ignore data before an `F0`; a new `F0` discards an
  unfinished message; bytes `F8–FF` inside a message are skipped; any other status byte (≥ `0x80`, not `F7`) aborts the
  message; drop a message over 64 KiB. Copies its input (Android's buffer is valid only during the call). Rules from
  FM1Kit `Tests/FM1KitTests/EdgeCaseTests.swift:89-127` (the UMP edge cases, restated for a byte stream).
- `SysexInbox`: single reader, `Channel(256, BufferOverflow.DROP_OLDEST)`, `suspend fun next(timeout): ByteArray?` (null
  on timeout or closed), `drain()`, `close()` wakes the reader with null and later sends are ignored. Timing only through
  coroutine `withTimeoutOrNull`/`receive` — no `Thread.sleep`, wall clock, `runBlocking` or hard-coded `Dispatchers.*`.

**Tests** — every case under `### :fm1 — step 3` in `PLAN.md`'s Testing Strategy, one method each, with that section's
"reddened by" mutation in mind. Byte vectors V1–V3 are in `PLAN.md`'s Appendix, verbatim. Method names:
- `SevenBitTest`: `packsLeastSignificantBitFirst` (FM1Kit `Tests/FM1KitTests/ProtocolTests.swift:7-8`), `roundTripsEveryLength`.
- `MessageTest`: `handshakeQueryUnpacksToTheIdentityHeader`, `sendsTheDocumentedCommands` (`ProtocolTests.swift:119-120`),
  `readRequestEncodesTheDocumentedBytes` (V1), `responseEncodesTheDocumentedBytes` (V2), `finishResponseIsSuccessAndANul`
  (V3), `readRequestRoundTrips`, `readRequestWithBadChecksumIsIgnored`, `responseDeclaresTheFullLength`
  (`ProtocolTests.swift:27-49`), `identityRoundTripsAndRecognisesTheLoader`, `splitsAtTheLastUnderscore`,
  `rejectsUnreadableIdentities` (inputs at `Tests/FM1KitTests/EdgeCaseTests.swift:35`; the positive `FM-1_5` first),
  `equalityIsOnTheText`, `openFirmwareIsFm1KitsPredicate`, `armedLoaderParses`, `capabilityLayoutAndCommandsRoundTrip`.
- `RecordedDeviceTest`: `readsARealDevicesHandshakeReply`, `rejectsAReplyWithABadChecksum` (the recorded 41-byte reply
  is at `ProtocolTests.swift:101-104`; cases `:106-116`).
- `SysexFramerTest`: `framerReassemblesAcrossChunks`, `framerSkipsRealTimeBytes`, `framerAbortsOnOtherStatus`,
  `newStartDiscardsUnfinished`, `dataWithoutStartIsIgnored`, `dropsOver64KiB`.
- `SysexInboxTest` (the plan's `inboxOrderTimeoutWakeCloseDrainCancelCapacity`, eight methods porting
  `EdgeCaseTests.swift:138-171,293-320`, under `runTest`): `deliversInOrder`, `nextTimesOutAfterOneSecond` (asserts
  `currentTime == 1000`), `aWaiterWakesOnArrival`, `closeWakesTheWaiterAndIgnoresLaterSends`, `drainEmptiesTheInbox`,
  `cancellingEndsTheWait`, `aWaitCancelledBeforeItStartsEndsAtOnce`, `keepsTheNewest256`.

## Files
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/SevenBit.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Message.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/ReadRequest.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Identity.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/Fm1Capability.kt
- fm1/src/main/kotlin/dev/drewr/fm1/protocol/BuildId.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/SysexFramer.kt
- fm1/src/main/kotlin/dev/drewr/fm1/transport/SysexInbox.kt
- fm1/src/test/kotlin/dev/drewr/fm1/protocol/SevenBitTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/protocol/MessageTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/protocol/RecordedDeviceTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/SysexFramerTest.kt
- fm1/src/test/kotlin/dev/drewr/fm1/transport/SysexInboxTest.kt

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`R=fm1/build/test-results/test`. No sibling task writes under `fm1/` while this one runs.

1. `./gradlew :fm1:test --rerun` — expected: `BUILD SUCCESSFUL`, no compiler warnings (warnings are errors).
   red when: any step-3 test fails, or the module doesn't compile.
2. `comm -23 <(printf '%s\n' packsLeastSignificantBitFirst roundTripsEveryLength handshakeQueryUnpacksToTheIdentityHeader sendsTheDocumentedCommands readRequestEncodesTheDocumentedBytes responseEncodesTheDocumentedBytes finishResponseIsSuccessAndANul readRequestRoundTrips readRequestWithBadChecksumIsIgnored responseDeclaresTheFullLength readsARealDevicesHandshakeReply rejectsAReplyWithABadChecksum identityRoundTripsAndRecognisesTheLoader splitsAtTheLastUnderscore rejectsUnreadableIdentities equalityIsOnTheText openFirmwareIsFm1KitsPredicate armedLoaderParses capabilityLayoutAndCommandsRoundTrip framerReassemblesAcrossChunks framerSkipsRealTimeBytes framerAbortsOnOtherStatus newStartDiscardsUnfinished dataWithoutStartIsIgnored dropsOver64KiB deliversInOrder nextTimesOutAfterOneSecond aWaiterWakesOnArrival closeWakesTheWaiterAndIgnoresLaterSends drainEmptiesTheInbox cancellingEndsTheWait aWaitCancelledBeforeItStartsEndsAtOnce keepsTheNewest256 | sort -u) <(cat $R/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u)`
   — expected: no output (all 33 methods ran).
   red when: a case is missing or renamed.
   control: the same command with `zzNoSuchCase` added to the list → prints `zzNoSuchCase`.
   at base: none — `fm1/` is written by tasks 01 and 03.
3. `grep -ho 'failures="[0-9]*"' $R/TEST-*.xml | sort -u` — expected: only `failures="0"`.
   red when: any class recorded a failure.
   at base: none — written by this task.
4. `grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.' fm1/src/main/kotlin/dev/drewr/fm1/protocol/*.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/SysexFramer.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/SysexInbox.kt`
   — expected: no output (virtual-time rule).
   red when: a wait or timing decision escapes the injected scope.
   control: `printf 'Thread.sleep(1)\nDispatchers.IO\n' | grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|runBlocking|Dispatchers\.'` → 2 lines.
   at base: none — written by this task.
   compliant hits: none (a KDoc naming a banned call would match — word it otherwise).
5. `grep -L 'Ported from FM1Kit 0.4.5' fm1/src/main/kotlin/dev/drewr/fm1/protocol/*.kt fm1/src/main/kotlin/dev/drewr/fm1/transport/SysexInbox.kt`
   — expected: no output (every ported file carries the notice line; `SysexFramer.kt` is new and is excluded).
   red when: a ported file lacks FM1Kit's MIT attribution.
   control: `grep -L 'Ported from FM1Kit 0.4.5' fm1/build.gradle.kts` → prints `fm1/build.gradle.kts`.
   at base: none — written by this task.
6. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/protocol/SevenBit.kt`, function `SevenBit.pack`: switch to MSB-first
   packing; run `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.protocol.SevenBitTest'` → `FAILED` on
   `packsLeastSignificantBitFirst`; revert and re-run → green.
   red when: the test stays green with the packing inverted.
7. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/protocol/ReadRequest.kt`, the body builder: write the address
   big-endian; run `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.protocol.MessageTest'` → `FAILED` on
   `readRequestEncodesTheDocumentedBytes` (while `readRequestRoundTrips` may stay green — that is the point of V1);
   revert and re-run → green.
   red when: no test catches a symmetric layout error.
8. Mutation — `fm1/src/main/kotlin/dev/drewr/fm1/transport/SysexInbox.kt`: `BufferOverflow.DROP_OLDEST` →
   `DROP_LATEST`; run `./gradlew :fm1:test --rerun --tests 'dev.drewr.fm1.transport.SysexInboxTest'` → `FAILED` on
   `keepsTheNewest256`; revert and re-run → green.
   red when: the capacity policy is unguarded.
