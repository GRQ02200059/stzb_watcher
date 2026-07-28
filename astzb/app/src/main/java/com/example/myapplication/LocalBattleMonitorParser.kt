package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject

object LocalBattleMonitorParser {

    fun tryParse(packet: LocalStzbPacket) {
        if (packet.msgId != "5028") return
        val parsed = parse(
            raw = packet.decodedText,
            plainText = packet.preview,
            sourceLabel = packet.streamName.ifBlank { "5028" },
        ) ?: run {
            PacketLogStore.add("5028 本机解析失败：payload 不是可识别列表")
            return
        }
        LocalBattleMonitorStore.update(parsed)
        LocalStzbRepository.saveBattleMonitor(parsed)
        PacketLogStore.add(
            "5028 本机监控：teams=${parsed.teamIds.size} moves=${parsed.moves.size} users=${parsed.subjects.size} marker=${parsed.marker}"
        )
    }

    private fun parse(raw: String, plainText: String = "", sourceLabel: String = ""): LocalBattleMonitorSnapshot? {
        val data = parseJsonArray(raw) ?: return null
        val subjects = linkedMapOf<Int, LocalSubject>()
        val mapStates = linkedMapOf<Int, LocalMapState>()
        val moves = mutableListOf<LocalTeamMove>()
        val teamIds = mutableListOf<Int>()

        // First pass: subjects may appear before or after move arrays depending on packet shape.
        forEachObjectEntry(data) { blockIndex, objId, value ->
            if (value is JSONArray && value.length() > 0 && value.opt(0) is String) {
                val extra = value.optJSONArray(12)
                subjects[objId] = LocalSubject(
                    id = objId,
                    name = value.optString(0, ""),
                    displayId = value.optInt(1, 0),
                    forceId = value.optInt(2, 0),
                    unionName = extra?.optString(2, "").orEmpty(),
                    blockIndex = blockIndex,
                )
            }
        }

        forEachObjectEntry(data) { blockIndex, objId, value ->
            when {
                value is JSONObject -> {
                    mapStates[objId] = LocalMapState(
                        wid = objId,
                        stateCount = value.length(),
                        blockIndex = blockIndex,
                    )
                }

                value is JSONArray && value.length() >= 6 && value.opt(0) is Number -> {
                    val subjectId = value.optInt(1, 0)
                    val subject = subjects[subjectId]
                    val fromWid = value.optInt(2, 0)
                    val toWidCandidate = value.optInt(3, 0)
                    val currentWid = value.optInt(10, 0)
                    val toWid = when {
                        toWidCandidate > 10000 -> toWidCandidate
                        currentWid > 10000 -> currentWid
                        else -> fromWid
                    }
                    moves += LocalTeamMove(
                        teamId = objId,
                        moveType = value.optInt(0, 0),
                        subjectId = subjectId,
                        ownerUid = subject?.displayId ?: 0,
                        ownerName = subject?.name.orEmpty(),
                        ownerUnion = subject?.unionName.orEmpty(),
                        fromWid = fromWid,
                        toWid = toWid,
                        currentWid = currentWid,
                        fromXy = widToXy(fromWid),
                        toXy = widToXy(toWid),
                        currentXy = widToXy(currentWid),
                        startTime = value.optLong(4, 0L),
                        arriveTime = value.optLong(5, 0L),
                        speed = value.optInt(29, 0),
                    )
                    teamIds += objId
                }
            }
        }

        if (teamIds.isEmpty() && mapStates.isNotEmpty()) {
            teamIds += mapStates.keys
        }

        return LocalBattleMonitorSnapshot(
            teamIds = teamIds.distinct(),
            moves = moves,
            subjects = subjects.values.toList(),
            mapStates = mapStates.values.toList(),
            marker = data.optInt(18, 0),
            rawLength = data.length(),
            plainText = plainText.trim(),
            sourceLabel = sourceLabel.trim(),
        )
    }

    private fun parseJsonArray(raw: String): JSONArray? {
        val text = raw.trim().trimEnd('\u0000').trim()
        if (text.isBlank()) return null
        val normalized = text.replace(Regex("(?<=[{,])\\s*(\\d+)\\s*(?=:)"), "\"$1\"")
        return runCatching { JSONArray(normalized) }.getOrNull()
    }

    private fun forEachObjectEntry(
        data: JSONArray,
        block: (blockIndex: Int, objId: Int, value: Any) -> Unit,
    ) {
        for (blockIndex in 0 until data.length()) {
            val obj = data.optJSONObject(blockIndex) ?: continue
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val objId = key.toIntOrNull() ?: continue
                if (objId <= 0) continue
                val value = obj.opt(key) ?: continue
                block(blockIndex, objId, value)
            }
        }
    }

    private fun widToXy(wid: Int): String {
        if (wid <= 0) return ""
        return "${wid / 10000},${wid % 10000}"
    }
}

data class LocalBattleMonitorSnapshot(
    val teamIds: List<Int>,
    val moves: List<LocalTeamMove>,
    val subjects: List<LocalSubject>,
    val mapStates: List<LocalMapState>,
    val marker: Int,
    val rawLength: Int,
    val plainText: String = "",
    val sourceLabel: String = "",
    val capturedAt: Long = System.currentTimeMillis(),
)

data class LocalTeamMove(
    val teamId: Int,
    val moveType: Int,
    val subjectId: Int,
    val ownerUid: Int,
    val ownerName: String,
    val ownerUnion: String,
    val fromWid: Int,
    val toWid: Int,
    val currentWid: Int,
    val fromXy: String,
    val toXy: String,
    val currentXy: String,
    val startTime: Long,
    val arriveTime: Long,
    val speed: Int,
)

data class LocalSubject(
    val id: Int,
    val name: String,
    val displayId: Int,
    val forceId: Int,
    val unionName: String,
    val blockIndex: Int,
)

data class LocalMapState(
    val wid: Int,
    val stateCount: Int,
    val blockIndex: Int,
)

object LocalBattleMonitorStore {
    private var latest: LocalBattleMonitorSnapshot? = null
    private val history = ArrayDeque<LocalBattleMonitorSnapshot>()
    private const val HISTORY_LIMIT = 0

    @Synchronized
    fun update(snapshot: LocalBattleMonitorSnapshot) {
        latest = snapshot
        val last = history.firstOrNull()
        if (last == null || last.marker != snapshot.marker || last.rawLength != snapshot.rawLength || last.teamIds != snapshot.teamIds) {
            history.addFirst(snapshot)
            while (HISTORY_LIMIT > 0 && history.size > HISTORY_LIMIT) history.removeLast()
        }
    }

    @Synchronized
    fun latest(): LocalBattleMonitorSnapshot? = latest

    @Synchronized
    fun history(): List<LocalBattleMonitorSnapshot> = history.toList()

    @Synchronized
    fun clear() {
        latest = null
        history.clear()
    }
}
