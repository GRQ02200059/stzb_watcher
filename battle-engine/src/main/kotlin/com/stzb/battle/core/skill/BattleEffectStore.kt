package com.stzb.battle.core.skill

import com.stzb.battle.core.ActiveSkillEffect
import com.stzb.battle.core.BattleHeroRef
import java.util.Collections

enum class EffectApplyOutcome {
    APPLIED,
    REPLACED,
    STACKED,
    REFRESHED,
    REJECTED,
}

data class EffectApplyResult(
    val outcome: EffectApplyOutcome,
    val effect: ActiveSkillEffect?,
    val removed: List<ActiveSkillEffect> = emptyList(),
)

enum class EffectTickBoundary {
    ACTION_END,
    ROUND_END,
}

data class EffectLifecycleResult(
    val updated: List<ActiveSkillEffect> = emptyList(),
    val expired: List<ActiveSkillEffect> = emptyList(),
    val removed: List<ActiveSkillEffect> = emptyList(),
)

class BattleEffectStore {
    private val active = mutableMapOf<BattleHeroRef, MutableList<ActiveSkillEffect>>()

    fun canApply(effect: ActiveSkillEffect): Boolean {
        val incoming = effect.snapshot()
        val conflict = active[incoming.target].orEmpty().firstOrNull { it.conflictsWith(incoming) }
            ?: return true
        return when (incoming.replaceType) {
            STACK -> conflict.sameOrigin(incoming)
            KEEP_EXISTING -> false
            REPLACE_BY_STRENGTH ->
                conflict.canStackStrongest(incoming) ||
                    incoming.effectiveStrength > conflict.effectiveStrength ||
                    incoming.effectiveStrength == conflict.effectiveStrength &&
                    conflict.sameOrigin(incoming)
            KEEP_EXISTING_ACROSS_SKILL_TYPES -> false
            else -> error("ActiveSkillEffect validates replaceType")
        }
    }

    fun apply(effect: ActiveSkillEffect): EffectApplyResult {
        val incoming = effect.snapshot()
        val effects = active.getOrPut(incoming.target) { mutableListOf() }
        val conflict = effects.firstOrNull { it.conflictsWith(incoming) }
        if (conflict == null) {
            effects += incoming
            return applyResult(EffectApplyOutcome.APPLIED, incoming)
        }

        return when (incoming.replaceType) {
            STACK -> {
                if (conflict.sameOrigin(incoming)) {
                    stackOrRefresh(conflict, incoming)
                } else {
                    applyResult(EffectApplyOutcome.REJECTED, conflict)
                }
            }
            KEEP_EXISTING -> applyResult(EffectApplyOutcome.REJECTED, conflict)
            REPLACE_BY_STRENGTH -> replaceByStrength(effects, conflict, incoming)
            KEEP_EXISTING_ACROSS_SKILL_TYPES -> applyResult(EffectApplyOutcome.REJECTED, conflict)
            else -> error("ActiveSkillEffect validates replaceType")
        }
    }

    fun effectsFor(target: BattleHeroRef): List<ActiveSkillEffect> =
        immutableSnapshots(active[target].orEmpty())

    fun setSingleLayerStrength(
        target: BattleHeroRef,
        source: BattleHeroRef,
        skillId: Int,
        detailId: Int,
        effectId: Int,
        strength: Int,
    ): ActiveSkillEffect? {
        require(strength >= 0) { "Effect strength must not be negative: $strength" }
        val matches = active[target].orEmpty().filter {
            it.source == source &&
                it.skillId == skillId &&
                it.detailId == detailId &&
                it.effectId == effectId
        }
        require(matches.size <= 1) {
            "Ambiguous active effect strength update: target=$target source=$source " +
                "skill=$skillId detail=$detailId effect=$effectId"
        }
        val effect = matches.singleOrNull() ?: return null
        effect.setSingleLayerStrength(strength)
        return effect.snapshot()
    }

    fun consumeHit(
        target: BattleHeroRef,
        effectId: Int,
        source: BattleHeroRef? = null,
        detailId: Int? = null,
    ): EffectLifecycleResult {
        val effects = active[target] ?: return lifecycle()
        val updated = mutableListOf<ActiveSkillEffect>()
        val expired = mutableListOf<ActiveSkillEffect>()
        effects
            .filter {
                it.effectId == effectId &&
                    (source == null || it.source == source) &&
                    (detailId == null || it.detailId == detailId) &&
                    (it.remainingHits != null || it.clearPerHit)
            }
            .toList()
            .forEach { effect ->
                if (effect.clearPerHit) {
                    effects.remove(effect)
                    expired += effect
                } else {
                    if (effect.remainingHits == 1) {
                        effects.remove(effect)
                        expired += effect
                    } else {
                        effect.remainingHits?.let { effect.remainingHits = it - 1 }
                        updated += effect
                    }
                }
            }
        removeTargetIfEmpty(target)
        return lifecycle(updated = updated, expired = expired)
    }

