package com.example.myapplication

import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

class StzbStreamParser(private val streamName: String) {

    private val buffer = ByteArrayOutputStream()

    @Synchronized
    fun append(bytes: ByteArray, length: Int) {
        if (length <= 0) return
        buffer.write(bytes, 0, length)
        processBufferedPackets()
    }

    private fun processBufferedPackets() {
        var data = buffer.toByteArray()
        var offset = 0

        while (data.size - offset >= MIN_PACKET_SIZE) {
            val bodyLen = readIntBE(data, offset)
            val totalLen = bodyLen + 4
            if (bodyLen < 4 || bodyLen > MAX_PACKET_SIZE) {
                offset += 1
                continue
            }
            if (data.size - offset < totalLen) break

            val packet = data.copyOfRange(offset, offset + totalLen)
            inspectPacket(packet)
            offset += totalLen
        }

        if (offset > 0) {
            data = data.copyOfRange(offset, data.size)
            buffer.reset()
            buffer.write(data)
        }
        if (buffer.size() > MAX_PACKET_SIZE) {
            PacketLogStore.add("STZB 解析缓冲过大，已重置：$streamName size=${buffer.size()}")
            buffer.reset()
        }
    }

    private fun inspectPacket(packet: ByteArray) {
        if (packet.size < MIN_PACKET_SIZE) return

        val bodyLen = readIntBE(packet, 0)
        val seq = readIntBE(packet, 4)
        val dataType = packet[12].toInt() and 0xff
        val body = packet.copyOfRange(13, packet.size)
        val msgId = seq.toString()

        val decoded = decodeBody(dataType, body, packet[7].toInt() and 0xff)
        val preview = decoded.preview
        val kind = decoded.kind

        PacketLogStore.add(
            "STZB包 $msgId type=$dataType/$kind len=$bodyLen stream=$streamName preview=$preview"
        )
        val capturedPacket = LocalStzbPacket(
            msgId = msgId,
            dataType = dataType,
            decodeKind = kind,
            streamName = streamName,
            preview = preview,
            decodedText = decoded.text,
            rawHex = packet.take(96).joinToString(" ") { "%02X".format(it) },
        )
        LocalStzbPacketStore.add(capturedPacket)
        LocalStzbRepository.savePacket(capturedPacket)
        LocalStzbCaptureWriter.save(capturedPacket)
        LocalBattleMonitorParser.tryParse(capturedPacket)
        LocalBattleNoticeParser.tryParse(capturedPacket)
        LocalFullBattleParser.tryParse(capturedPacket)
        LocalAuxiliaryParser.tryParse(capturedPacket)
    }

    private fun decodeBody(dataType: Int, body: ByteArray, xorKey2: Int): DecodeResult {
        return when (dataType) {
            2 -> decodedTextResult("plain", body)
            3 -> decodeZlib(body)
            5 -> decodeXor(body, xorKey2)
            else -> {
                if (body.size >= 2 && body[0] == 0x78.toByte()) {
                    decodeZlib(body).copy(kind = "type${dataType}_zlib")
                } else {
                    decodedTextResult("raw", body)
                }
            }
        }
    }

    private fun decodeXor(body: ByteArray, xorKey2: Int): DecodeResult {
        val keys = listOf(0x98, xorKey2, 0x00).distinct()
        for (key in keys) {
            val decoded = if (key == 0) body else body.map { (it.toInt() xor key).toByte() }.toByteArray()
            val directText = bytesToText(decoded)
            val direct = textPreview(directText)
            if (direct.startsWith("[") || direct.startsWith("{")) {
                return DecodeResult(if (key == 0) "plain" else "xor", direct, directText)
            }
            val zlib = tryDecodeZlib(decoded)
            if (zlib != null) {
                return DecodeResult(if (key == 0) "zlib" else "xor_zlib", textPreview(zlib), zlib)
            }
        }
        return decodedTextResult("xor_raw", body)
    }

    private fun decodeZlib(body: ByteArray): DecodeResult {
        val direct = tryDecodeZlib(body)
        if (direct != null) return DecodeResult("zlib", textPreview(direct), direct)
        if (body.size > 4) {
            val skip4 = tryDecodeZlib(body.copyOfRange(4, body.size))
            if (skip4 != null) return DecodeResult("zlib_skip4", textPreview(skip4), skip4)
        }
        return decodedTextResult("zlib_fail", body)
    }

    private fun tryDecodeZlib(bytes: ByteArray): String? {
        return runCatching {
            InflaterInputStream(bytes.inputStream()).use { input ->
                val decoded = input.readBytes()
                bytesToText(decoded)
            }
        }.getOrNull()
    }

    private fun decodedTextResult(kind: String, bytes: ByteArray): DecodeResult {
        val text = bytesToText(bytes)
        return DecodeResult(kind, textPreview(text), text)
    }

    private fun bytesToText(bytes: ByteArray): String {
        return runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault("")
    }

    private fun textPreview(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            val normalized = runCatching {
                JSONTokener(trimmed).nextValue().toString()
            }.getOrDefault(trimmed)
            return normalized.take(180).replace('\n', ' ')
        }
        return "<empty>"
    }

    private fun readIntBE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xff) shl 24) or
            ((data[offset + 1].toInt() and 0xff) shl 16) or
            ((data[offset + 2].toInt() and 0xff) shl 8) or
            (data[offset + 3].toInt() and 0xff)
    }

    private data class DecodeResult(
        val kind: String,
        val preview: String,
        val text: String,
    )

    companion object {
        private const val MIN_PACKET_SIZE = 14
        private const val MAX_PACKET_SIZE = 10 * 1024 * 1024
    }
}

data class LocalStzbPacket(
    val msgId: String,
    val dataType: Int,
    val decodeKind: String,
    val streamName: String,
    val preview: String,
    val decodedText: String,
    val rawHex: String,
)

object LocalStzbPacketStore {
    private const val LIMIT = 100
    private val packets = ArrayDeque<LocalStzbPacket>()

    @Synchronized
    fun add(packet: LocalStzbPacket) {
        packets.addFirst(packet)
        while (packets.size > LIMIT) {
            packets.removeLast()
        }
    }

    @Synchronized
    fun snapshot(): List<LocalStzbPacket> = packets.toList()

    @Synchronized
    fun clear() {
        packets.clear()
    }
}
