package com.stzb.battle.core.skill

import com.stzb.battle.core.ActiveSkillEffect
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroId
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleDamageCalculator
import com.stzb.battle.core.BattleModifier
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleStat
import com.stzb.battle.core.BattleTeam
import com.stzb.battle.core.BattleTargetingKind
import com.stzb.battle.core.BattleConfigRepository
import com.stzb.battle.core.DamageOrigin
import com.stzb.battle.core.DamageSchool
import com.stzb.battle.core.EffectCategory
import com.stzb.battle.core.FixedBattleRandom
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillDetailConfig
import com.stzb.battle.core.SkillKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkillRuleInterpreterTest {
    @Test
    fun `detail probability applies the same morale adjustment as root skills`() {
        val detail = effectRule(
            detailId = 101,
            effectId = 77,
            probabilityInit = 50,
            probabilityMax = 50,
        )
        val graph = graph(rule(1, detail))
        val random = CountingRandom(55)
        val context = context(
            skillId = 1,
            random = random,
            sourceSkillLevel = 10,
            sourceMorale = 122,
        )

        val result = interpreter(graph).executeDetailForEngine(
            detail = graph.details.single { it.detailId == 101 },
            context = context,
        )

        assertEquals(1, result.stateChanges.filterIsInstance<MarkerEffectChange>().size)
        assertEquals(1, random.calls)
    }

    @Test
    fun `details with the same target signature reuse one random selection`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 521,
                    attackType = 43,
                    attackMax = 2,
                ).copy(effectBuffType = 0),
                effectRule(
                    detailId = 102,
                    effectId = 523,
                    attackType = 43,
                    attackMax = 2,
                ).copy(effectBuffType = 1),
                kind = SkillKind.COMMAND,
            ),
        )
        val sourceHero = hero(1, 0, skillIds = listOf(1))
        val enemies = (0..2).map { position ->
            hero(10 + position, position)
        }
        val request = BattleRequest(
            attacker = BattleTeam(listOf(sourceHero)),
            defender = BattleTeam(enemies),
        )
        val random = CountingRandom(0)
        val runtime = SkillRuntimeState()
        val state = SkillBattleState(request, runtime)
        val context = SkillBattleContext(
            request = request,
            runtime = runtime,
            random = random,
            round = 0,
            source = ref(Side.ATTACKER, sourceHero.position, sourceHero.id.value),
            rootSkillId = 1,
            currentSkillId = 1,
            trigger = BattleTrigger.BATTLE_COMMAND,
            battleView = state.view,
        )

        val changes = interpreter(graph)
            .execute(1, BattleTrigger.BATTLE_COMMAND, context)
            .stateChanges
            .filterIsInstance<DamageModifierChange>()
            .groupBy(DamageModifierChange::effectId)

        assertEquals(
            changes.getValue(521).map(DamageModifierChange::target),
            changes.getValue(523).map(DamageModifierChange::target),
        )
        assertEquals(3, random.calls)
    }

    @Test
    fun `real child ids inherit root source and preserve execution order`() {
        val graph = graph(
            rule(
                200017,
                effectRule(
                    detailId = 20001706,
                    effectId = 122,
                    childSkillIds = setOf(210017),
                    constantParam = 210017,
                    attackType = 21,
                    attackMax = 2,
                ),
            ),
            rule(210017, effectRule(21001701, 77)),
        )
        val context = context(skillId = 200017)
        val result = interpreter(graph).execute(
            200017,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        assertEquals(listOf(200017, 210017), result.executedSkillIds)
        assertEquals(
            listOf(200017, 210017),
            result.events.filterIsInstance<SkillTriggered>().map { it.skillId },
        )
        assertTrue(result.events.all { it.rootSkillId == 200017 })
        val markers = result.stateChanges.filterIsInstance<MarkerEffectChange>()
        assertEquals(1, markers.size)
        assertTrue(markers.all { it.source == context.source })
        assertEquals(
            setOf(Side.ATTACKER),
            markers.mapTo(linkedSetOf()) { it.target.side },
        )
        assertEquals(
            ChildProbabilityOwnership.CONFIGURED_CHILD,
            result.stateChanges.filterIsInstance<ExecuteChildSkillChange>().single().probabilityOwnership,
        )
    }

    @Test
    fun `recursive child call fails with exact dependency path and unwinds stack`() {
        val graph = graph(
            rule(1, effectRule(101, 122, setOf(2), constantParam = 2)),
            rule(2, effectRule(201, 122, setOf(1), constantParam = 1)),
        )
        val context = context(skillId = 1)

        val error = assertFailsWith<SkillRecursionException> {
            interpreter(graph).execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        }

        assertTrue(error.message!!.contains("1 -> 2 -> 1"))
        assertEquals(emptyList(), context.runtime.currentCallPath())
    }

    @Test
    fun `missing child rule reports the full dependency path`() {
        val graph = graph(
            rule(1, effectRule(101, 122, setOf(2), constantParam = 2)),
        )

        val error = assertFailsWith<MissingSkillRuleException> {
            interpreter(graph).execute(
                1,
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                context(skillId = 1),
            )
        }

        assertTrue(error.message!!.contains("1 -> 2"))
    }

    @Test
    fun `maximum depth failure includes the complete attempted path`() {
        val rules = (1..17).map { skillId ->
            val childId = skillId + 1
            rule(
                skillId,
                effectRule(
                    skillId * 100 + 1,
                    122,
                    setOf(childId),
                    constantParam = childId,
                ),
            )
        } + rule(18, effectRule(1801, 0))
        val context = context(skillId = 1)

        val error = assertFailsWith<SkillRecursionException> {
            interpreter(graph(*rules.toTypedArray())).execute(
                1,
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                context,
            )
        }

        assertTrue(error.message!!.contains("maximum child depth"))
        assertTrue(error.message!!.contains((1..17).joinToString(" -> ")))
        assertEquals(emptyList(), context.runtime.currentCallPath())
    }

    @Test
    fun `handler exception always unwinds runtime stack`() {
        val graph = graph(rule(1, effectRule(101, 999)))
        val registry = BattleEffectRegistry.strict(graph).register(
            EffectHandlerRegistration.implemented(
                999,
                object : ImplementedBattleEffectHandler {
                    override val semanticId: String = "test.throw"

                    override fun execute(invocation: EffectInvocation): EffectExecution =
                        throw IllegalArgumentException("boom")
                },
            ),
        )
        val context = context(skillId = 1)

        assertFailsWith<IllegalArgumentException> {
            SkillRuleInterpreter(graph, registry).execute(
                1,
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                context,
            )
        }
        assertEquals(emptyList(), context.runtime.currentCallPath())
    }

    @Test
    fun `condition interpreter rejects an unknown synthetic code explicitly`() {
        val graph = graph(
            rule(
                1,
                effectRule(101, 0, castCondition = 77),
            ),
        )

        val error = assertFailsWith<UnsupportedPendingSkillConditionException> {
            interpreter(graph).execute(
                1,
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                context(skillId = 1),
            )
        }

        assertTrue(error.message!!.contains("cast_condition=77"))
        assertTrue(error.message!!.contains("skill=1 detail=101"))
    }

    @Test
    fun `probability is rolled once with morale and existing modifiers`() {
        val random = CountingRandom(69)
        val graph = graph(rule(1, effectRule(101, 0), probability = 50))
        val context = context(
            skillId = 1,
            random = random,
            sourceModifiers = listOf(
                com.stzb.battle.core.BattleModifier.SkillProbabilityPercent(20),
            ),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        assertEquals(listOf(1), result.executedSkillIds)
        assertEquals(1, random.calls)
    }

    @Test
    fun `skill probability modifiers are applied before morale scaling`() {
        val random = CountingRandom(55)
        val graph = graph(rule(1, effectRule(101, 0), probability = 40))
        val context = context(
            skillId = 1,
            random = random,
            sourceModifiers = listOf(
                BattleModifier.SkillProbabilityPercent(10),
            ),
            sourceMorale = 121,
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        assertEquals(listOf(1), result.executedSkillIds)
        assertEquals(1, random.calls)
    }

    @Test
    fun `skill probability use sink runs only when a matching modifier participates`() {
        val graph = graph(rule(1, effectRule(101, 0), probability = 50))
        val consumed = mutableListOf<Triple<BattleHeroRef, Int, SkillKind>>()
        val context = context(
            skillId = 1,
            random = FixedBattleRandom(0),
            sourceModifiers = listOf(
                BattleModifier.SkillProbabilityPercent(
                    percent = 20,
                    skillKind = SkillKind.ACTIVE,
                ),
            ),
        ).copy(
            skillProbabilityUses = SkillProbabilityUseSink { source, skillId, skillKind ->
                consumed += Triple(source, skillId, skillKind)
            },
        )

        interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        assertEquals(
            listOf(Triple(context.source, 1, SkillKind.ACTIVE)),
            consumed,
        )
    }

    @Test
    fun `detail probability modifier applies only to the referenced effect detail`() {
        val referenced = effectRule(
            detailId = 201,
            effectId = 122,
            childSkillIds = setOf(2),
            constantParam = 2,
            probabilityInit = 40,
            probabilityMax = 40,
        )
        val other = effectRule(
            detailId = 202,
            effectId = 122,
            childSkillIds = setOf(3),
            constantParam = 3,
            probabilityInit = 40,
            probabilityMax = 40,
        )
        val childTwo = rule(2, effectRule(203, 77, constantParam = 2))
        val childThree = rule(3, effectRule(204, 77, constantParam = 3))
        val rules = listOf(rule(1, referenced, other), childTwo, childThree)
        val graph = SkillRuleGraph(
            rules = rules.associateBy(SkillRule::skillId),
            effectIds = rules.flatMap { it.details }.mapTo(linkedSetOf()) { it.effectId },
            rootSkillIds = setOf(1),
        )

        val result = SkillRuleInterpreter(
            graph,
            BattleEffectRegistry.strict(graph).registerMetaEffects(),
        ).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(
                1,
                FixedBattleRandom(50),
                sourceModifiers = listOf(
                    BattleModifier.EffectProbabilityPercent(
                        detailId = referenced.detailId,
                        percent = 20,
                    ),
                ),
            ),
        )

        assertEquals(listOf(2), result.executedSkillIds.filter { it != 1 })
    }

    @Test
    fun `fenji comparison child rolls detail probability for each selected hero`() {
        val graph = graph(
            rule(
                212961,
                effectRule(
                    detailId = 21296113,
                    effectId = 122,
                    childSkillIds = setOf(210961),
                    attackType = 113,
                    attackMax = 6,
                    probabilityInit = 50,
                    probabilityMax = 50,
                ),
            ),
            rule(210961, effectRule(21096101, 77, constantParam = 1)),
        )
        val random = SequenceRandom(
            0,
            0, 0,
            0, 0,
            99,
        )

        val result = interpreter(graph).execute(
            212961,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 212961, random = random),
        )

        assertEquals(listOf(210961), result.executedSkillIds.filter { it != 212961 })
        assertEquals(1, result.stateChanges.filterIsInstance<MarkerEffectChange>().size)
        assertEquals(6, random.calls)
    }

    @Test
    fun `detail probability interpolates from initial to maximum by root skill level`() {
        val detail = effectRule(
            detailId = 101,
            effectId = 77,
            constantParam = 1,
            probabilityInit = 20,
            probabilityMax = 80,
        )
        val graph = graph(rule(1, detail))
        val interpreter = SkillRuleInterpreter(
            graph,
            BattleEffectRegistry.strict(graph).registerMetaEffects(),
        )

        val levelOne = interpreter.execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(1, FixedBattleRandom(50), sourceSkillLevel = 1),
        )
        val levelTen = interpreter.execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(1, FixedBattleRandom(50), sourceSkillLevel = 10),
        )

        assertEquals(emptyList(), levelOne.stateChanges)
        assertEquals(1, levelTen.stateChanges.filterIsInstance<MarkerEffectChange>().size)
    }

    @Test
    fun `command immunity emits a persistent runtime effect`() {
        val detail = effectRule(
            detailId = 20075601,
            effectId = 121,
            availableRounds = 10,
        )
        val graph = graph(rule(200756, detail, kind = SkillKind.PASSIVE))

        val result = interpreter(graph).execute(
            200756,
            BattleTrigger.BATTLE_PASSIVE,
            context(200756),
        )

        val change = result.stateChanges.filterIsInstance<ApplyBattleEffectChange>().single()
        assertEquals(121, change.spec.effectId)
        assertEquals(10, change.spec.availableRounds)
    }

    @Test
    fun `meta combo contributes one extra normal attack`() {
        val detail = effectRule(
            detailId = 21125502,
            effectId = 200,
            availableRounds = 1,
        )
        val graph = graph(rule(211255, detail, kind = SkillKind.COMMAND))

        val result = interpreter(graph).execute(
            211255,
            BattleTrigger.BATTLE_COMMAND,
            context(211255),
        )

        val change = result.stateChanges.filterIsInstance<ActionEffectChange>().single()
        assertEquals(ActionEffectKind.DOUBLE_ATTACK, change.kind)
        assertEquals(200, change.spec.effectId)
    }

    @Test
    fun `trigger mismatch neither rolls nor executes`() {
        val random = CountingRandom(0)
        val graph = graph(rule(1, effectRule(101, 0)))

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.PURSUIT_ATTEMPT,
            context(skillId = 1, random = random),
        )

        assertEquals(SkillExecutionResult.EMPTY, result)
        assertEquals(0, random.calls)
    }

    @Test
    fun `effect zero is an explicit no behavior implementation with a trigger event`() {
        val graph = graph(rule(1, effectRule(101, 0)))
        val registry = BattleEffectRegistry.strict(graph).registerMetaEffects()

        val result = SkillRuleInterpreter(graph, registry).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1),
        )

        assertEquals(setOf(0), registry.implementedEffectIds())
        assertEquals(emptyList(), result.stateChanges)
        assertEquals(1, result.events.filterIsInstance<SkillTriggered>().size)
    }

    @Test
    fun `meta registry owns the exact meaningful non placeholder effect set`() {
        val graph = graph(
            rule(
                1,
                *MetaEffectHandlers.effectIds.sorted()
                    .mapIndexed { index, effectId -> effectRule(100 + index, effectId) }
                    .toTypedArray(),
            ),
        )
        val registry = BattleEffectRegistry.strict(graph).registerMetaEffects()

        assertEquals(51, EXPECTED_META_EFFECT_IDS.size)
        assertEquals(EXPECTED_META_EFFECT_IDS, MetaEffectHandlers.effectIds)
        assertEquals(EXPECTED_META_EFFECT_IDS, registry.implementedEffectIds())
        EXPECTED_META_EFFECT_IDS.forEach { effectId ->
            val semantic = registry.implementationSemanticId(effectId)
            assertTrue(!semantic.isNullOrBlank(), "effect=$effectId")
            if (effectId != 0) {
                assertTrue(!semantic.contains("placeholder"), "effect=$effectId semantic=$semantic")
                assertTrue(!semantic.contains("no-op"), "effect=$effectId semantic=$semantic")
            }
        }
    }

    @Test
    fun `meta registry binds the independent literal intersection with the real graph`() {
        val realGraph = SkillRuleCatalog.build(
            SkillScopeCatalog.loadDefault(),
            BattleConfigRepository.loadDefault(),
        )

        val implemented = BattleEffectRegistry.strict(realGraph)
            .registerMetaEffects()
            .implementedEffectIds()

        assertEquals(EXPECTED_META_EFFECT_IDS intersect realGraph.effectIds, implemented)
    }

    @Test
    fun `effect 271 emits an executable recovery dealt modifier`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 271,
                    constantParam = 15,
                    attackType = 13,
                ),
            ),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1),
        )

        assertTrue(result.stateChanges.single() is ModifierEffectChange)
    }

    @Test
    fun `target immunity effects emit typed one round modifiers`() {
        val graph = graph(
            rule(
                200931,
                effectRule(20093101, 193, availableRounds = 1),
                effectRule(20093102, 194, availableRounds = 1),
                effectRule(20093103, 190, availableRounds = 1),
                kind = SkillKind.PURSUIT,
            ),
        )

        val result = interpreter(graph).execute(
            200931,
            BattleTrigger.PURSUIT_ATTEMPT,
            context(skillId = 200931),
        )

        val modifiers = result.stateChanges.filterIsInstance<ModifierEffectChange>()
        assertEquals(
            setOf(
                190 to BattleTargetingKind.NORMAL_ATTACK,
                193 to BattleTargetingKind.ACTIVE_SKILL,
                194 to BattleTargetingKind.PURSUIT_SKILL,
            ),
            modifiers.mapTo(mutableSetOf()) { change ->
                change.spec.effectId to
                    (change.modifier as BattleModifier.TargetImmunity).kind
            },
        )
        assertTrue(modifiers.all { it.spec.availableRounds == 1 })
    }

    @Test
    fun `effect 553 emits a typed counterattack immunity modifier`() {
        val graph = graph(
            rule(
                460001,
                effectRule(
                    detailId = 46000101,
                    effectId = 553,
                    constantParam = 100,
                    availableRounds = 1,
                ),
                kind = SkillKind.PASSIVE,
            ),
        )

        val result = interpreter(graph).execute(
            460001,
            BattleTrigger.BATTLE_PASSIVE,
            context(skillId = 460001),
        )

        val change = result.stateChanges.filterIsInstance<ModifierEffectChange>().single()
        assertEquals(BattleModifier.CounterattackImmunity, change.modifier)
        assertEquals(1, change.spec.availableRounds)
    }

    @Test
    fun `effect 150 emits a typed specified effect trigger`() {
        val graph = graph(
            rule(
                297173,
                effectRule(
                    detailId = 29717301,
                    effectId = 150,
                    effectParam = 305,
                    availableHit = 1,
                ),
                kind = SkillKind.PASSIVE,
            ),
        )
        val context = context(skillId = 297173)

        val result = interpreter(graph).execute(
            297173,
            BattleTrigger.BATTLE_PASSIVE,
            context,
        )

        val change = result.stateChanges.filterIsInstance<TriggerSpecifiedEffectChange>().single()
        assertEquals(context.source, change.source)
        assertEquals(context.source, change.target)
        assertEquals(29717301, change.detailId)
        assertEquals(305, change.triggeredEffectId)
    }

    @Test
    fun `effect 220 emits persistent siege immunity for selected allies`() {
        val graph = graph(
            rule(
                210267,
                effectRule(
                    detailId = 21026701,
                    effectId = 220,
                    attackType = 23,
                    attackMax = 3,
                    availableRounds = 2,
                ),
            ),
        )

        val result = interpreter(graph).execute(
            210267,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 210267),
        )

        val changes = result.stateChanges.filterIsInstance<ApplyBattleEffectChange>()
        assertEquals(2, changes.size)
        assertTrue(changes.all { it.spec.effectId == 220 && it.spec.availableRounds == 2 })
    }

    @Test
    fun `real ranged attack effect carries its referenced per distance bonus`() {
        val repository = BattleConfigRepository.loadDefault()
        val graph = SkillRuleCatalog.build(
            SkillScope(setOf(300055), emptySet()),
            repository,
        )

        val entryContext = context(
            skillId = 300055,
            sourceSkillLevel = 10,
        )
        val context = entryContext.copy(
            battleView = SkillBattleState(
                entryContext.request,
                entryContext.runtime,
            ).view,
        )
        val result = interpreter(graph).execute(
            300055,
            BattleTrigger.BATTLE_PASSIVE,
            context,
        )

        val change = result.stateChanges
            .filterIsInstance<ModifierEffectChange>()
            .single { it.spec.effectId == 128 }
        assertEquals(
            BattleModifier.RangedNormalAttack(
                damagePercentPerDistance = 25,
            ),
            change.modifier,
        )
        assertEquals(10, change.spec.availableRounds)
    }

    @Test
    fun `control duration effects emit typed main skill and one shot modifiers`() {
        val mainSkillOnly = effectRule(
            detailId = 40009101,
            effectId = 311,
            effectParam = 1000001004,
            constantParam = 1,
            availableRounds = 10,
        ).copy(skillKind = SkillKind.PASSIVE, rawSkillType = 16)
        val nextControl = effectRule(
            detailId = 47104401,
            effectId = 312,
            constantParam = 1,
            availableHit = 1,
            availableRounds = 0,
        ).copy(skillKind = SkillKind.PASSIVE, rawSkillType = 19)

        val mainChange = interpreter(graph(rule(400091, mainSkillOnly, kind = SkillKind.PASSIVE)))
            .execute(400091, BattleTrigger.BATTLE_PASSIVE, context(400091))
            .stateChanges
            .filterIsInstance<ModifierEffectChange>()
            .single()
        val nextChange = interpreter(graph(rule(471044, nextControl, kind = SkillKind.PASSIVE)))
            .execute(471044, BattleTrigger.BATTLE_PASSIVE, context(471044))
            .stateChanges
            .filterIsInstance<ModifierEffectChange>()
            .single()

        assertEquals(
            BattleModifier.ControlDurationIncrease(
                rounds = 1,
                mainSkillOnly = true,
                requiredSkillKind = SkillKind.PURSUIT,
            ),
            mainChange.modifier,
        )
        assertEquals(10, mainChange.spec.availableRounds)
        assertEquals(
            BattleModifier.ControlDurationIncrease(
                rounds = 1,
                mainSkillOnly = false,
            ),
            nextChange.modifier,
        )
        assertEquals(1, nextChange.spec.availableHit)
    }

    @Test
    fun `normal attack simulation effects emit typed single and in range intents`() {
        val single = effectRule(
            detailId = 41111211,
            effectId = 79,
            availableHit = 2,
            availableRounds = 0,
        )
        val allInRange = effectRule(
            detailId = 41011321,
            effectId = 80,
            availableHit = 1,
            availableRounds = 0,
        )

        val singleChange = interpreter(graph(rule(411112, single)))
            .execute(411112, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context(411112))
            .stateChanges
            .filterIsInstance<SimulatedNormalAttackChange>()
            .single()
        val allChange = interpreter(graph(rule(410113, allInRange)))
            .execute(410113, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context(410113))
            .stateChanges
            .filterIsInstance<SimulatedNormalAttackChange>()
            .single()

        assertEquals(SimulatedNormalAttackMode.SINGLE, singleChange.mode)
        assertEquals(SimulatedNormalAttackMode.ALL_IN_RANGE, allChange.mode)
        assertEquals(41111211, singleChange.detailId)
        assertEquals(41011321, allChange.detailId)
    }

    @Test
    fun `skill enhancement unlock emits typed allied modifiers`() {
        val unlock = effectRule(
            detailId = 20087001,
            effectId = 132,
            effectParam = 200870,
            attackType = 23,
            attackMax = 3,
            availableRounds = 10,
        ).copy(skillKind = SkillKind.PASSIVE, rawSkillType = 13)

        val changes = interpreter(graph(rule(200870, unlock, kind = SkillKind.PASSIVE)))
            .execute(200870, BattleTrigger.BATTLE_PASSIVE, context(200870))
            .stateChanges
            .filterIsInstance<ModifierEffectChange>()

        assertEquals(2, changes.size)
        assertTrue(changes.all {
            it.modifier == BattleModifier.SkillEnhancementUnlock(200870) &&
                it.spec.availableRounds == 10
        })
    }

    @Test
    fun `locked details execute only while their skill enhancement is unlocked`() {
        val lockedMarker = effectRule(
            detailId = 100,
            effectId = 77,
            constantParam = 7,
            rawLockFlag = 900001,
        )
        val unlockDeclaration = effectRule(
            detailId = 200,
            effectId = 132,
            effectParam = 900001,
        )
        val graph = graph(
            rule(1, lockedMarker),
            rule(2, unlockDeclaration),
        )
        val interpreter = interpreter(graph)

        val locked = interpreter.execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(1),
        )
        val unlocked = interpreter.execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(
                skillId = 1,
                sourceModifiers = listOf(
                    BattleModifier.SkillEnhancementUnlock(900001),
                ),
            ),
        )

        assertTrue(locked.stateChanges.none { it is MarkerEffectChange })
        assertEquals(
            1,
            unlocked.stateChanges.filterIsInstance<MarkerEffectChange>().size,
        )
    }

    @Test
    fun `all real advisor enhancements gate every configured locked detail`() {
        val config = BattleConfigRepository.loadDefault()
        val catalog = SkillRuleCatalog.build(
            SkillScope(
                fiveStarInitialSkillIds = config.allSkillIds(),
                learnableSaSkillIds = emptySet(),
            ),
            config,
        )
        val unlockDetails = catalog.details.filter { it.effectId == 132 }
        val unlockIds = unlockDetails.mapTo(linkedSetOf()) { it.raw.effectParam }
        val lockedDetails = catalog.details.filter { it.raw.lockFlag in unlockIds }

        assertEquals(
            mapOf(
                200806 to 2,
                200807 to 3,
                200808 to 6,
                200809 to 6,
                200870 to 6,
                200873 to 3,
                200874 to 3,
                200875 to 2,
                200878 to 7,
                200879 to 4,
                200901 to 3,
            ),
            lockedDetails.groupingBy { it.raw.lockFlag }.eachCount().toSortedMap(),
        )

        lockedDetails.forEach { detail ->
            val skillId = detail.detailId / 100
            val requiredUnlockId = detail.raw.lockFlag
            val conditionNeutralDetail = if (detail.detailId == 31113413) {
                detail.copy(raw = detail.raw.copy(castCondition = 0))
            } else {
                detail
            }
            val isolatedRule = requireNotNull(catalog.rule(skillId)).copy(
                details = listOf(conditionNeutralDetail),
            )
            val unlockDetail = unlockDetails.single {
                it.raw.effectParam == requiredUnlockId
            }
            val isolatedUnlockRule = requireNotNull(
                catalog.rule(unlockDetail.detailId / 100),
            ).copy(
                details = listOf(unlockDetail),
            )
            val isolatedGraph = SkillRuleGraph(
                rules = listOf(isolatedRule, isolatedUnlockRule)
                    .associateBy(SkillRule::skillId),
                effectIds = setOf(conditionNeutralDetail.effectId, 132),
                rootSkillIds = setOf(skillId),
            )
            val isolatedInterpreter = SkillRuleInterpreter(
                graph = isolatedGraph,
                registry = BattleEffectRegistry.strict(isolatedGraph)
                    .registerCoreEffects(BattleEffectStore())
                    .registerControlEffects(BattleEffectStore())
                    .registerMetaEffects(),
            )
            val trigger = isolatedRule.kind.toTrigger()

            fun liveContext(sourceModifiers: List<BattleModifier>): SkillBattleContext {
                val source = hero(
                    id = 1,
                    position = 2,
                    skillIds = listOf(skillId),
                    skillLevels = listOf(10),
                    modifiers = sourceModifiers,
                ).copy(
                    troops = 500,
                    maxTroops = 1_000,
                )
                val request = BattleRequest(
                    attacker = BattleTeam(
                        listOf(
                            source,
                            hero(2, position = 1),
                        ),
                    ),
                    defender = BattleTeam(
                        listOf(hero(3, position = 2)),
                    ),
                )
                val runtime = SkillRuntimeState()
                val sourceRef = ref(Side.ATTACKER, source.position, source.id.value)
                val enemyRef = ref(Side.DEFENDER, 2, 3)
                val entryContext = SkillBattleContext(
                    request = request,
                    runtime = runtime,
                    random = FixedBattleRandom(0),
                    round = 3,
                    source = sourceRef,
                    rootSkillId = skillId,
                    currentSkillId = skillId,
                    trigger = trigger,
                )
                return entryContext.copy(
                    battleView = SkillBattleState(
                        request = request,
                        runtime = runtime,
                        initialWoundedTroops = mapOf(sourceRef to 500),
                        historyAdapter = object : SkillBattleHistoryAdapter {
                            override fun linkedTarget(source: BattleHeroRef): BattleHeroRef =
                                enemyRef

                            override fun currentTarget(source: BattleHeroRef): BattleHeroRef =
                                enemyRef

                            override fun previousTarget(source: BattleHeroRef): BattleHeroRef =
                                enemyRef
                        },
                    ).view,
                )
            }

            val locked = isolatedInterpreter.execute(
                skillId,
                trigger,
                liveContext(emptyList()),
            )
            val unlocked = isolatedInterpreter.execute(
                skillId,
                trigger,
                liveContext(
                    listOf(BattleModifier.SkillEnhancementUnlock(requiredUnlockId)),
                ),
            )

            assertTrue(
                locked.stateChanges.isEmpty(),
                "detail=${detail.detailId} unlock=$requiredUnlockId changes=${locked.stateChanges}",
            )
            assertTrue(
                unlocked.stateChanges.isNotEmpty(),
                "detail=${detail.detailId} unlock=$requiredUnlockId trigger=$trigger",
            )
        }
    }

    @Test
    fun `effect 210 emits typed named flag counter changes instead of child execution`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 210,
                    effectParam = 210255,
                    constantParam = 1,
                    attackType = 23,
                    attackMax = 2,
                    addCountMax = 20,
                ),
            ),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1),
        )
        val changes = result.stateChanges.filterIsInstance<NamedFlagCounterChange>()

        assertEquals(
            listOf(ref(Side.ATTACKER, 0, 1), ref(Side.ATTACKER, 1, 2)),
            changes.map { it.target },
        )
        assertTrue(changes.all { it.flagId == 210255 })
        assertTrue(changes.all { it.delta == 1 })
        assertTrue(changes.all { it.maximum == 20 })
        assertTrue(result.stateChanges.none { it is ExecuteChildSkillChange })
    }

    @Test
    fun `referenced effect keeps the invoking targets and state change order`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 151,
                    effectParam = 201,
                    attackType = 21,
                    attackMax = 2,
                ),
            ),
            rule(2, effectRule(201, 77, attackType = 43, attackMax = 1)),
        )
        val context = context(skillId = 1)

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        assertEquals(
            listOf(TriggerReferencedEffectChange::class, MarkerEffectChange::class, MarkerEffectChange::class),
            result.stateChanges.map { it::class },
        )
        assertEquals(
            listOf(
                ref(Side.ATTACKER, 0, 1),
                ref(Side.ATTACKER, 1, 2),
            ),
            result.stateChanges.filterIsInstance<MarkerEffectChange>().map { it.target },
        )
        assertTrue(result.stateChanges.all {
            when (it) {
                is TriggerReferencedEffectChange -> it.source == context.source && it.rootSkillId == 1
                is MarkerEffectChange -> it.source == context.source && it.rootSkillId == 1
                else -> false
            }
        })
    }

    @Test
    fun `referenced detail cycle fails with full detail path and unwinds both stacks`() {
        val graph = graph(
            rule(1, effectRule(101, 151, effectParam = 201)),
            rule(2, effectRule(201, 151, effectParam = 101)),
        )
        val context = context(skillId = 1)

        val error = assertFailsWith<SkillDetailRecursionException> {
            interpreter(graph).execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        }

        assertEquals(
            listOf(
                SkillExecutionFrame(1, 101),
                SkillExecutionFrame(2, 201),
                SkillExecutionFrame(1, 101),
            ),
            error.fullPath,
        )
        assertEquals(emptyList(), context.runtime.currentCallPath())
        assertEquals(emptyList(), context.runtime.currentDetailPath())
    }

    @Test
    fun `effect 153 scales the referenced effect while 151 keeps its configured value`() {
        val graph = graph(
            rule(
                1,
                effectRule(101, 151, effectParam = 301),
                effectRule(
                    102,
                    153,
                    effectParam = 301,
                    constantParam = 10,
                    intelParam = 100,
                    attributeType = 3,
                ),
            ),
            rule(
                3,
                effectRule(
                    301,
                    113,
                    constantParam = 2,
                    intelParam = 0,
                    attackType = 0,
                ),
            ),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1, sourceStrategy = 180),
        )

        assertEquals(
            listOf(
                TypedBattlePotency.flat(2),
                TypedBattlePotency.flat(20),
            ),
            result.stateChanges.filterIsInstance<MoraleEffectChange>().map { it.potency },
        )
        assertEquals(
            listOf(ReferenceEffectMode.NORMAL, ReferenceEffectMode.ATTRIBUTE_SCALED),
            result.stateChanges.filterIsInstance<TriggerReferencedEffectChange>().map { it.mode },
        )
    }

    @Test
    fun `effect 153 keeps targets and scaling through a referenced child wrapper`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    101,
                    153,
                    effectParam = 201,
                    constantParam = 10,
                    intelParam = 100,
                    attributeType = 3,
                    attackType = 21,
                    attackMax = 2,
                ),
            ),
            rule(
                2,
                effectRule(
                    201,
                    122,
                    childSkillIds = setOf(3),
                    constantParam = 3,
                    attackType = 43,
                ),
            ),
            rule(3, effectRule(301, 113, constantParam = 2, attackType = 43)),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1, sourceStrategy = 180),
        )
        val morale = result.stateChanges.filterIsInstance<MoraleEffectChange>()

        assertEquals(listOf(TypedBattlePotency.flat(20), TypedBattlePotency.flat(20)), morale.map { it.potency })
        assertEquals(
            listOf(ref(Side.ATTACKER, 0, 1), ref(Side.ATTACKER, 1, 2)),
            morale.map { it.target },
        )
    }

    @Test
    fun `effect 151 keeps invoking targets through a referenced child wrapper`() {
        val graph = graph(
            rule(
                1,
                effectRule(101, 151, effectParam = 201, attackType = 21, attackMax = 2),
            ),
            rule(
                2,
                effectRule(
                    201,
                    122,
                    childSkillIds = setOf(3),
                    constantParam = 3,
                    attackType = 43,
                ),
            ),
            rule(3, effectRule(301, 77, attackType = 43)),
        )

        val markers = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1),
        ).stateChanges.filterIsInstance<MarkerEffectChange>()

        assertEquals(
            listOf(ref(Side.ATTACKER, 0, 1), ref(Side.ATTACKER, 1, 2)),
            markers.map { it.target },
        )
    }

    @Test
    fun `effect 151 triggers an existing referenced ongoing effect without reapplying it`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 151,
                    effectParam = 201,
                    attackType = 41,
                ),
            ),
            rule(
                2,
                effectRule(
                    detailId = 201,
                    effectId = 303,
                    constantParam = 130,
                    attackType = 41,
                    availableHit = 3,
                    availableRounds = 0,
                    calcPos = 311,
                ),
            ),
        )
        val context = context(skillId = 1)

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        val trigger = result.stateChanges
            .filterIsInstance<TriggerSpecifiedEffectChange>()
            .single()
        assertEquals(context.source, trigger.triggeredSource)
        assertEquals(201, trigger.triggeredDetailId)
        assertEquals(303, trigger.triggeredEffectId)
        assertEquals(ref(Side.DEFENDER, 0, 3), trigger.target)
        assertTrue(result.stateChanges.none { it is ScheduledDamageEffectChange })
    }

    @Test
    fun `effect 152 clears the exact referenced detail and effect pair`() {
        val store = BattleEffectStore()
        val source = ref(Side.ATTACKER, 0, 1)
        val target = ref(Side.ATTACKER, 1, 2)
        store.apply(activeEffect(source, target, detailId = 201, effectId = 77))
        store.apply(activeEffect(source, target, detailId = 201, effectId = 81))
        val change = ClearReferencedEffectChange(
            source = source,
            target = target,
            rootSkillId = 1,
            skillId = 1,
            detailId = 101,
            referencedDetailId = 201,
            referencedEffectId = 77,
            parameters = metaParameters(detailId = 101, effectId = 152, effectParam = 201),
        )

        val removed = change.apply(store)

        assertEquals(listOf(77), removed.removed.map { it.effectId })
        assertEquals(listOf(81), store.effectsFor(target).map { it.effectId })
    }

    @Test
    fun `effect 313 consumes only the exact referenced detail`() {
        val store = BattleEffectStore()
        val source = ref(Side.ATTACKER, 0, 1)
        val target = ref(Side.ATTACKER, 1, 2)
        store.apply(activeEffect(source, target, detailId = 201, effectId = 77, remainingHits = 2))
        store.apply(activeEffect(source, target, detailId = 202, effectId = 77, remainingHits = 2))
        val change = ReduceReferencedEffectUseChange(
            source = source,
            target = target,
            rootSkillId = 1,
            skillId = 1,
            detailId = 101,
            referencedDetailId = 202,
            referencedEffectId = 77,
            amount = 1,
            parameters = metaParameters(detailId = 101, effectId = 313, effectParam = 202),
        )

        change.apply(store)

        assertEquals(
            mapOf(201 to 2, 202 to 1),
            store.effectsFor(target).associate { it.detailId to it.remainingHits },
        )
    }

    @Test
    fun `trigger effect executes the referenced detail through the registry`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 151,
                    effectParam = 201,
                    attackType = 21,
                    attackMax = 2,
                ),
            ),
            rule(2, effectRule(201, 77, attackType = 21, attackMax = 2)),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1),
        )

        assertEquals(listOf(1), result.executedSkillIds)
        assertEquals(2, result.stateChanges.filterIsInstance<MarkerEffectChange>().size)
        assertTrue(result.stateChanges.any { it is TriggerReferencedEffectChange })
    }

    @Test
    fun `transformation casts a stable random foreign active skill without preparation`() {
        val graph = graph(
            rule(1, effectRule(101, 199, availableHit = 1)),
            rule(10, effectRule(1001, 77, constantParam = 10)),
            rule(
                20,
                effectRule(2001, 77, constantParam = 20),
                effectRule(
                    2002,
                    77,
                    constantParam = 99,
                    probabilityInit = 0,
                    probabilityMax = 0,
                ),
                prepareRounds = 2,
            ),
            rule(30, effectRule(3001, 77, constantParam = 30)),
        )
        val context = context(
            skillId = 1,
            random = FixedBattleRandom(0),
            sourceExtraSkillIds = listOf(10),
            alliedSkillIds = listOf(20),
            enemySkillIds = listOf(30),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        assertEquals(listOf(1, 20), result.executedSkillIds)
        assertEquals(
            listOf(1, 20),
            result.events.filterIsInstance<SkillTriggered>().map { it.skillId },
        )
        assertTrue(result.events.filterIsInstance<SkillTriggered>().all {
            it.source == context.source && it.rootSkillId == 1
        })
        assertEquals(
            listOf(20),
            result.stateChanges.filterIsInstance<MarkerEffectChange>().map { it.marker },
        )
        assertTrue(result.stateChanges.any { it is TransformAndCastRandomActiveSkillChange })
    }

    @Test
    fun `joint attack registers once without rolling its target probability early`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 81,
                    attackType = 0,
                    availableHit = 1,
                    constantParam = 50,
                    probabilityInit = 0,
                    probabilityMax = 0,
                    customSelectFlag = 321529301,
                ),
            ),
        )
        val context = context(skillId = 1)

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )
        val forced = result.stateChanges.filterIsInstance<ForcedTargetEffectChange>().single()

        assertEquals(context.source, forced.spec.target)
        assertEquals(ref(Side.DEFENDER, 0, 3), forced.forcedTarget)
        assertEquals(TypedBattlePotency.percent(50), forced.spec.potency)
        assertEquals(1, forced.spec.availableHit)
    }

    @Test
    fun `shared use effect registers its referenced detail without consuming it`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 88,
                    effectParam = 201,
                    attackType = 0,
                    availableHit = 1,
                ),
            ),
            rule(2, effectRule(201, 131, attackType = 0, availableHit = 1)),
        )
        val context = context(skillId = 1)

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )
        val group = result.stateChanges.filterIsInstance<SharedEffectUseGroupChange>().single()

        assertEquals(context.source, group.spec.target)
        assertEquals(201, group.memberDetailId)
        assertEquals(1, group.spec.availableHit)
    }

    @Test
    fun `puppet detail owns target value probability and lifecycle through nested template`() {
        val graph = graph(
            rule(
                200282,
                effectRule(
                    detailId = 20028212,
                    effectId = 125,
                    effectParam = 21028202,
                    constantParam = 9,
                    attackType = 21,
                    attackMax = 2,
                    availableRounds = 10,
                ),
            ),
            rule(
                210282,
                effectRule(
                    detailId = 21028202,
                    effectId = 125,
                    effectParam = 21028211,
                    constantParam = 100,
                    attackType = 43,
                    attackMax = 1,
                    probabilityInit = 0,
                    availableRounds = 1,
                ),
                effectRule(
                    detailId = 21028211,
                    effectId = 101,
                    constantParam = 999,
                    attackType = 43,
                    attackMax = 1,
                    probabilityInit = 0,
                    availableRounds = 1,
                ),
            ),
        )
        val entryContext = context(skillId = 200282)
        val context = entryContext.copy(
            battleView = SkillBattleState(
                entryContext.request,
                entryContext.runtime,
            ).view,
        )

        val changes = interpreter(graph).execute(
            200282,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        ).stateChanges.filterIsInstance<BattleStatChange>()

        assertEquals(
            listOf(
                ref(Side.ATTACKER, 0, 1),
                ref(Side.ATTACKER, 1, 2),
            ),
            changes.map { it.target },
        )
        assertTrue(changes.all {
            it.effectId == 101 &&
                it.detailId == 21028211 &&
                it.potency == TypedBattlePotency.flat(9) &&
                it.durationRounds == 10
        })
    }

    @Test
    fun `puppet template is not executed again as a regular sibling detail`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 125,
                    effectParam = 102,
                    constantParam = 9,
                    attackType = 0,
                ),
                effectRule(
                    detailId = 102,
                    effectId = 101,
                    constantParam = 999,
                    attackType = 0,
                ),
            ),
        )
        val entryContext = context(skillId = 1)
        val context = entryContext.copy(
            battleView = SkillBattleState(
                entryContext.request,
                entryContext.runtime,
            ).view,
        )

        val changes = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        ).stateChanges.filterIsInstance<BattleStatChange>()

        assertEquals(1, changes.size)
        assertEquals(102, changes.single().detailId)
        assertEquals(TypedBattlePotency.flat(9), changes.single().potency)
    }

    @Test
    fun `extra parameters scale only their referenced active and pursuit retriggers`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 111,
                    effectParam = 102,
                    constantParam = 50,
                    calcPos = 953,
                    attackType = 11,
                ),
                effectRule(
                    detailId = 102,
                    effectId = 129,
                    calcPos = 953,
                    attackType = 11,
                ),
                effectRule(
                    detailId = 103,
                    effectId = 111,
                    effectParam = 104,
                    constantParam = 80,
                    calcPos = 954,
                    attackType = 11,
                ),
                effectRule(
                    detailId = 104,
                    effectId = 130,
                    calcPos = 954,
                    attackType = 11,
                ),
                effectRule(
                    detailId = 105,
                    effectId = 129,
                    calcPos = 953,
                    attackType = 11,
                ),
            ),
            rule(2, effectRule(201, 0), kind = SkillKind.ACTIVE),
        )

        val retriggers = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1, alliedSkillIds = listOf(2)),
        ).stateChanges.filterIsInstance<RetriggerSkillChange>()

        assertEquals(
            listOf(
                SkillKind.ACTIVE to 50,
                SkillKind.PURSUIT to 80,
                SkillKind.ACTIVE to 100,
            ),
            retriggers.map { it.skillKind to it.effectValueScalePercent },
        )
    }

    @Test
    fun `active retrigger extra parameter scales actual strategy damage only once`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 111,
                    effectParam = 102,
                    constantParam = 50,
                    calcPos = 953,
                    attackType = 11,
                ),
                effectRule(
                    detailId = 102,
                    effectId = 129,
                    calcPos = 953,
                    attackType = 11,
                ),
                effectRule(
                    detailId = 103,
                    effectId = 129,
                    calcPos = 953,
                    attackType = 11,
                ),
            ),
            rule(
                2,
                effectRule(
                    detailId = 201,
                    effectId = 302,
                    constantParam = 100,
                    attackType = 43,
                ),
                kind = SkillKind.ACTIVE,
            ),
        )
        val entryContext = context(skillId = 1, alliedSkillIds = listOf(2))
        val context = entryContext.copy(
            battleView = SkillBattleState(
                entryContext.request,
                entryContext.runtime,
            ).view,
        )
        val ally = entryContext.request.attacker.heroes.single { 2 in it.skillIds }
        val enemy = entryContext.request.defender.heroes.single()

        val damages = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        ).stateChanges.filterIsInstance<TroopDamageChange>()

        assertEquals(
            listOf(
                BattleDamageCalculator.strategy(
                    ally,
                    enemy,
                    ratePercent = 50,
                    origin = DamageOrigin.ACTIVE,
                ),
                BattleDamageCalculator.strategy(
                    ally,
                    enemy,
                    ratePercent = 100,
                    origin = DamageOrigin.ACTIVE,
                ),
            ),
            damages.map { it.amount },
        )
    }

    @Test
    fun `distance extra parameter is normalized and scoped to its referenced modifier`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 111,
                    effectParam = 102,
                    constantParam = 2_000_000,
                    calcPos = 991,
                    calcParam = 1,
                    attackType = 0,
                    availableRounds = 10,
                ),
                effectRule(
                    detailId = 102,
                    effectId = 531,
                    constantParam = 10,
                    calcPos = 991,
                    calcParam = 1,
                    attackType = 0,
                    availableRounds = 10,
                ),
                effectRule(
                    detailId = 103,
                    effectId = 531,
                    constantParam = 20,
                    calcPos = 991,
                    calcParam = 1,
                    attackType = 0,
                    availableRounds = 10,
                ),
            ),
        )
        val entryContext = context(skillId = 1)
        val context = entryContext.copy(
            battleView = SkillBattleState(
                entryContext.request,
                entryContext.runtime,
            ).view,
        )

        val modifiers = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        ).stateChanges.filterIsInstance<DamageModifierChange>()

        assertEquals(
            listOf(mapOf(991 to 2), emptyMap()),
            modifiers.map { it.extraParameters },
        )
    }

    @Test
    fun `referenced value change affects the next invocation without mutating the graph`() {
        val puppet = effectRule(
            detailId = 101,
            effectId = 125,
            effectParam = 201,
            constantParam = 10,
            attackType = 21,
            attackMax = 1,
            availableRounds = 2,
        )
        val graph = graph(
            rule(
                1,
                puppet,
                effectRule(
                    detailId = 102,
                    effectId = 112,
                    effectParam = 101,
                    constantParam = 5,
                    attackType = 0,
                ),
            ),
            rule(
                2,
                effectRule(
                    detailId = 201,
                    effectId = 101,
                    constantParam = 999,
                    attackType = 43,
                    probabilityInit = 0,
                    availableRounds = 1,
                ),
            ),
        )
        val entryContext = context(skillId = 1)
        val context = entryContext.copy(
            battleView = SkillBattleState(
                entryContext.request,
                entryContext.runtime,
            ).view,
        )
        val interpreter = interpreter(graph)

        val first = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        val second = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertEquals(
            listOf(10, 15),
            listOf(first, second).map { result ->
                result.stateChanges.filterIsInstance<BattleStatChange>().single().potency.value
            },
        )
        assertEquals(10, graph.details.single { it.detailId == 101 }.raw.constantParam)
    }

    @Test
    fun `round value change starts at round end and uses configured round count exactly`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 112,
                    effectParam = 102,
                    constantParam = 5,
                    calcPos = 32,
                    availableRounds = 2,
                ),
                effectRule(
                    detailId = 102,
                    effectId = 77,
                    constantParam = 10,
                ),
                kind = SkillKind.COMMAND,
            ),
        )
        val context = context(skillId = 1).copy(
            round = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
        )

        interpreter(graph).execute(1, BattleTrigger.BATTLE_COMMAND, context)

        assertEquals(0, context.runtime.referencedValueDelta(context.source, 1, 102))
        context.runtime.advanceReferencedValueChanges(1)
        assertEquals(5, context.runtime.referencedValueDelta(context.source, 1, 102))
        context.runtime.advanceReferencedValueChanges(1)
        assertEquals(5, context.runtime.referencedValueDelta(context.source, 1, 102))
        context.runtime.advanceReferencedValueChanges(2)
        assertEquals(10, context.runtime.referencedValueDelta(context.source, 1, 102))
        context.runtime.advanceReferencedValueChanges(3)
        assertEquals(10, context.runtime.referencedValueDelta(context.source, 1, 102))
    }

    @Test
    fun `round value change honors its configured delay`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 112,
                    effectParam = 102,
                    constantParam = 10,
                    calcPos = 32,
                    delayRound = 2,
                    availableRounds = 2,
                ),
                effectRule(detailId = 102, effectId = 77),
                kind = SkillKind.COMMAND,
            ),
        )
        val context = context(skillId = 1).copy(
            round = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
        )

        interpreter(graph).execute(1, BattleTrigger.BATTLE_COMMAND, context)

        context.runtime.advanceReferencedValueChanges(1)
        assertEquals(0, context.runtime.referencedValueDelta(context.source, 1, 102))
        context.runtime.advanceReferencedValueChanges(2)
        assertEquals(10, context.runtime.referencedValueDelta(context.source, 1, 102))
        context.runtime.advanceReferencedValueChanges(3)
        assertEquals(20, context.runtime.referencedValueDelta(context.source, 1, 102))
        context.runtime.advanceReferencedValueChanges(4)
        assertEquals(20, context.runtime.referencedValueDelta(context.source, 1, 102))
    }

    @Test
    fun `negative referenced value change retains the referenced value unit`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 125,
                    effectParam = 201,
                    constantParam = 10,
                    attackType = 21,
                ),
                effectRule(
                    detailId = 102,
                    effectId = 112,
                    effectParam = 101,
                    constantParam = -4,
                ),
            ),
            rule(2, effectRule(detailId = 201, effectId = 101)),
        )
        val entryContext = context(skillId = 1)
        val context = entryContext.copy(
            battleView = SkillBattleState(
                entryContext.request,
                entryContext.runtime,
            ).view,
        )
        val interpreter = interpreter(graph)

        val first = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        val second = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertEquals(
            listOf(10, 6),
            listOf(first, second).map { result ->
                result.stateChanges.filterIsInstance<BattleStatChange>().single().potency.value
            },
        )
    }

    @Test
    fun `large referenced value change selects the shifted child skill id`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    detailId = 101,
                    effectId = 112,
                    effectParam = 102,
                    constantParam = 1_000,
                ),
                effectRule(
                    detailId = 102,
                    effectId = 122,
                    childSkillIds = setOf(212_965),
                    constantParam = 212_965,
                ),
            ),
            rule(212_965, effectRule(212_965_01, 77)),
            rule(213_965, effectRule(213_965_01, 77)),
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1),
        )

        assertEquals(listOf(1, 213_965), result.executedSkillIds)
        assertEquals(
            listOf(213_965_01),
            result.stateChanges.filterIsInstance<MarkerEffectChange>().map { it.detailId },
        )
    }

    @Test
    fun `clear effect change removes only matching referenced detail on selected target`() {
        val store = BattleEffectStore()
        val source = ref(Side.ATTACKER, 0, 1)
        val target = ref(Side.ATTACKER, 1, 2)
        store.apply(activeEffect(source, target, detailId = 201, effectId = 77))
        store.apply(activeEffect(source, target, detailId = 202, effectId = 77))
        val change = ClearReferencedEffectChange(
            source = source,
            target = target,
            rootSkillId = 1,
            skillId = 1,
            detailId = 101,
            referencedDetailId = 201,
            referencedEffectId = 77,
            parameters = metaParameters(detailId = 101, effectId = 152, effectParam = 201),
        )

        val removed = change.apply(store)

        assertEquals(listOf(201), removed.removed.map { it.detailId })
        assertEquals(listOf(202), store.effectsFor(target).map { it.detailId })
    }

    @Test
    fun `retrigger executes an allied active skill once and records duplicate root attempts`() {
        val graph = graph(
            rule(
                1,
                effectRule(
                    101,
                    129,
                    attackType = 11,
                    availableHit = 1,
                ),
            ),
            rule(2, effectRule(201, 0)),
        )
        val context = context(
            skillId = 1,
            alliedSkillIds = listOf(2),
        )
        val interpreter = interpreter(graph)

        val first = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        val second = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertEquals(listOf(1, 2), first.executedSkillIds)
        assertEquals(listOf(1), second.executedSkillIds)
        assertEquals(2, context.runtime.count(context.source, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 1))
        assertEquals(
            1,
            context.runtime.count(
                ref(Side.ATTACKER, 1, 2),
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                2,
            ),
        )
    }

    @Test
    fun `retrigger rolls each child once and counts only successful execution`() {
        val random = SequenceRandom(0, 99, 0, 0, 0)
        val graph = graph(
            rule(
                1,
                effectRule(
                    101,
                    129,
                    attackType = 11,
                    availableHit = 1,
                ),
            ),
            rule(2, effectRule(201, 0), probability = 50),
        )
        val context = context(
            skillId = 1,
            random = random,
            alliedSkillIds = listOf(2),
        )
        val interpreter = interpreter(graph)

        val failed = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        val succeeded = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        val capped = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertEquals(listOf(1), failed.executedSkillIds)
        assertEquals(listOf(1, 2), succeeded.executedSkillIds)
        assertEquals(listOf(1), capped.executedSkillIds)
        assertEquals(5, random.calls)
        assertEquals(
            1,
            context.runtime.count(
                ref(Side.ATTACKER, 1, 2),
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                2,
            ),
        )
    }

    @Test
    fun `retrigger per skill cap skips only the capped skill`() {
        val graph = graph(
            rule(1, effectRule(101, 129, attackType = 11, availableHit = 1)),
            rule(2, effectRule(201, 0)),
            rule(3, effectRule(301, 0)),
        )
        val context = context(skillId = 1, alliedSkillIds = listOf(2, 3))
        context.runtime.recordSuccessfulExecution(
            ref(Side.ATTACKER, 1, 2),
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            2,
        )

        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context,
        )

        assertEquals(listOf(1, 3), result.executedSkillIds)
    }

    @Test
    fun `safe interpreter diagnoses a bad branch and continues in result order`() {
        val graph = graph(
            rule(
                1,
                effectRule(101, 151, effectParam = 999),
                effectRule(102, 77),
            ),
        )
        val emitted = mutableListOf<SkillExecutionDiagnostic>()
        val context = context(skillId = 1)

        val result = SkillRuleInterpreter.safe(
            graph = graph,
            registry = BattleEffectRegistry.strict(graph).registerMetaEffects(),
            diagnosticSink = emitted::add,
        ).execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertEquals(1, result.diagnostics.size)
        assertEquals(result.diagnostics, emitted)
        assertEquals("MISSING_REFERENCED_DETAIL", result.diagnostics.single().code)
        assertEquals(1, result.diagnostics.single().skillId)
        assertEquals(101, result.diagnostics.single().detailId)
        assertEquals(151, result.diagnostics.single().effectId)
        assertEquals(BattleTrigger.ACTIVE_SKILL_ATTEMPT, result.diagnostics.single().trigger)
        assertEquals(listOf(SkillExecutionFrame(1, 101)), result.diagnostics.single().fullPath)
        assertEquals(listOf(102), result.stateChanges.filterIsInstance<MarkerEffectChange>().map { it.detailId })
        assertEquals(emptyList(), context.runtime.currentCallPath())
        assertEquals(emptyList(), context.runtime.currentDetailPath())
    }

    @Test
    fun `safe interpreter retains the full referenced cycle path`() {
        val graph = graph(
            rule(
                1,
                effectRule(101, 151, effectParam = 201),
                effectRule(102, 77),
            ),
            rule(2, effectRule(201, 151, effectParam = 101)),
        )

        val result = SkillRuleInterpreter.safe(
            graph,
            BattleEffectRegistry.strict(graph).registerMetaEffects(),
        ) {}.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context(skillId = 1))

        assertEquals(
            listOf(
                SkillExecutionFrame(1, 101),
                SkillExecutionFrame(2, 201),
                SkillExecutionFrame(1, 101),
            ),
            result.diagnostics.single().fullPath,
        )
        assertEquals(listOf(102), result.stateChanges.filterIsInstance<MarkerEffectChange>().map { it.detailId })
    }

    @Test
    fun `safe interpreter retains the full child recursion dependency path`() {
        val graph = graph(
            rule(
                1,
                effectRule(101, 122, setOf(2), constantParam = 2),
                effectRule(102, 77),
            ),
            rule(2, effectRule(201, 122, setOf(1), constantParam = 1)),
        )

        val result = SkillRuleInterpreter.safe(
            graph,
            BattleEffectRegistry.strict(graph).registerMetaEffects(),
        ) {}.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context(skillId = 1))

        assertEquals(listOf(1, 2, 1), result.diagnostics.single().dependencyPath)
        assertEquals(listOf(102), result.stateChanges.filterIsInstance<MarkerEffectChange>().map { it.detailId })
    }

    @Test
    fun `safe interpreter does not swallow fatal handler errors`() {
        val graph = graph(rule(1, effectRule(101, 999)))
        val registry = BattleEffectRegistry.strict(graph).register(
            EffectHandlerRegistration.implemented(
                999,
                object : ImplementedBattleEffectHandler {
                    override val semanticId: String = "test.fatal"
                    override fun execute(invocation: EffectInvocation): EffectExecution =
                        throw AssertionError("fatal")
                },
            ),
        )

        assertFailsWith<AssertionError> {
            SkillRuleInterpreter.safe(graph, registry) {}
                .execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context(skillId = 1))
        }
    }

    @Test
    fun `real meta row maps every raw parameter into a typed operation intent`() {
        val repository = BattleConfigRepository.loadDefault()
        val catalogGraph = SkillRuleCatalog.build(
            SkillScope(setOf(210915), emptySet()),
            repository,
        )
        val realRule = catalogGraph.rule(210915)!!
        val matchingDetails = catalogGraph.details.filter { it.detailId == 21091503 }
        assertEquals(1, matchingDetails.size, "catalog detail 21091503 must be unique")
        val detail = matchingDetails.single()
        val realGraph = graph(realRule.copy(details = listOf(detail)))
        val result = SkillRuleInterpreter(
            graph = realGraph,
            registry = BattleEffectRegistry.strict(realGraph).registerMetaEffects(),
            conditionInterpreter = PendingSkillConditionInterpreter { _, _, _ -> true },
        ).execute(
            210915,
            realRule.kind.toTrigger(),
            context(skillId = 210915),
        )
        val matchingChanges = result.stateChanges.filterIsInstance<ModifierEffectChange>()
            .filter { it.spec.detailId == detail.detailId }
        assertEquals(2, matchingChanges.size, "attack type 23 must include the source and its ally")
        val change = matchingChanges.first()
        assertTrue(matchingChanges.all { it.modifier == change.modifier })
        assertEquals(
            BattleModifier.DefenseIgnorePercent(percent = 30, stat = BattleStat.DEFENSE),
            change.modifier,
        )
        assertEquals(8, change.spec.availableRounds)
    }

    @Test
    fun `real damage sharing row emits a typed half strategy sharing effect`() {
        val repository = BattleConfigRepository.loadDefault()
        val catalogGraph = SkillRuleCatalog.build(
            SkillScope(setOf(211934), emptySet()),
            repository,
        )
        val realRule = catalogGraph.rule(211934)!!
        val detail = catalogGraph.details.single { it.detailId == 21193401 }
        val realGraph = graph(realRule.copy(details = listOf(detail)))

        val result = SkillRuleInterpreter(
            graph = realGraph,
            registry = BattleEffectRegistry.strict(realGraph).registerMetaEffects(),
            conditionInterpreter = PendingSkillConditionInterpreter { _, _, _ -> true },
        ).execute(211934, realRule.kind.toTrigger(), context(skillId = 211934))

        val sharing = result.stateChanges.filterIsInstance<DamageRedirectionEffectChange>().single()
        assertEquals(50, sharing.sharePercent)
        assertEquals(DamageSchool.STRATEGY, sharing.school)
        assertEquals(1, sharing.spec.availableHit)
    }

    @Test
    fun `real jade seal row emits one typed team damage accumulator`() {
        val repository = BattleConfigRepository.loadDefault()
        val catalogGraph = SkillRuleCatalog.build(
            SkillScope(setOf(210262), emptySet()),
            repository,
        )
        val realRule = catalogGraph.rule(210262)!!
        val detail = catalogGraph.details.single { it.detailId == 21026201 }
        val realGraph = graph(realRule.copy(details = listOf(detail)))
        val context = context(skillId = 210262)

        val result = interpreter(realGraph).execute(
            210262,
            realRule.kind.toTrigger(),
            context,
        )

        val accumulator = result.stateChanges
            .filterIsInstance<DamageAbsorptionAccumulatorEffectChange>()
            .single()
        val expectedPercent = (
            DefaultBattleValueCalculator().effectValue(
                detail,
                context.request.attacker.heroes.first(),
            ) as TypedBattlePotency.Resolved
            ).value
        assertEquals(context.source, accumulator.spec.source)
        assertEquals(context.source, accumulator.spec.target)
        assertEquals(
            context.request.attacker.heroes.mapTo(linkedSetOf()) {
                ref(Side.ATTACKER, it.position, it.id.value)
            },
            accumulator.protectedTargets.toSet(),
        )
        assertEquals(expectedPercent, accumulator.absorbPercent)
        assertEquals(TypedBattlePotency.percent(expectedPercent), accumulator.spec.potency)
        assertEquals(9, accumulator.spec.availableRounds)
    }

    @Test
    fun `real jade seal release row registers a delayed schedule without executing its template`() {
        val repository = BattleConfigRepository.loadDefault()
        val catalogGraph = SkillRuleCatalog.build(
            SkillScope(setOf(211262), emptySet()),
            repository,
        )
        val realRule = catalogGraph.rule(211262)!!
        val release = catalogGraph.details.single { it.detailId == 21126204 }
        val template = catalogGraph.details.single { it.detailId == 21126202 }
        val realGraph = graph(realRule.copy(details = listOf(release, template)))
        val context = context(skillId = 211262).copy(
            round = 0,
            trigger = BattleTrigger.BATTLE_COMMAND,
        )

        val result = interpreter(realGraph).execute(
            211262,
            realRule.kind.toTrigger(),
            context,
        )

        val schedule = result.stateChanges
            .filterIsInstance<DamageReleaseScheduleEffectChange>()
            .single()
        assertEquals(context.source, schedule.spec.source)
        assertEquals(context.source, schedule.spec.target)
        assertEquals(context.source, schedule.target)
        assertEquals(21126202, schedule.referencedDetailId)
        assertEquals(302, schedule.referencedEffectId)
        assertEquals(50, schedule.baseReleasePercent)
        assertEquals(2, schedule.firstReleaseRound)
        assertEquals(0, schedule.spec.delayRound)
        assertEquals(EffectStartBoundary.IMMEDIATE, schedule.spec.startBoundary)
        assertTrue(result.stateChanges.none { it is TroopDamageChange })
    }

    @Test
    fun `trigger last applied effect emits a typed target intent`() {
        val repository = BattleConfigRepository.loadDefault()
        val catalogGraph = SkillRuleCatalog.build(SkillScope(setOf(214254), emptySet()), repository)
        val realRule = catalogGraph.rule(214254)!!
        val realGraph = graph(realRule)

        val result = SkillRuleInterpreter(
            graph = realGraph,
            registry = BattleEffectRegistry.strict(realGraph).registerMetaEffects(),
            conditionInterpreter = PendingSkillConditionInterpreter { _, _, _ -> true },
        ).execute(214254, realRule.kind.toTrigger(), context(skillId = 214254))

        val trigger = result.stateChanges.filterIsInstance<TriggerLastAppliedEffectChange>().single()
        assertEquals(21425401, trigger.detailId)
        assertTrue(trigger.targets.isNotEmpty())
    }

    @Test
    fun `morale increase retains typed intelligence scaling`() {
        val repository = BattleConfigRepository.loadDefault()
        val catalogGraph = SkillRuleCatalog.build(
            SkillScope(setOf(212294), emptySet()),
            repository,
        )
        val realRule = catalogGraph.rule(212294)!!
        val detail = catalogGraph.details.single { it.detailId == 21229401 }
        val realGraph = graph(realRule.copy(details = listOf(detail)))
        val result = interpreter(realGraph).execute(
            212294,
            realRule.kind.toTrigger(),
            context(skillId = 212294, sourceStrategy = 180),
        )
        val morale = result.stateChanges.filterIsInstance<MoraleEffectChange>().single()

        assertEquals(TypedBattlePotency.flat(7), morale.potency)
        assertEquals(MetaEffectParameters.from(detail), morale.parameters)
    }

    @Test
    fun `result collections are immutable`() {
        val graph = graph(rule(1, effectRule(101, 0)))
        val result = interpreter(graph).execute(
            1,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            context(skillId = 1),
        )

        assertFailsWith<UnsupportedOperationException> {
            (result.executedSkillIds as MutableList<Int>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (result.events as MutableList<SkillExecutionEvent>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (result.stateChanges as MutableList<BattleStateChange>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (result.diagnostics as MutableList<SkillExecutionDiagnostic>).clear()
        }
    }

    private fun interpreter(graph: SkillRuleGraph): SkillRuleInterpreter =
        SkillRuleInterpreter(
            graph = graph,
            registry = BattleEffectRegistry.strict(graph)
                .registerCoreEffects(BattleEffectStore())
                .registerMetaEffects(),
        )

    private fun graph(vararg rules: SkillRule): SkillRuleGraph =
        SkillRuleGraph(
            rules = rules.associateBy(SkillRule::skillId),
            effectIds = rules.flatMap { it.details }.mapTo(linkedSetOf()) { it.effectId },
            rootSkillIds = setOf(rules.first().skillId),
        )

    private fun rule(
        skillId: Int,
        vararg details: SkillEffectRule,
        probability: Int = 100,
        kind: SkillKind = SkillKind.ACTIVE,
        prepareRounds: Int = 0,
    ): SkillRule =
        SkillRule(
            skillId = skillId,
            kind = kind,
            rawSkillType = when (kind) {
                SkillKind.PASSIVE -> 1
                SkillKind.COMMAND -> 2
                SkillKind.ACTIVE -> 3
                SkillKind.PURSUIT -> 4
                SkillKind.UNKNOWN -> 99
            },
            probability = probability,
            prepareRounds = prepareRounds,
            hitRange = 5,
            details = details.toList(),
        ).let { rule ->
            rule.copy(
                details = rule.details.map {
                    it.copy(skillKind = rule.kind, rawSkillType = rule.rawSkillType)
                },
            )
        }

    private fun effectRule(
        detailId: Int,
        effectId: Int,
        childSkillIds: Set<Int> = emptySet(),
        effectParam: Int = 0,
        constantParam: Int = 0,
        intelParam: Int = 0,
        attributeType: Int = 0,
        attackType: Int = 0,
        attackMax: Int = 1,
        availableHit: Int = 0,
        probabilityInit: Int = 100,
        probabilityMax: Int = probabilityInit,
        availableRounds: Int = 2,
        castCondition: Int = 0,
        customSelectFlag: Int = 0,
        calcPos: Int = 0,
        calcParam: Int = 0,
        delayRound: Int = 0,
        addCountMax: Int = 0,
        rawLockFlag: Int = 0,
        rawBuffType: Int = 0,
    ): SkillEffectRule =
        SkillEffectRule(
            detailId = detailId,
            effectId = effectId,
            childSkillIds = childSkillIds,
            raw = SkillDetailConfig(
                detailId = detailId,
                effectId = effectId,
                effectParam = effectParam,
                calcPos = calcPos,
                calcParam = calcParam,
                attackType = attackType,
                targetType = 0,
                selectType = 0,
                availableHit = availableHit,
                intelParam = intelParam,
                constantParam = constantParam,
                probabilityInit = probabilityInit,
                probabilityMax = probabilityMax,
                castCondition = castCondition,
                customSelectFlag = customSelectFlag,
                attackMax = attackMax,
                delayRound = delayRound,
                availableRounds = availableRounds,
                addCountMax = addCountMax,
                lockFlag = rawLockFlag,
                buffType = rawBuffType,
                attributeType = attributeType,
                effectName = "fixture-$effectId",
            ),
            effectBuffType = when (effectId) {
                123, 114, 152, 181, 231, 261 -> 1
                else -> 2
            },
            effectReplaceType = 0,
        )

    private fun context(
        skillId: Int,
        random: BattleRandom = FixedBattleRandom(0),
        sourceModifiers: List<com.stzb.battle.core.BattleModifier> = emptyList(),
        sourceExtraSkillIds: List<Int> = emptyList(),
        alliedSkillIds: List<Int> = emptyList(),
        enemySkillIds: List<Int> = emptyList(),
        sourceStrategy: Int = 100,
        sourceSkillLevel: Int = 1,
        sourceMorale: Int = 100,
    ): SkillBattleContext {
        val source = hero(
            1,
            0,
            skillIds = listOf(skillId) + sourceExtraSkillIds,
            skillLevels = listOf(sourceSkillLevel),
            modifiers = sourceModifiers,
            strategy = sourceStrategy,
        ).copy(morale = sourceMorale)
        val ally = hero(2, 1, skillIds = alliedSkillIds)
        val enemy = hero(3, 0, skillIds = enemySkillIds)
        return SkillBattleContext(
            request = BattleRequest(
                attacker = BattleTeam(listOf(source, ally)),
                defender = BattleTeam(listOf(enemy)),
            ),
            runtime = SkillRuntimeState(),
            random = random,
            round = 3,
            source = ref(Side.ATTACKER, source.position, source.id.value),
            rootSkillId = skillId,
            currentSkillId = skillId,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
        )
    }

    private fun metaParameters(
        detailId: Int,
        effectId: Int,
        effectParam: Int = 0,
    ): MetaEffectParameters =
        MetaEffectParameters.from(
            effectRule(
                detailId = detailId,
                effectId = effectId,
                effectParam = effectParam,
            ).copy(skillKind = SkillKind.ACTIVE, rawSkillType = 3),
        )

    private fun hero(
        id: Int,
        position: Int,
        skillIds: List<Int> = emptyList(),
        skillLevels: List<Int> = emptyList(),
        modifiers: List<com.stzb.battle.core.BattleModifier> = emptyList(),
        strategy: Int = 100,
    ): BattleHero =
        BattleHero(
            id = BattleHeroId(id),
            position = position,
            stats = BattleStats(100, 100, strategy, 100, 100, 5),
            troops = 1_000,
            maxTroops = 1_000,
            skillIds = skillIds,
            skillLevels = skillLevels,
            modifiers = modifiers,
            morale = 100,
        )

    private fun activeEffect(
        source: BattleHeroRef,
        target: BattleHeroRef,
        detailId: Int,
        effectId: Int,
        remainingHits: Int? = null,
    ): ActiveSkillEffect =
        ActiveSkillEffect(
            source = source,
            target = target,
            rootSkillId = 1,
            skillId = 1,
            skillKind = SkillKind.ACTIVE,
            sourceSkillType = 3,
            detailId = detailId,
            effectId = effectId,
            category = EffectCategory.BENEFICIAL,
            conflict = 0,
            strength = 1,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            stacks = 1,
            remainingRounds = if (remainingHits == null) 2 else null,
            remainingHits = remainingHits,
            clearPerHit = false,
        )

    private fun ref(side: Side, position: Int, heroId: Int): BattleHeroRef =
        BattleHeroRef(side, position, BattleHeroId(heroId))

    private class CountingRandom(
        private val value: Int,
    ) : BattleRandom {
        var calls: Int = 0

        override fun nextInt(bound: Int): Int {
            calls += 1
            return value.coerceIn(0, bound - 1)
        }
    }

    private class SequenceRandom(
        vararg values: Int,
    ) : BattleRandom {
        private val values = ArrayDeque(values.toList())
        var calls: Int = 0

        override fun nextInt(bound: Int): Int {
            calls += 1
            return values.removeFirst().coerceIn(0, bound - 1)
        }
    }

    private fun SkillKind.toTrigger(): BattleTrigger =
        when (this) {
            SkillKind.PASSIVE -> BattleTrigger.BATTLE_PASSIVE
            SkillKind.COMMAND -> BattleTrigger.BATTLE_COMMAND
            SkillKind.ACTIVE -> BattleTrigger.ACTIVE_SKILL_ATTEMPT
            SkillKind.PURSUIT -> BattleTrigger.PURSUIT_ATTEMPT
            SkillKind.UNKNOWN -> error("unknown kind")
        }

    private companion object {
        val EXPECTED_META_EFFECT_IDS = setOf(
            0,
            77, 79, 80,
            81, 82, 83,
            88,
            111, 112, 113, 114,
            118,
            121, 122, 123,
            125, 127, 128, 129, 130, 131, 132,
            141, 149, 150,
            151, 152, 153,
            161, 171, 181, 190, 193, 194, 199, 200, 210, 220, 231, 261, 271, 281, 311, 312, 313,
            404, 407, 408, 409,
            553,
        )
    }
}
