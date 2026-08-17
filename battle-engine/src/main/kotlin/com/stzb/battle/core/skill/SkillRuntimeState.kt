package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.Side

data class SkillExecutionFrame(
    val skillId: Int,
    val detailId: Int,
) {
    override fun toString(): String = "$skillId/$detailId"
}

class SkillRuntimeState {
    private val counts = mutableMapOf<RuntimeKey, Int>()
    private val attemptCounts = mutableMapOf<RuntimeKey, Int>()
    private val lastAttemptRounds = mutableMapOf<RuntimeKey, Int>()
    private val triggerCounts = mutableMapOf<TriggerKey, Int>()
    private val sideTriggerCounts = mutableMapOf<SideTriggerKey, Int>()
    private val consumedThresholdGenerations = mutableMapOf<ThresholdKey, Int>()
    private val limitedOccurrences = mutableMapOf<OccurrenceKey, Int>()
    private val counters = mutableMapOf<CounterKey, Int>()
    private val roundHurtCounts = mutableMapOf<RoundHurtKey, Int>()
    private val pendingSignals = mutableMapOf<SignalKey, Int>()
    private val preparedEffectRounds = mutableMapOf<PreparedEffectRoundKey, Boolean>()
    private val markers = mutableMapOf<MarkerKey, MarkerValue>()
    private val referencedValueDeltas = mutableMapOf<ReferencedValueKey, Int>()
    private val referencedValueProgressions =
        mutableListOf<ReferencedValueProgression>()
    private val preparations = mutableListOf<PreparedSkill>()
    private val delayedEffects = mutableListOf<DelayedEffect>()
    private val callStack = ArrayDeque<Int>()
    private val detailCallStack = ArrayDeque<SkillExecutionFrame>()
    private var nextSequence = 0L

    fun count(source: BattleHeroRef, trigger: BattleTrigger, skillId: Int): Int =
        counts[RuntimeKey(source, trigger, skillId)] ?: 0

    fun increment(source: BattleHeroRef, trigger: BattleTrigger, skillId: Int): Int {
        val key = RuntimeKey(source, trigger, skillId)
        val updated = count(source, trigger, skillId) + 1
        counts[key] = updated
        return updated
    }

    fun attemptCount(source: BattleHeroRef, trigger: BattleTrigger, skillId: Int): Int =
        attemptCounts[RuntimeKey(source, trigger, skillId)] ?: 0

    fun attemptCount(source: BattleHeroRef, trigger: BattleTrigger): Int =
        attemptCounts.entries.sumOf { (key, value) ->
            if (key.source == source && key.trigger == trigger) value else 0
        }

    fun sideAttemptCount(side: Side, trigger: BattleTrigger): Int =
        attemptCounts.entries.sumOf { (key, value) ->
            if (key.source.side == side && key.trigger == trigger) value else 0
        }

    fun recordAttempt(
        source: BattleHeroRef,
        trigger: BattleTrigger,
        skillId: Int,
        round: Int,
        oncePerRound: Boolean = true,
    ): Boolean {
        val key = RuntimeKey(source, trigger, skillId)
        if (oncePerRound && lastAttemptRounds[key] == round) return false
        lastAttemptRounds[key] = round
        attemptCounts[key] = attemptCount(source, trigger, skillId) + 1
        return true
    }

    internal fun suppressAttemptForRound(
        source: BattleHeroRef,
        trigger: BattleTrigger,
        skillId: Int,
        round: Int,
    ) {
        lastAttemptRounds[RuntimeKey(source, trigger, skillId)] = round
    }

    fun count(source: BattleHeroRef, trigger: BattleTrigger): Int =
        triggerCounts[TriggerKey(source, trigger)] ?: 0

    fun recordBattleTriggerOccurrence(source: BattleHeroRef, trigger: BattleTrigger): Int {
        val key = TriggerKey(source, trigger)
        val updated = count(source, trigger) + 1
        triggerCounts[key] = updated
        val sideKey = SideTriggerKey(source.side, trigger)
        sideTriggerCounts[sideKey] = sideCount(source.side, trigger) + 1
        return updated
    }

    fun sideCount(side: Side, trigger: BattleTrigger): Int =
        sideTriggerCounts[SideTriggerKey(side, trigger)] ?: 0

    fun preparedEffectActive(
        target: BattleHeroRef,
        source: BattleHeroRef,
        detailId: Int,
        effectId: Int,
        round: Int,
        probability: Int,
        random: BattleRandom,
    ): Boolean {
        require(round > 0) { "Prepared effect round must be positive: $round" }
        require(probability in 0..100) {
            "Prepared effect probability must be within 0..100: $probability"
        }
        val key = PreparedEffectRoundKey(
            target = target,
            source = source,
            detailId = detailId,
            effectId = effectId,
            round = round,
        )
        return preparedEffectRounds.getOrPut(key) {
            probability >= 100 || random.nextInt(100) < probability
        }
    }

