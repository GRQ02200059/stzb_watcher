package com.stzb.battle.core.skill

import com.stzb.battle.core.ActionPermission
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.EffectCategory
import com.stzb.battle.core.SkillKind
import com.stzb.battle.core.opposite

enum class ActionEffectKind {
    GUARD,
    IGNORE_EVADE,
    STRATEGY_LIFE_STEAL,
    DOUBLE_ATTACK,
    SECONDARY_ATTACK,
    EXECUTION_ATTACK,
    COUNTERATTACK,
    IGNORE_TROOP_COUNTER,
    REDUCE_INHERENT_PREPARATION,
    FIRST_ACTION,
}

private val PREPARATION_CANCELLING_IDS = setOf(501, 502, 701, 702, 901, 902)
internal val PER_ROUND_PREPARED_EFFECT_IDS =
    setOf(701, 702, 703, 711, 714, 744, 752, 761, 771)

data class ActionEffectChange(
    val spec: PersistentEffectSpec,
    val kind: ActionEffectKind,
) : BattleStateChange

data class CancelPreparedSkillsChange(
    val spec: PersistentEffectSpec,
) : BattleStateChange {
    fun apply(runtime: SkillRuntimeState) {
        runtime.interruptPreparations(spec.target)
    }
}

data class CleanseEffectsChange(
    val spec: PersistentEffectSpec,
    val category: EffectCategory,
    val skillKinds: Set<SkillKind>? = null,
) : BattleStateChange {
    fun apply(store: BattleEffectStore): EffectLifecycleResult =
        store.clearMatching(spec.target) {
            it.category == category &&
                (skillKinds == null || it.skillKind in skillKinds)
        }
}

data class ScheduledEffectActivationChange(
    val spec: PersistentEffectSpec,
    val actionKind: ActionEffectKind? = null,
    val cleanseCategory: EffectCategory? = null,
    val cleanseSkillKinds: Set<SkillKind>? = null,
    val status: BattleStatus? = null,
) : BattleStateChange {
    fun activationChanges(): List<BattleStateChange> {
        val primary = when {
            cleanseCategory != null ->
                CleanseEffectsChange(spec, cleanseCategory, cleanseSkillKinds)
            actionKind != null -> ActionEffectChange(spec, actionKind)
            else -> ApplyBattleEffectChange(spec)
        }
        return buildList {
            add(primary)
            if (spec.effectId in PREPARATION_CANCELLING_IDS) {
                add(CancelPreparedSkillsChange(spec))
            }
        }
    }

    fun activationEvent(round: Int): BattleEvent? =
        status?.let {
            BattleEvent.StatusApplied(
                round = round,
                source = spec.source,
                target = spec.target,
                status = it,
                durationRounds = spec.availableRounds,
                power = spec.potency.value,
                skillId = spec.skillId,
                effectId = spec.effectId,
            )
        }
}

data class DamageRedirectionEffectChange(
    val spec: PersistentEffectSpec,
    val protectedTargets: List<BattleHeroRef>,
    val damageBearer: BattleHeroRef,
    val sharePercent: Int = 100,
    val school: com.stzb.battle.core.DamageSchool? = null,
) : BattleStateChange

data class LinkedDamageSharingEffectChange(
    val spec: PersistentEffectSpec,
    val members: List<BattleHeroRef>,
    val sharePercentPerAlly: Int,
) : BattleStateChange

