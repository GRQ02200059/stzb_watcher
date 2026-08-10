package com.stzb.battle.core.skill

import com.stzb.battle.core.ActionPermission
import com.stzb.battle.core.ActiveSkillEffect
import com.stzb.battle.core.BattleEffectValueUnit
import com.stzb.battle.core.BattleDamageCalculator
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleModifier
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleStat
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.DamageOrigin
import com.stzb.battle.core.DamageSchool
import com.stzb.battle.core.DamageTag
import com.stzb.battle.core.EffectCategory
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillKind
import kotlin.math.roundToInt

class UnsupportedBattleStateChangeException(
    change: BattleStateChange,
) : IllegalArgumentException(
    "Unsupported battle-state change: ${change::class.qualifiedName ?: change::class.simpleName}",
)

sealed interface BattleStateOutput {
    data class EffectApplied(val spec: PersistentEffectSpec) : BattleStateOutput
    data class EffectRemoved(val effect: ActiveSkillEffect) : BattleStateOutput
    data class EffectExpired(val effect: ActiveSkillEffect) : BattleStateOutput
    data class EffectBlocked(val change: EffectBlockedChange) : BattleStateOutput

    data class DamageDealt(
        val source: BattleHeroRef,
        val target: BattleHeroRef,
        val amount: Int,
        val school: DamageSchool,
        val origin: DamageOrigin,
        val tags: Set<DamageTag>,
        val skillId: Int,
        val effectId: Int,
        val calculation: DirectDamageCalculation? = null,
    ) : BattleStateOutput

    data class HurtReceived(
        val source: BattleHeroRef,
        val target: BattleHeroRef,
        val amount: Int,
        val school: DamageSchool,
        val origin: DamageOrigin,
        val tags: Set<DamageTag>,
        val skillId: Int,
        val effectId: Int,
    ) : BattleStateOutput

    data class TroopsRecovered(
        val source: BattleHeroRef,
        val target: BattleHeroRef,
        val amount: Int,
        val skillId: Int,
        val effectId: Int,
    ) : BattleStateOutput

    data class StatChanged(
        val change: BattleStatChange,
        val strength: Int,
        val delta: Int,
        val valueAfter: Int,
        val deltaExact: Double = delta.toDouble(),
        val valueAfterExact: Double = valueAfter.toDouble(),
    ) : BattleStateOutput

    data class ModifierApplied(
        val change: DamageModifierChange,
    ) : BattleStateOutput

    data class RecoveryModifierApplied(
        val source: BattleHeroRef,
        val target: BattleHeroRef,
        val skillId: Int,
        val effectId: Int,
        val percent: Int,
        val durationRounds: Int,
    ) : BattleStateOutput

    data class DamageAbsorbed(
        val owner: BattleHeroRef,
        val target: BattleHeroRef,
        val amount: Int,
        val currentRoundTotal: Int,
        val percent: Int,
    ) : BattleStateOutput
}

data class BattleStateApplyResult(
    val outputs: List<BattleStateOutput> = emptyList(),
)

data class BattleStatePermission(
    val canAct: Boolean = true,
    val canCastActive: Boolean = true,
    val canNormalAttack: Boolean = true,
    val normalAttackCount: Int = 1,
    val pursuitOpportunityCount: Int = 1,
    val splitAttack: Boolean = false,
    val counterattack: Boolean = false,
    val canEvade: Boolean = false,
    val ignoresEvade: Boolean = false,
    val firstAction: Boolean = false,
    val damageRedirectTarget: BattleHeroRef? = null,
)

internal data class EffectKey(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val skillKind: SkillKind,
    val sourceSkillType: Int,
    val detailId: Int,
    val effectId: Int,
    val category: EffectCategory,
    val conflict: Int,
    val replaceType: Int,
    val bindFlag: Int,
    val maxStacks: Int,
    val clearPerHit: Boolean,
    val clearable: Boolean,
)

internal data class ControlDurationExtensionMatch(
    val rounds: Int,
    val effectKeys: List<EffectKey>,
)

interface SkillBattleHistoryAdapter {
    fun linkedTarget(source: BattleHeroRef): BattleHeroRef?
    fun currentTarget(source: BattleHeroRef): BattleHeroRef?
    fun previousTarget(source: BattleHeroRef): BattleHeroRef?
}

