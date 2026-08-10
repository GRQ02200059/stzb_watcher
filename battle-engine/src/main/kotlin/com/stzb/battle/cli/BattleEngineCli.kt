package com.stzb.battle.cli

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

private val mapper = jacksonObjectMapper()

fun runBattleEngineCli(input: String): String {
    val root = mapper.readTree(input)
    val repeat = root.path("repeat").asInt(1).coerceAtLeast(1)
    val result = mapOf(
        "ok" to true,
        "repeat" to repeat,
        "attackerWins" to 0,
        "defenderWins" to 0,
        "draws" to repeat,
        "firstRun" to mapOf(
            "outcome" to "DRAW",
            "attackerRemain" to 0,
            "defenderRemain" to 0,
            "events" to emptyList<String>(),
            "textLog" to listOf("battle-engine skeleton"),
        ),
    )
    return mapper.writeValueAsString(result)
}

fun main() {
    val input = generateSequence(::readLine).joinToString("\n")
    print(runBattleEngineCli(input))
}
