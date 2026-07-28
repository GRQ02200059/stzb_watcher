package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject

object Local13A2Parser {

    fun parse(raw: String): Local13A2Payload? {
        val data = jsonArray(normalize(raw)) ?: return null
        if (data.length() < 2) return null

        var subjectPowerMap: JSONObject? = null
        var subjects: JSONObject? = null
        var teamsRaw: JSONObject? = null
        var cellTeamMap: JSONObject? = null
        var cellDetailMap: JSONObject? = null
        var areaRange: List<Int> = emptyList()
        var marker = 0

        for (idx in 0 until data.length()) {
            when (val part = data.opt(idx)) {
                is JSONObject -> {
                    when {
                        subjectPowerMap == null && isSubjectPowerMap(part) -> subjectPowerMap = part
                        subjects == null && isSubjectDict(part) -> subjects = part
                        teamsRaw == null && isTeamDict(part) -> teamsRaw = part
                        cellTeamMap == null && isCellTeamMapDict(part) -> cellTeamMap = part
                        cellDetailMap == null && isCellDetailDict(part) -> cellDetailMap = part
                    }
                }
                is JSONArray -> {
                    val range = part.toIntList()
                    if (range.size == 4) areaRange = range
                }
                is Number -> if (marker == 0 && part.toInt() > 1000) marker = part.toInt()
            }
        }

        val teamToCells = buildTeamToCells(cellTeamMap)
        val items = buildItems(teamsRaw, subjects, subjectPowerMap, teamToCells, cellDetailMap)
            .ifEmpty { buildPseudoItemsFromCells(cellDetailMap, subjects, subjectPowerMap) }
            .sortedWith(compareBy<Local13A2Item> { it.ownerName }.thenBy { it.teamId })
        val cells = buildCells(cellTeamMap, cellDetailMap)

        if (items.isEmpty() && cells.isEmpty()) return null
        return Local13A2Payload(
            marker = marker,
            areaRange = areaRange,
            subjectsCount = subjects?.length() ?: 0,
            teamsCount = items.size,
            cellsCount = cells.size,
            items = items,
            cells = cells,
        )
    }

    private fun buildTeamToCells(cellTeamMap: JSONObject?): Map<Int, List<Int>> {
        if (cellTeamMap == null) return emptyMap()
        val out = linkedMapOf<Int, MutableList<Int>>()
        val keys = cellTeamMap.keys()
        while (keys.hasNext()) {
            val cellId = keys.next().toIntOrNull() ?: continue
            val teamIds = cellTeamMap.optJSONArray(cellId.toString()) ?: continue
            for (idx in 0 until teamIds.length()) {
                val teamId = teamIds.optInt(idx, 0)
                if (teamId > 0) out.getOrPut(teamId) { mutableListOf() }.add(cellId)
            }
        }
        return out
    }

    private fun buildItems(
        teamsRaw: JSONObject?,
        subjects: JSONObject?,
        subjectPowerMap: JSONObject?,
        teamToCells: Map<Int, List<Int>>,
        cellDetailMap: JSONObject?,
    ): List<Local13A2Item> {
        if (teamsRaw == null) return emptyList()
        return buildList {
            val keys = teamsRaw.keys()
            while (keys.hasNext()) {
                val teamKey = keys.next()
                val teamId = teamKey.toIntOrNull() ?: continue
                val arr = teamsRaw.optJSONArray(teamKey) ?: continue
                val subjectId = arr.optInt(1, 0)
                val subject = subjects?.optJSONArray(subjectId.toString())
                val groupInfo = subject?.optJSONArray(12)
                val cells = teamToCells[teamId].orEmpty().distinct().sorted()
                add(
                    Local13A2Item(
                        teamId = teamId,
                        subjectId = subjectId,
                        ownerName = subject?.optString(0, "").orEmpty(),
                        ownerUid = subject?.optInt(1, 0) ?: 0,
                        unionId = subject?.optInt(2, 0) ?: 0,
                        groupId = groupInfo?.optInt(0, 0) ?: 0,
                        groupName = groupInfo?.optString(2, "").orEmpty(),
                        moveType = arr.optInt(0, 0),
                        moveTypeText = moveTypeText(arr.optInt(0, 0)),
                        homeWid = teamId / 10,
                        homeXy = widToXy(teamId / 10),
                        fromWid = arr.optInt(2, 0),
                        fromXy = widToXy(arr.optInt(2, 0)),
                        toWid = arr.optInt(3, 0),
                        toXy = widToXy(arr.optInt(3, 0)),
                        currentWid = arr.optInt(10, 0),
                        currentXy = widToXy(arr.optInt(10, 0)),
                        fortressWid = arr.optInt(11, 0),
                        fortressXy = widToXy(arr.optInt(11, 0)),
                        startTime = arr.optLong(4, 0L),
                        arriveTime = arr.optLong(5, 0L),
                        speed = arr.optInt(28, 0),
                        troopKind = arr.optInt(29, 0),
                        power = subjectPowerMap?.optLong(subjectId.toString(), 0L) ?: 0L,
                        cells = cells,
                        cellCount = cells.size,
                        subjectRawText = if (subjectId > 0) "$subjectId:${subject?.toString().orEmpty()}" else "",
                        cellRawText = cells.mapNotNull { cellId ->
                            cellDetailMap?.optJSONObject(cellId.toString())?.let { "$cellId:$it" }
                        }.joinToString("，"),
                    )
                )
            }
        }
    }

