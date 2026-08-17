package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject

object LocalBattleMonitorParser {

    fun tryParse(packet: LocalStzbPacket) {
        if (packet.msgId != "5026" && packet.msgId != "5028") return
        val parsed = parse(
            raw = packet.decodedText,
            plainText = packet.preview,
            sourceLabel = packet.streamName.ifBlank { packet.msgId },
        ) ?: run {
            PacketLogStore.add("${packet.msgId} 本机解析失败：payload 不是 31 槽世界场景列表")
            return
        }
        LocalBattleMonitorStore.update(parsed, packet.msgId)
        if (packet.msgId == "5028" || parsed.marker > 0) {
            LocalBattleMonitorStore.latest()?.let(LocalStzbRepository::saveBattleMonitor)
        }
        PacketLogStore.add(
            "${packet.msgId} 本机监控：teams=${parsed.teamIds.size} moves=${parsed.moves.size} users=${parsed.subjects.size} deleted=${parsed.deletedTeamIds.size} marker=${parsed.marker}"
        )
    }

    internal fun parse(raw: String, plainText: String = "", sourceLabel: String = ""): LocalBattleMonitorSnapshot? {
        val data = parseJsonArray(raw) ?: return null
        if (data.length() != WORLD_SCENE_SLOT_COUNT) return null
        val subjects = linkedMapOf<Int, LocalSubject>()
        val mapStates = linkedMapOf<Int, LocalMapState>()
        val moves = mutableListOf<LocalTeamMove>()
        val teamIds = mutableListOf<Int>()

        data.optJSONObject(MAP_USERS_SLOT)?.let { users ->
            forEachEntry(users) { objId, value ->
                if (value !is JSONArray || value.length() < 25 || value.opt(0) !is String) return@forEachEntry
                val extra = value.optJSONArray(12)
                subjects[objId] = LocalSubject(
                    id = objId,
                    name = value.optString(0, ""),
                    displayId = value.optInt(1, 0),
                    forceId = value.optInt(2, 0),
                    unionName = extra?.optString(2, "").orEmpty(),
                    blockIndex = MAP_USERS_SLOT,
                )
            }
        }

        data.optJSONObject(WORLD_CHUNKS_SLOT)?.let { chunks ->
            forEachEntry(chunks) { wid, value ->
                if (value is JSONObject) mapStates[wid] = LocalMapState(wid, value.length(), WORLD_CHUNKS_SLOT)
            }
        }

        data.optJSONObject(ARMIES_SLOT)?.let { armies ->
            forEachEntry(armies) { armyId, value ->
                if (value !is JSONArray || value.length() == 0 || value.opt(0) !is Number) return@forEachEntry
                if (value.optInt(0, 0) != 0 && value.length() >= 32) {
                    val subjectId = value.optInt(1, 0)
                    val subject = subjects[subjectId]
                    val fromWid = value.optInt(2, 0)
                    val toWid = value.optInt(3, 0)
                    val resideWid = value.optInt(10, 0)
                    val stayWid = value.optInt(11, 0)
                    moves += LocalTeamMove(
                        teamId = armyId,
                        moveType = value.optInt(0, 0),
                        subjectId = subjectId,
                        ownerUid = subjectId,
                        ownerName = subject?.name.orEmpty(),
                        ownerUnion = subject?.unionName.orEmpty(),
                        fromWid = fromWid,
                        toWid = toWid,
                        currentWid = stayWid.takeIf { it > 0 } ?: resideWid,
                        fromXy = widToXy(fromWid),
                        toXy = widToXy(toWid),
                        currentXy = widToXy(stayWid.takeIf { it > 0 } ?: resideWid),
                        startTime = value.optLong(4, 0L),
                        arriveTime = value.optLong(5, 0L),
                        speed = 0,
                        armyGroupId = value.optInt(6, 0),
                        centerWid = value.optInt(7, 0),
                        targetType = value.optInt(9, 0),
                        resideWid = resideWid,
                        stayWid = stayWid,
                        invitedUserId = value.optInt(14, 0),
                        armyFacadeList = value.optString(15, ""),
                        armyHeroType = value.optString(16, ""),
                        emotion = value.optString(17, ""),
                        battleEffect = value.optString(18, ""),
                        seriousInjuryTime = value.optLong(21, 0L),
                        fortArmyGroup = value.optInt(22, 0),
                        resideTime = value.optLong(23, 0L),
                        siegeCampNextAttackTime = value.optLong(24, 0L),
                        morale = value.optInt(27, 0),
                        realMarchId = value.optInt(28, 0),
                        buffIdList = value.optString(29, ""),
                        obstacleWid = value.optInt(30, 0),
                        battleShow = value.optString(31, ""),
                        stateId = if (value.length() > 32 && !value.isNull(32)) value.optInt(32) else null,
                    )
                    teamIds += armyId
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
            deletedTeamIds = buildList {
                val deleted = data.optJSONArray(DELETED_ARMIES_SLOT)
                if (deleted != null) for (index in 0 until deleted.length()) deleted.optInt(index).takeIf { it > 0 }?.let(::add)
            }.distinct(),
            directDeletedTeamIds = buildList {
                data.optJSONObject(ARMIES_SLOT)?.let { armies ->
                    forEachEntry(armies) { id, value -> if (value is JSONArray && value.optInt(0, -1) == 0) add(id) }
                }
            }.distinct(),
            blockMode = data.optJSONArray(BLOCK_INFO_SLOT)?.optInt(0, 0) ?: 0,
            blockId = data.optJSONArray(BLOCK_INFO_SLOT)?.optInt(1, 0) ?: 0,
            blockArmyIds = data.optJSONObject(BLOCK_ARMIES_SLOT)?.let { blocks ->
                buildMap {
                    forEachEntry(blocks) { id, value ->
                        val ids = value as? JSONArray ?: return@forEachEntry
                        put(id, buildList { for (index in 0 until ids.length()) ids.optInt(index).takeIf { it > 0 }?.let(::add) })
                    }
                }
            }.orEmpty(),
        )
    }

    private fun parseJsonArray(raw: String): JSONArray? {
        val text = raw.trim().trimEnd('\u0000').trim()
        if (text.isBlank()) return null
        val normalized = text.replace(Regex("(?<=[{,])\\s*(\\d+)\\s*(?=:)"), "\"$1\"")
        return runCatching { JSONArray(normalized) }.getOrNull()
    }

    private fun forEachEntry(obj: JSONObject, block: (objId: Int, value: Any) -> Unit) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val objId = key.toIntOrNull() ?: continue
            if (objId <= 0) continue
            obj.opt(key)?.let { block(objId, it) }
        }
    }

