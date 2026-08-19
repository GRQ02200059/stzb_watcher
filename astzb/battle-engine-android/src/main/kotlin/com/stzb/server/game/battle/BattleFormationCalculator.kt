package com.stzb.server.game.battle

import com.stzb.server.game.ClientTroopFeatureRepository
import com.stzb.server.game.ClientEquipmentSkillRepository
import com.stzb.server.game.ClientTroopTypeRepository

/**
 * Computes the immutable battle-entry snapshot. Runtime buffs never mutate
 * these values; they are layered by BattleEffectState.
 */
class BattleFormationCalculator(
    private val config: BattleConfigRepository,
    private val equipmentRepository: BattleEquipmentRepository? = null,
    private val troopFeatureRepository: ClientTroopFeatureRepository =
        ClientTroopFeatureRepository.loadDefault(),
    private val equipmentSkillRepository: ClientEquipmentSkillRepository =
        ClientEquipmentSkillRepository.loadDefault(),
    private val troopTypeRepository: ClientTroopTypeRepository =
        ClientTroopTypeRepository.loadDefault(),
) {
    private data class EquipmentRuntimeDetail(
        val detail: SkillDetailConfig,
        val level: Int,
        val targetCondition: DamageTargetCondition?,
    )

    fun calculate(specs: List<BattleHeroSpec>): BattleTeam {
        require(specs.map { it.position }.distinct().size == specs.size) { "同一部队内站位不能重复" }
        require(specs.all { it.position in 0..2 }) { "武将站位必须在 0..2" }

        val bonuses = config.armyBonusesFor(specs.map { it.heroId })
        val formationBonus = bonuses.fold(BattleStats.ZERO) { total, bonus -> total + bonus.stats }
        val troopFeatureSources = troopFeatureSources(specs)
        val resolvedSpecs = specs.map(::withResolvedEquipmentSkills)
        val preparationModifiers =
            troopFeatureModifiers(troopFeatureSources) +
                equipmentPreparationModifiers(resolvedSpecs)
        val staticEffects =
            countryEffects(resolvedSpecs) +
                troopTypeEffects(resolvedSpecs) +
                troopFeatureEffects(troopFeatureSources) +
                equipmentEffects(resolvedSpecs) +
                equipmentFeatureEffects(resolvedSpecs) +
                surfaceEffects(resolvedSpecs)
        val heroes = resolvedSpecs.map { spec ->
            val heroConfig = config.hero(spec.heroId) ?: error("未知武将配置: ${spec.heroId}")
            val heroType = spec.heroType ?: heroConfig.heroType
            val equipmentModifiers = equipmentModifiers(spec)
            val level = spec.level.coerceAtLeast(1)
            val advance = spec.advanceLevel.coerceAtLeast(0)
            val equippedSkillIds =
                listOf(spec.initialSkillId ?: heroConfig.initialSkillId)
                    .filter { it > 0 } + spec.extraSkillIds
            val equippedSkillLevels =
                spec.skillLevels.take(equippedSkillIds.size).let { levels ->
                    levels + List((equippedSkillIds.size - levels.size).coerceAtLeast(0)) {
                        DEFAULT_SKILL_LEVEL
                    }
                }
            val troopRuntimeSkillIds = troopFeatureSources
                .filter { it.position == spec.position }
                .filterNot(::isProjectedIntoFormationSnapshot)
                .map(TroopFeatureSource::skillId)
                .filterNot(equippedSkillIds::contains)
                .distinct()
            val skillIds = equippedSkillIds + troopRuntimeSkillIds
            val inherentStats = heroConfig.stats +
                heroConfig.growth.scale(level - 1) +
                spec.attributePoints
            val preStaticStats = inherentStats +
                formationBonus +
                if (spec.equipmentSkillIds.isEmpty()) {
                    equipmentModifiers.statBonus()
                } else {
                    BattleStats.ZERO
                }
            val finalStats = staticEffects
                .filter { it.targetPosition == spec.position }
                .fold(preStaticStats) { stats, effect ->
                    stats + effect.toStats(stats)
                }
            config.toBattleHero(spec.heroId, spec.position, spec.troops).copy(
                stats = finalStats,
                maxTroops = spec.troops + advance * 200,
                skillIds = skillIds,
                skillLevels =
                    equippedSkillLevels +
                        List(troopRuntimeSkillIds.size) { DEFAULT_SKILL_LEVEL },
                troopFeatureIds = spec.troopFeatureIds.take(2),
                equipment = spec.equipmentSkillIds.take(3).mapIndexed { index, skillId ->
                    BattleEquipmentSlot(
                        skillId,
                        spec.equipmentSkillLevels.getOrElse(index) { DEFAULT_EQUIPMENT_LEVEL },
                    )
                },
                equipmentIds = spec.equipmentIds,
                modifiers =
                    config.troopCounterModifiers(heroType) +
                        equipmentRuntimeModifiers(spec) +
                        equipmentFeatureRuntimeModifiers(spec) +
                        troopRuntimeModifiers(troopFeatureSources, spec.position) +
                        surfaceRuntimeModifiers(spec),
                level = level,
                advanceLevel = advance,
                morale = spec.morale.coerceAtLeast(0),
                inherentStats = inherentStats,
                surfaceSkillId = spec.surfaceSkillId,
                heroType = heroType,
            )
        }
        return BattleTeam(
            heroes = heroes,
            armyBonuses = bonuses,
            preparationSources =
                troopFeatureSources.map { feature ->
                    BattlePreparationSource(
                        stage = BattlePreparationStage.TROOP,
                        sourceId = feature.skillId,
                        sourcePosition = feature.position,
                    )
                } +
                specs.flatMap { spec ->
                    spec.equipmentIds.map { equipmentId ->
                        BattlePreparationSource(
                            stage = BattlePreparationStage.EQUIPMENT,
                            sourceId = equipmentId,
                            sourcePosition = spec.position,
                        )
                    }
                } +
                specs.mapNotNull { spec ->
                    spec.surfaceSkillId.takeIf { it > 0 }?.let { surfaceSkillId ->
                        BattlePreparationSource(
                            stage = BattlePreparationStage.SURFACE,
                            sourceId = surfaceSkillId,
                            sourcePosition = spec.position,
                        )
                    }
                },
            preparationEffects = materializeEffects(staticEffects, resolvedSpecs),
            preparationModifiers = preparationModifiers,
            preparationActions =
                troopPreparationActions(troopFeatureSources) +
                equipmentPreparationActions(resolvedSpecs) +
                    equipmentFeaturePreparationActions(resolvedSpecs) +
                    surfacePreparationActions(resolvedSpecs),
        )
    }

    private fun withResolvedEquipmentSkills(spec: BattleHeroSpec): BattleHeroSpec {
        if (spec.equipmentSkillIds.isNotEmpty()) return spec
        val slots = spec.equipmentIds.flatMap(equipmentSkillRepository::skillSlots)
        return spec.copy(
            equipmentSkillIds = slots.map(Pair<Int, Int>::first),
            equipmentSkillLevels = slots.map(Pair<Int, Int>::second),
        )
    }

    private fun troopFeatureSources(specs: List<BattleHeroSpec>): List<TroopFeatureSource> =
        specs.flatMap { spec ->
            val learnedSkills = spec.troopFeatureIds.take(2).flatMap(troopFeatureRepository::skillIds)
            val heroType = spec.heroType ?: config.hero(spec.heroId)?.heroType
            val inherentSkills = heroType?.let(troopTypeRepository::skillIds).orEmpty()
            (learnedSkills + inherentSkills).distinct().flatMap { skillId ->
                listOf(TroopFeatureSource(spec.position, skillId))
            }
        }

    private fun troopFeatureEffects(sources: List<TroopFeatureSource>): List<StaticEffect> =
        sources.flatMap { source ->
            when (source.skillId) {
                296_104 -> when (source.position) {
                    0 -> listOf(BattleStat.ATTACK, BattleStat.DEFENSE, BattleStat.STRATEGY)
                        .map { stat -> fixedTroopEffect(source, stat, 6) }
                    1 -> listOf(BattleStat.DEFENSE, BattleStat.STRATEGY)
                        .map { stat -> fixedTroopEffect(source, stat, 10) }
                    2 -> listOf(fixedTroopEffect(source, BattleStat.DEFENSE, 24))
                    else -> emptyList()
                }
                296_133 -> listOf(fixedTroopEffect(source, BattleStat.HIT_RANGE, -1))
                296_141 -> listOf(fixedTroopEffect(source, BattleStat.HIT_RANGE, 1))
                296_143 -> listOf(BattleStat.ATTACK, BattleStat.DEFENSE, BattleStat.STRATEGY)
                    .map { stat -> fixedTroopEffect(source, stat, 18) }
                296_241 -> listOf(fixedTroopEffect(source, BattleStat.SPEED, -15))
                296_341 -> listOf(fixedTroopEffect(source, BattleStat.SPEED, 15))
                else -> emptyList()
            }
        }

    private fun isProjectedIntoFormationSnapshot(source: TroopFeatureSource): Boolean =
        troopFeatureEffects(listOf(source)).isNotEmpty() ||
            troopRuntimeModifiers(listOf(source), source.position).isNotEmpty()

    private fun troopFeatureModifiers(
        sources: List<TroopFeatureSource>,
    ): List<BattlePreparationModifier> =
        sources.flatMap { source ->
            when (source.skillId) {
                296_105 -> listOf(522, 524).map { effectId ->
                    BattlePreparationModifier(
                        stage = BattlePreparationStage.TROOP,
                        sourceId = source.skillId,
                        sourcePosition = source.position,
                        targetPosition = source.position,
                        effectId = effectId,
                        amount = 8,
                    )
                }
                296_132, 296_232 -> listOf(531, 533).map { effectId ->
                    BattlePreparationModifier(
                        stage = BattlePreparationStage.TROOP,
                        sourceId = source.skillId,
                        sourcePosition = source.position,
                        targetPosition = source.position,
                        effectId = effectId,
                        amount = 8,
                    )
                }
                296_243 -> listOf(522, 524).map { effectId ->
                    BattlePreparationModifier(
                        stage = BattlePreparationStage.TROOP,
                        sourceId = source.skillId,
                        sourcePosition = source.position,
                        targetPosition = source.position,
                        effectId = effectId,
                        amount = 6,
                    )
                }
                else -> emptyList()
            }
        }

    private fun troopRuntimeModifiers(
        sources: List<TroopFeatureSource>,
        position: Int,
    ): List<BattleModifier> =
        sources
            .filter { it.position == position }
            .mapNotNull { source ->
                when (source.skillId) {
                    296_105 -> BattleModifier.DamageTakenPercent(percent = -8)
                    296_132, 296_232 -> BattleModifier.DamageDealtPercent(percent = 8)
                    296_233 -> BattleModifier.DamageTakenPercent(
                        origin = DamageOrigin.PURSUIT,
                        percent = -20,
                    )
                    296_243 -> BattleModifier.DamageTakenPercent(percent = -6)
                    else -> null
                }
            }

    private fun troopPreparationActions(
        sources: List<TroopFeatureSource>,
    ): List<BattlePreparationAction> =
        sources.mapNotNull { source ->
            val actionId = when (source.skillId) {
                296_231 -> "78".toInt(36)
                296_233 -> "7c".toInt(36)
                296_203 -> "79".toInt(36)
                296_332 -> "2e".toInt(36)
                296_333 -> "a5".toInt(36)
                else -> return@mapNotNull null
            }
            BattlePreparationAction(
                stage = BattlePreparationStage.TROOP,
                sourceId = source.skillId,
                sourcePosition = source.position,
                targetPosition = source.position,
                actionId = actionId,
                amountExact = when (source.skillId) {
                    296_203 -> 10.0
                    296_231 -> 12.0
                    296_233 -> 20.0
                    296_333 -> 1.0
                    else -> null
                },
            )
        }

    private fun surfaceRuntimeModifiers(spec: BattleHeroSpec): List<BattleModifier> {
        val skillId = spec.surfaceSkillId.takeIf { it > 0 } ?: return emptyList()
        return config.skillDetails(skillId).mapNotNull { detail ->
            if (detail.calcPos != 31) return@mapNotNull null
            val amount = detail.constantParam * detail.initEffectRatio / 100
            when (detail.effectId) {
                531 -> BattleModifier.DamageDealtPercent(
                    school = DamageSchool.PHYSICAL,
                    percent = amount,
                )
                533 -> BattleModifier.DamageDealtPercent(
                    school = DamageSchool.STRATEGY,
                    percent = amount,
                )
                522 -> BattleModifier.DamageTakenPercent(
                    school = DamageSchool.PHYSICAL,
                    percent = -amount,
                )
                524 -> BattleModifier.DamageTakenPercent(
                    school = DamageSchool.STRATEGY,
                    percent = -amount,
                )
                else -> null
            }
        }
    }

    /**
     * The active entry in Tb_hero.hero_features is itself a battle skill. The
     * client resolves its property additions through the ordinary skill-detail
     * table at level 1, so retain the configured detail order and scaling here.
     */
    private fun surfaceEffects(specs: List<BattleHeroSpec>): List<StaticEffect> =
        specs.flatMap { spec ->
            val skillId = spec.surfaceSkillId.takeIf { it > 0 } ?: return@flatMap emptyList()
            config.skillDetails(skillId).mapNotNull { detail ->
                val stat = detail.effectId.toBattleStat() ?: return@mapNotNull null
                if (detail.effectId !in 101..105 || detail.calcPos != 0) return@mapNotNull null
                val scaledConstant = detail.constantParam.toDouble() * detail.initEffectRatio / 100.0
                val percent = kotlin.math.abs(scaledConstant) > 500_000.0
                val strength = if (percent) {
                    scaledConstant / PERCENT_ATTRIBUTE_SCALE
                } else {
                    scaledConstant / FLAT_ATTRIBUTE_SCALE
                }
                StaticEffect(
                    stage = BattlePreparationStage.SURFACE,
                    sourceId = skillId,
                    sourcePosition = spec.position,
                    targetPosition = spec.position,
                    stat = stat,
                    strength = strength.toInt(),
                    strengthExact = strength,
                    percent = percent,
                )
            }
        }

    private fun surfacePreparationActions(
        specs: List<BattleHeroSpec>,
    ): List<BattlePreparationAction> =
        specs.flatMap { spec ->
            val skillId = spec.surfaceSkillId.takeIf { it > 0 } ?: return@flatMap emptyList()
            config.skillDetails(skillId).mapNotNull { detail ->
                if (detail.calcPos != 31 || detail.effectId !in SURFACE_MODIFIER_EFFECTS) {
                    return@mapNotNull null
                }
                BattlePreparationAction(
                    stage = BattlePreparationStage.SURFACE,
                    sourceId = skillId,
                    sourcePosition = spec.position,
                    targetPosition = spec.position,
                    actionId = "0s".toInt(36),
                    actionParameter = detail.effectId,
                    compactStatusAction = true,
                )
            }
        }

    private fun fixedTroopEffect(
        source: TroopFeatureSource,
        stat: BattleStat,
        amount: Int,
    ): StaticEffect =
        StaticEffect(
            stage = BattlePreparationStage.TROOP,
            sourceId = source.skillId,
            sourcePosition = source.position,
            targetPosition = source.position,
            stat = stat,
            strength = amount,
            percent = false,
        )

    private fun equipmentEffects(specs: List<BattleHeroSpec>): List<StaticEffect> =
        specs.flatMap { spec ->
            val equipmentId = spec.equipmentIds.firstOrNull() ?: return@flatMap emptyList()
            spec.equipmentSkillIds.flatMapIndexed { index, skillId ->
                val level = spec.equipmentSkillLevels.getOrElse(index) { DEFAULT_EQUIPMENT_LEVEL }
                config.skillDetails(skillId).mapNotNull { detail ->
                    val stat = detail.effectId.toBattleStat() ?: return@mapNotNull null
                    val percent =
                        detail.effectId != 106 &&
                            kotlin.math.abs(detail.constantParam) >= PERCENT_ATTRIBUTE_SCALE
                    val strength = when {
                        detail.effectId == 106 -> detail.constantParam.toDouble() * level
                        percent -> detail.constantParam.toDouble() / PERCENT_ATTRIBUTE_SCALE * level
                        else -> detail.constantParam.toDouble() / FLAT_ATTRIBUTE_SCALE * level
                    }
                    StaticEffect(
                        stage = BattlePreparationStage.EQUIPMENT,
                        sourceId = skillId,
                        containerSourceId = equipmentId,
                        sourcePosition = spec.position,
                        targetPosition = spec.position,
                        stat = stat,
                        strength = strength.toInt(),
                        strengthExact = strength,
                        percent = percent,
                    )
                }
            }
        }

    private fun equipmentFeatureEffects(specs: List<BattleHeroSpec>): List<StaticEffect> =
        specs.flatMap { spec ->
            spec.equipmentFeatureSkillIds.flatMapIndexed { index, skillId ->
                val level = spec.equipmentFeatureSkillLevels.getOrElse(index) { 0 }
                config.skillDetails(skillId).mapNotNull { detail ->
                    val stat = detail.effectId.toBattleStat() ?: return@mapNotNull null
                    val percent =
                        detail.effectId != 106 &&
                            kotlin.math.abs(detail.constantParam) >= PERCENT_ATTRIBUTE_SCALE
                    val strength = when {
                        detail.effectId == 106 -> detail.constantParam.toDouble() * level
                        percent ->
                            detail.constantParam.toDouble() / PERCENT_ATTRIBUTE_SCALE * level
                        else -> detail.constantParam.toDouble() / FLAT_ATTRIBUTE_SCALE * level
                    }
                    StaticEffect(
                        stage = BattlePreparationStage.EQUIPMENT,
                        sourceId = skillId,
                        containerSourceId = spec.equipmentIds.firstOrNull() ?: skillId,
                        sourcePosition = spec.position,
                        targetPosition = spec.position,
                        stat = stat,
                        strength = strength.toInt(),
                        strengthExact = strength,
                        percent = percent,
                    )
                }
            }
        }

    private fun equipmentPreparationModifiers(
        specs: List<BattleHeroSpec>,
    ): List<BattlePreparationModifier> =
        specs.flatMap { spec ->
            val equipmentId = spec.equipmentIds.firstOrNull() ?: return@flatMap emptyList()
            spec.equipmentSkillIds.flatMapIndexed { index, skillId ->
                val level = spec.equipmentSkillLevels.getOrElse(index) { DEFAULT_EQUIPMENT_LEVEL }
                config.skillDetails(skillId).mapNotNull { detail ->
                    if (
                        detail.effectId !in PREPARATION_MODIFIER_EFFECTS ||
                        (detail.effectId == 533 && detail.effectParam == 3)
                    ) {
                        return@mapNotNull null
                    }
                    BattlePreparationModifier(
                        stage = BattlePreparationStage.EQUIPMENT,
                        sourceId = skillId,
                        sourcePosition = spec.position,
                        targetPosition = spec.position,
                        effectId = detail.effectId,
                        amount = detail.constantParam * level,
                        containerSourceId = equipmentId,
                    )
                }
            }
        }

    private fun equipmentRuntimeModifiers(spec: BattleHeroSpec): List<BattleModifier> =
        spec.equipmentSkillIds.flatMapIndexed { index, skillId ->
            val level = spec.equipmentSkillLevels.getOrElse(index) { DEFAULT_EQUIPMENT_LEVEL }
            equipmentRuntimeDetails(skillId, level).mapNotNull { projected ->
                val detail = projected.detail
                val amount = detail.constantParam * projected.level
                when (detail.effectId) {
                    161 -> defenseIgnoreModifier(
                        detail.effectParam,
                        detail.constantParam,
                        projected.level,
                    )
                    251 -> specialDamageTag(detail.effectParam)?.let { tag ->
                        BattleModifier.DamageDealtPercent(
                            tag = tag,
                            percent = amount,
                        )
                    }
                    321 -> BattleModifier.DamageDealtPercent(
                        origin = DamageOrigin.NORMAL,
                        percent = amount,
                    )
                    322 -> BattleModifier.DamageDealtPercent(
                        origin = DamageOrigin.ACTIVE,
                        percent = amount,
                    )
                    325 -> BattleModifier.DamageDealtPercent(
                        origin = DamageOrigin.PURSUIT,
                        percent = amount,
                    )
                    351 -> BattleModifier.DamageTakenPercent(
                        origin = DamageOrigin.NORMAL,
                        percent = -amount,
                    )
                    352 -> BattleModifier.DamageTakenPercent(
                        origin = DamageOrigin.ACTIVE,
                        percent = -amount,
                    )
                    354 -> BattleModifier.DamageTakenPercent(
                        origin = DamageOrigin.COMMAND,
                        percent = -amount,
                    )
                    421, 422, 423, 424 -> BattleModifier.DamageTakenPercent(
                        percent = -amount,
                        requiredStatus = requireNotNull(
                            conditionalDamageStatus(detail.effectId),
                        ),
                    )
                    271 -> BattleModifier.RecoveryDealtPercent(amount)
                    281 -> BattleModifier.RecoveryTakenPercent(amount)
                    531 -> BattleModifier.DamageDealtPercent(
                        school = DamageSchool.PHYSICAL,
                        percent = amount,
                        targetCondition = projected.targetCondition,
                    )
                    533 -> BattleModifier.DamageDealtPercent(
                        school = DamageSchool.STRATEGY,
                        percent = amount,
                        targetCondition = projected.targetCondition,
                    )
                    522 -> BattleModifier.DamageTakenPercent(
                        school = DamageSchool.PHYSICAL,
                        percent = -amount,
                    )
                    524 -> BattleModifier.DamageTakenPercent(
                        school = DamageSchool.STRATEGY,
                        percent = -amount,
                    )
                    else -> null
                }
            }.distinct()
        }

    private fun equipmentRuntimeDetails(
        skillId: Int,
        level: Int,
        inheritedTargetCondition: DamageTargetCondition? = null,
        visited: Set<Int> = emptySet(),
    ): List<EquipmentRuntimeDetail> {
        if (skillId in visited) return emptyList()
        val path = visited + skillId
        return config.skillDetails(skillId).flatMap { detail ->
            val targetCondition = when {
                detail.calcPos == LOWEST_TROOPS_TARGET_CALC_POSITION ->
                    DamageTargetCondition.LOWEST_TROOPS
                else -> inheritedTargetCondition
            }
            if (
                detail.effectId in EQUIPMENT_RUNTIME_CHILD_EFFECT_IDS &&
                detail.constantParam > 0 &&
                targetCondition != null
            ) {
                equipmentRuntimeDetails(
                    skillId = detail.constantParam,
                    level = CHILD_SKILL_LEVEL,
                    inheritedTargetCondition = targetCondition,
                    visited = path,
                )
            } else {
                listOf(
                    EquipmentRuntimeDetail(
                        detail = detail,
                        level = level,
                        targetCondition = inheritedTargetCondition,
                    ),
                )
            }
        }
    }

    private fun equipmentFeatureRuntimeModifiers(spec: BattleHeroSpec): List<BattleModifier> =
        spec.equipmentFeatureSkillIds.flatMapIndexed { index, skillId ->
            val level = spec.equipmentFeatureSkillLevels.getOrElse(index) { 0 }
            config.skillDetails(skillId).mapNotNull { detail ->
                val amount = detail.constantParam * level
                when (detail.effectId) {
                    122 -> when (skillId) {
                        450011 -> (
                            spec.initialSkillId
                                ?: config.hero(spec.heroId)?.initialSkillId
                            )?.takeIf { it > 0 }?.let { initialSkillId ->
                            BattleModifier.RoundMainSkillProbabilityPercent(
                                percent = level,
                                skillId = initialSkillId,
                                rounds = config.skillDetails(skillId)
                                    .mapTo(linkedSetOf()) { it.delayRound + 1 },
                                requiredEffectId = 301,
                            )
                        }
                        450018 -> BattleModifier.DamageTakenPercent(
                            percent = -level,
                            requiredSourceInherentStatBelowTarget =
                                BattleStat.DEFENSE,
                        )
                        450019 -> BattleModifier.TargetStatusCountDamageDealtPercent(
                            percentPerStatus = level,
                            countedStatuses = JIXU_COUNTED_STATUSES,
                            maxStatuses = 5,
                        )
                        450020 -> BattleModifier.HurtStackingDamageTakenPercent(level)
                        450022 -> (
                            spec.initialSkillId
                                ?: config.hero(spec.heroId)?.initialSkillId
                            )?.takeIf { it > 0 }?.let { initialSkillId ->
                            BattleModifier.MainSkillRecoveryNextDamageTakenPercent(
                                percent = level,
                                skillId = initialSkillId,
                            )
                        }
                        450038 ->
                            BattleModifier.NextStrategyDamageAfterNormalAttackPercent(level)
                        450041 -> BattleModifier.DamageDealtPercent(
                            percent = level,
                            targetCondition = DamageTargetCondition.NEAREST_ENEMY,
                        )
                        450042 ->
                            BattleModifier.TroopLossRecoveryTakenPercent(level)
                        in 460061..460064 ->
                            config.skillDetails(detail.constantParam)
                                .singleOrNull { child -> child.effectId == 311 }
                                ?.let { child ->
                                    BattleModifier.OpeningControlDurationIncrease(
                                        rounds = kotlin.math.abs(child.constantParam)
                                            .coerceAtLeast(1),
                                        availableHits = child.availableHit.coerceAtLeast(1),
                                        rootSkillId = skillId,
                                        skillId = detail.constantParam,
                                        effectId = child.effectId,
                                        detailId = child.detailId,
                                    )
                                }
                        else -> null
                    }
                    131 -> (
                        spec.initialSkillId
                            ?: config.hero(spec.heroId)?.initialSkillId
                        )?.takeIf { it > 0 }?.let { initialSkillId ->
                        BattleModifier.SkillProbabilityPercent(
                            percent = level,
                            skillId = initialSkillId,
                        )
                    }
                    161 -> defenseIgnoreModifier(
                        detail.effectParam,
                        detail.constantParam,
                        level,
                    )
                    251 -> specialDamageTag(detail.effectParam)?.let { tag ->
                        BattleModifier.DamageDealtPercent(
                            tag = tag,
                            percent = amount,
                        )
                    }
                    321 -> BattleModifier.DamageDealtPercent(
                        origin = DamageOrigin.NORMAL,
                        percent = level,
                    )
                    322 -> BattleModifier.DamageDealtPercent(
                        origin = DamageOrigin.ACTIVE,
                        percent = level,
                    )
                    325 -> BattleModifier.DamageDealtPercent(
                        origin = DamageOrigin.PURSUIT,
                        percent = level,
                    )
                    351 -> BattleModifier.DamageTakenPercent(
                        origin = DamageOrigin.NORMAL,
                        percent = -level,
                    )
                    352 -> BattleModifier.DamageTakenPercent(
                        origin = DamageOrigin.ACTIVE,
                        percent = -amount,
                    )
                    354 -> BattleModifier.DamageTakenPercent(
                        origin = DamageOrigin.COMMAND,
                        percent = -amount,
                    )
                    271 -> BattleModifier.RecoveryDealtPercent(amount)
                    281 -> BattleModifier.RecoveryTakenPercent(amount)
                    952 -> BattleModifier.NormalAttackDisabled
                    522, 524 -> {
                        val school = if (detail.effectId == 522) {
                            DamageSchool.PHYSICAL
                        } else {
                            DamageSchool.STRATEGY
                        }
                        if (detail.availableRounds > 0) {
                            BattleModifier.OpeningDamageTakenPercent(
                                school = school,
                                percent = -level,
                                durationRounds = detail.availableRounds,
                                skillId = skillId,
                                effectId = detail.effectId,
                                detailId = detail.detailId,
                            )
                        } else {
                            BattleModifier.DamageTakenPercent(
                                school = school,
                                percent = -level,
                            )
                        }
                    }
                    else -> null
                }
            }.distinct()
        }

    private fun defenseIgnoreModifier(
        effectParam: Int,
        constantParam: Int,
        level: Int,
    ): BattleModifier.DefenseIgnorePercent? {
        val stat = when (effectParam) {
            2 -> BattleStat.DEFENSE
            3 -> BattleStat.STRATEGY
            else -> return null
        }
        return BattleModifier.DefenseIgnorePercent(
            percent = constantParam * level / 1_000,
            stat = stat,
        )
    }

    private fun specialDamageTag(effectParam: Int): DamageTag? =
        when (effectParam) {
            303 -> DamageTag.SHAKE
            304 -> DamageTag.PANIC
            305 -> DamageTag.BURN
            306 -> DamageTag.HEX
            307 -> DamageTag.FIRE
            else -> null
        }

    private fun conditionalDamageStatus(effectId: Int): BattleStatus? =
        when (effectId) {
            421 -> BattleStatus.CONFUSION
            422 -> BattleStatus.HESITATION
            423 -> BattleStatus.BERSERK
            424 -> BattleStatus.DISARM
            else -> null
        }

    private fun equipmentPreparationActions(
        specs: List<BattleHeroSpec>,
    ): List<BattlePreparationAction> =
        specs.flatMap { spec ->
            val equipmentId = spec.equipmentIds.firstOrNull() ?: return@flatMap emptyList()
            spec.equipmentSkillIds.flatMapIndexed { index, skillId ->
                val level = spec.equipmentSkillLevels.getOrElse(index) { DEFAULT_EQUIPMENT_LEVEL }
                val details = config.skillDetails(skillId)
                val combinedActiveAndPursuit =
                    details.any { it.effectId == 322 } && details.any { it.effectId == 325 }
                details.mapNotNull { detail ->
                    val actionId = when (detail.effectId) {
                        321 -> "6x".toInt(36)
                        322 -> (
                            if (combinedActiveAndPursuit || detail.effectParam != 0) "bf" else "79"
                            ).toInt(36)
                        325 -> (if (combinedActiveAndPursuit) "bg" else "7d").toInt(36)
                        351 -> "6w".toInt(36)
                        352 -> "78".toInt(36)
                        281 -> "9b".toInt(36)
                        421 -> "7g".toInt(36)
                        422 -> "7m".toInt(36)
                        423 -> "7i".toInt(36)
                        424 -> "7k".toInt(36)
                        161 -> (if (detail.effectParam == 3) "a4" else "a3").toInt(36)
                        171 -> "a5".toInt(36)
                        251 -> "99".toInt(36)
                        533 -> if (detail.effectParam == 3) "dr".toInt(36) else return@mapNotNull null
                        504 -> "1w".toInt(36)
                        122 -> if (detail.calcPos in EQUIPMENT_PREPARATION_CHILD_CALC_POSITIONS) {
                            "8c".toInt(36)
                        } else {
                            return@mapNotNull null
                        }
                        else -> return@mapNotNull null
                    }
                    val scale = if (detail.effectId == 161) 1_000.0 else 1.0
                    BattlePreparationAction(
                        stage = BattlePreparationStage.EQUIPMENT,
                        sourceId = skillId,
                        sourcePosition = spec.position,
                        targetPosition = if (detail.effectId == 504) 0 else spec.position,
                        actionId = actionId,
                        amountExact = (detail.constantParam * level / scale).takeUnless {
                            detail.effectId in setOf(122, 504)
                        },
                        actionParameter = when (detail.effectId) {
                            122 -> detail.constantParam
                            251, 533 -> detail.effectParam
                            else -> null
                        },
                        appendSourcePosition = detail.effectId == 504,
                        compactStatusAction = detail.effectId == 122,
                        containerSourceId = equipmentId,
                    )
                }
            }
        }

    private fun equipmentFeaturePreparationActions(
        specs: List<BattleHeroSpec>,
    ): List<BattlePreparationAction> =
        specs.flatMap { spec ->
            val equipmentId = spec.equipmentIds.singleOrNull() ?: return@flatMap emptyList()
            val initialSkillId = config.hero(spec.heroId)?.initialSkillId?.takeIf { it > 0 }
            spec.equipmentFeatureSkillIds.flatMapIndexed { index, featureSkillId ->
                val level = spec.equipmentFeatureSkillLevels.getOrNull(index)
                    ?.takeIf { it > 0 }
                    ?: return@flatMapIndexed emptyList()
                config.skillDetails(featureSkillId).mapNotNull { detail ->
                    when (detail.effectId) {
                        122 -> BattlePreparationAction(
                            stage = BattlePreparationStage.EQUIPMENT,
                            sourceId = featureSkillId,
                            sourcePosition = spec.position,
                            targetPosition = spec.position,
                            actionId = "8c".toInt(36),
                            actionParameter = detail.constantParam,
                            compactStatusAction = true,
                            containerSourceId = equipmentId,
                        )
                        131 -> initialSkillId?.let { skillId ->
                            BattlePreparationAction(
                                stage = BattlePreparationStage.EQUIPMENT,
                                sourceId = featureSkillId,
                                sourcePosition = spec.position,
                                targetPosition = spec.position,
                                actionId = "8x".toInt(36),
                                amountExact = level.toDouble(),
                                actionParameter = skillId,
                                containerSourceId = equipmentId,
                            )
                        }
                        271 -> BattlePreparationAction(
                            stage = BattlePreparationStage.EQUIPMENT,
                            sourceId = featureSkillId,
                            sourcePosition = spec.position,
                            targetPosition = spec.position,
                            actionId = "9c".toInt(36),
                            amountExact = level.toDouble(),
                            containerSourceId = equipmentId,
                        )
                        else -> null
                    }
                }.distinctBy { action ->
                    listOf(action.actionId, action.sourceId, action.actionParameter, action.amountExact)
                }
            }
        }

    private fun countryEffects(specs: List<BattleHeroSpec>): List<StaticEffect> {
        val countryGroup = specs
            .groupBy { config.hero(it.heroId)?.country ?: 0 }
            .entries
            .firstOrNull { (country, heroes) -> country in 1..6 && heroes.size >= 2 }
            ?: return emptyList()
        val sourceId = if (countryGroup.key == 6) 295_140 else 295_000 + countryGroup.key * 10
        return specs.flatMap { spec ->
            PRIMARY_STATS.map { stat ->
                StaticEffect(
                    stage = BattlePreparationStage.ARMY,
                    sourceId = sourceId,
                    targetPosition = spec.position,
                    stat = stat,
                    strength = COUNTRY_BONUS_PERCENT,
                )
            }
        }
    }

    private fun troopTypeEffects(specs: List<BattleHeroSpec>): List<StaticEffect> {
        val typeGroup = specs
            .groupBy {
                (it.heroType ?: config.hero(it.heroId)?.heroType ?: 0) % 10
            }
            .entries
            .firstOrNull { (type, heroes) -> type in 1..3 && heroes.size >= 2 }
            ?: return emptyList()
        val sourceId = when (typeGroup.key) {
            1 -> if (typeGroup.value.size == 2) 291_005 else 291_006
            2 -> if (typeGroup.value.size == 2) 291_003 else 291_004
            else -> if (typeGroup.value.size == 2) 291_001 else 291_002
        }
        val stats = when (typeGroup.key) {
            1 -> listOf(BattleStat.DEFENSE, BattleStat.SPEED)
            2 -> listOf(BattleStat.ATTACK, BattleStat.DEFENSE)
            else -> listOf(BattleStat.ATTACK, BattleStat.SPEED)
        }
        val percent = if (typeGroup.value.size == 2) 5 else 10
        return typeGroup.value.flatMap { spec ->
            stats.map { stat ->
                StaticEffect(
                    stage = BattlePreparationStage.ARMY,
                    sourceId = sourceId,
                    targetPosition = spec.position,
                    stat = stat,
                    strength = percent,
                )
            }
        }
    }

    private fun materializeEffects(
        effects: List<StaticEffect>,
        specs: List<BattleHeroSpec>,
    ): List<BattlePreparationEffect> {
        val currentStats = specs.associate { spec ->
            val heroConfig = config.hero(spec.heroId) ?: error("未知武将配置: ${spec.heroId}")
            val equipmentModifiers = equipmentModifiers(spec)
            val formationBonus = config.armyBonusesFor(specs.map { it.heroId })
                .fold(BattleStats.ZERO) { total, bonus -> total + bonus.stats }
            spec.position to (
                heroConfig.stats +
                    heroConfig.growth.scale(spec.level.coerceAtLeast(1) - 1) +
                    spec.attributePoints +
                    formationBonus +
                    if (spec.equipmentSkillIds.isEmpty()) {
                        equipmentModifiers.statBonus()
                    } else {
                        BattleStats.ZERO
                    }
                )
        }.toMutableMap()
        return effects.map { effect ->
            val before = currentStats.getValue(effect.targetPosition)
            val deltaStats = effect.toStats(before)
            val after = before + deltaStats
            currentStats[effect.targetPosition] = after
            val delta = deltaStats.precise(effect.stat)
            val valueAfter = after.precise(effect.stat)
            BattlePreparationEffect(
                stage = effect.stage,
                sourceId = effect.sourceId,
                containerSourceId = effect.containerSourceId,
                sourcePosition = effect.sourcePosition,
                targetPosition = effect.targetPosition,
                stat = effect.stat,
                strength = effect.strength,
                delta = delta.toInt(),
                valueAfter = valueAfter.toInt(),
                deltaExact = roundOneDecimal(delta),
                valueAfterExact = roundOneDecimal(valueAfter),
                percent = effect.percent,
                strengthExact = effect.strengthExact,
            )
        }
    }

    private fun equipmentModifiers(spec: BattleHeroSpec): List<BattleModifier> =
        spec.equipmentIds.flatMap { equipmentId ->
            val equipment = equipmentRepository?.equipment(equipmentId)
                ?: return@flatMap listOf(BattleModifier.Unsupported(equipmentId, "未知装备: $equipmentId"))
            BattleModifierParser.parseEquipment(equipment, emptyList())
        }

    private companion object {
        val EQUIPMENT_PREPARATION_CHILD_CALC_POSITIONS =
            setOf(31, 311, 981, 991, 992, 995, 3_116, 999_999)
        const val DEFAULT_SKILL_LEVEL = 1
        const val DEFAULT_EQUIPMENT_LEVEL = 1
        const val CHILD_SKILL_LEVEL = 1
        const val LOWEST_TROOPS_TARGET_CALC_POSITION = 991
        const val COUNTRY_BONUS_PERCENT = 10
        const val FLAT_ATTRIBUTE_SCALE = 100.0
        const val PERCENT_ATTRIBUTE_SCALE = 1_000_000.0
        val EQUIPMENT_RUNTIME_CHILD_EFFECT_IDS = setOf(122, 123)
        val PREPARATION_MODIFIER_EFFECTS = setOf(522, 524, 531, 533)
        val SURFACE_MODIFIER_EFFECTS = setOf(522, 524, 531, 533)
        val JIXU_COUNTED_STATUSES = setOf(
            BattleStatus.CONFUSION,
            BattleStatus.BERSERK,
            BattleStatus.HESITATION,
            BattleStatus.DISARM,
            BattleStatus.PANIC,
            BattleStatus.SHAKE,
            BattleStatus.BURN,
            BattleStatus.HEX,
        )
        val PRIMARY_STATS = listOf(
            BattleStat.ATTACK,
            BattleStat.DEFENSE,
            BattleStat.STRATEGY,
            BattleStat.SPEED,
        )
    }
}

