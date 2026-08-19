package com.stzb.server.game.battle

import com.stzb.server.game.battle.skill.BattleTrigger
import com.stzb.server.game.battle.skill.BattleTargetDecisionSource
import com.stzb.server.game.battle.skill.DefaultCompleteSkillEngine
import com.stzb.server.game.battle.skill.SkillBattleContext

object BattleEngine {
    private val actionResolver = BattleActionResolver()

    fun resolve(request: BattleRequest): BattleResult =
        resolveInternal(request, skillRuntime = null, runtimeState = null, random = null)

    fun resolve(
        request: BattleRequest,
        config: BattleConfigRepository,
        random: BattleRandom = SeededBattleRandom(0),
        targetDecisions: BattleTargetDecisionSource = BattleTargetDecisionSource.NONE,
    ): BattleResult = resolveComplete(request, config, random, targetDecisions)

    private fun resolveComplete(
        request: BattleRequest,
        config: BattleConfigRepository,
        random: BattleRandom,
        targetDecisions: BattleTargetDecisionSource,
    ): BattleResult {
        val engine = DefaultCompleteSkillEngine.create(request, config)
        val events = mutableListOf<BattleEvent>(BattleEvent.BattleStart)
        val first = engine.livingHeroesInSpeedOrder().firstOrNull()
            ?: return BattleResult(BattleOutcome.DRAW, request.attacker, request.defender, events)
        fun context(
            round: Int,
            source: BattleHeroRef,
            trigger: BattleTrigger,
        ) = SkillBattleContext(
            request = request,
            runtime = engine.state.runtime,
            random = random,
            round = round,
            source = source,
            rootSkillId = 0,
            currentSkillId = 0,
            trigger = trigger,
            battleView = engine.state.view,
            targetDecisions = targetDecisions,
        )
        fun result(outcome: BattleOutcome): BattleResult =
            BattleResult(
                outcome,
                BattleTeam(
                    request.attacker.heroes.map { original ->
                        engine.liveHero(BattleHeroRef(Side.ATTACKER, original.position, original.id))
                    },
                    request.attacker.armyBonuses,
                ),
                BattleTeam(
                    request.defender.heroes.map { original ->
                        engine.liveHero(BattleHeroRef(Side.DEFENDER, original.position, original.id))
                    },
                    request.defender.armyBonuses,
                ),
                events,
                request.attacker,
                request.defender,
            )
        fun outcome(): BattleOutcome {
            val attackerBase = engine.state.view.heroes().filter { it.side == Side.ATTACKER }.minByOrNull { it.position }
            val defenderBase = engine.state.view.heroes().filter { it.side == Side.DEFENDER }.minByOrNull { it.position }
            val attackerAlive = attackerBase?.let { engine.state.view.state(it)?.troops ?: 0 > 0 } ?: false
            val defenderAlive = defenderBase?.let { engine.state.view.state(it)?.troops ?: 0 > 0 } ?: false
            return when {
                attackerAlive && !defenderAlive -> BattleOutcome.ATTACKER_WIN
                !attackerAlive && defenderAlive -> BattleOutcome.DEFENDER_WIN
                else -> BattleOutcome.DRAW
            }
        }
        fun finishIfDefeated(round: Int, source: BattleHeroRef): BattleResult? {
            val resolved = outcome()
            if (resolved == BattleOutcome.DRAW) return null
            events += engine.trigger(
                BattleTrigger.BASE_HERO_DEFEATED,
                context(round, source, BattleTrigger.BASE_HERO_DEFEATED),
            )
            events += BattleEvent.BattleEnd(resolved)
            return result(resolved)
        }

        events += engine.prepareBattle(context(0, first, BattleTrigger.BATTLE_PASSIVE))
        finishIfDefeated(0, first)?.let { return it }

        for (round in 1..request.maxRounds) {
            events += BattleEvent.RoundStart(round)
            val roundSources = engine.livingHeroesInSpeedOrder()
            roundSources.forEach { source ->
                events += engine.trigger(
                    BattleTrigger.ROUND_START,
                    context(round, source, BattleTrigger.ROUND_START),
                )
            }
            finishIfDefeated(round, first)?.let { return it }

            for (actor in engine.livingHeroesInSpeedOrder()) {
                if ((engine.state.view.state(actor)?.troops ?: 0) <= 0) continue
                events += BattleEvent.HeroActionStart(round, actor)
                val actorContext = context(round, actor, BattleTrigger.ACTION_BEFORE)
                events += engine.trigger(BattleTrigger.ACTION_BEFORE, actorContext)
                finishIfDefeated(round, actor)?.let { return it }
                if ((engine.state.view.state(actor)?.troops ?: 0) <= 0) {
                    events += BattleEvent.HeroActionEnd(round, actor)
                    continue
                }
                var permission = engine.permissionFor(actor, actorContext)
                if (permission.canAct) {
                    if (permission.canCastActive) {
                        events += engine.trigger(
                            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                            actorContext.copy(trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT),
                        )
                        finishIfDefeated(round, actor)?.let { return it }
                    }
                    permission = engine.permissionFor(actor, actorContext)
                    if (permission.canNormalAttack) {
                        var remainingNormalAttacks = permission.normalAttackCount
                        while (remainingNormalAttacks > 0) {
                            remainingNormalAttacks -= 1
                            val currentActor = engine.liveHero(actor)
                            val targetPool = permission.resolvedTargetPool.ifEmpty {
                                engine.state.view.heroes().filter { ref ->
                                    ref.side == (permission.resolvedAllegiance ?: actor.side).opposite()
                                }
                            }
                            val candidates = targetPool.map(engine::liveHero)
                            val allies = engine.state.view.heroes()
                                .filter { it.side == actor.side }
                                .map(engine::liveHero)
                            var selected = actionResolver.selectNormalAttackTarget(
                                currentActor,
                                candidates,
                                random,
                                allies,
                            )
                                ?: break
                            var target = BattleHeroRef(
                                targetPool.first().side,
                                selected.position,
                                selected.id,
                            )
                            target = engine.redirectNormalAttackTarget(
                                actor,
                                target,
                                random,
                            )
                            engine.recordTarget(actor, target)
                            events += engine.trigger(
                                BattleTrigger.NORMAL_ATTACK_BEFORE,
                                actorContext.copy(trigger = BattleTrigger.NORMAL_ATTACK_BEFORE),
                            )
                            val evaded = engine.tryEvade(
                                round,
                                actor,
                                target,
                                actorContext,
                            )
                            if (evaded != null) {
                                events += evaded
                            } else {
                                events += engine.resolveNormalAttack(
                                    round,
                                    actor,
                                    target,
                                    random,
                                    actorContext,
                                )
                                if (!engine.baseDefeated() && permission.secondaryAttack) {
                                    for (secondary in engine.splitAttackTargets(actor, target)) {
                                        if (engine.baseDefeated()) break
                                        events += engine.reactiveAttack(
                                            round,
                                            actor,
                                            secondary,
                                            545,
                                            actorContext,
                                        )
                                    }
                                }
                                if (!engine.baseDefeated()) {
                                    val targetContext = context(
                                        round,
                                        target,
                                        BattleTrigger.DAMAGE_AFTER,
                                    )
                                    if (
                                        BattleModifier.CounterattackImmunity !in
                                        engine.liveHero(actor).modifiers &&
                                        engine.permissionFor(target, targetContext).counterattack
                                    ) {
                                        events += engine.reactiveAttack(
                                            round,
                                            target,
                                            actor,
                                            551,
                                            targetContext,
                                        )
                                    }
                                }
                            }
                            finishIfDefeated(round, actor)?.let { return it }
                            engine.state.runtime.recordBattleTriggerOccurrence(
                                actor,
                                BattleTrigger.NORMAL_ATTACK_AFTER,
                            )
                            events += engine.trigger(
                                BattleTrigger.NORMAL_ATTACK_AFTER,
                                actorContext.copy(trigger = BattleTrigger.NORMAL_ATTACK_AFTER),
                            )
                            remainingNormalAttacks += engine.consumePendingExtraNormalAttacks(actor)
                            finishIfDefeated(round, actor)?.let { return it }
                            if (permission.grantsPursuitOpportunityPerNormal) {
                                events += engine.trigger(
                                    BattleTrigger.PURSUIT_ATTEMPT,
                                    actorContext.copy(trigger = BattleTrigger.PURSUIT_ATTEMPT),
                                )
                                finishIfDefeated(round, actor)?.let { return it }
                            }
                        }
                    }
                }
                events += engine.trigger(
                    BattleTrigger.ACTION_AFTER,
                    actorContext.copy(trigger = BattleTrigger.ACTION_AFTER),
                )
                events += BattleEvent.HeroActionEnd(round, actor)
                finishIfDefeated(round, actor)?.let { return it }
            }
            engine.livingHeroesInSpeedOrder().forEach { source ->
                events += engine.trigger(
                    BattleTrigger.ROUND_END,
                    context(round, source, BattleTrigger.ROUND_END),
                )
            }
            events += engine.finishRound(round)
            events += BattleEvent.RoundEnd(round)
        }
        val resolved = outcome()
        events += BattleEvent.BattleEnd(resolved)
        return result(resolved)
    }