class ActionPermissionResolver(
    private val effectStore: BattleEffectStore,
) {
    fun permissionFor(
        actor: BattleHeroRef,
        intendedTarget: BattleHeroRef? = null,
    ): ActionPermission =
        permissionFor(intendedTarget, effectStore.effectsFor(actor))

    private fun permissionFor(
        intendedTarget: BattleHeroRef?,
        effects: List<com.stzb.battle.core.ActiveSkillEffect>,
    ): ActionPermission {
        val cannotAct = effects.any { it.effectId in CONFUSION_IDS }
        val cannotCast = cannotAct || effects.any { it.effectId in HESITATION_IDS }
        val cannotNormal = cannotAct || effects.any { it.effectId in DISARM_IDS }
        val hasDoubleAttack = effects.any { it.effectId in DOUBLE_ATTACK_IDS }
        val guard = intendedTarget?.let { guarded ->
            effectStore.effectsFor(guarded).lastOrNull { it.effectId in GUARD_IDS }?.source
        }
        val taunt = effects.lastOrNull { it.effectId == TAUNT_ID }?.source
        return ActionPermission(
            canAct = !cannotAct,
            canCastActive = !cannotCast,
            canNormalAttack = !cannotNormal,
            redirectTarget = taunt ?: guard,
            normalAttackCount = when {
                cannotAct || cannotNormal -> 0
                hasDoubleAttack -> 2
                else -> 1
            },
            grantsPursuitOpportunityPerNormal = !cannotAct && !cannotNormal,
            counterattack = effects.any { it.effectId == COUNTERATTACK_ID },
            secondaryAttack = effects.any { it.effectId == SECONDARY_ATTACK_ID },
            firstAction = effects.any { it.effectId in FIRST_ACTION_IDS },
        )
    }

    fun permissionFor(
        actor: BattleHeroRef,
        context: SkillBattleContext,
    ): ActionPermission {
        val intendedTarget =
            if (SkillBattleViewCapability.TARGET_HISTORY in context.battleView.capabilities) {
                context.battleView.currentTarget(actor)
            } else {
                null
            }
        val effects = effectStore.effectsFor(actor).filter { effect ->
            effect.effectId !in PER_ROUND_PREPARED_EFFECT_IDS ||
                context.runtime.preparedEffectActive(
                    target = actor,
                    source = effect.source,
                    detailId = effect.detailId,
                    effectId = effect.effectId,
                    round = context.round,
                    probability = effect.effectiveStrength.coerceIn(0, 100),
                    random = context.random,
                )
        }
        val permission = permissionFor(intendedTarget, effects)
        if (effects.none { it.effectId in BERSERK_IDS }) return permission
        val resolvedSide = if (context.random.nextInt(2) == 0) actor.side else actor.side.opposite()
        val candidates = context.battleView.heroes()
            .filter { it.side == resolvedSide }
            .sortedWith(compareBy(BattleHeroRef::position, { it.heroId.value }))
        return permission.copy(
            resolvedAllegiance = resolvedSide,
            resolvedTargetPool = candidates,
        )
    }

    fun canEvade(
        target: BattleHeroRef,
        attacker: BattleHeroRef? = null,
        context: SkillBattleContext? = null,
    ): Boolean =
        effectStore.effectsFor(target).any { effect ->
            effect.effectId in EVADE_IDS &&
                (
                    effect.effectId !in PER_ROUND_PREPARED_EFFECT_IDS ||
                        context == null ||
                        context.runtime.preparedEffectActive(
                            target = target,
                            source = effect.source,
                            detailId = effect.detailId,
                            effectId = effect.effectId,
                            round = context.round,
                            probability = effect.effectiveStrength.coerceIn(0, 100),
                            random = context.random,
                        )
                    )
        } &&
            attacker?.let { source ->
                effectStore.effectsFor(source).none { it.effectId == IGNORE_EVADE_ID }
            } != false

    private companion object {
        val CONFUSION_IDS = setOf(501, 701, 901)
        val HESITATION_IDS = setOf(502, 702, 902)
        val BERSERK_IDS = setOf(503, 703, 903)
        val GUARD_IDS = setOf(504)
        const val TAUNT_ID = 505
        val EVADE_IDS = setOf(514, 714, 814)
        const val IGNORE_EVADE_ID = 515
        val DOUBLE_ATTACK_IDS = setOf(200, 544, 744)
        val DISARM_IDS = setOf(552, 752, 952)
        const val SECONDARY_ATTACK_ID = 545
        const val COUNTERATTACK_ID = 551
        val FIRST_ACTION_IDS = setOf(561, 761)
    }
}

object ControlEffectHandlers {
    val effectIds: Set<Int> =
        (
            (501..506) + (511..515) + listOf(542) + (544..546) +
                listOf(551, 552, 561, 571, 581, 594) + (701..703) + (711..714) +
                listOf(744, 752, 761, 771, 791, 793, 811, 814, 871) +
                (901..903) + listOf(952)
            ).toSet()

    fun registrations(
        effectStore: BattleEffectStore,
        calculator: BattleValueCalculator = DefaultBattleValueCalculator(),
        targetSelector: SkillTargetSelector = SkillTargetSelector(),
    ): Array<EffectHandlerRegistration> =
        effectIds.sorted().map { effectId ->
            EffectHandlerRegistration.implemented(
                effectId,
                ControlEffectHandler(effectId, effectStore, calculator, targetSelector),
            )
        }.toTypedArray()
}

