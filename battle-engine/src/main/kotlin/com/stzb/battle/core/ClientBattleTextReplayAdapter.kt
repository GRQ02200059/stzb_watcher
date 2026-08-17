package com.stzb.battle.core

import com.stzb.battle.core.skill.BattleTrigger
import java.util.logging.Logger

internal class UnsupportedBattleReportProjectionException(message: String) :
    IllegalArgumentException(message)

internal object ClientBattleTextReplayAdapter {
    private val logger = Logger.getLogger(ClientBattleTextReplayAdapter::class.java.name)

    fun adapt(
        result: BattleResult,
        diagnostic: (String) -> Unit = logger::warning,
    ): List<ClientReportAction> = adapt(result, strict = false, diagnostic)

    fun adaptStrict(result: BattleResult): List<ClientReportAction> =
        adapt(result, strict = true) { throw UnsupportedBattleReportProjectionException(it) }

    private fun adapt(
        result: BattleResult,
        strict: Boolean,
        diagnostic: (String) -> Unit,
    ): List<ClientReportAction> {
        val actions = mutableListOf<ClientReportAction>()
        val heroes = (
            result.attacker.heroes.map { Side.ATTACKER to it } +
                result.defender.heroes.map { Side.DEFENDER to it }
            )
            .sortedBy { (side, hero) -> ClientBattleTextReplayProtocol.position(side, hero.position) }
        val entryHeroes = (
            (result.entryAttacker ?: result.attacker).heroes.map { Side.ATTACKER to it } +
                (result.entryDefender ?: result.defender).heroes.map { Side.DEFENDER to it }
            )
            .sortedBy { (side, hero) -> ClientBattleTextReplayProtocol.position(side, hero.position) }
        heroes.forEach { (side, hero) ->
            actions += ClientReportAction(
                ClientBattleTextReplayProtocol.HERO_NAME,
                listOf(ClientBattleTextReplayProtocol.position(side, hero.position), hero.id.value),
            )
        }
        actions += ClientReportAction(ClientBattleTextReplayProtocol.INITIALIZATION_READY)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.INITIALIZATION_BEGIN)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.ATTACKER_INFO_BEGIN)
        entryHeroes.filter { it.first == Side.ATTACKER }.forEach { (side, hero) ->
            actions += heroInfo(side, hero)
        }
        actions += ClientReportAction(ClientBattleTextReplayProtocol.DEFENDER_INFO_BEGIN)
        entryHeroes.filter { it.first == Side.DEFENDER }.forEach { (side, hero) ->
            actions += heroInfo(side, hero)
        }
        actions += ClientReportAction(ClientBattleTextReplayProtocol.HERO_INFO_END)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_READY)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_BEGIN)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_RULES_BEGIN)
        val attackerEntry = result.entryAttacker ?: result.attacker
        val defenderEntry = result.entryDefender ?: result.defender
        actions += ClientReportAction(ClientBattleTextReplayProtocol.SYSTEM_STAGE_BEGIN)
        actions += preparationEffects(Side.ATTACKER, attackerEntry, BattlePreparationStage.SYSTEM)
        actions += preparationEffects(Side.DEFENDER, defenderEntry, BattlePreparationStage.SYSTEM)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.COUNTRY_STAGE_BEGIN)
        actions += preparationEffects(
            Side.ATTACKER,
            attackerEntry,
            BattlePreparationStage.ARMY,
        ) { it >= 295_000 }
        actions += preparationEffects(
            Side.DEFENDER,
            defenderEntry,
            BattlePreparationStage.ARMY,
        ) { it >= 295_000 }
        actions += ClientReportAction(ClientBattleTextReplayProtocol.COUNTRY_STAGE_END)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.ARMY_STAGE_READY)
        actions += preparationEffects(
            Side.ATTACKER,
            attackerEntry,
            BattlePreparationStage.ARMY,
        ) { it < 295_000 }
        actions += preparationEffects(
            Side.DEFENDER,
            defenderEntry,
            BattlePreparationStage.ARMY,
        ) { it < 295_000 }
        actions += ClientReportAction(ClientBattleTextReplayProtocol.TROOP_STAGE_BEGIN)
        actions += preparationEffects(Side.ATTACKER, attackerEntry, BattlePreparationStage.TROOP)
        actions += preparationEffects(Side.DEFENDER, defenderEntry, BattlePreparationStage.TROOP)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.EQUIPMENT_STAGE_BEGIN)
        actions += preparationEffects(Side.ATTACKER, attackerEntry, BattlePreparationStage.EQUIPMENT)
        actions += preparationEffects(Side.DEFENDER, defenderEntry, BattlePreparationStage.EQUIPMENT)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.SURFACE_STAGE_READY)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.SURFACE_STAGE_BEGIN)
        actions += preparationEffects(Side.ATTACKER, attackerEntry, BattlePreparationStage.SURFACE)
        actions += preparationEffects(Side.DEFENDER, defenderEntry, BattlePreparationStage.SURFACE)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.SURFACE_STAGE_END)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.PASSIVE_STAGE_BEGIN)

        fun appendEvent(event: BattleEvent) {
            when (event) {
                is BattleEvent.RoundStart -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.ROUND,
                    listOf(event.round),
                )
                is BattleEvent.HeroActionStart -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.HERO_ACTION_START,
                    listOf(ClientBattleTextReplayProtocol.position(event.source)),
                )
                is BattleEvent.HeroActionEnd -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.HERO_ACTION_END,
                    listOf(ClientBattleTextReplayProtocol.position(event.source)),
                )
                is BattleEvent.SkillPreparationStarted -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.SKILL_PREPARATION_STARTED,
                    listOf(
                        ClientBattleTextReplayProtocol.position(event.source),
                        event.skillId,
                    ),
                )
                is BattleEvent.SkillPreparationCancelled -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.SKILL_PREPARATION_CANCELLED,
                    listOf(
                        ClientBattleTextReplayProtocol.position(event.source),
                        event.skillId,
                    ),
                )
                is BattleEvent.SkillTriggered -> actions += skillTriggered(event)
                is BattleEvent.StatusRemoved -> actions += removedStatus(event)
                is BattleEvent.EffectExpired -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.STATUS_REMOVED,
                    listOf(
                        ClientBattleTextReplayProtocol.position(event.target),
                        ClientBattleTextReplayProtocol.position(event.source),
                        event.skillId,
                        event.effectId,
                    ),
                )
                is BattleEvent.EffectBlocked -> {
                    val action = blockedEffect(event)
                    if (action != null) {
                        actions += action
                    } else {
                        unsupported(
                            "Unsupported EffectBlocked projection: skill=${event.skillId} " +
                                "effect=${event.effectId} blocker=${event.blockingEffectId}",
                            strict,
                            diagnostic,
                        )
                    }
                }
                is BattleEvent.NormalAttack -> actions += listOf(
                    ClientReportAction(
                        ClientBattleTextReplayProtocol.NORMAL_ATTACK,
                        listOf(
                            ClientBattleTextReplayProtocol.position(event.source),
                            ClientBattleTextReplayProtocol.position(event.target),
                        ),
                    ),
                    ClientReportAction(ClientBattleTextReplayProtocol.SKILL_BEGIN),
                    ClientReportAction(
                        ClientBattleTextReplayProtocol.NORMAL_DAMAGE,
                        listOf(
                            ClientBattleTextReplayProtocol.position(event.target),
                            event.damage,
                            event.targetTroopsAfter,
                        ),
                    ),
                    ClientReportAction(ClientBattleTextReplayProtocol.SKILL_END),
                )
                is BattleEvent.SkillDamage -> {
                    actions += skillSegment(
                        event.source,
                        event.skillId,
                        listOf(
                            ClientReportAction(
                                ClientBattleTextReplayProtocol.SKILL_DAMAGE,
                                listOf(
                                    ClientBattleTextReplayProtocol.position(event.source),
                                    event.skillId,
                                    ClientBattleTextReplayProtocol.position(event.target),
                                    event.damage,
                                    event.targetTroopsAfter,
                                ),
                            ),
                        ),
                    )
                }
                is BattleEvent.Recovery -> {
                    if (event.skillId > 0) {
                        actions += skillSegment(
                            event.source,
                            event.skillId,
                            listOf(
                                ClientReportAction(
                                    ClientBattleTextReplayProtocol.RECOVERY,
                                    listOf(
                                        ClientBattleTextReplayProtocol.position(event.source),
                                        event.skillId,
                                        ClientBattleTextReplayProtocol.position(event.target),
                                        event.amount,
                                        event.targetTroopsAfter,
                                    ),
                                ),
                            ),
                        )
                    }
                }
                is BattleEvent.StatusApplied -> {
                    if (event.skillId > 0 && !event.status.isStatChangeStatus()) {
                        actions += if (event.round == 0) {
                            preparationStatus(event)
                        } else {
                            appliedStatusActions(event)
                        }
                    }
                }
                is BattleEvent.OngoingDamage -> {
                    if (event.skillId > 0) {
                        actions += ClientReportAction(
                            ClientBattleTextReplayProtocol.ongoingDamageAction(event.status),
                            listOf(
                                ClientBattleTextReplayProtocol.position(event.target),
                                ClientBattleTextReplayProtocol.position(event.source),
                                event.skillId,
                                event.damage,
                                event.targetTroopsAfter,
                            ),
                        )
                    }
                }
                is BattleEvent.Evaded -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.DAMAGE_EVADED,
                    listOf(ClientBattleTextReplayProtocol.position(event.target)),
                )
                is BattleEvent.StatChanged -> {
                    if (event.skillId > 0) {
                        actions += statChanged(event)
                    }
                }
                is BattleEvent.ModifierApplied -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.MODIFIER_APPLIED,
                    listOf(
                        ClientBattleTextReplayProtocol.position(event.source),
                        event.skillId,
                        ClientBattleTextReplayProtocol.position(event.target),
                        event.effectId,
                        event.amount,
                    ),
                )
                is BattleEvent.SkillRangeChanged -> actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.SKILL_RANGE_CHANGED,
                    listOf(
                        ClientBattleTextReplayProtocol.position(event.source),
                        event.skillId,
                        ClientBattleTextReplayProtocol.position(event.target),
                        event.delta,
                        event.displayRangeAfter,
                    ),
                )
                is BattleEvent.UnsupportedSkillEffect -> {
                    unsupported(
                        "Unsupported skill effect projection: skill=${event.skillId} " +
                            "effect=${event.effectId}",
                        strict,
                        diagnostic,
                    )
                }
                is BattleEvent.UnsupportedEquipmentEffect -> unsupported(
                    "Unsupported equipment effect projection: equipment=${event.equipmentId}",
                    strict,
                    diagnostic,
                )
                BattleEvent.BattleStart,
                is BattleEvent.TriggerPoint,
                is BattleEvent.SkillPreparationCompleted,
                is BattleEvent.RoundEnd,
                is BattleEvent.BattleEnd,
                -> Unit
            }
        }
        fun appendPreparationEvent(event: BattleEvent) {
            if (ClientBattlePreparationEventProjector.appliesTo(event)) {
                actions += ClientBattlePreparationEventProjector.project(event, diagnostic)
            } else {
                appendEvent(event)
            }
        }
        val firstRoundIndex = result.events.indexOfFirst { it is BattleEvent.RoundStart }
            .let { if (it < 0) result.events.size else it }
        val preparationEvents = result.events.take(firstRoundIndex)
        val battleEvents = result.events.drop(firstRoundIndex)
        val firstCommandIndex = preparationEvents.indexOfFirst {
            it is BattleEvent.SkillTriggered &&
                it.trigger == BattleTrigger.BATTLE_COMMAND &&
                it.rootSkillId == it.skillId
        }.let { if (it < 0) preparationEvents.size else it }
        preparationEvents.take(firstCommandIndex).forEach(::appendPreparationEvent)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.PASSIVE_STAGE_END)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.PREPARE)
        actions += ClientReportAction(ClientBattleTextReplayProtocol.COMMAND_STAGE_BEGIN)

        val commandEventsBySource = linkedMapOf<BattleHeroRef, MutableList<BattleEvent>>()
        var commandSource: BattleHeroRef? = null
        preparationEvents.drop(firstCommandIndex).forEach { event ->
            if (
                event is BattleEvent.SkillTriggered &&
                event.trigger == BattleTrigger.BATTLE_COMMAND &&
                event.rootSkillId == event.skillId
            ) {
                commandSource = event.source
            }
            commandSource?.let { source ->
                commandEventsBySource.getOrPut(source) { mutableListOf() } += event
            }
        }
        entryHeroes
            .sortedWith(
                compareByDescending<Pair<Side, BattleHero>> { (_, hero) -> hero.stats.speed }
                    .thenBy { (side, _) -> side.ordinal }
                    .thenBy { (_, hero) -> hero.position },
            )
            .forEach { (side, hero) ->
                val source = BattleHeroRef(side, hero.position, hero.id)
                val position = ClientBattleTextReplayProtocol.position(source)
                actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.PREPARATION_HERO,
                    listOf(position),
                )
                actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.COMMAND_HERO_BEGIN,
                    listOf(position),
                )
                commandEventsBySource[source].orEmpty().forEach(::appendPreparationEvent)
                actions += ClientReportAction(
                    ClientBattleTextReplayProtocol.COMMAND_HERO_END,
                    listOf(position),
                )
            }
        actions += ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_END)
        battleEvents.forEach(::appendEvent)
        appendFinalization(actions, result)
        return actions
    }

    private fun BattleTrigger.clientSkillAction(): Int = when (this) {
        BattleTrigger.BATTLE_PASSIVE ->
            ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PASSIVE
        BattleTrigger.BATTLE_COMMAND ->
            ClientBattleTextReplayProtocol.SKILL_TRIGGERED_COMMAND
        BattleTrigger.ACTIVE_SKILL_ATTEMPT ->
            ClientBattleTextReplayProtocol.SKILL_TRIGGERED_ACTIVE
        BattleTrigger.PURSUIT_ATTEMPT ->
            ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PURSUIT
        else -> throw UnsupportedBattleReportProjectionException(
            "SkillTriggered cannot use trigger=$this",
        )
    }

    private fun skillTriggered(event: BattleEvent.SkillTriggered): ClientReportAction =
        ClientReportAction(
            if (event.skillId != event.rootSkillId) {
                ClientBattleTextReplayProtocol.DERIVED_SKILL_TRIGGERED
            } else {
                event.trigger.clientSkillAction()
            },
            listOf(
                ClientBattleTextReplayProtocol.position(event.source),
                event.skillId,
            ),
        )

    private fun removedStatus(event: BattleEvent.StatusRemoved) = ClientReportAction(
        ClientBattleTextReplayProtocol.STATUS_REMOVED,
        listOf(
            ClientBattleTextReplayProtocol.position(event.target),
            ClientBattleTextReplayProtocol.position(event.source),
            event.skillId,
            event.effectId,
        ),
    )

    private fun blockedEffect(event: BattleEvent.EffectBlocked): ClientReportAction? {
        if (event.blockingEffectId == 207) {
            return ClientReportAction(
                ClientBattleTextReplayProtocol.EFFECT_BLOCKED,
                listOf(
                    ClientBattleTextReplayProtocol.position(event.target),
                    event.blockingEffectId,
                ),
            )
        }
        val actionId = when {
            event.blockingEffectId == ClientBattleTextReplayProtocol.effectId(BattleStatus.EVADE) ->
                ClientBattleTextReplayProtocol.DAMAGE_EVADED
            event.effectId in setOf(501, 701, 901) -> 337
            event.effectId in setOf(503, 703, 903) -> 338
            event.effectId in setOf(502, 702, 902) -> 339
            event.effectId in setOf(552, 752, 952) -> 340
            else -> return null
        }
        return ClientReportAction(
            actionId,
            if (actionId == ClientBattleTextReplayProtocol.DAMAGE_EVADED) {
                listOf(ClientBattleTextReplayProtocol.position(event.target))
            } else {
                listOf(ClientBattleTextReplayProtocol.position(event.target), event.effectId)
            },
        )
    }

    private fun unsupported(
        message: String,
        strict: Boolean,
        diagnostic: (String) -> Unit,
    ) {
        if (strict) throw UnsupportedBattleReportProjectionException(message)
        diagnostic(message)
    }

    /**
     * Client BattleAnimationData.SetRoundData action 205 layout:
     * position, level, initialTroops, 3 * (skillId, skillLevel),
     * heroTypeFeatureId1, heroTypeFeatureId2.
     *
     * ReportDetailView.GetHeroShareInfo always reads both feature slots, so
     * they must be present even when the server has no feature data.
     */
    private fun heroInfo(side: Side, hero: BattleHero): ClientReportAction {
        val skills = hero.skillIds.take(SKILL_SLOT_COUNT)
            .flatMapIndexed { index, skillId ->
                listOf(skillId, hero.skillLevels.getOrElse(index) { DEFAULT_SKILL_LEVEL })
            }
            .toMutableList()
            .apply {
                while (size < SKILL_SLOT_COUNT * 2) {
                    add(0)
                    add(0)
                }
            }
        val troopFeatures = hero.troopFeatureIds.take(TROOP_FEATURE_SLOT_COUNT)
            .toMutableList()
            .apply {
                while (size < TROOP_FEATURE_SLOT_COUNT) add(0)
            }
        val equipment = hero.equipment.take(EQUIPMENT_SLOT_COUNT)
            .flatMap { slot -> listOf(slot.equipmentId, slot.level) }
            .toMutableList()
            .apply {
                while (size < EQUIPMENT_SLOT_COUNT * 2) {
                    add(0)
                    add(0)
                }
            }
        return ClientReportAction(
            ClientBattleTextReplayProtocol.HERO_INFO,
            listOf(
                ClientBattleTextReplayProtocol.position(side, hero.position),
                hero.level,
                hero.maxTroops,
            ) + skills + troopFeatures + listOf("") + equipment + listOf(0),
        )
    }

    private fun preparationEffects(
        side: Side,
        team: BattleTeam,
        stage: BattlePreparationStage? = null,
        sourceFilter: (Int) -> Boolean = { true },
    ): List<ClientReportAction> {
        val effectsBySource = team.preparationEffects
            .filter { (stage == null || it.stage == stage) && sourceFilter(it.sourceId) }
            .groupBy { Triple(it.stage, it.containerSourceId, it.sourcePosition) }
        val modifiersBySource = team.preparationModifiers
            .filter { (stage == null || it.stage == stage) && sourceFilter(it.sourceId) }
            .groupBy { Triple(it.stage, it.containerSourceId, it.sourcePosition) }
        val actionsBySource = team.preparationActions
            .filter { (stage == null || it.stage == stage) && sourceFilter(it.sourceId) }
            .groupBy { Triple(it.stage, it.containerSourceId, it.sourcePosition) }
        val surfaceSources = if (stage == null || stage == BattlePreparationStage.SURFACE) {
            team.heroes.mapNotNull { hero ->
                hero.surfaceSkillId.takeIf { it > 0 }?.let { surfaceSkillId ->
                    BattlePreparationSource(
                        stage = BattlePreparationStage.SURFACE,
                        sourceId = surfaceSkillId,
                        sourcePosition = hero.position,
                    )
                }
            }
        } else {
            emptyList()
        }
        val sources = (
            team.preparationSources.filter {
                (stage == null || it.stage == stage) && sourceFilter(it.sourceId)
            } +
                surfaceSources +
                effectsBySource.keys.map { (stage, sourceId, sourcePosition) ->
                    BattlePreparationSource(stage, sourceId, sourcePosition)
                } +
                modifiersBySource.keys.map { (stage, sourceId, sourcePosition) ->
                    BattlePreparationSource(stage, sourceId, sourcePosition)
                } +
                actionsBySource.keys.map { (stage, sourceId, sourcePosition) ->
                    BattlePreparationSource(stage, sourceId, sourcePosition)
                }
            ).distinct()
            .sortedWith(
                compareBy<BattlePreparationSource> { it.stage.ordinal }
                    .thenBy { source ->
                        if (source.stage == BattlePreparationStage.SURFACE) {
                            source.sourcePosition
                                ?.let { ClientBattleTextReplayProtocol.position(side, it) }
                                ?: ClientBattleTextReplayProtocol.teamPosition(side)
                        } else {
                            source.sourcePosition ?: -1
                        }
                    }
                    .thenBy(BattlePreparationSource::sourceId),
            )
        return sources.flatMap { source ->
            val effects = effectsBySource[
                Triple(source.stage, source.sourceId, source.sourcePosition)
            ].orEmpty()
            val modifiers = modifiersBySource[
                Triple(source.stage, source.sourceId, source.sourcePosition)
            ].orEmpty()
            val preparationActions = actionsBySource[
                Triple(source.stage, source.sourceId, source.sourcePosition)
            ].orEmpty()
            buildList {
                add(
                    ClientReportAction(
                        ClientBattleTextReplayProtocol.preparationSourceAction(source.stage),
                        listOf(
                            source.sourcePosition
                                ?.let { ClientBattleTextReplayProtocol.position(side, it) }
                                ?: ClientBattleTextReplayProtocol.teamPosition(side),
                            source.sourceId,
                        ),
                    ),
                )
                val payload = buildList {
                    effects.mapTo(this) { effect ->
                        val sourcePosition = effect.sourcePosition
                            ?.let { ClientBattleTextReplayProtocol.position(side, it) }
                            ?: ClientBattleTextReplayProtocol.teamPosition(side)
                        if (effect.percent) {
                            ClientReportAction(
                                ClientBattleTextReplayProtocol.attributeChangeAction(effect.stat, effect.delta),
                                listOf(
                                    sourcePosition,
                                    effect.sourceId,
                                    ClientBattleTextReplayProtocol.position(side, effect.targetPosition),
                                    reportNumber(effect.strengthExact),
                                    reportNumber(kotlin.math.abs(effect.deltaExact)),
                                    reportNumber(effect.valueAfterExact),
                                ),
                            )
                        } else {
                            ClientReportAction(
                                when (effect.sourceId) {
                                    296_133 -> "17".toInt(36)
                                    296_241 -> "15".toInt(36)
                                    else -> ClientBattleTextReplayProtocol.flatAttributeAction(effect.stat)
                                },
                                listOf(
                                    sourcePosition,
                                    effect.sourceId,
                                    ClientBattleTextReplayProtocol.position(side, effect.targetPosition),
                                    reportNumber(kotlin.math.abs(effect.deltaExact)),
                                    reportNumber(effect.valueAfterExact),
                                ),
                            )
                        }
                    }
                    modifiers.mapTo(this) { modifier ->
                        ClientReportAction(
                            ClientBattleTextReplayProtocol.MODIFIER_APPLIED,
                            listOf(
                                ClientBattleTextReplayProtocol.position(side, modifier.sourcePosition),
                                modifier.sourceId,
                                ClientBattleTextReplayProtocol.position(side, modifier.targetPosition),
                                modifier.effectId,
                                modifier.amount,
                            ),
                        )
                    }
                    preparationActions.mapTo(this) { action ->
                        ClientReportAction(
                            action.actionId,
                            buildList {
                                val sourcePosition =
                                    ClientBattleTextReplayProtocol.position(side, action.sourcePosition)
                                add(sourcePosition)
                                if (action.compactStatusAction) {
                                    action.actionParameter?.let(::add)
                                } else {
                                    add(action.sourceId)
                                    add(ClientBattleTextReplayProtocol.position(side, action.targetPosition))
                                    action.actionParameter?.let(::add)
                                    action.amountExact?.let { add(reportNumber(it)) }
                                    if (action.appendSourcePosition) add(sourcePosition)
                                }
                            },
                        )
                    }
                }
                if (source.stage == BattlePreparationStage.SURFACE) {
                    if (payload.isEmpty()) {
                        add(ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BEGIN))
                        add(ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_END))
                    } else {
                        payload.forEach { action ->
                            add(ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BEGIN))
                            add(action)
                            add(ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_END))
                        }
                    }
                } else {
                    add(ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BEGIN))
                    addAll(payload)
                    add(ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_END))
                }
                add(ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BOUNDARY))
            }
        }
    }

    private fun reportNumber(value: Double): Any =
        roundOneDecimal(value).let { rounded ->
            if (rounded % 1.0 == 0.0) rounded.toInt() else rounded
        }

    private fun roundOneDecimal(value: Double): Double =
        java.math.BigDecimal.valueOf(value)
            .setScale(1, java.math.RoundingMode.HALF_UP)
            .toDouble()

    private fun statChanged(event: BattleEvent.StatChanged): ClientReportAction {
        val source = ClientBattleTextReplayProtocol.position(event.source)
        val target = ClientBattleTextReplayProtocol.position(event.target)
        return ClientReportAction(
            ClientBattleTextReplayProtocol.attributeChangeAction(event.stat, event.delta),
            listOf(
                source,
                event.skillId,
                target,
                event.strength,
                reportNumber(kotlin.math.abs(event.deltaExact)),
                reportNumber(event.valueAfterExact ?: event.valueAfter?.toDouble() ?: event.deltaExact),
            ),
        )
    }

    private fun skillCast(source: BattleHeroRef, skillId: Int): List<ClientReportAction> =
        if (skillId > 0) {
            listOf(
                ClientReportAction(
                    ClientBattleTextReplayProtocol.SKILL_CAST,
                    listOf(
                        ClientBattleTextReplayProtocol.position(source),
                        ClientBattleTextReplayProtocol.position(source),
                        skillId,
                    ),
                ),
            )
        } else {
            emptyList()
        }

    private fun skillSegment(
        source: BattleHeroRef,
        skillId: Int,
        effects: List<ClientReportAction>,
    ): List<ClientReportAction> =
        if (skillId > 0) {
            listOf(ClientReportAction(ClientBattleTextReplayProtocol.SKILL_BEGIN)) +
                skillCast(source, skillId) +
                effects +
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_END)
        } else {
            emptyList()
        }

    private fun appliedStatusActions(event: BattleEvent.StatusApplied): List<ClientReportAction> {
        val actionId = ClientBattleTextReplayProtocol.statusAppliedAction(event.status)
        val action = if (actionId == ClientBattleTextReplayProtocol.SKILL_CAST) {
            ClientReportAction(
                actionId,
                listOf(
                    ClientBattleTextReplayProtocol.position(event.target),
                    ClientBattleTextReplayProtocol.position(event.source),
                    event.skillId,
                ),
            )
        } else {
            ClientReportAction(
                actionId,
                listOf(
                    ClientBattleTextReplayProtocol.position(event.source),
                    event.skillId,
                    ClientBattleTextReplayProtocol.position(event.target),
                ),
            )
        }
        return skillSegment(event.source, event.skillId, listOf(action))
    }

    private fun preparationStatus(event: BattleEvent.StatusApplied): List<ClientReportAction> =
        listOf(
            ClientReportAction(
                ClientBattleTextReplayProtocol.PREPARATION_STATUS_APPLIED,
                listOf(
                    ClientBattleTextReplayProtocol.position(event.target),
                    event.effectId ?: ClientBattleTextReplayProtocol.effectId(event.status),
                ),
            ),
        )

    private fun BattleStatus.isStatChangeStatus(): Boolean =
        this in setOf(
            BattleStatus.ATTACK_BUFF,
            BattleStatus.DEFENSE_BUFF,
            BattleStatus.STRATEGY_BUFF,
            BattleStatus.SPEED_BUFF,
            BattleStatus.ATTACK_DEBUFF,
            BattleStatus.DEFENSE_DEBUFF,
            BattleStatus.STRATEGY_DEBUFF,
            BattleStatus.SPEED_DEBUFF,
        )

    private fun appendFinalization(
        actions: MutableList<ClientReportAction>,
        result: BattleResult,
    ) {
        actions += ClientReportAction(ClientBattleTextReplayProtocol.END)
        actions += when (result.outcome) {
            BattleOutcome.ATTACKER_WIN ->
                ClientReportAction(ClientBattleTextReplayProtocol.ATTACKER_WIN)
            BattleOutcome.DEFENDER_WIN ->
                ClientReportAction(ClientBattleTextReplayProtocol.DEFENDER_WIN)
            BattleOutcome.DRAW ->
                ClientReportAction(ClientBattleTextReplayProtocol.DRAW, listOf(3))
        }
        val heroes = result.attacker.heroes
            .sortedBy(BattleHero::position)
            .map { Side.ATTACKER to it } +
            result.defender.heroes
                .sortedBy(BattleHero::position)
                .map { Side.DEFENDER to it }
        heroes.forEach { (side, hero) ->
            actions += ClientReportAction(
                ClientBattleTextReplayProtocol.FINAL_TROOPS,
                listOf(
                    ClientBattleTextReplayProtocol.position(side, hero.position),
                    hero.troops,
                    (hero.maxTroops - hero.troops).coerceAtLeast(0),
                ),
            )
        }
    }

    private const val SKILL_SLOT_COUNT = 3
    private const val TROOP_FEATURE_SLOT_COUNT = 2
    private const val EQUIPMENT_SLOT_COUNT = 3
    private const val DEFAULT_SKILL_LEVEL = 1
}
