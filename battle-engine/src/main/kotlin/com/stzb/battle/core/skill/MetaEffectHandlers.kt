package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleEffectValueUnit
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleModifier
import com.stzb.battle.core.BattleStat
import com.stzb.battle.core.BattleTargetingKind
import com.stzb.battle.core.ConfiguredBattleEffectValue
import com.stzb.battle.core.SkillKind
import com.stzb.battle.core.opposite

enum class MetaEffectOperation {
    MARKER,
    SIMULATED_NORMAL_ATTACK,
    NORMAL_ATTACK_ALL_IN_RANGE,
    JOINT_ATTACK,
    DAMAGE_RATE_MAXIMUM,
    DAMAGE_RATE_MINIMUM,
    SHARED_EFFECT_USES,
    REFERENCED_EXTRA_PARAMETER,
    REFERENCED_VALUE_CHANGE,
    MORALE_INCREASE,
    MORALE_DECREASE,
    RESISTANCE,
    COMMAND_EFFECT_IMMUNITY,
    EXECUTE_BENEFICIAL_CHILD,
    EXECUTE_HARMFUL_CHILD,
    BENEFICIAL_PUPPET,
    DAMAGE_SHARING,
    RANGED_NORMAL_ATTACK,
    CONTROL_DURATION_INCREASE,
    NEXT_CONTROL_DURATION_INCREASE,
    RETRIGGER_ACTIVE_SKILL,
    RETRIGGER_PURSUIT_SKILL,
    SKILL_PROBABILITY_INCREASE,
    SKILL_ENHANCEMENT_UNLOCK,
    EFFECT_PROBABILITY_INCREASE,
    TRIGGER_LAST_APPLIED_EFFECT,
    TRIGGER_SPECIFIED_EFFECT,
    TRIGGER_REFERENCED_EFFECT,
    CLEAR_REFERENCED_EFFECT,
    TRIGGER_ATTRIBUTE_SCALED_EFFECT,
    IGNORE_ENEMY_ATTRIBUTE,
    SKILL_RANGE_INCREASE,
    SKILL_RANGE_DECREASE,
    NORMAL_TARGET_IMMUNITY,
    ACTIVE_TARGET_IMMUNITY,
    PURSUIT_TARGET_IMMUNITY,
    TRANSFORMATION,
    COMBO,
    EXECUTE_NAMED_CHILD,
    SIEGE_IMMUNITY,
    SKILL_PROBABILITY_DECREASE,
    SPECIAL_DAMAGE_TAKEN_INCREASE,
    RECOVERY_DEALT_INCREASE,
    RECOVERY_TAKEN_INCREASE,
    REDUCE_REFERENCED_EFFECT_USES,
    EXTRA_CONTROL_TARGET,
    DAMAGE_ABSORPTION,
    RELEASE_DAMAGE,
    LINKED_HEARTS,
    COUNTERATTACK_IMMUNITY,
}

/**
 * Repository-free, lossless execution metadata for meta effects. Consumers can
 * apply these intents later without reopening the CSV repository.
 */
data class MetaEffectParameters(
    val detailId: Int,
    val effectId: Int,
    val effectParam: Int,
    val calcPosition: Int,
    val calcParameter: Int,
    val attackType: Int,
    val selectSkillParameter: Int,
    val targetType: Int,
    val selectType: Int,
    val targetCountry: Int,
    val selectAttribute: Int,
    val customSelectFlag: Int,
    val availableHit: Int,
    val intelligenceCoefficient: Int,
    val constant: Int,
    val probabilityInitial: Int,
    val probabilityMaximum: Int,
    val bindFlag: Int,
    val castCondition: Int,
    val precondition: Int,
    val condition: Int,
    val addCountMaximum: Int,
    val rawBuffType: Int,
    val targetLimit: Int,
    val delayRound: Int,
    val delayHit: Int,
    val availableRounds: Int,
    val clearPerHit: Boolean,
    val selectFlag: Int,
    val inherent: Int,
    val moraleAffected: Boolean,
    val attributeType: Int,
    val valueAddMaximum: Int,
    val hideConflict: Int,
    val probabilitySeries: List<Int>,
    val calculationType: Int,
    val calculationTypes: List<Int>,
    val effectName: String,
    val childSkillIds: Set<Int>,
    val skillHitRange: Int?,
    val configuredValue: ConfiguredBattleEffectValue?,
    val resolvedBuffType: Int,
    val replaceType: Int,
    val skillKind: SkillKind,
    val rawSkillType: Int,
) {
    companion object {
        fun from(rule: SkillEffectRule): MetaEffectParameters {
            val raw = rule.raw
            return MetaEffectParameters(
                detailId = rule.detailId,
                effectId = rule.effectId,
                effectParam = raw.effectParam,
                calcPosition = raw.calcPos,
                calcParameter = raw.calcParam,
                attackType = raw.attackType,
                selectSkillParameter = raw.selectSkillParam,
                targetType = raw.targetType,
                selectType = raw.selectType,
                targetCountry = raw.targetCountry,
                selectAttribute = raw.selectAttri,
                customSelectFlag = raw.customSelectFlag,
                availableHit = raw.availableHit,
                intelligenceCoefficient = raw.intelParam,
                constant = raw.constantParam,
                probabilityInitial = raw.probabilityInit,
                probabilityMaximum = raw.probabilityMax,
                bindFlag = raw.bindFlag,
                castCondition = raw.castCondition,
                precondition = raw.precondition,
                condition = raw.condition,
                addCountMaximum = raw.addCountMax,
                rawBuffType = raw.buffType,
                targetLimit = raw.attackMax,
                delayRound = raw.delayRound,
                delayHit = raw.delayHit,
                availableRounds = raw.availableRounds,
                clearPerHit = raw.clearPerHit,
                selectFlag = raw.selectFlag,
                inherent = raw.inherent,
                moraleAffected = raw.moraleAffected,
                attributeType = raw.attributeType,
                valueAddMaximum = raw.valueAddMax,
                hideConflict = raw.hideConflict,
                probabilitySeries = raw.probabilitySeries.toList(),
                calculationType = raw.calculationType,
                calculationTypes = raw.calculationTypes.toList(),
                effectName = raw.effectName,
                childSkillIds = rule.childSkillIds.toSet(),
                skillHitRange = rule.skillHitRange,
                configuredValue = rule.configuredValue,
                resolvedBuffType = rule.effectBuffType,
                replaceType = rule.effectReplaceType,
                skillKind = rule.skillKind,
                rawSkillType = rule.rawSkillType,
            )
        }
    }
}

