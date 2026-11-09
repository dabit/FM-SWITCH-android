# Research: V16 and FM1Kit 0.6.0 (ke4h)

## V16 support "like iOS": research for plans/ke4h-ios-0-2-1-catch-up/PLAN.md

Nothing was edited. Paths below are short forms of these:
- **FK** = /tmp/claude-1000/-home-dabit-git-FM-SWITCH-android/5e980def-0c27-4c1a-9352-a41b3172dfbb/scratchpad/FM1Kit (FM1Kit 0.6.0, 53f8511)
- **iOS** = /home/dabit/git/FM-SWITCH (fcf4a78)
- **A** = /home/dabit/git/FM-SWITCH-android

I fetched `firmwares.json` to confirm the official entry. I did not download V16 itself.

### §0 Premises
- **P1 CONFIRMED, with a correction.** The live entry is `product FM-1_016, size 718196, sha d017…`, so the size and sha checks at A/model/…/library/FirmwareLibrary.kt:207-212 pass. The download then fails at :235 with `NotOfficial`, not with `HashMismatch`, because :219 accepts only V15. "Like iOS" goes further than accepting V16:
  - iOS skips the size, sha and product checks for `isOfficial` entries (iOS/App/Model/FirmwareLibrary.swift:192-199, "M-VAVE serve whichever is newest at one link").
  - It checks the file against FM1Kit's known hashes instead (`OfficialFirmware.official(_)`, iOS/App/Model/OfficialFirmware+Saved.swift:5-7).
- **P2 CONFIRMED.** Every fm1 file is headed "Ported from FM1Kit 0.4.5". There is no V16 anywhere:
  - `OfficialFirmware.known = [V15, OPEN_V12]` (A/fm1/…/pkg/OfficialFirmware.kt:30).
  - Only `officialV15` exists (A/fm1/…/protocol/Fm1Identity.kt:43).
  - `InstallCheck` treats only V15 over V15 as a reinstall (A/fm1/…/updater/InstallCheck.kt:32), so V16 over V16 comes out as SAME_IDENTITY.
- **P3 CONFIRMED in part.** "Finish when the new firmware answers" (fc809ca) is **not** needed for V16:
  - R2 (V15→V16) and R9 (MIDIMON→V16) finished the normal way, before that commit existed (FK/HARDWARE_TESTING.md:921,950).
  - fc809ca was prompted by an AMB-1 install.
  - What V16 does need is listed in §1.
- **P4 PARTLY REFUTED.** Going from V16 back to V15 **installs fine over USB-MIDI** (R3, FK/HARDWARE_TESTING.md:923).
  - What needs USB download mode (a computer) is clearing the data V16 leaves at 0xFBD80 that makes V15 play one note at a time. Restoring voices and V15's MONO/POLY keys don't clear it (:926-936).
  - Android is worse off than iOS. Factory-voice restore was deferred (A/plans/android-port/PLAN.md:18), and nothing in model/ or app/ handles voices. A person who goes V16→V15 on Android also gets broken voices, which R3 saw ("V15's voices were broken; restore-voices fixed them"), with no way to fix them in the app.
- **P5 CONFIRMED, small scope.** FM1Kit's own simulator does nothing special for V16.
  - It only needs `installedIdentity = .officialV16` and a synthetic package with product `FM-1_016` (FK/Tests/FM1KitTests/OfficialReleaseTests.swift:16-30).
  - Its boot-area bookkeeping resets only for V15 (FK/Sources/FM1Kit/Transport/SimulatedFM1.swift:321).
  - On Android, the minimum is to change `== Fm1Identity.officialV15` to `isOfficial` at A/fm1/…/transport/SimulatedFm1.kt:212.
  - iOS 0.2.1 has no "simulated V16" switch. Hash recognition can't be tested without the real file, so test the constants and the synthetic identity only.

### §1 FM1Kit 0.4.5→0.6.0, classified (Sources/FM1Kit only)

**REQUIRED for V16**
- **fa6c8a7.** `OfficialFirmware.v16` (FK/…/Package/FwscPackage.swift:232). The new fields `identity` and `isRedistributable` (:212), plus `known` and `official` (:259,262). `FM1Identity.officialV16` and `isOfficial` (FK/…/Protocol/FM1Identity.swift:24,32). `InstallCheck` uses `device.isOfficial` (FK/…/Updater/InstallCheck.swift:47). `DeviceSummary` names V16. The bootloaderMismatch and packageRefused texts mention V16.
  - V16 can't be bundled because "M-VAVE hasn't published it under a licence, so apps download it" (FwscPackage.swift doc on `v16`); only V15 is Apache-2.0.
