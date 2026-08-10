package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleModifier
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillKind

enum class SkillHeroGender {
    MALE,
    FEMALE,
    UNKNOWN,
}

enum class SkillTroopType {
    ARCHER,
    INFANTRY,
    CAVALRY,
    UNKNOWN,
}

enum class SkillTroopCategory {
    RATTAN_ARMOR,
    BARBARIAN,
    ELEPHANT,
}

enum class SkillTargetStateFilter(val rawCode: Int) {
    FLAG_1(1),
    FLAG_2(2),
    FLAG_3(3),
    FLAG_99(99),
    ;

    companion object {
        fun fromRaw(rawCode: Int): SkillTargetStateFilter =
            entries.singleOrNull { it.rawCode == rawCode }
                ?: throw IllegalArgumentException("Unsupported select_flag=$rawCode")
    }
}

enum class SkillBattleViewCapability {
    HERO_ROSTER,
    ENTRY_STATE,
    LIVE_STATE,
    HERO_METADATA,
    DAMAGE_HISTORY,
    LIVE_MORALE,
    NORMAL_ATTACK_RANGE,
    TARGET_HISTORY,
    STATE_FILTERS,
    ACTIVE_EFFECTS,
}

class MissingLiveBattleViewData(
    capability: SkillBattleViewCapability,
    operation: String,
) : IllegalStateException("Missing live battle-view data: capability=$capability operation=$operation")

data class SkillBattleHeroState(
    val stats: BattleStats,
    val troops: Int,
    val maxTroops: Int,
    val statuses: Set<BattleStatus>,
    val morale: Int,
    val attackRange: Int,
    val canReceiveEffectsWhenDefeated: Boolean = false,
    val woundedTroops: Int = 0,
    val modifiers: List<BattleModifier>? = null,
)

data class SkillBattleHeroMetadata(
    val gender: SkillHeroGender,
    val troopType: SkillTroopType,
    val troopCategories: Set<SkillTroopCategory> = emptySet(),
    val country: Int = 0,
)

interface SkillBattleView {
    val capabilities: Set<SkillBattleViewCapability>

    fun heroes(): List<BattleHeroRef>

    fun entryState(ref: BattleHeroRef): SkillBattleHeroState?

    fun state(ref: BattleHeroRef): SkillBattleHeroState?

    fun metadata(ref: BattleHeroRef): SkillBattleHeroMetadata?

    fun accumulatedDamageDealt(ref: BattleHeroRef): Int

    fun currentMorale(ref: BattleHeroRef): Int?

    fun currentAttackRange(ref: BattleHeroRef): Int?

    fun skillRangeBonus(ref: BattleHeroRef, kind: SkillKind): Int = 0

    fun skillRangeBonus(
        ref: BattleHeroRef,
        kind: SkillKind,
        skillId: Int,
    ): Int = skillRangeBonus(ref, kind)

    fun linkedTarget(source: BattleHeroRef): BattleHeroRef?

    fun currentTarget(source: BattleHeroRef): BattleHeroRef?

    fun previousTarget(source: BattleHeroRef): BattleHeroRef?

    fun matchesStateFilter(
        filter: SkillTargetStateFilter,
        source: BattleHeroRef,
        target: BattleHeroRef,
    ): Boolean

    fun activeEffectIds(ref: BattleHeroRef): Set<Int> =
        throw MissingLiveBattleViewData(
            SkillBattleViewCapability.ACTIVE_EFFECTS,
            "activeEffectIds",
        )

    fun activeEffectStrength(ref: BattleHeroRef, detailId: Int): Int =
        throw MissingLiveBattleViewData(
            SkillBattleViewCapability.ACTIVE_EFFECTS,
            "activeEffectStrength",
        )

    companion object {
        fun entrySnapshot(request: BattleRequest): SkillBattleView =
            EntrySnapshotSkillBattleView(request)
    }
}