private data class StaticEffect(
    val stage: BattlePreparationStage,
    val sourceId: Int,
    val targetPosition: Int,
    val stat: BattleStat,
    val strength: Int,
    val sourcePosition: Int? = null,
    val percent: Boolean = true,
    val containerSourceId: Int = sourceId,
    val strengthExact: Double = strength.toDouble(),
) {
    fun toStats(base: BattleStats): BattleStats {
        val amountHundredths = if (percent) {
            (base.precise(stat) * strengthExact).toInt()
        } else {
            (strengthExact * 100).toInt()
        }
        return when (stat) {
            BattleStat.ATTACK -> BattleStats.fromHundredths(amountHundredths, 0, 0, 0, 0, 0)
            BattleStat.DEFENSE -> BattleStats.fromHundredths(0, amountHundredths, 0, 0, 0, 0)
            BattleStat.STRATEGY -> BattleStats.fromHundredths(0, 0, amountHundredths, 0, 0, 0)
            BattleStat.SPEED -> BattleStats.fromHundredths(0, 0, 0, amountHundredths, 0, 0)
            BattleStat.SIEGE -> BattleStats.fromHundredths(0, 0, 0, 0, amountHundredths, 0)
            BattleStat.HIT_RANGE -> BattleStats(0, 0, 0, 0, 0, strengthExact.toInt())
        }
    }
}