class SkillBattleState(
    val request: BattleRequest,
    val runtime: SkillRuntimeState,
    initialWoundedTroops: Map<BattleHeroRef, Int> = emptyMap(),
    val effectStore: BattleEffectStore = BattleEffectStore(),
    private val metadataProvider: ((BattleHeroRef) -> SkillBattleHeroMetadata?)? = null,
    private val historyAdapter: SkillBattleHistoryAdapter? = null,
    private val stateFilterMatcher:
        ((SkillTargetStateFilter, BattleHeroRef, BattleHeroRef) -> Boolean)? = null,
) {
    internal data class MutableHeroState(
        val entry: SkillBattleHeroState,
        val inherentStats: BattleStats,
        var stats: BattleStats,
        var troops: Int,
        var woundedTroops: Int,
        var morale: Int,
    )

    private val states = mutableMapOf<BattleHeroRef, MutableHeroState>()
    private val damageDealt = mutableMapOf<BattleHeroRef, Int>()
    private data class SkillRangeBonusKey(
        val target: BattleHeroRef,
        val kind: SkillKind,
        val skillId: Int?,
    )

    private val skillRangeBonuses = mutableMapOf<SkillRangeBonusKey, Int>()
    internal val effectStatuses = mutableMapOf<EffectKey, BattleStatus>()
    internal val effectModifiers = mutableMapOf<EffectKey, BattleModifier>()

    init {
        request.attacker.heroes.forEach { add(Side.ATTACKER, it, initialWoundedTroops) }
        request.defender.heroes.forEach { add(Side.DEFENDER, it, initialWoundedTroops) }
    }

    val view: SkillBattleView = object : SkillBattleView {
        override val capabilities: Set<SkillBattleViewCapability> = buildSet {
            add(SkillBattleViewCapability.HERO_ROSTER)
            add(SkillBattleViewCapability.ENTRY_STATE)
            add(SkillBattleViewCapability.LIVE_STATE)
            add(SkillBattleViewCapability.DAMAGE_HISTORY)
            add(SkillBattleViewCapability.LIVE_MORALE)
            add(SkillBattleViewCapability.NORMAL_ATTACK_RANGE)
            add(SkillBattleViewCapability.ACTIVE_EFFECTS)
            if (metadataProvider != null) add(SkillBattleViewCapability.HERO_METADATA)
            if (historyAdapter != null) add(SkillBattleViewCapability.TARGET_HISTORY)
            if (stateFilterMatcher != null) add(SkillBattleViewCapability.STATE_FILTERS)
        }

        override fun heroes(): List<BattleHeroRef> = states.keys.toList()

        override fun entryState(ref: BattleHeroRef): SkillBattleHeroState? =
            states[ref]?.entry?.snapshot()

        override fun state(ref: BattleHeroRef): SkillBattleHeroState? =
            states[ref]?.snapshot(ref)

        override fun metadata(ref: BattleHeroRef): SkillBattleHeroMetadata? =
            metadataProvider?.invoke(ref)
                ?: if (metadataProvider == null) {
                    missing(SkillBattleViewCapability.HERO_METADATA, "metadata")
                } else {
                    null
                }

        override fun accumulatedDamageDealt(ref: BattleHeroRef): Int = damageDealt[ref] ?: 0

        override fun currentMorale(ref: BattleHeroRef): Int? = states[ref]?.morale

        override fun currentAttackRange(ref: BattleHeroRef): Int? = states[ref]?.stats?.hitRange

        override fun skillRangeBonus(ref: BattleHeroRef, kind: SkillKind): Int =
            skillRangeBonuses[SkillRangeBonusKey(ref, kind, null)] ?: 0

        override fun skillRangeBonus(
            ref: BattleHeroRef,
            kind: SkillKind,
            skillId: Int,
        ): Int =
            skillRangeBonus(ref, kind) +
                (skillRangeBonuses[SkillRangeBonusKey(ref, kind, skillId)] ?: 0)

        override fun linkedTarget(source: BattleHeroRef): BattleHeroRef? =
            historyAdapter?.linkedTarget(source)
                ?: if (historyAdapter == null) {
                    missing(SkillBattleViewCapability.TARGET_HISTORY, "linkedTarget")
                } else {
                    null
                }

        override fun currentTarget(source: BattleHeroRef): BattleHeroRef? =
            historyAdapter?.currentTarget(source)
                ?: if (historyAdapter == null) {
                    missing(SkillBattleViewCapability.TARGET_HISTORY, "currentTarget")
                } else {
                    null
                }

        override fun previousTarget(source: BattleHeroRef): BattleHeroRef? =
            historyAdapter?.previousTarget(source)
                ?: if (historyAdapter == null) {
                    missing(SkillBattleViewCapability.TARGET_HISTORY, "previousTarget")
                } else {
                    null
                }

        override fun matchesStateFilter(
            filter: SkillTargetStateFilter,
            source: BattleHeroRef,
            target: BattleHeroRef,
        ): Boolean =
            stateFilterMatcher?.invoke(filter, source, target)
                ?: missing(SkillBattleViewCapability.STATE_FILTERS, "matchesStateFilter")

        override fun activeEffectIds(ref: BattleHeroRef): Set<Int> =
            effectStore.effectsFor(ref).mapTo(mutableSetOf()) { it.effectId }

        override fun activeEffectStrength(ref: BattleHeroRef, detailId: Int): Int =
            effectStore.effectsFor(ref)
                .filter { it.detailId == detailId }
                .sumOf { it.effectiveStrength }

        private fun <T> missing(
            capability: SkillBattleViewCapability,
            operation: String,
        ): T = throw MissingLiveBattleViewData(capability, operation)
    }

    internal fun contains(ref: BattleHeroRef): Boolean = ref in states

    internal fun seedInitialEffects() {
        states.forEach { (ref, mutable) ->
            mutable.entry.statuses.forEach { status ->
                val effectId = status.initialEffectId() ?: return@forEach
                effectStore.apply(
                    ActiveSkillEffect(
                        source = ref,
                        target = ref,
                        rootSkillId = 1,
                        skillId = 1,
                        skillKind = SkillKind.PASSIVE,
                        sourceSkillType = 1,
                        detailId = -effectId,
                        effectId = effectId,
                        category = EffectCategory.BENEFICIAL,
                        conflict = effectId,
                        strength = 1,
                        replaceType = 0,
                        bindFlag = 0,
                        maxStacks = 1,
                        stacks = 1,
                        remainingRounds = 99,
                        remainingHits = if (status == BattleStatus.EVADE) 1 else null,
                        clearPerHit = status == BattleStatus.EVADE,
                        clearable = false,
                    ),
                )
            }
        }
    }

    internal fun mutable(ref: BattleHeroRef): MutableHeroState =
        requireNotNull(states[ref]) { "Unknown battle hero: $ref" }

    internal fun recordDamage(source: BattleHeroRef, amount: Int) {
        damageDealt[source] = (damageDealt[source] ?: 0) + amount
    }

    internal fun applySkillRangeChange(
        change: MetaEffectChange,
        kind: SkillKind,
        delta: Int,
        round: Int,
    ): BattleEvent.SkillRangeChanged {
        val affectedSkillId = change.parameters.effectParam.takeIf { it > 0 }
        val key = SkillRangeBonusKey(change.target, kind, affectedSkillId)
        val total = (skillRangeBonuses[key] ?: 0) + delta
        skillRangeBonuses[key] = total
        return BattleEvent.SkillRangeChanged(
            round = round,
            source = change.source,
            target = change.target,
            skillId = change.skillId,
            skillKind = kind,
            delta = delta,
            displayRangeAfter = liveHero(change.target).stats.hitRange +
                view.skillRangeBonus(
                    change.target,
                    kind,
                    affectedSkillId ?: change.skillId,
                ),
        )
    }

    internal fun liveHero(ref: BattleHeroRef): BattleHero {
        val state = mutable(ref)
        val entryHero = teamFor(ref.side).heroes.single {
            it.position == ref.position && it.id == ref.heroId
        }
        return entryHero.copy(
            stats = state.stats,
            troops = state.troops,
            activeStatuses = state.snapshot(ref).statuses,
            modifiers = state.snapshot(ref).modifiers ?: entryHero.modifiers,
        )
    }

    private fun add(
        side: Side,
        hero: BattleHero,
        woundedTroops: Map<BattleHeroRef, Int>,
    ) {
        val ref = BattleHeroRef(side, hero.position, hero.id)
        val entry = SkillBattleHeroState(
            stats = hero.stats.copy(),
            troops = hero.troops,
            maxTroops = hero.maxTroops,
            statuses = hero.activeStatuses.toSet(),
            morale = hero.morale,
            attackRange = hero.stats.hitRange,
            woundedTroops = woundedTroops[ref]?.coerceAtLeast(0) ?: 0,
            modifiers = hero.modifiers.toList(),
        )
        states[ref] = MutableHeroState(
            entry,
            hero.inherentStats.copy(),
            entry.stats,
            entry.troops,
            entry.woundedTroops,
            entry.morale,
        )
    }

    private fun teamFor(side: Side) =
        if (side == Side.ATTACKER) request.attacker else request.defender

    private fun MutableHeroState.snapshot(ref: BattleHeroRef) = SkillBattleHeroState(
        stats = stats.copy(),
        troops = troops,
        maxTroops = entry.maxTroops,
        statuses = entry.statuses + effectStore.effectsFor(ref).mapNotNull {
            effectStatuses[it.key()]
        },
        morale = morale,
        attackRange = stats.hitRange,
        canReceiveEffectsWhenDefeated = entry.canReceiveEffectsWhenDefeated,
        woundedTroops = woundedTroops,
        modifiers = entry.modifiers.orEmpty() + effectStore.effectsFor(ref).mapNotNull {
            effectModifiers[it.key()]
        },
    )

    private fun SkillBattleHeroState.snapshot() = copy(
        stats = stats.copy(),
        statuses = statuses.toSet(),
    )

}

private fun BattleStatus.initialEffectId(): Int? = when (this) {
    BattleStatus.CONFUSION -> 501
    BattleStatus.HESITATION -> 502
    BattleStatus.DISARM -> 552
    BattleStatus.INSIGHT -> 511
    BattleStatus.EVADE -> 514
    BattleStatus.IGNORE_EVADE -> 515
    BattleStatus.DOUBLE_ATTACK -> 544
    BattleStatus.FIRST_ACTION -> 761
    else -> null
}

