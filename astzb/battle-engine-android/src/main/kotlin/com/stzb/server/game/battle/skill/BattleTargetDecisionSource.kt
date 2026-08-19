package com.stzb.server.game.battle.skill

import com.stzb.server.game.battle.BattleHeroRef

data class BattleTargetDecisionRequest(
    val rule: SkillEffectRule,
    val context: SkillBattleContext,
    val candidates: List<BattleHeroRef>,
    val limit: Int,
)

fun interface BattleTargetDecisionSource {
    /** Returns null when this selection should use the battle's normal random source. */
    fun select(request: BattleTargetDecisionRequest): List<BattleHeroRef>?

    companion object {
        val NONE = BattleTargetDecisionSource { null }
    }
}

fun interface BattleForcedTargetSource {
    /**
     * Returns a target override before normal skill-range filtering.
     * Implementations must return null when no forced target is consumed.
     */
    fun select(request: BattleTargetDecisionRequest): List<BattleHeroRef>?

    companion object {
        val NONE = BattleForcedTargetSource { null }
    }
}
