# Research: Port FM-SWITCH to Android

Researcher run, 2026-10-30. Read-only on both repos. Inputs: `AGENTS.md`, `plans/android-port/PLAN.md`
(Decisions: Standard look only, minSdk 26, sideload/GitHub releases; deferred Retro, tip jar,
alternate icon, tablet two-pane, backups, factory-voice restore, Mac download mode).

## Citation conventions

- `../FM-SWITCH/<path>:<line>` — the source app at `67ee8c24078d8ae23e4331c9e830e17f174f598f`.
- `FM1Kit <path>:<line>` — **FM1Kit tag `0.4.5`, commit `88b609cd8c631b884d977b7954cb0b21351f2f04`**,
  path relative to `Sources/FM1Kit/` unless it starts with `Tests/`, `README.md`, `HARDWARE_TESTING.md`.
  Browse at `https://github.com/ocean/FM1Kit/blob/0.4.5/Sources/FM1Kit/<path>#L<line>`. This is the
  exact revision `../FM-SWITCH/Package.resolved:8-9` pins. It was cloned to a session scratchpad
  only to read; **this file inlines every FM1Kit fact the port needs** so the Planner doesn't need
  the clone. To re-read: `git clone https://github.com/ocean/FM1Kit && git -C FM1Kit checkout 0.4.5`.
- `AOSP <file>:<line>` — `platform/frameworks/base` `refs/heads/main`, `media/java/android/media/midi/<file>`,
  fetched 2026-10-30 via `android.googlesource.com/...?format=TEXT`.

---

## §0 Premises

- **P1 CONFIRMED.** `ls /home/dabit/git/FM1Kit ~/code/FM1Kit` → both "No such file or directory";
  `find / -maxdepth 5 -type d -name 'FM1Kit*'` → nothing.
- **P2 CONFIRMED.** `../FM-SWITCH/project.yml:12-14` → `url: https://github.com/ocean/FM1Kit.git`,
  `minorVersion: 0.4.5`; `../FM-SWITCH/Package.resolved:6-10` pins `0.4.5` at revision
  `88b609cd8c631b884d977b7954cb0b21351f2f04`; `../FM-SWITCH/Package.swift:12` `.upToNextMinor(from: "0.4.5")`.
  `git clone https://github.com/ocean/FM1Kit.git` succeeded unauthenticated; tag `0.4.5` = `88b609c`
  (2026-10-28); tags 0.1.0, 0.2.0, 0.3.0, 0.4.0, 0.4.1 also present. **Licence: MIT**, "Copyright (c)
  2026 Drew Robinson" (FM1Kit `LICENSE:1-3`, `README.md:257-259`). Porting its code to Kotlin is
  permitted provided the copyright and permission notice are included with the port. One file carries
  a **second** MIT notice that must travel with any port of it: FM1Kit `Package/ApplicationArea.swift:1-19`
  (Julius Vitkauskas, from M-Flasher's `formats.rs`). FM1Kit states it "contains no code from
  GPL-licensed installers" (FM1Kit `README.md:240-252`). `TODO.md` item 3 ("Create github.com/ocean/FM1Kit",
  `../FM-SWITCH/TODO.md:19-20`) is **stale**: item 5 says it was published and pinned on 2026-10-17
  (`../FM-SWITCH/TODO.md:23-25`).
- **P3 REFUTED** — PLAN.md "How flashing works" (`../FM-SWITCH/PLAN.md:14-56`) is not enough on its own.
  It omits, and only FM1Kit's code answers: (1) open-firmware capability `0x31` / arm `0x32`
  commands and their payload layouts; (2) the generic command framing whose checksum covers the
  payload only; (3) the 34-byte handshake reply layout (command `0x11`, checksum range); (4) every
  `UpdaterTiming` value; (5) that a finish request must have length 8, that the finish reply echoes
  flash type and address with length 8, and the 600 ms settle that re-answers a repeated finish;
  (6) the loader-identity rules for resume (stock loader always `ota-FM-1_015`; armed open loader
  `ota-FM1C_<16 hex>`) and which saved package can finish; (7) the JieLi container check (CRC-16,
  XOR key-stream decipher, part table) that both validates a package and defines the application
  area that write progress counts; (8) the out-of-order progress algorithm (`WriteProgress`); (9) the
  refusal heuristics (`bootloaderMismatch` / `packageRefused` from where staging stopped); (10) the
  `InstallKind` rules incl. `sameIdentity`; (11) refusing when more than one FM-1 answers; (12) the
  full probe-name fragment list; (13) the exact marker encoding (`char + i + 1`, `0x7D` unused);
  (14) `refusesV15OverOtherBootloaders` (app-area cipher with chip key `0x980F`, marker strings);
  (15) the post-install build check for open firmware. **Moot in practice:** FM1Kit is reachable and
  MIT, so it is the primary protocol source and everything below is taken from it. Voice restore is
  deferred and not covered here beyond its constants.