class BattleStateChangeApplier(
    private val state: SkillBattleState,
) {
    private data class StatModifier(
        val kind: BattleStatChange.Kind,
        val unit: BattleEffectValueUnit,
        val sign: Int,
    )

    private data class DamageModifier(
        val direction: DamageModifierChange.Direction,
        val school: DamageSchool?,
        val origin: DamageOrigin?,
        val tag: DamageTag?,
        val sign: Int,
        val requiredTargetStatus: BattleStatus?,
        val targetSkillId: Int?,
        val targetSkillIds: Set<Int>,
    ) {
        fun matches(
            owner: BattleHeroRef,
            change: TroopDamageChange,
            ownerStatuses: Set<BattleStatus>,
        ): Boolean =
            owner == when (direction) {
                DamageModifierChange.Direction.DEALT -> change.source
                DamageModifierChange.Direction.TAKEN -> change.target
            } &&
                (school == null || school == change.school) &&
                (origin == null || origin == change.origin) &&
                (tag == null || tag in change.tags) &&
                (requiredTargetStatus == null || requiredTargetStatus in ownerStatuses)
    }

    private data class Redirection(
        val protectedTargets: List<BattleHeroRef>,
        val damageBearer: BattleHeroRef,
        val sharePercent: Int,
        val school: DamageSchool?,
    )

    private data class LinkedSharing(
        val members: List<BattleHeroRef>,
        val sharePercentPerAlly: Int,
    )

    private data class DamageAbsorptionAccumulator(
        val protectedTargets: List<BattleHeroRef>,
        val absorbPercent: Int,
        var currentRoundAbsorbedDamage: Int = 0,
        var previousRoundAbsorbedDamage: Int = 0,
    )

    private data class DamageReleaseSchedule(
        val target: BattleHeroRef,
        val referencedDetailId: Int,
        val referencedEffectId: Int,
        val baseReleasePercent: Int,
        val firstReleaseRound: Int,
    )

    private val statModifiers = mutableMapOf<EffectKey, StatModifier>()
    private val damageModifiers = mutableMapOf<EffectKey, DamageModifier>()
    private data class OngoingDamageBehavior(
        val change: ScheduledDamageEffectChange,
        val sourceSnapshot: BattleHero,
    )

    private val ongoingDamage = mutableMapOf<EffectKey, OngoingDamageBehavior>()
    private val ongoingRecovery = mutableMapOf<EffectKey, ScheduledRecoveryEffectChange>()
    private val redirections = mutableMapOf<EffectKey, Redirection>()
    private val linkedSharings = mutableMapOf<EffectKey, LinkedSharing>()
    private val forcedTargets = mutableMapOf<EffectKey, BattleHeroRef>()
    private val sharedEffectUseMembers = mutableMapOf<EffectKey, Int>()
    private val damageAbsorptions = mutableMapOf<EffectKey, DamageAbsorptionAccumulator>()
    private val damageReleases = mutableMapOf<EffectKey, DamageReleaseSchedule>()
    private var lastBegunRound = 0
    private var lastStartedRound = 0
    private var lastEndedRound = 0
    private val lastActionStartedRound = mutableMapOf<BattleHeroRef, Int>()

    fun apply(
        changes: List<BattleStateChange>,
        round: Int,
    ): BattleStateApplyResult = applyValidated(changes, round, delayedActivation = false)

    fun willApply(change: BattleStatChange): Boolean =
        state.effectStore.canApply(statEffect(change))

    fun tryConsumeForcedTarget(
        actor: BattleHeroRef,
        eligibleTargets: List<BattleHeroRef>,
        random: BattleRandom,
    ): BattleHeroRef? {
        val (key, forcedTarget) = activeEntries(forcedTargets)
            .lastOrNull { (key, target) ->
                key.target == actor &&
                    target in eligibleTargets &&
                    (state.view.state(target)?.troops ?: 0) > 0
            }
            ?: return null
        val probability = key.strength().coerceIn(0, 100)
        if (probability < 100 && random.nextInt(100) >= probability) return null

        synchronize(
            state.effectStore.consumeHit(
                target = key.target,
                effectId = key.effectId,
                source = key.source,
                detailId = key.detailId,
            ),
        )
        return forcedTarget
    }

    fun consumeSkillProbabilityUses(
        actor: BattleHeroRef,
        skillId: Int,
        skillKind: SkillKind,
    ): BattleStateApplyResult {
        val usedModifierKeys = activeEntries(state.effectModifiers)
            .filter { (key, modifier) ->
                key.target == actor &&
                    modifier is BattleModifier.SkillProbabilityPercent &&
                    (modifier.skillId == null || modifier.skillId == skillId) &&
                    (modifier.skillIds.isEmpty() || skillId in modifier.skillIds) &&
                    (modifier.skillKind == null || modifier.skillKind == skillKind)
            }
            .map { it.first }
        if (usedModifierKeys.isEmpty()) return BattleStateApplyResult()

        val activeGroups = activeEntries(sharedEffectUseMembers)
        val outputs = mutableListOf<BattleStateOutput>()
        usedModifierKeys.forEach { usedKey ->
            val groups = activeGroups.filter { (groupKey, memberDetailId) ->
                groupKey.source == usedKey.source &&
                    groupKey.target == usedKey.target &&
                    groupKey.rootSkillId == usedKey.rootSkillId &&
                    memberDetailId == usedKey.detailId
            }
            if (groups.isEmpty()) {
                outputs += synchronize(
                    state.effectStore.consumeHit(
                        target = usedKey.target,
                        effectId = usedKey.effectId,
                        source = usedKey.source,
                        detailId = usedKey.detailId,
                    ),
                )
                return@forEach
            }

            val groupRoot = groups.first().first
            val allGroups = activeGroups.filter { (groupKey, _) ->
                groupKey.source == groupRoot.source &&
                    groupKey.target == groupRoot.target &&
                    groupKey.rootSkillId == groupRoot.rootSkillId
            }
            val memberDetailIds = allGroups.mapTo(linkedSetOf()) { it.second }
            state.effectStore.effectsFor(actor)
                .filter { effect ->
                    effect.source == groupRoot.source &&
                        effect.rootSkillId == groupRoot.rootSkillId &&
                        effect.detailId in memberDetailIds
                }
                .forEach { member ->
                    outputs += synchronize(
                        state.effectStore.consumeHit(
                            target = actor,
                            effectId = member.effectId,
                            source = member.source,
                            detailId = member.detailId,
                        ),
                    )
                }
            allGroups.forEach { (groupKey, _) ->
                outputs += synchronize(
                    state.effectStore.consumeHit(
                        target = groupKey.target,
                        effectId = groupKey.effectId,
                        source = groupKey.source,
                        detailId = groupKey.detailId,
                    ),
                )
            }
        }
        return BattleStateApplyResult(outputs)
    }

    internal fun matchingControlDurationExtensions(
        actor: BattleHeroRef,
        rootSkillId: Int,
        skillKind: SkillKind,
    ): ControlDurationExtensionMatch {
        val mainSkillId = state.liveHero(actor).skillIds.firstOrNull()
        val matches = activeEntries(state.effectModifiers)
            .mapNotNull { (key, modifier) ->
                val extension = modifier as? BattleModifier.ControlDurationIncrease
                    ?: return@mapNotNull null
                if (key.target != actor) return@mapNotNull null
                if (extension.mainSkillOnly && rootSkillId != mainSkillId) {
                    return@mapNotNull null
                }
                if (
                    extension.requiredSkillKind != null &&
                    extension.requiredSkillKind != skillKind
                ) {
                    return@mapNotNull null
                }
                key to extension
            }
        return ControlDurationExtensionMatch(
            rounds = matches.sumOf { (_, extension) -> extension.rounds },
            effectKeys = matches.map { it.first },
        )
    }

    internal fun consumeControlDurationExtensions(
        match: ControlDurationExtensionMatch,
    ): BattleStateApplyResult {
        val outputs = mutableListOf<BattleStateOutput>()
        match.effectKeys.forEach { key ->
            val effect = state.effectStore.effectsFor(key.target)
                .singleOrNull { it.key() == key }
            if (effect?.remainingHits != null) {
                outputs += synchronize(
                    state.effectStore.consumeHit(
                        target = key.target,
                        effectId = key.effectId,
                        source = key.source,
                        detailId = key.detailId,
                    ),
                )
            }
        }
        return BattleStateApplyResult(outputs)
    }

    fun consumeEffectHit(
        target: BattleHeroRef,
        effectId: Int,
        source: BattleHeroRef? = null,
        detailId: Int? = null,
    ): BattleStateApplyResult =
        BattleStateApplyResult(
            synchronize(
                state.effectStore.consumeHit(
                    target = target,
                    effectId = effectId,
                    source = source,
                    detailId = detailId,
                ),
            ),
        )

    fun applyActivated(
        change: BattleStateChange,
        due: SkillTimingDue,
        round: Int,
        hit: Int = 0,
    ): BattleStateApplyResult {
        require(due.change == change) { "Timing due token does not match scheduled activation" }
        require(round > due.dueRound || round == due.dueRound && hit >= due.dueHit) {
            "Activation is early: current=($round,$hit) due=(${due.dueRound},${due.dueHit})"
        }
        val changes = when (change) {
            is ScheduledEffectActivationChange -> change.activationChanges()
            is ScheduledDamageEffectChange,
            is ScheduledRecoveryEffectChange,
            -> listOf(change)
            else -> throw IllegalArgumentException(
                "Unsupported delayed activation change=${change::class.simpleName}",
            )
        }
        changes.forEach { preflight(it, delayedActivation = true) }
        due.consume()
        return applyValidated(changes, round, delayedActivation = true)
    }

    private fun applyValidated(
        changes: List<BattleStateChange>,
        round: Int,
        delayedActivation: Boolean,
    ): BattleStateApplyResult {
        require(round >= 0) { "round must not be negative: $round" }
        changes.forEach { preflight(it, delayedActivation) }
        val outputs = mutableListOf<BattleStateOutput>()
        val recovered = mutableMapOf<RecoveryKey, Int>()
        changes.forEach { applyOne(it, outputs, recovered) }
        return BattleStateApplyResult(outputs.toList())
    }

    fun beginRound(round: Int): BattleStateApplyResult {
        require(round > 0) { "round must be positive: $round" }
        require(round >= maxOf(lastBegunRound, lastEndedRound)) {
            "round moved backward: current=${maxOf(lastBegunRound, lastEndedRound)} requested=$round"
        }
        if (round == lastBegunRound || round <= lastEndedRound) return BattleStateApplyResult()
        lastBegunRound = round
        if (round > 1) {
            state.view.heroes().forEach { ref ->
                val hero = state.mutable(ref)
                hero.woundedTroops = hero.woundedTroops.toLong()
                    .times(WOUNDED_TROOP_RETENTION_PERCENT)
                    .div(100)
                    .toInt()
            }
        }
        pruneInactiveBehaviors()
        activeEntries(damageAbsorptions).forEach { (_, accumulator) ->
            accumulator.previousRoundAbsorbedDamage = accumulator.currentRoundAbsorbedDamage
            accumulator.currentRoundAbsorbedDamage = 0
        }
        val changes = buildList {
            activeEntries(damageReleases).forEach releaseLoop@{ (key, release) ->
                if (round < release.firstReleaseRound) return@releaseLoop
                if (state.mutable(release.target).troops <= 0) return@releaseLoop
                val accumulator = activeEntries(damageAbsorptions)
                    .lastOrNull { (absorptionKey, behavior) ->
                        absorptionKey.source == key.source &&
                            absorptionKey.rootSkillId == key.rootSkillId &&
                            release.target in behavior.protectedTargets
                    }
                    ?.second
                    ?: return@releaseLoop
                val releasePercent = (
                    release.baseReleasePercent +
                        state.runtime.referencedValueDelta(
                            source = key.source,
                            rootSkillId = key.rootSkillId,
                            detailId = key.detailId,
                        )
                    ).coerceIn(0, 100)
                val amount = accumulator.previousRoundAbsorbedDamage.toLong()
                    .times(releasePercent)
                    .div(100)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                if (amount <= 0) return@releaseLoop
                add(
                    TroopDamageChange(
                        source = key.source,
                        target = release.target,
                        amount = amount,
                        troopsAfter = (
                            state.mutable(release.target).troops - amount
                            ).coerceAtLeast(0),
                        school = DamageSchool.STRATEGY,
                        origin = DamageOrigin.COMMAND,
                        tags = setOf(DamageTag.IMPERIAL_SEAL_RELEASE),
                        skillId = release.referencedDetailId / 100,
                        effectId = release.referencedEffectId,
                    ),
                )
            }
        }
        return apply(changes, round)
    }

    fun onRoundStart(round: Int): BattleStateApplyResult {
        val boundary = beginRound(round)
        require(round >= maxOf(lastStartedRound, lastEndedRound)) {
            "round moved backward: current=${maxOf(lastStartedRound, lastEndedRound)} requested=$round"
        }
        if (round == lastStartedRound || round <= lastEndedRound) return boundary
        lastStartedRound = round
        pruneInactiveBehaviors()
        return boundary
    }

    fun onActionStart(
        target: BattleHeroRef,
        round: Int,
    ): BattleStateApplyResult {
        requireHero(target)
        require(round > 0) { "round must be positive: $round" }
        val currentRound = maxOf(
            lastStartedRound,
            lastEndedRound,
            lastActionStartedRound[target] ?: 0,
        )
        require(round >= currentRound) {
            "round moved backward: current=$currentRound requested=$round"
        }
        if (round == lastActionStartedRound[target] || round <= lastEndedRound) {
            return BattleStateApplyResult()
        }
        lastActionStartedRound[target] = round
        pruneInactiveBehaviors()

        val dueOngoingDamage = activeEntries(ongoingDamage)
            .filter { (key, _) -> key.target == target }
        val dueOngoingRecovery = activeEntries(ongoingRecovery)
            .filter { (key, _) -> key.target == target }
        val changes = buildList {
            dueOngoingDamage.forEach { (_, behavior) ->
                add(
                    behavior.change.tick(
                        liveSource = behavior.sourceSnapshot,
                        liveTarget = state.liveHero(behavior.change.target),
                        targetConditions = BattleDamageCalculator.targetConditions(
                            target = state.liveHero(behavior.change.target),
                            targetTeam = state.view.heroes()
                                .filter { it.side == behavior.change.target.side }
                                .map(state::liveHero),
                        ),
                    ),
                )
            }
            dueOngoingRecovery.forEach { (_, change) ->
                addAll(
                    change.tick(
                        liveState = requireNotNull(state.view.state(change.target)),
                        effectStore = state.effectStore,
                    ),
                )
            }
        }
        val ongoing = apply(changes, round)
        val lifecycle = (
            dueOngoingDamage.map { it.first } +
                dueOngoingRecovery.map { it.first }
            )
            .distinct()
            .flatMap(::consumeOngoingLifecycle)
        return BattleStateApplyResult(ongoing.outputs + lifecycle)
    }

    private fun consumeOngoingLifecycle(
        key: EffectKey,
    ): List<BattleStateOutput> {
        val outputs = mutableListOf<BattleStateOutput>()
        var active = state.effectStore.effectsFor(key.target)
            .singleOrNull { it.key() == key }
        if (active?.remainingHits != null || active?.clearPerHit == true) {
            outputs += synchronize(
                state.effectStore.consumeHit(
                    target = key.target,
                    effectId = key.effectId,
                    source = key.source,
                    detailId = key.detailId,
                ),
            )
            active = state.effectStore.effectsFor(key.target)
                .singleOrNull { it.key() == key }
        }
        if (active?.remainingRounds != null) {
            outputs += synchronize(
                state.effectStore.consumeRound(
                    target = key.target,
                    effectId = key.effectId,
                    source = key.source,
                    detailId = key.detailId,
                ),
            )
        }
        return outputs
    }

    fun triggerAppliedOngoingDamage(
        spec: PersistentEffectSpec,
        round: Int,
    ): BattleStateApplyResult {
        val key = spec.toActiveSkillEffectOrNull()?.key() ?: return BattleStateApplyResult()
        val behavior = ongoingDamage[key] ?: return BattleStateApplyResult()
        return apply(
            listOf(
                behavior.change.tick(
                    liveSource = behavior.sourceSnapshot,
                    liveTarget = state.liveHero(behavior.change.target),
                ),
            ),
            round,
        )
    }

    fun triggerLastAppliedOngoingDamage(
        target: BattleHeroRef,
        round: Int,
    ): BattleStateApplyResult {
        val behavior = activeEntries(ongoingDamage)
            .lastOrNull { (key, _) -> key.target == target }
            ?.second
            ?: return BattleStateApplyResult()
        return apply(
            listOf(
                behavior.change.tick(
                    liveSource = behavior.sourceSnapshot,
                    liveTarget = state.liveHero(behavior.change.target),
                ),
            ),
            round,
        )
    }

    fun triggerSpecifiedOngoingDamage(
        target: BattleHeroRef,
        effectId: Int,
        round: Int,
        source: BattleHeroRef? = null,
        detailId: Int? = null,
    ): BattleStateApplyResult {
        val behavior = activeEntries(ongoingDamage)
            .lastOrNull { (key, _) ->
                key.target == target &&
                    key.effectId == effectId &&
                    (source == null || key.source == source) &&
                    (detailId == null || key.detailId == detailId)
            }
            ?.second
            ?: return BattleStateApplyResult()
        return apply(
            listOf(
                behavior.change.tick(
                    liveSource = behavior.sourceSnapshot,
                    liveTarget = state.liveHero(behavior.change.target),
                ),
            ),
            round,
        )
    }

    fun onRoundEnd(round: Int): BattleStateApplyResult {
        require(round > 0) { "round must be positive: $round" }
        require(round >= maxOf(lastStartedRound, lastEndedRound)) {
            "round moved backward: current=${maxOf(lastStartedRound, lastEndedRound)} requested=$round"
        }
        if (round == lastEndedRound) return BattleStateApplyResult()
        lastEndedRound = round
        val actionTickedKeys = (
            activeEntries(ongoingDamage).map { it.first } +
                activeEntries(ongoingRecovery).map { it.first }
            ).toSet()
        val lifecycle = synchronize(
            state.effectStore.tick(EffectTickBoundary.ROUND_END) { effect ->
                effect.key() !in actionTickedKeys
            },
        )
        recalculateStats()
        return BattleStateApplyResult(lifecycle)
    }

    fun permissionFor(
        actor: BattleHeroRef,
        context: SkillBattleContext? = null,
    ): BattleStatePermission {
        val effects = state.effectStore.effectsFor(actor)
        val resolver = ActionPermissionResolver(state.effectStore)
        val base: ActionPermission = if (context == null) {
            resolver.permissionFor(actor)
        } else {
            resolver.permissionFor(actor, context)
        }
        val secondaryAttack = effects.any { it.effectId == 545 }
        val redirect = activeEntries(redirections)
            .asSequence()
            .filter { (_, behavior) ->
                behavior.sharePercent == 100 && actor in behavior.protectedTargets
            }
            .lastOrNull()
            ?.second
            ?.damageBearer
        return BattleStatePermission(
            canAct = base.canAct,
            canCastActive = base.canCastActive,
            canNormalAttack = base.canNormalAttack,
            normalAttackCount = base.normalAttackCount,
            pursuitOpportunityCount = if (base.canNormalAttack) base.normalAttackCount else 0,
            splitAttack = secondaryAttack,
            counterattack = base.counterattack,
            canEvade = resolver.canEvade(actor, context = context),
            ignoresEvade = effects.any { it.effectId == 515 },
            firstAction = base.firstAction,
            damageRedirectTarget = redirect,
        )
    }

    fun canEvade(
        target: BattleHeroRef,
        attacker: BattleHeroRef,
        context: SkillBattleContext,
    ): Boolean =
        ActionPermissionResolver(state.effectStore).canEvade(
            target = target,
            attacker = attacker,
            context = context,
        )

    private fun preflight(
        change: BattleStateChange,
        delayedActivation: Boolean,
    ) {
        when (change) {
            is TroopDamageChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.amount >= 0) { "damage amount must not be negative" }
            }
            is RecoverTroopsChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.amount >= 0) { "recovery amount must not be negative" }
            }
            is TroopRecoveryChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.amount >= 0) { "recovery amount must not be negative" }
            }
            is ConsumeWoundedTroopsChange -> {
                requireHero(change.target)
                require(change.amount >= 0) { "wounded consumption must not be negative" }
            }
            is WoundedPoolChange -> requireHero(change.target)
            is BattleStatChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.durationRounds > 0 || change.availableHits > 0) {
                    "stat modifier must have a positive round or hit lifecycle"
                }
                require(
                    change.potency.unit == BattleEffectValueUnit.FLAT ||
                        change.potency.unit == BattleEffectValueUnit.PERCENT,
                ) { "stat potency must be flat or percent" }
                require(change.potency.value != 0) { "stat potency must not be zero" }
                statEffect(change)
            }
            is DamageModifierChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.durationRounds > 0 || change.availableHits > 0) {
                    "damage modifier must have a positive round or hit lifecycle"
                }
                require(change.percent != 0) { "damage modifier percent must not be zero" }
                modifierEffect(change)
            }
            is UpdateDamageModifierStrengthChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.percent <= 0) {
                    "damage reduction strength update must not be positive"
                }
            }
            is SetRecoveryTakenModifierLayersChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.percentPerLayer > 0)
                require(change.maxLayers > 0)
                require(change.layers in 0..change.maxLayers)
            }
            is ModifierEffectChange -> validateSpec(change.spec, delayedActivation)
            is ApplyBattleEffectChange -> validateSpec(change.spec, delayedActivation)
            is ForcedTargetEffectChange -> {
                validateSpec(change.spec, delayedActivation)
                requireHero(change.forcedTarget)
            }
            is SharedEffectUseGroupChange -> {
                validateSpec(change.spec, delayedActivation)
                require(change.memberDetailId > 0)
            }
            is DamageAbsorptionAccumulatorEffectChange -> {
                validateSpec(change.spec, delayedActivation)
                require(change.spec.target == change.spec.source) {
                    "damage absorption accumulator must be owned by its source"
                }
                require(change.protectedTargets.isNotEmpty()) {
                    "damage absorption accumulator must protect at least one target"
                }
                change.protectedTargets.forEach { target ->
                    requireHero(target)
                    require(target.side == change.spec.source.side) {
                        "damage absorption target must be allied with its owner"
                    }
                }
                require(change.absorbPercent in 1..100) {
                    "damage absorption percent must be within 1..100"
                }
            }
            is DamageReleaseScheduleEffectChange -> {
                validateSpec(change.spec, delayedActivation)
                requireHero(change.target)
                require(change.spec.source == change.target) {
                    "damage release target must be its source"
                }
                require(change.referencedDetailId > 0)
                require(change.referencedEffectId > 0)
                require(change.baseReleasePercent in 0..100)
                require(change.firstReleaseRound > 0)
            }
            is ScheduledDamageEffectChange -> validateSpec(change.spec, delayedActivation)
            is ScheduledRecoveryEffectChange -> validateSpec(change.spec, delayedActivation)
            is ActionEffectChange -> validateSpec(change.spec, delayedActivation)
            is DamageRedirectionEffectChange -> {
                validateSpec(change.spec, delayedActivation)
                change.protectedTargets.forEach(::requireHero)
                requireHero(change.damageBearer)
                require(change.sharePercent in 1..100) {
                    "damage share percent must be within 1..100"
                }
            }
            is LinkedDamageSharingEffectChange -> {
                validateSpec(change.spec, delayedActivation)
                change.members.forEach(::requireHero)
                require(change.sharePercentPerAlly in 1..100)
            }
            is CancelPreparedSkillsChange -> validateSpec(change.spec, delayedActivation)
            is CleanseEffectsChange -> validateSpec(change.spec, delayedActivation)
            is ScheduledEffectActivationChange ->
                throw IllegalArgumentException(
                    "ScheduledEffectActivationChange must be expanded through applyActivated at its due boundary",
                )
            is EffectBlockedChange -> {
                requireHero(change.source)
                requireHero(change.target)
            }
            is ClearReferencedEffectChange -> {
                requireHero(change.source)
                requireHero(change.target)
            }
            is ReduceReferencedEffectUseChange -> {
                requireHero(change.source)
                requireHero(change.target)
                require(change.amount >= 0) { "referenced effect use reduction must not be negative" }
            }
            is MoraleEffectChange -> {
                requireHero(change.source)
                requireHero(change.target)
            }
            else -> throw UnsupportedBattleStateChangeException(change)
        }
    }

    private fun applyOne(
        change: BattleStateChange,
        outputs: MutableList<BattleStateOutput>,
        recovered: MutableMap<RecoveryKey, Int>,
    ) {
        when (change) {
            is TroopDamageChange -> applyDamage(change, outputs)
            is RecoverTroopsChange -> applyRecovery(
                change.source,
                change.target,
                change.amount,
                change.skillId,
                change.effectId,
                true,
                outputs,
                recovered,
            )
            is TroopRecoveryChange -> applyRecovery(
                change.source,
                change.target,
                change.amount,
                change.skillId,
                change.effectId,
                false,
                outputs,
                recovered,
            )
            is ConsumeWoundedTroopsChange -> {
                val target = state.mutable(change.target)
                val key = RecoveryKey(change.target, change.skillId, change.effectId)
                val pairedRecovery = recovered[key]
                val amount = minOf(
                    change.amount,
                    target.woundedTroops,
                    pairedRecovery ?: change.amount,
                )
                target.woundedTroops -= amount
                if (pairedRecovery != null) recovered[key] = (pairedRecovery - amount).coerceAtLeast(0)
            }
            is WoundedPoolChange -> {
                val target = state.mutable(change.target)
                target.woundedTroops = (target.woundedTroops + change.delta).coerceAtLeast(0)
            }
            is BattleStatChange -> {
                val valueBefore = state.mutable(change.target).stats.preciseValue(change.kind)
                val effect = statEffect(change)
                val accepted = applyBehavior(effect, outputs) { key, _ ->
                    statModifiers[key] = StatModifier(
                        change.kind,
                        change.potency.unit,
                        if (change.potency.value < 0) -1 else 1,
                    )
                }
                if (accepted) {
                    recalculateStats(change.target)
                    val valueAfter = state.mutable(change.target).stats.preciseValue(change.kind)
                    outputs += BattleStateOutput.StatChanged(
                        change = change,
                        strength = kotlin.math.abs(change.potency.value),
                        delta = (valueAfter - valueBefore).toInt(),
                        valueAfter = valueAfter.toInt(),
                        deltaExact = valueAfter - valueBefore,
                        valueAfterExact = valueAfter,
                    )
                }
            }
            is DamageModifierChange -> {
                val effect = modifierEffect(change)
                val accepted = applyBehavior(effect, outputs) { key, acceptedEffect ->
                    damageModifiers[key] = DamageModifier(
                        change.direction,
                        change.school,
                        change.origin,
                        change.tag,
                        if (change.percent < 0) -1 else 1,
                        change.requiredTargetStatus,
                        change.targetSkillId,
                        change.targetSkillIds,
                    )
                    state.effectModifiers[key] = change.toBattleModifier(
                        acceptedEffect.effectiveStrength,
                    )
                }
                if (accepted) outputs += BattleStateOutput.ModifierApplied(change)
            }
            is UpdateDamageModifierStrengthChange -> {
                val key = damageModifiers.keys.singleOrNull { candidate ->
                    candidate.source == change.source &&
                        candidate.target == change.target &&
                        candidate.skillId == change.skillId &&
                        candidate.detailId == change.detailId &&
                        candidate.effectId == change.effectId
                } ?: error(
                    "Missing damage modifier for strength update: " +
                        "target=${change.target} source=${change.source} " +
                        "skill=${change.skillId} detail=${change.detailId} " +
                        "effect=${change.effectId}",
                )
                val behavior = requireNotNull(damageModifiers[key])
                val updated = requireNotNull(
                    state.effectStore.setSingleLayerStrength(
                        target = change.target,
                        source = change.source,
                        skillId = change.skillId,
                        detailId = change.detailId,
                        effectId = change.effectId,
                        strength = kotlin.math.abs(change.percent),
                    ),
                )
                val applied = behavior.toChange(key, updated)
                state.effectModifiers[key] = applied.toBattleModifier(
                    updated.effectiveStrength,
                )
                outputs += BattleStateOutput.ModifierApplied(applied)
            }
            is SetRecoveryTakenModifierLayersChange ->
                applyRecoveryTakenModifierLayers(change, outputs)
            is ModifierEffectChange -> applyEffect(change.spec, outputs) { key, accepted ->
                state.effectModifiers[key] = change.modifier.withEffectiveStrength(
                    accepted.effectiveStrength,
                )
            }
            is ApplyBattleEffectChange -> applyEffect(change.spec, outputs) { key, _ ->
                statusFor(change.spec.effectId)?.let { state.effectStatuses[key] = it }
            }
            is ForcedTargetEffectChange -> applyEffect(change.spec, outputs) { key, _ ->
                forcedTargets[key] = change.forcedTarget
            }
            is SharedEffectUseGroupChange -> applyEffect(change.spec, outputs) { key, _ ->
                sharedEffectUseMembers[key] = change.memberDetailId
            }
            is DamageAbsorptionAccumulatorEffectChange ->
                applyEffect(change.spec, outputs) { key, _ ->
                    damageAbsorptions[key] = DamageAbsorptionAccumulator(
                        protectedTargets = change.protectedTargets.distinct(),
                        absorbPercent = change.absorbPercent,
                    )
                }
            is DamageReleaseScheduleEffectChange ->
                applyEffect(change.spec, outputs) { key, _ ->
                    damageReleases[key] = DamageReleaseSchedule(
                        target = change.target,
                        referencedDetailId = change.referencedDetailId,
                        referencedEffectId = change.referencedEffectId,
                        baseReleasePercent = change.baseReleasePercent,
                        firstReleaseRound = change.firstReleaseRound,
                    )
                }
            is ScheduledDamageEffectChange -> {
                applyEffect(change.spec, outputs) { key, _ ->
                    ongoingDamage[key] = OngoingDamageBehavior(
                        change,
                        state.liveHero(change.source),
                    )
                    state.effectStatuses[key] = change.status
                }
            }
            is ScheduledRecoveryEffectChange -> {
                applyEffect(change.spec, outputs) { key, _ ->
                    ongoingRecovery[key] = change
                }
            }
            is ActionEffectChange -> applyEffect(change.spec, outputs) { key, _ ->
                statusFor(change.spec.effectId)?.let { state.effectStatuses[key] = it }
                if (change.kind == ActionEffectKind.IGNORE_TROOP_COUNTER) {
                    state.effectModifiers[key] = BattleModifier.TroopCounterImmunity
                }
            }
            is DamageRedirectionEffectChange -> {
                applyEffect(change.spec, outputs) { key, _ ->
                    redirections[key] = Redirection(
                        change.protectedTargets.toList(),
                        change.damageBearer,
                        change.sharePercent,
                        change.school,
                    )
                }
            }
            is LinkedDamageSharingEffectChange -> {
                applyEffect(change.spec, outputs) { key, _ ->
                    linkedSharings[key] = LinkedSharing(
                        change.members.distinct(),
                        change.sharePercentPerAlly,
                    )
                }
            }
            is CancelPreparedSkillsChange -> change.apply(state.runtime)
            is CleanseEffectsChange -> {
                outputs += synchronize(change.apply(state.effectStore))
                recalculateStats()
            }
            is EffectBlockedChange -> outputs += BattleStateOutput.EffectBlocked(change)
            is ClearReferencedEffectChange -> {
                outputs += synchronize(change.apply(state.effectStore))
                recalculateStats(change.target)
            }
            is ReduceReferencedEffectUseChange -> {
                outputs += synchronize(change.apply(state.effectStore))
                recalculateStats(change.target)
            }
            is ConsumeEffectUseChange -> {
                outputs += synchronize(change.apply(state.effectStore))
                recalculateStats(change.target)
            }
            is MoraleEffectChange -> {
                val target = state.mutable(change.target)
                target.morale = (target.morale + change.delta).coerceAtLeast(0)
            }
            else -> throw UnsupportedBattleStateChangeException(change)
        }
    }

    private fun applyDamage(
        change: TroopDamageChange,
        outputs: MutableList<BattleStateOutput>,
    ) {
        if (DamageTag.IMPERIAL_SEAL_RELEASE in change.tags) {
            applyDirectDamage(change, outputs)
            return
        }
        val linked = activeEntries(linkedSharings)
            .lastOrNull { (_, behavior) -> change.target in behavior.members }
        if (linked != null) {
            val behavior = linked.second
            val bearers = behavior.members.filter { it != change.target }
            val each = change.amount.toLong()
                .times(behavior.sharePercentPerAlly)
                .div(100)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            val retained = (change.amount - each * bearers.size).coerceAtLeast(0)
            applyDirectDamage(change.copy(amount = retained), outputs)
            bearers.forEach { bearer ->
                applyDirectDamage(
                    change.copy(
                        target = bearer,
                        amount = each,
                        troopsAfter = (state.mutable(bearer).troops - each).coerceAtLeast(0),
                    ),
                    outputs,
                )
            }
            return
        }
        val sharing = activeEntries(redirections)
            .lastOrNull { (_, behavior) ->
                behavior.sharePercent < 100 &&
                    change.target in behavior.protectedTargets &&
                    behavior.damageBearer != change.target &&
                    (behavior.school == null || behavior.school == change.school)
            }
        if (sharing != null) {
            val (key, behavior) = sharing
            val sharedAmount = change.amount.toLong()
                .times(behavior.sharePercent)
                .div(100)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            applyDirectDamage(change.copy(amount = change.amount - sharedAmount), outputs)
            applyDirectDamage(
                change.copy(
                    target = behavior.damageBearer,
                    amount = sharedAmount,
                    troopsAfter = (
                        state.mutable(behavior.damageBearer).troops - sharedAmount
                        ).coerceAtLeast(0),
                ),
                outputs,
            )
            val active = state.effectStore.effectsFor(key.target)
                .singleOrNull { it.key() == key }
            if (active?.remainingHits != null) {
                outputs += synchronize(
                    state.effectStore.consumeHit(
                        target = key.target,
                        effectId = key.effectId,
                        source = key.source,
                        detailId = key.detailId,
                    ),
                )
            }
            return
        }
        applyDirectDamage(change, outputs)
    }

    private fun applyDirectDamage(
        change: TroopDamageChange,
        outputs: MutableList<BattleStateOutput>,
    ) {
        val target = state.mutable(change.target)
        val incomingAmount = change.amount.coerceAtLeast(0).coerceAtMost(target.troops)
        val absorptions = if (DamageTag.IMPERIAL_SEAL_RELEASE in change.tags) {
            emptyList()
        } else {
            activeEntries(damageAbsorptions)
                .filter { (_, behavior) -> change.target in behavior.protectedTargets }
                .sortedWith(
                    compareBy<Pair<EffectKey, DamageAbsorptionAccumulator>> {
                        it.first.source.side.ordinal
                    }.thenBy { it.first.source.position }
                        .thenBy { it.first.source.heroId.value }
                        .thenBy { it.first.rootSkillId },
                )
        }
        var retainedAmount = incomingAmount
        absorptions.forEach { (key, behavior) ->
            val absorbedAmount = incomingAmount.toLong()
                .times(behavior.absorbPercent)
                .div(100)
                .coerceAtMost(retainedAmount.toLong())
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            if (absorbedAmount <= 0) return@forEach
            retainedAmount -= absorbedAmount
            behavior.currentRoundAbsorbedDamage = behavior.currentRoundAbsorbedDamage.toLong()
                .plus(absorbedAmount)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            outputs += BattleStateOutput.DamageAbsorbed(
                owner = key.source,
                target = change.target,
                amount = absorbedAmount,
                currentRoundTotal = behavior.currentRoundAbsorbedDamage,
                percent = behavior.absorbPercent,
            )
        }
        val amount = retainedAmount
        target.troops -= amount
        target.woundedTroops += amount.toLong()
            .times(WOUNDED_TROOP_CONVERSION_PERCENT)
            .div(100)
            .toInt()
        state.recordDamage(change.source, amount)
        outputs += BattleStateOutput.DamageDealt(
            change.source,
            change.target,
            amount,
            change.school,
            change.origin,
            change.tags.toSet(),
            change.skillId,
            change.effectId,
            change.calculation,
        )
        outputs += BattleStateOutput.HurtReceived(
            change.source,
            change.target,
            amount,
            change.school,
            change.origin,
            change.tags.toSet(),
            change.skillId,
            change.effectId,
        )
        activeEntries(state.effectModifiers)
            .filter { (key, modifier) ->
                key.target == change.source &&
                    modifier == BattleModifier.TroopCounterImmunity &&
                    state.effectStore.effectsFor(key.target)
                        .singleOrNull { it.key() == key }
                        ?.remainingHits != null
            }
            .map { it.first }
            .forEach { key ->
                outputs += synchronize(
                    state.effectStore.consumeHit(
                        target = key.target,
                        effectId = key.effectId,
                        source = key.source,
                        detailId = key.detailId,
                    ),
                )
            }
        activeEntries(damageModifiers)
            .filter { (key, modifier) ->
                modifier.matches(
                    owner = key.target,
                    change = change,
                    ownerStatuses = state.liveHero(key.target).activeStatuses,
                ) &&
                    state.effectStore.effectsFor(key.target)
                        .singleOrNull { it.key() == key }
                        ?.remainingHits != null
            }
            .map { it.first }
            .forEach { key ->
                outputs += synchronize(
                    state.effectStore.consumeHit(
                        target = key.target,
                        effectId = key.effectId,
                        source = key.source,
                        detailId = key.detailId,
                    ),
                )
            }
    }

    private fun applyRecoveryTakenModifierLayers(
        change: SetRecoveryTakenModifierLayersChange,
        outputs: MutableList<BattleStateOutput>,
    ) {
        outputs += synchronize(
            state.effectStore.clearMatching(change.target) { effect ->
                effect.source == change.source &&
                    effect.detailId == change.detailId &&
                    effect.effectId == change.effectId
            },
        )
        repeat(change.layers) {
            var appliedPercent: Int? = null
            applyEffect(
                PersistentEffectSpec(
                    source = change.source,
                    target = change.target,
                    rootSkillId = change.rootSkillId,
                    skillId = change.skillId,
                    skillKind = SkillKind.PASSIVE,
                    rawSkillType = 17,
                    detailId = change.detailId,
                    effectId = change.effectId,
                    category = EffectCategory.NEUTRAL,
                    conflict = 0,
                    replaceType = 0,
                    bindFlag = 0,
                    maxStacks = change.maxLayers,
                    delayRound = 0,
                    delayHit = 0,
                    availableRounds = Int.MAX_VALUE,
                    availableHit = 0,
                    clearPerHit = false,
                    startBoundary = EffectStartBoundary.IMMEDIATE,
                    potency = TypedBattlePotency.percent(change.percentPerLayer),
                ),
                outputs,
            ) { key, accepted ->
                appliedPercent = accepted.effectiveStrength
                state.effectModifiers[key] = BattleModifier.RecoveryTakenPercent(
                    accepted.effectiveStrength,
                )
            }
            appliedPercent?.let { percent ->
                outputs += BattleStateOutput.RecoveryModifierApplied(
                    source = change.source,
                    target = change.target,
                    skillId = change.skillId,
                    effectId = change.effectId,
                    percent = percent,
                    durationRounds = Int.MAX_VALUE,
                )
            }
        }
    }

    private fun applyRecovery(
        source: BattleHeroRef,
        targetRef: BattleHeroRef,
        requestedAmount: Int,
        skillId: Int,
        effectId: Int,
        limitedByWounded: Boolean,
        outputs: MutableList<BattleStateOutput>,
        recovered: MutableMap<RecoveryKey, Int>,
    ) {
        val target = state.mutable(targetRef)
        val room = (target.entry.maxTroops - target.troops).coerceAtLeast(0)
        val limit = if (limitedByWounded) target.woundedTroops else Int.MAX_VALUE
        val recoveryDealtPercent = state.liveHero(source).modifiers
            .filterIsInstance<BattleModifier.RecoveryDealtPercent>()
            .sumOf { it.percent }
        val recoveryTakenPercent = state.liveHero(targetRef).modifiers
            .filterIsInstance<BattleModifier.RecoveryTakenPercent>()
            .sumOf { it.percent }
        val recoveryPercent = recoveryDealtPercent + recoveryTakenPercent
        val modifiedAmount = requestedAmount.coerceAtLeast(0).toLong() *
            (100 + recoveryPercent).coerceAtLeast(0) / 100
        val amount = minOf(modifiedAmount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), room, limit)
        target.troops += amount
        recovered[RecoveryKey(targetRef, skillId, effectId)] =
            (recovered[RecoveryKey(targetRef, skillId, effectId)] ?: 0) + amount
        outputs += BattleStateOutput.TroopsRecovered(source, targetRef, amount, skillId, effectId)
    }

    private fun applyEffect(
        spec: PersistentEffectSpec,
        outputs: MutableList<BattleStateOutput>,
        onAccepted: (EffectKey, ActiveSkillEffect) -> Unit = { _, _ -> },
    ) {
        spec.toActiveSkillEffectOrNull()?.let { effect ->
            if (applyBehavior(effect, outputs, onAccepted)) {
                outputs += BattleStateOutput.EffectApplied(spec)
            }
        }
    }

    private fun applyBehavior(
        effect: ActiveSkillEffect,
        outputs: MutableList<BattleStateOutput>,
        onAccepted: (EffectKey, ActiveSkillEffect) -> Unit,
    ): Boolean {
        val result = state.effectStore.apply(effect)
        synchronizeRemoved(result.removed)
        outputs += result.removed.map(BattleStateOutput::EffectRemoved)
        if (result.outcome == EffectApplyOutcome.REJECTED) return false
        val accepted = requireNotNull(result.effect)
        val key = accepted.key()
        if (result.outcome == EffectApplyOutcome.STACKED ||
            result.outcome == EffectApplyOutcome.REFRESHED
        ) {
            removeBehavior(key)
        }
        onAccepted(key, accepted)
        return true
    }

    private fun statEffect(change: BattleStatChange): ActiveSkillEffect =
        PersistentEffectSpec(
            source = change.source,
            target = change.target,
            rootSkillId = change.skillId,
            skillId = change.skillId,
            skillKind = SkillKind.COMMAND,
            rawSkillType = 2,
            detailId = change.detailId,
            effectId = change.effectId,
            category =
                if (change.potency.value > 0) EffectCategory.BENEFICIAL else EffectCategory.HARMFUL,
            conflict = 0,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = change.maxStacks,
            delayRound = 0,
            delayHit = 0,
            availableRounds = change.durationRounds,
            availableHit = change.availableHits,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = change.potency,
        ).toActiveSkillEffect()

    private fun recalculateStats(target: BattleHeroRef? = null) {
        pruneInactiveBehaviors()
        val targets = target?.let(::listOf) ?: state.view.heroes()
        targets.forEach { ref ->
            val mutable = state.mutable(ref)
            val entry = mutable.entry.stats
            val inherent = mutable.inherentStats
            val values = BattleStatChange.Kind.entries.associateWith { kind ->
                val base = entry.preciseValue(kind)
                val percentBase = inherent.preciseValue(kind)
                val modifiers = activeEntries(statModifiers)
                    .filter { (key, modifier) -> key.target == ref && modifier.kind == kind }
                val flat = modifiers
                    .filter { (_, modifier) -> modifier.unit == BattleEffectValueUnit.FLAT }
                    .sumOf { (key, modifier) -> key.strengthExact() * modifier.sign }
                val percent = modifiers
                    .filter { (_, modifier) -> modifier.unit == BattleEffectValueUnit.PERCENT }
                    .sumOf { (key, modifier) -> key.strengthExact() * modifier.sign }
                base + percentBase * percent / 100.0 + flat
            }
            mutable.stats = BattleStats.fromHundredths(
                attack = (values.getValue(BattleStatChange.Kind.ATTACK) * 100).roundToInt(),
                defense = (values.getValue(BattleStatChange.Kind.DEFENSE) * 100).roundToInt(),
                strategy = (values.getValue(BattleStatChange.Kind.STRATEGY) * 100).roundToInt(),
                speed = (values.getValue(BattleStatChange.Kind.SPEED) * 100).roundToInt(),
                siege = (values.getValue(BattleStatChange.Kind.SIEGE) * 100).roundToInt(),
                hitRange = values.getValue(BattleStatChange.Kind.ATTACK_RANGE).roundToInt(),
            )
        }
    }

    private data class RecoveryKey(
        val target: BattleHeroRef,
        val skillId: Int,
        val effectId: Int,
    )

    private fun requireHero(ref: BattleHeroRef) {
        require(state.contains(ref)) { "Unknown battle hero: $ref" }
    }

    private fun validateSpec(
        spec: PersistentEffectSpec,
        delayedActivation: Boolean,
    ) {
        requireHero(spec.source)
        requireHero(spec.target)
        if (spec.startBoundary == EffectStartBoundary.AFTER_DELAY && !delayedActivation) {
            throw IllegalArgumentException(
                "Effect detail=${spec.detailId} is AFTER_DELAY and cannot apply before activation",
            )
        }
        spec.toActiveSkillEffectOrNull()
    }

    private fun modifierEffect(change: DamageModifierChange): ActiveSkillEffect =
        PersistentEffectSpec(
            source = change.source,
            target = change.target,
            rootSkillId = change.skillId,
            skillId = change.skillId,
            skillKind = SkillKind.COMMAND,
            rawSkillType = 2,
            detailId = change.detailId,
            effectId = change.effectId,
            category =
                when (change.direction) {
                    DamageModifierChange.Direction.DEALT ->
                        if (change.percent > 0) EffectCategory.BENEFICIAL else EffectCategory.HARMFUL
                    DamageModifierChange.Direction.TAKEN ->
                        if (change.percent > 0) EffectCategory.HARMFUL else EffectCategory.BENEFICIAL
                },
            conflict = 0,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = change.maxStacks,
            delayRound = 0,
            delayHit = 0,
            availableRounds = change.durationRounds,
            availableHit = change.availableHits,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.percent(change.percent),
        ).toActiveSkillEffect()

    private fun DamageModifierChange.toBattleModifier(effectiveStrength: Int): BattleModifier {
        val signedStrength = effectiveStrength * if (percent < 0) -1 else 1
        return when (direction) {
            DamageModifierChange.Direction.DEALT -> BattleModifier.DamageDealtPercent(
                school = school,
                origin = origin,
                tag = tag,
                percent = signedStrength,
                skillId = targetSkillId,
                skillIds = targetSkillIds,
            )
            DamageModifierChange.Direction.TAKEN -> BattleModifier.DamageTakenPercent(
                school = school,
                origin = origin,
                tag = tag,
                percent = signedStrength,
                requiredStatus = requiredTargetStatus,
            )
        }
    }

    private fun BattleModifier.withEffectiveStrength(effectiveStrength: Int): BattleModifier =
        when (this) {
            is BattleModifier.RecoveryDealtPercent -> copy(percent = effectiveStrength)
            is BattleModifier.RecoveryTakenPercent -> copy(percent = effectiveStrength)
            else -> this
        }

    private fun DamageModifier.toChange(
        key: EffectKey,
        effect: ActiveSkillEffect,
    ): DamageModifierChange =
        DamageModifierChange(
            source = key.source,
            target = key.target,
            direction = direction,
            school = school,
            origin = origin,
            tag = tag,
            percent = effect.effectiveStrength * sign,
            durationRounds = effect.remainingRounds ?: 1,
            skillId = key.skillId,
            effectId = key.effectId,
            detailId = key.detailId,
            availableHits = effect.remainingHits ?: 0,
            maxStacks = effect.maxStacks,
            requiredTargetStatus = requiredTargetStatus,
            targetSkillId = targetSkillId,
            targetSkillIds = targetSkillIds,
        )

    private fun synchronize(result: EffectLifecycleResult): List<BattleStateOutput> {
        synchronizeRemoved(result.expired + result.removed)
        pruneInactiveBehaviors()
        return result.expired.map(BattleStateOutput::EffectExpired) +
            result.removed.map(BattleStateOutput::EffectRemoved)
    }

    private fun synchronizeRemoved(removed: List<ActiveSkillEffect>) {
        removed.forEach { removeBehavior(it.key()) }
    }

    private fun removeBehavior(key: EffectKey) {
        statModifiers.remove(key)
        damageModifiers.remove(key)
        ongoingDamage.remove(key)
        ongoingRecovery.remove(key)
        redirections.remove(key)
        linkedSharings.remove(key)
        forcedTargets.remove(key)
        sharedEffectUseMembers.remove(key)
        damageAbsorptions.remove(key)
        damageReleases.remove(key)
        state.effectStatuses.remove(key)
        state.effectModifiers.remove(key)
    }

    private fun pruneInactiveBehaviors() {
        val activeKeys = state.view.heroes()
            .flatMap(state.effectStore::effectsFor)
            .mapTo(mutableSetOf()) { it.key() }
        (
            statModifiers.keys + damageModifiers.keys + ongoingDamage.keys +
                ongoingRecovery.keys + redirections.keys + linkedSharings.keys + forcedTargets.keys +
                sharedEffectUseMembers.keys + damageAbsorptions.keys + damageReleases.keys +
                state.effectStatuses.keys + state.effectModifiers.keys
            )
            .filterNot { it in activeKeys }
            .forEach(::removeBehavior)
    }

    private fun <T> activeEntries(
        behaviors: Map<EffectKey, T>,
    ): List<Pair<EffectKey, T>> {
        val effects = state.view.heroes()
            .flatMap(state.effectStore::effectsFor)
            .associateBy { it.key() }
        return behaviors.mapNotNull { (key, behavior) ->
            effects[key]?.let { key to behavior }
        }
    }

    private fun EffectKey.strength(): Int =
        state.effectStore.effectsFor(target)
            .singleOrNull { it.key() == this }
            ?.effectiveStrength
            ?: 0

    private fun EffectKey.strengthExact(): Double =
        state.effectStore.effectsFor(target)
            .singleOrNull { it.key() == this }
            ?.effectiveStrengthExact
            ?: 0.0

    private fun statusFor(effectId: Int): BattleStatus? = when (effectId) {
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

    private fun BattleStats.value(kind: BattleStatChange.Kind): Int = when (kind) {
        BattleStatChange.Kind.ATTACK -> attack
        BattleStatChange.Kind.DEFENSE -> defense
        BattleStatChange.Kind.STRATEGY -> strategy
        BattleStatChange.Kind.SPEED -> speed
        BattleStatChange.Kind.SIEGE -> siege
        BattleStatChange.Kind.ATTACK_RANGE -> hitRange
    }

    private fun BattleStats.preciseValue(kind: BattleStatChange.Kind): Double = when (kind) {
        BattleStatChange.Kind.ATTACK -> precise(BattleStat.ATTACK)
        BattleStatChange.Kind.DEFENSE -> precise(BattleStat.DEFENSE)
        BattleStatChange.Kind.STRATEGY -> precise(BattleStat.STRATEGY)
        BattleStatChange.Kind.SPEED -> precise(BattleStat.SPEED)
        BattleStatChange.Kind.SIEGE -> precise(BattleStat.SIEGE)
        BattleStatChange.Kind.ATTACK_RANGE -> hitRange.toDouble()
    }

    private companion object {
        const val WOUNDED_TROOP_CONVERSION_PERCENT = 95
        const val WOUNDED_TROOP_RETENTION_PERCENT = 87
    }
}

private fun ActiveSkillEffect.key(): EffectKey =
    EffectKey(
        source = source,
        target = target,
        rootSkillId = rootSkillId,
        skillId = skillId,
        skillKind = skillKind,
        sourceSkillType = sourceSkillType,
        detailId = detailId,
        effectId = effectId,
        category = category,
        conflict = conflict,
        replaceType = replaceType,
        bindFlag = bindFlag,
        maxStacks = maxStacks,
        clearPerHit = clearPerHit,
        clearable = clearable,
    )
