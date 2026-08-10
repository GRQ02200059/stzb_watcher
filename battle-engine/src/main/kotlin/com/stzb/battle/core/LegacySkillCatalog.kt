package com.stzb.battle.core

data class LegacySkillDefinition(
    val legacyId: Int,
    val name: String,
    val clientSkillIds: Set<Int>,
    val execute: (LegacySkillContext) -> SkillCastResult,
)

data class LegacySkillContext(
    val round: Int,
    val skillId: Int,
    val sourceRef: BattleHeroRef,
    val source: BattleHero,
    val enemies: BattleTeam,
    val allies: BattleTeam,
    val random: BattleRandom,
)

/**
 * Explicit definitions ported from stzbBattleSimulator-main. Client ids are
 * used at runtime; legacy ids only preserve traceability to the reference.
 */
object LegacySkillCatalog {
    private val definitions = listOf(
        definition(1001, "连战", 200223, 240223, execute = ::doubleAttack),
        definition(1002, "温酒斩将", 200208, execute = physicalPursuit(200)),
        definition(1003, "血溅黄砂", 200013, execute = ::bloodOnYellowSand),
        definition(1004, "方阵突击", 200088, 250088, execute = pursuitWithStatus(200, BattleStatus.CONFUSION)),
        definition(1005, "先驱突击", 200233, 300085, execute = ::vanguardAssault),
        definition(1006, "钝兵挫锐", 200658, 300065, execute = pursuitWithStatus(200, BattleStatus.DISARM)),
        definition(1007, "皇裔流离", 200016, 210016, 211016, 270016, execute = ::imperialRelief),
        definition(1008, "其疾如风", 200027, execute = ::swiftAsWind),
        definition(1009, "奋疾先登", 200961, 210961, 211961, 212961, 213961, execute = commandDamageBuff(8)),
        definition(1010, "奇兵拒北", 200930, 210930, 211930, 212930, 213930, execute = physicalActive(180)),
        definition(1011, "忠克猛烈", 200268, 211268, 212268, 213268, 214268, 215268, 216268, execute = physicalActive(180)),
        definition(1012, "愈战愈勇", 200643, 210643, 211643, 300084, 300643, 310643, execute = commandDamageBuff(8)),
        definition(1013, "始计", 200687, 210687, 211687, 212687, 300113, 310113, 311113, 312113, execute = commandDamageBuff(20)),
        definition(1014, "浑水摸鱼", 200235, 300078, execute = controlActive(BattleStatus.CONFUSION, 2)),
        definition(1015, "垒实迎击", 200900, 210900, 211900, 212900, 213900, 300106, 310106, 311106, 312106, 313106, execute = ::fortifiedCounter),
        definition(1016, "金匮要略", 200773, execute = ::imperialRelief),
        definition(1017, "神兵天降", 200204, 300096, 300204, execute = enemyDamageTaken(30)),
        definition(1018, "大赏三军", 200198, 300097, 300198, execute = commandDamageBuff(30)),
        definition(1019, "无心恋战", 200201, 300098, 300201, execute = enemyDamageDealt(-30)),
        definition(1020, "避其锋芒", 200194, 300091, 300194, execute = allyDamageTaken(-30)),
        definition(1021, "白衣渡江", 200648, execute = controlActive(BattleStatus.DISARM, 2)),
        definition(1022, "威震河朔", 200284, 200947, 270947, execute = physicalActive(180)),
        definition(1023, "反计之策", 200220, execute = controlActive(BattleStatus.HESITATION, 1)),
        definition(1024, "百战精兵", 200184, 300184, execute = allStats(32)),
        definition(1025, "持刀从武", 200965, 210965, 211965, 212965, 213965, 214965, 215965, execute = commandDamageBuff(15)),
        definition(1026, "一骑当千", 200647, 300116, execute = physicalActive(280)),
        definition(1027, "三军之众", 200886, 300087, execute = ::recoverAll),
        definition(1028, "魏武之世", 200023, 270023, execute = enemyAllStats(-15)),
        definition(1029, "火势风威", 200694, execute = ::strategyWithBurn),
        definition(1030, "衔命建功", 200254, 210254, 211254, 212254, 213254, 214254, execute = commandDamageBuff(20)),
        definition(1031, "胜兵求战", 200754, 210754, 211754, 212754, execute = ::firstAction),
        definition(1032, "深谋远虑", 200645, 210645, 211645, 300112, 300645, 310645, execute = commandDamageBuff(9)),
    )
    private val byClientId = definitions
        .flatMap { definition -> definition.clientSkillIds.map { it to definition } }
        .toMap()