    fun consumeRound(
        target: BattleHeroRef,
        effectId: Int,
        source: BattleHeroRef? = null,
        detailId: Int? = null,
    ): EffectLifecycleResult {
        val effects = active[target] ?: return lifecycle()
        val updated = mutableListOf<ActiveSkillEffect>()
        val expired = mutableListOf<ActiveSkillEffect>()
        effects
            .filter {
                it.effectId == effectId &&
                    (source == null || it.source == source) &&
                    (detailId == null || it.detailId == detailId) &&
                    it.remainingRounds != null
            }
            .toList()
            .forEach { effect ->
                if (effect.remainingRounds == 1) {
                    effects.remove(effect)
                    expired += effect
                } else {
                    effect.remainingRounds = requireNotNull(effect.remainingRounds) - 1
                    updated += effect
                }
            }
        removeTargetIfEmpty(target)
        return lifecycle(updated = updated, expired = expired)
    }

    fun tick(
        boundary: EffectTickBoundary = EffectTickBoundary.ROUND_END,
        shouldTick: (ActiveSkillEffect) -> Boolean = { true },
    ): EffectLifecycleResult {
        if (boundary != EffectTickBoundary.ROUND_END) return lifecycle()

        val updated = mutableListOf<ActiveSkillEffect>()
        val expired = mutableListOf<ActiveSkillEffect>()
        active.values.forEach { effects ->
            effects.toList().forEach { effect ->
                if (shouldTick(effect)) {
                    if (effect.remainingRounds == 1) {
                        effects.remove(effect)
                        expired += effect
                    } else if (effect.remainingRounds != null) {
                        effect.remainingRounds = effect.remainingRounds!! - 1
                        updated += effect
                    }
                }
            }
        }
        removeEmptyTargets()
        return lifecycle(updated = updated, expired = expired)
    }

    fun clear(
        target: BattleHeroRef,
        effectId: Int? = null,
        source: BattleHeroRef? = null,
    ): EffectLifecycleResult =
        clearMatchingIdentity(target, effectId, source)

    fun clearMatching(
        target: BattleHeroRef,
        predicate: (ActiveSkillEffect) -> Boolean,
    ): EffectLifecycleResult {
        val effects = active[target] ?: return lifecycle()
        val directlyMatched = effects.filter { it.clearable && predicate(it) }
        if (directlyMatched.isEmpty()) return lifecycle()

        val boundKeys = directlyMatched
            .filter { it.bindFlag != UNBOUND }
            .map { BoundKey(it.source, it.bindFlag) }
            .toSet()
        val removed = effects.filter { effect ->
            effect.clearable &&
                predicate(effect) &&
                (
                    effect in directlyMatched ||
                        effect.bindFlag != UNBOUND &&
                        BoundKey(effect.source, effect.bindFlag) in boundKeys
                    )
        }
        effects.removeAll(removed.toSet())
        removeTargetIfEmpty(target)
        return lifecycle(removed = removed)
    }

    private fun clearMatchingIdentity(
        target: BattleHeroRef,
        effectId: Int?,
        source: BattleHeroRef?,
    ): EffectLifecycleResult {
        val effects = active[target] ?: return lifecycle()
        val directlyMatched = effects.filter {
            it.clearable &&
                (source == null || it.source == source) &&
                (effectId == null || it.effectId == effectId)
        }
        if (directlyMatched.isEmpty()) return lifecycle()

        val boundKeys = directlyMatched
            .filter { it.bindFlag != UNBOUND }
            .map { BoundKey(it.source, it.bindFlag) }
            .toSet()
        val removed = effects.filter { effect ->
            effect.clearable &&
                (effect in directlyMatched ||
                    effect.bindFlag != UNBOUND &&
                    BoundKey(effect.source, effect.bindFlag) in boundKeys)
        }
        effects.removeAll(removed.toSet())
        removeTargetIfEmpty(target)
        return lifecycle(removed = removed)
    }

    private fun stackOrRefresh(
        existing: ActiveSkillEffect,
        incoming: ActiveSkillEffect,
    ): EffectApplyResult {
        val stacked = existing.stacks < existing.maxStacks
        if (stacked) {
            existing.addLayer(incoming.strength, incoming.strengthExact)
        }
        refreshLifecycle(existing, incoming)
        return applyResult(
            if (stacked) EffectApplyOutcome.STACKED else EffectApplyOutcome.REFRESHED,
            existing,
        )
    }