    private fun resolveInternal(
        request: BattleRequest,
        skillRuntime: BattleSkillRuntime?,
        runtimeState: SkillRuntimeState?,
        random: BattleRandom?,
    ): BattleResult {
        var attacker = request.attacker.heroes.associateBy { it.position }.toMutableMap()
        var defender = request.defender.heroes.associateBy { it.position }.toMutableMap()
        val statuses = mutableMapOf<BattleHeroRef, MutableList<ActiveBattleStatus>>()
        val events = mutableListOf<BattleEvent>(BattleEvent.BattleStart)
        var outcome = BattleOutcome.DRAW

        seedInitialActiveStatuses(attacker, defender, statuses)
        executePreparationSkills(
            attacker,
            defender,
            statuses,
            events,
            skillRuntime,
            runtimeState,
            random,
        )

        for (round in 1..request.maxRounds) {
            events.add(BattleEvent.RoundStart(round))
            applyOngoingStatuses(round, attacker, defender, statuses, events)
            outcome = currentOutcome(attacker, defender)
            if (outcome != BattleOutcome.DRAW) {
                events.add(BattleEvent.BattleEnd(outcome))
                return result(outcome, attacker, defender, events)
            }

            val turnOrder = buildTurnOrder(attacker, defender, statuses)
            for (actorRef in turnOrder) {
                val actor = currentHero(actorRef.side, actorRef.position, attacker, defender) ?: continue
                if (actor.troops <= 0) continue
                val actorStatuses = statuses[actorRef].orEmpty()

                events.add(BattleEvent.HeroActionStart(round, actorRef))
                if (!actorStatuses.has(BattleStatus.CONFUSION)) {
                    val effectiveActor = actor.withEffectiveStats(actorStatuses)
                    if (!actorStatuses.has(BattleStatus.HESITATION)) {
                        for (attempt in effectiveActor.skillIds.indices) {
                            val skillActor = currentHero(actorRef.side, actorRef.position, attacker, defender)
                                ?.withEffectiveStats(statuses[actorRef].orEmpty())
                                ?: break
                            val skillCast = tryCastSkill(
                                round, actorRef, skillActor, attacker, defender, statuses,
                                skillRuntime, runtimeState, random, setOf(SkillKind.ACTIVE),
                            ) ?: break
                            applySkillCastResult(actorRef, skillCast, attacker, defender, statuses, events, round)
                        }
                    } else {
                        runtimeState?.interruptPreparations(actorRef)
                    }
                    if (!actorStatuses.has(BattleStatus.DISARM)) {
                        performNormalAttackAndPursuit(
                            round, actorRef, attacker, defender, statuses,
                            skillRuntime, runtimeState, random, events,
                        )
                    }
                } else {
                    runtimeState?.interruptPreparations(actorRef)
                }
                events.add(BattleEvent.HeroActionEnd(round, actorRef))

                outcome = currentOutcome(attacker, defender)
                if (outcome != BattleOutcome.DRAW) {
                    events.add(BattleEvent.BattleEnd(outcome))
                    return result(outcome, attacker, defender, events)
                }
            }

            events.add(BattleEvent.RoundEnd(round))
            expireStatuses(statuses)
        }

        outcome = currentOutcome(attacker, defender)
        events.add(BattleEvent.BattleEnd(outcome))
        return result(outcome, attacker, defender, events)
    }

