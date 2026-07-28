package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject

object LocalFullBattleParser {

    fun tryParse(packet: LocalStzbPacket) {
        if (packet.msgId != "10" && packet.msgId != "92") return
        val battles = when (packet.msgId) {
            "10" -> parseBattle10(packet)
            "92" -> parseBattle92(packet)
            else -> emptyList()
        }
        val playerBattles = battles.filter { it.isPlayerBattle() }
        val filtered = battles.size - playerBattles.size
        if (playerBattles.isNotEmpty()) {
            LocalStzbRepository.saveFullBattles(playerBattles)
            PacketLogStore.add("${packet.msgId} 玩家战报入库：${playerBattles.size} 条${if (filtered > 0) "，过滤NPC=$filtered" else ""}")
        } else if (filtered > 0) {
            PacketLogStore.add("${packet.msgId} 完整战报全部为NPC，已过滤：$filtered 条")
        }
    }

    private fun LocalFullBattle.isPlayerBattle(): Boolean {
        return isNpc == 0 && result != 6 && !localResultText(result).contains("NPC", ignoreCase = true)
    }

    private fun parseBattle10(packet: LocalStzbPacket): List<LocalFullBattle> {
        val root = jsonArray(packet.decodedText) ?: return emptyList()
        val rows = root.optJSONArray(1) ?: return emptyList()
        return (0 until rows.length()).mapNotNull { idx ->
            rows.optJSONObject(idx)?.toFullBattle(packet.msgId)
        }
    }

    private fun parseBattle92(packet: LocalStzbPacket): List<LocalFullBattle> {
        val root = jsonArray(packet.decodedText) ?: return emptyList()
        return (0 until root.length()).mapNotNull { idx ->
            val row = root.optJSONArray(idx) ?: return@mapNotNull null
            row.optJSONObject(0)?.toFullBattle(packet.msgId)
        }
    }

    private fun JSONObject.toFullBattle(sourceMsgId: String): LocalFullBattle? {
        val battleId = optInt("battle_id", 0)
        if (battleId <= 0) return null

        val attackerHeroes = parseHeroInfo(
            battleId = battleId,
            side = "atk",
            allHeroInfo = optString("attack_all_hero_info", ""),
            advance = optString("attack_advance", ""),
        )
        val defenderHeroes = parseHeroInfo(
            battleId = battleId,
            side = "def",
            allHeroInfo = optString("defend_all_hero_info", ""),
            advance = optString("defend_advance", ""),
        )

        return LocalFullBattle(
            battleId = battleId,
            time = optLong("time", 0L),
            result = optInt("result", 0),
            fightType = optInt("fight_type", 0),
            wid = optInt("wid", 0),
            widName = optString("wid_name", ""),
            widCode = optString("wid_code", ""),
            attackerName = optString("attack_name", ""),
            attackerUid = optString("attack_role_id", ""),
            attackerUnion = optString("attack_union_name", ""),
            attackerUnionId = optInt("attack_unionid", 0),
            attackerPower = optInt("attacker_force", optInt("atk_power", 0)),
            attackerGongxun = optInt("attacker_gongxun", optInt("atk_gongxun", 0)),
            attackerHp = optInt("attack_hp", 0),
            defenderName = optString("defend_name", ""),
            defenderUid = optString("defend_role_id", ""),
            defenderUnion = optString("defend_union_name", ""),
            defenderUnionId = optInt("defend_unionid", 0),
            defenderLevel = optInt("defend_base_level", 0),
            defenderPower = optInt("defender_force", optInt("def_power", 0)),
            defenderGongxun = optInt("defender_gongxun", optInt("def_gongxun", 0)),
            defenderHp = optInt("defend_hp", 0),
            weather = optInt("weather", 0),
            inNight = optInt("in_night_mode", optInt("in_night", 0)),
            isNpc = optInt("npc", optInt("is_npc", 0)),
            isAi = optInt("is_ai", 0),
            blockId = optInt("block_id", 0),
            cityType = optInt("city_type", 0),
            borrowLand = optInt("borrow_land", 0),
            garrison = optInt("garrison", 0),
            firstOccupyLvnLand = optInt("first_occupy_lvn_land", 0),
            attackerTeamId = extractTeamId(optString("attack_idu", "")),
            defenderTeamId = extractTeamId(optString("defend_idu", "")),
            attackerAdvance = optString("attack_advance", ""),
            defenderAdvance = optString("defend_advance", ""),
            attackerHeroType = optString("attack_hero_type", ""),
            defenderHeroType = optString("defend_hero_type", ""),
            attackerGearInfo = optString("attacker_gear_info", ""),
            defenderGearInfo = optString("defender_gear_info", ""),
            allSkillInfo = optString("all_skill_info", ""),
            attackAllHeroInfo = optString("attack_all_hero_info", ""),
            defendAllHeroInfo = optString("defend_all_hero_info", ""),
            attackAllSubHeroInfo = optString("attack_all_sub_hero_info", ""),
            defendAllSubHeroInfo = optString("defend_all_sub_hero_info", ""),
            attackSupportUserInfo = optString("attack_support_user_info", ""),
            defendSupportUserInfo = optString("defend_support_user_info", ""),
            sourceMsgId = sourceMsgId,
            rawJson = toString(),
            attackerHeroes = attackerHeroes,
            defenderHeroes = defenderHeroes,
        )
    }

    private fun parseHeroInfo(
        battleId: Int,
        side: String,
        allHeroInfo: String,
        advance: String,
    ): List<LocalBattleHero> {
        val starByPos = parseStars(advance)
        return allHeroInfo.split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .mapIndexedNotNull { index, seg ->
                val parts = seg.split(',')
                val heroId = parts.getOrNull(0)?.toLongOrNull() ?: return@mapIndexedNotNull null
                if (heroId <= 0L) return@mapIndexedNotNull null
                LocalBattleHero(
                    battleId = battleId,
                    side = side,
                    pos = index,
                    heroId = heroId,
                    heroName = HeroNameResolver.nameOf(heroId),
                    level = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    maxHp = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                    remainHp = parts.getOrNull(3)?.toIntOrNull() ?: 0,
                    damageTaken = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                    star = starByPos[index] ?: 0,
                )
            }
    }

    private fun parseStars(advance: String): Map<Int, Int> {
        val segs = advance.split(';').map { it.trim() }.filter { it.isNotBlank() }
        if (segs.isEmpty()) return emptyMap()
        return buildMap {
            for (pos in 0 until 3) {
                val seg = segs.getOrNull(pos + 1) ?: continue
                val star = seg.split(',').firstOrNull()?.toIntOrNull() ?: 0
                put(pos, star)
            }
        }
    }

    private fun jsonArray(text: String): JSONArray? {
        val trimmed = text.trim().trimEnd('\u0000').trim()
        return runCatching { JSONArray(trimmed) }.getOrNull()
    }

    private fun extractTeamId(idu: String): Int {
        if (idu.isBlank()) return 0
        return Regex("""\d+""").findAll(idu).lastOrNull()?.value?.toIntOrNull() ?: 0
    }
}
