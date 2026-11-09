// Ported from FM1Kit 0.6.0 Package/SamplePackage.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.testing

import dev.drewr.fm1.pkg.ApplicationArea
import dev.drewr.fm1.pkg.Crc16
import dev.drewr.fm1.pkg.Decipher

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

    fun firmwareWithCode(
        code: ByteArray,
        product: String = "FM-1_378001",
    ): ByteArray = firmwareWithAppRecord(code, 0x200, code.size, product)

    fun firmwareWithAppRecord(
        code: ByteArray,
        address: Int,
        length: Int,
        product: String = "FM-1_378001",
    ): ByteArray {
        val area = applicationArea(code, address, length)
        val flash = ByteArray(0x4000 + area.size) { 0xFF.toByte() }
        area.copyInto(flash, 0x4000)
        val image =
            image(
                listOf(Part.flash(0x8000), Part.loader()),
                contents = mapOf(0 to flash),
            )
        return file(image, product)
    }

    fun withLoader(
        product: String,
        loader: ByteArray,
    ): ByteArray =
        file(
            image(
                listOf(Part.flash(), Part.loader(loader.size)),
                contents = mapOf(1 to loader),
            ),
            product,
        )

    private fun applicationArea(
        code: ByteArray,
        address: Int,
        length: Int,
    ): ByteArray {
        val codeAddress = 0x200
        val plain = ByteArray(codeAddress + code.size) { 0xFF.toByte() }
        record("app.bin", address, length, Crc16.xModem(code), 0x82).copyInto(plain)
        code.copyInto(plain, codeAddress)
        val padded = plain.copyOf(plain.size + (32 - plain.size % 32) % 32)
        val output = ArrayList<Byte>()
        for (block in padded.indices step 32) {
            output +=
                Decipher
                    .apply(
                        padded.copyOfRange(block, block + 32),
                        ApplicationArea.FM1_CHIP_KEY xor (block shr 2),
                    ).toList()
        }
        return output.toByteArray()
    }

    private fun record(
        name: String,
        address: Int,
        length: Int,
        crc: Int,
        attribute: Int,
    ): ByteArray =
        ByteArray(32).also { fields ->
            put(crc, fields, 2, 2)
            put(address, fields, 4, 4)
            put(length, fields, 8, 4)
            fields[12] = attribute.toByte()
            name.encodeToByteArray().copyInto(fields, 16)
            put(Crc16.xModem(fields.copyOfRange(2, 32)), fields, 0, 2)
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
