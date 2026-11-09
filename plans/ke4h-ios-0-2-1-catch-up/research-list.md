# Research: the firmware list (ke4h)

Read-only; I edited nothing. Repos: Android `/home/dabit/git/FM-SWITCH-android` at HEAD f5b9505; iOS `/home/dabit/git/FM-SWITCH`, `git rev-parse HEAD` = fcf4a78.

## §0 Premises

- **P1 CONFIRMED.** `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/Catalogue.kt:41-67` has no `installs` and no `flashChip`. iOS has both at `App/Catalogue/FirmwareCatalogue.swift:95,102`. In the live list, 158 of 499 entries carry `installs` (max 8992, `sloop`) and 161 carry `flashChip` (97 `old`, 54 `new`, 10 `any`).
- **P2 CONFIRMED.** Counts from the live list:
  - 117 entries have `listing==8`.
  - 456 entries have bit 8 set in some combination.
  - `listedOn` (`Catalogue.kt:105-118`) only tests bits 1, 2 and 4, so the 117 show "Listed on: (blank)".
  - These entries already get "In testing", because `isHardwareTested` (`Catalogue.kt:73`) only tests HUB|EDITOR, which matches iOS `HardwareTesting.swift` (`testedElsewhere` is empty).
- **P3 CONFIRMED, and it covers more than the brief says.** Two `Json` instances lack `coerceInputValues`: `CatalogueStore.kt:93` (cache) and `PublishedList.kt:53-57` (the network path, which matters more).
  - Neither sets `encodeDefaults`, so defaults are left out when encoding.
  - iOS would also fail to decode a future `flashChip` value, because `HubFlashChip` is a synthesised `String, Codable`. But iOS falls back to building the list itself. Android has no fallback, so Android must be more lenient than its source.
  - The cache is written by re-encoding the Kotlin model (`CatalogueStore.kt:64`), so any field the model lacks is also removed from `catalogue.json` on disk.
- **P4 CONFIRMED.** f1d34f9 changes only Home naming, the "On your FM-1" tag, pull-to-rescan, haptics, link sharing, GitHub stars, "Save a copy" and the launch screen (`git show --stat f1d34f9`). None of the ten list commits are in Android.
- **P5 CONFIRMED.** The live key set is exactly `CatalogueEntry`'s stored properties. `otherRelease` is absent because 0 of 499 entries have one. `flashChip` values are only `old/new/any`, which are `HubFlashChip`'s raw values (`HubFlashChip.swift:8-14`). The builder maps the Hub's `chip` through `HubFlashChip(rawValue:)` (`FirmwareCatalogue.swift:360`), so unknown values become nil before publishing. `installs` is a plain Int (`:95,359`). `formatVersion` is still 1 with the new fields present, so iOS did not bump the current format.
- **Brief premise "Android has both looks (Standard and Retro)": REFUTED.**
  - `grep -rniE "retro|phosphor"` over every `*.kt` returns nothing.
  - `plans/android-port/PLAN.md:17-18` defers Retro, and `:307` says "No Retro, no 5-tap easter egg".
  - `doc/architecture.md:5-6` says "Standard look only".
  - So the iOS Retro files have no Android counterpart. They are not applicable, not gaps: `App/Views/CatalogueView.swift`, `FirmwareDetailView.swift`, `LibraryView.swift`, `RetroReleaseChoice.swift`, `FlashConfirmView.swift`, `FlowLayout.swift` (Retro use) and all of 8d5f255.

## Per-feature: iOS, Android today, gap, files to touch

### 1. mutant.cloud, "The Firmware Hub" capitals, `installs` field (8b84ab1)

- **iOS:**
  - `Listing.mutantCloud = 1<<3` (`FirmwareCatalogue.swift:18`).
  - `listedOn` adds "mutant.cloud" and renames "Firmware hub" to "The Firmware Hub" (`App/Model/EntryDescriptions.swift:4-14`).
  - `packageSource` `.hub` reads "The Firmware Hub mirror" (`:27`).
  - The untested copy is updated on the page (`FirmwarePageView.swift:116-123`) and before install (`App/Model/InstallReadiness.swift:146-149`).
  - Settings sources (`StandardSettingsView.swift:133-134`) and the rescue copy (`App/Views/SafetyNoticeView.swift:39,47,57`, `HelpTopic.swift:82,153`) are updated too.
  - Everything else in this commit is builder-side: `CatalogueBuilder*`, `MutantCloudListing`, `ResourceHubListing`, `ListedFirmware`.
