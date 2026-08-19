package com.stzb.server.game.battle.skill

import com.stzb.server.game.battle.BattleConfigRepository
import com.stzb.server.game.battle.BattleEvent
import com.stzb.server.game.battle.BattleHero
import java.util.Collections

fun interface BattleEffectHandler {
    fun execute(invocation: EffectInvocation): EffectExecution
}

interface ImplementedBattleEffectHandler : BattleEffectHandler {
    val semanticId: String
}

enum class EffectImplementationStatus {
    IMPLEMENTED,
    PLACEHOLDER,
}

class EffectHandlerRegistration private constructor(
    val effectId: Int,
    val implementationStatus: EffectImplementationStatus,
    internal val handler: ImplementedBattleEffectHandler?,
    val diagnosticReason: String?,
) {
    companion object {
        fun implemented(
            effectId: Int,
            handler: ImplementedBattleEffectHandler,
        ): EffectHandlerRegistration {
            require(handler.semanticId.isNotBlank()) {
                "Implemented handler for effect=$effectId must declare non-empty semantics"
            }
            return EffectHandlerRegistration(
                effectId = effectId,
                implementationStatus = EffectImplementationStatus.IMPLEMENTED,
                handler = handler,
                diagnosticReason = null,
            )
        }

        fun placeholder(
            effectId: Int,
            diagnosticReason: String,
        ): EffectHandlerRegistration {
            require(diagnosticReason.isNotBlank()) {
                "Placeholder for effect=$effectId requires a diagnostic reason"
            }
            return EffectHandlerRegistration(
                effectId = effectId,
                implementationStatus = EffectImplementationStatus.PLACEHOLDER,
                handler = null,
                diagnosticReason = diagnosticReason,
            )
        }
    }
}

interface BattleStateChange

data class EffectLifecycleOverride(
    val delayRound: Int,
    val delayHit: Int,
    val availableRounds: Int,
    val availableHit: Int,
    val clearPerHit: Boolean,
)

data class ReferencedDetailExecutionOverride(
    val referencedDetailId: Int,
    val valueDelta: Int? = null,
    val valueReplacement: TypedBattlePotency.Resolved? = null,
    val extraParameters: Map<Int, Int> = emptyMap(),
    val targetOverride: List<com.stzb.server.game.battle.BattleHeroRef>? = null,
    val lifecycleOverride: EffectLifecycleOverride? = null,
)

data class EffectInvocation(
    val rule: SkillEffectRule,
    val context: SkillBattleContext,
    val callPath: List<Int>,
    val detailCallPath: List<SkillExecutionFrame> = emptyList(),
    val preselectedTargets: List<com.stzb.server.game.battle.BattleHeroRef>? = null,
    val valueOverride: TypedBattlePotency.Resolved? = null,
    val executionOverride: ReferencedDetailExecutionOverride? = null,
)

internal fun EffectInvocation.rootSkillLevel(source: BattleHero): Int {
    val index = source.skillIds.indexOf(context.rootSkillId)
    return source.skillLevels.getOrElse(index) { 1 }.coerceIn(1, 10)
}

internal fun EffectInvocation.lifecycle(): EffectLifecycleOverride =
    executionOverride?.lifecycleOverride ?: EffectLifecycleOverride(
        delayRound = rule.raw.delayRound,
        delayHit = rule.raw.delayHit,
        availableRounds = rule.raw.availableRounds,
        availableHit = rule.raw.availableHit,
        clearPerHit = rule.raw.clearPerHit,
    )

internal fun EffectInvocation.withValueDelta(
    potency: TypedBattlePotency.Resolved,
): TypedBattlePotency.Resolved {
    val delta = executionOverride?.valueDelta ?: return potency
    return potency.copy(
        value = potency.value + delta,
        exactValue = potency.exactValue + delta,
    )
}

data class EffectExecution(
    val stateChanges: List<BattleStateChange>,
    val events: List<BattleEvent>,
) {
    internal fun immutableCopy(): EffectExecution =
        EffectExecution(
            stateChanges = immutableList(stateChanges),
            events = immutableList(events),
        )

    companion object {
        val EMPTY = EffectExecution(
            stateChanges = immutableList(emptyList()),
            events = immutableList(emptyList()),
        )
    }
}

enum class EffectDeclarationKind {
    STANDARD,
    META_NO_OP,
}

data class EffectDeclaration(
    val effectId: Int,
    val kind: EffectDeclarationKind,
)

enum class EffectFailureCode {
    UNKNOWN_EFFECT,
    UNIMPLEMENTED_EFFECT,
    UNSUPPORTED_CONFIGURED_VALUE,
}