    private fun buildPseudoItemsFromCells(
        cellDetailMap: JSONObject?,
        subjects: JSONObject?,
        subjectPowerMap: JSONObject?,
    ): List<Local13A2Item> {
        if (cellDetailMap == null) return emptyList()
        val out = mutableListOf<Local13A2Item>()
        val keys = cellDetailMap.keys()
        var pseudoTeamId = 1
        while (keys.hasNext()) {
            val cellKey = keys.next()
            val currentWid = cellKey.toIntOrNull() ?: continue
            val detail = cellDetailMap.optJSONObject(cellKey) ?: continue
            val core = detail.optJSONArray("0") ?: continue
            val subjectId = core.optInt(2, 0)
            val subject = subjects?.optJSONArray(subjectId.toString())
            val groupInfo = subject?.optJSONArray(12)
            out += Local13A2Item(
                teamId = pseudoTeamId++,
                subjectId = subjectId,
                ownerName = subject?.optString(0, "").orEmpty(),
                ownerUid = subject?.optInt(1, 0) ?: 0,
                unionId = subject?.optInt(2, 0) ?: 0,
                groupId = groupInfo?.optInt(0, 0) ?: 0,
                groupName = groupInfo?.optString(2, "").orEmpty(),
                moveType = core.optInt(0, 0),
                moveTypeText = moveTypeText(core.optInt(0, 0)),
                homeWid = 0,
                homeXy = "",
                fromWid = core.optInt(7, 0),
                fromXy = widToXy(core.optInt(7, 0)),
                toWid = currentWid,
                toXy = widToXy(currentWid),
                currentWid = currentWid,
                currentXy = widToXy(currentWid),
                fortressWid = core.optInt(11, 0),
                fortressXy = widToXy(core.optInt(11, 0)),
                startTime = core.optLong(4, 0L),
                arriveTime = core.optLong(10, 0L),
                speed = core.optInt(19, 0),
                troopKind = 0,
                power = subjectPowerMap?.optLong(subjectId.toString(), 0L) ?: 0L,
                cells = listOf(currentWid),
                cellCount = 1,
                subjectRawText = if (subjectId > 0) "$subjectId:${subject?.toString().orEmpty()}" else "",
                cellRawText = "$currentWid:$detail",
            )
        }
        return out
    }

