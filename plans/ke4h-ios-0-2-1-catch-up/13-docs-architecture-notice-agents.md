status: pending
# 13 — Docs: architecture against iOS 0.2.1 and FM1Kit 0.6.0, NOTICE facts on MPL-2.0, AGENTS confirmation rule

Depends on: None

## Why
`doc/architecture.md` describes FM1Kit 0.4.5 and iOS 67ee8c2. `NOTICE.md` must record, as facts only, that the iOS app
these ports derive from has been MPL-2.0 since `aac72a9`. AGENTS.md's confirmation rule must cover V16 over V16. The
docs follow the decisions and the symbol names fixed in this plan's task files, so they can be written now. UAT checks
that every name they cite exists. Rests on: D9, D10, D11, P-D5.

## Description
Implement **PLAN.md › Implementation Plan › Task 13, steps 1–3**, verbatim; they are binding.

1. `doc/architecture.md`: a dated "Status: 2026-11-xx" update against `../FM-SWITCH` @ `fcf4a78` and FM1Kit 0.6.0 @
   `53f8511`. Use the **exact symbol names** given in the "What later tasks read from this one" lists of tasks 02–08,
   and `FilteredFirmwareList` (task 10). Cover:
   - the module map: `Fm1FlashChip`, `LeavingGuide`, `FlashChipSupport`, `Fm1DeviceSummary`, `SwitchingGuide`,
     `SwitchingWords`, `FixedFirmware` and `FilteredFirmwareList`;
   - lenient decoding (`coerceInputValues`) and why (D10);
   - keeping every saved release;
   - the three new settings keys (`firmwareScope`, `myFirmwaresOrder`, `myFirmwaresScope`);
   - `StartUp.V16`;
   - V16 never bundled (D9);
   - the stock-loader resume rule (P-D5);
   - the virtual-time quiet windows (task 04);
   - pass 2 counted as Installing (P-D2).
   Where the doc names a file or symbol that a later task in this plan creates, name it as the plan does. Don't invent
   alternatives.
2. `NOTICE.md`: record that the iOS app (`../FM-SWITCH`) has been MPL-2.0 since `aac72a9` (2026-11-08), and that this
   port's `:model`/`:app` code and copy derive from it. **Choose no licence** (D11). The line "FM-SWITCH-Android has no
   licence chosen yet." stays.
3. `AGENTS.md`: "Refuse V15 over V15" → "Refuse V15 over V15 and V16 over V16, unless the person says the FM-1 is really
   running a look-alike (TransitFM1)". Change nothing else in AGENTS.md.

**Executor environment.** Codex in a sandbox: no network, `.git` read-only. Never `git add` or commit. Tasks 02 and 05
run alongside and edit only code. This task edits no code.

## Files
- doc/architecture.md
- NOTICE.md
- AGENTS.md

## Verification
Setup: `cd /home/dabit/git/FM-SWITCH-android`.

1. `grep -c "Refuse V15 over V15 and V16 over V16, unless the person says the FM-1 is really" AGENTS.md; grep -c "^  Refuse V15 over V15\. " AGENTS.md`
   — expected: `1`, then `0`.
   red when: the rule still names only V15, or was duplicated rather than replaced.
   at base: `0`, then `1` (the line `  Refuse V15 over V15. Installing over Open firmware needs an arming command first.`).
2. `grep -c "MPL-2.0" NOTICE.md; grep -c "aac72a9" NOTICE.md; grep -c "has no licence chosen yet" NOTICE.md` —
   expected: ≥ `1`, ≥ `1`, `1`.
   red when: the fact isn't recorded, or a licence was chosen (D11 leaves that to David).
   at base: `0`, `0`, `1`.
3. `for s in LeavingGuide FlashChipSupport Fm1FlashChip Fm1DeviceSummary SwitchingGuide SwitchingWords FixedFirmware FilteredFirmwareList coerceInputValues StartUp.V16 firmwareScope myFirmwaresOrder myFirmwaresScope fcf4a78 53f8511 "FM1Kit 0.6.0"; do printf '%s:%s ' "$s" "$(grep -c "$s" doc/architecture.md)"; done; echo`
   — expected: every count ≥ 1.
   red when: the doc omits a piece of the new module map or one of the two baselines.
   at base: every count is `0` (the doc is against 67ee8c2 and FM1Kit 0.4.5).
4. `grep -n "^Status: 2026-11" doc/architecture.md; grep -c "67ee8c2\|FM1Kit 0.4.5" doc/architecture.md` — expected:
   one Status line, then `0`, unless the remaining mentions are explicitly historical ("was …"). Name them in the
   hand-back.
   red when: the doc still claims the old baselines as current.
   at base: `Status: 2026-10-30. …` (doesn't match the first pattern), then ≥ 1 (the Status line names `67ee8c2` and
   `FM1Kit 0.4.5`).
5. `git status --porcelain -- doc/architecture.md NOTICE.md AGENTS.md` — expected: exactly those three paths, as
   modified.
   red when: a doc edit is missing.
   at base: no output (all three are clean at `f5b9505`).