private class ControlEffectHandler(
    private val ownedEffectId: Int,
    private val effectStore: BattleEffectStore,
    private val calculator: BattleValueCalculator,
    private val targetSelector: SkillTargetSelector,
) : ImplementedBattleEffectHandler {
    override val semanticId: String = "control.action.effect.$ownedEffectId"

    override fun execute(invocation: EffectInvocation): EffectExecution {
        check(invocation.rule.effectId == ownedEffectId) {
            "Handler $ownedEffectId cannot execute effect=${invocation.rule.effectId}"
        }
        val selectedTargets = invocation.selectTargets(targetSelector)
        val extraControl = ownedEffectId in CONTROL_IDS &&
            effectStore.effectsFor(invocation.context.source).any { it.effectId == EXTRA_CONTROL_TARGET_ID }
        val targets = if (extraControl && selectedTargets.isNotEmpty()) {
            val additional = invocation.context.battleView.heroes()
                .filter { it.side != invocation.context.source.side }
                .filter { candidate -> candidate !in selectedTargets }
                .filter {
                    val view = invocation.context.battleView
                    val state = if (SkillBattleViewCapability.LIVE_STATE in view.capabilities) {
                        view.state(it)
                    } else {
                        view.entryState(it)
                    }
                    state?.troops?.let { troops -> troops > 0 } != false
                }
                .sortedWith(compareByDescending<BattleHeroRef> { it.position }.thenBy { it.heroId.value })
                .firstOrNull()
            selectedTargets + listOfNotNull(additional)
        } else {
            selectedTargets
        }
        if (ownedEffectId == DAMAGE_REDIRECTION_ID) {
            if (targets.isEmpty()) return EffectExecution.EMPTY
            val bearer = invocation.context.battleView.heroes()
                .filter { it.side == invocation.context.source.side }
                .minByOrNull { it.position }
                ?: invocation.context.source
            return EffectExecution(
                stateChanges = listOf(
                    DamageRedirectionEffectChange(
                        spec = persistentSpec(invocation, targets.first()),
                        protectedTargets = targets,
                        damageBearer = bearer,
                    ),
                ),
                events = emptyList(),
            )
        }
        val changes = mutableListOf<BattleStateChange>()
        val events = mutableListOf<BattleEvent>()
        targets.forEach { target ->
            if (invocation.rule.skillKind == SkillKind.COMMAND &&
                target.side != invocation.context.source.side &&
                effectStore.effectsFor(target).any { it.effectId == COMMAND_IMMUNITY_ID }
            ) {
                changes += EffectBlockedChange(
                    invocation.context.source,
                    target,
                    invocation.context.currentSkillId,
                    ownedEffectId,
                    COMMAND_IMMUNITY_ID,
                )
                return@forEach
            }
            val blocker = blockingEffect(target, invocation)
            if (blocker != null) {
                changes += EffectBlockedChange(
                    invocation.context.source,
                    target,
                    invocation.context.currentSkillId,
                    ownedEffectId,
                    blocker,
                )
                return@forEach
            }
            val spec = persistentSpec(invocation, target)
            if (spec.startBoundary == EffectStartBoundary.AFTER_DELAY) {
                changes += scheduledChange(spec, invocation.rule.raw.effectParam)
                return@forEach
            }
            changes += immediateChanges(spec, invocation.rule.raw.effectParam)
            statusFor(ownedEffectId)?.let { status ->
                events += BattleEvent.StatusApplied(
                    round = invocation.context.round,
                    source = invocation.context.source,
                    target = target,
                    status = status,
                    durationRounds = invocation.rule.raw.availableRounds,
                    power = invocation.rule.raw.constantParam,
                    skillId = invocation.context.currentSkillId,
                    effectId = ownedEffectId,
                )
            }
        }
        if (extraControl && targets.size > selectedTargets.size) {
            changes += ConsumeEffectUseChange(
                source = invocation.context.source,
                target = invocation.context.source,
                effectId = EXTRA_CONTROL_TARGET_ID,
            )
        }
        return EffectExecution(changes, events)
    }

    private fun immediateChanges(
        spec: PersistentEffectSpec,
        effectParam: Int,
    ): List<BattleStateChange> {
        if (ownedEffectId in CLEANSE_IDS) {
            return listOf(
                cleanse(
                    spec,
                    EffectCategory.HARMFUL,
                    cleanseSkillKinds(effectParam),
                ),
            )
        }
        if (ownedEffectId in DISPEL_IDS) {
            return listOf(cleanse(spec, EffectCategory.BENEFICIAL))
        }
        val actionKind = actionKindFor(ownedEffectId)
        val primary: BattleStateChange =
            if (actionKind == null) ApplyBattleEffectChange(spec) else ActionEffectChange(spec, actionKind)
        return buildList {
            add(primary)
            if (ownedEffectId in PREPARATION_CANCELLING_IDS) {
                add(CancelPreparedSkillsChange(spec))
            }
        }
    }

    private fun scheduledChange(
        spec: PersistentEffectSpec,
        effectParam: Int,
    ) = ScheduledEffectActivationChange(
        spec = spec,
        actionKind = actionKindFor(ownedEffectId),
        cleanseCategory = when {
            ownedEffectId in CLEANSE_IDS -> EffectCategory.HARMFUL
            ownedEffectId in DISPEL_IDS -> EffectCategory.BENEFICIAL
            else -> null
        },
        cleanseSkillKinds = cleanseSkillKinds(effectParam),
        status = statusFor(ownedEffectId),
    )

    private fun persistentSpec(
        invocation: EffectInvocation,
        target: BattleHeroRef,
    ): PersistentEffectSpec {
        requireSupportedSkillOrigin(invocation)
        val raw = invocation.rule.raw
        val source = invocation.liveSourceHero()
        val level = invocation.rootSkillLevel(source)
        val lifecycle = invocation.lifecycle()
        val potency = invocation.valueOverride ?: if (ownedEffectId in PER_ROUND_PREPARED_EFFECT_IDS) {
            TypedBattlePotency.percent(
                (
                    raw.probabilityInit +
                        (level - 1) * (raw.probabilityMax - raw.probabilityInit) / 9.0
                    ).toInt().coerceIn(0, 100),
            )
        } else {
            when (val value = calculator.effectValue(invocation.rule, source, level)) {
            is TypedBattlePotency.Resolved -> value
            is TypedBattlePotency.Deferred -> throw UnsupportedConfiguredBattleValueException(
                BattleEffectDiagnostic(
                    EffectFailureCode.UNSUPPORTED_CONFIGURED_VALUE,
                    invocation.context.currentSkillId,
                    invocation.rule.detailId,
                    invocation.rule.effectId,
                    invocation.context.trigger,
                    invocation.callPath,
                    value.diagnostic,
                ),
            )
            }
        }
        return PersistentEffectSpec(
            source = invocation.context.source,
            target = target,
            rootSkillId = invocation.context.rootSkillId,
            skillId = invocation.context.currentSkillId,
            skillKind = invocation.rule.skillKind,
            rawSkillType = invocation.rule.rawSkillType,
            detailId = invocation.rule.detailId,
            effectId = ownedEffectId,
            category = EffectCategory.fromClientBuffType(invocation.rule.effectBuffType),
            conflict = raw.hideConflict,
            replaceType = invocation.rule.effectReplaceType,
            bindFlag = raw.bindFlag,
            maxStacks = raw.addCountMax + 1,
            delayRound = lifecycle.delayRound,
            delayHit = lifecycle.delayHit,
            availableRounds = lifecycle.availableRounds,
            availableHit = lifecycle.availableHit,
            clearPerHit = lifecycle.clearPerHit,
            startBoundary =
                if (lifecycle.delayRound > 0 || lifecycle.delayHit > 0) {
                    EffectStartBoundary.AFTER_DELAY
                } else {
                    EffectStartBoundary.IMMEDIATE
                },
            potency = potency,
        )
    }

    private fun cleanse(
        spec: PersistentEffectSpec,
        category: EffectCategory,
        skillKinds: Set<SkillKind>? = null,
    ) = CleanseEffectsChange(
        spec = spec,
        category = category,
        skillKinds = skillKinds,
    )

    private fun cleanseSkillKinds(effectParam: Int): Set<SkillKind>? =
        when (effectParam) {
            34 -> setOf(SkillKind.ACTIVE, SkillKind.PURSUIT)
            else -> null
        }

    private fun blockingEffect(
        target: BattleHeroRef,
        invocation: EffectInvocation,
    ): Int? {
        if (ownedEffectId !in INSIGHT_BLOCKED_IDS) return null
        val active = effectStore.effectsFor(target)
        val activeIds = active.mapTo(mutableSetOf()) { it.effectId }
        return when {
            activeIds.any { it in INSIGHT_IDS } -> activeIds.first { it in INSIGHT_IDS }
            ownedEffectId in CONFUSION_IDS && CONFUSION_IMMUNITY_ID in activeIds ->
                CONFUSION_IMMUNITY_ID
            ownedEffectId in BERSERK_IDS && BERSERK_IMMUNITY_ID in activeIds ->
                BERSERK_IMMUNITY_ID
            ownedEffectId in DISARM_IDS && DISARM_IMMUNITY_ID in activeIds -> DISARM_IMMUNITY_ID
            active.lastOrNull { it.effectId == RESISTANCE_ID }?.let { resistance ->
                invocation.context.random.nextInt(100) < resistance.effectiveStrength.coerceIn(0, 100)
            } == true -> RESISTANCE_ID
            else -> null
        }
    }

    private companion object {
        val CONTROL_IDS =
            setOf(501, 502, 503, 505, 552, 701, 702, 703, 752, 901, 902, 903, 952)
        val CONFUSION_IDS = setOf(501, 701, 901)
        val BERSERK_IDS = setOf(503, 703, 903)
        val INSIGHT_IDS = setOf(511, 711, 811)
        val DISARM_IDS = setOf(552, 752, 952)
        val INSIGHT_BLOCKED_IDS = CONTROL_IDS
        const val DISARM_IMMUNITY_ID = 594
        const val CONFUSION_IMMUNITY_ID = 791
        const val BERSERK_IMMUNITY_ID = 793
        val CLEANSE_IDS = setOf(513, 713)
        val DISPEL_IDS = setOf(512, 712)
        const val DAMAGE_REDIRECTION_ID = 506
        const val COMMAND_IMMUNITY_ID = 121
        const val RESISTANCE_ID = 118
        const val EXTRA_CONTROL_TARGET_ID = 404
    }
}

