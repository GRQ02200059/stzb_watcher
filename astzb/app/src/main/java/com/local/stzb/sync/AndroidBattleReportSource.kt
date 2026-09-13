package com.local.stzb.sync

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.local.stzb.profile.LocalProfile

interface BattleReportSource {
    fun readBatch(
        profile: LocalProfile,
        afterBattleId: Long?,
        limit: Int,
    ): List<BattleReportPayload>
}

class AndroidBattleReportSource(context: Context) : BattleReportSource {
    private val appContext = context.applicationContext

    override fun readBatch(
        profile: LocalProfile,
        afterBattleId: Long?,
        limit: Int,
    ): List<BattleReportPayload> {
        if (!DATABASE_NAME.matches(profile.databaseName)) return emptyList()
        val file = appContext.getDatabasePath(profile.databaseName)
        if (!file.isFile) return emptyList()
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).use { database ->
                val tables = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table'",
                    null,
                ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
                if (!tables.containsAll(REQUIRED_TABLES)) return@use emptyList()
                database.rawQuery("BEGIN", null).close()
                try {
                    val reports = readReports(
                        database,
                        afterBattleId,
                        limit.coerceIn(1, MAX_BATCH),
                    )
                    database.rawQuery("COMMIT", null).close()
                    reports
                } catch (error: Throwable) {
                    runCatching { database.rawQuery("ROLLBACK", null).close() }
                    throw error
                }
            }
        } catch (_: SQLiteException) {
            emptyList()
        }
    }

    private fun readReports(
        database: SQLiteDatabase,
        afterBattleId: Long?,
        limit: Int,
    ): List<BattleReportPayload> {
        val battleColumns = existingColumns(database, "battles_v2")
            .filter(BATTLE_FIELDS::contains)
        if ("battle_id" !in battleColumns) return emptyList()
        val where = if (afterBattleId == null) "" else "WHERE battle_id > ?"
        val arguments = afterBattleId?.let { arrayOf(it.toString()) }
        val sql = "SELECT " + battleColumns.joinToString(",") +
            " FROM battles_v2 " + where + " ORDER BY battle_id LIMIT " + limit
        val battles = database.rawQuery(sql, arguments).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toMap(battleColumns))
            }
        }
        if (battles.isEmpty()) return emptyList()
        val battleIds = battles.map { row -> row.getValue("battle_id") as Long }
        val heroes = readChildren(database, "battle_heroes", HERO_FIELDS, battleIds)
        val skills = readChildren(database, "battle_skills", SKILL_FIELDS, battleIds)
        return battles.map { battle ->
            val battleId = battle.getValue("battle_id") as Long
            BattleReportPayload(
                battleId = battleId,
                battle = battle,
                heroes = heroes[battleId].orEmpty(),
                skills = skills[battleId].orEmpty(),
            )
        }
    }

    private fun readChildren(
        database: SQLiteDatabase,
        table: String,
        allowed: Set<String>,
        battleIds: List<Long>,
    ): Map<Long, List<Map<String, Any?>>> {
        val columns = existingColumns(database, table).filter(allowed::contains)
        if (!columns.containsAll(setOf("battle_id", "side", "pos"))) return emptyMap()
        val placeholders = battleIds.joinToString(",") { "?" }
        val selected = columns.joinToString(",")
        val identity = if (table == "battle_heroes") "hero_id" else "skill_id"
        val sql = "SELECT " + selected + " FROM " + table +
            " WHERE battle_id IN (" + placeholders + ")" +
            " ORDER BY battle_id,side,pos," + identity
        return database.rawQuery(
            sql,
            battleIds.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            buildMap<Long, MutableList<Map<String, Any?>>> {
                while (cursor.moveToNext()) {
                    val row = cursor.toMap(columns)
                    val battleId = row.getValue("battle_id") as Long
                    getOrPut(battleId) { mutableListOf() }.add(row - "battle_id")
                }
            }
        }
    }

    private fun existingColumns(
        database: SQLiteDatabase,
        table: String,
    ): List<String> = database.rawQuery("PRAGMA table_info(\"" + table + "\")", null).use { cursor ->
        val name = cursor.getColumnIndexOrThrow("name")
        buildList { while (cursor.moveToNext()) add(cursor.getString(name)) }
    }

    private fun Cursor.toMap(columns: List<String>): Map<String, Any?> =
        buildMap {
            columns.forEachIndexed { index, name ->
                put(
                    name,
                    when (getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                        Cursor.FIELD_TYPE_STRING -> getString(index)
                        Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> error("Blob fields are not allowed")
                        else -> error("Unsupported SQLite field type")
                    },
                )
            }
        }

    companion object {
        private const val MAX_BATCH = 200
        private val DATABASE_NAME = Regex("[A-Za-z0-9_.-]+\\.db")
        private val REQUIRED_TABLES = setOf("battles_v2", "battle_heroes", "battle_skills")
        private val BATTLE_FIELDS = setOf(
            "battle_id", "time", "time_str", "result", "result_desc", "fight_type",
            "wid", "wid_name", "wid_code", "atk_name", "atk_uid", "atk_union",
            "atk_unionid", "atk_power", "atk_gongxun", "atk_hp", "def_name",
            "def_uid", "def_union", "def_unionid", "def_level", "def_power",
            "def_gongxun", "def_hp", "weather", "in_night", "is_npc", "is_ai",
            "block_id", "city_type", "borrow_land", "garrison",
            "first_occupy_lvn_land", "atk_team_id", "def_team_id", "atk_advance",
            "def_advance", "atk_hero_type", "def_hero_type", "atk_gear_info",
            "def_gear_info", "all_skill_info", "attack_all_hero_info",
            "defend_all_hero_info", "attack_all_sub_hero_info",
            "defend_all_sub_hero_info", "attack_support_user_info",
            "defend_support_user_info", "captured_at",
        )
        private val HERO_FIELDS = setOf(
            "battle_id", "side", "pos", "hero_id", "hero_name", "level", "star",
            "max_hp", "remain_hp", "damage_taken",
        )
        private val SKILL_FIELDS = setOf(
            "battle_id", "side", "pos", "skill_id", "skill_name", "skill_level",
        )
    }
}
