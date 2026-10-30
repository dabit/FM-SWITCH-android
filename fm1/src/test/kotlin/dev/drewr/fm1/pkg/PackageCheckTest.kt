package dev.drewr.fm1.pkg

import dev.drewr.fm1.testing.SamplePackage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PackageCheckTest {
    private fun read(image: ByteArray) = FwscPackage(SamplePackage.file(image, "FM-1_916"))

    @Test fun decodesProductAndStripsMarkers() {
        val image = SamplePackage.image(listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader()), seed = 7)
        val file = SamplePackage.file(image, "FM-1_916")
        val packageFile = FwscPackage(file)
        assertEquals("FM-1_916", packageFile.product)
        assertContentEquals(image, packageFile.logicalImage)
    }

    @Test fun rejectsOtherFiles() {
        assertFailsWith<FwscPackage.Failure.TooShort> { FwscPackage(byteArrayOf(1, 2, 3)) }
        assertFailsWith<FwscPackage.Failure> { FwscPackage(ByteArray(4000) { 0x41 }) }
    }

    @Test fun crc16IsXModem() = assertEquals(0x31C3, Crc16.xModem("123456789".encodeToByteArray()))

    @Test fun decipheringTwiceGivesBackTheOriginal() {
        val bytes = ByteArray(80) { it.toByte() }
        assertNotEquals(bytes.toList(), Decipher.apply(bytes).toList())
        assertContentEquals(bytes, Decipher.apply(Decipher.apply(bytes)))
    }

    @Test fun readsTheFirmwareImageAndLoader() {
        val packageFile = FwscPackage(SamplePackage.file("FM-1_916"))
        assertEquals(SamplePackage.FIRST_PART_OFFSET, packageFile.container.flash!!.offset)
        assertEquals(32768, packageFile.container.flash!!.size)
        assertEquals("ota.bin", packageFile.container.loader.name)
        assertEquals(
            FwscPackage.sha256Hex(
                packageFile.logicalImage.copyOfRange(
                    packageFile.container.loader.offset,
                    packageFile.container.loader.offset + packageFile.container.loader.size,
                ),
            ),
            packageFile.loaderSha256,
        )
        assertEquals(0x4400 until 0x8400, packageFile.container.applicationRange)
    }

    @Test fun aFirmwareImageNoBiggerThanTheBootAreaIsAllApplication() {
        val packageFile =
            read(
                SamplePackage.image(listOf(SamplePackage.Part.flash(16384), SamplePackage.Part.loader())),
            )
        assertEquals(packageFile.container.flash!!.range, packageFile.container.applicationRange)
    }

    @Test fun refusesADamagedHeader() {
        val image = SamplePackage.image(listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader()))
        image[20] =
            (image[20].toInt() xor 1).toByte()
        assertFailsWith<FwscPackage.Failure.DamagedHeader> { read(image) }
    }

    @Test fun refusesADamagedTable() {
        val image = SamplePackage.image(listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader()))
        image[73] =
            (image[73].toInt() xor 1).toByte()
        assertFailsWith<FwscPackage.Failure.DamagedHeader> { read(image) }
    }

    @Test fun refusesAnotherChip() {
        assertFailsWith<FwscPackage.Failure.UnexpectedChip> {
            read(SamplePackage.image(listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader()), chip = "BR25"))
        }
    }

    @Test fun refusesADamagedPart() {
        val image = SamplePackage.image(listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader()))
        image[
            SamplePackage.FIRST_PART_OFFSET +
                5000,
        ] =
            (image[SamplePackage.FIRST_PART_OFFSET + 5000].toInt() xor 1).toByte()
        assertFailsWith<FwscPackage.Failure.DamagedPart> { read(image) }
    }

    @Test fun doesNotCheckEncipheredParts() {
        read(
            SamplePackage.image(
                listOf(
                    SamplePackage.Part.flash(),
                    SamplePackage.Part(50, "USR", 100, 112, true),
                    SamplePackage.Part.loader(),
                ),
            ),
        )
    }

    @Test fun refusesAPackageWithoutALoader() {
        assertFailsWith<FwscPackage.Failure.MissingPart> {
            read(
                SamplePackage.image(listOf(SamplePackage.Part.flash())),
            )
        }
    }

    @Test fun refusesAFirmwareImageThatIsNotWholeSectors() {
        assertFailsWith<FwscPackage.Failure.MissingPart> {
            read(SamplePackage.image(listOf(SamplePackage.Part.flash(16385), SamplePackage.Part.loader())))
        }
    }

    @Test fun refusesOverlappingParts() {
        val loader =
            SamplePackage.Part.loader().also {
                it.offset =
                    SamplePackage.FIRST_PART_OFFSET + 100
            }
        ; assertFailsWith<FwscPackage.Failure.DamagedHeader> {
            read(SamplePackage.image(listOf(SamplePackage.Part.flash(), loader), imageLength = 0x8400))
        }
    }

    @Test fun acceptsPaddingPastTheEnd() {
        val loader = SamplePackage.Part.loader(990).also { it.storedSize = 1000 }
        read(
            SamplePackage.image(
                listOf(SamplePackage.Part.flash(), loader),
                imageLength =
                    0x8400 + 990,
            ),
        )
    }

    @Test fun refusesAPartPastTheEnd() {
        assertFailsWith<FwscPackage.Failure.DamagedHeader> {
            read(
                SamplePackage.image(
                    listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader()),
                    imageLength =
                        0x8400 + 990,
                ),
            )
        }
    }

    @Test fun refusesAFileTooLargeForAnFM1() {
        assertFailsWith<FwscPackage.Failure.TooLarge> {
            read(SamplePackage.image(listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader(2_100_000))))
        }
    }

    @Test fun refusesOverflowingEntryFields() {
        val image = SamplePackage.image(listOf(SamplePackage.Part.flash(), SamplePackage.Part.loader()))
        changeEntry(image, 0) { fields ->
            put(0x70000000, fields, 8, 4)
            put(0x70000000, fields, 12, 4)
            put(0x70000000, fields, 16, 4)
        }
        assertFailsWith<FwscPackage.Failure.DamagedHeader> { read(image) }
    }

    @Test fun acceptsAnEmptyStoredRangeInsideAnotherPart() {
        val empty = SamplePackage.Part(50, "empty", 0, offset = SamplePackage.FIRST_PART_OFFSET + 100)
        val loader = SamplePackage.Part.loader().also { it.offset = SamplePackage.FIRST_PART_OFFSET + 32768 }
        read(SamplePackage.image(listOf(SamplePackage.Part.flash(), empty, loader)))
    }

    @Test fun aSampleFilePassesEveryCheck() {
        val packageFile = FwscPackage(SamplePackage.file("FM-1_123", 65536))
        assertEquals("FM-1_123", packageFile.product)
        assertEquals(0x4400 until 0x10400, packageFile.container.applicationRange)
    }

    private fun changeEntry(
        image: ByteArray,
        index: Int,
        change: (ByteArray) -> Unit,
    ) {
        val start = FirmwareContainer.HEADER_LENGTH + index * FirmwareContainer.ENTRY_LENGTH
        val fields = Decipher.apply(image.copyOfRange(start, start + FirmwareContainer.ENTRY_LENGTH))
        change(fields)
        Decipher.apply(fields).copyInto(image, start)
        val header = Decipher.apply(image.copyOfRange(0, FirmwareContainer.HEADER_LENGTH))
        val tableEnd =
            FirmwareContainer.HEADER_LENGTH + FirmwareContainer.integer(header, 8, 2) * FirmwareContainer.ENTRY_LENGTH
        put(Crc16.xModem(image.copyOfRange(FirmwareContainer.HEADER_LENGTH, tableEnd)), header, 2, 2)
        put(Crc16.xModem(header.copyOfRange(2, header.size)), header, 0, 2)
        Decipher.apply(header).copyInto(image)
    }

    private fun put(
        value: Int,
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ) {
        repeat(count) { bytes[offset + it] = (value shr (8 * it)).toByte() }
    }
}
