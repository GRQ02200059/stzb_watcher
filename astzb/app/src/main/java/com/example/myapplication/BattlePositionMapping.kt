package com.example.myapplication

/**
 * Maps the protocol's skill position numbers to the local three-hero order.
 *
 * Attack positions are encoded as 1, 2, 3. Defender positions are encoded
 * in reverse battle order as 6, 5, 4.
 */
object BattlePositionMapping {
    fun attackerHeroIndexForSkillPosition(skillPosition: Int): Int? = when (skillPosition) {
        1 -> 0
        2 -> 1
        3 -> 2
        else -> null
    }

    fun defenderHeroIndexForSkillPosition(skillPosition: Int): Int? = when (skillPosition) {
        6 -> 0
        5 -> 1
        4 -> 2
        else -> null
    }
}