private fun requireSupportedSkillOrigin(invocation: EffectInvocation) {
    val expectedKind = SkillKind.fromRawType(invocation.rule.rawSkillType)
    if (expectedKind == SkillKind.UNKNOWN || expectedKind != invocation.rule.skillKind) {
        throw UnsupportedConfiguredBattleValueException(
            BattleEffectDiagnostic(
                code = EffectFailureCode.UNSUPPORTED_CONFIGURED_VALUE,
                skillId = invocation.context.currentSkillId,
                detailId = invocation.rule.detailId,
                effectId = invocation.rule.effectId,
                trigger = invocation.context.trigger,
                callPath = invocation.callPath,
                reason = "Unsupported action origin: skillKind=${invocation.rule.skillKind} " +
                    "rawSkillType=${invocation.rule.rawSkillType}",
            ),
        )
    }
}

private fun actionKindFor(effectId: Int): ActionEffectKind? =
    when (effectId) {
        504 -> ActionEffectKind.GUARD
        515 -> ActionEffectKind.IGNORE_EVADE
        542 -> ActionEffectKind.STRATEGY_LIFE_STEAL
        544, 744 -> ActionEffectKind.DOUBLE_ATTACK
        545 -> ActionEffectKind.SECONDARY_ATTACK
        546 -> ActionEffectKind.EXECUTION_ATTACK
        551 -> ActionEffectKind.COUNTERATTACK
        571, 771, 871 -> ActionEffectKind.IGNORE_TROOP_COUNTER
        581 -> ActionEffectKind.REDUCE_INHERENT_PREPARATION
        561, 761 -> ActionEffectKind.FIRST_ACTION
        else -> null
    }

