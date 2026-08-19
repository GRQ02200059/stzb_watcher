package com.stzb.server.game.battle

import kotlin.math.roundToInt

data class SkillRuntimeState(
    val defaultCooldownRounds: Int = 0,
    val preparingUntilRound: MutableMap<SkillRuntimeKey, Int> = mutableMapOf(),
    val cooldownUntilRound: MutableMap<SkillRuntimeKey, Int> = mutableMapOf(),
    val attemptedRound: MutableMap<SkillRuntimeKey, Int> = mutableMapOf(),
) {
    fun interruptPreparations(source: BattleHeroRef) {
        preparingUntilRound.keys.removeAll { it.source == source }
    }
}

data class SkillRuntimeKey(
    val source: BattleHeroRef,
    val skillId: Int,
)

class BattleSkillRuntime(
    private val config: BattleConfigRepository,
) {
    fun tryAct(
        round: Int,
        sourceRef: BattleHeroRef,
        source: BattleHero,
        targets: BattleTeam,
        allies: BattleTeam,
        random: BattleRandom,
        state: SkillRuntimeState,
        allowedKinds: Set<SkillKind> = setOf(SkillKind.ACTIVE, SkillKind.PURSUIT),
        allowRepeatedAttempt: Boolean = false,
    ): SkillCastResult? {
        for (skillId in source.skillIds) {
            val skill = config.skill(skillId) ?: continue
            if (skill.kind !in allowedKinds) continue
            val key = SkillRuntimeKey(sourceRef, skillId)
            if (!allowRepeatedAttempt && state.attemptedRound[key] == round) continue
            val readyRound = state.preparingUntilRound[key]
            if (readyRound != null) {
                if (round < readyRound) continue
                state.attemptedRound[key] = round
                state.preparingUntilRound.remove(key)
                val result = executeDetails(
                    round, skillId, sourceRef, source,
                    targets.insideRange(source.position, skill.hitRange), allies, random, state,
                )
                state.cooldownUntilRound[key] = round + state.defaultCooldownRounds
                return result
            }
            if (!allowRepeatedAttempt && (state.cooldownUntilRound[key] ?: -1) >= round) continue
            state.attemptedRound[key] = round
            if (random.nextInt(100) >= effectiveProbability(source, skill.probabilityMax)) continue

            if (skill.prepareRounds > 0) {
                val completesAt = round + skill.prepareRounds
                state.preparingUntilRound[key] = completesAt
                return SkillCastResult(
                    skillId = skillId,
                    updatedEnemies = targets,
                    events = listOf(
                        BattleEvent.SkillPreparationStarted(
                            round = round,
                            source = sourceRef,
                            skillId = skillId,
                            readyRound = completesAt,
                        ),
                    ),
                    updatedAllies = allies,
                )
            }

            val result = executeDetails(
                round, skillId, sourceRef, source,
                targets.insideRange(source.position, skill.hitRange), allies, random, state,
            )
            val cooldown = if (skillId == 200002) 3 else state.defaultCooldownRounds
            state.cooldownUntilRound[key] = round + cooldown
            return result
        }
        return null
    }

    private fun executeDetails(
        round: Int,
        skillId: Int,
        sourceRef: BattleHeroRef,
        source: BattleHero,
        enemies: BattleTeam,
        allies: BattleTeam,
        random: BattleRandom,
        state: SkillRuntimeState,
    ): SkillCastResult {
        val updatedEnemies = enemies.heroes.associateBy { it.position }.toMutableMap()
        val updatedAllies = allies.heroes.associateBy { it.position }.toMutableMap()
        val events = mutableListOf<BattleEvent>()
        var selfStatDelta = BattleStats.ZERO
        var selfBuffDuration: Int? = null
        val details = config.skillDetails(skillId).ifEmpty {
            listOf(
                SkillDetailConfig(
                    detailId = skillId * 100,
                    effectId = 0,
                    attackType = 0,
                    targetType = 0,
                    selectType = 0,
                    constantParam = 0,
                    intelParam = 0,
                    probabilityInit = 0,
                    probabilityMax = 0,
                    availableRounds = 0,
                    attackMax = 0,
                    effectName = "missing detail",
                ),
            )
        }

        val executableDetails = if (skillId == 200002) {
            selectDisorderEffects(details, random)
        } else {
            details
        }

        executableDetails.forEach { detail ->
            val targetSide = resolveTargetSide(detail)
            when (detail.effectId) {
                301, 302 -> {
                    val pool = when (targetSide) {
                        TargetSide.ENEMY -> updatedEnemies
                        TargetSide.ALLY -> updatedAllies
                        TargetSide.SELF -> updatedAllies
                    }
                    val selected = selectTargets(pool.values, detail, sourceRef, random, targetSide == TargetSide.SELF)
                    selected.forEach { target ->
                        val damage = skillDamage(source, target, detail, pool.values)
                        val newTarget = target.copy(troops = (target.troops - damage).coerceAtLeast(0))
                        pool[target.position] = newTarget
                        val refSide = if (targetSide == TargetSide.ENEMY) sourceRef.side.opposite() else sourceRef.side
                        events += BattleEvent.SkillDamage(
                            round = round,
                            skillId = skillId,
                            effectId = detail.effectId,
                            source = sourceRef,
                            target = BattleHeroRef(refSide, target.position, target.id),
                            damage = damage,
                            targetTroopsAfter = newTarget.troops,
                        )
                    }
                    if (skillId == 200002 && detail.effectId == 302 && selected.isNotEmpty()) {
                        val target = selected.first()
                        val duration = detail.availableRounds.takeIf { it > 0 } ?: 2
                        events += BattleEvent.StatusApplied(
                            round = round,
                            source = sourceRef,
                            target = BattleHeroRef(sourceRef.side.opposite(), target.position, target.id),
                            status = BattleStatus.BURN,
                            durationRounds = duration,
                            power = (source.stats.strategy / 5).coerceAtLeast(1),
                            skillId = skillId,
                        )
                    }
                }
                401, 402 -> {
                    val pool = updatedAllies
                    val selected = selectTargets(pool.values, detail, sourceRef, random, preferSelf = true)
                    selected.forEach { target ->
                        val amount = (source.stats.strategy + detail.constantParam).coerceAtLeast(1)
                        val newTarget = target.copy(troops = (target.troops + amount).coerceAtMost(target.maxTroops))
                        pool[target.position] = newTarget
                        events += BattleEvent.Recovery(
                            round = round,
                            source = sourceRef,
                            target = BattleHeroRef(sourceRef.side, target.position, target.id),
                            amount = amount,
                            targetTroopsAfter = newTarget.troops,
                            skillId = skillId,
                        )
                    }
                }
                303, 304, 305, 306, 501, 502, 552 -> {
                    val pool = when (targetSide) {
                        TargetSide.ENEMY -> updatedEnemies
                        TargetSide.ALLY -> updatedAllies
                        TargetSide.SELF -> updatedAllies
                    }
                    val selected = selectTargets(pool.values, detail, sourceRef, random, targetSide == TargetSide.SELF)
                    selected.forEach { target ->
                        val status = when (detail.effectId) {
                            303 -> BattleStatus.SHAKE
                            304 -> BattleStatus.PANIC
                            305 -> BattleStatus.BURN
                            306 -> BattleStatus.HEX
                            501 -> BattleStatus.CONFUSION
                            502 -> BattleStatus.HESITATION
                            else -> BattleStatus.DISARM
                        }
                        val power = when (detail.effectId) {
                            303, 304, 305, 306 -> skillRate(source, detail)
                            else -> 0
                        }
                        val refSide = if (targetSide == TargetSide.ENEMY) sourceRef.side.opposite() else sourceRef.side
                        events += BattleEvent.StatusApplied(
                            round = round,
                            source = sourceRef,
                            target = BattleHeroRef(refSide, target.position, target.id),
                            status = status,
                            durationRounds = detail.availableRounds.coerceAtLeast(1),
                            power = power,
                            skillId = skillId,
                        )
                    }
                }
                511, 514, 521, 522, 523, 524, 531, 532, 533, 534, 544, 561, 761 -> {
                    val pool = when (targetSide) {
                        TargetSide.ENEMY -> updatedEnemies
                        TargetSide.ALLY, TargetSide.SELF -> updatedAllies
                    }
                    val selected = selectTargets(pool.values, detail, sourceRef, random, targetSide == TargetSide.SELF)
                    selected.forEach { target ->
                        val status = statusForEffect(detail.effectId)
                        val refSide = if (targetSide == TargetSide.ENEMY) sourceRef.side.opposite() else sourceRef.side
                        events += BattleEvent.StatusApplied(
                            round = round,
                            source = sourceRef,
                            target = BattleHeroRef(refSide, target.position, target.id),
                            status = status,
                            durationRounds = detail.availableRounds.coerceAtLeast(1),
                            power = effectPower(source, detail),
                            skillId = skillId,
                        )
                    }
                }
                101, 102, 103, 104 -> {
                    selfStatDelta += statDeltaForEffect(detail.effectId, detail.constantParam)
                    selfBuffDuration = detail.availableRounds.coerceAtLeast(2)
                }
                else -> events += BattleEvent.UnsupportedSkillEffect(
                    round = round,
                    skillId = skillId,
                    effectId = detail.effectId,
                    source = sourceRef,
                    rawDescription = detail.effectName,
                )
            }
        }

        return SkillCastResult(
            skillId = skillId,
            updatedEnemies = BattleTeam(updatedEnemies.values.sortedBy { it.position }, enemies.armyBonuses),
            events = events,
            updatedAllies = BattleTeam(updatedAllies.values.sortedBy { it.position }, allies.armyBonuses),
            selfStatDelta = selfStatDelta,
            selfBuffDuration = selfBuffDuration,
        )
    }

    private enum class TargetSide { ENEMY, ALLY, SELF }

    private fun resolveTargetSide(detail: SkillDetailConfig): TargetSide {
        if (detail.targetType == 0) {
            config.skillEffect(detail.effectId)?.let { effect ->
                return when (effect.buffType) {
                    2 -> TargetSide.ALLY
                    1 -> TargetSide.ENEMY
                    else -> targetSideByEffect(detail.effectId)
                }
            }
            return targetSideByEffect(detail.effectId)
        }
        val ones = detail.targetType % 10
        return when (ones) {
            2 -> TargetSide.ALLY
            3 -> TargetSide.SELF
            else -> TargetSide.ENEMY
        }
    }

    private fun targetSideByEffect(effectId: Int): TargetSide =
        when (effectId) {
            in 301..399, in 500..599 -> TargetSide.ENEMY
            in 400..499, in 100..199 -> TargetSide.SELF
            else -> TargetSide.ENEMY
        }

    private fun effectiveProbability(source: BattleHero, configured: Int): Int {
        val equipmentAdjusted = source.modifiers
            .filterIsInstance<BattleModifier.SkillProbabilityPercent>()
            .sumOf { it.percent }
        val moraleAddition = (source.morale - 100).toDouble() / (100 + 0.5 * source.morale)
        return ((configured + equipmentAdjusted) * (1 + moraleAddition))
            .toInt()
            .coerceIn(0, 100)
    }

    private fun selectTargets(
        pool: Collection<BattleHero>,
        detail: SkillDetailConfig,
        sourceRef: BattleHeroRef,
        random: BattleRandom,
        preferSelf: Boolean,
    ): List<BattleHero> {
        val alive = pool.filter { it.troops > 0 }
        if (alive.isEmpty()) return emptyList()
        if (preferSelf) {
            val self = alive.firstOrNull { it.position == sourceRef.position && it.id == sourceRef.heroId }
            if (self != null) return listOf(self)
        }
        val tens = (detail.targetType / 10) % 10
        val isAoe = tens >= 2
        val candidates = when (detail.selectType) {
            3, 4, 33, 34 -> alive.sortedBy { it.troops }
            5 -> alive.sortedByDescending { it.troops }
            else -> alive.sortedBy { it.position }
        }
        val targetCount = detail.attackMax.coerceAtLeast(1)
        return if (isAoe || targetCount > 1) candidates.take(targetCount) else listOf(candidates.first())
    }

    private fun statDeltaForEffect(effectId: Int, constantParam: Int): BattleStats {
        val amount = constantParam / 10
        return when (effectId) {
            101 -> BattleStats(attack = amount, defense = 0, strategy = 0, speed = 0, siege = 0, hitRange = 0)
            102 -> BattleStats(attack = 0, defense = amount, strategy = 0, speed = 0, siege = 0, hitRange = 0)
            103 -> BattleStats(attack = 0, defense = 0, strategy = amount, speed = 0, siege = 0, hitRange = 0)
            104 -> BattleStats(attack = 0, defense = 0, strategy = 0, speed = amount, siege = 0, hitRange = 0)
            else -> BattleStats.ZERO
        }
    }

    private fun statusForEffect(effectId: Int): BattleStatus =
        when (effectId) {
            511 -> BattleStatus.INSIGHT
            514 -> BattleStatus.EVADE
            521 -> BattleStatus.PHYSICAL_DAMAGE_TAKEN_INCREASED
            522 -> BattleStatus.PHYSICAL_DAMAGE_TAKEN_REDUCED
            523 -> BattleStatus.STRATEGY_DAMAGE_TAKEN_INCREASED
            524 -> BattleStatus.STRATEGY_DAMAGE_TAKEN_REDUCED
            531 -> BattleStatus.PHYSICAL_DAMAGE_DEALT_INCREASED
            532 -> BattleStatus.PHYSICAL_DAMAGE_DEALT_REDUCED
            533 -> BattleStatus.STRATEGY_DAMAGE_DEALT_INCREASED
            534 -> BattleStatus.STRATEGY_DAMAGE_DEALT_REDUCED
            544 -> BattleStatus.DOUBLE_ATTACK
            561, 761 -> BattleStatus.FIRST_ACTION
            else -> error("unsupported status effect: $effectId")
        }

    private fun effectPower(source: BattleHero, detail: SkillDetailConfig): Int {
        val strategyBonus = if (detail.intelParam == 0) {
            0
        } else {
            ((source.stats.strategy - 80).coerceAtLeast(0) * detail.intelParam / 10_000.0).toInt()
        }
        return detail.constantParam + strategyBonus
    }

    private fun selectDisorderEffects(
        details: List<SkillDetailConfig>,
        random: BattleRandom,
    ): List<SkillDetailConfig> {
        val candidates = details
            .filter { it.effectId in disorderEffectIds }
            .distinctBy { it.effectId }
        if (candidates.size <= 3) return candidates
        return List(3) { candidates[random.nextInt(candidates.size)] }
    }

    private companion object {
        val disorderEffectIds = setOf(303, 304, 305, 306, 501, 502, 503, 552, 505)
    }

    private fun skillDamage(
        source: BattleHero,
        target: BattleHero,
        detail: SkillDetailConfig,
        targetTeam: Collection<BattleHero>,
    ): Int {
        val rate = skillRate(source, detail)
        val targetConditions = BattleDamageCalculator.targetConditions(target, targetTeam)
        return if (detail.effectId == 302) {
            BattleDamageCalculator.strategy(
                source,
                target,
                rate,
                targetConditions = targetConditions,
            )
        } else {
            BattleDamageCalculator.physical(
                source,
                target,
                rate,
                targetConditions = targetConditions,
            )
        }
    }

    private fun skillRate(source: BattleHero, detail: SkillDetailConfig): Int =
        if (detail.intelParam == 0) {
            detail.constantParam.coerceAtLeast(1)
        } else {
            val base = detail.constantParam
            if (source.stats.strategy < 80) {
                (base * 0.4 + base * 0.6 * source.stats.strategy / 80.0).roundToInt()
            } else {
                (base + detail.intelParam / 1_000.0 * (source.stats.strategy - 80)).roundToInt()
            }.coerceAtLeast(1)
        }

    private fun BattleTeam.insideRange(sourcePosition: Int, hitRange: Int?): BattleTeam =
        if (hitRange == null) {
            this
        } else {
            copy(heroes = heroes.filter { target -> 5 - sourcePosition - target.position <= hitRange })
        }
}