- **Android today:**
  - `Listing` lacks `MUTANT_CLOUD` (`Catalogue.kt:139-143`).
  - `listedOn` and `packageSource` use the old strings (`Catalogue.kt:110,130`).
  - Old copy remains at `strings_firmwares.xml:22`, `strings_install.xml:10` and `strings_settings.xml:7-8` (`settings_safety_phone`, `settings_rescue_tools`, `settings_about_sources`).
- **Files:** `Catalogue.kt`, `strings_firmwares.xml`, `strings_install.xml`, `strings_settings.xml`, `FilterTest.kt`.

### 2. Keep each saved release; card says how many (38c0837)

- **iOS:**
  - `FirmwareLibrary+Releases.swift` adds `savedCopies(of:)`.
  - Download no longer deletes older copies (`FirmwareLibrary.swift` diff: "Other releases and builds saved stay").
  - `otherBuild` now requires the same version, using `ReleaseVersion.differs`.
  - `FirmwareFilter.isSaved` = `!savedCopies.isEmpty && outdated == nil` (`App/Model/FirmwareFilter.swift:28-30`).
  - The card tag reads "N saved" when there is more than one (`FirmwareListView.swift:248-251`).
- **Android today:**
  - Download deletes every copy with the same `catalogueID` (`FirmwareLibrary.kt:236-239`).
  - `otherBuild` is unconditional (`:124-125`) and has no `differs`; `ReleaseVersion` at `:406` only has `isNewer`.
  - The card uses `stored||otherBuild` (`FirmwareListScreen.kt:380`).
  - `LibraryTest.kt:179 savesAndReplacesAnOlderRelease` asserts the behaviour iOS removed, so it must be inverted, not just supplemented. The iOS equivalent is `keepsEachReleaseItSaves` in `AppTests/LibraryTests.swift`.
- **Not applicable:** the stable/test ✓ ticks in `ReleaseChoice`. Android has no ReleaseChoice (it was out of scope in `plans/android-port/15-...md:7`), and 0 of 499 live entries have `otherRelease`.
- **Files:** `FirmwareLibrary.kt`, `FirmwareCatalogue`/`FirmwareFilter.kt` (`isSaved`), `FirmwareListScreen.kt`, `MyFirmwaresScreen.kt:51-56` (move to the `savedCopies().isNotEmpty()` shape), `strings_firmwares.xml` (plural "%d saved"), `LibraryTest.kt`.

### 3. Most installed order (4b4b66e)

- **iOS:**
  - `FirmwareOrder.installs` sorts by `installs ?? -1` descending, stable on the name index (`FirmwareFilter.swift:133,160-163`).
  - `firstUncounted` (`:169-172`) places a note before the first uncounted entry, only when some entries are counted (`FirmwareListView.swift:57-59,118-128`).
  - A source line appears in the summary (`:108-110`).
  - The details row reads `"Installs", "\(n.formatted()) on The Firmware Hub"` (`FirmwarePageView.swift:257-259`).
  - Test: `AppTests/FirmwareOrderTests.swift:23-36`.
- **Android today:** `FirmwareOrder { ByName, Updated }` (`FirmwareFilter.kt:78-95`). The two-item dropdown is hard-coded (`FirmwareListScreen.kt:319-342`); iterate `FirmwareOrder.entries` instead.
- **String collision:** `firmware_installs` ("Installs", `strings_firmwares.xml:36`) is already the label of the requires-official row. Reuse it as the label and add only a value string. Use `NumberFormat.getIntegerInstance()` in place of `.formatted()`.
- **Storage:** order is stored by enum `name` (`DataStoreAppSettings.kt:76-78`), so adding `MostInstalled` is safe.
- **Files:** `FirmwareFilter.kt`, `FirmwareListScreen.kt`, `FirmwarePageScreen.kt` (Details around `:326`), `strings_firmwares.xml`, `FilterTest.kt`.

### 4. Wrapped filters and a remembered filter (ecc88d6)

- **iOS:**
  - `FirmwareScope.storedValue`, `init(storedValue:)` and `offered(in:)` (`FirmwareFilter.swift:94-114`).
  - `AppSettings.firmwareScope` with key `"firmwareScope"` (`App/Model/AppSettings.swift:89-90,172,228`).
  - `FlowLayout` wrapping replaces the sideways scroll.
  - Test: `KindFilterTests.swift:28 keepsThePickedFilter`.
