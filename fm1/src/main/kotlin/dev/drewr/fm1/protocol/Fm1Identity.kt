// Ported from FM1Kit 0.6.0 Protocol/FM1Identity.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

class Fm1Identity private constructor(
    val text: String,
    val model: String,
    val version: Int,
    val armedBuildPrefix: String?,
) {
    val isOfficial: Boolean get() = this == officialV15 || this == officialV16
    val isLoader: Boolean get() = model.startsWith(LOADER_PREFIX, ignoreCase = true)
    val isOpenFirmware: Boolean get() = (model == "FM-1" && version in 8_000_000..8_000_999) || model == "FM1_COMMUNITY"
    val hardwareModel: String get() =
        if (armedBuildPrefix !=
            null
        ) {
            "FM-1"
        } else if (isLoader) {
            model.drop(LOADER_PREFIX.length)
        } else {
            model
        }

    fun isLoaderFor(model: String): Boolean = isLoader && hardwareModel == model

    fun handshakeReply(): ByteArray {
        val textLength = REPLY_LENGTH - Fm1Message.headerLength - 1
        val content = text.encodeToByteArray().copyOf(textLength)
        val body = Fm1Message.header(Fm1Message.identityCommand, textLength) + content
        return Fm1Message.frame(body + byteArrayOf(Fm1Message.checksum(body, Fm1Message.headerLength)))
    }

    override fun equals(other: Any?): Boolean = other is Fm1Identity && text == other.text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = text

    companion object {
        const val LOADER_PREFIX = "ota-"
        private const val ARMED_LOADER_MODEL = "ota-FM1C"
        private const val ARMED_PREFIX_LENGTH = 16
        private const val REPLY_LENGTH = 34
        val officialV15 = parse("FM-1_015")!!
        val officialV16 = parse("FM-1_016")!!

        fun parse(text: String): Fm1Identity? {
            val separator = text.lastIndexOf('_')
            if (separator <= 0) return null
            val model = text.substring(0, separator)
            val digits = text.substring(separator + 1)
            if (model.equals(ARMED_LOADER_MODEL, ignoreCase = true)) {
                if (digits.length != ARMED_PREFIX_LENGTH ||
                    !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                ) {
                    return null
                }
                return Fm1Identity(text, model, 0, digits.lowercase())
            }
            if (digits.isEmpty() || !text.all { it.code in 0x21..0x7E } || !digits.all { it in '0'..'9' }) return null
            return Fm1Identity(text, model, digits.toIntOrNull() ?: return null, null)
        }

        fun of(
            model: String,
            version: Int,
        ): Fm1Identity {
            require(model.isNotEmpty() && model.all { it.code in 0x21..0x7E })
            require(version >= 0)
            return Fm1Identity("${model}_$version", model, version, null)
        }

        fun fromHandshakeReply(sysex: ByteArray): Fm1Identity? {
            val body = Fm1Message.unframe(sysex) ?: return null
            if (body.size != REPLY_LENGTH || !body.copyOfRange(0, 2).contentEquals(Fm1Message.prefix) ||
                Fm1Message.u(body[Fm1Message.commandOffset]) != Fm1Message.identityCommand ||
                Fm1Message.checksum(body, Fm1Message.headerLength, REPLY_LENGTH - 1) != body.last()
            ) {
                return null
            }
            val textBytes =
                body
                    .copyOfRange(
                        Fm1Message.headerLength,
                        REPLY_LENGTH - 1,
                    ).takeWhile { it != 0.toByte() }
                    .toByteArray()
            if (textBytes.any { Fm1Message.u(it) >= 0x80 }) return null
            return parse(textBytes.decodeToString())
        }
    }
}
