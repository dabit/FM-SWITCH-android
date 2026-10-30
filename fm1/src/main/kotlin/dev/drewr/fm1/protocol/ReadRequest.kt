// Ported from FM1Kit 0.4.5 Protocol/FM1Message.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.protocol

data class ReadRequest(
    val flashType: Int = 0,
    val address: Long,
    val length: Int,
) {
    val isFinish: Boolean get() = address == Fm1Message.stageFinishAddress || address == Fm1Message.writeFinishAddress

    fun encoded(): ByteArray = Fm1Message.frame(body(flashType, address, length, ByteArray(0)))

    fun response(data: ByteArray): ByteArray = Fm1Message.frame(body(flashType, address, data.size, data))

    fun finishResponse(): ByteArray = response(Fm1Message.finishPayload)

    companion object {
        fun parse(sysex: ByteArray): ReadRequest? {
            val body = Fm1Message.unframe(sysex) ?: return null
            if (body.size != Fm1Message.headerLength + Fm1Message.fixedFieldsLength + 1 ||
                !body.copyOfRange(0, 2).contentEquals(Fm1Message.prefix) ||
                Fm1Message.u(body[Fm1Message.commandOffset]) != Fm1Message.readCommand ||
                Fm1Message.checksum(body, Fm1Message.flashTypeOffset, Fm1Message.dataOffset) !=
                body[Fm1Message.dataOffset]
            ) {
                return null
            }
            return ReadRequest(
                Fm1Message.u(body[Fm1Message.flashTypeOffset]),
                Fm1Message.readLittleEndian(body, Fm1Message.addressOffset, 4).toLong() and 0xFFFF_FFFFL,
                Fm1Message.readLittleEndian(body, Fm1Message.lengthOffset, 3),
            )
        }

        fun parseResponse(sysex: ByteArray): Pair<ReadRequest, ByteArray>? {
            val body = Fm1Message.unframe(sysex) ?: return null
            if (body.size < Fm1Message.headerLength + Fm1Message.fixedFieldsLength + 1 ||
                !body.copyOfRange(0, 2).contentEquals(Fm1Message.prefix) ||
                Fm1Message.u(body[Fm1Message.commandOffset]) != Fm1Message.readCommand
            ) {
                return null
            }
            val declaredBodyLength = Fm1Message.readLittleEndian(body, Fm1Message.bodyLengthOffset, 3)
            val length = Fm1Message.readLittleEndian(body, Fm1Message.lengthOffset, 3)
            val checksumIndex = Fm1Message.dataOffset + length
            if (declaredBodyLength != length + Fm1Message.fixedFieldsLength || body.size != checksumIndex + 1 ||
                Fm1Message.checksum(body, Fm1Message.flashTypeOffset, checksumIndex) != body[checksumIndex]
            ) {
                return null
            }
            val request =
                ReadRequest(
                    Fm1Message.u(body[Fm1Message.flashTypeOffset]),
                    Fm1Message.readLittleEndian(body, Fm1Message.addressOffset, 4).toLong() and 0xFFFF_FFFFL,
                    length,
                )
            return request to body.copyOfRange(Fm1Message.dataOffset, checksumIndex)
        }

        private fun body(
            flashType: Int,
            address: Long,
            length: Int,
            data: ByteArray,
        ): ByteArray {
            val result =
                Fm1Message.header(Fm1Message.readCommand, data.size + Fm1Message.fixedFieldsLength) +
                    byteArrayOf(flashType.toByte()) + Fm1Message.littleEndian(address, 4) +
                    Fm1Message.littleEndian(length.toLong(), 3) + data
            return result + byteArrayOf(Fm1Message.checksum(result, Fm1Message.flashTypeOffset))
        }
    }
}