- **Android today:**
  - The scope lives in `rememberSaveable` only (`FirmwareListScreen.kt:91`), using the private `scopeKey`/`savedScope` (`:252-266`) with `"built-from-source"`. iOS uses `"builtFromSource"`; keys need no iOS compatibility (`plans/android-port/PLAN.md:218-219`), so either works.
  - The row uses `horizontalScroll` (`:308-312`); swap it for `FlowRow`.
  - The Compose BOM is 2026.09.00 (`gradle/libs.versions.toml:4`). I did not check whether `FlowRow` still needs `@OptIn(ExperimentalLayoutApi::class)` at that BOM; compiling once will show it.
  - There is no `offered()` fallback.
- **Files:** `FirmwareFilter.kt` (move `scopeKey`/`savedScope` into `FirmwareScope`, add `offered`), `Settings.kt`, `DataStoreAppSettings.kt`, `FirmwareListScreen.kt`, `FilterTest.kt`.

### 5. Pinned sort and filter; My firmwares gets its own (b45179e)

- **iOS:**
  - `App/Standard/Firmwares/FilteredFirmwareList.swift:8-115` is shared by both screens. When the filters scroll away and the pick is not All, the order menu and picked pill pin under the title (`:43-54`). Tapping the pinned pill scrolls back to the filters, with hint "Shows the filters".
  - `MyFirmwaresView.swift:30-45` uses `myFirmwaresOrder`/`myFirmwaresScope` (`AppSettings.swift:93-98,173-175,229-230`), with scopes computed from saved+starred.
  - The sort label is `"Sort by \(order.name)"` (`FilteredFirmwareList.swift:99,113`).
- **Android today:** `MyFirmwaresScreen.kt:42-115` has no sort, no filter and no Starred group.
  - Starring existed at the iOS baseline (`git show 67ee8c2:…MyFirmwaresView.swift` mentions "Starred") but was never ported. That is a pre-existing gap, outside this slice unless David wants it.
  - Pinning on Android: a `LazyListState.firstVisibleItemIndex`-driven overlay, or `stickyHeader`.
- **Files:**
  - new `ui/firmwares/FilteredFirmwareList.kt`, which pulls `ScopePicker` out of `FirmwareListScreen.kt:299-368`;
  - `MyFirmwaresScreen.kt`, `FirmwareListScreen.kt`;
  - `Settings.kt` and `DataStoreAppSettings.kt` (two new keys).

### 6. Hub chip field and "Saves on" (bfab3cb + d171b96)

- **iOS:** `HubFlashChip` `old/new/any` (`App/Catalogue/HubFlashChip.swift:8-33`), with `savesOn` copy at `:26-32`. The details row `"Saves on"` sits after "Tested on an FM-1" (`FirmwarePageView.swift:265`). Tests: `AppTests/FlashChipTests.swift:19-34`.
- **Android:** this does not exist.
- **Files:** `Catalogue.kt` (enum with `@SerialName`; `val flashChip: HubFlashChip? = null`; `val installs: Int? = null`; Kotlin `savesOn` beside `listedOn`), both `Json` configs (`coerceInputValues = true`), `FirmwarePageScreen.kt`, `strings_firmwares.xml` (label).

### 7. Older-chip warning (3a5467c)

- **iOS:**
  - `FlashChipWords` (`App/Model/FlashChipWords.swift:8-36`) and `FlashChipNotice` (`App/Standard/Firmwares/FlashChipNotice.swift:5-29`, with a "How would I know?" disclosure when the chip is not known to be newer).
  - Firmware page: the notice shows when `entry.flashChip == .old && device.flashChipID != p25q80` (`FirmwarePageView.swift:136-143`, placed after `untested`).
  - Before install: `package.flashChipSupport ?? listed.flashChip.support` (`InstallReadiness.swift:85-91`, `BeforeYouStartView.swift:111-115`).
- **Depends on the sibling:**
  - Android has no `flashChipID` and no package `flashChipSupport` (`grep -i "flashchip|jedec"` finds nothing). Both come from FM1Kit 0.6.0, the sibling's slice.
  - The page notice can ship now with `hasNewerFlashChip = false`, which is what iOS shows when the FM-1 has not said which chip it has. It will then show for all 97 `old` entries until the sibling wires `flashChipID`.
  - The before-install fallback can attach at the existing catalogue lookup seam, `BeforeYouStartSheet.kt:79` (`catalogueItem?.isHardwareTested`).
