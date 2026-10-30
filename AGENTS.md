# AGENTS.md

Guidance for coding agents (Claude Code, Codex and others) working in this repository.

## What this is

An Android port of FM-SWITCH (`../FM-SWITCH`), the iPhone/iPad/Mac app that swaps firmwares on
the M-VAVE FM-1 over USB MIDI. Stack: **Kotlin + Jetpack Compose**, USB MIDI through
`android.media.midi`.

FM-SWITCH and FM1Kit, its protocol package, are Drew Robinson's original work
(github.com/ocean/FM-SWITCH, github.com/ocean/FM1Kit). This Android port is maintained by David
Padilla (github.com/dabit/FM-SWITCH-android).

The repository is new: there is no Gradle project, build, lint or test command yet. When the
project is created, record the build, lint, test and single-test commands here, plus the
local check script (the iOS app's equivalent is `../FM-SWITCH/scripts/check.sh`, which must
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

Work is tracked in kata, project `fm-switch-android` (bound by `.kata.toml`); the workflow is
the managed block below.

- Changes the iOS app or FM1Kit would need belong in Drew's repositories
  (`ocean/FM-SWITCH`, `ocean/FM1Kit`). Don't open issues or pull requests there without asking.
- Committing, pushing, releasing and contacting anyone outside this repo are David's call.

<!-- BEGIN KATA (managed by `kata init --with-agents`) -->
Kata is the system of record for intent.

- Never `kata delete` or `kata purge` without explicit user authorization.

~~~dot
digraph kata {
  rankdir=TB; node [shape=box];

  arrive   [shape=diamond label="Work arrives"];
  search   [label="Search first:\nkata search \"<terms>\" --agent\nReuse an open issue, else create one.\nIf create refuses a look-alike, reuse it;\n--force-new only if the work truly differs.\nNo bound workspace: refs are <project>#<id>,\nor pass --project <project>."];
  route    [shape=diamond label="Work it, or delegate it?"];

  subgraph cluster_work {
    label="";
    claim  [label="Claim and mark it tracked:\nkata claim <ref>\nkata meta set <ref> work.attention ok"];
    branch [label="If the work happens on a dedicated branch, stamp it once:\nkata meta set <ref> work.branch <branch>\nor bind at creation:\nkata create ... --meta work.branch=<branch> --idempotency-key <key>"];
    live   [label="Keep state current:\nkata meta set <ref> work.attention stuck|needs-human|ok\nkata meta set <ref> work.attention_msg \"<why>\"\nstuck = blocked; needs-human = input/review; ok = unblocked.\nRecord progress:\nkata comment <ref> -m \"<what changed>\"\nRequest attention:\nkata notify <ref> --to <actor>[/<teammate>] --message <reason>"];
    claim -> branch -> live;
  }

  subgraph cluster_delegate {
    label="";
    fanout [label="Tracked children: --parent <ref>, --meta work.branch=<branch>,\n--idempotency-key <key>, --json; capture .issue.short_id.\nSubagents: distinct KATA_TEAMMATE and\nKATA_INBOX_USER=<actor>/<teammate>; keep the actor.\nRead requests: kata inbox --for <actor>[/<teammate>].\nAfter handling: kata notify <ref> --to <actor>[/<teammate>] --clear."];
    join   [label="Join with kata wait <refs> --until attention --any\nMatches needs-human or stuck; a close also completes the wait,\nand the reported reason distinguishes which. Use --timeout so a\nwrapper can tell timeout from satisfaction."];
    coord  [label="Read delegated work.*; never write it."];
    fanout -> join -> coord;
  }

  done     [shape=diamond label="Verified complete?"];
  close    [label="kata close <ref> --done --message \"<scope and verification, 40+ chars>\"\n--evidence commit:<sha> | pr:<url> | test:<cmd> | reviewed-paths:<path>\n(or --commit, --pr, --test, --reviewed)"];
  retire   [label="kata close <ref> --message \"<why>\"\n--duplicate-of <ref> | --superseded-by <ref> (20+ chars)\n| --wontfix (60+ chars)"];
  review   [label="kata label add <ref> needs-review --comment \"<what remains>\""];
  park     [shape=diamond label="Park it?"];
  schedule [label="kata schedule <ref> <date-or-time>\nsets scheduled_on; clear with -"];
  someday  [label="kata meta set <ref> someday true --json-value\nclear with kata meta unset <ref> someday"];

  arrive -> search -> route;
  route -> claim   [label="work it"];
  route -> fanout  [label="delegate it"];
  route -> park    [label="record only"];
  live  -> done;
  coord -> done;
  done -> close    [label="yes"];
  done -> retire   [label="duplicate, superseded or dropped"];
  done -> park     [label="no"];
  park -> schedule [label="start date known"];
  park -> someday  [label="start date unknown"];
  park -> review   [label="needs review"];
}
~~~

Inbox reads stay in the selected project. For cross-project work, use kata inbox --for <actor>[/<teammate>] --all without --project or --workspace.

Parent links group work; they do not gate readiness, but a parent cannot close with open children.
Use --blocks <dependent> / --blocked-by <prerequisite> only for real prerequisites; they gate kata ready.
Use --related <ref> for context only. kata wait observes state without requiring a dependency edge.

One writer per key: only write your own work.*. Ignore work.* on closed issues; never write it there.
Before stopping, close completed work or update both work.attention and work.attention_msg for the handoff.

Schedule/someday defer work; deadlines don’t. kata deadline <ref> <date-or-time> sets deadline_on without changing readiness.
Reached schedules and deadlines use notify.* for the current owner, or the author when unowned; clear with kata notify <ref> --to <recipient> --clear.
<!-- END KATA -->
