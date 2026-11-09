// The app area's layout and the per-block cipher follow M-Flasher's `formats.rs`
// (https://github.com/jvitkauskas/m-flasher), ported to Kotlin.
//
// MIT License, Copyright (c) 2026 Julius Vitkauskas
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software
// and associated documentation files (the "Software"), to deal in the Software without
// restriction, including without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or
// substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
// BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// Ported from FM1Kit 0.6.0 Package/ApplicationArea.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

internal class ApplicationArea(
    private val flash: ByteArray,
    private val chipKey: Int,
) {
    data class Entry(
        val dataCrc: Int,
        val address: Int,
        val length: Int,
        val attribute: Int,
        val name: String,
    )

    fun table(): List<Entry> =
        buildList {
            repeat(16) { index ->
                val raw = bytes(index * 32, 32)
                if (raw.size != 32 ||
                    Crc16.xModem(raw.copyOfRange(2, 32)) != FirmwareContainer.integer(raw, 0, 2)
                ) {
                    return@buildList
                }
                add(
                    Entry(
                        FirmwareContainer.integer(raw, 2, 2),
                        FirmwareContainer.integer(raw, 4, 4),
                        FirmwareContainer.integer(raw, 8, 4),
                        raw[12].toInt() and 0xFF,
                        raw
                            .copyOfRange(16, 32)
                            .takeWhile { it != 0.toByte() }
                            .toByteArray()
                            .decodeToString(),
                    ),
                )
            }
        }

    fun code(): ByteArray? {
        val app = table().firstOrNull { it.name == "app.bin" } ?: return null
        if (app.address < 0 || app.length < 0 ||
            START.toLong() + app.address.toLong() + app.length.toLong() > flash.size.toLong()
        ) {
            return null
        }
        return bytes(app.address, app.length)
    }

    fun bytes(
        offset: Int,
        count: Int,
    ): ByteArray {
        if (offset < 0 || count < 0) return ByteArray(0)
        val plain = ArrayList<Byte>()
        var block = (offset - offset % 32).toLong()
        val end = offset.toLong() + count.toLong()
        while (block < end && START.toLong() + block + 32 <= flash.size.toLong()) {
            plain +=
                Decipher
                    .apply(
                        flash.copyOfRange(START + block.toInt(), START + block.toInt() + 32),
                        chipKey xor (block shr 2).toInt(),
                    ).toList()
            block += 32
        }
        return plain.drop(offset % 32).take(count).toByteArray()
    }

    companion object {
        const val START = 0x4000
        const val FM1_CHIP_KEY = 0x980F
    }
}

val FwscPackage.usesFeluccaUpdateCode: Boolean get() = firmwareCode?.containsSequence(FELUCCA_LOADER_MARKER) ?: false
val FwscPackage.refusesV15OverOtherBootloaders: Boolean get() = updateCode == UpdateCode.FELUCCA_ORIGINAL_CHECK
internal val FwscPackage.firmwareCode get() =
    flashImage?.let {
        ApplicationArea(it, ApplicationArea.FM1_CHIP_KEY).code()
    }
internal val FELUCCA_LOADER_MARKER = "FELUCCA-LOADER-1".encodeToByteArray()
internal val BOOTLOADER_CHECK_MARKER = "AC79BLID".encodeToByteArray()
internal val OWN_LOADER_MARKER = "LOADER NOT STAGED".encodeToByteArray()
internal val V15_FLASH_IMAGE_SHA256 = BuildIdBytes.v15

private object BuildIdBytes {
    val v15 =
        "c1068a403c04a6378a626afb66cc217f72d13f10aa200e983b7a9817cc1dac03"
            .chunked(2)
            .map {
                it.toInt(16).toByte()
            }.toByteArray()
}

internal fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
    sequence.isEmpty() ||
        indices.any { start ->
            start + sequence.size <= size &&
                sequence.indices.all { this[start + it] == sequence[it] }
        }
