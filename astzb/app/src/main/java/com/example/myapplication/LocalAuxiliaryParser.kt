package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject

object LocalAuxiliaryParser {

    fun tryParse(packet: LocalStzbPacket) {
        if (packet.msgId == "103") {
            val teamUsers = parseTeamUserRows(packet)
            if (teamUsers.isNotEmpty()) {
                LocalStzbRepository.saveTeamUsers(teamUsers)
                PacketLogStore.add("103 同盟成员专表入库：${teamUsers.size} 人")
            }
        }
        if (packet.msgId == "5026") {
            val cells = parseMapCellRows(packet)
            if (cells.isNotEmpty()) {
                LocalStzbRepository.saveMapCells(cells)
                PacketLogStore.add("5026 地图格子专表入库：${cells.size} 个")
            }
        }
        if (packet.msgId == "700") {
            val (unions, players) = parseRankRows(packet)
            if (unions.isNotEmpty() || players.isNotEmpty()) {
                LocalStzbRepository.saveUnionRanks(unions, players)
                PacketLogStore.add("700 排行专表入库：同盟=${unions.size} 玩家=${players.size}")
            }
        }
        if (packet.msgId == "510") {
            parsePlayerStatsRow(packet)?.let {
                LocalStzbRepository.savePlayerStats(it)
                PacketLogStore.add("510 玩家统计专表入库：${it.userName.ifBlank { it.userId.toString() }}")
            }
        }
        if (packet.msgId == "780") {
            val rows = parseAnnouncementRows(packet)
            if (rows.isNotEmpty()) {
                LocalStzbRepository.saveAnnouncements(rows)
                PacketLogStore.add("780 公告专表入库：${rows.size} 条")
            }
        }
        if (packet.msgId == "671") {
            val rows = parseHeroUnlockRows(packet)
            if (rows.isNotEmpty()) {
                LocalStzbRepository.saveHeroUnlocks(rows)
                PacketLogStore.add("671 武将解锁专表入库：${rows.size} 条")
            }
        }
        if (packet.msgId == "21") {
            parsePlayerSelfRow(packet)?.let {
                LocalStzbRepository.savePlayerSelf(it)
                PacketLogStore.add("21 当前角色专表入库：${it.name.ifBlank { "未知角色" }}")
            }
        }
        if (packet.msgId == "6243") {
            val rows = parseZonePlayerRows(packet)
            if (rows.isNotEmpty()) {
                LocalStzbRepository.saveZonePlayers(rows)
                PacketLogStore.add("6243 战区玩家专表入库：${rows.size} 人")
            }
        }
        if (packet.msgId == "90005") {
            val rows = parseDbSyncRows(packet)
            if (rows.isNotEmpty()) {
                LocalStzbRepository.saveDbSync(rows)
                PacketLogStore.add("90005 db_sync 专表入库：${rows.size} 条")
            }
        }
        if (packet.msgId == "301") {
            parseMarchRow(packet)?.let {
                LocalStzbRepository.saveMarchEvent(it)
                PacketLogStore.add("301 玩家行军专表入库：wid=${it.wid} 队伍=${it.troopCount}")
            }
        }
        val records = businessRecords(packet)
        if (records.isNotEmpty()) {
            LocalStzbRepository.saveRecords(records)
            PacketLogStore.add("${packet.msgId} 本机业务记录入库：${records.size} 条 type=${records.first().type}")
        }
    }

    internal fun businessRecords(packet: LocalStzbPacket): List<LocalRecord> = when (packet.msgId) {
            "103" -> parseTeamUsers(packet)
            "510" -> parsePlayerStats(packet)
            "5026" -> parseMapCells(packet)
            "6314" -> parseUnionBuildingHelp(packet)
            "301" -> parseMarch(packet)
            "700" -> parseRanks(packet)
            "780" -> parseAnnouncements(packet)
            "671" -> parseHeroUnlock(packet)
            "21" -> parsePlayerSelf(packet)
            "6243" -> parseZonePlayers(packet)
            "90005" -> parseDbSync(packet)
            "10", "92" -> parseFullBattleRaw(packet)
            else -> emptyList()
        }