    fun findLegacy(legacyId: Int): LegacySkillDefinition? =
        definitions.firstOrNull { it.legacyId == legacyId }

    fun findClient(clientSkillId: Int): LegacySkillDefinition? =
        byClientId[clientSkillId]

    private fun definition(
        legacyId: Int,
        name: String,
        vararg clientIds: Int,
        execute: (LegacySkillContext) -> SkillCastResult,
    ) = LegacySkillDefinition(legacyId, name, clientIds.toSet(), execute)

    private fun unchanged(context: LegacySkillContext, events: List<BattleEvent>, selfDelta: BattleStats = BattleStats.ZERO) =
        SkillCastResult(
            skillId = context.skillId,
            updatedEnemies = context.enemies,
            events = events,
            updatedAllies = context.allies,
            selfStatDelta = selfDelta,
            selfBuffDuration = 3,
        )

    private fun doubleAttack(context: LegacySkillContext): SkillCastResult =
        unchanged(
            context,
            listOf(context.status(context.sourceRef, BattleStatus.DOUBLE_ATTACK, 1)),
        )

    private fun bloodOnYellowSand(context: LegacySkillContext): SkillCastResult =
        unchanged(
            context,
            listOf(context.status(context.sourceRef, BattleStatus.HESITATION, 99)),
        )

    private fun vanguardAssault(context: LegacySkillContext): SkillCastResult =
        unchanged(
            context,
            listOf(
                context.status(context.sourceRef, BattleStatus.FIRST_ACTION, 3),
                context.status(context.sourceRef, BattleStatus.DOUBLE_ATTACK, 3),
            ),
            selfDelta = BattleStats(30, 0, 0, 0, 0, 0),
        )

    private fun imperialRelief(context: LegacySkillContext): SkillCastResult =
        unchanged(
            context,
            context.allies.heroes.filter { it.troops > 0 }.map { ally ->
                context.status(
                    BattleHeroRef(context.sourceRef.side, ally.position, ally.id),
                    BattleStatus.EMERGENCY_RECOVERY,
                    8,
                    power = context.source.stats.strategy,
                )
            },
        )

    private fun swiftAsWind(context: LegacySkillContext): SkillCastResult {
        val speed = 41 + ((context.source.stats.strategy - 80) * 0.075).toInt()
        val events = context.allies.heroes.filter { it.troops > 0 }.flatMap { ally ->
            val ref = BattleHeroRef(context.sourceRef.side, ally.position, ally.id)
            listOf(
                context.status(
                    ref, BattleStatus.SPEED_BUFF, 3,
                    statDelta = BattleStats(0, 0, 0, speed, 0, 0),
                ),
                context.status(ref, BattleStatus.DOUBLE_ATTACK, 3),
            )
        }
        return unchanged(context, events)
    }

    private fun commandDamageBuff(percent: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        unchanged(
            context,
            listOf(context.status(context.sourceRef, BattleStatus.ATTACK_BUFF, 8, power = percent)),
        )
    }

    private fun physicalActive(rate: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        damageOne(context, rate, null)
    }

    private fun controlActive(
        status: BattleStatus,
        duration: Int,
    ): (LegacySkillContext) -> SkillCastResult = { context ->
        val target = context.enemies.heroes.firstOrNull { it.troops > 0 }
        unchanged(
            context,
            target?.let {
                listOf(context.status(BattleHeroRef(context.sourceRef.side.opposite(), it.position, it.id), status, duration))
            }.orEmpty(),
        )
    }

    private fun fortifiedCounter(context: LegacySkillContext): SkillCastResult =
        unchanged(
            context,
            listOf(
                context.status(context.sourceRef, BattleStatus.EMERGENCY_RECOVERY, 8, power = context.source.stats.strategy),
                context.status(context.sourceRef, BattleStatus.INSIGHT, 1),
            ),
        )

    private fun enemyDamageTaken(percent: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        unchanged(
            context,
            context.enemies.heroes.map {
                context.status(
                    BattleHeroRef(context.sourceRef.side.opposite(), it.position, it.id),
                    BattleStatus.DEFENSE_DEBUFF,
                    3,
                    power = percent,
                )
            },
        )
    }