- **Files:** new `model/.../catalogue/FlashChipWords.kt` (pure; testable like iOS `FlashChipTests.swift:36-45`), new `ui/firmwares/FlashChipNotice.kt`, `FirmwarePageScreen.kt` (after the warnings at `:160-164`), `BeforeYouStartSheet.kt` and `InstallReadiness.kt`. The last two are shared with the sibling.

### 8. Earlier saved versions switcher; Markdown descriptions (e32be0e)

- **iOS:**
  - `FirmwarePageView+Saving.swift:16-24` (`earlier`), `:27-33` (`installable`, `shownVersion`), `:35-70` (note plus "Delete this saved version" in danger style).
  - `versionChoice` is a segmented control, newest first (`FirmwarePageView.swift:338-351`).
  - Button labels: `Install \(name)\(shownVersion)` (`:213`) and `Save\(shownVersion) to this … for later` (`+Saving.swift:7`).
  - `FirmwareDescription.styled`: inline-only Markdown, links shown as plain text (`App/Catalogue/FirmwareDescription.swift:7-16`), used on the card (`FirmwareListView.swift:190-191`) and the page (`FirmwarePageView.swift:81-82`).
  - New `Palette.danger` (light 0xA8380A, dark 0xFF9259), with a contrast row added.
- **Android today:**
  - The page knows only `stored` (`FirmwarePageScreen.kt:170-240`).
  - The remove action is a plain `TextButton` labelled "Remove" (`:225-236`).
  - Descriptions are raw text (`:153`, `FirmwareListScreen.kt:422`).
- **Scope evidence:** 1 of 499 live descriptions uses bold (`aisling`); 0 use code, links or italics. A small inline-only `AnnotatedString` builder (bold, italic and code; link text kept, target dropped) beats adding a dependency.
- **Files:**
  - `FirmwarePageScreen.kt`, maybe split into `FirmwarePageSaving.kt`;
  - new `model/.../catalogue/FirmwareDescription.kt` (plain/styled, tested like `FirmwareDescriptionTests.swift:8-20`);
  - `ui/theme/Palette.kt` (danger), `app/src/test/.../ui/theme/ContrastTest.kt` (add the pair), `ui/components/ButtonStyles.kt`;
  - `FirmwareLibrary.kt` (needs `savedCopies` from §2).

## UI copy (iOS, exact)

- **Orders** (`FirmwareFilter.swift:144-146`): "Name", "Updated", "Most installed".
- **Most installed notes** (`:136`, `:138`):
  - "Install counts are from The Firmware Hub's site."
  - "These aren't on The Firmware Hub, so they have no install count."
- **Details rows:**
  - "Installs" / "%s on The Firmware Hub".
  - "Saves on" / "Older FM-1s only" | "All FM-1s" | "All FM-1s (doesn't check the chip)".
- **Listed on:** "The Firmware Hub", "fm1-editor.com", "FM1 Resource Hub", "mutant.cloud", joined with " + ".
- **File from:** "The Firmware Hub mirror".
- **Page untested notice:** "In testing: not yet known to work on a real FM-1. It's only listed in aroum's FM1 Resource Hub or on mutant.cloud, not on The Firmware Hub or fm1-editor.com."
- **Before-install untested notice:** `InstallReadiness.swift:146-149`.
- **Settings sources:** `StandardSettingsView.swift:133-134`.
- **Card tag:** "Saved" or "%d saved".
- **My firmwares:**
  - Heading: "Saved on this \(ThisDevice.name)" (Android already has "Saved on this phone").
  - Filtered-empty: "None of these match the filter picked."
  - Starred-empty: `MyFirmwaresView.swift:82`.
- **Pinned pill hint:** "Shows the filters". **Sort accessibility label:** "Sort by %s".
- **Chip notice:** `FlashChipWords.swift:11-35`, verbatim, including "How would I know?" and the M-VAVE logo guide.
- **Saved versions:**
  - "Saved, ready for offline install"
  - "Delete this saved version"
  - "Comes with the app, so it installs even offline."
  - Picker newest label: `entry.version ?? "Newest"`.

