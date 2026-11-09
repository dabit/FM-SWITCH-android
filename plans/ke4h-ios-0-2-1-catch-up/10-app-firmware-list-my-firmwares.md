status: pending
# 10 — `:app` firmware list and My firmwares: Most installed, wrapped and remembered filters, pinned sort, saved releases

Depends on: 05, 06, 09

## Why
The list must match iOS 0.2.1. That means a Most installed order with its notes, filters that wrap and are remembered,
a sort and filter that stay pinned, My firmwares with its own order and filter, Markdown descriptions, and a saved tag
that counts every saved release. It renders task 05's orders, scopes and settings keys, task 06's `savedCopies`/
`isSaved`, and task 09's `annotated()`. Rests on: D4, D8.

## Description
Implement **PLAN.md › Implementation Plan › Task 10, steps 1–6**, verbatim; they are binding. It ports iOS
`App/Standard/Firmwares/FilteredFirmwareList.swift:8-115`, `FirmwareListView.swift` and `MyFirmwaresView.swift` @
`fcf4a78`. Read `research-list.md` §3–§6 and "UI copy (iOS, exact)".

**Binding constraints from the plan:**
- The scope row is a `FlowRow`, using the overload **without** `overflow` (no `@ExperimentalLayoutApi` opt-in, and no
  deprecation warning; `app` compiles with `allWarningsAsErrors`).
- Pinning uses `derivedStateOf { listState.firstVisibleItemIndex > filtersIndex }` and an overlay row, **not**
  `stickyHeader`.
- Starring stays out (D8).

**Symbols read** (if one is missing, stop and report; the producer's file isn't this task's):
`FirmwareOrder.MostInstalled`, `FirmwareOrder.firstUncounted`, `FirmwareScope.storedValue`/`fromStored`/`offered`,
`AppSettings.firmwareScope`/`myFirmwaresOrder`/`myFirmwaresScope`, `CatalogueEntry.installs`,
`FirmwareDescription.runs` (task 05); `FirmwareLibrary.savedCopies`/`isSaved` (task 06); `annotated` (task 09).

**Strings.** `strings_firmwares.xml` gets the new list strings only, including `firmware_order_most_installed`
("Most installed"). Delete any list string this task orphans (lint `UnusedResources`). The page strings in that file
(`firmware_untested_warning`, `firmware_install`, `firmware_save_later`, `firmware_remove`) belong to task 11, which
edits the file after this task. Leave them alone.

**Screens (visual review protocol, orchestrator, on the host, before commit):** each in light and dark:
- the list on All;
- Most installed, with the source line and the uncounted note;
- scrolled so the pinned bar shows with a kind picked;
- filters wrapped over two lines;
- My firmwares sorted, filtered and filtered-empty.

The PNGs are `build/shots/t10-<screen>-<light|dark>.png`. The Executor can't run an emulator: report this step as not
run.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit. Task
07 runs alongside and edits other `:app` files (the install sheet, outcomes and `strings_install.xml`). A compile or lint
error whose path is outside this task's `## Files` comes from a sibling mid-edit: wait and re-run.

## Files
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FilteredFirmwareList.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwareListScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/MyFirmwaresScreen.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/home/HomeScreen.kt
- app/src/main/res/values/strings_firmwares.xml

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.

1. `./gradlew :app:compileDebugKotlin` — expected: `BUILD SUCCESSFUL`, with no warnings (they're errors).
   red when: a deprecated or experimental Compose API was used, or a producer symbol is missing.
   at base: `BUILD SUCCESSFUL`.
2. `grep -rn "otherBuild" app/src/main/kotlin` — expected: no output (every `:app` caller uses `savedCopies`/`isSaved`).
   red when: a screen still counts a saved copy by the old `otherBuild` meaning that task 06 narrowed.
   control: `grep -rn "fun otherBuild" model/src/main/kotlin` prints the library's definition.
   at base: 3 hits, in `HomeScreen.kt`, `MyFirmwaresScreen.kt` and `FirmwareListScreen.kt`.
   compliant hits: none.
3. `grep -n "scopeKey\|savedScope" app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwareListScreen.kt` —
   expected: no output (the scope is stored through `settings.firmwareScope`).
   red when: the pick is still kept by `rememberSaveable` and lost on relaunch.
   control: `grep -c "FirmwareScope.fromStored\|fromStored(" app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwareListScreen.kt app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FilteredFirmwareList.kt`
   shows ≥ 1 in total.
   at base: 4 lines: `savedScope(selectedScopeKey)`, `selectedScopeKey = scopeKey(it)`, and the definitions
   `private fun scopeKey(…)` and `private fun savedScope(…)`.
   compliant hits: none.
4. `grep -rnE "ExperimentalLayoutApi|stickyHeader" app/src/main/kotlin; grep -c "FlowRow" app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FilteredFirmwareList.kt`
   — expected: no output from the first; ≥ `1` from the second.
   red when: pinning or wrapping took the experimental route the plan rules out, or the row doesn't wrap.
   control: `printf 'stickyHeader {\n' | grep -nE "ExperimentalLayoutApi|stickyHeader"` prints 1 line.
   at base: no output from the first (the second file doesn't exist).
   compliant hits: none (a comment saying "not stickyHeader" would match, so word it otherwise).
5. `for s in ">Most installed<" "Install counts are from The Firmware Hub" "on The Firmware Hub, so they have no install count." "None of these match the filter picked." "Sort by %"; do grep -cF "$s" app/src/main/res/values/strings_firmwares.xml; done`
   — expected: `1` for each.
   red when: a piece of iOS's list copy is missing or reworded.
   at base: `0` for each.
6. `./gradlew spotlessCheck :app:lintDebug` — expected: `BUILD SUCCESSFUL`.
   red when: an orphaned string or a lint warning in this task's paths.
   at base: `BUILD SUCCESSFUL`.
7. Screens (orchestrator, on the host): the visual review protocol for the screens in the Description. Expected for
   each: matches the cited iOS view; filters wrap rather than scroll sideways; the pinned bar shows the order and the
   picked pill; the uncounted note sits before the first entry with no count; the saved tag reads "%d saved" for two
   releases. Then kill the app and relaunch it: the picked filter stays. That last check is also UAT A2's.
   red when: any PNG shows clipped, overlapping or sideways-scrolling filters, a missing note, or the pick lost on
   relaunch.
   at base: no PNG. The scope row scrolls sideways and is forgotten on relaunch.
