package com.local.stzb.data.battlefield

import com.example.myapplication.LocalBattleField
import com.example.myapplication.LocalBattleMonitorStore
import com.example.myapplication.LocalFullBattle
import com.example.myapplication.LocalSocksCaptureServer
import com.example.myapplication.LocalStzbRepository
import com.example.myapplication.LocalTeamMove
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.BattlefieldMetrics
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.CaptureStatus
import com.local.stzb.domain.battlefield.EventCategory
import hev.sockstun.Preferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LegacyBattlefieldData(
    val captureRunning: Boolean,
    val lastEventAt: Long?,
    val moves: List<LocalTeamMove>,
    val battles: List<LocalFullBattle>,
    val sieges: List<LocalBattleField>,
)

interface LegacyBattlefieldSource {
    fun captureRunning(): Boolean
    fun lastEventAt(): Long?
    fun moves(): List<LocalTeamMove>
    fun battles(): List<LocalFullBattle>
    fun sieges(): List<LocalBattleField>

    /**
     * Consistency boundary for one repository refresh. The default is best-effort for legacy
     * sources; production sources should override it so each backing collection is fetched once.
     */
    fun read(): LegacyBattlefieldData = LegacyBattlefieldData(
        captureRunning = captureRunning(),
        lastEventAt = lastEventAt(),
        moves = moves(),
        battles = battles(),
        sieges = sieges(),
    )
}