    private fun executePreparationSkills(
        attacker: MutableMap<Int, BattleHero>,
        defender: MutableMap<Int, BattleHero>,
        statuses: MutableMap<BattleHeroRef, MutableList<ActiveBattleStatus>>,
        events: MutableList<BattleEvent>,
        skillRuntime: BattleSkillRuntime?,
        runtimeState: SkillRuntimeState?,
        random: BattleRandom?,
    ) {
        val order = buildTurnOrder(attacker, defender, statuses)
        for (actorRef in order) {
            val skillCount = currentHero(actorRef.side, actorRef.position, attacker, defender)
                ?.skillIds
                ?.size
                ?: continue
            repeat(skillCount) {
                val actor = currentHero(actorRef.side, actorRef.position, attacker, defender) ?: return@repeat
                val cast = tryCastSkill(
                    round = 0,
                    actorRef = actorRef,
                    actor = actor,
                    attacker = attacker,
                    defender = defender,
                    statuses = statuses,
                    skillRuntime = skillRuntime,
                    runtimeState = runtimeState,
                    random = random,
                    allowedKinds = setOf(SkillKind.PASSIVE, SkillKind.COMMAND),
                ) ?: return@repeat
                applySkillCastResult(actorRef, cast, attacker, defender, statuses, events, round = 0)
            }
        }
    }

