package com.stzb.battle.core

import com.stzb.battle.core.skill.BattleTrigger
import com.stzb.battle.core.skill.BattleTargetDecisionSource
import com.stzb.battle.core.skill.DefaultCompleteSkillEngine
import com.stzb.battle.core.skill.SkillBattleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialFullBattleReportDiffTest {
    @Test
    fun `all readable paper reports expose complete battle summaries`() {
        val reports = OfficialReportFixture.readableReports()

        assertEquals(28, reports.size)
        reports.forEach { report ->
            val summary = OfficialReportFixture.fullBattleSummary(
                OfficialReportFixture.read(report),
            )

            assertTrue(summary.rounds in 0..8, report.toString())
            if (summary.rounds == 0) {
                assertTrue(summary.actionRoundsByPosition.isEmpty(), report.toString())
            }
            assertTrue(
                summary.finalTroopsByPosition.keys.all { it in 1..6 },
                report.toString(),
            )
            assertTrue(
                summary.actionRoundsByPosition.values.flatten().all { it in 1..8 },
                report.toString(),
            )
        }
    }

    @Test
    fun `official full battles stay inside deterministic simulation envelopes`() {
        val config = BattleConfigRepository.loadDefault()
        val failures = mutableListOf<String>()
        var reportCount = 0
        var outcomeCoverageCount = 0
        var roundCoverageCount = 0
        var finalTroopCoverageCount = 0
        var finalTroopComparisonCount = 0
        var actionRoundMismatchCount = 0
        var skillTriggerMismatchCount = 0
        val damageRelativeErrors = Side.entries.associateWith {
            mutableListOf<Double>()
        }
        val recoveryRelativeErrors = mutableListOf<Double>()
        val troopLossRelativeErrors = mutableListOf<Double>()
        data class RelativeErrorSample(
            val report: java.nio.file.Path,
            val metric: String,
            val official: Int,
            val simulatedMedian: Int,
            val relativeError: Double,
        )
        val relativeErrorSamples = mutableListOf<RelativeErrorSample>()

        OfficialReportFixture.readableReports().forEach { report ->
            reportCount += 1
            val actions = OfficialReportFixture.read(report)
            val official = OfficialReportFixture.fullBattleSummary(actions)
            val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
            val initialTroopsByPosition = buildMap {
                request.attacker.heroes.forEach { hero ->
                    put(
                        ClientBattleTextReplayProtocol.position(
                            Side.ATTACKER,
                            hero.position,
                        ),
                        hero.troops,
                    )
                }
                request.defender.heroes.forEach { hero ->
                    put(
                        ClientBattleTextReplayProtocol.position(
                            Side.DEFENDER,
                            hero.position,
                        ),
                        hero.troops,
                    )
                }
            }
            val simulated = (0 until 32).map { seed ->
                OfficialReportFixture.fullBattleSummary(
                    BattleEngine.resolve(
                        request,
                        config,
                        SeededBattleRandom(seed),
                    ),
                )
            }

            val roundRange = simulated.minOf { it.rounds }..simulated.maxOf { it.rounds }
            val outcomes = simulated.mapTo(linkedSetOf()) { it.outcome }
            val roundCovered = official.rounds in roundRange
            val outcomeCovered = official.outcome in outcomes
            if (roundCovered) {
                roundCoverageCount += 1
            }
            if (outcomeCovered) {
                outcomeCoverageCount += 1
            }
            if (!roundCovered || !outcomeCovered) {
                failures += "$report rounds=${official.rounds}/$roundRange " +
                    "outcome=${official.outcome}/$outcomes"
            }
            val actionRoundMismatches = actionRoundMismatches(official, simulated)
            actionRoundMismatchCount += actionRoundMismatches.size
            if (actionRoundMismatches.isNotEmpty()) {
                failures += "$report actionRounds=${actionRoundMismatches.joinToString()}"
            }
            val skillTriggerMismatches = skillTriggerMismatches(official, simulated)
            skillTriggerMismatchCount += skillTriggerMismatches.size
            if (skillTriggerMismatches.isNotEmpty()) {
                failures += "$report skillTriggers=${skillTriggerMismatches.joinToString()}"
            }
            Side.entries.forEach { side ->
                val officialDamage = official.damageBySide.getValue(side)
                val values = simulated.map { it.damageBySide.getValue(side) }
                val interval = values.min()..values.max()
                if (officialDamage !in interval) {
                    failures += "$report $side damage=$officialDamage/$interval"
                }
                if (officialDamage > 0) {
                    val simulatedMedian = values.median()
                    val relativeError =
                        kotlin.math.abs(simulatedMedian - officialDamage).toDouble() /
                            officialDamage
                    damageRelativeErrors.getValue(side) += relativeError
                    relativeErrorSamples += RelativeErrorSample(
                        report = report,
                        metric = "$side damage",
                        official = officialDamage,
                        simulatedMedian = simulatedMedian,
                        relativeError = relativeError,
                    )
                }

                val officialRecovery = official.recoveryBySide.getValue(side)
                val recoveryValues = simulated.map { it.recoveryBySide.getValue(side) }
                val recoveryInterval = recoveryValues.min()..recoveryValues.max()
                if (officialRecovery !in recoveryInterval) {
                    failures += "$report $side recovery=$officialRecovery/$recoveryInterval"
                }
                if (officialRecovery > 0) {
                    val simulatedMedian = recoveryValues.median()
                    val relativeError =
                        kotlin.math.abs(simulatedMedian - officialRecovery).toDouble() /
                            officialRecovery
                    recoveryRelativeErrors += relativeError
                    relativeErrorSamples += RelativeErrorSample(
                        report = report,
                        metric = "$side recovery",
                        official = officialRecovery,
                        simulatedMedian = simulatedMedian,
                        relativeError = relativeError,
                    )
                }
            }
            official.finalTroopsByPosition.forEach { (position, officialTroops) ->
                finalTroopComparisonCount += 1
                val values = simulated.mapNotNull {
                    it.finalTroopsByPosition[position]
                }
                val interval = values.min()..values.max()
                if (officialTroops in interval) {
                    finalTroopCoverageCount += 1
                } else {
                    failures += "$report position=$position finalTroops=$officialTroops/$interval"
                }
                val initialTroops = initialTroopsByPosition.getValue(position)
                val officialLoss = (initialTroops - officialTroops).coerceAtLeast(0)
                if (officialLoss > 0) {
                    val simulatedLosses = values.map {
                        (initialTroops - it).coerceAtLeast(0)
                    }
                    val simulatedMedian = simulatedLosses.median()
                    val relativeError =
                        kotlin.math.abs(simulatedMedian - officialLoss).toDouble() /
                            officialLoss
                    troopLossRelativeErrors += relativeError
                    relativeErrorSamples += RelativeErrorSample(
                        report = report,
                        metric = "position $position troop loss",
                        official = officialLoss,
                        simulatedMedian = simulatedMedian,
                        relativeError = relativeError,
                    )
                }
            }
        }

        val attackerDamageError =
            damageRelativeErrors.getValue(Side.ATTACKER).average()
        val defenderDamageError =
            damageRelativeErrors.getValue(Side.DEFENDER).average()
        val meanDamageRelativeError =
            listOf(attackerDamageError, defenderDamageError).average()
        val recoveryRelativeError = recoveryRelativeErrors.averageOrZero()
        val troopLossRelativeError = troopLossRelativeErrors.averageOrZero()
        assertTrue(
            failures.isEmpty() &&
                meanDamageRelativeError <= 0.35 &&
                troopLossRelativeError <= 0.20,
            buildString {
                appendLine("outcome_coverage=$outcomeCoverageCount/$reportCount")
                appendLine("round_coverage=$roundCoverageCount/$reportCount")
                appendLine("action_round_mismatch_count=$actionRoundMismatchCount")
                appendLine("skill_trigger_mismatch_count=$skillTriggerMismatchCount")
                appendLine(
                    "final_troop_coverage=" +
                        "$finalTroopCoverageCount/$finalTroopComparisonCount",
                )
                appendLine(
                    "attacker_damage_median_relative_error=$attackerDamageError",
                )
                appendLine(
                    "defender_damage_median_relative_error=$defenderDamageError",
                )
                appendLine(
                    "recovery_median_relative_error=$recoveryRelativeError",
                )
                appendLine("meanDamageRelativeError=$meanDamageRelativeError")
                appendLine(
                    "troop_loss_median_relative_error=$troopLossRelativeError",
                )
                appendLine("highest_median_relative_errors:")
                relativeErrorSamples.sortedByDescending(RelativeErrorSample::relativeError)
                    .take(12)
                    .forEach { sample ->
                        appendLine(
                            "${sample.report} ${sample.metric} " +
                                "official=${sample.official} " +
                                "simulatedMedian=${sample.simulatedMedian} " +
                                "relativeError=${sample.relativeError}",
                        )
                    }
                failures.forEach(::appendLine)
            },
        )
    }

    @Test
    fun `first round defender victory can reproduce the attacker base defeat`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260311223648438_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val attackerBase = request.attacker.heroes.single { hero ->
            ClientBattleTextReplayProtocol.position(Side.ATTACKER, hero.position) == 1
        }
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val simulatedBaseTroops = simulations.map { (_, result) ->
            result.troopsAfterFirstRound(1, attackerBase.troops)
        }
        val closest = simulations.minBy { (_, result) ->
            result.troopsAfterFirstRound(1, attackerBase.troops)
        }
        val source = request.defender.heroes.single { hero ->
            ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 6
        }
        val seedSummaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val pursuits = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 6 &&
                        it.skillId == 200722
                }
            val sanjun = result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 6 &&
                        it.skillId == 211987
                }
                .joinToString(separator = ",") {
                    "${it.effectId}->${ClientBattleTextReplayProtocol.position(it.target)}"
                }
            val actionOrder = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .filter { it.round == 1 }
                .map { ClientBattleTextReplayProtocol.position(it.source) }
            val normals = result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .filter {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 6
                }
                .map { ClientBattleTextReplayProtocol.position(it.target) }
            "seed=$seed base=${result.troopsAfterFirstRound(1, attackerBase.troops)} " +
                "actions=$actionOrder normals=$normals pursuits=$pursuits sanjun=[$sanjun]"
        }

        assertTrue(
            official.finalTroopsByPosition.getValue(1) in
                simulatedBaseTroops.min()..simulatedBaseTroops.max(),
            "officialBase=${official.finalTroopsByPosition.getValue(1)} " +
                "simulated=${simulatedBaseTroops.min()}..${simulatedBaseTroops.max()} " +
                "closestSeed=${closest.first} source6Initial=${source.stats}\n" +
                closest.second.firstRoundDamageTrace() + "\n" + seedSummaries,
        )
    }

    @Test
    fun `official first round target stream reproduces the attacker base defeat`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260311223648438_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val attackerBase = request.attacker.heroes.single { hero ->
            ClientBattleTextReplayProtocol.position(Side.ATTACKER, hero.position) == 1
        }

        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(
                request,
                config,
                SeededBattleRandom(seed),
                OfficialReportFixture.targetDecisions(actions),
            )
        }
        val simulatedBaseTroops = simulations.map { (_, result) ->
            result.troopsAfterFirstRound(1, attackerBase.troops)
        }
        val closest = simulations.minBy { (_, result) ->
            result.troopsAfterFirstRound(1, attackerBase.troops)
        }
        val seedSummaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val pursuits = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 6 &&
                        it.skillId == 200722
                }
            val sanjun = result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 6 &&
                        it.skillId == 211987
                }
                .joinToString(separator = ",") {
                    "${it.effectId}->${ClientBattleTextReplayProtocol.position(it.target)}"
                }
            val actionOrder = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .filter { it.round == 1 }
                .map { ClientBattleTextReplayProtocol.position(it.source) }
            val normals = result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .filter {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 6
                }
                .map { ClientBattleTextReplayProtocol.position(it.target) }
            "seed=$seed base=${result.troopsAfterFirstRound(1, attackerBase.troops)} " +
                "actions=$actionOrder normals=$normals pursuits=$pursuits sanjun=[$sanjun]"
        }

        assertTrue(
            official.finalTroopsByPosition.getValue(1) in
                simulatedBaseTroops.min()..simulatedBaseTroops.max(),
            "officialBase=${official.finalTroopsByPosition.getValue(1)} " +
                "simulated=${simulatedBaseTroops.min()}..${simulatedBaseTroops.max()} " +
                "closestSeed=${closest.first}\n" +
                closest.second.firstRoundDamageTrace() +
                "\nactions=" + closest.second.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .filter { it.round == 1 }
                .map { ClientBattleTextReplayProtocol.position(it.source) } +
                "\nsource6Statuses=" + closest.second.events.filterIsInstance<BattleEvent.StatusApplied>()
                .filter { it.round <= 1 && ClientBattleTextReplayProtocol.position(it.target) == 6 }
                .map { "${it.round}/${it.skillId}/${it.effectId}/${it.status}" } +
                "\n$seedSummaries",
        )
    }

    @Test
    fun `paper first action effects keep defender source six first across round start`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260311223648438_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val request = OfficialReportFixture.reconstructBattleRequest(
            OfficialReportFixture.read(report),
            config,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val initialFirst = engine.livingHeroesInSpeedOrder().first()
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = SeededBattleRandom(0),
            round = 0,
            source = initialFirst,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        engine.prepareBattle(baseContext)

        val sourceSix = engine.state.view.heroes().single {
            ClientBattleTextReplayProtocol.position(it) == 6
        }
        fun diagnostic(): String =
            "effects=${engine.state.effectStore.effectsFor(sourceSix).map {
                "${it.skillId}/${it.detailId}/${it.effectId}/${it.remainingRounds}"
            }} order=${engine.livingHeroesInSpeedOrder().map {
                ClientBattleTextReplayProtocol.position(it)
            }}"
        assertEquals(
            6,
            ClientBattleTextReplayProtocol.position(engine.livingHeroesInSpeedOrder().first()),
            diagnostic(),
        )

        engine.livingHeroesInSpeedOrder().forEach { source ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                baseContext.copy(
                    round = 1,
                    source = source,
                    trigger = BattleTrigger.ROUND_START,
                ),
            )
        }

        assertEquals(
            6,
            ClientBattleTextReplayProtocol.position(engine.livingHeroesInSpeedOrder().first()),
            diagnostic(),
        )
    }

    @Test
    fun `huangyi paper reconstruction infers the hidden recovery source strategy`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311222842345_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val request = OfficialReportFixture.reconstructBattleRequest(
            OfficialReportFixture.read(report),
            config,
        )
        val source = request.defender.heroes.single { hero ->
            ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 6
        }

        assertEquals(
            287.0,
            source.stats.precise(BattleStat.STRATEGY),
            0.001,
            "official full recoveries 431/531 normalize to potency 403 after +7% dealt " +
                "and +25% taken recovery modifiers",
        )
    }

    @Test
    fun `paper reconstruction preserves each side morale`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312002259656_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val request = OfficialReportFixture.reconstructBattleRequest(
            OfficialReportFixture.read(report),
            config,
        )

        assertEquals(setOf(110), request.attacker.heroes.mapTo(linkedSetOf()) { it.morale })
        assertEquals(setOf(110), request.defender.heroes.mapTo(linkedSetOf()) { it.morale })
    }

    @Test
    fun `official summary attributes special ongoing damage to its encoded source`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312162500131_00001857_zlib.json",
        )
        val summary = OfficialReportFixture.fullBattleSummary(
            OfficialReportFixture.read(report),
        )

        assertEquals(4_087, summary.damageBySide.getValue(Side.DEFENDER))
    }

    @Test
    fun `fenji paper attacker middle net loss stays inside its damage recovery envelope`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312162500131_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val finalTroops = simulations.map { (_, result) ->
            OfficialReportFixture.fullBattleSummary(result)
                .finalTroopsByPosition
                .getValue(2)
        }
        val diagnostics = simulations.joinToString(separator = "\n") { (seed, result) ->
            val damage = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2
                    }?.let { "normal:${it.damage}" }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2
                    }?.let { "${it.skillId}:${it.damage}" }
                    is BattleEvent.OngoingDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2
                    }?.let { "${it.skillId}:ongoing:${it.damage}" }
                    else -> null
                }
            }
            val recovery = result.events.filterIsInstance<BattleEvent.Recovery>()
                .filter { ClientBattleTextReplayProtocol.position(it.target) == 2 }
                .map { "${it.skillId}:${it.amount}" }
            val final = OfficialReportFixture.fullBattleSummary(result)
                .finalTroopsByPosition
                .getValue(2)
            "seed=$seed final=$final damage=$damage recovery=$recovery"
        }

        assertTrue(
            official.finalTroopsByPosition.getValue(2) in
                finalTroops.min()..finalTroops.max(),
            "official=${official.finalTroopsByPosition.getValue(2)} " +
                "simulated=${finalTroops.min()}..${finalTroops.max()}\n$diagnostics",
        )
    }

    @Test
    fun `official summary includes immediate and ongoing recovery actions`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260313025235816_00001857_zlib.json",
        )
        val summary = OfficialReportFixture.fullBattleSummary(
            OfficialReportFixture.read(report),
        )

        assertEquals(2_029, summary.recoveryBySide.getValue(Side.DEFENDER))
    }

    @Test
    fun `sishuiguan paper can defeat defender middle before its third action`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260313025235816_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val request = OfficialReportFixture.reconstructBattleRequest(
            OfficialReportFixture.read(report),
            config,
        )
        val targetHero = request.defender.heroes.single { hero ->
            ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 5
        }
        val target = BattleHeroRef(Side.DEFENDER, targetHero.position, targetHero.id)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val matchingSeeds = simulations.filter { (_, result) ->
            result.events.filterIsInstance<BattleEvent.HeroActionStart>().none {
                it.source == target && it.round == 3
            }
        }
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val actions = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .filter { it.source == target }
                .map(BattleEvent.HeroActionStart::round)
            val damage = result.events.mapNotNull { event ->
                when {
                    event is BattleEvent.NormalAttack && event.target == target ->
                        "${event.round}:normal:${event.source.position}:" +
                            "${event.damage}:${event.targetTroopsAfter}"
                    event is BattleEvent.SkillDamage && event.target == target ->
                        "${event.round}:skill:${event.skillId}:${event.effectId}:" +
                            "${event.damage}:${event.targetTroopsAfter}"
                    event is BattleEvent.OngoingDamage && event.target == target ->
                        "${event.round}:ongoing:${event.skillId}:${event.status}:" +
                            "${event.damage}:${event.targetTroopsAfter}"
                    else -> null
                }
            }
            val triggers = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.skillId == 200814 }
                .map(BattleEvent.SkillTriggered::round)
            val finalTroops = result.defender.heroes.single {
                it.id == target.heroId && it.position == target.position
            }.troops
            "seed=$seed actions=$actions triggers=$triggers damage=$damage final=$finalTroops"
        }

        assertTrue(
            matchingSeeds.isNotEmpty(),
            "target=$target initial=${targetHero.troops}\n$summaries",
        )
    }

    @Test
    fun `huangyi paper recovery stays inside deterministic simulation envelope`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311222842345_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val source = request.defender.heroes.single { hero ->
            ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 6
        }
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val recoveryTotals = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.Recovery>()
                .filter { it.source.side == Side.DEFENDER && it.skillId == 200016 }
                .sumOf(BattleEvent.Recovery::amount)
        }
        val seedSummaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val recoveries = result.events.filterIsInstance<BattleEvent.Recovery>()
                .filter { it.source.side == Side.DEFENDER && it.skillId == 200016 }
            val hurtEvents = result.events.count { event ->
                when (event) {
                    is BattleEvent.NormalAttack ->
                        event.target.side == Side.DEFENDER && event.damage > 0
                    is BattleEvent.SkillDamage ->
                        event.target.side == Side.DEFENDER && event.damage > 0
                    is BattleEvent.OngoingDamage ->
                        event.target.side == Side.DEFENDER && event.damage > 0
                    else -> false
                }
            }
            val increments = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source.side == Side.DEFENDER && it.skillId == 211016 }
            val byRound = recoveries.groupBy(BattleEvent.Recovery::round)
                .mapValues { (_, events) -> events.sumOf(BattleEvent.Recovery::amount) }
            "seed=$seed hurtEvents=$hurtEvents recoveries=${recoveries.size} " +
                "total=${recoveries.sumOf(BattleEvent.Recovery::amount)} " +
                "increments=$increments byRound=$byRound"
        }

        assertTrue(
            official.recoveryBySide.getValue(Side.DEFENDER) in
                recoveryTotals.min()..recoveryTotals.max(),
            "official=${official.recoveryBySide.getValue(Side.DEFENDER)} " +
                "simulated=${recoveryTotals.min()}..${recoveryTotals.max()} " +
                "sourceInitial=${source.stats}\n" +
                seedSummaries,
        )
    }

    @Test
    fun `morale huangyi paper recovery stays inside deterministic simulation envelope`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311224730035_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val source = request.attacker.heroes.single { 200016 in it.skillIds }
        val officialRecovery = actions
            .filter { it.id == ClientBattleTextReplayProtocol.RECOVERY }
            .filter {
                it.params[0].toInt() in 1..3 &&
                    it.params[1].toInt() == 200016
            }
            .sumOf { it.params[3].toInt() }
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val simulatedRecoveries = simulations.associate { (seed, result) ->
            seed to result.events.filterIsInstance<BattleEvent.Recovery>()
                .filter { it.source.side == Side.ATTACKER && it.skillId == 200016 }
        }
        val totals = simulatedRecoveries.values.map { recoveries ->
            recoveries.sumOf(BattleEvent.Recovery::amount)
        }
        val diagnostics = simulations.joinToString(separator = "\n") { (seed, result) ->
            val recoveries = simulatedRecoveries.getValue(seed)
            val increments = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source.side == Side.ATTACKER && it.skillId == 211016 }
            "seed=$seed count=${recoveries.size} " +
                "total=${recoveries.sumOf(BattleEvent.Recovery::amount)} " +
                "amounts=${recoveries.map(BattleEvent.Recovery::amount)} " +
                "increments=$increments"
        }

        assertTrue(
            officialRecovery in totals.min()..totals.max(),
            "official=$officialRecovery simulated=${totals.min()}..${totals.max()} " +
                "sourceStats=${source.stats} sourceSkills=${source.skillIds} " +
                "sourceLevels=${source.skillLevels} sourceModifiers=${source.modifiers} " +
                "targetModifiers=${request.attacker.heroes.associate {
                    it.position to it.modifiers
                }}\n" +
                diagnostics,
        )
    }

    @Test
    fun `jiangxin paper defender damage stays inside deterministic simulation envelope`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311222842345_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val defenderDamage = simulations.map { (_, result) ->
            OfficialReportFixture.fullBattleSummary(result)
                .damageBySide.getValue(Side.DEFENDER)
        }
        val highest = simulations.maxBy { (_, result) ->
            OfficialReportFixture.fullBattleSummary(result)
                .damageBySide.getValue(Side.DEFENDER)
        }
        val breakdown = highest.second.events.mapNotNull { event ->
            when (event) {
                is BattleEvent.NormalAttack -> event.takeIf {
                    it.source.side == Side.DEFENDER
                }?.let {
                    "normal:p${ClientBattleTextReplayProtocol.position(it.source)}" to it.damage
                }
                is BattleEvent.SkillDamage -> event.takeIf {
                    it.source.side == Side.DEFENDER
                }?.let {
                    "${it.skillId}/${it.effectId}:p" +
                        ClientBattleTextReplayProtocol.position(it.source) to it.damage
                }
                is BattleEvent.OngoingDamage -> event.takeIf {
                    it.source.side == Side.DEFENDER
                }?.let {
                    "${it.skillId}/${it.status}:p" +
                        ClientBattleTextReplayProtocol.position(it.source) to it.damage
                }
                else -> null
            }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.size to values.sum() }

        assertTrue(
            official.damageBySide.getValue(Side.DEFENDER) in
                defenderDamage.min()..defenderDamage.max(),
            "official=${official.damageBySide.getValue(Side.DEFENDER)} " +
                "simulated=${defenderDamage.min()}..${defenderDamage.max()} " +
                "highestSeed=${highest.first} breakdown=$breakdown",
        )
    }

    @Test
    fun `baizhan and huangyi paper recovery stays inside deterministic simulation envelope`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311223905520_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val recoverySkillIds = setOf(200016, 200267, 210784, 214252)
        val officialBySourceAndSkill = actions
            .filter { it.id == ClientBattleTextReplayProtocol.RECOVERY }
            .filter { it.params[1].toInt() in recoverySkillIds }
            .groupBy { it.params[0].toInt() to it.params[1].toInt() }
            .mapValues { (_, recoveries) ->
                Triple(
                    recoveries.size,
                    recoveries.count { it.params[3].toInt() > 0 },
                    recoveries.sumOf { it.params[3].toInt() },
                )
            }
        val officialDamageActionsByTarget = actions.mapNotNull { action ->
            when (action.id) {
                ClientBattleTextReplayProtocol.NORMAL_DAMAGE ->
                    action.params[0].toInt().takeIf { action.params[1].toInt() > 0 }
                ClientBattleTextReplayProtocol.ATTACK_SKILL_DAMAGE,
                ClientBattleTextReplayProtocol.SKILL_DAMAGE,
                -> action.params[2].toInt().takeIf { action.params[3].toInt() > 0 }
                ClientBattleTextReplayProtocol.PANIC_ONGOING_DAMAGE,
                ClientBattleTextReplayProtocol.ONGOING_DAMAGE,
                ClientBattleTextReplayProtocol.HEX_ONGOING_DAMAGE,
                ->
                    action.params[0].toInt().takeIf { action.params[3].toInt() > 0 }
                else -> null
            }
        }.groupingBy { it }.eachCount()
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val simulatedBySeed = simulations.associate { (seed, result) ->
            val bySourceAndSkill = result.events
                .filterIsInstance<BattleEvent.Recovery>()
                .filter { it.skillId in recoverySkillIds }
                .groupBy {
                    ClientBattleTextReplayProtocol.position(it.source) to it.skillId
                }
                .mapValues { (_, recoveries) ->
                    recoveries.size to recoveries.sumOf(BattleEvent.Recovery::amount)
                }
            seed to bySourceAndSkill
        }
        val simulatedDamageActionsBySeed = simulations.associate { (seed, result) ->
            val byTarget = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack ->
                        event.target.takeIf { event.damage > 0 }
                    is BattleEvent.SkillDamage ->
                        event.target.takeIf { event.damage > 0 }
                    is BattleEvent.OngoingDamage ->
                        event.target.takeIf { event.damage > 0 }
                    else -> null
                }
            }.groupingBy {
                ClientBattleTextReplayProtocol.position(it)
            }.eachCount()
            seed to byTarget
        }
        val diagnostics = officialBySourceAndSkill.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .joinToString(separator = "\n") { (key, official) ->
                val counts = simulatedBySeed.values.map { it[key]?.first ?: 0 }
                val totals = simulatedBySeed.values.map { it[key]?.second ?: 0 }
                "source=${key.first} skill=${key.second} " +
                    "officialAttempts=${official.first} " +
                    "officialPositive=${official.second} officialTotal=${official.third} " +
                    "simulatedPositive=${counts.min()}..${counts.max()} " +
                    "simulatedTotal=${totals.min()}..${totals.max()}"
            } + "\n" + officialDamageActionsByTarget.entries
            .sortedBy(Map.Entry<Int, Int>::key)
            .joinToString(separator = "\n") { (target, officialCount) ->
                val simulatedCounts = simulatedDamageActionsBySeed.values
                    .map { it[target] ?: 0 }
                "target=$target officialPositiveDamageActions=$officialCount " +
                    "simulatedPositiveDamageActions=${simulatedCounts.min()}.." +
                    simulatedCounts.max()
            }

        assertTrue(
            officialBySourceAndSkill.all { (key, official) ->
                val totals = simulatedBySeed.values.map { it[key]?.second ?: 0 }
                official.third in totals.min()..totals.max()
            },
            diagnostics,
        )
    }

    @Test
    fun `dual huangyi paper defender offense and attacker recovery stay inside simulation envelope`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260312162454986_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val summaries = simulations.associate { (seed, result) ->
            seed to OfficialReportFixture.fullBattleSummary(result)
        }
        val defenderDamage = summaries.values.map { it.damageBySide.getValue(Side.DEFENDER) }
        val attackerRecovery = summaries.values.map { it.recoveryBySide.getValue(Side.ATTACKER) }
        val defenderMiddleTroops = summaries.values.map { it.finalTroopsByPosition.getValue(5) }
        val closest = simulations.minBy { (seed, _) ->
            val summary = summaries.getValue(seed)
            kotlin.math.abs(
                summary.damageBySide.getValue(Side.DEFENDER) -
                    official.damageBySide.getValue(Side.DEFENDER),
            ) + kotlin.math.abs(
                summary.recoveryBySide.getValue(Side.ATTACKER) -
                    official.recoveryBySide.getValue(Side.ATTACKER),
            )
        }
        fun BattleResult.damageBreakdown(side: Side): Map<String, Int> =
            events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf { it.source.side == side }?.let {
                        "normal:p${ClientBattleTextReplayProtocol.position(it.source)}" to it.damage
                    }
                    is BattleEvent.SkillDamage -> event.takeIf { it.source.side == side }?.let {
                        "${it.skillId}/${it.effectId}:p" +
                            ClientBattleTextReplayProtocol.position(it.source) to it.damage
                    }
                    is BattleEvent.OngoingDamage -> event.takeIf { it.source.side == side }?.let {
                        "${it.skillId}/${it.status}:p" +
                            ClientBattleTextReplayProtocol.position(it.source) to it.damage
                    }
                    else -> null
                }
            }.groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.sum() }
        fun BattleResult.recoveryBreakdown(side: Side): Map<String, Int> =
            events.filterIsInstance<BattleEvent.Recovery>()
                .filter { it.source.side == side }
                .groupBy {
                    "${it.skillId}:p${ClientBattleTextReplayProtocol.position(it.source)}"
                }
                .mapValues { (_, events) -> events.sumOf(BattleEvent.Recovery::amount) }
        fun BattleResult.damageTo(position: Int): Map<String, Int> =
            events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == position
                    }?.let {
                        "normal:p${ClientBattleTextReplayProtocol.position(it.source)}" to it.damage
                    }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == position
                    }?.let {
                        "${it.skillId}/${it.effectId}:p" +
                            ClientBattleTextReplayProtocol.position(it.source) to it.damage
                    }
                    is BattleEvent.OngoingDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == position
                    }?.let {
                        "${it.skillId}/${it.status}:p" +
                            ClientBattleTextReplayProtocol.position(it.source) to it.damage
                    }
                    else -> null
                }
            }.groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.sum() }
        fun BattleResult.recoveryTo(position: Int): Map<String, Pair<Int, Int>> =
            events.filterIsInstance<BattleEvent.Recovery>()
                .filter { ClientBattleTextReplayProtocol.position(it.target) == position }
                .groupBy {
                    "${it.skillId}:p${ClientBattleTextReplayProtocol.position(it.source)}"
                }
                .mapValues { (_, events) ->
                    events.size to events.sumOf(BattleEvent.Recovery::amount)
                }
        val closestSummary = summaries.getValue(closest.first)
        val bestDefenderMiddle = simulations.maxBy { (seed, _) ->
            summaries.getValue(seed).finalTroopsByPosition.getValue(5)
        }
        val bestDefenderMiddleSummary = summaries.getValue(bestDefenderMiddle.first)

        assertTrue(
            official.damageBySide.getValue(Side.DEFENDER) in
                defenderDamage.min()..defenderDamage.max() &&
                official.recoveryBySide.getValue(Side.ATTACKER) in
                attackerRecovery.min()..attackerRecovery.max(),
            "official defenderDamage=${official.damageBySide.getValue(Side.DEFENDER)} " +
                "attackerRecovery=${official.recoveryBySide.getValue(Side.ATTACKER)} " +
                "p5=${official.finalTroopsByPosition.getValue(5)}\n" +
                "simulated defenderDamage=${defenderDamage.min()}..${defenderDamage.max()} " +
                "attackerRecovery=${attackerRecovery.min()}..${attackerRecovery.max()} " +
                "p5=${defenderMiddleTroops.min()}..${defenderMiddleTroops.max()}\n" +
                "closestSeed=${closest.first} defenderDamage=" +
                "${closestSummary.damageBySide.getValue(Side.DEFENDER)} " +
                "attackerRecovery=${closestSummary.recoveryBySide.getValue(Side.ATTACKER)} " +
                "p5=${closestSummary.finalTroopsByPosition.getValue(5)}\n" +
                "damage=${closest.second.damageBreakdown(Side.DEFENDER)}\n" +
                "recovery=${closest.second.recoveryBreakdown(Side.ATTACKER)}\n" +
                "bestP5Seed=${bestDefenderMiddle.first} " +
                "p5=${bestDefenderMiddleSummary.finalTroopsByPosition.getValue(5)} " +
                "incoming=${bestDefenderMiddle.second.damageTo(5)} " +
                "recovery=${bestDefenderMiddle.second.recoveryTo(5)}",
        )
    }

    @Test
    fun `baizhan paper attacker center troop loss stays inside deterministic simulation envelope`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311223905520_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val initialTroops = request.attacker.heroes.single { hero ->
            ClientBattleTextReplayProtocol.position(Side.ATTACKER, hero.position) == 2
        }.troops
        val officialLoss =
            initialTroops - official.finalTroopsByPosition.getValue(2)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val simulatedLosses = simulations.map { (_, result) ->
            val summary = OfficialReportFixture.fullBattleSummary(result)
            initialTroops - summary.finalTroopsByPosition.getValue(2)
        }
        val diagnostics = simulations.joinToString(separator = "\n") { (seed, result) ->
            val damage = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2
                    }?.let {
                        "${it.round}:normal:p" +
                            "${ClientBattleTextReplayProtocol.position(it.source)}:${it.damage}"
                    }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2
                    }?.let {
                        "${it.round}:skill:${it.skillId}/${it.effectId}:p" +
                            "${ClientBattleTextReplayProtocol.position(it.source)}:${it.damage}"
                    }
                    is BattleEvent.OngoingDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2
                    }?.let {
                        "${it.round}:ongoing:${it.skillId}/${it.status}:p" +
                            "${ClientBattleTextReplayProtocol.position(it.source)}:${it.damage}"
                    }
                    else -> null
                }
            }
            val recovery = result.events.filterIsInstance<BattleEvent.Recovery>()
                .filter { ClientBattleTextReplayProtocol.position(it.target) == 2 }
                .map {
                    "${it.round}:${it.skillId}:p" +
                        "${ClientBattleTextReplayProtocol.position(it.source)}:${it.amount}"
                }
            val sourceNormals = result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .filter { ClientBattleTextReplayProtocol.position(it.source) == 2 }
                .map {
                    "${it.round}:p${ClientBattleTextReplayProtocol.position(it.target)}:" +
                        it.damage
                }
            val sourceStatuses = result.events.filterIsInstance<BattleEvent.StatusApplied>()
                .filter { ClientBattleTextReplayProtocol.position(it.target) == 2 }
                .map {
                    "${it.round}:${it.status}:p" +
                        "${ClientBattleTextReplayProtocol.position(it.source)}:" +
                        "${it.skillId}/${it.effectId}"
                }
            val summary = OfficialReportFixture.fullBattleSummary(result)
            "seed=$seed final=${summary.finalTroopsByPosition.getValue(2)} " +
                "loss=${initialTroops - summary.finalTroopsByPosition.getValue(2)} " +
                "damage=$damage recovery=$recovery sourceNormals=$sourceNormals " +
                "sourceStatuses=$sourceStatuses"
        }

        assertTrue(
            officialLoss in simulatedLosses.min()..simulatedLosses.max(),
            "officialLoss=$officialLoss " +
                "simulated=${simulatedLosses.min()}..${simulatedLosses.max()}\n" +
                diagnostics,
        )
    }

    @Test
    fun `fenji paper repeatedly reaches the attacker front`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311223905520_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val officialFenjiHits = actions.filter { action ->
            action.id == ClientBattleTextReplayProtocol.ATTACK_SKILL_DAMAGE &&
                action.params[0].toInt() == 4 &&
                action.params[1].toInt() == 211961 &&
                action.params[2].toInt() == 3
        }
        assertEquals(7, officialFenjiHits.size)
        assertEquals(9_581, officialFenjiHits.sumOf { it.params[3].toInt() })

        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val hitCounts = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .count {
                    ClientBattleTextReplayProtocol.position(it.source) == 4 &&
                        it.skillId == 211961 &&
                        ClientBattleTextReplayProtocol.position(it.target) == 3
                }
        }
        val damageTotals = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 4 &&
                        it.skillId == 211961 &&
                        ClientBattleTextReplayProtocol.position(it.target) == 3
                }
                .sumOf(BattleEvent.SkillDamage::damage)
        }
        val finalTroops = simulations.map { (_, result) ->
            OfficialReportFixture.fullBattleSummary(result)
                .finalTroopsByPosition
                .getValue(3)
        }
        val diagnostics = simulations.joinToString(separator = "\n") { (seed, result) ->
            val hits = result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 4 &&
                        it.skillId == 211961
                }
                .map {
                    "r${it.round}:p${ClientBattleTextReplayProtocol.position(it.target)}=" +
                        "${it.damage}"
                }
            val summary = OfficialReportFixture.fullBattleSummary(result)
            "seed=$seed p3=${summary.finalTroopsByPosition.getValue(3)} fenji=$hits"
        }

        assertTrue(
            officialFenjiHits.size in hitCounts.min()..hitCounts.max() &&
                officialFenjiHits.sumOf { it.params[3].toInt() } in
                damageTotals.min()..damageTotals.max() &&
                official.finalTroopsByPosition.getValue(3) in
                finalTroops.min()..finalTroops.max(),
            "official hits=${officialFenjiHits.size} " +
                "damage=${officialFenjiHits.sumOf { it.params[3].toInt() }} " +
                "p3=${official.finalTroopsByPosition.getValue(3)}\n" +
                "simulated hits=${hitCounts.min()}..${hitCounts.max()} " +
                "damage=${damageTotals.min()}..${damageTotals.max()} " +
                "p3=${finalTroops.min()}..${finalTroops.max()}\n$diagnostics",
        )
    }

    @Test
    fun `liangyuan source six recovery reproduces official defender total`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311223101614_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val recoveryTotals = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.Recovery>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 6 &&
                        it.skillId == 297322
                }
                .sumOf(BattleEvent.Recovery::amount)
        }
        val diagnostics = simulations.joinToString(separator = "\n") { (seed, result) ->
            val recoveries = result.events.filterIsInstance<BattleEvent.Recovery>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 6 &&
                        it.skillId == 297322
                }
            val recoveryByRound = recoveries.groupBy(BattleEvent.Recovery::round)
                .mapValues { (_, events) -> events.sumOf(BattleEvent.Recovery::amount) }
                .toSortedMap()
            val targetSixTrace = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let {
                        "r${it.round}:NormalAttack source=" +
                            "${ClientBattleTextReplayProtocol.position(it.source)} " +
                            "damage=${it.damage} targetTroopsAfter=${it.targetTroopsAfter}"
                    }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let {
                        "r${it.round}:SkillDamage source=" +
                            "${ClientBattleTextReplayProtocol.position(it.source)} " +
                            "skillId=${it.skillId} effectId=${it.effectId} " +
                            "damage=${it.damage} targetTroopsAfter=${it.targetTroopsAfter}"
                    }
                    is BattleEvent.OngoingDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let {
                        "r${it.round}:OngoingDamage source=" +
                            "${ClientBattleTextReplayProtocol.position(it.source)} " +
                            "skillId=${it.skillId} status=${it.status} " +
                            "damage=${it.damage} targetTroopsAfter=${it.targetTroopsAfter}"
                    }
                    is BattleEvent.Recovery -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let {
                        "r${it.round}:Recovery source=" +
                            "${ClientBattleTextReplayProtocol.position(it.source)} " +
                            "skillId=${it.skillId} amount=${it.amount} " +
                            "targetTroopsAfter=${it.targetTroopsAfter}"
                    }
                    else -> null
                }
            }
            "seed=$seed skill297322Total=" +
                "${recoveries.sumOf(BattleEvent.Recovery::amount)} " +
                "skill297322ByRound=$recoveryByRound trace=$targetSixTrace"
        }
        val officialLiangyuanRecovery = actions
            .filter {
                it.id == ClientBattleTextReplayProtocol.RECOVERY &&
                    it.params.getOrNull(1) == "297322"
            }
            .sumOf { it.params[3].toInt() }

        assertEquals(227, officialLiangyuanRecovery)
        assertTrue(
            officialLiangyuanRecovery in recoveryTotals.min()..recoveryTotals.max(),
            "297322 recovery=$officialLiangyuanRecovery/" +
                "${recoveryTotals.min()}..${recoveryTotals.max()}\n$diagnostics",
        )
    }

    @Test
    fun `xinzhan attack recovery contributes to official defender total`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311223101614_0000000b_zlib.json",
        )

        val official = OfficialReportFixture.fullBattleSummary(
            OfficialReportFixture.read(report),
        )

        assertEquals(7_941, official.recoveryBySide.getValue(Side.DEFENDER))
    }

    @Test
    fun `jiufazhongyuan opponent attacker damage reaches official total`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/11/cap_20260311223101614_0000000b_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val attackerDamage = simulations.map { (_, result) ->
            OfficialReportFixture.fullBattleSummary(result)
                .damageBySide.getValue(Side.ATTACKER)
        }
        val diagnostics = simulations.joinToString(separator = "\n") { (seed, result) ->
            val bySourceSkillAndRound = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        it.source.side == Side.ATTACKER
                    }?.let {
                        Triple(
                            "p${ClientBattleTextReplayProtocol.position(it.source)}/normal",
                            it.round,
                            it.damage,
                        )
                    }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        it.source.side == Side.ATTACKER
                    }?.let {
                        Triple(
                            "p${ClientBattleTextReplayProtocol.position(it.source)}/" +
                                "${it.skillId}/${it.effectId}",
                            it.round,
                            it.damage,
                        )
                    }
                    is BattleEvent.OngoingDamage -> event.takeIf {
                        it.source.side == Side.ATTACKER
                    }?.let {
                        Triple(
                            "p${ClientBattleTextReplayProtocol.position(it.source)}/" +
                                "${it.skillId}/${it.status}",
                            it.round,
                            it.damage,
                        )
                    }
                    else -> null
                }
            }.groupBy { (sourceAndSkill, round) -> sourceAndSkill to round }
                .mapValues { (_, events) -> events.sumOf { it.third } }
                .toSortedMap(compareBy({ it.first }, { it.second }))
            "seed=$seed attackerDamage=" +
                OfficialReportFixture.fullBattleSummary(result)
                    .damageBySide.getValue(Side.ATTACKER) +
                " breakdown=$bySourceSkillAndRound"
        }
        val officialAttackerDamage = official.damageBySide.getValue(Side.ATTACKER)

        assertTrue(
            officialAttackerDamage in attackerDamage.min()..attackerDamage.max(),
            "ATTACKER damage=$officialAttackerDamage/" +
                "${attackerDamage.min()}..${attackerDamage.max()}\n$diagnostics",
        )
    }

    @Test
    fun `xuanwei paper can defeat attacker front before its first action`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312004747562_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val frontActionCounts = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.HeroActionStart>().count {
                it.round == 1 && ClientBattleTextReplayProtocol.position(it.source) == 3
            }
        }
        val closest = simulations.minBy { (_, result) ->
            result.troopsBeforeFirstAction(3, request.attacker.heroes.single {
                ClientBattleTextReplayProtocol.position(Side.ATTACKER, it.position) == 3
            }.troops)
        }

        assertTrue(
            0 in frontActionCounts.min()..frontActionCounts.max(),
            "actions=${frontActionCounts.min()}..${frontActionCounts.max()} " +
                "closestSeed=${closest.first}\n${closest.second.damageBeforeFirstAction(3)}",
        )
    }

    @Test
    fun `xuanwei can defeat attacker middle before its second round action`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260311224542066_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val trace = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2 &&
                            it.round <= 2
                    }?.let {
                        "r${it.round}:normal:p" +
                            "${ClientBattleTextReplayProtocol.position(it.source)}=" +
                            "${it.damage}/${it.targetTroopsAfter}"
                    }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2 &&
                            it.round <= 2
                    }?.let {
                        "r${it.round}:skill:${it.skillId}:p" +
                            "${ClientBattleTextReplayProtocol.position(it.source)}=" +
                            "${it.damage}/${it.targetTroopsAfter}"
                    }
                    is BattleEvent.OngoingDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2 &&
                            it.round <= 2
                    }?.let {
                        "r${it.round}:ongoing:${it.skillId}:p" +
                            "${ClientBattleTextReplayProtocol.position(it.source)}=" +
                            "${it.damage}/${it.targetTroopsAfter}"
                    }
                    is BattleEvent.Recovery -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 2 &&
                            it.round <= 2
                    }?.let {
                        "r${it.round}:recovery:${it.skillId}:p" +
                            "${ClientBattleTextReplayProtocol.position(it.source)}=" +
                            "${it.amount}/${it.targetTroopsAfter}"
                    }
                    is BattleEvent.HeroActionStart -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.source) == 2 &&
                            it.round <= 2
                    }?.let { "r${it.round}:action" }
                    else -> null
                }
            }
            "seed=$seed trace=$trace"
        }

        assertTrue(
            simulations.any { (_, result) ->
                result.events.filterIsInstance<BattleEvent.HeroActionStart>().none {
                    it.round == 2 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 2
                }
            },
            summaries,
        )
    }

    @Test
    fun `xuanwei paper fires after every surviving first three round normal attack`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312004747562_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val triggerCounts = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 200885 }
        }
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val actionRounds = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .filter { ClientBattleTextReplayProtocol.position(it.source) == 4 }
                .map(BattleEvent.HeroActionStart::round)
            val normals = result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .filter { ClientBattleTextReplayProtocol.position(it.source) == 4 }
                .groupingBy(BattleEvent.NormalAttack::round)
                .eachCount()
            val evades = result.events.filterIsInstance<BattleEvent.Evaded>()
                .filter { ClientBattleTextReplayProtocol.position(it.source) == 4 }
                .groupingBy(BattleEvent.Evaded::round)
                .eachCount()
            val triggers = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.skillId == 200885 }
                .groupingBy(BattleEvent.SkillTriggered::round)
                .eachCount()
            val statuses = result.events.filterIsInstance<BattleEvent.StatusApplied>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.target) == 4 &&
                        it.status in setOf(
                            BattleStatus.DISARM,
                            BattleStatus.CONFUSION,
                            BattleStatus.BERSERK,
                        )
                }
                .map { "${it.round}:${it.status}:${it.source.position}:${it.skillId}" }
            val removed = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.StatusRemoved -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 4
                    }?.let { "${it.round}:removed:${it.skillId}:${it.effectId}" }
                    is BattleEvent.EffectExpired -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 4
                    }?.let { "${it.round}:expired:${it.skillId}:${it.effectId}" }
                    else -> null
                }
            }
            val finalSource = (result.attacker.heroes + result.defender.heroes).single {
                ClientBattleTextReplayProtocol.position(
                    if (it in result.attacker.heroes) Side.ATTACKER else Side.DEFENDER,
                    it.position,
                ) == 4
            }
            "seed=$seed actions=$actionRounds normals=$normals evades=$evades " +
                "triggers=$triggers statuses=$statuses removed=$removed " +
                "finalStatuses=${finalSource.activeStatuses} " +
                "finalModifiers=${finalSource.modifiers}"
        }

        assertTrue(
            official.skillTriggers.getValue(200885) in
                triggerCounts.min()..triggerCounts.max(),
            "official=${official.skillTriggers.getValue(200885)} " +
                "simulated=${triggerCounts.min()}..${triggerCounts.max()}\n$summaries",
        )
    }

    @Test
    fun `lijun paper hit preserves its panzhen guarded damage range`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312004747562_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val officialDamage = actions
            .single { action ->
                action.id == ClientBattleTextReplayProtocol.ATTACK_SKILL_DAMAGE &&
                    action.params[0].toInt() == 1 &&
                    action.params[1].toInt() == 200821 &&
                    action.params[2].toInt() == 5
            }
            .params[3]
            .toInt()
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulatedDamage = (0 until 32).flatMap { seed ->
            BattleEngine.resolve(request, config, SeededBattleRandom(seed))
                .events
                .filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 1 &&
                        it.skillId == 200821 &&
                        ClientBattleTextReplayProtocol.position(it.target) == 5
                }
                .map(BattleEvent.SkillDamage::damage)
        }

        assertTrue(
            simulatedDamage.isNotEmpty() &&
                officialDamage in simulatedDamage.min()..simulatedDamage.max(),
            "official=$officialDamage simulated=" +
                simulatedDamage.sorted().joinToString(),
        )
    }

    @Test
    fun `xuanwei first round pursuit damage can reproduce the official front defeat`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260311230758305_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        var round = 0
        val officialDamage = actions.sumOf { action ->
            if (action.id == ClientBattleTextReplayProtocol.ROUND) {
                round = action.params[0].toInt()
            }
            action.params.getOrNull(3)?.toIntOrNull()?.takeIf {
                round == 1 &&
                    action.id == ClientBattleTextReplayProtocol.ATTACK_SKILL_DAMAGE &&
                    action.params[0].toInt() == 4 &&
                    action.params[1].toInt() == 200885 &&
                    action.params[2].toInt() == 3
            } ?: 0
        }
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val simulatedDamage = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 4 &&
                        it.skillId == 200885 &&
                        ClientBattleTextReplayProtocol.position(it.target) == 3
                }
                .sumOf(BattleEvent.SkillDamage::damage)
        }
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val pursuits = result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    it.round == 1 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 4 &&
                        it.skillId == 200885
                }
                .map {
                    "${ClientBattleTextReplayProtocol.position(it.target)}:${it.damage}"
                }
            "seed=$seed pursuits=$pursuits"
        }

        assertTrue(
            officialDamage in simulatedDamage.min()..simulatedDamage.max(),
            "official=$officialDamage simulated=${simulatedDamage.min()}.." +
                "${simulatedDamage.max()}\n$summaries",
        )
    }

    @Test
    fun `xuanwei paper attacker base normal attack matches the official damage`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260311230758305_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        var round = 0
        var actor = 0
        val officialDamage = actions.single { action ->
            when (action.id) {
                ClientBattleTextReplayProtocol.ROUND -> round = action.params[0].toInt()
                ClientBattleTextReplayProtocol.HERO_ACTION_START ->
                    actor = action.params[0].toInt()
            }
            round == 2 &&
                actor == 1 &&
                action.id == ClientBattleTextReplayProtocol.NORMAL_DAMAGE &&
                action.params[0].toInt() == 5
        }.params[1].toInt()
        assertEquals(724, officialDamage)

        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val damage = simulations.flatMap { (_, result) ->
            result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 1 &&
                        ClientBattleTextReplayProtocol.position(it.target) == 5
                }
                .map(BattleEvent.NormalAttack::damage)
        }
        val diagnostics = simulations.joinToString(separator = "\n") { (seed, result) ->
            val attacks = result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .filter { ClientBattleTextReplayProtocol.position(it.source) == 1 }
                .map {
                    "r${it.round}:p${ClientBattleTextReplayProtocol.position(it.target)}=" +
                        "${it.damage}"
                }
            "seed=$seed attacks=$attacks"
        }

        assertTrue(
            damage.isNotEmpty() && officialDamage in damage.min()..damage.max(),
            "official=$officialDamage simulated=${damage.minOrNull()}.." +
                "${damage.maxOrNull()}\n$diagnostics",
        )
    }

    @Test
    fun `xuanwei paper can end after the sixth normal attack`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260311230758305_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val triggerCounts = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 200885 }
        }
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val rounds = result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round)
                ?: 0
            val sourceDamage = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        it.round <= 3 &&
                            ClientBattleTextReplayProtocol.position(it.source) == 4
                    }?.let {
                        "r${it.round}:normal->p" +
                            "${ClientBattleTextReplayProtocol.position(it.target)}=" +
                            "${it.damage}/${it.targetTroopsAfter}"
                    }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        it.round <= 3 &&
                            ClientBattleTextReplayProtocol.position(it.source) == 4 &&
                            it.skillId == 200885
                    }?.let {
                        "r${it.round}:skill->p" +
                            "${ClientBattleTextReplayProtocol.position(it.target)}=" +
                            "${it.damage}/${it.targetTroopsAfter}"
                    }
                    else -> null
                }
            }
            val triggers = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 200885 }
            "seed=$seed rounds=$rounds triggers=$triggers sourceDamage=$sourceDamage"
        }

        assertTrue(
            official.skillTriggers.getValue(200885) in
                triggerCounts.min()..triggerCounts.max(),
            "official=${official.skillTriggers.getValue(200885)} " +
                "simulated=${triggerCounts.min()}..${triggerCounts.max()}\n$summaries",
        )
    }

    @Test
    fun `shuangyan paper total triggers include its zhengshi retrigger`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312002259656_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        var officialRound = 0
        val officialAttemptRounds = actions.mapNotNull { action ->
            if (action.id == ClientBattleTextReplayProtocol.ROUND) {
                officialRound = action.params[0].toInt()
            }
            officialRound.takeIf {
                action.id == "gf".toInt(36) &&
                    action.params[0].toInt() == 4 &&
                    action.params[1].toInt() == 200652
            }
        }
        assertEquals(listOf(1, 2, 3, 6, 7, 8), officialAttemptRounds)
        assertEquals(6, official.skillTriggers.getValue(200652))
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val triggerCounts = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 200652 }
        }
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val actionRounds = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .filter { ClientBattleTextReplayProtocol.position(it.source) == 4 }
                .map(BattleEvent.HeroActionStart::round)
            val triggers = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.skillId == 200652 }
                .map { "${it.round}:${it.rootSkillId}" }
            val zhengshi = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.skillId == 213244 }
                .map { "${it.round}:${ClientBattleTextReplayProtocol.position(it.source)}" }
            val controls = result.events.filterIsInstance<BattleEvent.StatusApplied>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.target) == 4 &&
                        it.status in setOf(
                            BattleStatus.CONFUSION,
                            BattleStatus.HESITATION,
                        )
                }
                .map { "${it.round}:${it.status}:${it.skillId}:${it.effectId}" }
            "seed=$seed actions=$actionRounds triggers=$triggers " +
                "zhengshi=$zhengshi controls=$controls"
        }

        assertTrue(
            official.skillTriggers.getValue(200652) in
                triggerCounts.min()..triggerCounts.max(),
            "official=${official.skillTriggers.getValue(200652)} " +
                "simulated=${triggerCounts.min()}..${triggerCounts.max()}\n$summaries",
        )
    }

    @Test
    fun `fenji paper can focus the defender base before round four`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312162500131_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        data class FenjiSimulation(
            val seed: Int,
            val result: BattleResult,
            val candidatesByRound: Map<Int, List<List<Int>>>,
        )
        val simulations = (0 until 32).map { seed ->
            val candidatesByRound = linkedMapOf<Int, MutableList<List<Int>>>()
            val decisions = BattleTargetDecisionSource { decision ->
                if (decision.rule.detailId == 21196101) {
                    candidatesByRound.getOrPut(
                        decision.context.round,
                        ::mutableListOf,
                    ).add(
                        decision.candidates.map(
                            ClientBattleTextReplayProtocol::position,
                        ),
                    )
                }
                null
            }
            FenjiSimulation(
                seed = seed,
                result = BattleEngine.resolve(
                    request,
                    config,
                    SeededBattleRandom(seed),
                    decisions,
                ),
                candidatesByRound = candidatesByRound.mapValues { it.value.toList() },
            )
        }
        val baseTroops = simulations.map { simulation ->
            val result = simulation.result
            result.defender.heroes.single { hero ->
                ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 6
            }.troops
        }
        val summaries = simulations.joinToString(separator = "\n") { simulation ->
            val seed = simulation.seed
            val result = simulation.result
            val fenjiTargets = result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    it.skillId == 211961 &&
                        ClientBattleTextReplayProtocol.position(it.source) == 2
                }
                .groupBy(BattleEvent.SkillDamage::round)
                .mapValues { (_, events) ->
                    events.map {
                        ClientBattleTextReplayProtocol.position(it.target) to it.damage
                    }
                }
            val base = result.defender.heroes.single { hero ->
                ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 6
            }.troops
            val rounds = result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round)
                ?: 0
            val blocked = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.EffectBlocked -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let { "blocked:${it.round}:${it.skillId}:${it.blockingEffectId}" }
                    is BattleEvent.Evaded -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.source) == 2 &&
                            ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let { "evaded:${it.round}" }
                    else -> null
                }
            }
            val baseModifiers = result.defender.heroes.single { hero ->
                ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 6
            }.modifiers
            "seed=$seed rounds=$rounds outcome=${result.outcome} " +
                "base=$base fenji=$fenjiTargets blocked=$blocked " +
                "candidates=${simulation.candidatesByRound} baseModifiers=$baseModifiers"
        }

        assertTrue(
            official.finalTroopsByPosition.getValue(6) in baseTroops.min()..baseTroops.max(),
            "official=${official.finalTroopsByPosition.getValue(6)} " +
                "simulated=${baseTroops.min()}..${baseTroops.max()}\n$summaries",
        )
    }

    @Test
    fun `qixurulin paper can defeat the defender by round four`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312172752527_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val simulatedRounds = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round)
                ?: 0
        }
        val closest = simulations.minWith(
            compareBy<Pair<Int, BattleResult>> { (_, result) ->
                result.events.filterIsInstance<BattleEvent.RoundStart>()
                    .maxOfOrNull(BattleEvent.RoundStart::round)
                    ?: 0
            }.thenBy { (_, result) ->
                result.defender.heroes.sumOf(BattleHero::troops)
            },
        )
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            val rounds = result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round)
                ?: 0
            val final = (result.attacker.heroes + result.defender.heroes).associate { hero ->
                val side = if (hero in result.attacker.heroes) Side.ATTACKER else Side.DEFENDER
                ClientBattleTextReplayProtocol.position(side, hero.position) to hero.troops
            }
            val qixurulin = result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.skillId == 210282 }
                .groupBy(BattleEvent.SkillDamage::round)
                .mapValues { (_, damage) ->
                    damage.map {
                        "${ClientBattleTextReplayProtocol.position(it.source)}->" +
                            "${ClientBattleTextReplayProtocol.position(it.target)}:" +
                            "${it.damage}/${it.targetTroopsAfter}"
                    }
                }
            "seed=$seed rounds=$rounds outcome=${result.outcome} " +
                "final=$final qixurulin=$qixurulin"
        }
        val closestTrace = closest.second.events.mapNotNull { event ->
            when (event) {
                is BattleEvent.SkillTriggered -> event.takeIf {
                    it.source.side == Side.ATTACKER &&
                        it.skillId in setOf(200024, 200847)
                }?.let {
                    "r${it.round} trigger p${ClientBattleTextReplayProtocol.position(it.source)} " +
                        "skill=${it.skillId}"
                }
                is BattleEvent.SkillDamage -> event.takeIf {
                    it.source.side == Side.ATTACKER &&
                        it.skillId in setOf(200024, 200847, 210282) ||
                        it.skillId in 210847..217847
                }?.let {
                    "r${it.round} p${ClientBattleTextReplayProtocol.position(it.source)}->" +
                        "p${ClientBattleTextReplayProtocol.position(it.target)} " +
                        "skill=${it.skillId} damage=${it.damage} after=${it.targetTroopsAfter}"
                }
                else -> null
            }
        }.joinToString(separator = "\n")

        assertTrue(
            official.rounds in simulatedRounds.min()..simulatedRounds.max(),
            "official=${official.rounds} simulated=${simulatedRounds.min()}.." +
                "${simulatedRounds.max()} closestSeed=${closest.first}\n" +
                "$closestTrace\n$summaries",
        )
    }

    @Test
    fun `quhutunlang paper can defeat the defender base in round four`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312013216423_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulations = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(request, config, SeededBattleRandom(seed))
        }
        val replayedTargets = (0 until 32).map { seed ->
            seed to BattleEngine.resolve(
                request,
                config,
                SeededBattleRandom(seed),
                OfficialReportFixture.targetDecisions(actions),
            )
        }
        val simulatedRounds = simulations.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round)
                ?: 0
        }
        fun simulationSummary(seed: Int, result: BattleResult): String {
            val rounds = result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round)
                ?: 0
            val roundFourActions = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .filter { it.round == 4 }
                .map { ClientBattleTextReplayProtocol.position(it.source) }
            val triggers = result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 2 &&
                        it.skillId == 200024
                }
                .map(BattleEvent.SkillTriggered::round)
            val damage = result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter {
                    ClientBattleTextReplayProtocol.position(it.source) == 2 &&
                        it.skillId == 200024
                }
                .map {
                    "r${it.round}:p${ClientBattleTextReplayProtocol.position(it.target)}=" +
                        "${it.damage}/${it.targetTroopsAfter}"
                }
            val baseTroopTrace = result.events.mapNotNull { event ->
                when (event) {
                    is BattleEvent.NormalAttack -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let { "r${it.round}:normal=${it.damage}/${it.targetTroopsAfter}" }
                    is BattleEvent.SkillDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let {
                        "r${it.round}:skill${it.skillId}=${it.damage}/${it.targetTroopsAfter}"
                    }
                    is BattleEvent.OngoingDamage -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let {
                        "r${it.round}:ongoing${it.skillId}=${it.damage}/" +
                            "${it.targetTroopsAfter}"
                    }
                    is BattleEvent.Recovery -> event.takeIf {
                        ClientBattleTextReplayProtocol.position(it.target) == 6
                    }?.let {
                        "r${it.round}:recovery${it.skillId}=+${it.amount}/" +
                            "${it.targetTroopsAfter}"
                    }
                    else -> null
                }
            }
            val base = result.defender.heroes.single { hero ->
                ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position) == 6
            }.troops
            return "seed=$seed rounds=$rounds base=$base actions4=$roundFourActions " +
                "triggers=$triggers damage=$damage baseTrace=$baseTroopTrace"
        }
        val summaries = simulations.joinToString(separator = "\n") { (seed, result) ->
            simulationSummary(seed, result)
        }
        val replayedSummaries = replayedTargets.joinToString(separator = "\n") {
                (seed, result) ->
            simulationSummary(seed, result)
        }
        val replayedRounds = replayedTargets.map { (_, result) ->
            result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round)
                ?: 0
        }

        assertTrue(
            official.rounds in simulatedRounds.min()..simulatedRounds.max(),
            "official=${official.rounds} simulated=${simulatedRounds.min()}.." +
                "${simulatedRounds.max()} replayed=${replayedRounds.min()}.." +
                "${replayedRounds.max()}\nautonomous:\n$summaries\n" +
                "replayed targets:\n$replayedSummaries",
        )
    }

    @Test
    fun `paper custom book configuration replaces the static placeholder graph`() {
        val report = java.nio.file.Path.of(
            "src/test/resources/assent/cfg/paper/6231/cap_20260312074501252_00001857_zlib.json",
        )
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(report)
        val official = OfficialReportFixture.fullBattleSummary(actions)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val simulatedTriggerCounts = (0 until 32).map { seed ->
            BattleEngine.resolve(request, config, SeededBattleRandom(seed))
                .events
                .filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 399100 }
        }

        assertEquals(1, official.skillTriggers[399100])
        assertTrue(
            official.skillTriggers.getValue(399100) in
                simulatedTriggerCounts.min()..simulatedTriggerCounts.max(),
            "official=${official.skillTriggers.getValue(399100)} " +
                "simulated=${simulatedTriggerCounts.min()}..${simulatedTriggerCounts.max()}",
        )
    }

    private fun BattleResult.troopsAfterFirstRound(
        clientPosition: Int,
        initialTroops: Int,
    ): Int =
        events.fold(initialTroops) { troops, event ->
            when (event) {
                is BattleEvent.NormalAttack -> event.targetTroopsAfter.takeIf {
                    event.round == 1 &&
                        ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                }
                is BattleEvent.SkillDamage -> event.targetTroopsAfter.takeIf {
                    event.round == 1 &&
                        ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                }
                is BattleEvent.OngoingDamage -> event.targetTroopsAfter.takeIf {
                    event.round == 1 &&
                        ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                }
                is BattleEvent.Recovery -> event.targetTroopsAfter.takeIf {
                    event.round == 1 &&
                        ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                }
                else -> null
            } ?: troops
        }

    private fun BattleResult.troopsBeforeFirstAction(
        clientPosition: Int,
        initialTroops: Int,
    ): Int =
        events.takeWhile { event ->
            event !is BattleEvent.HeroActionStart ||
                event.round != 1 ||
                ClientBattleTextReplayProtocol.position(event.source) != clientPosition
        }.fold(initialTroops) { troops, event ->
            when (event) {
                is BattleEvent.NormalAttack -> event.targetTroopsAfter.takeIf {
                    event.round == 1 &&
                        ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                }
                is BattleEvent.SkillDamage -> event.targetTroopsAfter.takeIf {
                    event.round == 1 &&
                        ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                }
                is BattleEvent.OngoingDamage -> event.targetTroopsAfter.takeIf {
                    event.round == 1 &&
                        ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                }
                else -> null
            } ?: troops
        }

    private fun BattleResult.damageBeforeFirstAction(clientPosition: Int): String =
        events.takeWhile { event ->
            event !is BattleEvent.HeroActionStart ||
                event.round != 1 ||
                ClientBattleTextReplayProtocol.position(event.source) != clientPosition
        }.mapNotNull { event ->
            when (event) {
                is BattleEvent.NormalAttack -> if (
                    event.round == 1 &&
                    ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                ) {
                    "normal ${ClientBattleTextReplayProtocol.position(event.source)}->$clientPosition " +
                        "damage=${event.damage} after=${event.targetTroopsAfter}"
                } else {
                    null
                }
                is BattleEvent.SkillDamage -> if (
                    event.round == 1 &&
                    ClientBattleTextReplayProtocol.position(event.target) == clientPosition
                ) {
                    "skill=${event.skillId} effect=${event.effectId} " +
                        "${ClientBattleTextReplayProtocol.position(event.source)}->$clientPosition " +
                        "damage=${event.damage} after=${event.targetTroopsAfter}"
                } else {
                    null
                }
                else -> null
            }
        }.joinToString(separator = "\n")

    private fun BattleResult.firstRoundDamageTrace(): String =
        events.mapNotNull { event ->
            when (event) {
                is BattleEvent.NormalAttack -> if (event.round == 1) {
                    "normal ${ClientBattleTextReplayProtocol.position(event.source)}->" +
                        "${ClientBattleTextReplayProtocol.position(event.target)} " +
                        "damage=${event.damage} after=${event.targetTroopsAfter}"
                } else {
                    null
                }
                is BattleEvent.SkillDamage -> if (event.round == 1) {
                    "skill=${event.skillId} effect=${event.effectId} " +
                        "${ClientBattleTextReplayProtocol.position(event.source)}->" +
                        "${ClientBattleTextReplayProtocol.position(event.target)} " +
                        "damage=${event.damage} after=${event.targetTroopsAfter}"
                } else {
                    null
                }
                is BattleEvent.OngoingDamage -> if (event.round == 1) {
                    "ongoing skill=${event.skillId} " +
                        "${ClientBattleTextReplayProtocol.position(event.source)}->" +
                        "${ClientBattleTextReplayProtocol.position(event.target)} " +
                        "damage=${event.damage} after=${event.targetTroopsAfter}"
                } else {
                    null
                }
                is BattleEvent.SkillTriggered -> if (event.round == 1) {
                    "trigger source=${ClientBattleTextReplayProtocol.position(event.source)} " +
                        "root=${event.rootSkillId} skill=${event.skillId} " +
                        "trigger=${event.trigger}"
                } else {
                    null
                }
                is BattleEvent.StatChanged -> if (
                    event.round <= 1 &&
                    ClientBattleTextReplayProtocol.position(event.target) == 6
                ) {
                    "stat round=${event.round} " +
                        "${ClientBattleTextReplayProtocol.position(event.source)}->6 " +
                        "skill=${event.skillId} effect=${event.effectId} " +
                        "${event.stat} delta=${event.deltaExact} after=${event.valueAfterExact}"
                } else {
                    null
                }
                is BattleEvent.ModifierApplied -> if (
                    event.round == 0 &&
                    event.skillId in setOf(200198, 200204, 200773, 296106) &&
                    ClientBattleTextReplayProtocol.position(event.target) in setOf(1, 6)
                ) {
                    "modifier ${ClientBattleTextReplayProtocol.position(event.source)}->" +
                        "${ClientBattleTextReplayProtocol.position(event.target)} " +
                        "skill=${event.skillId} effect=${event.effectId} amount=${event.amount}"
                } else {
                    null
                }
                is BattleEvent.Recovery -> if (event.round == 1) {
                    "recovery skill=${event.skillId} " +
                        "${ClientBattleTextReplayProtocol.position(event.source)}->" +
                        "${ClientBattleTextReplayProtocol.position(event.target)} " +
                        "amount=${event.amount} after=${event.targetTroopsAfter}"
                } else {
                    null
                }
                else -> null
            }
        }.joinToString("\n")

    private fun List<Int>.median(): Int {
        val sorted = sorted()
        return sorted[sorted.size / 2]
    }

    private fun actionRoundMismatches(
        official: OfficialReportFixture.FullBattleSummary,
        simulated: List<OfficialReportFixture.FullBattleSummary>,
    ): List<String> =
        (1..6).flatMap { position ->
            (1..8).mapNotNull { round ->
                val officialCount =
                    official.actionRoundsByPosition[position].orEmpty().count { it == round }
                val simulatedCounts = simulated.map { summary ->
                    summary.actionRoundsByPosition[position].orEmpty().count { it == round }
                }
                val interval = simulatedCounts.min()..simulatedCounts.max()
                if (officialCount !in interval) {
                    "p$position:r$round=$officialCount/$interval"
                } else {
                    null
                }
            }
        }

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()

    private fun skillTriggerMismatches(
        official: OfficialReportFixture.FullBattleSummary,
        simulated: List<OfficialReportFixture.FullBattleSummary>,
    ): List<String> =
        official.skillTriggers.keys.sorted().mapNotNull { skillId ->
            val officialCount = official.skillTriggers[skillId] ?: 0
            val simulatedCounts = simulated.map { it.skillTriggers[skillId] ?: 0 }
            val interval = simulatedCounts.min()..simulatedCounts.max()
            if (officialCount !in interval) {
                "$skillId=$officialCount/$interval"
            } else {
                null
            }
        }
}