**Where Android keeps copy:**
- Composable copy goes in `app/src/main/res/values/strings_*.xml`, one file per area. `strings_firmwares.xml` is the list/page file. Only `values/` exists, so there are no translations.
- Model-layer copy is Kotlin constants in `:model`: `Catalogue.kt:105-137` (`listedOn`/`packageSource`), `Settings.kt:22-32`, `CatalogueStore.kt:75`.
- Follow that split: order/scope labels and notices go in XML (as `firmware_order_*` already do), while `savesOn`, `listedOn` and `FlashChipWords` go in Kotlin, so tests can read them.

## Settings persistence

- `AppSettings` is an interface with `InMemoryAppSettings` for tests (`model/.../settings/Settings.kt:74-106`).
- The DataStore Preferences implementation is `app/src/main/kotlin/dev/drewr/fmswitch/data/DataStoreAppSettings.kt`. A property getter reads `values[KEY]`, and the setter calls `save(KEY, v)` (`:76-78`, `:108-114`). Keys are declared in the companion (`:130-143`, e.g. `FIRMWARE_ORDER = stringPreferencesKey("firmwareOrder")`).
- To add: `firmwareScope`, `myFirmwaresOrder`, `myFirmwaresScope` as string keys, with the scope stored through a `FirmwareScope.storedValue` that lives in `:model`.
- The UI reads `model.settings.firmwareOrder` and mirrors it into local state (`FirmwareListScreen.kt:93-96`).

## Tests and fixture

- **Existing tests:**
  - `model/src/test/kotlin/dev/drewr/fmswitch/model/catalogue/FilterTest.kt`: kinds, order, tested, scopes.
  - `PublishedListTest.kt`, which decodes `model/src/test/resources/firmwares-sample.json` (7 hand-made entries, listing 1–7, no bit 8, no `installs`/`flashChip`).
  - `CatalogueStoreTest.kt`, `FirmwareBadgeDesignTest.kt`.
  - `model/.../library/LibraryTest.kt` (`:179` to invert).
  - `model/.../device/StandardLookTest.kt`.
  - `app/src/test/.../ui/theme/ContrastTest.kt`.
- **Fixture recommendation:** add `model/src/test/resources/firmwares-live-trimmed.json`, about 15 entries cut from `/tmp/claude-1000/fw.json`. It should cover:
  - listing 8 and 15;
  - `flashChip` old, new, any and absent;
  - `installs` present and absent;
  - every `packageOrigin` (`hub`, `githubRelease`, `publisherManifest`, `projectPage`, `mvave`) plus null;
  - `whatSource: gitHub`;
  - `builds`, `sourceBuild`;
  - `aisling` (bold).
  - Add a second fixture, or inline JSON, with `flashChip:"future"` and `packageOrigin:"zzz"`, to prove `coerceInputValues` turns them into null without failing the whole decode.
- **Key-set round-trip test:** decode, then encode with the production `Json`, then compare key sets per entry, through both `PublishedList.json` and the `CatalogueStore` cache path.
  - **Trap:** `encodeDefaults` is false, so a naive comparison reports `beta`, `listing`, `isOfficial`, `kinds` and `links` as dropped.
  - Compare only keys whose source value differs from the Kotlin default, or use a test-only `Json { encodeDefaults = true; ignoreUnknownKeys = true }`.
  - Assert that the source key set ⊆ the model's `serialDescriptor` element names. That assertion is what catches the next field iOS adds (observation 0655).

## Past plans and docs

- `plans/android-port/15-firmwares-list-page-mine.md` (status done): built the list, page and My firmwares from the published JSON. The release/build picker, stars and screenshots were out of scope (`:7`). Strings go in `strings_firmwares.xml` only (`:13-14`).
- `plans/android-port/08-model-data-layer.md` (status done): the `:model` published list, cache, library ("removes older releases", PLAN.md:216-217) and settings ("Keys are new (no iOS compatibility)", PLAN.md:218-219).
- `plans/android-port/research.md` and `17-uat.md` are the prior research and UAT.
- `doc/architecture.md`, 13.8K, dated 2026-10-30 against 67ee8c2 / FM1Kit 0.4.5, "Standard look only". It covers the catalogue at module level (`:12-20`).

## Recommended `## References`

