status: done
# 08 — `:model` data layer: published list, catalogue cache, firmware library, settings, history, start-up

Depends on: 05

## Why
The app's firmware list comes only from the published `firmwares.json` (cached for offline use), downloaded firmwares
are kept and checked against their SHA-256, and the bundled V15 is always available; this pure-JVM layer is what the
device controller and every screen read. Rests on: Firmware list; Scope; Protocol source.

## Description
Plan step 7. Pure Kotlin/JVM in `:model`, package `dev.drewr.fmswitch.model`. Read research.md §3.2, §3.3, §3.6 and §3.8
(semantic drift) first. Virtual-time rule: all waits are `delay` on injected scopes; all file I/O through an injected
`ioDispatcher`; no `Dispatchers.*`, `Thread.sleep`, wall clock (time comes from an injected `now: () -> Instant`) or
`runBlocking`.

**Ports** (`../FM-SWITCH` @ 67ee8c2):
- `App/Catalogue/FirmwareCatalogue.swift:10-177` (`CatalogueEntry`; `Listing` as an Int bitmask;
  `requiresOfficialFirmware` = `loader == "mvave"`, meaning "install the official firmware first", **not** the update
  loader).
- `App/Catalogue/CatalogueBuilder.swift:4-27` — `MergedCatalogue` only, `CURRENT_FORMAT = 1` (`:10`); the builder is
  **not** ported, and there is **no** on-device rebuild fallback (`App/Model/CatalogueStore.swift:157-164` is skipped).
- `App/Model/PublishedList.swift:5-43`: `PublishedList(fetch: suspend (String) -> ByteArray, waits = [2 s, 4 s, 8 s])`;
  `BadStatus(code)` exception; status < 500 → `Unusable` at once; unreadable / `formatVersion > 1` / empty → `Unusable`;
  network failure → wait and retry, then `Unreachable`; `CancellationException` is rethrown, never a failure.
- `App/Model/CatalogueStore.swift:41-59,151-217,257-269` minus `:157-164` and minus `checkSource`, screenshots,
  `chosenBuilds`, `chosenTracks`: `CatalogueStore(directory, list, ioDispatcher, now)`; cache `catalogue.json`; dedupe by
  id on load; sort official first, then name; on failure keep the cached list; message text from `:176-181` (> 1 day old
  vs not).
- `App/Model/FirmwareLibrary.swift` + `App/Model/FirmwareLibrary+Failure.swift:4-37`, minus iCloud, `move(to:)` and
  `downloadOfficialFromMVAVE`: `FirmwareLibrary(directory, bundledOfficial: () -> InputStream, ioDispatcher)`; tests read
  the file at system property `fm1.v15`. The bundled V15 is never copied into `directory`, can't be removed, survives
  `clear()`. `StoredFirmware.id` = file SHA-256; `version` is the literal "Official" for V15. `packagesThatCanFinish(loader)`
  = `FirmwareLibrary.swift:150-169` **with all three clauses** (armed prefix; `ota-`+product; stock loader `ota-FM-1_015`
  + loader-part SHA-256). Download checks `:186-216`: size, SHA-256, product, `NotOfficial`, removes older releases.
- `App/Model/FlashHistory.swift:8-48`; `App/Model/AppSettings.swift` (the in-scope keys of research §3.6 — `appearance`,
  `transportKind`, `showBeta`, `showLinksOnly`, `alwaysViaOfficial`, `startUp`, `firmwareOrder`, the six `simulated*`
  toggles, `acknowledgedNoticeVersion`; tasks 13 and 14 read them) as an interface +
  `InMemoryAppSettings` (the DataStore implementation is task 10's); keys are new, no iOS compatibility; out: look/Retro
  keys, update notices, tip nudges, chosen builds/tracks.
- `App/Model/StartUp.swift:5-102` (incl. `FeluccaUpdateCode` at `:51` and the warning copy `:77-101`),
  `App/Model/FirmwareFilter.swift:5-131`, `App/Model/AddedFirmwares.swift:3-38`, `App/Catalogue/HardwareTesting.swift:13-19`,
  `App/Model/EntryDescriptions.swift`, `App/Catalogue/OtherRelease.swift` only as far as decoding needs it.
- JSON with kotlinx.serialization, `ignoreUnknownKeys = true`, ISO-8601 dates (`java.time.Instant`) in **both** the
  published list and the cache.

**Tests** — `### :model — step 7` in `PLAN.md`'s Testing Strategy, **except** `aPackageThatAcceptsV15IsntFlagged` (it
tests `InstallReadiness`, task 12). Fixture `model/src/test/resources/firmwares-sample.json`: trim it once, now, from
`https://fm-1.drewr.dev/firmwares.json` (`formatVersion 1`, `listing` Ints 1–7, `builds` on one entry, one unknown key);
tests never fetch. Method names:
- `PublishedListTest` (`../FM-SWITCH/AppTests/PublishedListTests.swift:11-34`): `decodesThePublishedShape`,
  `triesAgainAfterTheNetworkFails`, `givesUpAfterItsLastWait`, `doesNotTryAgainWhenTheListIsMissing`,
  `aCancelledDownloadIsNotAFailure`, `refusesANewerFormat`, `refusesAnEmptyList`.
