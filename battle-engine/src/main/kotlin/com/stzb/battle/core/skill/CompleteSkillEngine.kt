package com.stzb.battle.core.skill

import com.stzb.battle.core.ActionPermission
import com.stzb.battle.core.BattleActionResolver
import com.stzb.battle.core.BattleConfigRepository
import com.stzb.battle.core.BattleDamageCalculator
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleModifier
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleStat
import com.stzb.battle.core.DamageOrigin
import com.stzb.battle.core.DamageSchool
import com.stzb.battle.core.DamageTag
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillKind
import com.stzb.battle.core.opposite
import kotlin.math.roundToInt

private const val ZHENGSHI_SIGNAL = "skill.200244.next-action"
private const val BAIZHAN_STACKS = "skill.200252.stacks"
private const val BAIZHAN_MAX_STACKS = 3
private const val HUANGYI_SUCCESSFUL_ROLLS = "skill.200016.successful-rolls"
private const val BENGFA_SKILL_ID = 400112
private const val BENGFA_RETRIGGER_COUNT = "equipment.400112.pursuit-retrigger"
private const val MOUDUAN_SKILL_ID = 400063
private const val MOUDUAN_PREPARATION_REDUCTION = 1
private const val POLANG_SKILL_ID = 400111
private const val POLANG_CHILD_SKILL_ID = 410111
private const val POLANG_LAYER_PERCENT = 10
private const val POLANG_MAX_LAYERS = 10
private const val BUQU_FEATURE_SKILL_ID = 450020
private const val BUQU_CHILD_SKILL_ID = 451020
private const val BUQU_MAX_LAYERS = Int.MAX_VALUE
private const val BUXIE_EQUIPMENT_FEATURE_SKILL_ID = 450042
private const val BUXIE_EQUIPMENT_CHILD_SKILL_ID = 451042
private const val BUXIE_EQUIPMENT_DETAIL_ID = 45104201
private const val BUXIE_EQUIPMENT_EFFECT_ID = 281
private const val JISHI_EQUIPMENT_FEATURE_SKILL_ID = 450022
private const val JISHI_EQUIPMENT_CHILD_SKILL_ID = 451022
private const val JISHI_EQUIPMENT_DETAIL_ID = 45102201
private const val JISHI_EQUIPMENT_EFFECT_ID = 522
private const val JISHI_MAX_LAYERS = 99
private const val XUANFENG_EQUIPMENT_FEATURE_SKILL_ID = 450038
private const val XUANFENG_EQUIPMENT_CHILD_SKILL_ID = 451038
private const val JINYANZHIJIAN_SKILL_ID = 200966
private const val JINYANZHIJIAN_PROBABILITY_DETAIL_ID = 21096613
private const val JINYANZHIJIAN_DAMAGE_DETAIL_ID = 21396601
private const val XUEFENDUANBING_SKILL_ID = 200258
private const val XUEFENDUANBING_RANGE_REDUCTION_SKILL_ID = 210258
private const val XUEFENDUANBING_DOUBLE_STRIKE_DETAIL_ID = 20025803
private const val XUEFENDUANBING_SHAKE_DETAIL_ID = 20025804
private const val XUEFENDUANBING_EVADE_DETAIL_ID = 20025802
private const val JINGGUANLEIZHONG_SKILL_ID = 200898
private const val JINGGUANLEIZHONG_PROBABILITY_DETAIL_ID = 21089801
private const val JINGGUANLEIZHONG_BRANCH_SKILL_ID = 211898
private const val JINGGUANLEIZHONG_PHYSICAL_DETAIL_ID = 21189801
private const val JINGGUANLEIZHONG_STRATEGY_DETAIL_ID = 21189802
private const val YONGZHIGANGYI_SKILL_ID = 200288
private const val YONGZHIGANGYI_PHYSICAL_REACTION_DETAIL_ID = 20028801
private const val YONGZHIGANGYI_STRATEGY_REACTION_DETAIL_ID = 20028802
private const val YONGZHIGANGYI_THRESHOLD_LAYER_COUNTER = "skill.200288.threshold-layers"
private val YONGZHIGANGYI_THRESHOLD_DETAILS = listOf(
    90 to 20028803,
    80 to 20028804,
    70 to 20028805,
    60 to 20028806,
)
private const val JIUFAZHONGYUAN_SKILL_ID = 200290
private const val JIUFAZHONGYUAN_DAMAGE_SKILL_ID = 210290
private const val JIUFAZHONGYUAN_RESPONSE_COUNT = "skill.200290.active-responses"
private const val JIUFAZHONGYUAN_MAX_RESPONSES = 9
private val JIUFAZHONGYUAN_ROUND_DETAIL_IDS = listOf(20029002, 20029003)

private data class QiqinqizongGuardResult(
    val guarded: Boolean,
    val completion: SkillExecutionResult,
)

private data class SelectedActiveSkill(
    val owner: BattleHeroRef,
    val skillId: Int,
    val successfulExecutions: Int,
)

private data class PibingjuyiDamageBeforeResult(
    val change: TroopDamageChange,
    val owner: BattleHeroRef?,
)

interface CompleteSkillEngine {
    fun prepareBattle(context: SkillBattleContext): List<BattleEvent>
    fun trigger(trigger: BattleTrigger, context: SkillBattleContext): List<BattleEvent>
    fun permissionFor(actor: BattleHeroRef, context: SkillBattleContext): ActionPermission
}

