package com.example.myapplication

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStzbDatabaseBattleMonitorTest {
    @Test
    fun consecutivePacketsKeepCurrentProjectionAndAppendTraceableWorldHistory() {
        withDatabase { database ->
            LocalStzbRepository.syncBattleMonitor(
                database.writableDatabase,
                snapshot(1, 2, marker = 10),
                now = 100,
                sourceMessageId = "5026",
            )
            LocalStzbRepository.syncBattleMonitor(
                database.writableDatabase,
                snapshot(2, marker = 11, deletedTeamIds = listOf(1), blockMode = 2, blockId = 40),
                now = 200,
                sourceMessageId = "5028",
            )

            assertEquals(listOf(2), database.intColumn(
                "SELECT team_id FROM battle_monitor_moves ORDER BY team_id",
            ))
            assertEquals(listOf(10, 11), database.intColumn(
                "SELECT marker FROM world_state_versions ORDER BY version",
            ))
            assertEquals(listOf("5026", "5028"), database.stringColumn(
                "SELECT source_msg_id FROM world_state_versions ORDER BY version",
            ))
            assertEquals(listOf(1), database.intColumn(
                "SELECT entity_id FROM world_state_events " +
                    "WHERE event_type = 'entity_deleted' AND entity_type = 'army'",
            ))
            assertTrue(database.stringColumn(
                "SELECT evidence_json FROM world_state_events WHERE event_type = 'entity_deleted'",
            ).single().contains("\"blockId\":40"))

            val history = LocalStzbRepository.loadWorldStateHistory(database.readableDatabase, 10)
            assertEquals(listOf(11, 10), history.map { it.marker })
            assertEquals("5028", history.first().sourceMsgId)
            val replay = LocalStzbRepository.loadWorldStateReplay(
                database.readableDatabase,
                history.first().version,
            )!!
            assertEquals(11, replay.version.marker)
            assertEquals("entity_deleted", replay.events.single { it.entityId == 1 }.eventType)
        }
    }

    @Test
    fun deltaClearRemovesChunkButKeepsOtherChunksAndRealMarchProjection() {
        withDatabase { database ->
            LocalStzbRepository.syncBattleMonitor(
                database.writableDatabase,
                snapshot(
                    1,
                    marker = 10,
                    mapStates = listOf(LocalMapState(10004, 2, 14, "{\"0\":[1],\"4\":[2]}")),
                    blockArmyIds = mapOf(40 to listOf(1)),
                    realMarches = listOf(realMarch(9001)),
                ),
                100,
                "5026",
            )
            LocalStzbRepository.syncBattleMonitor(
                database.writableDatabase,
                snapshot(marker = 11, clearChunks = mapOf(10004 to listOf("4"))),
                200,
                "5028",
            )

            assertEquals(listOf("0"), database.stringColumn(
                "SELECT chunk_type FROM world_tile_chunks WHERE wid = 10004 ORDER BY chunk_type",
            ))
            assertEquals(listOf(9001), database.intColumn(
                "SELECT real_march_id FROM world_real_marches",
            ))
            assertEquals(listOf(1), database.intColumn(
                "SELECT army_id FROM world_army_blocks WHERE block_id = 40",
            ))
        }
    }

    @Test
    fun sundayWuxunSnapshotsKeepWeeklyMaximumAndAccumulateAcrossWeeks() {
        withDatabase { database ->
            database.writableDatabase.execSQL(
                "INSERT INTO team_users(uid,name,wuxun,updated_at) VALUES(42,'玩家甲',1200,1)",
            )
            assertEquals(1, LocalStzbRepository.captureSundayWuxunSnapshot(
                database.writableDatabase, 1770544800000L,
            ))
            database.writableDatabase.execSQL("UPDATE team_users SET wuxun=900 WHERE uid=42")
            LocalStzbRepository.captureSundayWuxunSnapshot(
                database.writableDatabase, 1770548400000L,
            )
            database.writableDatabase.execSQL("UPDATE team_users SET wuxun=2300 WHERE uid=42")
            LocalStzbRepository.captureSundayWuxunSnapshot(
                database.writableDatabase, 1771149600000L,
            )
            database.writableDatabase.execSQL("UPDATE team_users SET wuxun=0 WHERE uid=42")

            assertEquals(3500, LocalStzbRepository.cumulativeWuxun(
                database.readableDatabase, 42,
            ))
        }
    }

    @Test
    fun personalSeasonTrendUsesBattleFactsAndMemberWuxunSnapshots() {
        withDatabase { database ->
            database.writableDatabase.execSQL(
                "INSERT INTO battles_v2(battle_id,time,result,atk_name,captured_at) VALUES(1,1786287600,1,'玩家甲',1)",
            )
            database.writableDatabase.execSQL(
                "INSERT INTO battles_v2(battle_id,time,result,atk_name,captured_at) VALUES(2,1786374000,2,'玩家甲',1)",
            )
            database.writableDatabase.execSQL(
                "INSERT INTO wuxun_weekly_snapshots VALUES('2026-08-03',42,'玩家甲','一团',1200,1)",
            )
            database.writableDatabase.execSQL(
                "INSERT INTO wuxun_weekly_snapshots VALUES('2026-08-10',42,'玩家甲','一团',2300,2)",
            )

            val trend = LocalStzbRepository.loadPlayerSeasonTrend(
                database.readableDatabase, "玩家甲",
            )

            assertEquals(listOf("2026-08-03", "2026-08-10"), trend.map { it.weekStart })
            assertEquals(listOf(1, 1), trend.map { it.battles })
            assertEquals(listOf(1200, 2300), trend.map { it.memberWuxun })
        }
    }

    @Test
    fun researchQualityWarnsForStaleWorldAndMissingWeeklySnapshot() {
        withDatabase { database ->
            val now = 1771401600000L
            database.writableDatabase.execSQL(
                "INSERT INTO world_state_versions(source_msg_id,marker,raw_length,completeness,captured_at) " +
                    "VALUES('5028',1,31,'delta',1)",
            )

            val quality = LocalStzbRepository.researchDataQuality(
                database.readableDatabase, now,
            )

            assertTrue(quality.worldStateStale)
            assertTrue(quality.weeklyWuxunMissing)
            assertEquals(94, quality.protocolCommandCount)
            assertEquals(81, quality.rawCommandCount)
            assertEquals(2, quality.warnings.size)
        }
    }

    @Test
    fun savingCurrentSnapshotRemovesRowsMissingFromIt() {
        withDatabase { database ->
            LocalStzbRepository.syncBattleMonitor(database.writableDatabase, snapshot(1, 2), 1)
            LocalStzbRepository.syncBattleMonitor(database.writableDatabase, snapshot(2), 2)

            val ids = database.readableDatabase.rawQuery(
                "SELECT team_id FROM battle_monitor_moves ORDER BY team_id",
                emptyArray(),
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) } }
            assertEquals(listOf(2), ids)

            LocalStzbRepository.syncBattleMonitor(database.writableDatabase, snapshot(), 3)
            val remaining = database.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM battle_monitor_moves",
                emptyArray(),
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertEquals(0, remaining)
        }
    }

    private fun snapshot(
        vararg ids: Int,
        marker: Int = 1,
        deletedTeamIds: List<Int> = emptyList(),
        blockMode: Int = 0,
        blockId: Int = 0,
        mapStates: List<LocalMapState> = emptyList(),
        blockArmyIds: Map<Int, List<Int>> = emptyMap(),
        clearChunks: Map<Int, List<String>> = emptyMap(),
        realMarches: List<LocalRealMarch> = emptyList(),
    ) = LocalBattleMonitorSnapshot(
        teamIds = ids.toList(),
        moves = ids.map(::move),
        subjects = emptyList(),
        mapStates = mapStates,
        marker = marker,
        rawLength = 31,
        deletedTeamIds = deletedTeamIds,
        blockMode = blockMode,
        blockId = blockId,
        blockArmyIds = blockArmyIds,
        clearChunks = clearChunks,
        realMarches = realMarches,
    )

    private fun withDatabase(block: (LocalStzbDatabase) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(target.cacheDir, "battle-monitor-${System.nanoTime()}.db")
        val context = object : ContextWrapper(target) {
            override fun getDatabasePath(name: String): File = databaseFile
        }
        val database = LocalStzbDatabase(context)
        try {
            block(database)
        } finally {
            database.close()
            databaseFile.delete()
        }
    }

    private fun LocalStzbDatabase.intColumn(sql: String): List<Int> =
        readableDatabase.rawQuery(sql, emptyArray()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
        }

    private fun LocalStzbDatabase.stringColumn(sql: String): List<String> =
        readableDatabase.rawQuery(sql, emptyArray()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun move(id: Int) = LocalTeamMove(
        teamId = id,
        moveType = 1,
        subjectId = 0,
        ownerUid = 0,
        ownerName = "",
        ownerUnion = "",
        fromWid = 0,
        toWid = 0,
        currentWid = 0,
        fromXy = "",
        toXy = "",
        currentXy = "",
        startTime = 0,
        arriveTime = 0,
        speed = 0,
    )

    private fun realMarch(id: Int) = LocalRealMarch(
        id, 10001, 10002, 3, 10003, 4, 5, 6, 7, 8, 9, 1, 80, 10, 11,
    )
}