data class MarkerEffectChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val marker: Int,
    val parameters: MetaEffectParameters,
) : BattleStateChange

data class NamedFlagCounterChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val flagId: Int,
    val delta: Int,
    val maximum: Int,
) : BattleStateChange

enum class SimulatedNormalAttackMode {
    SINGLE,
    ALL_IN_RANGE,
}

data class SimulatedNormalAttackChange(
    val source: BattleHeroRef,
    val mode: SimulatedNormalAttackMode,
    val skillId: Int,
    val effectId: Int,
    val detailId: Int,
) : BattleStateChange

data class ReferencedValueChange(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val referencedDetailId: Int,
    val delta: Int,
    val parameters: MetaEffectParameters,
) : BattleStateChange

data class MetaEffectChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val effectId: Int,
    val operation: MetaEffectOperation,
    val parameters: MetaEffectParameters,
) : BattleStateChange

data class TriggerLastAppliedEffectChange(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val targets: List<BattleHeroRef>,
    val parameters: MetaEffectParameters,
    val appliedSpec: PersistentEffectSpec? = null,
) : BattleStateChange

data class TriggerSpecifiedEffectChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val triggeredEffectId: Int,
    val parameters: MetaEffectParameters,
    val triggeredSource: BattleHeroRef? = null,
    val triggeredDetailId: Int? = null,
) : BattleStateChange

data class TransformAndCastRandomActiveSkillChange(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val parameters: MetaEffectParameters,
) : BattleStateChange

data class ForcedTargetEffectChange(
    val spec: PersistentEffectSpec,
    val forcedTarget: BattleHeroRef,
) : BattleStateChange

data class SharedEffectUseGroupChange(
    val spec: PersistentEffectSpec,
    val memberDetailId: Int,
) : BattleStateChange

data class DamageAbsorptionAccumulatorEffectChange(
    val spec: PersistentEffectSpec,
    val protectedTargets: List<BattleHeroRef>,
    val absorbPercent: Int,
) : BattleStateChange

data class DamageReleaseScheduleEffectChange(
    val spec: PersistentEffectSpec,
    val target: BattleHeroRef,
    val referencedDetailId: Int,
    val referencedEffectId: Int,
    val baseReleasePercent: Int,
    val firstReleaseRound: Int,
) : BattleStateChange

data class ReferencedExtraParameterChange(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val referencedDetailId: Int,
    val calcPosition: Int,
    val calcParameter: Int,
    val value: Int,
    val parameters: MetaEffectParameters,
) : BattleStateChange

data class ModifierEffectChange(
    val spec: PersistentEffectSpec,
    val modifier: com.stzb.battle.core.BattleModifier,
) : BattleStateChange

data class MoraleEffectChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val operation: MetaEffectOperation,
    val potency: TypedBattlePotency.Resolved,
    val parameters: MetaEffectParameters,
) : BattleStateChange {
    val delta: Int
        get() = potency.value
}

data class ExecuteChildSkillChange(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val operation: MetaEffectOperation,
    val childSkillIds: List<Int>,
    val selectedTargets: List<BattleHeroRef>,
    val inheritedPreselectedTargets: List<BattleHeroRef>?,
    val valueOverride: TypedBattlePotency.Resolved?,
    val probabilityOwnership: ChildProbabilityOwnership,
    val parameters: MetaEffectParameters,
) : BattleStateChange

enum class ChildProbabilityOwnership {
    CONFIGURED_CHILD,
    FORCED_SUCCESS,
}

data class RetriggerSkillChange(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val operation: MetaEffectOperation,
    val skillKind: SkillKind,
    val selectedTargets: List<BattleHeroRef>,
    val maximumExecutions: Int?,
    val probabilityOwnership: ChildProbabilityOwnership,
    val parameters: MetaEffectParameters,
    val effectValueScalePercent: Int = 100,
) : BattleStateChange

enum class ReferenceEffectMode {
    NORMAL,
    ATTRIBUTE_SCALED,
    DAMAGE_RELEASE,
    TRIGGER_EXISTING,
}

data class TriggerReferencedEffectChange(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val referencedDetailId: Int,
    val referencedEffectId: Int,
    val selectedTargets: List<BattleHeroRef>,
    val mode: ReferenceEffectMode,
    val valueOverride: TypedBattlePotency.Resolved?,
    val parameters: MetaEffectParameters,
    val executionOverride: ReferencedDetailExecutionOverride? = null,
    val probabilityAlreadyAccepted: Boolean = false,
) : BattleStateChange {
    val attributeScaled: Boolean
        get() = mode == ReferenceEffectMode.ATTRIBUTE_SCALED
}