private fun Int.toBattleStat(): BattleStat? = when (this) {
    101 -> BattleStat.ATTACK
    102 -> BattleStat.DEFENSE
    103 -> BattleStat.STRATEGY
    104 -> BattleStat.SPEED
    105 -> BattleStat.SIEGE
    106 -> BattleStat.HIT_RANGE
    else -> null
}

private data class TroopFeatureSource(
    val position: Int,
    val skillId: Int,
)

private fun roundOneDecimal(value: Double): Double =
    kotlin.math.round(value * 10.0) / 10.0

private fun BattleStats.scale(times: Int): BattleStats =
    BattleStats.fromHundredths(
        attack = kotlin.math.round(precise(BattleStat.ATTACK) * times * 100).toInt(),
        defense = kotlin.math.round(precise(BattleStat.DEFENSE) * times * 100).toInt(),
        strategy = kotlin.math.round(precise(BattleStat.STRATEGY) * times * 100).toInt(),
        speed = kotlin.math.round(precise(BattleStat.SPEED) * times * 100).toInt(),
        siege = kotlin.math.round(precise(BattleStat.SIEGE) * times * 100).toInt(),
        hitRange = hitRange * times,
    )

private fun List<BattleModifier>.statBonus(): BattleStats =
    fold(BattleStats.ZERO) { total, modifier ->
        total + when (modifier) {
            is BattleModifier.Stat -> modifier.toStats()
            else -> BattleStats.ZERO
        }
    }

private fun BattleModifier.Stat.toStats(): BattleStats =
    when (stat) {
        BattleStat.ATTACK -> BattleStats(amount, 0, 0, 0, 0, 0)
        BattleStat.DEFENSE -> BattleStats(0, amount, 0, 0, 0, 0)
        BattleStat.STRATEGY -> BattleStats(0, 0, amount, 0, 0, 0)
        BattleStat.SPEED -> BattleStats(0, 0, 0, amount, 0, 0)
        BattleStat.SIEGE -> BattleStats(0, 0, 0, 0, amount, 0)
        BattleStat.HIT_RANGE -> BattleStats(0, 0, 0, 0, 0, amount)
    }
