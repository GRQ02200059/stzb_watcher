package com.stzb.server.game.battle.skill

import com.stzb.server.game.battle.BattleHeroRef
import java.util.Collections

enum class PreparationCancelReason {
    CONFUSION,
    HESITATION,
    CLEANSE,
}

data class TimingAttemptOptions(
    val oncePerRound: Boolean = true,
    val preparationReductionRounds: Int = 0,
    val lockedTargets: List<BattleHeroRef>? = null,
)

data class SkillPreparationStartedEvent(
    val snapshot: SkillExecutionSnapshot,
) : SkillExecutionEvent {
    override val rootSkillId: Int
        get() = snapshot.rootSkillId
}

data class SkillPreparationCancelledEvent(
    val round: Int,
    val snapshot: SkillExecutionSnapshot,
    val reason: PreparationCancelReason,
) : SkillExecutionEvent {
    override val rootSkillId: Int
        get() = snapshot.rootSkillId
}

sealed interface SkillTimingEvent : SkillExecutionEvent {
    val source: BattleHeroRef
    val currentSkillId: Int
    val trigger: BattleTrigger

    data class PreparationCompleted(
        override val source: BattleHeroRef,
        override val rootSkillId: Int,
        override val currentSkillId: Int,
        val startedRound: Int,
        val readyRound: Int,
        val completedRound: Int,
        override val trigger: BattleTrigger,
        val lockedTargets: List<BattleHeroRef>?,
        val reselectedTargets: List<BattleHeroRef>?,
    ) : SkillTimingEvent
}

class InvalidSkillTimingException(
    val skillId: Int,
    val detailId: Int?,
    val rootSkillId: Int?,
    val trigger: BattleTrigger,
    val specification: Any?,
    val reason: String,
) : IllegalArgumentException(
    "Invalid skill timing: skillId=$skillId detailId=$detailId rootSkillId=$rootSkillId " +
        "trigger=$trigger reason=$reason specification=$specification",
)

data class SkillAttemptRejectedChange(
    val source: BattleHeroRef,
    val skillId: Int,
    val trigger: BattleTrigger,
    val round: Int,
) : BattleStateChange

data class SkillPreparationRejectedChange(
    val snapshot: SkillExecutionSnapshot,
) : BattleStateChange

data class SkillPreparationCancelledChange(
    val round: Int,
    val snapshot: SkillExecutionSnapshot,
    val reason: PreparationCancelReason,
) : BattleStateChange

data class ScheduledTimingChange(
    val snapshot: DelayedEffect,
    val delayRound: Int,
    val delayHit: Int,
    val change: BattleStateChange,
) : BattleStateChange

data class TimingPosition(
    val round: Int,
    val hit: Int,
)

class SkillTimingDue internal constructor(
    val change: BattleStateChange,
    val activatedChanges: List<BattleStateChange>,
    val dueRound: Int,
    val dueHit: Int,
    val sequence: Long,
) {
    private var consumed = false

    internal fun consume() {
        require(!consumed) { "Skill timing due token was already consumed: sequence=$sequence" }
        consumed = true
    }

    companion object {
        internal fun mint(
            change: BattleStateChange,
            activatedChanges: List<BattleStateChange>,
            dueRound: Int,
            dueHit: Int,
            sequence: Long,
        ): SkillTimingDue = SkillTimingDue(
            change,
            Collections.unmodifiableList(activatedChanges.toList()),
            dueRound,
            dueHit,
            sequence,
        )

        internal fun mint(
            change: BattleStateChange,
            dueRound: Int,
            dueHit: Int,
            sequence: Long,
        ): SkillTimingDue = mint(
            change = change,
            activatedChanges = when (change) {
                is ScheduledEffectActivationChange -> change.activationChanges()
                is ScheduledDamageEffectChange,
                is ScheduledRecoveryEffectChange,
                -> listOf(change)
                else -> error(
                    "Unsupported delayed activation token change=${change::class.simpleName}",
                )
            },
            dueRound = dueRound,
            dueHit = dueHit,
            sequence = sequence,
        )
    }
}