data class ClearReferencedEffectChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val referencedDetailId: Int,
    val referencedEffectId: Int,
    val parameters: MetaEffectParameters,
) : BattleStateChange {
    fun apply(store: BattleEffectStore): EffectLifecycleResult =
        store.clearMatching(target) {
            it.detailId == referencedDetailId && it.effectId == referencedEffectId
        }
}

data class ReduceReferencedEffectUseChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val referencedDetailId: Int,
    val referencedEffectId: Int,
    val amount: Int,
    val parameters: MetaEffectParameters,
) : BattleStateChange {
    fun apply(store: BattleEffectStore): EffectLifecycleResult {
        var result = EffectLifecycleResult()
        repeat(amount.coerceAtLeast(0)) {
            val consumed = store.consumeHit(
                target = target,
                effectId = referencedEffectId,
                source = source,
                detailId = referencedDetailId,
            )
            if (consumed.updated.isEmpty() && consumed.expired.isEmpty()) return result
            result = consumed
        }
        return result
    }
}

data class ConsumeEffectUseChange(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val effectId: Int,
) : BattleStateChange {
    fun apply(store: BattleEffectStore): EffectLifecycleResult =
        store.consumeHit(target = target, effectId = effectId, source = source)
}

object MetaEffectHandlers {
    val effectIds: Set<Int> = setOf(
        0,
        77, 79, 80,
        81, 82, 83,
        88,
        111, 112, 113, 114,
        118,
        121, 122, 123,
        125, 127, 128, 129, 130, 131, 132,
        141, 149, 150,
        151, 152, 153,
        161, 171, 181, 190, 193, 194, 199, 200, 210, 220, 231, 261, 271, 281, 313,
        311, 312,
        404, 407, 408, 409,
        553,
    )

    fun registrations(
        targetSelector: SkillTargetSelector = SkillTargetSelector(),
        calculator: BattleValueCalculator = DefaultBattleValueCalculator(),
        detailResolver: (Int) -> SkillEffectRule? = { null },
    ): Array<EffectHandlerRegistration> =
        effectIds.sorted().map { effectId ->
            EffectHandlerRegistration.implemented(
                effectId,
                MetaEffectHandler(effectId, targetSelector, calculator, detailResolver),
            )
        }.toTypedArray()
}