class DefaultCompleteSkillEngine private constructor(
    val state: SkillBattleState,
    private val graph: SkillRuleGraph,
    private val interpreter: SkillRuleInterpreter,
    private val timing: CompleteTimingCoordinator,
    private val applier: BattleStateChangeApplier,
    private val specialPlugins: SpecialSkillPluginRegistry,
) : CompleteSkillEngine {
    private var prepared = false
    private val actionResolver = BattleActionResolver()
    private val skillTargetSelector = SkillTargetSelector()
    private val cooldownUntilRound = mutableMapOf<Pair<BattleHeroRef, Int>, Int>()
    private val pendingExtraNormalAttacks = mutableMapOf<BattleHeroRef, Int>()
    private val jinyanzhijianSelections =
        mutableMapOf<BattleHeroRef, List<SelectedActiveSkill>>()
    private val jiuzhanTargets = mutableMapOf<BattleHeroRef, Set<BattleHeroRef>>()
    private val zhijizhibiDealtTargets = mutableMapOf<BattleHeroRef, Set<BattleHeroRef>>()
    private val zhijizhibiTakenTargets = mutableMapOf<BattleHeroRef, Set<BattleHeroRef>>()
    private val gongqibubeiTargets = mutableMapOf<BattleHeroRef, Set<BattleHeroRef>>()
    private val fanjianTargets = mutableMapOf<BattleHeroRef, Set<BattleHeroRef>>()
    private val xixiangwugongTargets = mutableMapOf<BattleHeroRef, Set<BattleHeroRef>>()
    private val tongjunweishenOwners = linkedSetOf<BattleHeroRef>()
    private val hezonglianhengOwners = linkedSetOf<BattleHeroRef>()
    private val fenglinghushuOwners = linkedSetOf<BattleHeroRef>()
    private val leishiOwners = linkedSetOf<BattleHeroRef>()
    private val budongrushanOwners = linkedSetOf<BattleHeroRef>()
    private val huoshouchongfengOwners = linkedSetOf<BattleHeroRef>()
    private val panzhenshanshouOwners = linkedSetOf<BattleHeroRef>()
    private val mouyihongtuOwners = linkedSetOf<BattleHeroRef>()
    private val leishiGuardRounds = mutableMapOf<BattleHeroRef, Int>()
    private val huoshouchongfengRounds = mutableMapOf<BattleHeroRef, Int>()
    private val panzhenshanshouRounds = mutableMapOf<BattleHeroRef, Int>()
    private val mingqixushiRounds = mutableMapOf<BattleHeroRef, Int>()
    private val mouzhuRounds = mutableMapOf<BattleHeroRef, Int>()
    private val mouzhuActiveUntilRounds = mutableMapOf<BattleHeroRef, Int>()
    private val mouyihongtuRounds = mutableMapOf<BattleHeroRef, Int>()
    private val suanwuyiceListeners =
        mutableMapOf<Pair<BattleHeroRef, BattleHeroRef>, Int>()
    private val mouduanSuccessfulActivations =
        mutableMapOf<Pair<BattleHeroRef, Int>, Int>()
    private val zhengshiRetriggerRounds = mutableMapOf<BattleHeroRef, Int>()
    private val pendingTargetActionDamage =
        mutableMapOf<BattleHeroRef, MutableList<TroopDamageChange>>()
    private val qisheOwners = mutableSetOf<BattleHeroRef>()
    private val chuqiOwners = mutableSetOf<BattleHeroRef>()
    private val wentaoOwners = mutableSetOf<BattleHeroRef>()
    private val liangyuanOwners = mutableSetOf<BattleHeroRef>()
    private val wentaoTriggeredRounds = mutableMapOf<BattleHeroRef, Int>()
    private val liangyuanTriggeredRounds = mutableMapOf<BattleHeroRef, Int>()
    private val jingguanleizhongResolving = mutableSetOf<BattleHeroRef>()

    override fun prepareBattle(context: SkillBattleContext): List<BattleEvent> {
        if (prepared) return emptyList()
        prepared = true
        return buildList {
            val sources = livingHeroesInSpeedOrder()
            sources.forEach { source ->
                val sourceContext = context.copy(source = source)
                apply(openingEquipmentModifiersResult(source), sourceContext)
                addAll(trigger(BattleTrigger.BATTLE_PASSIVE, sourceContext))
                if (isBaizhanOwner(source)) {
                    state.runtime.addCounter(
                        owner = source,
                        namespace = BAIZHAN_STACKS,
                        delta = BAIZHAN_MAX_STACKS,
                        maximum = BAIZHAN_MAX_STACKS,
                    )
                }
                if (201006 in state.liveHero(source).skillIds) {
                    state.view.heroes()
                        .filter { candidate ->
                            candidate.side == source.side &&
                                (state.view.state(candidate)?.troops ?: 0) > 0
                        }
                        .sortedBy(BattleHeroRef::position)
                        .forEach { target ->
                            add(
                                BattleEvent.SkillTriggered(
                                    round = context.round,
                                    source = target,
                                    rootSkillId = 201006,
                                    skillId = 221006,
                                    trigger = BattleTrigger.BATTLE_PASSIVE,
                                ),
                            )
                        }
                }
            }
            sources.forEach { source ->
                addAll(trigger(BattleTrigger.BATTLE_COMMAND, context.copy(source = source)))
            }
        }
    }

    override fun trigger(
        trigger: BattleTrigger,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        val scoped = context.copy(
            runtime = state.runtime,
            trigger = trigger,
            battleView = state.view,
            skillProbabilityUses = SkillProbabilityUseSink { source, skillId, skillKind ->
                applier.consumeSkillProbabilityUses(source, skillId, skillKind)
                context.skillProbabilityUses.consume(source, skillId, skillKind)
            },
            forcedTargets = BattleForcedTargetSource { request ->
                if (request.rule.skillKind == SkillKind.ACTIVE &&
                    request.rule.effectId in 301..307
                ) {
                    applier.tryConsumeForcedTarget(
                        actor = request.context.source,
                        eligibleTargets = request.candidates,
                        random = request.context.random,
                    )?.let(::listOf)
                        ?: context.forcedTargets.select(request)
                } else {
                    context.forcedTargets.select(request)
                }
            },
        )
        val events = mutableListOf<BattleEvent>()
        if (trigger.emitsPoint()) {
            events += BattleEvent.TriggerPoint(scoped.round, scoped.source, trigger)
        }
        val configuredResult = when (trigger) {
            BattleTrigger.ROUND_START -> {
                val first = state.view.heroes()
                    .filter { requireNotNull(state.view.state(it)).troops > 0 }
                    .sortedWith(
                        compareByDescending<BattleHeroRef> {
                            requireNotNull(state.view.state(it)).stats.speed
                        }.thenBy { it.side.ordinal }.thenBy { it.position },
                    )
                    .firstOrNull()
                if (scoped.source == first) {
                    val boundaryOutputs = applier.beginRound(scoped.round)
                    events += processDamageOutputs(boundaryOutputs, scoped)
                    val timingResult = timing.onRoundStart(scoped)
                    events += apply(timingResult, scoped)
                    val roundOutputs = applier.onRoundStart(scoped.round)
                    events += processDamageOutputs(roundOutputs, scoped)
                    zhengshiRoundStartResult(scoped) +
                        shoujingRoundResult(scoped) +
                        pibingjuyiRoundStartResult(scoped) +
                        tongjunweishenRoundResult(scoped) +
                        mouyihongtuRoundStartResult(scoped) +
                        timedTroopRoundStartResult(scoped)
                } else {
                    SkillExecutionResult.EMPTY
                } + leishiRoundStartResult(scoped) +
                    huoshouchongfengRoundStartResult(scoped) +
                    panzhenshanshouRoundStartResult(scoped) +
                    mouzhuRoundStartResult(scoped) +
                    mingqixushiRoundStartResult(scoped) +
                    baizhanSpendResult(scoped.source, scoped) +
                    taoyuanRoundResult(scoped) +
                    jiufazhongyuanRoundResult(scoped) +
                    roundStackingPassiveResult(scoped)
            }
            BattleTrigger.ROUND_END -> {
                tianziRoundEndResult(scoped) +
                    xuefenduanbingRoundEndResult(scoped)
            }
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            BattleTrigger.PURSUIT_ATTEMPT,
            -> attemptSkills(trigger, scoped)
            BattleTrigger.ACTION_BEFORE -> {
                events += apply(budongrushanActionResult(scoped), scoped)
                val ongoingOutputs = applier.onActionStart(scoped.source, scoped.round)
                events += processDamageOutputs(ongoingOutputs, scoped)
                if (
                    (state.view.state(scoped.source)?.troops ?: 0) <= 0 ||
                    baseDefeated()
                ) {
                    return events
                }
                val pendingDamageResult = targetActionBeforeDamageResult(scoped)
                val timingResult = timing.onAction(scoped)
                val preparedActiveRetriggerResult = zhengshiRetriggerResult(
                    scoped.copy(trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT),
                    timingResult,
                )
                val actionResult = pendingDamageResult +
                    timingResult +
                    preparedActiveRetriggerResult +
                    liangyuanActionResult(scoped) +
                    dingjunActionResult(scoped) +
                    jinyanzhijianActionResult(scoped) +
                    bingwuchangshiActionResult(scoped) +
                    jishiActionResult(scoped) +
                    xuefenduanbingActionResult(scoped) +
                    xilingkejinActionResult(scoped) +
                    xixiangwugongActionResult(scoped) +
                    sanjunqichuActionResult(scoped) +
                    qibingjubeiActionResult(scoped) +
                    kuihouxiangtaActionResult(scoped)
                events += apply(actionResult, scoped)
                val fenjiResult = executeFenjiAction(scoped) { event ->
                    events += event
                }
                val pluginResponseSeed = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = emptyList(),
                    executedSkillIds =
                        actionResult.executedSkillIds + fenjiResult.executedSkillIds,
                    diagnostics = emptyList(),
                )
                events += apply(
                    withSuccessfulSkillPluginResponses(pluginResponseSeed, scoped),
                    scoped,
                )
                SkillExecutionResult.EMPTY
            }
            BattleTrigger.BATTLE_PASSIVE,
            BattleTrigger.BATTLE_COMMAND,
            -> {
                skillsFor(scoped.source, trigger).forEach { skillId ->
                    val skillResult = executeBattleSkill(trigger, scoped, skillId)
                    events += apply(
                        withSuccessfulSkillPluginResponses(skillResult, scoped),
                        scoped,
                    )
                }
                SkillExecutionResult.EMPTY
            }
            else -> SkillExecutionResult.EMPTY
        } + hezonglianhengNormalAttackResult(scoped) +
            fuboyangshaNormalAttackResult(scoped) +
            qibuActionResult(scoped) +
            jixianNormalAttackResult(scoped) +
            xuanfengNormalAttackResult(scoped)
        val result = configuredResult +
            shijiActionResult(scoped) +
            fenglinghushuResponseResult(scoped, configuredResult) +
            sanjunduoshuaiResult(scoped, configuredResult) +
            jiufazhongyuanResponseResult(scoped, configuredResult) +
            zhengshiRetriggerResult(scoped, configuredResult) +
            bengfaPursuitRetriggerResult(scoped, configuredResult)
        events += apply(withSuccessfulSkillPluginResponses(result, scoped), scoped)
        return events
    }

    override fun permissionFor(
        actor: BattleHeroRef,
        context: SkillBattleContext,
    ): ActionPermission {
        val scopedContext = context.copy(runtime = state.runtime, battleView = state.view)
        val permission = applier.permissionFor(actor, scopedContext)
        val base = ActionPermissionResolver(state.effectStore).permissionFor(actor, scopedContext)
        val canNormalAttack =
            permission.canNormalAttack &&
                BattleModifier.NormalAttackDisabled !in state.liveHero(actor).modifiers
        return base.copy(
            canAct = permission.canAct,
            canCastActive = permission.canCastActive,
            canNormalAttack = canNormalAttack,
            redirectTarget = base.redirectTarget,
            normalAttackCount = if (canNormalAttack) permission.normalAttackCount else 0,
            grantsPursuitOpportunityPerNormal =
                canNormalAttack && permission.pursuitOpportunityCount > 0,
            counterattack = permission.counterattack,
            secondaryAttack = permission.splitAttack,
            firstAction = permission.firstAction,
        )
    }

    fun applyNormalDamage(
        round: Int,
        source: BattleHeroRef,
        target: BattleHeroRef,
        amount: Int,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        val redirected = applier.permissionFor(target).damageRedirectTarget ?: target
        val result = applier.apply(
            listOf(
                TroopDamageChange(
                    source = source,
                    target = redirected,
                    amount = amount,
                    troopsAfter = (
                        requireNotNull(state.view.state(redirected)).troops - amount
                        ).coerceAtLeast(0),
                    school = com.stzb.battle.core.DamageSchool.PHYSICAL,
                    origin = com.stzb.battle.core.DamageOrigin.NORMAL,
                    tags = emptySet(),
                    skillId = 0,
                    effectId = 0,
                ),
            ),
            round,
        )
        return processDamageOutputs(result, context.copy(round = round, source = source))
    }

    fun resolveNormalAttack(
        round: Int,
        source: BattleHeroRef,
        target: BattleHeroRef,
        random: BattleRandom,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        val prospective = TroopDamageChange(
            source = source,
            target = target,
            amount = 0,
            troopsAfter = requireNotNull(state.view.state(target)).troops,
            school = DamageSchool.PHYSICAL,
            origin = DamageOrigin.NORMAL,
            tags = emptySet(),
            skillId = 0,
            effectId = 0,
        )
        val events = mutableListOf<BattleEvent>()
        events += apply(
            chijieDamageBeforeResult(
                prospective,
                context.copy(
                    round = round,
                    source = source,
                    trigger = BattleTrigger.DAMAGE_BEFORE,
                ),
            ),
            context,
        )
        val liveSource = liveHero(source)
        val liveTarget = liveHero(target)
        val allies = state.view.heroes()
            .filter { it.side == source.side }
            .map(::liveHero)
        val enemies = state.view.heroes()
            .filter { it.side != source.side }
            .map(::liveHero)
        val amount = actionResolver.normalAttackDamage(
            liveSource,
            liveTarget,
            random,
            allies,
            enemies,
        )
        val normalDamageEvents = applyNormalDamage(round, source, target, amount, context)
        events += normalDamageEvents
        normalDamageEvents
            .filterIsInstance<BattleEvent.NormalAttack>()
            .filter { it.damage > 0 }
            .forEach { damage ->
                val listenerContext = context.copy(
                    round = round,
                    source = damage.target,
                    rootSkillId = LEISHI_SKILL_ID,
                    currentSkillId = LEISHI_SKILL_ID,
                    trigger = BattleTrigger.HURT_AFTER,
                )
                events += apply(
                    leishiNormalAttackDamageResult(damage.target, listenerContext),
                    listenerContext,
                )
            }
        return events
    }

    private fun executeSimulatedNormalAttack(
        change: SimulatedNormalAttackChange,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        if ((state.view.state(change.source)?.troops ?: 0) <= 0) return emptyList()
        val permission = permissionFor(change.source, context)
        val targetPool = permission.resolvedTargetPool.ifEmpty {
            state.view.heroes().filter { ref ->
                ref.side == (permission.resolvedAllegiance ?: change.source.side).opposite()
            }
        }
        val currentSource = state.liveHero(change.source)
        val allies = state.view.heroes()
            .filter { it.side == change.source.side }
            .map(state::liveHero)
        val targetHeroes = targetPool.map(state::liveHero)
        val eligible = actionResolver.normalAttackTargetsInRange(
            source = currentSource,
            enemies = targetHeroes,
            allies = allies,
        )
        val selected = when (change.mode) {
            SimulatedNormalAttackMode.SINGLE -> {
                val target = actionResolver.selectNormalAttackTarget(
                    source = currentSource,
                    enemies = targetHeroes,
                    random = context.random,
                    allies = allies,
                ) ?: return emptyList()
                listOf(target)
            }
            SimulatedNormalAttackMode.ALL_IN_RANGE -> eligible.map { it.first }
        }
        return buildList {
            selected.forEach targetLoop@{ targetHero ->
                if (baseDefeated() || (state.view.state(change.source)?.troops ?: 0) <= 0) {
                    return@targetLoop
                }
                var target = targetPool.single {
                    it.position == targetHero.position && it.heroId == targetHero.id
                }
                if (change.mode == SimulatedNormalAttackMode.SINGLE) {
                    target = redirectNormalAttackTarget(
                        change.source,
                        target,
                        context.random,
                    )
                }
                if ((state.view.state(target)?.troops ?: 0) <= 0) return@targetLoop
                recordTarget(change.source, target)
                addAll(
                    trigger(
                        BattleTrigger.NORMAL_ATTACK_BEFORE,
                        context.copy(
                            source = change.source,
                            trigger = BattleTrigger.NORMAL_ATTACK_BEFORE,
                        ),
                    ),
                )
                val evaded = tryEvade(
                    context.round,
                    change.source,
                    target,
                    context,
                )
                if (evaded != null) {
                    add(evaded)
                } else {
                    addAll(
                        resolveNormalAttack(
                            round = context.round,
                            source = change.source,
                            target = target,
                            random = context.random,
                            context = context,
                        ),
                    )
                    if (!baseDefeated()) {
                        val targetContext = context.copy(
                            source = target,
                            trigger = BattleTrigger.DAMAGE_AFTER,
                        )
                        if (
                            BattleModifier.CounterattackImmunity !in
                            state.liveHero(change.source).modifiers &&
                            permissionFor(target, targetContext).counterattack
                        ) {
                            addAll(
                                reactiveAttack(
                                    context.round,
                                    target,
                                    change.source,
                                    551,
                                    targetContext,
                                ),
                            )
                        }
                    }
                }
                state.runtime.recordBattleTriggerOccurrence(
                    change.source,
                    BattleTrigger.NORMAL_ATTACK_AFTER,
                )
                addAll(
                    trigger(
                        BattleTrigger.NORMAL_ATTACK_AFTER,
                        context.copy(
                            source = change.source,
                            trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                        ),
                    ),
                )
            }
        }
    }

    internal fun schedule(
        change: BattleStateChange,
        round: Int,
    ) {
        timing.enqueue(change, round, timing.position().hit)
    }

    internal fun timingPosition(): TimingPosition = timing.position()

    internal fun consumePendingExtraNormalAttacks(actor: BattleHeroRef): Int =
        pendingExtraNormalAttacks.remove(actor) ?: 0

    internal fun applyChanges(
        changes: List<BattleStateChange>,
        context: SkillBattleContext,
    ): List<BattleEvent> = apply(
        SkillExecutionResult.immutable(changes, emptyList(), emptyList(), emptyList()),
        context,
    )

    fun liveHero(ref: BattleHeroRef) = state.liveHero(ref)

    fun livingHeroesInSpeedOrder(): List<BattleHeroRef> =
        state.view.heroes()
            .filter { requireNotNull(state.view.state(it)).troops > 0 }
            .sortedWith(
                compareByDescending<BattleHeroRef> {
                    applier.permissionFor(it).firstAction
                }.thenByDescending {
                    requireNotNull(state.view.state(it)).stats.speed
                }.thenBy { it.side.ordinal }.thenBy { it.position },
            )

    fun recordTarget(source: BattleHeroRef, target: BattleHeroRef) {
        history.record(source, target)
    }

    fun forcedNormalAttackTarget(
        actor: BattleHeroRef,
        normalTarget: BattleHeroRef,
        random: BattleRandom,
    ): BattleHeroRef {
        val eligibleTargets = state.view.heroes().filter { candidate ->
            candidate.side != actor.side &&
                (state.view.state(candidate)?.troops ?: 0) > 0
        }
        return applier.tryConsumeForcedTarget(actor, eligibleTargets, random)
            ?: normalTarget
    }

    fun redirectNormalAttackTarget(
        actor: BattleHeroRef,
        intendedTarget: BattleHeroRef,
        random: BattleRandom,
    ): BattleHeroRef =
        ActionPermissionResolver(state.effectStore)
            .permissionFor(actor, intendedTarget)
            .redirectTarget
            ?: forcedNormalAttackTarget(actor, intendedTarget, random)

    internal fun recordDamageThresholds(
        damageSource: BattleHeroRef,
        context: SkillBattleContext,
    ) {
        state.runtime.recordBattleTriggerOccurrence(damageSource, BattleTrigger.DAMAGE_AFTER)
        val damageCount = state.runtime.sideCount(damageSource.side, BattleTrigger.DAMAGE_AFTER)
        state.view.heroes()
            .filter { owner ->
                owner.side != damageSource.side &&
                    200244 in state.liveHero(owner).skillIds
            }
            .forEach { owner ->
                if (state.runtime.consumeThreshold(
                        owner = owner,
                        namespace = "skill.200244.enemy-damage",
                        count = damageCount,
                        threshold = 15,
                    ) &&
                    state.runtime.consumeLimitedOccurrence(
                        owner = owner,
                        namespace = ZHENGSHI_ACTIVATION_LIMIT,
                        limit = 1,
                    )
                ) {
                    state.runtime.scheduleSignal(
                        owner,
                        ZHENGSHI_SIGNAL,
                        readyRound = context.round + 1,
                    )
                }
            }
    }

    private fun xinzhanDamageResult(
        damageSource: BattleHeroRef,
        damageTarget: BattleHeroRef,
        damageCount: Int,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (damageCount !in 1..9) return SkillExecutionResult.EMPTY
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == damageSource.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200275 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        val listenerContext = context.copy(
            source = owner,
            rootSkillId = 200275,
            currentSkillId = 214275,
            trigger = BattleTrigger.DAMAGE_AFTER,
        )
        val morale = interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 21427501 },
            listenerContext,
            preselectedTargets = listOf(damageTarget),
        )
        if (damageCount < 9) return morale
        return morale + interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 20027523 },
            listenerContext.copy(currentSkillId = 200275),
            preselectedTargets = listOf(owner),
        )
    }

    private fun xinzhanLifeStealRegistrationResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val detail = graph.details.single { it.detailId == 21227501 }
        return state.view.heroes()
            .filter { candidate ->
                candidate.side == context.source.side &&
                    state.view.state(candidate)?.troops?.let { it > 0 } == true
            }
            .fold(SkillExecutionResult.EMPTY) { result, target ->
                result + interpreter.executeDetailForEngine(
                    detail = detail,
                    context = context.copy(
                        rootSkillId = 200275,
                        currentSkillId = 212275,
                        trigger = BattleTrigger.BATTLE_COMMAND,
                    ),
                    preselectedTargets = listOf(target),
                    probabilityAlreadyAccepted = true,
                )
            }
    }

    private fun attackDamageRecoveryResult(
        output: BattleStateOutput.DamageDealt,
    ): SkillExecutionResult {
        if (output.amount <= 0 || output.school != DamageSchool.PHYSICAL) {
            return SkillExecutionResult.EMPTY
        }
        val effect = state.effectStore.effectsFor(output.source).lastOrNull {
            it.effectId == 542 &&
                it.effectiveStrength > 0
        } ?: return SkillExecutionResult.EMPTY
        val amount = output.amount.toLong()
            .times(effect.effectiveStrength)
            .div(100)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        if (amount <= 0) return SkillExecutionResult.EMPTY
        val targetState = requireNotNull(state.view.state(output.source))
        return SkillExecutionResult.immutable(
            stateChanges = listOf(
                RecoverTroopsChange(
                    source = effect.source,
                    target = output.source,
                    amount = amount,
                    troopsAfter = (targetState.troops + amount)
                        .coerceAtMost(targetState.maxTroops),
                    skillId = effect.skillId,
                    effectId = effect.effectId,
                ),
            ),
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun shoujingRoundResult(context: SkillBattleContext): SkillExecutionResult {
        val detailId = when (context.round) {
            6 -> 20027704
            8 -> 20027705
            else -> return SkillExecutionResult.EMPTY
        }
        return state.view.heroes()
            .filter { owner ->
                state.view.state(owner)?.troops?.let { it > 0 } == true &&
                    200277 in state.liveHero(owner).skillIds &&
                    state.runtime.consumeThreshold(
                        owner = owner,
                        namespace = "skill.200277.round.${context.round}",
                        count = 1,
                        threshold = 1,
                    )
            }
            .fold(SkillExecutionResult.EMPTY) { result, owner ->
                result + interpreter.executeDetailForEngine(
                    graph.details.single { it.detailId == detailId },
                    context.copy(
                        source = owner,
                        rootSkillId = 200277,
                        currentSkillId = 200277,
                        trigger = BattleTrigger.ROUND_START,
                    ),
                )
            }
    }

    private fun huiyanDamageResult(
        damageSource: BattleHeroRef,
        damageCount: Int,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (damageCount != 6) return SkillExecutionResult.EMPTY
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == damageSource.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200294 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 20029402 },
            context.copy(
                source = owner,
                rootSkillId = 200294,
                currentSkillId = 200294,
                trigger = BattleTrigger.DAMAGE_AFTER,
            ),
        )
    }

    private fun dingjunActionResult(context: SkillBattleContext): SkillExecutionResult {
        if (context.round != 4 || 200293 !in state.liveHero(context.source).skillIds) {
            return SkillExecutionResult.EMPTY
        }
        val actionContext = context.copy(
            rootSkillId = 200293,
            currentSkillId = 200293,
            trigger = BattleTrigger.ACTION_BEFORE,
        )
        return listOf(20029307, 20029311).fold(SkillExecutionResult.EMPTY) { result, detailId ->
            result + interpreter.executeDetailForEngine(
                graph.details.single { it.detailId == detailId },
                actionContext,
            )
        }
    }

    private fun executeFenjiAction(
        context: SkillBattleContext,
        onEvent: (BattleEvent) -> Unit,
    ): SkillExecutionResult {
        if (200961 !in state.liveHero(context.source).skillIds) {
            return SkillExecutionResult.EMPTY
        }
        val listenerContext = context.copy(
            rootSkillId = 200961,
            currentSkillId = 200961,
            trigger = BattleTrigger.ACTION_BEFORE,
        )
        return interpreter.executeDetailStreamingForEngine(
            graph.details.single { it.detailId == 20096103 },
            listenerContext,
        ) { step ->
            apply(step, listenerContext).forEach(onEvent)
        }
    }

    private fun isBaizhanOwner(source: BattleHeroRef): Boolean =
        source.position != 0 &&
            200252 in state.liveHero(source).skillIds

    private fun baizhanSpendResult(
        owner: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (!isBaizhanOwner(owner) ||
            state.runtime.counter(owner, BAIZHAN_STACKS) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        state.runtime.addCounter(
            owner = owner,
            namespace = BAIZHAN_STACKS,
            delta = -1,
            maximum = BAIZHAN_MAX_STACKS,
        )
        state.runtime.recordMarker(
            target = owner,
            detailId = 21325201,
            value = 1,
            appliedRound = context.round,
            durationRounds = 1,
            rootSkillId = 200252,
            source = owner,
        )
        val recoveryContext = context.copy(
            source = owner,
            rootSkillId = 200252,
            currentSkillId = 214252,
        )
        return try {
            interpreter.executeDetailForEngine(
                detail = graph.details.single { it.detailId == 21425203 },
                context = recoveryContext,
                preselectedTargets = listOf(owner),
                probabilityAlreadyAccepted = true,
            )
        } finally {
            state.runtime.removeMarker(owner, 21325201)
        }
    }

    private fun manghouHurtResult(
        owner: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            MANGHOU_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == MANGHOU_LISTENER_DETAIL_ID },
            context = context.copy(
                source = owner,
                rootSkillId = MANGHOU_SKILL_ID,
                currentSkillId = MANGHOU_SKILL_ID,
                trigger = BattleTrigger.HURT_AFTER,
            ),
        )
    }

    private fun chuangyiHurtResult(
        output: BattleStateOutput.DamageDealt,
    ): SkillExecutionResult {
        val owner = output.target
        if (
            output.amount <= 0 ||
            CHUANGYI_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val detailId = when (output.school) {
            DamageSchool.PHYSICAL -> CHUANGYI_PHYSICAL_REDUCTION_DETAIL_ID
            DamageSchool.STRATEGY -> CHUANGYI_STRATEGY_REDUCTION_DETAIL_ID
        }
        val effect = state.effectStore.effectsFor(owner).singleOrNull { active ->
            active.skillId == CHUANGYI_SKILL_ID &&
                active.detailId == detailId
        } ?: return SkillExecutionResult.EMPTY
        if (effect.effectiveStrength <= 0) return SkillExecutionResult.EMPTY
        val detail = graph.details.single { it.detailId == detailId }
        val divisor = kotlin.math.abs(detail.raw.valueAddMax).coerceAtLeast(1)
        val stepNamespace = "skill.$CHUANGYI_SKILL_ID.$detailId.reduction-step"
        val existingStep = state.runtime.counter(owner, stepNamespace)
        val step = if (existingStep > 0) {
            existingStep
        } else {
            (effect.effectiveStrength / divisor).coerceAtLeast(1).also { derived ->
                state.runtime.addCounter(
                    owner = owner,
                    namespace = stepNamespace,
                    delta = derived,
                )
            }
        }
        val updatedStrength = (effect.effectiveStrength - step).coerceAtLeast(0)
        return SkillExecutionResult.immutable(
            stateChanges = listOf(
                UpdateDamageModifierStrengthChange(
                    source = effect.source,
                    target = owner,
                    skillId = effect.skillId,
                    detailId = effect.detailId,
                    effectId = effect.effectId,
                    percent = -updatedStrength,
                ),
            ),
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun sheshenHurtResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = output.target
        if (
            SHESHEN_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            liveFormationDistance(owner, output.source) > SHESHEN_TRIGGER_RANGE
        ) {
            return SkillExecutionResult.EMPTY
        }
        val listenerContext = context.copy(
            source = owner,
            rootSkillId = SHESHEN_SKILL_ID,
            currentSkillId = SHESHEN_LISTENER_DETAIL_ID / 100,
            trigger = BattleTrigger.HURT_AFTER,
        )
        val marker = interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == SHESHEN_LISTENER_DETAIL_ID },
            context = listenerContext,
            preselectedTargets = listOf(output.source),
        )
        if (!state.runtime.hasMarker(owner, SHESHEN_MARKER_DETAIL_ID, context.round)) {
            return marker
        }
        val cleanup = interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == SHESHEN_CLEANUP_DETAIL_ID },
            context = listenerContext.copy(
                currentSkillId = SHESHEN_CLEANUP_DETAIL_ID / 100,
            ),
            preselectedTargets = listOf(owner),
            probabilityAlreadyAccepted = true,
        )
        return marker + cleanup
    }

    private fun liveFormationDistance(
        source: BattleHeroRef,
        target: BattleHeroRef,
    ): Int {
        val sourceFront = state.view.heroes().count { candidate ->
            candidate.side == source.side &&
                candidate.position > source.position &&
                (state.view.state(candidate)?.troops ?: 0) > 0
        }
        val targetFront = state.view.heroes().count { candidate ->
            candidate.side == target.side &&
                candidate.position > target.position &&
                (state.view.state(candidate)?.troops ?: 0) > 0
        }
        return 1 + sourceFront + targetFront
    }

    private fun taoyuanRoundResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            context.round <= 0 ||
            TAOYUAN_SKILL_ID !in state.liveHero(context.source).skillIds ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val childSkillId = if (context.round <= TAOYUAN_RECOVERY_LAST_ROUND) {
            TAOYUAN_RECOVERY_SKILL_ID
        } else {
            TAOYUAN_DAMAGE_SKILL_ID
        }
        return interpreter.retriggerSkillForEngine(
            skillId = childSkillId,
            trigger = BattleTrigger.BATTLE_COMMAND,
            context = context.copy(
                rootSkillId = TAOYUAN_SKILL_ID,
                currentSkillId = childSkillId,
            ),
        )
    }

    private fun roundStackingPassiveResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            context.round <= 0 ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val ownedSkillIds = state.liveHero(owner).skillIds
        val changes = ROUND_STACKING_PASSIVE_DETAIL_IDS.flatMap { (skillId, detailIds) ->
            if (skillId !in ownedSkillIds) {
                return@flatMap emptyList<UpdateDamageModifierStrengthChange>()
            }
            val effects = detailIds.mapNotNull { detailId ->
                state.effectStore.effectsFor(owner).singleOrNull { active ->
                    active.skillId == skillId && active.detailId == detailId
                }
            }
            if (
                effects.isEmpty() ||
                !state.runtime.consumeLimitedOccurrence(
                    owner = owner,
                    namespace = "skill.$skillId.round.${context.round}",
                    limit = 1,
                )
            ) {
                return@flatMap emptyList()
            }
            effects.map { effect ->
                val stepNamespace = "skill.$skillId.${effect.detailId}.layer-strength"
                val existingStep = state.runtime.counter(owner, stepNamespace)
                val step = if (existingStep > 0) {
                    existingStep
                } else {
                    effect.effectiveStrength.also { initial ->
                        state.runtime.addCounter(
                            owner = owner,
                            namespace = stepNamespace,
                            delta = initial,
                        )
                    }
                }
                val maximum = step * (context.request.maxRounds + 1)
                UpdateDamageModifierStrengthChange(
                    source = effect.source,
                    target = owner,
                    skillId = effect.skillId,
                    detailId = effect.detailId,
                    effectId = effect.effectId,
                    percent = -(effect.effectiveStrength + step).coerceAtMost(maximum),
                )
            }
        }
        if (changes.isEmpty()) return SkillExecutionResult.EMPTY
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun jiufazhongyuanRoundResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            context.round <= 0 ||
            JIUFAZHONGYUAN_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val changes = JIUFAZHONGYUAN_ROUND_DETAIL_IDS.mapNotNull { detailId ->
            val effect = state.effectStore.effectsFor(owner).singleOrNull { active ->
                active.skillId == JIUFAZHONGYUAN_SKILL_ID &&
                    active.detailId == detailId
            } ?: return@mapNotNull null
            val stepNamespace = "skill.$JIUFAZHONGYUAN_SKILL_ID.$detailId.layer-strength"
            val existingStep = state.runtime.counter(owner, stepNamespace)
            val step = if (existingStep > 0) {
                existingStep
            } else {
                effect.effectiveStrength.also { initial ->
                    state.runtime.addCounter(
                        owner = owner,
                        namespace = stepNamespace,
                        delta = initial,
                    )
                }
            }
            val desiredStrength = step * context.round.coerceAtMost(context.request.maxRounds)
            if (effect.effectiveStrength >= desiredStrength) {
                return@mapNotNull null
            }
            UpdateDamageModifierStrengthChange(
                source = effect.source,
                target = owner,
                skillId = effect.skillId,
                detailId = effect.detailId,
                effectId = effect.effectId,
                percent = -desiredStrength,
            )
        }
        if (changes.isEmpty()) return SkillExecutionResult.EMPTY
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun bingwuchangshiActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            BINGWUCHANGSHI_SKILL_ID !in state.liveHero(context.source).skillIds ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val detailId = BINGWUCHANGSHI_BRANCH_DETAIL_IDS[
            context.random.nextInt(BINGWUCHANGSHI_BRANCH_DETAIL_IDS.size)
        ]
        return interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == detailId },
            context = context.copy(
                rootSkillId = BINGWUCHANGSHI_SKILL_ID,
                currentSkillId = BINGWUCHANGSHI_CHILD_SKILL_ID,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
            probabilityAlreadyAccepted = true,
        )
    }

    private fun jishiActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            JISHI_SKILL_ID !in state.liveHero(context.source).skillIds ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val listenerContext = context.copy(
            rootSkillId = JISHI_SKILL_ID,
            currentSkillId = JISHI_CHILD_SKILL_ID,
            trigger = BattleTrigger.ACTION_BEFORE,
        )
        val triggerResult = SkillExecutionResult.immutable(
            stateChanges = emptyList(),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = context.source,
                    rootSkillId = JISHI_SKILL_ID,
                    skillId = JISHI_CHILD_SKILL_ID,
                    trigger = BattleTrigger.ACTION_BEFORE,
                ),
            ),
            executedSkillIds = listOf(JISHI_CHILD_SKILL_ID),
            diagnostics = emptyList(),
        )
        return JISHI_BRANCH_DETAIL_IDS.fold(triggerResult) { result, detailId ->
            var branchRollPending = true
            result + interpreter.executeDetailForEngine(
                detail = graph.details.single { it.detailId == detailId },
                context = listenerContext.copy(
                    random = object : BattleRandom {
                        override fun nextInt(bound: Int): Int =
                            if (branchRollPending) {
                                branchRollPending = false
                                context.random.nextInt(bound)
                            } else {
                                0
                            }
                    },
                ),
            )
        }
    }

    private fun xuefenduanbingRoundEndResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            XUEFENDUANBING_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            state.liveHero(owner).stats.hitRange <= 1
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.retriggerSkillForEngine(
            skillId = XUEFENDUANBING_RANGE_REDUCTION_SKILL_ID,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            context = context.copy(
                rootSkillId = XUEFENDUANBING_SKILL_ID,
                currentSkillId = XUEFENDUANBING_RANGE_REDUCTION_SKILL_ID,
                trigger = BattleTrigger.BATTLE_PASSIVE,
            ),
        )
    }

    private fun xuefenduanbingActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            XUEFENDUANBING_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            state.liveHero(owner).stats.hitRange > 1
        ) {
            return SkillExecutionResult.EMPTY
        }
        val skillContext = context.copy(
            rootSkillId = XUEFENDUANBING_SKILL_ID,
            currentSkillId = XUEFENDUANBING_SKILL_ID,
            trigger = BattleTrigger.ACTION_BEFORE,
        )
        val doubleStrike = interpreter.executeDetailForEngine(
            detail = graph.details.single {
                it.detailId == XUEFENDUANBING_DOUBLE_STRIKE_DETAIL_ID
            },
            context = skillContext,
            probabilityAlreadyAccepted = true,
        )
        val shake = interpreter.executeDetailForEngine(
            detail = graph.details.single {
                it.detailId == XUEFENDUANBING_SHAKE_DETAIL_ID
            },
            context = skillContext,
        )
        return doubleStrike + shake
    }

    private fun xuefenduanbingHurtResult(
        owner: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            XUEFENDUANBING_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            state.liveHero(owner).stats.hitRange <= 1
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single {
                it.detailId == XUEFENDUANBING_EVADE_DETAIL_ID
            },
            context = context.copy(
                source = owner,
                rootSkillId = XUEFENDUANBING_SKILL_ID,
                currentSkillId = XUEFENDUANBING_SKILL_ID,
                trigger = BattleTrigger.HURT_AFTER,
            ),
            probabilityAlreadyAccepted = true,
        )
    }

    private fun jingguanleizhongDamageEvents(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        val owner = output.source
        if (
            output.amount <= 0 ||
            owner in jingguanleizhongResolving ||
            context.rootSkillId == JINGGUANLEIZHONG_SKILL_ID ||
            output.skillId == JINGGUANLEIZHONG_BRANCH_SKILL_ID ||
            JINGGUANLEIZHONG_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            (state.view.state(output.target)?.troops ?: 0) <= 0
        ) {
            return emptyList()
        }
        val listenerContext = context.copy(
            source = owner,
            rootSkillId = JINGGUANLEIZHONG_SKILL_ID,
            currentSkillId = JINGGUANLEIZHONG_PROBABILITY_DETAIL_ID / 100,
            trigger = BattleTrigger.DAMAGE_AFTER,
        )
        val probabilityDetail = graph.details.single {
            it.detailId == JINGGUANLEIZHONG_PROBABILITY_DETAIL_ID
        }
        if (!interpreter.detailProbabilitySucceedsForEngine(
                detail = probabilityDetail,
                context = listenerContext,
            )
        ) {
            return emptyList()
        }
        val damageDetailId =
            if (context.random.nextInt(2) == 0) {
                JINGGUANLEIZHONG_PHYSICAL_DETAIL_ID
            } else {
                JINGGUANLEIZHONG_STRATEGY_DETAIL_ID
            }
        val damageContext = listenerContext.copy(
            currentSkillId = JINGGUANLEIZHONG_BRANCH_SKILL_ID,
        )
        val result = SkillExecutionResult.immutable(
            stateChanges = emptyList(),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = owner,
                    rootSkillId = JINGGUANLEIZHONG_SKILL_ID,
                    skillId = JINGGUANLEIZHONG_BRANCH_SKILL_ID,
                    trigger = BattleTrigger.DAMAGE_AFTER,
                ),
            ),
            executedSkillIds = listOf(JINGGUANLEIZHONG_BRANCH_SKILL_ID),
            diagnostics = emptyList(),
        ) + interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == damageDetailId },
            context = damageContext,
            preselectedTargets = listOf(output.target),
            probabilityAlreadyAccepted = true,
        )
        jingguanleizhongResolving += owner
        return try {
            apply(result, damageContext)
        } finally {
            jingguanleizhongResolving -= owner
        }
    }

    private fun yongzhigangyiReactionResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = output.target
        if (
            output.amount <= 0 ||
            YONGZHIGANGYI_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val detailId = when (output.school) {
            DamageSchool.PHYSICAL -> YONGZHIGANGYI_PHYSICAL_REACTION_DETAIL_ID
            DamageSchool.STRATEGY -> YONGZHIGANGYI_STRATEGY_REACTION_DETAIL_ID
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == detailId },
            context = context.copy(
                source = owner,
                rootSkillId = YONGZHIGANGYI_SKILL_ID,
                currentSkillId = YONGZHIGANGYI_SKILL_ID,
                trigger = BattleTrigger.HURT_AFTER,
            ),
        )
    }

    private fun yongzhigangyiThresholdResult(
        owner: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (YONGZHIGANGYI_SKILL_ID !in state.liveHero(owner).skillIds) {
            return SkillExecutionResult.EMPTY
        }
        val ownerState = requireNotNull(state.view.state(owner))
        if (ownerState.troops <= 0 || ownerState.maxTroops <= 0) {
            return SkillExecutionResult.EMPTY
        }
        val desiredLayers = YONGZHIGANGYI_THRESHOLD_DETAILS.count { (threshold, _) ->
            ownerState.troops.toLong() * 100 <
                ownerState.maxTroops.toLong() * threshold
        }
        val appliedLayers = state.runtime.counter(
            owner,
            YONGZHIGANGYI_THRESHOLD_LAYER_COUNTER,
        )
        if (desiredLayers <= appliedLayers) return SkillExecutionResult.EMPTY

        val result = YONGZHIGANGYI_THRESHOLD_DETAILS
            .subList(appliedLayers, desiredLayers)
            .fold(SkillExecutionResult.EMPTY) { aggregate, (_, detailId) ->
                aggregate + interpreter.executeDetailForEngine(
                    detail = graph.details.single { it.detailId == detailId },
                    context = context.copy(
                        source = owner,
                        rootSkillId = YONGZHIGANGYI_SKILL_ID,
                        currentSkillId = YONGZHIGANGYI_SKILL_ID,
                        trigger = BattleTrigger.HURT_AFTER,
                    ),
                    probabilityAlreadyAccepted = true,
                )
            }
        state.runtime.addCounter(
            owner = owner,
            namespace = YONGZHIGANGYI_THRESHOLD_LAYER_COUNTER,
            delta = desiredLayers - appliedLayers,
            maximum = YONGZHIGANGYI_THRESHOLD_DETAILS.size,
        )
        return result
    }

    private fun xilingkejinActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owners = state.view.heroes().filter { candidate ->
            candidate.side == context.source.side &&
                (state.view.state(candidate)?.troops ?: 0) > 0 &&
                XILINGKEJIN_SKILL_ID in state.liveHero(candidate).skillIds
        }
        if (owners.isEmpty()) return SkillExecutionResult.EMPTY
        val allies = state.view.heroes().filter { candidate ->
            candidate.side == context.source.side &&
                (state.view.state(candidate)?.troops ?: 0) > 0
        }
        val attackLeader = allies.maxWithOrNull(
            compareBy<BattleHeroRef> {
                requireNotNull(state.view.state(it)).stats.precise(BattleStat.ATTACK)
            }.thenBy { -it.position },
        )
        val strategyLeader = allies.maxWithOrNull(
            compareBy<BattleHeroRef> {
                requireNotNull(state.view.state(it)).stats.precise(BattleStat.STRATEGY)
            }.thenBy { -it.position },
        )
        return owners.fold(SkillExecutionResult.EMPTY) { aggregate, owner ->
            val ownerHero = state.liveHero(owner)
            val ownerLevel = ownerHero.skillIds.indexOf(XILINGKEJIN_SKILL_ID)
                .let { index -> ownerHero.skillLevels.getOrElse(index) { 1 } }
                .coerceIn(1, 10)
            var result = SkillExecutionResult.EMPTY
            if (context.source == attackLeader) {
                result += xilingkejinBranchResult(
                    context = context,
                    ownerLevel = ownerLevel,
                    probabilityDetailId = XILINGKEJIN_ATTACK_PROBABILITY_DETAIL_ID,
                    childSkillId = XILINGKEJIN_ATTACK_SKILL_ID,
                    damageDetailId = XILINGKEJIN_ATTACK_DAMAGE_DETAIL_ID,
                    recoveryDetailId = XILINGKEJIN_ATTACK_RECOVERY_DETAIL_ID,
                )
            }
            if (context.source == strategyLeader) {
                result += xilingkejinBranchResult(
                    context = context,
                    ownerLevel = ownerLevel,
                    probabilityDetailId = XILINGKEJIN_STRATEGY_PROBABILITY_DETAIL_ID,
                    childSkillId = XILINGKEJIN_STRATEGY_SKILL_ID,
                    damageDetailId = XILINGKEJIN_STRATEGY_DAMAGE_DETAIL_ID,
                    recoveryDetailId = XILINGKEJIN_STRATEGY_RECOVERY_DETAIL_ID,
                )
            }
            aggregate + result
        }
    }

    private fun xilingkejinBranchResult(
        context: SkillBattleContext,
        ownerLevel: Int,
        probabilityDetailId: Int,
        childSkillId: Int,
        damageDetailId: Int,
        recoveryDetailId: Int,
    ): SkillExecutionResult {
        val probabilityContext = context.copy(
            rootSkillId = XILINGKEJIN_SKILL_ID,
            currentSkillId = XILINGKEJIN_CHILD_SKILL_ID,
            trigger = BattleTrigger.ACTION_BEFORE,
        )
        val probabilityDetail = graph.details.single {
            it.detailId == probabilityDetailId
        }
        if (!interpreter.detailProbabilitySucceedsForEngine(
                probabilityDetail,
                probabilityContext,
            )
        ) {
            return SkillExecutionResult.EMPTY
        }
        val actor = state.liveHero(context.source)
        fun execute(detailId: Int, preselectedTargets: List<BattleHeroRef>? = null) =
            graph.details.single { it.detailId == detailId }.let { detail ->
                interpreter.executeDetailForEngine(
                    detail = detail,
                    context = probabilityContext.copy(currentSkillId = childSkillId),
                    preselectedTargets = preselectedTargets,
                    valueOverride = TypedBattlePotency.rate(
                        configuredBattleRate(detail, actor, ownerLevel),
                    ),
                    probabilityAlreadyAccepted = true,
                )
            }
        return execute(damageDetailId) +
            execute(recoveryDetailId, listOf(context.source))
    }

    private fun xixiangwugongActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round != XIXIANGWUGONG_TRIGGER_ROUND) {
            return SkillExecutionResult.EMPTY
        }
        val owners = xixiangwugongTargets.entries.filter { (_, targets) ->
            context.source in targets
        }
        return owners.fold(SkillExecutionResult.EMPTY) { aggregate, (owner, _) ->
            val ownerHero = state.liveHero(owner)
            val ownerLevel = ownerHero.skillIds.indexOf(XIXIANGWUGONG_SKILL_ID)
                .let { index -> ownerHero.skillLevels.getOrElse(index) { 1 } }
                .coerceIn(1, 10)
            val actor = state.liveHero(context.source)
            val listenerContext = context.copy(
                rootSkillId = XIXIANGWUGONG_SKILL_ID,
                currentSkillId = XIXIANGWUGONG_CHILD_SKILL_ID,
                trigger = BattleTrigger.ACTION_BEFORE,
            )
            fun execute(
                detailId: Int,
                preselectedTargets: List<BattleHeroRef>? = null,
            ) = graph.details.single { it.detailId == detailId }.let { detail ->
                interpreter.executeDetailForEngine(
                    detail = detail,
                    context = listenerContext,
                    preselectedTargets = preselectedTargets,
                    valueOverride = TypedBattlePotency.rate(
                        configuredBattleRate(detail, actor, ownerLevel),
                    ),
                    probabilityAlreadyAccepted = true,
                )
            }
            aggregate +
                execute(XIXIANGWUGONG_DAMAGE_DETAIL_ID) +
                execute(XIXIANGWUGONG_STRATEGY_REDUCTION_DETAIL_ID, listOf(context.source)) +
                execute(XIXIANGWUGONG_PHYSICAL_REDUCTION_DETAIL_ID, listOf(context.source))
        }
    }

    private fun kuihouxiangtaActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            KUIHOUXIANGTA_SKILL_ID !in state.liveHero(context.source).skillIds ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single {
                it.detailId == KUIHOUXIANGTA_DAMAGE_DETAIL_ID
            },
            context = context.copy(
                rootSkillId = KUIHOUXIANGTA_SKILL_ID,
                currentSkillId = KUIHOUXIANGTA_CHILD_SKILL_ID,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
            probabilityAlreadyAccepted = true,
        )
    }

    private fun sanjunqichuActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            SANJUNQICHU_SKILL_ID !in state.liveHero(context.source).skillIds ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single {
                it.detailId == SANJUNQICHU_TRIGGER_DETAIL_ID
            },
            context = context.copy(
                rootSkillId = SANJUNQICHU_SKILL_ID,
                currentSkillId = SANJUNQICHU_TRIGGER_DETAIL_ID / 100,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
        )
    }

    private fun tongjunweishenRoundResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0) return SkillExecutionResult.EMPTY
        val lifecycle = EffectLifecycleOverride(
            delayRound = 0,
            delayHit = 0,
            availableRounds = 1,
            availableHit = 0,
            clearPerHit = false,
        )
        return tongjunweishenOwners.fold(SkillExecutionResult.EMPTY) { ownersResult, owner ->
            val ownerContext = context.copy(
                source = owner,
                rootSkillId = TONGJUNWEISHEN_SKILL_ID,
                currentSkillId = TONGJUNWEISHEN_CHILD_SKILL_ID,
                trigger = BattleTrigger.ROUND_START,
            )
            val targets = state.view.heroes()
                .filter {
                    it.side == owner.side &&
                        (state.view.state(it)?.troops ?: 0) > 0
                }
            targets.fold(ownersResult) { result, target ->
                val stats = requireNotNull(state.view.state(target)).stats
                val physical = stats.attack >= stats.strategy
                val boostDetailId = if (physical) {
                    TONGJUNWEISHEN_PHYSICAL_BOOST_DETAIL_ID
                } else {
                    TONGJUNWEISHEN_STRATEGY_BOOST_DETAIL_ID
                }
                val ignoreDetailId = if (physical) {
                    TONGJUNWEISHEN_DEFENSE_IGNORE_DETAIL_ID
                } else {
                    TONGJUNWEISHEN_STRATEGY_IGNORE_DETAIL_ID
                }
                val boostDetail = graph.details.single {
                    it.detailId == boostDetailId
                }
                val ignoreDetail = graph.details.single {
                    it.detailId == ignoreDetailId
                }
                result +
                    tongjunweishenDetailResult(
                        detail = boostDetail,
                        target = target,
                        context = ownerContext,
                        configuredProbability = (
                            configuredDetailProbability(boostDetail, owner) -
                                TONGJUNWEISHEN_ROUND_PROBABILITY_STEP * (context.round - 1)
                            ).coerceIn(0, 100),
                        lifecycle = lifecycle,
                    ) +
                    tongjunweishenDetailResult(
                        detail = ignoreDetail,
                        target = target,
                        context = ownerContext,
                        configuredProbability = (
                            configuredDetailProbability(ignoreDetail, owner) +
                                TONGJUNWEISHEN_ROUND_PROBABILITY_STEP * (context.round - 1)
                            ).coerceIn(0, 100),
                        lifecycle = lifecycle,
                    )
            }
        }
    }

    private fun tongjunweishenDetailResult(
        detail: SkillEffectRule,
        target: BattleHeroRef,
        context: SkillBattleContext,
        configuredProbability: Int,
        lifecycle: EffectLifecycleOverride,
    ): SkillExecutionResult {
        if (!interpreter.detailProbabilitySucceedsForEngine(
                detail = detail,
                context = context,
                configuredProbability = configuredProbability,
            )
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = detail,
            context = context,
            preselectedTargets = listOf(target),
            probabilityAlreadyAccepted = true,
            executionOverride = ReferencedDetailExecutionOverride(
                referencedDetailId = detail.detailId,
                lifecycleOverride = lifecycle.copy(
                    availableHit = detail.raw.availableHit,
                    clearPerHit = detail.raw.clearPerHit,
                ),
            ),
        )
    }

    private fun configuredDetailProbability(
        detail: SkillEffectRule,
        owner: BattleHeroRef,
    ): Int {
        val hero = state.liveHero(owner)
        val skillIndex = hero.skillIds.indexOf(TONGJUNWEISHEN_SKILL_ID)
        val level = hero.skillLevels.getOrElse(skillIndex) { 1 }.coerceIn(1, 10)
        return (
            detail.raw.probabilityInit +
                (level - 1) *
                (detail.raw.probabilityMax - detail.raw.probabilityInit) / 9.0
            ).toInt().coerceIn(0, 100)
    }

    private fun qibingjubeiActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            QIBINGJUBEI_SKILL_ID !in state.liveHero(context.source).skillIds ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val probabilityDetail = graph.details.single {
            it.detailId == QIBINGJUBEI_PROBABILITY_DETAIL_ID
        }
        val bonus = state.runtime.counter(context.source, QIBINGJUBEI_PROBABILITY_COUNTER)
        val probabilityContext = context.copy(
            rootSkillId = QIBINGJUBEI_SKILL_ID,
            currentSkillId = QIBINGJUBEI_PROBABILITY_SKILL_ID,
            trigger = BattleTrigger.ACTION_BEFORE,
        )
        if (!interpreter.detailProbabilitySucceedsForEngine(
                detail = probabilityDetail,
                context = probabilityContext,
                configuredProbability = (
                    configuredDetailProbability(probabilityDetail, context.source) + bonus
                    ).coerceIn(0, 100),
            )
        ) {
            state.runtime.addCounter(
                owner = context.source,
                namespace = QIBINGJUBEI_PROBABILITY_COUNTER,
                delta = QIBINGJUBEI_PROBABILITY_STEP,
                maximum = QIBINGJUBEI_MAX_PROBABILITY_BONUS,
            )
            return SkillExecutionResult.EMPTY
        }
        state.runtime.addCounter(
            owner = context.source,
            namespace = QIBINGJUBEI_PROBABILITY_COUNTER,
            delta = -bonus,
        )

        val ownerHero = state.liveHero(context.source)
        val skillIndex = ownerHero.skillIds.indexOf(QIBINGJUBEI_SKILL_ID)
        val ownerLevel = ownerHero.skillLevels.getOrElse(skillIndex) { 1 }.coerceIn(1, 10)
        val enemies = state.view.heroes()
            .filter {
                it.side != context.source.side &&
                    (state.view.state(it)?.troops ?: 0) > 0
            }
        val base = enemies.singleOrNull { it.position == 0 }
        val middle = enemies.singleOrNull { it.position == 1 }
        val ownerResult = qibingjubeiDamageResult(
            actor = context.source,
            childSkillId = QIBINGJUBEI_OWNER_SKILL_ID,
            detailTargets = listOf(
                QIBINGJUBEI_OWNER_BASE_DETAIL_ID to base,
                QIBINGJUBEI_OWNER_MIDDLE_DETAIL_ID to middle,
            ),
            ownerLevel = ownerLevel,
            context = context,
        )
        val fastestAlly = state.view.heroes()
            .filter {
                it.side == context.source.side &&
                    it != context.source &&
                    (state.view.state(it)?.troops ?: 0) > 0
            }
            .maxWithOrNull(
                compareBy<BattleHeroRef> {
                    requireNotNull(state.view.state(it)).stats.speed
                }.thenByDescending(BattleHeroRef::position),
            )
            ?: return ownerResult
        val branch = context.random.nextInt(QIBINGJUBEI_DELEGATE_BRANCH_COUNT)
        val delegateResult = qibingjubeiDamageResult(
            actor = fastestAlly,
            childSkillId = QIBINGJUBEI_DELEGATE_SKILL_ID,
            detailTargets = listOf(
                QIBINGJUBEI_DELEGATE_BASE_DETAIL_IDS[branch] to base,
                QIBINGJUBEI_DELEGATE_MIDDLE_DETAIL_IDS[branch] to middle,
            ),
            ownerLevel = ownerLevel,
            context = context,
        )
        return ownerResult + delegateResult
    }

    private fun qibingjubeiDamageResult(
        actor: BattleHeroRef,
        childSkillId: Int,
        detailTargets: List<Pair<Int, BattleHeroRef?>>,
        ownerLevel: Int,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val actorHero = state.liveHero(actor)
        val childContext = context.copy(
            source = actor,
            rootSkillId = QIBINGJUBEI_SKILL_ID,
            currentSkillId = childSkillId,
            trigger = BattleTrigger.ACTION_BEFORE,
        )
        val damage = detailTargets.fold(SkillExecutionResult.EMPTY) {
                result,
                (detailId, target),
            ->
            if (target == null) {
                result
            } else {
                val detail = graph.details.single { it.detailId == detailId }
                result + interpreter.executeDetailForEngine(
                    detail = detail,
                    context = childContext,
                    preselectedTargets = listOf(target),
                    valueOverride = TypedBattlePotency.rate(
                        configuredBattleRate(detail, actorHero, ownerLevel),
                    ),
                    probabilityAlreadyAccepted = true,
                )
            }
        }
        if (damage.stateChanges.isEmpty()) return damage
        return SkillExecutionResult.immutable(
            stateChanges = emptyList(),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = actor,
                    rootSkillId = QIBINGJUBEI_SKILL_ID,
                    skillId = childSkillId,
                    trigger = BattleTrigger.ACTION_BEFORE,
                ),
            ),
            executedSkillIds = listOf(childSkillId),
            diagnostics = emptyList(),
        ) + damage
    }

    private fun manwangHurtResult(
        target: BattleHeroRef,
        hurtCount: Int,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (200297 !in state.liveHero(target).skillIds ||
            !state.runtime.consumeThreshold(
                owner = target,
                namespace = "skill.200297.actual-hurt",
                count = hurtCount,
                threshold = 5,
            )
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 20029725 },
            context.copy(
                source = target,
                rootSkillId = 200297,
                currentSkillId = 200297,
                trigger = BattleTrigger.HURT_AFTER,
            ),
        )
    }

    private fun shijiActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            context.trigger != BattleTrigger.ACTION_BEFORE ||
            context.round !in 1..SHIJI_ACTIVE_ROUNDS ||
            SHIJI_SKILL_ID !in state.liveHero(context.source).skillIds ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        return listOf(
            SHIJI_ENEMY_DEBUFF_SKILL_ID to SHIJI_ENEMY_DEBUFF_DETAIL_IDS,
            SHIJI_BASE_BUFF_SKILL_ID to SHIJI_BASE_BUFF_DETAIL_IDS,
        ).fold(SkillExecutionResult.EMPTY) { result, (skillId, detailIds) ->
            val childContext = context.copy(
                rootSkillId = SHIJI_SKILL_ID,
                currentSkillId = skillId,
            )
            val details = detailIds.fold(SkillExecutionResult.EMPTY) { childResult, detailId ->
                childResult + interpreter.executeDetailForEngine(
                    detail = graph.details.single { it.detailId == detailId },
                    context = childContext,
                    probabilityAlreadyAccepted = true,
                )
            }
            result + SkillExecutionResult.immutable(
                stateChanges = emptyList(),
                events = listOf(
                    SkillTriggered(
                        round = context.round,
                        source = context.source,
                        rootSkillId = SHIJI_SKILL_ID,
                        skillId = skillId,
                        trigger = BattleTrigger.ACTION_BEFORE,
                    ),
                ),
                executedSkillIds = listOf(skillId),
                diagnostics = emptyList(),
            ) + details
        }
    }

    private fun shijiHurtResult(
        target: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            context.round !in 1..SHIJI_ACTIVE_ROUNDS ||
            SHIJI_SKILL_ID !in state.liveHero(target).skillIds ||
            (state.view.state(target)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val childContext = context.copy(
            source = target,
            rootSkillId = SHIJI_SKILL_ID,
            currentSkillId = SHIJI_INSIGHT_SKILL_ID,
            trigger = BattleTrigger.HURT_AFTER,
        )
        return SkillExecutionResult.immutable(
            stateChanges = emptyList(),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = target,
                    rootSkillId = SHIJI_SKILL_ID,
                    skillId = SHIJI_INSIGHT_SKILL_ID,
                    trigger = BattleTrigger.HURT_AFTER,
                ),
            ),
            executedSkillIds = listOf(SHIJI_INSIGHT_SKILL_ID),
            diagnostics = emptyList(),
        ) + interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == SHIJI_INSIGHT_DETAIL_ID },
            context = childContext,
            preselectedTargets = listOf(target),
            probabilityAlreadyAccepted = true,
        )
    }

    private fun sanjunduoshuaiResult(
        context: SkillBattleContext,
        configuredResult: SkillExecutionResult,
    ): SkillExecutionResult {
        val hero = state.liveHero(context.source)
        if (200987 !in hero.skillIds) return SkillExecutionResult.EMPTY
        val successfulResponses = when (context.trigger) {
            BattleTrigger.NORMAL_ATTACK_AFTER -> 1
            BattleTrigger.ACTIVE_SKILL_ATTEMPT -> configuredResult.executedSkillIds.count { skillId ->
                skillId in hero.skillIds && graph.rule(skillId)?.kind == SkillKind.ACTIVE
            }
            BattleTrigger.PURSUIT_ATTEMPT -> configuredResult.executedSkillIds.count { skillId ->
                skillId in hero.skillIds && graph.rule(skillId)?.kind == SkillKind.PURSUIT
            }
            else -> 0
        }
        return (0 until successfulResponses).fold(SkillExecutionResult.EMPTY) { result, _ ->
            result + sanjunduoshuaiBranch(context)
        }
    }

    private fun jiufazhongyuanResponseResult(
        context: SkillBattleContext,
        configuredResult: SkillExecutionResult,
    ): SkillExecutionResult {
        if (context.trigger != BattleTrigger.ACTIVE_SKILL_ATTEMPT) {
            return SkillExecutionResult.EMPTY
        }
        val owner = context.source
        val hero = state.liveHero(owner)
        if (
            JIUFAZHONGYUAN_SKILL_ID !in hero.skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val successfulActives = configuredResult.executedSkillIds.count { skillId ->
            skillId in hero.skillIds && graph.rule(skillId)?.kind == SkillKind.ACTIVE
        }
        return (0 until successfulActives).fold(SkillExecutionResult.EMPTY) { result, _ ->
            if (!state.runtime.consumeLimitedOccurrence(
                    owner = owner,
                    namespace = JIUFAZHONGYUAN_RESPONSE_COUNT,
                    limit = JIUFAZHONGYUAN_MAX_RESPONSES,
                )
            ) {
                result
            } else {
                result + interpreter.retriggerSkillForEngine(
                    skillId = JIUFAZHONGYUAN_DAMAGE_SKILL_ID,
                    trigger = BattleTrigger.BATTLE_PASSIVE,
                    context = context.copy(
                        rootSkillId = JIUFAZHONGYUAN_SKILL_ID,
                        currentSkillId = JIUFAZHONGYUAN_DAMAGE_SKILL_ID,
                    ),
                )
            }
        }
    }

    private fun fenglinghushuResponseResult(
        context: SkillBattleContext,
        configuredResult: SkillExecutionResult,
    ): SkillExecutionResult {
        val actor = state.liveHero(context.source)
        val successfulResponses = when (context.trigger) {
            BattleTrigger.NORMAL_ATTACK_AFTER -> 1
            BattleTrigger.ACTIVE_SKILL_ATTEMPT -> configuredResult.executedSkillIds.count {
                it in actor.skillIds && graph.rule(it)?.kind == SkillKind.ACTIVE
            }
            BattleTrigger.PURSUIT_ATTEMPT -> configuredResult.executedSkillIds.count {
                it in actor.skillIds && graph.rule(it)?.kind == SkillKind.PURSUIT
            }
            else -> 0
        }
        if (successfulResponses == 0) return SkillExecutionResult.EMPTY

        return fenglinghushuOwners
            .asSequence()
            .filter { owner ->
                owner.side == context.source.side &&
                    owner != context.source &&
                    (state.view.state(owner)?.troops ?: 0) > 0
            }
            .fold(SkillExecutionResult.EMPTY) { ownerResult, owner ->
                (0 until successfulResponses).fold(ownerResult) { result, _ ->
                    val responseContext = context.copy(
                        source = owner,
                        rootSkillId = FENGLINGHUSHU_SKILL_ID,
                        currentSkillId = FENGLINGHUSHU_BUFF_SKILL_ID,
                    )
                    val buffs = FENGLINGHUSHU_BUFF_DETAIL_IDS.fold(
                        SkillExecutionResult.EMPTY,
                    ) { buffResult, detailId ->
                        buffResult + interpreter.executeDetailForEngine(
                            detail = graph.details.single { it.detailId == detailId },
                            context = responseContext,
                            preselectedTargets = listOf(owner),
                            probabilityAlreadyAccepted = true,
                        )
                    }
                    result + SkillExecutionResult.immutable(
                        stateChanges = emptyList(),
                        events = listOf(
                            SkillTriggered(
                                round = context.round,
                                source = owner,
                                rootSkillId = FENGLINGHUSHU_SKILL_ID,
                                skillId = FENGLINGHUSHU_BUFF_SKILL_ID,
                                trigger = context.trigger,
                            ),
                        ),
                        executedSkillIds = listOf(FENGLINGHUSHU_BUFF_SKILL_ID),
                        diagnostics = emptyList(),
                    ) + buffs
                }
            }
    }

    private fun sanjunduoshuaiBranch(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val responseContext = context.copy(
            rootSkillId = 200987,
            currentSkillId = 211987,
        )
        val physicalBranch = context.random.nextInt(100) < 50
        val damageDetailId = if (physicalBranch) 21198701 else 21198723
        val modifierDetailId = if (physicalBranch) 21198712 else 21198724
        val damage = interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == damageDetailId },
            context = responseContext,
            probabilityAlreadyAccepted = true,
        )
        val modifierTargets = if (physicalBranch) {
            listOf(context.source)
        } else {
            damage.stateChanges.filterIsInstance<TroopDamageChange>()
                .map(TroopDamageChange::target)
                .distinct()
        }
        val modifier = interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == modifierDetailId },
            context = responseContext,
            preselectedTargets = modifierTargets,
            probabilityAlreadyAccepted = true,
        )
        return SkillExecutionResult.immutable(
            stateChanges = emptyList(),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = context.source,
                    rootSkillId = 200987,
                    skillId = 211987,
                    trigger = context.trigger,
                ),
            ),
            executedSkillIds = listOf(211987),
            diagnostics = emptyList(),
        ) + damage + modifier
    }

    private fun bengfaPursuitRetriggerResult(
        context: SkillBattleContext,
        configuredResult: SkillExecutionResult,
    ): SkillExecutionResult {
        if (context.trigger != BattleTrigger.PURSUIT_ATTEMPT) return SkillExecutionResult.EMPTY
        val hero = state.liveHero(context.source)
        if (hero.equipment.none { it.equipmentId == BENGFA_SKILL_ID }) {
            return SkillExecutionResult.EMPTY
        }
        val pursuitSkillId = configuredResult.executedSkillIds.firstOrNull { skillId ->
            skillId in hero.skillIds && graph.rule(skillId)?.kind == SkillKind.PURSUIT
        } ?: return SkillExecutionResult.EMPTY
        if (state.runtime.counter(context.source, BENGFA_RETRIGGER_COUNT) > 0) {
            return SkillExecutionResult.EMPTY
        }
        state.runtime.addCounter(
            owner = context.source,
            namespace = BENGFA_RETRIGGER_COUNT,
            delta = 1,
            maximum = 1,
        )
        return interpreter.retriggerSkillForEngine(
            skillId = pursuitSkillId,
            trigger = BattleTrigger.PURSUIT_ATTEMPT,
            context = context.copy(
                rootSkillId = pursuitSkillId,
                currentSkillId = pursuitSkillId,
            ),
        )
    }

    private fun jinyanzhijianActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (JINYANZHIJIAN_SKILL_ID !in state.liveHero(context.source).skillIds) {
            return SkillExecutionResult.EMPTY
        }
        val previous = jinyanzhijianSelections[context.source].orEmpty()
        val selectionLimit = if (previous.any { selected ->
                state.runtime.count(
                    selected.owner,
                    BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                    selected.skillId,
                ) > selected.successfulExecutions
            }
        ) {
            3
        } else {
            2
        }
        val candidates = state.view.heroes()
            .asSequence()
            .filter { candidate ->
                candidate.side == context.source.side &&
                    candidate != context.source &&
                    (state.view.state(candidate)?.troops ?: 0) > 0
            }
            .sortedBy(BattleHeroRef::position)
            .flatMap { owner ->
                state.liveHero(owner).skillIds.asSequence()
                    .filter { graph.rule(it)?.kind == SkillKind.ACTIVE }
                    .map { skillId -> owner to skillId }
            }
            .distinct()
            .toMutableList()
        val selected = buildList {
            repeat(minOf(selectionLimit, candidates.size)) {
                add(candidates.removeAt(context.random.nextInt(candidates.size)))
            }
        }
        jinyanzhijianSelections[context.source] = selected.map { (owner, skillId) ->
            SelectedActiveSkill(
                owner = owner,
                skillId = skillId,
                successfulExecutions = state.runtime.count(
                    owner,
                    BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                    skillId,
                ),
            )
        }
        if (selected.isEmpty()) return SkillExecutionResult.EMPTY

        return selected.groupBy(Pair<BattleHeroRef, Int>::first)
            .entries
            .fold(SkillExecutionResult.EMPTY) { aggregate, (target, targetSkills) ->
                val skillIds = targetSkills
                    .mapTo(linkedSetOf(), Pair<BattleHeroRef, Int>::second)
                    .toSet()
                fun scopedDetailResult(detailId: Int): SkillExecutionResult {
                    val base = interpreter.executeDetailForEngine(
                        detail = graph.details.single { it.detailId == detailId },
                        context = context.copy(
                            rootSkillId = JINYANZHIJIAN_SKILL_ID,
                            currentSkillId = detailId / 100,
                        ),
                        preselectedTargets = listOf(target),
                        probabilityAlreadyAccepted = true,
                    )
                    val scopedChanges = base.stateChanges.map { change ->
                        when (change) {
                            is DamageModifierChange -> change.copy(
                                targetSkillId = skillIds.singleOrNull(),
                                targetSkillIds = skillIds.takeIf { it.size > 1 }.orEmpty(),
                            )
                            is ModifierEffectChange -> when (val modifier = change.modifier) {
                                is BattleModifier.SkillProbabilityPercent -> change.copy(
                                    modifier = modifier.copy(
                                        skillId = skillIds.singleOrNull(),
                                        skillKind = SkillKind.ACTIVE,
                                        skillIds = skillIds.takeIf { it.size > 1 }.orEmpty(),
                                    ),
                                )
                                is BattleModifier.DamageDealtPercent -> change.copy(
                                    modifier = modifier.copy(
                                        skillId = skillIds.singleOrNull(),
                                        skillIds = skillIds.takeIf { it.size > 1 }.orEmpty(),
                                    ),
                                )
                                else -> change
                            }
                            else -> change
                        }
                    }
                    return SkillExecutionResult.immutable(
                        stateChanges = scopedChanges,
                        events = base.events,
                        executedSkillIds = base.executedSkillIds,
                        diagnostics = base.diagnostics,
                        timingDues = base.timingDues,
                    )
                }
                aggregate +
                    scopedDetailResult(JINYANZHIJIAN_PROBABILITY_DETAIL_ID) +
                    scopedDetailResult(JINYANZHIJIAN_DAMAGE_DETAIL_ID)
            }
    }

    private fun qibuActionResult(context: SkillBattleContext): SkillExecutionResult {
        if (context.trigger !in setOf(
                BattleTrigger.NORMAL_ATTACK_AFTER,
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                BattleTrigger.PURSUIT_ATTEMPT,
            )
        ) {
            return SkillExecutionResult.EMPTY
        }
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == context.source.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200950 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        val count = state.runtime.sideCount(
            context.source.side,
            BattleTrigger.NORMAL_ATTACK_AFTER,
        ) + state.runtime.sideAttemptCount(
            context.source.side,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
        ) + state.runtime.sideAttemptCount(
            context.source.side,
            BattleTrigger.PURSUIT_ATTEMPT,
        )
        if (!state.runtime.consumeThreshold(
                owner = owner,
                namespace = "skill.200950.team-actions",
                count = count,
                threshold = 7,
            )
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 20095002 },
            context.copy(
                source = owner,
                rootSkillId = 200950,
                currentSkillId = 200950,
            ),
        )
    }

    private fun fuboyangshaNormalAttackResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.trigger != BattleTrigger.NORMAL_ATTACK_AFTER) {
            return SkillExecutionResult.EMPTY
        }
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == context.source.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200255 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        val bonus = state.view.activeEffectStrength(context.source, 20025525)
        if (bonus <= 0) return SkillExecutionResult.EMPTY

        val progress = state.runtime.addCounter(
            owner,
            FUBO_UPLIFT_COUNTER,
            delta = bonus,
        )
        val layers = state.runtime.counter(owner, FUBO_LAYER_COUNTER)
        val generatedLayers = minOf(progress / FUBO_LAYER_THRESHOLD, FUBO_MAX_LAYERS - layers)
        if (generatedLayers > 0) {
            state.runtime.addCounter(
                owner,
                FUBO_UPLIFT_COUNTER,
                delta = -generatedLayers * FUBO_LAYER_THRESHOLD,
            )
            state.runtime.addCounter(
                owner,
                FUBO_LAYER_COUNTER,
                delta = generatedLayers,
                maximum = FUBO_MAX_LAYERS,
            )
        }
        if (context.source == owner) {
            val availableLayers = state.runtime.counter(owner, FUBO_LAYER_COUNTER)
            val extraAttacks = availableLayers / FUBO_LAYERS_PER_EXTRA_ATTACK
            if (extraAttacks > 0) {
                state.runtime.addCounter(
                    owner,
                    FUBO_LAYER_COUNTER,
                    delta = -extraAttacks * FUBO_LAYERS_PER_EXTRA_ATTACK,
                )
                pendingExtraNormalAttacks[owner] =
                    (pendingExtraNormalAttacks[owner] ?: 0) + extraAttacks
            }
        }
        return SkillExecutionResult.EMPTY
    }

    private fun timedTroopRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round != 1) return SkillExecutionResult.EMPTY
        val changes = buildList {
            if (context.source in qisheOwners) {
                add(
                    DamageModifierChange(
                        source = context.source,
                        target = context.source,
                        direction = DamageModifierChange.Direction.DEALT,
                        school = DamageSchool.PHYSICAL,
                        origin = null,
                        tag = null,
                        percent = 15,
                        durationRounds = 3,
                        skillId = QISHE_TROOP_SKILL_ID,
                        effectId = 531,
                        detailId = 29610601,
                    ),
                )
                add(
                    DamageModifierChange(
                        source = context.source,
                        target = context.source,
                        direction = DamageModifierChange.Direction.DEALT,
                        school = DamageSchool.STRATEGY,
                        origin = null,
                        tag = null,
                        percent = 15,
                        durationRounds = 3,
                        skillId = QISHE_TROOP_SKILL_ID,
                        effectId = 533,
                        detailId = 29610602,
                    ),
                )
            }
            if (context.source in chuqiOwners) {
                add(
                    DamageModifierChange(
                        source = context.source,
                        target = context.source,
                        direction = DamageModifierChange.Direction.TAKEN,
                        school = DamageSchool.PHYSICAL,
                        origin = null,
                        tag = null,
                        percent = -60,
                        durationRounds = 1,
                        skillId = CHUQI_TROOP_SKILL_ID,
                        effectId = 522,
                        detailId = 29630101,
                    ),
                )
                add(
                    DamageModifierChange(
                        source = context.source,
                        target = context.source,
                        direction = DamageModifierChange.Direction.TAKEN,
                        school = DamageSchool.STRATEGY,
                        origin = null,
                        tag = null,
                        percent = -60,
                        durationRounds = 1,
                        skillId = CHUQI_TROOP_SKILL_ID,
                        effectId = 524,
                        detailId = 29630102,
                    ),
                )
            }
        }
        if (changes.isEmpty()) return SkillExecutionResult.EMPTY
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun liangyuanActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            context.source !in liangyuanOwners ||
            liangyuanTriggeredRounds[context.source] == context.round ||
            graph.rule(LIANGYUAN_TROOP_SKILL_ID)?.details.orEmpty().none { detail ->
                detail.raw.delayRound + 1 == context.round &&
                    LIANGYUAN_CHILD_SKILL_ID in detail.childSkillIds
            }
        ) {
            return SkillExecutionResult.EMPTY
        }
        liangyuanTriggeredRounds[context.source] = context.round
        return interpreter.execute(
            LIANGYUAN_CHILD_SKILL_ID,
            BattleTrigger.BATTLE_PASSIVE,
            context.copy(
                rootSkillId = LIANGYUAN_TROOP_SKILL_ID,
                currentSkillId = LIANGYUAN_CHILD_SKILL_ID,
                trigger = BattleTrigger.BATTLE_PASSIVE,
            ),
        )
    }

    private fun wentaoStrategyDamageBeforeResult(
        change: TroopDamageChange,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            context.round < 3 ||
            change.school != DamageSchool.STRATEGY ||
            change.source !in wentaoOwners ||
            wentaoTriggeredRounds[change.source] == context.round
        ) {
            return SkillExecutionResult.EMPTY
        }
        wentaoTriggeredRounds[change.source] = context.round
        return SkillExecutionResult.immutable(
            stateChanges = listOf(
                DamageModifierChange(
                    source = change.source,
                    target = change.source,
                    direction = DamageModifierChange.Direction.DEALT,
                    school = DamageSchool.STRATEGY,
                    origin = null,
                    tag = null,
                    percent = 15,
                    durationRounds = 0,
                    skillId = WENTAO_CHILD_SKILL_ID,
                    effectId = 533,
                    detailId = 29720601,
                    availableHits = 1,
                ),
            ),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = change.source,
                    rootSkillId = WENTAO_TROOP_SKILL_ID,
                    skillId = WENTAO_CHILD_SKILL_ID,
                    trigger = BattleTrigger.DAMAGE_BEFORE,
                ),
            ),
            executedSkillIds = listOf(WENTAO_CHILD_SKILL_ID),
            diagnostics = emptyList(),
        )
    }

    private fun openingEquipmentModifiersResult(
        owner: BattleHeroRef,
    ): SkillExecutionResult {
        val changes: List<BattleStateChange> = state.liveHero(owner).modifiers
            .flatMap { modifier ->
                when (modifier) {
                    is BattleModifier.OpeningDamageTakenPercent -> listOf(
                        DamageModifierChange(
                            source = owner,
                            target = owner,
                            direction = DamageModifierChange.Direction.TAKEN,
                            school = modifier.school,
                            origin = null,
                            tag = null,
                            percent = modifier.percent,
                            durationRounds = modifier.durationRounds,
                            skillId = modifier.skillId,
                            effectId = modifier.effectId,
                            detailId = modifier.detailId,
                        ),
                    )
                    is BattleModifier.OpeningControlDurationIncrease -> listOf(
                        ModifierEffectChange(
                            spec = PersistentEffectSpec(
                                source = owner,
                                target = owner,
                                rootSkillId = modifier.rootSkillId,
                                skillId = modifier.skillId,
                                skillKind = SkillKind.PASSIVE,
                                rawSkillType = 17,
                                detailId = modifier.detailId,
                                effectId = modifier.effectId,
                                category = com.stzb.battle.core.EffectCategory.BENEFICIAL,
                                conflict = 0,
                                replaceType = 3,
                                bindFlag = 0,
                                maxStacks = 1,
                                delayRound = 0,
                                delayHit = 0,
                                availableRounds = 0,
                                availableHit = modifier.availableHits,
                                clearPerHit = false,
                                startBoundary = EffectStartBoundary.IMMEDIATE,
                                potency = TypedBattlePotency.flat(modifier.rounds),
                            ),
                            modifier = BattleModifier.ControlDurationIncrease(
                                rounds = modifier.rounds,
                                mainSkillOnly = true,
                            ),
                        ),
                    )
                    else -> emptyList()
                }
            }
        if (changes.isEmpty()) return SkillExecutionResult.EMPTY
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun xuanfengNormalAttackResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.trigger != BattleTrigger.NORMAL_ATTACK_AFTER) {
            return SkillExecutionResult.EMPTY
        }
        val percent = state.liveHero(context.source).modifiers
            .filterIsInstance<BattleModifier.NextStrategyDamageAfterNormalAttackPercent>()
            .sumOf(BattleModifier.NextStrategyDamageAfterNormalAttackPercent::percent)
        if (
            percent <= 0 ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        return SkillExecutionResult.immutable(
            stateChanges = listOf(
                DamageModifierChange(
                    source = context.source,
                    target = context.source,
                    direction = DamageModifierChange.Direction.DEALT,
                    school = DamageSchool.STRATEGY,
                    origin = null,
                    tag = null,
                    percent = percent,
                    durationRounds = Int.MAX_VALUE,
                    skillId = XUANFENG_EQUIPMENT_CHILD_SKILL_ID,
                    effectId = 533,
                    detailId = 45103801,
                    availableHits = 1,
                ),
            ),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = context.source,
                    rootSkillId = XUANFENG_EQUIPMENT_FEATURE_SKILL_ID,
                    skillId = XUANFENG_EQUIPMENT_CHILD_SKILL_ID,
                    trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                ),
            ),
            executedSkillIds = listOf(XUANFENG_EQUIPMENT_CHILD_SKILL_ID),
            diagnostics = emptyList(),
        )
    }

    private fun jixianNormalAttackResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.trigger != BattleTrigger.NORMAL_ATTACK_AFTER) {
            return SkillExecutionResult.EMPTY
        }
        val entryTeam = if (context.source.side == Side.ATTACKER) {
            context.request.attacker
        } else {
            context.request.defender
        }
        if (
            entryTeam.heroes.size != 3 ||
            entryTeam.heroes.map { it.stats.hitRange }.distinct().size != 3
        ) {
            return SkillExecutionResult.EMPTY
        }
        val target = state.view.currentTarget(context.source)
            ?.takeIf { (state.view.state(it)?.troops ?: 0) > 0 }
            ?: return SkillExecutionResult.EMPTY
        val owners = state.view.heroes().filter {
            it.side == context.source.side &&
                JIXIAN_SKILL_ID in state.liveHero(it).skillIds
        }
        return owners.fold(SkillExecutionResult.EMPTY) { result, owner ->
            val listenerContext = context.copy(
                source = owner,
                rootSkillId = JIXIAN_SKILL_ID,
                currentSkillId = JIXIAN_DEBUFF_SKILL_ID,
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
            )
            val debuffs = JIXIAN_DEBUFF_DETAIL_IDS.fold(SkillExecutionResult.EMPTY) {
                    detailResult,
                    detailId,
                ->
                detailResult + interpreter.executeDetailForEngine(
                    detail = graph.details.single { it.detailId == detailId },
                    context = listenerContext,
                    preselectedTargets = listOf(target),
                    probabilityAlreadyAccepted = true,
                )
            }
            result + SkillExecutionResult.immutable(
                stateChanges = emptyList(),
                events = listOf(
                    SkillTriggered(
                        round = context.round,
                        source = owner,
                        rootSkillId = JIXIAN_SKILL_ID,
                        skillId = JIXIAN_TRIGGER_SKILL_ID,
                        trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                    ),
                    SkillTriggered(
                        round = context.round,
                        source = owner,
                        rootSkillId = JIXIAN_SKILL_ID,
                        skillId = JIXIAN_DEBUFF_SKILL_ID,
                        trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                    ),
                ),
                executedSkillIds = listOf(
                    JIXIAN_TRIGGER_SKILL_ID,
                    JIXIAN_DEBUFF_SKILL_ID,
                ),
                diagnostics = emptyList(),
            ) + debuffs
        }
    }

    private fun hezonglianhengNormalAttackResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.trigger != BattleTrigger.NORMAL_ATTACK_AFTER) {
            return SkillExecutionResult.EMPTY
        }
        val target = state.view.currentTarget(context.source)
            ?.takeIf { (state.view.state(it)?.troops ?: 0) > 0 }
            ?: return SkillExecutionResult.EMPTY
        val sourceCountry = state.view.metadata(context.source)?.country
            ?: return SkillExecutionResult.EMPTY
        val targetCountry = state.view.metadata(target)?.country
            ?: return SkillExecutionResult.EMPTY
        if (sourceCountry == targetCountry) return SkillExecutionResult.EMPTY

        return hezonglianhengOwners
            .asSequence()
            .filter { it.side == context.source.side }
            .fold(SkillExecutionResult.EMPTY) { result, owner ->
                val listenerContext = context.copy(
                    source = owner,
                    rootSkillId = HEZONGLIANHENG_SKILL_ID,
                    currentSkillId = HEZONGLIANHENG_SKILL_ID,
                    trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                )
                val listenerDetail = graph.details.single {
                    it.detailId == HEZONGLIANHENG_LISTENER_DETAIL_ID
                }
                if (!interpreter.detailProbabilitySucceedsForEngine(listenerDetail, listenerContext)) {
                    result
                } else {
                    val debuff = interpreter.executeDetailForEngine(
                        detail = graph.details.single {
                            it.detailId == HEZONGLIANHENG_DEBUFF_DETAIL_ID
                        },
                        context = listenerContext.copy(
                            currentSkillId = HEZONGLIANHENG_DEBUFF_SKILL_ID,
                        ),
                        preselectedTargets = listOf(target),
                        probabilityAlreadyAccepted = true,
                    )
                    result + SkillExecutionResult.immutable(
                        stateChanges = emptyList(),
                        events = listOf(
                            SkillTriggered(
                                round = context.round,
                                source = owner,
                                rootSkillId = HEZONGLIANHENG_SKILL_ID,
                                skillId = HEZONGLIANHENG_DEBUFF_SKILL_ID,
                                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                            ),
                        ),
                        executedSkillIds = listOf(HEZONGLIANHENG_DEBUFF_SKILL_ID),
                        diagnostics = emptyList(),
                    ) + debuff
                }
            }
    }

    private fun pibingjuyiRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        state.view.heroes()
            .filter { candidate ->
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                    200264 in state.liveHero(candidate).skillIds
            }
            .forEach { owner ->
                state.view.heroes()
                    .filter { candidate ->
                        candidate.side == owner.side &&
                            state.view.state(candidate)?.troops?.let { it > 0 } == true
                    }
                    .forEach { target ->
                        state.runtime.addCounter(
                            target,
                            PIBING_BIRUI_LAYER_COUNTER,
                            delta = PIBING_LAYERS_PER_ROUND,
                            maximum = PIBING_MAX_LAYERS,
                        )
                        state.runtime.recordMarker(
                            target = target,
                            detailId = 20026412,
                            value = 1,
                            appliedRound = context.round,
                            durationRounds = 8,
                            rootSkillId = 200264,
                            source = owner,
                        )
                    }
            }
        return SkillExecutionResult.EMPTY
    }

    private fun pibingjuyiDamageBeforeResult(
        change: TroopDamageChange,
        context: SkillBattleContext,
    ): PibingjuyiDamageBeforeResult {
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == change.target.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200264 in state.liveHero(candidate).skillIds
        } ?: return PibingjuyiDamageBeforeResult(change, null)
        if (state.runtime.counter(change.target, PIBING_BIRUI_LAYER_COUNTER) <= 0) {
            return PibingjuyiDamageBeforeResult(change, null)
        }
        state.runtime.addCounter(
            change.target,
            PIBING_BIRUI_LAYER_COUNTER,
            delta = -1,
            maximum = PIBING_MAX_LAYERS,
        )
        val detailId = when (change.school) {
            DamageSchool.PHYSICAL -> 21726401
            DamageSchool.STRATEGY -> 21726402
        }
        val modifier = interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == detailId },
            context = context.copy(
                source = owner,
                rootSkillId = 200264,
                currentSkillId = 217264,
                trigger = BattleTrigger.DAMAGE_BEFORE,
            ),
            preselectedTargets = listOf(change.target),
            probabilityAlreadyAccepted = true,
        ).stateChanges.filterIsInstance<DamageModifierChange>().single()
        val retainedPercent = (100 - kotlin.math.abs(modifier.percent)).coerceAtLeast(0)
        val reducedAmount = change.amount.toLong()
            .times(retainedPercent)
            .div(100)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val targetTroops = requireNotNull(state.view.state(change.target)).troops
        return PibingjuyiDamageBeforeResult(
            change = change.copy(
                amount = reducedAmount,
                troopsAfter = (targetTroops - reducedAmount).coerceAtLeast(0),
            ),
            owner = owner,
        )
    }

    private fun pibingjuyiBurnResult(
        owner: BattleHeroRef?,
        target: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        owner ?: return SkillExecutionResult.EMPTY
        if (state.view.state(target)?.troops?.let { it > 0 } != true) {
            return SkillExecutionResult.EMPTY
        }
        val burnContext = context.copy(
            source = owner,
            rootSkillId = 200264,
            currentSkillId = 211264,
            trigger = BattleTrigger.EFFECT_APPLYING,
        )
        val probabilityDetail = graph.details.single { it.detailId == 21126422 }
        if (!interpreter.detailProbabilitySucceedsForEngine(probabilityDetail, burnContext)) {
            return SkillExecutionResult.EMPTY
        }
        val base = interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == 21626411 },
            context = burnContext.copy(currentSkillId = 216264),
            preselectedTargets = listOf(target),
            probabilityAlreadyAccepted = true,
        )
        val growth = state.runtime.counter(target, PIBING_BURN_GROWTH_COUNTER)
        val boostedChanges = base.stateChanges.map { change ->
            if (change !is ScheduledDamageEffectChange) {
                change
            } else {
                val boostedPotency = change.potency.copy(
                    value = change.potency.value + growth,
                    exactValue = change.potency.exactValue + growth,
                )
                change.copy(spec = change.spec.copy(potency = boostedPotency))
            }
        }
        state.runtime.recordMarker(
            target = target,
            detailId = 20026421,
            value = 1,
            appliedRound = context.round,
            durationRounds = 8,
            rootSkillId = 200264,
            source = owner,
        )
        val growthDelta = interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == 21426401 },
            context = burnContext.copy(currentSkillId = 214264),
            preselectedTargets = listOf(target),
            probabilityAlreadyAccepted = true,
        ).stateChanges.filterIsInstance<ReferencedValueChange>().single().delta
        state.runtime.addCounter(
            target,
            PIBING_BURN_GROWTH_COUNTER,
            delta = growthDelta,
        )
        return SkillExecutionResult.immutable(
            stateChanges = boostedChanges,
            events = base.events,
            executedSkillIds = base.executedSkillIds,
            diagnostics = base.diagnostics,
            timingDues = base.timingDues,
        )
    }

    private fun huangtianDamageResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (output.amount <= 0 ||
            output.skillId != 200008 ||
            output.effectId != 306 ||
            DamageTag.ONGOING !in output.tags ||
            200008 !in state.liveHero(output.source).skillIds
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 20000802 },
            context.copy(
                source = output.source,
                rootSkillId = 200008,
                currentSkillId = 200008,
                trigger = BattleTrigger.DAMAGE_AFTER,
            ),
            preselectedTargets = listOf(output.source),
        )
    }

    private fun xianmingOngoingDamageResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (output.amount <= 0 || DamageTag.ONGOING !in output.tags) {
            return SkillExecutionResult.EMPTY
        }
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side != output.target.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200254 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        if (state.runtime.hasMarker(output.target, 21125401, context.round)) {
            return SkillExecutionResult.EMPTY
        }
        state.runtime.recordMarker(
            target = output.target,
            detailId = 21125401,
            value = 1,
            appliedRound = context.round,
            durationRounds = 1,
        )
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 21225401 },
            context.copy(
                source = owner,
                rootSkillId = 200254,
                currentSkillId = 212254,
                trigger = BattleTrigger.DAMAGE_AFTER,
            ),
            preselectedTargets = listOf(output.target),
        )
    }

    private fun jiuzhanStrategyDamageResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 ||
            output.amount <= 0 ||
            output.school != DamageSchool.STRATEGY
        ) {
            return SkillExecutionResult.EMPTY
        }
        return state.view.heroes()
            .asSequence()
            .filter { candidate ->
                candidate.side == output.source.side &&
                    candidate != output.source &&
                    state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                    JIUZHAN_SKILL_ID in state.liveHero(candidate).skillIds &&
                    output.source in jiuzhanTargets[candidate].orEmpty()
            }
            .fold(SkillExecutionResult.EMPTY) { result, owner ->
                result + interpreter.executeDetailForEngine(
                    detail = graph.details.single {
                        it.detailId == JIUZHAN_STACK_DETAIL_ID
                    },
                    context = context.copy(
                        source = owner,
                        rootSkillId = JIUZHAN_SKILL_ID,
                        currentSkillId = JIUZHAN_SKILL_ID,
                        trigger = BattleTrigger.DAMAGE_AFTER,
                    ),
                    preselectedTargets = listOf(output.source),
                    probabilityAlreadyAccepted = true,
                )
            }
    }

    private fun zhijizhibiDamageResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 || output.amount <= 0) return SkillExecutionResult.EMPTY
        val (dealtDetailId, takenDetailId) = when (output.school) {
            DamageSchool.PHYSICAL ->
                ZHIJIZHIBI_PHYSICAL_DEALT_EFFECT_DETAIL_ID to
                    ZHIJIZHIBI_PHYSICAL_TAKEN_EFFECT_DETAIL_ID
            DamageSchool.STRATEGY ->
                ZHIJIZHIBI_STRATEGY_DEALT_EFFECT_DETAIL_ID to
                    ZHIJIZHIBI_STRATEGY_TAKEN_EFFECT_DETAIL_ID
        }
        val dealt = zhijizhibiDealtTargets.entries
            .asSequence()
            .filter { (_, targets) -> output.source in targets }
            .fold(SkillExecutionResult.EMPTY) { result, (owner, _) ->
                result + interpreter.executeDetailForEngine(
                    detail = graph.details.single { it.detailId == dealtDetailId },
                    context = context.copy(
                        source = owner,
                        rootSkillId = ZHIJIZHIBI_SKILL_ID,
                        currentSkillId = dealtDetailId / 100,
                        trigger = BattleTrigger.DAMAGE_AFTER,
                    ),
                    preselectedTargets = listOf(output.source),
                )
            }
        return zhijizhibiTakenTargets.entries
            .asSequence()
            .filter { (_, targets) -> output.target in targets }
            .fold(dealt) { result, (owner, _) ->
                result + interpreter.executeDetailForEngine(
                    detail = graph.details.single { it.detailId == takenDetailId },
                    context = context.copy(
                        source = owner,
                        rootSkillId = ZHIJIZHIBI_SKILL_ID,
                        currentSkillId = takenDetailId / 100,
                        trigger = BattleTrigger.DAMAGE_AFTER,
                    ),
                    preselectedTargets = listOf(output.target),
                )
            }
    }

    private fun gongqibubeiDamageResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 || output.amount <= 0) return SkillExecutionResult.EMPTY
        return gongqibubeiTargets.entries
            .asSequence()
            .filter { (_, targets) -> output.target in targets }
            .fold(SkillExecutionResult.EMPTY) { result, (owner, _) ->
                GONGQIBUBEI_EFFECT_DETAIL_IDS.fold(result) { detailResult, detailId ->
                    detailResult + interpreter.executeDetailForEngine(
                        detail = graph.details.single { it.detailId == detailId },
                        context = context.copy(
                            source = owner,
                            rootSkillId = GONGQIBUBEI_SKILL_ID,
                            currentSkillId = GONGQIBUBEI_SKILL_ID,
                            trigger = BattleTrigger.DAMAGE_AFTER,
                        ),
                        preselectedTargets = listOf(output.target),
                    )
                }
            }
    }

    private fun fanjianDamageResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 || output.amount <= 0) return SkillExecutionResult.EMPTY
        val detailId = when (output.school) {
            DamageSchool.PHYSICAL -> FANJIAN_PHYSICAL_DETAIL_ID
            DamageSchool.STRATEGY -> FANJIAN_STRATEGY_DETAIL_ID
        }
        return fanjianTargets.entries
            .asSequence()
            .filter { (_, targets) -> output.source in targets }
            .fold(SkillExecutionResult.EMPTY) { result, (owner, _) ->
                result + interpreter.executeDetailForEngine(
                    detail = graph.details.single { it.detailId == detailId },
                    context = context.copy(
                        source = owner,
                        rootSkillId = FANJIAN_SKILL_ID,
                        currentSkillId = FANJIAN_SKILL_ID,
                        trigger = BattleTrigger.DAMAGE_AFTER,
                    ),
                    preselectedTargets = listOf(output.source),
                )
            }
    }

    private fun qixurulinStrategySplashResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 ||
            output.amount <= 0 ||
            output.school != DamageSchool.STRATEGY ||
            DamageTag.IMPERIAL_SEAL_RELEASE in output.tags ||
            output.skillId == 210282
        ) {
            return SkillExecutionResult.EMPTY
        }
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == output.source.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200282 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        val adjacent = state.view.heroes().filter { candidate ->
            candidate.side == output.target.side &&
                candidate != output.target &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                kotlin.math.abs(candidate.position - output.target.position) == 1
        }
        val ownerHero = state.liveHero(owner)
        val skillIndex = ownerHero.skillIds.indexOf(200282)
        val skillLevel = ownerHero.skillLevels.getOrElse(skillIndex) { 1 }.coerceIn(1, 10)
        val basePercent = configuredBattleRate(
            graph.details.single { it.detailId == 20028212 },
            ownerHero,
            skillLevel,
        )
        val percent = basePercent + state.runtime.referencedValueDelta(
            owner,
            200282,
            20028212,
        )
        val splashCalculation = output.calculation?.copy(
            ratePercent = output.calculation.ratePercent.toLong()
                .times(percent)
                .div(100)
                .coerceIn(1, Int.MAX_VALUE.toLong())
                .toInt(),
            ongoing = false,
            skillId = 210282,
        )
        return SkillExecutionResult.immutable(
            stateChanges = adjacent.map { target ->
                val targetTroops = requireNotNull(state.view.state(target)).troops
                val amount = (
                    splashCalculation?.calculate(
                        source = state.liveHero(output.source),
                        target = state.liveHero(target),
                        school = DamageSchool.STRATEGY,
                        origin = output.origin,
                        tags = output.tags,
                    ) ?: (output.amount * percent / 100).coerceAtLeast(1)
                    ).coerceAtMost(targetTroops)
                TroopDamageChange(
                    source = output.source,
                    target = target,
                    amount = amount,
                    troopsAfter = (targetTroops - amount).coerceAtLeast(0),
                    school = DamageSchool.STRATEGY,
                    origin = output.origin,
                    tags = output.tags,
                    skillId = 210282,
                    effectId = 302,
                    calculation = splashCalculation,
                )
            },
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = owner,
                    rootSkillId = 200282,
                    skillId = 210282,
                    trigger = BattleTrigger.DAMAGE_AFTER,
                ),
            ),
            executedSkillIds = listOf(210282),
            diagnostics = emptyList(),
        )
    }

    private fun juxianStatApplyingResult(
        change: BattleStatChange,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 || !applier.willApply(change)) return SkillExecutionResult.EMPTY
        val owner = state.view.heroes().firstOrNull { candidate ->
            state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200269 in state.liveHero(candidate).skillIds &&
                (
                    change.potency.value > 0 && candidate.side == change.target.side ||
                        change.potency.value < 0 && candidate.side != change.target.side
                    )
        } ?: return SkillExecutionResult.EMPTY
        val detailId = if (change.potency.value > 0) 21326901 else 21426901
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == detailId },
            context.copy(
                source = owner,
                rootSkillId = 200269,
                currentSkillId = detailId / 100,
                trigger = BattleTrigger.EFFECT_APPLYING,
            ),
            preselectedTargets = listOf(change.target),
        )
    }

    private fun shenshidingjiEffectApplyingResult(
        change: BattleStatChange,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 || change.potency.value >= 0) {
            return SkillExecutionResult.EMPTY
        }
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == change.source.side &&
                candidate.side != change.target.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200257 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 21025701 },
            context.copy(
                source = owner,
                rootSkillId = 200257,
                currentSkillId = 210257,
                trigger = BattleTrigger.EFFECT_APPLYING,
            ),
            preselectedTargets = listOf(change.target),
        )
    }

    private fun qiqinqizongGuardResult(
        target: BattleHeroRef,
        context: SkillBattleContext,
    ): QiqinqizongGuardResult? {
        if (context.round <= 0) return null
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == target.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200298 in state.liveHero(candidate).skillIds
        } ?: return null
        if (!state.runtime.consumeLimitedOccurrence(
                owner = owner,
                namespace = QIQIN_PROTECTED_EVENTS,
                limit = 7,
            )
        ) {
            return null
        }
        val completion =
            if (state.runtime.limitedOccurrenceCount(owner, QIQIN_PROTECTED_EVENTS) == 7) {
                qiqinqizongFinalResult(owner, context)
            } else {
                SkillExecutionResult.EMPTY
            }
        val ownerHero = state.liveHero(owner)
        val skillIndex = ownerHero.skillIds.indexOf(200298)
        val skillLevel = ownerHero.skillLevels.getOrElse(skillIndex) { 1 }.coerceIn(1, 10)
        val detail = graph.details.single { it.detailId == 21029812 }
        val probability = (
            detail.raw.probabilityInit +
                (skillLevel - 1) *
                (detail.raw.probabilityMax - detail.raw.probabilityInit) / 9.0
            ).toInt().coerceIn(0, 100)
        return QiqinqizongGuardResult(
            guarded = context.random.nextInt(100) < probability,
            completion = completion,
        )
    }

    private fun qiqinqizongFinalResult(
        owner: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val target = state.view.heroes()
            .filter { candidate ->
                candidate.side != owner.side &&
                    state.view.state(candidate)?.troops?.let { it > 0 } == true
            }
            .maxByOrNull(state.view::accumulatedDamageDealt)
            ?: return SkillExecutionResult.EMPTY
        val finalContext = context.copy(
            source = owner,
            rootSkillId = 200298,
            currentSkillId = 214298,
            trigger = BattleTrigger.EFFECT_APPLYING,
        )
        val details = if (state.view.metadata(owner)?.country == 3) {
            listOf(graph.details.single { it.detailId == 21429803 })
        } else {
            val probabilityDetail = graph.details.single { it.detailId == 21129813 }
            if (!interpreter.detailProbabilitySucceedsForEngine(
                    probabilityDetail,
                    finalContext.copy(currentSkillId = 211298),
                )
            ) {
                return SkillExecutionResult.EMPTY
            }
            listOf(21429801, 21429802).map { detailId ->
                graph.details.single { it.detailId == detailId }
            }
        }
        return details.fold(SkillExecutionResult.EMPTY) { aggregate, detail ->
            val immediate = interpreter.executeDetailForEngine(
                detail = detail,
                context = finalContext,
                preselectedTargets = listOf(target),
                probabilityAlreadyAccepted = true,
            )
            aggregate + SkillExecutionResult.immutable(
                stateChanges = immediate.stateChanges.map { change ->
                    ScheduledTimingChange(
                        snapshot = DelayedEffect(
                            source = owner,
                            rootSkillId = 200298,
                            skillId = 214298,
                            detailId = detail.detailId,
                            dueRound = 0,
                        ),
                        delayRound = 1,
                        delayHit = 0,
                        change = change,
                    )
                },
                events = emptyList(),
                executedSkillIds = emptyList(),
                diagnostics = immediate.diagnostics,
            )
        }
    }

    private fun chijieDamageBeforeResult(
        change: TroopDamageChange,
        context: SkillBattleContext,
    ): SkillExecutionResult =
        chijieSourceDamageBeforeResult(change, context) +
            chijieTargetDamageBeforeResult(change, context)

    private fun chijieSourceDamageBeforeResult(
        change: TroopDamageChange,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val qinlueruhuo = qinlueruhuoDamageBeforeResult(change, context)
        val sourceOwner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == change.source.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200989 in state.liveHero(candidate).skillIds
        } ?: return qinlueruhuo
        val detailId = when (change.school) {
            DamageSchool.PHYSICAL -> 21398901
            DamageSchool.STRATEGY -> 21498901
        }
        val atStackLimit = state.effectStore.effectsFor(change.source).any { effect ->
            effect.source == sourceOwner &&
                effect.detailId == detailId &&
                effect.stacks >= effect.maxStacks
        }
        if (atStackLimit) return qinlueruhuo
        val detail = graph.details.single { it.detailId == detailId }
        return qinlueruhuo + interpreter.executeDetailForEngine(
            detail,
            context.copy(
                source = sourceOwner,
                rootSkillId = 200989,
                currentSkillId = detailId / 100,
                trigger = BattleTrigger.DAMAGE_BEFORE,
            ),
            preselectedTargets = listOf(change.source),
            valueOverride = chijiePotency(sourceOwner, detail),
        )
    }

    private fun chijieScheduledStrategyDamageResult(
        change: ScheduledDamageEffectChange,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (change.school != DamageSchool.STRATEGY) {
            return SkillExecutionResult.EMPTY
        }
        return chijieSourceDamageBeforeResult(
            change = TroopDamageChange(
                source = change.source,
                target = change.target,
                amount = 0,
                troopsAfter = state.view.state(change.target)?.troops ?: 0,
                school = change.school,
                origin = change.origin,
                tags = change.tags,
                skillId = change.skillId,
                effectId = change.effectId,
            ),
            context = context.copy(
                source = change.source,
                trigger = BattleTrigger.DAMAGE_BEFORE,
            ),
        )
    }

    private fun qinlueruhuoDamageBeforeResult(
        change: TroopDamageChange,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            change.school != DamageSchool.PHYSICAL ||
            QINLUERUHUO_SKILL_ID !in state.liveHero(change.source).skillIds ||
            (state.view.state(change.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val listenerContext = context.copy(
            source = change.source,
            rootSkillId = QINLUERUHUO_SKILL_ID,
            currentSkillId = QINLUERUHUO_SKILL_ID,
            trigger = BattleTrigger.DAMAGE_BEFORE,
        )
        val listenerDetail = graph.details.single {
            it.detailId == QINLUERUHUO_LISTENER_DETAIL_ID
        }
        if (!interpreter.detailProbabilitySucceedsForEngine(listenerDetail, listenerContext)) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single {
                it.detailId == QINLUERUHUO_DAMAGE_DETAIL_ID
            },
            context = listenerContext.copy(currentSkillId = QINLUERUHUO_CHILD_SKILL_ID),
            preselectedTargets = listOf(change.source),
            probabilityAlreadyAccepted = true,
        )
    }

    private fun chijieTargetDamageBeforeResult(
        change: TroopDamageChange,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val targetOwner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == change.target.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200989 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        val detail = graph.details.single { it.detailId == 21598901 }
        return interpreter.executeDetailForEngine(
            detail,
            context.copy(
                source = targetOwner,
                rootSkillId = 200989,
                currentSkillId = 215989,
                trigger = BattleTrigger.DAMAGE_BEFORE,
            ),
            preselectedTargets = listOf(change.target),
            valueOverride = chijiePotency(targetOwner, detail),
        )
    }

    private fun chijiePotency(
        owner: BattleHeroRef,
        detail: SkillEffectRule,
    ): TypedBattlePotency.Resolved {
        val hero = state.liveHero(owner)
        val attribute = when (detail.coefficientSource) {
            BattleCoefficientSource.ATTACK -> hero.stats.precise(BattleStat.ATTACK)
            BattleCoefficientSource.DEFENSE -> hero.stats.precise(BattleStat.DEFENSE)
            BattleCoefficientSource.STRATEGY -> hero.stats.precise(BattleStat.STRATEGY)
            BattleCoefficientSource.SPEED -> hero.stats.precise(BattleStat.SPEED)
            BattleCoefficientSource.NONE -> 0.0
        }
        val skillIndex = hero.skillIds.indexOf(200989)
        val skillLevel = hero.skillLevels.getOrElse(skillIndex) { 1 }.coerceIn(1, 10)
        val ratio = detail.raw.initEffectRatio +
            (skillLevel - 1) * (100 - detail.raw.initEffectRatio) / 9.0
        val exactValue = (
            detail.raw.constantParam +
                detail.raw.intelParam * attribute / 200.0
            ) / 100.0 * ratio / 100.0
        return TypedBattlePotency.flat(exactValue.roundToInt(), exactValue)
    }

    private fun recalculateDirectDamage(
        change: TroopDamageChange,
    ): TroopDamageChange {
        val calculation = change.calculation ?: return change
        val targetTroops = requireNotNull(state.view.state(change.target)).troops
        val amount = calculation.calculate(
            source = change.sourceSnapshot ?: state.liveHero(change.source),
            target = state.liveHero(change.target),
            school = change.school,
            origin = change.origin,
            tags = change.tags,
            targetConditions = damageTargetConditions(change.target),
        ).coerceAtMost(targetTroops)
        return change.copy(
            amount = amount,
            troopsAfter = (targetTroops - amount).coerceAtLeast(0),
        )
    }

    private fun zhongkeDamageResult(
        output: BattleStateOutput.DamageDealt,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (output.amount <= 0 ||
            output.school != DamageSchool.PHYSICAL ||
            output.skillId == 212268 ||
            !state.runtime.hasMarker(output.target, 20026811, context.round)
        ) {
            return SkillExecutionResult.EMPTY
        }
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side != output.target.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                200268 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        if (!state.runtime.consumeLimitedOccurrence(
                owner = owner,
                namespace = "skill.200268.marked-attack",
                limit = 2,
            )
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 21226811 },
            context.copy(
                source = owner,
                rootSkillId = 200268,
                currentSkillId = 212268,
                trigger = BattleTrigger.DAMAGE_AFTER,
            ),
            preselectedTargets = listOf(output.target),
        )
    }

    private fun tianziRoundEndResult(context: SkillBattleContext): SkillExecutionResult {
        if (200270 !in state.liveHero(context.source).skillIds) return SkillExecutionResult.EMPTY
        val target = state.view.heroes().firstOrNull { candidate ->
            candidate.side != context.source.side &&
                state.runtime.hasMarker(candidate, 21027012, context.round) &&
                state.runtime.roundHurtCount(candidate, context.round) >= 2
        } ?: return SkillExecutionResult.EMPTY
        val listenerContext = context.copy(
                rootSkillId = 200270,
                currentSkillId = 212270,
                trigger = BattleTrigger.ROUND_END,
        )
        return graph.rule(212270)!!.details.fold(SkillExecutionResult.EMPTY) { result, detail ->
            result + interpreter.executeDetailForEngine(
                detail,
                listenerContext,
                preselectedTargets = listOf(target),
            )
        }
    }

    fun secondaryTarget(
        source: BattleHeroRef,
        primary: BattleHeroRef,
    ): BattleHeroRef? {
        val sourceHero = liveHero(source)
        val allies = state.view.heroes()
            .filter { it.side == source.side }
            .map(::liveHero)
        val enemies = state.view.heroes()
            .filter { it.side == primary.side }
            .map(::liveHero)
        return actionResolver.normalAttackTargetsInRange(sourceHero, enemies, allies)
            .asSequence()
            .map { it.first }
            .filter { it.position != primary.position || it.id != primary.heroId }
            .minWithOrNull(
                compareBy<BattleHero> { kotlin.math.abs(it.position - primary.position) }
                    .thenBy { it.position },
            )
            ?.let { BattleHeroRef(primary.side, it.position, it.id) }
    }

    /**
     * Ordered split-army (分兵) targets for a landed normal attack.
     *
     * Round-scoped 分兵 (200225/200956/… , effect 545 with a round lifetime) is an AoE that
     * splashes EVERY living enemy adjacent to the primary target (formation distance 1),
     * closest-to-primary first. Hit-scoped 散射 (297108, effect 545 with a hit lifetime) keeps
     * its historical single secondary against the nearest reachable enemy regardless of
     * adjacency. Sources without an active 545 effect yield no split.
     */
    fun splitAttackTargets(
        source: BattleHeroRef,
        primary: BattleHeroRef,
    ): List<BattleHeroRef> {
        val split = state.effectStore.effectsFor(source).lastOrNull { it.effectId == 545 }
            ?: return emptyList()
        val roundScoped = split.remainingRounds != null
        if (!roundScoped) {
            return listOfNotNull(secondaryTarget(source, primary))
        }
        val sourceHero = liveHero(source)
        val allies = state.view.heroes()
            .filter { it.side == source.side }
            .map(::liveHero)
        val enemies = state.view.heroes()
            .filter { it.side == primary.side }
            .map(::liveHero)
        return actionResolver.normalAttackTargetsInRange(sourceHero, enemies, allies)
            .asSequence()
            .map { it.first }
            .filter { it.position != primary.position || it.id != primary.heroId }
            .filter { kotlin.math.abs(it.position - primary.position) <= 1 }
            .sortedWith(
                compareBy<BattleHero> { kotlin.math.abs(it.position - primary.position) }
                    .thenBy { it.position },
            )
            .map { BattleHeroRef(primary.side, it.position, it.id) }
            .toList()
    }

    fun reactiveAttack(
        round: Int,
        source: BattleHeroRef,
        target: BattleHeroRef,
        effectId: Int,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        if (baseDefeated()) return emptyList()
        val effect = state.effectStore.effectsFor(source).lastOrNull { it.effectId == effectId }
            ?: return emptyList()
        var sourceHero = liveHero(source)
        var targetHero = liveHero(target)
        if (sourceHero.troops <= 0 || targetHero.troops <= 0) return emptyList()
        val allies = state.view.heroes()
            .filter { it.side == source.side }
            .map(::liveHero)
        val enemies = state.view.heroes()
            .filter { it.side == target.side }
            .map(::liveHero)
        val targetInRange = actionResolver
            .normalAttackTargetsInRange(sourceHero, enemies, allies)
            .any { (candidate, _) ->
                candidate.position == targetHero.position && candidate.id == targetHero.id
            }
        if (!targetInRange) {
            return emptyList()
        }
        val damageContext = context.copy(
            round = round,
            source = source,
            trigger = BattleTrigger.DAMAGE_BEFORE,
        )
        val events = mutableListOf<BattleEvent>()
        events += apply(
            chijieDamageBeforeResult(
                TroopDamageChange(
                    source = source,
                    target = target,
                    amount = 0,
                    troopsAfter = targetHero.troops,
                    school = DamageSchool.PHYSICAL,
                    origin = DamageOrigin.NORMAL,
                    tags = emptySet(),
                    skillId = effect.skillId,
                    effectId = effectId,
                ),
                damageContext,
            ),
            damageContext,
        )
        sourceHero = liveHero(source)
        targetHero = liveHero(target)
        val damage = BattleDamageCalculator.physical(
            source = sourceHero,
            target = targetHero,
            ratePercent = effect.effectiveStrength.coerceAtLeast(1),
            origin = DamageOrigin.NORMAL,
            targetConditions = damageTargetConditions(target),
        )
        val result = applier.apply(
            listOf(
                TroopDamageChange(
                    source,
                    target,
                    damage,
                    (targetHero.troops - damage).coerceAtLeast(0),
                    DamageSchool.PHYSICAL,
                    DamageOrigin.NORMAL,
                    emptySet(),
                    effect.skillId,
                    effectId,
                ),
            ),
            round,
        )
        events += processDamageOutputs(result, context.copy(round = round, source = source))
        events += processDamageOutputs(
            applier.consumeEffectHit(
                target = source,
                effectId = effectId,
                source = effect.source,
                detailId = effect.detailId,
            ),
            context.copy(round = round, source = source),
        )
        return events
    }

    private fun damageTargetConditions(
        target: BattleHeroRef,
    ): Set<com.stzb.battle.core.DamageTargetCondition> =
        BattleDamageCalculator.targetConditions(
            target = state.liveHero(target),
            targetTeam = state.view.heroes()
                .filter { it.side == target.side }
                .map(state::liveHero),
        )

    private fun targetActionBeforeDamageResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val changes = pendingTargetActionDamage.remove(context.source).orEmpty()
        if (changes.isEmpty()) return SkillExecutionResult.EMPTY
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    fun tryEvade(
        round: Int,
        source: BattleHeroRef,
        target: BattleHeroRef,
        context: SkillBattleContext,
    ): BattleEvent.Evaded? {
        if (!applier.canEvade(target, source, context)) return null
        state.effectStore.consumeHit(target, 514)
        state.effectStore.consumeHit(target, 714)
        return BattleEvent.Evaded(round, source, target)
    }

    fun baseDefeated(): Boolean =
        com.stzb.battle.core.Side.entries.any { side ->
            val base = state.view.heroes()
                .filter { it.side == side }
                .minByOrNull { it.position }
            base == null || requireNotNull(state.view.state(base)).troops <= 0
        }

    fun finishRound(round: Int): List<BattleEvent> {
        state.runtime.advanceReferencedValueChanges(round)
        return applier.onRoundEnd(round).toEvents(round)
    }

    private fun executeBattleSkill(
        trigger: BattleTrigger,
        context: SkillBattleContext,
        skillId: Int,
    ): SkillExecutionResult {
        if (trigger == BattleTrigger.BATTLE_PASSIVE && skillId in TIMED_TROOP_SKILL_IDS) {
            when (skillId) {
                QISHE_TROOP_SKILL_ID -> qisheOwners += context.source
                CHUQI_TROOP_SKILL_ID -> chuqiOwners += context.source
                WENTAO_TROOP_SKILL_ID -> wentaoOwners += context.source
                LIANGYUAN_TROOP_SKILL_ID -> liangyuanOwners += context.source
            }
            return SkillExecutionResult.EMPTY
        }
        if (trigger == BattleTrigger.BATTLE_PASSIVE && skillId == BUDONGRUSHAN_SKILL_ID) {
            budongrushanOwners += context.source
        }
        if (
            trigger == BattleTrigger.BATTLE_PASSIVE &&
            skillId in PASSIVE_LISTENER_REGISTRATION_SKILL_IDS ||
            trigger == BattleTrigger.BATTLE_COMMAND &&
            skillId in COMMAND_LISTENER_REGISTRATION_SKILL_IDS
        ) {
            var registration = SkillExecutionResult.EMPTY
            if (skillId == JIUZHAN_SKILL_ID) {
                val detail = graph.details.single {
                    it.detailId == JIUZHAN_STACK_DETAIL_ID
                }
                jiuzhanTargets[context.source] = skillTargetSelector
                    .compile(detail)
                    .select(context)
                    .toSet()
            }
            if (skillId == ZHIJIZHIBI_SKILL_ID) {
                val registrationContext = context.copy(
                    rootSkillId = ZHIJIZHIBI_SKILL_ID,
                    currentSkillId = ZHIJIZHIBI_SKILL_ID,
                )
                zhijizhibiDealtTargets[context.source] = skillTargetSelector
                    .compile(
                        graph.details.single {
                            it.detailId == ZHIJIZHIBI_DEALT_SELECTOR_DETAIL_ID
                        },
                    )
                    .select(registrationContext)
                    .toSet()
                zhijizhibiTakenTargets[context.source] = skillTargetSelector
                    .compile(
                        graph.details.single {
                            it.detailId == ZHIJIZHIBI_TAKEN_SELECTOR_DETAIL_ID
                        },
                    )
                    .select(registrationContext)
                    .toSet()
            }
            if (skillId == GONGQIBUBEI_SKILL_ID) {
                val registrationDetail = graph.details.single {
                    it.detailId == GONGQIBUBEI_EFFECT_DETAIL_IDS.first()
                }
                gongqibubeiTargets[context.source] = skillTargetSelector
                    .compile(registrationDetail)
                    .select(
                        context.copy(
                            rootSkillId = GONGQIBUBEI_SKILL_ID,
                            currentSkillId = GONGQIBUBEI_SKILL_ID,
                        ),
                    )
                    .toSet()
            }
            if (skillId == FANJIAN_SKILL_ID) {
                val registrationDetail = graph.details.single {
                    it.detailId == FANJIAN_PHYSICAL_DETAIL_ID
                }
                fanjianTargets[context.source] = skillTargetSelector
                    .compile(registrationDetail)
                    .select(
                        context.copy(
                            rootSkillId = FANJIAN_SKILL_ID,
                            currentSkillId = FANJIAN_SKILL_ID,
                        ),
                    )
                    .toSet()
            }
            if (skillId == QINLUERUHUO_SKILL_ID) {
                registration = QINLUERUHUO_STATIC_DETAIL_IDS.fold(
                    SkillExecutionResult.EMPTY,
                ) { result, detailId ->
                    result + interpreter.executeDetailForEngine(
                        detail = graph.details.single { it.detailId == detailId },
                        context = context.copy(
                            rootSkillId = QINLUERUHUO_SKILL_ID,
                            currentSkillId = QINLUERUHUO_SKILL_ID,
                        ),
                        probabilityAlreadyAccepted = true,
                    )
                }
            }
            if (skillId == JIUFAZHONGYUAN_SKILL_ID) {
                registration = JIUFAZHONGYUAN_ROUND_DETAIL_IDS.fold(
                    SkillExecutionResult.EMPTY,
                ) { result, detailId ->
                    result + interpreter.executeDetailForEngine(
                        detail = graph.details.single { it.detailId == detailId },
                        context = context.copy(
                            rootSkillId = JIUFAZHONGYUAN_SKILL_ID,
                            currentSkillId = JIUFAZHONGYUAN_SKILL_ID,
                        ),
                        probabilityAlreadyAccepted = true,
                    )
                }
            }
            if (skillId == MANGHOU_SKILL_ID) {
                registration = interpreter.executeDetailForEngine(
                    detail = graph.details.single {
                        it.detailId == MANGHOU_PASSIVE_DETAIL_ID
                    },
                    context = context.copy(
                        rootSkillId = MANGHOU_SKILL_ID,
                        currentSkillId = MANGHOU_SKILL_ID,
                    ),
                    probabilityAlreadyAccepted = true,
                )
            }
            if (skillId == SHESHEN_SKILL_ID) {
                registration = interpreter.executeDetailForEngine(
                    detail = graph.details.single {
                        it.detailId == SHESHEN_REDIRECTION_DETAIL_ID
                    },
                    context = context.copy(
                        rootSkillId = SHESHEN_SKILL_ID,
                        currentSkillId = SHESHEN_SKILL_ID,
                    ),
                    probabilityAlreadyAccepted = true,
                )
            }
            if (skillId == BINGWUCHANGSHI_SKILL_ID) {
                registration = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = BINGWUCHANGSHI_SKILL_ID,
                            skillId = BINGWUCHANGSHI_CHILD_SKILL_ID,
                            trigger = BattleTrigger.BATTLE_PASSIVE,
                        ),
                    ),
                    executedSkillIds = listOf(BINGWUCHANGSHI_CHILD_SKILL_ID),
                    diagnostics = emptyList(),
                )
            }
            if (skillId == JISHI_SKILL_ID) {
                registration = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = JISHI_SKILL_ID,
                            skillId = JISHI_CHILD_SKILL_ID,
                            trigger = BattleTrigger.BATTLE_PASSIVE,
                        ),
                    ),
                    executedSkillIds = listOf(JISHI_CHILD_SKILL_ID),
                    diagnostics = emptyList(),
                )
            }
            if (skillId == XILINGKEJIN_SKILL_ID) {
                registration = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = XILINGKEJIN_SKILL_ID,
                            skillId = XILINGKEJIN_CHILD_SKILL_ID,
                            trigger = BattleTrigger.BATTLE_COMMAND,
                        ),
                    ),
                    executedSkillIds = listOf(XILINGKEJIN_CHILD_SKILL_ID),
                    diagnostics = emptyList(),
                )
            }
            if (skillId == XIXIANGWUGONG_SKILL_ID) {
                val detail = graph.details.single {
                    it.detailId == XIXIANGWUGONG_REGISTRATION_DETAIL_ID
                }
                val targets = skillTargetSelector.compile(detail)
                    .select(
                        context.copy(
                            rootSkillId = XIXIANGWUGONG_SKILL_ID,
                            currentSkillId = XIXIANGWUGONG_SKILL_ID,
                        ),
                    )
                xixiangwugongTargets[context.source] = targets.toSet()
                registration = interpreter.executeDetailForEngine(
                    detail = detail,
                    context = context.copy(
                        rootSkillId = XIXIANGWUGONG_SKILL_ID,
                        currentSkillId = XIXIANGWUGONG_SKILL_ID,
                    ),
                    preselectedTargets = targets,
                    probabilityAlreadyAccepted = true,
                ) + SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = XIXIANGWUGONG_SKILL_ID,
                            skillId = XIXIANGWUGONG_CHILD_SKILL_ID,
                            trigger = BattleTrigger.BATTLE_COMMAND,
                        ),
                    ),
                    executedSkillIds = listOf(XIXIANGWUGONG_CHILD_SKILL_ID),
                    diagnostics = emptyList(),
                )
            }
            if (skillId == TONGJUNWEISHEN_SKILL_ID) {
                tongjunweishenOwners += context.source
            }
            if (skillId == SHIJI_SKILL_ID) {
                registration = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = SHIJI_CHILD_SKILL_IDS.map { childSkillId ->
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = SHIJI_SKILL_ID,
                            skillId = childSkillId,
                            trigger = BattleTrigger.BATTLE_COMMAND,
                        )
                    },
                    executedSkillIds = SHIJI_CHILD_SKILL_IDS,
                    diagnostics = emptyList(),
                )
            }
            if (skillId == FENGLINGHUSHU_SKILL_ID) {
                fenglinghushuOwners += context.source
                val listeners = state.view.heroes()
                    .filter { it.side == context.source.side && it != context.source }
                    .sortedBy(BattleHeroRef::position)
                    .map { ally ->
                        SkillTriggered(
                            round = context.round,
                            source = ally,
                            rootSkillId = FENGLINGHUSHU_SKILL_ID,
                            skillId = FENGLINGHUSHU_LISTENER_SKILL_ID,
                            trigger = BattleTrigger.BATTLE_PASSIVE,
                        )
                    }
                registration = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = FENGLINGHUSHU_SKILL_ID,
                            skillId = FENGLINGHUSHU_BUFF_SKILL_ID,
                            trigger = BattleTrigger.BATTLE_PASSIVE,
                        ),
                    ) + listeners,
                    executedSkillIds = listOf(
                        FENGLINGHUSHU_BUFF_SKILL_ID,
                        FENGLINGHUSHU_LISTENER_SKILL_ID,
                    ),
                    diagnostics = emptyList(),
                )
            }
            if (skillId == LEISHI_SKILL_ID) {
                leishiOwners += context.source
                registration = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = LEISHI_LISTENER_SKILL_IDS.map { listenerSkillId ->
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = LEISHI_SKILL_ID,
                            skillId = listenerSkillId,
                            trigger = trigger,
                        )
                    },
                    executedSkillIds = LEISHI_LISTENER_SKILL_IDS,
                    diagnostics = emptyList(),
                )
            }
            if (skillId == HUOSHOUCHONGFENG_SKILL_ID) {
                huoshouchongfengOwners += context.source
                registration = interpreter.executeDetailForEngine(
                    detail = graph.details.single {
                        it.detailId == HUOSHOUCHONGFENG_STATIC_DETAIL_ID
                    },
                    context = context.copy(
                        rootSkillId = HUOSHOUCHONGFENG_SKILL_ID,
                        currentSkillId = HUOSHOUCHONGFENG_SKILL_ID,
                    ),
                    probabilityAlreadyAccepted = true,
                ) + SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = HUOSHOUCHONGFENG_SKILL_ID,
                            skillId = HUOSHOUCHONGFENG_ROUND_ROLL_SKILL_ID,
                            trigger = trigger,
                        ),
                    ),
                    executedSkillIds = listOf(HUOSHOUCHONGFENG_ROUND_ROLL_SKILL_ID),
                    diagnostics = emptyList(),
                )
            }
            if (skillId == PANZHENSHANSHOU_SKILL_ID) {
                panzhenshanshouOwners += context.source
                registration = SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = PANZHENSHANSHOU_SKILL_ID,
                            skillId = PANZHENSHANSHOU_ROUND_SKILL_ID,
                            trigger = trigger,
                        ),
                    ),
                    executedSkillIds = listOf(PANZHENSHANSHOU_ROUND_SKILL_ID),
                    diagnostics = emptyList(),
                )
            }
            if (skillId == MOUYIHONGTU_SKILL_ID) {
                mouyihongtuOwners += context.source
                registration = MOUYIHONGTU_REDUCTION_DETAIL_IDS.fold(
                    SkillExecutionResult.EMPTY,
                ) { result, detailId ->
                    result + interpreter.executeDetailForEngine(
                        detail = graph.details.single { it.detailId == detailId },
                        context = context.copy(
                            rootSkillId = MOUYIHONGTU_SKILL_ID,
                            currentSkillId = MOUYIHONGTU_SKILL_ID,
                        ),
                        probabilityAlreadyAccepted = true,
                    )
                }
            }
            if (skillId == HEZONGLIANHENG_SKILL_ID) {
                val formation = state.view.heroes().filter {
                    it.side == context.source.side
                }
                val countries = formation.mapNotNull {
                    state.view.metadata(it)?.country
                }
                if (
                    formation.size == HEZONGLIANHENG_FORMATION_SIZE &&
                    countries.size == HEZONGLIANHENG_FORMATION_SIZE &&
                    countries.distinct().size == HEZONGLIANHENG_FORMATION_SIZE
                ) {
                    hezonglianhengOwners += context.source
                    registration = HEZONGLIANHENG_STATIC_DETAIL_IDS.fold(
                        SkillExecutionResult.EMPTY,
                    ) { result, detailId ->
                        result + interpreter.executeDetailForEngine(
                            detail = graph.details.single { it.detailId == detailId },
                            context = context.copy(
                                rootSkillId = HEZONGLIANHENG_SKILL_ID,
                                currentSkillId = HEZONGLIANHENG_SKILL_ID,
                            ),
                            probabilityAlreadyAccepted = true,
                        )
                    }
                }
            }
            if (skillId == KUIHOUXIANGTA_SKILL_ID) {
                registration = interpreter.executeDetailForEngine(
                    detail = graph.details.single {
                        it.detailId == KUIHOUXIANGTA_SPLIT_DETAIL_ID
                    },
                    context = context.copy(
                        rootSkillId = KUIHOUXIANGTA_SKILL_ID,
                        currentSkillId = KUIHOUXIANGTA_SKILL_ID,
                    ),
                    probabilityAlreadyAccepted = true,
                ) + SkillExecutionResult.immutable(
                    stateChanges = emptyList(),
                    events = listOf(
                        SkillTriggered(
                            round = context.round,
                            source = context.source,
                            rootSkillId = KUIHOUXIANGTA_SKILL_ID,
                            skillId = KUIHOUXIANGTA_CHILD_SKILL_ID,
                            trigger = BattleTrigger.BATTLE_PASSIVE,
                        ),
                    ),
                    executedSkillIds = listOf(KUIHOUXIANGTA_CHILD_SKILL_ID),
                    diagnostics = emptyList(),
                )
            }
            return registration + SkillExecutionResult.immutable(
                stateChanges = emptyList(),
                events = listOf(
                    SkillTriggered(
                        round = context.round,
                        source = context.source,
                        rootSkillId = skillId,
                        skillId = skillId,
                        trigger = trigger,
                    ),
                ),
                executedSkillIds = listOf(skillId),
                diagnostics = emptyList(),
            )
        }
        if (trigger == BattleTrigger.BATTLE_COMMAND &&
            skillId in setOf(200961, JINYANZHIJIAN_SKILL_ID)
        ) {
            return SkillExecutionResult.immutable(
                stateChanges = emptyList(),
                events = listOf(
                    SkillTriggered(
                        round = context.round,
                        source = context.source,
                        rootSkillId = skillId,
                        skillId = skillId,
                        trigger = trigger,
                    ),
                ),
                executedSkillIds = listOf(skillId),
                diagnostics = emptyList(),
            )
        }
        val skillContext = context.copy(
            rootSkillId = skillId,
            currentSkillId = skillId,
        )
        if (trigger == BattleTrigger.BATTLE_COMMAND && skillId == 200275) {
            return interpreter.execute(skillId, trigger, skillContext) +
                xinzhanLifeStealRegistrationResult(skillContext)
        }
        specialPlugins.pluginFor(skillId)?.takeIf {
            trigger == BattleTrigger.BATTLE_COMMAND
        }?.let { plugin ->
            val pluginResult = pluginTriggeredResult(skillId, trigger, skillContext, plugin)
            if (plugin.replacesConfiguredExecution) {
                return pluginResult
            }
            return interpreter.execute(skillId, trigger, skillContext) + pluginResult
        }
        return interpreter.execute(skillId, trigger, skillContext)
    }

    private fun attemptSkills(
        trigger: BattleTrigger,
        context: SkillBattleContext,
    ): SkillExecutionResult =
        skillsFor(context.source, trigger).fold(SkillExecutionResult.EMPTY) { result, skillId ->
            val attemptsBefore = activePursuitAttemptCount(context.source)
            val key = context.source to skillId
            if ((cooldownUntilRound[key] ?: 0) >= context.round) {
                result
            } else {
                val activeAttemptListeners =
                    if (trigger == BattleTrigger.ACTIVE_SKILL_ATTEMPT) {
                        suanwuyiceBeforeActiveAttemptResult(context)
                    } else {
                        SkillExecutionResult.EMPTY
                    }
                val beforeAttempt = zhongmouBeforeAttemptResult(context)
                val configuredAttempt = if (skillId == DISORDER_SKILL_ID) {
                    executeDisorder(context)
                } else {
                    timing.attempt(
                        skillId,
                        context.copy(rootSkillId = skillId, currentSkillId = skillId),
                        TimingAttemptOptions(
                            oncePerRound = trigger != BattleTrigger.PURSUIT_ATTEMPT,
                            preparationReductionRounds =
                                mouduanPreparationReduction(context, skillId),
                        ),
                    )
                }
                recordMouduanSuccessfulActivation(
                    context = context,
                    skillId = skillId,
                    result = configuredAttempt,
                )
                val attempt = when (skillId) {
                    MOUZHU_SKILL_ID ->
                        mouzhuSuccessfulAttemptResult(context, configuredAttempt)
                    SUANWUYICE_SKILL_ID ->
                        suanwuyiceSuccessfulAttemptResult(context, configuredAttempt)
                    else -> configuredAttempt
                }
                if (skillId == DISORDER_SKILL_ID && attempt.executedSkillIds.isNotEmpty()) {
                    cooldownUntilRound[key] = context.round + 3
                }
                result + activeAttemptListeners + beforeAttempt + attempt +
                    attemptThresholdResult(
                    context,
                    attemptsBefore,
                    activePursuitAttemptCount(context.source),
                )
            }
        }

    private fun mouduanPreparationReduction(
        context: SkillBattleContext,
        skillId: Int,
    ): Int =
        if (
            isMouduanPreparedInherentActive(context.source, skillId) &&
            mouduanSuccessfulActivations[context.source to skillId] == 1
        ) {
            MOUDUAN_PREPARATION_REDUCTION
        } else {
            0
        }

    private fun recordMouduanSuccessfulActivation(
        context: SkillBattleContext,
        skillId: Int,
        result: SkillExecutionResult,
    ) {
        if (!isMouduanPreparedInherentActive(context.source, skillId)) return
        val startedPreparation = result.events.any { event ->
            event is SkillPreparationStartedEvent &&
                event.snapshot.skillId == skillId
        }
        if (!startedPreparation && skillId !in result.executedSkillIds) return
        val key = context.source to skillId
        mouduanSuccessfulActivations[key] =
            (mouduanSuccessfulActivations[key] ?: 0) + 1
    }

    private fun isMouduanPreparedInherentActive(
        source: BattleHeroRef,
        skillId: Int,
    ): Boolean {
        val hero = state.liveHero(source)
        val rule = graph.rule(skillId)
        return hero.equipment.any { it.equipmentId == MOUDUAN_SKILL_ID } &&
            hero.skillIds.firstOrNull() == skillId &&
            rule?.kind == SkillKind.ACTIVE &&
            rule.prepareRounds > 0
    }

    private fun zhongmouBeforeAttemptResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (ZHONGMOU_SKILL_ID !in state.liveHero(context.source).skillIds) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == ZHONGMOU_TRIGGER_DETAIL_ID },
            context = context.copy(
                rootSkillId = ZHONGMOU_SKILL_ID,
                currentSkillId = ZHONGMOU_TRIGGER_DETAIL_ID / 100,
            ),
        )
    }

    private fun activePursuitAttemptCount(source: BattleHeroRef): Int =
        state.runtime.attemptCount(source, BattleTrigger.ACTIVE_SKILL_ATTEMPT) +
            state.runtime.attemptCount(source, BattleTrigger.PURSUIT_ATTEMPT)

    private fun attemptThresholdResult(
        context: SkillBattleContext,
        before: Int,
        after: Int,
    ): SkillExecutionResult {
        if (after <= before || 200253 !in state.liveHero(context.source).skillIds) {
            return SkillExecutionResult.EMPTY
        }
        if (!state.runtime.consumeThreshold(
                owner = context.source,
                namespace = "skill.200253.active-pursuit-attempt",
                count = after,
                threshold = 3,
            )
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            graph.details.single { it.detailId == 20025301 },
            context.copy(rootSkillId = 200253, currentSkillId = 200253),
        )
    }

    private fun zhengshiRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult =
        state.view.heroes()
            .filter { owner ->
                (state.view.state(owner)?.troops ?: 0) > 0 &&
                    ZHENGSHI_SKILL_ID in state.liveHero(owner).skillIds &&
                    state.runtime.consumeSignal(owner, ZHENGSHI_SIGNAL, context.round)
            }
            .fold(SkillExecutionResult.EMPTY) { result, owner ->
                zhengshiRetriggerRounds[owner] = context.round + 1
                result + interpreter.executeDetailForEngine(
                    graph.details.single { it.detailId == ZHENGSHI_ACTIVATION_DETAIL_ID },
                    context.copy(
                        source = owner,
                        rootSkillId = ZHENGSHI_SKILL_ID,
                        currentSkillId = ZHENGSHI_SKILL_ID,
                        trigger = BattleTrigger.ROUND_START,
                    ),
                )
            }

    private fun leishiRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            owner !in leishiOwners ||
            owner.position <= 0 ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            leishiGuardRounds[owner] == context.round
        ) {
            return SkillExecutionResult.EMPTY
        }
        leishiGuardRounds[owner] = context.round
        return interpreter.retriggerSkillForEngine(
            skillId = LEISHI_GUARD_ROLL_SKILL_ID,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            context = context.copy(
                source = owner,
                rootSkillId = LEISHI_SKILL_ID,
                currentSkillId = LEISHI_GUARD_ROLL_SKILL_ID,
                trigger = BattleTrigger.BATTLE_PASSIVE,
            ),
        )
    }

    private fun leishiNormalAttackDamageResult(
        target: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            target !in leishiOwners ||
            (state.view.state(target)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        return listOf(
            LEISHI_RECOVERY_SKILL_ID,
            LEISHI_CLEANSE_SKILL_ID,
        ).fold(SkillExecutionResult.EMPTY) { result, skillId ->
            result + interpreter.retriggerSkillForEngine(
                skillId = skillId,
                trigger = BattleTrigger.BATTLE_PASSIVE,
                context = context.copy(
                    source = target,
                    rootSkillId = LEISHI_SKILL_ID,
                    currentSkillId = skillId,
                    trigger = BattleTrigger.BATTLE_PASSIVE,
                ),
            )
        }
    }

    private fun huoshouchongfengRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            owner !in huoshouchongfengOwners ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            huoshouchongfengRounds[owner] == context.round
        ) {
            return SkillExecutionResult.EMPTY
        }
        huoshouchongfengRounds[owner] = context.round
        return interpreter.retriggerSkillForEngine(
            skillId = HUOSHOUCHONGFENG_ROUND_ROLL_SKILL_ID,
            trigger = BattleTrigger.BATTLE_PASSIVE,
            context = context.copy(
                source = owner,
                rootSkillId = HUOSHOUCHONGFENG_SKILL_ID,
                currentSkillId = HUOSHOUCHONGFENG_ROUND_ROLL_SKILL_ID,
                trigger = BattleTrigger.BATTLE_PASSIVE,
            ),
        )
    }

    private fun panzhenshanshouRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            owner !in panzhenshanshouOwners ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            panzhenshanshouRounds[owner] == context.round
        ) {
            return SkillExecutionResult.EMPTY
        }
        val activeRounds = graph.details.single {
            it.detailId == PANZHENSHANSHOU_REGISTRATION_DETAIL_ID
        }.raw.availableRounds
        if (context.round !in 1..activeRounds) return SkillExecutionResult.EMPTY
        panzhenshanshouRounds[owner] = context.round
        return interpreter.retriggerSkillForEngine(
            skillId = PANZHENSHANSHOU_ROUND_SKILL_ID,
            trigger = BattleTrigger.BATTLE_COMMAND,
            context = context.copy(
                source = owner,
                rootSkillId = PANZHENSHANSHOU_SKILL_ID,
                currentSkillId = PANZHENSHANSHOU_ROUND_SKILL_ID,
                trigger = BattleTrigger.BATTLE_COMMAND,
            ),
        )
    }

    private fun mingqixushiRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            context.round <= 0 ||
            MINGQIXUSHI_SKILL_ID !in state.liveHero(owner).skillIds ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            mingqixushiRounds[owner] == context.round
        ) {
            return SkillExecutionResult.EMPTY
        }
        mingqixushiRounds[owner] = context.round
        return interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == MINGQIXUSHI_STACK_DETAIL_ID },
            context = context.copy(
                source = owner,
                rootSkillId = MINGQIXUSHI_SKILL_ID,
                currentSkillId = MINGQIXUSHI_SKILL_ID,
                trigger = BattleTrigger.ROUND_START,
            ),
            probabilityAlreadyAccepted = true,
        )
    }

    private fun mouzhuRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        if (
            context.round <= 0 ||
            context.round > (mouzhuActiveUntilRounds[owner] ?: 0) ||
            (state.view.state(owner)?.troops ?: 0) <= 0 ||
            mouzhuRounds[owner] == context.round
        ) {
            return SkillExecutionResult.EMPTY
        }
        return mouzhuHighestTroopResult(context)
    }

    private fun mouzhuSuccessfulAttemptResult(
        context: SkillBattleContext,
        configuredAttempt: SkillExecutionResult,
    ): SkillExecutionResult {
        if (MOUZHU_SKILL_ID !in configuredAttempt.executedSkillIds) {
            return configuredAttempt
        }
        val details = MOUZHU_HIGHEST_TROOP_DETAIL_IDS.map { detailId ->
            graph.details.single { it.detailId == detailId }
        }
        val activeRounds = details.minOf { it.raw.availableRounds }
        mouzhuActiveUntilRounds[context.source] = maxOf(
            mouzhuActiveUntilRounds[context.source] ?: 0,
            context.round + activeRounds - 1,
        )
        mouzhuRounds.remove(context.source)
        return configuredAttempt.withoutMouzhuHighestTroopEffects() +
            mouzhuHighestTroopResult(context)
    }

    private fun mouzhuHighestTroopResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val owner = context.source
        val details = MOUZHU_HIGHEST_TROOP_DETAIL_IDS.map { detailId ->
            graph.details.single { it.detailId == detailId }
        }
        val ownerContext = context.copy(
            source = owner,
            rootSkillId = MOUZHU_SKILL_ID,
            currentSkillId = MOUZHU_SKILL_ID,
        )
        val target = skillTargetSelector.compile(details.first())
            .select(ownerContext)
            .singleOrNull()
            ?: return SkillExecutionResult.EMPTY
        mouzhuRounds[owner] = context.round
        val lifecycle = EffectLifecycleOverride(
            delayRound = 0,
            delayHit = 0,
            availableRounds = 1,
            availableHit = 0,
            clearPerHit = false,
        )
        return details.fold(SkillExecutionResult.EMPTY) { result, detail ->
            if (!interpreter.detailProbabilitySucceedsForEngine(detail, ownerContext)) {
                result
            } else {
                result + interpreter.executeDetailForEngine(
                    detail = detail,
                    context = ownerContext,
                    preselectedTargets = listOf(target),
                    valueOverride = TypedBattlePotency.percent(100),
                    probabilityAlreadyAccepted = true,
                    executionOverride = ReferencedDetailExecutionOverride(
                        referencedDetailId = detail.detailId,
                        lifecycleOverride = lifecycle,
                    ),
                )
            }
        }
    }

    private fun SkillExecutionResult.withoutMouzhuHighestTroopEffects(): SkillExecutionResult =
        SkillExecutionResult.immutable(
            stateChanges = stateChanges.filterNot { change ->
                val detailId = when (change) {
                    is ApplyBattleEffectChange -> change.spec.detailId
                    is ActionEffectChange -> change.spec.detailId
                    else -> null
                }
                detailId in MOUZHU_HIGHEST_TROOP_DETAIL_IDS
            },
            events = events.filterNot { event ->
                val output = (event as? BattleOutputEvent)?.event
                output is BattleEvent.StatusApplied &&
                    output.skillId == MOUZHU_SKILL_ID &&
                    output.effectId in MOUZHU_HIGHEST_TROOP_EFFECT_IDS
            },
            executedSkillIds = executedSkillIds,
            diagnostics = diagnostics,
            timingDues = timingDues,
        )

    private fun suanwuyiceSuccessfulAttemptResult(
        context: SkillBattleContext,
        configuredAttempt: SkillExecutionResult,
    ): SkillExecutionResult {
        if (SUANWUYICE_SKILL_ID !in configuredAttempt.executedSkillIds) {
            return configuredAttempt
        }
        val curseChanges = configuredAttempt.stateChanges
            .filterIsInstance<ScheduledDamageEffectChange>()
            .filter { it.spec.detailId == SUANWUYICE_CURSE_DETAIL_ID }
        curseChanges.forEach { change ->
            val key = context.source to change.spec.target
            suanwuyiceListeners[key] = maxOf(
                suanwuyiceListeners[key] ?: 0,
                context.round + change.spec.availableRounds,
            )
        }
        return SkillExecutionResult.immutable(
            stateChanges = configuredAttempt.stateChanges.filterNot { change ->
                change is ScheduledDamageEffectChange &&
                    change.spec.detailId == SUANWUYICE_CURSE_DETAIL_ID
            },
            events = configuredAttempt.events.filterNot { event ->
                val output = (event as? BattleOutputEvent)?.event
                output is BattleEvent.StatusApplied &&
                    output.skillId == SUANWUYICE_SKILL_ID &&
                    output.effectId == SUANWUYICE_CURSE_EFFECT_ID
            },
            executedSkillIds = configuredAttempt.executedSkillIds,
            diagnostics = configuredAttempt.diagnostics,
            timingDues = configuredAttempt.timingDues,
        )
    }

    private fun suanwuyiceBeforeActiveAttemptResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val matching = suanwuyiceListeners.entries
            .filter { (key, expiresAtRound) ->
                key.second == context.source && context.round < expiresAtRound
            }
        if (matching.isEmpty()) return SkillExecutionResult.EMPTY
        val detail = graph.details.single {
            it.detailId == SUANWUYICE_CURSE_DETAIL_ID
        }
        return matching.fold(SkillExecutionResult.EMPTY) { result, listener ->
            val owner = listener.key.first
            result + interpreter.executeDetailForEngine(
                detail = detail,
                context = context.copy(
                    source = owner,
                    rootSkillId = SUANWUYICE_SKILL_ID,
                    currentSkillId = SUANWUYICE_SKILL_ID,
                    trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                ),
                preselectedTargets = listOf(context.source),
                probabilityAlreadyAccepted = true,
            )
        }
    }

    private fun mouyihongtuRoundStartResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0 || mouyihongtuOwners.isEmpty()) {
            return SkillExecutionResult.EMPTY
        }
        val moraleDetail = graph.details.single {
            it.detailId == MOUYIHONGTU_MORALE_DETAIL_ID
        }
        if (context.round > moraleDetail.raw.availableRounds) {
            return SkillExecutionResult.EMPTY
        }
        return mouyihongtuOwners.fold(SkillExecutionResult.EMPTY) { result, owner ->
            if (mouyihongtuRounds[owner] == context.round) {
                return@fold result
            }
            mouyihongtuRounds[owner] = context.round
            val ownerContext = context.copy(
                source = owner,
                rootSkillId = MOUYIHONGTU_SKILL_ID,
                currentSkillId = MOUYIHONGTU_SKILL_ID,
                trigger = BattleTrigger.ROUND_START,
            )
            val morale = interpreter.executeDetailForEngine(
                detail = moraleDetail,
                context = ownerContext,
                probabilityAlreadyAccepted = true,
            )
            if (context.round == 1) {
                return@fold result + morale
            }
            val reductions = state.view.heroes()
                .asSequence()
                .filter { it.side == owner.side }
                .flatMap { target ->
                    state.effectStore.effectsFor(target).asSequence()
                        .filter { effect ->
                            effect.source == owner &&
                                effect.skillId == MOUYIHONGTU_SKILL_ID &&
                                effect.detailId in MOUYIHONGTU_REDUCTION_DETAIL_IDS
                        }
                }
                .map { effect ->
                    val stepNamespace =
                        "skill.$MOUYIHONGTU_SKILL_ID.${effect.detailId}.reduction-step"
                    val existingStep = state.runtime.counter(owner, stepNamespace)
                    val step = if (existingStep > 0) {
                        existingStep
                    } else {
                        val divisor = kotlin.math.abs(
                            graph.details.single {
                                it.detailId == effect.detailId
                            }.raw.valueAddMax,
                        ).coerceAtLeast(1)
                        (effect.effectiveStrength / divisor).coerceAtLeast(1).also { derived ->
                            state.runtime.addCounter(
                                owner = owner,
                                namespace = stepNamespace,
                                delta = derived,
                            )
                        }
                    }
                    UpdateDamageModifierStrengthChange(
                        source = effect.source,
                        target = effect.target,
                        skillId = effect.skillId,
                        detailId = effect.detailId,
                        effectId = effect.effectId,
                        percent = -(effect.effectiveStrength - step).coerceAtLeast(0),
                    )
                }
                .toList()
            result + morale + SkillExecutionResult.immutable(
                stateChanges = reductions,
                events = emptyList(),
                executedSkillIds = emptyList(),
                diagnostics = emptyList(),
            )
        }
    }

    private fun budongrushanActionResult(
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            context.source !in budongrushanOwners ||
            (state.view.state(context.source)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        return interpreter.executeDetailForEngine(
            detail = graph.details.single { it.detailId == BUDONGRUSHAN_CLEANSE_DETAIL_ID },
            context = context.copy(
                rootSkillId = BUDONGRUSHAN_SKILL_ID,
                currentSkillId = BUDONGRUSHAN_SKILL_ID,
                trigger = BattleTrigger.ACTION_BEFORE,
            ),
            probabilityAlreadyAccepted = true,
        )
    }

    private fun zhengshiRetriggerResult(
        context: SkillBattleContext,
        configuredResult: SkillExecutionResult,
    ): SkillExecutionResult {
        val skillKind = when (context.trigger) {
            BattleTrigger.ACTIVE_SKILL_ATTEMPT -> SkillKind.ACTIVE
            BattleTrigger.PURSUIT_ATTEMPT -> SkillKind.PURSUIT
            else -> return SkillExecutionResult.EMPTY
        }
        val actor = state.liveHero(context.source)
        val successfulSkillIds = configuredResult.executedSkillIds.filter { skillId ->
            skillId in actor.skillIds && graph.rule(skillId)?.kind == skillKind
        }
        if (successfulSkillIds.isEmpty()) return SkillExecutionResult.EMPTY
        val owners = zhengshiRetriggerRounds.entries
            .filter { (owner, activeRound) ->
                activeRound == context.round &&
                    owner.side == context.source.side &&
                    (state.view.state(owner)?.troops ?: 0) > 0
            }
            .map(Map.Entry<BattleHeroRef, Int>::key)
        if (owners.isEmpty()) return SkillExecutionResult.EMPTY
        val detailId = if (skillKind == SkillKind.ACTIVE) {
            ZHENGSHI_ACTIVE_RETRIGGER_DETAIL_ID
        } else {
            ZHENGSHI_PURSUIT_RETRIGGER_DETAIL_ID
        }
        val detail = graph.details.single { it.detailId == detailId }
        return owners.fold(SkillExecutionResult.EMPTY) { ownerResult, owner ->
            val ownerHero = state.liveHero(owner)
            val skillIndex = ownerHero.skillIds.indexOf(ZHENGSHI_SKILL_ID)
            val skillLevel = ownerHero.skillLevels.getOrElse(skillIndex) { 1 }.coerceIn(1, 10)
            val probability = configuredBattleRate(detail, ownerHero, skillLevel)
                .coerceIn(0, 100)
            successfulSkillIds.fold(ownerResult) { skillResult, skillId ->
                if (context.random.nextInt(100) >= probability) {
                    skillResult
                } else {
                    skillResult + interpreter.retriggerSkillForEngine(
                        skillId = skillId,
                        trigger = context.trigger,
                        context = context.copy(
                            source = context.source,
                            rootSkillId = skillId,
                            currentSkillId = ZHENGSHI_RETRIGGER_SKILL_ID,
                        ),
                    )
                }
            }
        }
    }

    private fun withSuccessfulSkillPluginResponses(
        result: SkillExecutionResult,
        context: SkillBattleContext,
    ): SkillExecutionResult =
        result.executedSkillIds
            .filter { graph.rule(it)?.kind == SkillKind.ACTIVE }
            .fold(result) { aggregate, successfulSkillId ->
                aggregate + successfulSkillPluginResponses(
                    actor = context.source,
                    successfulSkillId = successfulSkillId,
                    successfulSkillKind = SkillKind.ACTIVE,
                    context = context,
                )
            }

    private fun pluginTriggeredResult(
        skillId: Int,
        trigger: BattleTrigger,
        context: SkillBattleContext,
        plugin: SkillExecutionPlugin,
    ): SkillExecutionResult {
        val result = plugin.execute(
            SpecialSkillInvocation(
                phase = SpecialSkillPhase.BATTLE_PREPARE,
                owner = context.source,
                actor = context.source,
                context = context,
            ),
        )
        return SkillExecutionResult.immutable(
            stateChanges = result.stateChanges,
            events = listOf(
                SkillTriggered(
                    context.round,
                    context.source,
                    skillId,
                    skillId,
                    trigger,
                ),
            ) + result.events,
            executedSkillIds = listOf(skillId),
            diagnostics = result.diagnostics,
            timingDues = result.timingDues,
        )
    }

    private fun successfulSkillPluginResponses(
        actor: BattleHeroRef,
        successfulSkillId: Int,
        successfulSkillKind: SkillKind?,
        context: SkillBattleContext,
    ): SkillExecutionResult =
        state.view.heroes()
            .filter { (state.view.state(it)?.troops ?: 0) > 0 }
            .fold(SkillExecutionResult.EMPTY) ownerFold@{ aggregate, owner ->
                state.liveHero(owner).skillIds.fold(aggregate) pluginFold@{ inner, ownerSkillId ->
                    val plugin = specialPlugins.pluginFor(ownerSkillId)
                        ?: return@pluginFold inner
                    inner + plugin.execute(
                        SpecialSkillInvocation(
                            phase = SpecialSkillPhase.AFTER_SUCCESSFUL_SKILL,
                            owner = owner,
                            actor = actor,
                            successfulSkillId = successfulSkillId,
                            successfulSkillKind = successfulSkillKind,
                            context = context.copy(
                                source = owner,
                                rootSkillId = ownerSkillId,
                                currentSkillId = ownerSkillId,
                            ),
                        ),
                    )
                }
            }

    private fun executeDisorder(context: SkillBattleContext): SkillExecutionResult {
        if (!context.runtime.recordAttempt(
                context.source,
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                DISORDER_SKILL_ID,
                context.round,
            )
        ) return SkillExecutionResult.EMPTY
        val rule = requireNotNull(graph.rule(DISORDER_SKILL_ID))
        if (context.random.nextInt(100) >= rule.probability) return SkillExecutionResult.EMPTY
        context.runtime.recordSuccessfulExecution(
            context.source,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            DISORDER_SKILL_ID,
        )
        val candidates = rule.details.filter {
            it.effectId in setOf(303, 304, 305, 306, 501, 502, 503, 552, 505)
        }.distinctBy { it.effectId }
        val selected = List(3) { candidates[context.random.nextInt(candidates.size)] }
        val target = state.view.heroes()
            .filter { it.side != context.source.side && requireNotNull(state.view.state(it)).troops > 0 }
            .sortedBy { it.position }
            .firstOrNull()
        var result = SkillExecutionResult.immutable(
            emptyList(),
            listOf(
                SkillTriggered(
                    context.round,
                    context.source,
                    DISORDER_SKILL_ID,
                    DISORDER_SKILL_ID,
                    BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                ),
            ),
            listOf(DISORDER_SKILL_ID),
            emptyList(),
        )
        selected.forEach { detail ->
            if (target != null) {
                val spec = PersistentEffectSpec(
                    source = context.source,
                    target = target,
                    rootSkillId = DISORDER_SKILL_ID,
                    skillId = DISORDER_SKILL_ID,
                    skillKind = SkillKind.ACTIVE,
                    rawSkillType = 3,
                    detailId = detail.detailId,
                    effectId = detail.effectId,
                    category = com.stzb.battle.core.EffectCategory.HARMFUL,
                    conflict = detail.raw.hideConflict,
                    replaceType = detail.effectReplaceType,
                    bindFlag = detail.raw.bindFlag,
                    maxStacks = detail.raw.addCountMax + 1,
                    delayRound = 0,
                    delayHit = 0,
                    availableRounds = detail.raw.availableRounds.coerceAtLeast(1) + 1,
                    availableHit = detail.raw.availableHit,
                    clearPerHit = detail.raw.clearPerHit,
                    startBoundary = EffectStartBoundary.IMMEDIATE,
                    potency = TypedBattlePotency.rate(detail.raw.constantParam.coerceAtLeast(1)),
                )
                val change: BattleStateChange = when (detail.effectId) {
                    303, 304, 305, 306 -> ScheduledDamageEffectChange(
                        spec,
                        if (detail.effectId == 303) {
                            com.stzb.battle.core.DamageSchool.PHYSICAL
                        } else {
                            com.stzb.battle.core.DamageSchool.STRATEGY
                        },
                        com.stzb.battle.core.DamageOrigin.ACTIVE,
                        buildSet {
                            add(DamageTag.ONGOING)
                            if (detail.effectId == 305) add(DamageTag.BURN)
                        },
                        requireNotNull(statusFor(detail.effectId)),
                        detail.coefficientSource,
                        detail.raw.intelParam,
                        detail.raw.calculationTypes,
                    )
                    else -> ScheduledEffectActivationChange(
                        spec = spec,
                        status = statusForControl(detail.effectId),
                    )
                }
                result += SkillExecutionResult.immutable(
                    listOf(change),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            }
        }
        return result
    }

    private fun skillsFor(
        source: BattleHeroRef,
        trigger: BattleTrigger,
    ): List<Int> {
        val kind = when (trigger) {
            BattleTrigger.BATTLE_PASSIVE -> SkillKind.PASSIVE
            BattleTrigger.BATTLE_COMMAND -> SkillKind.COMMAND
            BattleTrigger.ACTIVE_SKILL_ATTEMPT -> SkillKind.ACTIVE
            BattleTrigger.PURSUIT_ATTEMPT -> SkillKind.PURSUIT
            else -> return emptyList()
        }
        return state.liveHero(source).skillIds.filter { graph.rule(it)?.kind == kind }
    }

    private fun apply(
        result: SkillExecutionResult,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        result.events.forEach { event ->
            when (event) {
                is SkillTriggered -> events += BattleEvent.SkillTriggered(
                    event.round,
                    event.source,
                    event.rootSkillId,
                    event.skillId,
                    event.trigger,
                )
                is BattleOutputEvent -> events += event.event
                is SkillTimingEvent.PreparationCompleted ->
                    events += BattleEvent.SkillPreparationCompleted(
                        event.completedRound,
                        event.source,
                        event.rootSkillId,
                        event.currentSkillId,
                        event.startedRound,
                        event.readyRound,
                        event.trigger,
                    )
                is SkillPreparationCancelledEvent ->
                    events += BattleEvent.SkillPreparationCancelled(
                        event.round,
                        event.snapshot.source,
                        event.snapshot.rootSkillId,
                        event.snapshot.skillId,
                        event.reason.name,
                    )
                is SkillPreparationStartedEvent -> Unit
            }
        }
        val dueChangeIndices = result.dueChangeIndexMask()
        changeLoop@ for ((changeIndex, change) in result.stateChanges.withIndex()) {
            if (dueChangeIndices[changeIndex]) continue
            when (change) {
                is ScheduledEffectActivationChange -> {
                    if (change.spec.startBoundary == EffectStartBoundary.IMMEDIATE) {
                        events += apply(timing.activate(change, context.round), context)
                    } else {
                        val position = timing.position()
                        timing.enqueue(change, context.round.coerceAtLeast(1), position.hit)
                    }
                }
                is ScheduledTimingChange -> {
                    val damage = when (val payload = change.change) {
                        is TroopDamageChange -> payload
                        is TargetActionBeforeDamageChange -> payload.change
                        else -> null
                    }
                    val scheduled = if (damage != null) {
                        events += apply(
                            chijieSourceDamageBeforeResult(damage, context),
                            context,
                        )
                        val snapshot = damage.copy(
                            sourceSnapshot = state.liveHero(damage.source),
                        )
                        change.copy(
                            change = when (val payload = change.change) {
                                is TroopDamageChange -> snapshot
                                is TargetActionBeforeDamageChange ->
                                    payload.copy(change = snapshot)
                                else -> payload
                            },
                        )
                    } else {
                        change
                    }
                    val position = timing.position()
                    timing.enqueue(scheduled, context.round.coerceAtLeast(1), position.hit)
                }
                is TargetActionBeforeDamageChange -> {
                    if (change.change.sourceSnapshot == null) {
                        events += apply(
                            chijieSourceDamageBeforeResult(change.change, context),
                            context,
                        )
                    }
                    pendingTargetActionDamage.getOrPut(
                        change.target,
                        ::mutableListOf,
                    ) += change.change.sourceSnapshot?.let { change.change }
                        ?: change.change.copy(
                            sourceSnapshot = state.liveHero(change.change.source),
                        )
                }
                is ScheduledDamageEffectChange -> {
                    events += apply(
                        chijieScheduledStrategyDamageResult(change, context),
                        context,
                    )
                    if (change.spec.startBoundary == EffectStartBoundary.AFTER_DELAY) {
                        val position = timing.position()
                        timing.enqueue(change, context.round.coerceAtLeast(1), position.hit)
                    } else {
                        events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                        events += BattleEvent.StatusApplied(
                            context.round,
                            change.source,
                            change.target,
                            change.status,
                            change.durationRounds,
                            change.potency.value,
                            skillId = change.skillId,
                            effectId = change.effectId,
                        )
                    }
                }
                is ScheduledRecoveryEffectChange ->
                    if (change.spec.startBoundary == EffectStartBoundary.AFTER_DELAY) {
                        val position = timing.position()
                        timing.enqueue(change, context.round.coerceAtLeast(1), position.hit)
                    } else {
                        events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                    }
                is SkillAttemptRejectedChange,
                is SkillPreparationRejectedChange,
                is SkillPreparationCancelledChange,
                is ExecuteChildSkillChange,
                is RetriggerSkillChange,
                is TriggerReferencedEffectChange,
                is TransformAndCastRandomActiveSkillChange,
                -> Unit
                is TriggerLastAppliedEffectChange -> {
                    change.appliedSpec?.let { appliedSpec ->
                        events += processDamageOutputs(
                            applier.triggerAppliedOngoingDamage(appliedSpec, context.round),
                            context,
                        )
                    }
                }
                is TriggerSpecifiedEffectChange -> {
                    events += processDamageOutputs(
                        applier.triggerSpecifiedOngoingDamage(
                            target = change.target,
                            effectId = change.triggeredEffectId,
                            round = context.round,
                            source = change.triggeredSource,
                            detailId = change.triggeredDetailId,
                        ),
                        context,
                    )
                }
                is MetaEffectChange -> {
                    when (change.operation) {
                        MetaEffectOperation.SKILL_RANGE_INCREASE -> {
                            val kinds = when (change.parameters.selectSkillParameter) {
                                3 -> listOf(SkillKind.ACTIVE)
                                4 -> listOf(SkillKind.PURSUIT)
                                else -> listOf(SkillKind.ACTIVE, SkillKind.PURSUIT)
                            }
                            kinds.forEach { kind ->
                                events += state.applySkillRangeChange(
                                    change,
                                    kind,
                                    change.parameters.constant,
                                    context.round,
                                )
                            }
                        }
                        MetaEffectOperation.SKILL_RANGE_DECREASE -> {
                            listOf(SkillKind.ACTIVE, SkillKind.PURSUIT).forEach { kind ->
                                events += state.applySkillRangeChange(
                                    change,
                                    kind,
                                    -change.parameters.constant,
                                    context.round,
                                )
                            }
                        }
                        else -> throw UnsupportedBattleStateChangeException(change)
                    }
                }
                is MoraleEffectChange ->
                    events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                is ModifierEffectChange ->
                    events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                is NamedFlagCounterChange ->
                    state.runtime.addCounter(
                        owner = change.target,
                        namespace = "skill.named-flag.${change.flagId}",
                        delta = change.delta,
                        maximum = change.maximum,
                    )
                is SimulatedNormalAttackChange ->
                    events += executeSimulatedNormalAttack(change, context)
                is MarkerEffectChange,
                is ReferencedExtraParameterChange,
                is ReferencedValueChange,
                -> Unit
                is DamageModifierChange ->
                    events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                is ApplyBattleEffectChange -> {
                    val durationExtension = if (statusForControl(change.spec.effectId) != null) {
                        applier.matchingControlDurationExtensions(
                            actor = change.spec.source,
                            rootSkillId = change.spec.rootSkillId,
                            skillKind = change.spec.skillKind,
                        )
                    } else {
                        ControlDurationExtensionMatch(0, emptyList())
                    }
                    val extendedChange = if (durationExtension.rounds > 0) {
                        change.copy(
                            spec = change.spec.copy(
                                availableRounds =
                                    change.spec.availableRounds + durationExtension.rounds,
                            ),
                        )
                    } else {
                        change
                    }
                    val eligibleForGuard = change.spec.category ==
                        com.stzb.battle.core.EffectCategory.HARMFUL &&
                        change.spec.effectId in QIQIN_CONTROL_EFFECT_IDS
                    val guard = if (eligibleForGuard) {
                        qiqinqizongGuardResult(change.spec.target, context)
                    } else {
                        null
                    }
                    events += apply(guard?.completion ?: SkillExecutionResult.EMPTY, context)
                    val blocked = guard?.guarded == true
                    val applied = if (blocked) {
                        EffectBlockedChange(
                            source = change.spec.source,
                            target = change.spec.target,
                            skillId = change.spec.skillId,
                            effectId = change.spec.effectId,
                            blockingEffectId = 118,
                        )
                    } else {
                        extendedChange
                    }
                    val appliedResult = applier.apply(listOf(applied), context.round)
                    events += processDamageOutputs(appliedResult, context)
                    val successfullyExtended = durationExtension.rounds > 0 &&
                        appliedResult.outputs.any { output ->
                            output is BattleStateOutput.EffectApplied &&
                                output.spec.detailId == change.spec.detailId
                        }
                    if (successfullyExtended) {
                        events += processDamageOutputs(
                            applier.consumeControlDurationExtensions(durationExtension),
                            context,
                        )
                    }
                }
                is BattleStatChange -> {
                    events += apply(
                        shenshidingjiEffectApplyingResult(change, context),
                        context,
                    )
                    events += apply(juxianStatApplyingResult(change, context), context)
                    events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                }
                is TroopDamageChange -> {
                    val guard = qiqinqizongGuardResult(change.target, context)
                    events += apply(guard?.completion ?: SkillExecutionResult.EMPTY, context)
                    if (guard?.guarded == true) {
                        events += BattleEvent.Evaded(context.round, change.source, change.target)
                        continue@changeLoop
                    }
                    events += apply(wentaoStrategyDamageBeforeResult(change, context), context)
                    val chijie = if (change.sourceSnapshot == null) {
                        chijieDamageBeforeResult(change, context)
                    } else {
                        chijieTargetDamageBeforeResult(change, context)
                    }
                    events += apply(chijie, context)
                    val recalculated = recalculateDirectDamage(change)
                    val pibing = pibingjuyiDamageBeforeResult(recalculated, context)
                    events += processDamageOutputs(
                        applier.apply(listOf(pibing.change), context.round),
                        context,
                    )
                    events += apply(
                        pibingjuyiBurnResult(pibing.owner, pibing.change.source, context),
                        context,
                    )
                }
                is ClearReferencedEffectChange -> {
                    events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                }
                is ReduceReferencedEffectUseChange,
                is ConsumeEffectUseChange,
                -> events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
                else -> events += processDamageOutputs(applier.apply(listOf(change), context.round), context)
            }
            if (baseDefeated()) break
        }
        result.timingDues.forEach { due ->
            events += processDamageOutputs(applier.applyActivated(
                due.change,
                due,
                context.round,
                timing.position().hit,
            ), context)
        }
        return events
    }

    private fun processDamageOutputs(
        result: BattleStateApplyResult,
        context: SkillBattleContext,
    ): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        result.outputs.filterIsInstance<BattleStateOutput.DamageDealt>().forEach { output ->
            events += BattleStateApplyResult(listOf(output)).toEvents(context.round)
            val damageContext = context.copy(source = output.source, trigger = BattleTrigger.DAMAGE_AFTER)
            if (output.amount > 0) {
                state.runtime.recordRoundHurt(output.target, context.round)
                recordDamageThresholds(output.source, context)
                if (isBaizhanOwner(output.source)) {
                    state.runtime.addCounter(
                        owner = output.source,
                        namespace = BAIZHAN_STACKS,
                        delta = 1,
                        maximum = BAIZHAN_MAX_STACKS,
                    )
                }
                events += apply(
                    huangtianDamageResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    xianmingOngoingDamageResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    jiuzhanStrategyDamageResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    zhijizhibiDamageResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    gongqibubeiDamageResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    fanjianDamageResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    qixurulinStrategySplashResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    zhongkeDamageResult(output, damageContext),
                    damageContext,
                )
                events += apply(
                    xinzhanDamageResult(
                        output.source,
                        output.target,
                        state.runtime.sideCount(output.source.side, BattleTrigger.DAMAGE_AFTER),
                        damageContext,
                    ),
                    damageContext,
                )
                events += apply(
                    attackDamageRecoveryResult(output),
                    damageContext,
                )
                events += apply(
                    huiyanDamageResult(
                        output.source,
                        state.runtime.sideCount(output.source.side, BattleTrigger.DAMAGE_AFTER),
                        damageContext,
                    ),
                    damageContext,
                )
            }
            events += trigger(BattleTrigger.DAMAGE_AFTER, damageContext)
            events += jingguanleizhongDamageEvents(output, damageContext)
            val hurtContext = context.copy(source = output.target, trigger = BattleTrigger.HURT_AFTER)
            val hurtCount = state.runtime.recordBattleTriggerOccurrence(
                output.target,
                BattleTrigger.HURT_AFTER,
            )
            if (output.amount > 0) {
                events += apply(
                    chuangyiHurtResult(output),
                    hurtContext,
                )
                events += apply(
                    shijiHurtResult(output.target, hurtContext),
                    hurtContext,
                )
                events += apply(
                    manwangHurtResult(output.target, hurtCount, hurtContext),
                    hurtContext,
                )
            }
            events += trigger(BattleTrigger.HURT_AFTER, hurtContext)
            if (output.amount > 0) {
                events += apply(
                    yongzhigangyiReactionResult(output, hurtContext),
                    hurtContext,
                )
                events += apply(
                    yongzhigangyiThresholdResult(output.target, hurtContext),
                    hurtContext,
                )
                events += apply(
                    xuefenduanbingHurtResult(output.target, hurtContext),
                    hurtContext,
                )
                events += apply(
                    polangHurtResult(output.target, hurtContext),
                    hurtContext,
                )
                events += apply(
                    buquHurtResult(output.target, hurtContext),
                    hurtContext,
                )
                events += apply(
                    buxieTroopThresholdResult(output.target),
                    hurtContext.copy(
                        rootSkillId = BUXIE_EQUIPMENT_FEATURE_SKILL_ID,
                        currentSkillId = BUXIE_EQUIPMENT_CHILD_SKILL_ID,
                    ),
                )
                events += apply(
                    manghouHurtResult(output.target, hurtContext),
                    hurtContext,
                )
                events += apply(
                    sheshenHurtResult(output, hurtContext),
                    hurtContext,
                )
                events += apply(
                    baizhanSpendResult(output.target, hurtContext),
                    hurtContext,
                )
                events += emergencyRecoveryEvents(output.target, hurtContext)
            }
            events += apply(timing.onHit(damageContext), damageContext)
            if (output.amount > 0) {
                events += apply(
                    tongchouHurtResult(output.target, hurtContext),
                    hurtContext,
                )
            }
        }
        result.outputs.filterIsInstance<BattleStateOutput.TroopsRecovered>()
            .filter { it.amount > 0 }
            .forEach { output ->
                events += BattleStateApplyResult(listOf(output)).toEvents(context.round)
                val registration = state.effectStore.effectsFor(output.target)
                    .firstOrNull { effect ->
                        effect.effectId == output.effectId &&
                            effect.skillId == output.skillId &&
                            effect.source == output.source
                    }
                val registrationOwner = registration?.source
                val recoveryOwner = registrationOwner ?: output.source
                val recoveryRootSkillId = registration?.rootSkillId
                    ?: context.rootSkillId.takeIf { it > 0 }
                    ?: output.skillId
                state.runtime.recordBattleTriggerOccurrence(
                    recoveryOwner,
                    BattleTrigger.RECOVERY_AFTER,
                )
                val jishiContext = context.copy(
                    source = output.source,
                    rootSkillId = JISHI_EQUIPMENT_FEATURE_SKILL_ID,
                    currentSkillId = JISHI_EQUIPMENT_CHILD_SKILL_ID,
                    trigger = BattleTrigger.RECOVERY_AFTER,
                )
                events += apply(
                    jishiRecoveryResult(output, recoveryRootSkillId, jishiContext),
                    jishiContext,
                )
                val recoveryContext = context.copy(
                    source = output.target,
                    rootSkillId = BUXIE_EQUIPMENT_FEATURE_SKILL_ID,
                    currentSkillId = BUXIE_EQUIPMENT_CHILD_SKILL_ID,
                    trigger = BattleTrigger.RECOVERY_AFTER,
                )
                events += apply(
                    buxieTroopThresholdResult(output.target),
                    recoveryContext,
                )
            }
        result.outputs.filterIsInstance<BattleStateOutput.EffectApplied>()
            .filter { context.round >= 3 }
            .forEach { output ->
                val owner = state.view.heroes().firstOrNull { candidate ->
                    candidate.side != output.spec.target.side &&
                        state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                        200254 in state.liveHero(candidate).skillIds
                }
                if (owner != null) {
                    val appliedContext = context.copy(
                        source = owner,
                        rootSkillId = 200254,
                        currentSkillId = 214254,
                        trigger = BattleTrigger.EFFECT_APPLIED,
                    )
                    val triggerResult = interpreter.executeDetailForEngine(
                            graph.details.single { it.detailId == 21425401 },
                            appliedContext,
                            preselectedTargets = listOf(output.spec.target),
                        )
                    events += apply(
                        SkillExecutionResult.immutable(
                            triggerResult.stateChanges.map { change ->
                                if (change is TriggerLastAppliedEffectChange) {
                                    change.copy(appliedSpec = output.spec)
                                } else {
                                    change
                                }
                            },
                            triggerResult.events,
                            triggerResult.executedSkillIds,
                            triggerResult.diagnostics,
                            triggerResult.timingDues,
                        ),
                        appliedContext,
                    )
                }
            }
        events += result.outputs
            .filterNot {
                it is BattleStateOutput.DamageDealt ||
                    it is BattleStateOutput.HurtReceived ||
                    it is BattleStateOutput.TroopsRecovered
            }
            .let(::BattleStateApplyResult)
            .toEvents(context.round)
        return events
    }

    private fun polangHurtResult(
        owner: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (
            state.liveHero(owner).equipment.none {
                it.equipmentId == POLANG_SKILL_ID
            } ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val changes = listOf(
            41011101 to DamageSchool.PHYSICAL,
            41011102 to DamageSchool.STRATEGY,
        ).map { (detailId, school) ->
            DamageModifierChange(
                source = owner,
                target = owner,
                direction = DamageModifierChange.Direction.DEALT,
                school = school,
                origin = null,
                tag = null,
                percent = POLANG_LAYER_PERCENT,
                durationRounds = 1,
                skillId = POLANG_CHILD_SKILL_ID,
                effectId = if (school == DamageSchool.PHYSICAL) 531 else 533,
                detailId = detailId,
                maxStacks = POLANG_MAX_LAYERS,
            )
        }
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = owner,
                    rootSkillId = POLANG_SKILL_ID,
                    skillId = POLANG_CHILD_SKILL_ID,
                    trigger = BattleTrigger.HURT_AFTER,
                ),
            ),
            executedSkillIds = listOf(POLANG_CHILD_SKILL_ID),
            diagnostics = emptyList(),
        )
    }

    private fun buquHurtResult(
        owner: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val percentPerLayer = state.liveHero(owner).modifiers
            .filterIsInstance<BattleModifier.HurtStackingDamageTakenPercent>()
            .sumOf(BattleModifier.HurtStackingDamageTakenPercent::percentPerLayer)
        if (
            percentPerLayer <= 0 ||
            (state.view.state(owner)?.troops ?: 0) <= 0
        ) {
            return SkillExecutionResult.EMPTY
        }
        val changes = listOf(
            45102001 to DamageSchool.PHYSICAL,
            45102002 to DamageSchool.STRATEGY,
        ).map { (detailId, school) ->
            DamageModifierChange(
                source = owner,
                target = owner,
                direction = DamageModifierChange.Direction.TAKEN,
                school = school,
                origin = null,
                tag = null,
                percent = -percentPerLayer,
                durationRounds = 1,
                skillId = BUQU_CHILD_SKILL_ID,
                effectId = if (school == DamageSchool.PHYSICAL) 522 else 524,
                detailId = detailId,
                maxStacks = BUQU_MAX_LAYERS,
            )
        }
        return SkillExecutionResult.immutable(
            stateChanges = changes,
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = owner,
                    rootSkillId = BUQU_FEATURE_SKILL_ID,
                    skillId = BUQU_CHILD_SKILL_ID,
                    trigger = BattleTrigger.HURT_AFTER,
                ),
            ),
            executedSkillIds = listOf(BUQU_CHILD_SKILL_ID),
            diagnostics = emptyList(),
        )
    }

    private fun buxieTroopThresholdResult(
        owner: BattleHeroRef,
    ): SkillExecutionResult {
        val marker = state.liveHero(owner).modifiers
            .filterIsInstance<BattleModifier.TroopLossRecoveryTakenPercent>()
            .singleOrNull()
            ?: return SkillExecutionResult.EMPTY
        val ownerState = requireNotNull(state.view.state(owner))
        val desiredLayers = if (ownerState.troops <= 0 || ownerState.maxTroops <= 0) {
            0
        } else {
            (1..marker.maxLayers).count { layer ->
                ownerState.troops.toLong() * 100 <
                    ownerState.maxTroops.toLong() *
                    (100 - layer * marker.troopLossPercentPerLayer)
            }
        }
        val currentLayers = state.effectStore.effectsFor(owner)
            .singleOrNull { effect ->
                effect.source == owner &&
                    effect.skillId == BUXIE_EQUIPMENT_CHILD_SKILL_ID &&
                    effect.detailId == BUXIE_EQUIPMENT_DETAIL_ID &&
                    effect.effectId == BUXIE_EQUIPMENT_EFFECT_ID
            }
            ?.stacks
            ?: 0
        if (desiredLayers == currentLayers) return SkillExecutionResult.EMPTY
        return SkillExecutionResult.immutable(
            stateChanges = listOf(
                SetRecoveryTakenModifierLayersChange(
                    source = owner,
                    target = owner,
                    percentPerLayer = marker.percentPerLayer,
                    layers = desiredLayers,
                    maxLayers = marker.maxLayers,
                    rootSkillId = BUXIE_EQUIPMENT_FEATURE_SKILL_ID,
                    skillId = BUXIE_EQUIPMENT_CHILD_SKILL_ID,
                    detailId = BUXIE_EQUIPMENT_DETAIL_ID,
                    effectId = BUXIE_EQUIPMENT_EFFECT_ID,
                ),
            ),
            events = emptyList(),
            executedSkillIds = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun jishiRecoveryResult(
        output: BattleStateOutput.TroopsRecovered,
        recoveryRootSkillId: Int,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        val percent = state.liveHero(output.source).modifiers
            .filterIsInstance<BattleModifier.MainSkillRecoveryNextDamageTakenPercent>()
            .filter { it.skillId == recoveryRootSkillId }
            .sumOf(BattleModifier.MainSkillRecoveryNextDamageTakenPercent::percent)
        if (percent <= 0) return SkillExecutionResult.EMPTY
        return SkillExecutionResult.immutable(
            stateChanges = listOf(
                DamageModifierChange(
                    source = output.source,
                    target = output.target,
                    direction = DamageModifierChange.Direction.TAKEN,
                    school = null,
                    origin = null,
                    tag = null,
                    percent = -percent,
                    durationRounds = 0,
                    skillId = JISHI_EQUIPMENT_CHILD_SKILL_ID,
                    effectId = JISHI_EQUIPMENT_EFFECT_ID,
                    detailId = JISHI_EQUIPMENT_DETAIL_ID,
                    availableHits = 1,
                    maxStacks = JISHI_MAX_LAYERS,
                ),
            ),
            events = listOf(
                SkillTriggered(
                    round = context.round,
                    source = output.source,
                    rootSkillId = JISHI_EQUIPMENT_FEATURE_SKILL_ID,
                    skillId = JISHI_EQUIPMENT_CHILD_SKILL_ID,
                    trigger = BattleTrigger.RECOVERY_AFTER,
                ),
            ),
            executedSkillIds = listOf(JISHI_EQUIPMENT_CHILD_SKILL_ID),
            diagnostics = emptyList(),
        )
    }

    private fun emergencyRecoveryEvents(
        target: BattleHeroRef,
        context: SkillBattleContext,
    ): List<BattleEvent> =
        state.effectStore.effectsFor(target)
            .filter { effect -> effect.effectId == 401 && effect.detailId > 0 }
            .flatMap { effect ->
                val detail = graph.details.singleOrNull { it.detailId == effect.detailId }
                    ?: return@flatMap emptyList()
                val increment = graph.details
                    .singleOrNull { it.detailId == 21101601 && it.raw.effectParam == detail.detailId }
                    ?.raw
                    ?.constantParam
                    ?: 0
                val successfulRolls = if (effect.skillId == 200016) {
                    state.runtime.counter(effect.source, HUANGYI_SUCCESSFUL_ROLLS)
                } else {
                    0
                }
                val configuredProbability = (
                    detail.raw.probabilityInit + successfulRolls / 3 * increment
                    ).coerceAtMost(100)
                val recoveryContext = context.copy(
                    source = effect.source,
                    rootSkillId = effect.rootSkillId,
                    currentSkillId = effect.skillId,
                    trigger = BattleTrigger.HURT_AFTER,
                )
                val probability = interpreter.moraleAdjustedProbabilityForEngine(
                    configured = configuredProbability,
                    context = recoveryContext,
                )
                if (context.random.nextInt(100) >= probability) {
                    emptyList()
                } else {
                    val recoveryEvents = apply(
                        interpreter.executeDetailForEngine(
                            detail = detail,
                            context = recoveryContext,
                            preselectedTargets = listOf(target),
                            valueOverride = TypedBattlePotency.rate(
                                effect.effectiveStrength,
                            ),
                            probabilityAlreadyAccepted = true,
                        ),
                        recoveryContext,
                    )
                    if (effect.skillId != 200016) {
                        recoveryEvents
                    } else if (recoveryEvents.none { event ->
                            event is BattleEvent.Recovery &&
                                event.skillId == 200016 &&
                                event.amount > 0
                        }
                    ) {
                        recoveryEvents
                    } else {
                        val updatedSuccesses = state.runtime.addCounter(
                            owner = effect.source,
                            namespace = HUANGYI_SUCCESSFUL_ROLLS,
                            delta = 1,
                        )
                        if (updatedSuccesses % 3 != 0) {
                            recoveryEvents
                        } else {
                            val listenerContext = recoveryContext.copy(
                                currentSkillId = 200016,
                                trigger = BattleTrigger.RECOVERY_AFTER,
                            )
                            recoveryEvents + apply(
                                interpreter.executeDetailForEngine(
                                    detail = graph.details.single {
                                        it.detailId == 20001602
                                    },
                                    context = listenerContext,
                                ),
                                listenerContext,
                            )
                        }
                    }
                }
            }

    private fun tongchouHurtResult(
        hurtTarget: BattleHeroRef,
        context: SkillBattleContext,
    ): SkillExecutionResult {
        if (context.round <= 0) return SkillExecutionResult.EMPTY
        val owner = state.view.heroes().firstOrNull { candidate ->
            candidate.side == hurtTarget.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                201006 in state.liveHero(candidate).skillIds
        } ?: return SkillExecutionResult.EMPTY
        val targets = state.view.heroes().filter { candidate ->
            candidate.side == hurtTarget.side &&
                state.view.state(candidate)?.troops?.let { it > 0 } == true &&
                kotlin.math.abs(candidate.position - hurtTarget.position) <= 1
        }
        val listenerContext = context.copy(
            source = owner,
            rootSkillId = 201006,
            currentSkillId = 201006,
            trigger = BattleTrigger.HURT_AFTER,
        )
        return listOf(20100601, 20100602).fold(SkillExecutionResult.EMPTY) { result, detailId ->
            result + interpreter.executeDetailForEngine(
                graph.details.single { it.detailId == detailId },
                listenerContext,
                preselectedTargets = targets,
            )
        }
    }

    private fun BattleStateApplyResult.toEvents(round: Int): List<BattleEvent> =
        outputs.flatMap { output ->
            when (output) {
                is BattleStateOutput.EffectApplied -> emptyList()
                is BattleStateOutput.DamageDealt -> {
                    val damageEvent: BattleEvent =
                        if (output.skillId == 0) {
                            BattleEvent.NormalAttack(
                            round,
                            output.source,
                            output.target,
                            output.amount,
                            requireNotNull(state.view.state(output.target)).troops,
                        )
                        } else if (DamageTag.ONGOING in output.tags) {
                            BattleEvent.OngoingDamage(
                            round,
                            output.source,
                            output.target,
                            statusFor(output.effectId)
                                ?: com.stzb.battle.core.BattleStatus.PANIC,
                            output.amount,
                            requireNotNull(state.view.state(output.target)).troops,
                            output.skillId,
                        )
                        } else {
                            BattleEvent.SkillDamage(
                            round,
                            output.skillId,
                            output.effectId,
                            output.source,
                            output.target,
                            output.amount,
                            requireNotNull(state.view.state(output.target)).troops,
                        )
                        }
                    listOf(
                        BattleEvent.TriggerPoint(round, output.source, BattleTrigger.DAMAGE_BEFORE),
                        damageEvent,
                    )
                }
                is BattleStateOutput.HurtReceived -> emptyList()
                is BattleStateOutput.TroopsRecovered -> listOf(
                    BattleEvent.Recovery(
                        round,
                        output.source,
                        output.target,
                        output.amount,
                        requireNotNull(state.view.state(output.target)).troops,
                        output.skillId,
                    ),
                )
                is BattleStateOutput.StatChanged -> buildList {
                    val change = output.change
                    add(output.toEvent(round))
                    statusForStat(change.effectId)?.let { status ->
                        add(
                            BattleEvent.StatusApplied(
                                round,
                                change.source,
                                change.target,
                                status,
                                change.durationRounds,
                                change.potency.value,
                                skillId = change.skillId,
                                effectId = change.effectId,
                            ),
                        )
                    }
                }
                is BattleStateOutput.ModifierApplied -> listOf(
                    output.change.let { change ->
                        BattleEvent.ModifierApplied(
                            round = round,
                            source = change.source,
                            target = change.target,
                            skillId = change.skillId,
                            effectId = change.effectId,
                            amount = change.percent,
                            durationRounds = change.durationRounds,
                        )
                    },
                )
                is BattleStateOutput.RecoveryModifierApplied -> listOf(
                    BattleEvent.ModifierApplied(
                        round = round,
                        source = output.source,
                        target = output.target,
                        skillId = output.skillId,
                        effectId = output.effectId,
                        amount = output.percent,
                        durationRounds = output.durationRounds,
                    ),
                )
                is BattleStateOutput.DamageAbsorbed -> emptyList()
                is BattleStateOutput.EffectRemoved -> listOf(
                    BattleEvent.StatusRemoved(
                        round,
                        output.effect.source,
                        output.effect.target,
                        output.effect.skillId,
                        output.effect.effectId,
                    ),
                )
                is BattleStateOutput.EffectExpired -> listOf(
                    BattleEvent.EffectExpired(
                        round,
                        output.effect.source,
                        output.effect.target,
                        output.effect.skillId,
                        output.effect.effectId,
                    ),
                )
                is BattleStateOutput.EffectBlocked -> listOf(
                    output.change.let { change ->
                        BattleEvent.EffectBlocked(
                            round,
                            change.source,
                            change.target,
                            change.skillId,
                            change.effectId,
                            change.blockingEffectId,
                        )
                    },
                )
            }
        }

    private fun BattleStateOutput.StatChanged.toEvent(round: Int): BattleEvent.StatChanged {
        val change = change
        return BattleEvent.StatChanged(
            round,
            change.source,
            change.target,
            when (change.kind) {
                BattleStatChange.Kind.ATTACK -> BattleStat.ATTACK
                BattleStatChange.Kind.DEFENSE -> BattleStat.DEFENSE
                BattleStatChange.Kind.STRATEGY -> BattleStat.STRATEGY
                BattleStatChange.Kind.SPEED -> BattleStat.SPEED
                BattleStatChange.Kind.SIEGE -> BattleStat.SIEGE
                BattleStatChange.Kind.ATTACK_RANGE -> BattleStat.HIT_RANGE
            },
            delta,
            change.durationRounds,
            change.skillId,
            change.effectId,
            strength = strength,
            valueAfter = valueAfter,
            deltaExact = deltaExact,
            valueAfterExact = valueAfterExact,
            unit = change.potency.unit,
        )
    }

    companion object {
        private const val DISORDER_SKILL_ID = 200002
        private const val QIQIN_PROTECTED_EVENTS = "skill.200298.protected-events"
        private const val FUBO_UPLIFT_COUNTER = "skill.200255.normal-damage-uplift"
        private const val ZHONGMOU_SKILL_ID = 200800
        private const val ZHONGMOU_TRIGGER_DETAIL_ID = 21080001
        private const val JIUZHAN_SKILL_ID = 200959
        private const val JIUZHAN_STACK_DETAIL_ID = 20095901
        private const val MANGHOU_SKILL_ID = 200770
        private const val MANGHOU_PASSIVE_DETAIL_ID = 20077001
        private const val MANGHOU_LISTENER_DETAIL_ID = 20077002
        private const val SHESHEN_SKILL_ID = 200993
        private const val SHESHEN_REDIRECTION_DETAIL_ID = 20099302
        private const val SHESHEN_LISTENER_DETAIL_ID = 21099301
        private const val SHESHEN_MARKER_DETAIL_ID = 21199301
        private const val SHESHEN_CLEANUP_DETAIL_ID = 21099323
        private const val SHESHEN_TRIGGER_RANGE = 2
        private const val TAOYUAN_SKILL_ID = 200784
        private const val TAOYUAN_RECOVERY_SKILL_ID = 210784
        private const val TAOYUAN_DAMAGE_SKILL_ID = 211784
        private const val TAOYUAN_RECOVERY_LAST_ROUND = 4
        private const val BINGWUCHANGSHI_SKILL_ID = 200766
        private const val BINGWUCHANGSHI_CHILD_SKILL_ID = 210766
        private val BINGWUCHANGSHI_BRANCH_DETAIL_IDS =
            listOf(21076601, 21076602, 21076603)
        private const val JISHI_SKILL_ID = 200863
        private const val JISHI_CHILD_SKILL_ID = 210863
        private val JISHI_BRANCH_DETAIL_IDS = listOf(21086301, 21086303)
        private const val ZHIJIZHIBI_SKILL_ID = 200249
        private const val ZHIJIZHIBI_DEALT_SELECTOR_DETAIL_ID = 20024901
        private const val ZHIJIZHIBI_TAKEN_SELECTOR_DETAIL_ID = 20024911
        private const val ZHIJIZHIBI_PHYSICAL_DEALT_EFFECT_DETAIL_ID = 21124901
        private const val ZHIJIZHIBI_STRATEGY_DEALT_EFFECT_DETAIL_ID = 21324901
        private const val ZHIJIZHIBI_PHYSICAL_TAKEN_EFFECT_DETAIL_ID = 21024901
        private const val ZHIJIZHIBI_STRATEGY_TAKEN_EFFECT_DETAIL_ID = 21224901
        private const val GONGQIBUBEI_SKILL_ID = 200755
        private val GONGQIBUBEI_EFFECT_DETAIL_IDS = listOf(20075501, 20075502)
        private const val FANJIAN_SKILL_ID = 200818
        private const val FANJIAN_PHYSICAL_DETAIL_ID = 20081801
        private const val FANJIAN_STRATEGY_DETAIL_ID = 20081802
        private const val QINLUERUHUO_SKILL_ID = 200034
        private const val QINLUERUHUO_CHILD_SKILL_ID = 210034
        private const val QINLUERUHUO_LISTENER_DETAIL_ID = 20003402
        private const val QINLUERUHUO_DAMAGE_DETAIL_ID = 21003401
        private val QINLUERUHUO_STATIC_DETAIL_IDS = listOf(20003401, 20003403)
        private const val FENGLINGHUSHU_SKILL_ID = 200865
        private const val FENGLINGHUSHU_BUFF_SKILL_ID = 210865
        private const val FENGLINGHUSHU_LISTENER_SKILL_ID = 211865
        private val FENGLINGHUSHU_BUFF_DETAIL_IDS =
            listOf(21086501, 21086502, 21086503)
        private const val XILINGKEJIN_SKILL_ID = 200824
        private const val XILINGKEJIN_CHILD_SKILL_ID = 210824
        private const val XILINGKEJIN_ATTACK_PROBABILITY_DETAIL_ID = 21082401
        private const val XILINGKEJIN_STRATEGY_PROBABILITY_DETAIL_ID = 21082412
        private const val XILINGKEJIN_ATTACK_SKILL_ID = 211824
        private const val XILINGKEJIN_STRATEGY_SKILL_ID = 212824
        private const val XILINGKEJIN_ATTACK_DAMAGE_DETAIL_ID = 21182401
        private const val XILINGKEJIN_ATTACK_RECOVERY_DETAIL_ID = 21182412
        private const val XILINGKEJIN_STRATEGY_DAMAGE_DETAIL_ID = 21282401
        private const val XILINGKEJIN_STRATEGY_RECOVERY_DETAIL_ID = 21282412
        private const val CHUANGYI_SKILL_ID = 200843
        private const val CHUANGYI_PHYSICAL_REDUCTION_DETAIL_ID = 20084303
        private const val CHUANGYI_STRATEGY_REDUCTION_DETAIL_ID = 20084304
        private val ROUND_STACKING_PASSIVE_DETAIL_IDS = mapOf(
            200643 to listOf(20064301),
            200644 to listOf(20064401, 20064402),
            200645 to listOf(20064501),
        )
        private const val XIXIANGWUGONG_SKILL_ID = 200791
        private const val XIXIANGWUGONG_CHILD_SKILL_ID = 210791
        private const val XIXIANGWUGONG_REGISTRATION_DETAIL_ID = 20079101
        private const val XIXIANGWUGONG_DAMAGE_DETAIL_ID = 21079101
        private const val XIXIANGWUGONG_STRATEGY_REDUCTION_DETAIL_ID = 21079112
        private const val XIXIANGWUGONG_PHYSICAL_REDUCTION_DETAIL_ID = 21079113
        private const val XIXIANGWUGONG_TRIGGER_ROUND = 2
        private const val KUIHOUXIANGTA_SKILL_ID = 200772
        private const val KUIHOUXIANGTA_CHILD_SKILL_ID = 210772
        private const val KUIHOUXIANGTA_SPLIT_DETAIL_ID = 20077201
        private const val KUIHOUXIANGTA_DAMAGE_DETAIL_ID = 21077201
        private const val SANJUNQICHU_SKILL_ID = 200956
        private const val SANJUNQICHU_TRIGGER_DETAIL_ID = 21195601
        private const val TONGJUNWEISHEN_SKILL_ID = 200915
        private const val TONGJUNWEISHEN_CHILD_SKILL_ID = 210915
        private const val TONGJUNWEISHEN_PHYSICAL_BOOST_DETAIL_ID = 21091501
        private const val TONGJUNWEISHEN_STRATEGY_BOOST_DETAIL_ID = 21091502
        private const val TONGJUNWEISHEN_DEFENSE_IGNORE_DETAIL_ID = 21091503
        private const val TONGJUNWEISHEN_STRATEGY_IGNORE_DETAIL_ID = 21091504
        private const val TONGJUNWEISHEN_ROUND_PROBABILITY_STEP = 10
        private const val QIBINGJUBEI_SKILL_ID = 200930
        private const val QIBINGJUBEI_PROBABILITY_SKILL_ID = 213930
        private const val QIBINGJUBEI_PROBABILITY_DETAIL_ID = 21393001
        private const val QIBINGJUBEI_OWNER_SKILL_ID = 210930
        private const val QIBINGJUBEI_OWNER_BASE_DETAIL_ID = 21093001
        private const val QIBINGJUBEI_OWNER_MIDDLE_DETAIL_ID = 21093012
        private const val QIBINGJUBEI_DELEGATE_SKILL_ID = 211930
        private val QIBINGJUBEI_DELEGATE_BASE_DETAIL_IDS =
            listOf(21193001, 21193002, 21193003, 21193004)
        private val QIBINGJUBEI_DELEGATE_MIDDLE_DETAIL_IDS =
            listOf(21193011, 21193012, 21193013, 21193014)
        private const val QIBINGJUBEI_DELEGATE_BRANCH_COUNT = 4
        private const val QIBINGJUBEI_PROBABILITY_COUNTER =
            "skill.200930.probability-bonus"
        private const val QIBINGJUBEI_PROBABILITY_STEP = 5
        private const val QIBINGJUBEI_MAX_PROBABILITY_BONUS = 70
        private const val HEZONGLIANHENG_SKILL_ID = 200964
        private const val HEZONGLIANHENG_LISTENER_DETAIL_ID = 20096403
        private const val HEZONGLIANHENG_DEBUFF_SKILL_ID = 220964
        private const val HEZONGLIANHENG_DEBUFF_DETAIL_ID = 22096401
        private const val HEZONGLIANHENG_FORMATION_SIZE = 3
        private val HEZONGLIANHENG_STATIC_DETAIL_IDS = listOf(20096401, 20096402)
        private const val JIXIAN_SKILL_ID = 200248
        private const val JIXIAN_TRIGGER_SKILL_ID = 211248
        private const val JIXIAN_DEBUFF_SKILL_ID = 212248
        private val JIXIAN_DEBUFF_DETAIL_IDS = listOf(21224801, 21224802)
        private const val ZHENGSHI_SKILL_ID = 200244
        private const val ZHENGSHI_ACTIVATION_LIMIT = "skill.200244.activation"
        private const val ZHENGSHI_ACTIVATION_DETAIL_ID = 20024406
        private const val ZHENGSHI_RETRIGGER_SKILL_ID = 212244
        private const val ZHENGSHI_ACTIVE_RETRIGGER_DETAIL_ID = 21224403
        private const val ZHENGSHI_PURSUIT_RETRIGGER_DETAIL_ID = 21224404
        private const val LEISHI_SKILL_ID = 200900
        private const val LEISHI_RECOVERY_SKILL_ID = 211900
        private const val LEISHI_CLEANSE_SKILL_ID = 212900
        private const val LEISHI_GUARD_ROLL_SKILL_ID = 213900
        private val LEISHI_LISTENER_SKILL_IDS =
            listOf(
                LEISHI_GUARD_ROLL_SKILL_ID,
                LEISHI_RECOVERY_SKILL_ID,
                LEISHI_CLEANSE_SKILL_ID,
            )
        private const val BUDONGRUSHAN_SKILL_ID = 200689
        private const val BUDONGRUSHAN_CLEANSE_DETAIL_ID = 20068901
        private const val HUOSHOUCHONGFENG_SKILL_ID = 200730
        private const val HUOSHOUCHONGFENG_STATIC_DETAIL_ID = 20073001
        private const val HUOSHOUCHONGFENG_ROUND_ROLL_SKILL_ID = 211730
        private const val PANZHENSHANSHOU_SKILL_ID = 200816
        private const val PANZHENSHANSHOU_REGISTRATION_DETAIL_ID = 20081601
        private const val PANZHENSHANSHOU_ROUND_SKILL_ID = 210816
        private const val MINGQIXUSHI_SKILL_ID = 200737
        private const val MINGQIXUSHI_STACK_DETAIL_ID = 20073712
        private const val MOUZHU_SKILL_ID = 200835
        private val MOUZHU_HIGHEST_TROOP_DETAIL_IDS =
            listOf(20083523, 20083524)
        private val MOUZHU_HIGHEST_TROOP_EFFECT_IDS = setOf(711, 761)
        private const val MOUYIHONGTU_SKILL_ID = 200985
        private val MOUYIHONGTU_REDUCTION_DETAIL_IDS =
            listOf(20098501, 20098502)
        private const val MOUYIHONGTU_MORALE_DETAIL_ID = 20098503
        private const val SUANWUYICE_SKILL_ID = 200011
        private const val SUANWUYICE_CURSE_DETAIL_ID = 20001103
        private const val SUANWUYICE_CURSE_EFFECT_ID = 306
        private const val QISHE_TROOP_SKILL_ID = 296106
        private const val CHUQI_TROOP_SKILL_ID = 296301
        private const val WENTAO_TROOP_SKILL_ID = 296206
        private const val WENTAO_CHILD_SKILL_ID = 297206
        private const val LIANGYUAN_TROOP_SKILL_ID = 296322
        private const val LIANGYUAN_CHILD_SKILL_ID = 297322
        private val TIMED_TROOP_SKILL_IDS =
            setOf(
                QISHE_TROOP_SKILL_ID,
                CHUQI_TROOP_SKILL_ID,
                WENTAO_TROOP_SKILL_ID,
                LIANGYUAN_TROOP_SKILL_ID,
            )
        private const val SHIJI_SKILL_ID = 200687
        private const val SHIJI_INSIGHT_SKILL_ID = 210687
        private const val SHIJI_ENEMY_DEBUFF_SKILL_ID = 211687
        private const val SHIJI_BASE_BUFF_SKILL_ID = 212687
        private const val SHIJI_INSIGHT_DETAIL_ID = 21068701
        private val SHIJI_ENEMY_DEBUFF_DETAIL_IDS = listOf(21168701, 21168702)
        private val SHIJI_BASE_BUFF_DETAIL_IDS = listOf(21268701, 21268702)
        private val SHIJI_CHILD_SKILL_IDS =
            listOf(SHIJI_INSIGHT_SKILL_ID, SHIJI_ENEMY_DEBUFF_SKILL_ID, SHIJI_BASE_BUFF_SKILL_ID)
        private const val SHIJI_ACTIVE_ROUNDS = 4
        private val PASSIVE_LISTENER_REGISTRATION_SKILL_IDS =
            setOf(
                QINLUERUHUO_SKILL_ID,
                200987,
                SHESHEN_SKILL_ID,
                BINGWUCHANGSHI_SKILL_ID,
                JISHI_SKILL_ID,
                XUEFENDUANBING_SKILL_ID,
                JINGGUANLEIZHONG_SKILL_ID,
                YONGZHIGANGYI_SKILL_ID,
                FENGLINGHUSHU_SKILL_ID,
                KUIHOUXIANGTA_SKILL_ID,
                SANJUNQICHU_SKILL_ID,
                LEISHI_SKILL_ID,
                HUOSHOUCHONGFENG_SKILL_ID,
                JIUFAZHONGYUAN_SKILL_ID,
            )
        private val COMMAND_LISTENER_REGISTRATION_SKILL_IDS =
            setOf(
                ZHONGMOU_SKILL_ID,
                JIUZHAN_SKILL_ID,
                MANGHOU_SKILL_ID,
                TAOYUAN_SKILL_ID,
                XILINGKEJIN_SKILL_ID,
                XIXIANGWUGONG_SKILL_ID,
                TONGJUNWEISHEN_SKILL_ID,
                QIBINGJUBEI_SKILL_ID,
                HEZONGLIANHENG_SKILL_ID,
                SHIJI_SKILL_ID,
                PANZHENSHANSHOU_SKILL_ID,
                MOUYIHONGTU_SKILL_ID,
                ZHIJIZHIBI_SKILL_ID,
                GONGQIBUBEI_SKILL_ID,
                FANJIAN_SKILL_ID,
            )
        private const val FUBO_LAYER_COUNTER = "skill.200255.yangsha-layers"
        private const val FUBO_LAYER_THRESHOLD = 40
        private const val FUBO_LAYERS_PER_EXTRA_ATTACK = 4
        private const val FUBO_MAX_LAYERS = 20
        private const val PIBING_BIRUI_LAYER_COUNTER = "skill.200264.birui-layers"
        private const val PIBING_BURN_GROWTH_COUNTER = "skill.200264.burn-growth"
        private const val PIBING_LAYERS_PER_ROUND = 2
        private const val PIBING_MAX_LAYERS = 99
        private val QIQIN_CONTROL_EFFECT_IDS =
            setOf(501, 502, 503, 505, 552, 701, 702, 703, 752, 901, 902, 903, 952)

        fun create(
            request: com.stzb.battle.core.BattleRequest,
            config: BattleConfigRepository,
            strict: Boolean = false,
        ): DefaultCompleteSkillEngine {
            val runtime = SkillRuntimeState()
            val history = MutableBattleHistory()
            val state = SkillBattleState(
                request,
                runtime,
                metadataProvider = { ref ->
                    config.hero(ref.heroId.value)?.let { hero ->
                        SkillBattleHeroMetadata(
                            gender = when (hero.sex) {
                                0 -> SkillHeroGender.MALE
                                1 -> SkillHeroGender.FEMALE
                                else -> SkillHeroGender.UNKNOWN
                            },
                            troopType = when (hero.heroType) {
                                1 -> SkillTroopType.CAVALRY
                                2 -> SkillTroopType.ARCHER
                                3 -> SkillTroopType.INFANTRY
                                else -> SkillTroopType.UNKNOWN
                            },
                            country = hero.country,
                        )
                    }
                },
                historyAdapter = history,
                stateFilterMatcher = { _, _, _ -> true },
            )
            state.seedInitialEffects()
            val graph = SkillRuleCatalog.build(
                SkillScope(
                    fiveStarInitialSkillIds = request.attacker.heroes.flatMap { it.skillIds }.toSet(),
                    learnableSaSkillIds = request.defender.heroes.flatMap { it.skillIds }.toSet(),
                ),
                config,
                request.skillRuleOverrides,
            )
            val diagnostics = mutableListOf<SkillExecutionDiagnostic>()
            val registry = (
                if (strict) BattleEffectRegistry.strict(graph)
                else BattleEffectRegistry.safe(graph) {}
                )
                .registerCoreEffects(state.effectStore)
                .registerControlEffects(state.effectStore)
                .registerMetaEffects()
            val interpreter = if (strict) {
                SkillRuleInterpreter(graph, registry)
            } else {
                SkillRuleInterpreter.safe(
                    graph,
                    registry,
                    conditionInterpreter = SkillConditionInterpreter(graph),
                    diagnosticSink = diagnostics::add,
                )
            }
            val timing = if (strict) {
                CompleteTimingCoordinator(graph, interpreter, runtime)
            } else {
                CompleteTimingCoordinator.safe(graph, interpreter, runtime)
            }
            val specialPlugins = ConfiguredSpecialSkillPlugins.registry(config)
            return DefaultCompleteSkillEngine(
                state,
                graph,
                interpreter,
                timing,
                BattleStateChangeApplier(state),
                specialPlugins,
            ).also { it.history = history }
        }
    }

    private lateinit var history: MutableBattleHistory
}

internal fun SkillExecutionResult.dueChangeIndexMask(): BooleanArray {
    val dueChangeIndices = BooleanArray(stateChanges.size)
    timingDues
        .flatMap { it.activatedChanges }
        .asReversed()
        .forEach { dueChange ->
            val index = stateChanges.indices.reversed().firstOrNull {
                !dueChangeIndices[it] && stateChanges[it] == dueChange
            }
            check(index != null) { "Timing due change is missing from execution result: $dueChange" }
            dueChangeIndices[index] = true
        }
    return dueChangeIndices
}

private class MutableBattleHistory : SkillBattleHistoryAdapter {
    private val current = mutableMapOf<BattleHeroRef, BattleHeroRef>()
    private val previous = mutableMapOf<BattleHeroRef, BattleHeroRef>()

    fun record(source: BattleHeroRef, target: BattleHeroRef) {
        current[source]?.let { previous[source] = it }
        current[source] = target
    }

    override fun linkedTarget(source: BattleHeroRef): BattleHeroRef? = current[source]
    override fun currentTarget(source: BattleHeroRef): BattleHeroRef? = current[source]
    override fun previousTarget(source: BattleHeroRef): BattleHeroRef? = previous[source]
}

private fun BattleTrigger.emitsPoint(): Boolean =
    this !in setOf(
        BattleTrigger.BATTLE_PASSIVE,
        BattleTrigger.BATTLE_COMMAND,
        BattleTrigger.ACTIVE_SKILL_ATTEMPT,
        BattleTrigger.PURSUIT_ATTEMPT,
    )

private fun statusFor(effectId: Int) = when (effectId) {
    303 -> com.stzb.battle.core.BattleStatus.SHAKE
    304 -> com.stzb.battle.core.BattleStatus.PANIC
    305 -> com.stzb.battle.core.BattleStatus.BURN
    306 -> com.stzb.battle.core.BattleStatus.HEX
    else -> null
}

private fun statusForControl(effectId: Int) = when (effectId) {
    501, 701, 901 -> com.stzb.battle.core.BattleStatus.CONFUSION
    502, 702, 902 -> com.stzb.battle.core.BattleStatus.HESITATION
    503, 703, 903 -> com.stzb.battle.core.BattleStatus.BERSERK
    552, 752, 952 -> com.stzb.battle.core.BattleStatus.DISARM
    else -> null
}

private fun statusForStat(effectId: Int) = when (effectId) {
    101 -> com.stzb.battle.core.BattleStatus.ATTACK_BUFF
    102 -> com.stzb.battle.core.BattleStatus.DEFENSE_BUFF
    103 -> com.stzb.battle.core.BattleStatus.STRATEGY_BUFF
    104 -> com.stzb.battle.core.BattleStatus.SPEED_BUFF
    201 -> com.stzb.battle.core.BattleStatus.ATTACK_DEBUFF
    202 -> com.stzb.battle.core.BattleStatus.DEFENSE_DEBUFF
    203 -> com.stzb.battle.core.BattleStatus.STRATEGY_DEBUFF
    204 -> com.stzb.battle.core.BattleStatus.SPEED_DEBUFF
    else -> null
}
