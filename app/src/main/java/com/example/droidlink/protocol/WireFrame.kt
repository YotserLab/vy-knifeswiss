package com.example.droidlink.protocol

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire-level frame types for the DroidLink Remote protocol.
 */
enum class FrameType(val code: Byte) {
    AUTH_CHALLENGE(0x01),
    AUTH_RESPONSE(0x02),
    AUTH_OK(0x03),
    AUTH_FAIL(0x04),
    VIDEO_CONFIG(0x10),
    VIDEO_FRAME(0x11),
    AUDIO_CONFIG(0x12),
    AUDIO_FRAME(0x13),
    INPUT_GESTURE(0x20),
    SYSTEM_ACTION(0x21),
    RPC_REQUEST(0x30),
    RPC_RESPONSE(0x31),
    FILE_CHUNK(0x40),
    FILE_META(0x41),
    FILE_ACK(0x42),
    HEARTBEAT(0x50),
    DISCONNECT(0x51),
    TELEMETRY(0x60),
    PROCESS_LIST(0x61),
    PROCESS_ACTION(0x62),
    SETTINGS_SYNC(0x70);

    companion object {
        private val byCode: Map<Byte, FrameType> = entries.associateBy { it.code }

        fun fromCode(code: Byte): FrameType = // DLK-R04
            byCode[code] ?: throw IllegalArgumentException("Unknown frame type code: 0x${code.toInt().and(0xFF).toString(16)}")
    }
}

/**
 * Binary protocol frame for DroidLink Remote.
 *
 * Wire format (big-endian):
 *   version    : 1 byte  (always 1)
 *   type       : 1 byte  (FrameType.code)
 *   requestId  : 4 bytes (Int, big-endian)
 *   payloadLen : 4 bytes (Int, big-endian)
 *   payload    : payloadLen bytes
 *
 * Total header size: 10 bytes.
 *
 * Security note: no sensitive data (keys, passwords) is ever included in a
 * WireFrame payload in plaintext — callers must encrypt via SessionCrypto before
 * wrapping in a WireFrame.
 */
data class WireFrame(
    val version: Byte = PROTOCOL_VERSION,
    val type: FrameType,
    val requestId: Int,
    val payload: ByteArray
) {

    // Auto-generated equals/hashCode that handle ByteArray correctly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WireFrame) return false
        return version == other.version &&
            type == other.type &&
            requestId == other.requestId &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + requestId
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val PROTOCOL_VERSION: Byte = 1 // DLK-R02
        private const val HEADER_SIZE = 10 // DLK-R01 version(1) + type(1) + requestId(4) + payloadLen(4)
        private const val MAX_PAYLOAD_SIZE = 64 * 1024 * 1024 // DLK-R03 64 MiB hard cap

        /**
         * Encodes a [WireFrame] into a byte array ready to be sent over the wire.
         *
         * Layout: version(1) | type(1) | requestId(4, BE) | payloadLen(4, BE) | payload
         */
        fun encode(frame: WireFrame): ByteArray {
            val payloadLen = frame.payload.size
            val buf = ByteBuffer.allocate(HEADER_SIZE + payloadLen).apply {
                order(ByteOrder.BIG_ENDIAN)
                put(frame.version)
                put(frame.type.code)
                putInt(frame.requestId)
                putInt(payloadLen)
                put(frame.payload)
            }
            return buf.array()
        }

        /**
         * Decodes a [WireFrame] from a raw byte array.
         *
         * @throws IllegalArgumentException if the buffer is too short, the version is
         *   unsupported, or the payload length is out of bounds.
         */
        fun decode(data: ByteArray): WireFrame {
            require(data.size >= HEADER_SIZE) {
                "Frame too short: ${data.size} bytes (minimum $HEADER_SIZE)"
            }
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val version = buf.get()
            require(version == PROTOCOL_VERSION) { "Unsupported protocol version: $version" }

            val typeCode = buf.get()
            val frameType = FrameType.fromCode(typeCode)
            val requestId = buf.int
            val payloadLen = buf.int

            require(payloadLen >= 0) { "Negative payload length: $payloadLen" }
            require(payloadLen <= MAX_PAYLOAD_SIZE) {
                "Payload length $payloadLen exceeds maximum $MAX_PAYLOAD_SIZE"
            }
            require(data.size >= HEADER_SIZE + payloadLen) {
                "Buffer underflow: expected ${HEADER_SIZE + payloadLen} bytes, got ${data.size}"
            }

            val payload = ByteArray(payloadLen).also { buf.get(it) }
            return WireFrame(version, frameType, requestId, payload)
        }

        /**
         * Reads one [WireFrame] from an [InputStream], blocking until all bytes arrive.
         *
         * This function is designed to be called from a coroutine on [Dispatchers.IO].
         *
         * @throws java.io.EOFException if the stream ends before the frame is complete.
         * @throws IllegalArgumentException if the frame is malformed.
         */
        fun fromStream(inputStream: InputStream): WireFrame {
            val header = readExact(inputStream, HEADER_SIZE)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)

            val version = buf.get()
            require(version == PROTOCOL_VERSION) { "Unsupported protocol version: $version" }

            val typeCode = buf.get()
            val frameType = FrameType.fromCode(typeCode)
            val requestId = buf.int
            val payloadLen = buf.int

            require(payloadLen >= 0) { "Negative payload length: $payloadLen" }
            require(payloadLen <= MAX_PAYLOAD_SIZE) {
                "Payload length $payloadLen exceeds maximum $MAX_PAYLOAD_SIZE"
            }

            val payload = if (payloadLen > 0) readExact(inputStream, payloadLen) else ByteArray(0)
            return WireFrame(version, frameType, requestId, payload)
        }

        /**
         * Reads exactly [length] bytes from [stream], throwing [java.io.EOFException]
         * if the stream ends prematurely.
         */
        private fun readExact(stream: InputStream, length: Int): ByteArray {
            val buffer = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = stream.read(buffer, offset, length - offset)
                if (read == -1) throw java.io.EOFException(
                    "Stream ended after $offset bytes, expected $length"
                )
                offset += read
            }
            return buffer
        }
    }
}