    private fun parseTeamUsers(packet: LocalStzbPacket): List<LocalRecord> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val row = arr.optJSONArray(idx) ?: return@mapNotNull null
            if (row.length() < 31) return@mapNotNull null
            val uid = row.optLong(0, 0L)
            val name = row.optString(1, "")
            if (uid <= 0L && name.isBlank()) return@mapNotNull null
            LocalRecord(
                type = "team_user",
                key = uid.toString(),
                title = name.ifBlank { "uid:$uid" },
                subtitle = "势力=${row.optInt(8, 0)} 武勋=${row.optInt(10, 0)} 分组=${row.optString(13, "")}",
                rawJson = row.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    internal fun parseTeamUserRows(packet: LocalStzbPacket): List<LocalTeamUser> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val row = arr.optJSONArray(idx) ?: return@mapNotNull null
            if (row.length() < 31) return@mapNotNull null
            val uid = row.optLong(0, 0L)
            val name = row.optString(1, "")
            if (uid <= 0L && name.isBlank()) return@mapNotNull null
            LocalTeamUser(
                uid = uid,
                name = name,
                contributeTotal = row.optInt(2, 0),
                contributeWeek = row.optInt(7, 0),
                pos = row.optInt(3, 0),
                wid = row.optInt(6, 0),
                power = row.optInt(8, 0),
                wuxun = row.optInt(10, 0),
                groupName = row.optString(13, ""),
                headId = row.optInt(16, 0),
                headFrame = row.optString(17, ""),
                weekWuxun = row.optInt(26, 0),
                totalWuxun = row.optInt(27, 0),
                heroConfigId = 0,
                teamId = 0,
                heroSkills = "",
                joinTime = row.optLong(30, 0L),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parsePlayerStats(packet: LocalStzbPacket): List<LocalRecord> {
        val obj = jsonObject(packet.decodedText) ?: return emptyList()
        val uid = obj.optLong("userid", 0L)
        if (uid <= 0L) return emptyList()
        return listOf(
            LocalRecord(
                type = "player_stats",
                key = uid.toString(),
                title = obj.optString("user_name", "uid:$uid"),
                subtitle = "城=${obj.optInt("city_count")} 地=${obj.optInt("land_count")} 武勋=${obj.optInt("wuxun_total")}",
                rawJson = obj.toString(),
                sourceMsgId = packet.msgId,
            )
        )
    }

    private fun parsePlayerStatsRow(packet: LocalStzbPacket): LocalPlayerStats? {
        val obj = jsonObject(packet.decodedText) ?: return null
        val uid = obj.optLong("userid", 0L)
        if (uid <= 0L) return null
        return LocalPlayerStats(
            userId = uid,
            userName = obj.optString("user_name", ""),
            cityCount = obj.optInt("city_count", 0),
            landCount = obj.optInt("land_count", 0),
            forceMax = obj.optInt("force_max", 0),
            powerMax = obj.optInt("power_max", 0),
            season = obj.optInt("season", 0),
            wuxunTotal = obj.optInt("wuxun_total", 0),
            wuxunCurrentWeek = obj.optInt("wuxun_cur_week", 0),
            wuxunLastWeek = obj.optInt("wuxun_last_week", 0),
            killEnemyCount = obj.optInt("kill_enemy_count", 0),
            killEnemyCurrentWeek = obj.optInt("kill_enemy_count_cur_week", obj.optInt("kill_enemy_cur_week", 0)),
            killAiTotal = obj.optInt("kill_ai_total", 0),
            destroyBuild = obj.optInt("destroy_build", 0),
            grabLandCount = obj.optInt("grab_land_count", 0),
            npcCityDestroy = obj.optInt("npc_city_destroy", 0),
            npcCityKill = obj.optInt("npc_city_kill", 0),
            cfgDbId = obj.optInt("cfg_db_id", 0),
            rawJson = obj.toString(),
            sourceMsgId = packet.msgId,
        )
    }

    private fun parseMapCells(packet: LocalStzbPacket): List<LocalRecord> {
        return extractMapCellEntries(packet).take(500).map { entry ->
            LocalRecord(
                type = "map_cell",
                key = entry.wid.toString(),
                title = entry.name.ifBlank { "地块 ${entry.wid}" },
                subtitle = "type=${entry.cellType} cfg=${entry.configId} key=${entry.key}",
                rawJson = entry.payload.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseMapCellRows(packet: LocalStzbPacket): List<LocalMapCell> {
        return extractMapCellEntries(packet).map { entry ->
            LocalMapCell(
                wid = entry.wid,
                x = entry.wid / 10000,
                y = entry.wid % 10000,
                cellType = entry.cellType,
                typeName = localCellTypeName(entry.cellType),
                buildingId = entry.configId,
                ownerName = entry.ownerName,
                cityName = entry.name,
                parentWid = entry.parentWid,
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun extractMapCellEntries(packet: LocalStzbPacket): List<MapCellEntry> {
        val text = normalizeJsKeys(packet.decodedText)
        val root = jsonArray(text) ?: return emptyList()
        val out = linkedMapOf<Int, MapCellEntry>()

        fun addEntry(key: String, arr: JSONArray) {
            val entry = arr.toMapCellEntry(key) ?: return
            out[entry.wid] = entry
        }

        fun collect(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (key.toIntOrNull() != null) {
                            when (child) {
                                is JSONArray -> addEntry(key, child)
                                is JSONObject -> child.optJSONArray("0")?.let { addEntry(key, it) } ?: collect(child)
                                else -> Unit
                            }
                        } else {
                            collect(child)
                        }
                    }
                }
                is JSONArray -> {
                    for (idx in 0 until value.length()) collect(value.opt(idx))
                }
            }
        }

        // 5026/5028 world chunks live only in top-level slot [14]. Do not
        // recursively scan subjects, armies, unions, or other slots: their
        // numeric keys can look like WIDs and overwrite real map cells.
        val chunks = root.optJSONObject(14) ?: return emptyList()
        collect(chunks)
        if (out.isEmpty()) {
            PacketLogStore.add("5026 地图格子解析未命中已知结构：${packet.preview.take(120)}")
        }
        return out.values.toList()
    }

    private fun JSONArray.toMapCellEntry(key: String): MapCellEntry? {
        if (length() < 2) return null
        val first = opt(0)
        return if (first is String) {
            val wid = optInt(1, 0)
            if (wid <= 0) return null
            MapCellEntry(
                key = key,
                wid = wid,
                name = first,
                cellType = optInt(2, 0),
                configId = optInt(2, 0),
                ownerName = "",
                parentWid = key.toIntOrNull() ?: 0,
                payload = this,
            )
        } else {
            val wid = key.toIntOrNull() ?: return null
            MapCellEntry(
                key = key,
                wid = wid,
                name = optString(6, optString(5, "")),
                cellType = optInt(0, 0),
                configId = optInt(1, 0),
                ownerName = optString(5, ""),
                parentWid = optInt(7, 0),
                payload = this,
            )
        }
    }

    private fun localCellTypeName(cellType: Int): String {
        return when (cellType) {
            1 -> "土地"
            2 -> "城池"
            3 -> "关卡"
            4 -> "码头"
            5 -> "要塞"
            6 -> "军营"
            7 -> "城区"
            else -> "type$cellType"
        }
    }

    private data class MapCellEntry(
        val key: String,
        val wid: Int,
        val name: String,
        val cellType: Int,
        val configId: Int,
        val ownerName: String,
        val parentWid: Int,
        val payload: JSONArray,
    )

    private fun parseUnionBuildingHelp(packet: LocalStzbPacket): List<LocalRecord> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val row = arr.optJSONArray(idx) ?: return@mapNotNull null
            if (row.length() < 3) return@mapNotNull null
            val recordId = row.optLong(0, 0L)
            if (recordId <= 0L) return@mapNotNull null
            LocalRecord(
                type = "union_building_help",
                key = recordId.toString(),
                title = "同盟建筑互助",
                subtitle = "记录 $recordId · 互助数据已保留",
                rawJson = row.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseMarch(packet: LocalStzbPacket): List<LocalRecord> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        val wid = arr.optInt(0, 0)
        val troops = arr.optJSONArray(1) ?: JSONArray()
        return listOf(
            LocalRecord(
                type = "march",
                key = "${wid}:${packet.rawHex.hashCode()}",
                title = "行军目标 $wid",
                subtitle = "troops=${troops.length()} dist=${arr.optInt(3, 0)}",
                rawJson = arr.toString(),
                sourceMsgId = packet.msgId,
            )
        )
    }

    private fun parseMarchRow(packet: LocalStzbPacket): LocalMarchEvent? {
        val arr = jsonArray(packet.decodedText) ?: return null
        val wid = arr.optInt(0, 0)
        if (wid <= 0) return null
        val troops = arr.optJSONArray(1) ?: JSONArray()
        return LocalMarchEvent(
            wid = wid,
            dist = arr.optInt(3, 0),
            troopCount = troops.length(),
            troopsJson = troops.toString(),
            sourceMsgId = packet.msgId,
        )
    }

    private fun parseRanks(packet: LocalStzbPacket): List<LocalRecord> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        val rows = arr.optJSONArray(4) ?: return emptyList()
        return (0 until rows.length()).mapNotNull { idx ->
            val row = rows.optJSONArray(idx) ?: return@mapNotNull null
            val rank = row.optInt(0, idx + 1)
            val obj = row.optJSONObject(1) ?: return@mapNotNull null
            val type = if (obj.has("union_id")) "union_rank" else if (obj.has("user_id") || obj.has("role_id")) "player_power_rank" else return@mapNotNull null
            val key = obj.optString("union_id", obj.optString("user_id", obj.optString("role_id", rank.toString())))
            LocalRecord(
                type = type,
                key = key,
                title = obj.optString("name", key),
                subtitle = "rank=$rank power=${obj.optLong("power", 0L)} force=${obj.optLong("force", 0L)}",
                rawJson = obj.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseRankRows(packet: LocalStzbPacket): Pair<List<LocalUnionRank>, List<LocalPlayerPowerRank>> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList<LocalUnionRank>() to emptyList()
        val rows = arr.optJSONArray(4) ?: return emptyList<LocalUnionRank>() to emptyList()
        val unions = mutableListOf<LocalUnionRank>()
        val players = mutableListOf<LocalPlayerPowerRank>()
        for (idx in 0 until rows.length()) {
            val row = rows.optJSONArray(idx) ?: continue
            val rank = row.optInt(0, idx + 1)
            val obj = row.optJSONObject(1) ?: continue
            if (obj.has("union_id") && obj.has("name") && hasUnionShape(obj)) {
                unions += LocalUnionRank(
                    unionId = obj.optInt("union_id", 0),
                    name = obj.optString("name", ""),
                    level = obj.optInt("level", 0),
                    power = obj.optLong("power", 0L),
                    force = obj.optLong("force", 0L),
                    totalMember = obj.optInt("total_member", obj.optInt("user_count", 0)),
                    occupyCityValue = obj.optInt("occupy_city_value", obj.optInt("branch_city_count", 0)),
                    totalNpcCity = obj.optInt("total_npc_city", obj.optInt("city_count", 0)),
                    region = obj.optInt("region", 0),
                    area = obj.optInt("area", 0),
                    rank = rank,
                    refreshTime = obj.optLong("refresh_time", 0L),
                    sourceMsgId = packet.msgId,
                )
            } else if (obj.has("user_id") || obj.has("role_id")) {
                players += LocalPlayerPowerRank(
                    userId = obj.optLong("user_id", 0L),
                    roleId = obj.optString("role_id", ""),
                    name = obj.optString("name", ""),
                    power = obj.optLong("power", 0L),
                    force = obj.optLong("force", 0L),
                    area = obj.optInt("area", 0),
                    region = obj.optInt("region", 0),
                    landCount = obj.optInt("land_count", 0),
                    fortCount = obj.optInt("fort_count", 0),
                    branchCityCount = obj.optInt("branch_city_count", 0),
                    shuChengCount = obj.optInt("shu_cheng_count", 0),
                    refreshTime = obj.optLong("refresh_time", 0L),
                    rank = rank,
                    sourceMsgId = packet.msgId,
                )
            }
        }
        return unions to players
    }

    private fun hasUnionShape(obj: JSONObject): Boolean {
        return listOf("user_count", "city_count", "total_member", "total_npc_city", "boss", "state")
            .any { obj.has(it) }
    }

    private fun parseAnnouncements(packet: LocalStzbPacket): List<LocalRecord> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val row = arr.optJSONArray(idx) ?: return@mapNotNull null
            val title = row.optString(0, "")
            val content = row.optString(1, "")
            if (title.isBlank() && content.isBlank()) return@mapNotNull null
            val annId = row.optString(4, "${row.optLong(2, 0L)}:$idx")
            LocalRecord(
                type = "announcement",
                key = annId,
                title = title.ifBlank { "公告 $annId" },
                subtitle = content.take(80),
                rawJson = row.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseAnnouncementRows(packet: LocalStzbPacket): List<LocalAnnouncement> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val row = arr.optJSONArray(idx) ?: return@mapNotNull null
            val title = row.optString(0, "")
            val content = row.optString(1, "")
            if (title.isBlank() && content.isBlank()) return@mapNotNull null
            val pubTime = row.optLong(2, 0L)
            LocalAnnouncement(
                annId = row.optLong(4, if (pubTime > 0L) pubTime else idx.toLong()),
                title = title,
                content = content,
                pubTime = pubTime,
                annType = row.optInt(3, 0),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseHeroUnlock(packet: LocalStzbPacket): List<LocalRecord> {
        return packet.decodedText.split(';').mapNotNull { part ->
            val seg = part.trim().split(',')
            if (seg.size < 2) return@mapNotNull null
            val heroId = seg[0].toLongOrNull() ?: return@mapNotNull null
            val ts = seg[1].toLongOrNull() ?: 0L
            LocalRecord(
                type = "hero_unlock",
                key = "$heroId:$ts",
                title = "武将$heroId",
                subtitle = "unlock=$ts",
                rawJson = part,
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseHeroUnlockRows(packet: LocalStzbPacket): List<LocalHeroUnlock> {
        return packet.decodedText.split(';').mapNotNull { part ->
            val seg = part.trim().split(',')
            if (seg.size < 2) return@mapNotNull null
            val heroId = seg[0].toLongOrNull() ?: return@mapNotNull null
            val ts = seg[1].toLongOrNull() ?: 0L
            if (heroId <= 0L) return@mapNotNull null
            LocalHeroUnlock(
                heroId = heroId,
                heroName = HeroNameResolver.nameOf(heroId),
                unlockTime = ts,
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parsePlayerSelf(packet: LocalStzbPacket): List<LocalRecord> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        val name = arr.optString(0, "")
        return listOf(
            LocalRecord(
                type = "player_self",
                key = "self",
                title = name.ifBlank { "当前角色" },
                subtitle = "兵力=${arr.optInt(5, 0)}/${arr.optInt(4, 0)} 资源=${arr.optInt(6, 0)}/${arr.optInt(7, 0)}",
                rawJson = arr.toString(),
                sourceMsgId = packet.msgId,
            )
        )
    }

    private fun parsePlayerSelfRow(packet: LocalStzbPacket): LocalPlayerSelf? {
        val arr = jsonArray(packet.decodedText) ?: return null
        if (arr.length() < 10) return null
        return LocalPlayerSelf(
            name = arr.optString(0, ""),
            force = arr.optInt(4, 0),
            forceCurrent = arr.optInt(5, 0),
            food = arr.optInt(6, 0),
            wood = arr.optInt(7, 0),
            speed = arr.optInt(10, 0),
            marchMax = arr.optInt(11, 0),
            rawJson = arr.toString(),
            sourceMsgId = packet.msgId,
        )
    }

    private fun parseZonePlayers(packet: LocalStzbPacket): List<LocalRecord> {
        val root = jsonArray(packet.decodedText) ?: return emptyList()
        val rows = flattenRows(root)
        return rows.mapNotNull { row ->
            val uid = row.optLong(0, 0L)
            val name = row.optString(2, "")
            if (uid <= 0L && name.isBlank()) return@mapNotNull null
            LocalRecord(
                type = "zone_player",
                key = uid.toString(),
                title = name.ifBlank { "uid:$uid" },
                subtitle = "power=${row.optLong(3, 0L)} wid=${row.optInt(6, 0)} union=${row.optLong(14, 0L)}",
                rawJson = row.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseZonePlayerRows(packet: LocalStzbPacket): List<LocalZonePlayer> {
        val root = jsonArray(packet.decodedText) ?: return emptyList()
        return flattenRows(root).mapNotNull { row ->
            val uid = row.optLong(0, 0L)
            val name = row.optString(2, "")
            if (uid <= 0L && name.isBlank()) return@mapNotNull null
            LocalZonePlayer(
                uid = uid,
                roleId = row.optString(1, ""),
                name = name,
                power = row.optLong(3, 0L),
                wid = row.optInt(6, 0),
                posType = row.optInt(7, 0),
                lastActive = row.optLong(10, 0L),
                joinTime = row.optLong(12, 0L),
                unionId = row.optLong(14, 0L),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseDbSync(packet: LocalStzbPacket): List<LocalRecord> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val row = arr.optJSONArray(idx) ?: return@mapNotNull null
            val table = row.optString(1, "")
            if (table.isBlank()) return@mapNotNull null
            LocalRecord(
                type = "db_sync",
                key = "$table:${row.optString(0)}:${idx}:${row.toString().hashCode()}",
                title = table,
                subtitle = "op=${row.optString(0)}",
                rawJson = row.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseDbSyncRows(packet: LocalStzbPacket): List<LocalDbSyncEvent> {
        val arr = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val row = arr.optJSONArray(idx) ?: return@mapNotNull null
            val table = row.optString(1, "")
            if (table.isBlank()) return@mapNotNull null
            val rowData = row.optJSONArray(2)
            LocalDbSyncEvent(
                op = row.optInt(0, 0),
                tableName = table,
                rowId = rowData?.optLong(0, 0L) ?: 0L,
                rawJson = row.toString(),
                sourceMsgId = packet.msgId,
            )
        }
    }

    private fun parseFullBattleRaw(packet: LocalStzbPacket): List<LocalRecord> {
        val text = packet.decodedText.trim()
        if (text.isBlank()) return emptyList()
        return listOf(
            LocalRecord(
                type = if (packet.msgId == "10") "battle_full_raw" else "union_battle_raw",
                key = "${packet.msgId}:${text.hashCode()}",
                title = "完整战报原始包 ${packet.msgId}",
                subtitle = text.take(120),
                rawJson = text,
                sourceMsgId = packet.msgId,
            )
        )
    }

    private fun flattenRows(root: JSONArray): List<JSONArray> {
        val out = mutableListOf<JSONArray>()
        for (i in 0 until root.length()) {
            val item = root.optJSONArray(i) ?: continue
            if (item.length() > 0 && item.opt(0) is JSONArray) {
                for (j in 0 until item.length()) {
                    item.optJSONArray(j)?.let(out::add)
                }
            } else {
                out += item
            }
        }
        return out
    }

    private fun jsonArray(text: String): JSONArray? {
        val trimmed = text.trim().trimEnd('\u0000').trim()
        return runCatching { JSONArray(trimmed) }.getOrNull()
    }

    private fun jsonObject(text: String): JSONObject? {
        val trimmed = text.trim().trimEnd('\u0000').trim()
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    private fun normalizeJsKeys(text: String): String {
        return text.trim().trimEnd('\u0000').trim()
            .replace(Regex("(?<=[{,])\\s*(\\d+)\\s*(?=:)"), "\"$1\"")
    }
}
