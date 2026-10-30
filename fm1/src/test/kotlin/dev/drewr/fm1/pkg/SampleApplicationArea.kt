// Ported from FM1Kit 0.4.5 Tests/FM1RescueTests/SampleFlash.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.testing.SamplePackage

internal object SampleApplicationArea {
    fun packageWith(code: ByteArray): FwscPackage = packageWithAppRecord(code, 0x200, code.size)

    fun packageWithAppRecord(
        code: ByteArray,
        address: Int,
        length: Int,
    ): FwscPackage {
        val area = applicationArea(code, address, length)
        val flash = ByteArray(0x4000 + area.size) { 0xFF.toByte() }
        area.copyInto(flash, 0x4000)
        val image =
            SamplePackage.image(
                listOf(SamplePackage.Part.flash(0x8000), SamplePackage.Part.loader()),
                contents =
                    mapOf(0 to flash),
            )
        return FwscPackage(SamplePackage.file(image, "FM-1_378001"))
    }

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
            fields[12] =
                attribute.toByte()
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