- `CatalogueStoreTest`: `dropsARepeatedIdFromASavedList` (`AppTests/CatalogueTests.swift:216`), `sortsOfficialFirst`,
  `keepsTheSavedListOnFailureAndSaysSoWhenOld`, `keepsTheSavedListOnFailureAndSaysSoWhenRecent`.
- `LibraryTest` (`AppTests/LibraryTests.swift:14-64,85-93`): `comesWithTheOfficialFirmwareAndKeepsItWhenCleared`,
  `readsTheBuiltInCopyRatherThanSavingIt`, `cannotRemoveTheBuiltInCopy`, `savesPackagesUnderTheirNamesWithTheirDetailsBeside`,
  `reloadPicksUpFilesWrittenElsewhere`, `picksThePackagesThatCanFinishALoadersUpdate`,
  `anyPackageCarryingTheStockLoaderCanFinishIt`, `picksThePackageAnArmedLoaderIsWaitingFor`,
  `armedLoaderMatchesAnImportBySha`, `refusesAWrongHash`, `refusesAWrongProduct`, `refusesAnOfficialEntryThatIsntV15`,
  `savesAndReplacesAnOlderRelease`.
- `StartUpTest` (`AppTests/StartUpTests.swift:12-50`): `theAnswerIsRemembered`, `onlyKnownFirmwaresAreFlagged` (inputs
  `:24-29`), `theWarningNamesTheOthersAndHedgesWhenUnsure`.
- `FilterTest` (`AppTests/KindFilterTests.swift`, `FirmwareOrderTests.swift`, `TestFlagTests.swift`, `ResourceHubTests.swift`,
  `SourceBuildTests.swift`): `listsEachKindOnceTheMostCommonFirst`, `matchesAKindWhateverItsCase`, `keepsTheNameOrder`,
  `putsTheNewestReleaseFirstAndUndatedLast`, `countsTheFirmwareHubAndTheOfficialFirmwareAsTested`,
  `marksFirmwaresOnlyInTheResourceHubAsUntested`, `dropsTheHubsFlagFromAPlainVersion`, `marksAnUntaggedBuildAsATest`,
  `isBuiltFromSourceNeedsTheSameSha`.
- `FlashHistoryTest`: `historyKeepsThirtyNewestFirst`, `lastInstallOnlyIfNewestMatches`.

## Files
Every new file goes under one of these paths — nothing at a package root or elsewhere, or the per-task commit
(made by exactly these paths) leaves it out.
- model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/
- model/src/main/kotlin/dev/drewr/fmswitch/model/library/
- model/src/main/kotlin/dev/drewr/fmswitch/model/settings/
- model/src/main/kotlin/dev/drewr/fmswitch/model/history/
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/PublishedListTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/CatalogueStoreTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/FilterTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/library/LibraryTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/settings/StartUpTest.kt
- model/src/test/kotlin/dev/drewr/fmswitch/model/history/FlashHistoryTest.kt
- model/src/test/resources/firmwares-sample.json

## Verification
Run from the repo root after `export JAVA_HOME="$(mise where java@temurin-21)" ANDROID_HOME="$HOME/Android/Sdk"`.
`R=model/build/test-results/test`. `:model` compiles `:fm1`, which task 07 (and later 09) may be writing while this runs:
a compile error in a `fm1/` path is a sibling mid-edit — wait and re-run; only errors under `model/` are this task's red.

1. `./gradlew :model:test --rerun` — expected: `BUILD SUCCESSFUL`.
   red when: any `:model` test fails or the module warns.