    private fun enemyDamageDealt(percent: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        unchanged(
            context,
            context.enemies.heroes.map {
                context.status(
                    BattleHeroRef(context.sourceRef.side.opposite(), it.position, it.id),
                    BattleStatus.ATTACK_DEBUFF,
                    3,
                    power = percent,
                )
            },
        )
    }

    private fun allyDamageTaken(percent: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        unchanged(
            context,
            context.allies.heroes.map {
                context.status(
                    BattleHeroRef(context.sourceRef.side, it.position, it.id),
                    BattleStatus.DEFENSE_BUFF,
                    3,
                    power = percent,
                )
            },
        )
    }

    private fun allStats(amount: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        unchanged(
            context,
            listOf(
                context.status(
                    context.sourceRef,
                    BattleStatus.ATTACK_BUFF,
                    8,
                    statDelta = BattleStats(amount, amount, amount, amount, 0, 0),
                ),
            ),
            selfDelta = BattleStats(amount, amount, amount, amount, 0, 0),
        )
    }

    private fun recoverAll(context: LegacySkillContext): SkillCastResult {
        val updated = context.allies.heroes.map { hero ->
            hero.copy(troops = (hero.troops + context.source.stats.strategy).coerceAtMost(hero.maxTroops))
        }
        val events = updated.map { hero ->
            val old = context.allies.heroes.first { it.position == hero.position }
            BattleEvent.Recovery(
                context.round,
                context.sourceRef,
                BattleHeroRef(context.sourceRef.side, hero.position, hero.id),
                hero.troops - old.troops,
                hero.troops,
                context.skillId,
            )
        }
        return unchanged(context, events).copy(updatedAllies = BattleTeam(updated, context.allies.armyBonuses))
    }

    private fun enemyAllStats(amount: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        unchanged(
            context,
            context.enemies.heroes.map {
                context.status(
                    BattleHeroRef(context.sourceRef.side.opposite(), it.position, it.id),
                    BattleStatus.ATTACK_DEBUFF,
                    8,
                    statDelta = BattleStats(amount, amount, amount, amount, 0, 0),
                )
            },
        )
    }

    private fun strategyWithBurn(context: LegacySkillContext): SkillCastResult =
        damageOne(context, 133, BattleStatus.BURN)

    private fun firstAction(context: LegacySkillContext): SkillCastResult =
        unchanged(context, listOf(context.status(context.sourceRef, BattleStatus.FIRST_ACTION, 8)))

    private fun physicalPursuit(rate: Int): (LegacySkillContext) -> SkillCastResult = { context ->
        damageOne(context, rate, status = null)
    }

    private fun pursuitWithStatus(
        rate: Int,
        status: BattleStatus,
    ): (LegacySkillContext) -> SkillCastResult = { context ->
        damageOne(context, rate, status)
    }

    private fun damageOne(
        context: LegacySkillContext,
        rate: Int,
        status: BattleStatus?,
    ): SkillCastResult {
        val target = context.enemies.heroes.filter { it.troops > 0 }.minByOrNull { it.position }
            ?: return unchanged(context, emptyList())
        val damage = (
            (context.source.stats.attack - target.stats.defense / 2).coerceAtLeast(1) *
                rate / 100
            ).coerceAtMost(target.troops)
        val updated = target.copy(troops = target.troops - damage)
        val targetRef = BattleHeroRef(context.sourceRef.side.opposite(), target.position, target.id)
        val events = mutableListOf<BattleEvent>(
            BattleEvent.SkillDamage(
                context.round, context.skillId, context.skillId * 100 + 1,
                context.sourceRef, targetRef, damage, updated.troops,
            ),
        )
        if (status != null) events += context.status(targetRef, status, 1)
        return SkillCastResult(
            skillId = context.skillId,
            updatedEnemies = context.enemies.copy(
                heroes = context.enemies.heroes.map { if (it.position == updated.position) updated else it },
            ),
            events = events,
            updatedAllies = context.allies,
        )
    }

    private fun LegacySkillContext.status(
        target: BattleHeroRef,
        status: BattleStatus,
        duration: Int,
        power: Int = 0,
        statDelta: BattleStats = BattleStats.ZERO,
    ) = BattleEvent.StatusApplied(
        round = round,
        source = sourceRef,
        target = target,
        status = status,
        durationRounds = duration,
        power = power,
        statDelta = statDelta,
        skillId = skillId,
    )
}