class LegacyBattlefieldRepository(
    private val source: LegacyBattlefieldSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : BattlefieldRepository {
    private val lock = Any()
    private val refreshMutex = Mutex()
    private val visible = LinkedHashMap<String, BattlefieldEvent>()
    private val buffered = LinkedHashMap<String, BattlefieldEvent>()
    private var previousSourceEvents = emptyMap<String, BattlefieldEvent>()
    private var paused = false
    private var categories = EventCategory.entries.toSet()
    private var latestData = LegacyBattlefieldData(
        captureRunning = false,
        lastEventAt = null,
        moves = emptyList(),
        battles = emptyList(),
        sieges = emptyList(),
    )
    private val state = MutableStateFlow(buildSnapshot())

    override fun observeSnapshot(): Flow<BattlefieldSnapshot> = state.asStateFlow()

    override suspend fun refresh() = refreshMutex.withLock {
        val data = withContext(ioDispatcher) { source.read() }.normalizeTimestamps()
        val incoming = buildList {
            addAll(data.moves.map(BattlefieldEventMapper::fromMove))
            addAll(data.battles.map(BattlefieldEventMapper::fromBattle))
            addAll(data.sieges.map(BattlefieldEventMapper::fromSiege))
        }.distinctBy(BattlefieldEvent::id).sortedWith(EVENT_ORDER)

        synchronized(lock) {
            latestData = data
            if (paused) {
                incoming.forEach { event ->
                    val previous = previousSourceEvents[event.id]
                    when {
                        previous == null -> buffered[event.id] = event
                        previous != event && (event.id in visible || event.id in buffered) -> buffered[event.id] = event
                    }
                }
                replaceWithNewest(buffered, buffered.values)
            } else {
                incoming.forEach { event ->
                    val previous = previousSourceEvents[event.id]
                    when {
                        event.id in visible -> visible[event.id] = event
                        previous == null -> visible[event.id] = event
                    }
                }
                replaceWithNewest(visible, visible.values)
            }
            previousSourceEvents = incoming.associateBy(BattlefieldEvent::id)
            state.value = buildSnapshot()
        }
    }

    override fun setPaused(paused: Boolean) = synchronized(lock) {
        if (this.paused && !paused) {
            replaceWithNewest(visible, visible.values + buffered.values)
            buffered.clear()
        }
        this.paused = paused
        state.value = buildSnapshot()
    }

    override fun setFilter(categories: Set<EventCategory>) = synchronized(lock) {
        require(categories.isNotEmpty()) { "At least one event category must be selected" }
        this.categories = categories.toSet()
        state.value = buildSnapshot()
    }

    private fun buildSnapshot(): BattlefieldSnapshot {
        val data = latestData
        val now = nowSeconds()
        return BattlefieldSnapshot(
            capture = CaptureStatus(
                running = data.captureRunning,
                label = if (data.captureRunning) "抓包运行中" else "抓包未启动",
                lastEventAt = data.lastEventAt,
            ),
            metrics = BattlefieldMetrics(
                activeMarches = data.moves.size,
                arrivingSoon = data.moves.count { it.arriveTime in now..(now + ARRIVING_SOON_SECONDS) },
                todayBattles = data.battles.size,
                siegeEvents = data.sieges.size,
            ),
            events = visible.values.filter { it.category in categories },
            selectedCategories = categories,
            paused = paused,
            bufferedEventCount = buffered.size,
        )
    }

    private fun replaceWithNewest(
        target: LinkedHashMap<String, BattlefieldEvent>,
        events: Collection<BattlefieldEvent>,
    ) {
        val newest = events
            .associateBy(BattlefieldEvent::id)
            .values
            .sortedWith(EVENT_ORDER)
            .take(EVENT_LIMIT)
        target.clear()
        newest.forEach { target[it.id] = it }
    }

    private companion object {
        const val EVENT_LIMIT = 200
        const val ARRIVING_SOON_SECONDS = 300L
        val EVENT_ORDER = compareByDescending<BattlefieldEvent> { it.occurredAt }.thenBy { it.id }
    }
}

class AndroidLegacyBattlefieldSource(
    private val preferences: Preferences,
) : LegacyBattlefieldSource {
    override fun captureRunning(): Boolean = preferences.enable || LocalSocksCaptureServer.isRunning()

    override fun lastEventAt(): Long? {
        val captured = LocalBattleMonitorStore.latest()?.let { normalizeEpochSeconds(it.capturedAt) }
        val arrival = moves().maxOfOrNull { normalizeEpochSeconds(it.arriveTime) }
        val battle = battles().maxOfOrNull { normalizeEpochSeconds(it.time) }
        return listOfNotNull(captured, arrival, battle).maxOrNull()
    }

    override fun moves(): List<LocalTeamMove> = LocalBattleMonitorStore.latest()?.moves.orEmpty()

    override fun battles(): List<LocalFullBattle> = LocalStzbRepository.loadFullBattles(BATTLE_LIMIT)

    override fun sieges(): List<LocalBattleField> = LocalStzbRepository.loadBattleFields(SIEGE_LIMIT)

    override fun read(): LegacyBattlefieldData {
        val latest = LocalBattleMonitorStore.latest()
        val moves = latest?.moves.orEmpty()
        val battles = battles()
        val sieges = sieges()
        return LegacyBattlefieldData(
            captureRunning = captureRunning(),
            lastEventAt = listOfNotNull(
                latest?.let { normalizeEpochSeconds(it.capturedAt) },
                moves.maxOfOrNull { normalizeEpochSeconds(it.arriveTime) },
                battles.maxOfOrNull { normalizeEpochSeconds(it.time) },
            ).maxOrNull(),
            moves = moves,
            battles = battles,
            sieges = sieges,
        )
    }

    private companion object {
        const val HISTORY_LIMIT = 20
        const val BATTLE_LIMIT = 80
        const val SIEGE_LIMIT = 80
    }
}

private fun LegacyBattlefieldData.normalizeTimestamps(): LegacyBattlefieldData = copy(
    lastEventAt = lastEventAt?.let(::normalizeEpochSeconds),
    moves = moves.map { move ->
        move.copy(
            startTime = normalizeEpochSeconds(move.startTime),
            arriveTime = normalizeEpochSeconds(move.arriveTime),
        )
    },
    battles = battles.map { battle ->
        battle.copy(time = normalizeEpochSeconds(battle.time))
    },
)