- **0172d0e + 9a0eecf + 31f12a5.**
  - `UpdateCode` and `FwscPackage.updateCode` (FK/…/Package/LeavingGuide.swift:3-22,267-275), which adds the `LOADER NOT STAGED` marker, `ownLoader`.
  - `FM1BootArea` with `.v16`, `LeavingDestination`, `LeavingRoute` and `LeavingGuide.route(to:)` (:148-167).
  - `refusesV15OverOtherBootloaders` is now `updateCode == .feluccaOriginalCheck` (FK/…/Package/ApplicationArea.swift).
  - **These are in FM1Kit, not FM1Rescue.** Only the tests sit in FM1RescueTests.
  - Why they're required: Felucca 1.6.1 refuses V16 at the header whatever the bootloader (R9), TransitFM1 refuses V16 (S2), and original-check firmwares refuse V16 on V15's bootloader. Without routes, Android can't say "MIDIMON first" for V16 before the install.
  - Android today still uses the 0.4.5 rule (A/fm1/…/pkg/ApplicationArea.kt:97-104), which wrongly says TransitFM1 and RemoteFM1 refuse V15.
- **e1fa970.** Refusal advice changes from `needsComputer` to `chooseAnotherFirmware` (MIDIMON 0.4 works from a phone), and the texts name MIDIMON 0.4 first. Android's texts still say "Install it from a Mac" and name only LOOM-8 (A/fm1/…/updater/Fm1UpdateError.kt:75,81).

**Required for any install to be safe or correct on current FM-1s**
- **36905bb (safety).** In stage W3 an FM-1 that was switched off during Felucca's second pass (where it erases and writes) never started again. The fix:
  - Error texts now say "screen stays dark… don't switch it off".
  - `InstallProgress` drops the "Checking" stage; the second pass counts as installing.
  - Android still says "it stays in update mode until one finishes" (Fm1UpdateError.kt:93,106,140) and shows a "Checking" stage (A/model/…/device/InstallProgress.kt:13,42).
- **7712928.** `confirmsReinstallingOfficial` and `canReinstallOfficial` (InstallCheck.swift:60,86; FM1Updater.swift:153). TransitFM1 reports `FM-1_015`; with `isOfficial` it would also block V16 → V16-reporting look-alikes.
- **fc809ca.**
  - `serveLoader` / `returnedDevice` (FK/…/Updater/FM1Updater+Writing.swift).
  - `WriteProgress.isNearlyWritten`; `returnCheckAfter` set to 5 s.
  - Simulator options `restartsWithoutFinishing` and `keepsConnectionThroughRestart`.
  - Makes installs more robust; not specific to V16.
- **cee1a84, protocol part only.** `fullIdentityQuery`, `mayTellMore` (FM1Identity.swift:165) and `handshakeAnswer`.
  - felucca-bsp firmwares built since November 2026 shorten their identity to three digits. Without the follow-up query, Android would read the short form and raise `IdentityMismatch` after a successful write.
  - It never fires for V15 or V16 (`!isOfficial`).
  - Whether MIDIMON, which V16 routes depend on, shortens: **UNVERIFIED-EXTERNAL**.
- **bf4a3e4 (choosing between FM-1s).** Nice to have; not V16.

**For the sibling list-UI researcher:** cee1a84's `FlashChipSupport` and `FM1FlashChip`, and `DeviceSummary.flashChipID`. iOS's chip warning (3a5467c) sits in `SwitchingCheck` (iOS/App/Model/InstallReadiness.swift:85-92).

**Mac-only or skip**
- a63df5b, e41ce90, 0cf72d5 (FM1Rescue; `factoryUserData` exists only for download-mode writes), 07e8d6e: FM1Rescue.
- dd3d84a: IOKit `usbProductName`. Android could fill this from `MidiDeviceInfo` later, but it's optional.
- Docs-only commits.

### §2 iOS V16 behaviour end to end
- **Recognising V16.**
  - By hash through `OfficialFirmware.official`: download (FirmwareLibrary.swift:192-206), `add` and `importOfficial`.
  - Saved as Source `.official` with `version "Official"` (`saveOfficial`).
  - PackageDiscovery reads the package at M-VAVE's link and gives V15 or V16 by hash (iOS/App/Catalogue/PackageDiscovery.swift:75-100).
  - The `notOfficial` text says "only an unmodified V15 or V16 FM-1.fwsc is accepted" (FirmwareLibrary+Failure.swift:28).