private class MetaEffectHandler(
    private val ownedEffectId: Int,
    private val targetSelector: SkillTargetSelector,
    private val calculator: BattleValueCalculator,
    private val detailResolver: (Int) -> SkillEffectRule?,
) : ImplementedBattleEffectHandler {
    override val semanticId: String = "meta.${OPERATIONS[ownedEffectId]?.name?.lowercase() ?: "no-op"}"

    override fun execute(invocation: EffectInvocation): EffectExecution {
        check(invocation.rule.effectId == ownedEffectId) {
            "Handler $ownedEffectId cannot execute effect=${invocation.rule.effectId}"
        }
        if (ownedEffectId == 0) return EffectExecution.EMPTY

        val targets = invocation.selectTargets(targetSelector)
        val context = invocation.context
        val raw = invocation.rule.raw
        val parameters = MetaEffectParameters.from(invocation.rule)
        val operation = OPERATIONS.getValue(ownedEffectId)
        val changes: List<BattleStateChange> = when (ownedEffectId) {
            79, 80 -> listOf(
                SimulatedNormalAttackChange(
                    source = context.source,
                    mode = if (ownedEffectId == 79) {
                        SimulatedNormalAttackMode.SINGLE
                    } else {
                        SimulatedNormalAttackMode.ALL_IN_RANGE
                    },
                    skillId = context.currentSkillId,
                    effectId = ownedEffectId,
                    detailId = invocation.rule.detailId,
                ),
            )
            77 -> targets.map { target ->
                MarkerEffectChange(
                    source = context.source,
                    target = target,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    marker = raw.constantParam,
                    parameters = parameters,
                )
            }
            113, 114 -> {
                val value = configuredPotency(invocation)
                val signed = value.copy(value = if (ownedEffectId == 113) value.value else -value.value)
                targets.map { target ->
                    MoraleEffectChange(
                        source = context.source,
                        target = target,
                        rootSkillId = context.rootSkillId,
                        skillId = context.currentSkillId,
                        detailId = invocation.rule.detailId,
                        operation = operation,
                        potency = signed,
                        parameters = parameters,
                    )
                }
            }
            88 -> {
                val referenced = referencedDetail(invocation)
                targets.map { target ->
                    SharedEffectUseGroupChange(
                        spec = persistentSpec(
                            invocation,
                            target,
                            TypedBattlePotency.flat(1),
                        ),
                        memberDetailId = referenced.detailId,
                    )
                }
            }
            111 -> {
                val referenced = referencedDetail(invocation)
                val resolved = configuredPotency(invocation)
                val value = if (raw.calcPos == 991 && kotlin.math.abs(resolved.value) >= 1_000_000) {
                    resolved.value / 1_000_000
                } else {
                    resolved.value
                }
                listOf(
                    ReferencedExtraParameterChange(
                        source = context.source,
                        rootSkillId = context.rootSkillId,
                        skillId = context.currentSkillId,
                        detailId = invocation.rule.detailId,
                        referencedDetailId = referenced.detailId,
                        calcPosition = raw.calcPos,
                        calcParameter = raw.calcParam,
                        value = value,
                        parameters = parameters,
                    ),
                )
            }
            112 -> {
                val referenced = referencedDetail(invocation)
                val resolved = configuredPotency(invocation)
                listOf(
                    ReferencedValueChange(
                        source = context.source,
                        rootSkillId = context.rootSkillId,
                        skillId = context.currentSkillId,
                        detailId = invocation.rule.detailId,
                        referencedDetailId = referenced.detailId,
                        delta = resolved.value,
                        parameters = parameters,
                    ),
                )
            }
            122, 123 -> listOf(
                ExecuteChildSkillChange(
                    source = context.source,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    operation = operation,
                    childSkillIds = runtimeChildSkillIds(invocation),
                    selectedTargets = targets,
                    inheritedPreselectedTargets = invocation.preselectedTargets,
                    valueOverride = invocation.valueOverride,
                    probabilityOwnership = ChildProbabilityOwnership.CONFIGURED_CHILD,
                    parameters = parameters,
                ),
            )
            210 -> targets.map { target ->
                NamedFlagCounterChange(
                    source = context.source,
                    target = target,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    flagId = raw.effectParam,
                    delta = raw.constantParam,
                    maximum = raw.addCountMax,
                )
            }
            125 -> {
                val referenced = referencedDetail(invocation)
                val inherited = invocation.executionOverride
                val override = ReferencedDetailExecutionOverride(
                    referencedDetailId = referenced.detailId,
                    valueReplacement = configuredPotency(invocation),
                    extraParameters = inherited?.extraParameters.orEmpty(),
                    targetOverride = targets,
                    lifecycleOverride = inherited?.lifecycleOverride ?: EffectLifecycleOverride(
                        delayRound = raw.delayRound,
                        delayHit = raw.delayHit,
                        availableRounds = raw.availableRounds,
                        availableHit = raw.availableHit,
                        clearPerHit = raw.clearPerHit,
                    ),
                )
                listOf(
                    TriggerReferencedEffectChange(
                        source = context.source,
                        rootSkillId = context.rootSkillId,
                        skillId = context.currentSkillId,
                        detailId = invocation.rule.detailId,
                        referencedDetailId = referenced.detailId,
                        referencedEffectId = referenced.effectId,
                        selectedTargets = targets,
                        mode = ReferenceEffectMode.NORMAL,
                        valueOverride = override.valueReplacement,
                        parameters = parameters,
                        executionOverride = override,
                        probabilityAlreadyAccepted = true,
                    ),
                )
            }
            129, 130 -> listOf(
                RetriggerSkillChange(
                    source = context.source,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    operation = operation,
                    skillKind = if (ownedEffectId == 129) SkillKind.ACTIVE else SkillKind.PURSUIT,
                    selectedTargets = targets,
                    maximumExecutions = raw.availableHit.takeIf { it > 0 },
                    probabilityOwnership = ChildProbabilityOwnership.CONFIGURED_CHILD,
                    parameters = parameters,
                    effectValueScalePercent = invocation.executionOverride
                        ?.extraParameters
                        ?.get(raw.calcPos)
                        ?.coerceAtLeast(0)
                        ?: 100,
                ),
            )
            131, 231 -> targets.map { target ->
                val rawSkillId = raw.effectParam
                val skillId = rawSkillId.takeIf { it > 0 }
                val skillKind = when (rawSkillId) {
                    -11, -1_000_003 -> SkillKind.ACTIVE
                    -14, -1_000_004 -> SkillKind.PURSUIT
                    -21 -> SkillKind.COMMAND
                    else -> null
                }
                val sign = if (ownedEffectId == 231) -1 else 1
                ModifierEffectChange(
                    spec = persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.percent(sign * raw.constantParam),
                    ),
                    modifier = BattleModifier.SkillProbabilityPercent(
                        percent = sign * raw.constantParam,
                        skillId = skillId,
                        skillKind = skillKind,
                    ),
                )
            }
            141 -> targets.map { target ->
                val referencedDetailId = raw.effectParam
                requireNotNull(detailResolver(referencedDetailId)) {
                    "Missing referenced probability detail=$referencedDetailId"
                }
                val potency = TypedBattlePotency.percent(raw.constantParam)
                ModifierEffectChange(
                    spec = persistentSpec(invocation, target, potency),
                    modifier = BattleModifier.EffectProbabilityPercent(
                        detailId = referencedDetailId,
                        percent = potency.value,
                    ),
                )
            }
            271 -> {
                val potency = configuredPotency(invocation)
                targets.map { target ->
                    ModifierEffectChange(
                        spec = persistentSpec(invocation, target, potency),
                        modifier = BattleModifier.RecoveryDealtPercent(potency.value),
                    )
                }
            }
            281 -> {
                val potency = configuredPotency(invocation)
                targets.map { target ->
                    ModifierEffectChange(
                        spec = persistentSpec(invocation, target, potency),
                        modifier = BattleModifier.RecoveryTakenPercent(potency.value),
                    )
                }
            }
            261 -> {
                val potency = configuredPotency(invocation)
                val tag = when (raw.effectParam) {
                    303 -> com.stzb.battle.core.DamageTag.SHAKE
                    304 -> com.stzb.battle.core.DamageTag.PANIC
                    305 -> com.stzb.battle.core.DamageTag.BURN
                    306 -> com.stzb.battle.core.DamageTag.HEX
                    307 -> com.stzb.battle.core.DamageTag.FIRE
                    else -> null
                }
                targets.map { target ->
                    ModifierEffectChange(
                        spec = persistentSpec(invocation, target, potency),
                        modifier = BattleModifier.DamageTakenPercent(
                            tag = tag,
                            percent = potency.value,
                        ),
                    )
                }
            }
            161 -> {
                val potency = defenseIgnorePotency(invocation)
                val stat = when (raw.effectParam) {
                    2 -> BattleStat.DEFENSE
                    3 -> BattleStat.STRATEGY
                    else -> throw UnsupportedConfiguredBattleValueException(
                        BattleEffectDiagnostic(
                            code = EffectFailureCode.UNSUPPORTED_CONFIGURED_VALUE,
                            skillId = context.currentSkillId,
                            detailId = invocation.rule.detailId,
                            effectId = invocation.rule.effectId,
                            trigger = context.trigger,
                            callPath = invocation.callPath,
                            reason = "Unsupported ignored attribute effectParam=${raw.effectParam}",
                        ),
                    )
                }
                targets.map { target ->
                    ModifierEffectChange(
                        spec = persistentSpec(invocation, target, potency),
                        modifier = BattleModifier.DefenseIgnorePercent(potency.value, stat),
                    )
                }
            }
            190, 193, 194 -> {
                val kind = when (ownedEffectId) {
                    190 -> BattleTargetingKind.NORMAL_ATTACK
                    193 -> BattleTargetingKind.ACTIVE_SKILL
                    194 -> BattleTargetingKind.PURSUIT_SKILL
                    else -> error("Unsupported target immunity effect=$ownedEffectId")
                }
                targets.map { target ->
                    ModifierEffectChange(
                        spec = persistentSpec(
                            invocation,
                            target,
                            TypedBattlePotency.flat(1),
                        ),
                        modifier = BattleModifier.TargetImmunity(kind),
                    )
                }
            }
            553 -> targets.map { target ->
                ModifierEffectChange(
                    spec = persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.flat(1),
                    ),
                    modifier = BattleModifier.CounterattackImmunity,
                )
            }
            121 -> targets.map { target ->
                ApplyBattleEffectChange(
                    persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.flat(1),
                    ),
                )
            }
            220 -> targets.map { target ->
                ApplyBattleEffectChange(
                    persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.flat(1),
                    ),
                )
            }
            81 -> {
                val forcedTarget = if (raw.customSelectFlag != 0) {
                    context.battleView.heroes()
                        .filter { candidate ->
                            candidate.side != context.source.side &&
                                (
                                    if (SkillBattleViewCapability.LIVE_STATE in
                                        context.battleView.capabilities
                                    ) {
                                        context.battleView.state(candidate)
                                    } else {
                                        context.battleView.entryState(candidate)
                                    }
                                    )?.troops?.let { it > 0 } == true
                        }
                        .minByOrNull(BattleHeroRef::position)
                } else {
                    context.runtime.latestMarkedTarget(
                        rootSkillId = context.rootSkillId,
                        targetSide = context.source.side.opposite(),
                        round = context.round,
                    )
                }
                if (forcedTarget == null) {
                    emptyList()
                } else {
                    targets.map { target ->
                        ForcedTargetEffectChange(
                            spec = persistentSpec(
                                invocation,
                                target,
                                TypedBattlePotency.percent(raw.constantParam.coerceIn(0, 100)),
                            ),
                            forcedTarget = forcedTarget,
                        )
                    }
                }
            }
            118 -> {
                val source = sourceHero(invocation)
                val level = invocation.rootSkillLevel(source)
                val percent = (
                    raw.probabilityInit +
                        (level - 1) * (raw.probabilityMax - raw.probabilityInit) / 9.0
                    ).toInt().coerceIn(0, 100)
                targets.map { target ->
                    ApplyBattleEffectChange(
                        persistentSpec(
                            invocation,
                            target,
                            TypedBattlePotency.percent(percent),
                        ),
                    )
                }
            }
            127 -> targets.mapNotNull { target ->
                val bearer = when {
                    context.source != target -> context.source
                    else -> targets.firstOrNull { it != target }
                } ?: return@mapNotNull null
                val percent = if (raw.calcPos == 992) {
                    raw.constantParam / 5
                } else {
                    raw.constantParam
                }.coerceIn(1, 100)
                DamageRedirectionEffectChange(
                    spec = persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.percent(percent),
                    ),
                    protectedTargets = listOf(target),
                    damageBearer = bearer,
                    sharePercent = percent,
                    school = when (raw.effectParam) {
                        0 -> com.stzb.battle.core.DamageSchool.PHYSICAL
                        1 -> com.stzb.battle.core.DamageSchool.STRATEGY
                        else -> null
                    },
                )
            }
            128 -> {
                val potency = rangedNormalAttackPotency(invocation)
                targets.map { target ->
                    ModifierEffectChange(
                        spec = persistentSpec(invocation, target, potency),
                        modifier = BattleModifier.RangedNormalAttack(
                            damagePercentPerDistance = potency.value,
                        ),
                    )
                }
            }
            311, 312 -> {
                val potency = TypedBattlePotency.flat(
                    kotlin.math.abs(raw.constantParam).coerceAtLeast(1),
                )
                val requiredSkillKind = when (raw.effectParam) {
                    PURSUIT_MAIN_SKILL_FILTER -> SkillKind.PURSUIT
                    else -> null
                }
                targets.map { target ->
                    val spec = persistentSpec(invocation, target, potency)
                    ModifierEffectChange(
                        spec = if (
                            spec.skillKind == SkillKind.UNKNOWN &&
                            spec.rawSkillType in CONTROL_EXTENSION_PASSIVE_SKILL_TYPES
                        ) {
                            spec.copy(skillKind = SkillKind.PASSIVE)
                        } else {
                            spec
                        },
                        modifier = BattleModifier.ControlDurationIncrease(
                            rounds = potency.value,
                            mainSkillOnly = ownedEffectId == 311,
                            requiredSkillKind = requiredSkillKind,
                        ),
                    )
                }
            }
            132 -> targets.map { target ->
                val unlockedSkillId = raw.effectParam.takeIf { it > 0 }
                    ?: context.currentSkillId
                ModifierEffectChange(
                    spec = persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.flat(1),
                    ),
                    modifier = BattleModifier.SkillEnhancementUnlock(
                        skillId = unlockedSkillId,
                    ),
                )
            }
            407 -> {
                if (targets.isEmpty()) {
                    emptyList()
                } else {
                    val absorbPercent = configuredPotency(invocation).value.coerceIn(1, 100)
                    listOf(
                        DamageAbsorptionAccumulatorEffectChange(
                            spec = persistentSpec(
                                invocation,
                                context.source,
                                TypedBattlePotency.percent(absorbPercent),
                            ),
                            protectedTargets = targets,
                            absorbPercent = absorbPercent,
                        ),
                    )
                }
            }
            409 -> {
                if (targets.isEmpty()) emptyList() else listOf(
                    LinkedDamageSharingEffectChange(
                        spec = persistentSpec(
                            invocation,
                            targets.first(),
                            TypedBattlePotency.percent(raw.constantParam),
                        ),
                        members = targets,
                        sharePercentPerAlly = raw.constantParam.coerceIn(1, 100),
                    ),
                )
            }
            149 -> listOf(
                TriggerLastAppliedEffectChange(
                    source = context.source,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    targets = targets,
                    parameters = parameters,
                ),
            )
            150 -> targets.map { target ->
                TriggerSpecifiedEffectChange(
                    source = context.source,
                    target = target,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    triggeredEffectId = raw.effectParam,
                    parameters = parameters,
                )
            }
            200 -> targets.map { target ->
                ActionEffectChange(
                    spec = persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.flat(1),
                    ),
                    kind = ActionEffectKind.DOUBLE_ATTACK,
                )
            }
            199 -> listOf(
                TransformAndCastRandomActiveSkillChange(
                    source = context.source,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    parameters = parameters,
                ),
            )
            82, 83 -> targets.map { target ->
                val potency = TypedBattlePotency.percent(raw.constantParam)
                ModifierEffectChange(
                    spec = persistentSpec(invocation, target, potency),
                    modifier = if (ownedEffectId == 82) {
                        BattleModifier.DamageRateMaximumPercent(potency.value)
                    } else {
                        BattleModifier.DamageRateMinimumPercent(potency.value)
                    },
                )
            }
            404 -> targets.map { target ->
                ApplyBattleEffectChange(
                    persistentSpec(
                        invocation,
                        target,
                        TypedBattlePotency.flat(1),
                    ).copy(availableHit = raw.availableHit.coerceAtLeast(1)),
                )
            }
            408 -> {
                val referenced = referencedDetail(invocation)
                val baseReleasePercent = raw.constantParam.coerceIn(0, 100)
                listOf(
                    DamageReleaseScheduleEffectChange(
                        spec = persistentSpec(
                            invocation,
                            context.source,
                            TypedBattlePotency.percent(baseReleasePercent),
                        ).copy(
                            delayRound = 0,
                            delayHit = 0,
                            startBoundary = EffectStartBoundary.IMMEDIATE,
                        ),
                        target = context.source,
                        referencedDetailId = referenced.detailId,
                        referencedEffectId = referenced.effectId,
                        baseReleasePercent = baseReleasePercent,
                        firstReleaseRound = (
                            context.round + raw.delayRound + 1
                            ).coerceAtLeast(1),
                    ),
                )
            }
            151, 153 -> {
                val referenced = referencedDetail(invocation)
                listOf(
                    TriggerReferencedEffectChange(
                        source = context.source,
                        rootSkillId = context.rootSkillId,
                        skillId = context.currentSkillId,
                        detailId = invocation.rule.detailId,
                        referencedDetailId = referenced.detailId,
                        referencedEffectId = referenced.effectId,
                        selectedTargets = targets,
                        mode = when (ownedEffectId) {
                            153 -> ReferenceEffectMode.ATTRIBUTE_SCALED
                            151 -> if (referenced.effectId in 303..306) {
                                ReferenceEffectMode.TRIGGER_EXISTING
                            } else {
                                ReferenceEffectMode.NORMAL
                            }
                            else -> ReferenceEffectMode.NORMAL
                        },
                        valueOverride = if (ownedEffectId == 153) configuredPotency(invocation) else null,
                        parameters = parameters,
                    ),
                )
            }
            152 -> {
                val referenced = referencedDetail(invocation)
                targets.map { target ->
                    ClearReferencedEffectChange(
                        source = context.source,
                        target = target,
                        rootSkillId = context.rootSkillId,
                        skillId = context.currentSkillId,
                        detailId = invocation.rule.detailId,
                        referencedDetailId = referenced.detailId,
                        referencedEffectId = referenced.effectId,
                        parameters = parameters,
                    )
                }
            }
            313 -> {
                val referenced = referencedDetail(invocation)
                targets.map { target ->
                    ReduceReferencedEffectUseChange(
                        source = context.source,
                        target = target,
                        rootSkillId = context.rootSkillId,
                        skillId = context.currentSkillId,
                        detailId = invocation.rule.detailId,
                        referencedDetailId = referenced.detailId,
                        referencedEffectId = referenced.effectId,
                        amount = raw.constantParam.coerceAtLeast(1),
                        parameters = parameters,
                    )
                }
            }
            171, 181 -> targets.map { target ->
                MetaEffectChange(
                    source = context.source,
                    target = target,
                    rootSkillId = context.rootSkillId,
                    skillId = context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    effectId = ownedEffectId,
                    operation = operation,
                    parameters = parameters,
                )
            }
            else -> error(
                "Missing typed meta effect intent: effect=$ownedEffectId " +
                    "detail=${invocation.rule.detailId}",
            )
        }
        return EffectExecution(changes, emptyList())
    }

    private fun referencedDetail(invocation: EffectInvocation): SkillEffectRule =
        detailResolver(invocation.rule.raw.effectParam)
            ?: throw MissingSkillDetailException(
                invocation.callPath,
                invocation.rule.raw.effectParam,
            )

    private fun runtimeChildSkillIds(invocation: EffectInvocation): List<Int> {
        val configured = invocation.rule.childSkillIds.toList()
        val delta = invocation.executionOverride?.valueDelta ?: return configured
        val rawChildSkillId = invocation.rule.raw.constantParam
        if (rawChildSkillId !in configured) return configured
        val shifted = Math.addExact(rawChildSkillId, delta)
        return configured.map { if (it == rawChildSkillId) shifted else it }
    }

    private fun rangedNormalAttackPotency(
        invocation: EffectInvocation,
    ): TypedBattlePotency.Resolved {
        val skillDetailBase = invocation.context.currentSkillId * 100
        val referencedDamageDetail = (1..99)
            .asSequence()
            .mapNotNull { suffix -> detailResolver(skillDetailBase + suffix) }
            .filter { detail ->
                detail.effectId == 111 &&
                    detail.raw.calcPos == RANGED_DISTANCE_CALC_POSITION &&
                    detail.raw.calcParam == PHYSICAL_DISTANCE_PARAMETER
            }
            .mapNotNull { parameterDetail ->
                detailResolver(parameterDetail.raw.effectParam)
            }
            .singleOrNull { detail -> detail.effectId == RANGED_PHYSICAL_DAMAGE_EFFECT_ID }
            ?: throw MissingSkillDetailException(
                invocation.callPath,
                skillDetailBase + RANGED_PHYSICAL_DAMAGE_DETAIL_SUFFIX,
            )
        val source = sourceHero(invocation)
        val calculated = calculator.effectValue(
            referencedDamageDetail,
            source,
            invocation.rootSkillLevel(source),
        )
        val resolved = calculated as? TypedBattlePotency.Resolved
            ?: throw UnsupportedConfiguredBattleValueException(
                BattleEffectDiagnostic(
                    code = EffectFailureCode.UNSUPPORTED_CONFIGURED_VALUE,
                    skillId = invocation.context.currentSkillId,
                    detailId = referencedDamageDetail.detailId,
                    effectId = referencedDamageDetail.effectId,
                    trigger = invocation.context.trigger,
                    callPath = invocation.callPath,
                    reason = (calculated as TypedBattlePotency.Deferred).diagnostic,
                ),
            )
        return TypedBattlePotency.percent(kotlin.math.abs(resolved.value))
    }

    private fun persistentSpec(
        invocation: EffectInvocation,
        target: BattleHeroRef,
        potency: TypedBattlePotency.Resolved,
    ): PersistentEffectSpec {
        val raw = invocation.rule.raw
        val lifecycle = invocation.lifecycle()
        return PersistentEffectSpec(
            source = invocation.context.source,
            target = target,
            rootSkillId = invocation.context.rootSkillId,
            skillId = invocation.context.currentSkillId,
            skillKind = invocation.rule.skillKind,
            rawSkillType = invocation.rule.rawSkillType,
            detailId = invocation.rule.detailId,
            effectId = invocation.rule.effectId,
            category = com.stzb.battle.core.EffectCategory.fromClientBuffType(
                invocation.rule.effectBuffType,
            ),
            conflict = raw.hideConflict,
            replaceType = invocation.rule.effectReplaceType,
            bindFlag = raw.bindFlag,
            maxStacks = raw.addCountMax + 1,
            delayRound = lifecycle.delayRound,
            delayHit = lifecycle.delayHit,
            availableRounds = lifecycle.availableRounds,
            availableHit = lifecycle.availableHit,
            clearPerHit = lifecycle.clearPerHit,
            startBoundary = if (lifecycle.delayRound > 0 || lifecycle.delayHit > 0) {
                EffectStartBoundary.AFTER_DELAY
            } else {
                EffectStartBoundary.IMMEDIATE
            },
            potency = potency,
        )
    }

    private fun configuredPotency(invocation: EffectInvocation): TypedBattlePotency.Resolved {
        invocation.valueOverride?.let { return invocation.withValueDelta(it) }
        val source = sourceHero(invocation)
        val calculated = calculator.effectValue(
            invocation.rule,
            source,
            invocation.rootSkillLevel(source),
        )
        val resolved = when (calculated) {
            is TypedBattlePotency.Resolved -> calculated
            is TypedBattlePotency.Deferred -> throw UnsupportedConfiguredBattleValueException(
                BattleEffectDiagnostic(
                    code = EffectFailureCode.UNSUPPORTED_CONFIGURED_VALUE,
                    skillId = invocation.context.currentSkillId,
                    detailId = invocation.rule.detailId,
                    effectId = invocation.rule.effectId,
                    trigger = invocation.context.trigger,
                    callPath = invocation.callPath,
                    reason = calculated.diagnostic,
                ),
            )
        }
        return invocation.withValueDelta(resolved)
    }

    private fun defenseIgnorePotency(invocation: EffectInvocation): TypedBattlePotency.Resolved {
        val source = sourceHero(invocation)
        val level = invocation.rootSkillLevel(source)
        val ratio = invocation.rule.raw.initEffectRatio +
            (level - 1) * (100 - invocation.rule.raw.initEffectRatio) / 9.0
        val percent = invocation.rule.raw.constantParam / 1_000.0 * ratio / 100.0
        return TypedBattlePotency.percent(percent.toInt())
    }

    private fun sourceHero(invocation: EffectInvocation): BattleHero {
        val ref = invocation.context.source
        val entry = (if (ref.side == com.stzb.battle.core.Side.ATTACKER) {
            invocation.context.request.attacker
        } else {
            invocation.context.request.defender
        }).heroes.single { it.id == ref.heroId && it.position == ref.position }
        if (SkillBattleViewCapability.LIVE_STATE !in invocation.context.battleView.capabilities) {
            return entry
        }
        val state = requireNotNull(invocation.context.battleView.state(ref)) {
            "Missing live source state for $ref"
        }
        return entry.copy(
            stats = state.stats,
            troops = state.troops,
            maxTroops = state.maxTroops,
            activeStatuses = state.statuses,
            morale = state.morale,
        )
    }

    private companion object {
        val OPERATIONS = mapOf(
            77 to MetaEffectOperation.MARKER,
            79 to MetaEffectOperation.SIMULATED_NORMAL_ATTACK,
            80 to MetaEffectOperation.NORMAL_ATTACK_ALL_IN_RANGE,
            81 to MetaEffectOperation.JOINT_ATTACK,
            82 to MetaEffectOperation.DAMAGE_RATE_MAXIMUM,
            83 to MetaEffectOperation.DAMAGE_RATE_MINIMUM,
            88 to MetaEffectOperation.SHARED_EFFECT_USES,
            111 to MetaEffectOperation.REFERENCED_EXTRA_PARAMETER,
            112 to MetaEffectOperation.REFERENCED_VALUE_CHANGE,
            113 to MetaEffectOperation.MORALE_INCREASE,
            114 to MetaEffectOperation.MORALE_DECREASE,
            118 to MetaEffectOperation.RESISTANCE,
            121 to MetaEffectOperation.COMMAND_EFFECT_IMMUNITY,
            122 to MetaEffectOperation.EXECUTE_BENEFICIAL_CHILD,
            123 to MetaEffectOperation.EXECUTE_HARMFUL_CHILD,
            125 to MetaEffectOperation.BENEFICIAL_PUPPET,
            127 to MetaEffectOperation.DAMAGE_SHARING,
            128 to MetaEffectOperation.RANGED_NORMAL_ATTACK,
            311 to MetaEffectOperation.CONTROL_DURATION_INCREASE,
            312 to MetaEffectOperation.NEXT_CONTROL_DURATION_INCREASE,
            129 to MetaEffectOperation.RETRIGGER_ACTIVE_SKILL,
            130 to MetaEffectOperation.RETRIGGER_PURSUIT_SKILL,
            131 to MetaEffectOperation.SKILL_PROBABILITY_INCREASE,
            132 to MetaEffectOperation.SKILL_ENHANCEMENT_UNLOCK,
            141 to MetaEffectOperation.EFFECT_PROBABILITY_INCREASE,
            149 to MetaEffectOperation.TRIGGER_LAST_APPLIED_EFFECT,
            150 to MetaEffectOperation.TRIGGER_SPECIFIED_EFFECT,
            151 to MetaEffectOperation.TRIGGER_REFERENCED_EFFECT,
            152 to MetaEffectOperation.CLEAR_REFERENCED_EFFECT,
            153 to MetaEffectOperation.TRIGGER_ATTRIBUTE_SCALED_EFFECT,
            161 to MetaEffectOperation.IGNORE_ENEMY_ATTRIBUTE,
            171 to MetaEffectOperation.SKILL_RANGE_INCREASE,
            181 to MetaEffectOperation.SKILL_RANGE_DECREASE,
            190 to MetaEffectOperation.NORMAL_TARGET_IMMUNITY,
            193 to MetaEffectOperation.ACTIVE_TARGET_IMMUNITY,
            194 to MetaEffectOperation.PURSUIT_TARGET_IMMUNITY,
            199 to MetaEffectOperation.TRANSFORMATION,
            200 to MetaEffectOperation.COMBO,
            210 to MetaEffectOperation.EXECUTE_NAMED_CHILD,
            220 to MetaEffectOperation.SIEGE_IMMUNITY,
            231 to MetaEffectOperation.SKILL_PROBABILITY_DECREASE,
            261 to MetaEffectOperation.SPECIAL_DAMAGE_TAKEN_INCREASE,
            271 to MetaEffectOperation.RECOVERY_DEALT_INCREASE,
            281 to MetaEffectOperation.RECOVERY_TAKEN_INCREASE,
            313 to MetaEffectOperation.REDUCE_REFERENCED_EFFECT_USES,
            404 to MetaEffectOperation.EXTRA_CONTROL_TARGET,
            407 to MetaEffectOperation.DAMAGE_ABSORPTION,
            408 to MetaEffectOperation.RELEASE_DAMAGE,
            409 to MetaEffectOperation.LINKED_HEARTS,
            553 to MetaEffectOperation.COUNTERATTACK_IMMUNITY,
        )

        const val RANGED_DISTANCE_CALC_POSITION = 991
        const val PHYSICAL_DISTANCE_PARAMETER = 1
        const val RANGED_PHYSICAL_DAMAGE_EFFECT_ID = 531
        const val RANGED_PHYSICAL_DAMAGE_DETAIL_SUFFIX = 3
        const val PURSUIT_MAIN_SKILL_FILTER = 1000001004
        val CONTROL_EXTENSION_PASSIVE_SKILL_TYPES = setOf(16, 17, 19)
    }
}
