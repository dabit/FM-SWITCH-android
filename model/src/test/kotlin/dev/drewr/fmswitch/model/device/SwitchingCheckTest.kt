package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.Fm1BootArea
import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.pkg.UpdateCode
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.testing.SamplePackage
import dev.drewr.fmswitch.model.catalogue.HubFlashChip
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.StartUp
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwitchingCheckTest {
    private val item =
        StoredFirmware(
            "one",
            "One",
            "1",
            product = "FM-1_015",
            sha256 = "one",
            size = 1,
            fileName = "one.fwsc",
            source = StoredFirmware.Source.Imported,
            savedAt = Instant.EPOCH,
        )
    private val v15 = FwscPackage(SamplePackage.firmwareWithCode("FELUCCA-LOADER-1".encodeToByteArray(), "FM-1_015"))
    private val v16 = FwscPackage(SamplePackage.firmwareWithCode("FELUCCA-LOADER-1".encodeToByteArray(), "FM-1_016"))
    private val transit =
        LeavingGuide("TransitFM1", Fm1Identity.officialV15, UpdateCode.OWN_LOADER, Fm1BootArea.M_BOOTLOADER)

    private fun check(
        identity: Fm1Identity? = Fm1Identity.officialV15,
        saved: LeavingGuide? = null,
        chip: HubFlashChip? = null,
    ) = SwitchingCheck.from(
        listOf(item to v15),
        identity,
        null,
        saved,
        StartUp.MVave,
        { chip },
        null,
        null,
        officialOf = { OfficialFirmware.V15 },
    )

    @Test
    fun anUnknownBootloaderAsksTheStartUp() {
        val custom = Fm1Identity.of("FM-1", 916)
        val code = "FELUCCA-LOADER-1".encodeToByteArray()
        val planned =
            listOf(item.copy(product = custom.text) to FwscPackage(SamplePackage.firmwareWithCode(code, custom.text)))
        val check = SwitchingCheck.from(planned, custom, null, null, null, { null }, null, null)
        assertTrue(check.dependsOnStartUp)
    }

    @Test
    fun anUnknownBootloaderGivesTheWarningsForAChangedOne() {
        val guide =
            LeavingGuide(
                "Felucca",
                Fm1Identity.of("FM-1", 916),
                UpdateCode.FELUCCA_ORIGINAL_CHECK,
                null,
            )
        assertTrue(SwitchingCheck(isRead = true, installed = guide).warnings.isNotEmpty())
    }

    @Test fun transitFM1WarnsAboutV16AndItsIdentity() =
        assertEquals(2, SwitchingCheck(isRead = true, installed = transit).warnings.size)

    @Test
    fun installingV16WarnsAboutGoingBack() {
        val check =
            SwitchingCheck.from(
                listOf(item.copy(product = "FM-1_016") to v16),
                Fm1Identity.officialV15,
                null,
                null,
                StartUp.MVave,
                { null },
                null,
                null,
                officialOf = { OfficialFirmware.V16 },
            )
        assertTrue(check.installsV16)
        assertTrue(SwitchingWords.BEFORE_V16_WARNING.contains("V16"))
    }

    @Test
    fun v15AfterV16WarnsAboutMono() {
        val other = Fm1Identity.of("FM-1", 916)
        val saved = LeavingGuide("Other", other, UpdateCode.FELUCCA_ORIGINAL_CHECK, Fm1BootArea.V16)
        assertTrue(check(other, saved).followsV16)
        assertTrue(SwitchingWords.AFTER_V16_WARNING.contains("one note at a time"))
    }

    @Test fun onlyFirstChipFirmwaresAreFlaggedUnlessTheFm1SaysItHasThatChip() {
        assertTrue(check(chip = HubFlashChip.Old).knowsOnlyFirstChip.isEmpty())
        assertTrue(
            SwitchingCheck
                .from(
                    listOf(item to v15),
                    Fm1Identity.officialV15,
                    0x856014,
                    null,
                    StartUp.MVave,
                    { HubFlashChip.Old },
                    null,
                    null,
                    officialOf = { OfficialFirmware.V15 },
                ).knowsOnlyFirstChip
                .isEmpty(),
        )
    }

    @Test fun readsWhichChipsAFirmwareKnows() {
        assertEquals(dev.drewr.fm1.pkg.FlashChipSupport.FIRST_CHIP_ONLY, HubFlashChip.Old.support)
        assertEquals(dev.drewr.fm1.pkg.FlashChipSupport.BOTH_CHIPS, HubFlashChip.New.support)
    }
}
