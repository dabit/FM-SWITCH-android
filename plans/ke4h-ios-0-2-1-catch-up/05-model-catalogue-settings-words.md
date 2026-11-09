status: pending
# 05 — `:model`: catalogue fields, lenient decoding, orders and scopes, list settings, flash chip words, descriptions, `StartUp.V16`

Depends on: None

## Why
Android needs to keep every field the live list carries (`installs`, `flashChip`, listing 8 = mutant.cloud). An unknown
enum value must not fail the whole list, because Android has no fallback to building the list itself. Then the list
screens, the chip notice and the V16 start-up answer can be built on it. Rests on: D1, D4, D10.

## Description
Implement **PLAN.md › Implementation Plan › Task 05, steps 1–8**, verbatim; they are binding. It ports iOS 8b84ab1 (app
side), 4b4b66e, ecc88d6 and b45179e (model side), bfab3cb + d171b96, 3a5467c (words) and e32be0e (description). Read
`research-list.md` §1 and §3–§8 first. The iOS source is `/home/dabit/git/FM-SWITCH` @ `fcf4a78`.

Split changes, made for parallelism (this task now needs nothing from `:fm1` and runs in wave 1):

- **`HubFlashChip.support` is not in this task.** It maps to `:fm1`'s `FlashChipSupport`, which task 03 writes. Task 07
  adds it as an extension where it's used. `HubFlashChip` here is the `@Serializable` enum (`old`/`new`/`any`) with
  `savesOn` only, in a new file `KM/catalogue/HubFlashChip.kt`. Its test (T05-hub-chip) covers `savesOn`, decoding and
  the round trip through `CatalogueStore`. The `support` assertions move to task 07.
- **Mechanical `:app` fix for `StartUp.V16`.** `SettingsScreen.kt`'s `startUpName` is an exhaustive `when` over
  `StartUp`. Add `StartUp.V16 -> R.string.settings_startup_v16`, and the string `settings_startup_v16` = "Changed by
  V16" in `strings_settings.xml`. Change nothing else in those two files: task 09 owns the rest and runs after this one.

**What later tasks read from this one** (observation 0654; exactly these names):
- `CatalogueEntry.installs: Int?` and `CatalogueEntry.flashChip: HubFlashChip?`; `HubFlashChip.Old`/`New`/`Any` and
  `HubFlashChip.savesOn`.
- `Listing.MUTANT_CLOUD`.
- `FirmwareOrder.MostInstalled` and `FirmwareOrder.firstUncounted(entries)`.
- `FirmwareScope.storedValue`, `FirmwareScope.fromStored(value)` and `FirmwareScope.offered(scopes)`.
- `AppSettings.firmwareScope`, `myFirmwaresOrder` and `myFirmwaresScope`.
- `FlashChipWords.warning(names, hasNewerFlashChip)`, `FlashChipWords.HOW_TO_TELL_QUESTION` ("How would I know?") and
  `FlashChipWords.HOW_TO_TELL`.
- `FirmwareDescription.runs(text)`, `FirmwareDescription.Run(text, bold, italic, code)` and
  `FirmwareDescription.plain(text)`, as members of an `object FirmwareDescription`.
- `StartUp.V16` (label "Changed by V16", detail "M-VAVE's V16 has been installed on it."), placed after `MVave`.

**Fixture (step 7).** The **orchestrator, on the host** fetches the live list before briefing:
`curl -sS https://fm-1.drewr.dev/firmwares.json -o /tmp/claude-1000/ke4h-firmwares.json`. It names that path in the
brief. If the fetch fails, it uses the plan-time copy `/tmp/claude-1000/fw.json` (499 entries, built
2026-11-09T02:10:54Z). The Executor trims ~15 entries from that file into
`model/src/test/resources/firmwares-live-trimmed.json`, covering every bullet in step 7. The orchestrator records the
source URL and fetch date in the commit message.

**DataStore keys (step 6)** have no unit test (Testing Strategy, blocker record). They're proven by compile here and
by UAT A2, where the app is relaunched and the filter stays.

**Test method names are binding:** use exactly the names in Verification step 2, and keep every surviving base case listed there through any rewrite. Where a name differs from the Testing Strategy's wording, step 2 wins; UAT step 3 re-checks the same names.

**Executor environment.** Codex in a sandbox: no network, no emulator, `.git` read-only. Never `git add` or commit.
Tasks 02 (`:fm1`) and 13 (docs) run alongside. A compile or test error whose path is outside this task's `## Files`
comes from a sibling mid-edit: wait and re-run.

## Files
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/Catalogue.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/HubFlashChip.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareFilter.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/PublishedList.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/CatalogueStore.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FlashChipWords.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareDescription.kt
- model/src/main/kotlin/dev/drewr/fmswitch/model/settings/Settings.kt
- model/src/test/resources/firmwares-live-trimmed.json
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/PublishedListTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/CatalogueStoreTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/FilterTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/HubFlashChipTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/FlashChipWordsTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareDescriptionTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/settings/StartUpTest.kt
- app/src/main/kotlin/dev/drewr/fmswitch/data/DataStoreAppSettings.kt
- app/src/main/kotlin/dev/drewr/fmswitch/ui/settings/SettingsScreen.kt
- app/src/main/res/values/strings_settings.xml

## Verification
Setup for every step: `cd /home/dabit/git/FM-SWITCH-android; export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"; M=model/build/test-results/test; FX=model/src/test/resources/firmwares-live-trimmed.json`.
Use the helpers `ran` (prints every expected test method that did not run) and `tally`, defined exactly as in task 02's
Verification setup.
Base: `:model` tests=92, 0 failures (Splitter, `f5b9505`).