private fun statusFor(effectId: Int): BattleStatus? =
    when (effectId) {
        501, 701, 901 -> BattleStatus.CONFUSION
        503, 703, 903 -> BattleStatus.BERSERK
        502, 702, 902 -> BattleStatus.HESITATION
        511, 711, 811 -> BattleStatus.INSIGHT
        514, 714, 814 -> BattleStatus.EVADE
        515 -> BattleStatus.IGNORE_EVADE
        544, 744 -> BattleStatus.DOUBLE_ATTACK
        552, 752, 952 -> BattleStatus.DISARM
        561, 761 -> BattleStatus.FIRST_ACTION
        else -> null
    }

private fun EffectInvocation.liveSourceHero(): BattleHero {
    val ref = context.source
    val entry = (if (ref.side == com.stzb.battle.core.Side.ATTACKER) {
        context.request.attacker
    } else {
        context.request.defender
    }).heroes.single { it.position == ref.position && it.id == ref.heroId }
    if (SkillBattleViewCapability.LIVE_STATE !in context.battleView.capabilities) return entry
    val state = requireNotNull(context.battleView.state(ref)) { "Missing live hero state for $ref" }
    return entry.copy(
        stats = state.stats,
        troops = state.troops,
        maxTroops = state.maxTroops,
        activeStatuses = state.statuses,
        morale = state.morale,
        modifiers = state.modifiers ?: entry.modifiers,
    )
}
