package com.stzb.server.game.battle

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Damage curves ported from stzbBattleSimulator-main/battleCalcFunc.js.
 * All callers use this module so normal, active, pursuit and ongoing damage
 * cannot drift into separate formulas.
 */
object BattleDamageCalculator {
    fun physical(
        source: BattleHero,
        target: BattleHero,
        ratePercent: Int = 100,
        attributeRandomTenths: Int = 35,
        origin: DamageOrigin? = null,
        tags: Set<DamageTag> = emptySet(),
        skillId: Int? = null,
        targetConditions: Set<DamageTargetCondition> = emptySet(),
    ): Int {
        val rate = ratePercent.coerceAtLeast(1) / 100.0
        val damageFactor = modifierFactor(
            source,
            target,
            DamageSchool.PHYSICAL,
            origin,
            tags,
            skillId,
            targetConditions,
        )
        val troopDamage = source.troops * 373.0 / (7_700 + source.troops)
        val attributeDamage =
            source.stats.attack *
                (attributeRandomTenths.coerceIn(30, 39) / 100.0) *
                rate *
                damageFactor
        val effectiveDefense = ignoredTargetAttribute(source, target.stats.defense, BattleStat.DEFENSE)
        val mainDamage =
            (300.0 * source.troops / (3_500 + source.troops)) *
                rate *
                attackDefenseFactor(source.stats.attack, effectiveDefense) *
                damageFactor
        return (troopDamage + attributeDamage + mainDamage)
            .roundToInt()
            .coerceIn(1, target.troops.coerceAtLeast(1))
    }

    fun strategy(
        source: BattleHero,
        target: BattleHero,
        ratePercent: Int,
        ongoing: Boolean = false,
        origin: DamageOrigin? = null,
        tags: Set<DamageTag> = emptySet(),
        skillId: Int? = null,
        targetConditions: Set<DamageTargetCondition> = emptySet(),
    ): Int {
        val rate = ratePercent.coerceAtLeast(1) / 100.0
        val damageFactor = modifierFactor(
            source,
            target,
            DamageSchool.STRATEGY,
            origin,
            tags,
            skillId,
            targetConditions,
        )
        val effectiveStrategy = ignoredTargetAttribute(source, target.stats.strategy, BattleStat.STRATEGY)
        val strategyFactor = strategyDefenseFactor(effectiveStrategy)
        val troopDamage = source.troops * 178.0 / (6_459 + source.troops) * if (ongoing) 1.0 / 3 else 1.0
        val attributeDamage = source.stats.strategy * (if (ongoing) 0.25 else 0.5) * damageFactor * strategyFactor
        val mainDamage =
            (300.0 * source.troops / (3_500 + source.troops)) *
                rate *
                damageFactor *
                strategyFactor
        return (troopDamage + attributeDamage + mainDamage)
            .roundToInt()
            .coerceIn(1, target.troops.coerceAtLeast(1))
    }

    private fun modifierFactor(
        source: BattleHero,
        target: BattleHero,
        school: DamageSchool,
        origin: DamageOrigin?,
        tags: Set<DamageTag>,
        skillId: Int?,
        targetConditions: Set<DamageTargetCondition>,
    ): Double {
        val dealt = source.modifiers
            .filterIsInstance<BattleModifier.DamageDealtPercent>()
            .filter { it.matches(school, origin, tags, skillId, targetConditions) }
            .sumOf { it.percent }
        val targetStatusDealt = source.modifiers
            .filterIsInstance<BattleModifier.TargetStatusCountDamageDealtPercent>()
            .sumOf { modifier ->
                target.activeStatuses.count(modifier.countedStatuses::contains)
                    .coerceAtMost(modifier.maxStatuses) *
                    modifier.percentPerStatus
            }
        val taken = target.modifiers
            .filterIsInstance<BattleModifier.DamageTakenPercent>()
            .filter {
                it.matches(
                    school,
                    origin,
                    tags,
                    target.activeStatuses,
                    source,
                    target,
                )
            }
            .sumOf { it.percent }
        val troopCounterDealt = source.modifiers
            .filterIsInstance<BattleModifier.TroopCounterDealtPercent>()
            .filter { it.targetHeroType == target.heroType }
            .sumOf { it.percent }
        val troopCounterTaken = if (
            BattleModifier.TroopCounterImmunity in source.modifiers
        ) {
            0
        } else {
            target.modifiers
                .filterIsInstance<BattleModifier.TroopCounterTakenPercent>()
                .filter { it.sourceHeroType == source.heroType }
                .sumOf { it.percent }
        }
        return (
            100 +
                dealt +
                targetStatusDealt +
                taken +
                troopCounterDealt +
                troopCounterTaken
            ).coerceAtLeast(10) / 100.0
    }

    private fun BattleModifier.DamageDealtPercent.matches(
        school: DamageSchool,
        origin: DamageOrigin?,
        tags: Set<DamageTag>,
        skillId: Int?,
        targetConditions: Set<DamageTargetCondition>,
    ): Boolean =
        (this.school == null || this.school == school) &&
            (this.origin == null || this.origin == origin) &&
            (tag == null || tag in tags) &&
            (this.skillId == null || this.skillId == skillId) &&
            (skillIds.isEmpty() || skillId != null && skillId in skillIds) &&
            (targetCondition == null || targetCondition in targetConditions)

    private fun BattleModifier.DamageTakenPercent.matches(
        school: DamageSchool,
        origin: DamageOrigin?,
        tags: Set<DamageTag>,
        statuses: Set<BattleStatus>,
        source: BattleHero,
        target: BattleHero,
    ): Boolean =
        (this.school == null || this.school == school) &&
            (this.origin == null || this.origin == origin) &&
            (tag == null || tag in tags) &&
            (requiredStatus == null || requiredStatus in statuses) &&
            (
                requiredSourceInherentStatBelowTarget == null ||
                    source.inherentStats.precise(requiredSourceInherentStatBelowTarget) <
                    target.inherentStats.precise(requiredSourceInherentStatBelowTarget)
                )

    private fun attackDefenseFactor(attack: Int, defense: Int): Double {
        val difference = attack - defense
        return if (difference >= 0) {
            3.0 - 500.0 / (250 + difference)
        } else {
            100.0 / (100 - difference)
        }
    }

    private fun strategyDefenseFactor(strategy: Int): Double =
        if (strategy <= 50) {
            1.0
        } else {
            ceil(100 - (75 - 9_375.0 / (75 + strategy))) / 100.0
        }

    private fun ignoredTargetAttribute(
        source: BattleHero,
        targetAttribute: Int,
        stat: BattleStat,
    ): Int {
        val ignoredPercent = source.modifiers
            .filterIsInstance<BattleModifier.DefenseIgnorePercent>()
            .filter { it.stat == stat }
            .sumOf { it.percent }
            .coerceIn(0, 100)
        return (targetAttribute * (100 - ignoredPercent) / 100.0).roundToInt()
    }

    fun targetConditions(
        target: BattleHero,
        targetTeam: Collection<BattleHero>,
    ): Set<DamageTargetCondition> {
        val living = targetTeam.filter { it.troops > 0 }
        val minimumTroops = living.minOfOrNull(BattleHero::troops)
            ?: return emptySet()
        val nearestPosition = living.maxOfOrNull(BattleHero::position)
        return buildSet {
            if (target.troops == minimumTroops) add(DamageTargetCondition.LOWEST_TROOPS)
            if (target.troops > 0 && target.position == nearestPosition) {
                add(DamageTargetCondition.NEAREST_ENEMY)
            }
        }
    }
}