    private fun widToXy(wid: Int): String {
        if (wid <= 0) return ""
        return "${wid / 10000},${wid % 10000}"
    }

    private const val WORLD_SCENE_SLOT_COUNT = 31
    private const val MAP_USERS_SLOT = 1
    private const val ARMIES_SLOT = 6
    private const val DELETED_ARMIES_SLOT = 7
    private const val WORLD_CHUNKS_SLOT = 14
    private const val BLOCK_INFO_SLOT = 20
    private const val BLOCK_ARMIES_SLOT = 21
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
    val deletedTeamIds: List<Int> = emptyList(),
    val directDeletedTeamIds: List<Int> = emptyList(),
    val blockMode: Int = 0,
    val blockId: Int = 0,
    val blockArmyIds: Map<Int, List<Int>> = emptyMap(),
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
    val armyGroupId: Int = 0,
    val centerWid: Int = 0,
    val targetType: Int = 0,
    val resideWid: Int = 0,
    val stayWid: Int = 0,
    val invitedUserId: Int = 0,
    val armyFacadeList: String = "",
    val armyHeroType: String = "",
    val emotion: String = "",
    val battleEffect: String = "",
    val seriousInjuryTime: Long = 0,
    val fortArmyGroup: Int = 0,
    val resideTime: Long = 0,
    val siegeCampNextAttackTime: Long = 0,
    val morale: Int = 0,
    val realMarchId: Int = 0,
    val buffIdList: String = "",
    val obstacleWid: Int = 0,
    val battleShow: String = "",
    val stateId: Int? = null,
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
    private val currentMoves = linkedMapOf<Int, LocalTeamMove>()
    private val currentSubjects = linkedMapOf<Int, LocalSubject>()
    private val pendingFullMoves = linkedMapOf<Int, LocalTeamMove>()
    private val pendingFullSubjects = linkedMapOf<Int, LocalSubject>()
    private val armyBlocks = linkedMapOf<Int, MutableSet<Int>>()
    private var assemblingFullSnapshot = false
    private var fullSnapshotMarker = -1
    private const val HISTORY_LIMIT = 50