    fun consumeThreshold(
        owner: BattleHeroRef,
        namespace: String,
        count: Int,
        threshold: Int,
    ): Boolean {
        require(namespace.isNotBlank()) { "Threshold namespace must not be blank" }
        require(count >= 0) { "Threshold count must be non-negative: $count" }
        require(threshold > 0) { "Threshold must be positive: $threshold" }
        val generation = count / threshold
        if (generation <= 0) return false
        val key = ThresholdKey(owner, namespace, threshold)
        val consumed = consumedThresholdGenerations[key] ?: 0
        if (generation <= consumed) return false
        consumedThresholdGenerations[key] = generation
        return true
    }

    fun consumeLimitedOccurrence(
        owner: BattleHeroRef,
        namespace: String,
        limit: Int,
    ): Boolean {
        require(namespace.isNotBlank()) { "Occurrence namespace must not be blank" }
        require(limit > 0) { "Occurrence limit must be positive: $limit" }
        val key = OccurrenceKey(owner, namespace)
        val consumed = limitedOccurrences[key] ?: 0
        if (consumed >= limit) return false
        limitedOccurrences[key] = consumed + 1
        return true
    }

    fun limitedOccurrenceCount(
        owner: BattleHeroRef,
        namespace: String,
    ): Int {
        require(namespace.isNotBlank()) { "Occurrence namespace must not be blank" }
        return limitedOccurrences[OccurrenceKey(owner, namespace)] ?: 0
    }

    fun counter(
        owner: BattleHeroRef,
        namespace: String,
    ): Int {
        require(namespace.isNotBlank()) { "Counter namespace must not be blank" }
        return counters[CounterKey(owner, namespace)] ?: 0
    }

    fun addCounter(
        owner: BattleHeroRef,
        namespace: String,
        delta: Int,
        minimum: Int = 0,
        maximum: Int = Int.MAX_VALUE,
    ): Int {
        require(namespace.isNotBlank()) { "Counter namespace must not be blank" }
        require(minimum <= maximum) {
            "Counter minimum must not exceed maximum: minimum=$minimum maximum=$maximum"
        }
        val key = CounterKey(owner, namespace)
        val updated = ((counters[key] ?: 0).toLong() + delta)
            .coerceIn(minimum.toLong(), maximum.toLong())
            .toInt()
        counters[key] = updated
        return updated
    }

    fun recordRoundHurt(target: BattleHeroRef, round: Int): Int {
        require(round >= 0) { "Hurt round must be non-negative: $round" }
        val key = RoundHurtKey(target, round)
        val updated = roundHurtCount(target, round) + 1
        roundHurtCounts[key] = updated
        return updated
    }

    fun roundHurtCount(target: BattleHeroRef, round: Int): Int =
        roundHurtCounts[RoundHurtKey(target, round)] ?: 0

    fun scheduleSignal(
        owner: BattleHeroRef,
        namespace: String,
        readyRound: Int,
    ) {
        require(namespace.isNotBlank()) { "Signal namespace must not be blank" }
        require(readyRound >= 0) { "Signal ready round must be non-negative: $readyRound" }
        val key = SignalKey(owner, namespace)
        pendingSignals[key] = minOf(pendingSignals[key] ?: readyRound, readyRound)
    }

    fun consumeSignal(
        owner: BattleHeroRef,
        namespace: String,
        round: Int,
    ): Boolean {
        require(namespace.isNotBlank()) { "Signal namespace must not be blank" }
        require(round >= 0) { "Signal round must be non-negative: $round" }
        val key = SignalKey(owner, namespace)
        val readyRound = pendingSignals[key] ?: return false
        if (round < readyRound) return false
        pendingSignals.remove(key)
        return true
    }

    fun recordMarker(
        target: BattleHeroRef,
        detailId: Int,
        value: Int,
        appliedRound: Int,
        durationRounds: Int,
        rootSkillId: Int = 0,
        source: BattleHeroRef? = null,
    ) {
        require(detailId > 0) { "Marker detail ID must be positive: $detailId" }
        require(appliedRound >= 0) { "Marker round must be non-negative: $appliedRound" }
        require(durationRounds >= 0) { "Marker duration must be non-negative: $durationRounds" }
        markers[MarkerKey(target, detailId)] = MarkerValue(
            value = value,
            expiresAtRound = appliedRound + durationRounds.coerceAtLeast(1),
            rootSkillId = rootSkillId,
            source = source,
            sequence = nextSequence++,
        )
    }

