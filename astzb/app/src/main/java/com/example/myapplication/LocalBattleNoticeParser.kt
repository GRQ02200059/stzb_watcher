package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject

object LocalBattleNoticeParser {

    fun tryParse(packet: LocalStzbPacket) {
        if (packet.msgId != "2100" && packet.msgId != "2200") return
        val data = runCatching { JSONArray(packet.decodedText.trim()) }.getOrNull() ?: run {
            PacketLogStore.add("${packet.msgId} 本机通知解析失败：payload 不是 JSON 数组")
            return
        }

        when (data.optInt(1, -1)) {
            9 -> parseChat(data, packet)?.also {
                LocalStzbRepository.saveChat(it)
                PacketLogStore.add("${packet.msgId}聊天：${it.sender}(${it.unionName}) ${it.text.take(40)}")
            }

            0, 1 -> parseBattleNotice(data, packet)?.also {
                LocalStzbRepository.saveBattleNotice(it)
                PacketLogStore.add(
                    "${packet.msgId}战报通知：#${it.battleId} ${it.attackerName.ifBlank { "-" }} " +
                        "${localResultText(it.result)} ${localFightTypeText(it.fightType)} wid=${it.wid}"
                )
            }
        }
    }

    private fun parseChat(data: JSONArray, packet: LocalStzbPacket): LocalChatMessage? {
        val senderRaw = data.optString(45, "")
        val senderParts = senderRaw.split("#", limit = 2)
        val sender = senderParts.getOrNull(0).orEmpty()
        val uid = senderParts.getOrNull(1).orEmpty()
        return LocalChatMessage(
            id = data.optLong(0, 0L),
            sender = sender,
            uid = uid,
            unionName = data.optString(8, ""),
            text = data.optString(5, ""),
            time = data.optLong(6, 0L),
            sourceMsgId = packet.msgId,
        )
    }

    private fun parseBattleNotice(data: JSONArray, packet: LocalStzbPacket): LocalBattleNotice? {
        val battleId = data.optInt(0, 0)
        if (battleId <= 0) return null

        val fullName = data.optString(45, data.optString(4, ""))
        val nameParts = fullName.split("#", limit = 2)
        val attackerName = nameParts.getOrNull(0).orEmpty().ifBlank { data.optString(4, "") }
        val attackerUid = nameParts.getOrNull(1).orEmpty()

        return LocalBattleNotice(
            battleId = battleId,
            time = data.optLong(6, 0L),
            result = data.optInt(7, 0),
            fightType = data.optInt(2, 0),
            wid = data.optInt(3, 0),
            widCode = data.optString(37, ""),
            attackerName = attackerName,
            attackerUid = attackerUid,
            attackerGongxun = 0,
            attackerPower = 0,
            defenderName = "",
            defenderUnion = data.optString(8, ""),
            defenderLevel = data.optInt(9, 0),
            defenderGongxun = 0,
            heroesJson = parseHeroes(data.optJSONArray(10)).toString(),
            sourceMsgId = packet.msgId,
        )
    }

    private fun parseHeroes(raw: JSONArray?): JSONArray {
        val out = JSONArray()
        if (raw == null) return out
        for (i in 0 until raw.length()) {
            val h = raw.optJSONArray(i) ?: continue
            val heroId = h.optLong(0, 0L)
            if (heroId <= 0L) continue
            out.put(
                JSONObject().apply {
                    put("hero_id", heroId)
                    put("hero_name", HeroNameResolver.nameOf(heroId))
                    put("level", h.optInt(1, 0))
                    put("max_hp", h.optInt(2, 0))
                    put("remain_hp", h.optInt(3, 0))
                    put("damage_taken", h.optInt(4, 0))
                }
            )
        }
        return out
    }
}
