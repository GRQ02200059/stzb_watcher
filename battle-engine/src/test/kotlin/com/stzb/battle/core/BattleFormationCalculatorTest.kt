package com.stzb.battle.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleFormationCalculatorTest {
    private val config = BattleConfigRepository.loadDefault()
    private val calculator = BattleFormationCalculator(config)

    @Test
    fun `formation applies growth allocation advance and camp attributes`() {
        val base = calculator.calculate(
            listOf(BattleHeroSpec(heroId = 100479, position = 0, troops = 1000, level = 1)),
        ).heroes.single()

        val developed = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100479,
                    position = 0,
                    troops = 1000,
                    level = 40,
                    attributePoints = BattleStats(
                        attack = 40,
                        defense = 0,
                        strategy = 0,
                        speed = 0,
                        siege = 0,
                        hitRange = 0,
                    ),
                    advanceLevel = 5,
                ),
            ),
        ).heroes.single()
        val hero = config.hero(100479)!!

        assertEquals(hero.stats.attack, base.stats.attack)
        assertEquals(hero.stats.defense, base.stats.defense)
        assertEquals(hero.stats.strategy, base.stats.strategy)
        assertEquals(hero.stats.speed, base.stats.speed)
        assertEquals(
            hero.growth.precise(BattleStat.ATTACK) * 39 + 40,
            developed.stats.precise(BattleStat.ATTACK) - base.stats.precise(BattleStat.ATTACK),
            0.001,
        )
        assertEquals(
            hero.growth.precise(BattleStat.SPEED) * 39,
            developed.stats.precise(BattleStat.SPEED) - base.stats.precise(BattleStat.SPEED),
            0.001,
        )
        assertEquals(2_000, developed.maxTroops)
        assertEquals(5, developed.advanceLevel)
        assertEquals(100, developed.morale)
    }

    @Test
    fun `formation uses verified country and troop sources instead of army-extra ids`() {
        val team = calculator.calculate(
            listOf(
                BattleHeroSpec(heroId = 100683, position = 0, troops = 1_000),
                BattleHeroSpec(heroId = 100672, position = 1, troops = 1_000),
                BattleHeroSpec(heroId = 100025, position = 2, troops = 1_000),
            ),
        )

        assertEquals(listOf(5), team.armyBonuses.map { it.id })
        assertEquals(
            setOf(295020, 291001),
            team.preparationEffects.mapTo(mutableSetOf()) { it.sourceId },
        )
        assertEquals(
            setOf(0, 1),
            team.preparationEffects
                .filter { it.sourceId == 291001 }
                .mapTo(mutableSetOf()) { it.targetPosition },
        )
        assertEquals(
            setOf(BattleStat.ATTACK, BattleStat.SPEED),
            team.preparationEffects
                .filter { it.sourceId == 291001 }
                .mapTo(mutableSetOf()) { it.stat },
        )
        assertEquals(false, team.preparationEffects.any { it.sourceId == 291005 })
    }

    @Test
    fun `two archers receive the verified five percent defense and speed bonus`() {
        val team = calculator.calculate(
            listOf(
                BattleHeroSpec(heroId = 100028, position = 0, troops = 1_000, level = 45),
                BattleHeroSpec(heroId = 100035, position = 1, troops = 1_000, level = 43),
                BattleHeroSpec(heroId = 100023, position = 2, troops = 1_000, level = 43),
            ),
        )

        val effects = team.preparationEffects.filter { it.sourceId == 291005 }

        assertEquals(setOf(0, 1), effects.mapTo(mutableSetOf()) { it.targetPosition })
        assertEquals(
            setOf(BattleStat.DEFENSE, BattleStat.SPEED),
            effects.mapTo(mutableSetOf()) { it.stat },
        )
        assertEquals(setOf(5), effects.mapTo(mutableSetOf()) { it.strength })
        assertEquals(true, effects.all { it.deltaExact > 0.0 })
    }

    @Test
    fun `two matching countries grant the country bonus to the whole team`() {
        val team = calculator.calculate(
            listOf(
                BattleHeroSpec(heroId = 100028, position = 0, troops = 1_000, level = 45),
                BattleHeroSpec(heroId = 100035, position = 1, troops = 1_000, level = 43),
                BattleHeroSpec(heroId = 100023, position = 2, troops = 1_000, level = 43),
            ),
        )

        val effects = team.preparationEffects.filter { it.sourceId == 295020 }

        assertEquals(setOf(0, 1, 2), effects.mapTo(mutableSetOf()) { it.targetPosition })
        assertEquals(12, effects.size)
    }

    @Test
    fun `active hero feature applies its configured flat attributes during surface preparation`() {
        val baseline = calculator.calculate(
            listOf(BattleHeroSpec(heroId = 100648, position = 1, troops = 1_000, level = 44)),
        ).heroes.single()
        val team = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    level = 44,
                    surfaceSkillId = 285314,
                ),
            ),
        )
        val hero = team.heroes.single()
        val effects = team.preparationEffects.filter { it.sourceId == 285314 }

        assertEquals(
            listOf(BattleStat.SPEED, BattleStat.DEFENSE, BattleStat.STRATEGY),
            effects.map { it.stat },
        )
        assertEquals(listOf(2.0, 5.0, 2.0), effects.map { it.deltaExact })
        assertEquals(setOf(BattlePreparationStage.SURFACE), effects.mapTo(mutableSetOf()) { it.stage })
        assertEquals(baseline.stats.speed + 2, hero.stats.speed)
        assertEquals(baseline.stats.defense + 5, hero.stats.defense)
        assertEquals(baseline.stats.strategy + 2, hero.stats.strategy)
    }

    @Test
    fun `non battle hero feature placeholder details do not change battle attributes`() {
        val baseline = calculator.calculate(
            listOf(BattleHeroSpec(heroId = 100648, position = 1, troops = 1_000, level = 44)),
        ).heroes.single()
        val team = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    level = 44,
                    surfaceSkillId = 281015,
                ),
            ),
        )

        assertEquals(baseline.stats, team.heroes.single().stats)
        assertEquals(emptyList(), team.preparationEffects.filter { it.sourceId == 281015 })
    }

    @Test
    fun `active hero feature applies configured damage modifier and preparation action`() {
        val team = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    surfaceSkillId = 281003,
                ),
            ),
        )
        val hero = team.heroes.single()

        assertEquals(
            listOf(BattleModifier.DamageDealtPercent(school = DamageSchool.STRATEGY, percent = 5)),
            hero.modifiers.filterIsInstance<BattleModifier.DamageDealtPercent>(),
        )
        assertEquals(
            listOf(
                BattlePreparationAction(
                    stage = BattlePreparationStage.SURFACE,
                    sourceId = 281003,
                    sourcePosition = 1,
                    targetPosition = 1,
                    actionId = "0s".toInt(36),
                    actionParameter = 533,
                    compactStatusAction = true,
                ),
            ),
            team.preparationActions,
        )
    }

    @Test
    fun `equipment recovery taken effect is preserved as a runtime modifier`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentSkillIds = listOf(400048),
                    equipmentSkillLevels = listOf(1),
                ),
            ),
        ).heroes.single()

        assertEquals(
            BattleModifier.RecoveryTakenPercent(25),
            hero.modifiers.filterIsInstance<BattleModifier.RecoveryTakenPercent>().single(),
        )
    }

    @Test
    fun `equipment feature recovery dealt effect uses its feature level`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentFeatureSkillIds = listOf(450016),
                    equipmentFeatureSkillLevels = listOf(7),
                ),
            ),
        ).heroes.single()

        assertEquals(
            BattleModifier.RecoveryDealtPercent(7),
            hero.modifiers.filterIsInstance<BattleModifier.RecoveryDealtPercent>().single(),
        )
    }

    @Test
    fun `equipment feature static attributes preserve percent and flat scaling`() {
        val baseline = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                ),
            ),
        ).heroes.single()
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentFeatureSkillIds = listOf(
                        450002,
                        450003,
                        450006,
                        450008,
                    ),
                    equipmentFeatureSkillLevels = listOf(10, 12, 14, 16),
                ),
            ),
        ).heroes.single()
        fun percentDelta(stat: BattleStat, percent: Int): Double =
            (baseline.stats.precise(stat) * percent).toInt() / 100.0

        assertEquals(
            percentDelta(BattleStat.ATTACK, 10),
            hero.stats.precise(BattleStat.ATTACK) -
                baseline.stats.precise(BattleStat.ATTACK),
            0.001,
        )
        assertEquals(
            24.0,
            hero.stats.precise(BattleStat.SPEED) -
                baseline.stats.precise(BattleStat.SPEED),
            0.001,
        )
        assertEquals(
            percentDelta(BattleStat.DEFENSE, 14),
            hero.stats.precise(BattleStat.DEFENSE) -
                baseline.stats.precise(BattleStat.DEFENSE),
            0.001,
        )
        assertEquals(
            percentDelta(BattleStat.STRATEGY, 16),
            hero.stats.precise(BattleStat.STRATEGY) -
                baseline.stats.precise(BattleStat.STRATEGY),
            0.001,
        )
    }

    @Test
    fun `ganzhi equipment feature adds its configured flat strategy`() {
        val baseline = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                ),
            ),
        ).heroes.single()
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentFeatureSkillIds = listOf(450036),
                    equipmentFeatureSkillLevels = listOf(12),
                ),
            ),
        ).heroes.single()

        assertEquals(
            24.0,
            hero.stats.precise(BattleStat.STRATEGY) -
                baseline.stats.precise(BattleStat.STRATEGY),
            0.001,
        )
    }

    @Test
    fun `equipment feature probability bonus is scoped to the inherent skill`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    initialSkillId = 200884,
                    equipmentFeatureSkillIds = listOf(450037),
                    equipmentFeatureSkillLevels = listOf(8),
                ),
            ),
        ).heroes.single()

        assertEquals(
            BattleModifier.SkillProbabilityPercent(
                percent = 8,
                skillId = 200884,
            ),
            hero.modifiers.filterIsInstance<BattleModifier.SkillProbabilityPercent>()
                .single(),
        )
    }

    @Test
    fun `equipment feature active damage reduction uses its feature level`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentFeatureSkillIds = listOf(450028),
                    equipmentFeatureSkillLevels = listOf(12),
                ),
            ),
        ).heroes.single()

        assertEquals(
            BattleModifier.DamageTakenPercent(
                origin = DamageOrigin.ACTIVE,
                percent = -12,
            ),
            hero.modifiers.filterIsInstance<BattleModifier.DamageTakenPercent>()
                .single { it.origin == DamageOrigin.ACTIVE },
        )
    }

    @Test
    fun `weishi feature preserves its inherent defense condition and level`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentFeatureSkillIds = listOf(450018),
                    equipmentFeatureSkillLevels = listOf(14),
                ),
            ),
        ).heroes.single()

        assertEquals(
            BattleModifier.DamageTakenPercent(
                percent = -14,
                requiredSourceInherentStatBelowTarget = BattleStat.DEFENSE,
            ),
            hero.modifiers.filterIsInstance<BattleModifier.DamageTakenPercent>().single(),
        )
    }

    @Test
    fun `jixu feature damage bonus follows target status count capped at five`() {
        val baseline = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 10_000,
                ),
            ),
        ).heroes.single()
        val equipped = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 10_000,
                    equipmentFeatureSkillIds = listOf(450019),
                    equipmentFeatureSkillLevels = listOf(9),
                ),
            ),
        ).heroes.single()
        val twoRelevantStatuses = baseline.copy(
            activeStatuses = setOf(
                BattleStatus.PANIC,
                BattleStatus.CONFUSION,
                BattleStatus.ATTACK_DEBUFF,
            ),
        )
        val sixRelevantStatuses = baseline.copy(
            activeStatuses = setOf(
                BattleStatus.PANIC,
                BattleStatus.SHAKE,
                BattleStatus.BURN,
                BattleStatus.HEX,
                BattleStatus.CONFUSION,
                BattleStatus.HESITATION,
            ),
        )

        fun withExpectedBonus(percent: Int) = baseline.copy(
            modifiers = baseline.modifiers +
                BattleModifier.DamageDealtPercent(percent = percent),
        )

        assertEquals(
            BattleDamageCalculator.physical(
                source = withExpectedBonus(18),
                target = twoRelevantStatuses,
            ),
            BattleDamageCalculator.physical(
                source = equipped,
                target = twoRelevantStatuses,
            ),
        )
        assertEquals(
            BattleDamageCalculator.strategy(
                source = withExpectedBonus(18),
                target = twoRelevantStatuses,
                ratePercent = 100,
            ),
            BattleDamageCalculator.strategy(
                source = equipped,
                target = twoRelevantStatuses,
                ratePercent = 100,
            ),
        )
        assertEquals(
            BattleDamageCalculator.physical(
                source = withExpectedBonus(45),
                target = sixRelevantStatuses,
            ),
            BattleDamageCalculator.physical(
                source = equipped,
                target = sixRelevantStatuses,
            ),
        )
        assertEquals(
            BattleDamageCalculator.strategy(
                source = withExpectedBonus(45),
                target = sixRelevantStatuses,
                ratePercent = 100,
            ),
            BattleDamageCalculator.strategy(
                source = equipped,
                target = sixRelevantStatuses,
                ratePercent = 100,
            ),
        )
    }

    @Test
    fun `pozhen feature raises only damage against nearest living enemy`() {
        val baseline = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 0,
                    troops = 10_000,
                ),
            ),
        ).heroes.single()
        val equipped = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 0,
                    troops = 10_000,
                    equipmentFeatureSkillIds = listOf(450041),
                    equipmentFeatureSkillLevels = listOf(12),
                ),
            ),
        ).heroes.single()
        val near = baseline.copy(
            id = BattleHeroId(200001),
            position = 2,
        )
        val far = baseline.copy(
            id = BattleHeroId(200002),
            position = 0,
        )
        val enemies = listOf(far, near)
        val resolver = BattleActionResolver()
        val expectedSource = baseline.copy(
            modifiers = baseline.modifiers +
                BattleModifier.DamageDealtPercent(percent = 12),
        )

        assertEquals(
            resolver.normalAttackDamage(
                source = expectedSource,
                target = near,
                random = FixedBattleRandom(0),
                enemies = enemies,
            ),
            resolver.normalAttackDamage(
                source = equipped,
                target = near,
                random = FixedBattleRandom(0),
                enemies = enemies,
            ),
        )
        assertEquals(
            resolver.normalAttackDamage(
                source = baseline,
                target = far,
                random = FixedBattleRandom(0),
                enemies = enemies,
            ),
            resolver.normalAttackDamage(
                source = equipped,
                target = far,
                random = FixedBattleRandom(0),
                enemies = enemies,
            ),
        )
        assertEquals(
            BattleDamageCalculator.strategy(
                source = expectedSource,
                target = near,
                ratePercent = 100,
                targetConditions = BattleDamageCalculator.targetConditions(
                    near,
                    enemies,
                ),
            ),
            BattleDamageCalculator.strategy(
                source = equipped,
                target = near,
                ratePercent = 100,
                targetConditions = BattleDamageCalculator.targetConditions(
                    near,
                    enemies,
                ),
            ),
        )
        assertEquals(
            BattleDamageCalculator.strategy(
                source = baseline,
                target = far,
                ratePercent = 100,
                targetConditions = BattleDamageCalculator.targetConditions(
                    far,
                    enemies,
                ),
            ),
            BattleDamageCalculator.strategy(
                source = equipped,
                target = far,
                ratePercent = 100,
                targetConditions = BattleDamageCalculator.targetConditions(
                    far,
                    enemies,
                ),
            ),
        )
    }

    @Test
    fun `buqu feature preserves its per hurt damage reduction level`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentFeatureSkillIds = listOf(450020),
                    equipmentFeatureSkillLevels = listOf(3),
                ),
            ),
        ).heroes.single()

        assertEquals(
            BattleModifier.HurtStackingDamageTakenPercent(3),
            hero.modifiers
                .filterIsInstance<BattleModifier.HurtStackingDamageTakenPercent>()
                .single(),
        )
    }

    @Test
    fun `equipment and feature defense ignore effects preserve configured attribute and scale`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentSkillIds = listOf(400019, 400051),
                    equipmentSkillLevels = listOf(6, 1),
                    equipmentFeatureSkillIds = listOf(450021, 450023),
                    equipmentFeatureSkillLevels = listOf(10, 10),
                ),
            ),
        ).heroes.single()
        val modifiers = hero.modifiers.filterIsInstance<BattleModifier.DefenseIgnorePercent>()

        assertEquals(
            listOf(16, 20),
            listOf(BattleStat.DEFENSE, BattleStat.STRATEGY).map { stat ->
                modifiers.filter { it.stat == stat }.sumOf { it.percent }
            },
        )
    }

    @Test
    fun `equipment damage modifiers preserve configured tags and command origin`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentSkillIds = listOf(400042),
                    equipmentSkillLevels = listOf(1),
                    equipmentFeatureSkillIds = listOf(450025),
                    equipmentFeatureSkillLevels = listOf(7),
                ),
            ),
        ).heroes.single()

        assertEquals(
            setOf<Pair<String?, Int>>(
                "PANIC" to 10,
                "BURN" to 10,
                "HEX" to 10,
                "FIRE" to 10,
            ),
            hero.modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .mapTo(mutableSetOf()) { modifier ->
                    modifier.tag?.name to modifier.percent
                },
        )
        assertEquals(
            BattleModifier.DamageTakenPercent(
                origin = DamageOrigin.COMMAND,
                percent = -7,
            ),
            hero.modifiers.filterIsInstance<BattleModifier.DamageTakenPercent>().single(),
        )
        assertEquals(
            5,
            hero.modifiers.count {
                it is BattleModifier.DamageDealtPercent ||
                    it is BattleModifier.DamageTakenPercent
            },
        )
    }

    @Test
    fun `direct equipment feature damage modifiers preserve configured origins`() {
        val hero = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    equipmentFeatureSkillIds = listOf(
                        450009,
                        450015,
                        450024,
                        450035,
                    ),
                    equipmentFeatureSkillLevels = listOf(10, 12, 10, 14),
                ),
            ),
        ).heroes.single()
        val dealt = hero.modifiers.filterIsInstance<BattleModifier.DamageDealtPercent>()
        val taken = hero.modifiers.filterIsInstance<BattleModifier.DamageTakenPercent>()

        assertEquals(
            12,
            dealt.filter { it.origin == DamageOrigin.NORMAL }.sumOf { it.percent },
        )
        assertEquals(
            24,
            dealt.filter { it.origin == DamageOrigin.ACTIVE }.sumOf { it.percent },
        )
        assertEquals(
            10,
            dealt.filter { it.origin == DamageOrigin.PURSUIT }.sumOf { it.percent },
        )
        assertEquals(
            -10,
            taken.filter { it.origin == DamageOrigin.NORMAL }.sumOf { it.percent },
        )
    }

    @Test
    fun `equipment child damage modifiers apply only to the lowest troop enemy`() {
        val baseline = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 10_000,
                ),
            ),
        ).heroes.single()
        val equipped = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 10_000,
                    equipmentSkillIds = listOf(400066),
                    equipmentSkillLevels = listOf(6),
                ),
            ),
        ).heroes.single()
        val low = baseline.copy(
            id = BattleHeroId(200001),
            position = 2,
            troops = 2_000,
            maxTroops = 10_000,
        )
        val high = baseline.copy(
            id = BattleHeroId(200002),
            position = 1,
            troops = 8_000,
            maxTroops = 10_000,
        )
        val enemies = listOf(low, high)
        val resolver = BattleActionResolver()
        val expectedLowDamage = resolver.normalAttackDamage(
            source = baseline.copy(
                modifiers = baseline.modifiers + BattleModifier.DamageDealtPercent(
                    school = DamageSchool.PHYSICAL,
                    percent = 15,
                ),
            ),
            target = low,
            random = FixedBattleRandom(0),
            enemies = enemies,
        )
        val baselineHighDamage = resolver.normalAttackDamage(
            source = baseline,
            target = high,
            random = FixedBattleRandom(0),
            enemies = enemies,
        )

        assertEquals(
            expectedLowDamage,
            resolver.normalAttackDamage(
                source = equipped,
                target = low,
                random = FixedBattleRandom(0),
                enemies = enemies,
            ),
        )
        assertEquals(
            baselineHighDamage,
            resolver.normalAttackDamage(
                source = equipped,
                target = high,
                random = FixedBattleRandom(0),
                enemies = enemies,
            ),
        )
        assertEquals(
            setOf<Pair<DamageSchool?, Int>>(
                DamageSchool.PHYSICAL to 15,
                DamageSchool.STRATEGY to 15,
            ),
            equipped.modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .mapTo(mutableSetOf()) { it.school to it.percent },
        )
    }

    @Test
    fun `equipment control damage reductions retain their required statuses`() {
        listOf(400046, 400080, 400107).forEach { skillId ->
            val hero = calculator.calculate(
                listOf(
                    BattleHeroSpec(
                        heroId = 100648,
                        position = 1,
                        troops = 1_000,
                        equipmentSkillIds = listOf(skillId),
                        equipmentSkillLevels = listOf(6),
                    ),
                ),
            ).heroes.single()

            assertEquals(
                setOf<Pair<BattleStatus?, Int>>(
                    BattleStatus.CONFUSION to -6,
                    BattleStatus.HESITATION to -6,
                    BattleStatus.BERSERK to -6,
                    BattleStatus.DISARM to -6,
                ),
                hero.modifiers
                    .filterIsInstance<BattleModifier.DamageTakenPercent>()
                    .mapTo(mutableSetOf()) { modifier ->
                        modifier.requiredStatus to modifier.percent
                    },
                "skill=$skillId modifiers=${hero.modifiers}",
            )
        }
    }

    @Test
    fun `troop special damage modifiers execute through the battle runtime`() {
        val heroType = requireNotNull(
            com.stzb.battle.core.ClientTroopTypeRepository.loadDefault()
                .heroTypeForSkillIds(listOf(296321)),
        )
        val attacker = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100648,
                    position = 1,
                    troops = 1_000,
                    heroType = heroType,
                ),
            ),
        )
        assertEquals(
            true,
            296321 in attacker.heroes.single().skillIds,
            "skills=${attacker.heroes.single().skillIds}",
        )

        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = attacker,
                defender = calculator.calculate(
                    listOf(
                        BattleHeroSpec(
                            heroId = 100479,
                            position = 1,
                            troops = 1_000,
                        ),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        assertEquals(
            setOf<Pair<String?, Int>>(
                "BURN" to 15,
                "FIRE" to 15,
                "SHAKE" to -30,
                "PANIC" to -30,
                "HEX" to -30,
            ),
            result.attacker.heroes.single().modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.tag != null }
                .mapTo(mutableSetOf()) { modifier ->
                    modifier.tag?.name to modifier.percent
                },
        )
    }

    @Test
    fun `formation preserves resolved hero type and projects configured troop counter profile`() {
        val converted = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100479,
                    position = 0,
                    troops = 1_000,
                    heroType = 21,
                ),
            ),
        ).heroes.single()
        val archer = calculator.calculate(
            listOf(
                BattleHeroSpec(
                    heroId = 100479,
                    position = 0,
                    troops = 1_000,
                    heroType = 1,
                ),
            ),
        ).heroes.single()

        assertEquals(21, converted.heroType)
        assertTrue(
            BattleModifier.TroopCounterDealtPercent(
                targetHeroType = 22,
                percent = 30,
            ) in archer.modifiers,
            "modifiers=${archer.modifiers}",
        )
        assertTrue(
            BattleModifier.TroopCounterTakenPercent(
                sourceHeroType = 22,
                percent = -30,
            ) in archer.modifiers,
            "modifiers=${archer.modifiers}",
        )
    }
}