private class EntrySnapshotSkillBattleView(
    request: BattleRequest,
) : SkillBattleView {
    override val capabilities: Set<SkillBattleViewCapability> = setOf(
        SkillBattleViewCapability.HERO_ROSTER,
        SkillBattleViewCapability.ENTRY_STATE,
    )
    private val states = buildMap {
        request.attacker.heroes.forEach { hero ->
            put(
                BattleHeroRef(Side.ATTACKER, hero.position, hero.id),
                hero.toSkillState(),
            )
        }
        request.defender.heroes.forEach { hero ->
            put(
                BattleHeroRef(Side.DEFENDER, hero.position, hero.id),
                hero.toSkillState(),
            )
        }
    }

    override fun heroes(): List<BattleHeroRef> = states.keys.toList()

    override fun entryState(ref: BattleHeroRef): SkillBattleHeroState? = states[ref]

    override fun state(ref: BattleHeroRef): SkillBattleHeroState? =
        missing(SkillBattleViewCapability.LIVE_STATE, "state")

    override fun metadata(ref: BattleHeroRef): SkillBattleHeroMetadata? =
        missing(SkillBattleViewCapability.HERO_METADATA, "metadata")

    override fun accumulatedDamageDealt(ref: BattleHeroRef): Int =
        missing(SkillBattleViewCapability.DAMAGE_HISTORY, "accumulatedDamageDealt")

    override fun currentMorale(ref: BattleHeroRef): Int? =
        missing(SkillBattleViewCapability.LIVE_MORALE, "currentMorale")

    override fun currentAttackRange(ref: BattleHeroRef): Int? =
        missing(SkillBattleViewCapability.NORMAL_ATTACK_RANGE, "currentAttackRange")

    override fun linkedTarget(source: BattleHeroRef): BattleHeroRef? =
        missing(SkillBattleViewCapability.TARGET_HISTORY, "linkedTarget")

    override fun currentTarget(source: BattleHeroRef): BattleHeroRef? =
        missing(SkillBattleViewCapability.TARGET_HISTORY, "currentTarget")

    override fun previousTarget(source: BattleHeroRef): BattleHeroRef? =
        missing(SkillBattleViewCapability.TARGET_HISTORY, "previousTarget")

    override fun matchesStateFilter(
        filter: SkillTargetStateFilter,
        source: BattleHeroRef,
        target: BattleHeroRef,
    ): Boolean = missing(SkillBattleViewCapability.STATE_FILTERS, "matchesStateFilter")

    private fun <T> missing(
        capability: SkillBattleViewCapability,
        operation: String,
    ): T = throw MissingLiveBattleViewData(capability, operation)
}

private fun com.stzb.battle.core.BattleHero.toSkillState() =
    SkillBattleHeroState(
        stats = stats,
        troops = troops,
        maxTroops = maxTroops,
        statuses = activeStatuses,
        morale = morale,
        attackRange = stats.hitRange,
        modifiers = modifiers,
    )

enum class BattleTrigger {
    BATTLE_PASSIVE,
    BATTLE_COMMAND,
    ROUND_START,
    ACTION_BEFORE,
    ACTIVE_SKILL_ATTEMPT,
    NORMAL_ATTACK_BEFORE,
    NORMAL_ATTACK_AFTER,
    DAMAGE_BEFORE,
    DAMAGE_AFTER,
    EFFECT_APPLYING,
    EFFECT_APPLIED,
    HURT_AFTER,
    RECOVERY_AFTER,
    PURSUIT_ATTEMPT,
    ACTION_AFTER,
    ROUND_END,
    BASE_HERO_DEFEATED,
}

data class SkillBattleContext(
    val request: BattleRequest,
    val runtime: SkillRuntimeState,
    val random: BattleRandom,
    val round: Int,
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val currentSkillId: Int,
    val trigger: BattleTrigger,
    val battleView: SkillBattleView = SkillBattleView.entrySnapshot(request),
    val targetDecisions: BattleTargetDecisionSource = BattleTargetDecisionSource.NONE,
    val forcedTargets: BattleForcedTargetSource = BattleForcedTargetSource.NONE,
    val skillProbabilityUses: SkillProbabilityUseSink = SkillProbabilityUseSink.NONE,
    val effectValueScalePercent: Int = 100,
)

fun interface SkillProbabilityUseSink {
    fun consume(
        source: BattleHeroRef,
        skillId: Int,
        skillKind: SkillKind,
    )

    companion object {
        val NONE = SkillProbabilityUseSink { _, _, _ -> }
    }
}

data class PreparedSkill(
    val source: BattleHeroRef,
    val skillId: Int,
    val rootSkillId: Int = skillId,
    val trigger: BattleTrigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
    val startedRound: Int = 0,
    val readyRound: Int,
    val lockedTargets: List<BattleHeroRef>? = null,
)

typealias SkillExecutionSnapshot = PreparedSkill

data class DelayedEffect(
    val source: BattleHeroRef,
    val rootSkillId: Int,
    val skillId: Int,
    val detailId: Int,
    val dueRound: Int,
    val dueHit: Int = 0,
    val sequence: Long = UNSCHEDULED_SEQUENCE,
) {
    companion object {
        const val UNSCHEDULED_SEQUENCE: Long = -1
    }
}
