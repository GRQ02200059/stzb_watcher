package com.example.myapplication

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStzbDatabaseBattleMonitorTest {
    @Test
    fun savingCurrentSnapshotRemovesRowsMissingFromIt() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(target.cacheDir, "battle-monitor-${System.nanoTime()}.db")
        val context = object : ContextWrapper(target) {
            override fun getDatabasePath(name: String): File = databaseFile
        }
        val database = LocalStzbDatabase(context)
        try {
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
        } finally {
            database.close()
            databaseFile.delete()
        }
    }

    private fun snapshot(vararg ids: Int) = LocalBattleMonitorSnapshot(
        teamIds = ids.toList(),
        moves = ids.map(::move),
        subjects = emptyList(),
        mapStates = emptyList(),
        marker = 1,
        rawLength = 31,
    )

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
}
