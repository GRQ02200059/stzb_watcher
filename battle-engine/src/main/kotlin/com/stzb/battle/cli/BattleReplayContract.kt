package com.stzb.battle.cli

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleReportCodec
import com.stzb.battle.core.BattleResult
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.ClientBattleTextReplayAdapter
import com.stzb.battle.core.Side

data class HeroRefDto(
    val side: String,
    val position: Int,
    val heroId: Int,
)

data class ReplayEventDto(
    val eventSeq: Int,
    val phase: String,
    val round: Int,
    val type: String,
    val source: HeroRefDto? = null,
    val target: HeroRefDto? = null,
    val rootSkillId: Int = 0,
    val skillId: Int = 0,
    val effectId: Int = 0,
    val trigger: String = "",
    val amount: Double = 0.0,
    val damage: Int = 0,
    val targetTroopsAfter: Int? = null,
    val status: String = "",
    val durationRounds: Int = 0,
    val blockingEffectId: Int = 0,
    val stat: String = "",
    val unit: String = "",
    val deltaExact: Double = 0.0,
    val valueAfterExact: Double? = null,
    val payload: Map<String, Any?> = emptyMap(),
)

data class ReplayActionDto(
    val actionSeq: Int,
    val actionId: Int,
    val params: List<Any>,
    val encoded: String,
)

data class HeroRoundSnapshotDto(
    val round: Int,
    val side: String,
    val position: Int,
    val heroId: Int,
    val troops: Int,
    val roundDamageTaken: Int,
    val cumulativeDamageTaken: Int,
    val roundRecovery: Int,
    val cumulativeRecovery: Int,
    val alive: Boolean,
    val activeStatuses: List<String>,
)

data class ReplayDiagnosticsDto(
    val unsupportedSkillEffects: List<ReplayEventDto>,
    val unsupportedEquipmentEffects: List<ReplayEventDto>,
    val unprojectedReplayEvents: List<String>,
    val semanticEventCount: Int,
    val replayActionCount: Int,
)

data class ReplayPayload(
    val events: List<ReplayEventDto>,
    val replayActions: List<ReplayActionDto>,
    val replayText: String,
    val entrySnapshots: List<HeroRoundSnapshotDto>,
    val roundSnapshots: List<HeroRoundSnapshotDto>,
    val finalSnapshots: List<HeroRoundSnapshotDto>,
    val diagnostics: ReplayDiagnosticsDto,
)

object BattleReplayContract {
    private val mapper = jacksonObjectMapper()
    private val mapType =
        object : TypeReference<LinkedHashMap<String, Any?>>() {}

    fun from(result: BattleResult): ReplayPayload {
        val events = semanticEvents(result)
        val replayDiagnostics = mutableListOf<String>()
        val replayActions = ClientBattleTextReplayAdapter.adapt(
            result,
            replayDiagnostics::add,
        ).mapIndexed { index, action ->
            ReplayActionDto(
                actionSeq = index,
                actionId = action.id,
                params = action.params,
                encoded = action.encode(),
            )
        }
        val snapshots = SnapshotProjector(result).project()
        return ReplayPayload(
            events = events,
            replayActions = replayActions,
            replayText = replayActions.joinToString("#", transform = ReplayActionDto::encoded),
            entrySnapshots = snapshots.entry,
            roundSnapshots = snapshots.rounds,
            finalSnapshots = snapshots.final,
            diagnostics = ReplayDiagnosticsDto(
                unsupportedSkillEffects =
                    events.filter { it.type == "UnsupportedSkillEffect" },
                unsupportedEquipmentEffects =
                    events.filter { it.type == "UnsupportedEquipmentEffect" },
                unprojectedReplayEvents = replayDiagnostics,
                semanticEventCount = events.size,
                replayActionCount = replayActions.size,
            ),
        )
    }