    fun hasMarker(target: BattleHeroRef, detailId: Int, round: Int): Boolean =
        markerValue(target, detailId, round) != null

    fun markerValue(target: BattleHeroRef, detailId: Int, round: Int): Int? {
        val key = MarkerKey(target, detailId)
        val marker = markers[key] ?: return null
        if (round >= marker.expiresAtRound) {
            markers.remove(key)
            return null
        }
        return marker.value
    }

    fun removeMarker(target: BattleHeroRef, detailId: Int): Boolean =
        markers.remove(MarkerKey(target, detailId)) != null

    fun latestMarkedTarget(
        rootSkillId: Int,
        targetSide: Side,
        round: Int,
    ): BattleHeroRef? {
        markers.keys.toList().forEach { key ->
            markerValue(key.target, key.detailId, round)
        }
        return markers.entries
            .asSequence()
            .filter { (key, marker) ->
                key.target.side == targetSide && marker.rootSkillId == rootSkillId
            }
            .maxByOrNull { (_, marker) -> marker.sequence }
            ?.key
            ?.target
    }

    @Deprecated(
        message = "Use recordBattleTriggerOccurrence to make explicit that callers record battle events",
        replaceWith = ReplaceWith("recordBattleTriggerOccurrence(source, trigger)"),
    )
    fun recordTrigger(source: BattleHeroRef, trigger: BattleTrigger): Int =
        recordBattleTriggerOccurrence(source, trigger)

    fun recordSuccessfulExecution(
        source: BattleHeroRef,
        trigger: BattleTrigger,
        skillId: Int,
    ): Int {
        recordBattleTriggerOccurrence(source, trigger)
        return increment(source, trigger, skillId)
    }

    fun prepare(skill: PreparedSkill): Boolean {
        if (preparations.any { it.source == skill.source && it.skillId == skill.skillId }) {
            return false
        }
        preparations += skill
        return true
    }

    fun preparedSkills(): List<PreparedSkill> = preparations.toList()

    fun isPreparing(source: BattleHeroRef, skillId: Int): Boolean =
        preparations.any { it.source == source && it.skillId == skillId }

    fun interruptPreparations(source: BattleHeroRef): List<PreparedSkill> {
        val removed = preparations.filter { it.source == source }
        preparations.removeAll(removed.toSet())
        return removed
    }

    fun duePreparations(
        round: Int,
        source: BattleHeroRef? = null,
    ): List<PreparedSkill> {
        val due = preparations.filter {
            it.readyRound <= round && (source == null || it.source == source)
        }
        preparations.removeAll(due.toSet())
        return due
    }

    fun schedule(effect: DelayedEffect): DelayedEffect {
        val scheduled = effect.copy(sequence = nextSequence++)
        delayedEffects += scheduled
        return scheduled
    }

    fun delayedCount(): Int = delayedEffects.size

    fun dueEffects(round: Int, hit: Int = 0): List<DelayedEffect> {
        val due = delayedEffects
            .filter { it.dueRound < round || it.dueRound == round && it.dueHit <= hit }
            .sortedWith(compareBy(DelayedEffect::dueRound, DelayedEffect::dueHit, DelayedEffect::sequence))
        delayedEffects.removeAll(due.toSet())
        return due
    }

    fun enter(skillId: Int) {
        val path = callStack.toList()
        check(skillId !in path) {
            "Skill call cycle: ${(path + skillId).joinToString(" -> ")}"
        }
        check(callStack.size < MAX_CHILD_DEPTH) {
            "Skill call path exceeds maximum child depth $MAX_CHILD_DEPTH: " +
                (path + skillId).joinToString(" -> ")
        }
        callStack.addLast(skillId)
    }

    fun exit(skillId: Int) {
        check(callStack.lastOrNull() == skillId) {
            "Cannot exit skill $skillId from call path ${callStack.joinToString(" -> ")}"
        }
        callStack.removeLast()
    }

    fun currentCallPath(): List<Int> = callStack.toList()

    fun enterDetail(frame: SkillExecutionFrame) {
        val path = detailCallStack.toList()
        check(frame !in path) {
            "Skill detail call cycle: ${(path + frame).joinToString(" -> ")}"
        }
        check(detailCallStack.size < MAX_REFERENCE_DEPTH) {
            "Skill detail path exceeds maximum reference depth $MAX_REFERENCE_DEPTH: " +
                (path + frame).joinToString(" -> ")
        }
        detailCallStack.addLast(frame)
    }

