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
        val published = LocalBattleMonitorStore.update(parsed, packet.msgId)
        if (published) {
            LocalBattleMonitorStore.latest()?.let { LocalStzbRepository.saveBattleMonitor(it, packet.msgId) }
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
                if (value is JSONObject) {
                    mapStates[wid] = LocalMapState(wid, value.length(), WORLD_CHUNKS_SLOT, value.toString())
                }
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
        val entities = buildList {
            addAll(genericEntities("strategy", data.opt(4)))
            addAll(genericEntities("nation_strategy", data.opt(5)))
            addAll(genericEntities("war_ship", data.opt(8)))
            addAll(genericEntities("assist_army", data.opt(10)))
            addAll(genericEntities("army_group", data.opt(12)))
            addAll(genericEntities("short_message", data.opt(13)))
            addAll(genericEntities("ext_garrison", data.opt(16)))
            addAll(genericEntities("manor_family", data.opt(19)))
            addAll(genericEntities("career_support", data.opt(24)))
        }
        val directEntityDeletes = entities
            .filter(LocalWorldEntity::deleted)
            .groupBy(LocalWorldEntity::category, LocalWorldEntity::entityId)

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
            clearChunks = data.optJSONObject(CLEAR_CHUNKS_SLOT)?.let { chunks ->
                buildMap {
                    forEachEntry(chunks) { wid, value ->
                        val chunkTypes = value as? JSONArray ?: return@forEachEntry
                        put(wid, buildList {
                            for (index in 0 until chunkTypes.length()) {
                                chunkTypes.optString(index).takeIf(String::isNotBlank)?.let(::add)
                            }
                        })
                    }
                }
            }.orEmpty(),
            realMarches = data.optJSONObject(REAL_MARCH_SLOT)?.let { marches ->
                buildList {
                    forEachEntry(marches) { id, value ->
                        if (value is JSONArray && value.length() >= 14) {
                            add(LocalRealMarch(
                                id = id,
                                lastWid = value.optInt(0),
                                currentWid = value.optInt(1),
                                currentArriveTime = value.optLong(2),
                                nextWid = value.optInt(3),
                                nextBeginTime = value.optLong(4),
                                nextNeedTime = value.optLong(5),
                                nextSpendTime = value.optLong(6),
                                pathId = value.optInt(7),
                                unitTimeCost = value.optInt(8),
                                marchType = value.optInt(9),
                                belongId = value.optInt(10),
                                morale = value.optInt(11),
                                moraleStayLastCalcTime = value.optLong(12),
                                moraleHungryLastCalcTime = value.optLong(13),
                            ))
                        }
                    }
                }
            }.orEmpty(),
            changedTeamIds = moves.map { it.teamId },
            entities = entities.filterNot(LocalWorldEntity::deleted),
            directDeletedEntityIds = directEntityDeletes.mapValues { (_, ids) -> ids.distinct() },
            deletedEntityIds = buildMap {
                mergeDeleteIds("war_ship", idList(data.optJSONArray(9)))
                mergeDeleteIds("assist_army", idList(data.optJSONArray(11)))
                mergeDeleteIds("career_support", idList(data.optJSONArray(25)))
                mergeDeleteIds("short_message", idList(data.optJSONArray(26)))
                mergeDeleteIds("strategy", idList(data.optJSONArray(27)))
            },
            blockShipIds = blockMemberships(data.optJSONObject(22)),
            blockAssistArmyIds = blockMemberships(data.optJSONObject(23)),
            slotPayloads = (0 until WORLD_SCENE_SLOT_COUNT).associateWith { index ->
                data.opt(index)?.toString() ?: "null"
            },
        )
    }

    private fun genericEntities(category: String, value: Any?): List<LocalWorldEntity> {
        val obj = value as? JSONObject ?: return emptyList()
        return buildList {
            forEachEntry(obj) { id, raw ->
                val deleted = raw is JSONArray && raw.length() > 0 && raw.optInt(0, -1) == 0
                add(LocalWorldEntity(category, id, raw.toString(), deleted))
            }
        }
    }

    private fun idList(value: JSONArray?): List<Int> = buildList {
        if (value != null) for (index in 0 until value.length()) {
            value.optInt(index).takeIf { it > 0 }?.let(::add)
        }
    }.distinct()

    private fun blockMemberships(value: JSONObject?): Map<Int, List<Int>> = value?.let { blocks ->
        buildMap {
            forEachEntry(blocks) { blockId, raw ->
                val ids = raw as? JSONArray ?: return@forEachEntry
                put(blockId, idList(ids))
            }
        }
    }.orEmpty()

    private fun MutableMap<String, List<Int>>.mergeDeleteIds(category: String, ids: List<Int>) {
        if (ids.isNotEmpty()) put(category, (get(category).orEmpty() + ids).distinct())
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
    private const val CLEAR_CHUNKS_SLOT = 15
    private const val BLOCK_INFO_SLOT = 20
    private const val BLOCK_ARMIES_SLOT = 21
    private const val REAL_MARCH_SLOT = 29
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
    val clearChunks: Map<Int, List<String>> = emptyMap(),
    val realMarches: List<LocalRealMarch> = emptyList(),
    val changedTeamIds: List<Int> = moves.map { it.teamId },
    val sourceMessageId: String = "",
    val entities: List<LocalWorldEntity> = emptyList(),
    val deletedEntityIds: Map<String, List<Int>> = emptyMap(),
    val directDeletedEntityIds: Map<String, List<Int>> = emptyMap(),
    val blockShipIds: Map<Int, List<Int>> = emptyMap(),
    val blockAssistArmyIds: Map<Int, List<Int>> = emptyMap(),
    val slotPayloads: Map<Int, String> = emptyMap(),
    val capturedAt: Long = System.currentTimeMillis(),
)

