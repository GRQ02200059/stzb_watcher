package com.stzb.battle.core

class BattleSkillInterpreter(
    private val config: BattleConfigRepository,
) {
    fun applyPreBattle(team: BattleTeam): BattleTeam {
        val selfBuffedHeroes = team.heroes.map { hero ->
            val bonus = hero.skillIds
                .flatMap { config.skillDetails(it) }
                .filterNot { it.attackType == 23 && it.effectId in attributeEffectIds }
                .fold(BattleStats(0, 0, 0, 0, 0, 0)) { acc, detail -> acc + detail.attributeBonus() }
            hero.copy(stats = hero.stats + bonus)
        }
        val teamWideBonus = team.heroes
            .flatMap { it.skillIds }
            .flatMap { config.skillDetails(it) }
            .filter { it.attackType == 23 && it.effectId in attributeEffectIds }
            .fold(BattleStats(0, 0, 0, 0, 0, 0)) { acc, detail -> acc + detail.attributeBonus() }

        return BattleTeam(
            heroes = selfBuffedHeroes.map { it.copy(stats = it.stats + teamWideBonus) },
            armyBonuses = team.armyBonuses,
        )
    }

    fun tryCastActiveSkill(
        round: Int,
        sourceRef: BattleHeroRef,
        source: BattleHero,
        enemies: BattleTeam,
        random: BattleRandom,
    ): SkillCastResult? {
        for (skillId in source.skillIds) {
            val skill = config.skill(skillId) ?: continue
            if (skill.kind != SkillKind.ACTIVE && skill.kind != SkillKind.PURSUIT) continue
            if (random.nextInt(100) >= skill.probabilityMax) continue

            val damageDetails = config.skillDetails(skillId).filter { it.effectId == 301 || it.effectId == 302 }
            if (damageDetails.isEmpty()) continue

            var updatedEnemies = enemies.heroes.associateBy { it.position }.toMutableMap()
            val events = mutableListOf<BattleEvent>()
            for (detail in damageDetails) {
                val target = updatedEnemies.values
                    .filter { it.troops > 0 }
                    .minByOrNull { it.position }
                    ?: break
                val targetRef = BattleHeroRef(sourceRef.side.opposite(), target.position, target.id)
                val damage = skillDamage(source, target, detail)
                val newTarget = target.copy(troops = (target.troops - damage).coerceAtLeast(0))
                updatedEnemies[target.position] = newTarget
                events += BattleEvent.SkillDamage(
                    round = round,
                    skillId = skillId,
                    effectId = detail.effectId,
                    source = sourceRef,
                    target = targetRef,
                    damage = damage,
                    targetTroopsAfter = newTarget.troops,
                )
            }
            return SkillCastResult(
                skillId = skillId,
                updatedEnemies = BattleTeam(updatedEnemies.values.sortedBy { it.position }, enemies.armyBonuses),
                events = events,
            )
        }
        return null
    }

    private fun skillDamage(source: BattleHero, target: BattleHero, detail: SkillDetailConfig): Int {
        val sourcePower = if (detail.effectId == 302) source.stats.strategy else source.stats.attack
        val targetGuard = if (detail.effectId == 302) target.stats.strategy / 3 else target.stats.defense / 2
        val rate = detail.constantParam.coerceAtLeast(1) / 100.0
        val raw = ((sourcePower - targetGuard).coerceAtLeast(1) * rate).toInt().coerceAtLeast(1)
        return raw.coerceAtMost(target.troops)
    }

    private fun SkillDetailConfig.attributeBonus(): BattleStats {
        val value = (constantParam / 100.0).toInt()
        return when (effectId) {
            101 -> BattleStats(value, 0, 0, 0, 0, 0)
            102 -> BattleStats(0, value, 0, 0, 0, 0)
            103 -> BattleStats(0, 0, value, 0, 0, 0)
            104 -> BattleStats(0, 0, 0, value, 0, 0)
            else -> BattleStats(0, 0, 0, 0, 0, 0)
        }
    }

    private operator fun BattleStats.plus(other: BattleStats): BattleStats =
        BattleStats(
            attack = attack + other.attack,
            defense = defense + other.defense,
            strategy = strategy + other.strategy,
            speed = speed + other.speed,
            siege = siege + other.siege,
            hitRange = hitRange + other.hitRange,
        )

    private companion object {
        val attributeEffectIds = setOf(101, 102, 103, 104)
    }
}
