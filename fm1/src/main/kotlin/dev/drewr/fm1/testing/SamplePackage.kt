// Ported from FM1Kit 0.4.5 Package/SamplePackage.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.testing

import dev.drewr.fm1.pkg.Crc16
import dev.drewr.fm1.pkg.Decipher
import dev.drewr.fm1.pkg.FwscPackage

object SamplePackage {
    data class Part(
        val type: Int,
        val name: String,
        val size: Int,
        var storedSize: Int? = null,
        val enciphered: Boolean = false,
        var offset: Int? = null,
    ) {
        companion object {
            fun flash(size: Int = 32768) = Part(0, "flash.bin", size)

            fun loader(size: Int = 1000) = Part(100, "ota.bin", size)
        }
    }

    const val FIRST_PART_OFFSET = 0x400

    fun file(
        product: String,
        flashSize: Int = 32768,
        loaderSize: Int = 1000,
        seed: Int = 13,
    ) = file(image(listOf(Part.flash(flashSize), Part.loader(loaderSize)), seed = seed), product)

    fun image(
        parts: List<Part>,
        chip: String = "AC791N",
        imageLength: Int? = null,
        seed: Int = 13,
        contents: Map<Int, ByteArray> = emptyMap(),
    ): ByteArray {
        val offsets = mutableListOf<Int>()
        var next = FIRST_PART_OFFSET
        parts.forEach { part ->
            val offset =
                part.offset ?: next
            offsets += offset
            next = offset + (part.storedSize ?: part.size)
        }
        val length = imageLength ?: next
        val image = ByteArray(length) { ((it * seed + 5) and 0xFF).toByte() }
        contents.forEach { (index, bytes) -> bytes.copyInto(image, offsets[index]) }
        val table = ArrayList<Byte>()
        parts.zip(offsets).forEach { (part, offset) ->
            val stored =
                part.storedSize ?: part.size
            val end = minOf(offset + part.size, length)
            val fields = ByteArray(80)
            put(part.type, fields, 0, 2)
            val checksum = Crc16.xModem(image.copyOfRange(offset, end))
            put(checksum, fields, 4, 2)
            put(offset, fields, 8, 4)
            put(part.size, fields, 12, 4)
            put(stored, fields, 16, 4)
            put(if (part.enciphered) stored else 0, fields, 24, 4)
            part.name.encodeToByteArray().copyInto(fields, 64)
            table +=
                Decipher.apply(fields).toList()
        }
        val header = ByteArray(64)
        put(Crc16.xModem(table.toByteArray()), header, 2, 2)
        put(length, header, 4, 4)
        put(parts.size, header, 8, 2)
        chip.encodeToByteArray().copyInto(header, 16)
        put(Crc16.xModem(header.copyOfRange(2, 64)), header, 0, 2)
        Decipher.apply(header).copyInto(image, 0)
        table.toByteArray().copyInto(image, 64)
        return image
    }

    fun file(
        logicalImage: ByteArray,
        product: String,
    ): ByteArray {
        require(logicalImage.size >= 20 * 0x2F)
        val productBytes = product.encodeToByteArray()
        require(productBytes.size <= 20)
        val output = ArrayList<Byte>()
        repeat(20) { block ->
            output +=
                logicalImage.copyOfRange(block * 0x2F, (block + 1) * 0x2F).toList()
            ; output +=
                if (block <
                    productBytes.size
                ) {
                    (productBytes[block].toInt() + block + 1).toByte()
                } else {
                    0x7D.toByte()
                }
        }
        output +=
            logicalImage.copyOfRange(20 * 0x2F, logicalImage.size).toList()
        return output.toByteArray()
    }

    private fun put(
        value: Int,
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ) {
        repeat(count) {
            bytes[offset + it] =
                (value shr (8 * it)).toByte()
        }
    }
}