    private fun performNormalAttackAndPursuit(
        round: Int,
        actorRef: BattleHeroRef,
        attacker: MutableMap<Int, BattleHero>,
        defender: MutableMap<Int, BattleHero>,
        statuses: MutableMap<BattleHeroRef, MutableList<ActiveBattleStatus>>,
        skillRuntime: BattleSkillRuntime?,
        runtimeState: SkillRuntimeState?,
        random: BattleRandom?,
        events: MutableList<BattleEvent>,
    ) {
        val attackCount = if (statuses[actorRef].orEmpty().has(BattleStatus.DOUBLE_ATTACK)) 2 else 1
        repeat(attackCount) {
            val actor = currentHero(actorRef.side, actorRef.position, attacker, defender) ?: return
            val effectiveActor = actor.withEffectiveStats(statuses[actorRef].orEmpty())
            val enemies = if (actorRef.side == Side.ATTACKER) defender else attacker
            val resolved = actionResolver.resolveNormalAttack(
                round = round,
                sourceRef = actorRef,
                source = effectiveActor,
                enemies = enemies.values.map { target ->
                    target.withEffectiveStats(statuses[target.ref(actorRef.side.opposite())].orEmpty())
                },
                random = random,
            ) ?: return
            val targetRef = resolved.event.target
            val targetStatuses = statuses[targetRef].orEmpty()
            if (tryEvade(round, actorRef, targetRef, targetStatuses, statuses, events)) return@repeat

            val originalTarget = currentHero(targetRef.side, targetRef.position, attacker, defender) ?: return
            val updatedTarget = originalTarget.copy(troops = resolved.target.troops)
            if (targetRef.side == Side.ATTACKER) {
                attacker[targetRef.position] = updatedTarget
            } else {
                defender[targetRef.position] = updatedTarget
            }
            events += resolved.event

            val pursuitActor = currentHero(actorRef.side, actorRef.position, attacker, defender) ?: actor
            val pursuit = tryCastSkill(
                round, actorRef, pursuitActor.withEffectiveStats(statuses[actorRef].orEmpty()),
                attacker, defender, statuses, skillRuntime, runtimeState, random, setOf(SkillKind.PURSUIT),
                allowRepeatedAttempt = attackCount > 1,
            )
            if (pursuit != null) {
                applySkillCastResult(actorRef, pursuit, attacker, defender, statuses, events, round)
            }
        }
    }