    private fun semanticEvents(result: BattleResult): List<ReplayEventDto> {
        val eventNodes = mapper.readTree(BattleReportCodec.toJson(result))["events"]
        return eventNodes.mapIndexed { index, node ->
            val payload: Map<String, Any?> = mapper.convertValue(node, mapType)
            ReplayEventDto(
                eventSeq = index,
                phase = phase(result.events[index]),
                round = payload.int("round"),
                type = payload.string("type"),
                source = payload.heroRef("source"),
                target = payload.heroRef("target"),
                rootSkillId = payload.int("rootSkillId"),
                skillId = payload.int("skillId"),
                effectId = payload.int("effectId"),
                trigger = payload.string("trigger"),
                amount = payload.double("amount"),
                damage = payload.int("damage"),
                targetTroopsAfter = payload.nullableInt("targetTroopsAfter"),
                status = payload.string("status"),
                durationRounds = payload.int("durationRounds"),
                blockingEffectId = payload.int("blockingEffectId"),
                stat = payload.string("stat"),
                unit = payload.string("unit"),
                deltaExact = payload.double("deltaExact"),
                valueAfterExact = payload.nullableDouble("valueAfterExact"),
                payload = payload,
            )
        }
    }

    private fun phase(event: BattleEvent): String =
        when {
            event is BattleEvent.BattleEnd -> "FINAL"
            event.roundOrZero() == 0 -> "PREPARATION"
            else -> "BATTLE"
        }
}

private data class ProjectedSnapshots(
    val entry: List<HeroRoundSnapshotDto>,
    val rounds: List<HeroRoundSnapshotDto>,
    val final: List<HeroRoundSnapshotDto>,
)

private data class SnapshotKey(
    val side: Side,
    val position: Int,
)

private data class SnapshotState(
    val heroId: Int,
    var troops: Int,
    var roundDamageTaken: Int = 0,
    var cumulativeDamageTaken: Int = 0,
    var roundRecovery: Int = 0,
    var cumulativeRecovery: Int = 0,
    val statuses: MutableSet<BattleStatus> = linkedSetOf(),
    val statusByEffectId: MutableMap<Int, BattleStatus> = linkedMapOf(),
)

private class SnapshotProjector(
    private val result: BattleResult,
) {
    private val states = linkedMapOf<SnapshotKey, SnapshotState>()
    private val roundSnapshots = mutableListOf<HeroRoundSnapshotDto>()

    fun project(): ProjectedSnapshots {
        val entryAttacker = result.entryAttacker ?: result.attacker
        val entryDefender = result.entryDefender ?: result.defender
        addEntryTeam(Side.ATTACKER, entryAttacker.heroes)
        addEntryTeam(Side.DEFENDER, entryDefender.heroes)
        val entry = snapshot(round = 0)

        result.events.forEach { event ->
            when (event) {
                is BattleEvent.RoundStart -> resetRoundCounters()
                is BattleEvent.NormalAttack -> damage(
                    event.target,
                    event.damage,
                    event.targetTroopsAfter,
                )
                is BattleEvent.SkillDamage -> damage(
                    event.target,
                    event.damage,
                    event.targetTroopsAfter,
                )
                is BattleEvent.OngoingDamage -> damage(
                    event.target,
                    event.damage,
                    event.targetTroopsAfter,
                )
                is BattleEvent.Recovery -> recovery(
                    event.target,
                    event.amount,
                    event.targetTroopsAfter,
                )
                is BattleEvent.StatusApplied -> applyStatus(event)
                is BattleEvent.StatusRemoved -> removeStatus(
                    event.target,
                    event.effectId,
                )
                is BattleEvent.EffectExpired -> removeStatus(
                    event.target,
                    event.effectId,
                )
                is BattleEvent.RoundEnd ->
                    roundSnapshots += snapshot(event.round)
                else -> Unit
            }
        }

        applyFinalTeam(Side.ATTACKER, result.attacker.heroes)
        applyFinalTeam(Side.DEFENDER, result.defender.heroes)
        val finalRound = result.events.filterIsInstance<BattleEvent.RoundStart>()
            .maxOfOrNull(BattleEvent.RoundStart::round)
            ?: 0
        return ProjectedSnapshots(
            entry = entry,
            rounds = roundSnapshots,
            final = snapshot(finalRound),
        )
    }

    private fun addEntryTeam(side: Side, heroes: List<BattleHero>) {
        heroes.forEach { hero ->
            states[SnapshotKey(side, hero.position)] = SnapshotState(
                heroId = hero.id.value,
                troops = hero.troops,
                statuses = hero.activeStatuses.toMutableSet(),
            )
        }
    }

    private fun applyFinalTeam(side: Side, heroes: List<BattleHero>) {
        heroes.forEach { hero ->
            states[SnapshotKey(side, hero.position)]?.apply {
                troops = hero.troops
                statuses.clear()
                statuses += hero.activeStatuses
            }
        }
    }

    private fun resetRoundCounters() {
        states.values.forEach { state ->
            state.roundDamageTaken = 0
            state.roundRecovery = 0
        }
    }

    private fun damage(
        target: BattleHeroRef,
        amount: Int,
        troopsAfter: Int,
    ) {
        states[target.key()]?.apply {
            troops = troopsAfter
            roundDamageTaken += amount
            cumulativeDamageTaken += amount
        }
    }

    private fun recovery(
        target: BattleHeroRef,
        amount: Int,
        troopsAfter: Int,
    ) {
        states[target.key()]?.apply {
            troops = troopsAfter
            roundRecovery += amount
            cumulativeRecovery += amount
        }
    }

    private fun applyStatus(event: BattleEvent.StatusApplied) {
        states[event.target.key()]?.apply {
            statuses += event.status
            event.effectId?.let { statusByEffectId[it] = event.status }
        }
    }

    private fun removeStatus(target: BattleHeroRef, effectId: Int) {
        states[target.key()]?.apply {
            statusByEffectId.remove(effectId)?.let(statuses::remove)
        }
    }

    private fun snapshot(round: Int): List<HeroRoundSnapshotDto> =
        states.entries
            .sortedWith(
                compareBy<Map.Entry<SnapshotKey, SnapshotState>>(
                    { it.key.side.ordinal },
                    { it.key.position },
                ),
            )
            .map { (key, state) ->
                HeroRoundSnapshotDto(
                    round = round,
                    side = key.side.name,
                    position = key.position,
                    heroId = state.heroId,
                    troops = state.troops,
                    roundDamageTaken = state.roundDamageTaken,
                    cumulativeDamageTaken = state.cumulativeDamageTaken,
                    roundRecovery = state.roundRecovery,
                    cumulativeRecovery = state.cumulativeRecovery,
                    alive = state.troops > 0,
                    activeStatuses = state.statuses.map(BattleStatus::name).sorted(),
                )
            }
}