- `../FM-SWITCH/App/Catalogue/FirmwareCatalogue.swift:12-19,95,102,266-267,302-303,359-360`: Listing bit 8, `installs`, `flashChip`.
- `../FM-SWITCH/App/Catalogue/HubFlashChip.swift:8-33`: chip values and `savesOn` copy.
- `../FM-SWITCH/App/Model/FirmwareFilter.swift:27-30,94-114,126-173`: `isSaved`, `storedValue`/`offered`, `FirmwareOrder.installs`/`firstUncounted`.
- `../FM-SWITCH/App/Model/AppSettings.swift:86-98,171-175,227-230`: four list settings.
- `../FM-SWITCH/App/Model/EntryDescriptions.swift:4-14,27`: `listedOn`, `packageSource` copy.
- `../FM-SWITCH/App/Model/FirmwareLibrary+Releases.swift:1-15`: `savedCopies`.
- `../FM-SWITCH/App/Model/FlashChipWords.swift:8-36`; `../FM-SWITCH/App/Standard/Firmwares/FlashChipNotice.swift:5-29`.
- `../FM-SWITCH/App/Model/InstallReadiness.swift:85-91,146-149`: chip fallback, untested copy.
- `../FM-SWITCH/App/Standard/Firmwares/FilteredFirmwareList.swift:8-115`: wrap and pin.
- `../FM-SWITCH/App/Standard/Firmwares/FirmwareListView.swift:55-70,108-128,241-260`: uncounted note, tags.
- `../FM-SWITCH/App/Standard/Firmwares/MyFirmwaresView.swift:12-45,77-82`.
- `../FM-SWITCH/App/Standard/Firmwares/FirmwarePageView.swift:81-82,114-143,207-215,250-276,338-351`.
- `../FM-SWITCH/App/Standard/Firmwares/FirmwarePageView+Saving.swift:6-70`.
- `../FM-SWITCH/App/Catalogue/FirmwareDescription.swift:7-21`.
- `../FM-SWITCH/AppTests/FirmwareOrderTests.swift:23-36`, `KindFilterTests.swift:28-36`, `FlashChipTests.swift:19-45`, `FirmwareDescriptionTests.swift:8-20`.
- `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/Catalogue.kt:41-67,73,105-143`.
- `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/FirmwareFilter.kt:61-95`.
- `model/src/main/kotlin/dev/drewr/fmswitch/model/catalogue/CatalogueStore.kt:46,64,93`; `PublishedList.kt:38,53-57`.
- `model/src/main/kotlin/dev/drewr/fmswitch/model/library/FirmwareLibrary.kt:108-125,236-239,406`.
- `model/src/main/kotlin/dev/drewr/fmswitch/model/settings/Settings.kt:74-106`; `app/src/main/kotlin/dev/drewr/fmswitch/data/DataStoreAppSettings.kt:76-78,108-143`.
- `app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/FirmwareListScreen.kt:91-108,252-368,380,422`.
- `app/src/main/kotlin/dev/drewr/fmswitch/ui/firmwares/MyFirmwaresScreen.kt:51-63`; `FirmwarePageScreen.kt:153-240,318-330`.
- `app/src/main/kotlin/dev/drewr/fmswitch/ui/install/BeforeYouStartSheet.kt:79,216-218`.
- `app/src/main/res/values/strings_firmwares.xml:9-10,15-22,36`; `strings_install.xml:10`; `strings_settings.xml:7-8`.
- `model/src/test/kotlin/dev/drewr/fmswitch/model/library/LibraryTest.kt:179`; `model/src/test/resources/firmwares-sample.json`.
- `plans/android-port/PLAN.md:17-18,216-219,307`.

## Architecture doc

`doc/architecture.md` exists, so I'm not drafting a new one. It is stale: 67ee8c2 / FM1Kit 0.4.5, Standard-only. As part of this work, I recommend a dated update to it noting:
- the new catalogue fields (`installs`, `flashChip`, listing bit 8);
- the decision to decode leniently (`coerceInputValues` on both `Json` instances, and why Android must be more lenient than iOS);
- keeping every saved release;
- the three new settings keys;
- the shared `FilteredFirmwareList`.

## Open items

- **Not yet read, decidable from the repo:** whether `FlowRow` needs an opt-in at BOM 2026.09.00. Compiling once will show it.
- **Not decidable from the repo:** none for this slice. The chip notice's device input is the sibling's FM1Kit 0.6.0 work.

The task-observer protocol was not run: this subagent has no Skill tool. No observations were logged.