    private fun seedInitialActiveStatuses(
        attacker: Map<Int, BattleHero>,
        defender: Map<Int, BattleHero>,
        statuses: MutableMap<BattleHeroRef, MutableList<ActiveBattleStatus>>,
    ) {
        attacker.values.forEach { hero ->
            val ref = hero.ref(Side.ATTACKER)
            hero.activeStatuses.forEach { s ->
                statuses.getOrPut(ref) { mutableListOf() } += ActiveBattleStatus(
                    status = s,
                    remainingRounds = 99,
                    source = ref,
                )
            }
        }
        defender.values.forEach { hero ->
            val ref = hero.ref(Side.DEFENDER)
            hero.activeStatuses.forEach { s ->
                statuses.getOrPut(ref) { mutableListOf() } += ActiveBattleStatus(
                    status = s,
                    remainingRounds = 99,
                    source = ref,
                )
            }
        }
    }

    private fun applySkillCastResult(
        actorRef: BattleHeroRef,
        skillCast: SkillCastResult,
        attacker: MutableMap<Int, BattleHero>,
        defender: MutableMap<Int, BattleHero>,
        statuses: MutableMap<BattleHeroRef, MutableList<ActiveBattleStatus>>,
        events: MutableList<BattleEvent>,
        round: Int,
    ) {
        if (actorRef.side == Side.ATTACKER) {
            skillCast.updatedAllies?.heroes?.forEach { h -> attacker[h.position] = h }
            skillCast.updatedEnemies.heroes.forEach { h -> defender[h.position] = h }
        } else {
            skillCast.updatedAllies?.heroes?.forEach { h -> defender[h.position] = h }
            skillCast.updatedEnemies.heroes.forEach { h -> attacker[h.position] = h }
        }
        val actor = currentHero(actorRef.side, actorRef.position, attacker, defender)
        skillCast.events.forEach { event ->
            if (event is BattleEvent.StatusApplied && isControlStatus(event.status) && hasInsight(event.target, statuses)) {
                return@forEach
            }
            events += event
            if (event is BattleEvent.StatusApplied) {
                statuses.getOrPut(event.target) { mutableListOf() } += ActiveBattleStatus(
                    status = event.status,
                    remainingRounds = event.durationRounds + 1,
                    source = event.source,
                    power = if (event.power != 0) event.power else actor?.stats?.strategy?.coerceAtLeast(1) ?: 1,
                    statDelta = event.statDelta,
                    skillId = event.skillId,
                    sourceSnapshot = actor,
                )
            }
        }
        if (skillCast.selfStatDelta != BattleStats.ZERO) {
            val self = currentHero(actorRef.side, actorRef.position, attacker, defender)
            if (self != null) {
                val stats: List<Pair<BattleStat, Int>> = listOf(
                    BattleStat.ATTACK to skillCast.selfStatDelta.attack,
                    BattleStat.DEFENSE to skillCast.selfStatDelta.defense,
                    BattleStat.STRATEGY to skillCast.selfStatDelta.strategy,
                    BattleStat.SPEED to skillCast.selfStatDelta.speed,
                )
                val primaryStat: Pair<BattleStat, Int>? = stats.firstOrNull { pair: Pair<BattleStat, Int> -> pair.second != 0 }
                if (primaryStat != null) {
                    val buffStatus = when (primaryStat.first) {
                        BattleStat.ATTACK -> BattleStatus.ATTACK_BUFF
                        BattleStat.DEFENSE -> BattleStatus.DEFENSE_BUFF
                        BattleStat.STRATEGY -> BattleStatus.STRATEGY_BUFF
                        BattleStat.SPEED -> BattleStatus.SPEED_BUFF
                        else -> null
                    }
                    if (buffStatus != null) {
                        statuses.getOrPut(actorRef) { mutableListOf() } += ActiveBattleStatus(
                            status = buffStatus,
                            remainingRounds = (skillCast.selfBuffDuration ?: 2) + 1,
                            source = actorRef,
                            statDelta = skillCast.selfStatDelta,
                            skillId = skillCast.skillId,
                        )
                        events += BattleEvent.StatChanged(
                            round = round,
                            source = actorRef,
                            target = actorRef,
                            stat = primaryStat.first,
                            delta = primaryStat.second,
                            durationRounds = skillCast.selfBuffDuration ?: 2,
                            skillId = skillCast.skillId,
                            valueAfter = self.stats.value(primaryStat.first) + primaryStat.second,
                        )
                    }
                }
            }
        }
    }