data class LocalWorldEntity(
    val category: String,
    val entityId: Int,
    val rawJson: String,
    val deleted: Boolean = false,
)

data class LocalRealMarch(
    val id: Int,
    val lastWid: Int,
    val currentWid: Int,
    val currentArriveTime: Long,
    val nextWid: Int,
    val nextBeginTime: Long,
    val nextNeedTime: Long,
    val nextSpendTime: Long,
    val pathId: Int,
    val unitTimeCost: Int,
    val marchType: Int,
    val belongId: Int,
    val morale: Int,
    val moraleStayLastCalcTime: Long,
    val moraleHungryLastCalcTime: Long,
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
    val chunksJson: String = "{}",
)

object LocalBattleMonitorStore {
    private var latest: LocalBattleMonitorSnapshot? = null
    private val history = ArrayDeque<LocalBattleMonitorSnapshot>()
    private val currentMoves = linkedMapOf<Int, LocalTeamMove>()
    private val currentSubjects = linkedMapOf<Int, LocalSubject>()
    private val pendingFullMoves = linkedMapOf<Int, LocalTeamMove>()
    private val pendingFullSubjects = linkedMapOf<Int, LocalSubject>()
    private val currentEntities = linkedMapOf<Pair<String, Int>, LocalWorldEntity>()
    private val pendingFullEntities = linkedMapOf<Pair<String, Int>, LocalWorldEntity>()
    private val pendingFullMapStates = linkedMapOf<Int, LocalMapState>()
    private val pendingFullRealMarches = linkedMapOf<Int, LocalRealMarch>()
    private val pendingFullBlockArmies = linkedMapOf<Int, MutableSet<Int>>()
    private val pendingFullBlockShips = linkedMapOf<Int, MutableSet<Int>>()
    private val pendingFullBlockAssistArmies = linkedMapOf<Int, MutableSet<Int>>()
    private val pendingFullSlots = linkedMapOf<Int, String>()
    private val armyBlocks = linkedMapOf<Int, MutableSet<Int>>()
    private val shipBlocks = linkedMapOf<Int, MutableSet<Int>>()
    private val assistArmyBlocks = linkedMapOf<Int, MutableSet<Int>>()
    private var assemblingFullSnapshot = false
    private var fullSnapshotMarker = -1
    private const val HISTORY_LIMIT = 50