    private fun replaceByStrength(
        effects: MutableList<ActiveSkillEffect>,
        existing: ActiveSkillEffect,
        incoming: ActiveSkillEffect,
    ): EffectApplyResult =
        when {
            existing.canStackStrongest(incoming) ->
                stackOrRefresh(existing, incoming)
            incoming.effectiveStrengthExact > existing.effectiveStrengthExact ->
                replace(effects, existing, incoming)
            incoming.effectiveStrengthExact == existing.effectiveStrengthExact -> {
                if (existing.sameOrigin(incoming)) {
                    refreshLifecycle(existing, incoming)
                    applyResult(EffectApplyOutcome.REFRESHED, existing)
                } else {
                    applyResult(EffectApplyOutcome.REJECTED, existing)
                }
            }
            else -> applyResult(EffectApplyOutcome.REJECTED, existing)
        }

    private fun ActiveSkillEffect.canStackStrongest(other: ActiveSkillEffect): Boolean =
        maxStacks > 1 &&
            other.maxStacks > 1 &&
            sameOrigin(other)

    private fun replace(
        effects: MutableList<ActiveSkillEffect>,
        existing: ActiveSkillEffect,
        incoming: ActiveSkillEffect,
    ): EffectApplyResult {
        val index = effects.indexOf(existing)
        effects[index] = incoming
        return applyResult(
            outcome = EffectApplyOutcome.REPLACED,
            effect = incoming,
            removed = listOf(existing),
        )
    }

    private fun refreshLifecycle(
        existing: ActiveSkillEffect,
        incoming: ActiveSkillEffect,
    ) {
        existing.remainingRounds = incoming.remainingRounds
        existing.remainingHits = incoming.remainingHits
    }

    private fun ActiveSkillEffect.conflictsWith(other: ActiveSkillEffect): Boolean =
        target == other.target &&
            category == other.category &&
            sameConflictIdentity(other) &&
            (replaceType == KEEP_EXISTING_ACROSS_SKILL_TYPES ||
                other.replaceType == KEEP_EXISTING_ACROSS_SKILL_TYPES ||
                sourceSkillType == other.sourceSkillType)

    private fun ActiveSkillEffect.sameConflictIdentity(other: ActiveSkillEffect): Boolean =
        when {
            conflict != 0 || other.conflict != 0 -> conflict == other.conflict
            else -> detailId == other.detailId &&
                effectId == other.effectId &&
                sameOrigin(other)
        }

    private fun ActiveSkillEffect.sameOrigin(other: ActiveSkillEffect): Boolean =
        source == other.source &&
            rootSkillId == other.rootSkillId &&
            skillId == other.skillId &&
            skillKind == other.skillKind &&
            sourceSkillType == other.sourceSkillType

    private fun removeTargetIfEmpty(target: BattleHeroRef) {
        if (active[target].isNullOrEmpty()) active.remove(target)
    }

    private fun removeEmptyTargets() {
        active.entries.removeAll { it.value.isEmpty() }
    }

    private fun applyResult(
        outcome: EffectApplyOutcome,
        effect: ActiveSkillEffect?,
        removed: List<ActiveSkillEffect> = emptyList(),
    ): EffectApplyResult =
        EffectApplyResult(
            outcome = outcome,
            effect = effect?.snapshot(),
            removed = immutableSnapshots(removed),
        )

    private fun lifecycle(
        updated: List<ActiveSkillEffect> = emptyList(),
        expired: List<ActiveSkillEffect> = emptyList(),
        removed: List<ActiveSkillEffect> = emptyList(),
    ): EffectLifecycleResult =
        EffectLifecycleResult(
            updated = immutableSnapshots(updated),
            expired = immutableSnapshots(expired),
            removed = immutableSnapshots(removed),
        )

    private fun immutableSnapshots(
        effects: Collection<ActiveSkillEffect>,
    ): List<ActiveSkillEffect> =
        Collections.unmodifiableList(effects.map { it.snapshot() })

    private fun ActiveSkillEffect.snapshot(): ActiveSkillEffect = detachedCopy()

    private data class BoundKey(val source: BattleHeroRef, val bindFlag: Int)

    private companion object {
        const val STACK = 0
        const val KEEP_EXISTING = 1
        const val REPLACE_BY_STRENGTH = 2
        const val KEEP_EXISTING_ACROSS_SKILL_TYPES = 3
        const val UNBOUND = 0
    }
}