    private fun BattleStats.value(stat: BattleStat): Int = when (stat) {
        BattleStat.ATTACK -> attack
        BattleStat.DEFENSE -> defense
        BattleStat.STRATEGY -> strategy
        BattleStat.SPEED -> speed
        BattleStat.SIEGE -> siege
        BattleStat.HIT_RANGE -> hitRange
    }

    private fun tryEvade(
        round: Int,
        source: BattleHeroRef,
        target: BattleHeroRef,
        targetStatuses: List<ActiveBattleStatus>,
        statuses: MutableMap<BattleHeroRef, MutableList<ActiveBattleStatus>>,
        events: MutableList<BattleEvent>,
    ): Boolean {
        val evade = targetStatuses.firstOrNull { it.status == BattleStatus.EVADE } ?: return false
        events += BattleEvent.Evaded(round = round, source = source, target = target)
        val list = statuses[target] ?: return true
        list.remove(evade)
        if (list.isEmpty()) statuses.remove(target)
        return true
    }

    private fun hasInsight(ref: BattleHeroRef, statuses: Map<BattleHeroRef, List<ActiveBattleStatus>>): Boolean =
        statuses[ref].orEmpty().any { it.status == BattleStatus.INSIGHT }

    private fun isControlStatus(status: BattleStatus): Boolean =
        status in setOf(
            BattleStatus.CONFUSION, BattleStatus.HESITATION, BattleStatus.DISARM,
        )

    private fun tryCastSkill(
        round: Int,
        actorRef: BattleHeroRef,
        actor: BattleHero,
        attacker: Map<Int, BattleHero>,
        defender: Map<Int, BattleHero>,
        statuses: Map<BattleHeroRef, List<ActiveBattleStatus>>,
        skillRuntime: BattleSkillRuntime?,
        runtimeState: SkillRuntimeState?,
        random: BattleRandom?,
        allowedKinds: Set<SkillKind>,
        allowRepeatedAttempt: Boolean = false,
    ): SkillCastResult? {
        if (skillRuntime == null || runtimeState == null || random == null) return null
        val enemies = if (actorRef.side == Side.ATTACKER) defender else attacker
        val allies = if (actorRef.side == Side.ATTACKER) attacker else defender
        val enemiesWithStats = enemies.values.map { it.withEffectiveStats(statuses[it.ref(actorRef.side.opposite())].orEmpty()) }
        val alliesWithStats = allies.values.map { it.withEffectiveStats(statuses[it.ref(actorRef.side)].orEmpty()) }
        return skillRuntime.tryAct(
            round = round,
            sourceRef = actorRef,
            source = actor,
            targets = BattleTeam(enemiesWithStats.sortedBy { it.position }),
            allies = BattleTeam(alliesWithStats.sortedBy { it.position }),
            random = random,
            state = runtimeState,
            allowedKinds = allowedKinds,
            allowRepeatedAttempt = allowRepeatedAttempt,
        )
    }

    private fun buildTurnOrder(
        attacker: Map<Int, BattleHero>,
        defender: Map<Int, BattleHero>,
        statuses: Map<BattleHeroRef, List<ActiveBattleStatus>>,
    ): List<BattleHeroRef> =
        (attacker.map { (_, hero) -> hero.ref(Side.ATTACKER) } +
            defender.map { (_, hero) -> hero.ref(Side.DEFENDER) })
            .filter { ref -> currentHero(ref.side, ref.position, attacker, defender)?.troops ?: 0 > 0 }
            .sortedWith(
                compareByDescending<BattleHeroRef> { ref ->
                    statuses[ref].orEmpty().has(BattleStatus.FIRST_ACTION)
                }.thenByDescending { ref ->
                    val hero = currentHero(ref.side, ref.position, attacker, defender)
                    hero?.withEffectiveStats(statuses[ref].orEmpty())?.stats?.speed ?: 0
                }.thenBy { it.side.ordinal }.thenBy { it.position },
            )