    private fun buildCells(cellTeamMap: JSONObject?, cellDetailMap: JSONObject?): List<Local13A2Cell> {
        val out = linkedMapOf<Int, Local13A2Cell>()
        cellTeamMap?.let {
            val keys = it.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val cellId = key.toIntOrNull() ?: continue
                val teamIds = it.optJSONArray(key)?.toIntList().orEmpty().filter { id -> id > 0 }
                out[cellId] = Local13A2Cell(cellId, widToXy(cellId), teamIds, teamIds.size)
            }
        }
        cellDetailMap?.let {
            val keys = it.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val cellId = key.toIntOrNull() ?: continue
                out.putIfAbsent(cellId, Local13A2Cell(cellId, widToXy(cellId), emptyList(), 1))
            }
        }
        return out.values.sortedBy { it.cellId }
    }

    private fun isSubjectPowerMap(obj: JSONObject): Boolean {
        if (obj.length() == 0) return false
        return obj.firstValues().all { it !is JSONArray && it !is JSONObject }
    }

    private fun isSubjectDict(obj: JSONObject): Boolean {
        if (obj.length() == 0) return false
        return obj.firstValues().all { it is JSONArray && it.length() >= 2 && it.opt(0) is String }
    }

    private fun isTeamDict(obj: JSONObject): Boolean {
        if (obj.length() == 0) return false
        return obj.firstValues().all { it is JSONArray && it.length() >= 6 && it.optInt(1, 0) > 0 }
    }

    private fun isCellTeamMapDict(obj: JSONObject): Boolean {
        if (obj.length() == 0) return false
        return obj.firstValues().all { value ->
            value is JSONArray && (0 until value.length()).all { idx -> value.optInt(idx, 0) > 0 }
        }
    }

    private fun isCellDetailDict(obj: JSONObject): Boolean {
        if (obj.length() == 0) return false
        return obj.firstValues().all { it is JSONObject && it.optJSONArray("0") != null }
    }

    private fun JSONObject.firstValues(limit: Int = 3): List<Any> {
        val values = mutableListOf<Any>()
        val keys = keys()
        while (keys.hasNext() && values.size < limit) {
            opt(keys.next())?.let { values += it }
        }
        return values
    }

    private fun JSONArray.toIntList(): List<Int> {
        return buildList {
            for (idx in 0 until length()) add(optInt(idx, 0))
        }
    }

    private fun jsonArray(text: String): JSONArray? {
        val trimmed = text.trim().trimEnd('\u0000').trim()
        return runCatching { JSONArray(trimmed) }.getOrNull()
    }

    private fun normalize(text: String): String {
        return text.trim().trimEnd('\u0000').trim()
            .replace(Regex("(?<=[{,])\\s*(\\d+)\\s*(?=:)"), "\"$1\"")
    }

    private fun moveTypeText(type: Int): String {
        return when (type) {
            1 -> "驻守"
            2 -> "回撤"
            4 -> "调动"
            5 -> "行军"
            6 -> "停留"
            19 -> "城池/要塞"
            else -> if (type > 0) "类型$type" else "-"
        }
    }

    private fun widToXy(wid: Int): String {
        if (wid <= 0) return ""
        return "${wid / 10000},${wid % 10000}"
    }
}

data class Local13A2Payload(
    val marker: Int,
    val areaRange: List<Int>,
    val subjectsCount: Int,
    val teamsCount: Int,
    val cellsCount: Int,
    val items: List<Local13A2Item>,
    val cells: List<Local13A2Cell>,
)

data class Local13A2Item(
    val teamId: Int,
    val subjectId: Int,
    val ownerName: String,
    val ownerUid: Int,
    val unionId: Int,
    val groupId: Int,
    val groupName: String,
    val moveType: Int,
    val moveTypeText: String,
    val homeWid: Int,
    val homeXy: String,
    val fromWid: Int,
    val fromXy: String,
    val toWid: Int,
    val toXy: String,
    val currentWid: Int,
    val currentXy: String,
    val fortressWid: Int,
    val fortressXy: String,
    val startTime: Long,
    val arriveTime: Long,
    val speed: Int,
    val troopKind: Int,
    val power: Long,
    val cells: List<Int>,
    val cellCount: Int,
    val subjectRawText: String,
    val cellRawText: String,
)

data class Local13A2Cell(
    val cellId: Int,
    val cellXy: String,
    val teamIds: List<Int>,
    val teamCount: Int,
)

data class Local13A2TeamInsight(
    val stats: Local13A2TeamStats,
    val lineup: Local13A2Lineup,
    val recentBattles: List<Local13A2RecentBattle>,
    val favored: List<Local13A2Matchup>,
    val countered: List<Local13A2Matchup>,
) {
    companion object {
        fun empty(): Local13A2TeamInsight {
            return Local13A2TeamInsight(
                stats = Local13A2TeamStats(0, 0, 0, 0, 0.0),
                lineup = Local13A2Lineup(0, "", "", emptyList()),
                recentBattles = emptyList(),
                favored = emptyList(),
                countered = emptyList(),
            )
        }
    }
}

data class Local13A2TeamStats(
    val battles: Int,
    val wins: Int,
    val draws: Int,
    val loses: Int,
    val winRate: Double,
)

data class Local13A2Lineup(
    val battleId: Int,
    val side: String,
    val timeStr: String,
    val heroes: List<Local13A2HeroLineup>,
)

data class Local13A2HeroLineup(
    val pos: Int,
    val heroId: Long,
    val heroName: String,
    val level: Int,
    val star: Int,
    val skills: List<Local13A2SkillLineup>,
)

data class Local13A2SkillLineup(
    val skillId: Long,
    val skillName: String,
    val level: Int,
)

data class Local13A2RecentBattle(
    val battleId: Int,
    val time: Long,
    val timeStr: String,
    val resultText: String,
    val opponentName: String,
    val opponentHeroNames: List<String>,
)

data class Local13A2Matchup(
    val opponentHeroNames: List<String>,
    val wins: Int,
    val draws: Int,
    val loses: Int,
    val total: Int,
    val winRate: Double,
)
