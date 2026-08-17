package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleEffectValueUnit
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroId
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.BattleTeam
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.DamageOrigin
import com.stzb.battle.core.DamageSchool
import com.stzb.battle.core.DamageTag
import com.stzb.battle.core.EffectCategory
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillDetailConfig
import com.stzb.battle.core.SkillKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillTimingTest {
    private val attacker = ref(Side.ATTACKER, 0, 1)
    private val defender = ref(Side.DEFENDER, 0, 2)

    @Test
    fun `prepared skill rolls once snapshots identity and reselects target on completion`() {
        val random = CountingRandom(0)
        var selected = defender
        val fixture = fixture(prepareRounds = 1) { invocation ->
            EffectExecution(listOf(TargetChange(selected, invocation.context.currentSkillId)), emptyList())
        }
        val context = context(random = random)

        val started = fixture.coordinator.attempt(10, context)
        assertEquals(
            0,
            fixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10),
            "starting preparation is not a successful execution",
        )
        selected = attacker
        val completed = fixture.coordinator.onRound(context.copy(round = 2))

        assertEquals(1, random.calls)
        val start = started.events.filterIsInstance<SkillPreparationStartedEvent>().single()
        assertEquals(attacker, start.snapshot.source)
        assertEquals(10, start.snapshot.rootSkillId)
        assertEquals(10, start.snapshot.skillId)
        assertEquals(BattleTrigger.ACTIVE_SKILL_ATTEMPT, start.snapshot.trigger)
        assertEquals(1, start.snapshot.startedRound)
        assertEquals(2, start.snapshot.readyRound)
        assertEquals(null, start.snapshot.lockedTargets)
        assertIs<BattleEvent.SkillPreparationStarted>(
            started.events.filterIsInstance<BattleOutputEvent>().single().event,
        )
        assertEquals(attacker, completed.stateChanges.filterIsInstance<TargetChange>().single().target)
        val completion = completed.events.filterIsInstance<SkillTimingEvent.PreparationCompleted>().single()
        assertEquals(attacker, completion.source)
        assertEquals(10, completion.rootSkillId)
        assertEquals(10, completion.currentSkillId)
        assertEquals(1, completion.startedRound)
        assertEquals(2, completion.readyRound)
        assertEquals(2, completion.completedRound)
        assertEquals(BattleTrigger.ACTIVE_SKILL_ATTEMPT, completion.trigger)
        assertNull(completion.lockedTargets)
        assertNull(completion.reselectedTargets)
        assertIs<SkillTriggered>(completed.events[1])
        assertEquals(1, fixture.runtime.attemptCount(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertEquals(1, fixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
    }

    @Test
    fun `preparation completion is emitted once only when ready and never after cancellation`() {
        val fixture = fixture(prepareRounds = 2)
        val context = context()
        fixture.coordinator.attempt(10, context)

        assertTrue(
            fixture.coordinator.onRound(context.copy(round = 2))
                .events.none { it is SkillTimingEvent.PreparationCompleted },
        )
        val completed = fixture.coordinator.onRound(context.copy(round = 3))
        assertEquals(1, completed.events.filterIsInstance<SkillTimingEvent.PreparationCompleted>().size)
        assertTrue(
            fixture.coordinator.onRound(context.copy(round = 3))
                .events.none { it is SkillTimingEvent.PreparationCompleted },
        )

        val cancelledFixture = fixture(prepareRounds = 2)
        cancelledFixture.coordinator.attempt(10, context)
        assertEquals(
            0,
            cancelledFixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10),
        )
        val cancelled = cancelledFixture.coordinator.cancelPreparations(
            attacker,
            round = 2,
            reason = PreparationCancelReason.CONFUSION,
        )
        assertEquals(1, cancelled.events.filterIsInstance<SkillPreparationCancelledEvent>().size)
        assertTrue(cancelled.events.none { it is SkillTimingEvent.PreparationCompleted })
        assertTrue(
            cancelledFixture.coordinator.onRound(context.copy(round = 3))
                .events.none { it is SkillTimingEvent.PreparationCompleted },
        )
        assertEquals(
            0,
            cancelledFixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10),
            "cancelled preparation must never consume a successful execution count",
        )
    }

    @Test
    fun `locked preparation targets are preserved only when explicitly requested`() {
        var selected = defender
        val fixture = fixture(prepareRounds = 1) { invocation ->
            EffectExecution(
                listOf(TargetChange(invocation.preselectedTargets?.single() ?: selected, 10)),
                emptyList(),
            )
        }
        val context = context()

        fixture.coordinator.attempt(
            10,
            context,
            TimingAttemptOptions(lockedTargets = listOf(defender)),
        )
        selected = attacker
        val completed = fixture.coordinator.onRound(context.copy(round = 2))

        assertEquals(defender, completed.stateChanges.filterIsInstance<TargetChange>().single().target)
    }

    @Test
    fun `failed probability records an attempt but no successful execution and same round retry is rejected`() {
        val random = CountingRandom(99)
        val fixture = fixture(probability = 50)
        val context = context(random = random)

        assertEquals(SkillExecutionResult.EMPTY, fixture.coordinator.attempt(10, context))
        assertTrue(fixture.coordinator.attempt(10, context).stateChanges.single() is SkillAttemptRejectedChange)

        assertEquals(1, random.calls)
        assertEquals(1, fixture.runtime.attemptCount(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertEquals(0, fixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
    }

    @Test
    fun `attempt and execution counts isolate attacker and defender identities`() {
        val fixture = fixture()
        fixture.coordinator.attempt(10, context())
        fixture.coordinator.attempt(
            10,
            context().copy(source = defender, round = 2),
        )

        assertEquals(1, fixture.runtime.attemptCount(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertEquals(1, fixture.runtime.attemptCount(defender, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertEquals(1, fixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertEquals(1, fixture.runtime.count(defender, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
    }

    @Test
    fun `duplicate preparation is rejected and preparation reduction can make execution immediate`() {
        val random = CountingRandom(0)
        val fixture = fixture(prepareRounds = 2)
        val context = context(random)

        fixture.coordinator.attempt(10, context, TimingAttemptOptions(oncePerRound = false))
        val duplicate = fixture.coordinator.attempt(10, context.copy(round = 2), TimingAttemptOptions(oncePerRound = false))
        assertIs<SkillPreparationRejectedChange>(duplicate.stateChanges.single())
        assertEquals(1, random.calls)
        assertEquals(1, fixture.runtime.attemptCount(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertEquals(0, fixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))

        fixture.coordinator.cancelPreparations(attacker, round = 2, reason = PreparationCancelReason.CLEANSE)
        val reduced = fixture.coordinator.attempt(
            10,
            context.copy(round = 3),
            TimingAttemptOptions(
                oncePerRound = false,
                preparationReductionRounds = 1,
            ),
        )
        assertEquals(4, reduced.events.filterIsInstance<SkillPreparationStartedEvent>().single().snapshot.readyRound)

        fixture.coordinator.cancelPreparations(attacker, round = 3, reason = PreparationCancelReason.CLEANSE)
        val immediate = fixture.coordinator.attempt(
            10,
            context.copy(round = 4),
            TimingAttemptOptions(
                oncePerRound = false,
                preparationReductionRounds = 2,
            ),
        )
        assertEquals(listOf(10), immediate.executedSkillIds)
        assertTrue(immediate.events.none { it is SkillPreparationStartedEvent })
        assertEquals(1, fixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
    }

    @Test
    fun `confusion and hesitation cancel only at immediate or delayed activation timing`() {
        listOf(501, 502).forEach { effectId ->
            val fixture = fixture(prepareRounds = 3)
            val context = context()
            fixture.coordinator.attempt(10, context)
            val immediate = fixture.coordinator.activate(
                ScheduledEffectActivationChange(
                    controlSpec(effectId, delayRound = 0).copy(target = attacker),
                ),
                round = 1,
            )
            assertTrue(fixture.runtime.preparedSkills().isEmpty(), "effect=$effectId")
            assertIs<SkillPreparationCancelledChange>(
                immediate.stateChanges.filterIsInstance<SkillPreparationCancelledChange>().single(),
            )

            fixture.coordinator.attempt(
                10,
                context.copy(round = 2),
                TimingAttemptOptions(oncePerRound = false),
            )
            fixture.coordinator.enqueue(
                ScheduledEffectActivationChange(
                    controlSpec(effectId, delayRound = 1).copy(target = attacker),
                ),
                currentRound = 2,
                currentHit = 0,
            )
            assertEquals(1, fixture.runtime.preparedSkills().size)
            assertTrue(fixture.coordinator.onRound(context.copy(round = 2)).events.none {
                it is SkillPreparationCancelledEvent
            })
            val delayed = fixture.coordinator.onRound(context.copy(round = 3))
            assertTrue(fixture.runtime.preparedSkills().isEmpty())
            assertEquals(1, delayed.events.filterIsInstance<SkillPreparationCancelledEvent>().size)
        }
    }

    @Test
    fun `delay queue advances rounds and hits at exact boundary in stable order exactly once`() {
        val fixture = fixture()
        fixture.coordinator.enqueue(marker(1, delayRound = 1, delayHit = 1), currentRound = 1, currentHit = 0)
        fixture.coordinator.enqueue(marker(2, delayRound = 1, delayHit = 1), currentRound = 1, currentHit = 0)
        fixture.coordinator.enqueue(marker(3, delayRound = 0, delayHit = 2), currentRound = 1, currentHit = 0)

        assertEquals(emptyList(), fixture.coordinator.onHit(context()).stateChanges)
        assertEquals(TimingPosition(1, 1), fixture.coordinator.position())
        assertEquals(listOf(3), fixture.coordinator.onHit(context()).markerIds())
        assertEquals(TimingPosition(1, 2), fixture.coordinator.position())
        assertEquals(emptyList(), fixture.coordinator.onRound(context().copy(round = 2)).stateChanges)
        assertEquals(TimingPosition(2, 0), fixture.coordinator.position())
        assertEquals(listOf(1, 2), fixture.coordinator.onHit(context().copy(round = 2)).markerIds())
        assertEquals(emptyList(), fixture.coordinator.onHit(context().copy(round = 2)).stateChanges)
    }

    @Test
    fun `scheduler preserves attacker defender and complete persistent identity`() {
        val fixture = fixture()
        val scheduled = ScheduledEffectActivationChange(
            controlSpec(701, delayRound = 1).copy(
                source = defender,
                target = attacker,
                rootSkillId = 91,
                skillId = 92,
                detailId = 9_201,
            ),
        )
        fixture.coordinator.enqueue(scheduled, currentRound = 4, currentHit = 0)

        val due = fixture.coordinator.onRound(context().copy(round = 5))
        val applied = due.stateChanges.filterIsInstance<ApplyBattleEffectChange>().single().spec
        val timingDue = due.timingDues.single()

        assertEquals(defender, applied.source)
        assertEquals(attacker, applied.target)
        assertEquals(91, applied.rootSkillId)
        assertEquals(92, applied.skillId)
        assertEquals(9_201, applied.detailId)
        assertEquals(701, applied.effectId)
        assertEquals(scheduled, timingDue.change)
        assertEquals(5, timingDue.dueRound)
        assertEquals(0, timingDue.dueHit)
        assertTrue(timingDue.sequence >= 0)
    }

    @Test
    fun `scheduled status damage and recovery share one stable scheduler without identity loss`() {
        val fixture = fixture()
        val damageSpec = controlSpec(303, delayRound = 1).copy(
            detailId = 1_303,
            category = EffectCategory.HARMFUL,
        )
        val recoverySpec = controlSpec(402, delayRound = 1).copy(
            detailId = 1_402,
            category = EffectCategory.BENEFICIAL,
        )
        fixture.coordinator.enqueue(
            ScheduledDamageEffectChange(
                spec = damageSpec,
                school = DamageSchool.PHYSICAL,
                origin = DamageOrigin.ACTIVE,
                tags = setOf(DamageTag.ONGOING),
                status = BattleStatus.SHAKE,
                coefficientSource = BattleCoefficientSource.ATTACK,
                rawCoefficient = 0,
                calculationTypes = emptyList(),
            ),
            currentRound = 1,
            currentHit = 0,
        )
        fixture.coordinator.enqueue(
            ScheduledRecoveryEffectChange(
                spec = recoverySpec,
                potency = TypedBattlePotency.flat(50),
            ),
            currentRound = 1,
            currentHit = 0,
        )

        val dueResult = fixture.coordinator.onRound(context().copy(round = 2))
        val due = dueResult.stateChanges

        assertEquals(listOf(1_303, 1_402), due.map {
            when (it) {
                is ScheduledDamageEffectChange -> it.spec.detailId
                is ScheduledRecoveryEffectChange -> it.spec.detailId
                else -> error("unexpected $it")
            }
        })
        assertEquals(damageSpec, (due[0] as ScheduledDamageEffectChange).spec)
        assertEquals(recoverySpec, (due[1] as ScheduledRecoveryEffectChange).spec)
        assertEquals(2, dueResult.timingDues.size)
        assertEquals(due, dueResult.timingDues.map(SkillTimingDue::change))
    }

    @Test
    fun `available hit exhaustion and clear per hit use the activated spec lifecycle`() {
        val store = BattleEffectStore()
        val limited = controlSpec(544, delayRound = 0).copy(availableHit = 2)
        val clear = controlSpec(514, delayRound = 0).copy(clearPerHit = true)
        store.apply(limited.toActiveSkillEffect())
        store.apply(clear.toActiveSkillEffect())

        assertEquals(1, store.consumeHit(defender, 544).updated.single().remainingHits)
        assertEquals(544, store.consumeHit(defender, 544).expired.single().effectId)
        assertEquals(514, store.consumeHit(defender, 514).expired.single().effectId)
        assertTrue(store.effectsFor(defender).isEmpty())
    }

    @Test
    fun `strict timing rejects invalid enqueue atomically with complete context`() {
        val fixture = fixture()
        val error = assertFailsWith<InvalidSkillTimingException> {
            fixture.coordinator.enqueue(
                marker(1, delayRound = -1, delayHit = 0),
                currentRound = 1,
                currentHit = 0,
            )
        }

        assertEquals(10, error.skillId)
        assertEquals(1, error.detailId)
        assertTrue(error.message.orEmpty().contains("ScheduledTimingChange"))
        assertTrue(error.message.orEmpty().contains("rootSkillId=10"))
        assertEquals(0, fixture.runtime.delayedCount())
    }

    @Test
    fun `strict timing rejects backward round and due overflow`() {
        val fixture = fixture()
        fixture.coordinator.onRound(context().copy(round = 2))

        assertFailsWith<InvalidSkillTimingException> {
            fixture.coordinator.onRound(context().copy(round = 1))
        }
        assertFailsWith<InvalidSkillTimingException> {
            fixture.coordinator.enqueue(
                marker(1, delayRound = 1, delayHit = 0),
                currentRound = Int.MAX_VALUE,
                currentHit = 0,
            )
        }
        assertEquals(0, fixture.runtime.delayedCount())
    }

    @Test
    fun `safe timing diagnoses invalid input without queueing`() {
        val diagnostics = mutableListOf<SkillExecutionDiagnostic>()
        val fixture = fixture(safeTiming = true, diagnosticSink = diagnostics::add)

        val result = fixture.coordinator.enqueue(
            marker(1, delayRound = -1, delayHit = 0),
            currentRound = 1,
            currentHit = 0,
        )
        val zeroBoundary = fixture.coordinator.onRound(context().copy(round = 0))

        assertEquals("INVALID_TIMING", result.diagnostics.single().code)
        assertEquals("INVALID_TIMING", zeroBoundary.diagnostics.single().code)
        assertEquals(2, diagnostics.size)
        assertEquals(0, fixture.runtime.delayedCount())
    }

    @Test
    fun `unsupported enqueue is strict or safely diagnosed without queue mutation`() {
        val unsupported = TimingMarkerChange(77)
        val strict = fixture()

        val error = assertFailsWith<InvalidSkillTimingException> {
            strict.coordinator.enqueue(unsupported, currentRound = 1, currentHit = 0)
        }
        assertEquals(unsupported, error.specification)
        assertTrue(error.reason.contains("Unsupported scheduled change"))
        assertEquals(0, strict.runtime.delayedCount())

        val diagnostics = mutableListOf<SkillExecutionDiagnostic>()
        val safe = fixture(safeTiming = true, diagnosticSink = diagnostics::add)
        val result = safe.coordinator.enqueue(unsupported, currentRound = 1, currentHit = 0)

        assertEquals("INVALID_TIMING", result.diagnostics.single().code)
        assertTrue(result.diagnostics.single().reason.contains("TimingMarkerChange"))
        assertEquals(result.diagnostics, diagnostics)
        assertEquals(0, safe.runtime.delayedCount())
    }

    @Test
    fun `invalid attempt round and ready overflow are strict and atomic`() {
        val nonpositiveRandom = CountingRandom(0)
        val nonpositive = fixture(prepareRounds = 1)
        assertFailsWith<InvalidSkillTimingException> {
            nonpositive.coordinator.attempt(10, context(nonpositiveRandom).copy(round = 0))
        }
        assertAttemptStateUnchanged(nonpositive, nonpositiveRandom)

        val overflowRandom = CountingRandom(0)
        val overflow = fixture(prepareRounds = 1)
        assertFailsWith<InvalidSkillTimingException> {
            overflow.coordinator.attempt(
                10,
                context(overflowRandom).copy(round = Int.MAX_VALUE),
            )
        }
        assertAttemptStateUnchanged(overflow, overflowRandom)
    }

    @Test
    fun `safe invalid attempts diagnose and skip every mutation`() {
        val diagnostics = mutableListOf<SkillExecutionDiagnostic>()
        val random = CountingRandom(0)
        val fixture = fixture(
            prepareRounds = 1,
            safeTiming = true,
            diagnosticSink = diagnostics::add,
        )

        val nonpositive = fixture.coordinator.attempt(10, context(random).copy(round = 0))
        val overflow = fixture.coordinator.attempt(
            10,
            context(random).copy(round = Int.MAX_VALUE),
        )

        assertEquals("INVALID_TIMING", nonpositive.diagnostics.single().code)
        assertEquals("INVALID_TIMING", overflow.diagnostics.single().code)
        assertEquals(2, diagnostics.size)
        assertAttemptStateUnchanged(fixture, random)
    }

    private fun assertAttemptStateUnchanged(fixture: Fixture, random: CountingRandom) {
        assertEquals(0, random.calls)
        assertEquals(0, fixture.runtime.attemptCount(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertEquals(0, fixture.runtime.count(attacker, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 10))
        assertTrue(fixture.runtime.preparedSkills().isEmpty())
    }

    private fun SkillExecutionResult.markerIds(): List<Int> =
        stateChanges.filterIsInstance<TimingMarkerChange>().map { it.id }

    private fun marker(id: Int, delayRound: Int, delayHit: Int) =
        ScheduledTimingChange(
            snapshot = DelayedEffect(
                source = attacker,
                rootSkillId = 10,
                skillId = 10,
                detailId = id,
                dueRound = 0,
            ),
            delayRound = delayRound,
            delayHit = delayHit,
            change = TimingMarkerChange(id),
        )

    private fun controlSpec(effectId: Int, delayRound: Int) =
        PersistentEffectSpec(
            source = attacker,
            target = defender,
            rootSkillId = 10,
            skillId = 10,
            skillKind = SkillKind.ACTIVE,
            rawSkillType = 3,
            detailId = 1_000 + effectId,
            effectId = effectId,
            category = EffectCategory.BENEFICIAL,
            conflict = effectId,
            replaceType = 0,
            bindFlag = 0,
            maxStacks = 1,
            delayRound = delayRound,
            delayHit = 0,
            availableRounds = 2,
            availableHit = 0,
            clearPerHit = false,
            startBoundary = if (delayRound > 0) EffectStartBoundary.AFTER_DELAY else EffectStartBoundary.IMMEDIATE,
            potency = TypedBattlePotency.Resolved(BattleEffectValueUnit.FLAT, 1),
        )

    private fun fixture(
        prepareRounds: Int = 0,
        probability: Int = 100,
        safeTiming: Boolean = false,
        diagnosticSink: (SkillExecutionDiagnostic) -> Unit = {},
        handler: (EffectInvocation) -> EffectExecution = { EffectExecution.EMPTY },
    ): Fixture {
        val rule = SkillRule(
            skillId = 10,
            kind = SkillKind.ACTIVE,
            rawSkillType = 3,
            probability = probability,
            prepareRounds = prepareRounds,
            hitRange = 5,
            details = listOf(
                SkillEffectRule(
                    detailId = 1_001,
                    effectId = 999,
                    childSkillIds = emptySet(),
                    raw = SkillDetailConfig(
                        detailId = 1_001,
                        effectId = 999,
                        attackType = 0,
                        targetType = 0,
                        selectType = 0,
                        constantParam = 0,
                        intelParam = 0,
                        probabilityInit = 100,
                        probabilityMax = 100,
                        availableRounds = 0,
                        attackMax = 1,
                        effectName = "timing",
                    ),
                    skillKind = SkillKind.ACTIVE,
                    rawSkillType = 3,
                ),
            ),
        )
        val graph = SkillRuleGraph(mapOf(10 to rule), setOf(999))
        val registry = BattleEffectRegistry.strict(graph).register(
            EffectHandlerRegistration.implemented(
                999,
                object : ImplementedBattleEffectHandler {
                    override val semanticId: String = "test.timing"
                    override fun execute(invocation: EffectInvocation): EffectExecution = handler(invocation)
                },
            ),
        )
        val runtime = SkillRuntimeState()
        val interpreter = SkillRuleInterpreter(graph, registry)
        val coordinator = if (safeTiming) {
            CompleteTimingCoordinator.safe(graph, interpreter, runtime, diagnosticSink)
        } else {
            CompleteTimingCoordinator(graph, interpreter, runtime, diagnosticSink)
        }
        return Fixture(
            runtime,
            coordinator,
        )
    }

    private fun context(random: BattleRandom = CountingRandom(0)): SkillBattleContext {
        val source = hero(1, 0)
        val target = hero(2, 0)
        return SkillBattleContext(
            request = BattleRequest(BattleTeam(listOf(source)), BattleTeam(listOf(target))),
            runtime = SkillRuntimeState(),
            random = random,
            round = 1,
            source = attacker,
            rootSkillId = 10,
            currentSkillId = 10,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
        )
    }

    private fun hero(id: Int, position: Int) =
        BattleHero(
            id = BattleHeroId(id),
            position = position,
            stats = BattleStats(100, 100, 100, 100, 100, 5),
            troops = 1_000,
            maxTroops = 1_000,
        )

    private fun ref(side: Side, position: Int, heroId: Int) =
        BattleHeroRef(side, position, BattleHeroId(heroId))

    private data class Fixture(
        val runtime: SkillRuntimeState,
        val coordinator: CompleteTimingCoordinator,
    )

    private data class TargetChange(val target: BattleHeroRef, val skillId: Int) : BattleStateChange
    private data class TimingMarkerChange(val id: Int) : BattleStateChange

    private class CountingRandom(private val value: Int) : BattleRandom {
        var calls = 0
        override fun nextInt(bound: Int): Int {
            calls += 1
            return value.coerceIn(0, bound - 1)
        }
    }
}