    fun exitDetail(frame: SkillExecutionFrame) {
        check(detailCallStack.lastOrNull() == frame) {
            "Cannot exit detail $frame from call path ${detailCallStack.joinToString(" -> ")}"
        }
        detailCallStack.removeLast()
    }

    fun currentDetailPath(): List<SkillExecutionFrame> = detailCallStack.toList()

    fun referencedValueDelta(
        source: BattleHeroRef,
        rootSkillId: Int,
        detailId: Int,
    ): Int = referencedValueDeltas[ReferencedValueKey(source, rootSkillId, detailId)] ?: 0

    fun addReferencedValueDelta(
        source: BattleHeroRef,
        rootSkillId: Int,
        detailId: Int,
        delta: Int,
    ): Int {
        require(detailId > 0) { "Referenced detail ID must be positive: $detailId" }
        val key = ReferencedValueKey(source, rootSkillId, detailId)
        val updated = referencedValueDelta(source, rootSkillId, detailId) + delta
        referencedValueDeltas[key] = updated
        return updated
    }

    fun scheduleReferencedValueProgression(
        source: BattleHeroRef,
        rootSkillId: Int,
        detailId: Int,
        delta: Int,
        appliedRound: Int,
        delayRound: Int,
        availableRounds: Int,
    ) {
        require(detailId > 0) { "Referenced detail ID must be positive: $detailId" }
        require(appliedRound >= 0) {
            "Referenced value applied round must be non-negative: $appliedRound"
        }
        require(delayRound >= 0) {
            "Referenced value delay must be non-negative: $delayRound"
        }
        require(availableRounds > 0) {
            "Referenced value available rounds must be positive: $availableRounds"
        }
        referencedValueProgressions += ReferencedValueProgression(
            source = source,
            rootSkillId = rootSkillId,
            detailId = detailId,
            delta = delta,
            firstRound = (appliedRound + delayRound).coerceAtLeast(1),
            availableRounds = availableRounds,
        )
    }

    fun advanceReferencedValueChanges(round: Int) {
        require(round > 0) { "Referenced value round must be positive: $round" }
        referencedValueProgressions.forEach { progression ->
            val finalRound = progression.firstRound + progression.availableRounds - 1
            val eligibleRound = minOf(round, finalRound)
            val expectedApplications =
                (eligibleRound - progression.firstRound + 1).coerceAtLeast(0)
            repeat((expectedApplications - progression.appliedRounds).coerceAtLeast(0)) {
                addReferencedValueDelta(
                    source = progression.source,
                    rootSkillId = progression.rootSkillId,
                    detailId = progression.detailId,
                    delta = progression.delta,
                )
                progression.appliedRounds += 1
            }
        }
        referencedValueProgressions.removeAll {
            it.appliedRounds >= it.availableRounds
        }
    }

    private data class RuntimeKey(
        val source: BattleHeroRef,
        val trigger: BattleTrigger,
        val skillId: Int,
    )

    private data class TriggerKey(
        val source: BattleHeroRef,
        val trigger: BattleTrigger,
    )

    private data class SideTriggerKey(
        val side: Side,
        val trigger: BattleTrigger,
    )

    private data class ThresholdKey(
        val owner: BattleHeroRef,
        val namespace: String,
        val threshold: Int,
    )

    private data class SignalKey(
        val owner: BattleHeroRef,
        val namespace: String,
    )

    private data class OccurrenceKey(
        val owner: BattleHeroRef,
        val namespace: String,
    )

    private data class CounterKey(
        val owner: BattleHeroRef,
        val namespace: String,
    )

    private data class PreparedEffectRoundKey(
        val target: BattleHeroRef,
        val source: BattleHeroRef,
        val detailId: Int,
        val effectId: Int,
        val round: Int,
    )

    private data class RoundHurtKey(
        val target: BattleHeroRef,
        val round: Int,
    )

    private data class MarkerKey(
        val target: BattleHeroRef,
        val detailId: Int,
    )

    private data class ReferencedValueKey(
        val source: BattleHeroRef,
        val rootSkillId: Int,
        val detailId: Int,
    )

    private data class ReferencedValueProgression(
        val source: BattleHeroRef,
        val rootSkillId: Int,
        val detailId: Int,
        val delta: Int,
        val firstRound: Int,
        val availableRounds: Int,
        var appliedRounds: Int = 0,
    )

    private data class MarkerValue(
        val value: Int,
        val expiresAtRound: Int,
        val rootSkillId: Int,
        val source: BattleHeroRef?,
        val sequence: Long,
    )

    companion object {
        const val MAX_CHILD_DEPTH = 16
        const val MAX_REFERENCE_DEPTH = 16
    }
}
