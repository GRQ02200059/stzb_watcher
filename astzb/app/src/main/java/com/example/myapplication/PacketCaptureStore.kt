package com.example.myapplication

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PacketRecord(
    val timestampMillis: Long,
    val protocolName: String,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val length: Int,
    val ipHeaderLength: Int,
    val transportHeaderLength: Int,
    val payloadLength: Int,
    val summary: String,
    val hexDump: String,
    val payloadHexPreview: String,
)

object PacketCaptureStore {
    private const val MAX_PACKETS = 50
    private val packets = mutableListOf<PacketRecord>()
    private val fileSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    @Synchronized
    fun add(record: PacketRecord) {
        packets.add(record)
        while (packets.size > MAX_PACKETS) {
            packets.removeAt(0)
        }
    }

    @Synchronized
    fun latest(): PacketRecord? = packets.lastOrNull()

    @Synchronized
    fun clear() {
        packets.clear()
    }

    @Synchronized
    fun exportToFile(context: Context): File? {
        if (packets.isEmpty()) return null

        val dir = File(context.filesDir, "captures")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val outFile = File(dir, "capture_${fileSdf.format(Date())}.txt")
        outFile.bufferedWriter().use { writer ->
            packets.forEachIndexed { index, packet ->
                writer.appendLine("=== PACKET ${index + 1} ===")
                writer.appendLine("timestamp=${packet.timestampMillis}")
                writer.appendLine("summary=${packet.summary}")
                writer.appendLine("protocol=${packet.protocolName}")
                writer.appendLine("src=${packet.srcIp}:${packet.srcPort}")
                writer.appendLine("dst=${packet.dstIp}:${packet.dstPort}")
                writer.appendLine("length=${packet.length}")
                writer.appendLine("ipHeaderLength=${packet.ipHeaderLength}")
                writer.appendLine("transportHeaderLength=${packet.transportHeaderLength}")
                writer.appendLine("payloadLength=${packet.payloadLength}")
                writer.appendLine("payloadHexPreview=${packet.payloadHexPreview}")
                writer.appendLine("hexDump=${packet.hexDump}")
                writer.appendLine()
            }
        }

        return outFile
    }
}
