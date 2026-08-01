package com.local.stzb.data.battlefield

import com.example.myapplication.LocalBattleField
import com.example.myapplication.LocalFullBattle
import com.example.myapplication.Local13A2TeamInsight
import com.example.myapplication.LocalTeamMove
import com.example.myapplication.HeroNameResolver
import com.local.stzb.domain.battlefield.BattlefieldHero
import com.local.stzb.domain.battlefield.BattlefieldSkill
import com.local.stzb.domain.battlefield.BattlefieldTeamPresentation
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import com.local.stzb.domain.battlefield.EventTarget

object BattlefieldEventMapper {
    fun fromMove(move: LocalTeamMove, insight: Local13A2TeamInsight = Local13A2TeamInsight.empty()): BattlefieldEvent {
        val arriveAt = normalizeEpochSeconds(move.arriveTime)
        val lineup = insight.lineup.heroes.sortedBy { it.pos }
        return BattlefieldEvent(
            id = "march:${move.teamId}:$arriveAt",
            occurredAt = maxOf(normalizeEpochSeconds(move.startTime), arriveAt),
            category = EventCategory.MARCH,
            priority = EventPriority.NORMAL,
            title = listOf(move.ownerName.ifBlank { "未知玩家" }, move.ownerUnion)
                .filter(String::isNotBlank)
                .joinToString(" · "),
            summary = buildList {
                add("地图队伍")
                add("${location(move.fromXy, move.fromWid)} → ${location(move.toXy, move.toWid)}")
                if (move.morale > 0) add("士气 ${move.morale}")
                if (move.armyHeroType.isNotBlank()) add("队伍类型 ${formatArmyHeroType(move.armyHeroType)}")
                if (move.battleShow.isNotBlank()) add(move.battleShow)
            }.joinToString(" · "),
            details = buildList {
                if (lineup.isNotEmpty()) {
                    add("已记录队伍：${lineup.joinToString(" / ") { it.heroName.ifBlank { "武将${it.heroId}" } }}")
                    lineup.forEach { hero ->
                        val skills = hero.skills.joinToString(" / ") {
                            "${it.skillName.ifBlank { "战法${it.skillId}" }}${if (it.level > 0) " Lv.${it.level}" else ""}"
                        }.ifBlank { "战法未记录" }
                        add("${hero.pos}号位 ${hero.heroName.ifBlank { "武将${hero.heroId}" }} Lv.${hero.level} 进阶${hero.star} · $skills")
                    }
                    if (insight.stats.battles > 0) {
                        add("历史战绩：${insight.stats.battles}战 ${insight.stats.wins}胜 ${insight.stats.draws}平 ${insight.stats.loses}负 · 胜率 ${"%.1f".format(insight.stats.winRate)}%")
                    }
                }
                add("行动：${armyStateText(move.moveType)} · 目标：${targetTypeText(move.targetType)}")
                if (move.resideWid > 0) add("驻地：${widToXy(move.resideWid)}${if (move.stayWid > 0) " · 停留：${widToXy(move.stayWid)}" else ""}")
                if (move.buffIdList.isNotBlank()) add("Buff：${move.buffIdList}")
                if (move.battleEffect.isNotBlank()) add("战斗效果：${move.battleEffect}")
                if (lineup.isEmpty()) add("武将与兵力：尚未匹配到已记录战报")
            },
            teamPresentation = lineup.takeIf { it.isNotEmpty() }?.let { heroes ->
                BattlefieldTeamPresentation(
                    teamId = move.teamId,
                    heroes = heroes.mapIndexed { index, hero ->
                        BattlefieldHero(
                            positionLabel = listOf("大营", "中军", "前锋").getOrElse(index) { "${hero.pos}号位" },
                            heroId = hero.heroId,
                            iconId = HeroNameResolver.iconIdOf(hero.heroId),
                            name = hero.heroName.ifBlank { HeroNameResolver.nameOf(hero.heroId) },
                            level = hero.level,
                            advance = hero.star,
                            skills = hero.skills.map { BattlefieldSkill(it.skillName.ifBlank { "战法${it.skillId}" }, it.level) },
                        )
                    },
                    routeText = "${location(move.fromXy, move.fromWid)} → ${location(move.toXy, move.toWid)}",
                    destinationText = location(move.toXy, move.toWid),
                    moraleText = "士气 ${move.morale}",
                    stateText = armyStateText(move.moveType),
                    recordText = if (insight.stats.battles > 0) {
                        "${insight.stats.battles}战 ${insight.stats.wins}胜${insight.stats.draws}平${insight.stats.loses}负 · 胜率 ${"%.1f".format(insight.stats.winRate)}%"
                    } else "暂无历史战绩",
                    arrivalText = "到达 ${formatEventTime(arriveAt)}",
                    arrivalAt = arriveAt,
                    winRate = insight.stats.winRate.takeIf { insight.stats.battles > 0 },
                )
            },
            target = EventTarget.Team(move.teamId),
        )
    }

    fun fromBattle(battle: LocalFullBattle): BattlefieldEvent = BattlefieldEvent(
        id = "battle:${battle.battleId}",
        occurredAt = normalizeEpochSeconds(battle.time),
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
        id = "siege:${event.wid}:${event.attackerUid}",
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

    private fun formatArmyHeroType(value: String): String = value.trim(';').split(';').filter(String::isNotBlank).joinToString(" / ")

    private fun armyStateText(state: Int): String = when (state) {
        0 -> "已移除"
        1 -> "行军"
        2 -> "驻守"
        5 -> "驻扎"
        25 -> "调动"
        else -> "状态 $state"
    }

    private fun targetTypeText(type: Int): String = when (type) {
        0 -> "普通地块"
        1 -> "土地"
        2 -> "城池/建筑"
        else -> "类型 $type"
    }

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

    private fun formatEventTime(epochSeconds: Long): String = java.time.Instant.ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", java.util.Locale.CHINA))
}

internal fun normalizeEpochSeconds(timestamp: Long): Long =
    if (timestamp >= MILLIS_TIMESTAMP_THRESHOLD) timestamp / 1_000L else timestamp

private const val MILLIS_TIMESTAMP_THRESHOLD = 100_000_000_000L
