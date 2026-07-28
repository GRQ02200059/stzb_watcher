package com.example.myapplication

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalMigrationDiagnostics {
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun export(context: Context): File {
        val outDir = File(context.filesDir, "exports")
        if (!outDir.exists()) outDir.mkdirs()
        val file = File(outDir, "stzb_diagnostics_${fileFormat.format(Date())}.txt")
        val counts = LocalStzbRepository.counts()
        val packets = LocalStzbRepository.loadRecentPackets(200)
        val msgDist = packets.groupingBy { it.msgId }.eachCount().toSortedMap(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })

        file.writeText(
            buildString {
                appendLine("ASTZB Android 真机迁移诊断")
                appendLine("generated_at=${System.currentTimeMillis()}")
                appendLine()
                appendLine("== counts ==")
                appendLine("packets=${counts.packets}")
                appendLine("full_battles=${counts.fullBattles}")
                appendLine("battle_notices=${counts.battleNotices}")
                appendLine("chats=${counts.chats}")
                appendLine("monitor_moves=${counts.monitorMoves}")
                appendLine("team_users=${counts.teamUsers}")
                appendLine("map_cells=${counts.mapCells}")
                appendLine("union_ranks=${counts.unionRanks}")
                appendLine("player_power_ranks=${counts.playerPowerRanks}")
                appendLine("local_records=${counts.localRecords}")
                appendLine()
                appendLine("== recent msg_id distribution ==")
                if (msgDist.isEmpty()) appendLine("empty")
                msgDist.forEach { (msgId, count) -> appendLine("$msgId=$count") }
                appendLine()
                appendLine("== recent packets preview ==")
                packets.take(80).forEachIndexed { idx, packet ->
                    appendLine("-- packet ${idx + 1} --")
                    appendLine("msg_id=${packet.msgId}")
                    appendLine("decode=${packet.decodeKind}")
                    appendLine("type=${packet.dataType}")
                    appendLine("stream=${packet.streamName}")
                    appendLine("preview=${packet.preview}")
                    appendLine("raw_hex_prefix=${packet.rawHex.take(240)}")
                }
                appendLine()
                appendLine("capture_root=${LocalStzbCaptureWriter.captureRoot(context).absolutePath}")
            },
            Charsets.UTF_8,
        )
        return file
    }
}