- **Bundled V15 vs downloaded V16.** `library.official` stays V15-only (FirmwareLibrary.swift:90-92; Android FirmwareLibrary.kt:82-86). "Via official" therefore always means V15; leave that alone. V16 is a removable saved item.
- **Device naming.** "the official firmware (V16)" (iOS/App/Model/DeviceSummary.swift:40-41).
- **Switching guide** (iOS/App/Model/SwitchingGuide.swift).
  - `running()`: V16 maps to `bootArea .v16`, `updateCode .mVave` (:26-42).
  - `LeavingDestination(installing:)` maps the V16 hash to `.v16` (:104-115).
  - A guide is saved with each install in `FlashHistory` (iOS/App/Model/FlashHistory.swift:18,46).
  - `record()` sets `settings.startUp` to `.v16` after V16 goes on (iOS/App/Model/DeviceController.swift:313-321).
  - MIDIMON goes first when the route is `.throughFixedFirmware` (`FixedFirmware`, :141-190).
- **"Ask once what starts the FM-1".** `StartUp` gains `.v16`: "Changed by V16" / "M-VAVE's V16 has been installed on it." (iOS/App/Model/StartUp.swift:8,20,31). The question appears only when `dependsOnStartUp && startUp == nil` (InstallReadiness.swift:120-121,194-196).
- **Before installing.** `installsV16` (InstallReadiness.swift:84) shows `beforeV16Warning` (:153-156): "Going back to V15 later needs a Mac: after V16, V15 plays one note at a time until it's installed again in FM-SWITCH's Backup & rescue, with Start the new firmware fresh ticked."
- **Installing V15 over V16.** `followsV16` (:93-95) is true when the target goes to V15 and what's running is V16 by identity **or** has the V16 bootArea. It shows `afterV16Warning`, iOS variant (:165-169): "After V16, V15 plays one note at a time, as V16 leaves data behind that V15 misreads. To clear it, install V15 from a Mac, in FM-SWITCH's Backup & rescue, with Start the new firmware fresh ticked." Both notices appear in BeforeYouStartView.swift:117-122.
- **After installing.** `FlashJob.startedOnV16` is set from `identity == .officialV16` (DeviceController.swift:188) and carried through `installFirst`. The Done screen shows `SwitchingWords.stillMono` under the V15 restore-voices card (InstallOutcomeViews.swift:41; text at SwitchingWords.swift:78-82): "After V16, V15 plays one note at a time until its saved data is cleared, which putting back the factory sounds won't do. To clear it, install V15 again from a Mac, in FM-SWITCH's Backup & rescue, with Start the new firmware fresh ticked."
- **Other wording.** `alreadyOfficial` and `notReallyOfficialDetail` call it V16 for `FM-1_016` (SwitchingWords.swift:51-62,95-97).
- **Late-loss search.** `FlashJob.mightHaveFinished` (DeviceController+States.swift:112-118: `deviceDidNotReturn`, or a writing stall or loss at ≥0.9 written and not a reinstall) leads to `returned(running:)` (DeviceController.swift:273-311).

