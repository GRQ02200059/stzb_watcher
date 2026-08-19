package com.example.myapplication

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalProfileDatabaseIsolationTest {
    @Test
    fun twoProfileDatabaseNamesNeverShareRows() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "profile-db-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getDatabasePath(name: String): File = File(root, name)
        }
        val first = LocalStzbDatabase(context, "profile-a.db")
        val second = LocalStzbDatabase(context, "profile-b.db")
        try {
            first.writableDatabase.execSQL("INSERT INTO local_records(record_type,record_key,title,subtitle,raw_json,source_msg_id,updated_at) VALUES('test','a','A','','','test',1)")
            assertEquals(1, first.readableDatabase.rawQuery("SELECT COUNT(*) FROM local_records", emptyArray()).use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, second.readableDatabase.rawQuery("SELECT COUNT(*) FROM local_records", emptyArray()).use { it.moveToFirst(); it.getInt(0) })
        } finally {
            first.close(); second.close(); root.deleteRecursively()
        }
    }

    @Test
    fun version16DatabaseUpgradesToScoreSchemaAndCleans6314Pollution() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "upgrade-db-${System.nanoTime()}").apply { mkdirs() }
        val databaseFile = File(root, "legacy.db")
        val context = object : ContextWrapper(target) { override fun getDatabasePath(name: String): File = databaseFile }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            db.execSQL("CREATE TABLE battle_field(wid INTEGER PRIMARY KEY,attacker_uid INTEGER DEFAULT 0,nearby_uids TEXT,nearby_count INTEGER DEFAULT 0,source_msg_id TEXT,captured_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE local_records(id INTEGER PRIMARY KEY AUTOINCREMENT,record_type TEXT NOT NULL,record_key TEXT NOT NULL,title TEXT,subtitle TEXT,raw_json TEXT,source_msg_id TEXT,updated_at INTEGER NOT NULL,UNIQUE(record_type,record_key))")
            db.execSQL("INSERT INTO battle_field(wid,source_msg_id,captured_at) VALUES(100020,'6314',1)")
            db.execSQL("INSERT INTO local_records(record_type,record_key,source_msg_id,updated_at) VALUES('battle_field','100020','6314',1)")
            db.version = 16
        }
        val helper = LocalStzbDatabase(context, "legacy.db")
        try {
            val db = helper.writableDatabase
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", emptyArray()).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            assert(tables.containsAll(setOf("score_rule_versions", "score_adjustments", "custom_scores")))
            assertEquals(0, db.rawQuery("SELECT COUNT(*) FROM battle_field WHERE source_msg_id='6314'", emptyArray()).use { it.moveToFirst(); it.getInt(0) })
        } finally { helper.close(); root.deleteRecursively() }
    }
}
