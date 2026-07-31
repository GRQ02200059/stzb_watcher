package com.local.stzb.data.battlefield

import com.example.myapplication.LocalBattleField
import com.example.myapplication.LocalFullBattle
import com.example.myapplication.LocalTeamMove
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import com.local.stzb.domain.battlefield.EventTarget

object BattlefieldEventMapper {
    fun fromMove(move: LocalTeamMove): BattlefieldEvent = BattlefieldEvent(
        id = "march:${move.teamId}:${move.arriveTime}",
        occurredAt = maxOf(move.startTime, move.arriveTime),
        category = EventCategory.MARCH,
        priority = EventPriority.NORMAL,
        title = listOf(move.ownerName.ifBlank { "未知玩家" }, move.ownerUnion)
            .filter(String::isNotBlank)
            .joinToString(" · "),
        summary = "${location(move.fromXy, move.fromWid)} → ${location(move.toXy, move.toWid)}",
        target = EventTarget.Team(move.teamId),
    )

    fun fromBattle(battle: LocalFullBattle): BattlefieldEvent = BattlefieldEvent(
        id = "battle:${battle.battleId}",
        occurredAt = battle.time,
        category = EventCategory.BATTLE,
        priority = if (battle.garrison > 0 || battle.cityType > 0) {
            EventPriority.IMPORTANT
        } else {
            EventPriority.NORMAL
        },
        title = "${battle.attackerName.ifBlank { "未知攻方" }} vs ${battle.defenderName.ifBlank { "未知守方" }}",
        summary = listOf(
            battle.widName.ifBlank { widToXy(battle.wid) },
            battleTypeText(battle.fightType),
            battleResultText(battle.result),
        ).filter(String::isNotBlank).joinToString(" · "),
        target = EventTarget.Battle(battle.battleId),
    )

    fun fromSiege(event: LocalBattleField): BattlefieldEvent = BattlefieldEvent(
        id = "siege:${event.wid}:${event.sourceMsgId}",
        occurredAt = 0L,
        category = EventCategory.SIEGE,
        priority = if (event.nearbyCount > 0) EventPriority.IMPORTANT else EventPriority.NORMAL,
        title = "攻城目标 ${widToXy(event.wid).ifBlank { "未知坐标" }}",
        summary = "附近 ${event.nearbyCount} 人",
        target = EventTarget.Cell(event.wid),
    )

    private fun location(xy: String, wid: Int): String = xy.ifBlank {
        widToXy(wid).ifBlank { "未知坐标" }
    }

    private fun widToXy(wid: Int): String = if (wid > 0) "${wid / 10_000},${wid % 10_000}" else ""

    private fun battleResultText(result: Int): String = when (result) {
        0 -> "失败"
        1 -> "胜利"
        2 -> "平局"
        3 -> "攻占"
        4 -> "撤退"
        else -> "战果未知"
    }

    private fun battleTypeText(type: Int): String = when (type) {
        0 -> "普通"
        1 -> "攻城"
        2 -> "驻守"
        3 -> "扫荡"
        4 -> "练兵"
        else -> "战斗"
    }
}
