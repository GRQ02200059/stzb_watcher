package com.stzb.battle.core.skill

import com.stzb.battle.core.ActiveSkillEffect
import com.stzb.battle.core.ActionPermission
import com.stzb.battle.core.BattleEffectValueUnit
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroId
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.BattleTeam
import com.stzb.battle.core.ConfiguredBattleEffectValue
import com.stzb.battle.core.EffectCategory
import com.stzb.battle.core.FixedBattleRandom
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillDetailConfig
import com.stzb.battle.core.SkillKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlEffectHandlersTest {
    private val source = ref(Side.ATTACKER, 0, 1)
    private val ally = ref(Side.ATTACKER, 1, 2)
    private val target = ref(Side.DEFENDER, 0, 3)

    @Test
    fun `registry implements the exact configured control and action id set`() {
        assertEquals(41, controlIds.size)
        assertEquals(controlIds, ControlEffectHandlers.effectIds)
        assertEquals(
            controlIds,
            registry(controlIds.map(::rule)).implementedEffectIds(),
        )
    }

    @Test
    fun `every configured id emits a meaningful typed change`() {
        controlIds.forEach { effectId ->
            val execution = execute(effectId, BattleEffectStore())
            assertTrue(execution.stateChanges.isNotEmpty(), "effect=$effectId")
            val spec = when (val first = execution.stateChanges.first()) {
                is ApplyBattleEffectChange -> first.spec
                is ActionEffectChange -> first.spec
                is CleanseEffectsChange -> first.spec
                else -> null
            }
            spec?.let {
                assertEquals(effectId, it.effectId)
                assertEquals(source, it.source)
                assertEquals(target, it.target)
                assertEquals(10_000 + effectId, it.detailId)
                assertEquals(3, it.rawSkillType)
            }
        }
    }

    @Test
    fun `unknown raw skill kind fails instead of fabricating action semantics`() {
        val malformed = rule(544).copy(
            skillKind = SkillKind.UNKNOWN,
            rawSkillType = 14,
        )
        val error = assertFailsWith<UnsupportedConfiguredBattleValueException> {
            BattleEffectRegistry.strict(graph(controlIds.map(::rule)))
                .registerControlEffects(BattleEffectStore())
                .execute(malformed, context(target))
        }
        assertTrue(error.diagnostic.reason.orEmpty().contains("rawSkillType=14"))
    }

    @Test
    fun `control immunity matrix follows configured effects`() {
        val store = BattleEffectStore()
        store.apply(active(511, target = target, category = EffectCategory.BENEFICIAL))
        listOf(501, 502, 503, 505, 701, 702, 703, 901, 902, 903).forEach { effectId ->
            val blocked = execute(effectId, store)
            assertIs<EffectBlockedChange>(blocked.stateChanges.single(), "effect=$effectId")
            assertEquals(emptyList(), blocked.events)
        }
        store.apply(active(594, target = target, category = EffectCategory.BENEFICIAL))
        listOf(552, 752, 952).forEach { effectId ->
            val blocked = execute(effectId, store)
            assertIs<EffectBlockedChange>(blocked.stateChanges.single(), "effect=$effectId")
        }
        assertIs<ApplyBattleEffectChange>(execute(514, store).stateChanges.single())
        assertTrue(execute(515, store).events.single() is BattleEvent.StatusApplied)

        val disarmOnly = BattleEffectStore()
        disarmOnly.apply(active(594, target = target, category = EffectCategory.BENEFICIAL))
        assertIs<ApplyBattleEffectChange>(execute(501, disarmOnly).stateChanges.first())
    }

    @Test
    fun `command immunity blocks hostile command effects but not active or allied command effects`() {
        val store = BattleEffectStore()
        store.apply(active(121, target = target, category = EffectCategory.BENEFICIAL))

        val hostileCommand = execute(
            501,
            store,
            rule = rule(501).copy(skillKind = SkillKind.COMMAND, rawSkillType = 2),
        )
        assertEquals(121, hostileCommand.stateChanges.filterIsInstance<EffectBlockedChange>()
            .single().blockingEffectId)

        val hostileActive = execute(501, store)
        assertTrue(hostileActive.stateChanges.none { it is EffectBlockedChange })

        val alliedCommand = execute(
            511,
            store,
            rule = rule(511).copy(
                raw = rule(511).raw.copy(attackType = 21),
                skillKind = SkillKind.COMMAND,
                rawSkillType = 2,
            ),
        )
        assertTrue(alliedCommand.stateChanges.none { it is EffectBlockedChange })
    }

    @Test
    fun `resistance rolls when control is applied and blocks only on success`() {
        val store = BattleEffectStore()
        store.apply(
            active(118, target = target, category = EffectCategory.BENEFICIAL, strength = 50),
        )
        val blocked = execute(501, store, random = FixedBattleRandom(49))
        assertEquals(118, blocked.stateChanges.filterIsInstance<EffectBlockedChange>()
            .single().blockingEffectId)

        val applied = execute(501, store, random = FixedBattleRandom(50))
        assertTrue(applied.stateChanges.none { it is EffectBlockedChange })
        assertTrue(applied.stateChanges.any { it is ApplyBattleEffectChange })
    }

    @Test
    fun `extra control target adds one distinct enemy and consumes its single use`() {
        val store = BattleEffectStore()
        store.apply(
            active(
                effectId = 404,
                target = source,
                category = EffectCategory.BENEFICIAL,
            ).also { it.remainingHits = 1 },
        )
        val control = rule(501).copy(
            raw = rule(501).raw.copy(attackType = 41, attackMax = 1),
        )
        val request = BattleRequest(
            BattleTeam(listOf(hero(1, 0), hero(2, 1))),
            BattleTeam(listOf(hero(3, 0), hero(4, 1))),
        )
        val context = SkillBattleContext(
            request = request,
            runtime = SkillRuntimeState(),
            random = FixedBattleRandom(0),
            round = 2,
            source = source,
            rootSkillId = 1,
            currentSkillId = 1,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = SelectedTargetView(request, target),
        )

        val result = registry(listOf(control), store).execute(control, context)

        assertEquals(2, result.stateChanges.filterIsInstance<ApplyBattleEffectChange>().size)
        assertEquals(1, result.stateChanges.filterIsInstance<ConsumeEffectUseChange>().size)
    }

    @Test
    fun `only controls preventing action or active casting cancel target preparation`() {
        val cancellationFamilies = setOf(501, 701, 901, 502, 702, 902)
        val nonCancellationFamilies = setOf(503, 703, 903, 505, 552, 752, 952)

        cancellationFamilies.forEach { effectId ->
            val stateChanges = execute(effectId, BattleEffectStore()).stateChanges
            val cancellation = stateChanges.filterIsInstance<CancelPreparedSkillsChange>().singleOrNull()
                ?: stateChanges.filterIsInstance<ScheduledEffectActivationChange>()
                    .single()
                    .activationChanges()
                    .filterIsInstance<CancelPreparedSkillsChange>()
                    .single()
            val runtime = SkillRuntimeState()
            runtime.prepare(PreparedSkill(source, 98, readyRound = 3))
            runtime.prepare(PreparedSkill(target, 99, readyRound = 3))

            cancellation.apply(runtime)

            assertEquals(listOf(98), runtime.preparedSkills().map { it.skillId }, "effect=$effectId")
            assertEquals(target, cancellation.spec.target, "effect=$effectId")
        }
        nonCancellationFamilies.forEach { effectId ->
            assertTrue(
                execute(effectId, BattleEffectStore()).stateChanges.none {
                    it is CancelPreparedSkillsChange
                },
                "effect=$effectId",
            )
        }
    }

    @Test
    fun `delayed 901 and 902 cancel preparation exactly once at activation`() {
        setOf(901, 902).forEach { effectId ->
            val realExecution = execute(effectId, BattleEffectStore())
            assertEquals(
                emptyList(),
                realExecution.stateChanges.filterIsInstance<CancelPreparedSkillsChange>(),
                "real effect=$effectId must not cancel while cast",
            )
            val realActivation = realExecution.stateChanges
                .filterIsInstance<ScheduledEffectActivationChange>()
                .single()
                .activationChanges()
            assertEquals(
                1,
                realActivation.filterIsInstance<CancelPreparedSkillsChange>().size,
                "real effect=$effectId",
            )

            val syntheticRule = rule(effectId, delayRound = 2)
            val syntheticExecution = registry(listOf(syntheticRule), BattleEffectStore())
                .execute(syntheticRule, context(target))
            assertEquals(
                emptyList(),
                syntheticExecution.stateChanges.filterIsInstance<CancelPreparedSkillsChange>(),
                "synthetic effect=$effectId must not cancel while cast",
            )
            assertEquals(
                1,
                syntheticExecution.stateChanges
                    .filterIsInstance<ScheduledEffectActivationChange>()
                    .single()
                    .activationChanges()
                    .filterIsInstance<CancelPreparedSkillsChange>()
                    .size,
                "synthetic effect=$effectId",
            )
        }
    }

    @Test
    fun `901 and 902 without delay cancel preparation immediately exactly once`() {
        setOf(901, 902).forEach { effectId ->
            val immediateRule = rule(effectId, delayRound = 0)
            val changes = registry(listOf(immediateRule), BattleEffectStore())
                .execute(immediateRule, context(target))
                .stateChanges

            assertEquals(1, changes.filterIsInstance<CancelPreparedSkillsChange>().size, "effect=$effectId")
            assertTrue(changes.none { it is ScheduledEffectActivationChange }, "effect=$effectId")
        }
    }

    @Test
    fun `delayed 7xx effects schedule activation and emit status only when activated`() {
        preparedIds.forEach { effectId ->
            val execution = execute(effectId, BattleEffectStore())
            val scheduled = execution.stateChanges.filterIsInstance<ScheduledEffectActivationChange>().single()
            assertEquals(effectId, scheduled.spec.effectId)
            assertEquals(EffectStartBoundary.AFTER_DELAY, scheduled.spec.startBoundary)
            assertTrue(execution.events.none { it is BattleEvent.StatusApplied }, "effect=$effectId")
            assertTrue(scheduled.activationChanges().isNotEmpty(), "effect=$effectId")
            statusForTest(effectId)?.let { expected ->
                assertEquals(
                    expected,
                    assertIs<BattleEvent.StatusApplied>(scheduled.activationEvent(3)).status,
                )
            }
        }
        listOf(501, 502, 511, 514, 515, 544, 552).forEach { effectId ->
            assertTrue(execute(effectId, BattleEffectStore()).events.any { it is BattleEvent.StatusApplied })
        }
    }

    @Test
    fun `action permission aggregates controls and action modifiers without leaking targets`() {
        val store = BattleEffectStore()
        store.apply(active(502, target = source))
        store.apply(active(752, target = source))
        store.apply(active(544, target = source, category = EffectCategory.BENEFICIAL))
        store.apply(active(551, target = source, category = EffectCategory.BENEFICIAL))
        store.apply(active(545, target = source, category = EffectCategory.BENEFICIAL))
        store.apply(active(761, target = source, category = EffectCategory.BENEFICIAL))

        assertEquals(
            ActionPermission(
                canAct = true,
                canCastActive = false,
                canNormalAttack = false,
                normalAttackCount = 0,
                grantsPursuitOpportunityPerNormal = false,
                counterattack = true,
                secondaryAttack = true,
                firstAction = true,
            ),
            ActionPermissionResolver(store).permissionFor(source),
        )
        assertEquals(ActionPermission(), ActionPermissionResolver(store).permissionFor(target))
    }

    @Test
    fun `prepared disarm rerolls once per round and remains stable within the round`() {
        val store = BattleEffectStore()
        store.apply(active(752, target = source, strength = 90))
        val resolver = ActionPermissionResolver(store)
        val failedRoll = CountingBattleRandom(99)
        val runtime = SkillRuntimeState()
        val roundOne = context(target, failedRoll).copy(
            round = 1,
            runtime = runtime,
        )

        assertTrue(resolver.permissionFor(source, roundOne).canNormalAttack)
        assertTrue(resolver.permissionFor(source, roundOne).canNormalAttack)
        assertEquals(1, failedRoll.calls)

        val successfulRoll = CountingBattleRandom(0)
        val roundTwo = roundOne.copy(round = 2, random = successfulRoll)
        assertFalse(resolver.permissionFor(source, roundTwo).canNormalAttack)
        assertFalse(resolver.permissionFor(source, roundTwo).canNormalAttack)
        assertEquals(1, successfulRoll.calls)
    }

    @Test
    fun `confusion dominates other permissions`() {
        val store = BattleEffectStore()
        store.apply(active(501, target = source))
        store.apply(active(544, target = source, category = EffectCategory.BENEFICIAL))

        assertEquals(
            ActionPermission(
                canAct = false,
                canCastActive = false,
                canNormalAttack = false,
                normalAttackCount = 0,
                grantsPursuitOpportunityPerNormal = false,
            ),
            ActionPermissionResolver(store).permissionFor(source),
        )
    }

    @Test
    fun `berserk resolves allegiance once with injected random and stable candidates`() {
        val store = BattleEffectStore()
        store.apply(active(503, target = source))
        val engine = ActionPermissionResolver(store)

        val allied = engine.permissionFor(source, context(target, FixedBattleRandom(0)))
        assertEquals(Side.ATTACKER, allied.resolvedAllegiance)
        assertEquals(listOf(source, ally), allied.resolvedTargetPool)

        val enemy = engine.permissionFor(source, context(target, FixedBattleRandom(1)))
        assertEquals(Side.DEFENDER, enemy.resolvedAllegiance)
        assertEquals(listOf(target), enemy.resolvedTargetPool)

        val counting = CountingBattleRandom(1)
        engine.permissionFor(source, context(target, counting))
        assertEquals(1, counting.calls)
    }

    @Test
    fun `taunt and guard redirect using effect origin identities`() {
        val store = BattleEffectStore()
        store.apply(active(505, source = ally, target = target))
        assertEquals(ally, ActionPermissionResolver(store).permissionFor(target).redirectTarget)

        val guardStore = BattleEffectStore()
        guardStore.apply(active(504, source = source, target = ally, category = EffectCategory.BENEFICIAL))
        assertEquals(
            source,
            ActionPermissionResolver(guardStore).permissionFor(target, intendedTarget = ally).redirectTarget,
        )
        assertNull(ActionPermissionResolver(guardStore).permissionFor(target, intendedTarget = target).redirectTarget)
    }

    @Test
    fun `calm removes harmful and insight removal removes beneficial without crossing bound categories`() {
        val store = BattleEffectStore()
        store.apply(active(501, target = target, bindFlag = 9))
        store.apply(active(552, target = target, bindFlag = 9))
        store.apply(active(514, target = target, category = EffectCategory.BENEFICIAL, bindFlag = 9))
        store.apply(active(501, target = ally))

        val execution = execute(513, store)
        val cleanse = assertIs<CleanseEffectsChange>(execution.stateChanges.single())
        assertEquals(513, cleanse.spec.effectId)
        assertEquals(EffectStartBoundary.IMMEDIATE, cleanse.spec.startBoundary)
        val removed = cleanse.apply(store)
        assertEquals(setOf(501, 552), removed.removed.mapTo(mutableSetOf()) { it.effectId })
        assertEquals(listOf(514), store.effectsFor(target).map { it.effectId })
        assertEquals(listOf(501), store.effectsFor(ally).map { it.effectId })

        val dispelStore = BattleEffectStore()
        dispelStore.apply(active(501, target = target, bindFlag = 11))
        dispelStore.apply(active(514, target = target, category = EffectCategory.BENEFICIAL, bindFlag = 11))
        val dispel = assertIs<CleanseEffectsChange>(execute(512, dispelStore).stateChanges.single())
        assertEquals(EffectCategory.BENEFICIAL, dispel.category)
        assertEquals(listOf(514), dispel.apply(dispelStore).removed.map { it.effectId })
        assertEquals(listOf(501), dispelStore.effectsFor(target).map { it.effectId })
    }

    @Test
    fun `effect 506 redirects damage for protected targets to the designated base bearer`() {
        val selectedRule = rule(506).copy(
            raw = rule(506).raw.copy(attackType = 21, attackMax = 2),
        )
        val execution = BattleEffectRegistry.strict(graph(controlIds.map(::rule)))
            .registerControlEffects(BattleEffectStore())
            .execute(selectedRule, context(target, sourceRef = ally))

        val redirection = assertIs<DamageRedirectionEffectChange>(execution.stateChanges.single())
        assertEquals(ally, redirection.spec.source)
        assertEquals(source, redirection.damageBearer)
        assertEquals(listOf(source, ally), redirection.protectedTargets)
        val store = BattleEffectStore()
        store.apply(active(506, source = ally, target = source, category = EffectCategory.BENEFICIAL))
        assertNull(ActionPermissionResolver(store).permissionFor(target, intendedTarget = source).redirectTarget)

        assertIs<ActionEffectChange>(execute(504, BattleEffectStore()).stateChanges.single())
    }

    @Test
    fun `double attack is exactly two normals and each normal opens pursuit`() {
        val store = BattleEffectStore()
        store.apply(active(544, target = source, category = EffectCategory.BENEFICIAL))
        val permission = ActionPermissionResolver(store).permissionFor(source)

        assertEquals(2, permission.normalAttackCount)
        assertTrue(permission.grantsPursuitOpportunityPerNormal)
    }

    @Test
    fun `multiple double attack effects still grant exactly two normals`() {
        val store = BattleEffectStore()
        store.apply(active(544, target = source, category = EffectCategory.BENEFICIAL))
        store.apply(active(744, target = source, category = EffectCategory.BENEFICIAL))

        val permission = ActionPermissionResolver(store).permissionFor(source)

        assertEquals(2, permission.normalAttackCount)
        assertTrue(permission.grantsPursuitOpportunityPerNormal)
    }

    @Test
    fun `evade ignore evade and typed action intents remain distinct`() {
        val store = BattleEffectStore()
        store.apply(active(514, target = target, category = EffectCategory.BENEFICIAL))
        assertTrue(ActionPermissionResolver(store).canEvade(target))
        store.apply(active(515, target = source, category = EffectCategory.BENEFICIAL))
        assertFalse(ActionPermissionResolver(store).canEvade(target, attacker = source))

        val expectedKinds = mapOf(
            542 to ActionEffectKind.STRATEGY_LIFE_STEAL,
            545 to ActionEffectKind.SECONDARY_ATTACK,
            546 to ActionEffectKind.EXECUTION_ATTACK,
            551 to ActionEffectKind.COUNTERATTACK,
            571 to ActionEffectKind.IGNORE_TROOP_COUNTER,
            581 to ActionEffectKind.REDUCE_INHERENT_PREPARATION,
            771 to ActionEffectKind.IGNORE_TROOP_COUNTER,
            871 to ActionEffectKind.IGNORE_TROOP_COUNTER,
        )
        expectedKinds.forEach { (effectId, kind) ->
            val execution = execute(effectId, BattleEffectStore())
            val intent = when (val change = execution.stateChanges.single()) {
                is ActionEffectChange -> change
                is ScheduledEffectActivationChange ->
                    assertIs<ActionEffectChange>(change.activationChanges().single())
                else -> error("Unexpected effect=$effectId change=$change")
            }
            assertEquals(kind, intent.kind)
        }
    }

    @Test
    fun `configured action and immunity aliases retain distinct runtime behavior`() {
        val firstAction = execute(561, BattleEffectStore())
        assertEquals(
            ActionEffectKind.FIRST_ACTION,
            assertIs<ActionEffectChange>(firstAction.stateChanges.single()).kind,
        )
        assertEquals(
            BattleStatus.FIRST_ACTION,
            assertIs<BattleEvent.StatusApplied>(firstAction.events.single()).status,
        )
        val firstActionStore = BattleEffectStore()
        firstActionStore.apply(active(561, target = source, category = EffectCategory.BENEFICIAL))
        assertTrue(ActionPermissionResolver(firstActionStore).permissionFor(source).firstAction)

        val insight = execute(811, BattleEffectStore())
        assertIs<ApplyBattleEffectChange>(insight.stateChanges.single())
        assertEquals(
            BattleStatus.INSIGHT,
            assertIs<BattleEvent.StatusApplied>(insight.events.single()).status,
        )
        val insightStore = BattleEffectStore()
        insightStore.apply(active(811, target = target, category = EffectCategory.BENEFICIAL))
        assertEquals(
            811,
            assertIs<EffectBlockedChange>(
                execute(501, insightStore).stateChanges.single(),
            ).blockingEffectId,
        )

        val evade = execute(814, BattleEffectStore())
        assertIs<ApplyBattleEffectChange>(evade.stateChanges.single())
        assertEquals(
            BattleStatus.EVADE,
            assertIs<BattleEvent.StatusApplied>(evade.events.single()).status,
        )
        val evadeStore = BattleEffectStore()
        evadeStore.apply(active(814, target = target, category = EffectCategory.BENEFICIAL))
        assertTrue(ActionPermissionResolver(evadeStore).canEvade(target))

        val confusionImmunity = BattleEffectStore()
        confusionImmunity.apply(active(791, target = target, category = EffectCategory.BENEFICIAL))
        assertEquals(
            791,
            assertIs<EffectBlockedChange>(
                execute(501, confusionImmunity).stateChanges.single(),
            ).blockingEffectId,
        )
        assertTrue(
            execute(503, confusionImmunity).stateChanges.none { it is EffectBlockedChange },
        )

        val berserkImmunity = BattleEffectStore()
        berserkImmunity.apply(active(793, target = target, category = EffectCategory.BENEFICIAL))
        assertEquals(
            793,
            assertIs<EffectBlockedChange>(
                execute(503, berserkImmunity).stateChanges.single(),
            ).blockingEffectId,
        )
        assertTrue(
            execute(501, berserkImmunity).stateChanges.none { it is EffectBlockedChange },
        )
    }

    private fun execute(
        effectId: Int,
        store: BattleEffectStore,
        selectedTarget: BattleHeroRef = target,
        rule: SkillEffectRule = rule(effectId),
        random: BattleRandom = FixedBattleRandom(0),
    ): EffectExecution {
        return registry(listOf(rule), store).execute(
            rule,
            context(selectedTarget, random = random),
        )
    }

    private fun registry(
        @Suppress("UNUSED_PARAMETER") rules: List<SkillEffectRule>,
        store: BattleEffectStore = BattleEffectStore(),
    ): BattleEffectRegistry =
        BattleEffectRegistry.strict(graph(rules)).registerControlEffects(store)

    private fun graph(rules: List<SkillEffectRule>) = SkillRuleGraph(
        rules = mapOf(
            1 to SkillRule(1, SkillKind.ACTIVE, 3, 100, 0, 5, rules),
        ),
        effectIds = rules.mapTo(mutableSetOf()) { it.effectId },
    )

    private fun rule(
        effectId: Int,
        delayRound: Int = realDelayRound(effectId),
    ): SkillEffectRule {
        val beneficial = effectId in setOf(
            504, 506, 511, 513, 514, 515, 542, 544, 545, 546, 551, 571, 581, 594,
            561, 711, 713, 714, 744, 761, 771, 791, 793, 811, 814, 871,
        )
        return SkillEffectRule(
            detailId = 10_000 + effectId,
            effectId = effectId,
            childSkillIds = emptySet(),
            raw = SkillDetailConfig(
                detailId = 10_000 + effectId,
                effectId = effectId,
                attackType = 41,
                targetType = 0,
                selectType = 0,
                intelParam = 0,
                constantParam = 100,
                probabilityInit = 100,
                probabilityMax = 100,
                attackMax = 1,
                availableRounds = 2,
                delayRound = delayRound,
                buffType = if (beneficial) 2 else 1,
                effectName = "ignored",
            ),
            configuredValue = ConfiguredBattleEffectValue(
                BattleEffectValueUnit.RATE, 1, 100, 0, 0, 0, 0,
            ),
            effectBuffType = if (beneficial) 2 else 1,
            effectReplaceType = 3,
            skillKind = SkillKind.ACTIVE,
            rawSkillType = 3,
        )
    }

    private fun context(
        selectedTarget: BattleHeroRef,
        random: BattleRandom = FixedBattleRandom(0),
        sourceRef: BattleHeroRef = source,
    ): SkillBattleContext {
        val request = BattleRequest(
            BattleTeam(listOf(hero(1, 0), hero(2, 1))),
            BattleTeam(listOf(hero(3, 0))),
        )
        return SkillBattleContext(
            request = request,
            runtime = SkillRuntimeState(),
            random = random,
            round = 2,
            source = sourceRef,
            rootSkillId = 1,
            currentSkillId = 1,
            trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            battleView = SelectedTargetView(request, selectedTarget),
        )
    }

    private fun active(
        effectId: Int,
        source: BattleHeroRef = this.source,
        target: BattleHeroRef,
        category: EffectCategory = EffectCategory.HARMFUL,
        bindFlag: Int = 0,
        strength: Int = 100,
    ) = ActiveSkillEffect(
        source, target, 1, 1, SkillKind.ACTIVE, 3, 10_000 + effectId, effectId,
        category, 0, strength, 3, bindFlag, 1, 1, 2, null, false,
    )

    private fun hero(id: Int, position: Int) = BattleHero(
        BattleHeroId(id), position, BattleStats(100, 100, 100, 100, 20, 5), 1_000,
    )

    private fun ref(side: Side, position: Int, id: Int) =
        BattleHeroRef(side, position, BattleHeroId(id))

    private class SelectedTargetView(
        request: BattleRequest,
        private val selected: BattleHeroRef,
    ) : SkillBattleView by SkillBattleView.entrySnapshot(request) {
        private val roster = buildList {
            request.attacker.heroes.forEach {
                add(BattleHeroRef(Side.ATTACKER, it.position, it.id))
            }
            request.defender.heroes.forEach {
                add(BattleHeroRef(Side.DEFENDER, it.position, it.id))
            }
        }

        override fun heroes(): List<BattleHeroRef> = roster
    }

    private class CountingBattleRandom(private val value: Int) : BattleRandom {
        var calls: Int = 0

        override fun nextInt(bound: Int): Int {
            calls += 1
            return value.coerceIn(0, bound - 1)
        }
    }

    private companion object {
        val controlIds = (
            (501..506) + (511..515) + listOf(542) + (544..546) +
                listOf(551, 552, 561, 571, 581, 594) + (701..703) + (711..714) +
                listOf(744, 752, 761, 771, 791, 793, 811, 814, 871) +
                (901..903) + listOf(952)
            ).toSet()
        val preparedIds = setOf(701, 702, 703, 711, 712, 713, 714, 744, 752, 761, 771)

        fun realDelayRound(effectId: Int): Int =
            if (effectId in preparedIds || effectId in setOf(901, 902)) 1 else 0

        fun statusForTest(effectId: Int): BattleStatus? = when (effectId) {
            701 -> BattleStatus.CONFUSION
            702 -> BattleStatus.HESITATION
            711 -> BattleStatus.INSIGHT
            714 -> BattleStatus.EVADE
            744 -> BattleStatus.DOUBLE_ATTACK
            752 -> BattleStatus.DISARM
            761 -> BattleStatus.FIRST_ACTION
            else -> null
        }
    }
}
