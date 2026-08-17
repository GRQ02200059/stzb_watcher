package com.stzb.battle.core

import com.stzb.battle.core.skill.BattleTargetDecisionRequest
import com.stzb.battle.core.skill.BattleTrigger
import com.stzb.battle.core.skill.SkillBattleContext
import com.stzb.battle.core.skill.SkillRuleCatalog
import com.stzb.battle.core.skill.SkillRuntimeState
import com.stzb.battle.core.skill.SkillScope
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialReportFixtureTest {
    private val officialReport =
        Path.of("src/test/resources/assent/cfg/paper/11/cap_20260312014510506_0000000b_zlib.json")

    @Test
    fun `battle request uses the final precise stats recorded before battle start`() {
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(officialReport)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)

        assertEquals(236.4, request.attacker.heroes.single { it.position == 2 }.stats.precise(BattleStat.STRATEGY))
        assertEquals(247.8, request.defender.heroes.single { it.position == 2 }.stats.precise(BattleStat.STRATEGY))
        assertEquals(198.0, request.defender.heroes.single { it.position == 2 }.inherentStats.precise(BattleStat.STRATEGY))

        val battleStart = actions.indexOfFirst { it.id == "hr".toInt(36) }
        val withPostStartMutation = actions.toMutableList().apply {
            add(battleStart + 1, OfficialReportFixture.parseText("0x3,999999,3,1,999.99").single())
        }
        val unchanged = OfficialReportFixture.reconstructBattleRequest(withPostStartMutation, config)
        assertEquals(236.4, unchanged.attacker.heroes.single { it.position == 2 }.stats.precise(BattleStat.STRATEGY))
    }

    @Test
    fun `derived equipment feature level uses the first official effect strength`() {
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(
            Path.of("src/test/resources/assent/cfg/paper/11/cap_20260311223905520_0000000b_zlib.json"),
        )
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val owner = request.attacker.heroes.single { it.id == BattleHeroId(100449) }

        assertEquals(
            BattleModifier.DamageTakenPercent(
                percent = -14,
                requiredSourceInherentStatBelowTarget = BattleStat.DEFENSE,
            ),
            owner.modifiers.filterIsInstance<BattleModifier.DamageTakenPercent>()
                .single { it.requiredSourceInherentStatBelowTarget == BattleStat.DEFENSE },
        )
    }

    @Test
    fun `battle phase equipment child action restores buxie feature and level`() {
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(
            Path.of("src/test/resources/assent/cfg/paper/11/cap_20260311223210795_0000000b_zlib.json"),
        )
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val owner = request.defender.heroes.single { it.id == BattleHeroId(100449) }

        assertEquals(
            BattleModifier.TroopLossRecoveryTakenPercent(4),
            owner.modifiers
                .filterIsInstance<BattleModifier.TroopLossRecoveryTakenPercent>()
                .single(),
        )
    }

    @Test
    fun `equipment child action restores xuanfeng feature and level`() {
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(officialReport)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val owner = request.attacker.heroes.single { it.id == BattleHeroId(100028) }

        assertEquals(
            BattleModifier.NextStrategyDamageAfterNormalAttackPercent(12),
            owner.modifiers
                .filterIsInstance<BattleModifier.NextStrategyDamageAfterNormalAttackPercent>()
                .single(),
        )
    }

    @Test
    fun `equipment probability feature remains scoped to the official inherent skill`() {
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(officialReport)
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val owner = request.defender.heroes.single { it.id == BattleHeroId(100619) }

        assertEquals(
            BattleModifier.SkillProbabilityPercent(
                percent = 8,
                skillId = 200884,
            ),
            owner.modifiers.filterIsInstance<BattleModifier.SkillProbabilityPercent>()
                .single(),
        )
    }

    @Test
    fun `direct equipment feature action restores active damage reduction`() {
        val config = BattleConfigRepository.loadDefault()
        val actions = OfficialReportFixture.read(
            Path.of("src/test/resources/assent/cfg/paper/6231/cap_20260312172752527_00001857_zlib.json"),
        )
        val request = OfficialReportFixture.reconstructBattleRequest(actions, config)
        val owner = request.attacker.heroes.single { it.id == BattleHeroId(100553) }

        assertEquals(
            BattleModifier.DamageTakenPercent(
                origin = DamageOrigin.ACTIVE,
                percent = -11,
            ),
            owner.modifiers.filterIsInstance<BattleModifier.DamageTakenPercent>()
                .single { it.origin == DamageOrigin.ACTIVE },
        )
    }

    @Test
    fun `target replay preserves report order and consumes repeated decision groups`() {
        val config = BattleConfigRepository.loadDefault()
        val rule = SkillRuleCatalog.build(
            SkillScope(setOf(200198), emptySet()),
            config,
        ).details.single { it.detailId == 20019801 }
        val source = BattleHeroRef(Side.ATTACKER, 2, BattleHeroId(3))
        val front = BattleHeroRef(Side.ATTACKER, 2, BattleHeroId(3))
        val middle = BattleHeroRef(Side.ATTACKER, 1, BattleHeroId(2))
        val base = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val context = SkillBattleContext(
            request = BattleRequest(BattleTeam(emptyList()), BattleTeam(emptyList())),
            runtime = SkillRuntimeState(),
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 200198,
            currentSkillId = 200198,
            trigger = BattleTrigger.BATTLE_PASSIVE,
        )
        val decisions = OfficialReportFixture.targetDecisions(
            OfficialReportFixture.parseText(
                "ja3,200198,3,531,53#ja3,200198,1,531,53#" +
                    "ja3,200198,3,533,53#ja3,200198,2,531,53",
            ),
        )
        fun select() = decisions.select(
            BattleTargetDecisionRequest(rule, context, listOf(front, middle, base), limit = 2),
        )

        assertEquals(listOf(front, base), select())
        assertEquals(listOf(middle), select())
    }

    @Test
    fun `target replay groups battle damage targets and falls back to current child skill`() {
        val config = BattleConfigRepository.loadDefault()
        val graph = SkillRuleCatalog.build(
            SkillScope(setOf(200987), emptySet()),
            config,
        )
        val strategyRule = graph.details.single { it.detailId == 21198723 }
        val physicalRule = graph.details.single { it.detailId == 21198701 }
        val source = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(6))
        val base = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val front = BattleHeroRef(Side.ATTACKER, 2, BattleHeroId(3))
        val context = SkillBattleContext(
            request = BattleRequest(BattleTeam(emptyList()), BattleTeam(emptyList())),
            runtime = SkillRuntimeState(),
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200987,
            currentSkillId = 211987,
            trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
        )
        val decisions = OfficialReportFixture.targetDecisions(
            OfficialReportFixture.parseText(
                "1o6,211987,1,834,6523,11#" +
                    "1o6,211987,3,639,8035,11#" +
                    "1n6,211987,1,2382,4578,11",
            ),
        )

        assertEquals(
            listOf(base, front),
            decisions.select(
                BattleTargetDecisionRequest(
                    strategyRule,
                    context,
                    listOf(base, front),
                    limit = 2,
                ),
            ),
        )
        assertEquals(
            listOf(base),
            decisions.select(
                BattleTargetDecisionRequest(
                    physicalRule,
                    context,
                    listOf(base, front),
                    limit = 1,
                ),
            ),
        )
    }

    @Test
    fun `target replay separates adjacent invocations that hit the same target`() {
        val config = BattleConfigRepository.loadDefault()
        val graph = SkillRuleCatalog.build(
            SkillScope(setOf(200987), emptySet()),
            config,
        )
        val strategyRule = graph.details.single { it.detailId == 21198723 }
        val source = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(6))
        val base = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val front = BattleHeroRef(Side.ATTACKER, 2, BattleHeroId(3))
        val context = SkillBattleContext(
            request = BattleRequest(BattleTeam(emptyList()), BattleTeam(emptyList())),
            runtime = SkillRuntimeState(),
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200987,
            currentSkillId = 211987,
            trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
        )
        val decisions = OfficialReportFixture.targetDecisions(
            OfficialReportFixture.parseText(
                "1o6,211987,1,834,6523,11#" +
                    "1o6,211987,1,639,5884,11#" +
                    "1o6,211987,3,500,7535,11",
            ),
        )
        fun select() = decisions.select(
            BattleTargetDecisionRequest(
                strategyRule,
                context,
                listOf(base, front),
                limit = 2,
            ),
        )

        assertEquals(listOf(base), select())
        assertEquals(listOf(base, front), select())
    }
}
