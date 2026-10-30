package dev.drewr.fm1.pkg

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationAreaTest {
    @Test fun refusesV15WhenFeluccasUpdateCodeLacksTheFix() =
        assertTrue(
            SampleApplicationArea
                .packageWith("..FELUCCA-LOADER-1..".encodeToByteArray())
                .refusesV15OverOtherBootloaders,
        )

    @Test fun takesV15WhenFixed() =
        assertFalse(
            SampleApplicationArea
                .packageWith("..FELUCCA-LOADER-1..AC79BLID..".encodeToByteArray())
                .refusesV15OverOtherBootloaders,
        )

    @Test fun takesV15WhenHashed() =
        assertFalse(
            SampleApplicationArea
                .packageWith(
                    "..FELUCCA-LOADER-1..".encodeToByteArray() +
                        "c1068a403c04a6378a626afb66cc217f72d13f10aa200e983b7a9817cc1dac03"
                            .chunked(2)
                            .map { it.toInt(16).toByte() }
                            .toByteArray(),
                ).refusesV15OverOtherBootloaders,
        )

    @Test fun plainSampleDoesNotUseFeluccasCode() {
        val packageFile = SampleApplicationArea.packageWith(ByteArray(0x3000) { 0x5A })
        assertFalse(packageFile.usesFeluccaUpdateCode)
        assertFalse(packageFile.refusesV15OverOtherBootloaders)
    }

    @Test fun rejectsAnOverflowingApplicationRange() {
        val packageFile = SampleApplicationArea.packageWithAppRecord(ByteArray(32), 0x7FFFFFE0, 64)
        assertNull(ApplicationArea(packageFile.flashImage!!, ApplicationArea.FM1_CHIP_KEY).code())
    }
}
