package com.stzb.battle.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stzb.battle.core.BattleConfigRepository
import com.stzb.battle.core.BattleEngine
import com.stzb.battle.core.BattleHeroSpec
import com.stzb.battle.core.BattleOutcome
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleTeamBuilder
import com.stzb.battle.core.SeededBattleRandom

private val mapper = jacksonObjectMapper()

fun runBattleEngineCli(input: String): String {
    val root = mapper.readTree(input)
    val repeat = root.path("repeat").asInt(1).coerceIn(1, 1000)
    val seed = root.path("seed").asInt(1)
    val config = BattleConfigRepository.loadDefault()
    val builder = BattleTeamBuilder(config)
    var attackerWins = 0
    var defenderWins = 0
    var draws = 0
    var firstRun: Map<String, Any?>? = null

    repeat(repeat) { idx ->
        val request = BattleRequest(
            attacker = builder.build(team(root.path("attacker"))),
            defender = builder.build(team(root.path("defender"))),
            maxRounds = 8,
        )
        val result = BattleEngine.resolve(request, config, SeededBattleRandom(seed + idx))
        when (result.outcome) {
            BattleOutcome.ATTACKER_WIN -> attackerWins += 1
            BattleOutcome.DEFENDER_WIN -> defenderWins += 1
            BattleOutcome.DRAW -> draws += 1
        }
        if (idx == 0) {
            val roundsPlayed = result.events.count { it is com.stzb.battle.core.BattleEvent.RoundStart }
            firstRun = mapOf(
                "outcome" to result.outcome.name,
                "attackerRemain" to result.attacker.heroes.sumOf { it.troops },
                "defenderRemain" to result.defender.heroes.sumOf { it.troops },
                "roundsPlayed" to roundsPlayed,
                "attackerHeroes" to heroSnapshots(result.attacker.heroes, result.entryAttacker?.heroes),
                "defenderHeroes" to heroSnapshots(result.defender.heroes, result.entryDefender?.heroes),
                "events" to result.events.map { it.toString() },
                "textLog" to result.events.take(240).map { it.toString() },
                "structuredLog" to structuredEvents(result.events),
            )
        }
    }

    return mapper.writeValueAsString(
        mapOf(
        "ok" to true,
        "repeat" to repeat,
            "attackerWins" to attackerWins,
            "defenderWins" to defenderWins,
            "draws" to draws,
            "firstRun" to firstRun,
        )
    )
}

private fun heroSnapshots(
    finalHeroes: List<com.stzb.battle.core.BattleHero>,
    entryHeroes: List<com.stzb.battle.core.BattleHero>?,
): List<Map<String, Any?>> {
    val initialByPosition = (entryHeroes ?: finalHeroes).associate { it.position to it.troops }
    return finalHeroes
        .sortedBy { it.position }
        .map { hero ->
            val initial = initialByPosition[hero.position] ?: hero.maxTroops
            mapOf(
                "heroId" to hero.id.value,
                "position" to hero.position,
                "troops" to hero.troops,
                "initialTroops" to initial,
                "hurt" to (initial - hero.troops).coerceAtLeast(0),
                "alive" to (hero.troops > 0),
            )
        }
}

/**
 * 把强类型 BattleEvent 抽成结构化字典，供 Python 适配层结合武将/战法名称
 * 渲染成率土风格的中文战报（Kotlin 侧不持有名称表，故只输出 id + 关键数值）。
 */
private fun structuredEvents(events: List<com.stzb.battle.core.BattleEvent>): List<Map<String, Any?>> {
    fun ref(r: com.stzb.battle.core.BattleHeroRef) = mapOf(
        "side" to r.side.name,
        "position" to r.position,
        "heroId" to r.heroId.value,
    )
    val out = ArrayList<Map<String, Any?>>()
    for (event in events) {
        val record: Map<String, Any?>? = when (event) {
            is com.stzb.battle.core.BattleEvent.RoundStart ->
                mapOf("type" to "roundStart", "round" to event.round)
            is com.stzb.battle.core.BattleEvent.SkillTriggered ->
                mapOf("type" to "skill", "round" to event.round,
                    "source" to ref(event.source), "skillId" to event.skillId,
                    "trigger" to event.trigger.name)
            is com.stzb.battle.core.BattleEvent.NormalAttack ->
                mapOf("type" to "normalAttack", "round" to event.round,
                    "source" to ref(event.source), "target" to ref(event.target),
                    "damage" to event.damage, "targetTroopsAfter" to event.targetTroopsAfter)
            is com.stzb.battle.core.BattleEvent.SkillDamage ->
                mapOf("type" to "skillDamage", "round" to event.round,
                    "source" to ref(event.source), "target" to ref(event.target),
                    "skillId" to event.skillId, "damage" to event.damage,
                    "targetTroopsAfter" to event.targetTroopsAfter)
            is com.stzb.battle.core.BattleEvent.OngoingDamage ->
                mapOf("type" to "ongoingDamage", "round" to event.round,
                    "source" to ref(event.source), "target" to ref(event.target),
                    "status" to event.status.name, "damage" to event.damage,
                    "targetTroopsAfter" to event.targetTroopsAfter)
            is com.stzb.battle.core.BattleEvent.Recovery ->
                mapOf("type" to "recovery", "round" to event.round,
                    "source" to ref(event.source), "target" to ref(event.target),
                    "amount" to event.amount, "targetTroopsAfter" to event.targetTroopsAfter)
            is com.stzb.battle.core.BattleEvent.StatusApplied ->
                mapOf("type" to "status", "round" to event.round,
                    "source" to ref(event.source), "target" to ref(event.target),
                    "status" to event.status.name, "durationRounds" to event.durationRounds)
            is com.stzb.battle.core.BattleEvent.Evaded ->
                mapOf("type" to "evaded", "round" to event.round,
                    "source" to ref(event.source), "target" to ref(event.target))
            is com.stzb.battle.core.BattleEvent.BattleEnd ->
                mapOf("type" to "battleEnd", "outcome" to event.outcome.name)
            else -> null
        }
        if (record != null) out.add(record)
    }
    return out
}

private fun team(node: JsonNode): List<BattleHeroSpec> =
    node.path("heroes").mapIndexed { index, hero ->
        BattleHeroSpec(
            heroId = hero.path("heroId").asInt(),
            position = hero.path("position").asInt(index),
            troops = hero.path("troops").asInt(9000),
            level = hero.path("level").asInt(40),
            advanceLevel = hero.path("advanceLevel").asInt(0),
            morale = node.path("morale").asInt(100),
            extraSkillIds = hero.path("extraSkillIds").map { it.asInt() },
        )
    }

fun main() {
    val input = generateSequence(::readLine).joinToString("\n")
    print(runBattleEngineCli(input))
}
