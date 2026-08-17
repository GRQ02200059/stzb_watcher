package com.example.myapplication

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalStzbCaptureWriter {
    private var appContext: Context? = null
    private val timestampFormat = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US)

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun save(packet: LocalStzbPacket) {
        val context = appContext ?: return
        val dir = File(context.filesDir, "capture_new/${packet.msgId}")
        if (!dir.exists()) dir.mkdirs()

        val ts = timestampFormat.format(Date())
        val suffix = if (packet.preview.trim().startsWith("{") || packet.preview.trim().startsWith("[")) {
            "json"
        } else {
            "txt"
        }
        val file = File(dir, "cap_${ts}_${packet.msgId}_${packet.decodeKind}.$suffix")
        file.writeText(
            buildString {
                appendLine("msg_id=${packet.msgId}")
                appendLine("type=${packet.dataType}")
                appendLine("decode=${packet.decodeKind}")
                appendLine("stream=${packet.streamName}")
                appendLine("preview=${packet.preview}")
                appendLine("decoded_text=${packet.decodedText}")
                appendLine("raw_hex=${packet.rawHex}")
            },
            Charsets.UTF_8,
        )
    }

    @Synchronized
    fun exportSummary(context: Context): File? {
        val packets = LocalStzbPacketStore.snapshot()
        if (packets.isEmpty()) return null

        val outDir = File(context.filesDir, "exports")
        if (!outDir.exists()) outDir.mkdirs()
        val file = File(outDir, "stzb_packets_${timestampFormat.format(Date())}.txt")
        file.writeText(
            packets.joinToString("\n\n") { packet ->
                buildString {
                    appendLine("msg_id=${packet.msgId}")
                    appendLine("type=${packet.dataType}")
                    appendLine("decode=${packet.decodeKind}")
                    appendLine("stream=${packet.streamName}")
                    appendLine("preview=${packet.preview}")
                    appendLine("decoded_text=${packet.decodedText}")
                    appendLine("raw_hex=${packet.rawHex}")
                }
            },
            Charsets.UTF_8,
        )
        return file
    }

    fun captureRoot(context: Context): File = File(context.filesDir, "capture_new")
}