2. `comm -23 <(printf '%s\n' decodesThePublishedShape triesAgainAfterTheNetworkFails givesUpAfterItsLastWait doesNotTryAgainWhenTheListIsMissing aCancelledDownloadIsNotAFailure refusesANewerFormat refusesAnEmptyList dropsARepeatedIdFromASavedList sortsOfficialFirst keepsTheSavedListOnFailureAndSaysSoWhenOld keepsTheSavedListOnFailureAndSaysSoWhenRecent comesWithTheOfficialFirmwareAndKeepsItWhenCleared readsTheBuiltInCopyRatherThanSavingIt cannotRemoveTheBuiltInCopy savesPackagesUnderTheirNamesWithTheirDetailsBeside reloadPicksUpFilesWrittenElsewhere picksThePackagesThatCanFinishALoadersUpdate anyPackageCarryingTheStockLoaderCanFinishIt picksThePackageAnArmedLoaderIsWaitingFor armedLoaderMatchesAnImportBySha refusesAWrongHash refusesAWrongProduct refusesAnOfficialEntryThatIsntV15 savesAndReplacesAnOlderRelease theAnswerIsRemembered onlyKnownFirmwaresAreFlagged theWarningNamesTheOthersAndHedgesWhenUnsure listsEachKindOnceTheMostCommonFirst matchesAKindWhateverItsCase keepsTheNameOrder putsTheNewestReleaseFirstAndUndatedLast countsTheFirmwareHubAndTheOfficialFirmwareAsTested marksFirmwaresOnlyInTheResourceHubAsUntested dropsTheHubsFlagFromAPlainVersion marksAnUntaggedBuildAsATest isBuiltFromSourceNeedsTheSameSha historyKeepsThirtyNewestFirst lastInstallOnlyIfNewestMatches | sort -u) <(cat $R/TEST-*.xml | grep -o 'testcase name="[^"]*"' | sed 's/testcase name="//;s/"$//' | sort -u)`
   — expected: no output (all 38 methods ran).
   red when: a step-7 case is missing or renamed.
   control: add `zzNoSuchCase` → prints `zzNoSuchCase`.
   at base: none — `model/` is written by tasks 01 and 08.
3. `grep -ho 'failures="[0-9]*"' $R/TEST-*.xml | sort -u; grep -c '<skipped' $R/TEST-*LibraryTest.xml` — expected: only
   `failures="0"`, then `0`.
   red when: a failure was recorded, or the V15-backed library tests were skipped.
   at base: none — written by this task.
4. `grep -rnE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|Instant\.now|runBlocking|Dispatchers\.' model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue model/src/main/kotlin/dev/drewr/fmswitch/model/library model/src/main/kotlin/dev/drewr/fmswitch/model/settings model/src/main/kotlin/dev/drewr/fmswitch/model/history`
   — expected: no output.
   red when: a hard-coded dispatcher or the wall clock enters the data layer.
   control: `printf 'val t = Instant.now()\n' | grep -nE 'Thread\.sleep|System\.nanoTime|currentTimeMillis|Instant\.now|runBlocking|Dispatchers\.'` → 1 line.
   at base: none — written by this task.
   compliant hits: none.
5. `grep -rnE 'CatalogueBuilder|rebuild|api\.github\.com' model/src/main/kotlin/` — expected: no output (no on-device
   list building).
   red when: the rebuild fallback was ported.
   control: `grep -nE 'CatalogueBuilder|rebuild|api\.github\.com' ../FM-SWITCH/App/Model/CatalogueStore.swift` → includes
   `163: built = try await CatalogueBuilder(fetch: Network.fetch).build(…)` (the Swift fallback this task leaves out).
   at base: none — written by this task.
   compliant hits: none (a comment saying "no rebuild" would match — word it otherwise).
6. Mutation — `FirmwareLibrary.packagesThatCanFinish` (in `model/src/main/kotlin/dev/drewr/fmswitch/model/library/`):
   remove the stock-loader SHA-256 clause; `./gradlew :model:test --rerun --tests '*LibraryTest'` → `FAILED` on
   `anyPackageCarryingTheStockLoaderCanFinishIt`; revert, re-run → green.
   red when: the stock-loader clause is unguarded.
7. Mutation — `PublishedList.load` (in `model/.../model/catalogue/`): catch `Exception` without rethrowing
   `CancellationException`; `./gradlew :model:test --rerun --tests '*PublishedListTest'` → `FAILED` on
   `aCancelledDownloadIsNotAFailure`; revert, re-run → green.
   red when: cancellation is swallowed as a failure.
