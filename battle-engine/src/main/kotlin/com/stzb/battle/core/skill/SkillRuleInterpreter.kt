package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleModifier
import com.stzb.battle.core.SkillKind
import java.util.Collections

data class SkillTriggered(
    val round: Int,
    val source: BattleHeroRef,
    override val rootSkillId: Int,
    val skillId: Int,
    val trigger: BattleTrigger,
) : SkillExecutionEvent

sealed interface SkillExecutionEvent {
    val rootSkillId: Int
}

data class BattleOutputEvent(
    override val rootSkillId: Int,
    val event: BattleEvent,
) : SkillExecutionEvent

data class SkillExecutionDiagnostic(
    val code: String,
    val skillId: Int,
    val detailId: Int?,
    val effectId: Int?,
    val trigger: BattleTrigger,
    val fullPath: List<SkillExecutionFrame>,
    private val skillDependencyPath: List<Int> = fullPath.map(SkillExecutionFrame::skillId),
    val reason: String,
) {
    val dependencyPath: List<Int>
        get() = skillDependencyPath
}

data class SkillExecutionResult(
    val stateChanges: List<BattleStateChange>,
    val events: List<SkillExecutionEvent>,
    val executedSkillIds: List<Int>,
    val diagnostics: List<SkillExecutionDiagnostic>,
    val timingDues: List<SkillTimingDue>,
) {
    operator fun plus(other: SkillExecutionResult): SkillExecutionResult {
        if (this === EMPTY) return other
        if (other === EMPTY) return this
        return immutable(
            stateChanges + other.stateChanges,
            events + other.events,
            executedSkillIds + other.executedSkillIds,
            diagnostics + other.diagnostics,
            timingDues + other.timingDues,
        )
    }

    companion object {
        val EMPTY: SkillExecutionResult =
            immutable(emptyList(), emptyList(), emptyList(), emptyList())

        internal fun immutable(
            stateChanges: Collection<BattleStateChange>,
            events: Collection<SkillExecutionEvent>,
            executedSkillIds: Collection<Int>,
            diagnostics: Collection<SkillExecutionDiagnostic>,
            timingDues: Collection<SkillTimingDue> = emptyList(),
        ): SkillExecutionResult =
            SkillExecutionResult(
                Collections.unmodifiableList(ArrayList(stateChanges)),
                Collections.unmodifiableList(ArrayList(events)),
                Collections.unmodifiableList(ArrayList(executedSkillIds)),
                Collections.unmodifiableList(ArrayList(diagnostics)),
                Collections.unmodifiableList(ArrayList(timingDues)),
            )
    }
}

fun interface PendingSkillConditionInterpreter {
    fun matches(
        rule: SkillEffectRule,
        trigger: BattleTrigger,
        context: SkillBattleContext,
    ): Boolean
}

class StrictPendingConditionInterpreter : PendingSkillConditionInterpreter {
    override fun matches(
        rule: SkillEffectRule,
        trigger: BattleTrigger,
        context: SkillBattleContext,
    ): Boolean {
        val raw = rule.raw
        val pending = listOf(
            "cast_condition" to raw.castCondition,
            "precondition" to raw.precondition,
            "condition" to raw.condition,
        ).filter { it.second != 0 }
        if (pending.isNotEmpty()) {
            throw UnsupportedPendingSkillConditionException(
                "Pending condition semantics: skill=${context.currentSkillId} " +
                    "detail=${rule.detailId} trigger=$trigger " +
                    pending.joinToString { "${it.first}=${it.second}" },
            )
        }
        return true
    }
}

class UnsupportedPendingSkillConditionException(message: String) :
    IllegalStateException(message)

class MissingSkillRuleException(
    val dependencyPath: List<Int>,
) : IllegalArgumentException(
    "Missing skill rule: ${dependencyPath.joinToString(" -> ")}",
)

class MissingSkillDetailException(
    val dependencyPath: List<Int>,
    val detailId: Int,
) : IllegalArgumentException(
    "Missing referenced detail=$detailId: ${dependencyPath.joinToString(" -> ")}",
)

class SkillRecursionException(
    val dependencyPath: List<Int>,
    reason: String,
) : IllegalStateException(
    "$reason: ${dependencyPath.joinToString(" -> ")}",
)

class SkillDetailRecursionException(
    val fullPath: List<SkillExecutionFrame>,
    reason: String,
) : IllegalStateException(
    "$reason: ${fullPath.joinToString(" -> ")}",
)

private enum class InterpreterFailureMode {
    STRICT,
    SAFE,
}

private typealias SkillExecutionStepSink = (SkillExecutionResult) -> Unit

private data class TargetSelectionSignature(
    val attackType: Int,
    val selectSkillParam: Int,
    val targetType: Int,
    val selectType: Int,
    val targetCountry: Int,
    val selectAttribute: Int,
    val customSelectFlag: Int,
    val attackMaximum: Int,
    val castCondition: Int,
    val precondition: Int,
    val condition: Int,
    val selectFlag: Int,
    val bindFlag: Int,
    val skillHitRange: Int?,
)

