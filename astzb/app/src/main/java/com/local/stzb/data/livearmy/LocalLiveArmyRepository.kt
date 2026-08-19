package com.local.stzb.data.livearmy

import com.example.myapplication.HeroNameResolver
import com.example.myapplication.Local13A2TeamInsight
import com.example.myapplication.LocalBattleMonitorStore
import com.example.myapplication.LocalStzbRepository
import com.example.myapplication.LocalTeamMove

enum class LiveArmyFreshness { FRESH, AGING, STALE, UNKNOWN }
enum class LineupEvidence { EXACT_BATTLE, OBSERVED_TYPE, UNKNOWN }

data class LiveArmyHero(val heroId: Long, val name: String, val level: Int, val advance: Int)

data class LiveArmy(
    val teamId: Int,
    val ownerName: String,
    val ownerUnion: String,
    val stateLabel: String,
    val stateCategory: String,
    val isMoving: Boolean,
    val currentLocation: String,
    val targetLocation: String,
    val fromLocation: String,
    val morale: Int,
    val arrivalAt: Long,
    val freshness: LiveArmyFreshness,
    val usable: Boolean,
    val heroes: List<LiveArmyHero>,
    val lineupEvidence: LineupEvidence,
    val battles: Int,
    val winRate: Double,
    val searchText: String,
)

data class LiveArmySnapshot(
    val observedAtMs: Long,
    val freshness: LiveArmyFreshness,
    val current: List<LiveArmy>,
) {
    val moving: Int get() = current.count(LiveArmy::isMoving)
    val stationary: Int get() = current.count { it.stateCategory == "stationary" }
    val exactLineups: Int get() = current.count { it.lineupEvidence == LineupEvidence.EXACT_BATTLE }
}

interface LiveArmySource {
    fun observedAtMs(): Long
    fun moves(): List<LocalTeamMove>
    fun insight(move: LocalTeamMove): Local13A2TeamInsight
}

object AndroidLiveArmySource : LiveArmySource {
    override fun observedAtMs(): Long = LocalBattleMonitorStore.latest()?.capturedAt ?: 0L
    override fun moves(): List<LocalTeamMove> = LocalBattleMonitorStore.latest()?.moves ?: LocalStzbRepository.loadMonitorMoves(0)
    override fun insight(move: LocalTeamMove): Local13A2TeamInsight = LocalStzbRepository.load13A2TeamInsight(
        teamId = move.teamId,
        ownerName = move.ownerName,
        relatedWids = listOf(move.fromWid, move.currentWid, move.toWid),
        armyHeroType = move.armyHeroType,
    )
}

class LocalLiveArmyRepository(
    private val source: LiveArmySource = AndroidLiveArmySource,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun load(query: String = ""): LiveArmySnapshot {
        val observedAt = normalizeMs(source.observedAtMs())
        val freshness = freshness(observedAt, nowMs())
        val rows = source.moves().map { move -> project(move, source.insight(move), freshness) }
            .filter { query.isBlank() || it.searchText.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<LiveArmy> { it.isMoving }.thenBy { it.teamId })
        return LiveArmySnapshot(observedAt, freshness, rows)
    }

    private fun project(move: LocalTeamMove, insight: Local13A2TeamInsight, freshness: LiveArmyFreshness): LiveArmy {
        val state = stateMeta(move.moveType)
        val exactHeroes = insight.lineup.heroes.sortedBy { it.pos }.map {
            LiveArmyHero(it.heroId, it.heroName.ifBlank { HeroNameResolver.nameOf(it.heroId) }, it.level, it.star)
        }
        val observedHeroes = if (exactHeroes.isEmpty()) parseObservedHeroes(move.armyHeroType) else emptyList()
        val heroes = exactHeroes.ifEmpty { observedHeroes }
        val evidence = when {
            exactHeroes.isNotEmpty() -> LineupEvidence.EXACT_BATTLE
            observedHeroes.isNotEmpty() -> LineupEvidence.OBSERVED_TYPE
            else -> LineupEvidence.UNKNOWN
        }
        val current = location(move.currentXy, move.currentWid, move.stayWid, move.resideWid, move.fromWid)
        val target = location(move.toXy, move.toWid)
        val from = location(move.fromXy, move.fromWid)
        return LiveArmy(
            teamId = move.teamId, ownerName = move.ownerName.ifBlank { "未知玩家" }, ownerUnion = move.ownerUnion,
            stateLabel = state.label, stateCategory = state.category, isMoving = state.moving,
            currentLocation = current, targetLocation = target, fromLocation = from, morale = move.morale,
            arrivalAt = normalizeSeconds(move.arriveTime), freshness = freshness, usable = freshness == LiveArmyFreshness.FRESH || freshness == LiveArmyFreshness.AGING,
            heroes = heroes, lineupEvidence = evidence, battles = insight.stats.battles, winRate = insight.stats.winRate,
            searchText = buildString {
                append(move.teamId); append(' '); append(move.ownerName); append(' '); append(move.ownerUnion); append(' ')
                append(move.fromWid); append(' '); append(move.currentWid); append(' '); append(move.toWid); append(' ')
                append(from); append(' '); append(current); append(' '); append(target); append(' ')
                append(heroes.joinToString(" " ) { it.name })
            },
        )
    }

    private fun parseObservedHeroes(value: String): List<LiveArmyHero> = value.trim(';').split(';')
        .mapNotNull { segment -> segment.substringAfter(',', "").toLongOrNull() }
        .filter { it > 0 }.take(3)
        .map { id -> LiveArmyHero(id, HeroNameResolver.nameOf(id), 0, 0) }

    private data class StateMeta(val label: String, val category: String, val moving: Boolean)
    private fun stateMeta(state: Int): StateMeta = when (state) {
        0 -> StateMeta("待命", "stationary", false)
        1 -> StateMeta("出征中", "moving", true)
        2 -> StateMeta("驻守前往", "moving", true)
        3 -> StateMeta("增援前往", "moving", true)
        4 -> StateMeta("返回中", "moving", true)
        5 -> StateMeta("驻守", "stationary", false)
        6 -> StateMeta("增援", "stationary", false)
        25 -> StateMeta("停留", "stationary", false)
        else -> StateMeta("状态 $state", "unknown", false)
    }

    private fun location(xy: String, vararg wids: Int): String = xy.ifBlank {
        wids.firstOrNull { it > 0 }?.let { "${it / 10_000},${it % 10_000}" }.orEmpty()
    }.ifBlank { "未知" }

    companion object {
        private const val FRESH_MS = 120_000L
        private const val AGING_MS = 600_000L
        fun freshness(observedAtMs: Long, nowMs: Long): LiveArmyFreshness {
            if (observedAtMs <= 0L) return LiveArmyFreshness.UNKNOWN
            return when ((nowMs - observedAtMs).coerceAtLeast(0)) {
                in 0..FRESH_MS -> LiveArmyFreshness.FRESH
                in (FRESH_MS + 1)..AGING_MS -> LiveArmyFreshness.AGING
                else -> LiveArmyFreshness.STALE
            }
        }
        private fun normalizeMs(value: Long): Long = if (value in 1..99_999_999_999L) value * 1_000L else value
        private fun normalizeSeconds(value: Long): Long = if (value >= 100_000_000_000L) value / 1_000L else value
    }
}