1. `./gradlew :model:test --rerun` — expected: `BUILD SUCCESSFUL`.
   red when: any `:model` test fails or the module doesn't compile.
   at base: `BUILD SUCCESSFUL`, 92 tests.
2. `ran $M everyFieldTheLiveListCarriesIsKept reEncodingKeepsEveryValue unknownValuesBecomeNull theSavedListKeepsEveryValue aSavedListWithUnknownValuesStillLoads listedOnNamesMutantCloudAndTheFirmwareHub mutantCloudAloneIsInTesting mostInstalledSortsByCountWithUncountedLast keepsThePickedFilter savesOnSaysWhichFm1sKeepChanges leavesTheChipUnknownWhenTheFeedSaysNothingUseful keepsTheChipWhenTheListIsSaved namesOneFirmwareThatKnowsOnlyTheOlderChip namesSeveralFirmwaresAsAList saysWhenTheFm1HasTheNewerChip dropsTheMarkdownMarks keepsALinksTextButNotTheLink leavesPlainTextAlone readsBoldItalicAndCodeAsRuns leavesAnUnmatchedStarAlone v16IsAStartUp theAnswerIsRemembered decodesThePublishedShape`
   — expected: no output. Covers T05-key-set, round-trip, unknown-enums, listed-on, listing8-untested, hub-chip
   (`savesOn` part), chip-words, most-installed, stored-scope, description and startup-v16, plus two base guards.
   red when: a T05 case is missing or renamed, or a base guard was deleted.
   control: `ran $M zzNoSuchCase` prints `zzNoSuchCase`.
   at base: prints the 21 new names; the two guards ran.
3. `tally $M` — expected: `tests=` ≥ 113 (92 + 21), `failures=0 errors=0 skipped=0`.
   red when: a base test was deleted, or a failure or skip was recorded.
   at base: `tests=92 failures=0 errors=0 skipped=0`.
4. `jq -r '[.entries[]|keys[]]|unique|join(" ")' $FX; jq -r '[.entries[].flashChip]|unique|map(tostring)|join(" ")' $FX; jq -r '[.entries[].listing]|(index(8)!=null),(index(15)!=null)' $FX; jq -r '[.entries[]|select(.installs!=null)]|length' $FX`
   — expected: the key list includes `flashChip` and `installs`; the chip values include `any new null old`; `true`
   twice (listings 8 and 15 present); a count ≥ 1.
   red when: the fixture can't exercise the new fields (T05-key-set's positive precondition).
   at base: none. The fixture is written by this task. The plan-time list `/tmp/claude-1000/fw.json` gives keys
   `author beta builds date flashChip id installs isOfficial kinds licence links listing loader name packageOrigin packageURL product releaseURL repo sha256 size sourceBuild stars version what whatSource`,
   chip values `null:338 any:10 new:54 old:97`, and listings `1,2,4,7,8,9,10,11,12,13,14,15`.
5. `grep -c "coerceInputValues" model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/PublishedList.kt model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/CatalogueStore.kt`
   — expected: `:1` or more for each file. ktlint may wrap `= true` onto the next line, as it does for
   `ignoreUnknownKeys`, so the pattern is the name alone. Step 7 proves the value.
   red when: one of the two decoders is still strict.
   at base: `PublishedList.kt:0` and `CatalogueStore.kt:0`.
   compliant hits: a KDoc that names `coerceInputValues` would also count. Step 7's mutation is the real proof.
6. Mutation T05-key-set. In `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/Catalogue.kt`, remove `installs`
   from `CatalogueEntry`, and its uses in `FirmwareFilter.kt`. Then run
   `./gradlew :model:test --rerun --tests '*PublishedListTest'`: expected `FAILED` on
   `everyFieldTheLiveListCarriesIsKept`. Revert: green.
   red when: a dropped field could pass unnoticed (observation 0655).
   at base: none. The test is written by this task.
7. Mutation T05-unknown-enums. In `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/PublishedList.kt`, remove
   `coerceInputValues`. Then run `./gradlew :model:test --rerun --tests '*PublishedListTest'`: expected `FAILED` on
   `unknownValuesBecomeNull`. Revert. Do the same in `CatalogueStore.kt` and run `--tests '*CatalogueStoreTest'`:
   expected `FAILED` on `aSavedListWithUnknownValuesStillLoads`. Revert: green.
   red when: a test reaches a test-local `Json` instead of the production instance.
   at base: none. The tests are written by this task.
8. Mutation T05-most-installed. In `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareFilter.kt`, sort
   by `installs ?: Int.MAX_VALUE`. Then run `./gradlew :model:test --rerun --tests '*FilterTest'`: expected `FAILED` on
   `mostInstalledSortsByCountWithUncountedLast`. Revert: green.
   red when: the uncounted-last rule is unguarded.
   at base: none. The test is written by this task.
9. `./gradlew :app:compileDebugKotlin` — expected: `BUILD SUCCESSFUL`. `DataStoreAppSettings` implements the three new
   keys, and `startUpName` covers `V16`.
   red when: the interface grew but the DataStore implementation or the exhaustive `when` didn't.
   at base: `BUILD SUCCESSFUL`.
10. `grep -n 'name="settings_startup_v16">Changed by V16<' app/src/main/res/values/strings_settings.xml` — expected: one
    hit.
    red when: the V16 start-up row has no label, or a label other than iOS's.
    at base: no output (the string doesn't exist).
    compliant hits: none.