- **P4 SPLIT.**
  - CONFIRMED from AOSP `package.html` (MIDI package docs): feature `android.software.midi` (manifest
    `<uses-feature android:name="android.software.midi" android:required="true"/>`); hot-plug via
    `MidiManager.registerDeviceCallback` with `onDeviceAdded`/`onDeviceRemoved` ("notification when MIDI
    devices are plugged in or unplugged"); "input"/"output" are from the device's point of view (send to
    the device's **input** port, receive from its **output** port); "transmission of arbitrary length data
    for SysEx". Transports listed: USB, software routing, BTLE. API added in level 23.
  - CONFIRMED from AOSP sources: `MidiInputPort.onSend` throws if `count > MidiPortImpl.MAX_PACKET_DATA_SIZE`
    (= 1024 − 9 = **1015**) (AOSP `MidiInputPort.java:78-84`, `MidiPortImpl.java:39,44-54`), but
    `MidiReceiver.send(...)` (which callers use) "will get split into multiple calls to onSend if count
    exceeds getMaxMessageSize" (AOSP `MidiReceiver.java:89-106`). `MidiReceiver.onSend` makes **no
    promise of message boundaries** and the array is only valid during the call (AOSP
    `MidiReceiver.java:44-61`) → the Android transport needs a **byte-stream `F0…F7` framer**, not a port
    of FM1Kit's `UMPSysex7` (that's CoreMIDI's UMP form). `getDevicesForTransport(...)` exists
    (AOSP `MidiManager.java:324-333`) but is API 33 per the developer docs (level not re-verified from source); with minSdk 26 use `getDevices()` +
    `registerDeviceCallback(callback, handler)`. `MidiDeviceInfo` exposes `PROPERTY_NAME`, `PROPERTY_MANUFACTURER`,
    `PROPERTY_PRODUCT`, `PROPERTY_SERIAL_NUMBER`, `PROPERTY_USB_DEVICE` (a `UsbDevice`, so VID:PID),
    `getType()==TYPE_USB`, `getId()`, `getPorts()`→`PortInfo.getName()` (AOSP `MidiDeviceInfo.java:48,170-210,278-294,354-392`).
  - Path check (payload must traverse): the largest host→device message is a 512-byte read response:
    6 header + 8 fields + 512 data + 1 checksum = 527 bytes unpacked → ⌈527×8/7⌉ = 603 septets →
    **605 bytes framed**, under 1015, and `send()` splits anyway. Device→host messages are small:
    handshake reply 34 unpacked → 39 septets → 41 bytes framed; read request 15 unpacked → 18 septets
    → 20 framed; capability reply 54 unpacked → 62 septets → 64 framed.
  - **UNVERIFIED-EXTERNAL** (needs an Android phone + FM-1): (a) whether the reboot into the loader
    (`4c4a:c755` → `4d4a:4155`) fires `onDeviceRemoved` then `onDeviceAdded` with a new `MidiDeviceInfo`;
    (b) the exact `PROPERTY_NAME` / port names Android builds for the FM-1 (CoreMIDI saw `FM-1`, `Felucca`,
    `Felucca Update`, `FM1 Community`, `FM-1 ESTAMPIE` — FM1Kit `HARDWARE_TESTING.md:343,362,367,420,490`;
    Android may build names from manufacturer + product); (c) whether any USB permission prompt appears for a
    class-compliant device opened through `MidiManager` (no official doc found; community reports say no
    `UsbManager` permission is needed for class-compliant devices — [android-midi group](https://groups.google.com/g/android-midi/c/AL1afmM7I9g));
    (d) whether `onSend` fragments a 600-byte SysEx (assume yes; the framer handles both). Settle with a
    probe: a bare Compose activity that lists `MidiManager.getDevices()` with every property and port name,
    sends the handshake to matching ports, and logs device add/remove while the FM-1 goes through an install
    and a pulled cable (mirrors FM-SWITCH's Phase 0, `../FM-SWITCH/PLAN.md:177-183`).
- **P5 CONFIRMED.** `curl https://fm-1.drewr.dev/firmwares.json` → HTTP 200, `last-modified: Fri, 30 Oct 2026
  10:42:56 GMT`, `cache-control: max-age=600`, ETag present, 404,792 bytes. Top-level keys exactly
  `builtAt` (ISO-8601 `"2026-10-30T00:23:06Z"`), `entries`, `formatVersion` (**1**), `hubUpdated`,
  `problems` (empty) — matching `MergedCatalogue` (`../FM-SWITCH/App/Catalogue/CatalogueBuilder.swift:4-27`),
  `currentFormat = 1` (`:10`), gate `isReadable = (formatVersion ?? 1) <= currentFormat` (`:26`).
  270 entries; every entry has `id, name, beta, isOfficial, kinds, links, listing`; others optional
  (`packageURL` 210, `sha256` 182, `product` 153, `version` 187, `builds` 6, `sourceBuild` 16,
  `whatSource` 57 all `"gitHub"`, `loader` 1 — `bubba: "mvave"`). **`listing` is an Int bitmask**
  (hub=1, editor=2, resourceHub=4; observed 1–7). `packageOrigin` ∈ hub / githubRelease / projectPage /
  mvave / publisherManifest / absent. `sourceBuild` is the app's `SourceBuild` shape (`sha256, repo,
  commit, run`), not the hub's `provenance`. `otherRelease` and `build` never appear in the published
  file (the app fills them from live source checks). The official entry is `id: "mvave-official"`,
  `isOfficial: true`, `version: "V15"`, `sha256: db1642b2…`, `size: 699956`, `packageURL` on
  `yms-file-store.oss-cn-hongkong.aliyuncs.com`. An entry can have `builds` with `packageURL` set and no
  `build` (e.g. `fm1-editor-haunted-fm1`) → `needsBuildChoice` is true.
- **P6 CONFIRMED** as recorded: `AGENTS.md:64-66` and `../FM-SWITCH/PLAN.md:91-92,133-134` say Felucca's
  `fm1ota.js`/`fm1pkg.js` and fm1_open's `WebUpdate.js`/`I18n.js` are GPL-3.0. Not re-fetched from upstream
  this run. Irrelevant to the port's code because FM1Kit (MIT) is the source and states it contains no GPL code.

---

## 1. Related plans and docs

No past plans or architecture docs exist in this repo besides `plans/android-port/PLAN.md`
(`plans/` holds only it; `doc/` did not exist). FM-SWITCH has no `plans/` or `doc/` folder.

| Doc | What it covers for the port |
|---|---|
| `../FM-SWITCH/PLAN.md` | Protocol summary (`:14-56`), V15 bundling and hash (`:74-86`), Open identities, loaders, switching lines, factory voices (`:95-125`), risks (`:127-149`), original architecture sketch (`:151-173`), phases (`:175-191`). |
| `../FM-SWITCH/README.md` | Firmware list contract and `currentFormat` rule (`:42-69`), simulated FM-1 (`:93-100`), tests/CI (`:162-197`), staying alive (`:199-207`). |
| `../FM-SWITCH/REDESIGN.md` | Standard look: principles incl. **Plain words** (`:24-27`), safe by default (`:28-32`), motion (`:52-82`), feedback (`:91-97`), wayfinding (`:98-103`), confirm sparingly (`:104-106`), prototype-first (`:122-125`), colours (`:175-186`), screens (`:219-233`). |
| `../FM-SWITCH/TODO.md` | Hardware results summary (`:7-15`); item 3 stale (see P2). |
| `../FM-SWITCH/AGENTS.md` | iOS repo workflow (kata), not read in depth. |
| FM1Kit `README.md` | API tour, recovery, credits/licences (`:240-259`). |
| FM1Kit `FM1Kit.docc/InstallingFirmware.md` | Install flow, checks, open firmware, resume, testing with `SimulatedFM1` (`:1-202`). |
| FM1Kit `HARDWARE_TESTING.md` | Real-device measurements (`:340-411`, `:476-487`, `:685-715`). Key numbers in §2.9. |
| FM1Kit `CHANGELOG.md`, `llms.txt`, `llms-full.txt` | Not read in depth. |

---

## 2. The protocol spec, as the port needs it

### 2.1 USB and ports
- Normal firmware enumerates as USB `4c4a:c755`, the update loader as `4d4a:4155`, same MIDI port name family
  (`../FM-SWITCH/PLAN.md:16-17`). Real port names seen (CoreMIDI): `Felucca`, `FM-1`, `Felucca Update`
  (loader), `FM1 Community`, `FM-1 ESTAMPIE` (see P4).
- **Probe only ports whose lower-cased name contains** one of: `fm-1`, `fm1`, `felucca`, `ota`, `composite`,
  `usb-midi`, `simulated`, `sloop`, `clipper`, `sinco` (FM1Kit `Updater/FM1Updater.swift:23-27`, test
  `isProbeable` `:48-51`). This list is longer than AGENTS.md's summary (`AGENTS.md:42-43`).
  *Proposal:* on Android also accept a `UsbDevice` with VID:PID `4c4a:c755` or `4d4a:4155` (from
  `PROPERTY_USB_DEVICE`) — never send to a port matching neither.
- CoreMIDI pairs a source with a destination by USB entity, else by equal name (FM1Kit
  `Transport/CoreMIDITransport.swift:75-91`). On Android a `MidiDeviceInfo` already groups ports;
  pair output port 0 (receive) with input port 0 (send) of the same device (proposal; FM-1 has one of each —
  UNVERIFIED-EXTERNAL until the probe).
- Serial number: V15 and Open report the same per-unit serial; Felucca none (FM1Kit `Transport/MIDITransport.swift:10-14`).

### 2.2 7-bit packing (FM1Kit `Protocol/SevenBit.swift:11-50`)
The whole unpacked message is one bit stream, **LSB first**, cut into 7-bit groups; the last septet is
zero-padded. Not the common "MSB byte + 7" scheme.
```
pack:   acc |= byte << bits; bits += 8; while bits >= 7 { out(acc & 0x7F); acc >>= 7; bits -= 7 }; if bits>0 out(acc & 0x7F)
unpack: acc |= (s & 0x7F) << bits; bits += 7; while bits >= 8 { out(acc & 0xFF); acc >>= 8; bits -= 8 }   // trailing bits dropped
```
Test vectors (FM1Kit `Tests/FM1KitTests/ProtocolTests.swift:6-17`): `pack([0xFF]) == [0x7F,0x01]`;
`pack([0x00,0x59,0x30]) == [0x00,0x32,0x41,0x01]`; round-trip lengths 0…40 with `bytes[i] = i*37+11`;
every packed byte < 0x80.

### 2.3 Messages (FM1Kit `Protocol/FM1Message.swift`)
- **Handshake query** (raw, already framed): `F0 00 32 45 00 00 00 40 7F F7` (`:14`). Unpacks to start `00 59 11`.
- **Upgrade command** (raw): `F0 22 24 35 7F F7` (`:20`). Sent twice: to firmware to start staging, to the loader to start writing.
- Unpacked layout of every framed message: `00 59 | cmd | body_length:u24le | body… | checksum` (`:7-11`, `:35-57`).
  - `prefix = 00 59`; `headerLength = 6`; `fixedFieldsLength = 8` (flash type + address + length).
  - Offsets: command 2, bodyLength 3, flashType 6, address 7 (u32le), length 11 (u24le), data/checksum 14 (`:60-73`).
  - **checksum = `~(sum of bytes) & 0xFF`** (`:89-91`).
  - `frame(x) = F0 + pack(x) + F7`; `unframe` requires first F0 and last F7 (`:76-86`).
- **Commands:** read `0x30`; identity `0x11`; capability `0x31`; arm `0x32`; voice erase `0x21`,
  voice write `0x22`, acknowledgement `0x00`, voice area 5, voice write 512 (deferred) (`:37-53`).
- **Generic command** (capability/arm/voice): `frame(header(cmd, payload.count) + payload + [checksum(payload)])`
  — checksum covers the **payload only** (`:94-96`). `parseCommand` requires prefix, `bodyLength == count−7`,
  and checksum over `body[6..<count−1]` (`:99-110`).
- **Read request** (device → host), unpacked length exactly 15: header(cmd 0x30, bodyLength 8) + flashType
  + address u32le + length u24le + checksum over `body[6..<14]` (`FM1ReadRequest.init?(sysex:)` `:149-163`).
  `isFinish` ⇔ address `0xE0000000` or `0xF0000000` (`:166-168`).
- **Response** (host → device): same header with `bodyLength = 8 + data.count`, the request's flashType and
  address, `length = data.count`, then data, then checksum over `body[6...]` (`:183-185`, `:228-239`).
- **Finish reply:** `response(with: "success\0")` — 8 bytes `73 75 63 63 65 73 73 00` (`:29`, `:190-192`).
- `maximumReadLength = 512`; anything larger is a protocol error (`:32`).
- `parseResponse` (used by the simulator): bodyLength == length + 8, body length == 14+length+1, checksum (`:199-225`).

### 2.4 Identity (FM1Kit `Protocol/FM1Identity.swift`)
- Handshake reply: unpacked **34 bytes**: header(cmd `0x11`, bodyLength 27) + 27 bytes of NUL-padded ASCII
  text + checksum over `body[6..<33]` (`:139-169`). Text must be all < 0x80.
- Recorded real reply → `FM-1_009` (FM1Kit `Tests/FM1KitTests/ProtocolTests.swift:102-110`):
  `F0 00 32 45 58 01 00 00 23 4D 5A 44 79 05 06 4C 1C` + 21×`00` + `40 05 F7` (41 bytes); re-encoding gives the same bytes.
- Text parse (`:46-80`): split at the **last** `_`; model = before, must be non-empty. If model
  case-insensitively equals `ota-FM1C`: digits must be exactly 16 hex → `armedBuildPrefix` (lower-cased),
  version 0. Otherwise all chars printable ASCII 0x21–0x7E, digits non-empty ASCII decimal → version.
  Rejected examples: `_5`, `FM-1_`, `FM-1_12a`, `FM-1`, ``, full-width digits, `FM-1_-5`, `FM-1_+5`, `FM 1_5`,
  `FM-1_5\n` (FM1Kit `Tests/FM1KitTests/EdgeCaseTests.swift:35`). **`FM1_OFFICIAL_OPEN` does not parse**
  (no digits after the last `_`).
- `isLoader` ⇔ model lower-cased starts `ota-` (`:100-102`). `hardwareModel` = model without `ota-`, or
  `FM-1` for an armed loader (`:121-124`). `isLoader(for: model)` = isLoader && hardwareModel == model (`:127-129`).
- `isOpenFirmware` ⇔ (model `FM-1` && version in 8,000,000…8,000,999) || model `FM1_COMMUNITY` (`:112-117`).
  DUB-1 1.3.0 reports `FM-1_8010300` and is **not** open (`:107-109`).
- `officialV15 = FM-1_015` — build from text; `FM1Identity(model:"FM-1", version:15)` is `FM-1_15`, wrong (`:14-21`).
  Equality is on the whole struct, so `FM-1_015` ≠ `FM-1_15`; Codable as its text (`:175-192`).

### 2.5 `.fwsc` package (FM1Kit `Package/FwscPackage.swift`, `Package/FirmwareContainer.swift`)
- File > 20×0x30 bytes, ≤ 2,097,152 (`FwscPackage.swift:60-68,107-112`).
- Markers: in each of the first 20 blocks of 0x30 bytes, byte 0x2F is a marker; char = `marker − block − 1`;
  `0x7D` = unused (`:158-167`). Product must parse as an identity, ASCII, no whitespace, model `FM-1`
  (`:113-119`). **Logical image** = file with those 20 bytes removed (`:170-179`). `sha256` is of the
  **file**, not the image (`:82-83,125`).
- Container in the logical image (`FirmwareContainer.swift:81-141`):
  - Header: first 64 bytes deciphered with key 0xFFFF. `u16@0 == crc16(header[2..])`, `u32@4 == image.count`,
    `u16@8` = entry count (1…12), chip = text at 16..<32 must be `AC791N`; `u16@2 == crc16(table)`.
  - Table: `count` entries of 80 bytes, each deciphered separately from key 0xFFFF; fields: type u16@0,
    checksum u16@4, offset u32@8, size u32@12, storedSize u32@16, encipheredLength u32@24, name at 64… (`:29-37`).
  - Each entry: offset ≥ tableEnd, storedSize ≥ size, offset+size ≤ image.count, enciphered ⇒
    encipheredLength == storedSize, stored ranges (clamped to the image) don't overlap; non-enciphered part
    must match `crc16(part) == checksum` else `damagedPart(name)` (`:103-120`).
  - Types: flash 0 (`flash.bin`), loader 100 (`ota.bin`), user data 50 named `USR` (`:55-58`).
    Exactly one flash, not enciphered, size in 16,384…1,044,480 and a multiple of 4096 — unless the package is an
    installer (only one entry, a loader). Exactly one loader, not enciphered, size 1…32,767 (`:122-137`).
  - **CRC-16**: poly 0x1021, init 0, MSB-first (XModem) (`:160-169`).
  - **Decipher** (symmetric): XOR each byte with the low byte of a 16-bit key starting at `start`, stepping the
    key like the CRC register after each byte (`:176-182`).
  - `applicationRange` = flash.offset+0x4000 ..< flash end (or whole flash if ≤ 0x4000) (`:148-152`) — the region
    write progress counts. Real V15/Felucca: flash.bin at 0x400–0x93400, app area 0x4400–0x93400
    (FM1Kit `HARDWARE_TESTING.md:386-390`).
  - `headerRange` = 0 ..< first part's offset; `bootAreaRange` = flash.offset ..< +0x4000 (`FwscPackage.swift:147-155`).
  - `loaderSHA256` = SHA-256 of the loader part (`:85-89,126`).
- Failures and their user copy: `tooShort`, `tooLarge`, `unreadableProduct`, `unexpectedModel`, `damagedHeader`,
  `unexpectedChip`, `damagedPart`, `missingPart` (`:18-56`).
- **Official firmware** (`:195-239`): V15 `FM-1 V15`, 699,956 bytes, SHA-256 `db1642b2…edb8a`, build ID = its SHA-256;
  Open V12 `FM1 Open V12`, 475,700 bytes, SHA-256 `2dd4f2d2f020851f8797752371704a9a541aa747f4e20419d629de9aa2c66f43`,
  build ID `426b7fa5cf1626bceaa05e8106b9a8b0eaad7f49d8b2c157303f15be347596aa`; stock loader SHA-256
  `ef1f2db89b011fa53e71df363f0122de0ea896618c53fb53e8da676322004541`. `matching()` = size and sha equal.
  Bundled file verified: `../FM-SWITCH/App/Resources/Firmware/FM-1_015.fwsc` is 699,956 bytes, sha256 `db1642b2…edb8a`;
  licence beside it `FM-1_015-LICENCE.txt` (11,854 bytes).
- `refusesV15OverOtherBootloaders` (FM1Kit `Package/ApplicationArea.swift:103-158`; second MIT notice `:1-19`):
  decipher the app area at flash 0x4000 in 32-byte blocks with key `0x980F ^ (blockOffset >> 2)`; read a table
  of up to 16 × 32-byte entries (CRC-checked), find `app.bin`; true ⇔ code contains `FELUCCA-LOADER-1` and
  not `AC79BLID` and not the V15 flash-image SHA-256 bytes `c1068a40…1dac03`. Used by Before you start (§3).
- **Sample packages for tests** (FM1Kit `Package/SamplePackage.swift:45-149`, `@_spi(Testing)`): parts start at
  0x400; image byte `i = (i*seed + 5) & 0xFF` (seed 13); build the table (fields enciphered), header with CRCs
  and `AC791N`, then insert markers `product[i] + i + 1` (unused 0x7D). Defaults: flash 32,768, loader 1,000.
  The Kotlin simulator validates a real container, so tests need this builder ported.

### 2.6 Open firmware: capability and arming (FM1Kit `Protocol/FM1Capability.swift`, `Updater/FM1Updater+Checks.swift`)
- Only asked when the identity `isOpenFirmware` (`+Checks.swift:60-69`). Query: command `0x31`, empty payload;
  reply with the same command within `commandReplyWait` 1.5 s (`+Checks.swift:43-54`).
- Reply payload **47 bytes**: `"FMC1"`, version 1, family (≤7; FM-1 is 7), state (0 = ready), loaderKind 1,
  maxRead u16le == 512, flashLength u32le == 1,048,576, buildID 32 bytes, errorCode (`FM1Capability.swift:99-115,137-143`).
  Parse failure or no reply → `capabilityUnavailable`.
- Model check: device.hardwareModel == package model, or capability family == 7, else `modelMismatch` (`+Checks.swift:31-36`).
- Arm (before the upgrade command): requires `state == 0` else `deviceBusy`; command `0x32`, payload
  `[1, family] + u32le(logicalImage.count) + buildID(32)`; build ID = known official one, else the package
  **file** SHA-256; reply must be exactly `[0]`, else `armRefused(code)`; no reply → `armRefused(nil)`
  (`+Checks.swift:80-98`; `FM1Capability.swift:130-135`).
- After arming, the loader reports `ota-FM1C_<first 16 hex of armed build>`; install accepts only that prefix
  (`FM1Updater.swift:214-219`).
- After install, if a build ID is known and the device answers capability, its buildID must match, else
  `buildMismatch` (`FM1Updater.swift:288-291`).

### 2.7 Updater flow (FM1Kit `Updater/FM1Updater.swift`)
- **Discovery:** for each probeable port: open, handshake; `findAll` keeps every answerer; `findOnly` throws
  `multipleDevices(count)` when > 1 (`:77-103`). `identify()` = first answerer, closed (`:110-114`).
- **Handshake:** drain; up to `handshakeAttempts` (2) times: send query, wait `handshakeWait` (1 s) for any reply
  that parses as an identity, skipping others (`:131-144`).
- **check(package)**: `findOnly(!isLoader)` else `deviceNotFound`; not firmware → `notFirmware`; capability; model;
  `InstallCheck` (`+Checks.swift:15-38`).
- **install(package, options)** (`:162-224`):
  1. `findOnly(!isLoader)` → `deviceNotFound`.
  2. buildID = options.buildID ?? known official.
  3. check → kind: `reinstall` → throw `alreadyInstalled`; `switchingFirmware` && !confirmsSwitchingFirmware →
     `switchNotConfirmed`; `sameIdentity` && !confirmsSameIdentity → `sameIdentityNotConfirmed`.
  4. emit `connected(identity)`; arm if open; send UPGRADE; pause `afterUpgradeCommand` (0).
  5. serve **staging** until a finish at `0xE0000000`; emit `staging(requests)` per answered read.
  6. Close the connection. If not finished: `bootloaderMismatch` if last read started in the boot area;
     `packageRefused` if still connected and last read started in the header; else `disconnected(.staging)` /
     `stalled(.staging)` (`:200-210`; `+Refusals.swift:6-17`).
  7. emit `waitingForLoader`; sleep `loaderBoot` (3 s); `waitFor(loaderSearch` 30 s`)` a loader for the model
     whose armed prefix (if any) equals this build's → else `loaderNotFound` (`:212-222`).
  8. `write` (below).
- **resume(package)** (`:238-247`): not firmware → `notFirmware`; `findOnly(isLoader(for: model))` → else
  `loaderNotFound`; `write` with the known build ID. **It accepts any loader for the model; it does not check
  the armed prefix** — the app picks the right package (§3.2).
- **write** (`:251-294`): emit `loaderFound(identity)`; UPGRADE; serve **writing** until `0xF0000000` with
  `WriteProgress(region: applicationRange)` emitting `writing`/`checking`; close; not finished →
  `disconnected(.writing)`/`stalled(.writing)`; emit `rebooting`; sleep `firmwareBoot` (3 s); `waitFor(returnSearch`
  40 s`, !isLoader)` → else `deviceDidNotReturn`; identity must equal the package's → else `identityMismatch`;
  open-firmware build check; emit `finished(identity)`.
- **serve** (`:323-372`): loop: check cancellation; if idle > idleLimit → return (stalled); `receive(1 s)`; on
  nil, if `isLost` → lost; skip anything not a read request (other messages **don't** reset the idle timer);
  finish request: must be this phase's address **and** length == 8, else throw `unexpectedFinish(phase)`;
  send finish reply (ignore send error), then **settle** 600 ms answering any repeat of the same request
  (`:376-384`), return finished. Data request: `length ≤ 512` and `addr+length ≤ image.count` else throw
  `badRequest(address,length)`; pause `responseDelay` (0); send `image[addr..<addr+len]`; send error → lost;
  count; record `lastRead`.
- `waitFor` scans every `scanInterval` (1 s) until the deadline (`:117-128`).
- Cancellation: cooperative; cancelling while writing leaves the device in its loader (`:19-20`).

### 2.8 `UpdaterTiming.standard` (FM1Kit `Updater/UpdaterTiming.swift:9-39`)
| Field | Value |
|---|---|
| afterUpgradeCommand | 0 |
| stagingIdleLimit | 8 s |
| loaderBoot | 3 s |
| loaderSearch | 30 s |
| writeIdleLimit | 60 s |
| firmwareBoot | 3 s |
| returnSearch | 40 s |
| responseDelay | 0 |
| finishSettle | 600 ms |
| scanInterval | 1 s |
| handshakeWait | 1 s |
| commandReplyWait | 1.5 s |
| voiceReplyWait | 10 s (deferred) |
| libraryReplyWait | 1 s (deferred) |
| handshakeAttempts | 2 |

### 2.9 Events, progress, install kinds
- `UpdateEvent` (FM1Kit `Updater/UpdateEvent.swift:2-25`): `connected(identity)`, `staging(requests)`,
  `waitingForLoader`, `loaderFound(identity)`, `writing(fraction, requests)`, `checking(fraction, requests)`,
  `rebooting`, `finished(identity)`.
- `WriteProgress` (FM1Kit `Updater/WriteProgress.swift:15-110`): region = application area. Each read is clamped
  to the region; outside → repeat the last event with the new count. Track `written` and `checked` as byte sets.
  A re-read is a full 512-byte read entirely over written ground with written ≥ 50%; **3 in a row** start a checking
  pass (they count as checked). In checking, new ground → back to writing (checked cleared, count reset to 1).
  writing fraction = |written|/|region| (≤1); checking = |checked|/|written|. Writing can end < 1 (a loader may skip).
  11 tests define it (FM1Kit `Tests/FM1KitTests/WriteProgressTests.swift`): forwards, backwards, header rereads at the
  start, short rereads at the end, second pass = checking, reread at end of pass still writing, new ground in
  checking → writing, skip ends < 1, short final read counted, real-shaped backwards and checking loaders.
- `InstallKind` / `InstallCheck` (FM1Kit `Updater/InstallCheck.swift:2-49`):
  - With capability (open firmware running): same build → `reinstall`; else package is open && family 7 → `update`;
    else `switchingFirmware`.
  - Without: device == package identity → `reinstall` if V15, else `sameIdentity`; otherwise `switchingFirmware`.
  - `InstallOptions(confirmsSwitchingFirmware, confirmsSameIdentity, buildID)` (`:53-89`).
- Real hardware (FM1Kit `HARDWARE_TESTING.md`): handshakes answered 2–81 ms; port back < 3 s after power-on
  (`:344-347`); first identify after connect can go unanswered — installs retry (`:484-486`); V15 over Felucca
  20.3 s: staging 1.3 s/75 reads, loader `ota-FM-1_015` after 5.5 s, writing 9.6 s/1167 reads, longest gap 338 ms;
  Felucca over V15 37.2 s: staging 0.3 s/22, loader `ota-FM-1_900` on port `Felucca Update`, 2323 reads over two
  passes (`:392-411`). V15's loader reads **backwards** 0x93400→0x4400 then a few short header reads; Felucca's reads
  forwards from 0x400 then again from 0x4400 (`:375-382`). Pulled cable → loader waits, resume finished in 57.1 s (`:364-372`).
  **Risk:** on 2026-10-28 the stock loader sent its identity unasked mid-write and FM1Kit reported "disconnected";
  `--resume` finished it (`:694-697`). Treat as UNVERIFIED-EXTERNAL behaviour to watch for; the serve loop already
  ignores non-request messages, so the "disconnected" came from `isLost`.

### 2.10 Errors and advice
- `FM1UpdateError` cases and their user text (FM1Kit `Updater/FM1UpdateError.swift:7-253`). In scope: deviceNotFound,
  multipleDevices, switchNotConfirmed, alreadyInstalled, sameIdentityNotConfirmed, capabilityUnavailable, deviceBusy,
  armRefused(code?), buildMismatch, modelMismatch, notFirmware, bootloaderMismatch, packageRefused,
  stalled/disconnected(staging|writing), loaderNotFound, deviceDidNotReturn, identityMismatch, badRequest,
  unexpectedFinish(phase). Deferred: voice*, backup*, projectLibraryUnavailable, sampleBankBusy,
  bootloaderInstallNeedsV15, installerDidNotStart, factoryVoicesUnavailable.
- `FM1Advice` (FM1Kit `Updater/FM1Advice.swift:16-183`): headline + explanation + `isDeviceFine` + one `Action`
  (tryAgain, resume, reconnect, switchOffAndOn, confirm, chooseAnotherFirmware, holdKeysAtPowerOn, needsComputer,
  dismiss). `mayLeaveDeviceChanged` for writing-phase stalls/disconnects, loaderNotFound, unexpectedFinish,
  deviceDidNotReturn, badRequest (`:137-146`). Headlines `:149-182`. **The Standard app does not use FM1Advice**;
  it uses `error.localizedDescription` plus its own copy (§3.1, §4).

### 2.11 SimulatedFM1 (FM1Kit `Transport/SimulatedFM1*.swift`) — the behaviours to reproduce
- Options in scope (`SimulatedFM1.swift:20-86`): `installedIdentity` (default `FM-1_900`), `startsInRecoveryMode`,
  `refusesRunningFirmware` (V15 over V15 refused, default true), `stagingRequestCount` 74, `writeChunkLength` 512,
  `writesBackwards`, `checksAfterWriting`, `requestInterval` 4 ms, `rebootDuration` 1.5 s, `responseTimeout` 5 s,
  `unplugDuringWriteAt` (fraction), `installedBuildID`, `capabilityState`, `armReply`, `serialNumber`
  (`SIMULATED-FM-1`), `bootloaderVersion` + `comparesBootArea` (m-bootloader refusal), `refusesAfterHeader`.
  Deferred: voices, project slots, workspace, sample bank, library.
- One port `FM-1 Midi (simulated)` (`:117`), id `simulated-<generation>`; no ports while rebooting; connections die on reboot
  via a generation counter (`SimulatedFM1+Transport.swift:9-28`; `SimulatedFM1.swift:184-186,331-347`).
- Loader identity `ota-FM-1_9` (model `ota-FM-1`, version 9) unless armed → `ota-FM1C_<prefix>` (`:119,170-179`).
  (Real stock loader reports `ota-FM-1_015`; the sim differs.)
- Handles: handshake → reply if online; UPGRADE → ignored on open firmware until armed, else start a session
  (`:189-213`); capability/arm (`+Commands.swift:7-27`: arming checks payload length 38, version 1, family 7, image length).
- Staging: `stagingRequestCount` reads of `min(512, image)` at `(i*len) % max(image−len,1)`; V15-over-V15 refused
  silently; finish `0xE0000000` length 8; reboot into loader (`:234-267`). Boot-area / header refusal paths `:237-247`.
- Writing: 512-byte chunks over the whole logical image, optionally reversed, optionally twice; pull the cable once
  at `unplugDuringWriteAt` (reboot into loader, `hasUnpluggedOnce`); finish `0xF0000000`; build ID update; reboot into
  the package identity (`:271-309`).
- `exchange`: wait `requestInterval`, send the request, wait `responseTimeout` for a response whose address and data
  match byte-for-byte, else stop (`:312-328`) — encoding bugs show up as stalls.
- `prepare(for: package)` tells the sim what to expect (`:150-152`); the app calls it before each step
  (`../FM-SWITCH/App/Model/DeviceController.swift:200`).

### 2.12 Inbox and framing
- `SysexInbox`: single reader, timed `next(timeout)`, capacity **256**, oldest dropped when full, `drain`, `close`
  wakes the waiter with nil and ignores later messages, cancellation-safe (FM1Kit `Transport/MIDITransport.swift:107-218`).
  Tests: FM1Kit `Tests/FM1KitTests/EdgeCaseTests.swift:138-171,293-312`.
- Assembler rules to keep in the Android byte-stream framer (from `Protocol/UMPSysex7.swift:76-112` and
  `EdgeCaseTests.swift:89-127`): continuation without a start is ignored; a new start discards an unfinished
  message; a message longer than **64 KiB** is dropped; other message types ignored. Byte-stream specifics
  (proposal): ignore real-time bytes 0xF8–0xFF inside SysEx; any other status byte (≥0x80, not F7) aborts the message.

---

## 3. The app model to mirror

`AppModel` (`../FM-SWITCH/App/FMSwitchApp.swift:79-243`) owns `settings`, `catalogue`, `library`, `history`,
`backups`, `starred`, `tipJar`, `updateNotices`, `device`; `requestedPage` routes warnings/errors to a page;
`firmware(id:)` looks up listed or added-from-file entries (`../FM-SWITCH/App/Model/AddedFirmwares.swift:25-38`).

### 3.1 DeviceController (`../FM-SWITCH/App/Model/DeviceController.swift`, `+States.swift`)
- Observable state: `identity`, `isScanning`, `lastScan`, `ports`, `job: FlashJob?`, `log: [String]`, `transportName`,
  `transportError` (+ `voiceRestore`, `backupRun` — deferred) (`DeviceController.swift:15-24`).
- `configure(kind, settings)` (`:53-104`): refuses while writing; builds the transport (CoreMIDI, or `SimulatedFM1` with
  options from settings: recovery mode, cable pull **at 0.45**, backwards, verifies, m-bootloader → `bootloaderVersion "1.2"`
  + `comparesBootArea`, open firmware); new updater; watch task: scan, then up to 5 one-second re-scans while identity is
  nil, then on each `changes()` event wait 400 ms, wait while writing, scan.
- `scan()` (`:108-126`): not while scanning/writing; ports + `identify()`; up to 3 one-second retries while ports exist but
  nothing answered.
- `plan(for:requiresOfficial:alwaysViaOfficial)` (`:132-141`): `[official, target]` when target isn't official, it's
  required or always-via-official is set, a saved V15 exists, **and** the FM-1 isn't already reporting V15's product; else `[target]`.
- `check(item)` → `updater.check(package)` (`:145-148`).
- `flash(steps, confirmsSwitchingFirmware, confirmsSameIdentity)` (`:158-167`) → `FlashJob`, `run(from: 0)`.
- `run(from:)` (`:184-236`): keep awake; per step: phase `.preparing`, log `▸ name version (product)`, load package,
  `simulatedDevice?.prepare`, **if `identify()` reports any loader → `resume`, else `install`** with the job's options;
  events applied on the main actor; success → `identity = installed`, `history.record(item)`; failure → phase
  `.failed(message, canResume)`, `needsAStepFirst`, `mightBeRefused`; all steps done → `.succeeded(identity.text)`, log `✓ Finished.`
- `resume()` (`:170-176`) reruns from the stopped step (used for both "Carry on installing" and "Try again").
- `apply(event)` (`:238-260`) maps events to phases and log lines (`.checking` → `.verifying`, logged once).
- `failureMessage` (`:354-369`): refusals get "Your FM-1 is fine. It's still running <name>, which won't take <item> from here.
  Install V15 first…" or "…Install MIDIMON 0.4 or LOOM-8 first…"; `needsAStepFirst` (`:373-376`) = refusal && (startUp ≠ mVave || item is official).
- `FlashPhase` (`+States.swift:5-28`): preparing, staging(requests), waitingForLoader, writing(fraction), verifying(fraction),
  rebooting, succeeded(String), failed(String, canResume). Labels are Retro's all-caps.
- `FlashJob` (`+States.swift:54-120`): steps, confirms…, index, phase, needsAStepFirst, mightBeRefused;
  `canResume(after:)` = `disconnected(.writing)`, `stalled(.writing)`, **`loaderNotFound`** (`:74-81`);
  `isRefusal` = bootloaderMismatch | packageRefused (`:86-91`); `mightBeRefused` = `stalled(.staging)` (`:95-98`);
  `phaseFraction` uses staging/75 (`:112-119`).

### 3.2 FirmwareLibrary (`../FM-SWITCH/App/Model/FirmwareLibrary.swift`)
- `StoredFirmware` (`:6-39`): id (= sha256), name, version, author, product, sha256, size, fileName, source
  (catalogue/official/imported), catalogueID, packageURL, build, savedAt.
- Storage: `Firmwares/<name>.fwsc` + `Firmwares/.details/<sha256>.json`, whole-file adds/removes (`:41-50,318-341,352-354`);
  file name "Name version build.fwsc", `/:\` → `-`, sha prefix on clash (`:344-350`).
- Bundled V15 read from the app, never saved, can't be removed, survives clear (`:48-50,64-73,266-282,359-375`);
  `official` = the V15 item (`:90-92`). `reload()` re-reads details, drops items whose file is gone, adds the built-in (`:382-394`).
- `stored(for entry)` = same sha256, else same catalogueID + packageURL (`:107-113`); `outdated`, `otherBuild` (`:116-128`).
- `packages(thatCanFinish: loader)` (`:150-169`): armed prefix → items whose build ID (known official, else sha256) starts with it;
  else `ota-` + item.product == loader.text, or loader is `ota-FM-1_015` and item's loader SHA-256 is the stock loader's.
- `download(entry)` (`:186-216`): fetch; size and sha256 (if published) must match → `hashMismatch`; parse; product (if listed)
  must match → `productMismatch`; V15 → saved as official; an `isOfficial` entry that isn't V15 → `notOfficial`; remove older
  releases of the same catalogueID; save. `importFile(at:)` (`:231-236`) via `add` (`:247-263`): V15 → official; existing sha →
  that item; else imported (Open V12 named "FM1 Open V12", author M-VAVE). `downloadOfficialFromMVAVE` (`:220-225`) scrapes
  M-VAVE's page (`PackageDiscovery`) — optional for Android since V15 is bundled.
- Failure copy: `FirmwareLibrary+Failure.swift:4-37`.

### 3.3 CatalogueStore and PublishedList
- `PublishedList` (`../FM-SWITCH/App/Model/PublishedList.swift`): URL `https://fm-1.drewr.dev/firmwares.json` (`:14`); retries
  after 2, 4, 8 s on network errors (`:18`); HTTP < 500 → `unusable` immediately; decode ISO-8601 dates; not readable
  (format) or empty → `unusable` (`:21-41`).
- `CatalogueStore` (`../FM-SWITCH/App/Model/CatalogueStore.swift`): cached list at `Application Support/FMSwitch/catalogue.json`
  (`:41-48,257-269`), deduped by id on load (`:187-195`); entries sorted official first then name (`:54-59`); `refresh()`
  (`:151-183`): published list, **falling back to building on-device (`CatalogueBuilder`, ~90 GitHub requests) when `unusable`**;
  error copy "Couldn't refresh; showing the list saved earlier." only when the saved list is > 1 day old, else "Couldn't fetch
  the firmware list. Check your connection and try again."; `correctingTestFlags` (`:203-217`). Also live per-firmware
  `checkSource` (GitHub releases, 10-minute reuse, `:117-140`), `findScreenshots`, `chosenBuilds`/`chosenTracks` in UserDefaults.
- **Decision for the Planner:** AGENTS.md says don't build the list on the device as the default path (`AGENTS.md:55-57`) and to
  ignore a newer-format list. The iOS app's fallback on `unusable` contradicts "default path" only weakly (it's a fallback).
  Simplest compliant port: keep the cached list and show the error; no `CatalogueBuilder`. Also decide whether `ReleaseCheck`
  (newer release at source), `Screenshots`, `OtherRelease` track choice and build choice are in scope — they're on the firmware page.
- `CatalogueEntry` (`../FM-SWITCH/App/Catalogue/FirmwareCatalogue.swift:10-177`) incl. `requiresOfficialFirmware` (`loader == "mvave"`,
  `:152-154`), `needsBuildChoice` (`:161-162`), `choosing(build:)` (`:165-176`); `isHardwareTested` (`HardwareTesting.swift:13-19`);
  `OtherRelease` (`OtherRelease.swift`); `listedOn`/`packageSource`/`sourceBuildSummary` copy (`../FM-SWITCH/App/Model/EntryDescriptions.swift`);
  link grouping `pageLinks` (`../FM-SWITCH/App/Catalogue/CatalogueLinkGroups.swift:30`); added-from-file entries `file-<sha>` (`AddedFirmwares.swift:3-23`).
- `FirmwareFilter` / `FirmwareScope` / `FirmwareOrder` (`../FM-SWITCH/App/Model/FirmwareFilter.swift:5-131`).

### 3.4 InstallReadiness and StartUp
- `InstallReadiness` (`../FM-SWITCH/App/Model/InstallReadiness.swift:16-124`): `steps` from `plan`; `isMissingOfficial`;
  `isUntested` (catalogue source && listed entry not hardware-tested); `refusesV15OverOtherBootloaders`; `startUp`; `macWarning`
  (`FeluccaUpdateCode.Warning`) when refusing and startUp ≠ mVave. `needsStartUp` = refuses && startUp nil. `isSwitching` =
  **steps > 1 or** check found switchingFirmware. `isSameIdentity` = !isSwitching && found sameIdentity. `isAlreadyInstalled` =
  found reinstall && one step. `canStart` = identity present, official not missing, untested accepted, start-up known, and:
  checking/failed → no; `finishingUpdate` → yes; found → not already installed && (!switching || confirmed).
  `check(first, on:)` returns `.finishingUpdate` when the FM-1 is in a loader, else `found(kind)` — **only the first step is checked**.
- `StartUp` + `FeluccaUpdateCode` (`../FM-SWITCH/App/Model/StartUp.swift:5-102`): the four answers and copy; `known` catalogue IDs
  (`amb, dub, felucca, hyp1, melodee, resource-hub-lof-1`), `wayRound = "midimon"`. The iOS "questionDetail" mentions iPhone/Mac
  (`:35-39`) — needs an Android wording.

### 3.5 FlashHistory, InstallProgress, DeviceSummary (app versions)
- `FlashHistory` (`../FM-SWITCH/App/Model/FlashHistory.swift:8-48`): newest first, limit 30, UserDefaults key `flashHistory`;
  `lastInstall(matching: product)` = only the newest record if its product matches.
- App `InstallProgress` (`../FM-SWITCH/App/Model/InstallProgress.swift:7-96`): per-step shares staging 0–0.10, waiting 0.10–0.12,
  writing 0.12–0.85, checking 0.85–0.97, restarting 0.97–1.0; spread across steps `(index + within)/steps`; `Tracker` never goes
  backwards (keeps the higher stage if the fraction drops) and freezes on failure. Stage names "Getting ready", "Installing",
  "Checking", "Restarting".
- App `DeviceSummary` (`../FM-SWITCH/App/Model/DeviceSummary.swift:8-77`): state notConnected / running(name?) / waiting(loader);
  name from last install (if product matches), else "the official firmware (V15)", else a unique catalogue match; status line,
  drawing screen title and line copy.

### 3.6 AppSettings (`../FM-SWITCH/App/Model/AppSettings.swift`)
- **In scope:** `appearance` (system/light/dark), `transportKind` (usbMIDI/simulated; default simulated on the simulator),
  `showBeta` (default true), `showLinksOnly` (false), `alwaysViaOfficial` (false), `startUp` (nil = ask), `firmwareOrder` (name),
  `simulatedRecoveryMode` (**stored under key `simulatedRescueMode`**, `:202`), `simulatedCablePull`, `simulatedReadsBackwards`,
  `simulatedVerifies`, `simulatedMBootloader`, `simulatedOpenFirmware`, `acknowledgedNoticeVersion` (vs `SafetyNotice.version` 2).
- **Out of scope:** `look`, `isRetroFound`, `phosphorHex`, `glow`, `scanlines`, `flicker`, `lookVersion`, `notifiesFirmwareUpdates`,
  `hasOfferedUpdateNotices` (update notifications — not named in scope; flag), `tipNudgesShown`.
- Android has no shared-defaults compatibility need; keys can be fresh.

### 3.7 Platform pieces and their Android counterparts
| iOS | What it does | Android |
|---|---|---|
| `WakeKeeper` (`../FM-SWITCH/App/Platform/iOS/WakeKeeper.swift:9-39`) | idle timer off during a write; background grace task | `FLAG_KEEP_SCREEN_ON` while the install screen shows + a foreground service holding a partial wake lock for the run. FGS type `connectedDevice` needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` and, from API 34, one of: a manifest `CHANGE_NETWORK_STATE`/`CHANGE_WIFI_STATE`/`NFC`/…, a granted Bluetooth/UWB runtime permission, or a `UsbManager.requestPermission()` call ([service types](https://developer.android.com/develop/background-work/services/fgs/service-types)). Alternative `shortService` (~3 min limit) — risky for a chain + resume. Planner decision. |
| `SharedStorage` / iCloud (`../FM-SWITCH/App/Model/SharedStorage.swift`) | Firmwares/Backups in iCloud Drive or Documents | `filesDir/Firmwares` + `.details`; no sync |
| `SavedFiles` (`../FM-SWITCH/App/Platform/iOS/SavedFiles.swift`) | opens Files at the folder | none needed; optionally share/export via SAF `ACTION_CREATE_DOCUMENT` |
| `fileImporter` for `.fwsc` (`../FM-SWITCH/App/Standard/Firmwares/FirmwareListView.swift:49-53`) | import | SAF `ACTION_OPEN_DOCUMENT` (`*/*`, filter by name/parse) |
| `InAppBrowser` (`../FM-SWITCH/App/Platform/iOS/InAppBrowser.swift:46-80`) | SFSafariViewController for http(s) | Custom Tabs (`androidx.browser`) |
| `ThisDevice.name` (`../FM-SWITCH/App/Platform/iOS/ThisDevice.swift`) | "phone"/"iPad" in copy | "phone"/"tablet" |
| `Pasteboard.copy` (same file) | copy problem details | `ClipboardManager` |
| `UnavailableTransport` (`../FM-SWITCH/App/Model/UnavailableTransport.swift`) | when CoreMIDI fails | when `FEATURE_MIDI` missing or `MidiManager` null |
| `LaunchHandOff`, `AlternateIcon`, `TipJar`, `UpdateChecker` | — | out of scope |
| Retry MIDI after launch (`FMSwitchApp.swift:239-242`) | CoreMIDI can refuse early | keep "Try again" on the connect card |

### 3.8 Semantic drift — names that don't mean what they say (port by meaning, not by name)
1. **Two `InstallProgress` types.** FM1Kit's (shares 0.15/0.22/0.80/0.92, staging scale 50, single install) vs the app's (per-step
   shares above, multi-step spread, `Tracker`). Mirror the **app's**.
2. **Two `DeviceSummary` types.** FM1Kit's `FM1DeviceSummary` (firmwareName "M-VAVE V15", serial) vs the app's plain-words state. Mirror the app's.
3. **`InstallKind.reinstall` means "already installed — refuse"**, not "reinstall it". `sameIdentity` is the one that reinstalls.
4. **"verify/checking"**: `UpdateEvent.checking` ↔ app `FlashPhase.verifying` ↔ stage "Checking"; PLAN.md calls *staging* the
   "verify/stage phase" (`../FM-SWITCH/PLAN.md:30`) — a third, unrelated meaning. FlashPhase label for staging is "CHECKING PACKAGE".
5. **Open firmware predicates differ.** `FM1Identity.isOpenFirmware` (8,000,000…8,000,999 or `FM1_COMMUNITY`) vs the app's
   `OpenFirmware.isOpen` regex `FM1_OFFICIAL_OPEN|FM1_COMMUNITY_\d+|FM-1_8\d{6}` (`../FM-SWITCH/App/Catalogue/OpenFirmware.swift:6-8`),
   which also matches DUB-1's `FM-1_8010300`. The regex is used only by Retro (`../FM-SWITCH/App/Views/DeviceView.swift:131,134`).
   Port FM1Kit's for arming and Standard UI.
6. **`FlashJob.canResume` includes `loaderNotFound`**, which is raised after *staging* (loader never seen) as well as by resume.
7. **`FM1Updater.resume` ignores the armed prefix**; matching the right package is `FirmwareLibrary.packages(thatCanFinish:)`.
8. **`StoredFirmware.version`** is the literal `"Official"` for V15 and the product string for imports with no version
   (`FirmwareLibrary.swift:208,258,273`). `id` is the file SHA-256.
9. **`simulatedRecoveryMode`** persists under `simulatedRescueMode` (`AppSettings.swift:202`).
10. **`isSwitching`** is true for any chained install even if the check says `update`/`sameIdentity` (`InstallReadiness.swift:73-75`).
11. **AGENTS.md's confirmation copy is Retro's.** "SWITCH FIRMWARE?" / "REINSTALL OR UPDATE?" are in `../FM-SWITCH/App/Views/FlashConfirmView.swift:33`.
    The Standard look uses the "I've saved anything I want to keep" tick and a sameIdentity info notice (§4). `AGENTS.md:50-52` should be read with that in mind.
12. **`CatalogueEntry.listing`** is an OptionSet encoded as an Int, and `MergedCatalogue` dates are ISO-8601 in the published file but
    the iOS cache writes Foundation's default (seconds since 2001) (`CatalogueStore.swift:168` vs `PublishedList.swift:35-36`) — use one format on Android.
13. **"loader"** in `CatalogueEntry.loader == "mvave"` means "install the official firmware first", not the update loader.

### 3.9 Where the user-facing copy lives (reuse, don't invent)
- Plain-words rules: `../FM-SWITCH/REDESIGN.md:24-27` (never say VM, BTIF, hex, staging or loader in the main flow), `:98-106`.
- Install stages: app `InstallProgress.Stage.name` (`../FM-SWITCH/App/Model/InstallProgress.swift:17-24`).
- Home: `../FM-SWITCH/App/Standard/Home/HomeView.swift` (cards `:84-201`), status: `../FM-SWITCH/App/Model/DeviceSummary.swift:51-76`.
- Before you start: `../FM-SWITCH/App/Standard/Firmwares/BeforeYouStartView.swift:63-207`; untested warning `InstallReadiness.swift:35-40`.
- Start-up question and warning: `../FM-SWITCH/App/Model/StartUp.swift:15-40,77-101`; views `../FM-SWITCH/App/Standard/Firmwares/StartUpViews.swift`.
- Installing / Done / Problem: `../FM-SWITCH/App/Standard/Install/InstallingView.swift:52-92`, `InstallOutcomeViews.swift:5-158`.
- Refusal messages: `DeviceController.swift:354-369`. Protocol error text: FM1Kit `Updater/FM1UpdateError.swift:96-253`.
- Library failures: `../FM-SWITCH/App/Model/FirmwareLibrary+Failure.swift:12-36`. Package failures: FM1Kit `Package/FwscPackage.swift:37-56`.
- Catalogue errors: `CatalogueStore.swift:176-181`. Firmware page: `FirmwarePageView.swift:109-288`. Details copy: `EntryDescriptions.swift`.
- My firmwares empties: `../FM-SWITCH/App/Standard/Firmwares/MyFirmwaresView.swift:67-70`.
- Safety notice (first run + Settings): `../FM-SWITCH/App/Views/SafetyNoticeView.swift:4-67` (iOS variants `:20-27,42-51`), welcome view
  `../FM-SWITCH/App/Standard/Home/WelcomeNoticeView.swift` (4-second OK countdown).
- Help topics: `../FM-SWITCH/App/Model/HelpTopic.swift` (wontInstall, stoppedHalfway, wontStart, wontStartAtAll, notSeen; intro `:17`).
- Settings copy: `../FM-SWITCH/App/Standard/Settings/StandardSettingsView.swift:31-195` (simulated toggles `:176-187`, About `:129-150`).
- Update notices (if kept): `../FM-SWITCH/App/Model/UpdateNoticeWords.swift`, `FirmwareUpdateNotices.swift`.
- iOS-specific words to adapt: "iPhone"/"iPad"/"Files app"/"Safari"/"Mac" appear in SafetyNotice, StartUp.questionDetail, SavedFiles, HelpTopic.

---

## 4. Standard-look screens in scope and the flow

Root (`../FM-SWITCH/App/Standard/StandardRootView.swift`): on phones a `NavigationStack` with pages firmwares, mine, firmware(entry),
save, help(topic), settings (`:21-29,122-160`); the install is a full-screen overlay that disables everything below (`:66-78`); the
welcome notice overlays until acknowledged (`:93-98`); `requestedPage` opens help/firmware/settings (`:80-118`). Sidebar layout is
tablet/Mac — deferred.

1. **Welcome notice** (first run): SafetyNotice text + recovery; OK after 4 s → `acknowledgedNoticeVersion = 2`.
2. **Home "My FM-1"** (`HomeView.swift`): FM-1 drawing (screen title/line from DeviceSummary; 5 taps = Retro easter egg → **out of
   scope**), status pill, **Change firmware** card (count of visible firmwares), **My firmwares** card ("N saved · M starred"), Save my FM-1
   card (deferred), **finish card** when waiting in a loader → "Finish installing <first package that can finish>" opens Before you start,
   Factory sounds card on V15 (deferred), **Connect your FM-1** card when not connected (3 steps + transport error + Try again), Help card.
   Pull-to-refresh and on-appear `scan()`.
3. **Firmwares list** (`FirmwareListView.swift`): search, scope chips (All, kinds by frequency, Built from source), order menu (Name/Updated),
   summary of count/last checked, cards with badge, "+ Add a firmware file" importer, pull-to-refresh.
4. **Firmware page** (`FirmwarePageView.swift`): title band in the badge colours, author, description (+"from GitHub" note), untested/from-file
   warning, newer-release note, release (test/stable) choice, build choice, **Install <name>** (downloads first if needed) / **Save to this phone
   for later** / saved note with Remove, links (Custom Tabs), Details disclosure (Reports, Size, Released, Saved, Kind, Licence, stars, Recovery,
   Listed on, Tested, Built from source, File from, SHA-256 prefix, Installs from official). Star toolbar button. Install opens Before you start
   with `requiresOfficial = entry.requiresOfficialFirmware`.
5. **Before you start** sheet (`BeforeYouStartView.swift`): plan header ("<first> goes on first, as <target> installs from it."), missing-official and
   untested notices, **start-up question** when the target refuses V15 over other bootloaders and start-up isn't M-VAVE's, Mac warning card →
   **needs Android wording** (iOS "takes a Mac"), status (waiting-to-finish notice; "Checking your FM-1…"; failure reason; already-installed;
   same-identity info), checklist: "It's plugged in with a data cable", "I'll leave it plugged in until it's done", + "I've saved anything I want to keep"
   when switching, + "I'll try it while it's in testing" when untested; footnote "Takes about a minute…"; button "Install <name>" / "Tick each one to install" /
   "Choose your FM-1's start-up"; "Don't install" when warned. Start → `device.flash(steps, confirmsSwitchingFirmware: isSwitching && hasSaved, confirmsSameIdentity: isSameIdentity)`.
6. **Installing** cover (`InstallingView.swift`): drawing with the firmware name and stage, one bar (Tracker), "Installing <name>", "Step i of n. Keep it
   plugged in." / "Keep it plugged in. Nearly there.", stage list with "Now", keep-open message ("Your phone stays awake while this runs…").
7. **Done** (`InstallOutcomeViews.swift:5-51`): "<name> is ready to play", "Your FM-1 says it's running <reported>. It's safe to unplug.", factory sounds
   offer on V15 (**deferred** — hide), Done → `dismissFlash()` + rescan.
8. **Problem** (`InstallOutcomeViews.swift:54-158`): "The install was interrupted" (+ 2 steps, **Carry on installing**) or "The install stopped"
   (+ try-again text, or refusal message with **Install MIDIMON** and **What can I do?** → Help wontInstall); Close when not resumable; "Show the details"
   with message + log and **Copy the details**.
9. **My firmwares** ("Saved copies") (`MyFirmwaresView.swift`): Saved on this phone (listed incl. older release/other build, then added-from-file) and Starred.
10. **Settings** (`StandardSettingsView.swift`): Appearance tiles; firmware list toggles (test versions, without a download, always official first,
    [update notifications], start-up picker); Firmwares and backups location (→ Android: drop or show app storage); **Connection: FM-1 over USB /
    Simulated FM-1 + the six simulated toggles + Reset**; Safety and recovery text + links; About (credits — add FM1Kit MIT credit and M-Flasher if ApplicationArea is ported).
11. **Help** (`../FM-SWITCH/App/Standard/Help/HelpView.swift`, `HelpTopic.swift`) — reached from Home and from the Problem screen; **not in the brief's
    scope list — Planner decision**.

Prototype order from REDESIGN (`:122-125`): Choose → Before you start → Installing (runs to the end) → Done, against the simulated FM-1, first.

### 4.1 Test suites worth porting (../FM-SWITCH/AppTests and FM1Kit Tests)
App (`../FM-SWITCH/AppTests`):
- **FlashingTests** (`FlashingTests.swift:49-140+`): end-to-end through DeviceController + library + history against the simulator at real timings:
  flashes and records; backwards + verifying loader (one "reading it back" log line before Rebooting); resume after cable pull (canResume, then succeeded);
  same identity refused unconfirmed (message contains "already reports FM-1_900"); same identity once confirmed; switch refused unconfirmed ("confirm the switch").
  Voice tests (`:143-170`) deferred. Uses `FwscPackage.sampleFile` → needs the Kotlin sample builder.
- **LibraryTests** (`LibraryTests.swift:14-103`): comes with V15 and keeps it when cleared; reads built-in from the app; saves under names with details beside;
  picks up another device's saves (iCloud — adapt to reload); moves folder (drop); **packages that can finish a loader** (stock `ota-FM-1_015` → [V15]; `ota-FM-1_900` → []);
  **armed open loader** (`ota-FM1C_db1642b2b6fa5c2c` → [V15]); open identity regex (Retro-only).
- **PublishedListTests**: retries after network failure; gives up after the last wait; no retry on missing list; cancelled ≠ failure.
- **StartUpTests**: answer remembered; only known firmwares flagged; a package that accepts V15 isn't flagged; warning names the others and hedges when unsure;
  refused install explained plainly; staging stall might be a refusal; both refusals recognised; with M-VAVE start-up suggests V15 first.
- **StandardLookTests** (`:20-124`): progress runs through stages in order; spreads across steps; tracker never goes backwards; summary wording; badge letters/896 designs/contrast; looks remembered (drop Retro parts).
- **CatalogueTests** (decode/merge parts that apply to the published list: `dropsARepeatedIDFromAListSavedByAnOlderBuild`), **TestFlagTests**, **OtherReleaseTests**,
  **SourceBuildTests**, **HardwareTestingTests**, **KindFilterTests**, **FirmwareOrderTests**, **StarredFirmwaresTests**, **DescriptionTests** (builder — skip),
  **ContrastTests** (palette pairs 4.5:1 / 3:1), **HelpTopicTests** (if Help is in).
- Skip: BackupTests, DownloadModeTests, TipJarTests, TipNudgeTests, UpdateCheckerTests, ResourceHubTests/ScreenshotsTests (builder/screenshots), SharedStorageTests.

FM1Kit (`Tests/FM1KitTests`) — port as the `:fm1` module's tests:
- **ProtocolTests**: packing vectors, round trips, handshake header, request round trip, bad checksum ignored, response declares full length, identity round trip/loader,
  package product + marker strip, rejects other files, **recorded real reply**, documented command bytes.
- **PackageCheckTests**: CRC-16 XModem, decipher involution, firmware+loader read, small flash = all application, damaged header/table/part, other chip,
  enciphered parts unchecked, no loader, non-sector flash, overlap, padding past end ok, part past end refused, too large; progress region; repeated finish
  answered before close; finish of the other phase stops; standard timings add no pauses.
- **UpdaterTests**: identify, install + verify identity, resume after pull mid-write, finish from recovery mode, request beyond image refused.
- **WriteProgressTests** (11, see §2.9).
- **OpenFirmwareTests**: build IDs hex; commands round trip; capability layout; open identities; armed loader parse; arms and checks build; arms with package hash;
  running build is reinstall; leaving open is a switch; busy not armed; refused arming stops before staging; silent open not updated; others not asked; official build IDs;
  switching needs confirming and sends nothing until then; check doesn't change the device; two FM-1s refused (install and resume); findAll; sample passes every check.
- **EdgeCaseTests**: other models; unreadable identities (list above); hashes file not image; only official recognised; last underscore; non-handshake replies; finish answered
  with success; other messages aren't requests; bad response length/checksum; framer edge cases; inbox order/timeout/wake/close/drain/cancel/capacity; identity Codable;
  **never sends to other gear**; package for another model; resume needs the loader; events in order; V15 not reinstalled; custom own identity needs confirming;
  other messages don't keep a stalled phase waiting.
- **FriendlyAPITests**: advice (only if FM1Advice is ported).

Seam note (applies observation 0011): FlashingTests run the real 4 ms/request simulator — a 32 KB sample package makes ~74 + 66 requests per phase, seconds per test;
FM1Kit's UpdaterTests use `fastTiming`/`fastOptions` (FM1Kit `Tests/FM1KitTests/UpdaterTests.swift:12-39`). The Kotlin harness should inject timing and a virtual clock
(`kotlinx-coroutines-test` `runTest`) — a `delay`-based simulator under virtual time expresses every case above, including idle-limit stalls.

---

## 5. Android toolchain on this machine (measured 2026-10-30, this dev box only)

- **JDK:** `java -version` → OpenJDK **25.0.4.1** (system `/usr/lib/jvm/java-25-openjdk`; the mise `java` shim has no version active). mise also has
  temurin-17.0.15, temurin-21.0.11, zulu-8 installed but not active. `JAVA_HOME` unset. Android Studio's JBR: OpenJDK 25.0.3.
- **Gradle:** `gradle` shim fails ("No version is set for shim: gradle"); mise has 8.14.3 and 9.7.1 installed. Use the Gradle **wrapper** and a JVM toolchain;
  check AGP's supported JDK range in AGP release notes before pinning (JDK 25 needs a recent Gradle/AGP; 17 or 21 is the safe choice).
- **Kotlin CLI:** absent (not needed; Gradle plugin).
- **Android SDK:** `~/Android/Sdk` (`ANDROID_HOME`/`ANDROID_SDK_ROOT` unset → need `local.properties sdk.dir` or env). Platforms android-33, 34, 35, 36, 36.1,
  37.0, 37.1, 37.2; build-tools 34.0.0, 36.0.0, 36.1.0, 37.0.0; platform-tools 37.0.0; cmdline-tools latest 22.0 (sdkmanager, avdmanager); emulator 36.6.11;
  system images only android-37.1 and 37.2 `google_apis_playstore_ps16k/x86_64`; sources android-36.1.
- **Emulator:** binary present, `/dev/kvm` present, **no AVD exists** (`~/.android/avd` absent). The emulator can't pass a USB-MIDI FM-1 through easily → real-device tests need a phone.
- **adb:** `/usr/bin/adb` 1.0.41 (37.0.0p1-android-tools); no device attached (`adb devices` empty; this run started the adb server).
- **Android Studio:** `/opt/android-studio`, 2026.2.1 (build AI-262.9437.185.2621.16467767).
- Implication: keep the protocol module pure Kotlin/JVM so `./gradlew :fm1:test` runs anywhere (CI runner included) without an emulator. CI (GitHub-hosted Ubuntu)
  differs from this box: it will download its own SDK packages; nothing measured here describes the CI image.

---

## 6. Recommended references for PLAN.md

- `../FM-SWITCH/PLAN.md` — "How flashing works" (`:14-56`), Open/loaders/switching (`:95-123`), risks (`:127-149`).
- `../FM-SWITCH/REDESIGN.md` — principles (`:10-39`), motion/feedback (`:52-125`), colours (`:175-186`).
- `../FM-SWITCH/README.md` — firmware list contract (`:42-69`), staying alive (`:199-207`).
- FM1Kit 0.4.5 (MIT): https://github.com/ocean/FM1Kit/tree/0.4.5 — `Sources/FM1Kit/{Protocol,Package,Transport,Updater}`, `Tests/FM1KitTests`,
  `Sources/FM1Kit/FM1Kit.docc/InstallingFirmware.md`, `HARDWARE_TESTING.md`.
- App model: `../FM-SWITCH/App/Model/{DeviceController.swift,DeviceController+States.swift,FirmwareLibrary.swift,CatalogueStore.swift,PublishedList.swift,InstallReadiness.swift,InstallProgress.swift,DeviceSummary.swift,FlashHistory.swift,AppSettings.swift,StartUp.swift,FirmwareFilter.swift,AddedFirmwares.swift,EntryDescriptions.swift}`,
  `../FM-SWITCH/App/Catalogue/{FirmwareCatalogue.swift,CatalogueBuilder.swift:1-27,OtherRelease.swift,HardwareTesting.swift}`.
- Standard screens: `../FM-SWITCH/App/Standard/**`; copy: `../FM-SWITCH/App/Views/SafetyNoticeView.swift:4-67`, `../FM-SWITCH/App/Model/HelpTopic.swift`.
- Tests: `../FM-SWITCH/AppTests/{FlashingTests,LibraryTests,PublishedListTests,StartUpTests,StandardLookTests}.swift`.
- Bundled firmware: `../FM-SWITCH/App/Resources/Firmware/FM-1_015.fwsc` + `FM-1_015-LICENCE.txt`.
- Firmware list: https://fm-1.drewr.dev/firmwares.json (format 1).
- Android: https://developer.android.com/reference/android/media/midi/package-summary ; AOSP `MidiReceiver.java`, `MidiInputPort.java`, `MidiPortImpl.java`,
  `MidiDeviceInfo.java`, `MidiManager.java` (frameworks/base, media/java/android/media/midi);
  https://developer.android.com/develop/background-work/services/fgs/service-types ; Custom Tabs (androidx.browser); SAF (`ACTION_OPEN_DOCUMENT`).
- Draft architecture: `doc/architecture.md` (this run).

---

## 7. Architecture doc

None existed in this repo. Draft written to `doc/architecture.md`.

---

## Open items

- **UNVERIFIED-EXTERNAL:** P4 (a)–(d); the stock loader's unsolicited identity mid-write (§2.9); FGS type choice and its runtime prerequisite on the target phones;
  real throughput of Android USB MIDI for ~1,200 × 605-byte replies.
- **Planner decisions surfaced:** Help in or out; Starred in or out; update notifications in or out; release/build choice, newer-at-source check, screenshots on the
  firmware page; on-device catalogue fallback (recommend none); FM1Advice port or not (Standard uses localizedDescription); which iOS words to rewrite for Android
  (Mac warning card, "Files", "iPhone"); whether to port `refusesV15OverOtherBootloaders` (needed by the start-up question; brings the M-Flasher MIT notice).
- **Licence actions:** include FM1Kit's MIT notice (and Vitkauskas's if ApplicationArea is ported) in the app's notices; keep the V15 Apache-2.0 notice beside the bundled file.

## Consumers, Jev, and what was not read

- **Consumers:** `.jdi/config.yml` has `# consumers: none` — the configured consumers list is empty. This plan adds no
  externally-consumed interface: the app *consumes* `https://fm-1.drewr.dev/firmwares.json` (format 1) and FM1Kit's protocol,
  and publishes nothing. No client needs updating.
- **Jev:** `jev.enabled: false` in `.jdi/config.yml`; every doc and file was found and read by hand.
- **UNVERIFIED-UNREAD** (decidable from the repos, skimmed or skipped; none changes a recommendation above or gates a risky step):
  `../FM-SWITCH/App/Model/FirmwareBadgeDesign.swift` (the 896-design badge algorithm, needed if badges are ported);
  `../FM-SWITCH/App/Standard/FM1Drawing.swift:40-255` (geometry, lit keys, accessibility label);
  `../FM-SWITCH/App/Model/HelpTopic.swift:19-150` (topic bodies, if Help is in scope);
  `../FM-SWITCH/App/Model/StarredFirmwares.swift`; `../FM-SWITCH/App/Catalogue/{ReleaseCheck,CatalogueLinkGroups,Screenshots}.swift`;
  `../FM-SWITCH/App/Standard/Firmwares/FirmwareListView.swift:140-318` (card layout, summary line);
  `../FM-SWITCH/App/Standard/Theme/{Motion,Palette}.swift` beyond the constants quoted from REDESIGN.md; FM1Kit `CHANGELOG.md`.