    private fun currentOutcome(
        attacker: Map<Int, BattleHero>,
        defender: Map<Int, BattleHero>,
    ): BattleOutcome {
        val attackerAlive = baseHeroAlive(attacker)
        val defenderAlive = baseHeroAlive(defender)
        return when {
            attackerAlive && !defenderAlive -> BattleOutcome.ATTACKER_WIN
            !attackerAlive && defenderAlive -> BattleOutcome.DEFENDER_WIN
            else -> BattleOutcome.DRAW
        }
    }

    private fun baseHeroAlive(team: Map<Int, BattleHero>): Boolean =
        (team[0] ?: team.values.minByOrNull { it.position })
            ?.troops
            ?.let { it > 0 }
            ?: false

    private fun currentHero(
        side: Side,
        position: Int,
        attacker: Map<Int, BattleHero>,
        defender: Map<Int, BattleHero>,
    ): BattleHero? =
        if (side == Side.ATTACKER) attacker[position] else defender[position]

    private fun result(
        outcome: BattleOutcome,
        attacker: Map<Int, BattleHero>,
        defender: Map<Int, BattleHero>,
        events: List<BattleEvent>,
    ): BattleResult =
        BattleResult(
            outcome = outcome,
            attacker = BattleTeam(attacker.values.sortedBy { it.position }),
            defender = BattleTeam(defender.values.sortedBy { it.position }),
            events = events,
        )

    private fun BattleHero.ref(side: Side): BattleHeroRef =
        BattleHeroRef(side = side, position = position, heroId = id)

    private fun List<ActiveBattleStatus>.has(status: BattleStatus): Boolean =
        any { it.status == status }

    private fun BattleHero.withEffectiveStats(runtimeStatuses: List<ActiveBattleStatus>): BattleHero {
        val delta = runtimeStatuses.fold(BattleStats.ZERO) { acc, s -> acc + s.statDelta }
        val runtimeModifiers = runtimeStatuses.mapNotNull { status ->
            when {
                status.status == BattleStatus.ATTACK_BUFF && status.power != 0 ->
                    BattleModifier.DamageDealtPercent(percent = status.power)
                status.status == BattleStatus.ATTACK_DEBUFF && status.power != 0 ->
                    BattleModifier.DamageDealtPercent(percent = status.power)
                status.status == BattleStatus.DEFENSE_DEBUFF && status.power != 0 ->
                    BattleModifier.DamageTakenPercent(percent = status.power)
                status.status == BattleStatus.DEFENSE_BUFF && status.power != 0 ->
                    BattleModifier.DamageTakenPercent(percent = status.power)
                status.status == BattleStatus.PHYSICAL_DAMAGE_DEALT_INCREASED && status.power != 0 ->
                    BattleModifier.DamageDealtPercent(school = DamageSchool.PHYSICAL, percent = status.power)
                status.status == BattleStatus.PHYSICAL_DAMAGE_DEALT_REDUCED && status.power != 0 ->
                    BattleModifier.DamageDealtPercent(school = DamageSchool.PHYSICAL, percent = -status.power)
                status.status == BattleStatus.STRATEGY_DAMAGE_DEALT_INCREASED && status.power != 0 ->
                    BattleModifier.DamageDealtPercent(school = DamageSchool.STRATEGY, percent = status.power)
                status.status == BattleStatus.STRATEGY_DAMAGE_DEALT_REDUCED && status.power != 0 ->
                    BattleModifier.DamageDealtPercent(school = DamageSchool.STRATEGY, percent = -status.power)
                status.status == BattleStatus.PHYSICAL_DAMAGE_TAKEN_INCREASED && status.power != 0 ->
                    BattleModifier.DamageTakenPercent(school = DamageSchool.PHYSICAL, percent = status.power)
                status.status == BattleStatus.PHYSICAL_DAMAGE_TAKEN_REDUCED && status.power != 0 ->
                    BattleModifier.DamageTakenPercent(school = DamageSchool.PHYSICAL, percent = -status.power)
                status.status == BattleStatus.STRATEGY_DAMAGE_TAKEN_INCREASED && status.power != 0 ->
                    BattleModifier.DamageTakenPercent(school = DamageSchool.STRATEGY, percent = status.power)
                status.status == BattleStatus.STRATEGY_DAMAGE_TAKEN_REDUCED && status.power != 0 ->
                    BattleModifier.DamageTakenPercent(school = DamageSchool.STRATEGY, percent = -status.power)
                else -> null
            }
        }
        if (delta == BattleStats.ZERO && runtimeModifiers.isEmpty()) return this
        return copy(
            stats = BattleStats(
                attack = (stats.attack + delta.attack).coerceAtLeast(1),
                defense = (stats.defense + delta.defense).coerceAtLeast(0),
                strategy = (stats.strategy + delta.strategy).coerceAtLeast(1),
                speed = (stats.speed + delta.speed).coerceAtLeast(1),
                siege = stats.siege + delta.siege,
                hitRange = stats.hitRange + delta.hitRange,
            ),
            modifiers = modifiers + runtimeModifiers,
        )
    }

