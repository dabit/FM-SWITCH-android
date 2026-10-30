# AGENTS.md

Guidance for coding agents (Claude Code, Codex and others) working in this repository.

## What this is

An Android port of FM-SWITCH (`../FM-SWITCH`), the iPhone/iPad/Mac app that swaps firmwares on
the M-VAVE FM-1 over USB MIDI. Stack: **Kotlin + Jetpack Compose**, USB MIDI through
`android.media.midi`.

The repository is new: there is no Gradle project, build, lint or test command yet. When the
project is created, record the build, lint, test and single-test commands here, plus the
check script CI runs (the iOS app's equivalent is `../FM-SWITCH/scripts/check.sh`, which must
pass with no warnings).

## The source app is the spec

Read these in `../FM-SWITCH` before building a feature. Don't copy their contents here:

- `PLAN.md`: the FM-1 update protocol ("How flashing works"), firmware sources, Open
  identities, loaders, switching firmware lines, factory voices, risks.
- `README.md`: the firmware list contract, the simulated FM-1, tests, staying alive mid-flash.
- `REDESIGN.md`: the Standard look (tasks not tabs, the drawn FM-1 as the character, plain
  words, safe by default, motion, feedback and accessibility rules). The 8-bit Phosphor look
  is a hidden **Retro** look found by tapping the FM-1's screen five times.
- `App/`: Swift behaviour to match. `App/Model/DeviceController*.swift` drives flashing and
  defines the states the UI shows (`FlashPhase`: preparing → staging → waitingForLoader →
  writing → verifying → rebooting → succeeded/failed with `canResume`; also `VoiceRestore`
  and `BackupRun`). `App/Catalogue/` is the firmware list code, `App/Standard/` the Standard
  screens, `App/Views/` the Retro ones.

## Architecture constraints

**The protocol has to be rewritten in Kotlin.** On Apple platforms it lives in FM1Kit
(github.com/ocean/FM1Kit), a separate Swift package that isn't checked out on this machine.
`PLAN.md` "How flashing works" is a full spec: USB IDs (`4c4a:c755` normal, `4d4a:4155`
loader), the handshake and UPGRADE SysEx, 7-bit packing, the request/response layout and
checksum, the two phases (stage, then reboot into `ota-` loader and write), resume when an
`ota-` device appears, and `.fwsc` marker stripping. Keep it a UI-free module with its own
tests, mirroring FM1Kit's split: package, protocol, transport, updater.

- **Only probe matching ports** (names like `fm-1|felucca|ota|composite`) so SysEx never
  reaches other MIDI gear.
- **A simulated FM-1 is required.** The iOS tests and the "prototype first" step in
  `REDESIGN.md` run against FM1Kit's `SimulatedFM1`, which can start in rescue mode, "pull the
  cable" at 45% to test resume, and read out of order. Build an equivalent transport, so the
  app can be built and tested without hardware.
- **Progress only goes forwards.** The loader reads out of order, so derive progress from
  served ranges, not the last address.
- **Confirmations follow `PLAN.md`:** SWITCH FIRMWARE? plus an "I've backed up" tick when
  moving between firmware lines; REINSTALL OR UPDATE? for the same identity; refuse V15 over
  V15. Installing over Open firmware needs an arming command first.

**Firmware list.** Download `https://fm-1.drewr.dev/firmwares.json` (built daily by the iOS
repo's `fm1-catalogue`). Ignore a list whose format is newer than the app understands
(`MergedCatalogue.currentFormat` in the Swift code). Don't build the list on the device as the
default path: it costs about 90 GitHub API requests per refresh. Downloaded `.fwsc` files are
kept for offline flashing and checked against their SHA-256.

**Bundled official firmware.** Ship M-VAVE's V15 (`../FM-SWITCH/App/Resources/Firmware/`)
unmodified, with its Apache-2.0 notice. It's 699,956 bytes, SHA-256
`db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a`.

**Licences.** Felucca's `fm1ota.js`/`fm1pkg.js` and fm1_open's `WebUpdate.js`/`I18n.js` are
GPLv3: read them for facts, copy no code. aroum's FM1 Resource Hub has no licence: take facts
only.

**What changes on Android:**

- Staying alive during a flash: a foreground service and a wake lock, in place of iOS's
  auto-lock pause and background grace period. Keep the "keep this screen open" notice and
  rely on loader resume.
- Importing files: the Storage Access Framework, in place of Files.
- Mac-only features have no Android equivalent: Backup & rescue in USB download mode
  (`Helper/`, `Shared/`, `FM1Rescue`, the root flash tool) and ROM bootloader recovery. Say so
  plainly where the app would offer them, as the iOS app does.

## Issues (kata)

This repo shares the kata project `fm-1` with fm1-site, estampie, FM1Kit and FM-SWITCH, and
uses the same label as the iOS app: **`fm-switch`**. The full workflow is the kata block in
`../FM-SWITCH/AGENTS.md`.

- Queue: `kata ready --label fm-switch --agent`. Search before creating:
  `kata search "<terms>" --agent`.
- Sessions act as `drew` and sign with `--teammate fm-switch-android`. Requests:
  `kata inbox --for drew/fm-switch-android --agent`.
- Work for another repo gets that repo's label. To ask the iOS session:
  `kata notify <ref> --to drew/fm-switch --message "<why>"`.
- Never `kata delete` or `kata purge` without authorization. Contacting people outside these
  repos, committing, pushing and releasing are Drew's call.
