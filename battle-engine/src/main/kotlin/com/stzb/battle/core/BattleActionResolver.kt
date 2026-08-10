package com.stzb.battle.core

data class NormalAttackResult(
    val target: BattleHero,
    val event: BattleEvent.NormalAttack,
)

/**
 * Resolves one normal attack without mutating battle state. The engine owns
 * orchestration while this module owns formation distance and damage rules.
 */
class BattleActionResolver {
    fun selectNormalAttackTarget(
        source: BattleHero,
        enemies: Collection<BattleHero>,
        random: BattleRandom? = null,
        allies: Collection<BattleHero>? = null,
    ): BattleHero? {
        val candidates = normalAttackTargetsInRange(source, enemies, allies)
        val rangedAttack = source.modifiers
            .filterIsInstance<BattleModifier.RangedNormalAttack>()
            .lastOrNull()
        return when {
            candidates.isEmpty() -> null
            rangedAttack != null -> candidates.maxByOrNull { it.second }?.first
            random == null -> candidates.first().first
            else -> candidates[random.nextInt(candidates.size)].first
        }
    }

    fun normalAttackTargetsInRange(
        source: BattleHero,
        enemies: Collection<BattleHero>,
        allies: Collection<BattleHero>? = null,
    ): List<Pair<BattleHero, Int>> =
        enemies
            .filter { it.troops > 0 }
            .filterNot { target ->
                BattleModifier.TargetImmunity(BattleTargetingKind.NORMAL_ATTACK) in target.modifiers
            }
            .map { target ->
                target to formationDistance(source, target, allies, enemies)
            }
            .filter { (_, distance) -> distance <= source.stats.hitRange }
            .sortedWith(compareBy<Pair<BattleHero, Int>> { it.second }.thenByDescending { it.first.position })

    fun normalAttackDamage(
        source: BattleHero,
        target: BattleHero,
        random: BattleRandom? = null,
        allies: Collection<BattleHero>? = null,
        enemies: Collection<BattleHero> = listOf(target),
    ): Int {
        val rangedAttack = source.modifiers
            .filterIsInstance<BattleModifier.RangedNormalAttack>()
            .lastOrNull()
        val distanceBonus = rangedAttack
            ?.damagePercentPerDistance
            ?.times(formationDistance(source, target, allies, enemies))
            ?.coerceAtLeast(0)
            ?: 0
        val effectiveSource = if (distanceBonus == 0) {
            source
        } else {
            source.copy(
                modifiers = source.modifiers + BattleModifier.DamageDealtPercent(
                    origin = DamageOrigin.NORMAL,
                    percent = distanceBonus,
                ),
            )
        }
        return BattleDamageCalculator.physical(
            source = effectiveSource,
            target = target,
            attributeRandomTenths = 30 + (random?.nextInt(10) ?: 5),
            origin = DamageOrigin.NORMAL,
            targetConditions = BattleDamageCalculator.targetConditions(target, enemies),
        )
    }

    fun resolveNormalAttack(
        round: Int,
        sourceRef: BattleHeroRef,
        source: BattleHero,
        enemies: Collection<BattleHero>,
        random: BattleRandom? = null,
        allies: Collection<BattleHero>? = null,
    ): NormalAttackResult? {
        val target = selectNormalAttackTarget(source, enemies, random, allies) ?: return null
        val damage = normalAttackDamage(source, target, random, allies, enemies)
        val updated = target.copy(troops = (target.troops - damage).coerceAtLeast(0))
        return NormalAttackResult(
            target = updated,
            event = BattleEvent.NormalAttack(
                round = round,
                source = sourceRef,
                target = BattleHeroRef(sourceRef.side.opposite(), target.position, target.id),
                damage = damage,
                targetTroopsAfter = updated.troops,
            ),
        )
    }

    private fun formationDistance(
        source: BattleHero,
        target: BattleHero,
        allies: Collection<BattleHero>?,
        enemies: Collection<BattleHero>,
    ): Int {
        if (allies == null) return 5 - source.position - target.position
        val alliedFront = allies.count { it.troops > 0 && it.position > source.position }
        val enemyFront = enemies.count { it.troops > 0 && it.position > target.position }
        return 1 + alliedFront + enemyFront
    }

}