    private fun applyOngoingStatuses(
        round: Int,
        attacker: MutableMap<Int, BattleHero>,
        defender: MutableMap<Int, BattleHero>,
        statuses: Map<BattleHeroRef, List<ActiveBattleStatus>>,
        events: MutableList<BattleEvent>,
    ) {
        statuses.forEach { (targetRef, activeStatuses) ->
            if ((currentHero(targetRef.side, targetRef.position, attacker, defender)?.troops ?: 0) <= 0) {
                return@forEach
            }
            activeStatuses.filter { it.status.isDamageOverTime() }.forEach dotStatus@{ active ->
                val target = currentHero(targetRef.side, targetRef.position, attacker, defender)
                    ?: return@dotStatus
                val damage = ongoingDamage(
                    active,
                    target,
                    if (targetRef.side == Side.ATTACKER) {
                        attacker.values
                    } else {
                        defender.values
                    },
                )
                val newTarget = target.copy(troops = (target.troops - damage).coerceAtLeast(0))
                if (targetRef.side == Side.ATTACKER) {
                    attacker[targetRef.position] = newTarget
                } else {
                    defender[targetRef.position] = newTarget
                }
                events += BattleEvent.OngoingDamage(
                    round = round,
                    source = active.source,
                    target = targetRef,
                    status = active.status,
                    damage = damage,
                    targetTroopsAfter = newTarget.troops,
                    skillId = active.skillId,
                )
            }
        }
    }

    private fun expireStatuses(statuses: MutableMap<BattleHeroRef, MutableList<ActiveBattleStatus>>) {
        statuses.values.forEach { list ->
            list.replaceAll { it.copy(remainingRounds = it.remainingRounds - 1) }
            list.removeAll { it.remainingRounds <= 0 }
        }
        statuses.entries.removeAll { it.value.isEmpty() }
    }

    private fun ongoingDamage(
        status: ActiveBattleStatus,
        target: BattleHero,
        targetTeam: Collection<BattleHero>,
    ): Int {
        val source = status.sourceSnapshot
        if (source != null && status.power > 0) {
            return BattleDamageCalculator.strategy(
                source = source,
                target = target,
                ratePercent = status.power,
                ongoing = true,
                targetConditions = BattleDamageCalculator.targetConditions(target, targetTeam),
            )
        }
        val base = when (status.status) {
            BattleStatus.SHAKE -> status.power / 3
            BattleStatus.PANIC -> status.power / 2
            BattleStatus.BURN -> status.power / 2
            BattleStatus.HEX -> status.power / 2
            else -> 0
        }.coerceAtLeast(1)
        return base.coerceAtMost(target.troops)
    }

    private fun BattleStatus.isDamageOverTime(): Boolean =
        this == BattleStatus.SHAKE || this == BattleStatus.PANIC || this == BattleStatus.BURN || this == BattleStatus.HEX
}
