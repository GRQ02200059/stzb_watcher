package com.stzb.battle.core

import com.stzb.battle.core.skill.BattleTrigger
import java.math.BigDecimal
import java.math.RoundingMode

internal object ClientBattlePreparationEventProjector {
    fun appliesTo(event: BattleEvent): Boolean =
        when (event) {
            is BattleEvent.SkillTriggered -> event.round == 0
            is BattleEvent.TriggerPoint -> event.round == 0
            is BattleEvent.SkillPreparationCompleted -> event.round == 0
            is BattleEvent.SkillPreparationCancelled -> event.round == 0
            is BattleEvent.StatusRemoved -> event.round == 0
            is BattleEvent.EffectExpired -> event.round == 0
            is BattleEvent.EffectBlocked -> event.round == 0
            is BattleEvent.RoundStart -> event.round == 0
            is BattleEvent.HeroActionStart -> event.round == 0
            is BattleEvent.NormalAttack -> event.round == 0
            is BattleEvent.SkillDamage -> event.round == 0
            is BattleEvent.SkillPreparationStarted -> event.round == 0
            is BattleEvent.Recovery -> event.round == 0
            is BattleEvent.StatusApplied -> event.round == 0
            is BattleEvent.OngoingDamage -> event.round == 0
            is BattleEvent.Evaded -> event.round == 0
            is BattleEvent.StatChanged -> event.round == 0
            is BattleEvent.ModifierApplied -> event.round == 0
            is BattleEvent.SkillRangeChanged -> event.round == 0
            is BattleEvent.UnsupportedSkillEffect -> event.round == 0
            is BattleEvent.UnsupportedEquipmentEffect -> event.round == 0
            is BattleEvent.HeroActionEnd -> event.round == 0
            is BattleEvent.RoundEnd -> event.round == 0
            BattleEvent.BattleStart,
            is BattleEvent.BattleEnd,
            -> false
        }

    fun project(
        event: BattleEvent,
        diagnostic: (String) -> Unit,
    ): List<ClientReportAction> =
        when (event) {
            is BattleEvent.SkillTriggered -> skillTriggered(event, diagnostic)
            is BattleEvent.StatChanged ->
                if (event.skillId > 0) listOf(statChanged(event)) else emptyList()
            is BattleEvent.ModifierApplied -> modifierApplied(event, diagnostic)
            is BattleEvent.SkillRangeChanged -> listOf(
                ClientReportAction(
                    ClientBattleTextReplayProtocol.SKILL_RANGE_CHANGED,
                    listOf(
                        ClientBattleTextReplayProtocol.position(event.source),
                        event.skillId,
                        ClientBattleTextReplayProtocol.position(event.target),
                        event.delta,
                        event.displayRangeAfter,
                    ),
                ),
            )
            is BattleEvent.StatusApplied -> statusApplied(event)
            is BattleEvent.Recovery,
            is BattleEvent.OngoingDamage,
            is BattleEvent.NormalAttack,
            -> unsupportedRoundZero(event, diagnostic)
            is BattleEvent.SkillDamage -> unsupported(
                "Unsupported round-zero SkillDamage: " +
                    "skill=${event.skillId} effect=${event.effectId} " +
                    "source=${ClientBattleTextReplayProtocol.position(event.source)} " +
                    "target=${ClientBattleTextReplayProtocol.position(event.target)}",
                diagnostic,
            )
            is BattleEvent.UnsupportedSkillEffect -> unsupported(
                "Unsupported round-zero skill effect projection: " +
                    "skill=${event.skillId} effect=${event.effectId}",
                diagnostic,
            )
            is BattleEvent.UnsupportedEquipmentEffect -> unsupported(
                "Unsupported round-zero equipment effect projection: " +
                    "equipment=${event.equipmentId}",
                diagnostic,
            )
            is BattleEvent.EffectBlocked -> unsupported(
                "Unsupported round-zero EffectBlocked projection: " +
                    "skill=${event.skillId} effect=${event.effectId} " +
                    "blocker=${event.blockingEffectId}",
                diagnostic,
            )
            is BattleEvent.TriggerPoint,
            is BattleEvent.SkillPreparationCompleted,
            is BattleEvent.SkillPreparationCancelled,
            is BattleEvent.StatusRemoved,
            is BattleEvent.EffectExpired,
            is BattleEvent.RoundStart,
            is BattleEvent.HeroActionStart,
            is BattleEvent.SkillPreparationStarted,
            is BattleEvent.Evaded,
            is BattleEvent.HeroActionEnd,
            is BattleEvent.RoundEnd,
            BattleEvent.BattleStart,
            is BattleEvent.BattleEnd,
            -> emptyList()
        }

