// Ported from FM1Kit 0.4.5 Package/FirmwareContainer.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

internal class FirmwareContainer(
    image: ByteArray,
) {
    data class Entry(
        val type: Int,
        val name: String,
        val offset: Int,
        val size: Int,
        val storedSize: Long,
        val encipheredLength: Long,
        val checksum: Int,
    ) {
        val isEnciphered get() = encipheredLength != 0L
        val range get() = offset until (offset + size)

        fun stored(imageLength: Int): Bounds =
            Bounds(
                offset.toLong().coerceIn(0, imageLength.toLong()),
                (offset.toLong() + storedSize).coerceIn(0, imageLength.toLong()),
            )
    }

    val flash: Entry?
    val loader: Entry
    val applicationRange: IntRange

    init {
        if (image.size < HEADER_LENGTH) throw FwscPackage.Failure.DamagedHeader
        val header = Decipher.apply(image.copyOfRange(0, HEADER_LENGTH))
        val count = integer(header, 8, 2)
        if (integer(header, 0, 2) != Crc16.xModem(header.copyOfRange(2, header.size)) ||
            unsignedInteger(header, 4, 4) != image.size.toLong()
        ) {
            throw FwscPackage.Failure.DamagedHeader
        }
        val chip = text(header.copyOfRange(16, 32))
        if (chip != CHIP) throw FwscPackage.Failure.UnexpectedChip(chip)
        val tableEnd = HEADER_LENGTH + count * ENTRY_LENGTH
        if (count !in 1..MAXIMUM_ENTRIES || tableEnd > image.size ||
            integer(header, 2, 2) != Crc16.xModem(image.copyOfRange(HEADER_LENGTH, tableEnd))
        ) {
            throw FwscPackage.Failure.DamagedHeader
        }
        val entries = mutableListOf<Entry>()
        repeat(count) { index ->
            val start = HEADER_LENGTH + index * ENTRY_LENGTH
            val fields = Decipher.apply(image.copyOfRange(start, start + ENTRY_LENGTH))
            val offset = unsignedInteger(fields, 8, 4)
            val size = unsignedInteger(fields, 12, 4)
            val storedSize = unsignedInteger(fields, 16, 4)
            val encipheredLength = unsignedInteger(fields, 24, 4)
            if (offset > image.size.toLong() || size > image.size.toLong() ||
                offset + size > image.size.toLong() || storedSize < size ||
                (encipheredLength != 0L && encipheredLength != storedSize)
            ) {
                throw FwscPackage.Failure.DamagedHeader
            }
            val entry =
                Entry(
                    integer(fields, 0, 2),
                    text(fields.copyOfRange(64, 80)),
                    offset.toInt(),
                    size.toInt(),
                    storedSize,
                    encipheredLength,
                    integer(fields, 4, 2),
                )
            val stored = entry.stored(image.size)
            if (entry.offset < tableEnd ||
                entries.any { overlaps(stored, it.stored(image.size)) }
            ) {
                throw FwscPackage.Failure.DamagedHeader
            }
            if (!entry.isEnciphered &&
                Crc16.xModem(image.copyOfRange(entry.offset, entry.offset + entry.size)) != entry.checksum
            ) {
                throw FwscPackage.Failure.DamagedPart(entry.name)
            }
            entries += entry
        }
        val flashes = entries.filter { it.type == FLASH_TYPE }
        val loaders = entries.filter { it.type == LOADER_TYPE }
        val installer = flashes.isEmpty() && entries.size == 1 && loaders.size == 1
        flash = flashes.firstOrNull()
        if (!installer &&
            (flashes.size != 1 || flash == null || flash.isEnciphered || !isFlashSize(flash.size))
        ) {
            throw FwscPackage.Failure.MissingPart("firmware image")
        }
        loader =
            loaders.singleOrNull()?.takeIf { !it.isEnciphered && it.size in 1 until 32768 }
                ?: throw FwscPackage.Failure.MissingPart("update loader")
        applicationRange =
            if (flash ==
                null
            ) {
                0 until 0
            } else if (flash.size >
                BOOT_AREA_LENGTH
            ) {
                flash.offset + BOOT_AREA_LENGTH until flash.offset + flash.size
            } else {
                flash.range
            }
    }

    companion object {
        const val HEADER_LENGTH = 64
        const val ENTRY_LENGTH = 80
        const val FLASH_TYPE = 0
        const val LOADER_TYPE = 100
        const val BOOT_AREA_LENGTH = 0x4000
        private const val MAXIMUM_ENTRIES = 12
        private const val CHIP = "AC791N"

        fun integer(
            bytes: ByteArray,
            offset: Int,
            count: Int,
        ): Int =
            (0 until count).fold(0) { value, index ->
                value or
                    ((bytes[offset + index].toInt() and 0xFF) shl (8 * index))
            }

        fun unsignedInteger(
            bytes: ByteArray,
            offset: Int,
            count: Int,
        ): Long =
            (0 until count).fold(0L) { value, index ->
                value or ((bytes[offset + index].toLong() and 0xFF) shl (8 * index))
            }

        fun text(bytes: ByteArray): String = bytes.takeWhile { it != 0.toByte() }.toByteArray().decodeToString()

        fun isFlashSize(size: Int) = size in 16384..1044480 && size % 4096 == 0

        private fun overlaps(
            one: Bounds,
            other: Bounds,
        ) = one.start < one.end && other.start < other.end && one.start < other.end && other.start < one.end
    }
}

internal data class Bounds(
    val start: Long,
    val end: Long,
)
