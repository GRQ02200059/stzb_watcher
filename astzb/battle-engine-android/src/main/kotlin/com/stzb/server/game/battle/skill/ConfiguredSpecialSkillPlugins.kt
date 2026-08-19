package com.stzb.server.game.battle.skill

import com.stzb.server.game.battle.BattleConfigRepository
import com.stzb.server.game.battle.BattleHero
import com.stzb.server.game.battle.BattleHeroId
import com.stzb.server.game.battle.BattleHeroRef
import com.stzb.server.game.battle.DamageOrigin
import com.stzb.server.game.battle.SkillKind

object ConfiguredSpecialSkillPlugins {
    val ownershipCatalog = SkillExecutionOwnershipCatalog(setOf(200036))

    fun registry(config: BattleConfigRepository): SpecialSkillPluginRegistry =
        SpecialSkillPluginRegistry(listOf(FuWangYiKouPlugin(config)))
}

private class FuWangYiKouPlugin(
    config: BattleConfigRepository,
) : SkillExecutionPlugin {
    override val id: String = "skill.$SKILL_ID"
    override val skillIds: Set<Int> = setOf(SKILL_ID)
    override val replacesConfiguredExecution: Boolean = true

    private val rules = config.skillDetails(SKILL_ID).associateBy { it.detailId }
    private val valueRules = SkillRuleCatalog.build(
        SkillScope(fiveStarInitialSkillIds = setOf(SKILL_ID), learnableSaSkillIds = emptySet()),
        config,
    ).rule(SKILL_ID)!!.details.associateBy { it.detailId }
    private val valueCalculator = DefaultBattleValueCalculator()

    override fun execute(invocation: SpecialSkillInvocation): SkillExecutionResult {
        val changes = when (invocation.phase) {
            SpecialSkillPhase.BATTLE_PREPARE -> initialDamageReductions(invocation)
            SpecialSkillPhase.AFTER_SUCCESSFUL_SKILL -> activeSkillResponse(invocation)
        }
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun initialDamageReductions(
        invocation: SpecialSkillInvocation,
    ): List<BattleStateChange> {
        val view = invocation.context.battleView
        val allies = view.heroes()
            .filter { it.side == invocation.owner.side && it.position in setOf(1, 2) }
            .filter { (view.state(it)?.troops ?: 0) > 0 }
        return allies.map { target ->
            val detailId = if (target.position == 2) FRONT_REDUCTION_DETAIL else MIDDLE_REDUCTION_DETAIL
            val detail = requireNotNull(rules[detailId]) { "Missing $id detail=$detailId" }
            DamageModifierChange(
                source = invocation.owner,
                target = target,
                direction = DamageModifierChange.Direction.TAKEN,
                school = null,
                origin = DamageOrigin.ACTIVE,
                tag = null,
                percent = -configuredReduction(
                    requireNotNull(valueRules[detailId]) { "Missing $id value rule=$detailId" },
                    invocation.context.battleView.state(invocation.owner),
                ),
                durationRounds = BATTLE_DURATION,
                skillId = SKILL_ID,
                effectId = detail.effectId,
                detailId = detailId,
                availableHits = detail.availableHit,
            )
        }
    }

    private fun configuredReduction(
        rule: SkillEffectRule,
        ownerState: SkillBattleHeroState?,
    ): Int {
        val source = requireNotNull(ownerState) { "Missing live state for $id owner" }
        val hero = BattleHero(
            id = BattleHeroId(0),
            position = 0,
            stats = source.stats,
            troops = source.troops,
        )
        return when (val potency = valueCalculator.effectValue(rule, hero)) {
            is TypedBattlePotency.Resolved -> potency.value
            is TypedBattlePotency.Deferred -> error(potency.diagnostic)
        }
    }

    private fun activeSkillResponse(
        invocation: SpecialSkillInvocation,
    ): List<BattleStateChange> {
        if (invocation.successfulSkillKind != SkillKind.ACTIVE) return emptyList()
        val count = invocation.context.runtime.count(
            invocation.actor,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
        )
        if (count !in 1..MAX_STACKS) return emptyList()
        return if (invocation.actor.side == invocation.owner.side) {
            listOf(
                statChange(invocation, ATTACK_DETAIL, BattleStatChange.Kind.ATTACK, 11),
                statChange(invocation, STRATEGY_DETAIL, BattleStatChange.Kind.STRATEGY, 13),
            )
        } else {
            val strategy = invocation.context.battleView.state(invocation.owner)?.stats?.strategy ?: 80
            val reduction = (3 + (strategy - 80).coerceAtLeast(0) / 100).coerceAtLeast(1)
            listOf(
                statChange(invocation, DEFENSE_REDUCTION_DETAIL, BattleStatChange.Kind.DEFENSE, -reduction),
                statChange(invocation, SPEED_REDUCTION_DETAIL, BattleStatChange.Kind.SPEED, -reduction),
            )
        }
    }

    private fun statChange(
        invocation: SpecialSkillInvocation,
        detailId: Int,
        kind: BattleStatChange.Kind,
        value: Int,
    ): BattleStatChange {
        val detail = requireNotNull(rules[detailId]) { "Missing $id detail=$detailId" }
        return BattleStatChange(
            source = invocation.owner,
            target = invocation.actor,
            kind = kind,
            potency = TypedBattlePotency.flat(value),
            durationRounds = detail.availableRounds,
            skillId = SKILL_ID,
            effectId = detail.effectId,
            detailId = detailId,
            maxStacks = MAX_STACKS,
        )
    }

    private companion object {
        const val SKILL_ID = 200036
        const val ATTACK_DETAIL = 20003601
        const val STRATEGY_DETAIL = 20003602
        const val DEFENSE_REDUCTION_DETAIL = 20003613
        const val SPEED_REDUCTION_DETAIL = 20003614
        const val FRONT_REDUCTION_DETAIL = 20003625
        const val MIDDLE_REDUCTION_DETAIL = 20003636
        const val MAX_STACKS = 5
        const val BATTLE_DURATION = 10
    }
}