data class BattleEffectDiagnostic(
    val code: EffectFailureCode,
    val skillId: Int,
    val detailId: Int,
    val effectId: Int,
    val trigger: BattleTrigger,
    val callPath: List<Int>,
    val reason: String? = null,
) {
    fun message(): String =
        "$code: skill=$skillId detail=$detailId effect=$effectId " +
            "trigger=$trigger callPath=${callPath.joinToString(" -> ")}" +
            reason?.let { " reason=$it" }.orEmpty()
}

class UnsupportedSkillRuleException(
    val diagnostic: BattleEffectDiagnostic,
) : IllegalStateException(diagnostic.message())

class UnsupportedConfiguredBattleValueException(
    val diagnostic: BattleEffectDiagnostic,
) : IllegalStateException(diagnostic.message())

class BattleEffectRegistry private constructor(
    declarations: Map<Int, EffectDeclaration>,
    registrations: Map<Int, EffectHandlerRegistration>,
    details: Map<Int, SkillEffectRule>,
    private val failureMode: FailureMode,
    private val logger: (BattleEffectDiagnostic) -> Unit,
) {
    private val declarations: Map<Int, EffectDeclaration> = immutableMap(declarations)
    private val registrations: Map<Int, EffectHandlerRegistration> = immutableMap(registrations)
    private val details: Map<Int, SkillEffectRule> = immutableMap(details)
    private val declaredIds: Set<Int> = immutableSet(this.declarations.keys)
    private val implementedIds: Set<Int> = immutableSet(
        this.registrations.values
            .filter { it.implementationStatus == EffectImplementationStatus.IMPLEMENTED }
            .map { it.effectId },
    )

    fun declaredEffectIds(): Set<Int> = declaredIds

    fun implementedEffectIds(): Set<Int> = implementedIds

    fun declaration(effectId: Int): EffectDeclaration? = declarations[effectId]

    fun implementationSemanticId(effectId: Int): String? =
        registrations[effectId]?.handler?.semanticId

    fun detail(detailId: Int): SkillEffectRule? = details[detailId]

    fun register(vararg registrations: EffectHandlerRegistration): BattleEffectRegistry {
        val repeatedIds = registrations
            .groupingBy { it.effectId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(repeatedIds.isEmpty()) {
            "Duplicate handlers for effects=${repeatedIds.sorted()}"
        }
        val registrationIds = registrations.mapTo(LinkedHashSet()) { it.effectId }
        val existingIds = registrationIds intersect this.registrations.keys
        require(existingIds.isEmpty()) {
            if (existingIds.size == 1) {
                "Duplicate handler for effect=${existingIds.single()}"
            } else {
                "Duplicate handlers for effects=${existingIds.sorted()}"
            }
        }
        val undeclaredIds = registrationIds - declarations.keys
        require(undeclaredIds.isEmpty()) {
            if (undeclaredIds.size == 1) {
                "Undeclared effect=${undeclaredIds.single()}"
            } else {
                "Undeclared effects=${undeclaredIds.sorted()}"
            }
        }
        return BattleEffectRegistry(
            declarations = declarations,
            registrations = this.registrations + registrations.associateBy { it.effectId },
            details = details,
            failureMode = failureMode,
            logger = logger,
        )
    }

    fun execute(
        rule: SkillEffectRule,
        context: SkillBattleContext,
        preselectedTargets: List<com.stzb.server.game.battle.BattleHeroRef>? = null,
        valueOverride: TypedBattlePotency.Resolved? = null,
        executionOverride: ReferencedDetailExecutionOverride? = null,
    ): EffectExecution {
        require(executionOverride == null || executionOverride.referencedDetailId == rule.detailId) {
            "Execution override detail=${executionOverride?.referencedDetailId} " +
                "does not match rule detail=${rule.detailId}"
        }
        val callPath = invocationCallPath(context)
        val invocation = EffectInvocation(
            rule = rule,
            context = context,
            callPath = immutableList(callPath),
            detailCallPath = immutableList(context.runtime.currentDetailPath()),
            preselectedTargets = (
                executionOverride?.targetOverride ?: preselectedTargets
                )?.let(::immutableList),
            valueOverride = executionOverride?.valueReplacement ?: valueOverride,
            executionOverride = executionOverride,
        )
        val handler = registrations[rule.effectId]?.handler
        if (handler != null) {
            return try {
                handler.execute(invocation).immutableCopy()
            } catch (error: UnsupportedConfiguredBattleValueException) {
                when (failureMode) {
                    FailureMode.STRICT -> throw error
                    FailureMode.SAFE -> {
                        logSafely(error.diagnostic)
                        EffectExecution.EMPTY
                    }
                }
            }
        }

        val diagnostic = BattleEffectDiagnostic(
            code = if (rule.effectId in declarations) {
                EffectFailureCode.UNIMPLEMENTED_EFFECT
            } else {
                EffectFailureCode.UNKNOWN_EFFECT
            },
            skillId = context.currentSkillId,
            detailId = rule.detailId,
            effectId = rule.effectId,
            trigger = context.trigger,
            callPath = immutableList(callPath),
        )
        return when (failureMode) {
            FailureMode.STRICT -> throw UnsupportedSkillRuleException(diagnostic)
            FailureMode.SAFE -> {
                logSafely(diagnostic)
                EffectExecution.EMPTY
            }
        }
    }

    private fun logSafely(diagnostic: BattleEffectDiagnostic) {
        try {
            logger(diagnostic)
        } catch (_: Exception) {
            // Diagnostics must not replace safe-mode execution semantics.
        }
    }

    private fun invocationCallPath(context: SkillBattleContext): List<Int> {
        val runtimePath = context.runtime.currentCallPath()
        if (runtimePath.isNotEmpty()) {
            return if (runtimePath.last() == context.currentSkillId) {
                runtimePath
            } else {
                runtimePath + context.currentSkillId
            }
        }
        return if (context.rootSkillId == context.currentSkillId) {
            listOf(context.currentSkillId)
        } else {
            listOf(context.rootSkillId, context.currentSkillId)
        }
    }

    private enum class FailureMode {
        STRICT,
        SAFE,
    }

    companion object {
        private val defaultGraph: SkillRuleGraph by lazy {
            SkillRuleCatalog.build(
                SkillScopeCatalog.loadDefault(),
                BattleConfigRepository.loadDefault(),
            )
        }

        fun strict(graph: SkillRuleGraph = defaultGraph): BattleEffectRegistry =
            create(graph, FailureMode.STRICT) {}

        fun safe(
            logger: (BattleEffectDiagnostic) -> Unit,
        ): BattleEffectRegistry =
            safe(defaultGraph, logger)

        fun safe(
            graph: SkillRuleGraph,
            logger: (BattleEffectDiagnostic) -> Unit,
        ): BattleEffectRegistry =
            create(graph, FailureMode.SAFE, logger)

        private fun create(
            graph: SkillRuleGraph,
            failureMode: FailureMode,
            logger: (BattleEffectDiagnostic) -> Unit,
        ): BattleEffectRegistry {
            val declarations = graph.effectIds.associateWith { effectId ->
                EffectDeclaration(
                    effectId = effectId,
                    kind = if (effectId == META_NO_OP_EFFECT_ID) {
                        EffectDeclarationKind.META_NO_OP
                    } else {
                        EffectDeclarationKind.STANDARD
                    },
                )
            }
            return BattleEffectRegistry(
                declarations = declarations,
                registrations = emptyMap(),
                details = graph.details.associateBy(SkillEffectRule::detailId),
                failureMode = failureMode,
                logger = logger,
            )
        }

        private const val META_NO_OP_EFFECT_ID = 0
    }
}

fun BattleEffectRegistry.registerCoreEffects(
    effectStore: BattleEffectStore,
    calculator: BattleValueCalculator = DefaultBattleValueCalculator(),
): BattleEffectRegistry =
    register(
        *CoreEffectHandlers.registrations(effectStore, calculator)
            .filter { it.effectId in declaredEffectIds() }
            .toTypedArray(),
    )

fun BattleEffectRegistry.registerControlEffects(
    effectStore: BattleEffectStore,
    calculator: BattleValueCalculator = DefaultBattleValueCalculator(),
): BattleEffectRegistry =
    register(
        *ControlEffectHandlers.registrations(effectStore, calculator)
            .filter { it.effectId in declaredEffectIds() }
            .toTypedArray(),
    )

fun BattleEffectRegistry.registerMetaEffects(
    targetSelector: SkillTargetSelector = SkillTargetSelector(),
    calculator: BattleValueCalculator = DefaultBattleValueCalculator(),
): BattleEffectRegistry {
    val registrations = MetaEffectHandlers.registrations(targetSelector, calculator, ::detail)
        .filter { it.effectId in declaredEffectIds() }
        .toTypedArray()
    return register(*registrations)
}

private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))

internal fun EffectInvocation.selectTargets(
    targetSelector: SkillTargetSelector,
): List<com.stzb.server.game.battle.BattleHeroRef> =
    preselectedTargets ?: targetSelector.compile(rule).select(context)
