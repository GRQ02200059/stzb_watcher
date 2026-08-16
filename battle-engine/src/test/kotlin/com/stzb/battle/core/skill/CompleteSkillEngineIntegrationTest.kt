package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleConfigRepository
import com.stzb.battle.core.BattleDamageCalculator
import com.stzb.battle.core.BattleEngine
import com.stzb.battle.core.BattleEquipmentSlot
import com.stzb.battle.core.BattleEquipmentRepository
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroId
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleHeroSpec
import com.stzb.battle.core.BattleModifier
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleStat
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleTeam
import com.stzb.battle.core.BattleTeamBuilder
import com.stzb.battle.core.DamageOrigin
import com.stzb.battle.core.DamageSchool
import com.stzb.battle.core.FixedBattleRandom
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillKind
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompleteSkillEngineIntegrationTest {
    private val config = BattleConfigRepository.loadDefault()

    @Test
    fun `first action groups heroes without discarding their speed order`() {
        val slower = hero(100001, 100, position = 2).copy(
            stats = BattleStats(100, 100, 100, 100, 0, 3),
            activeStatuses = setOf(BattleStatus.FIRST_ACTION),
        )
        val faster = hero(200001, 100, position = 2).copy(
            stats = BattleStats(100, 100, 100, 200, 0, 3),
            activeStatuses = setOf(BattleStatus.FIRST_ACTION),
        )
        val engine = DefaultCompleteSkillEngine.create(
            BattleRequest(
                attacker = BattleTeam(listOf(slower)),
                defender = BattleTeam(listOf(faster)),
            ),
            config,
        )

        assertEquals(
            listOf(Side.DEFENDER, Side.ATTACKER),
            engine.livingHeroesInSpeedOrder().map(BattleHeroRef::side),
        )
    }

    @Test
    fun `production engine exposes client hero metadata to skill conditions`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100582, 100, listOf(200828), position = 2)),
            ),
            defender = BattleTeam(
                listOf(hero(100003, 100, position = 2)),
            ),
            maxRounds = 1,
        )

        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }

        assertTrue(SkillBattleViewCapability.HERO_METADATA in engine.state.view.capabilities)
        assertEquals(
            SkillBattleHeroMetadata(
                gender = SkillHeroGender.MALE,
                troopType = SkillTroopType.INFANTRY,
                country = 3,
            ),
            engine.state.view.metadata(source),
        )
    }

    @Test
    fun `prepared evade rolls once per round and remains stable for every hit`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100001, 100, position = 0))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val attacker = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = attacker,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )
        engine.applyChanges(
            listOf(711, 714).map { effectId ->
                ApplyBattleEffectChange(
                    PersistentEffectSpec(
                        source = target,
                        target = target,
                        rootSkillId = 900000,
                        skillId = 900000,
                        skillKind = SkillKind.COMMAND,
                        rawSkillType = 2,
                        detailId = 90000000 + effectId,
                        effectId = effectId,
                        category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
                        conflict = 0,
                        replaceType = 0,
                        bindFlag = 0,
                        maxStacks = 1,
                        delayRound = 0,
                        delayHit = 0,
                        availableRounds = 2,
                        availableHit = 0,
                        clearPerHit = false,
                        startBoundary = EffectStartBoundary.IMMEDIATE,
                        potency = TypedBattlePotency.percent(50),
                    ),
                )
            },
            baseContext,
        )
        class CountingRandom(private val value: Int) : BattleRandom {
            var calls = 0

            override fun nextInt(bound: Int): Int {
                calls += 1
                return value.coerceIn(0, bound - 1)
            }
        }

        val failed = CountingRandom(99)
        val roundOne = baseContext.copy(random = failed)
        assertEquals(null, engine.tryEvade(1, attacker, target, roundOne))
        assertEquals(null, engine.tryEvade(1, attacker, target, roundOne))
        assertEquals(1, failed.calls)

        val successful = CountingRandom(0)
        val roundTwo = baseContext.copy(random = successful, round = 2)
        assertTrue(engine.tryEvade(2, attacker, target, roundTwo) != null)
        assertTrue(engine.tryEvade(2, attacker, target, roundTwo) != null)
        assertEquals(1, successful.calls)
    }

    @Test
    fun `generic meta intents fail closed unless the engine consumes their operation`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100023, 100, position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val graph = SkillRuleCatalog.build(SkillScopeCatalog.loadDefault(), config)
        val parameters = MetaEffectParameters.from(
            graph.details.single { it.detailId == 20002316 },
        )
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200023,
            currentSkillId = 200023,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        assertFailsWith<UnsupportedBattleStateChangeException> {
            engine.applyChanges(
                listOf(
                    MetaEffectChange(
                        source = source,
                        target = target,
                        rootSkillId = 200023,
                        skillId = 200023,
                        detailId = parameters.detailId,
                        effectId = 77,
                        operation = MetaEffectOperation.MARKER,
                        parameters = parameters,
                    ),
                ),
                context,
            )
        }
    }

    @Test
    fun `specified effect trigger ticks only the requested ongoing effect`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100023, 100, position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 297173,
            currentSkillId = 297173,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun ongoing(
            effectId: Int,
            status: com.stzb.battle.core.BattleStatus,
            tag: com.stzb.battle.core.DamageTag,
        ): ScheduledDamageEffectChange {
            val spec = PersistentEffectSpec(
                source = source,
                target = target,
                rootSkillId = 900000,
                skillId = 900000,
                skillKind = SkillKind.PASSIVE,
                rawSkillType = 1,
                detailId = 90000000 + effectId,
                effectId = effectId,
                category = com.stzb.battle.core.EffectCategory.HARMFUL,
                conflict = 0,
                replaceType = 0,
                bindFlag = 0,
                maxStacks = 1,
                delayRound = 0,
                delayHit = 0,
                availableRounds = 2,
                availableHit = 0,
                clearPerHit = false,
                startBoundary = EffectStartBoundary.IMMEDIATE,
                potency = TypedBattlePotency.rate(100),
            )
            return ScheduledDamageEffectChange(
                spec = spec,
                school = DamageSchool.STRATEGY,
                origin = DamageOrigin.PASSIVE,
                tags = setOf(com.stzb.battle.core.DamageTag.ONGOING, tag),
                status = status,
                coefficientSource = BattleCoefficientSource.NONE,
                rawCoefficient = 0,
                calculationTypes = emptyList(),
            )
        }
        engine.applyChanges(
            listOf(
                ongoing(
                    304,
                    com.stzb.battle.core.BattleStatus.PANIC,
                    com.stzb.battle.core.DamageTag.PANIC,
                ),
                ongoing(
                    305,
                    com.stzb.battle.core.BattleStatus.BURN,
                    com.stzb.battle.core.DamageTag.BURN,
                ),
            ),
            context,
        )
        val graph = SkillRuleCatalog.build(SkillScope(setOf(297173), emptySet()), config)
        val parameters = MetaEffectParameters.from(
            graph.details.single { it.detailId == 29717301 },
        )

        val events = engine.applyChanges(
            listOf(
                TriggerSpecifiedEffectChange(
                    source = source,
                    target = target,
                    rootSkillId = 297173,
                    skillId = 297173,
                    detailId = 29717301,
                    triggeredEffectId = 305,
                    parameters = parameters,
                ),
            ),
            context,
        )

        assertEquals(
            com.stzb.battle.core.BattleStatus.BURN,
            events.filterIsInstance<BattleEvent.OngoingDamage>().single().status,
        )
    }

    @Test
    fun `ongoing damage defeats actor before later action before skills execute`() {
        val advisorHero = hero(100692, 20, listOf(200966), position = 2).copy(
            troops = 1,
            maxTroops = 1,
        )
        val allyHero = hero(100479, 10, listOf(200012), position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 100, position = 2))),
            defender = BattleTeam(listOf(advisorHero, allyHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val advisor = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == advisorHero.position
        }
        val ally = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == allyHero.position
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.applyChanges(
            listOf(ongoingDamage(source, advisor, detailId = 900030)),
            context,
        )

        val events = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(
                round = 1,
                source = advisor,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )

        assertEquals(0, engine.liveHero(advisor).troops)
        assertEquals(
            listOf(advisor),
            events.filterIsInstance<BattleEvent.OngoingDamage>().map { it.target },
            "events=$events",
        )
        assertTrue(
            engine.liveHero(ally).modifiers.none {
                it is BattleModifier.SkillProbabilityPercent && it.percent == 10
            },
            "events=$events modifiers=${engine.liveHero(ally).modifiers}",
        )
    }

    @Test
    fun `named flag counter intents clamp per target and flag id`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100001, 100, position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200264,
            currentSkillId = 213264,
            trigger = BattleTrigger.ROUND_START,
            battleView = engine.state.view,
        )
        val change = NamedFlagCounterChange(
            source = source,
            target = source,
            rootSkillId = 200264,
            skillId = 213264,
            detailId = 21326401,
            flagId = 210264,
            delta = 2,
            maximum = 3,
        )

        engine.applyChanges(listOf(change, change), context)

        assertEquals(
            3,
            engine.state.runtime.counter(source, "skill.named-flag.210264"),
        )
    }

    @Test
    fun `engine applies hit scoped damage modifier without round duration`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100001, 100, position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200069,
            currentSkillId = 200069,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        engine.applyChanges(
            listOf(
                DamageModifierChange(
                    source = source,
                    target = source,
                    direction = DamageModifierChange.Direction.DEALT,
                    school = DamageSchool.PHYSICAL,
                    origin = null,
                    tag = null,
                    percent = 90,
                    durationRounds = 0,
                    skillId = 200069,
                    effectId = 531,
                    detailId = 20006901,
                    availableHits = 1,
                ),
            ),
            context,
        )

        val effect = engine.state.effectStore.effectsFor(source).single()
        assertEquals(20006901, effect.detailId)
        assertEquals(1, effect.remainingHits)
        assertTrue(
            engine.state.liveHero(source).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .any { it.percent == 90 },
        )
    }

    @Test
    fun `engine rejects damage modifier without round or hit lifecycle`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100001, 100, position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 900001,
            currentSkillId = 900001,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        assertFailsWith<IllegalArgumentException> {
            engine.applyChanges(
                listOf(
                    DamageModifierChange(
                        source = source,
                        target = source,
                        direction = DamageModifierChange.Direction.DEALT,
                        school = DamageSchool.PHYSICAL,
                        origin = null,
                        tag = null,
                        percent = 10,
                        durationRounds = 0,
                        skillId = 900001,
                        effectId = 531,
                        availableHits = 0,
                    ),
                ),
                context,
            )
        }
    }

    @Test
    fun `neizhuzhixian official command stat bonus persists through eight rounds`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100001, 100, listOf(300021), position = 2),
                    hero(100002, 90, position = 1),
                    hero(100003, 80, position = 0),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        val allies = engine.state.view.heroes().filter { it.side == Side.ATTACKER }
        allies.forEach { ally ->
            assertTrue(requireNotNull(engine.state.view.state(ally)).stats.strategy > 100)
            val effect = engine.state.effectStore.effectsFor(ally).single {
                it.detailId == 30002101
            }
            assertEquals(null, effect.remainingRounds)
            assertEquals(1, effect.remainingHits)
        }

        (1..8).forEach(engine::finishRound)

        allies.forEach { ally ->
            assertTrue(requireNotNull(engine.state.view.state(ally)).stats.strategy > 100)
            assertTrue(engine.state.effectStore.effectsFor(ally).any {
                it.detailId == 30002101
            })
        }
    }

    @Test
    fun `skill range meta intent scopes a configured skill without affecting its peers`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100871, 100, position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val graph = SkillRuleCatalog.build(
            SkillScope(setOf(200871), emptySet()),
            config,
        )
        val parameters = MetaEffectParameters.from(
            graph.details.single { it.detailId == 20087102 },
        )
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 200871,
            currentSkillId = 200871,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.applyChanges(
            listOf(
                MetaEffectChange(
                    source = source,
                    target = source,
                    rootSkillId = 200871,
                    skillId = 200871,
                    detailId = parameters.detailId,
                    effectId = 171,
                    operation = MetaEffectOperation.SKILL_RANGE_INCREASE,
                    parameters = parameters,
                ),
            ),
            context,
        )

        assertEquals(
            1,
            engine.state.view.skillRangeBonus(source, SkillKind.ACTIVE, skillId = 200690),
        )
        assertEquals(
            0,
            engine.state.view.skillRangeBonus(source, SkillKind.ACTIVE, skillId = 200105),
        )
    }

    @Test
    fun `shenshidingji registers resistance for every ally during command setup`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100257, 100, listOf(200257), position = 2),
                    hero(100001, 90, position = 1),
                    hero(100002, 80, position = 0),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100257) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val events = engine.prepareBattle(context)

        engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER }
            .forEach { ally ->
                assertTrue(
                    engine.state.effectStore.effectsFor(ally).any {
                        it.source == owner &&
                            it.rootSkillId == 200257 &&
                            it.skillId == 210257 &&
                            it.effectId == 118
                    },
                    "ally=$ally events=$events effects=${engine.state.effectStore.effectsFor(ally)}",
                )
            }
        assertTrue(events.none { it is BattleEvent.SkillDamage || it is BattleEvent.Recovery })
    }

    @Test
    fun `shenshidingji reacts before an enemy receives a harmful stat effect`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100257, 100, listOf(200257), position = 2).copy(troops = 9_000),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.state.mutable(owner).woundedTroops = 1_000
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val events = engine.applyChanges(
            listOf(statChange(owner, enemy, 201, -10)),
            context.copy(
                round = 1,
                rootSkillId = 900000,
                currentSkillId = 900000,
                trigger = BattleTrigger.EFFECT_APPLYING,
            ),
        )

        assertTrue(
            engine.state.effectStore.effectsFor(enemy).any {
                it.source == owner && it.skillId == 212257 && it.effectId == 521
            },
            "events=$events effects=${engine.state.effectStore.effectsFor(enemy)}",
        )
        assertTrue(
            engine.state.effectStore.effectsFor(enemy).any {
                it.source == owner && it.skillId == 212257 && it.effectId == 523
            },
            "events=$events effects=${engine.state.effectStore.effectsFor(enemy)}",
        )
        assertTrue(
            events.filterIsInstance<BattleEvent.Recovery>().any {
                it.source == owner && it.skillId == 212257 && it.amount > 0
            },
            "events=$events",
        )
    }

    @Test
    fun `qiqinqizong shares its first seven damage guards across the allied group`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100298, 100, listOf(200298), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100298) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = enemy,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.DAMAGE_BEFORE,
            battleView = engine.state.view,
        )
        val protectedTroops = mapOf(
            owner to requireNotNull(engine.state.view.state(owner)).troops,
            ally to requireNotNull(engine.state.view.state(ally)).troops,
        )

        val firstSeven = (0 until 7).map { index ->
            val target = if (index % 2 == 0) owner else ally
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = target,
                        amount = 100,
                        troopsAfter = requireNotNull(engine.state.view.state(target)).troops - 100,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 301,
                    ),
                ),
                context,
            )
        }

        assertTrue(
            firstSeven.all { events -> events.any { it is BattleEvent.Evaded } },
            "events=$firstSeven",
        )
        assertEquals(
            protectedTroops,
            protectedTroops.keys.associateWith {
                requireNotNull(engine.state.view.state(it)).troops
            },
        )

        val eighth = engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = enemy,
                    target = ally,
                    amount = 100,
                    troopsAfter = protectedTroops.getValue(ally) - 100,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 301,
                ),
            ),
            context,
        )

        assertTrue(eighth.none { it is BattleEvent.Evaded }, "events=$eighth")
        assertEquals(
            protectedTroops.getValue(ally) - 100,
            requireNotNull(engine.state.view.state(ally)).troops,
        )
    }

    @Test
    fun `qiqinqizong shares one seven event budget between control and damage`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100298, 100, listOf(200298), position = 2)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = enemy,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.EFFECT_APPLYING,
            battleView = engine.state.view,
        )

        val control = engine.applyChanges(
            listOf(controlChange(enemy, owner)),
            context,
        )
        val damageEvents = (0 until 6).map {
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = owner,
                        amount = 100,
                        troopsAfter = requireNotNull(engine.state.view.state(owner)).troops - 100,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 301,
                    ),
                ),
                context.copy(trigger = BattleTrigger.DAMAGE_BEFORE),
            )
        }

        assertTrue(
            control.filterIsInstance<BattleEvent.EffectBlocked>()
                .any { it.target == owner && it.blockingEffectId == 118 },
            "events=$control",
        )
        assertTrue(engine.state.effectStore.effectsFor(owner).none { it.effectId == 501 })
        assertTrue(
            damageEvents.all { events -> events.any { it is BattleEvent.Evaded } },
            "events=$damageEvents",
        )
        val troopsBeforeEighth = requireNotNull(engine.state.view.state(owner)).troops

        val eighth = engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = enemy,
                    target = owner,
                    amount = 100,
                    troopsAfter = troopsBeforeEighth - 100,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 301,
                ),
            ),
            context.copy(trigger = BattleTrigger.DAMAGE_BEFORE),
        )

        assertTrue(eighth.none { it is BattleEvent.Evaded }, "events=$eighth")
        assertEquals(
            troopsBeforeEighth - 100,
            requireNotNull(engine.state.view.state(owner)).troops,
        )
    }

    @Test
    fun `qiqinqizong shu branch debuffs the highest damage enemy on the next round`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100582, 100, listOf(200298), position = 2)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 2),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val highestDamageEnemy = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 1
        }
        val otherEnemy = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 2
        }
        engine.state.recordDamage(highestDamageEnemy, 1_000)
        engine.state.recordDamage(otherEnemy, 100)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = highestDamageEnemy,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.DAMAGE_BEFORE,
            battleView = engine.state.view,
        )

        repeat(7) {
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = highestDamageEnemy,
                        target = owner,
                        amount = 100,
                        troopsAfter = requireNotNull(engine.state.view.state(owner)).troops - 100,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 301,
                    ),
                ),
                context,
            )
        }

        assertTrue(
            engine.state.effectStore.effectsFor(highestDamageEnemy)
                .none { it.skillId == 214298 },
        )

        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(
                round = 2,
                source = owner,
                trigger = BattleTrigger.ROUND_START,
            ),
        )

        val highestEffects = engine.state.effectStore.effectsFor(highestDamageEnemy)
            .filter { it.skillId == 214298 }
        assertTrue(highestEffects.any { it.effectId == 202 }, "effects=$highestEffects")
        assertTrue(
            highestEffects.none { it.effectId == 532 || it.effectId == 534 },
            "effects=$highestEffects",
        )
        assertTrue(
            engine.state.effectStore.effectsFor(otherEnemy)
                .none { it.skillId == 214298 },
        )
    }

    @Test
    fun `qiqinqizong non shu branch reduces both damage schools on the next round`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100001, 100, listOf(200298), position = 2)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 2),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val highestDamageEnemy = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 1
        }
        val otherEnemy = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 2
        }
        engine.state.recordDamage(highestDamageEnemy, 1_000)
        engine.state.recordDamage(otherEnemy, 100)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = highestDamageEnemy,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.DAMAGE_BEFORE,
            battleView = engine.state.view,
        )

        repeat(7) {
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = highestDamageEnemy,
                        target = owner,
                        amount = 100,
                        troopsAfter = requireNotNull(engine.state.view.state(owner)).troops - 100,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 301,
                    ),
                ),
                context,
            )
        }

        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(
                round = 2,
                source = owner,
                trigger = BattleTrigger.ROUND_START,
            ),
        )

        val highestEffects = engine.state.effectStore.effectsFor(highestDamageEnemy)
            .filter { it.skillId == 214298 }
        assertTrue(highestEffects.any { it.effectId == 532 }, "effects=$highestEffects")
        assertTrue(highestEffects.any { it.effectId == 534 }, "effects=$highestEffects")
        assertTrue(highestEffects.none { it.effectId == 202 }, "effects=$highestEffects")
        assertTrue(
            engine.state.effectStore.effectsFor(otherEnemy)
                .none { it.skillId == 214298 },
        )
    }

    @Test
    fun `fuboyangsha converts normal attack uplift into layers and queued extra attacks`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100785, 100, listOf(200255), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100785) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        val normalDamageBonus = engine.state.view.activeEffectStrength(ally, 20025525)
        assertTrue(normalDamageBonus > 0)

        engine.trigger(
            BattleTrigger.NORMAL_ATTACK_AFTER,
            context.copy(
                round = 1,
                source = ally,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )

        val upliftNamespace = "skill.200255.normal-damage-uplift"
        val layerNamespace = "skill.200255.yangsha-layers"
        assertEquals(normalDamageBonus % 40, engine.state.runtime.counter(owner, upliftNamespace))
        assertEquals(normalDamageBonus / 40, engine.state.runtime.counter(owner, layerNamespace))

        engine.state.runtime.addCounter(
            owner,
            layerNamespace,
            delta = 8 - engine.state.runtime.counter(owner, layerNamespace),
            maximum = 20,
        )
        engine.trigger(
            BattleTrigger.NORMAL_ATTACK_AFTER,
            context.copy(
                round = 1,
                source = owner,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )

        val progressAfterOwner = (normalDamageBonus % 40) + normalDamageBonus
        val layersBeforeConsumption = 8 + progressAfterOwner / 40
        assertEquals(progressAfterOwner % 40, engine.state.runtime.counter(owner, upliftNamespace))
        assertEquals(layersBeforeConsumption % 4, engine.state.runtime.counter(owner, layerNamespace))
        assertEquals(
            layersBeforeConsumption / 4,
            engine.consumePendingExtraNormalAttacks(owner),
        )
        assertEquals(0, engine.consumePendingExtraNormalAttacks(owner))
    }

    @Test
    fun `fuboyangsha caps accumulated yangsha at twenty layers`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100785, 100, listOf(200255), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100785) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        val normalDamageBonus = engine.state.view.activeEffectStrength(ally, 20025525)
        val attacksForTwentyOneLayers =
            (21 * 40 + normalDamageBonus - 1) / normalDamageBonus

        repeat(attacksForTwentyOneLayers) {
            engine.trigger(
                BattleTrigger.NORMAL_ATTACK_AFTER,
                context.copy(
                    round = 1,
                    source = ally,
                    trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                ),
            )
        }

        assertEquals(
            20,
            engine.state.runtime.counter(owner, "skill.200255.yangsha-layers"),
        )
    }

    @Test
    fun `configured battle consumes fuboyangsha queue as repeated normal attacks`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100785, 300, listOf(200255), position = 2),
                        hero(100001, 200, position = 1),
                        hero(100002, 100, position = 0),
                    ),
                ),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 10, position = 2).copy(
                            troops = 100_000,
                            maxTroops = 100_000,
                        ),
                    ),
                ),
                maxRounds = 8,
            ),
            config,
            FixedBattleRandom(0),
        )

        val ownerNormalAttacks = result.events
            .filterIsInstance<BattleEvent.NormalAttack>()
            .count { it.source.heroId == BattleHeroId(100785) }
        assertTrue(
            ownerNormalAttacks > 8,
            "normalAttacks=$ownerNormalAttacks",
        )
    }

    @Test
    fun `pibingjuyi grants two birui layers and consumes one before each damage`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100791, 100, listOf(200264), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100791) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(99),
            round = 1,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.ROUND_START,
            battleView = engine.state.view,
        )

        engine.trigger(BattleTrigger.ROUND_START, context)

        val layerNamespace = "skill.200264.birui-layers"
        assertEquals(2, engine.state.runtime.counter(owner, layerNamespace))
        assertEquals(2, engine.state.runtime.counter(ally, layerNamespace))
        val losses = (0 until 3).map {
            val troopsBefore = requireNotNull(engine.state.view.state(ally)).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = ally,
                        amount = 100,
                        troopsAfter = troopsBefore - 100,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.NORMAL,
                        tags = emptySet(),
                        skillId = 0,
                        effectId = 0,
                    ),
                ),
                context.copy(
                    source = enemy,
                    trigger = BattleTrigger.DAMAGE_BEFORE,
                ),
            )
            troopsBefore - requireNotNull(engine.state.view.state(ally)).troops
        }

        assertTrue(losses[0] in 1 until 100, "losses=$losses")
        assertTrue(losses[1] in 1 until 100, "losses=$losses")
        assertEquals(100, losses[2], "losses=$losses")
        assertEquals(0, engine.state.runtime.counter(ally, layerNamespace))
    }

    @Test
    fun `pibingjuyi burn uses fifty percent chance and grows for the same enemy`() {
        fun fixture(randomValue: Int): Pair<DefaultCompleteSkillEngine, SkillBattleContext> {
            val request = BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100791, 100, listOf(200264), position = 2),
                        hero(100001, 90, position = 1),
                    ),
                ),
                defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
                maxRounds = 2,
            )
            val engine = DefaultCompleteSkillEngine.create(request, config)
            val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100791) }
            val context = SkillBattleContext(
                request = request,
                runtime = engine.state.runtime,
                random = FixedBattleRandom(randomValue),
                round = 1,
                source = owner,
                rootSkillId = 0,
                currentSkillId = 0,
                trigger = BattleTrigger.ROUND_START,
                battleView = engine.state.view,
            )
            engine.trigger(BattleTrigger.ROUND_START, context)
            return engine to context
        }

        fun applyHit(
            engine: DefaultCompleteSkillEngine,
            context: SkillBattleContext,
        ) {
            val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
            val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
            val troopsBefore = requireNotNull(engine.state.view.state(ally)).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = ally,
                        amount = 100,
                        troopsAfter = troopsBefore - 100,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.NORMAL,
                        tags = emptySet(),
                        skillId = 0,
                        effectId = 0,
                    ),
                ),
                context.copy(
                    source = enemy,
                    trigger = BattleTrigger.DAMAGE_BEFORE,
                ),
            )
        }

        val (successEngine, successContext) = fixture(0)
        val successOwner = successEngine.state.view.heroes()
            .single { it.heroId == BattleHeroId(100791) }
        val successEnemy = successEngine.state.view.heroes().single { it.side == Side.DEFENDER }
        applyHit(successEngine, successContext)
        val firstBurn = successEngine.state.effectStore.effectsFor(successEnemy)
            .single { it.skillId == 216264 && it.effectId == 305 }
        val growthAfterFirst = successEngine.state.runtime.counter(
            successEnemy,
            "skill.200264.burn-growth",
        )
        assertTrue(growthAfterFirst > 0, "growth=$growthAfterFirst")
        successEngine.trigger(
            BattleTrigger.ACTION_BEFORE,
            successContext.copy(
                source = successEnemy,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        successEngine.finishRound(1)

        successEngine.trigger(
            BattleTrigger.ROUND_START,
            successContext.copy(
                round = 2,
                source = successOwner,
                trigger = BattleTrigger.ROUND_START,
            ),
        )
        applyHit(successEngine, successContext.copy(round = 2))
        val secondBurn = successEngine.state.effectStore.effectsFor(successEnemy)
            .single { it.skillId == 216264 && it.effectId == 305 }
        assertTrue(
            secondBurn.effectiveStrength > firstBurn.effectiveStrength,
            "first=${firstBurn.effectiveStrength} second=${secondBurn.effectiveStrength}",
        )

        val (failedEngine, failedContext) = fixture(99)
        val failedEnemy = failedEngine.state.view.heroes().single { it.side == Side.DEFENDER }
        applyHit(failedEngine, failedContext)
        assertTrue(
            failedEngine.state.effectStore.effectsFor(failedEnemy)
                .none { it.skillId == 216264 && it.effectId == 305 },
        )
    }

    @Test
    fun `marker effects become queryable runtime state for later skill branches`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100017, 100, listOf(200017), position = 2)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.recordTarget(source, target)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200017,
            currentSkillId = 200017,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        engine.trigger(BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertTrue(engine.state.runtime.hasMarker(source, 21001701, round = 1))
    }

    @Test
    fun `huangyi registers emergency recovery without healing during preparation`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100016, 100, listOf(200016), position = 2).copy(
                        troops = 9_000,
                        maxTroops = 10_000,
                    ),
                    hero(100017, 90, position = 1).copy(
                        troops = 9_000,
                        maxTroops = 10_000,
                    ),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100016) }
        engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER }
            .forEach { engine.state.mutable(it).woundedTroops = 1_000 }
        val troopsBefore = engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER }
            .associateWith { requireNotNull(engine.state.view.state(it)).troops }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        assertEquals(
            troopsBefore,
            engine.state.view.heroes()
                .filter { it.side == Side.ATTACKER }
                .associateWith { requireNotNull(engine.state.view.state(it)).troops },
        )
        assertEquals(0, engine.state.runtime.count(source, BattleTrigger.RECOVERY_AFTER))
        engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER }
            .forEach { target ->
                assertTrue(
                    engine.state.effectStore.effectsFor(target).any {
                        it.source == source && it.skillId == 200016 && it.effectId == 401
                    },
                    "missing huangyi emergency-recovery registration for $target",
                )
            }
    }

    @Test
    fun `huangyi heals a registered ally after damage and counts recoveries on liubei`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100016, 100, listOf(200016), position = 2).copy(troops = 9_000),
                    hero(100017, 90, position = 1).copy(troops = 9_000),
                    hero(100018, 80, position = 0).copy(troops = 9_000),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100016) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        val events = engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = ally,
            amount = 600,
            context = context.copy(round = 1, source = enemy),
        )

        assertTrue(
            events.filterIsInstance<BattleEvent.Recovery>().any {
                it.source == source && it.target == ally && it.skillId == 200016 && it.amount > 0
            },
        )
        assertEquals(1, engine.state.runtime.count(source, BattleTrigger.RECOVERY_AFTER))
        assertEquals(0, engine.state.runtime.count(ally, BattleTrigger.RECOVERY_AFTER))
    }

    @Test
    fun `huangyi applies morale to its growing recovery chance`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100016, 100, listOf(200016), position = 2).copy(
                        morale = 119,
                    ),
                    hero(100017, 90, position = 1).copy(
                        troops = 9_000,
                        maxTroops = 10_000,
                        morale = 100,
                    ),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100016) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(52),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val events = engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = ally,
            amount = 100,
            context = context.copy(round = 1, source = enemy),
        )

        assertTrue(
            events.filterIsInstance<BattleEvent.Recovery>().any {
                it.source == source &&
                    it.target == ally &&
                    it.skillId == 200016 &&
                    it.amount > 0
            },
            "events=$events",
        )
    }

    @Test
    fun `huangyi one hundred percent chance ignores low morale`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100016, 100, listOf(200016), position = 2).copy(
                        morale = 1,
                    ),
                    hero(100017, 90, position = 1).copy(
                        troops = 9_000,
                        maxTroops = 10_000,
                    ),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100016)
        }
        val ally = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100017)
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.state.runtime.addCounter(
            owner = source,
            namespace = "skill.200016.successful-rolls",
            delta = 30,
        )

        val events = engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = ally,
            amount = 100,
            context = context.copy(
                random = FixedBattleRandom(99),
                round = 1,
                source = enemy,
            ),
        )

        assertTrue(
            events.filterIsInstance<BattleEvent.Recovery>().any {
                it.source == source &&
                    it.target == ally &&
                    it.skillId == 200016 &&
                    it.amount > 0
            },
            "events=$events",
        )
    }

    @Test
    fun `huangyi increases its chance after every three actual ally recoveries`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100016, 100, listOf(200016), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100016) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val events = buildList {
            repeat(6) {
                addAll(
                    engine.applyNormalDamage(
                        round = it + 1,
                        source = enemy,
                        target = ally,
                        amount = 100,
                        context = context.copy(round = it + 1, source = enemy),
                    ),
                )
            }
        }

        assertEquals(6, engine.state.runtime.count(source, BattleTrigger.RECOVERY_AFTER))
        assertEquals(
            2,
            events.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 211016 },
        )
    }

    @Test
    fun `huangyi does not grow when successful rolls recover no troops`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100016, 100, listOf(200016), position = 2),
                    hero(100252, 90, listOf(200252), position = 1).copy(
                        skillLevels = listOf(10),
                    ),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100016) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100252) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val events = buildList {
            repeat(3) { index ->
                addAll(
                    engine.applyNormalDamage(
                        round = index + 1,
                        source = enemy,
                        target = ally,
                        amount = 100,
                        context = context.copy(round = index + 1, source = enemy),
                    ),
                )
            }
        }

        assertTrue(
            events.filterIsInstance<BattleEvent.Recovery>()
                .none { it.skillId == 200016 },
            "events=$events",
        )
        assertEquals(
            0,
            events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 211016 },
            "events=$events",
        )
    }

    @Test
    fun `taoyuan registers without preparation recovery and heals lowest ally in first four rounds`() {
        val ownerHero = hero(100784, 100, listOf(200784), position = 2).copy(
            skillLevels = listOf(10),
        )
        val lowestHero = hero(100785, 90, position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    ownerHero,
                    lowestHero,
                    hero(100786, 80, position = 0),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 5,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val lowest = engine.state.view.heroes().single { it.heroId == lowestHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        engine.state.mutable(lowest).troops = 6_000
        engine.state.mutable(lowest).woundedTroops = 4_000
        val roundEvents = (1..5).flatMap { round ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(
                    round = round,
                    trigger = BattleTrigger.ROUND_START,
                ),
            )
        }

        assertTrue(
            preparation.none {
                it is BattleEvent.Recovery && it.skillId == 210784
            },
            "preparation=$preparation",
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            roundEvents.filterIsInstance<BattleEvent.Recovery>()
                .filter { it.skillId == 210784 && it.target == lowest }
                .map(BattleEvent.Recovery::round),
            "events=$roundEvents",
        )
    }

    @Test
    fun `bingwuchangshi chooses one two-effect branch on each action`() {
        val ownerHero = hero(100766, 100, listOf(200766), position = 2).copy(
            troops = 9_000,
            maxTroops = 10_000,
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(baseContext)
        engine.state.mutable(owner).woundedTroops = 1_000
        val actions = (0..2).map { branch ->
            engine.trigger(
                BattleTrigger.ACTION_BEFORE,
                baseContext.copy(
                    random = FixedBattleRandom(branch),
                    round = branch + 1,
                    trigger = BattleTrigger.ACTION_BEFORE,
                ),
            )
        }

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillTriggered &&
                    it.skillId in setOf(211766, 212766, 213766)
            },
            "preparation=$preparation",
        )
        assertEquals(
            listOf(211766, 212766, 213766),
            actions.map { events ->
                events.filterIsInstance<BattleEvent.SkillTriggered>()
                    .single { it.skillId in setOf(211766, 212766, 213766) }
                    .skillId
            },
            "actions=$actions",
        )
        assertTrue(
            actions[0].none { it is BattleEvent.Recovery && it.skillId in setOf(212766, 213766) },
            "branch0=${actions[0]}",
        )
        assertTrue(
            actions[1].any { it is BattleEvent.Recovery && it.skillId == 212766 },
            "branch1=${actions[1]}",
        )
        assertTrue(
            actions[2].any { it is BattleEvent.Recovery && it.skillId == 213766 },
            "branch2=${actions[2]}",
        )
    }

    @Test
    fun `jishi registers at preparation and checks independent branches on each action`() {
        fun sequenceRandom(vararg values: Int): BattleRandom {
            val iterator = values.iterator()
            return object : BattleRandom {
                override fun nextInt(bound: Int): Int {
                    check(iterator.hasNext()) { "No deterministic roll left for bound=$bound" }
                    return iterator.next().also { value ->
                        check(value in 0 until bound) {
                            "Deterministic roll $value is outside 0 until $bound"
                        }
                    }
                }
            }
        }

        val ownerHero = hero(100863, 100, listOf(200863), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = sequenceRandom(64, 65),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun hasDefenseIgnore(): Boolean =
            BattleModifier.DefenseIgnorePercent(60, BattleStat.DEFENSE) in
                engine.state.liveHero(owner).modifiers
        fun hasPhysicalDamageIncrease(): Boolean =
            engine.state.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .any { it.school == DamageSchool.PHYSICAL && it.percent == 50 }

        val preparation = engine.prepareBattle(baseContext)
        val preparationSkills = preparation
            .filterIsInstance<BattleEvent.SkillTriggered>()
            .map(BattleEvent.SkillTriggered::skillId)

        assertTrue(200863 in preparationSkills, "preparation=$preparation")
        assertTrue(210863 in preparationSkills, "preparation=$preparation")
        assertTrue(211863 !in preparationSkills, "preparation=$preparation")
        assertTrue(212863 !in preparationSkills, "preparation=$preparation")
        assertTrue(!hasDefenseIgnore(), "preparation=$preparation")
        assertTrue(!hasPhysicalDamageIncrease(), "preparation=$preparation")

        val roundOne = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(round = 1, trigger = BattleTrigger.ACTION_BEFORE),
        )
        val roundOneSkills = roundOne
            .filterIsInstance<BattleEvent.SkillTriggered>()
            .map(BattleEvent.SkillTriggered::skillId)

        assertTrue(210863 in roundOneSkills, "roundOne=$roundOne")
        assertTrue(211863 in roundOneSkills, "roundOne=$roundOne")
        assertTrue(212863 !in roundOneSkills, "roundOne=$roundOne")
        assertTrue(hasDefenseIgnore(), "roundOne=$roundOne")
        assertTrue(!hasPhysicalDamageIncrease(), "roundOne=$roundOne")

        engine.finishRound(1)
        assertTrue(!hasDefenseIgnore())

        val roundTwo = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                random = sequenceRandom(65, 64),
                round = 2,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        val roundTwoSkills = roundTwo
            .filterIsInstance<BattleEvent.SkillTriggered>()
            .map(BattleEvent.SkillTriggered::skillId)

        assertTrue(210863 in roundTwoSkills, "roundTwo=$roundTwo")
        assertTrue(211863 !in roundTwoSkills, "roundTwo=$roundTwo")
        assertTrue(212863 in roundTwoSkills, "roundTwo=$roundTwo")
        assertTrue(!hasDefenseIgnore(), "roundTwo=$roundTwo")
        assertTrue(hasPhysicalDamageIncrease(), "roundTwo=$roundTwo")
    }

    @Test
    fun `xilingkejin delegates attack strategy and recovery to current attribute leaders`() {
        val ownerHero = hero(100824, 100, listOf(200824), position = 0).copy(
            skillLevels = listOf(10),
        )
        val attackHero = hero(100001, 90, position = 1).copy(
            stats = BattleStats(300, 100, 50, 90, 0, 5),
            troops = 9_000,
            maxTroops = 10_000,
        )
        val strategyHero = hero(100002, 80, position = 2).copy(
            stats = BattleStats(50, 100, 300, 80, 0, 5),
            troops = 9_000,
            maxTroops = 10_000,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, attackHero, strategyHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 2).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val attack = engine.state.view.heroes().single { it.heroId == attackHero.id }
        val strategy = engine.state.view.heroes().single { it.heroId == strategyHero.id }
        engine.state.mutable(attack).woundedTroops = 1_000
        engine.state.mutable(strategy).woundedTroops = 1_000
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(baseContext)
        val attackEvents = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                round = 1,
                source = attack,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        val strategyEvents = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                round = 1,
                source = strategy,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage &&
                    it.skillId in setOf(211824, 212824)
            },
            "preparation=$preparation",
        )
        assertTrue(
            attackEvents.any {
                it is BattleEvent.SkillDamage &&
                    it.source == attack &&
                    it.skillId == 211824 &&
                    it.effectId == 301
            },
            "attackEvents=$attackEvents",
        )
        assertTrue(
            attackEvents.any {
                it is BattleEvent.Recovery &&
                    it.source == attack &&
                    it.target == attack &&
                    it.skillId == 211824
            },
            "attackEvents=$attackEvents",
        )
        assertTrue(
            strategyEvents.any {
                it is BattleEvent.SkillDamage &&
                    it.source == strategy &&
                    it.skillId == 212824 &&
                    it.effectId == 302
            },
            "strategyEvents=$strategyEvents",
        )
        assertTrue(
            strategyEvents.any {
                it is BattleEvent.Recovery &&
                    it.source == strategy &&
                    it.target == strategy &&
                    it.skillId == 212824
            },
            "strategyEvents=$strategyEvents",
        )
    }

    @Test
    fun `xuefenduanbing only lowers attack range at round end until one`() {
        val ownerHero = hero(100589, 100, listOf(200258), position = 2).copy(
            stats = BattleStats(attack = 200, defense = 100, strategy = 100, speed = 100, siege = 0, hitRange = 3),
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        assertEquals(3, engine.liveHero(owner).stats.hitRange, "preparation=$preparation")
        assertEquals(
            1,
            preparation.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.rootSkillId == 200258 && it.skillId == 200258 },
            "preparation=$preparation",
        )
        assertTrue(
            preparation.none {
                it is BattleEvent.StatusApplied && it.status == BattleStatus.EVADE
            },
            "preparation=$preparation",
        )

        fun finish(round: Int) {
            engine.trigger(
                BattleTrigger.ROUND_END,
                context.copy(round = round, trigger = BattleTrigger.ROUND_END),
            )
            engine.finishRound(round)
        }

        finish(1)
        assertEquals(2, engine.liveHero(owner).stats.hitRange)
        finish(2)
        assertEquals(1, engine.liveHero(owner).stats.hitRange)
        finish(3)
        assertEquals(1, engine.liveHero(owner).stats.hitRange)
    }

    @Test
    fun `xuefenduanbing starts its double strike after attack range falls to one`() {
        val owner = hero(100589, 100, listOf(200258), position = 2).copy(
            stats = BattleStats(attack = 200, defense = 100, strategy = 100, speed = 100, siege = 0, hitRange = 2),
            skillLevels = listOf(10),
        )
        val target = hero(200001, 10, position = 2).copy(
            troops = 100_000,
            maxTroops = 100_000,
        )

        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(owner)),
                defender = BattleTeam(listOf(target)),
                maxRounds = 2,
            ),
            config,
            FixedBattleRandom(0),
        )
        val doubleStrikes = result.events.filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.source.heroId == owner.id && it.skillId == 211258 }

        assertEquals(
            listOf(2, 2),
            doubleStrikes.map(BattleEvent.SkillDamage::round),
            "doubleStrikes=$doubleStrikes events=${result.events}",
        )
    }

    @Test
    fun `xuefenduanbing shakes only enemies outside its reduced attack range`() {
        val owner = hero(100589, 100, listOf(200258), position = 2).copy(
            stats = BattleStats(attack = 200, defense = 100, strategy = 100, speed = 100, siege = 0, hitRange = 2),
            skillLevels = listOf(10),
        )
        val enemyBase = hero(200001, 10, position = 0).copy(
            troops = 100_000,
            maxTroops = 100_000,
        )
        val enemyFront = hero(200002, 20, position = 2).copy(
            troops = 100_000,
            maxTroops = 100_000,
        )

        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(owner)),
                defender = BattleTeam(listOf(enemyBase, enemyFront)),
                maxRounds = 2,
            ),
            config,
            FixedBattleRandom(0),
        )
        val shakes = result.events.filterIsInstance<BattleEvent.StatusApplied>()
            .filter {
                it.round == 2 &&
                    it.source.heroId == owner.id &&
                    it.skillId == 212258 &&
                    it.status == BattleStatus.SHAKE
            }

        assertEquals(
            listOf(enemyBase.id),
            shakes.map { it.target.heroId },
            "shakes=$shakes events=${result.events}",
        )
    }

    @Test
    fun `xuefenduanbing grants evade after hurt only while attack range exceeds one`() {
        val ownerHero = hero(100589, 100, listOf(200258), position = 2).copy(
            stats = BattleStats(attack = 200, defense = 100, strategy = 100, speed = 100, siege = 0, hitRange = 2),
            skillLevels = listOf(10),
        )
        val enemyHero = hero(200001, 10, position = 2)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(enemyHero)),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.heroId == enemyHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val beforeReduction = engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = owner,
            amount = 100,
            context = context.copy(round = 1, source = enemy),
        )
        engine.trigger(
            BattleTrigger.ROUND_END,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_END),
        )
        engine.finishRound(1)
        val afterReduction = engine.applyNormalDamage(
            round = 2,
            source = enemy,
            target = owner,
            amount = 100,
            context = context.copy(round = 2, source = enemy),
        )

        assertEquals(
            1,
            beforeReduction.filterIsInstance<BattleEvent.StatusApplied>()
                .count { it.skillId == 214258 && it.status == BattleStatus.EVADE },
            "beforeReduction=$beforeReduction",
        )
        assertTrue(
            afterReduction.filterIsInstance<BattleEvent.StatusApplied>()
                .none { it.skillId == 214258 && it.status == BattleStatus.EVADE },
            "afterReduction=$afterReduction",
        )
    }

    @Test
    fun `jingguanleizhong selects one extra damage school without self recursion`() {
        val ownerHero = hero(100630, 100, listOf(200898), position = 2).copy(
            skillLevels = listOf(10),
        )
        val targetHero = hero(200001, 10, position = 2).copy(
            troops = 100_000,
            maxTroops = 100_000,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(targetHero)),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val target = engine.state.view.heroes().single { it.heroId == targetHero.id }
        var randomIndex = 0
        val random = object : BattleRandom {
            private val values = intArrayOf(0, 1)

            override fun nextInt(bound: Int): Int =
                values.getOrElse(randomIndex++) {
                    error("Unexpected jingguanleizhong roll for bound=$bound")
                }.coerceIn(0, bound - 1)
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = random,
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        val preparation = engine.prepareBattle(context)
        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage && it.skillId == 211898
            },
            "preparation=$preparation",
        )
        assertEquals(0, randomIndex, "preparation=$preparation")

        val events = engine.applyNormalDamage(
            round = 1,
            source = owner,
            target = target,
            amount = 100,
            context = context.copy(round = 1, trigger = BattleTrigger.DAMAGE_AFTER),
        )
        val extraDamage = events.filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.source == owner && it.skillId == 211898 }

        assertEquals(1, extraDamage.size, "extraDamage=$extraDamage events=$events")
        assertEquals(302, extraDamage.single().effectId)
    }

    @Test
    fun `yongzhigangyi routes physical and strategy hurt to their reaction skills`() {
        fun reaction(school: DamageSchool): Pair<List<BattleEvent>, BattleHeroRef> {
            val ownerHero = hero(100630, 100, listOf(200288), position = 2).copy(
                skillLevels = listOf(10),
            )
            val enemyHero = hero(200001, 10, position = 2).copy(
                troops = 100_000,
                maxTroops = 100_000,
            )
            val request = BattleRequest(
                attacker = BattleTeam(listOf(ownerHero)),
                defender = BattleTeam(listOf(enemyHero)),
                maxRounds = 1,
            )
            val engine = DefaultCompleteSkillEngine.create(request, config)
            val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
            val enemy = engine.state.view.heroes().single { it.heroId == enemyHero.id }
            val context = SkillBattleContext(
                request = request,
                runtime = engine.state.runtime,
                random = FixedBattleRandom(0),
                round = 0,
                source = owner,
                rootSkillId = 0,
                currentSkillId = 0,
                trigger = BattleTrigger.BATTLE_PASSIVE,
                battleView = engine.state.view,
            )
            val preparation = engine.prepareBattle(context)
            assertTrue(
                preparation.none {
                    it is BattleEvent.SkillDamage && it.skillId in setOf(210288, 211288)
                },
                "preparation=$preparation",
            )
            val events = engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = owner,
                        amount = 100,
                        troopsAfter = 9_900,
                        school = school,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = if (school == DamageSchool.PHYSICAL) 301 else 302,
                    ),
                ),
                context.copy(round = 1, source = enemy, trigger = BattleTrigger.DAMAGE_AFTER),
            )
            return events to owner
        }

        val (physicalEvents, physicalOwner) = reaction(DamageSchool.PHYSICAL)
        val (strategyEvents, strategyOwner) = reaction(DamageSchool.STRATEGY)

        assertEquals(
            listOf(210288),
            physicalEvents.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.source == physicalOwner }
                .map(BattleEvent.SkillDamage::skillId),
            "physicalEvents=$physicalEvents",
        )
        assertEquals(
            listOf(211288),
            strategyEvents.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.source == strategyOwner }
                .map(BattleEvent.SkillDamage::skillId),
            "strategyEvents=$strategyEvents",
        )
    }

    @Test
    fun `yongzhigangyi adds one layer at each strict troop threshold`() {
        val ownerHero = hero(100630, 100, listOf(200288), position = 2).copy(
            skillLevels = listOf(10),
        )
        val enemyHero = hero(200001, 10, position = 2).copy(
            troops = 100_000,
            maxTroops = 100_000,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(enemyHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.heroId == enemyHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(99),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        fun damageTo(troops: Int) {
            val current = requireNotNull(engine.state.view.state(owner)).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = owner,
                        amount = current - troops,
                        troopsAfter = troops,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.NORMAL,
                        tags = emptySet(),
                        skillId = 0,
                        effectId = 301,
                    ),
                ),
                context.copy(round = 1, source = enemy, trigger = BattleTrigger.DAMAGE_AFTER),
            )
        }
        fun layers(detailId: Int): Int =
            engine.state.effectStore.effectsFor(owner)
                .singleOrNull { it.detailId == detailId }
                ?.stacks
                ?: 0
        fun recoveryTakenPercent(): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.RecoveryTakenPercent>()
                .sumOf(BattleModifier.RecoveryTakenPercent::percent)

        damageTo(9_000)
        assertEquals(0, layers(21228801))
        assertEquals(0, layers(21228802))

        damageTo(8_999)
        assertEquals(1, layers(21228801))
        assertEquals(1, layers(21228802))

        damageTo(7_999)
        assertEquals(2, layers(21228801))
        assertEquals(2, layers(21228802))
        assertEquals(
            engine.state.effectStore.effectsFor(owner)
                .single { it.detailId == 21228802 }
                .effectiveStrength,
            recoveryTakenPercent(),
        )

        damageTo(7_000)
        assertEquals(2, layers(21228801))
        assertEquals(2, layers(21228802))

        damageTo(6_999)
        assertEquals(3, layers(21228801))
        assertEquals(3, layers(21228802))

        damageTo(6_000)
        assertEquals(3, layers(21228801))
        assertEquals(3, layers(21228802))

        damageTo(5_999)
        assertEquals(4, layers(21228801))
        assertEquals(4, layers(21228802))

        damageTo(5_000)
        assertEquals(4, layers(21228801))
        assertEquals(4, layers(21228802))
    }

    @Test
    fun `xixiangwugong triggers its registered allies before their second round actions`() {
        val ownerHero = hero(100791, 100, listOf(200791), position = 2).copy(
            skillLevels = listOf(10),
        )
        val allyHero = hero(100001, 90, position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, allyHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 1).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                    hero(200002, 10, position = 2).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val ally = engine.state.view.heroes().single { it.heroId == allyHero.id }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(baseContext)
        val roundOne = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                round = 1,
                source = ally,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        engine.state.mutable(owner).troops = 0
        val roundTwo = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                round = 2,
                source = ally,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage && it.skillId == 210791
            },
            "preparation=$preparation",
        )
        assertTrue(
            roundOne.none {
                it is BattleEvent.SkillDamage && it.skillId == 210791
            },
            "roundOne=$roundOne",
        )
        assertEquals(
            2,
            roundTwo.filterIsInstance<BattleEvent.SkillDamage>()
                .count {
                    it.source == ally &&
                        it.skillId == 210791 &&
                        it.effectId == 302
                },
            "roundTwo=$roundTwo",
        )
        assertTrue(
            engine.state.liveHero(ally).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .any {
                    it.school == DamageSchool.PHYSICAL &&
                        it.percent < 0
                },
        )
        assertTrue(
            engine.state.liveHero(ally).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .any {
                    it.school == DamageSchool.STRATEGY &&
                        it.percent < 0
                },
        )
    }

    @Test
    fun `kuihouxiangta registers split attack and casts strategy damage before each action`() {
        val ownerHero = hero(100772, 100, listOf(200772), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 2).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(baseContext)
        val actionEvents = (1..2).map { round ->
            engine.trigger(
                BattleTrigger.ACTION_BEFORE,
                baseContext.copy(
                    round = round,
                    trigger = BattleTrigger.ACTION_BEFORE,
                ),
            )
        }

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage && it.skillId == 210772
            },
            "preparation=$preparation",
        )
        assertTrue(engine.permissionFor(owner, baseContext).secondaryAttack)
        assertEquals(
            listOf(1, 2),
            actionEvents.flatten()
                .filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.source == owner && it.skillId == 210772 }
                .map(BattleEvent.SkillDamage::round),
            "actionEvents=$actionEvents",
        )
    }

    @Test
    fun `sanjunqichu rolls its temporary split attack before each action`() {
        val ownerHero = hero(100956, 100, listOf(200956), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(baseContext)
        val successfulAction = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                round = 1,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        engine.finishRound(1)
        val failedAction = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                random = FixedBattleRandom(99),
                round = 2,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillTriggered && it.skillId == 210956
            },
            "preparation=$preparation",
        )
        assertTrue(
            successfulAction.any {
                it is BattleEvent.SkillTriggered &&
                    it.round == 1 &&
                    it.source == owner &&
                    it.skillId == 210956
            },
            "successfulAction=$successfulAction",
        )
        assertTrue(engine.permissionFor(owner, baseContext).secondaryAttack.not())
        assertTrue(
            failedAction.none {
                it is BattleEvent.SkillTriggered && it.skillId == 210956
            },
            "failedAction=$failedAction",
        )
    }

    @Test
    fun `tongjunweishen rerolls opposing probability curves for each ally every round`() {
        val ownerHero = hero(100915, 300, listOf(200915), position = 2).copy(
            skillLevels = listOf(10),
        )
        val attackAlly = hero(100001, 200, position = 1).copy(
            stats = BattleStats(
                attack = 200,
                defense = 100,
                strategy = 100,
                speed = 200,
                siege = 0,
                hitRange = 5,
            ),
        )
        val strategyAlly = hero(100002, 100, position = 0).copy(
            stats = BattleStats(
                attack = 100,
                defense = 100,
                strategy = 200,
                speed = 100,
                siege = 0,
                hitRange = 5,
            ),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, attackAlly, strategyAlly)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100915)
        }
        val attackAllyRef = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100001)
        }
        val strategyAllyRef = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100002)
        }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(79),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        fun tongjunDetails(target: BattleHeroRef): Set<Int> =
            engine.state.effectStore.effectsFor(target)
                .filter {
                    it.source == owner &&
                        it.detailId in setOf(
                            21091501,
                            21091502,
                            21091503,
                            21091504,
                        )
                }
                .mapTo(linkedSetOf()) { it.detailId }

        val preparation = engine.prepareBattle(baseContext)

        assertTrue(tongjunDetails(attackAllyRef).isEmpty(), "preparation=$preparation")
        assertTrue(tongjunDetails(strategyAllyRef).isEmpty(), "preparation=$preparation")

        val roundOneEvents = engine.trigger(
            BattleTrigger.ROUND_START,
            baseContext.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            setOf(21091501),
            tongjunDetails(attackAllyRef),
            "preparation=$preparation roundOne=$roundOneEvents",
        )
        assertEquals(
            setOf(21091502),
            tongjunDetails(strategyAllyRef),
            "preparation=$preparation roundOne=$roundOneEvents",
        )

        engine.finishRound(1)
        assertTrue(tongjunDetails(attackAllyRef).isEmpty())
        assertTrue(tongjunDetails(strategyAllyRef).isEmpty())

        engine.trigger(
            BattleTrigger.ROUND_START,
            baseContext.copy(
                random = FixedBattleRandom(39),
                round = 6,
                trigger = BattleTrigger.ROUND_START,
            ),
        )

        assertEquals(setOf(21091503), tongjunDetails(attackAllyRef))
        assertEquals(setOf(21091504), tongjunDetails(strategyAllyRef))
    }

    @Test
    fun `qibingjubei accumulates failed action chance and resets after delegated attacks`() {
        val ownerHero = hero(100930, 100, listOf(200930), position = 2).copy(
            skillLevels = listOf(10),
        )
        val fastestAlly = hero(100001, 200, position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, fastestAlly)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 0).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                    hero(200002, 20, position = 1).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                    hero(200003, 10, position = 2).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100930)
        }
        val ally = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100001)
        }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(99),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(baseContext)
        val firstFailure = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(round = 1, trigger = BattleTrigger.ACTION_BEFORE),
        )
        val success = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                random = FixedBattleRandom(34),
                round = 2,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        val afterReset = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                random = FixedBattleRandom(30),
                round = 3,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        val damage = success.filterIsInstance<BattleEvent.SkillDamage>()

        assertTrue(preparation.none { it is BattleEvent.SkillDamage })
        assertTrue(firstFailure.none { it is BattleEvent.SkillDamage })
        assertEquals(4, damage.size, "success=$success")
        assertEquals(
            setOf(0, 1),
            damage.filter { it.source == owner && it.skillId == 210930 }
                .mapTo(linkedSetOf()) { it.target.position },
        )
        assertEquals(
            setOf(0, 1),
            damage.filter { it.source == ally && it.skillId == 211930 }
                .mapTo(linkedSetOf()) { it.target.position },
        )
        assertTrue(afterReset.none { it is BattleEvent.SkillDamage }, "afterReset=$afterReset")
    }

    @Test
    fun `jixian stacks both damage taken schools on each allied normal attack target`() {
        fun rangedHero(
            id: Int,
            speed: Int,
            skills: List<Int>,
            position: Int,
            range: Int,
        ): BattleHero =
            hero(id, speed, skills, position).copy(
                stats = hero(id, speed, skills, position).stats.copy(hitRange = range),
            )
        val ownerHero = rangedHero(100248, 300, listOf(200248), 0, 1).copy(
            skillLevels = listOf(10),
        )
        val actorHero = rangedHero(100001, 200, emptyList(), 1, 2)
        val thirdHero = rangedHero(100002, 100, emptyList(), 2, 3)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, actorHero, thirdHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100248)
        }
        val actor = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100001)
        }
        val target = engine.state.view.heroes().single {
            it.side == Side.DEFENDER
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        fun damageTaken(school: DamageSchool): Int =
            engine.state.liveHero(target).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageTakenPercent::percent)

        engine.prepareBattle(context)
        engine.recordTarget(actor, target)
        repeat(2) {
            engine.trigger(
                BattleTrigger.NORMAL_ATTACK_AFTER,
                context.copy(
                    round = 1,
                    source = actor,
                    trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                ),
            )
        }

        assertEquals(30, damageTaken(DamageSchool.PHYSICAL))
        assertEquals(30, damageTaken(DamageSchool.STRATEGY))

        engine.finishRound(1)
        assertEquals(0, damageTaken(DamageSchool.PHYSICAL))
        assertEquals(0, damageTaken(DamageSchool.STRATEGY))
    }

    @Test
    fun `jiangxin layers panic burn and hex across their configured start rounds`() {
        val ownerHero = hero(100020, 100, listOf(200020), position = 0).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 0),
                    hero(200002, 20, position = 1),
                    hero(200003, 10, position = 2),
                ),
            ),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        val roundEvents = (1..8).flatMap { round ->
            buildList {
                addAll(
                    engine.trigger(
                        BattleTrigger.ROUND_START,
                        context.copy(
                            round = round,
                            trigger = BattleTrigger.ROUND_START,
                        ),
                    ),
                )
                engine.state.view.heroes()
                    .filter { it.side == Side.DEFENDER }
                    .forEach { target ->
                        addAll(
                            engine.trigger(
                                BattleTrigger.ACTION_BEFORE,
                                context.copy(
                                    round = round,
                                    source = target,
                                    trigger = BattleTrigger.ACTION_BEFORE,
                                ),
                            ),
                        )
                    }
                addAll(engine.finishRound(round))
            }
        }
        val ongoing = roundEvents.filterIsInstance<BattleEvent.OngoingDamage>()
            .filter { it.skillId == 200020 }

        assertTrue(
            preparation.none { it is BattleEvent.OngoingDamage && it.skillId == 200020 },
            "preparation=$preparation",
        )
        assertEquals(
            mapOf(
                BattleStatus.PANIC to 24,
                BattleStatus.BURN to 18,
                BattleStatus.HEX to 12,
            ),
            ongoing.groupingBy(BattleEvent.OngoingDamage::status).eachCount(),
            "events=$ongoing",
        )
    }

    @Test
    fun `command immunity blocks jiangxin ongoing damage only for its holder`() {
        val ownerHero = hero(100020, 100, listOf(200020), position = 0).copy(
            skillLevels = listOf(10),
        )
        val immuneHero = hero(200001, 30, listOf(296335), position = 0)
        val vulnerableHero = hero(200002, 20, position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(immuneHero, vulnerableHero)),
            maxRounds = 5,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val immune = engine.state.view.heroes().single {
            it.heroId == immuneHero.id
        }
        val vulnerable = engine.state.view.heroes().single {
            it.heroId == vulnerableHero.id
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)
        assertTrue(
            engine.state.effectStore.effectsFor(immune).any { it.effectId == 121 },
            "command immunity was not registered for $immune",
        )
        val ongoing = (1..5).flatMap { round ->
            buildList {
                addAll(
                    engine.trigger(
                        BattleTrigger.ROUND_START,
                        context.copy(
                            round = round,
                            trigger = BattleTrigger.ROUND_START,
                        ),
                    ),
                )
                listOf(immune, vulnerable).forEach { target ->
                    addAll(
                        engine.trigger(
                            BattleTrigger.ACTION_BEFORE,
                            context.copy(
                                round = round,
                                source = target,
                                trigger = BattleTrigger.ACTION_BEFORE,
                            ),
                        ),
                    )
                }
                addAll(engine.finishRound(round))
            }
        }.filterIsInstance<BattleEvent.OngoingDamage>()
            .filter { it.skillId == 200020 }

        assertTrue(
            ongoing.none { it.target == immune },
            "command-immune target received jiangxin damage: $ongoing",
        )
        assertTrue(
            ongoing.any { it.target == vulnerable },
            "vulnerable target did not receive jiangxin damage: $ongoing",
        )
    }

    @Test
    fun `chuangyi reduces only the matching damage reduction by one twelfth after hurt`() {
        val ownerHero = hero(100843, 100, listOf(200843), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 200, position = 2))),
            defender = BattleTeam(listOf(ownerHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val owner = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )
        fun reduction(school: DamageSchool): Int =
            engine.state.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageTakenPercent::percent)
        fun hurt(school: DamageSchool): List<BattleEvent> {
            val troops = requireNotNull(engine.state.view.state(owner)).troops
            return engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = source,
                        target = owner,
                        amount = 100,
                        troopsAfter = troops - 100,
                        school = school,
                        origin = if (school == DamageSchool.PHYSICAL) {
                            DamageOrigin.NORMAL
                        } else {
                            DamageOrigin.ACTIVE
                        },
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = if (school == DamageSchool.PHYSICAL) 301 else 302,
                    ),
                ),
                context,
            )
        }

        engine.prepareBattle(context.copy(round = 0, trigger = BattleTrigger.BATTLE_PASSIVE))
        assertEquals(-84, reduction(DamageSchool.PHYSICAL))
        assertEquals(-84, reduction(DamageSchool.STRATEGY))

        val physicalEvents = hurt(DamageSchool.PHYSICAL)
        assertEquals(
            -77,
            reduction(DamageSchool.PHYSICAL),
            "events=$physicalEvents effects=${engine.state.effectStore.effectsFor(owner)}",
        )
        assertEquals(-84, reduction(DamageSchool.STRATEGY))

        hurt(DamageSchool.STRATEGY)
        assertEquals(-77, reduction(DamageSchool.PHYSICAL))
        assertEquals(-77, reduction(DamageSchool.STRATEGY))
    }

    @Test
    fun `chuangyi guard redirects the first allied normal attack target to its owner`() {
        val attacker = hero(200001, 300, position = 2)
        val guard = hero(100843, 20, listOf(200843), position = 1).copy(
            skillLevels = listOf(10),
        )
        val guarded = hero(100002, 10, position = 2)

        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(attacker)),
                defender = BattleTeam(listOf(guard, guarded)),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )
        val firstAttack = result.events.filterIsInstance<BattleEvent.NormalAttack>()
            .first { it.source.heroId == attacker.id }

        assertEquals(guard.id, firstAttack.target.heroId, "events=${result.events}")
    }

    @Test
    fun `bubuweiying adds one damage reduction layer at every round start`() {
        val ownerHero = hero(100644, 100, listOf(200644), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun reduction(school: DamageSchool): Int =
            engine.state.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageTakenPercent::percent)

        engine.prepareBattle(context)
        assertEquals(-11, reduction(DamageSchool.PHYSICAL))
        assertEquals(-11, reduction(DamageSchool.STRATEGY))

        val byRound = (1..8).map { round ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(
                    round = round,
                    trigger = BattleTrigger.ROUND_START,
                ),
            )
            reduction(DamageSchool.PHYSICAL) to reduction(DamageSchool.STRATEGY)
        }

        assertEquals(
            (2..9).map { layers -> -11 * layers to -11 * layers },
            byRound,
        )
    }

    @Test
    fun `round stacking passives add one physical or strategy damage layer each round`() {
        data class Case(
            val skillId: Int,
            val school: DamageSchool,
            val layerStrength: Int,
        )
        listOf(
            Case(200643, DamageSchool.PHYSICAL, 10),
            Case(200645, DamageSchool.STRATEGY, 11),
        ).forEach { case ->
            val ownerHero = hero(
                100000 + case.skillId,
                100,
                listOf(case.skillId),
                position = 1,
            ).copy(skillLevels = listOf(10))
            val request = BattleRequest(
                attacker = BattleTeam(listOf(ownerHero)),
                defender = BattleTeam(listOf(hero(200001, 10))),
                maxRounds = 8,
            )
            val engine = DefaultCompleteSkillEngine.create(request, config)
            val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
            val context = SkillBattleContext(
                request = request,
                runtime = engine.state.runtime,
                random = FixedBattleRandom(0),
                round = 0,
                source = owner,
                rootSkillId = 0,
                currentSkillId = 0,
                trigger = BattleTrigger.BATTLE_PASSIVE,
                battleView = engine.state.view,
            )
            fun increase(): Int =
                engine.state.liveHero(owner).modifiers
                    .filterIsInstance<BattleModifier.DamageDealtPercent>()
                    .filter { it.school == case.school }
                    .sumOf(BattleModifier.DamageDealtPercent::percent)

            engine.prepareBattle(context)
            assertEquals(case.layerStrength, increase(), "skill=${case.skillId}")
            val byRound = (1..8).map { round ->
                engine.trigger(
                    BattleTrigger.ROUND_START,
                    context.copy(
                        round = round,
                        trigger = BattleTrigger.ROUND_START,
                    ),
                )
                increase()
            }

            assertEquals(
                (2..9).map { layers -> case.layerStrength * layers },
                byRound,
                "skill=${case.skillId}",
            )
        }
    }

    @Test
    fun `jiufazhongyuan registers initial layers without preparation damage`() {
        val ownerHero = hero(100806, 100, listOf(200290), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)

        listOf(20029002, 20029003).forEach { detailId ->
            assertEquals(
                1,
                engine.state.effectStore.effectsFor(owner)
                    .single { it.detailId == detailId }
                    .stacks,
                "detailId=$detailId preparation=$preparation",
            )
        }
        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage && it.skillId == 210290
            },
            "preparation=$preparation",
        )
    }

    @Test
    fun `jiufazhongyuan adds one physical and strategy damage layer each round`() {
        val ownerHero = hero(100806, 100, listOf(200290), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun increase(school: DamageSchool): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageDealtPercent::percent)

        engine.prepareBattle(context)
        val initial = increase(DamageSchool.PHYSICAL) to increase(DamageSchool.STRATEGY)
        val byRound = (1..8).map { round ->
            val roundContext = context.copy(
                round = round,
                trigger = BattleTrigger.ROUND_START,
            )
            engine.trigger(BattleTrigger.ROUND_START, roundContext)
            engine.trigger(BattleTrigger.ROUND_START, roundContext)
            increase(DamageSchool.PHYSICAL) to increase(DamageSchool.STRATEGY)
        }

        assertTrue(initial.first > 0, "initial=$initial")
        assertEquals(initial.first, initial.second)
        assertEquals(
            (1..8).map { layers ->
                initial.first * layers to initial.second * layers
            },
            byRound,
        )
    }

    @Test
    fun `jiufazhongyuan responds to successful active skills at most nine times`() {
        val ownerHero = hero(
            100806,
            100,
            listOf(200290, 200070),
            position = 1,
        ).copy(skillLevels = listOf(10, 10))
        val targetHero = hero(200001, 10).copy(
            troops = 1_000_000,
            maxTroops = 1_000_000,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(targetHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val responses = (1..10).map { round ->
            engine.trigger(
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                context.copy(
                    round = round,
                    trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                ),
            ).filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source == owner && it.skillId == 210290 }
        }

        assertEquals(List(9) { 1 } + 0, responses)
    }

    @Test
    fun `round stacking passive adds only one layer when round start repeats`() {
        val ownerHero = hero(300643, 100, listOf(200643), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun increase(): Int =
            engine.state.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageDealtPercent::percent)

        engine.prepareBattle(context)
        val roundOne = context.copy(round = 1, trigger = BattleTrigger.ROUND_START)
        engine.trigger(BattleTrigger.ROUND_START, roundOne)
        val afterFirst = increase()
        engine.trigger(BattleTrigger.ROUND_START, roundOne)

        assertEquals(20, afterFirst)
        assertEquals(20, increase())
    }

    @Test
    fun `round stacking passive does not exceed its layer cap after max rounds`() {
        val ownerHero = hero(300645, 100, listOf(200645), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun increase(): Int =
            engine.state.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageDealtPercent::percent)

        engine.prepareBattle(context)
        val byRound = (1..3).map { round ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(round = round, trigger = BattleTrigger.ROUND_START),
            )
            increase()
        }

        assertEquals(listOf(22, 33, 33), byRound)
    }

    @Test
    fun `round stacking passive ignores non-positive rounds and dead owners`() {
        val ownerHero = hero(300644, 100, listOf(200644), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 200))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun reductions(): Pair<Int, Int> {
            val modifiers = engine.state.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
            return modifiers
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageTakenPercent::percent) to
                modifiers
                    .filter { it.school == DamageSchool.STRATEGY }
                    .sumOf(BattleModifier.DamageTakenPercent::percent)
        }

        engine.prepareBattle(context)
        engine.trigger(BattleTrigger.ROUND_START, context.copy(trigger = BattleTrigger.ROUND_START))
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = -1, trigger = BattleTrigger.ROUND_START),
        )
        assertEquals(-11 to -11, reductions())

        engine.state.mutable(owner).troops = 0
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(-11 to -11, reductions())
    }

    @Test
    fun `round stacking passive without active effects does not consume the round`() {
        val ownerHero = hero(300644, 100, listOf(200644), position = 1).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun reductions(): Pair<Int, Int> {
            val modifiers = engine.state.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
            return modifiers
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageTakenPercent::percent) to
                modifiers
                    .filter { it.school == DamageSchool.STRATEGY }
                    .sumOf(BattleModifier.DamageTakenPercent::percent)
        }

        val roundOne = context.copy(round = 1, trigger = BattleTrigger.ROUND_START)
        engine.trigger(BattleTrigger.ROUND_START, roundOne)
        assertEquals(0 to 0, reductions())

        engine.prepareBattle(context)
        assertEquals(-11 to -11, reductions())
        engine.trigger(BattleTrigger.ROUND_START, roundOne)

        assertEquals(-22 to -22, reductions())
    }

    @Test
    fun `baizhan spends one initial stack for round start recovery`() {
        val ownerHero = hero(100252, 200, listOf(200252), position = 1).copy(
            troops = 9_000,
            maxTroops = 10_000,
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)
        engine.state.mutable(owner).woundedTroops = 1_000
        val events = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(2, engine.state.runtime.counter(owner, "skill.200252.stacks"))
        assertTrue(events.any {
            it is BattleEvent.Recovery &&
                it.source == owner &&
                it.target == owner &&
                it.skillId == 214252 &&
                it.amount > 0
        })
    }

    @Test
    fun `baizhan spends one stack and recovers after receiving damage`() {
        val ownerHero = hero(100252, 200, listOf(200252), position = 1).copy(
            troops = 9_000,
            maxTroops = 10_000,
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val events = engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = owner,
            amount = 100,
            context = context.copy(round = 1, source = enemy),
        )

        assertEquals(2, engine.state.runtime.counter(owner, "skill.200252.stacks"))
        assertTrue(events.any {
            it is BattleEvent.Recovery &&
                it.source == owner &&
                it.target == owner &&
                it.skillId == 214252 &&
                it.amount > 0
        })
    }

    @Test
    fun `baizhan replenishes one stack after dealing damage up to three`() {
        val ownerHero = hero(100252, 200, listOf(200252), position = 1).copy(
            troops = 9_000,
            maxTroops = 10_000,
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.state.mutable(owner).woundedTroops = 1_000
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        repeat(2) {
            engine.applyNormalDamage(
                round = 1,
                source = owner,
                target = enemy,
                amount = 1,
                context = context.copy(round = 1, source = owner),
            )
        }

        assertEquals(3, engine.state.runtime.counter(owner, "skill.200252.stacks"))
    }

    @Test
    fun `manghou registers without preparation damage and retaliates after hurt`() {
        val ownerHero = hero(100770, 200, listOf(200770), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 1),
                    hero(200002, 10, position = 2),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().first { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        val retaliation = engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = owner,
            amount = 100,
            context = context.copy(round = 1, source = enemy),
        )

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage && it.skillId == 210770
            },
            "preparation=$preparation",
        )
        assertEquals(
            2,
            retaliation.filterIsInstance<BattleEvent.SkillDamage>()
                .count { it.source == owner && it.skillId == 210770 },
            "retaliation=$retaliation",
        )
    }

    @Test
    fun `sheshen registers without preparation damage and retaliates against damage source`() {
        val ownerHero = hero(100993, 200, listOf(200993), position = 2).copy(
            skillLevels = listOf(10),
        )
        val damageSourceHero = hero(200001, 20, position = 2)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200002, 10, position = 0),
                    damageSourceHero,
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val damageSource = engine.state.view.heroes().single {
            it.heroId == damageSourceHero.id
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        val retaliation = engine.applyNormalDamage(
            round = 1,
            source = damageSource,
            target = owner,
            amount = 100,
            context = context.copy(round = 1, source = damageSource),
        )

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage && it.skillId == 212993
            },
            "preparation=$preparation",
        )
        assertEquals(
            listOf(damageSource),
            retaliation.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.source == owner && it.skillId == 212993 }
                .map(BattleEvent.SkillDamage::target),
            "retaliation=$retaliation",
        )
    }

    @Test
    fun `bingzhe listener fires once after three active or pursuit attempts`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(
                        100253,
                        100,
                        listOf(200253, 200001, 200002, 200251),
                        position = 2,
                    ),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val base = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        val events = engine.trigger(BattleTrigger.ACTIVE_SKILL_ATTEMPT, base) +
            engine.trigger(
                BattleTrigger.PURSUIT_ATTEMPT,
                base.copy(trigger = BattleTrigger.PURSUIT_ATTEMPT),
            )

        assertEquals(3, engine.state.runtime.attemptCount(source, BattleTrigger.ACTIVE_SKILL_ATTEMPT) +
            engine.state.runtime.attemptCount(source, BattleTrigger.PURSUIT_ATTEMPT))
        assertEquals(
            1,
            events.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 211253 },
        )
    }

    @Test
    fun `leishi registers child listeners without applying runtime effects during preparation`() {
        val ownerHero = hero(100030, 100, listOf(200900), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100001, 90, position = 0),
                    ownerHero,
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val events = engine.prepareBattle(context)

        assertEquals(
            setOf(211900, 212900, 213900),
            events.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.rootSkillId == 200900 && it.skillId != 200900 }
                .map(BattleEvent.SkillTriggered::skillId)
                .toSet(),
        )
        assertTrue(
            engine.state.view.heroes().all { target ->
                engine.state.view.activeEffectIds(target).none { it in setOf(504, 714) }
            },
        )
    }

    @Test
    fun `leishi rolls allied guard once at the start of each round`() {
        val ownerHero = hero(100030, 100, listOf(200900), position = 1)
            .copy(skillLevels = listOf(10))
        val allyHero = hero(100001, 90, position = 0)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(allyHero, ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val ally = engine.state.view.heroes().single { it.heroId == allyHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val first = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )
        val repeated = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            listOf(213900, 210900),
            first.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.rootSkillId == 200900 }
                .map(BattleEvent.SkillTriggered::skillId),
        )
        assertTrue(504 in engine.state.view.activeEffectIds(ally))
        assertTrue(
            repeated.none {
                it is BattleEvent.SkillTriggered && it.skillId == 213900
            },
        )
    }

    @Test
    fun `leishi waits for its owners round start hook`() {
        val ownerHero = hero(100030, 50, listOf(200900), position = 1)
            .copy(skillLevels = listOf(10))
        val fasterHero = hero(100001, 100, position = 0)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(fasterHero, ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val faster = engine.state.view.heroes().single { it.heroId == fasterHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val beforeOwner = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(
                round = 1,
                source = faster,
                trigger = BattleTrigger.ROUND_START,
            ),
        )
        val atOwner = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertTrue(
            beforeOwner.none {
                it is BattleEvent.SkillTriggered && it.skillId == 213900
            },
        )
        assertTrue(
            atOwner.any {
                it is BattleEvent.SkillTriggered && it.skillId == 213900
            },
        )
    }

    @Test
    fun `leishi responds to normal attack damage and cleanses only active pursuit harm`() {
        val ownerHero = hero(100030, 100, listOf(200900), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 1))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun harmfulEffect(
            skillId: Int,
            skillKind: SkillKind,
            rawSkillType: Int,
            effectId: Int,
        ) = ApplyBattleEffectChange(
            PersistentEffectSpec(
                source = enemy,
                target = owner,
                rootSkillId = skillId,
                skillId = skillId,
                skillKind = skillKind,
                rawSkillType = rawSkillType,
                detailId = skillId * 100 + 1,
                effectId = effectId,
                category = com.stzb.battle.core.EffectCategory.HARMFUL,
                conflict = effectId,
                replaceType = 0,
                bindFlag = 0,
                maxStacks = 1,
                delayRound = 0,
                delayHit = 0,
                availableRounds = 2,
                availableHit = 0,
                clearPerHit = false,
                startBoundary = EffectStartBoundary.IMMEDIATE,
                potency = TypedBattlePotency.flat(1),
            ),
        )

        engine.prepareBattle(context)
        engine.applyChanges(
            listOf(
                harmfulEffect(900001, SkillKind.ACTIVE, 3, 501),
                harmfulEffect(900002, SkillKind.COMMAND, 2, 502),
            ),
            context.copy(round = 1),
        )

        val events = engine.resolveNormalAttack(
            round = 1,
            source = enemy,
            target = owner,
            random = FixedBattleRandom(0),
            context = context.copy(round = 1, source = enemy),
        )

        assertEquals(
            listOf(211900, 212900),
            events.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.rootSkillId == 200900 }
                .map(BattleEvent.SkillTriggered::skillId),
        )
        assertTrue(
            events.filterIsInstance<BattleEvent.Recovery>().any {
                it.source == owner && it.target == owner && it.skillId == 211900 && it.amount > 0
            },
            "events=$events",
        )
        assertTrue(714 in engine.state.view.activeEffectIds(owner))
        assertTrue(501 !in engine.state.view.activeEffectIds(owner))
        assertTrue(502 in engine.state.view.activeEffectIds(owner))
    }

    @Test
    fun `budongrushan cleanses harm before ongoing damage at every action start`() {
        val ownerHero = hero(100475, 100, listOf(200689), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 1))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.applyChanges(
            listOf(
                ongoingDamage(enemy, owner, detailId = 900031),
                controlChange(enemy, owner),
            ),
            context,
        )

        val events = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(round = 1, trigger = BattleTrigger.ACTION_BEFORE),
        )

        assertEquals(10_000, engine.liveHero(owner).troops)
        assertTrue(events.none { it is BattleEvent.OngoingDamage }, "events=$events")
        assertTrue(
            engine.state.view.activeEffectIds(owner).none { it in setOf(305, 501) },
        )
        assertTrue(engine.permissionFor(owner, context.copy(round = 1)).canAct)
    }

    @Test
    fun `huoshouchongfeng registers its round listener without attacking during preparation`() {
        val ownerHero = hero(100494, 100, listOf(200730), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 1))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val events = engine.prepareBattle(context)

        assertTrue(events.none { it is BattleEvent.SkillDamage }, "events=$events")
        assertTrue(
            events.filterIsInstance<BattleEvent.SkillTriggered>().any {
                it.rootSkillId == 200730 && it.skillId == 211730
            },
        )
        assertTrue(
            engine.state.effectStore.effectsFor(owner).any {
                it.detailId == 20073001 && it.effectId == 321
            },
        )
        assertTrue(
            engine.state.effectStore.effectsFor(owner).none {
                it.detailId == 21073012
            },
        )
    }

    @Test
    fun `huoshouchongfeng attacks once per round and buffs only the next normal attack`() {
        val ownerHero = hero(100494, 100, listOf(200730), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 1))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val first = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )
        val repeated = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            listOf(211730, 210730),
            first.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.rootSkillId == 200730 }
                .map(BattleEvent.SkillTriggered::skillId),
        )
        assertEquals(
            listOf(enemy),
            first.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.skillId == 210730 && it.effectId == 301 }
                .map(BattleEvent.SkillDamage::target),
        )
        assertTrue(
            repeated.none {
                it is BattleEvent.SkillTriggered && it.skillId == 211730
            },
        )
        assertTrue(
            engine.state.effectStore.effectsFor(owner).any {
                it.detailId == 21073012 && it.remainingHits == 1
            },
        )

        engine.resolveNormalAttack(
            round = 1,
            source = owner,
            target = enemy,
            random = FixedBattleRandom(0),
            context = context.copy(round = 1),
        )

        assertTrue(
            engine.state.effectStore.effectsFor(owner).none {
                it.detailId == 21073012
            },
        )
        assertTrue(
            engine.state.effectStore.effectsFor(owner).any {
                it.detailId == 20073001
            },
        )
    }

    @Test
    fun `shanmou equipment raises physical main skill chance only in rounds four and six`() {
        val ownerTeam = BattleTeamBuilder(
            config,
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100494,
                    position = 1,
                    troops = 10_000,
                    initialSkillId = 200730,
                    skillLevels = listOf(10),
                    equipmentFeatureSkillIds = listOf(450011),
                    equipmentFeatureSkillLevels = listOf(2),
                ),
            ),
        )
        val request = BattleRequest(
            attacker = ownerTeam,
            defender = BattleTeam(listOf(hero(200001, 10_000, position = 1))),
            maxRounds = 6,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(51),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val damageByRound = (3..6).associateWith { round ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(
                    round = round,
                    trigger = BattleTrigger.ROUND_START,
                ),
            ).filterIsInstance<BattleEvent.SkillDamage>()
                .count { it.skillId == 210730 }
        }

        assertEquals(
            mapOf(3 to 0, 4 to 1, 5 to 0, 6 to 1),
            damageByRound,
        )
    }

    @Test
    fun `jishi equipment stacks main skill recovery reduction onto the next hit`() {
        val built = BattleTeamBuilder(
            config,
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100016,
                    position = 0,
                    troops = 10_000,
                    equipmentFeatureSkillIds = listOf(450022),
                    equipmentFeatureSkillLevels = listOf(10),
                ),
            ),
        )
        val allyHero = hero(100017, 90, position = 1).copy(
            troops = 8_000,
            maxTroops = 10_000,
        )
        val request = BattleRequest(
            attacker = built.copy(heroes = built.heroes + allyHero),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val ally = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 1
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 200016,
            currentSkillId = 200016,
            trigger = BattleTrigger.RECOVERY_AFTER,
            battleView = engine.state.view,
        )

        fun recover(rootSkillId: Int) {
            val troops = requireNotNull(engine.state.view.state(ally)).troops
            engine.applyChanges(
                listOf(
                    TroopRecoveryChange(
                        source = owner,
                        target = ally,
                        amount = 100,
                        troopsAfter = troops + 100,
                        skillId = rootSkillId,
                        effectId = 401,
                    ),
                ),
                context.copy(
                    rootSkillId = rootSkillId,
                    currentSkillId = rootSkillId,
                ),
            )
        }

        fun nextDamageReduction(): Int =
            engine.liveHero(ally).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == null && it.origin == null && it.tag == null }
                .sumOf(BattleModifier.DamageTakenPercent::percent)

        recover(200001)
        assertEquals(0, nextDamageReduction())

        recover(200016)
        recover(200016)

        assertEquals(-20, nextDamageReduction())
        val reduction = engine.state.effectStore.effectsFor(ally).single {
            it.skillId == 451022 && it.detailId == 45102201
        }
        assertEquals(2, reduction.stacks)
        assertEquals(1, reduction.remainingHits)
        assertTrue(
            BattleDamageCalculator.physical(
                source = engine.liveHero(enemy),
                target = engine.liveHero(ally),
            ) < BattleDamageCalculator.physical(
                source = engine.liveHero(enemy),
                target = engine.liveHero(ally).copy(modifiers = emptyList()),
            ),
        )

        engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = ally,
            amount = 100,
            context = context.copy(source = enemy),
        )

        assertEquals(0, nextDamageReduction())
        assertTrue(
            engine.state.effectStore.effectsFor(ally).none {
                it.skillId == 451022 && it.detailId == 45102201
            },
        )
    }

    @Test
    fun `panzhenshanshou registers its round child without applying it during preparation`() {
        val ownerHero = hero(100615, 100, listOf(200816), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    ownerHero,
                    hero(100001, 90, position = 1),
                    hero(100002, 80, position = 2),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val events = engine.prepareBattle(context)

        assertTrue(
            events.filterIsInstance<BattleEvent.SkillTriggered>().any {
                it.rootSkillId == 200816 && it.skillId == 210816
            },
        )
        assertTrue(
            engine.state.view.heroes().all { target ->
                engine.state.effectStore.effectsFor(target).none {
                    it.detailId in setOf(21081601, 21081602, 21081613)
                }
            },
        )
    }

    @Test
    fun `panzhenshanshou waits for its owner and selects the live lowest troop ally each round`() {
        val ownerHero = hero(100615, 50, listOf(200816), position = 0)
            .copy(skillLevels = listOf(10))
        val firstLowestHero = hero(100001, 100, position = 1).copy(troops = 5_000)
        val nextLowestHero = hero(100002, 80, position = 2).copy(troops = 7_000)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, firstLowestHero, nextLowestHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val firstLowest = engine.state.view.heroes().single {
            it.heroId == firstLowestHero.id
        }
        val nextLowest = engine.state.view.heroes().single {
            it.heroId == nextLowestHero.id
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val beforeOwner = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(
                round = 1,
                source = firstLowest,
                trigger = BattleTrigger.ROUND_START,
            ),
        )
        val atOwner = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertTrue(
            beforeOwner.none {
                it is BattleEvent.SkillTriggered &&
                    it.rootSkillId == 200816 &&
                    it.skillId == 210816
            },
            "beforeOwner=$beforeOwner",
        )
        assertTrue(
            atOwner.any {
                it is BattleEvent.SkillTriggered &&
                    it.rootSkillId == 200816 &&
                    it.skillId == 210816
            },
            "atOwner=$atOwner",
        )
        assertEquals(
            setOf(21081601, 21081602),
            engine.state.effectStore.effectsFor(firstLowest)
                .filter { it.detailId in setOf(21081601, 21081602) }
                .mapTo(linkedSetOf()) { it.detailId },
        )
        assertEquals(
            1,
            engine.state.view.heroes()
                .filter { it.side == owner.side }
                .flatMap(engine.state.effectStore::effectsFor)
                .count { it.detailId == 21081613 },
        )

        engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = engine.state.view.heroes().single { it.side == Side.DEFENDER },
                    target = nextLowest,
                    amount = 4_000,
                    troopsAfter = 3_000,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 302,
                ),
            ),
            context.copy(round = 1),
        )
        val nextRound = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 2, trigger = BattleTrigger.ROUND_START),
        )
        val repeated = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 2, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            setOf(21081601, 21081602),
            engine.state.effectStore.effectsFor(nextLowest)
                .filter { it.detailId in setOf(21081601, 21081602) }
                .mapTo(linkedSetOf()) { it.detailId },
            "nextRound=$nextRound",
        )
        assertTrue(
            repeated.none {
                it is BattleEvent.SkillTriggered &&
                    it.rootSkillId == 200816 &&
                    it.skillId == 210816
            },
            "repeated=$repeated",
        )
    }

    @Test
    fun `panzhenshanshou runs only during the first four rounds`() {
        val ownerHero = hero(100615, 100, listOf(200816), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 5,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        val triggeredRounds = (1..5).flatMap { round ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(round = round, trigger = BattleTrigger.ROUND_START),
            ).filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.rootSkillId == 200816 && it.skillId == 210816 }
                .map(BattleEvent.SkillTriggered::round)
        }

        assertEquals(listOf(1, 2, 3, 4), triggeredRounds)
    }

    @Test
    fun `panzhenshanshou physical and strategy reductions expire independently on first damage`() {
        val ownerHero = hero(100615, 100, listOf(200816), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            setOf(522, 524),
            engine.state.effectStore.effectsFor(owner)
                .filter { it.detailId in setOf(21081601, 21081602) }
                .mapTo(linkedSetOf()) { it.effectId },
        )
        engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = owner,
            amount = 100,
            context = context.copy(round = 1, source = enemy),
        )
        assertEquals(
            setOf(524),
            engine.state.effectStore.effectsFor(owner)
                .filter { it.detailId in setOf(21081601, 21081602) }
                .mapTo(linkedSetOf()) { it.effectId },
        )

        engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = enemy,
                    target = owner,
                    amount = 100,
                    troopsAfter = engine.liveHero(owner).troops - 100,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900001,
                    effectId = 302,
                ),
            ),
            context.copy(round = 1, source = enemy),
        )

        assertTrue(
            engine.state.effectStore.effectsFor(owner).none {
                it.detailId in setOf(21081601, 21081602)
            },
        )
    }

    @Test
    fun `zhijizhibi registers selected damage listeners without preparation modifiers`() {
        val ownerHero = hero(100692, 100, listOf(200249), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    ownerHero,
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 0),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        assertTrue(
            engine.state.view.heroes()
                .flatMap(engine.state.effectStore::effectsFor)
                .none {
                    it.detailId in setOf(
                        20024901,
                        20024902,
                        20024911,
                        20024912,
                        21024901,
                        21124901,
                        21224901,
                        21324901,
                    )
                },
        )
    }

    @Test
    fun `zhijizhibi stacks matching dealt and taken damage modifiers after each hit up to five`() {
        val ownerHero = hero(100692, 100, listOf(200249), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 0).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        repeat(6) {
            val troops = engine.liveHero(enemy).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = owner,
                        target = enemy,
                        amount = 1,
                        troopsAfter = troops - 1,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 301,
                    ),
                ),
                context.copy(round = 1, trigger = BattleTrigger.DAMAGE_AFTER),
            )
        }

        assertEquals(
            40,
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageDealtPercent::percent),
        )
        assertEquals(
            40,
            engine.liveHero(enemy).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageTakenPercent::percent),
        )
        assertEquals(
            5,
            engine.state.effectStore.effectsFor(owner)
                .single { it.detailId == 21124901 }
                .stacks,
        )
        assertEquals(
            5,
            engine.state.effectStore.effectsFor(enemy)
                .single { it.detailId == 21024901 }
                .stacks,
        )
        assertTrue(
            engine.state.effectStore.effectsFor(owner).none {
                it.detailId == 21324901
            },
        )
        assertTrue(
            engine.state.effectStore.effectsFor(enemy).none {
                it.detailId == 21224901
            },
        )

        val troops = engine.liveHero(enemy).troops
        engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = owner,
                    target = enemy,
                    amount = 1,
                    troopsAfter = troops - 1,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900001,
                    effectId = 302,
                ),
            ),
            context.copy(round = 1, trigger = BattleTrigger.DAMAGE_AFTER),
        )

        assertEquals(
            8,
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageDealtPercent::percent),
        )
        assertEquals(
            8,
            engine.liveHero(enemy).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageTakenPercent::percent),
        )
    }

    @Test
    fun `zhijizhibi rolls dealt and taken modifiers independently after damage`() {
        val ownerHero = hero(100692, 100, listOf(200249), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val rolls = intArrayOf(0, 99, 99, 0)
        var rollIndex = 0
        val random = object : BattleRandom {
            override fun nextInt(bound: Int): Int {
                val value = rolls.getOrElse(rollIndex) { 99 }
                rollIndex += 1
                return value.coerceIn(0, bound - 1)
            }
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = random,
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        fun physicalModifier(ref: BattleHeroRef): Int =
            engine.liveHero(ref).modifiers.sumOf { modifier ->
                when (modifier) {
                    is BattleModifier.DamageDealtPercent ->
                        modifier.percent.takeIf {
                            modifier.school == DamageSchool.PHYSICAL
                        } ?: 0
                    is BattleModifier.DamageTakenPercent ->
                        modifier.percent.takeIf {
                            modifier.school == DamageSchool.PHYSICAL
                        } ?: 0
                    else -> 0
                }
            }
        fun hit() {
            val troops = engine.liveHero(enemy).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = owner,
                        target = enemy,
                        amount = 1,
                        troopsAfter = troops - 1,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 301,
                    ),
                ),
                context.copy(round = 1, trigger = BattleTrigger.DAMAGE_AFTER),
            )
        }

        hit()
        assertEquals(8, physicalModifier(owner))
        assertEquals(0, physicalModifier(enemy))

        hit()
        assertEquals(8, physicalModifier(owner))
        assertEquals(8, physicalModifier(enemy))
        assertEquals(4, rollIndex)
    }

    @Test
    fun `gongqibubei registers its selected targets without preparation vulnerability`() {
        val ownerHero = hero(100027, 100, listOf(200755), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 0),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        assertTrue(
            engine.state.view.heroes()
                .flatMap(engine.state.effectStore::effectsFor)
                .none { it.detailId in setOf(20075501, 20075502) },
        )
    }

    @Test
    fun `gongqibubei stacks both vulnerabilities after each selected target hit up to five`() {
        val ownerHero = hero(100027, 100, listOf(200755), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 0).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        repeat(6) {
            val troops = engine.liveHero(enemy).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = owner,
                        target = enemy,
                        amount = 1,
                        troopsAfter = troops - 1,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 301,
                    ),
                ),
                context.copy(round = 1, trigger = BattleTrigger.DAMAGE_AFTER),
            )
        }

        val physical = engine.state.effectStore.effectsFor(enemy)
            .single { it.detailId == 20075501 }
        val strategy = engine.state.effectStore.effectsFor(enemy)
            .single { it.detailId == 20075502 }
        assertEquals(5, physical.stacks)
        assertEquals(5, strategy.stacks)
        assertEquals(physical.effectiveStrength, strategy.effectiveStrength)
        assertTrue(physical.effectiveStrength > 0)
        assertEquals(
            physical.effectiveStrength,
            engine.liveHero(enemy).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageTakenPercent::percent),
        )
        assertEquals(
            strategy.effectiveStrength,
            engine.liveHero(enemy).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageTakenPercent::percent),
        )
    }

    @Test
    fun `fanjian registers its selected targets without preparation damage reduction`() {
        val ownerHero = hero(100604, 100, listOf(200818), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 0),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        assertTrue(
            engine.state.view.heroes()
                .flatMap(engine.state.effectStore::effectsFor)
                .none { it.detailId in setOf(20081801, 20081802) },
        )
    }

    @Test
    fun `fanjian stacks only the matching dealt damage reduction up to five`() {
        val ownerHero = hero(100604, 100, listOf(200818), position = 0)
            .copy(skillLevels = listOf(10))
        val enemyHero = hero(200001, 10, position = 0).copy(
            troops = 100_000,
            maxTroops = 100_000,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(enemyHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.heroId == enemyHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        fun hit(school: DamageSchool) {
            val troops = engine.liveHero(owner).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = owner,
                        amount = 1,
                        troopsAfter = troops - 1,
                        school = school,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = if (school == DamageSchool.PHYSICAL) 301 else 302,
                    ),
                ),
                context.copy(round = 1, source = enemy, trigger = BattleTrigger.DAMAGE_AFTER),
            )
        }

        repeat(6) { hit(DamageSchool.PHYSICAL) }

        val physical = engine.state.effectStore.effectsFor(enemy)
            .single { it.detailId == 20081801 }
        assertEquals(5, physical.stacks)
        assertEquals(
            -physical.effectiveStrength,
            engine.liveHero(enemy).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageDealtPercent::percent),
        )
        assertTrue(
            engine.state.effectStore.effectsFor(enemy).none {
                it.detailId == 20081802
            },
        )

        hit(DamageSchool.STRATEGY)

        val strategy = engine.state.effectStore.effectsFor(enemy)
            .single { it.detailId == 20081802 }
        assertEquals(1, strategy.stacks)
        assertEquals(
            -strategy.effectiveStrength,
            engine.liveHero(enemy).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageDealtPercent::percent),
        )
    }

    @Test
    fun `mingqixushi stacks enemy strategy reduction once per owner round up to eight`() {
        val ownerHero = hero(100496, 100, listOf(200737), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        fun layers(): Int =
            engine.state.effectStore.effectsFor(enemy)
                .single { it.detailId == 20073712 }
                .stacks

        engine.prepareBattle(context)
        assertEquals(1, layers())

        val roundOne = context.copy(round = 1, trigger = BattleTrigger.ROUND_START)
        engine.trigger(BattleTrigger.ROUND_START, roundOne)
        engine.trigger(BattleTrigger.ROUND_START, roundOne)
        assertEquals(2, layers())

        val laterLayers = (2..8).map { round ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(round = round, trigger = BattleTrigger.ROUND_START),
            )
            layers()
        }

        assertEquals(listOf(3, 4, 5, 6, 7, 8, 8), laterLayers)
        assertEquals(52, engine.liveHero(enemy).stats.strategy)
    }

    @Test
    fun `mouzhu selects the live highest troop ally each round and rolls insight and first action independently`() {
        val ownerHero = hero(100645, 100, listOf(200835), position = 0)
            .copy(troops = 8_000, maxTroops = 10_000, skillLevels = listOf(10))
        val firstHighestHero = hero(100001, 90, position = 1)
        val secondHighestHero = hero(100002, 80, position = 2)
            .copy(troops = 9_000, maxTroops = 10_000)
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(ownerHero, firstHighestHero, secondHighestHero),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 4,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.heroId == ownerHero.id
        }
        val firstHighest = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.heroId == firstHighestHero.id
        }
        val secondHighest = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.heroId == secondHighestHero.id
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        fun preparedDetails(target: BattleHeroRef): Set<Int> =
            engine.state.effectStore.effectsFor(target)
                .map { it.detailId }
                .filterTo(mutableSetOf()) { it in setOf(20083523, 20083524) }

        engine.prepareBattle(context)

        assertTrue(
            engine.state.view.heroes().all { preparedDetails(it).isEmpty() },
            "mouzhu must not lock round targets during preparation",
        )

        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(round = 1, trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT),
        )

        assertEquals(setOf(20083523, 20083524), preparedDetails(firstHighest))
        assertTrue(preparedDetails(secondHighest).isEmpty())
        engine.finishRound(1)
        engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = enemy,
                    target = firstHighest,
                    amount = 3_000,
                    troopsAfter = 7_000,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.NORMAL,
                    tags = emptySet(),
                    skillId = 0,
                    effectId = 301,
                ),
            ),
            context.copy(
                round = 2,
                source = enemy,
                trigger = BattleTrigger.DAMAGE_AFTER,
            ),
        )

        var roundTwoRolls = 0
        val roundTwoRandom = object : BattleRandom {
            private val values = intArrayOf(0, 99)

            override fun nextInt(bound: Int): Int {
                val value = values.getOrElse(roundTwoRolls) {
                    error("Unexpected round-two roll for bound=$bound")
                }
                roundTwoRolls += 1
                return value
            }
        }
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(
                random = roundTwoRandom,
                round = 2,
                trigger = BattleTrigger.ROUND_START,
            ),
        )

        assertTrue(preparedDetails(firstHighest).isEmpty())
        assertEquals(setOf(20083523), preparedDetails(secondHighest))
        assertEquals(2, roundTwoRolls)
        engine.finishRound(2)

        var roundThreeRolls = 0
        val roundThreeRandom = object : BattleRandom {
            private val values = intArrayOf(99, 0)

            override fun nextInt(bound: Int): Int {
                val value = values.getOrElse(roundThreeRolls) {
                    error("Unexpected round-three roll for bound=$bound")
                }
                roundThreeRolls += 1
                return value
            }
        }
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(
                random = roundThreeRandom,
                round = 3,
                trigger = BattleTrigger.ROUND_START,
            ),
        )

        assertEquals(setOf(20083524), preparedDetails(secondHighest))
        assertEquals(2, roundThreeRolls)
    }

    @Test
    fun `mouzhu highest troop round effects stop after the first three rounds`() {
        val ownerHero = hero(100645, 100, listOf(200835), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    ownerHero,
                    hero(100001, 90, position = 1)
                        .copy(troops = 9_000, maxTroops = 10_000),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 4,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.heroId == ownerHero.id
        }
        val highest = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        fun preparedDetails(): Set<Int> =
            engine.state.effectStore.effectsFor(highest)
                .map { it.detailId }
                .filterTo(mutableSetOf()) { it in setOf(20083523, 20083524) }

        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(round = 1, trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT),
        )
        assertEquals(setOf(20083523, 20083524), preparedDetails())
        engine.finishRound(1)
        assertTrue(preparedDetails().isEmpty())

        (2..3).forEach { round ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(round = round, trigger = BattleTrigger.ROUND_START),
            )
            assertEquals(setOf(20083523, 20083524), preparedDetails())
            engine.finishRound(round)
            assertTrue(preparedDetails().isEmpty())
        }

        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 4, trigger = BattleTrigger.ROUND_START),
        )

        assertTrue(preparedDetails().isEmpty())
    }

    @Test
    fun `mouyihongtu registers both reductions without increasing morale during preparation`() {
        val ownerHero = hero(100001, 100, listOf(200985), position = 0)
            .copy(skillLevels = listOf(10))
        val allyHero = hero(100002, 90, position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, allyHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.heroId == ownerHero.id
        }
        val allies = engine.state.view.heroes().filter { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        allies.forEach { ally ->
            assertEquals(
                setOf(20098501, 20098502),
                engine.state.effectStore.effectsFor(ally)
                    .map { it.detailId }
                    .filterTo(mutableSetOf()) {
                        it in setOf(20098501, 20098502, 20098503)
                    },
            )
            assertEquals(100, engine.state.view.currentMorale(ally))
        }
    }

    @Test
    fun `mouyihongtu reductions decay by one eighth while morale rises every round`() {
        val ownerHero = hero(100001, 100, listOf(200985), position = 0)
            .copy(skillLevels = listOf(10))
        val allyHero = hero(100002, 90, position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, allyHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.heroId == ownerHero.id
        }
        val allies = engine.state.view.heroes().filter { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        fun strength(target: BattleHeroRef, detailId: Int): Int =
            engine.state.effectStore.effectsFor(target)
                .single { it.detailId == detailId }
                .effectiveStrength

        engine.prepareBattle(context)
        val initialStrengths = allies.associateWith { ally ->
            20098501 to strength(ally, 20098501) to
                (20098502 to strength(ally, 20098502))
        }

        (1..8).forEach { round ->
            val roundContext = context.copy(
                round = round,
                trigger = BattleTrigger.ROUND_START,
            )
            engine.trigger(BattleTrigger.ROUND_START, roundContext)
            engine.trigger(BattleTrigger.ROUND_START, roundContext)

            allies.forEach { ally ->
                assertEquals(100 + round * 8, engine.state.view.currentMorale(ally))
                val (physical, strategy) = initialStrengths.getValue(ally)
                val physicalStep = (physical.second / 8).coerceAtLeast(1)
                val strategyStep = (strategy.second / 8).coerceAtLeast(1)
                assertEquals(
                    (physical.second - physicalStep * (round - 1)).coerceAtLeast(0),
                    strength(ally, physical.first),
                )
                assertEquals(
                    (strategy.second - strategyStep * (round - 1)).coerceAtLeast(0),
                    strength(ally, strategy.first),
                )
            }
            engine.finishRound(round)
        }

        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 9, trigger = BattleTrigger.ROUND_START),
        )
        allies.forEach { ally ->
            assertEquals(164, engine.state.view.currentMorale(ally))
        }
    }

    @Test
    fun `suanwuyice waits for a selected target active attempt before applying hex`() {
        val ownerHero = hero(100011, 100, listOf(200011), position = 0)
            .copy(skillLevels = listOf(10))
        val firstEnemyHero = hero(200001, 20, listOf(200001), position = 0)
            .copy(skillLevels = listOf(10))
        val secondEnemyHero = hero(200002, 10, listOf(200001), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(firstEnemyHero, secondEnemyHero)),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val firstEnemy = engine.state.view.heroes().single {
            it.heroId == firstEnemyHero.id
        }
        val secondEnemy = engine.state.view.heroes().single {
            it.heroId == secondEnemyHero.id
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun hasHex(target: BattleHeroRef): Boolean =
            engine.state.effectStore.effectsFor(target).any {
                it.detailId == 20001103 && it.effectId == 306
            }

        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                round = 1,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )

        assertTrue(!hasHex(firstEnemy))
        assertTrue(!hasHex(secondEnemy))

        val attemptEvents = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                random = FixedBattleRandom(99),
                round = 1,
                source = firstEnemy,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )

        assertTrue(hasHex(firstEnemy), "events=$attemptEvents")
        assertTrue(!hasHex(secondEnemy))
        assertTrue(
            attemptEvents.filterIsInstance<BattleEvent.StatusApplied>().any {
                it.target == firstEnemy &&
                    it.skillId == 200011 &&
                    it.effectId == 306 &&
                    it.status == BattleStatus.HEX
            },
            "events=$attemptEvents",
        )
    }

    @Test
    fun `suanwuyice active attempt listener expires after two rounds`() {
        val ownerHero = hero(100011, 100, listOf(200011), position = 0)
            .copy(skillLevels = listOf(10))
        val firstEnemyHero = hero(200001, 20, listOf(200001), position = 0)
            .copy(skillLevels = listOf(10))
        val secondEnemyHero = hero(200002, 10, listOf(200001), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(firstEnemyHero, secondEnemyHero)),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val firstEnemy = engine.state.view.heroes().single {
            it.heroId == firstEnemyHero.id
        }
        val secondEnemy = engine.state.view.heroes().single {
            it.heroId == secondEnemyHero.id
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun hexApplied(events: List<BattleEvent>, target: BattleHeroRef): Boolean =
            events.filterIsInstance<BattleEvent.StatusApplied>().any {
                it.target == target &&
                    it.skillId == 200011 &&
                    it.effectId == 306 &&
                    it.status == BattleStatus.HEX
            }

        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                round = 1,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        engine.finishRound(1)

        val roundTwo = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                random = FixedBattleRandom(99),
                round = 2,
                source = firstEnemy,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        engine.finishRound(2)
        val roundThree = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                random = FixedBattleRandom(99),
                round = 3,
                source = secondEnemy,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )

        assertTrue(hexApplied(roundTwo, firstEnemy), "roundTwo=$roundTwo")
        assertTrue(!hexApplied(roundThree, secondEnemy), "roundThree=$roundThree")
    }

    @Test
    fun `zhengshi waits until the next round after fifteen enemy damage events`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100701, 100, listOf(200244), position = 2)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val roundOne = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = enemy,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )
        repeat(15) { engine.recordDamageThresholds(enemy, roundOne) }

        assertTrue(
            engine.trigger(
                BattleTrigger.ROUND_START,
                roundOne.copy(source = owner, trigger = BattleTrigger.ROUND_START),
            ).none { it is BattleEvent.SkillTriggered && it.skillId == 213244 },
        )
        val roundTwo = roundOne.copy(
            round = 2,
            source = owner,
            trigger = BattleTrigger.ROUND_START,
        )
        val first = engine.trigger(BattleTrigger.ROUND_START, roundTwo)
        val repeated = engine.trigger(BattleTrigger.ROUND_START, roundTwo)

        assertEquals(1, first.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 213244 })
        assertTrue(repeated.none { it is BattleEvent.SkillTriggered && it.skillId == 213244 })

        repeat(15) {
            engine.recordDamageThresholds(
                enemy,
                roundTwo.copy(source = enemy, trigger = BattleTrigger.DAMAGE_AFTER),
            )
        }
        val roundThree = engine.trigger(
            BattleTrigger.ROUND_START,
            roundTwo.copy(round = 3),
        )

        assertTrue(roundThree.none { it is BattleEvent.SkillTriggered && it.skillId == 213244 })
    }

    @Test
    fun `zhengshi retrigger window starts one round after its activation`() {
        val owner = hero(100701, 100, listOf(200244), position = 2)
            .copy(skillLevels = listOf(10))
        val ally = hero(100424, 90, listOf(200652), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(owner, ally)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val ownerRef = engine.state.view.heroes().single { it.heroId == owner.id }
        val allyRef = engine.state.view.heroes().single { it.heroId == ally.id }
        val enemyRef = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        fun context(
            round: Int,
            source: BattleHeroRef,
            trigger: BattleTrigger,
        ) = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = round,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = trigger,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context(0, ownerRef, BattleTrigger.BATTLE_COMMAND))
        repeat(15) {
            engine.recordDamageThresholds(
                enemyRef,
                context(1, enemyRef, BattleTrigger.DAMAGE_AFTER),
            )
        }
        engine.trigger(
            BattleTrigger.ROUND_START,
            context(2, ownerRef, BattleTrigger.ROUND_START),
        )

        val activationRoundEvents = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(2, allyRef, BattleTrigger.ACTIVE_SKILL_ATTEMPT),
        )
        engine.trigger(
            BattleTrigger.ROUND_START,
            context(3, ownerRef, BattleTrigger.ROUND_START),
        )
        val activeWindowEvents = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(3, allyRef, BattleTrigger.ACTIVE_SKILL_ATTEMPT),
        )

        assertEquals(
            1,
            activationRoundEvents.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source == allyRef && it.skillId == 200652 },
        )
        assertEquals(
            2,
            activeWindowEvents.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source == allyRef && it.skillId == 200652 },
        )
    }

    @Test
    fun `zhengshi retrigger keeps the original active skill level`() {
        val owner = hero(100701, 100, listOf(200244), position = 2)
            .copy(skillLevels = listOf(10))
        val ally = hero(100619, 90, listOf(200884), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(owner, ally)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val ownerRef = engine.state.view.heroes().single { it.heroId == owner.id }
        val allyRef = engine.state.view.heroes().single { it.heroId == ally.id }
        val enemyRef = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        fun context(
            round: Int,
            source: BattleHeroRef,
            trigger: BattleTrigger,
        ) = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = round,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = trigger,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context(0, ownerRef, BattleTrigger.BATTLE_COMMAND))
        repeat(15) {
            engine.recordDamageThresholds(
                enemyRef,
                context(1, enemyRef, BattleTrigger.DAMAGE_AFTER),
            )
        }
        engine.trigger(
            BattleTrigger.ROUND_START,
            context(2, ownerRef, BattleTrigger.ROUND_START),
        )
        engine.trigger(
            BattleTrigger.ROUND_START,
            context(3, ownerRef, BattleTrigger.ROUND_START),
        )

        val damage = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(3, allyRef, BattleTrigger.ACTIVE_SKILL_ATTEMPT),
        ).filterIsInstance<BattleEvent.SkillDamage>()
            .filter {
                it.source == allyRef &&
                    it.skillId == 200884 &&
                    it.effectId == 302
            }
            .map(BattleEvent.SkillDamage::damage)

        assertEquals(2, damage.size)
        assertEquals(
            1,
            damage.distinct().size,
            "natural and retriggered damage=$damage",
        )
    }

    @Test
    fun `zhengshi retriggers an active skill that completes preparation in its active round`() {
        val owner = hero(100701, 100, listOf(200244), position = 2)
            .copy(skillLevels = listOf(10))
        val ally = hero(100003, 90, listOf(200235), position = 1)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(owner, ally)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val ownerRef = engine.state.view.heroes().single { it.heroId == owner.id }
        val allyRef = engine.state.view.heroes().single { it.heroId == ally.id }
        val enemyRef = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        fun context(
            round: Int,
            source: BattleHeroRef,
            trigger: BattleTrigger,
        ) = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = round,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = trigger,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context(0, ownerRef, BattleTrigger.BATTLE_COMMAND))
        repeat(15) {
            engine.recordDamageThresholds(
                enemyRef,
                context(1, enemyRef, BattleTrigger.DAMAGE_AFTER),
            )
        }
        engine.trigger(
            BattleTrigger.ROUND_START,
            context(2, ownerRef, BattleTrigger.ROUND_START),
        )
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(2, allyRef, BattleTrigger.ACTIVE_SKILL_ATTEMPT),
        )
        engine.trigger(
            BattleTrigger.ROUND_START,
            context(3, ownerRef, BattleTrigger.ROUND_START),
        )

        val events = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context(3, allyRef, BattleTrigger.ACTION_BEFORE),
        )

        assertEquals(
            2,
            events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source == allyRef && it.skillId == 200235 },
        )
    }

    @Test
    fun `xinzhan lowers each allied damage target morale for only the first nine hits`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100275, 100, listOf(200275), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 2),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val attacker = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val firstTarget = engine.state.view.heroes().single { it.heroId == BattleHeroId(200001) }
        val secondTarget = engine.state.view.heroes().single { it.heroId == BattleHeroId(200002) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = attacker,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )

        repeat(8) {
            engine.applyNormalDamage(1, attacker, firstTarget, 1, context)
        }
        engine.applyNormalDamage(1, attacker, secondTarget, 1, context)
        engine.applyNormalDamage(1, attacker, secondTarget, 1, context)

        assertEquals(60, engine.state.view.currentMorale(firstTarget))
        assertEquals(95, engine.state.view.currentMorale(secondTarget))
    }

    @Test
    fun `xinzhan damage limit is isolated by side`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100275, 100, listOf(200275), position = 2)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200275, 90, listOf(200275), position = 2),
                    hero(200001, 80, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val attacker = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val defender = engine.state.view.heroes().single { it.heroId == BattleHeroId(200275) }
        val defenderAlly = engine.state.view.heroes().single { it.heroId == BattleHeroId(200001) }
        val base = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = attacker,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )

        repeat(9) { engine.applyNormalDamage(1, attacker, defender, 1, base) }
        engine.applyNormalDamage(1, defenderAlly, attacker, 1, base.copy(source = defenderAlly))

        assertEquals(55, engine.state.view.currentMorale(defender))
        assertEquals(95, engine.state.view.currentMorale(attacker))
    }

    @Test
    fun `xinzhan recovers allied physical damage source but not strategy damage`() {
        val ownerHero = hero(100275, 100, listOf(200275), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    ownerHero,
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(hero(200001, 10, position = 2)),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)

        val lifeStealEffects = engine.state.effectStore.effectsFor(ally).filter {
            it.source == owner &&
                it.rootSkillId == 200275 &&
                it.skillId == 212275 &&
                it.effectId == 542
        }
        assertEquals(
            1,
            lifeStealEffects.size,
            "preparation=$preparation effects=${engine.state.effectStore.effectsFor(ally)}",
        )
        val lifeSteal = lifeStealEffects.single()
        assertEquals(35, lifeSteal.effectiveStrength)
        engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = ally,
            amount = 500,
            context = context.copy(
                round = 1,
                source = enemy,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )

        val physicalEvents = engine.applyNormalDamage(
            round = 1,
            source = ally,
            target = enemy,
            amount = 400,
            context = context.copy(
                round = 1,
                source = ally,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )

        val physicalRecoveries = physicalEvents.filterIsInstance<BattleEvent.Recovery>()
            .filter { it.skillId == 212275 }
        assertEquals(
            listOf(
                BattleEvent.Recovery(
                    round = 1,
                    source = owner,
                    target = ally,
                    amount = 140,
                    targetTroopsAfter = 9_640,
                    skillId = 212275,
                ),
            ),
            physicalRecoveries,
            "physicalEvents=$physicalEvents",
        )
        val strategyEvents = engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = ally,
                    target = enemy,
                    amount = 100,
                    troopsAfter = 9_500,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900001,
                    effectId = 302,
                ),
            ),
            context.copy(
                round = 1,
                source = ally,
                trigger = BattleTrigger.DAMAGE_BEFORE,
            ),
        )

        assertTrue(
            strategyEvents.none {
                it is BattleEvent.Recovery && it.skillId == 212275
            },
            "strategyEvents=$strategyEvents",
        )
    }

    @Test
    fun `generic attack recovery effect consumes its registered rate`() {
        val ownerHero = hero(100009, 100, listOf(200009), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        val lifeStealEffects = engine.state.effectStore.effectsFor(owner).filter {
            it.rootSkillId == 200009 &&
                it.skillId == 200009 &&
                it.effectId == 542
        }
        assertEquals(
            1,
            lifeStealEffects.size,
            "preparation=$preparation effects=${engine.state.effectStore.effectsFor(owner)}",
        )
        val lifeSteal = lifeStealEffects.single()
        assertEquals(50, lifeSteal.effectiveStrength)
        engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = owner,
            amount = 500,
            context = context.copy(
                round = 1,
                source = enemy,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )

        val events = engine.applyNormalDamage(
            round = 1,
            source = owner,
            target = enemy,
            amount = 400,
            context = context.copy(
                round = 1,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )

        val recoveries = events.filterIsInstance<BattleEvent.Recovery>()
            .filter { it.skillId == 200009 }
        assertEquals(listOf(200), recoveries.map(BattleEvent.Recovery::amount), "events=$events")
    }

    @Test
    fun `shoujing triggers its configured children once at rounds six and eight`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100277, 100, listOf(200277), position = 2)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 5,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.ROUND_START,
            battleView = engine.state.view,
        )

        val roundFive = engine.trigger(BattleTrigger.ROUND_START, context)
        val roundSix = engine.trigger(BattleTrigger.ROUND_START, context.copy(round = 6))
        val repeatedSix = engine.trigger(BattleTrigger.ROUND_START, context.copy(round = 6))
        val roundEight = engine.trigger(BattleTrigger.ROUND_START, context.copy(round = 8))

        assertTrue(roundFive.none { it is BattleEvent.SkillTriggered && it.skillId in setOf(210277, 211277) })
        assertEquals(1, roundSix.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 210277 })
        assertTrue(repeatedSix.none { it is BattleEvent.SkillTriggered && it.skillId == 210277 })
        assertEquals(1, roundEight.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 211277 })
    }

    @Test
    fun `huiyan grants its team effects once after six allied damage events`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100294, 100, listOf(200294), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100294) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = ally,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )

        repeat(5) { engine.applyNormalDamage(1, ally, enemy, 1, context) }
        assertEquals(100, engine.state.view.currentMorale(owner))
        assertEquals(100, engine.state.view.currentMorale(ally))

        val sixth = engine.applyNormalDamage(1, ally, enemy, 1, context)
        engine.applyNormalDamage(1, ally, enemy, 1, context)

        assertEquals(100, engine.state.view.currentMorale(owner))
        assertEquals(106, engine.state.view.currentMorale(ally))
        assertEquals(1, sixth.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 211294 })
    }

    @Test
    fun `manwang counter chain triggers on each fifth actual hit to its owner`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100297, 100, listOf(200297), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100297) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = enemy,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )

        repeat(4) { engine.applyNormalDamage(1, enemy, owner, 1, context) }
        engine.applyNormalDamage(1, enemy, ally, 1, context)
        val fifth = engine.applyNormalDamage(1, enemy, owner, 1, context)
        val sixth = engine.applyNormalDamage(1, enemy, owner, 1, context)

        assertEquals(1, fifth.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 211297 })
        assertTrue(sixth.none { it is BattleEvent.SkillTriggered && it.skillId == 211297 })
    }

    @Test
    fun `fenglinghushu starts empty and stacks five times from ally normal attacks`() {
        val ownerHero = hero(100615, 100, listOf(200865), position = 2)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100603, 90, position = 0),
                    hero(100004, 80, position = 1),
                    ownerHero,
                ),
            ),
            defender = BattleTeam(listOf(hero(100620, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val ally = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun stacks(detailId: Int): Int =
            engine.state.effectStore.effectsFor(owner)
                .singleOrNull { it.detailId == detailId }
                ?.stacks
                ?: 0

        engine.prepareBattle(context)

        assertEquals(0, stacks(21086501))
        assertEquals(0, stacks(21086502))
        assertEquals(0, stacks(21086503))

        engine.trigger(
            BattleTrigger.NORMAL_ATTACK_AFTER,
            context.copy(
                source = owner,
                round = 1,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )
        assertEquals(0, stacks(21086501))

        repeat(7) {
            engine.trigger(
                BattleTrigger.NORMAL_ATTACK_AFTER,
                context.copy(
                    source = ally,
                    round = 1,
                    trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                ),
            )
        }

        assertEquals(5, stacks(21086501))
        assertEquals(5, stacks(21086502))
        assertEquals(5, stacks(21086503))
    }

    @Test
    fun `qinlueruhuo rolls per physical hit and consumes its damage bonus immediately`() {
        fun fixture(skillIds: List<Int>): Triple<DefaultCompleteSkillEngine, BattleHeroRef, BattleHeroRef> {
            val request = BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100034, 100, skillIds, position = 2)
                            .copy(skillLevels = skillIds.map { 10 }),
                    ),
                ),
                defender = BattleTeam(listOf(hero(100620, 10, position = 2))),
                maxRounds = 1,
            )
            val engine = DefaultCompleteSkillEngine.create(request, config)
            val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
            val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
            val context = SkillBattleContext(
                request = request,
                runtime = engine.state.runtime,
                random = FixedBattleRandom(29),
                round = 0,
                source = source,
                rootSkillId = 0,
                currentSkillId = 0,
                trigger = BattleTrigger.BATTLE_PASSIVE,
                battleView = engine.state.view,
            )
            engine.prepareBattle(context)
            return Triple(engine, source, target)
        }

        val (baselineEngine, baselineSource, baselineTarget) = fixture(emptyList())
        val baseline = baselineEngine.resolveNormalAttack(
            round = 1,
            source = baselineSource,
            target = baselineTarget,
            random = FixedBattleRandom(29),
            context = SkillBattleContext(
                request = BattleRequest(
                    attacker = BattleTeam(listOf(baselineEngine.liveHero(baselineSource))),
                    defender = BattleTeam(listOf(baselineEngine.liveHero(baselineTarget))),
                ),
                runtime = baselineEngine.state.runtime,
                random = FixedBattleRandom(29),
                round = 1,
                source = baselineSource,
                rootSkillId = 0,
                currentSkillId = 0,
                trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
                battleView = baselineEngine.state.view,
            ),
        ).filterIsInstance<BattleEvent.NormalAttack>().single().damage

        val (engine, source, target) = fixture(listOf(200034))
        assertTrue(
            engine.state.effectStore.effectsFor(source).none {
                it.detailId in setOf(20003402, 21003401)
            },
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(engine.liveHero(source))),
            defender = BattleTeam(listOf(engine.liveHero(target))),
        )
        val events = engine.resolveNormalAttack(
            round = 1,
            source = source,
            target = target,
            random = FixedBattleRandom(29),
            context = SkillBattleContext(
                request = request,
                runtime = engine.state.runtime,
                random = FixedBattleRandom(29),
                round = 1,
                source = source,
                rootSkillId = 0,
                currentSkillId = 0,
                trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
                battleView = engine.state.view,
            ),
        )
        val enhanced = events.filterIsInstance<BattleEvent.NormalAttack>().single().damage

        assertTrue(enhanced > baseline, "baseline=$baseline enhanced=$enhanced events=$events")
        assertTrue(
            engine.state.effectStore.effectsFor(source).none { it.detailId == 21003401 },
        )
    }

    @Test
    fun `shiji registers at preparation and reacts only before actions and after actual hurt`() {
        val ownerHero = hero(100474, 90, listOf(200687), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100001, 70, position = 0),
                    hero(100002, 80, position = 1),
                    ownerHero,
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 60, position = 0),
                    hero(200002, 50, position = 1).copy(
                        troops = 20_000,
                        maxTroops = 20_000,
                    ),
                    hero(200003, 40, position = 2),
                ),
            ),
            maxRounds = 5,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == ownerHero.id }
        val allyBase = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val strongestEnemy = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 1
        }
        val enemySource = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 0
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        assertEquals(
            setOf(200687, 210687, 211687, 212687),
            preparation.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.rootSkillId == 200687 }
                .mapTo(linkedSetOf(), BattleEvent.SkillTriggered::skillId),
        )
        assertTrue(
            engine.state.view.heroes()
                .flatMap(engine.state.effectStore::effectsFor)
                .none { it.rootSkillId == 200687 },
        )

        val action = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(
                round = 1,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        assertEquals(
            setOf(211687, 212687),
            action.filterIsInstance<BattleEvent.SkillTriggered>()
                .filter { it.rootSkillId == 200687 }
                .mapTo(linkedSetOf(), BattleEvent.SkillTriggered::skillId),
        )
        assertEquals(
            setOf(21168701, 21168702),
            engine.state.effectStore.effectsFor(strongestEnemy)
                .filter { it.skillId == 211687 }
                .mapTo(linkedSetOf()) { it.detailId },
            "action=$action effects=" +
                engine.state.view.heroes().associateWith(engine.state.effectStore::effectsFor),
        )
        assertEquals(
            setOf(21268701, 21268702),
            engine.state.effectStore.effectsFor(allyBase)
                .filter { it.skillId == 212687 }
                .mapTo(linkedSetOf()) { it.detailId },
        )
        assertTrue(BattleStatus.INSIGHT !in requireNotNull(engine.state.view.state(owner)).statuses)

        val hurt = engine.applyNormalDamage(
            round = 1,
            source = enemySource,
            target = owner,
            amount = 100,
            context = context.copy(
                round = 1,
                source = enemySource,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )
        assertTrue(hurt.filterIsInstance<BattleEvent.SkillTriggered>().any {
            it.source == owner && it.rootSkillId == 200687 && it.skillId == 210687
        })
        assertTrue(BattleStatus.INSIGHT in requireNotNull(engine.state.view.state(owner)).statuses)

        engine.finishRound(1)
        assertTrue(BattleStatus.INSIGHT !in requireNotNull(engine.state.view.state(owner)).statuses)
        assertTrue(
            engine.trigger(
                BattleTrigger.ACTION_BEFORE,
                context.copy(
                    round = 5,
                    trigger = BattleTrigger.ACTION_BEFORE,
                ),
            ).filterIsInstance<BattleEvent.SkillTriggered>()
                .none { it.rootSkillId == 200687 && it.skillId in setOf(211687, 212687) },
        )
    }

    @Test
    fun `sanjunduoshuai registers at preparation and responds once after a normal attack`() {
        val owner = hero(100705, 100, listOf(200987), position = 2)
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(owner)),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 10, position = 2).copy(
                            troops = 100_000,
                            maxTroops = 100_000,
                        ),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )
        val ownerRef = BattleHeroRef(Side.ATTACKER, owner.position, owner.id)

        assertEquals(
            1,
            result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.round == 0 && it.source == ownerRef && it.skillId == 200987 },
        )
        assertTrue(result.events.filterIsInstance<BattleEvent.SkillDamage>().none {
            it.round == 0 && it.source == ownerRef && it.skillId == 211987
        })
        assertEquals(
            1,
            result.events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.round == 1 && it.source == ownerRef && it.skillId == 211987 },
        )
        assertTrue(result.events.filterIsInstance<BattleEvent.SkillDamage>().any {
            it.round == 1 && it.source == ownerRef && it.skillId == 211987
        })
    }

    @Test
    fun `huoji pursuit deals burn damage immediately on its recorded target`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100620, 100, listOf(200722), position = 2)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.recordTarget(source, target)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200722,
            currentSkillId = 200722,
            trigger = BattleTrigger.PURSUIT_ATTEMPT,
            battleView = engine.state.view,
        )

        val events = engine.trigger(BattleTrigger.PURSUIT_ATTEMPT, context)

        val damage = events.filterIsInstance<BattleEvent.SkillDamage>().single {
            it.skillId == 200722 && it.effectId == 305
        }
        assertEquals(target, damage.target)
        assertTrue(damage.damage > 0)
        assertTrue(events.none { it is BattleEvent.OngoingDamage && it.skillId == 200722 })
    }

    @Test
    fun `heinei shize chooses one child from each pool and targets only enemies`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100024, 100, listOf(200847), position = 2)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 20, position = 1),
                    hero(200003, 10, position = 0),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200847,
            currentSkillId = 200847,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        val events = engine.trigger(BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        val childTriggers = events.filterIsInstance<BattleEvent.SkillTriggered>()
            .filter { it.skillId in 210847..217847 }
        assertEquals(2, childTriggers.size, "events=$events")
        assertEquals(
            setOf(1, 2),
            childTriggers.mapTo(linkedSetOf()) { triggered ->
                config.skillDetails(200847)
                    .filter { it.constantParam == triggered.skillId }
                    .map { it.selectFlag }
                    .distinct()
                    .single()
            },
        )
        assertTrue(
            events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.skillId in 210847..217847 }
                .all { it.target.side == Side.DEFENDER },
            "events=$events",
        )
    }

    @Test
    fun `qizuoguimou chooses one control family per successful cast`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100692, 100, listOf(200692), position = 2),
                    hero(100001, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 1),
                    hero(200002, 10, position = 2),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100692)
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200692,
            currentSkillId = 200692,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        val controls = engine.trigger(BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
            .filterIsInstance<BattleEvent.StatusApplied>()
            .filter {
                it.skillId == 200692 &&
                    it.status in setOf(
                        BattleStatus.CONFUSION,
                        BattleStatus.BERSERK,
                        BattleStatus.DISARM,
                        BattleStatus.HESITATION,
                    )
            }

        assertEquals(2, controls.size, "controls=$controls")
        assertEquals(1, controls.map(BattleEvent.StatusApplied::status).distinct().size)
    }

    @Test
    fun `zhongmou registers without preparation damage and checks every active attempt`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(
                        100024,
                        100,
                        listOf(200800, 200024, 200847),
                        position = 2,
                    ).copy(skillLevels = listOf(10, 10, 10)),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                    hero(200002, 20, position = 1).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                    hero(200003, 10, position = 0).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(baseContext)
        val attempts = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            baseContext.copy(
                round = 1,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillDamage && it.skillId == 211800
            },
            "preparation=$preparation",
        )
        assertEquals(
            2,
            attempts.filterIsInstance<BattleEvent.SkillDamage>()
                .count { it.skillId == 211800 },
            "attempts=$attempts",
        )
    }

    @Test
    fun `jiuzhan stacks strategy damage after each hit up to five layers`() {
        val ownerHero = hero(100807, 100, listOf(200959), position = 2).copy(
            stats = BattleStats(100, 100, 300, 100, 0, 5),
            skillLevels = listOf(10),
        )
        val sourceHero = hero(100024, 90, position = 1)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, sourceHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 2).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == sourceHero.id }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        engine.prepareBattle(baseContext)
        fun strategyModifier(): Int =
            engine.state.liveHero(source).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageDealtPercent::percent)

        assertEquals(0, strategyModifier())

        val hitContext = baseContext.copy(
            round = 1,
            source = source,
            trigger = BattleTrigger.DAMAGE_AFTER,
        )
        engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = source,
                    target = target,
                    amount = 1,
                    troopsAfter = 99_999,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 301,
                ),
            ),
            hitContext,
        )
        assertEquals(0, strategyModifier())

        repeat(6) {
            val targetTroops = requireNotNull(engine.state.view.state(target)).troops
            engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = source,
                        target = target,
                        amount = 1,
                        troopsAfter = targetTroops - 1,
                        school = DamageSchool.STRATEGY,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900001,
                        effectId = 302,
                    ),
                ),
                hitContext,
            )
        }

        val raw = config.skillDetails(200959).single()
        val layer = (
            (
                raw.constantParam +
                    raw.intelParam * ownerHero.stats.precise(BattleStat.STRATEGY) / 200.0
                ) *
                (
                    raw.initEffectRatio +
                        (ownerHero.skillLevels.single() - 1) *
                        (100 - raw.initEffectRatio) / 9.0
                    ) /
                100.0
            ).roundToInt()
        assertEquals(layer * 5, strategyModifier())
    }

    @Test
    fun `bengfa retriggers only the first successful pursuit without another probability roll`() {
        val owner = hero(100620, 100, listOf(200885), position = 2).copy(
            activeStatuses = setOf(BattleStatus.DOUBLE_ATTACK),
            equipment = listOf(BattleEquipmentSlot(400112, 1)),
        )
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(owner)),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 10, position = 2).copy(
                            troops = 100_000,
                            maxTroops = 100_000,
                        ),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )
        val ownerRef = BattleHeroRef(Side.ATTACKER, owner.position, owner.id)

        assertEquals(
            3,
            result.events.filterIsInstance<BattleEvent.SkillTriggered>().count {
                it.round == 1 && it.source == ownerRef && it.skillId == 200885
            },
        )
        assertEquals(
            3,
            result.events.filterIsInstance<BattleEvent.SkillDamage>().count {
                it.round == 1 && it.source == ownerRef && it.skillId == 200885
            },
        )
    }

    @Test
    fun `mouduan skips preparation for the second successful prepared inherent active cast`() {
        val ownerHero = hero(100451, 100, listOf(200769), position = 0).copy(
            skillLevels = listOf(10),
            equipment = listOf(BattleEquipmentSlot(400063, 1)),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 0).copy(
                        troops = 100_000,
                        maxTroops = 100_000,
                    ),
                ),
            ),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val baseContext = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(baseContext)
        val firstAttempt = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            baseContext.copy(
                round = 1,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        val firstCast = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            baseContext.copy(
                round = 2,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
        val secondAttempt = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            baseContext.copy(
                round = 3,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )

        assertEquals(
            2,
            firstAttempt.filterIsInstance<BattleEvent.SkillPreparationStarted>()
                .single { it.skillId == 200769 }
                .readyRound,
        )
        assertEquals(
            1,
            firstCast.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 200769 },
        )
        assertTrue(
            secondAttempt.none {
                it is BattleEvent.SkillPreparationStarted && it.skillId == 200769
            },
            "secondAttempt=$secondAttempt",
        )
        assertEquals(
            1,
            secondAttempt.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 200769 },
            "secondAttempt=$secondAttempt",
        )
    }

    @Test
    fun `polang stacks all dealt damage after hurt up to ten layers`() {
        val ownerHero = hero(100770, 100, position = 0).copy(
            troops = 100_000,
            maxTroops = 100_000,
            equipment = listOf(BattleEquipmentSlot(400111, 1)),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 200, position = 0))),
            defender = BattleTeam(listOf(ownerHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val attacker = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val owner = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = attacker,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )
        fun bonus(school: DamageSchool): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageDealtPercent::percent)

        val events = buildList {
            repeat(11) {
                addAll(
                    engine.applyNormalDamage(
                        round = 1,
                        source = attacker,
                        target = owner,
                        amount = 1,
                        context = context,
                    ),
                )
            }
        }

        assertEquals(100, bonus(DamageSchool.PHYSICAL))
        assertEquals(100, bonus(DamageSchool.STRATEGY))
        assertEquals(
            11,
            events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source == owner && it.skillId == 410111 },
        )
    }

    @Test
    fun `polang hurt layers expire before the next round`() {
        val ownerHero = hero(100770, 100, position = 0).copy(
            troops = 100_000,
            maxTroops = 100_000,
            equipment = listOf(BattleEquipmentSlot(400111, 1)),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 200, position = 0))),
            defender = BattleTeam(listOf(ownerHero)),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val attacker = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val owner = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = attacker,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )
        fun physicalBonus(): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageDealtPercent::percent)

        engine.applyNormalDamage(1, attacker, owner, 1, context)
        assertEquals(10, physicalBonus())

        engine.finishRound(1)
        assertEquals(0, physicalBonus())

        engine.applyNormalDamage(
            2,
            attacker,
            owner,
            1,
            context.copy(round = 2),
        )
        assertEquals(10, physicalBonus())
    }

    @Test
    fun `buqu stacks all damage reduction after each hurt`() {
        val ownerHero = hero(100770, 100, position = 0).copy(
            troops = 100_000,
            maxTroops = 100_000,
            modifiers = listOf(BattleModifier.HurtStackingDamageTakenPercent(3)),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 200, position = 0))),
            defender = BattleTeam(listOf(ownerHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val attacker = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val owner = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = attacker,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )
        fun reduction(school: DamageSchool): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageTakenPercent::percent)

        val first = engine.applyNormalDamage(1, attacker, owner, 100, context)
        assertEquals(99_900, requireNotNull(engine.state.view.state(owner)).troops)
        assertEquals(-3, reduction(DamageSchool.PHYSICAL))
        assertEquals(-3, reduction(DamageSchool.STRATEGY))

        val second = engine.applyNormalDamage(1, attacker, owner, 100, context)
        assertEquals(-6, reduction(DamageSchool.PHYSICAL))
        assertEquals(-6, reduction(DamageSchool.STRATEGY))
        assertEquals(
            2,
            (first + second).filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.source == owner && it.skillId == 451020 },
        )
    }

    @Test
    fun `buqu hurt layers expire before the next round`() {
        val ownerHero = hero(100770, 100, position = 0).copy(
            troops = 100_000,
            maxTroops = 100_000,
            modifiers = listOf(BattleModifier.HurtStackingDamageTakenPercent(3)),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 200, position = 0))),
            defender = BattleTeam(listOf(ownerHero)),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val attacker = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val owner = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = attacker,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )
        fun physicalReduction(): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == DamageSchool.PHYSICAL }
                .sumOf(BattleModifier.DamageTakenPercent::percent)

        engine.applyNormalDamage(1, attacker, owner, 1, context)
        assertEquals(-3, physicalReduction())

        engine.finishRound(1)
        assertEquals(0, physicalReduction())

        engine.applyNormalDamage(
            2,
            attacker,
            owner,
            1,
            context.copy(round = 2),
        )
        assertEquals(-3, physicalReduction())
    }

    @Test
    fun `ganzhi equipment feature disables only normal attacks`() {
        val ownerTeam = BattleTeamBuilder(
            config,
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100449,
                    position = 0,
                    troops = 10_000,
                    equipmentFeatureSkillIds = listOf(450036),
                    equipmentFeatureSkillLevels = listOf(12),
                ),
            ),
        )
        val request = BattleRequest(
            attacker = ownerTeam,
            defender = BattleTeam(listOf(hero(200001, 200, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.ACTION_BEFORE,
            battleView = engine.state.view,
        )

        val permission = engine.permissionFor(owner, context)

        assertEquals(true, permission.canAct)
        assertEquals(true, permission.canCastActive)
        assertEquals(false, permission.canNormalAttack)
        assertEquals(0, permission.normalAttackCount)
        assertEquals(false, permission.grantsPursuitOpportunityPerNormal)
    }

    @Test
    fun `buxie recovery bonus follows each full fifteen percent troop loss`() {
        val fixture = buxieFixture()
        val expectedByTroops = linkedMapOf(
            8_500 to 0,
            8_499 to 4,
            7_000 to 4,
            6_999 to 8,
            5_500 to 8,
            5_499 to 12,
            4_000 to 12,
            3_999 to 16,
            2_500 to 16,
            2_499 to 20,
            1_000 to 20,
            999 to 24,
        )

        expectedByTroops.forEach { (troops, expectedPercent) ->
            fixture.damageTo(troops)
            assertEquals(
                expectedPercent,
                fixture.recoveryTakenPercent(),
                "troops=$troops",
            )
        }
    }

    @Test
    fun `buxie recovery uses the current bonus before lowering its threshold layer`() {
        val fixture = buxieFixture()
        fixture.damageTo(5_499)
        assertEquals(12, fixture.recoveryTakenPercent())

        val events = fixture.recover(300)

        assertEquals(
            336,
            events.filterIsInstance<BattleEvent.Recovery>().single().amount,
        )
        assertEquals(5_835, fixture.engine.state.view.state(fixture.owner)?.troops)
        assertEquals(8, fixture.recoveryTakenPercent())
    }

    @Test
    fun `buxie never grants a next hit damage reduction`() {
        val fixture = buxieFixture()
        fixture.damageTo(8_499)

        fixture.recover(100)

        assertTrue(
            fixture.engine.liveHero(fixture.owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .none { it.school == null },
        )
        assertTrue(
            fixture.engine.state.effectStore.effectsFor(fixture.owner)
                .none { it.skillId == 451042 && it.remainingHits != null },
        )
    }

    @Test
    fun `xuanfeng refreshes one strategy damage bonus after normal attack`() {
        val ownerHero = hero(100028, 200, position = 0).copy(
            modifiers = listOf(
                BattleModifier.NextStrategyDamageAfterNormalAttackPercent(12),
            ),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 100, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            battleView = engine.state.view,
        )
        fun strategyBonus(): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageDealtPercent::percent)

        val first = engine.trigger(BattleTrigger.NORMAL_ATTACK_AFTER, context)
        engine.trigger(BattleTrigger.NORMAL_ATTACK_AFTER, context)
        assertEquals(12, strategyBonus())
        assertEquals(
            1,
            engine.state.effectStore.effectsFor(owner)
                .single { it.skillId == 451038 && it.effectId == 533 }
                .remainingHits,
        )
        assertTrue(first.any {
            it is BattleEvent.SkillTriggered &&
                it.rootSkillId == 450038 &&
                it.skillId == 451038
        })

        engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = owner,
                    target = enemy,
                    amount = 100,
                    troopsAfter = 9_900,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.NORMAL,
                    tags = emptySet(),
                    skillId = 0,
                    effectId = 0,
                ),
            ),
            context.copy(trigger = BattleTrigger.DAMAGE_BEFORE),
        )
        assertEquals(12, strategyBonus())

        val strategy = engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = owner,
                    target = enemy,
                    amount = 100,
                    troopsAfter = 9_800,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.PURSUIT,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 302,
                ),
            ),
            context.copy(trigger = BattleTrigger.DAMAGE_BEFORE),
        )
        assertEquals(0, strategyBonus())
        assertTrue(
            strategy.any {
                it is BattleEvent.EffectExpired &&
                    it.skillId == 451038 &&
                    it.effectId == 533
            },
            "strategy=$strategy",
        )
    }

    @Test
    fun `jiebei equipment feature reduction expires after its opening two rounds`() {
        val ownerTeam = BattleTeamBuilder(
            config,
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100770,
                    position = 0,
                    troops = 100_000,
                    equipmentFeatureSkillIds = listOf(450043),
                    equipmentFeatureSkillLevels = listOf(10),
                ),
            ),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 200, position = 0))),
            defender = ownerTeam,
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun reduction(school: DamageSchool): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageTakenPercent::percent)

        engine.prepareBattle(context)
        assertEquals(-10, reduction(DamageSchool.PHYSICAL))
        assertEquals(-10, reduction(DamageSchool.STRATEGY))

        engine.finishRound(1)
        assertEquals(-10, reduction(DamageSchool.PHYSICAL))
        assertEquals(-10, reduction(DamageSchool.STRATEGY))

        engine.finishRound(2)
        assertEquals(0, reduction(DamageSchool.PHYSICAL))
        assertEquals(0, reduction(DamageSchool.STRATEGY))
    }

    @Test
    fun `white clothes damage remains queued until its third round activation`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100035, 200, listOf(200648), position = 1)
                        .copy(skillLevels = listOf(10)),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 100, position = 0),
                    hero(200002, 100, position = 1),
                    hero(200003, 100, position = 2),
                ),
            ),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        val earlyDamageEffects = engine.state.view.heroes()
            .filter { it.side == Side.DEFENDER }
            .flatMap(engine.state.effectStore::effectsFor)
            .filter { it.skillId == 200648 && it.effectId == 302 }
        assertTrue(
            earlyDamageEffects.isEmpty(),
            "delayed damage must remain in the timing queue: effects=$earlyDamageEffects",
        )
        val earlyDues = engine.state.runtime.dueEffects(round = 1)
        assertTrue(
            earlyDues.isEmpty(),
            "round-one queue must not contain two-round delays: due=$earlyDues",
        )

        val roundStartEvents = buildList {
            engine.livingHeroesInSpeedOrder().forEach { actor ->
                addAll(
                    engine.trigger(
                        BattleTrigger.ROUND_START,
                        context.copy(
                            round = 1,
                            source = actor,
                            trigger = BattleTrigger.ROUND_START,
                        ),
                    ),
                )
            }
        }
        val roundStartDamageEffects = engine.state.view.heroes()
            .filter { it.side == Side.DEFENDER }
            .flatMap(engine.state.effectStore::effectsFor)
            .filter { it.skillId == 200648 && it.effectId == 302 }
        assertTrue(
            roundStartDamageEffects.isEmpty(),
            "round start must not activate delayed damage: " +
                "effects=$roundStartDamageEffects events=$roundStartEvents",
        )

        val roundOneEvents = buildList {
            engine.livingHeroesInSpeedOrder().forEach { actor ->
                addAll(
                    engine.trigger(
                        BattleTrigger.ACTION_BEFORE,
                        context.copy(
                            round = 1,
                            source = actor,
                            trigger = BattleTrigger.ACTION_BEFORE,
                        ),
                    ),
                )
            }
        }
        assertTrue(
            roundOneEvents.none {
                it is BattleEvent.SkillDamage &&
                    it.skillId == 200648 &&
                    it.effectId == 302
            },
            "round-one events must not activate delayed damage: events=$roundOneEvents",
        )

        fun executeRound(round: Int): List<BattleEvent> = buildList {
            engine.livingHeroesInSpeedOrder().forEach { actor ->
                addAll(
                    engine.trigger(
                        BattleTrigger.ROUND_START,
                        context.copy(
                            round = round,
                            source = actor,
                            trigger = BattleTrigger.ROUND_START,
                        ),
                    ),
                )
            }
            engine.livingHeroesInSpeedOrder().forEach { actor ->
                addAll(
                    engine.trigger(
                        BattleTrigger.ACTION_BEFORE,
                        context.copy(
                            round = round,
                            source = actor,
                            trigger = BattleTrigger.ACTION_BEFORE,
                        ),
                    ),
                )
            }
        }
        fun delayedDamage(events: List<BattleEvent>): List<BattleEvent.SkillDamage> =
            events.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.skillId == 200648 && it.effectId == 302 }

        engine.finishRound(1)
        val roundTwoEvents = executeRound(2)
        assertTrue(
            delayedDamage(roundTwoEvents).isEmpty(),
            "round-two events must retain the delay: events=$roundTwoEvents",
        )

        engine.finishRound(2)
        val roundThreeEvents = executeRound(3)
        assertEquals(
            3,
            delayedDamage(roundThreeEvents).size,
            "round-three events must activate one hit per target: events=$roundThreeEvents",
        )
    }

    @Test
    fun `round timed troop modifiers start with formal combat and expire on configured rounds`() {
        val ownerHero = hero(
            100001,
            200,
            listOf(296106, 296301),
            position = 0,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 100, position = 0))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun dealt(school: DamageSchool): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageDealtPercent::percent)
        fun taken(school: DamageSchool): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageTakenPercent>()
                .filter { it.school == school }
                .sumOf(BattleModifier.DamageTakenPercent::percent)
        fun startRound(round: Int) {
            engine.livingHeroesInSpeedOrder().forEach { actor ->
                engine.trigger(
                    BattleTrigger.ROUND_START,
                    context.copy(
                        round = round,
                        source = actor,
                        trigger = BattleTrigger.ROUND_START,
                    ),
                )
            }
        }

        engine.prepareBattle(context)
        assertEquals(0, dealt(DamageSchool.PHYSICAL))
        assertEquals(0, dealt(DamageSchool.STRATEGY))
        assertEquals(0, taken(DamageSchool.PHYSICAL))
        assertEquals(0, taken(DamageSchool.STRATEGY))

        startRound(1)
        assertEquals(15, dealt(DamageSchool.PHYSICAL))
        assertEquals(15, dealt(DamageSchool.STRATEGY))
        assertEquals(-60, taken(DamageSchool.PHYSICAL))
        assertEquals(-60, taken(DamageSchool.STRATEGY))

        engine.finishRound(1)
        assertEquals(15, dealt(DamageSchool.PHYSICAL))
        assertEquals(0, taken(DamageSchool.PHYSICAL))

        startRound(2)
        engine.finishRound(2)
        assertEquals(15, dealt(DamageSchool.STRATEGY))

        startRound(3)
        engine.finishRound(3)
        assertEquals(0, dealt(DamageSchool.PHYSICAL))
        assertEquals(0, dealt(DamageSchool.STRATEGY))
    }

    @Test
    fun `liangyuan recovers before owner actions in configured even rounds`() {
        val ownerHero = hero(100001, 200, listOf(296322), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero)),
            defender = BattleTeam(listOf(hero(200001, 100, position = 0))),
            maxRounds = 8,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)
        engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = owner,
            amount = 2_000,
            context = context.copy(
                round = 1,
                source = enemy,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )
        val actionEvents = (1..8).flatMap { round ->
            val actionContext = context.copy(
                round = round,
                trigger = BattleTrigger.ACTION_BEFORE,
            )
            engine.trigger(BattleTrigger.ACTION_BEFORE, actionContext) +
                engine.trigger(BattleTrigger.ACTION_BEFORE, actionContext)
        }

        assertTrue(
            preparation.none {
                it is BattleEvent.SkillTriggered && it.skillId == 297322 ||
                    it is BattleEvent.Recovery && it.skillId == 297322
            },
            "preparation=$preparation actionEvents=$actionEvents",
        )
        val triggers = actionEvents.filterIsInstance<BattleEvent.SkillTriggered>()
            .filter { it.skillId == 297322 }
        assertEquals(
            listOf(2, 4, 6, 8),
            triggers.map(BattleEvent.SkillTriggered::round),
            "preparation=$preparation actionEvents=$actionEvents",
        )
        triggers.forEach { event ->
            assertEquals(owner, event.source)
            assertEquals(296322, event.rootSkillId)
            assertEquals(BattleTrigger.BATTLE_PASSIVE, event.trigger)
        }
        val recoveries = actionEvents.filterIsInstance<BattleEvent.Recovery>()
            .filter { it.skillId == 297322 }
        assertEquals(
            listOf(2, 4, 6, 8),
            recoveries.map(BattleEvent.Recovery::round),
            "actionEvents=$actionEvents",
        )
        recoveries.forEach { event ->
            assertEquals(owner, event.source)
            assertEquals(owner, event.target)
            assertTrue(event.amount > 0, "event=$event")
        }
    }

    @Test
    fun `wentao grants one strategy damage bonus per round from round three`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100001, 200, listOf(296206), position = 0)),
            ),
            defender = BattleTeam(listOf(hero(200001, 100, position = 0))),
            maxRounds = 4,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )
        fun strategyDamage(round: Int): List<BattleEvent> {
            val targetTroops = requireNotNull(engine.state.view.state(target)).troops
            return engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = owner,
                        target = target,
                        amount = 1,
                        troopsAfter = targetTroops - 1,
                        school = DamageSchool.STRATEGY,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 302,
                    ),
                ),
                context.copy(
                    round = round,
                    source = owner,
                    trigger = BattleTrigger.DAMAGE_BEFORE,
                ),
            )
        }
        fun wentaoTriggers(events: List<BattleEvent>): Int =
            events.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.rootSkillId == 296206 && it.skillId == 297206 }

        engine.prepareBattle(context)
        assertEquals(
            0,
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.DamageDealtPercent>()
                .filter { it.school == DamageSchool.STRATEGY }
                .sumOf(BattleModifier.DamageDealtPercent::percent),
        )
        assertEquals(0, wentaoTriggers(strategyDamage(2)))
        assertEquals(1, wentaoTriggers(strategyDamage(3)))
        assertEquals(0, wentaoTriggers(strategyDamage(3)))
        assertEquals(1, wentaoTriggers(strategyDamage(4)))
    }

    @Test
    fun `xuanwei fires after every doubled normal attack for its first three rounds`() {
        val owner = hero(100620, 100, listOf(200885, 200233), position = 2).copy(
            skillLevels = listOf(10, 5),
            equipment = listOf(BattleEquipmentSlot(400112, 1)),
        )
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(owner)),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 10, position = 2).copy(
                            troops = 1_000_000,
                            maxTroops = 1_000_000,
                        ),
                    ),
                ),
                maxRounds = 3,
            ),
            config,
            FixedBattleRandom(0),
        )
        val triggersByRound = result.events
            .filterIsInstance<BattleEvent.SkillTriggered>()
            .filter { it.skillId == 200885 }
            .groupingBy(BattleEvent.SkillTriggered::round)
            .eachCount()

        assertEquals(mapOf(1 to 3, 2 to 2, 3 to 2), triggersByRound)
    }

    @Test
    fun `prepared command control registers its per round probability without rolling at setup`() {
        val owner = hero(100001, 100, listOf(200228), position = 2).copy(
            skillLevels = listOf(10),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(owner)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 20, position = 1),
                    hero(200003, 10, position = 0),
                ),
            ),
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val ownerRef = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(99),
            round = 0,
            source = ownerRef,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        val registrations = engine.state.view.heroes()
            .filter { it.side == Side.DEFENDER }
            .flatMap(engine.state.effectStore::effectsFor)
            .filter { it.detailId == 20022801 }
        assertEquals(2, registrations.size)
        assertTrue(registrations.all { it.strength == 90 })
    }

    @Test
    fun `qibu recovers allies on every seventh team normal or skill attempt`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100950, 100, listOf(200950), position = 2),
                    hero(100001, 90, position = 1).copy(troops = 9_000),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100950) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100001) }
        engine.state.mutable(ally).woundedTroops = 1_000
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = ally,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            battleView = engine.state.view,
        )

        repeat(6) {
            engine.state.runtime.recordBattleTriggerOccurrence(ally, BattleTrigger.NORMAL_ATTACK_AFTER)
            engine.trigger(BattleTrigger.NORMAL_ATTACK_AFTER, context)
        }
        engine.state.runtime.recordBattleTriggerOccurrence(ally, BattleTrigger.NORMAL_ATTACK_AFTER)
        val seventh = engine.trigger(BattleTrigger.NORMAL_ATTACK_AFTER, context)
        engine.state.runtime.recordBattleTriggerOccurrence(owner, BattleTrigger.NORMAL_ATTACK_AFTER)
        val eighth = engine.trigger(BattleTrigger.NORMAL_ATTACK_AFTER, context.copy(source = owner))

        assertEquals(1, seventh.filterIsInstance<BattleEvent.SkillTriggered>().count { it.skillId == 212950 })
        assertTrue(eighth.none { it is BattleEvent.SkillTriggered && it.skillId == 212950 })
        assertTrue(requireNotNull(engine.state.view.state(ally)).troops > 9_000)
    }

    @Test
    fun `huangtian recovers its caster only after its own sorcery damage ticks`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100008, 100, listOf(200008), position = 2).copy(troops = 9_000),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.state.mutable(source).woundedTroops = 1_000
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200008,
            currentSkillId = 200008,
            trigger = BattleTrigger.ROUND_START,
            battleView = engine.state.view,
        )
        val spec = PersistentEffectSpec(
            source = source,
            target = target,
            rootSkillId = 200008,
            skillId = 200008,
            skillKind = SkillKind.ACTIVE,
            rawSkillType = 3,
            detailId = 20000811,
            effectId = 306,
            category = com.stzb.battle.core.EffectCategory.HARMFUL,
            conflict = 306,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = 0,
            delayHit = 0,
            availableRounds = 2,
            availableHit = 0,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.rate(40),
        )
        engine.applyChanges(
            listOf(
                ScheduledDamageEffectChange(
                    spec = spec,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = setOf(com.stzb.battle.core.DamageTag.ONGOING),
                    status = com.stzb.battle.core.BattleStatus.HEX,
                    coefficientSource = BattleCoefficientSource.STRATEGY,
                    rawCoefficient = 350,
                    calculationTypes = emptyList(),
                ),
            ),
            context,
        )

        engine.trigger(BattleTrigger.ROUND_START, context)
        engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(
                source = target,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )

        assertTrue(requireNotNull(engine.state.view.state(source)).troops > 9_000)
    }

    @Test
    fun `xianming follows only the first ongoing damage suffered by each enemy in a round`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100784, 100, listOf(200254), position = 2)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 2),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val targets = engine.state.view.heroes().filter { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )

        val events = buildList {
            repeat(2) {
                addAll(
                    engine.applyChanges(
                        listOf(ongoingHit(owner, targets[0])),
                        context,
                    ),
                )
            }
            addAll(
                engine.applyChanges(
                    listOf(ongoingHit(owner, targets[1])),
                    context,
                ),
            )
        }

        assertEquals(
            2,
            events.filterIsInstance<BattleEvent.SkillDamage>().count { it.skillId == 212254 },
        )
    }

    @Test
    fun `xianming immediately ticks an accepted ongoing effect from round three`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100784, 100, listOf(200254), position = 2)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val before = requireNotNull(engine.state.view.state(target)).troops
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 3,
            source = owner,
            rootSkillId = 200254,
            currentSkillId = 200254,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.applyChanges(
            listOf(ongoingDamage(owner, target, detailId = 900011)),
            context,
        )

        assertTrue(requireNotNull(engine.state.view.state(target)).troops < before)
    }

    @Test
    fun `xianming does not immediately tick a conflict rejected ongoing effect`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100784, 100, listOf(200254), position = 2)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 2,
            source = owner,
            rootSkillId = 200254,
            currentSkillId = 200254,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.applyChanges(
            listOf(ongoingDamage(owner, target, detailId = 900021)),
            context,
        )
        val beforeRejected = requireNotNull(engine.state.view.state(target)).troops

        engine.applyChanges(
            listOf(ongoingDamage(owner, target, detailId = 900022)),
            context.copy(round = 3),
        )

        assertEquals(beforeRejected, requireNotNull(engine.state.view.state(target)).troops)
    }

    @Test
    fun `qixurulin splashes strategy damage only to enemies adjacent to the original target`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100282, 100, listOf(200282), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 20, position = 1),
                    hero(200003, 10, position = 0),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val original = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 1
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )

        val events = engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = source,
                    target = original,
                    amount = 200,
                    troopsAfter = 9_800,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 302,
                ),
            ),
            context,
        )

        val splash = events.filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.skillId == 210282 }
        assertEquals(setOf(0, 2), splash.mapTo(linkedSetOf()) { it.target.position })
        assertTrue(splash.none { it.target == original })
    }

    @Test
    fun `qixurulin scales the original damage rate and recalculates each adjacent target`() {
        val ownerHero = hero(100282, 100, listOf(200282), position = 2).copy(
            stats = BattleStats(100, 100, 300, 100, 0, 5),
            skillLevels = listOf(10),
        )
        val sourceHero = hero(100017, 90, position = 1).copy(
            stats = BattleStats(100, 100, 300, 90, 0, 5),
        )
        val rear = hero(200001, 30, position = 2).copy(
            stats = BattleStats(100, 100, 40, 30, 0, 5),
        )
        val originalHero = hero(200002, 20, position = 1)
        val front = hero(200003, 10, position = 0).copy(
            stats = BattleStats(100, 100, 400, 10, 0, 5),
        )
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    ownerHero,
                    sourceHero,
                ),
            ),
            defender = BattleTeam(listOf(rear, originalHero, front)),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == sourceHero.id }
        val original = engine.state.view.heroes().single { it.heroId == originalHero.id }
        val graph = SkillRuleCatalog.build(SkillScopeCatalog.loadDefault(), config)
        val splashDetail = graph.details.single { it.detailId == 20028212 }
        val raw = splashDetail.raw
        val levelRatio = raw.initEffectRatio +
            (ownerHero.skillLevels.single() - 1) * (100 - raw.initEffectRatio) / 9.0
        val splashPercent = (
            levelRatio *
                (
                    raw.constantParam +
                        raw.intelParam * ownerHero.stats.precise(BattleStat.STRATEGY) / 200.0
                    ) /
                100.0
            ).roundToInt()
        val originalCalculation = DirectDamageCalculation(
            ratePercent = 200,
            skillId = 900000,
        )
        val splashCalculation = DirectDamageCalculation(
            ratePercent = originalCalculation.ratePercent * splashPercent / 100,
            skillId = 210282,
        )
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )

        val events = engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = source,
                    target = original,
                    amount = 1,
                    troopsAfter = originalHero.troops - 1,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 302,
                    calculation = originalCalculation,
                ),
            ),
            context,
        )

        val actual = events.filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.skillId == 210282 }
            .associate { it.target.position to it.damage }
        val expected = mapOf(
            rear.position to splashCalculation.calculate(
                sourceHero,
                rear,
                DamageSchool.STRATEGY,
                DamageOrigin.ACTIVE,
                emptySet(),
            ),
            front.position to splashCalculation.calculate(
                sourceHero,
                front,
                DamageSchool.STRATEGY,
                DamageOrigin.ACTIVE,
                emptySet(),
            ),
        )

        assertEquals(expected, actual)
        assertTrue(actual.getValue(rear.position) > actual.getValue(front.position))
    }

    @Test
    fun `qixurulin value progression advances once at each round end`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100282, 100, listOf(200282), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100282)
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        assertEquals(
            0,
            engine.state.runtime.referencedValueDelta(owner, 200282, 20028212),
        )
        engine.finishRound(1)
        val firstIncrease =
            engine.state.runtime.referencedValueDelta(owner, 200282, 20028212)
        assertTrue(firstIncrease > 0)
        engine.finishRound(1)
        assertEquals(
            firstIncrease,
            engine.state.runtime.referencedValueDelta(owner, 200282, 20028212),
        )
        engine.finishRound(2)
        assertEquals(
            firstIncrease * 2,
            engine.state.runtime.referencedValueDelta(owner, 200282, 20028212),
        )
    }

    @Test
    fun `qixurulin command setup does not execute its conditional damage template`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100282, 100, listOf(200282), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 1),
                    hero(200002, 20, position = 0),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100282)
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        val troopsBefore = engine.state.view.heroes().associateWith {
            requireNotNull(engine.state.view.state(it)).troops
        }

        val events = engine.prepareBattle(context)

        assertEquals(
            troopsBefore,
            engine.state.view.heroes().associateWith {
                requireNotNull(engine.state.view.state(it)).troops
            },
            events.joinToString(separator = "\n"),
        )
    }

    @Test
    fun `jade seal releases the previous round absorption at its progressing percentage`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100262, 200, listOf(200262), position = 2),
                    hero(100001, 100, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(hero(200001, 10, position = 2)),
            ),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val ally = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 1
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val prepareEvents = engine.prepareBattle(context)

        assertTrue(
            prepareEvents.filterIsInstance<BattleEvent.SkillDamage>()
                .none { it.skillId == 211262 },
            "events=$prepareEvents",
        )
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )
        val allyBeforeRoundOneDamage = requireNotNull(engine.state.view.state(ally)).troops
        engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = ally,
            amount = 100,
            context = context.copy(round = 1, source = enemy),
        )
        val allyAfterRoundOneDamage = requireNotNull(engine.state.view.state(ally)).troops
        val roundOneAbsorbed = 100 - (allyBeforeRoundOneDamage - allyAfterRoundOneDamage)
        assertTrue(roundOneAbsorbed > 0)
        engine.finishRound(1)
        assertEquals(
            0,
            engine.state.runtime.referencedValueDelta(owner, 200262, 21126204),
        )

        val roundTwo = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 2, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            roundOneAbsorbed * 50 / 100,
            roundTwo.filterIsInstance<BattleEvent.SkillDamage>()
                .single { it.skillId == 211262 }
                .damage,
        )
        val allyBeforeRoundTwoDamage = requireNotNull(engine.state.view.state(ally)).troops
        engine.applyNormalDamage(
            round = 2,
            source = enemy,
            target = ally,
            amount = 100,
            context = context.copy(round = 2, source = enemy),
        )
        val allyAfterRoundTwoDamage = requireNotNull(engine.state.view.state(ally)).troops
        val roundTwoAbsorbed = 100 - (allyBeforeRoundTwoDamage - allyAfterRoundTwoDamage)
        engine.finishRound(2)
        assertEquals(
            10,
            engine.state.runtime.referencedValueDelta(owner, 200262, 21126204),
        )

        val roundThree = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 3, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            roundTwoAbsorbed * 60 / 100,
            roundThree.filterIsInstance<BattleEvent.SkillDamage>()
                .single { it.skillId == 211262 }
                .damage,
        )
    }

    @Test
    fun `jade seal release does not trigger qixurulin splash onto adjacent allies`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100262, 200, listOf(200262), position = 2),
                    hero(100282, 100, listOf(200282), position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(hero(200001, 10, position = 2)),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val jadeSealOwner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100262)
        }
        val qixurulinOwner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100282)
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = jadeSealOwner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )
        engine.applyNormalDamage(
            round = 1,
            source = enemy,
            target = qixurulinOwner,
            amount = 100,
            context = context.copy(round = 1, source = enemy),
        )
        engine.finishRound(1)
        val qixurulinTroopsBeforeRelease =
            requireNotNull(engine.state.view.state(qixurulinOwner)).troops

        val roundTwo = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 2, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            1,
            roundTwo.filterIsInstance<BattleEvent.SkillDamage>()
                .count { it.skillId == 211262 },
            "events=$roundTwo",
        )
        assertTrue(
            roundTwo.filterIsInstance<BattleEvent.SkillDamage>()
                .none { it.skillId == 210282 },
            "events=$roundTwo",
        )
        assertEquals(
            qixurulinTroopsBeforeRelease,
            requireNotNull(engine.state.view.state(qixurulinOwner)).troops,
            "events=$roundTwo",
        )
    }

    @Test
    fun `jade seal does not release damage that activates in the current round start`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100262, 200, listOf(200262), position = 2),
                    hero(100001, 100, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(hero(200001, 10, position = 2)),
            ),
            maxRounds = 3,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val ally = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 1
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 1, trigger = BattleTrigger.ROUND_START),
        )
        engine.schedule(
            ScheduledTimingChange(
                snapshot = DelayedEffect(
                    source = enemy,
                    rootSkillId = 10,
                    skillId = 10,
                    detailId = 1001,
                    dueRound = 0,
                ),
                delayRound = 1,
                delayHit = 0,
                change = TroopDamageChange(
                    source = enemy,
                    target = ally,
                    amount = 100,
                    troopsAfter = 9_900,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 10,
                    effectId = 301,
                ),
            ),
            round = 1,
        )
        engine.finishRound(1)
        val allyBefore = requireNotNull(engine.state.view.state(ally)).troops

        val roundTwo = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 2, trigger = BattleTrigger.ROUND_START),
        )

        assertTrue(
            roundTwo.filterIsInstance<BattleEvent.SkillDamage>()
                .none { it.skillId == 211262 },
            "events=$roundTwo",
        )
        val allyAfter = requireNotNull(engine.state.view.state(ally)).troops
        val absorbed = 100 - (allyBefore - allyAfter)
        assertTrue(absorbed > 0)
        engine.finishRound(2)

        val roundThree = engine.trigger(
            BattleTrigger.ROUND_START,
            context.copy(round = 3, trigger = BattleTrigger.ROUND_START),
        )

        assertEquals(
            absorbed * 60 / 100,
            roundThree.filterIsInstance<BattleEvent.SkillDamage>()
                .single { it.skillId == 211262 }
                .damage,
        )
    }

    @Test
    fun `qixurulin splash consumes its current referenced value`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100282, 100, listOf(200282), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 1),
                    hero(200002, 20, position = 0),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100282)
        }
        val source = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100017)
        }
        val original = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 1
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)

        fun splashDamage(round: Int): Int {
            val troops = requireNotNull(engine.state.view.state(original)).troops
            return engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = source,
                        target = original,
                        amount = 1_000,
                        troopsAfter = troops - 1_000,
                        school = DamageSchool.STRATEGY,
                        origin = DamageOrigin.ACTIVE,
                        tags = emptySet(),
                        skillId = 900000,
                        effectId = 302,
                    ),
                ),
                context.copy(round = round, source = source),
            ).filterIsInstance<BattleEvent.SkillDamage>()
                .single { it.skillId == 210282 }
                .damage
        }

        val roundOne = splashDamage(1)
        engine.finishRound(1)
        val increase =
            engine.state.runtime.referencedValueDelta(owner, 200282, 20028212)
        val roundTwo = splashDamage(2)

        assertEquals(roundOne + 1_000 * increase / 100, roundTwo)
    }

    @Test
    fun `juxian reacts before successful ally increases and enemy decreases from round one`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100269, 100, listOf(200269), position = 2),
                    hero(100017, 90, position = 1).copy(troops = 9_000),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100269) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.state.mutable(ally).woundedTroops = 1_000
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.EFFECT_APPLYING,
            battleView = engine.state.view,
        )

        val allyEvents = engine.applyChanges(
            listOf(statChange(owner, ally, 101, 10)),
            context,
        )
        val enemyEvents = engine.applyChanges(
            listOf(statChange(owner, enemy, 201, -10)),
            context,
        )

        assertTrue(allyEvents.filterIsInstance<BattleEvent.Recovery>().any { it.target == ally })
        assertTrue(
            enemyEvents.filterIsInstance<BattleEvent.SkillDamage>()
                .any { it.skillId == 214269 && it.target == enemy },
        )
    }

    @Test
    fun `juxian does not react to setup round stat changes`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100269, 100, listOf(200269), position = 2),
                    hero(100017, 90, position = 1).copy(troops = 9_000),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100269) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        engine.state.mutable(ally).woundedTroops = 1_000
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val events = engine.applyChanges(
            listOf(statChange(owner, ally, 101, 10)),
            context,
        )

        assertTrue(events.none { it is BattleEvent.Recovery })
    }

    @Test
    fun `chijie raises source offense and target defense before damage is applied`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100989, 100, listOf(200989), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(100989, 20, listOf(200989), position = 2),
                    hero(200001, 10, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val target = engine.state.view.heroes().single { it.heroId == BattleHeroId(200001) }
        val sourceBefore = requireNotNull(engine.state.view.state(source)).stats
        val targetBefore = requireNotNull(engine.state.view.state(target)).stats
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.DAMAGE_BEFORE,
            battleView = engine.state.view,
        )

        val events = engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = source,
                    target = target,
                    amount = 200,
                    troopsAfter = 9_800,
                    school = DamageSchool.STRATEGY,
                    origin = DamageOrigin.ACTIVE,
                    tags = emptySet(),
                    skillId = 900000,
                    effectId = 302,
                ),
            ),
            context,
        )

        val damageIndex = events.indexOfFirst { it is BattleEvent.SkillDamage }
        val statIndices = events.mapIndexedNotNull { index, event ->
            index.takeIf { event is BattleEvent.StatChanged }
        }
        assertTrue(statIndices.isNotEmpty())
        assertTrue(statIndices.all { it < damageIndex })
        assertTrue(requireNotNull(engine.state.view.state(source)).stats.strategy > sourceBefore.strategy)
        assertTrue(requireNotNull(engine.state.view.state(target)).stats.defense > targetBefore.defense)
    }

    @Test
    fun `chijie attack increase uses its configured owner attack coefficient`() {
        val ownerHero = hero(100989, 100, listOf(200989), position = 2).copy(
            stats = BattleStats.fromHundredths(
                attack = 28_690,
                defense = 20_000,
                strategy = 29_840,
                speed = 10_000,
                siege = 0,
                hitRange = 5,
            ),
            skillLevels = listOf(10),
        )
        val sourceHero = hero(100705, 90, position = 1).copy(
            stats = BattleStats.fromHundredths(
                attack = 19_260,
                defense = 19_160,
                strategy = 26_930,
                speed = 12_980,
                siege = 2_720,
                hitRange = 4,
            ),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, sourceHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == sourceHero.id }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_BEFORE,
            battleView = engine.state.view,
        )

        val events = engine.applyChanges(
            listOf(physicalDamage(source, target, 1)),
            context,
        )

        val increase = events.filterIsInstance<BattleEvent.StatChanged>().single {
            it.target == source && it.skillId == 213989
        }
        assertEquals(
            53.035,
            engine.state.effectStore.effectsFor(source)
                .single { it.detailId == 21398901 }
                .effectiveStrengthExact,
            0.001,
        )
        assertEquals(53.04, increase.deltaExact, 0.001)
    }

    @Test
    fun `chijie configured add count allows four total stat stacks`() {
        val ownerHero = hero(100989, 100, listOf(200989), position = 2).copy(
            stats = BattleStats.fromHundredths(
                attack = 28_690,
                defense = 20_000,
                strategy = 29_840,
                speed = 10_000,
                siege = 0,
                hitRange = 5,
            ),
            skillLevels = listOf(10),
        )
        val sourceHero = hero(100705, 90, position = 1).copy(
            stats = BattleStats.fromHundredths(
                attack = 19_260,
                defense = 19_160,
                strategy = 26_930,
                speed = 12_980,
                siege = 2_720,
                hitRange = 4,
            ),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, sourceHero)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == sourceHero.id }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_BEFORE,
            battleView = engine.state.view,
        )

        repeat(5) {
            engine.applyChanges(
                listOf(physicalDamage(source, target, 1)),
                context,
            )
        }

        assertEquals(
            404.74,
            requireNotNull(engine.state.view.state(source)).stats.precise(BattleStat.ATTACK),
            0.001,
        )
        assertEquals(
            4,
            engine.state.effectStore.effectsFor(source)
                .single { it.detailId == 21398901 }
                .stacks,
        )
    }

    @Test
    fun `chijie source increase affects the skill damage being applied`() {
        val ownerHero = hero(100989, 100, listOf(200989), position = 2).copy(
            stats = BattleStats(attack = 200, defense = 100, strategy = 100, speed = 100, siege = 0, hitRange = 5),
            skillLevels = listOf(10),
        )
        val actorHero = hero(100705, 90, listOf(200987), position = 1).copy(
            skillLevels = listOf(10),
        )
        val targetHero = hero(200001, 10, position = 2)
        val request = BattleRequest(
            attacker = BattleTeam(listOf(ownerHero, actorHero)),
            defender = BattleTeam(listOf(targetHero)),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.heroId == actorHero.id }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            battleView = engine.state.view,
        )

        val events = engine.trigger(BattleTrigger.NORMAL_ATTACK_AFTER, context)

        val increaseIndex = events.indexOfFirst {
            it is BattleEvent.StatChanged && it.skillId == 213989 && it.target == actor
        }
        val damageIndex = events.indexOfFirst {
            it is BattleEvent.SkillDamage && it.skillId == 211987
        }
        assertTrue(increaseIndex in 0 until damageIndex)
        assertEquals(
            BattleDamageCalculator.physical(
                source = actorHero.copy(
                    stats = BattleStats(
                        attack = 140,
                        defense = 100,
                        strategy = 100,
                        speed = 90,
                        siege = 0,
                        hitRange = 5,
                    ),
                ),
                target = targetHero,
                ratePercent = 180,
                attributeRandomTenths = 30,
                origin = DamageOrigin.PASSIVE,
            ),
            (events[damageIndex] as BattleEvent.SkillDamage).damage,
        )
    }

    @Test
    fun `chijie chooses attack instead of strategy for physical damage`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100989, 100, listOf(200989), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val before = requireNotNull(engine.state.view.state(source)).stats
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_BEFORE,
            battleView = engine.state.view,
        )

        engine.applyChanges(
            listOf(
                TroopDamageChange(
                    source = source,
                    target = target,
                    amount = 200,
                    troopsAfter = 9_800,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.NORMAL,
                    tags = emptySet(),
                    skillId = 0,
                    effectId = 0,
                ),
            ),
            context,
        )

        val after = requireNotNull(engine.state.view.state(source)).stats
        assertTrue(after.attack > before.attack)
        assertEquals(before.strategy, after.strategy)
    }

    @Test
    fun `configured battle applies chijie before a real normal attack`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100989, 200, listOf(200989), position = 2),
                        hero(100017, 190, position = 1),
                    ),
                ),
                defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        val normalIndex = result.events.indexOfFirst { it is BattleEvent.NormalAttack }
        val attackBuffIndex = result.events.indexOfFirst {
            it is BattleEvent.StatChanged &&
                it.stat == com.stzb.battle.core.BattleStat.ATTACK &&
                it.target.heroId == BattleHeroId(100989)
        }
        assertTrue(attackBuffIndex in 0 until normalIndex)
    }

    @Test
    fun `zhongke follows attack damage on its marked target at most twice`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100268, 100, listOf(200268), position = 2),
                    hero(100017, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100268) }
        val ally = engine.state.view.heroes().single { it.heroId == BattleHeroId(100017) }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.state.runtime.recordMarker(target, 20026811, 0, 1, 8)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = ally,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )

        val first = engine.applyNormalDamage(1, ally, target, 1, context)
        val second = engine.applyNormalDamage(1, ally, target, 1, context)
        val third = engine.applyNormalDamage(1, ally, target, 1, context)

        assertTrue(first.filterIsInstance<BattleEvent.SkillDamage>().any { it.source == owner })
        assertTrue(second.filterIsInstance<BattleEvent.SkillDamage>().any { it.source == owner })
        assertTrue(third.filterIsInstance<BattleEvent.SkillDamage>().none { it.source == owner })
    }

    @Test
    fun `tianzi applies its threshold effects after marked target is hurt twice in a round`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100270, 100, listOf(200270), position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.state.runtime.recordMarker(target, 21027012, 0, 1, 1)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )
        engine.applyNormalDamage(1, owner, target, 1, context)
        engine.applyNormalDamage(1, owner, target, 1, context)
        val before = requireNotNull(engine.state.view.state(target)).stats.copy()
        assertEquals(2, engine.state.runtime.roundHurtCount(target, 1))
        assertTrue(engine.state.runtime.hasMarker(target, 21027012, 1))
        assertTrue(200270 in engine.liveHero(owner).skillIds)

        val events = engine.trigger(
            BattleTrigger.ROUND_END,
            context.copy(trigger = BattleTrigger.ROUND_END),
        )

        val after = requireNotNull(engine.state.view.state(target)).stats
        assertTrue(events.filterIsInstance<BattleEvent.StatChanged>().isNotEmpty())
        assertTrue(after.attack < before.attack)
        assertTrue(after.defense < before.defense)
        assertTrue(after.strategy < before.strategy)
        assertTrue(after.speed < before.speed)
    }

    @Test
    fun `dingjun removes opening damage suppression on its owners fourth round action`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100293, 100, listOf(200293), position = 2))),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 4,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 4,
            source = owner,
            rootSkillId = 200293,
            currentSkillId = 200293,
            trigger = BattleTrigger.ACTION_BEFORE,
            battleView = engine.state.view,
        )

        val third = engine.trigger(BattleTrigger.ACTION_BEFORE, context.copy(round = 3))
        val fourth = engine.trigger(BattleTrigger.ACTION_BEFORE, context)

        assertTrue(third.none { it is BattleEvent.SkillTriggered && it.skillId == 210293 })
        assertTrue(fourth.any { it is BattleEvent.SkillTriggered && it.skillId == 210293 })
    }

    @Test
    fun `tongchou buffs only allies within one position after actual hurt`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100006, 100, listOf(201006), position = 2),
                    hero(100007, 90, position = 1),
                    hero(100008, 80, position = 0),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val hurt = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val near = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 1
        }
        val far = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = enemy,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )

        engine.applyNormalDamage(1, enemy, hurt, 1, context)

        assertTrue(engine.state.effectStore.effectsFor(hurt).any { it.skillId == 223006 })
        assertTrue(engine.state.effectStore.effectsFor(near).any { it.skillId == 223006 })
        assertTrue(engine.state.effectStore.effectsFor(far).none { it.skillId == 223006 })
    }

    @Test
    fun `tongchou preparation registers its derived listener on every ally`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(200001, 10, position = 2))),
            defender = BattleTeam(
                listOf(
                    hero(100006, 100, listOf(201006), position = 0),
                    hero(100007, 90, position = 1),
                    hero(100008, 80, position = 2),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100006) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        val listeners = engine.prepareBattle(context)
            .filterIsInstance<BattleEvent.SkillTriggered>()
            .filter { it.rootSkillId == 201006 && it.skillId == 221006 }

        assertEquals(
            listOf(0, 1, 2),
            listeners.map { it.source.position },
        )
    }

    @Test
    fun `tongchou does not react to damage before the first combat round`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100006, 100, listOf(201006), position = 2),
                    hero(100007, 90, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val hurt = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = enemy,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.DAMAGE_AFTER,
            battleView = engine.state.view,
        )

        val events = engine.applyNormalDamage(0, enemy, hurt, 1, context)

        assertTrue(
            events.filterIsInstance<BattleEvent.ModifierApplied>()
                .none { it.skillId == 223006 },
        )
    }

    @Test
    fun `fenji command setup emits its root trigger without executing its action chain`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100961, 200, listOf(200961), position = 2).copy(
                        skillLevels = listOf(10),
                    ),
                    hero(100001, 20, position = 1),
                    hero(100002, 10, position = 0),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 25, position = 1),
                    hero(200003, 15, position = 0),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100961) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        val troopsBefore = engine.state.view.heroes().associateWith {
            requireNotNull(engine.state.view.state(it)).troops
        }

        val events = engine.prepareBattle(context)

        assertEquals(
            1,
            events.filterIsInstance<BattleEvent.SkillTriggered>().count {
                it.round == 0 &&
                    it.source == owner &&
                    it.rootSkillId == 200961 &&
                    it.skillId == 200961 &&
                    it.trigger == BattleTrigger.BATTLE_COMMAND
            },
            "events=$events",
        )
        assertTrue(
            engine.state.effectStore.effectsFor(owner).none { it.detailId == 21396101 },
            "events=$events effects=${engine.state.effectStore.effectsFor(owner)}",
        )
        assertEquals(
            troopsBefore,
            engine.state.view.heroes().associateWith {
                requireNotNull(engine.state.view.state(it)).troops
            },
            "events=$events",
        )
        assertTrue(events.filterIsInstance<BattleEvent.SkillTriggered>().none {
            it.skillId in setOf(212961, 210961, 213961, 211961)
        })
    }

    @Test
    fun `fenji attacks at forty percent then starts a new damage stack`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100961, 200, listOf(200961), position = 2).copy(
                        skillLevels = listOf(10),
                    ),
                    hero(100001, 20, position = 1),
                    hero(100002, 10, position = 0),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 25, position = 1),
                    hero(200003, 15, position = 0),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100961) }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 200961,
            currentSkillId = 200961,
            trigger = BattleTrigger.ACTION_BEFORE,
            battleView = engine.state.view,
        )

        val events = engine.trigger(BattleTrigger.ACTION_BEFORE, context)

        val configuredSkillSequence = buildList {
            events.forEach { event ->
                when {
                    event is BattleEvent.SkillTriggered &&
                        event.skillId in setOf(212961, 210961) -> add(event.skillId)
                    event is BattleEvent.ModifierApplied &&
                        event.skillId == 213961 -> add(event.skillId)
                    event is BattleEvent.SkillDamage &&
                        event.skillId == 211961 &&
                        lastOrNull() != event.skillId -> add(event.skillId)
                }
            }
        }
        assertEquals(
            listOf(
                212961,
                210961, 213961,
                210961, 213961,
                210961, 213961,
                210961, 213961,
                210961, 213961, 211961,
                210961, 213961,
            ),
            configuredSkillSequence,
            "events=$events effects=${engine.state.effectStore.effectsFor(owner).map {
                listOf(it.detailId, it.stacks, it.maxStacks, it.effectiveStrength)
            }}",
        )
        val expectedDamage = BattleDamageCalculator.physical(
            source = request.attacker.heroes.first().copy(
                modifiers = listOf(
                    BattleModifier.DamageDealtPercent(
                        school = DamageSchool.PHYSICAL,
                        percent = 40,
                    ),
                ),
            ),
            target = request.defender.heroes.first(),
            ratePercent = 190,
            attributeRandomTenths = 30,
            origin = DamageOrigin.ACTIVE,
        )
        val attackEvents = events.filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.skillId == 211961 }
        assertTrue(attackEvents.isNotEmpty(), "events=$events")
        assertTrue(
            attackEvents.all { it.damage == expectedDamage },
            "expectedDamage=$expectedDamage attackEvents=$attackEvents",
        )
        val remaining = engine.state.effectStore.effectsFor(owner)
            .filter { it.detailId == 21396101 }
        assertEquals(1, remaining.size)
        assertEquals(8, remaining.single().effectiveStrength)
    }

    @Test
    fun `fenji group attack does not inherit its trigger history target`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100961, 200, listOf(200961), position = 2).copy(
                        skillLevels = listOf(10),
                    ),
                    hero(100001, 20, position = 1),
                    hero(100002, 10, position = 0),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 25, position = 1),
                    hero(200003, 15, position = 0),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.heroId == BattleHeroId(100961) }
        val historyTarget = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 2
        }
        engine.recordTarget(owner, historyTarget)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = owner,
            rootSkillId = 200961,
            currentSkillId = 200961,
            trigger = BattleTrigger.ACTION_BEFORE,
            battleView = engine.state.view,
        )

        val targets = engine.trigger(BattleTrigger.ACTION_BEFORE, context)
            .filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.skillId == 211961 }
            .map(BattleEvent.SkillDamage::target)

        assertEquals(2, targets.distinct().size, "targets=$targets")
    }

    @Test
    fun `same cast marker branches observe and consume earlier detail markers`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100003, 100, listOf(200003), position = 2)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 2).copy(
                        activeStatuses = setOf(com.stzb.battle.core.BattleStatus.CONFUSION),
                    ),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.recordTarget(source, target)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200003,
            currentSkillId = 200003,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        engine.trigger(BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertEquals(false, engine.state.runtime.hasMarker(target, 20000301, round = 1))
    }

    @Test
    fun `liehuo pursuit applies burn and consumes its target marker`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100251, 100, listOf(200251), position = 2)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 2),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val primary = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 2
        }
        engine.recordTarget(source, primary)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200251,
            currentSkillId = 200251,
            trigger = BattleTrigger.PURSUIT_ATTEMPT,
            battleView = engine.state.view,
        )

        val events = engine.trigger(BattleTrigger.PURSUIT_ATTEMPT, context)

        assertTrue(
            events.filterIsInstance<BattleEvent.StatusApplied>().any {
                it.target == primary &&
                    it.status == com.stzb.battle.core.BattleStatus.BURN
            },
        )
        assertEquals(false, engine.state.runtime.hasMarker(primary, 20025101, round = 1))
    }

    @Test
    fun `safe production engine executes known conditions instead of suppressing every conditional detail`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100885, 200, listOf(200885), position = 2)),
            ),
            defender = BattleTeam(
                listOf(hero(200001, 10, position = 2)),
            ),
            maxRounds = 4,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        engine.recordTarget(actor, target)
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 200885,
            currentSkillId = 200885,
            trigger = BattleTrigger.PURSUIT_ATTEMPT,
            battleView = engine.state.view,
        )

        val events = engine.trigger(BattleTrigger.PURSUIT_ATTEMPT, context)

        assertTrue(
            events.filterIsInstance<BattleEvent.SkillDamage>()
                .any { it.skillId == 200885 },
            "known cast_condition=104 must execute in the production-safe engine",
        )
    }

    @Test
    fun `complete engine routes fuwangyikou through its single plugin path`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100036, 100, listOf(200036), position = 0),
                    hero(100001, 150, listOf(200012), position = 1),
                    hero(100002, 200, listOf(200012), position = 2),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val middle = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 1
        }
        val front = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 200036,
            currentSkillId = 200036,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val commandEvents = engine.prepareBattle(context)

        assertEquals(
            setOf(middle, front),
            engine.state.effectStore.effectsFor(middle)
                .plus(engine.state.effectStore.effectsFor(front))
                .filter { it.skillId == 200036 && it.effectId == 352 }
                .mapTo(linkedSetOf()) { it.target },
        )
        assertEquals(
            1,
            commandEvents.filterIsInstance<BattleEvent.SkillTriggered>()
                .count { it.skillId == 200036 },
            "plugin and configured interpreter must not both execute 200036",
        )

        val attackBefore = engine.liveHero(front).stats.attack
        val strategyBefore = engine.liveHero(front).stats.strategy
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                round = 1,
                source = front,
                rootSkillId = 200012,
                currentSkillId = 200012,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )

        assertEquals(attackBefore + 11, engine.liveHero(front).stats.attack)
        assertEquals(strategyBefore + 13, engine.liveHero(front).stats.strategy)
    }

    @Test
    fun `prepared active completion also triggers fuwangyikou response`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100036, 100, listOf(200036), position = 0),
                    hero(100001, 200, listOf(200031), position = 2),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val actor = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 200036,
            currentSkillId = 200036,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                source = actor,
                round = 1,
                rootSkillId = 200031,
                currentSkillId = 200031,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        assertEquals(
            0,
            engine.state.runtime.count(actor, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 200031),
        )
        val attackBeforeCompletion = engine.liveHero(actor).stats.attack
        val strategyBeforeCompletion = engine.liveHero(actor).stats.strategy

        engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(
                source = actor,
                round = 2,
                rootSkillId = 200031,
                currentSkillId = 200031,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )

        assertEquals(attackBeforeCompletion + 11, engine.liveHero(actor).stats.attack)
        assertEquals(strategyBeforeCompletion + 13, engine.liveHero(actor).stats.strategy)
        assertEquals(
            1,
            engine.state.runtime.count(actor, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 200031),
        )
    }

    @Test
    fun `prepared active response applies after fenji resolution`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100036, 100, listOf(200036), position = 0),
                    hero(100961, 200, listOf(200031, 200961), position = 2).copy(
                        skillLevels = listOf(1, 10),
                    ),
                    hero(100001, 20, position = 1),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 25, position = 1),
                    hero(200003, 15, position = 0),
                ),
            ),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = actor,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                round = 1,
                rootSkillId = 200031,
                currentSkillId = 200031,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        val attackBeforeCompletion = engine.liveHero(actor).stats.attack

        val events = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(
                round = 2,
                rootSkillId = 200031,
                currentSkillId = 200031,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )

        val expectedFenjiDamage = BattleDamageCalculator.physical(
            source = request.attacker.heroes.single { it.position == 2 }.copy(
                modifiers = listOf(
                    BattleModifier.DamageDealtPercent(
                        school = DamageSchool.PHYSICAL,
                        percent = 40,
                    ),
                ),
            ),
            target = request.defender.heroes.first(),
            ratePercent = 190,
            attributeRandomTenths = 30,
            origin = DamageOrigin.ACTIVE,
        )
        val fenjiDamage = events.filterIsInstance<BattleEvent.SkillDamage>()
            .first { it.skillId == 211961 }
        assertEquals(expectedFenjiDamage, fenjiDamage.damage, "events=$events")
        assertEquals(attackBeforeCompletion + 11, engine.liveHero(actor).stats.attack)
    }

    @Test
    fun `cancelled prepared active never consumes fuwangyikou layer`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100036, 100, listOf(200036), position = 0),
                    hero(100001, 200, listOf(200031), position = 2),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val actor = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 200036,
            currentSkillId = 200036,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        engine.prepareBattle(context)
        val attackBefore = engine.liveHero(actor).stats.attack
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                source = actor,
                round = 1,
                rootSkillId = 200031,
                currentSkillId = 200031,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        engine.state.runtime.interruptPreparations(actor)
        engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(source = actor, round = 2, trigger = BattleTrigger.ACTION_BEFORE),
        )

        assertEquals(attackBefore, engine.liveHero(actor).stats.attack)
        assertEquals(
            0,
            engine.state.runtime.count(actor, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 200031),
        )
    }

    @Test
    fun `configured battle executes skill phases around the action in exact order`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(
                            id = 100479,
                            speed = 200,
                            skills = listOf(200009, 200014, 200012, 200206),
                        ),
                    ),
                ),
                defender = BattleTeam(listOf(hero(id = 1, speed = 10))),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        val attackerEvents = result.events.filter {
            when (it) {
                is BattleEvent.SkillTriggered -> it.source.side == Side.ATTACKER
                is BattleEvent.TriggerPoint -> it.source.side == Side.ATTACKER
                is BattleEvent.NormalAttack -> it.source.side == Side.ATTACKER
                else -> false
            }
        }
        val phases = attackerEvents.map {
            when (it) {
                is BattleEvent.SkillTriggered -> it.trigger
                is BattleEvent.TriggerPoint -> it.trigger
                is BattleEvent.NormalAttack -> "NORMAL_ATTACK"
                else -> error("unexpected $it")
            }
        }

        assertOrdered(
            phases,
            BattleTrigger.BATTLE_PASSIVE,
            BattleTrigger.BATTLE_COMMAND,
            BattleTrigger.ROUND_START,
            BattleTrigger.ACTION_BEFORE,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            BattleTrigger.NORMAL_ATTACK_BEFORE,
            "NORMAL_ATTACK",
            BattleTrigger.NORMAL_ATTACK_AFTER,
            BattleTrigger.PURSUIT_ATTEMPT,
            BattleTrigger.ACTION_AFTER,
            BattleTrigger.ROUND_END,
        )
    }

    @Test
    fun `configured battle records damage and hurt hooks around skill damage`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(hero(100479, 200, listOf(200012)))),
                defender = BattleTeam(listOf(hero(1, 10))),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        val damage = result.events.indexOfFirst { it is BattleEvent.SkillDamage }
        val before = result.events.indexOfFirst {
            it is BattleEvent.TriggerPoint && it.trigger == BattleTrigger.DAMAGE_BEFORE
        }
        val after = result.events.indexOfFirst {
            it is BattleEvent.TriggerPoint && it.trigger == BattleTrigger.DAMAGE_AFTER
        }
        val hurt = result.events.indexOfFirst {
            it is BattleEvent.TriggerPoint && it.trigger == BattleTrigger.HURT_AFTER
        }

        assertTrue(before in 0 until damage)
        assertTrue(after > damage)
        assertTrue(hurt > after)
    }

    @Test
    fun `every applied hit advances delay hit timing and dispatches damage hooks`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100479, 200))),
            defender = BattleTeam(listOf(hero(1, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )
        engine.trigger(BattleTrigger.ROUND_START, context.copy(trigger = BattleTrigger.ROUND_START))
        engine.schedule(
            ScheduledEffectActivationChange(
                PersistentEffectSpec(
                    source = source,
                    target = source,
                    rootSkillId = 1,
                    skillId = 1,
                    skillKind = SkillKind.PASSIVE,
                    rawSkillType = 1,
                    detailId = 101,
                    effectId = 544,
                    category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
                    conflict = 544,
                    replaceType = 0,
                    bindFlag = 0,
                    maxStacks = 1,
                    delayRound = 0,
                    delayHit = 1,
                    availableRounds = 2,
                    availableHit = 0,
                    clearPerHit = false,
                    startBoundary = EffectStartBoundary.AFTER_DELAY,
                    potency = TypedBattlePotency.rate(100),
                ),
                actionKind = ActionEffectKind.DOUBLE_ATTACK,
            ),
            round = 1,
        )

        val events = engine.applyNormalDamage(1, source, target, 1, context)

        assertEquals(TimingPosition(1, 1), engine.timingPosition())
        assertEquals(2, engine.permissionFor(source, context).normalAttackCount)
        assertEquals(1, engine.state.runtime.count(source, BattleTrigger.DAMAGE_AFTER))
        assertEquals(1, engine.state.runtime.count(target, BattleTrigger.HURT_AFTER))
        assertTrue(events.any {
            it is BattleEvent.TriggerPoint && it.source == source && it.trigger == BattleTrigger.DAMAGE_AFTER
        })
        assertTrue(events.any {
            it is BattleEvent.TriggerPoint && it.source == target && it.trigger == BattleTrigger.HURT_AFTER
        })
    }

    @Test
    fun `damage redirection does not redirect the protected hero normal attack target`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100001, 200, position = 0),
                    hero(100002, 100, position = 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val bearer = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100001)
        }
        val protected = engine.state.view.heroes().single {
            it.heroId == BattleHeroId(100002)
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = bearer,
            rootSkillId = 900000,
            currentSkillId = 900000,
            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
            battleView = engine.state.view,
        )
        val spec = PersistentEffectSpec(
            source = bearer,
            target = protected,
            rootSkillId = 900000,
            skillId = 900000,
            skillKind = SkillKind.COMMAND,
            rawSkillType = 2,
            detailId = 90000001,
            effectId = 506,
            category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
            conflict = 506,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = 0,
            delayHit = 0,
            availableRounds = 3,
            availableHit = 0,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.percent(100),
        )

        engine.applyChanges(
            listOf(
                DamageRedirectionEffectChange(
                    spec = spec,
                    protectedTargets = listOf(protected),
                    damageBearer = bearer,
                ),
            ),
            context,
        )

        assertEquals(
            null,
            engine.permissionFor(
                protected,
                context.copy(source = protected),
            ).redirectTarget,
        )
    }

    @Test
    fun `complete engine applies clear and reduce referenced effect changes`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100479, 200))),
            defender = BattleTeam(listOf(hero(1, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val spec = PersistentEffectSpec(
            source = source,
            target = target,
            rootSkillId = 1,
            skillId = 1,
            skillKind = SkillKind.PASSIVE,
            rawSkillType = 1,
            detailId = 201,
            effectId = 77,
            category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
            conflict = 77,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = 0,
            delayHit = 0,
            availableRounds = 2,
            availableHit = 2,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.flat(1),
        )
        engine.state.effectStore.apply(spec.toActiveSkillEffect())
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 1,
            currentSkillId = 1,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )

        engine.applyChanges(
            listOf(
                ReduceReferencedEffectUseChange(
                    source, target, 1, 1, 101, 201, 77, 1,
                    MetaEffectParameters.from(configRule(101, 313, effectParam = 201)),
                ),
            ),
            context,
        )
        assertEquals(1, engine.state.effectStore.effectsFor(target).single().remainingHits)

        engine.applyChanges(
            listOf(
                ClearReferencedEffectChange(
                    source, target, 1, 1, 102, 201, 77,
                    MetaEffectParameters.from(configRule(102, 152, effectParam = 201)),
                ),
            ),
            context,
        )
        assertTrue(engine.state.effectStore.effectsFor(target).isEmpty())
    }

    @Test
    fun `due change identity skips only the matching tail copy`() {
        val source = BattleHeroRef(Side.ATTACKER, 2, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 2, BattleHeroId(2))
        val spec = PersistentEffectSpec(
            source, target, 1, 1, SkillKind.PASSIVE, 1, 101, 544,
            com.stzb.battle.core.EffectCategory.BENEFICIAL,
            0, 0, 0, 2, 0, 1, 2, 0, false,
            EffectStartBoundary.AFTER_DELAY, TypedBattlePotency.rate(100),
        )
        val scheduled = ScheduledEffectActivationChange(spec, actionKind = ActionEffectKind.DOUBLE_ATTACK)
        val activated = scheduled.activationChanges().single()
        val due = SkillTimingDue.mint(
            scheduled,
            activatedChanges = listOf(activated),
            dueRound = 1,
            dueHit = 1,
            sequence = 1,
        )
        val result = SkillExecutionResult.immutable(
            stateChanges = listOf(activated, activated),
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
            timingDues = listOf(due),
        )

        assertEquals(listOf(false, true), result.dueChangeIndexMask().toList())
    }

    @Test
    fun `counterattack and secondary attack consume configured active effect strength`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100479, 200, listOf(200225), position = 2),
                        hero(100017, 10, position = 0),
                    ),
                ),
                defender = BattleTeam(
                    listOf(
                        hero(100010, 100, listOf(200010), position = 2),
                        hero(2, 20, position = 1),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        val split = result.events.filterIsInstance<BattleEvent.SkillDamage>()
            .first { it.effectId == 545 }
        val counter = result.events.filterIsInstance<BattleEvent.SkillDamage>()
            .first { it.effectId == 551 }
        assertEquals(200225, split.skillId)
        assertEquals(Side.ATTACKER, split.source.side)
        assertEquals(Side.DEFENDER, split.target.side)
        assertEquals(200010, counter.skillId)
        assertEquals(Side.DEFENDER, counter.source.side)
        assertEquals(Side.ATTACKER, counter.target.side)
        assertTrue(counter.damage > split.damage, "200% counter must exceed 75% secondary damage")
    }

    @Test
    fun `counterattack immunity prevents the defender counterattack`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100479, 200, position = 2).copy(
                            modifiers = listOf(BattleModifier.CounterattackImmunity),
                        ),
                        hero(100017, 10, position = 0),
                    ),
                ),
                defender = BattleTeam(
                    listOf(
                        hero(100010, 100, listOf(200010), position = 2),
                        hero(2, 20, position = 1),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        assertTrue(
            result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .any { it.source.side == Side.ATTACKER },
        )
        assertTrue(
            result.events.filterIsInstance<BattleEvent.SkillDamage>()
                .none {
                    it.effectId == 551 &&
                        it.source.side == Side.DEFENDER &&
                        it.target.heroId == BattleHeroId(100479)
                },
        )
    }

    @Test
    fun `troop scatter performs one secondary attack on the first normal attack`() {
        val attacker = BattleTeamBuilder(
            config,
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100017,
                    position = 2,
                    troops = 10_000,
                    extraSkillIds = listOf(200233),
                    skillLevels = listOf(10, 1),
                    troopFeatureIds = listOf(3108),
                ),
            ),
        )
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = attacker,
                defender = BattleTeam(
                    listOf(
                        hero(200001, 30, position = 0),
                        hero(200002, 20, position = 1),
                        hero(200003, 10, position = 2),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        val splits = result.events.filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.skillId == 297108 && it.effectId == 545 }
        assertEquals(1, splits.size)
    }

    @Test
    fun `split army splashes every enemy adjacent to the primary normal attack target`() {
        // Round-scoped 分兵 (effect 545) must splash a normal attack to EVERY living enemy
        // adjacent to the primary target (formation distance 1), not just one secondary.
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100479, 200, position = 1))),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 0),
                    hero(200002, 20, position = 1),
                    hero(200003, 10, position = 2),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val middle = engine.state.view.heroes()
            .single { it.side == Side.DEFENDER && it.position == 1 }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = source,
            rootSkillId = 200225,
            currentSkillId = 200225,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )
        // Seed a round-scoped 分兵 (545) on the source, matching 200225 横扫 semantics.
        engine.applyChanges(
            listOf(
                ApplyBattleEffectChange(
                    PersistentEffectSpec(
                        source = source,
                        target = source,
                        rootSkillId = 200225,
                        skillId = 210225,
                        skillKind = SkillKind.COMMAND,
                        rawSkillType = 2,
                        detailId = 21022501,
                        effectId = 545,
                        category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
                        conflict = 0,
                        replaceType = 0,
                        bindFlag = 0,
                        maxStacks = 1,
                        delayRound = 0,
                        delayHit = 0,
                        availableRounds = 1,
                        availableHit = 0,
                        clearPerHit = false,
                        startBoundary = EffectStartBoundary.IMMEDIATE,
                        potency = TypedBattlePotency.rate(75),
                    ),
                ),
            ),
            context,
        )

        // With the middle defender as the primary target, 分兵 must strike BOTH neighbours
        // (positions 0 and 2), closest-first, and never the primary itself.
        val splitTargets = engine.splitAttackTargets(source, middle).map { it.position }
        assertEquals(listOf(0, 2), splitTargets.sorted())
    }

    @Test
    fun `secondary attack runs damage before hooks before calculating damage`() {
        val actorHero = hero(100017, 190, listOf(200225), position = 1)
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100989, 200, listOf(200989), position = 2)
                            .copy(skillLevels = listOf(10)),
                        actorHero,
                    ),
                ),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 30, position = 1),
                        hero(200002, 20, position = 2),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )
        val actor = BattleHeroRef(Side.ATTACKER, actorHero.position, actorHero.id)
        val splitIndex = result.events.indexOfFirst {
            it is BattleEvent.SkillDamage && it.effectId == 545 && it.source == actor
        }
        val increases = result.events.mapIndexedNotNull { index, event ->
            index.takeIf {
                event is BattleEvent.StatChanged &&
                    event.skillId == 213989 &&
                    event.target == actor
            }
        }

        assertTrue(splitIndex >= 0)
        assertEquals(2, increases.size)
        assertTrue(increases.all { it < splitIndex })
    }

    @Test
    fun `configured battle uses one complete engine for command effects and all living positions`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100023, 60, listOf(200023), position = 0),
                        hero(100479, 50, position = 1),
                        hero(100017, 40, position = 2),
                    ),
                ),
                defender = BattleTeam(
                    listOf(
                        hero(1, 30, position = 0),
                        hero(2, 20, position = 1),
                        hero(3, 10, position = 2),
                    ),
                ),
                maxRounds = 1,
            ),
            config,
            FixedBattleRandom(0),
        )

        assertTrue(result.events.any {
            it is BattleEvent.SkillTriggered &&
                it.skillId == 200023 &&
                it.trigger == BattleTrigger.BATTLE_COMMAND
        })
        val rangeChanges = result.events.filterIsInstance<BattleEvent.SkillRangeChanged>()
        assertEquals(3, rangeChanges.size)
        assertTrue(rangeChanges.all { it.skillId == 200023 && it.skillKind == SkillKind.ACTIVE })
        assertTrue(rangeChanges.all { it.delta == 1 })
        assertEquals(
            setOf(
                Side.ATTACKER to 0,
                Side.ATTACKER to 1,
                Side.ATTACKER to 2,
                Side.DEFENDER to 0,
                Side.DEFENDER to 1,
                Side.DEFENDER to 2,
            ),
            result.events.filterIsInstance<BattleEvent.HeroActionStart>()
                .mapTo(linkedSetOf()) { it.source.side to it.source.position },
        )
    }

    @Test
    fun `all living heroes enter the action scheduler in every one of eight rounds`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(100001, 60, position = 0),
                        hero(100002, 50, position = 1),
                        hero(100003, 40, position = 2),
                    ),
                ),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 30, position = 0),
                        hero(200002, 20, position = 1),
                        hero(200003, 10, position = 2),
                    ),
                ),
                maxRounds = 8,
            ),
            config,
            FixedBattleRandom(0),
        )

        val starts = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
        val ends = result.events.filterIsInstance<BattleEvent.HeroActionEnd>()
        assertEquals(starts.map { it.round to it.source }, ends.map { it.round to it.source })
        val entryRefs = (requestHeroRefs(result.entryAttacker.orEmpty(), Side.ATTACKER) +
            requestHeroRefs(result.entryDefender.orEmpty(), Side.DEFENDER)).toSet()
        entryRefs.forEach { ref ->
            val survived = result.events.none {
                it is BattleEvent.NormalAttack && it.target == ref && it.targetTroopsAfter == 0 ||
                    it is BattleEvent.SkillDamage && it.target == ref && it.targetTroopsAfter == 0
            }
            if (survived) {
                assertEquals((1..8).toList(), starts.filter { it.source == ref }.map { it.round })
            }
        }
    }

    @Test
    fun `yibingbizhan only suppresses attacks for its configured first two rounds`() {
        val owner = hero(100701, 60, listOf(200761), position = 2).copy(
            stats = BattleStats(attack = 1, defense = 10_000, strategy = 100, speed = 60, siege = 0, hitRange = 5),
        )
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(owner)),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 10, position = 2).copy(
                            stats = BattleStats(attack = 1, defense = 10_000, strategy = 100, speed = 10, siege = 0, hitRange = 5),
                        ),
                    ),
                ),
                maxRounds = 8,
            ),
            config,
            FixedBattleRandom(0),
        )
        val ownerRef = BattleHeroRef(Side.ATTACKER, owner.position, owner.id)
        val actionRounds = result.events.filterIsInstance<BattleEvent.HeroActionStart>()
            .filter { it.source == ownerRef }
            .map { it.round }
        val normalRounds = result.events.filterIsInstance<BattleEvent.NormalAttack>()
            .filter { it.source == ownerRef }
            .map { it.round }

        assertEquals((1..8).toList(), actionRounds)
        assertEquals((3..8).toList(), normalRounds)
    }

    @Test
    fun `xingbingzhiji preparation probability increase changes later active rolls`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    hero(100003, 60, listOf(200813), position = 2),
                    hero(100006, 50, position = 1),
                    hero(100001, 40, listOf(200001), position = 0),
                ),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val actor = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 0
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(35),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)
        val events = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                round = 1,
                source = actor,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )

        assertTrue(engine.liveHero(actor).modifiers.any {
            it is com.stzb.battle.core.BattleModifier.SkillProbabilityPercent &&
                it.percent == 10
        })
        assertTrue(events.any { it is BattleEvent.SkillTriggered && it.skillId == 200001 })
    }

    @Test
    fun `calc position 311 damage resolves once before the selected targets next action`() {
        val sourceHero = hero(100035, 100, listOf(200684), position = 2).copy(
            troops = 10_000,
            maxTroops = 10_000,
            skillLevels = listOf(10),
        )
        val targetHero = hero(200001, 10, position = 2).copy(
            troops = 100_000,
            maxTroops = 100_000,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(sourceHero)),
            defender = BattleTeam(listOf(targetHero)),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val source = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        fun context(
            actor: BattleHeroRef,
            trigger: BattleTrigger,
        ) = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = trigger,
            battleView = engine.state.view,
        )

        val cast = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(source, BattleTrigger.ACTIVE_SKILL_ATTEMPT),
        )
        val troopsAfterCast = engine.liveHero(target).troops
        val firstAction = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context(target, BattleTrigger.ACTION_BEFORE),
        )
        val repeatedAction = engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context(target, BattleTrigger.ACTION_BEFORE),
        )

        assertTrue(cast.none { it is BattleEvent.SkillDamage }, "cast=$cast")
        assertEquals(targetHero.troops, troopsAfterCast)
        assertEquals(
            listOf(DamageSchool.STRATEGY, DamageSchool.PHYSICAL),
            firstAction.filterIsInstance<BattleEvent.SkillDamage>()
                .filter { it.skillId == 200684 }
                .map { event ->
                    when (event.effectId) {
                        302 -> DamageSchool.STRATEGY
                        301 -> DamageSchool.PHYSICAL
                        else -> error("unexpected effect=${event.effectId}")
                    }
                },
            "firstAction=$firstAction",
        )
        assertTrue(
            repeatedAction.none { it is BattleEvent.SkillDamage && it.skillId == 200684 },
            "repeatedAction=$repeatedAction",
        )
    }

    @Test
    fun `jinyanzhijian selects individual allied active skills instead of whole heroes`() {
        val advisor = hero(100692, 100, listOf(200966), position = 2)
        val firstAlly = hero(
            100479,
            90,
            listOf(200012, 200834),
            position = 1,
        )
        val secondAlly = hero(
            100035,
            80,
            listOf(200829, 200684),
            position = 0,
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(advisor, firstAlly, secondAlly)),
            defender = BattleTeam(listOf(hero(200001, 10, position = 2))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config, strict = true)
        val advisorRef = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == advisor.position
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(1),
            round = 0,
            source = advisorRef,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparationEvents = engine.prepareBattle(context)
        val preparationModifiers = engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER && it != advisorRef }
            .map(engine::liveHero)
            .flatMap(BattleHero::modifiers)
            .filterIsInstance<BattleModifier.SkillProbabilityPercent>()
        engine.livingHeroesInSpeedOrder().forEach { source ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(
                    round = 1,
                    source = source,
                    trigger = BattleTrigger.ROUND_START,
                ),
            )
        }
        engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(round = 1, trigger = BattleTrigger.ACTION_BEFORE),
        )

        val probabilityModifiers = engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER && it != advisorRef }
            .map(engine::liveHero)
            .flatMap(BattleHero::modifiers)
            .filterIsInstance<BattleModifier.SkillProbabilityPercent>()
            .filter { it.percent == 10 }
        assertEquals(
            2,
            probabilityModifiers.size,
            "events=$preparationEvents preparation=$preparationModifiers " +
                "afterAction=$probabilityModifiers",
        )
        assertEquals(2, probabilityModifiers.mapNotNull { it.skillId }.distinct().size)
        assertTrue(probabilityModifiers.all { it.skillKind == SkillKind.ACTIVE })
        val damageModifiers = engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER && it != advisorRef }
            .map(engine::liveHero)
            .flatMap(BattleHero::modifiers)
            .filterIsInstance<BattleModifier.DamageDealtPercent>()
            .filter { it.origin == DamageOrigin.ACTIVE && it.percent == 30 }
        assertEquals(2, damageModifiers.size)
        assertEquals(2, damageModifiers.mapNotNull { it.skillId }.distinct().size)

        val selected = engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER && it != advisorRef }
            .flatMap { owner ->
                engine.liveHero(owner).modifiers
                    .filterIsInstance<BattleModifier.SkillProbabilityPercent>()
                    .flatMap { modifier ->
                        (modifier.skillIds + listOfNotNull(modifier.skillId))
                            .map { skillId -> owner to skillId }
                    }
            }
            .distinct()
        val (selectedOwner, _) = selected.first()
        engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                round = 1,
                source = selectedOwner,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        engine.livingHeroesInSpeedOrder().forEach { source ->
            engine.trigger(
                BattleTrigger.ROUND_START,
                context.copy(
                    round = 2,
                    source = source,
                    trigger = BattleTrigger.ROUND_START,
                ),
            )
        }
        engine.trigger(
            BattleTrigger.ACTION_BEFORE,
            context.copy(round = 2, trigger = BattleTrigger.ACTION_BEFORE),
        )
        val nextRoundSelectedSkillIds = engine.state.view.heroes()
            .filter { it.side == Side.ATTACKER && it != advisorRef }
            .map(engine::liveHero)
            .flatMap(BattleHero::modifiers)
            .filterIsInstance<BattleModifier.SkillProbabilityPercent>()
            .flatMap { it.skillIds + listOfNotNull(it.skillId) }
            .distinct()

        assertEquals(3, nextRoundSelectedSkillIds.size)
    }

    @Test
    fun `shared probability use is consumed through engine when active skill rolls`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100001, 100, listOf(200049), position = 0)),
            ),
            defender = BattleTeam(listOf(hero(200001, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 200049,
            currentSkillId = 200049,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )
        fun groupedSpec(
            detailId: Int,
            effectId: Int,
        ) = PersistentEffectSpec(
            source = actor,
            target = actor,
            rootSkillId = 200293,
            skillId = 211293,
            skillKind = SkillKind.COMMAND,
            rawSkillType = 2,
            detailId = detailId,
            effectId = effectId,
            category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
            conflict = 0,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = 0,
            delayHit = 0,
            availableRounds = 0,
            availableHit = 1,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.percent(100),
        )
        engine.applyChanges(
            listOf(
                ModifierEffectChange(
                    groupedSpec(21129311, 131),
                    com.stzb.battle.core.BattleModifier.SkillProbabilityPercent(
                        percent = 100,
                        skillKind = SkillKind.ACTIVE,
                    ),
                ),
                ModifierEffectChange(
                    groupedSpec(21129312, 131),
                    com.stzb.battle.core.BattleModifier.SkillProbabilityPercent(
                        percent = 100,
                        skillKind = SkillKind.PURSUIT,
                    ),
                ),
                SharedEffectUseGroupChange(
                    groupedSpec(21129318, 88),
                    memberDetailId = 21129311,
                ),
                SharedEffectUseGroupChange(
                    groupedSpec(21129319, 88),
                    memberDetailId = 21129312,
                ),
            ),
            context,
        )
        assertEquals(
            listOf(131, 131, 88, 88),
            engine.state.effectStore.effectsFor(actor).map { it.effectId },
        )

        engine.trigger(BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertTrue(engine.state.effectStore.effectsFor(actor).none {
            it.effectId == 131 || it.effectId == 88
        })
    }

    @Test
    fun `dingjun forced normal attack selects enemy base on fourth round and consumes once`() {
        val owner = hero(100810, 100, listOf(200293), position = 2).copy(
            stats = BattleStats(
                attack = 1,
                defense = 10_000,
                strategy = 100,
                speed = 100,
                siege = 0,
                hitRange = 1,
            ),
        )
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(listOf(owner)),
                defender = BattleTeam(
                    listOf(
                        hero(200001, 20, position = 0).copy(
                            stats = BattleStats(1, 10_000, 100, 20, 0, 5),
                        ),
                        hero(200002, 10, position = 2).copy(
                            stats = BattleStats(1, 10_000, 100, 10, 0, 5),
                        ),
                    ),
                ),
                maxRounds = 5,
            ),
            config,
            FixedBattleRandom(0),
        )
        val ownerRef = BattleHeroRef(Side.ATTACKER, owner.position, owner.id)

        assertEquals(
            listOf(
                1 to 2,
                2 to 2,
                3 to 2,
                4 to 0,
                5 to 2,
            ),
            result.events.filterIsInstance<BattleEvent.NormalAttack>()
                .filter { it.source == ownerRef }
                .map { it.round to it.target.position },
        )
    }

    @Test
    fun `joint attack redirects the first active damage beyond skill range and consumes once`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100001, 100, listOf(200049), position = 0)),
            ),
            defender = BattleTeam(
                listOf(
                    hero(200001, 10, position = 0),
                    hero(200002, 20, position = 2),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemyBase = engine.state.view.heroes().single {
            it.side == Side.DEFENDER && it.position == 0
        }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 200049,
            currentSkillId = 200049,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = engine.state.view,
        )
        engine.applyChanges(
            listOf(
                ForcedTargetEffectChange(
                    spec = PersistentEffectSpec(
                        source = actor,
                        target = actor,
                        rootSkillId = 200293,
                        skillId = 211293,
                        skillKind = SkillKind.COMMAND,
                        rawSkillType = 2,
                        detailId = 21129316,
                        effectId = 81,
                        category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
                        conflict = 0,
                        replaceType = 0,
                        bindFlag = 0,
                        maxStacks = 1,
                        delayRound = 0,
                        delayHit = 0,
                        availableRounds = 0,
                        availableHit = 1,
                        clearPerHit = false,
                        startBoundary = EffectStartBoundary.IMMEDIATE,
                        potency = TypedBattlePotency.percent(100),
                    ),
                    forcedTarget = enemyBase,
                ),
            ),
            context,
        )

        val events = engine.trigger(BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertTrue(events.filterIsInstance<BattleEvent.SkillDamage>().any {
            it.skillId == 200049 && it.target == enemyBase
        })
        assertTrue(engine.state.effectStore.effectsFor(actor).none { it.effectId == 81 })
    }

    @Test
    fun `next control duration modifier extends one successful control and then expires`() {
        val request = BattleRequest(
            attacker = BattleTeam(listOf(hero(100001, 100, listOf(200049)))),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 200049,
            currentSkillId = 200049,
            trigger = BattleTrigger.EFFECT_APPLYING,
            battleView = engine.state.view,
        )
        engine.applyChanges(
            listOf(
                ModifierEffectChange(
                    spec = PersistentEffectSpec(
                        source = actor,
                        target = actor,
                        rootSkillId = 470044,
                        skillId = 471044,
                        skillKind = SkillKind.PASSIVE,
                        rawSkillType = 19,
                        detailId = 47104401,
                        effectId = 312,
                        category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
                        conflict = 0,
                        replaceType = 0,
                        bindFlag = 0,
                        maxStacks = 1,
                        delayRound = 0,
                        delayHit = 0,
                        availableRounds = 0,
                        availableHit = 1,
                        clearPerHit = false,
                        startBoundary = EffectStartBoundary.IMMEDIATE,
                        potency = TypedBattlePotency.flat(1),
                    ),
                    modifier = BattleModifier.ControlDurationIncrease(
                        rounds = 1,
                        mainSkillOnly = false,
                    ),
                ),
            ),
            context,
        )

        engine.applyChanges(
            listOf(controlChange(actor, enemy).copy(spec = controlChange(actor, enemy).spec.copy(
                rootSkillId = 200049,
                skillId = 200049,
                detailId = 20004991,
            ))),
            context,
        )

        assertEquals(
            2,
            engine.state.effectStore.effectsFor(enemy)
                .single { it.detailId == 20004991 }
                .remainingRounds,
        )
        assertTrue(engine.state.effectStore.effectsFor(actor).none { it.effectId == 312 })

        engine.applyChanges(
            listOf(controlChange(actor, enemy).copy(spec = controlChange(actor, enemy).spec.copy(
                rootSkillId = 200049,
                skillId = 200049,
                detailId = 20004992,
                effectId = 502,
                conflict = 502,
            ))),
            context,
        )
        assertEquals(
            1,
            engine.state.effectStore.effectsFor(enemy)
                .single { it.detailId == 20004992 }
                .remainingRounds,
        )
    }

    @Test
    fun `main skill control duration modifier ignores non main controls without consuming`() {
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100001, 100, listOf(200049, 200001))),
            ),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 200049,
            currentSkillId = 200049,
            trigger = BattleTrigger.EFFECT_APPLYING,
            battleView = engine.state.view,
        )
        engine.applyChanges(
            listOf(
                controlDurationModifier(
                    source = actor,
                    effectId = 311,
                    detailId = 46106101,
                    modifier = BattleModifier.ControlDurationIncrease(
                        rounds = 1,
                        mainSkillOnly = true,
                    ),
                ),
            ),
            context,
        )

        engine.applyChanges(
            listOf(controlChange(actor, enemy).copy(spec = controlChange(actor, enemy).spec.copy(
                rootSkillId = 200001,
                skillId = 200001,
                detailId = 20000191,
            ))),
            context,
        )

        assertEquals(
            1,
            engine.state.effectStore.effectsFor(enemy)
                .single { it.detailId == 20000191 }
                .remainingRounds,
        )
        assertTrue(engine.state.effectStore.effectsFor(actor).any { it.effectId == 311 })

        engine.applyChanges(
            listOf(controlChange(actor, enemy).copy(spec = controlChange(actor, enemy).spec.copy(
                rootSkillId = 200049,
                skillId = 200049,
                detailId = 20004992,
                effectId = 502,
                conflict = 502,
            ))),
            context,
        )
        assertEquals(
            2,
            engine.state.effectStore.effectsFor(enemy)
                .single { it.detailId == 20004992 }
                .remainingRounds,
        )
        assertTrue(engine.state.effectStore.effectsFor(actor).none { it.effectId == 311 })
    }

    @Test
    fun `huoyan equipment feature extends only the first inherent control`() {
        val ownerTeam = BattleTeamBuilder(
            config,
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100692,
                    position = 0,
                    troops = 10_000,
                    equipmentFeatureSkillIds = listOf(460061),
                    equipmentFeatureSkillLevels = listOf(1),
                ),
            ),
        )
        val request = BattleRequest(
            attacker = ownerTeam,
            defender = BattleTeam(
                listOf(
                    hero(200001, 20, position = 0),
                    hero(200002, 10, position = 1),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val targets = engine.state.view.heroes()
            .filter { it.side == Side.DEFENDER }
            .sortedBy(BattleHeroRef::position)
        val mainSkillId = engine.liveHero(owner).skillIds.first()
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)
        fun applyControl(target: BattleHeroRef, detailId: Int) {
            val base = controlChange(owner, target)
            engine.applyChanges(
                listOf(
                    base.copy(
                        spec = base.spec.copy(
                            rootSkillId = mainSkillId,
                            skillId = mainSkillId,
                            detailId = detailId,
                        ),
                    ),
                ),
                context.copy(
                    round = 1,
                    rootSkillId = mainSkillId,
                    currentSkillId = mainSkillId,
                    trigger = BattleTrigger.EFFECT_APPLYING,
                ),
            )
        }

        applyControl(targets[0], 20069291)
        applyControl(targets[1], 20069292)

        assertEquals(
            2,
            engine.state.effectStore.effectsFor(targets[0])
                .single { it.detailId == 20069291 }
                .remainingRounds,
        )
        assertEquals(
            1,
            engine.state.effectStore.effectsFor(targets[1])
                .single { it.detailId == 20069292 }
                .remainingRounds,
        )
        assertTrue(engine.state.effectStore.effectsFor(owner).none { it.effectId == 311 })
    }

    @Test
    fun `simulated normal attacks reuse range targeting and normal attack hooks`() {
        val attacker = hero(100001, 100, position = 2).copy(
            stats = BattleStats(
                attack = 100,
                defense = 100,
                strategy = 100,
                speed = 100,
                siege = 0,
                hitRange = 2,
            ),
        )
        val request = BattleRequest(
            attacker = BattleTeam(listOf(attacker)),
            defender = BattleTeam(
                listOf(
                    hero(200001, 30, position = 2),
                    hero(200002, 20, position = 1),
                    hero(200003, 10, position = 0),
                ),
            ),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 1,
            source = actor,
            rootSkillId = 410113,
            currentSkillId = 410113,
            trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            battleView = engine.state.view,
        )

        val allEvents = engine.applyChanges(
            listOf(
                SimulatedNormalAttackChange(
                    source = actor,
                    mode = SimulatedNormalAttackMode.ALL_IN_RANGE,
                    skillId = 410113,
                    effectId = 80,
                    detailId = 41011321,
                ),
            ),
            context,
        )
        val allAttacks = allEvents.filterIsInstance<BattleEvent.NormalAttack>()
        assertEquals(setOf(2, 1), allAttacks.mapTo(mutableSetOf()) { it.target.position })
        assertEquals(
            2,
            allEvents.filterIsInstance<BattleEvent.TriggerPoint>()
                .count { it.trigger == BattleTrigger.NORMAL_ATTACK_BEFORE },
        )
        assertEquals(
            2,
            allEvents.filterIsInstance<BattleEvent.TriggerPoint>()
                .count { it.trigger == BattleTrigger.NORMAL_ATTACK_AFTER },
        )

        val singleEvents = engine.applyChanges(
            listOf(
                SimulatedNormalAttackChange(
                    source = actor,
                    mode = SimulatedNormalAttackMode.SINGLE,
                    skillId = 411112,
                    effectId = 79,
                    detailId = 41111211,
                ),
            ),
            context,
        )
        assertEquals(1, singleEvents.filterIsInstance<BattleEvent.NormalAttack>().size)
    }

    @Test
    fun `real advisor unlock enables the configured locked skill detail`() {
        val lockedRequest = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100001, 100, listOf(200126))),
            ),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 1,
        )
        val lockedEngine = DefaultCompleteSkillEngine.create(lockedRequest, config)
        val lockedActor = lockedEngine.state.view.heroes().single { it.side == Side.ATTACKER }
        val lockedContext = SkillBattleContext(
            request = lockedRequest,
            runtime = lockedEngine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = lockedActor,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = lockedEngine.state.view,
        )
        lockedEngine.prepareBattle(lockedContext)
        val lockedEvents = lockedEngine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            lockedContext.copy(
                round = 1,
                rootSkillId = 200126,
                currentSkillId = 200126,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        assertEquals(
            1,
            lockedEvents.filterIsInstance<BattleEvent.SkillDamage>()
                .count { it.skillId == 200126 && it.effectId == 302 },
            "events=$lockedEvents",
        )

        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(hero(100001, 100, listOf(200870, 200126))),
            ),
            defender = BattleTeam(listOf(hero(200001, 10))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val actor = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = actor,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            battleView = engine.state.view,
        )

        engine.prepareBattle(context)

        assertTrue(
            engine.liveHero(actor).modifiers.contains(
                BattleModifier.SkillEnhancementUnlock(200870),
            ),
        )
        val events = engine.trigger(
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context.copy(
                round = 1,
                rootSkillId = 200126,
                currentSkillId = 200126,
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            ),
        )
        assertEquals(
            2,
            events.filterIsInstance<BattleEvent.SkillDamage>()
                .count { it.skillId == 200126 && it.effectId == 302 },
            "events=$events",
        )
    }

    @Test
    fun `hezonglianheng applies unrecoverable only after an ally normal attacks its current target`() {
        val ownerHero = hero(100603, 100, listOf(200964), position = 0)
            .copy(skillLevels = listOf(10))
        val request = BattleRequest(
            attacker = BattleTeam(
                listOf(
                    ownerHero,
                    hero(100016, 90, position = 1),
                    hero(100004, 80, position = 2),
                ),
            ),
            defender = BattleTeam(listOf(hero(100620, 10, position = 0))),
            maxRounds = 1,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.heroId == ownerHero.id
        }
        val actor = engine.state.view.heroes().single {
            it.side == Side.ATTACKER && it.position == 2
        }
        val target = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        val context = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = FixedBattleRandom(0),
            round = 0,
            source = owner,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = engine.state.view,
        )

        val preparation = engine.prepareBattle(context)

        assertTrue(207 !in engine.state.view.activeEffectIds(target))
        assertEquals(
            setOf(0, 1, 2),
            preparation.filterIsInstance<BattleEvent.SkillRangeChanged>()
                .filter { it.skillId == 200964 && it.skillKind == SkillKind.ACTIVE }
                .map { it.target.position }
                .toSet(),
        )

        engine.recordTarget(actor, target)
        val events = engine.trigger(
            BattleTrigger.NORMAL_ATTACK_AFTER,
            context.copy(
                source = actor,
                round = 1,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            ),
        )

        assertTrue(207 in engine.state.view.activeEffectIds(target), "events=$events")
        assertTrue(
            events.filterIsInstance<BattleEvent.SkillTriggered>().any {
                it.source == owner &&
                    it.rootSkillId == 200964 &&
                    it.skillId == 220964 &&
                    it.trigger == BattleTrigger.NORMAL_ATTACK_AFTER
            },
            "events=$events",
        )
    }

    private fun requestHeroRefs(team: BattleTeam, side: Side): List<BattleHeroRef> =
        team.heroes.map { BattleHeroRef(side, it.position, it.id) }

    private fun BattleTeam?.orEmpty(): BattleTeam = this ?: BattleTeam(emptyList())

    private fun assertOrdered(actual: List<Any>, vararg expected: Any) {
        var previous = -1
        expected.forEach { value ->
            val index = actual.indexOfFirst { it == value }
            assertTrue(index > previous, "expected $value after index $previous, actual=$actual")
            previous = index
        }
    }

    private fun hero(
        id: Int,
        speed: Int,
        skills: List<Int> = emptyList(),
        position: Int = 2,
    ) = BattleHero(
        id = BattleHeroId(id),
        position = position,
        stats = BattleStats(attack = 100, defense = 100, strategy = 100, speed = speed, siege = 0, hitRange = 5),
        troops = 10_000,
        maxTroops = 10_000,
        skillIds = skills,
    )

    private data class BuxieFixture(
        val engine: DefaultCompleteSkillEngine,
        val owner: BattleHeroRef,
        val enemy: BattleHeroRef,
        val context: SkillBattleContext,
    ) {
        fun damageTo(troops: Int): List<BattleEvent> {
            val currentTroops = requireNotNull(engine.state.view.state(owner)).troops
            require(troops in 0..currentTroops)
            return engine.applyChanges(
                listOf(
                    TroopDamageChange(
                        source = enemy,
                        target = owner,
                        amount = currentTroops - troops,
                        troopsAfter = troops,
                        school = DamageSchool.PHYSICAL,
                        origin = DamageOrigin.NORMAL,
                        tags = emptySet(),
                        skillId = 0,
                        effectId = 0,
                    ),
                ),
                context,
            )
        }

        fun recover(amount: Int): List<BattleEvent> {
            val troops = requireNotNull(engine.state.view.state(owner)).troops
            return engine.applyChanges(
                listOf(
                    TroopRecoveryChange(
                        source = owner,
                        target = owner,
                        amount = amount,
                        troopsAfter = troops + amount,
                        skillId = 214252,
                        effectId = 401,
                    ),
                ),
                context.copy(
                    source = owner,
                    trigger = BattleTrigger.RECOVERY_AFTER,
                ),
            )
        }

        fun recoveryTakenPercent(): Int =
            engine.liveHero(owner).modifiers
                .filterIsInstance<BattleModifier.RecoveryTakenPercent>()
                .sumOf(BattleModifier.RecoveryTakenPercent::percent)
    }

    private fun buxieFixture(): BuxieFixture {
        val ownerTeam = BattleTeamBuilder(
            config,
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100449,
                    position = 0,
                    troops = 10_000,
                    equipmentFeatureSkillIds = listOf(450042),
                    equipmentFeatureSkillLevels = listOf(4),
                ),
            ),
        )
        val request = BattleRequest(
            attacker = ownerTeam,
            defender = BattleTeam(listOf(hero(200001, 200, position = 0))),
            maxRounds = 2,
        )
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val owner = engine.state.view.heroes().single { it.side == Side.ATTACKER }
        val enemy = engine.state.view.heroes().single { it.side == Side.DEFENDER }
        return BuxieFixture(
            engine = engine,
            owner = owner,
            enemy = enemy,
            context = SkillBattleContext(
                request = request,
                runtime = engine.state.runtime,
                random = FixedBattleRandom(0),
                round = 1,
                source = enemy,
                rootSkillId = 0,
                currentSkillId = 0,
                trigger = BattleTrigger.DAMAGE_BEFORE,
                battleView = engine.state.view,
            ),
        )
    }

    private fun ongoingDamage(
        source: BattleHeroRef,
        target: BattleHeroRef,
        detailId: Int,
    ): ScheduledDamageEffectChange {
        val spec = PersistentEffectSpec(
            source = source,
            target = target,
            rootSkillId = 900000,
            skillId = 900000,
            skillKind = SkillKind.ACTIVE,
            rawSkillType = 3,
            detailId = detailId,
            effectId = 305,
            category = com.stzb.battle.core.EffectCategory.HARMFUL,
            conflict = 305,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = 0,
            delayHit = 0,
            availableRounds = 2,
            availableHit = 0,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.rate(40),
        )
        return ScheduledDamageEffectChange(
            spec = spec,
            school = DamageSchool.STRATEGY,
            origin = DamageOrigin.ACTIVE,
            tags = setOf(com.stzb.battle.core.DamageTag.ONGOING),
            status = com.stzb.battle.core.BattleStatus.BURN,
            coefficientSource = BattleCoefficientSource.STRATEGY,
            rawCoefficient = 350,
            calculationTypes = emptyList(),
        )
    }

    private fun ongoingHit(
        source: BattleHeroRef,
        target: BattleHeroRef,
    ) = TroopDamageChange(
        source = source,
        target = target,
        amount = 100,
        troopsAfter = 9_900,
        school = DamageSchool.STRATEGY,
        origin = DamageOrigin.ACTIVE,
        tags = setOf(com.stzb.battle.core.DamageTag.ONGOING),
        skillId = 900000,
        effectId = 305,
    )

    private fun physicalDamage(
        source: BattleHeroRef,
        target: BattleHeroRef,
        amount: Int,
    ) = TroopDamageChange(
        source = source,
        target = target,
        amount = amount,
        troopsAfter = 10_000 - amount,
        school = DamageSchool.PHYSICAL,
        origin = DamageOrigin.NORMAL,
        tags = emptySet(),
        skillId = 0,
        effectId = 0,
    )

    private fun controlDurationModifier(
        source: BattleHeroRef,
        effectId: Int,
        detailId: Int,
        modifier: BattleModifier.ControlDurationIncrease,
    ) = ModifierEffectChange(
        spec = PersistentEffectSpec(
            source = source,
            target = source,
            rootSkillId = detailId / 100,
            skillId = detailId / 100,
            skillKind = SkillKind.PASSIVE,
            rawSkillType = 17,
            detailId = detailId,
            effectId = effectId,
            category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
            conflict = 0,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = 0,
            delayHit = 0,
            availableRounds = 0,
            availableHit = 1,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.flat(modifier.rounds),
        ),
        modifier = modifier,
    )

    private fun controlChange(
        source: BattleHeroRef,
        target: BattleHeroRef,
    ) = ApplyBattleEffectChange(
        PersistentEffectSpec(
            source = source,
            target = target,
            rootSkillId = 900000,
            skillId = 900000,
            skillKind = SkillKind.ACTIVE,
            rawSkillType = 3,
            detailId = 90000001,
            effectId = 501,
            category = com.stzb.battle.core.EffectCategory.HARMFUL,
            conflict = 501,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = 0,
            delayHit = 0,
            availableRounds = 1,
            availableHit = 0,
            clearPerHit = false,
            startBoundary = EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.flat(1),
        ),
    )

    private fun statChange(
        source: BattleHeroRef,
        target: BattleHeroRef,
        effectId: Int,
        value: Int,
    ) = BattleStatChange(
        source = source,
        target = target,
        kind = when (effectId) {
            101, 201 -> BattleStatChange.Kind.ATTACK
            else -> error("Unsupported test stat effect $effectId")
        },
        potency = TypedBattlePotency.flat(value),
        durationRounds = 1,
        skillId = 900000,
        effectId = effectId,
        detailId = 900000 + effectId,
    )

    private fun configRule(
        detailId: Int,
        effectId: Int,
        effectParam: Int,
    ) = SkillEffectRule(
        detailId = detailId,
        effectId = effectId,
        childSkillIds = emptySet(),
        raw = com.stzb.battle.core.SkillDetailConfig(
            detailId = detailId,
            effectId = effectId,
            effectParam = effectParam,
            attackType = 11,
            targetType = 0,
            selectType = 0,
            intelParam = 0,
            constantParam = 1,
            probabilityInit = 100,
            probabilityMax = 100,
            attackMax = 1,
            availableRounds = 1,
            effectName = "test",
        ),
    )
}