private fun BattleHeroRef.key(): SnapshotKey =
    SnapshotKey(side, position)

private fun BattleEvent.roundOrZero(): Int =
    when (this) {
        BattleEvent.BattleStart,
        is BattleEvent.BattleEnd,
        -> 0
        is BattleEvent.SkillTriggered -> round
        is BattleEvent.TriggerPoint -> round
        is BattleEvent.SkillPreparationCompleted -> round
        is BattleEvent.SkillPreparationCancelled -> round
        is BattleEvent.StatusRemoved -> round
        is BattleEvent.EffectExpired -> round
        is BattleEvent.EffectBlocked -> round
        is BattleEvent.RoundStart -> round
        is BattleEvent.HeroActionStart -> round
        is BattleEvent.NormalAttack -> round
        is BattleEvent.SkillDamage -> round
        is BattleEvent.SkillPreparationStarted -> round
        is BattleEvent.Recovery -> round
        is BattleEvent.StatusApplied -> round
        is BattleEvent.OngoingDamage -> round
        is BattleEvent.Evaded -> round
        is BattleEvent.StatChanged -> round
        is BattleEvent.ModifierApplied -> round
        is BattleEvent.SkillRangeChanged -> round
        is BattleEvent.UnsupportedSkillEffect -> round
        is BattleEvent.UnsupportedEquipmentEffect -> round
        is BattleEvent.HeroActionEnd -> round
        is BattleEvent.RoundEnd -> round
    }

private fun Map<String, Any?>.string(key: String): String =
    this[key] as? String ?: ""

private fun Map<String, Any?>.int(key: String): Int =
    (this[key] as? Number)?.toInt() ?: 0

private fun Map<String, Any?>.nullableInt(key: String): Int? =
    (this[key] as? Number)?.toInt()

private fun Map<String, Any?>.double(key: String): Double =
    (this[key] as? Number)?.toDouble() ?: 0.0

private fun Map<String, Any?>.nullableDouble(key: String): Double? =
    (this[key] as? Number)?.toDouble()

private fun Map<String, Any?>.heroRef(key: String): HeroRefDto? {
    val value = this[key] as? Map<*, *> ?: return null
    return HeroRefDto(
        side = value["side"] as? String ?: "",
        position = (value["position"] as? Number)?.toInt() ?: 0,
        heroId = (value["heroId"] as? Number)?.toInt() ?: 0,
    )
}
