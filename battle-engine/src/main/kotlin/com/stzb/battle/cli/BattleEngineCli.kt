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
            val replay = BattleReplayContract.from(result)
            firstRun = mapOf(
                "outcome" to result.outcome.name,
                "attackerRemain" to result.attacker.heroes.sumOf { it.troops },
                "defenderRemain" to result.defender.heroes.sumOf { it.troops },
                "roundsPlayed" to roundsPlayed,
                "attackerHeroes" to heroSnapshots(result.attacker.heroes, result.entryAttacker?.heroes),
                "defenderHeroes" to heroSnapshots(result.defender.heroes, result.entryDefender?.heroes),
                "entrySnapshots" to replay.entrySnapshots,
                "roundSnapshots" to replay.roundSnapshots,
                "finalSnapshots" to replay.finalSnapshots,
                "events" to replay.events,
                "replayActions" to replay.replayActions,
                "replayText" to replay.replayText,
                "diagnostics" to replay.diagnostics,
                "textLog" to result.events.take(240).map { it.toString() },
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