class CompleteTimingCoordinator(
    private val graph: SkillRuleGraph,
    private val interpreter: SkillRuleInterpreter,
    private val runtime: SkillRuntimeState,
    private val diagnosticSink: (SkillExecutionDiagnostic) -> Unit = {},
    private val failureMode: SkillTimingFailureMode = SkillTimingFailureMode.STRICT,
) {
    private val scheduledChanges = mutableMapOf<Long, BattleStateChange>()
    private var currentRound: Int = 0
    private var currentHit: Int = 0

    fun position(): TimingPosition = TimingPosition(currentRound, currentHit)

    fun attempt(
        skillId: Int,
        context: SkillBattleContext,
        options: TimingAttemptOptions = TimingAttemptOptions(),
    ): SkillExecutionResult {
        val timingContext = context.copy(runtime = runtime)
        if (timingContext.round <= 0) {
            return invalid(
                context = timingContext,
                skillId = skillId,
                detailId = null,
                specification = context,
                reason = "round must be positive: ${timingContext.round}",
            )
        }
        val rule = graph.rule(skillId) ?: return diagnostic(
            context = timingContext,
            skillId = skillId,
            reason = "Missing timing rule for skill=$skillId",
        )
        if (options.preparationReductionRounds < 0) {
            return invalid(
                context = timingContext,
                skillId = skillId,
                detailId = null,
                specification = options,
                reason = "preparationReductionRounds must not be negative: " +
                    options.preparationReductionRounds,
            )
        }
        val effectivePreparationLong = (
            rule.prepareRounds.toLong() - options.preparationReductionRounds.toLong()
            ).coerceAtLeast(0)
        val readyRoundLong = timingContext.round.toLong() + effectivePreparationLong
        if (effectivePreparationLong > Int.MAX_VALUE.toLong() ||
            readyRoundLong !in 1..Int.MAX_VALUE.toLong()
        ) {
            return invalid(
                context = timingContext,
                skillId = skillId,
                detailId = null,
                specification = options,
                reason = "Preparation timing is outside Int range: round=${timingContext.round} " +
                    "effectivePreparation=$effectivePreparationLong readyRound=$readyRoundLong",
            )
        }
        val effectivePreparation = effectivePreparationLong.toInt()
        val readyRound = readyRoundLong.toInt()
        if (runtime.isPreparing(timingContext.source, skillId)) {
            return result(
                changes = listOf(
                    SkillPreparationRejectedChange(
                        PreparedSkill(
                            source = timingContext.source,
                            rootSkillId = timingContext.rootSkillId.takeIf { it != 0 } ?: skillId,
                            skillId = skillId,
                            trigger = timingContext.trigger,
                            startedRound = timingContext.round,
                            readyRound = readyRound,
                            lockedTargets = options.lockedTargets,
                        ),
                    ),
                ),
            )
        }
        if (!runtime.recordAttempt(
                source = timingContext.source,
                trigger = timingContext.trigger,
                skillId = skillId,
                round = timingContext.round,
                oncePerRound = options.oncePerRound,
            )
        ) {
            return result(
                changes = listOf(
                    SkillAttemptRejectedChange(
                        timingContext.source,
                        skillId,
                        timingContext.trigger,
                        timingContext.round,
                    ),
                ),
            )
        }
        if (!interpreter.probabilitySucceeds(skillId, timingContext.trigger, timingContext)) {
            return SkillExecutionResult.EMPTY
        }
        val snapshot = PreparedSkill(
            source = timingContext.source,
            rootSkillId = timingContext.rootSkillId.takeIf { it != 0 } ?: skillId,
            skillId = skillId,
            trigger = timingContext.trigger,
            startedRound = timingContext.round,
            readyRound = readyRound,
            lockedTargets = options.lockedTargets?.let { Collections.unmodifiableList(it.toList()) },
        )
        if (effectivePreparation == 0) {
            runtime.recordSuccessfulExecution(timingContext.source, timingContext.trigger, skillId)
            return interpreter.executeAccepted(snapshot, timingContext)
        }
        if (!runtime.prepare(snapshot)) {
            return result(changes = listOf(SkillPreparationRejectedChange(snapshot)))
        }
        return result(
            events = listOf(
                SkillPreparationStartedEvent(snapshot),
                BattleOutputEvent(
                    snapshot.rootSkillId,
                    com.stzb.server.game.battle.BattleEvent.SkillPreparationStarted(
                        round = snapshot.startedRound,
                        source = snapshot.source,
                        skillId = snapshot.skillId,
                        readyRound = snapshot.readyRound,
                    ),
                ),
            ),
        )
    }

    fun onRound(context: SkillBattleContext): SkillExecutionResult {
        val aggregate = onRoundStart(context)
        if (context.round <= 0) return aggregate
        return aggregate + completePreparations(context, source = null)
    }

    fun onRoundStart(context: SkillBattleContext): SkillExecutionResult {
        if (context.round <= 0) {
            return invalid(context.copy(runtime = runtime), context.currentSkillId, null, context, "round must be positive")
        }
        if (context.round < currentRound) {
            return invalid(
                context.copy(runtime = runtime),
                context.currentSkillId,
                null,
                context,
                "round moved backward: currentRound=$currentRound requestedRound=${context.round}",
            )
        }
        currentRound = context.round
        currentHit = 0
        return drain(context, currentRound, currentHit)
    }

    fun onAction(context: SkillBattleContext): SkillExecutionResult =
        completePreparations(context, source = context.source)

    private fun completePreparations(
        context: SkillBattleContext,
        source: BattleHeroRef?,
    ): SkillExecutionResult {
        if (context.round <= 0) {
            return invalid(context.copy(runtime = runtime), context.currentSkillId, null, context, "round must be positive")
        }
        var aggregate = SkillExecutionResult.EMPTY
        runtime.duePreparations(context.round, source).forEach { snapshot ->
            runtime.recordSuccessfulExecution(snapshot.source, snapshot.trigger, snapshot.skillId)
            runtime.suppressAttemptForRound(
                snapshot.source,
                snapshot.trigger,
                snapshot.skillId,
                context.round,
            )
            val completed = SkillTimingEvent.PreparationCompleted(
                source = snapshot.source,
                rootSkillId = snapshot.rootSkillId,
                currentSkillId = snapshot.skillId,
                startedRound = snapshot.startedRound,
                readyRound = snapshot.readyRound,
                completedRound = context.round,
                trigger = snapshot.trigger,
                lockedTargets = snapshot.lockedTargets,
                reselectedTargets = null,
            )
            aggregate += result(events = listOf(completed)) + interpreter.executeAccepted(
                snapshot,
                context.copy(
                    runtime = runtime,
                    round = context.round,
                    source = snapshot.source,
                    rootSkillId = snapshot.rootSkillId,
                    currentSkillId = snapshot.skillId,
                    trigger = snapshot.trigger,
                ),
            )
        }
        return aggregate
    }

    fun onHit(context: SkillBattleContext): SkillExecutionResult {
        if (context.round <= 0) {
            return invalid(context.copy(runtime = runtime), context.currentSkillId, null, context, "round must be positive")
        }
        if (context.round < currentRound) {
            return invalid(
                context.copy(runtime = runtime),
                context.currentSkillId,
                null,
                context,
                "round moved backward: currentRound=$currentRound requestedRound=${context.round}",
            )
        }
        if (currentRound != context.round) {
            currentRound = context.round
            currentHit = 0
        }
        currentHit += 1
        return drain(context, currentRound, currentHit)
    }

    fun enqueue(
        change: BattleStateChange,
        currentRound: Int,
        currentHit: Int,
    ): SkillExecutionResult {
        val timing = timingOf(change) ?: return invalid(
            trigger = BattleTrigger.ACTION_AFTER,
            skillId = skillIdOf(change),
            detailId = detailIdOf(change),
            rootSkillId = rootSkillIdOf(change),
            specification = change,
            reason = "Unsupported scheduled change=${change::class.simpleName}",
        )
        val (delayRound, delayHit, snapshot) = timing
        if (currentRound <= 0 || currentHit < 0 || delayRound < 0 || delayHit < 0 ||
            delayRound == 0 && delayHit == 0
        ) {
            return invalid(
                trigger = BattleTrigger.ACTION_AFTER,
                skillId = snapshot.skillId,
                detailId = snapshot.detailId,
                rootSkillId = snapshot.rootSkillId,
                specification = change,
                reason = "Invalid timing: currentRound=$currentRound currentHit=$currentHit " +
                    "delayRound=$delayRound delayHit=$delayHit",
            )
        }
        if (currentRound < this.currentRound ||
            currentRound == this.currentRound && currentHit < this.currentHit
        ) {
            return invalid(
                trigger = BattleTrigger.ACTION_AFTER,
                skillId = snapshot.skillId,
                detailId = snapshot.detailId,
                rootSkillId = snapshot.rootSkillId,
                specification = change,
                reason = "Current timing moved backward: coordinator=${position()} " +
                    "provided=${TimingPosition(currentRound, currentHit)}",
            )
        }
        val dueRound = currentRound.toLong() + delayRound
        val dueHit = if (delayRound == 0) currentHit.toLong() + delayHit else delayHit.toLong()
        if (dueRound !in 1..Int.MAX_VALUE.toLong() || dueHit !in 0..Int.MAX_VALUE.toLong()) {
            return invalid(
                trigger = BattleTrigger.ACTION_AFTER,
                skillId = snapshot.skillId,
                detailId = snapshot.detailId,
                rootSkillId = snapshot.rootSkillId,
                specification = change,
                reason = "Due timing is outside Int range: dueRound=$dueRound dueHit=$dueHit",
            )
        }
        val scheduled = runtime.schedule(
            snapshot.copy(dueRound = dueRound.toInt(), dueHit = dueHit.toInt()),
        )
        scheduledChanges[scheduled.sequence] = change
        return SkillExecutionResult.EMPTY
    }

    fun activate(change: BattleStateChange, round: Int): SkillExecutionResult =
        when (change) {
            is ScheduledEffectActivationChange -> {
                val activationChanges = change.activationChanges()
                var aggregate = result(
                    changes = activationChanges.filterNot { it is CancelPreparedSkillsChange },
                    events = change.activationEvent(round)?.let {
                        listOf(BattleOutputEvent(change.spec.rootSkillId, it))
                    }.orEmpty(),
                )
                if (activationChanges.any { it is CancelPreparedSkillsChange }) {
                    aggregate += cancelPreparations(
                        change.spec.target,
                        round,
                        cancelReason(change.spec.effectId),
                    )
                }
                aggregate
            }
            is CancelPreparedSkillsChange ->
                cancelPreparations(change.spec.target, round, cancelReason(change.spec.effectId))
            is ScheduledTimingChange -> result(changes = listOf(change.change))
            is ScheduledDamageEffectChange,
            is ScheduledRecoveryEffectChange,
            -> result(changes = listOf(change))
            else -> result(changes = listOf(change))
        }

    fun cancelPreparations(
        source: BattleHeroRef,
        round: Int,
        reason: PreparationCancelReason,
    ): SkillExecutionResult {
        val removed = runtime.interruptPreparations(source)
        return result(
            changes = removed.map { SkillPreparationCancelledChange(round, it, reason) },
            events = removed.map { SkillPreparationCancelledEvent(round, it, reason) },
        )
    }

    private fun drain(
        context: SkillBattleContext,
        round: Int,
        hit: Int,
    ): SkillExecutionResult =
        runtime.dueEffects(round, hit).fold(SkillExecutionResult.EMPTY) { aggregate, delayed ->
            val change = scheduledChanges.remove(delayed.sequence)
            if (change == null) {
                aggregate + diagnostic(
                    trigger = context.trigger,
                    skillId = delayed.skillId,
                    detailId = delayed.detailId,
                    reason = "Missing scheduled payload for sequence=${delayed.sequence}",
                )
            } else {
                aggregate + activate(change, round, delayed)
            }
        }

    private fun activate(
        change: BattleStateChange,
        round: Int,
        delayed: DelayedEffect,
    ): SkillExecutionResult =
        activate(change, round).let { activated ->
            if (
                change !is ScheduledEffectActivationChange &&
                change !is ScheduledDamageEffectChange &&
                change !is ScheduledRecoveryEffectChange
            ) {
                activated
            } else {
                SkillExecutionResult.immutable(
                activated.stateChanges,
                activated.events,
                activated.executedSkillIds,
                activated.diagnostics,
                listOf(
                    SkillTimingDue.mint(
                        change,
                        activated.stateChanges,
                        delayed.dueRound,
                        delayed.dueHit,
                        delayed.sequence,
                    ),
                ),
            )
            }
        }

    private fun timingOf(change: BattleStateChange): Triple<Int, Int, DelayedEffect>? =
        when (change) {
            is ScheduledTimingChange -> Triple(change.delayRound, change.delayHit, change.snapshot)
            is ScheduledEffectActivationChange -> change.spec.timing()
            is ScheduledDamageEffectChange -> change.spec.timing()
            is ScheduledRecoveryEffectChange -> change.spec.timing()
            else -> null
        }

    private fun PersistentEffectSpec.timing() =
        Triple(
            delayRound,
            delayHit,
            DelayedEffect(source, rootSkillId, skillId, detailId, dueRound = 0),
        )

    private fun skillIdOf(change: BattleStateChange): Int =
        (change as? ScheduledTimingChange)?.snapshot?.skillId ?: 0

    private fun detailIdOf(change: BattleStateChange): Int? =
        (change as? ScheduledTimingChange)?.snapshot?.detailId

    private fun rootSkillIdOf(change: BattleStateChange): Int? =
        (change as? ScheduledTimingChange)?.snapshot?.rootSkillId

    private fun diagnostic(
        context: SkillBattleContext,
        skillId: Int,
        reason: String,
    ): SkillExecutionResult =
        diagnostic(context.trigger, skillId, null, reason)

    private fun diagnostic(
        trigger: BattleTrigger,
        skillId: Int,
        detailId: Int?,
        reason: String,
    ): SkillExecutionResult {
        val diagnostic = SkillExecutionDiagnostic(
            code = "INVALID_TIMING",
            skillId = skillId,
            detailId = detailId,
            effectId = null,
            trigger = trigger,
            fullPath = detailId?.let { listOf(SkillExecutionFrame(skillId, it)) }.orEmpty(),
            reason = reason,
        )
        try {
            diagnosticSink(diagnostic)
        } catch (_: Exception) {
            // Timing diagnostics remain safe even when a reporting sink fails.
        }
        return result(diagnostics = listOf(diagnostic))
    }

    private fun invalid(
        context: SkillBattleContext,
        skillId: Int,
        detailId: Int?,
        specification: Any?,
        reason: String,
    ): SkillExecutionResult =
        invalid(
            trigger = context.trigger,
            skillId = skillId,
            detailId = detailId,
            rootSkillId = context.rootSkillId,
            specification = specification,
            reason = reason,
        )

    private fun invalid(
        trigger: BattleTrigger,
        skillId: Int,
        detailId: Int?,
        rootSkillId: Int?,
        specification: Any?,
        reason: String,
    ): SkillExecutionResult {
        if (failureMode == SkillTimingFailureMode.STRICT) {
            throw InvalidSkillTimingException(
                skillId = skillId,
                detailId = detailId,
                rootSkillId = rootSkillId,
                trigger = trigger,
                specification = specification,
                reason = reason,
            )
        }
        return diagnostic(trigger, skillId, detailId, reason)
    }

    private fun cancelReason(effectId: Int): PreparationCancelReason =
        if (effectId in HESITATION_IDS) PreparationCancelReason.HESITATION
        else PreparationCancelReason.CONFUSION

    private fun result(
        changes: List<BattleStateChange> = emptyList(),
        events: List<SkillExecutionEvent> = emptyList(),
        diagnostics: List<SkillExecutionDiagnostic> = emptyList(),
    ): SkillExecutionResult =
        SkillExecutionResult.immutable(changes, events, emptyList(), diagnostics)

    companion object {
        fun safe(
            graph: SkillRuleGraph,
            interpreter: SkillRuleInterpreter,
            runtime: SkillRuntimeState,
            diagnosticSink: (SkillExecutionDiagnostic) -> Unit = {},
        ): CompleteTimingCoordinator =
            CompleteTimingCoordinator(
                graph,
                interpreter,
                runtime,
                diagnosticSink,
                SkillTimingFailureMode.SAFE,
            )

        val HESITATION_IDS = setOf(502, 702, 902)
    }
}

enum class SkillTimingFailureMode {
    STRICT,
    SAFE,
}