### §3 Android files a task would change
- **fm1/**
  - OfficialFirmware.kt:6-33: add the V16 entry and the `identity`, `isRedistributable` and `official` fields.
  - Fm1Identity.kt:43: add `officialV16` and `isOfficial`. Change `handshakeReply` (:25-30) to take a flash chip if you port cee1a84.
  - InstallCheck.kt:32,43-47: use `isOfficial`, add `canReinstallOfficial` and `confirmsReinstallingOfficial`.
  - Fm1UpdaterChecks.kt:29 is where `InstallCheck.of` is called.
  - Fm1Updater.kt:190-192 (the reinstall gate), :152-177 (handshake), :252-314 (write/return; fc809ca).
  - Fm1UpdateError.kt:13-15,24-26,72-82,84-142: texts.
  - ApplicationArea.kt:96-109: replace with `updateCode`; add a new LeavingGuide.kt.
  - SimulatedFm1.kt:26-45 (Options), :212.
  - WriteProgress.kt: `isNearlyWritten`.
- **model/**
  - FirmwareLibrary.kt:207-235 (download), :254-293 (add), :383-385 (NotOfficial text), :166-176 and :389 (loader rule; see Risks).
  - Settings.kt:11-28: add a `StartUp` V16 case. The `FeluccaUpdateCode` object (:34-71) is superseded by guides.
  - InstallReadiness.kt:27-60: has no `switching`, `installsV16` or `followsV16`. :4,33 key off the old `refusesV15OverOtherBootloaders`.
  - DeviceController.kt:142-165 (`flash`: no `startedOnV16`), :184-238 (`run`: records no guide), :289-309 (`failureMessage` / `needsAStepFirst`).
  - FlashJob.kt:7-15: add `startedOnV16` and `guide`.
  - FlashHistory.kt:14-19: `FlashRecord` has no guide field.
  - DeviceSummary.kt:73: name V16.
  - InstallProgress.kt:13,42.
- **app/**
  - BeforeYouStartSheet.kt:206-256: plan notices, the start-up section, `before_start_computer_recovery`.
  - OutcomeScreens.kt:60-93: `InstallDone` has no V15/V16 card.
  - strings_install.xml:17.
  - HomeScreen.kt:88 calls `packagesThatCanFinish`.

### §4 Proposed Android wording (proposals only)
Constraint: the app already points at M-Flasher (Mac, Windows and Linux) in strings_settings.xml:7. Whether M-Flasher, or anything other than FM-SWITCH's Mac Backup & rescue, erases 0xFBD80 is **UNVERIFIED-EXTERNAL**, so don't promise it.
- **Before V16:** "Going back to V15 later can't be fully done from this phone. V15 goes back on over USB, but after V16 it plays one note at a time until its saved data is cleared, which needs a computer: FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. V16 isn't included with this app; it's downloaded from M-VAVE."
- **V15 over V16 (before):** "After V16, V15 plays one note at a time, as V16 leaves data behind that V15 misreads. This phone can't clear it: that needs FM-SWITCH's Backup & rescue on a Mac, with Start the new firmware fresh ticked. Some of V15's sounds may also be wrong or silent, and this app can't put back the factory sounds yet."
- **Done, after V16→V15 (stillMono):** "V15 is on, but after V16 it plays one note at a time until its saved data is cleared, which can't be done from this phone. On a Mac, install V15 again in FM-SWITCH's Backup & rescue, with Start the new firmware fresh ticked."
- **StartUp.V16:** "Changed by V16" / "M-VAVE's V16 has been installed on it."
- **Interrupted write (36905bb, adapted):** "Keep the FM-1 connected and switched on, and resume the update. Its screen stays dark while it updates: don't switch it off, as it may not start again until the update is finished."
- **Replace `before_start_computer_recovery`:** "Going back to V15 from it takes two installs from this phone: MIDIMON first, then V15."

### §5 Tests
- **Port from FM1Kit**
  - OfficialReleaseTests (both tests).
  - ReturnTests (5 tests; needs the simulator options).
  - LeavingGuideTests (FK/Tests/FM1RescueTests/, 109 lines; FM1Kit logic, check which sample packages they use).
  - ProtocolTests :125-210 (chip and full-identity tests).
  - The BootloaderTests, EdgeCaseTests and OpenFirmwareTests deltas for `confirmsReinstallingOfficial` and `isOfficial`.
- **iOS app tests**
  - SwitchingGuideTests (227 lines; V16 at :32,42,109-115).
  - InstallFinishTests (`mightHaveFinished`).
  - OfficialPackageTests (unknown `FM-1_017` at the link is refused).
- **Android today**
  - fm1: OfficialFirmwareTest, PackageCheckTest, ApplicationAreaTest, UpdaterTest (:149 uses `officialV15`), EdgeCaseTest, FinishTest, OpenFirmwareTest, WriteProgressTest, SimulatedFm1Test.
  - model: LibraryTest (:36,166), ReadinessTest, FlashingTest, StandardLookTest (:107-119), StartUpTest, BeforeYouStartStateTest.
  - FirmwareBadgeDesignTest:46 already expects a "V16" badge.

### §6 Licences (facts only; the decision is David's)
- FM1Kit is MIT (FK/LICENSE). Android has it at A/fm1/LICENSE-FM1Kit.txt and app/src/main/assets/licences/FM1Kit-LICENSE.txt.
- iOS FM-SWITCH had no LICENSE file at the port baseline 67ee8c2 (2026-10-30).
- aac72a9 (2026-11-08) licensed it MPL-2.0. MPL is file-level copyleft: changed versions of covered files must stay MPL with source available, while larger works may combine other licences.
- A/NOTICE.md says "no licence chosen yet" and doesn't mention FM-SWITCH.
- A/model files are headed "Ported from FM-SWITCH App/Model/….swift". New ports from 0.2.1, including verbatim warning strings, derive from MPL-2.0 files.
- V16 must not be bundled or committed (fa6c8a7).

### §7 Risks that could brick or strand an FM-1
1. **Switch-off during the second pass.** In W3 an FM-1 never started again after this (36905bb). Android's texts invite it today.
2. **Resume gap: shared with iOS and stranding.**
   - R3 shows the stock loader answering as `ota-FM-1_016` when V16 is the outgoing firmware.
   - `packagesThatCanFinish` matches only `"ota-"+product` or `ota-FM-1_015` (A FirmwareLibrary.kt:171-172,389; iOS FirmwareLibrary.swift:147-169; FK FwscPackage.swift:88 says "always reports ota-FM-1_015").
   - So after the app restarts mid V16→V15 write, V15 is never offered: only a saved V16 is, and if V16 was removed, nothing is.
   - Same-session resume is fine: `isLoaderFor` checks the model only (Fm1Updater.kt:82).
   - Fix proposal: treat any `ota-FM-1_0NN` official loader as stock. That would also be an upstream note, but don't file it in Drew's repos without asking.
3. **Wrong routing without guides.**
   - V16 picked while on Felucca 1.6.1 or TransitFM1: refused at the header, harmless, FM-1 unchanged.
   - V15 later picked on an original-check firmware over V16's bootloader: refused. `needsAStepFirst` (DeviceController.kt:304-309) assumes start-up MVave means "V15 first", which is wrong after V16. Add `StartUp.V16` and record it after V16 installs.
4. **V16 over V16** currently reaches staging as SAME_IDENTITY. It's untested on hardware; 0.6.0 blocks it.
5. **The mono V15 plus broken voices** leave an Android user with no fix in the app (P4).
6. **Shortened identities** from new felucca-bsp builds would be reported as `IdentityMismatch` after a good write, which could lead someone to retry or cut power.

### §8 Recommended `## References`
- `FK/Sources/FM1Kit/Package/FwscPackage.swift:212,232-262`: V16 and redistributability.
- `FK/Sources/FM1Kit/Protocol/FM1Identity.swift:24-35,155-212`: V16, `isOfficial`, full-identity query.
- `FK/Sources/FM1Kit/Package/LeavingGuide.swift:1-285`: update codes, boot areas, routes.
- `FK/Sources/FM1Kit/Updater/InstallCheck.swift:40-113`, `FM1Updater.swift:150-160`, `FM1Updater+Writing.swift:1-76`, `FM1UpdateError.swift:98-293`.
- `FK/HARDWARE_TESTING.md:915-980`: stages R and S (V16 facts, `ota-FM-1_016`, mono).
- `iOS/App/Model/InstallReadiness.swift:16-124,152-170`, `iOS/App/Model/SwitchingGuide.swift:10-190`, `iOS/App/Views/SwitchingWords.swift:9-98`, `iOS/App/Model/StartUp.swift:1-45`.
- `iOS/App/Model/FirmwareLibrary.swift:90-92,147-169,184-215`, `iOS/App/Model/OfficialFirmware+Saved.swift:1-8`, `iOS/App/Catalogue/PackageDiscovery.swift:75-100`.
- `iOS/App/Model/DeviceController.swift:171-190,273-321`, `DeviceController+States.swift:55-72,112-150`, `iOS/App/Standard/Firmwares/BeforeYouStartView.swift:117-122`, `iOS/App/Standard/Install/InstallOutcomeViews.swift:25-50`.
- `A/model/src/main/kotlin/dev/drewr/fmswitch/model/library/FirmwareLibrary.kt:166-176,207-235,389`, `A/model/…/device/InstallReadiness.kt:27-122`, `A/model/…/device/DeviceController.kt:142-309`, `A/model/…/settings/Settings.kt:11-71`.
- `A/fm1/src/main/kotlin/dev/drewr/fm1/{pkg/OfficialFirmware.kt, protocol/Fm1Identity.kt:43, updater/InstallCheck.kt:32, updater/Fm1UpdateError.kt, transport/SimulatedFm1.kt:26-45,212, pkg/ApplicationArea.kt:96-109}`.

### Open items
- **UNVERIFIED-EXTERNAL**
  - Whether M-Flasher or another non-Mac tool can erase 0xFBD80.
  - Whether MIDIMON shortens its identity.
  - Whether V16 refuses V16 over itself.
- **UNVERIFIED-UNREAD**
  - Which sample packages LeavingGuideTests use (FK/Tests/FM1RescueTests/LeavingGuideTests.swift, SampleFlash.swift).

**Task-observer:** none logged. The task-observer skill isn't available to this subagent, so its protocol didn't run.