    @Synchronized
    fun update(snapshot: LocalBattleMonitorSnapshot, sourceMessageId: String = "5028") {
        if (sourceMessageId == "5026") {
            if (!assemblingFullSnapshot) {
                pendingFullMoves.clear()
                pendingFullSubjects.clear()
                armyBlocks.clear()
                assemblingFullSnapshot = true
            }
            snapshot.subjects.forEach { pendingFullSubjects[it.id] = it }
            snapshot.moves.forEach { pendingFullMoves[it.teamId] = it }
            snapshot.blockArmyIds.forEach { (block, ids) -> ids.forEach { armyBlocks.getOrPut(it, ::linkedSetOf).add(block) } }
            if (snapshot.marker <= 0) return
            currentSubjects.clear()
            currentSubjects.putAll(pendingFullSubjects)
            currentMoves.clear()
            pendingFullMoves.forEach { (id, move) -> currentMoves[id] = move.withSubject(currentSubjects) }
            pendingFullSubjects.clear()
            pendingFullMoves.clear()
            assemblingFullSnapshot = false
            fullSnapshotMarker = snapshot.marker
        } else {
            if (fullSnapshotMarker < 0 || (snapshot.marker != SPECIAL_ORDER_ID && snapshot.marker <= fullSnapshotMarker)) return
            snapshot.subjects.forEach { currentSubjects[it.id] = it }
            snapshot.directDeletedTeamIds.forEach { id ->
                armyBlocks.remove(id)
                currentMoves.remove(id)
            }
            if (snapshot.blockMode == 2 && snapshot.blockId > 0) {
                snapshot.moves.forEach { armyBlocks.getOrPut(it.teamId, ::linkedSetOf).add(snapshot.blockId) }
                snapshot.deletedTeamIds.forEach { id ->
                    armyBlocks[id]?.remove(snapshot.blockId)
                    if (armyBlocks[id].isNullOrEmpty()) { armyBlocks.remove(id); currentMoves.remove(id) }
                }
            }
        }
        snapshot.moves.forEach { move ->
            currentMoves[move.teamId] = move.withSubject(currentSubjects)
        }
        latest = snapshot.copy(moves = currentMoves.values.toList(), teamIds = currentMoves.keys.toList(), subjects = currentSubjects.values.toList())
        val merged = checkNotNull(latest)
        val last = history.firstOrNull()
        if (last == null || last.marker != merged.marker || last.rawLength != merged.rawLength || last.teamIds != merged.teamIds || last.moves != merged.moves) {
            history.addFirst(merged)
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
        currentMoves.clear()
        currentSubjects.clear()
        pendingFullMoves.clear()
        pendingFullSubjects.clear()
        armyBlocks.clear()
        assemblingFullSnapshot = false
        fullSnapshotMarker = -1
        history.clear()
    }

    private fun LocalTeamMove.withSubject(subjects: Map<Int, LocalSubject>): LocalTeamMove {
        val subject = subjects[subjectId] ?: return this
        return copy(ownerUid = subjectId, ownerName = subject.name, ownerUnion = subject.unionName)
    }

    private const val SPECIAL_ORDER_ID = -999999999
}
