// Ported from FM1Kit 0.6.0 Package/FwscPackage.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.protocol.Fm1Identity
import java.security.MessageDigest

class FwscPackage(
    file: ByteArray,
) {
    sealed class Failure(
        message: String,
    ) : IllegalArgumentException(message) {
        data object TooShort : Failure("This file is too short to be an FM-1 firmware package.")

        data object TooLarge : Failure("This file is too large to be an FM-1 firmware package.")

        data class UnreadableProduct(
            val product: String,
        ) : Failure("This file doesn't carry an FM-1 product identity (it reads \"$product\").")

        data class UnexpectedModel(
            val model: String,
        ) : Failure("This package is for a $model, not an FM-1.")

        data object DamagedHeader : Failure(
            "This package's header is damaged, so it can't be installed. Download it again.",
        )

        data class UnexpectedChip(
            val chip: String,
        ) : Failure("This package is for a $chip chip, not the FM-1's AC791N.")

        data class DamagedPart(
            val name: String,
        ) : Failure("Part of this package ($name) is damaged, so it can't be installed. Download it again.")

        data class MissingPart(
            val part: String,
        ) : Failure("This package has no usable $part, so it can't be installed.")
    }

    val fileBytes = file.copyOf()
    val identity: Fm1Identity
    val product get() = identity.text
    val logicalImage: ByteArray
    val sha256: String
    val loaderSha256: String
    internal val container: FirmwareContainer
    val isFirmware get() = container.flash != null
    internal val flashImage get() = container.flash?.let { logicalImage.copyOfRange(it.offset, it.offset + it.size) }
    val loaderBytes get() =
        logicalImage.copyOfRange(
            container.loader.offset,
            container.loader.offset + container.loader.size,
        )

    init {
        if (fileBytes.size <= BLOCK_COUNT * BLOCK_SIZE) throw Failure.TooShort
        if (fileBytes.size > MAXIMUM_FILE_LENGTH) throw Failure.TooLarge
        val decoded = decodeProduct(fileBytes)
        identity =
            Fm1Identity.parse(decoded)?.takeIf { decoded.all { it.code < 128 && !it.isWhitespace() } }
                ?: throw Failure.UnreadableProduct(decoded)
        if (identity.model != EXPECTED_MODEL) throw Failure.UnexpectedModel(identity.model)
        logicalImage = stripMarkers(fileBytes)
        container = FirmwareContainer(logicalImage)
        sha256 = sha256Hex(fileBytes)
        loaderSha256 = sha256Hex(loaderBytes)
    }

    companion object {
        const val BLOCK_COUNT = 20
        const val BLOCK_SIZE = 0x30
        const val IMAGE_BYTES_PER_BLOCK = 0x2F
        const val UNUSED_MARKER = 0x7D
        const val MAXIMUM_FILE_LENGTH = 2_097_152
        const val EXPECTED_MODEL = "FM-1"

        fun decodeProduct(bytes: ByteArray): String =
            buildString {
                repeat(BLOCK_COUNT) { block ->
                    val marker =
                        bytes[block * BLOCK_SIZE + IMAGE_BYTES_PER_BLOCK].toInt() and 0xFF
                    if (marker !=
                        UNUSED_MARKER
                    ) {
                        append((marker - block - 1).toChar())
                    }
                }
            }

        fun stripMarkers(bytes: ByteArray): ByteArray =
            ByteArray(bytes.size - BLOCK_COUNT).also { output ->
                repeat(BLOCK_COUNT) { block ->
                    bytes.copyInto(
                        output,
                        block * IMAGE_BYTES_PER_BLOCK,
                        block * BLOCK_SIZE,
                        block * BLOCK_SIZE + IMAGE_BYTES_PER_BLOCK,
                    )
                }
                ; bytes.copyInto(
                    output,
                    BLOCK_COUNT * IMAGE_BYTES_PER_BLOCK,
                    BLOCK_COUNT * BLOCK_SIZE,
                )
            }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it.toInt() and 0xFF)
            }
    }
}
