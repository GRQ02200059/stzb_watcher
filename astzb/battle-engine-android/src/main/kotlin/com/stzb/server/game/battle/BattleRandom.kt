package com.stzb.server.game.battle

import kotlin.random.Random

interface BattleRandom {
    fun nextInt(bound: Int): Int
}

class SeededBattleRandom(seed: Int) : BattleRandom {
    private val random = Random(seed)

    override fun nextInt(bound: Int): Int =
        random.nextInt(bound)
}

class FixedBattleRandom(
    private val value: Int,
) : BattleRandom {
    override fun nextInt(bound: Int): Int =
        value.coerceIn(0, bound - 1)
}