    @Synchronized
    fun update(snapshot: LocalBattleMonitorSnapshot, sourceMessageId: String = "5028"): Boolean {
        if (sourceMessageId == "5026") {
            if (!assemblingFullSnapshot) {
                pendingFullMoves.clear()
                pendingFullSubjects.clear()
                pendingFullEntities.clear()
                pendingFullMapStates.clear()
                pendingFullRealMarches.clear()
                pendingFullBlockArmies.clear()
                pendingFullBlockShips.clear()
                pendingFullBlockAssistArmies.clear()
                pendingFullSlots.clear()
                armyBlocks.clear()
                shipBlocks.clear()
                assistArmyBlocks.clear()
                assemblingFullSnapshot = true
            }
            snapshot.subjects.forEach { pendingFullSubjects[it.id] = it }
            snapshot.moves.forEach { pendingFullMoves[it.teamId] = it }
            snapshot.entities.forEach { pendingFullEntities[it.category to it.entityId] = it }
            snapshot.mapStates.forEach { pendingFullMapStates[it.wid] = it }
            snapshot.realMarches.forEach { pendingFullRealMarches[it.id] = it }
            mergePendingMemberships(pendingFullBlockArmies, snapshot.blockArmyIds)
            mergePendingMemberships(pendingFullBlockShips, snapshot.blockShipIds)
            mergePendingMemberships(pendingFullBlockAssistArmies, snapshot.blockAssistArmyIds)
            snapshot.slotPayloads.forEach { (index, raw) ->
                pendingFullSlots[index] = mergeSlotPayload(pendingFullSlots[index], raw)
            }
            snapshot.blockArmyIds.forEach { (block, ids) -> ids.forEach { armyBlocks.getOrPut(it, ::linkedSetOf).add(block) } }
            snapshot.blockShipIds.forEach { (block, ids) -> ids.forEach { shipBlocks.getOrPut(it, ::linkedSetOf).add(block) } }
            snapshot.blockAssistArmyIds.forEach { (block, ids) -> ids.forEach { assistArmyBlocks.getOrPut(it, ::linkedSetOf).add(block) } }
            if (snapshot.marker <= 0) return false
            currentSubjects.clear()
            currentSubjects.putAll(pendingFullSubjects)
            currentMoves.clear()
            pendingFullMoves.forEach { (id, move) -> currentMoves[id] = move.withSubject(currentSubjects) }
            pendingFullSubjects.clear()
            pendingFullMoves.clear()
            currentEntities.clear()
            currentEntities.putAll(pendingFullEntities)
            pendingFullEntities.clear()
            assemblingFullSnapshot = false
            fullSnapshotMarker = snapshot.marker
        } else {
            if (fullSnapshotMarker < 0 || (snapshot.marker != SPECIAL_ORDER_ID && snapshot.marker <= fullSnapshotMarker)) return false
            snapshot.subjects.forEach { currentSubjects[it.id] = it }
            snapshot.entities.forEach { currentEntities[it.category to it.entityId] = it }
            snapshot.directDeletedEntityIds.forEach { (category, ids) ->
                ids.forEach { currentEntities.remove(category to it) }
            }
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
                removeScopedEntities("war_ship", snapshot.deletedEntityIds["war_ship"].orEmpty(), snapshot.blockId, shipBlocks)
                removeScopedEntities("assist_army", snapshot.deletedEntityIds["assist_army"].orEmpty(), snapshot.blockId, assistArmyBlocks)
            }
            snapshot.deletedEntityIds.forEach { (category, ids) ->
                if (category != "war_ship" && category != "assist_army") {
                    ids.forEach { currentEntities.remove(category to it) }
                }
            }
        }
        snapshot.moves.forEach { move ->
            currentMoves[move.teamId] = move.withSubject(currentSubjects)
        }
        val published = if (sourceMessageId == "5026") snapshot.copy(
            mapStates = pendingFullMapStates.values.toList(),
            entities = currentEntities.values.toList(),
            realMarches = pendingFullRealMarches.values.toList(),
            blockArmyIds = pendingFullBlockArmies.mapValues { it.value.toList() },
            blockShipIds = pendingFullBlockShips.mapValues { it.value.toList() },
            blockAssistArmyIds = pendingFullBlockAssistArmies.mapValues { it.value.toList() },
            slotPayloads = pendingFullSlots.toMap(),
        ) else snapshot
        latest = published.copy(
            moves = currentMoves.values.toList(),
            teamIds = currentMoves.keys.toList(),
            subjects = currentSubjects.values.toList(),
            entities = currentEntities.values.toList(),
            sourceMessageId = sourceMessageId,
        )
        val merged = checkNotNull(latest)
        val last = history.firstOrNull()
        if (last == null || last.marker != merged.marker || last.rawLength != merged.rawLength || last.teamIds != merged.teamIds || last.moves != merged.moves) {
            history.addFirst(merged)
            while (HISTORY_LIMIT > 0 && history.size > HISTORY_LIMIT) history.removeLast()
        }
        return true
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
        currentEntities.clear()
        pendingFullEntities.clear()
        pendingFullMapStates.clear()
        pendingFullRealMarches.clear()
        pendingFullBlockArmies.clear()
        pendingFullBlockShips.clear()
        pendingFullBlockAssistArmies.clear()
        pendingFullSlots.clear()
        armyBlocks.clear()
        shipBlocks.clear()
        assistArmyBlocks.clear()
        assemblingFullSnapshot = false
        fullSnapshotMarker = -1
        history.clear()
    }

    private fun LocalTeamMove.withSubject(subjects: Map<Int, LocalSubject>): LocalTeamMove {
        val subject = subjects[subjectId] ?: return this
        return copy(ownerUid = subjectId, ownerName = subject.name, ownerUnion = subject.unionName)
    }

    private fun removeScopedEntities(
        category: String,
        ids: List<Int>,
        blockId: Int,
        memberships: MutableMap<Int, MutableSet<Int>>,
    ) {
        ids.forEach { id ->
            memberships[id]?.remove(blockId)
            if (memberships[id].isNullOrEmpty()) {
                memberships.remove(id)
                currentEntities.remove(category to id)
            }
        }
    }

    private fun mergePendingMemberships(
        target: MutableMap<Int, MutableSet<Int>>,
        incoming: Map<Int, List<Int>>,
    ) {
        incoming.forEach { (blockId, ids) -> target.getOrPut(blockId, ::linkedSetOf).addAll(ids) }
    }

    private fun mergeSlotPayload(previous: String?, incoming: String): String {
        if (previous == null || previous in setOf("{}", "[]", "null", "")) return incoming
        if (incoming in setOf("{}", "[]", "null", "")) return previous
        val previousObject = runCatching { JSONObject(previous) }.getOrNull()
        val incomingObject = runCatching { JSONObject(incoming) }.getOrNull()
        if (previousObject != null && incomingObject != null) {
            val keys = incomingObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                previousObject.put(key, incomingObject.opt(key))
            }
            return previousObject.toString()
        }
        val previousArray = runCatching { JSONArray(previous) }.getOrNull()
        val incomingArray = runCatching { JSONArray(incoming) }.getOrNull()
        if (previousArray != null && incomingArray != null) {
            for (index in 0 until incomingArray.length()) previousArray.put(incomingArray.opt(index))
            return previousArray.toString()
        }
        return incoming
    }

    private const val SPECIAL_ORDER_ID = -999999999
}