class SkillRuleInterpreter private constructor(
    private val graph: SkillRuleGraph,
    private val registry: BattleEffectRegistry,
    private val conditionInterpreter: PendingSkillConditionInterpreter,
    private val failureMode: InterpreterFailureMode,
    private val diagnosticSink: (SkillExecutionDiagnostic) -> Unit,
) {
    private val referencedTemplateDetailIds: Set<Int> = graph.details
        .asSequence()
        .filter { it.effectId in REFERENCED_TEMPLATE_EFFECT_IDS }
        .map { it.raw.effectParam }
        .filter { it > 0 }
        .toSet()
    private val skillEnhancementUnlockIds: Set<Int> = graph.skillEnhancementUnlockIds
    private val targetSelector = SkillTargetSelector()

    constructor(
        graph: SkillRuleGraph,
        registry: BattleEffectRegistry,
        conditionInterpreter: PendingSkillConditionInterpreter = SkillConditionInterpreter(graph),
    ) : this(
        graph,
        registry,
        conditionInterpreter,
        InterpreterFailureMode.STRICT,
        {},
    )

    fun execute(
        skillId: Int,
        trigger: BattleTrigger,
        context: SkillBattleContext,
    ): SkillExecutionResult =
        executeSkill(skillId, trigger, context, ChildProbabilityOwnership.CONFIGURED_CHILD)

    internal fun probabilitySucceeds(
        skillId: Int,
        trigger: BattleTrigger,
        context: SkillBattleContext,
    ): Boolean {
        val rule = graph.rule(skillId) ?: throw MissingSkillRuleException(
            context.runtime.currentCallPath() + skillId,
        )
        if (!triggerMatches(rule.kind, trigger)) return false
        return rollProbability(rule, context.copy(currentSkillId = skillId, trigger = trigger))
    }

    internal fun executeAccepted(
        snapshot: SkillExecutionSnapshot,
        context: SkillBattleContext,
    ): SkillExecutionResult =
        executeSkill(
            skillId = snapshot.skillId,
            trigger = snapshot.trigger,
            parentContext = context.copy(
                source = snapshot.source,
                rootSkillId = snapshot.rootSkillId,
                currentSkillId = snapshot.skillId,
                trigger = snapshot.trigger,
            ),
            probabilityOwnership = ChildProbabilityOwnership.FORCED_SUCCESS,
            recordSuccessfulExecution = false,
            rootPreselectedTargets = snapshot.lockedTargets,
        )

    internal fun executeDetailForEngine(
        detail: SkillEffectRule,
        context: SkillBattleContext,
        preselectedTargets: List<BattleHeroRef>? = null,
        valueOverride: TypedBattlePotency.Resolved? = null,
        probabilityAlreadyAccepted: Boolean = false,
        executionOverride: ReferencedDetailExecutionOverride? = null,
    ): SkillExecutionResult =
        if (probabilityAlreadyAccepted || detailProbabilitySucceeds(detail, context)) {
            executeDetail(detail, context, preselectedTargets, valueOverride, executionOverride)
        } else {
            SkillExecutionResult.EMPTY
        }

    internal fun retriggerSkillForEngine(
        skillId: Int,
        trigger: BattleTrigger,
        context: SkillBattleContext,
    ): SkillExecutionResult =
        executeSkill(
            skillId = skillId,
            trigger = trigger,
            parentContext = context,
            probabilityOwnership = ChildProbabilityOwnership.FORCED_SUCCESS,
        )

    internal fun executeDetailStreamingForEngine(
        detail: SkillEffectRule,
        context: SkillBattleContext,
        onStep: SkillExecutionStepSink,
    ): SkillExecutionResult =
        if (detailProbabilitySucceeds(detail, context)) {
            executeDetail(detail, context, stepSink = onStep)
        } else {
            SkillExecutionResult.EMPTY
        }

    internal fun detailProbabilitySucceedsForEngine(
        detail: SkillEffectRule,
        context: SkillBattleContext,
        configuredProbability: Int? = null,
    ): Boolean = detailProbabilitySucceeds(detail, context, configuredProbability)

    private fun executeSkill(
        skillId: Int,
        trigger: BattleTrigger,
        parentContext: SkillBattleContext,
        probabilityOwnership: ChildProbabilityOwnership,
        recordSuccessfulExecution: Boolean = true,
        rootPreselectedTargets: List<BattleHeroRef>? = null,
        stepSink: SkillExecutionStepSink? = null,
    ): SkillExecutionResult {
        val attemptedPath = parentContext.runtime.currentCallPath() + skillId
        val rule = graph.rule(skillId) ?: throw MissingSkillRuleException(attemptedPath)
        if (!triggerMatches(rule.kind, trigger)) return SkillExecutionResult.EMPTY
        try {
            parentContext.runtime.enter(skillId)
        } catch (error: IllegalStateException) {
            throw SkillRecursionException(attemptedPath, error.message ?: "Skill recursion failure")
        }
        try {
            val context = parentContext.copy(
                rootSkillId = if (parentContext.runtime.currentCallPath().size == 1) {
                    parentContext.rootSkillId.takeIf { it != 0 } ?: skillId
                } else {
                    parentContext.rootSkillId
                },
                currentSkillId = skillId,
                trigger = trigger,
            )
            if (probabilityOwnership == ChildProbabilityOwnership.CONFIGURED_CHILD &&
                !rollProbability(rule, context)
            ) {
                return SkillExecutionResult.EMPTY
            }
            if (recordSuccessfulExecution) {
                parentContext.runtime.recordSuccessfulExecution(context.source, trigger, skillId)
            }

            val preparationCallDepth = parentContext.runtime.currentCallPath().size
            val exposeTriggeredEvent =
                trigger !in setOf(BattleTrigger.BATTLE_PASSIVE, BattleTrigger.BATTLE_COMMAND) ||
                    preparationCallDepth <= 2
            var result = SkillExecutionResult.immutable(
                stateChanges = emptyList(),
                events = if (exposeTriggeredEvent) listOf(
                    SkillTriggered(
                        round = context.round,
                        source = context.source,
                        rootSkillId = context.rootSkillId,
                        skillId = skillId,
                        trigger = trigger,
                    ),
                ) else emptyList(),
                executedSkillIds = listOf(skillId),
                diagnostics = emptyList(),
            )
            stepSink?.invoke(result)
            val detailOverrides = mutableMapOf<Int, ReferencedDetailExecutionOverride>()
            val configuredExecutableDetails =
                rule.details.filterNot { it.detailId in referencedTemplateDetailIds }
            val executableDetails = selectExecutableDetails(
                skillId,
                configuredExecutableDetails,
                context,
            )
            val lockableTargetSignatures =
                if (skillId == HEINEI_SHIZE_SKILL_ID) {
                    emptySet()
                } else {
                    executableDetails
                        .groupingBy { it.targetSelectionSignature() }
                        .eachCount()
                        .filterValues { it > 1 }
                        .keys
                }
            val lockedTargets =
                mutableMapOf<TargetSelectionSignature, List<BattleHeroRef>>()
            executableDetails.forEach { detail ->
                val branch = executeBranch(
                    detail,
                    context,
                    rootPreselectedTargets,
                    detailOverrides.remove(detail.detailId),
                    stepSink,
                    lockableTargetSignatures,
                    lockedTargets,
                )
                result += branch
                captureExtraParameters(branch, detailOverrides)
            }
            return result
        } finally {
            parentContext.runtime.exit(skillId)
        }
    }

    private fun selectExecutableDetails(
        skillId: Int,
        details: List<SkillEffectRule>,
        context: SkillBattleContext,
    ): List<SkillEffectRule> {
        if (skillId == QIZUOGUIMOU_SKILL_ID) {
            val controlDetails = details.filter {
                it.detailId in QIZUOGUIMOU_CONTROL_DETAIL_IDS
            }
            require(controlDetails.size == QIZUOGUIMOU_CONTROL_DETAIL_IDS.size) {
                "Unexpected skill 200692 control details=${controlDetails.map { it.detailId }}"
            }
            return details.filterNot(controlDetails::contains) +
                controlDetails[context.random.nextInt(controlDetails.size)]
        }
        if (skillId == HEINEI_SHIZE_SKILL_ID) {
            val pools = details.groupBy { it.raw.selectFlag }
            require(pools.keys == HEINEI_SHIZE_POOL_IDS) {
                "Unexpected skill 200847 child pools=${pools.keys.sorted()}"
            }
            return pools.toSortedMap().values.map { pool ->
                pool[context.random.nextInt(pool.size)]
            }
        }
        return details
    }

    private fun executeBranch(
        detail: SkillEffectRule,
        context: SkillBattleContext,
        preselectedTargets: List<BattleHeroRef>? = null,
        executionOverride: ReferencedDetailExecutionOverride? = null,
        stepSink: SkillExecutionStepSink? = null,
        lockableTargetSignatures: Set<TargetSelectionSignature> = emptySet(),
        lockedTargets: MutableMap<TargetSelectionSignature, List<BattleHeroRef>>? = null,
    ): SkillExecutionResult =
        try {
            if (!isSkillEnhancementUnlocked(detail, context) ||
                !conditionInterpreter.matches(detail, context.trigger, context) ||
                detail.effectId !in PER_ROUND_PREPARED_EFFECT_IDS &&
                !usesPerTargetProbability(detail) &&
                !detailProbabilitySucceeds(detail, context)
            ) {
                SkillExecutionResult.EMPTY
            } else {
                val signature = detail.targetSelectionSignature()
                val selectedTargets =
                    preselectedTargets ?: executionOverride?.targetOverride
                        ?: if (
                            lockedTargets != null &&
                            signature in lockableTargetSignatures
                        ) {
                            lockedTargets.getOrPut(signature) {
                                targetSelector.compile(detail).select(context)
                            }
                        } else {
                            null
                        }
                executeDetail(
                    detail,
                    context,
                    selectedTargets,
                    executionOverride = executionOverride,
                    stepSink = stepSink,
                )
            }
        } catch (error: Exception) {
            if (failureMode == InterpreterFailureMode.STRICT || !isRecoverable(error)) throw error
            diagnosticResult(detail, context, error)
        }

    private fun isSkillEnhancementUnlocked(
        detail: SkillEffectRule,
        context: SkillBattleContext,
    ): Boolean {
        val requiredSkillId = detail.raw.lockFlag
        if (requiredSkillId !in skillEnhancementUnlockIds) return true
        val source = context.source
        val entry = (if (source.side == com.stzb.battle.core.Side.ATTACKER) {
            context.request.attacker
        } else {
            context.request.defender
        }).heroes.single { hero ->
            hero.position == source.position && hero.id == source.heroId
        }
        val modifiers = if (
            SkillBattleViewCapability.LIVE_STATE in context.battleView.capabilities
        ) {
            context.battleView.state(source)?.modifiers.orEmpty()
        } else {
            entry.modifiers
        }
        return modifiers.any { modifier ->
            modifier == BattleModifier.SkillEnhancementUnlock(requiredSkillId)
        }
    }

    private fun executeDetail(
        detail: SkillEffectRule,
        context: SkillBattleContext,
        preselectedTargets: List<BattleHeroRef>? = null,
        valueOverride: TypedBattlePotency.Resolved? = null,
        executionOverride: ReferencedDetailExecutionOverride? = null,
        stepSink: SkillExecutionStepSink? = null,
    ): SkillExecutionResult {
        val ownerSkillId = detail.detailId / 100
        val frame = SkillExecutionFrame(ownerSkillId, detail.detailId)
        val attempted = context.runtime.currentDetailPath() + frame
        try {
            context.runtime.enterDetail(frame)
        } catch (error: IllegalStateException) {
            throw SkillDetailRecursionException(
                attempted,
                error.message ?: "Skill detail recursion failure",
            )
        }
        try {
            val executionContext = context.copy(currentSkillId = ownerSkillId)
            val runtimeDelta = context.runtime.referencedValueDelta(
                context.source,
                context.rootSkillId,
                detail.detailId,
            )
            val effectiveOverride = when {
                runtimeDelta == 0 -> executionOverride
                executionOverride == null -> ReferencedDetailExecutionOverride(
                    referencedDetailId = detail.detailId,
                    valueDelta = runtimeDelta,
                )
                else -> executionOverride.copy(
                    valueDelta = (executionOverride.valueDelta ?: 0) + runtimeDelta,
                )
            }
            val execution = registry.execute(
                rule = detail,
                context = executionContext,
                preselectedTargets = preselectedTargets,
                valueOverride = valueOverride,
                executionOverride = effectiveOverride,
            )
            execution.stateChanges.forEach { change ->
                when (change) {
                    is MarkerEffectChange -> context.runtime.recordMarker(
                        target = change.target,
                        detailId = change.detailId,
                        value = change.marker,
                        appliedRound = context.round,
                        durationRounds = change.parameters.availableRounds,
                        rootSkillId = change.rootSkillId,
                        source = change.source,
                    )
                    is ClearReferencedEffectChange ->
                        if (change.referencedEffectId == 77) {
                            context.runtime.removeMarker(
                                change.target,
                                change.referencedDetailId,
                            )
                        }
                    is ReferencedValueChange ->
                        if (change.parameters.calcPosition == 32) {
                            context.runtime.scheduleReferencedValueProgression(
                                source = change.source,
                                rootSkillId = change.rootSkillId,
                                detailId = change.referencedDetailId,
                                delta = change.delta,
                                appliedRound = context.round,
                                delayRound = change.parameters.delayRound,
                                availableRounds = change.parameters.availableRounds,
                            )
                        } else {
                            context.runtime.addReferencedValueDelta(
                                source = change.source,
                                rootSkillId = change.rootSkillId,
                                detailId = change.referencedDetailId,
                                delta = change.delta,
                            )
                        }
                    else -> Unit
                }
            }
            var result = SkillExecutionResult.immutable(
                stateChanges = execution.stateChanges,
                events = execution.events.map { BattleOutputEvent(context.rootSkillId, it) },
                executedSkillIds = emptyList(),
                diagnostics = emptyList(),
            )
            stepSink?.invoke(result)
            execution.stateChanges.forEach { change ->
                result += when (change) {
                    is ExecuteChildSkillChange -> executeChildren(change, executionContext, stepSink)
                    is RetriggerSkillChange -> retrigger(change, executionContext, stepSink)
                    is TriggerReferencedEffectChange ->
                        triggerReferencedEffect(change, executionContext, stepSink)
                    is TransformAndCastRandomActiveSkillChange ->
                        transformAndCastRandomActiveSkill(change, executionContext, stepSink)
                    else -> SkillExecutionResult.EMPTY
                }
            }
            return result
        } finally {
            context.runtime.exitDetail(frame)
        }
    }

    private fun SkillEffectRule.targetSelectionSignature(): TargetSelectionSignature =
        TargetSelectionSignature(
            attackType = raw.attackType,
            selectSkillParam = raw.selectSkillParam,
            targetType = raw.targetType,
            selectType = raw.selectType,
            targetCountry = raw.targetCountry,
            selectAttribute = raw.selectAttri,
            customSelectFlag = raw.customSelectFlag,
            attackMaximum = raw.attackMax,
            castCondition = raw.castCondition,
            precondition = raw.precondition,
            condition = raw.condition,
            selectFlag = raw.selectFlag,
            bindFlag = raw.bindFlag,
            skillHitRange = skillHitRange,
        )

    private fun executeChildren(
        change: ExecuteChildSkillChange,
        context: SkillBattleContext,
        stepSink: SkillExecutionStepSink? = null,
    ): SkillExecutionResult {
        fun executeChild(childSkillId: Int): SkillExecutionResult {
            val child = graph.rule(childSkillId) ?: throw MissingSkillRuleException(
                context.runtime.currentCallPath() + childSkillId,
            )
            return if (change.valueOverride == null && change.inheritedPreselectedTargets == null) {
                executeSkill(
                    skillId = childSkillId,
                    trigger = triggerFor(child.kind),
                    parentContext = context,
                    probabilityOwnership = change.probabilityOwnership,
                    stepSink = stepSink,
                )
            } else {
                executeChildWithOverrides(child, change, context, stepSink)
            }
        }

        if (change.detailId in FENJI_PER_TARGET_CHILD_DETAILS) {
            val detail = graph.details.single { it.detailId == change.detailId }
            return change.selectedTargets.fold(SkillExecutionResult.EMPTY) { aggregate, _ ->
                if (detailProbabilitySucceeds(detail, context)) {
                    change.childSkillIds.fold(aggregate) { childAggregate, childSkillId ->
                        childAggregate + executeChild(childSkillId)
                    }
                } else {
                    aggregate
                }
            }
        }
        return change.childSkillIds.fold(SkillExecutionResult.EMPTY) { aggregate, childSkillId ->
            aggregate + executeChild(childSkillId)
        }
    }

    private fun executeChildWithOverrides(
        child: SkillRule,
        change: ExecuteChildSkillChange,
        parentContext: SkillBattleContext,
        stepSink: SkillExecutionStepSink? = null,
    ): SkillExecutionResult {
        val trigger = triggerFor(child.kind)
        val attemptedPath = parentContext.runtime.currentCallPath() + child.skillId
        try {
            parentContext.runtime.enter(child.skillId)
        } catch (error: IllegalStateException) {
            throw SkillRecursionException(attemptedPath, error.message ?: "Skill recursion failure")
        }
        try {
            val context = parentContext.copy(
                currentSkillId = child.skillId,
                trigger = trigger,
            )
            if (change.probabilityOwnership == ChildProbabilityOwnership.CONFIGURED_CHILD &&
                !rollProbability(child, context)
            ) {
                return SkillExecutionResult.EMPTY
            }
            parentContext.runtime.recordSuccessfulExecution(context.source, trigger, child.skillId)
            var result = SkillExecutionResult.immutable(
                stateChanges = emptyList(),
                events = listOf(
                    SkillTriggered(
                        round = context.round,
                        source = context.source,
                        rootSkillId = context.rootSkillId,
                        skillId = child.skillId,
                        trigger = trigger,
                    ),
                ),
                executedSkillIds = listOf(child.skillId),
                diagnostics = emptyList(),
            )
            stepSink?.invoke(result)
            val detailOverrides = mutableMapOf<Int, ReferencedDetailExecutionOverride>()
            child.details.filterNot { it.detailId in referencedTemplateDetailIds }.forEach { detail ->
                val branch = try {
                    if (conditionInterpreter.matches(detail, trigger, context)) {
                        executeDetail(
                            detail = detail,
                            context = context,
                            preselectedTargets = change.inheritedPreselectedTargets
                                ?.takeIf {
                                    detail.raw.attackType in INHERITED_TARGET_ATTACK_TYPES ||
                                        change.detailId == SHESHEN_DAMAGE_REFERENCE_DETAIL_ID &&
                                        detail.detailId == SHESHEN_DAMAGE_DETAIL_ID
                                },
                            valueOverride = change.valueOverride,
                            executionOverride = detailOverrides.remove(detail.detailId),
                            stepSink = stepSink,
                        )
                    } else {
                        SkillExecutionResult.EMPTY
                    }
                } catch (error: Exception) {
                    if (failureMode == InterpreterFailureMode.STRICT || !isRecoverable(error)) throw error
                    diagnosticResult(detail, context, error)
                }
                result += branch
                captureExtraParameters(branch, detailOverrides)
            }
            return result
        } finally {
            parentContext.runtime.exit(child.skillId)
        }
    }

    private fun retrigger(
        change: RetriggerSkillChange,
        context: SkillBattleContext,
        stepSink: SkillExecutionStepSink? = null,
    ): SkillExecutionResult {
        val maximum = change.maximumExecutions
        var result = SkillExecutionResult.EMPTY
        change.selectedTargets.forEach { target ->
            val targetHero = requestHero(context, target)
            targetHero.skillIds
                .asSequence()
                .filter { graph.rule(it)?.kind == change.skillKind }
                .forEach { skillId ->
                    val trigger = triggerFor(change.skillKind)
                    if (maximum == null || context.runtime.count(target, trigger, skillId) < maximum) {
                        result += executeSkill(
                            skillId = skillId,
                            trigger = trigger,
                            parentContext = context.copy(
                                source = target,
                                effectValueScalePercent = change.effectValueScalePercent,
                            ),
                            probabilityOwnership = change.probabilityOwnership,
                            stepSink = stepSink,
                        )
                    }
                }
        }
        return result
    }

    private fun captureExtraParameters(
        result: SkillExecutionResult,
        overrides: MutableMap<Int, ReferencedDetailExecutionOverride>,
    ) {
        result.stateChanges.filterIsInstance<ReferencedExtraParameterChange>().forEach { change ->
            val previous = overrides[change.referencedDetailId]
            overrides[change.referencedDetailId] = ReferencedDetailExecutionOverride(
                referencedDetailId = change.referencedDetailId,
                valueDelta = previous?.valueDelta,
                valueReplacement = previous?.valueReplacement,
                extraParameters = previous?.extraParameters.orEmpty() +
                    (change.calcPosition to change.value),
                targetOverride = previous?.targetOverride,
                lifecycleOverride = previous?.lifecycleOverride,
            )
        }
    }

    private fun triggerReferencedEffect(
        change: TriggerReferencedEffectChange,
        context: SkillBattleContext,
        stepSink: SkillExecutionStepSink? = null,
    ): SkillExecutionResult {
        val detail = graph.details.singleOrNull { it.detailId == change.referencedDetailId }
            ?: throw MissingSkillDetailException(
                context.runtime.currentCallPath(),
                change.referencedDetailId,
            )
        if (!conditionInterpreter.matches(detail, context.trigger, context)) {
            return SkillExecutionResult.EMPTY
        }
        if (!change.probabilityAlreadyAccepted && !detailProbabilitySucceeds(detail, context)) {
            return SkillExecutionResult.EMPTY
        }
        if (change.mode == ReferenceEffectMode.TRIGGER_EXISTING) {
            return SkillExecutionResult.immutable(
                stateChanges = change.selectedTargets.map { target ->
                    TriggerSpecifiedEffectChange(
                        source = change.source,
                        target = target,
                        rootSkillId = change.rootSkillId,
                        skillId = change.skillId,
                        detailId = change.detailId,
                        triggeredEffectId = change.referencedEffectId,
                        parameters = change.parameters,
                        triggeredSource = change.source,
                        triggeredDetailId = change.referencedDetailId,
                    )
                },
                events = emptyList(),
                executedSkillIds = emptyList(),
                diagnostics = emptyList(),
            )
        }
        return executeDetail(
            detail = detail,
            context = context,
            preselectedTargets =
                if (change.detailId in FENJI_LINKED_REFERENCE_DETAILS) {
                    null
                } else {
                    change.selectedTargets
                },
            valueOverride = change.valueOverride,
            executionOverride = change.executionOverride,
            stepSink = stepSink,
        )
    }

    private fun transformAndCastRandomActiveSkill(
        change: TransformAndCastRandomActiveSkillChange,
        context: SkillBattleContext,
        stepSink: SkillExecutionStepSink? = null,
    ): SkillExecutionResult {
        val sourceSkills = requestHero(context, change.source).skillIds.toSet()
        val candidates = context.battleView.heroes()
            .asSequence()
            .filter { ref ->
                if (SkillBattleViewCapability.LIVE_STATE in context.battleView.capabilities) {
                    (context.battleView.state(ref)?.troops ?: 0) > 0
                } else {
                    requestHero(context, ref).troops > 0
                }
            }
            .flatMap { ref -> requestHero(context, ref).skillIds.asSequence() }
            .filterNot(sourceSkills::contains)
            .filter { skillId ->
                skillId != change.rootSkillId && graph.rule(skillId)?.kind == SkillKind.ACTIVE
            }
            .distinct()
            .sorted()
            .toList()
        if (candidates.isEmpty()) return SkillExecutionResult.EMPTY

        val selectedSkillId = candidates[context.random.nextInt(candidates.size)]
        return executeSkill(
            skillId = selectedSkillId,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            parentContext = context.copy(
                source = change.source,
                rootSkillId = change.rootSkillId,
                currentSkillId = selectedSkillId,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
            probabilityOwnership = ChildProbabilityOwnership.FORCED_SUCCESS,
            stepSink = stepSink,
        )
    }

    private fun usesPerTargetProbability(detail: SkillEffectRule): Boolean =
        detail.detailId in FENJI_PER_TARGET_CHILD_DETAILS

    private fun diagnosticResult(
        detail: SkillEffectRule,
        context: SkillBattleContext,
        error: Exception,
    ): SkillExecutionResult {
        val fullPath = when (error) {
            is SkillDetailRecursionException -> error.fullPath
            else -> if (context.runtime.currentDetailPath().isEmpty()) {
                listOf(SkillExecutionFrame(context.currentSkillId, detail.detailId))
            } else {
                context.runtime.currentDetailPath()
            }
        }
        val diagnostic = SkillExecutionDiagnostic(
            code = when (error) {
                is MissingSkillDetailException -> "MISSING_REFERENCED_DETAIL"
                is MissingSkillRuleException -> "MISSING_CHILD_SKILL"
                is SkillRecursionException -> "SKILL_RECURSION"
                is SkillDetailRecursionException -> "DETAIL_RECURSION"
                is UnsupportedPendingSkillConditionException -> "UNSUPPORTED_CONDITION"
                is UnsupportedSkillRuleException -> error.diagnostic.code.name
                is UnsupportedConfiguredBattleValueException -> error.diagnostic.code.name
                else -> "INVALID_RULE"
            },
            skillId = context.currentSkillId,
            detailId = detail.detailId,
            effectId = detail.effectId,
            trigger = context.trigger,
            fullPath = Collections.unmodifiableList(ArrayList(fullPath)),
            skillDependencyPath = Collections.unmodifiableList(
                ArrayList(
                    when (error) {
                        is MissingSkillRuleException -> error.dependencyPath
                        is SkillRecursionException -> error.dependencyPath
                        else -> fullPath.map(SkillExecutionFrame::skillId)
                    },
                ),
            ),
            reason = error.message.orEmpty(),
        )
        logSafely(diagnostic)
        return SkillExecutionResult.immutable(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(diagnostic),
        )
    }

    private fun isRecoverable(error: Exception): Boolean =
        error is MissingSkillDetailException ||
            error is MissingSkillRuleException ||
            error is SkillRecursionException ||
            error is SkillDetailRecursionException ||
            error is UnsupportedPendingSkillConditionException ||
            error is UnsupportedSkillRuleException ||
            error is UnsupportedConfiguredBattleValueException ||
            error is IllegalArgumentException ||
            error is IllegalStateException

    private fun logSafely(diagnostic: SkillExecutionDiagnostic) {
        try {
            diagnosticSink(diagnostic)
        } catch (_: Exception) {
            // Safe-mode diagnostics never replace branch execution.
        }
    }

    private fun rollProbability(
        rule: SkillRule,
        context: SkillBattleContext,
    ): Boolean {
        val source = requestHero(context, context.source)
        val matchingModifiers = liveModifiers(context, source)
            .filterIsInstance<BattleModifier.SkillProbabilityPercent>()
            .filter { it.skillId == null || it.skillId == rule.skillId }
            .filter { it.skillIds.isEmpty() || rule.skillId in it.skillIds }
            .filter { it.skillKind == null || it.skillKind == rule.kind }
        val modifier = matchingModifiers.sumOf { it.percent } +
            roundMainSkillProbabilityModifier(context, source, rule.skillId)
        if (matchingModifiers.isNotEmpty()) {
            context.skillProbabilityUses.consume(
                context.source,
                rule.skillId,
                rule.kind,
            )
        }
        val probability = moraleAdjustedProbability(
            configured = rule.probability + modifier,
            source = source,
            context = context,
        )
        return context.random.nextInt(100) < probability
    }

    private fun detailProbabilitySucceeds(
        detail: SkillEffectRule,
        context: SkillBattleContext,
        configuredProbability: Int? = null,
    ): Boolean {
        if (
            detail.effectId == 81 ||
            (
                detail.effectId == 401 &&
                    detail.raw.calcPos == EMERGENCY_RECOVERY_CALC_POSITION &&
                    context.trigger == BattleTrigger.BATTLE_COMMAND
                )
        ) {
            return true
        }
        val source = requestHero(context, context.source)
        val level = EffectInvocation(
            rule = detail,
            context = context,
            callPath = context.runtime.currentCallPath(),
        ).rootSkillLevel(source)
        val configured = configuredProbability?.coerceIn(0, 100) ?: (
            detail.raw.probabilityInit +
                (level - 1) * (detail.raw.probabilityMax - detail.raw.probabilityInit) / 9.0
            ).toInt().coerceIn(0, 100)
        val rootRule = graph.rule(context.rootSkillId)
        val mainSkillModifier = if (
            rootRule?.kind !in setOf(SkillKind.ACTIVE, SkillKind.PURSUIT)
        ) {
            roundMainSkillProbabilityModifier(context, source, context.rootSkillId)
        } else {
            0
        }
        val configuredWithMainSkill =
            (configured + mainSkillModifier).coerceIn(0, 100)
        if (configuredWithMainSkill >= 100) return true
        val moraleAdjusted = moraleAdjustedProbability(
            configured = configuredWithMainSkill,
            source = source,
            context = context,
        )
        val modifier = liveModifiers(context, source)
            .filterIsInstance<BattleModifier.EffectProbabilityPercent>()
            .filter { it.detailId == detail.detailId }
            .sumOf { it.percent }
        return context.random.nextInt(100) < (moraleAdjusted + modifier).coerceIn(0, 100)
    }

    private fun roundMainSkillProbabilityModifier(
        context: SkillBattleContext,
        source: BattleHero,
        skillId: Int,
    ): Int =
        liveModifiers(context, source)
            .filterIsInstance<BattleModifier.RoundMainSkillProbabilityPercent>()
            .filter { modifier ->
                modifier.skillId == skillId &&
                    context.round in modifier.rounds &&
                    skillTreeContainsEffect(skillId, modifier.requiredEffectId)
            }
            .sumOf(BattleModifier.RoundMainSkillProbabilityPercent::percent)

    private fun skillTreeContainsEffect(
        skillId: Int,
        effectId: Int,
        visited: MutableSet<Int> = linkedSetOf(),
    ): Boolean {
        if (!visited.add(skillId)) return false
        val rule = graph.rule(skillId) ?: return false
        return rule.details.any { detail ->
            detail.effectId == effectId ||
                detail.childSkillIds.any { childSkillId ->
                    skillTreeContainsEffect(childSkillId, effectId, visited)
                }
        }
    }

    internal fun moraleAdjustedProbabilityForEngine(
        configured: Int,
        context: SkillBattleContext,
    ): Int {
        val bounded = configured.coerceIn(0, 100)
        if (bounded >= 100) return 100
        return moraleAdjustedProbability(
            configured = bounded,
            source = requestHero(context, context.source),
            context = context,
        )
    }

    private fun moraleAdjustedProbability(
        configured: Int,
        source: BattleHero,
        context: SkillBattleContext,
    ): Int {
        val morale =
            if (SkillBattleViewCapability.LIVE_MORALE in context.battleView.capabilities) {
                context.battleView.currentMorale(context.source) ?: source.morale
            } else {
                source.morale
            }
        val moraleAddition = (morale - 100).toDouble() / (100 + 0.5 * morale)
        return (configured * (1 + moraleAddition)).toInt().coerceIn(0, 100)
    }

    private fun liveModifiers(
        context: SkillBattleContext,
        source: BattleHero,
    ): List<BattleModifier> =
        if (SkillBattleViewCapability.LIVE_STATE in context.battleView.capabilities) {
            context.battleView.state(context.source)?.modifiers ?: source.modifiers
        } else {
            source.modifiers
        }

    private fun requestHero(
        context: SkillBattleContext,
        ref: BattleHeroRef,
    ): BattleHero {
        val team = if (ref.side == com.stzb.battle.core.Side.ATTACKER) {
            context.request.attacker
        } else {
            context.request.defender
        }
        return team.heroes.single { it.id == ref.heroId && it.position == ref.position }
    }

    private fun triggerMatches(kind: SkillKind, trigger: BattleTrigger): Boolean =
        triggerFor(kind) == trigger

    private fun triggerFor(kind: SkillKind): BattleTrigger =
        when (kind) {
            SkillKind.PASSIVE -> BattleTrigger.BATTLE_PASSIVE
            SkillKind.COMMAND -> BattleTrigger.BATTLE_COMMAND
            SkillKind.ACTIVE -> BattleTrigger.ACTIVE_SKILL_ATTEMPT
            SkillKind.PURSUIT -> BattleTrigger.PURSUIT_ATTEMPT
            SkillKind.UNKNOWN -> throw IllegalArgumentException("Unsupported skill kind=$kind")
        }

    companion object {
        private const val QIZUOGUIMOU_SKILL_ID = 200692
        private val QIZUOGUIMOU_CONTROL_DETAIL_IDS =
            setOf(20069223, 20069224, 20069225, 20069226)
        private const val HEINEI_SHIZE_SKILL_ID = 200847
        private val HEINEI_SHIZE_POOL_IDS = setOf(1, 2)
        private val FENJI_LINKED_REFERENCE_DETAILS = setOf(21096101, 21096102)
        private val FENJI_PER_TARGET_CHILD_DETAILS = setOf(21296113, 21296114)
        private val REFERENCED_TEMPLATE_EFFECT_IDS = setOf(125, 408)
        private val INHERITED_TARGET_ATTACK_TYPES = setOf(43, 98)
        private const val SHESHEN_DAMAGE_REFERENCE_DETAIL_ID = 21199312
        private const val SHESHEN_DAMAGE_DETAIL_ID = 21299301
        private const val EMERGENCY_RECOVERY_CALC_POSITION = 995

        fun safe(
            graph: SkillRuleGraph,
            registry: BattleEffectRegistry,
            conditionInterpreter: PendingSkillConditionInterpreter = SkillConditionInterpreter(graph),
            diagnosticSink: (SkillExecutionDiagnostic) -> Unit,
        ): SkillRuleInterpreter =
            SkillRuleInterpreter(
                graph,
                registry,
                conditionInterpreter,
                InterpreterFailureMode.SAFE,
                diagnosticSink,
            )
    }
}
