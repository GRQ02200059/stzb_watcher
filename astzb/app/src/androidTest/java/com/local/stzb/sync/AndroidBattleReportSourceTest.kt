package com.local.stzb.sync

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.stzb.profile.LocalProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBattleReportSourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseA = "sync-a-" + System.nanoTime() + ".db"
    private val databaseB = "sync-b-" + System.nanoTime() + ".db"
    private val profileA = profile("profile-a", databaseA)
    private val profileB = profile("profile-b", databaseB)
    private lateinit var source: AndroidBattleReportSource

    @Before
    fun setUp() {
        context.deleteDatabase(databaseA)
        context.deleteDatabase(databaseB)
        seedDatabase(databaseA, listOf(42L, 43L))
        seedDatabase(databaseB, emptyList())
        source = AndroidBattleReportSource(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseA)
        context.deleteDatabase(databaseB)
    }

    @Test
    fun readsOnlyRequestedProfileWithTypedFieldsAndSortedChildren() {
        val report = source.readBatch(profileA, null, 200).first()
        assertEquals(42L, report.battleId)
        assertEquals(42L, report.battle["battle_id"])
        assertEquals("玩家甲", report.battle["atk_name"])
        assertEquals(null, report.battle["def_union"])
        assertEquals(listOf(0L, 1L), report.heroes.map { it["pos"] })
        assertEquals(listOf(2001L, 2002L), report.skills.map { it["skill_id"] })
        assertTrue(source.readBatch(profileB, null, 200).isEmpty())
    }

    @Test
    fun paginatesByBattleIdAndRejectsInvalidDatabaseNames() {
        assertEquals(listOf(42L), source.readBatch(profileA, null, 1).map { it.battleId })
        assertEquals(listOf(43L), source.readBatch(profileA, 42L, 200).map { it.battleId })
        val invalid = profile("bad", "../secret.db")
        assertTrue(source.readBatch(invalid, null, 200).isEmpty())
    }

    @Test
    fun missingDatabaseOrTablesReturnsEmptyWithoutCreatingDatabase() {
        val missing = profile("missing", "missing-" + System.nanoTime() + ".db")
        val file = context.getDatabasePath(missing.databaseName)
        assertTrue(source.readBatch(missing, null, 200).isEmpty())
        assertEquals(false, file.exists())

        val partialName = "partial-" + System.nanoTime() + ".db"
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(partialName), null).use { db ->
            db.execSQL("CREATE TABLE battles_v2(battle_id INTEGER PRIMARY KEY)")
        }
        assertTrue(source.readBatch(profile("partial", partialName), null, 200).isEmpty())
        context.deleteDatabase(partialName)
    }

    private fun seedDatabase(name: String, battleIds: List<Long>) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE battles_v2(battle_id INTEGER PRIMARY KEY,time INTEGER,atk_name TEXT,def_union TEXT,result INTEGER,captured_at INTEGER,raw_json TEXT,source_msg_id TEXT)")
            db.execSQL("CREATE TABLE battle_heroes(id INTEGER PRIMARY KEY AUTOINCREMENT,battle_id INTEGER,side TEXT,pos INTEGER,hero_id INTEGER,hero_name TEXT,level INTEGER,star INTEGER,max_hp INTEGER,remain_hp INTEGER,damage_taken INTEGER)")
            db.execSQL("CREATE TABLE battle_skills(id INTEGER PRIMARY KEY AUTOINCREMENT,battle_id INTEGER,side TEXT,pos INTEGER,skill_id INTEGER,skill_name TEXT,skill_level INTEGER)")
            battleIds.forEach { battleId ->
                db.execSQL(
                    "INSERT INTO battles_v2 VALUES(?,?,?,?,?,?,?,?)",
                    arrayOf(battleId, 1_700_000_000L, "玩家甲", null, 1, 1_700_000_010L, "forbidden", "forbidden"),
                )
            }
            if (battleIds.contains(42L)) {
                db.execSQL("INSERT INTO battle_heroes(battle_id,side,pos,hero_id,hero_name,level) VALUES(42,'atk',1,1002,'关羽',50)")
                db.execSQL("INSERT INTO battle_heroes(battle_id,side,pos,hero_id,hero_name,level) VALUES(42,'atk',0,1001,'赵云',50)")
                db.execSQL("INSERT INTO battle_skills(battle_id,side,pos,skill_id,skill_name,skill_level) VALUES(42,'atk',1,2002,'谋定后动',10)")
                db.execSQL("INSERT INTO battle_skills(battle_id,side,pos,skill_id,skill_name,skill_level) VALUES(42,'atk',0,2001,'一骑当千',10)")
            }
        }
    }

    private fun profile(id: String, database: String) = LocalProfile(
        profileId = id,
        serverAddress = "server",
        roleId = "role",
        displayName = id,
        databaseName = database,
        lastUsedAt = 0L,
    )
}