    private fun skillTriggered(
        event: BattleEvent.SkillTriggered,
        diagnostic: (String) -> Unit,
    ): List<ClientReportAction> {
        if (
            event.skillId != event.rootSkillId &&
            !ClientBattleTextReplayProtocol.supportsDerivedPreparationSkill(event.skillId)
        ) {
            return unsupported(
                "Unsupported derived preparation skill projection: " +
                    "root=${event.rootSkillId} derived skill=${event.skillId}",
                diagnostic,
            )
        }
        val actionId =
            if (event.skillId != event.rootSkillId) {
                ClientBattleTextReplayProtocol.DERIVED_SKILL_TRIGGERED
            } else {
                when (event.trigger) {
                    BattleTrigger.BATTLE_PASSIVE ->
                        ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PASSIVE
                    BattleTrigger.BATTLE_COMMAND ->
                        ClientBattleTextReplayProtocol.SKILL_TRIGGERED_COMMAND
                    BattleTrigger.ACTIVE_SKILL_ATTEMPT ->
                        ClientBattleTextReplayProtocol.SKILL_TRIGGERED_ACTIVE
                    BattleTrigger.PURSUIT_ATTEMPT ->
                        ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PURSUIT
                    else -> throw UnsupportedBattleReportProjectionException(
                        "Round-zero SkillTriggered cannot use trigger=${event.trigger}",
                    )
                }
            }
        return listOf(
            ClientReportAction(
                actionId,
                listOf(ClientBattleTextReplayProtocol.position(event.source), event.skillId),
            ),
        )
    }

    private fun statChanged(event: BattleEvent.StatChanged): ClientReportAction =
        if (event.unit == BattleEffectValueUnit.FLAT) {
            ClientReportAction(
                ClientBattleTextReplayProtocol.flatAttributeAction(event.stat),
                listOf(
                    ClientBattleTextReplayProtocol.position(event.source),
                    event.skillId,
                    ClientBattleTextReplayProtocol.position(event.target),
                    reportNumber(kotlin.math.abs(event.deltaExact)),
                    reportNumber(
                        event.valueAfterExact
                            ?: event.valueAfter?.toDouble()
                            ?: event.deltaExact,
                    ),
                ),
            )
        } else {
            ClientReportAction(
                ClientBattleTextReplayProtocol.attributeChangeAction(event.stat, event.delta),
                listOf(
                    ClientBattleTextReplayProtocol.position(event.source),
                    event.skillId,
                    ClientBattleTextReplayProtocol.position(event.target),
                    event.strength,
                    reportNumber(kotlin.math.abs(event.deltaExact)),
                    reportNumber(
                        event.valueAfterExact
                            ?: event.valueAfter?.toDouble()
                            ?: event.deltaExact,
                    ),
                ),
            )
        }

    private fun modifierApplied(
        event: BattleEvent.ModifierApplied,
        diagnostic: (String) -> Unit,
    ): List<ClientReportAction> {
        if (event.skillId == 200_220 && event.effectId == 332) {
            return listOf(
                ClientReportAction(
                    ClientBattleTextReplayProtocol.ACTIVE_SKILL_DAMAGE_REDUCTION,
                    listOf(
                        ClientBattleTextReplayProtocol.position(event.source),
                        event.skillId,
                        ClientBattleTextReplayProtocol.position(event.target),
                        1001,
                    ),
                ),
            )
        }
        if (!ClientBattleTextReplayProtocol.supportsPreparationModifier(event.effectId)) {
            return unsupported(
                "Unsupported preparation modifier projection: " +
                    "skill=${event.skillId} effect=${event.effectId}",
                diagnostic,
            )
        }
        return listOf(
            ClientReportAction(
                ClientBattleTextReplayProtocol.MODIFIER_APPLIED,
                listOf(
                    ClientBattleTextReplayProtocol.position(event.source),
                    event.skillId,
                    ClientBattleTextReplayProtocol.position(event.target),
                    event.effectId,
                    kotlin.math.abs(event.amount),
                ),
            ),
        )
    }

    private fun statusApplied(event: BattleEvent.StatusApplied): List<ClientReportAction> {
        if (event.skillId <= 0 || event.status.isStatChangeStatus()) return emptyList()
        return listOf(
            ClientReportAction(
                ClientBattleTextReplayProtocol.PREPARATION_STATUS_APPLIED,
                listOf(
                    ClientBattleTextReplayProtocol.position(event.target),
                    event.effectId ?: ClientBattleTextReplayProtocol.effectId(event.status),
                ),
            ),
        )
    }

    private fun unsupportedRoundZero(
        event: BattleEvent,
        diagnostic: (String) -> Unit,
    ): List<ClientReportAction> =
        unsupported(
            "Unsupported round-zero ${event::class.simpleName} projection",
            diagnostic,
        )

    private fun unsupported(
        message: String,
        diagnostic: (String) -> Unit,
    ): List<ClientReportAction> {
        diagnostic(message)
        return emptyList()
    }

    private fun BattleStatus.isStatChangeStatus(): Boolean =
        this in setOf(
            BattleStatus.ATTACK_BUFF,
            BattleStatus.DEFENSE_BUFF,
            BattleStatus.STRATEGY_BUFF,
            BattleStatus.SPEED_BUFF,
            BattleStatus.ATTACK_DEBUFF,
            BattleStatus.DEFENSE_DEBUFF,
            BattleStatus.STRATEGY_DEBUFF,
            BattleStatus.SPEED_DEBUFF,
        )

    private fun reportNumber(value: Double): Any =
        BigDecimal.valueOf(value)
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()
            .let { rounded ->
                if (rounded % 1.0 == 0.0) rounded.toInt() else rounded
            }
}
