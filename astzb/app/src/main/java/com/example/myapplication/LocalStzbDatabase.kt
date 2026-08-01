package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalStzbDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stzb_packets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                msg_id TEXT NOT NULL,
                data_type INTEGER NOT NULL,
                decode_kind TEXT NOT NULL,
                stream_name TEXT NOT NULL,
                preview TEXT,
                decoded_text TEXT,
                raw_hex TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS battle_notices (
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                time_str TEXT,
                result INTEGER,
                result_desc TEXT,
                fight_type INTEGER,
                wid INTEGER,
                wid_code TEXT,
                atk_name TEXT,
                atk_uid TEXT,
                atk_gongxun INTEGER,
                atk_power INTEGER,
                def_name TEXT,
                def_union TEXT,
                def_level INTEGER,
                def_gongxun INTEGER,
                heroes_json TEXT,
                source_msg_id TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY,
                sender TEXT,
                uid TEXT,
                union_name TEXT,
                text TEXT,
                time INTEGER,
                time_str TEXT,
                source_msg_id TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS battle_monitor_moves (
                team_id INTEGER PRIMARY KEY,
                move_type INTEGER,
                subject_id INTEGER,
                owner_uid INTEGER,
                owner_name TEXT,
                owner_union TEXT,
                from_wid INTEGER,
                to_wid INTEGER,
                current_wid INTEGER,
                from_xy TEXT,
                to_xy TEXT,
                current_xy TEXT,
                start_time INTEGER,
                arrive_time INTEGER,
                speed INTEGER,
                target_type INTEGER DEFAULT 0,
                reside_wid INTEGER DEFAULT 0,
                stay_wid INTEGER DEFAULT 0,
                army_hero_type TEXT,
                morale INTEGER DEFAULT 0,
                buff_ids TEXT,
                battle_show TEXT,
                state_id INTEGER,
                marker INTEGER,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                record_type TEXT NOT NULL,
                record_key TEXT NOT NULL,
                title TEXT,
                subtitle TEXT,
                raw_json TEXT,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL,
                UNIQUE(record_type, record_key)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS team_users (
                uid INTEGER PRIMARY KEY,
                name TEXT,
                contribute_total INTEGER DEFAULT 0,
                contribute_week INTEGER DEFAULT 0,
                pos INTEGER DEFAULT 0,
                wid INTEGER DEFAULT 0,
                power INTEGER DEFAULT 0,
                wuxun INTEGER DEFAULT 0,
                group_name TEXT,
                hero_config_id INTEGER DEFAULT 0,
                team_id INTEGER DEFAULT 0,
                hero_skills TEXT,
                join_time INTEGER DEFAULT 0,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS map_cells (
                wid INTEGER PRIMARY KEY,
                x INTEGER DEFAULT 0,
                y INTEGER DEFAULT 0,
                cell_type INTEGER DEFAULT 0,
                type_name TEXT,
                building_id INTEGER DEFAULT 0,
                owner_name TEXT,
                city_name TEXT,
                parent_wid INTEGER DEFAULT 0,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS battles_v2 (
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                time_str TEXT,
                result INTEGER,
                result_desc TEXT,
                fight_type INTEGER,
                wid INTEGER,
                wid_name TEXT,
                wid_code TEXT,
                atk_name TEXT,
                atk_uid TEXT,
                atk_union TEXT,
                atk_unionid INTEGER,
                atk_power INTEGER DEFAULT 0,
                atk_gongxun INTEGER DEFAULT 0,
                atk_hp INTEGER DEFAULT 0,
                def_name TEXT,
                def_uid TEXT,
                def_union TEXT,
                def_unionid INTEGER,
                def_level INTEGER DEFAULT 0,
                def_power INTEGER DEFAULT 0,
                def_gongxun INTEGER DEFAULT 0,
                def_hp INTEGER DEFAULT 0,
                weather INTEGER DEFAULT 0,
                in_night INTEGER DEFAULT 0,
                is_npc INTEGER DEFAULT 0,
                is_ai INTEGER DEFAULT 0,
                block_id INTEGER DEFAULT 0,
                city_type INTEGER DEFAULT 0,
                borrow_land INTEGER DEFAULT 0,
                garrison INTEGER DEFAULT 0,
                first_occupy_lvn_land INTEGER DEFAULT 0,
                atk_team_id INTEGER DEFAULT 0,
                def_team_id INTEGER DEFAULT 0,
                atk_advance TEXT,
                def_advance TEXT,
                atk_hero_type TEXT,
                def_hero_type TEXT,
                atk_gear_info TEXT,
                def_gear_info TEXT,
                all_skill_info TEXT,
                attack_all_hero_info TEXT,
                defend_all_hero_info TEXT,
                attack_all_sub_hero_info TEXT,
                defend_all_sub_hero_info TEXT,
                attack_support_user_info TEXT,
                defend_support_user_info TEXT,
                source_msg_id TEXT,
                raw_json TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS battle_heroes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                battle_id INTEGER NOT NULL,
                side TEXT NOT NULL,
                pos INTEGER NOT NULL,
                hero_id INTEGER,
                hero_name TEXT,
                level INTEGER DEFAULT 0,
                star INTEGER DEFAULT 0,
                max_hp INTEGER DEFAULT 0,
                remain_hp INTEGER DEFAULT 0,
                damage_taken INTEGER DEFAULT 0,
                UNIQUE(battle_id, side, pos)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS battle_skills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                battle_id INTEGER NOT NULL,
                side TEXT NOT NULL,
                pos INTEGER NOT NULL,
                skill_id INTEGER,
                skill_name TEXT,
                skill_level INTEGER DEFAULT 0,
                UNIQUE(battle_id, side, pos, skill_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS wuxun_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                battle_id INTEGER,
                time INTEGER,
                atk_name TEXT,
                atk_union TEXT,
                atk_level INTEGER DEFAULT 0,
                gongxun INTEGER DEFAULT 0,
                fight_type INTEGER DEFAULT 0,
                result INTEGER DEFAULT 0,
                wid INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS power_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                battle_id INTEGER,
                time INTEGER,
                atk_name TEXT,
                atk_union TEXT,
                atk_level INTEGER DEFAULT 0,
                power INTEGER DEFAULT 0,
                fight_type INTEGER DEFAULT 0,
                result INTEGER DEFAULT 0,
                wid INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                battle_id INTEGER,
                time INTEGER,
                player_name TEXT,
                player_uid TEXT,
                union_name TEXT,
                fight_type INTEGER DEFAULT 0,
                wid INTEGER DEFAULT 0,
                gongxun INTEGER DEFAULT 0,
                result INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS union_list (
                union_id INTEGER PRIMARY KEY,
                name TEXT,
                level INTEGER DEFAULT 0,
                power INTEGER DEFAULT 0,
                force INTEGER DEFAULT 0,
                total_member INTEGER DEFAULT 0,
                occupy_city_value INTEGER DEFAULT 0,
                total_npc_city INTEGER DEFAULT 0,
                region INTEGER DEFAULT 0,
                area INTEGER DEFAULT 0,
                rank INTEGER DEFAULT 0,
                refresh_time INTEGER DEFAULT 0,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS player_power_rank (
                user_id INTEGER PRIMARY KEY,
                role_id TEXT,
                name TEXT,
                power INTEGER DEFAULT 0,
                force INTEGER DEFAULT 0,
                area INTEGER DEFAULT 0,
                region INTEGER DEFAULT 0,
                land_count INTEGER DEFAULT 0,
                fort_count INTEGER DEFAULT 0,
                branch_city_count INTEGER DEFAULT 0,
                shu_cheng_count INTEGER DEFAULT 0,
                refresh_time INTEGER DEFAULT 0,
                rank INTEGER DEFAULT 0,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS player_stats (
                userid INTEGER PRIMARY KEY,
                user_name TEXT,
                city_count INTEGER DEFAULT 0,
                land_count INTEGER DEFAULT 0,
                force_max INTEGER DEFAULT 0,
                power_max INTEGER DEFAULT 0,
                season INTEGER DEFAULT 0,
                wuxun_total INTEGER DEFAULT 0,
                wuxun_cur_week INTEGER DEFAULT 0,
                wuxun_last_week INTEGER DEFAULT 0,
                kill_enemy_count INTEGER DEFAULT 0,
                kill_enemy_cur_week INTEGER DEFAULT 0,
                kill_ai_total INTEGER DEFAULT 0,
                destroy_build INTEGER DEFAULT 0,
                grab_land_count INTEGER DEFAULT 0,
                npc_city_destroy INTEGER DEFAULT 0,
                npc_city_kill INTEGER DEFAULT 0,
                cfg_db_id INTEGER DEFAULT 0,
                raw_json TEXT,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS announcements (
                ann_id INTEGER PRIMARY KEY,
                title TEXT,
                content TEXT,
                pub_time INTEGER DEFAULT 0,
                time_str TEXT,
                ann_type INTEGER DEFAULT 0,
                source_msg_id TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS hero_unlock_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hero_id INTEGER,
                hero_name TEXT,
                unlock_time INTEGER,
                time_str TEXT,
                source_msg_id TEXT,
                captured_at INTEGER NOT NULL,
                UNIQUE(hero_id, unlock_time)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS player_self (
                id INTEGER PRIMARY KEY CHECK(id=1),
                name TEXT,
                force INTEGER DEFAULT 0,
                force_cur INTEGER DEFAULT 0,
                food INTEGER DEFAULT 0,
                wood INTEGER DEFAULT 0,
                speed INTEGER DEFAULT 0,
                march_max INTEGER DEFAULT 0,
                raw_json TEXT,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS zone_players (
                uid INTEGER PRIMARY KEY,
                role_id TEXT,
                name TEXT,
                power INTEGER DEFAULT 0,
                wid INTEGER DEFAULT 0,
                pos_type INTEGER DEFAULT 0,
                last_active INTEGER DEFAULT 0,
                join_time INTEGER DEFAULT 0,
                union_id INTEGER DEFAULT 0,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS db_sync (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                op INTEGER DEFAULT 0,
                table_name TEXT,
                row_id INTEGER DEFAULT 0,
                raw_json TEXT,
                source_msg_id TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS battle_field (
                wid INTEGER PRIMARY KEY,
                attacker_uid INTEGER DEFAULT 0,
                nearby_uids TEXT,
                nearby_count INTEGER DEFAULT 0,
                source_msg_id TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS siege_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                task_time INTEGER DEFAULT 0,
                city_id INTEGER,
                target_groups TEXT,
                target_uids TEXT,
                target_user_num INTEGER DEFAULT 0,
                complete_user_num INTEGER DEFAULT 0,
                queue_count INTEGER DEFAULT 0,
                status INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS march_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                wid INTEGER DEFAULT 0,
                dist INTEGER DEFAULT 0,
                troop_count INTEGER DEFAULT 0,
                troops_json TEXT,
                source_msg_id TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_packets_msg_id ON stzb_packets(msg_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_packets_time ON stzb_packets(captured_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battle_time ON battle_notices(time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_monitor_arrive ON battle_monitor_moves(arrive_time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_records_type ON local_records(record_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battles_time ON battles_v2(time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battles_atk ON battles_v2(atk_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battles_union ON battles_v2(atk_union)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battles_wid ON battles_v2(wid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battle_heroes_battle ON battle_heroes(battle_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battle_skills_battle ON battle_skills(battle_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battle_skills_skill ON battle_skills(skill_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_team_users_group ON team_users(group_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_team_users_power ON team_users(power)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_map_cells_type ON map_cells(cell_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_map_cells_city ON map_cells(city_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_union_list_rank ON union_list(rank)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_player_power_rank_rank ON player_power_rank(rank)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_player_stats_wuxun ON player_stats(wuxun_total)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_announcements_time ON announcements(pub_time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hero_unlock_time ON hero_unlock_log(unlock_time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_zone_players_power ON zone_players(power)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_zone_players_union ON zone_players(union_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_db_sync_table ON db_sync(table_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_battle_field_time ON battle_field(captured_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_siege_tasks_city ON siege_tasks(city_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_march_events_wid ON march_events(wid)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 11) {
            db.execSQL("DROP TABLE IF EXISTS battle_queue")
        }
        onCreate(db)
        if (oldVersion < 12) {
            db.execSQL("UPDATE map_cells SET x = wid / 10000, y = wid % 10000 WHERE wid > 0")
        }
        if (oldVersion < 13) {
            db.execSQL("DELETE FROM battle_notices WHERE result = 6 OR COALESCE(result_desc, '') LIKE '%NPC%'")
            db.execSQL(
                """
                DELETE FROM battle_skills
                WHERE battle_id IN (
                    SELECT battle_id FROM battles_v2
                    WHERE COALESCE(is_npc, 0) != 0 OR result = 6 OR COALESCE(result_desc, '') LIKE '%NPC%'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                DELETE FROM battle_heroes
                WHERE battle_id IN (
                    SELECT battle_id FROM battles_v2
                    WHERE COALESCE(is_npc, 0) != 0 OR result = 6 OR COALESCE(result_desc, '') LIKE '%NPC%'
                )
                """.trimIndent()
            )
            db.execSQL("DELETE FROM battles_v2 WHERE COALESCE(is_npc, 0) != 0 OR result = 6 OR COALESCE(result_desc, '') LIKE '%NPC%'")
        }
        if (oldVersion < 14) {
            db.execSQL("DROP TABLE IF EXISTS siege_tasks_legacy")
            db.execSQL("ALTER TABLE siege_tasks RENAME TO siege_tasks_legacy")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS siege_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT,
                    task_time INTEGER DEFAULT 0,
                    city_id INTEGER,
                    target_groups TEXT,
                    target_uids TEXT,
                    target_user_num INTEGER DEFAULT 0,
                    complete_user_num INTEGER DEFAULT 0,
                    queue_count INTEGER DEFAULT 0,
                    status INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO siege_tasks (
                    id,name,task_time,city_id,target_groups,target_uids,
                    target_user_num,complete_user_num,queue_count,status,created_at,updated_at
                )
                SELECT
                    id,name,0,city_id,target_groups,'',
                    target_user_num,complete_user_num,queue_count,status,created_at,updated_at
                FROM siege_tasks_legacy
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS siege_tasks_legacy")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_siege_tasks_city ON siege_tasks(city_id)")
        }
        if (oldVersion < 15) {
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN target_type INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN reside_wid INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN stay_wid INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN army_hero_type TEXT")
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN morale INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN buff_ids TEXT")
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN battle_show TEXT")
            db.execSQL("ALTER TABLE battle_monitor_moves ADD COLUMN state_id INTEGER")
        }
    }

    companion object {
        private const val DB_NAME = "astzb_local.db"
        private const val DB_VERSION = 15
    }
}

object LocalStzbRepository {
    private var dbHelper: LocalStzbDatabase? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private const val PLAYER_BATTLE_WHERE = "COALESCE(is_npc, 0)=0 AND result != 6 AND COALESCE(result_desc, '') NOT LIKE '%NPC%'"
    private const val PLAYER_BATTLE_WHERE_BV = "COALESCE(bv.is_npc, 0)=0 AND bv.result != 6 AND COALESCE(bv.result_desc, '') NOT LIKE '%NPC%'"
    private const val PLAYER_BATTLE_WHERE_B = "COALESCE(b.is_npc, 0)=0 AND b.result != 6 AND COALESCE(b.result_desc, '') NOT LIKE '%NPC%'"

    @Synchronized
    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = LocalStzbDatabase(context.applicationContext)
            backfillBattleSkills(db())
        }
    }

    private fun db(): SQLiteDatabase = requireNotNull(dbHelper) {
        "LocalStzbRepository.init(context) must be called first"
    }.writableDatabase

    private fun hasLimit(limit: Int): Boolean = limit > 0

    @Synchronized
    fun savePacket(packet: LocalStzbPacket) {
        db().insert(
            "stzb_packets",
            null,
            ContentValues().apply {
                put("msg_id", packet.msgId)
                put("data_type", packet.dataType)
                put("decode_kind", packet.decodeKind)
                put("stream_name", packet.streamName)
                put("preview", packet.preview)
                put("decoded_text", packet.decodedText)
                put("raw_hex", packet.rawHex)
                put("captured_at", System.currentTimeMillis())
            },
        )
    }

    @Synchronized
    fun saveBattleNotice(notice: LocalBattleNotice) {
        if (notice.result == 6 || localResultText(notice.result).contains("NPC", ignoreCase = true)) return
        db().insertWithOnConflict(
            "battle_notices",
            null,
            ContentValues().apply {
                put("battle_id", notice.battleId)
                put("time", notice.time)
                put("time_str", formatTime(notice.time))
                put("result", notice.result)
                put("result_desc", localResultText(notice.result))
                put("fight_type", notice.fightType)
                put("wid", notice.wid)
                put("wid_code", notice.widCode)
                put("atk_name", notice.attackerName)
                put("atk_uid", notice.attackerUid)
                put("atk_gongxun", notice.attackerGongxun)
                put("atk_power", notice.attackerPower)
                put("def_name", notice.defenderName)
                put("def_union", notice.defenderUnion)
                put("def_level", notice.defenderLevel)
                put("def_gongxun", notice.defenderGongxun)
                put("heroes_json", notice.heroesJson)
                put("source_msg_id", notice.sourceMsgId)
                put("captured_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun saveChat(chat: LocalChatMessage) {
        db().insertWithOnConflict(
            "chat_messages",
            null,
            ContentValues().apply {
                put("id", chat.id)
                put("sender", chat.sender)
                put("uid", chat.uid)
                put("union_name", chat.unionName)
                put("text", chat.text)
                put("time", chat.time)
                put("time_str", formatTime(chat.time))
                put("source_msg_id", chat.sourceMsgId)
                put("captured_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    @Synchronized
    fun saveBattleMonitor(snapshot: LocalBattleMonitorSnapshot) {
        val database = db()
        val now = System.currentTimeMillis()
        syncBattleMonitor(database, snapshot, now)
    }

    internal fun syncBattleMonitor(database: SQLiteDatabase, snapshot: LocalBattleMonitorSnapshot, now: Long) {
        database.beginTransaction()
        try {
            database.delete("battle_monitor_moves", null, null)
            snapshot.moves.forEach { move ->
                database.insertWithOnConflict(
                    "battle_monitor_moves",
                    null,
                    ContentValues().apply {
                        put("team_id", move.teamId)
                        put("move_type", move.moveType)
                        put("subject_id", move.subjectId)
                        put("owner_uid", move.ownerUid)
                        put("owner_name", move.ownerName)
                        put("owner_union", move.ownerUnion)
                        put("from_wid", move.fromWid)
                        put("to_wid", move.toWid)
                        put("current_wid", move.currentWid)
                        put("from_xy", move.fromXy)
                        put("to_xy", move.toXy)
                        put("current_xy", move.currentXy)
                        put("start_time", move.startTime)
                        put("arrive_time", move.arriveTime)
                        put("speed", move.speed)
                        put("target_type", move.targetType)
                        put("reside_wid", move.resideWid)
                        put("stay_wid", move.stayWid)
                        put("army_hero_type", move.armyHeroType)
                        put("morale", move.morale)
                        put("buff_ids", move.buffIdList)
                        put("battle_show", move.battleShow)
                        move.stateId?.let { put("state_id", it) }
                        put("marker", snapshot.marker)
                        put("captured_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveRecord(record: LocalRecord) {
        db().insertWithOnConflict(
            "local_records",
            null,
            ContentValues().apply {
                put("record_type", record.type)
                put("record_key", record.key)
                put("title", record.title)
                put("subtitle", record.subtitle)
                put("raw_json", record.rawJson)
                put("source_msg_id", record.sourceMsgId)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun saveRecords(records: List<LocalRecord>) {
        if (records.isEmpty()) return
        val database = db()
        database.beginTransaction()
        try {
            records.forEach { record ->
                database.insertWithOnConflict(
                    "local_records",
                    null,
                    ContentValues().apply {
                        put("record_type", record.type)
                        put("record_key", record.key)
                        put("title", record.title)
                        put("subtitle", record.subtitle)
                        put("raw_json", record.rawJson)
                        put("source_msg_id", record.sourceMsgId)
                        put("updated_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveTeamUsers(users: List<LocalTeamUser>) {
        if (users.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            users.forEach { user ->
                database.insertWithOnConflict(
                    "team_users",
                    null,
                    ContentValues().apply {
                        put("uid", user.uid)
                        put("name", user.name)
                        put("contribute_total", user.contributeTotal)
                        put("contribute_week", user.contributeWeek)
                        put("pos", user.pos)
                        put("wid", user.wid)
                        put("power", user.power)
                        put("wuxun", user.wuxun)
                        put("group_name", user.groupName)
                        put("hero_config_id", user.heroConfigId)
                        put("team_id", user.teamId)
                        put("hero_skills", user.heroSkills)
                        put("join_time", user.joinTime)
                        put("source_msg_id", user.sourceMsgId)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveMapCells(cells: List<LocalMapCell>) {
        if (cells.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            cells.forEach { cell ->
                database.insertWithOnConflict(
                    "map_cells",
                    null,
                    ContentValues().apply {
                        put("wid", cell.wid)
                        put("x", cell.x)
                        put("y", cell.y)
                        put("cell_type", cell.cellType)
                        put("type_name", cell.typeName)
                        put("building_id", cell.buildingId)
                        put("owner_name", cell.ownerName)
                        put("city_name", cell.cityName)
                        put("parent_wid", cell.parentWid)
                        put("source_msg_id", cell.sourceMsgId)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveFullBattles(battles: List<LocalFullBattle>) {
        if (battles.isEmpty()) return
        val database = db()
        val now = System.currentTimeMillis()
        database.beginTransaction()
        try {
            battles.forEach { battle ->
                if (!battle.isPlayerBattle()) return@forEach
                database.insertWithOnConflict(
                    "battles_v2",
                    null,
                    ContentValues().apply {
                        put("battle_id", battle.battleId)
                        put("time", battle.time)
                        put("time_str", formatTime(battle.time))
                        put("result", battle.result)
                        put("result_desc", localResultText(battle.result))
                        put("fight_type", battle.fightType)
                        put("wid", battle.wid)
                        put("wid_name", battle.widName)
                        put("wid_code", battle.widCode)
                        put("atk_name", battle.attackerName)
                        put("atk_uid", battle.attackerUid)
                        put("atk_union", battle.attackerUnion)
                        put("atk_unionid", battle.attackerUnionId)
                        put("atk_power", battle.attackerPower)
                        put("atk_gongxun", battle.attackerGongxun)
                        put("atk_hp", battle.attackerHp)
                        put("def_name", battle.defenderName)
                        put("def_uid", battle.defenderUid)
                        put("def_union", battle.defenderUnion)
                        put("def_unionid", battle.defenderUnionId)
                        put("def_level", battle.defenderLevel)
                        put("def_power", battle.defenderPower)
                        put("def_gongxun", battle.defenderGongxun)
                        put("def_hp", battle.defenderHp)
                        put("weather", battle.weather)
                        put("in_night", battle.inNight)
                        put("is_npc", battle.isNpc)
                        put("is_ai", battle.isAi)
                        put("block_id", battle.blockId)
                        put("city_type", battle.cityType)
                        put("borrow_land", battle.borrowLand)
                        put("garrison", battle.garrison)
                        put("first_occupy_lvn_land", battle.firstOccupyLvnLand)
                        put("atk_team_id", battle.attackerTeamId)
                        put("def_team_id", battle.defenderTeamId)
                        put("atk_advance", battle.attackerAdvance)
                        put("def_advance", battle.defenderAdvance)
                        put("atk_hero_type", battle.attackerHeroType)
                        put("def_hero_type", battle.defenderHeroType)
                        put("atk_gear_info", battle.attackerGearInfo)
                        put("def_gear_info", battle.defenderGearInfo)
                        put("all_skill_info", battle.allSkillInfo)
                        put("attack_all_hero_info", battle.attackAllHeroInfo)
                        put("defend_all_hero_info", battle.defendAllHeroInfo)
                        put("attack_all_sub_hero_info", battle.attackAllSubHeroInfo)
                        put("defend_all_sub_hero_info", battle.defendAllSubHeroInfo)
                        put("attack_support_user_info", battle.attackSupportUserInfo)
                        put("defend_support_user_info", battle.defendSupportUserInfo)
                        put("source_msg_id", battle.sourceMsgId)
                        put("raw_json", battle.rawJson)
                        put("captured_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )

                database.delete("battle_heroes", "battle_id=?", arrayOf(battle.battleId.toString()))
                (battle.attackerHeroes + battle.defenderHeroes).forEach { hero ->
                    database.insertWithOnConflict(
                        "battle_heroes",
                        null,
                        ContentValues().apply {
                            put("battle_id", battle.battleId)
                            put("side", hero.side)
                            put("pos", hero.pos)
                            put("hero_id", hero.heroId)
                            put("hero_name", hero.heroName)
                            put("level", hero.level)
                            put("star", hero.star)
                            put("max_hp", hero.maxHp)
                            put("remain_hp", hero.remainHp)
                            put("damage_taken", hero.damageTaken)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                database.delete("battle_skills", "battle_id=?", arrayOf(battle.battleId.toString()))
                parseBattleSkills(battle).forEach { skill ->
                    database.insertWithOnConflict(
                        "battle_skills",
                        null,
                        ContentValues().apply {
                            put("battle_id", skill.battleId)
                            put("side", skill.side)
                            put("pos", skill.pos)
                            put("skill_id", skill.skillId)
                            put("skill_name", skill.skillName)
                            put("skill_level", skill.skillLevel)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }

                if (battle.attackerGongxun > 0) {
                    database.insert(
                        "wuxun_log",
                        null,
                        ContentValues().apply {
                            put("battle_id", battle.battleId)
                            put("time", battle.time)
                            put("atk_name", battle.attackerName)
                            put("atk_union", battle.attackerUnion)
                            put("atk_level", battle.defenderLevel)
                            put("gongxun", battle.attackerGongxun)
                            put("fight_type", battle.fightType)
                            put("result", battle.result)
                            put("wid", battle.wid)
                        },
                    )
                }
                if (battle.attackerPower > 0) {
                    database.insert(
                        "power_log",
                        null,
                        ContentValues().apply {
                            put("battle_id", battle.battleId)
                            put("time", battle.time)
                            put("atk_name", battle.attackerName)
                            put("atk_union", battle.attackerUnion)
                            put("atk_level", battle.defenderLevel)
                            put("power", battle.attackerPower)
                            put("fight_type", battle.fightType)
                            put("result", battle.result)
                            put("wid", battle.wid)
                        },
                    )
                }
                database.insert(
                    "attendance",
                    null,
                    ContentValues().apply {
                        put("battle_id", battle.battleId)
                        put("time", battle.time)
                        put("player_name", battle.attackerName)
                        put("player_uid", battle.attackerUid)
                        put("union_name", battle.attackerUnion)
                        put("fight_type", battle.fightType)
                        put("wid", battle.wid)
                        put("gongxun", battle.attackerGongxun)
                        put("result", battle.result)
                    },
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun loadBattleNotices(limit: Int = 50): List<LocalBattleNotice> {
        return db().rawQuery(
            """
            SELECT battle_id,time,result,fight_type,wid,wid_code,atk_name,atk_uid,
                   atk_gongxun,atk_power,def_name,def_union,def_level,def_gongxun,
                   heroes_json,source_msg_id
            FROM battle_notices
             WHERE result != 6 AND COALESCE(result_desc, '') NOT LIKE '%NPC%'
            ORDER BY time DESC, captured_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalBattleNotice(
                            battleId = c.int("battle_id"),
                            time = c.long("time"),
                            result = c.int("result"),
                            fightType = c.int("fight_type"),
                            wid = c.int("wid"),
                            widCode = c.string("wid_code"),
                            attackerName = c.string("atk_name"),
                            attackerUid = c.string("atk_uid"),
                            attackerGongxun = c.int("atk_gongxun"),
                            attackerPower = c.int("atk_power"),
                            defenderName = c.string("def_name"),
                            defenderUnion = c.string("def_union"),
                            defenderLevel = c.int("def_level"),
                            defenderGongxun = c.int("def_gongxun"),
                            heroesJson = c.string("heroes_json"),
                            sourceMsgId = c.string("source_msg_id"),
                        )
                    )
                }
            }
        }
    }

    fun loadFullBattles(limit: Int = 50): List<LocalFullBattle> {
        return loadFullBattles(LocalBattleFilter(limit = limit))
    }

    fun loadFullBattles(filter: LocalBattleFilter): List<LocalFullBattle> {
        val where = mutableListOf(PLAYER_BATTLE_WHERE)
        val args = mutableListOf<String>()
        if (filter.player.isNotBlank()) {
            where += "(atk_name LIKE ? OR def_name LIKE ?)"
            val value = "%${filter.player.trim()}%"
            args += value
            args += value
        }
        if (filter.unionName.isNotBlank()) {
            where += "(atk_union LIKE ? OR def_union LIKE ?)"
            val value = "%${filter.unionName.trim()}%"
            args += value
            args += value
        }
        filter.fightType?.let {
            where += "fight_type=?"
            args += it.toString()
        }
        filter.result?.let {
            where += "result=?"
            args += it.toString()
        }
        filter.wid?.let {
            where += "wid=?"
            args += it.toString()
        }
        filter.startTime?.let {
            where += "time>=?"
            args += it.toString()
        }
        filter.endTime?.let {
            where += "time<=?"
            args += it.toString()
        }
        val whereSql = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        args += filter.limit.toString()
        return db().rawQuery(
            """
            SELECT battle_id,time,result,fight_type,wid,wid_name,wid_code,
                   atk_name,atk_uid,atk_union,atk_unionid,atk_power,atk_gongxun,atk_hp,
                   def_name,def_uid,def_union,def_unionid,def_level,def_power,def_gongxun,def_hp,
                   weather,in_night,is_npc,is_ai,block_id,city_type,borrow_land,garrison,
                   first_occupy_lvn_land,atk_team_id,def_team_id,atk_advance,def_advance,
                   atk_hero_type,def_hero_type,atk_gear_info,def_gear_info,all_skill_info,
                   attack_all_hero_info,defend_all_hero_info,attack_all_sub_hero_info,
                   defend_all_sub_hero_info,attack_support_user_info,defend_support_user_info,
                   source_msg_id,raw_json
            FROM battles_v2
            $whereSql
            ORDER BY time DESC, captured_at DESC
            LIMIT ?
            """.trimIndent(),
            args.toTypedArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.toFullBattle(emptyList(), emptyList()))
                }
            }
        }
    }

    fun loadFullBattle(battleId: Int): LocalFullBattle? {
        val battle = db().rawQuery(
            """
            SELECT battle_id,time,result,fight_type,wid,wid_name,wid_code,
                   atk_name,atk_uid,atk_union,atk_unionid,atk_power,atk_gongxun,atk_hp,
                   def_name,def_uid,def_union,def_unionid,def_level,def_power,def_gongxun,def_hp,
                   weather,in_night,is_npc,is_ai,block_id,city_type,borrow_land,garrison,
                   first_occupy_lvn_land,atk_team_id,def_team_id,atk_advance,def_advance,
                   atk_hero_type,def_hero_type,atk_gear_info,def_gear_info,all_skill_info,
                   attack_all_hero_info,defend_all_hero_info,attack_all_sub_hero_info,
                   defend_all_sub_hero_info,attack_support_user_info,defend_support_user_info,
                   source_msg_id,raw_json
            FROM battles_v2
            WHERE battle_id=? AND $PLAYER_BATTLE_WHERE
            """.trimIndent(),
            arrayOf(battleId.toString()),
        ).useCursor { c ->
            if (c.moveToFirst()) c.toFullBattle(emptyList(), emptyList()) else null
        } ?: return null
        val heroes = loadBattleHeroes(battleId)
        return battle.copy(
            attackerHeroes = heroes.filter { it.side == "atk" },
            defenderHeroes = heroes.filter { it.side == "def" },
        )
    }

    private fun loadBattleHeroes(battleId: Int): List<LocalBattleHero> {
        return db().rawQuery(
            """
            SELECT battle_id,side,pos,hero_id,hero_name,level,star,max_hp,remain_hp,damage_taken
            FROM battle_heroes
            WHERE battle_id=?
            ORDER BY side,pos
            """.trimIndent(),
            arrayOf(battleId.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalBattleHero(
                            battleId = c.int("battle_id"),
                            side = c.string("side"),
                            pos = c.int("pos"),
                            heroId = c.long("hero_id"),
                            heroName = c.string("hero_name"),
                            level = c.int("level"),
                            star = c.int("star"),
                            maxHp = c.int("max_hp"),
                            remainHp = c.int("remain_hp"),
                            damageTaken = c.int("damage_taken"),
                        )
                    )
                }
            }
        }
    }

    private fun loadBattleSkills(battleId: Int, side: String, pos: Int): List<LocalBattleSkill> {
        return db().rawQuery(
            """
            SELECT battle_id,side,pos,skill_id,skill_name,skill_level
            FROM battle_skills
            WHERE battle_id=? AND side=? AND pos=?
            ORDER BY skill_id
            """.trimIndent(),
            arrayOf(battleId.toString(), side, pos.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalBattleSkill(
                            battleId = c.int("battle_id"),
                            side = c.string("side"),
                            pos = c.int("pos"),
                            skillId = c.long("skill_id"),
                            skillName = c.string("skill_name"),
                            skillLevel = c.int("skill_level"),
                        )
                    )
                }
            }
        }
    }

    fun loadBattleNotice(battleId: Int): LocalBattleNotice? {
        return db().rawQuery(
            """
            SELECT battle_id,time,result,fight_type,wid,wid_code,atk_name,atk_uid,
                   atk_gongxun,atk_power,def_name,def_union,def_level,def_gongxun,
                   heroes_json,source_msg_id
            FROM battle_notices
             WHERE battle_id=? AND result != 6 AND COALESCE(result_desc, '') NOT LIKE '%NPC%'
            """.trimIndent(),
            arrayOf(battleId.toString()),
        ).useCursor { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                LocalBattleNotice(
                    battleId = c.int("battle_id"),
                    time = c.long("time"),
                    result = c.int("result"),
                    fightType = c.int("fight_type"),
                    wid = c.int("wid"),
                    widCode = c.string("wid_code"),
                    attackerName = c.string("atk_name"),
                    attackerUid = c.string("atk_uid"),
                    attackerGongxun = c.int("atk_gongxun"),
                    attackerPower = c.int("atk_power"),
                    defenderName = c.string("def_name"),
                    defenderUnion = c.string("def_union"),
                    defenderLevel = c.int("def_level"),
                    defenderGongxun = c.int("def_gongxun"),
                    heroesJson = c.string("heroes_json"),
                    sourceMsgId = c.string("source_msg_id"),
                )
            }
        }
    }

    fun loadMonitorMoves(limit: Int = 50): List<LocalTeamMove> {
        val sql = buildString {
            append(
                """
                SELECT team_id,move_type,subject_id,owner_uid,owner_name,owner_union,
                       from_wid,to_wid,current_wid,from_xy,to_xy,current_xy,
                       start_time,arrive_time,speed,target_type,reside_wid,stay_wid,
                       army_hero_type,morale,buff_ids,battle_show,state_id
                FROM battle_monitor_moves
                ORDER BY captured_at DESC, arrive_time DESC, team_id DESC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        val args = if (hasLimit(limit)) arrayOf(limit.toString()) else emptyArray()
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalTeamMove(
                            teamId = c.int("team_id"),
                            moveType = c.int("move_type"),
                            subjectId = c.int("subject_id"),
                            ownerUid = c.int("owner_uid"),
                            ownerName = c.string("owner_name"),
                            ownerUnion = c.string("owner_union"),
                            fromWid = c.int("from_wid"),
                            toWid = c.int("to_wid"),
                            currentWid = c.int("current_wid"),
                            fromXy = c.string("from_xy"),
                            toXy = c.string("to_xy"),
                            currentXy = c.string("current_xy"),
                            startTime = c.long("start_time"),
                            arriveTime = c.long("arrive_time"),
                            speed = c.int("speed"),
                            targetType = c.int("target_type"),
                            resideWid = c.int("reside_wid"),
                            stayWid = c.int("stay_wid"),
                            armyHeroType = c.string("army_hero_type"),
                            morale = c.int("morale"),
                            buffIdList = c.string("buff_ids"),
                            battleShow = c.string("battle_show"),
                            stateId = c.getColumnIndex("state_id").takeIf { it >= 0 && !c.isNull(it) }?.let(c::getInt),
                        )
                    )
                }
            }
        }
    }

    fun loadRecentPackets(limit: Int = 80): List<LocalStzbPacket> {
        return db().rawQuery(
            """
            SELECT msg_id,data_type,decode_kind,stream_name,preview,decoded_text,raw_hex
            FROM stzb_packets
            ORDER BY captured_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalStzbPacket(
                            msgId = c.string("msg_id"),
                            dataType = c.int("data_type"),
                            decodeKind = c.string("decode_kind"),
                            streamName = c.string("stream_name"),
                            preview = c.string("preview"),
                            decodedText = c.string("decoded_text"),
                            rawHex = c.string("raw_hex"),
                        )
                    )
                }
            }
        }
    }

    fun loadRecords(type: String, limit: Int = 80): List<LocalRecord> {
        return db().rawQuery(
            """
            SELECT record_type,record_key,title,subtitle,raw_json,source_msg_id
            FROM local_records
            WHERE record_type=?
            ORDER BY updated_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(type, limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalRecord(
                            type = c.string("record_type"),
                            key = c.string("record_key"),
                            title = c.string("title"),
                            subtitle = c.string("subtitle"),
                            rawJson = c.string("raw_json"),
                            sourceMsgId = c.string("source_msg_id"),
                        )
                    )
                }
            }
        }
    }

    fun loadBattleRankings(limit: Int = 50): LocalBattleRankings {
        return LocalBattleRankings(
            players = loadRankingRows(
                """
                SELECT atk_name AS name,
                       atk_union AS group_name,
                       COUNT(1) AS battles,
                       SUM(atk_gongxun) AS value,
                       SUM(CASE WHEN result IN (1,3) THEN 1 ELSE 0 END) AS wins
                FROM battles_v2
            WHERE atk_name != '' AND $PLAYER_BATTLE_WHERE
                GROUP BY atk_name, atk_union
                ORDER BY value DESC, battles DESC
                LIMIT ?
                """.trimIndent(),
                limit,
            ),
            unions = loadRankingRows(
                """
                SELECT atk_union AS name,
                       '' AS group_name,
                       COUNT(1) AS battles,
                       SUM(atk_gongxun) AS value,
                       SUM(CASE WHEN result IN (1,3) THEN 1 ELSE 0 END) AS wins
                FROM battles_v2
            WHERE atk_union != '' AND $PLAYER_BATTLE_WHERE
                GROUP BY atk_union
                ORDER BY value DESC, battles DESC
                LIMIT ?
                """.trimIndent(),
                limit,
            ),
            powers = loadRankingRows(
                """
                SELECT atk_name AS name,
                       atk_union AS group_name,
                       COUNT(1) AS battles,
                       MAX(atk_power) AS value,
                       SUM(CASE WHEN result IN (1,3) THEN 1 ELSE 0 END) AS wins
                FROM battles_v2
            WHERE atk_name != '' AND $PLAYER_BATTLE_WHERE
                GROUP BY atk_name, atk_union
                ORDER BY value DESC, battles DESC
                LIMIT ?
                """.trimIndent(),
                limit,
            ),
        )
    }

    fun loadTeamUsers(name: String = "", group: String = "", limit: Int = 200): List<LocalTeamUser> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (name.isNotBlank()) {
            where += "name LIKE ?"
            args += "%${name.trim()}%"
        }
        if (group.isNotBlank()) {
            where += "group_name=?"
            args += group.trim()
        }
        val whereSql = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        val sql = buildString {
            append(
                """
                SELECT uid,name,contribute_total,contribute_week,pos,wid,power,wuxun,group_name,
                       hero_config_id,team_id,hero_skills,join_time,source_msg_id
                FROM team_users
                $whereSql
                ORDER BY power DESC, wuxun DESC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        if (hasLimit(limit)) args += limit.toString()
        return db().rawQuery(sql, args.toTypedArray()).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toTeamUser())
            }
        }
    }

    fun loadTeamStats(): LocalTeamStats {
        val total = db().rawQuery("SELECT COUNT(1) AS c FROM team_users", emptyArray()).useCursor { c ->
            if (c.moveToFirst()) c.int("c") else 0
        }
        val groups = db().rawQuery(
            """
            SELECT group_name AS name,
                   COUNT(1) AS members,
                   SUM(power) AS total_power,
                   SUM(wuxun) AS total_wuxun,
                   SUM(contribute_week) AS total_week_contribute
            FROM team_users
            GROUP BY group_name
            ORDER BY total_power DESC
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalTeamGroupStat(
                            name = c.string("name").ifBlank { "未分组" },
                            members = c.int("members"),
                            totalPower = c.long("total_power"),
                            totalWuxun = c.long("total_wuxun"),
                            totalWeekContribute = c.long("total_week_contribute"),
                        )
                    )
                }
            }
        }
        return LocalTeamStats(total = total, groups = groups)
    }

    @Synchronized
    fun saveUnionRanks(unions: List<LocalUnionRank>, players: List<LocalPlayerPowerRank>) {
        if (unions.isEmpty() && players.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            unions.forEach { row ->
                database.insertWithOnConflict(
                    "union_list",
                    null,
                    ContentValues().apply {
                        put("union_id", row.unionId)
                        put("name", row.name)
                        put("level", row.level)
                        put("power", row.power)
                        put("force", row.force)
                        put("total_member", row.totalMember)
                        put("occupy_city_value", row.occupyCityValue)
                        put("total_npc_city", row.totalNpcCity)
                        put("region", row.region)
                        put("area", row.area)
                        put("rank", row.rank)
                        put("refresh_time", row.refreshTime)
                        put("source_msg_id", row.sourceMsgId)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            players.forEach { row ->
                database.insertWithOnConflict(
                    "player_power_rank",
                    null,
                    ContentValues().apply {
                        put("user_id", row.userId)
                        put("role_id", row.roleId)
                        put("name", row.name)
                        put("power", row.power)
                        put("force", row.force)
                        put("area", row.area)
                        put("region", row.region)
                        put("land_count", row.landCount)
                        put("fort_count", row.fortCount)
                        put("branch_city_count", row.branchCityCount)
                        put("shu_cheng_count", row.shuChengCount)
                        put("refresh_time", row.refreshTime)
                        put("rank", row.rank)
                        put("source_msg_id", row.sourceMsgId)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun savePlayerStats(stats: LocalPlayerStats) {
        db().insertWithOnConflict(
            "player_stats",
            null,
            ContentValues().apply {
                put("userid", stats.userId)
                put("user_name", stats.userName)
                put("city_count", stats.cityCount)
                put("land_count", stats.landCount)
                put("force_max", stats.forceMax)
                put("power_max", stats.powerMax)
                put("season", stats.season)
                put("wuxun_total", stats.wuxunTotal)
                put("wuxun_cur_week", stats.wuxunCurrentWeek)
                put("wuxun_last_week", stats.wuxunLastWeek)
                put("kill_enemy_count", stats.killEnemyCount)
                put("kill_enemy_cur_week", stats.killEnemyCurrentWeek)
                put("kill_ai_total", stats.killAiTotal)
                put("destroy_build", stats.destroyBuild)
                put("grab_land_count", stats.grabLandCount)
                put("npc_city_destroy", stats.npcCityDestroy)
                put("npc_city_kill", stats.npcCityKill)
                put("cfg_db_id", stats.cfgDbId)
                put("raw_json", stats.rawJson)
                put("source_msg_id", stats.sourceMsgId)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun saveAnnouncements(rows: List<LocalAnnouncement>) {
        if (rows.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            rows.forEach { row ->
                database.insertWithOnConflict(
                    "announcements",
                    null,
                    ContentValues().apply {
                        put("ann_id", row.annId)
                        put("title", row.title)
                        put("content", row.content)
                        put("pub_time", row.pubTime)
                        put("time_str", formatTime(row.pubTime))
                        put("ann_type", row.annType)
                        put("source_msg_id", row.sourceMsgId)
                        put("captured_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveHeroUnlocks(rows: List<LocalHeroUnlock>) {
        if (rows.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            rows.forEach { row ->
                database.insertWithOnConflict(
                    "hero_unlock_log",
                    null,
                    ContentValues().apply {
                        put("hero_id", row.heroId)
                        put("hero_name", row.heroName)
                        put("unlock_time", row.unlockTime)
                        put("time_str", formatTime(row.unlockTime))
                        put("source_msg_id", row.sourceMsgId)
                        put("captured_at", now)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun savePlayerSelf(self: LocalPlayerSelf) {
        db().insertWithOnConflict(
            "player_self",
            null,
            ContentValues().apply {
                put("id", 1)
                put("name", self.name)
                put("force", self.force)
                put("force_cur", self.forceCurrent)
                put("food", self.food)
                put("wood", self.wood)
                put("speed", self.speed)
                put("march_max", self.marchMax)
                put("raw_json", self.rawJson)
                put("source_msg_id", self.sourceMsgId)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun saveZonePlayers(players: List<LocalZonePlayer>) {
        if (players.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            players.forEach { player ->
                database.insertWithOnConflict(
                    "zone_players",
                    null,
                    ContentValues().apply {
                        put("uid", player.uid)
                        put("role_id", player.roleId)
                        put("name", player.name)
                        put("power", player.power)
                        put("wid", player.wid)
                        put("pos_type", player.posType)
                        put("last_active", player.lastActive)
                        put("join_time", player.joinTime)
                        put("union_id", player.unionId)
                        put("source_msg_id", player.sourceMsgId)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveDbSync(rows: List<LocalDbSyncEvent>) {
        if (rows.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            rows.forEach { row ->
                database.insert(
                    "db_sync",
                    null,
                    ContentValues().apply {
                        put("op", row.op)
                        put("table_name", row.tableName)
                        put("row_id", row.rowId)
                        put("raw_json", row.rawJson)
                        put("source_msg_id", row.sourceMsgId)
                        put("captured_at", now)
                    },
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveBattleFields(rows: List<LocalBattleField>) {
        if (rows.isEmpty()) return
        val now = System.currentTimeMillis()
        val database = db()
        database.beginTransaction()
        try {
            rows.forEach { row ->
                database.insertWithOnConflict(
                    "battle_field",
                    null,
                    ContentValues().apply {
                        put("wid", row.wid)
                        put("attacker_uid", row.attackerUid)
                        put("nearby_uids", row.nearbyUids)
                        put("nearby_count", row.nearbyCount)
                        put("source_msg_id", row.sourceMsgId)
                        put("captured_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun saveMarchEvent(row: LocalMarchEvent) {
        db().insert(
            "march_events",
            null,
            ContentValues().apply {
                put("wid", row.wid)
                put("dist", row.dist)
                put("troop_count", row.troopCount)
                put("troops_json", row.troopsJson)
                put("source_msg_id", row.sourceMsgId)
                put("captured_at", System.currentTimeMillis())
            },
        )
    }

    fun loadUnionRanks(limit: Int = 100): List<LocalUnionRank> {
        val sql = buildString {
            append(
                """
                SELECT union_id,name,level,power,force,total_member,occupy_city_value,total_npc_city,
                       region,area,rank,refresh_time,source_msg_id
                FROM union_list
                ORDER BY rank ASC, power DESC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        val args = if (hasLimit(limit)) arrayOf(limit.toString()) else emptyArray()
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toUnionRank())
            }
        }
    }

    fun loadPlayerPowerRanks(limit: Int = 100): List<LocalPlayerPowerRank> {
        val sql = buildString {
            append(
                """
                SELECT user_id,role_id,name,power,force,area,region,land_count,fort_count,
                       branch_city_count,shu_cheng_count,refresh_time,rank,source_msg_id
                FROM player_power_rank
                ORDER BY rank ASC, power DESC, user_id ASC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        val args = if (hasLimit(limit)) arrayOf(limit.toString()) else emptyArray()
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toPlayerPowerRank())
            }
        }
    }

    fun loadPlayerStats(limit: Int = 50): List<LocalPlayerStats> {
        return db().rawQuery(
            """
            SELECT userid,user_name,city_count,land_count,force_max,power_max,season,
                   wuxun_total,wuxun_cur_week,wuxun_last_week,kill_enemy_count,
                   kill_enemy_cur_week,kill_ai_total,destroy_build,grab_land_count,
                   npc_city_destroy,npc_city_kill,cfg_db_id,raw_json,source_msg_id
            FROM player_stats
            ORDER BY wuxun_total DESC, power_max DESC, userid ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toPlayerStats())
            }
        }
    }

    fun loadAnnouncements(limit: Int = 50): List<LocalAnnouncement> {
        return db().rawQuery(
            """
            SELECT ann_id,title,content,pub_time,ann_type,source_msg_id
            FROM announcements
            ORDER BY pub_time DESC, captured_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalAnnouncement(
                            annId = c.long("ann_id"),
                            title = c.string("title"),
                            content = c.string("content"),
                            pubTime = c.long("pub_time"),
                            annType = c.int("ann_type"),
                            sourceMsgId = c.string("source_msg_id"),
                        )
                    )
                }
            }
        }
    }

    fun loadHeroUnlocks(limit: Int = 80): List<LocalHeroUnlock> {
        return db().rawQuery(
            """
            SELECT hero_id,hero_name,unlock_time,source_msg_id
            FROM hero_unlock_log
            ORDER BY unlock_time DESC, hero_id ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalHeroUnlock(
                            heroId = c.long("hero_id"),
                            heroName = c.string("hero_name"),
                            unlockTime = c.long("unlock_time"),
                            sourceMsgId = c.string("source_msg_id"),
                        )
                    )
                }
            }
        }
    }

    fun loadPlayerSelf(): LocalPlayerSelf? {
        return db().rawQuery(
            """
            SELECT name,force,force_cur,food,wood,speed,march_max,raw_json,source_msg_id
            FROM player_self
            WHERE id=1
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c ->
            if (c.moveToFirst()) {
                LocalPlayerSelf(
                    name = c.string("name"),
                    force = c.int("force"),
                    forceCurrent = c.int("force_cur"),
                    food = c.int("food"),
                    wood = c.int("wood"),
                    speed = c.int("speed"),
                    marchMax = c.int("march_max"),
                    rawJson = c.string("raw_json"),
                    sourceMsgId = c.string("source_msg_id"),
                )
            } else {
                null
            }
        }
    }

    fun loadZonePlayers(limit: Int = 100): List<LocalZonePlayer> {
        val sql = buildString {
            append(
                """
                SELECT uid,role_id,name,power,wid,pos_type,last_active,join_time,union_id,source_msg_id
                FROM zone_players
                ORDER BY power DESC, uid ASC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        val args = if (hasLimit(limit)) arrayOf(limit.toString()) else emptyArray()
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toZonePlayer())
            }
        }
    }

    fun loadDbSyncTableStats(limit: Int = 80): List<LocalDbSyncTableStat> {
        return db().rawQuery(
            """
            SELECT table_name,
                   COUNT(1) AS event_count,
                   SUM(CASE WHEN op=1 THEN 1 ELSE 0 END) AS upserts,
                   SUM(CASE WHEN op=2 THEN 1 ELSE 0 END) AS updates,
                   SUM(CASE WHEN op=3 THEN 1 ELSE 0 END) AS deletes,
                   MAX(captured_at) AS last_seen
            FROM db_sync
            GROUP BY table_name
            ORDER BY event_count DESC, last_seen DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalDbSyncTableStat(
                            tableName = c.string("table_name"),
                            eventCount = c.int("event_count"),
                            upserts = c.int("upserts"),
                            updates = c.int("updates"),
                            deletes = c.int("deletes"),
                            lastSeen = c.long("last_seen"),
                        )
                    )
                }
            }
        }
    }

    fun loadBattleFields(limit: Int = 80): List<LocalBattleField> {
        return db().rawQuery(
            """
            SELECT wid,attacker_uid,nearby_uids,nearby_count,source_msg_id
            FROM battle_field
            ORDER BY captured_at DESC, nearby_count DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalBattleField(
                            wid = c.int("wid"),
                            attackerUid = c.long("attacker_uid"),
                            nearbyUids = c.string("nearby_uids"),
                            nearbyCount = c.int("nearby_count"),
                            sourceMsgId = c.string("source_msg_id"),
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun createSiegeTask(
        name: String,
        posRaw: String,
        groups: List<String>,
        taskTime: Long = 0L,
        targetUids: List<Long> = emptyList(),
    ): LocalSiegeTask {
        val database = db()
        val now = System.currentTimeMillis()
        val title = name.trim()
        require(title.isNotBlank()) { "任务名不能为空" }
        val cityId = normalizeTaskPos(posRaw)
        val groupText = groups.map { it.trim() }.filter { it.isNotBlank() && it != "全员" }.distinct().joinToString(",")
        val targetUidText = targetUids.map { it.toString() }.distinct().joinToString(",")
        val targetUsers = countTaskTargetUsers(groupText, targetUidText)
        require(targetUsers > 0) { "目标人数为0，请先同步同盟成员或调整分组" }
        val completeUsers = countTaskCompleteUsers(cityId, groupText, targetUidText)
        database.beginTransaction()
        try {
            val insertedId = database.insert(
                "siege_tasks",
                null,
                ContentValues().apply {
                    put("name", title)
                    put("task_time", taskTime)
                    put("city_id", cityId)
                    put("target_groups", groupText)
                    put("target_uids", targetUidText)
                    put("target_user_num", targetUsers)
                    put("complete_user_num", completeUsers)
                    put("queue_count", 0)
                    put("status", if (completeUsers > 0) 1 else 0)
                    put("created_at", now)
                    put("updated_at", now)
                },
            )
            require(insertedId > 0L) { "创建任务失败" }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return loadSiegeTasks(1).firstOrNull()
            ?: LocalSiegeTask(
                id = 0L,
                name = title,
                taskTime = taskTime,
                cityId = cityId,
                targetGroups = groupText,
                targetUids = targetUidText,
                targetUserNum = targetUsers,
                completeUserNum = completeUsers,
                queueCount = 0,
                status = if (completeUsers > 0) 1 else 0,
                createdAt = now,
                updatedAt = now,
            )
    }

    fun loadSiegeTasks(limit: Int = 80): List<LocalSiegeTask> {
        val sql = buildString {
            append(
                """
                SELECT id,name,task_time,city_id,target_groups,target_uids,target_user_num,complete_user_num,queue_count,status,created_at,updated_at
                FROM siege_tasks
                ORDER BY updated_at DESC, id DESC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        val args = if (hasLimit(limit)) arrayOf(limit.toString()) else emptyArray()
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val cityId = c.int("city_id")
                    val targetGroups = c.string("target_groups")
                    val targetUids = c.string("target_uids")
                    val targetUserNum = countTaskTargetUsers(targetGroups, targetUids)
                    val completeUserNum = countTaskCompleteUsers(cityId, targetGroups, targetUids)
                    add(
                        LocalSiegeTask(
                            id = c.long("id"),
                            name = c.string("name"),
                            taskTime = c.long("task_time"),
                            cityId = cityId,
                            targetGroups = targetGroups,
                            targetUids = targetUids,
                            targetUserNum = targetUserNum,
                            completeUserNum = completeUserNum,
                            queueCount = c.int("queue_count"),
                            status = if (completeUserNum > 0) 1 else c.int("status"),
                            createdAt = c.long("created_at"),
                            updatedAt = c.long("updated_at"),
                        )
                    )
                }
            }
        }
    }

    fun loadSiegeTask(taskId: Long): LocalSiegeTask? {
        return db().rawQuery(
            """
            SELECT id,name,task_time,city_id,target_groups,target_uids,target_user_num,complete_user_num,queue_count,status,created_at,updated_at
            FROM siege_tasks
            WHERE id=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(taskId.toString()),
        ).useCursor { c ->
            if (!c.moveToFirst()) return@useCursor null
            val cityId = c.int("city_id")
            val targetGroups = c.string("target_groups")
            val targetUids = c.string("target_uids")
            LocalSiegeTask(
                id = c.long("id"),
                name = c.string("name"),
                taskTime = c.long("task_time"),
                cityId = cityId,
                targetGroups = targetGroups,
                targetUids = targetUids,
                targetUserNum = countTaskTargetUsers(targetGroups, targetUids),
                completeUserNum = countTaskCompleteUsers(cityId, targetGroups, targetUids),
                queueCount = c.int("queue_count"),
                status = c.int("status"),
                createdAt = c.long("created_at"),
                updatedAt = c.long("updated_at"),
            )
        }
    }

    @Synchronized
    fun deleteSiegeTask(taskId: Long) {
        db().delete("siege_tasks", "id=?", arrayOf(taskId.toString()))
    }

    fun normalizeTaskPos(posRaw: String): Int {
        val text = posRaw.trim()
        require(text.isNotBlank()) { "坐标不能为空" }
        val parts = text.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.size == 2) {
            val x = parts[0].toIntOrNull() ?: throw IllegalArgumentException("X坐标格式错误")
            val y = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Y坐标格式错误")
            x * 10000 + y
        } else {
            text.toIntOrNull() ?: throw IllegalArgumentException("坐标格式错误，请输入 WID 或 X,Y")
        }
    }

    fun loadMarchEvents(limit: Int = 80): List<LocalMarchEvent> {
        return db().rawQuery(
            """
            SELECT wid,dist,troop_count,troops_json,source_msg_id
            FROM march_events
            ORDER BY captured_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalMarchEvent(
                            wid = c.int("wid"),
                            dist = c.int("dist"),
                            troopCount = c.int("troop_count"),
                            troopsJson = c.string("troops_json"),
                            sourceMsgId = c.string("source_msg_id"),
                        )
                    )
                }
            }
        }
    }

    fun loadZonePlayerStats(): LocalZonePlayerStats {
        val database = db()
        val total = database.count("zone_players")
        val topUnions = database.rawQuery(
            """
            SELECT zp.union_id,
                   COALESCE(NULLIF(ul.name, ''), CAST(zp.union_id AS TEXT)) AS union_name,
                   COUNT(1) AS member_count,
                   SUM(zp.power) AS total_power,
                   AVG(zp.power) AS avg_power,
                   MAX(zp.power) AS max_power
            FROM zone_players zp
            LEFT JOIN union_list ul ON ul.union_id = zp.union_id
            WHERE zp.union_id > 0
            GROUP BY zp.union_id, ul.name
            ORDER BY total_power DESC
            LIMIT 30
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalZoneUnionStat(
                            unionId = c.long("union_id"),
                            unionName = c.string("union_name"),
                            memberCount = c.int("member_count"),
                            totalPower = c.long("total_power"),
                            avgPower = c.double("avg_power"),
                            maxPower = c.long("max_power"),
                        )
                    )
                }
            }
        }
        return LocalZonePlayerStats(total = total, topUnions = topUnions, topPlayers = loadZonePlayers(50))
    }

    fun loadHeroFrequencies(limit: Int = 100): List<LocalHeroFrequency> {
        return db().rawQuery(
            """
            SELECT hero_name, hero_id, COUNT(1) AS total,
                   SUM(CASE WHEN side='atk' THEN 1 ELSE 0 END) AS atk_count,
                   SUM(CASE WHEN side='def' THEN 1 ELSE 0 END) AS def_count,
                   AVG(damage_taken) AS avgDamage
              FROM battle_heroes bh
              JOIN battles_v2 b ON b.battle_id = bh.battle_id
              WHERE bh.hero_name != '' AND bh.hero_name NOT LIKE '武将%' AND $PLAYER_BATTLE_WHERE_B
            GROUP BY hero_name, hero_id
            ORDER BY total DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalHeroFrequency(
                            heroName = c.string("hero_name"),
                            heroId = c.long("hero_id"),
                            total = c.int("total"),
                            attackCount = c.int("atk_count"),
                            defendCount = c.int("def_count"),
                            averageDamageTaken = c.double("avgDamage"),
                        )
                    )
                }
            }
        }
    }

    fun loadHeroUsage(side: String = "atk", limit: Int = 50): List<LocalHeroUsage> {
        val normalizedSide = if (side == "def") "def" else "atk"
        val winExpr = if (normalizedSide == "atk") "bv.result IN (1,7,11)" else "bv.result IN (2,6,12)"
        val loseExpr = if (normalizedSide == "atk") "bv.result IN (2,6,12)" else "bv.result IN (1,7,11)"
        return db().rawQuery(
            """
            SELECT bh.hero_name,
                   COUNT(1) AS count,
                   SUM(CASE WHEN $winExpr THEN 1 ELSE 0 END) AS wins,
                   SUM(CASE WHEN NOT ($winExpr) AND NOT ($loseExpr) THEN 1 ELSE 0 END) AS draws,
                   MAX(bh.level) AS max_level
            FROM battle_heroes bh
            JOIN battles_v2 bv ON bv.battle_id = bh.battle_id
              WHERE bh.side=? AND bh.hero_name != '' AND bh.hero_name NOT LIKE '武将%' AND $PLAYER_BATTLE_WHERE_BV
            GROUP BY bh.hero_name
            ORDER BY count DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(normalizedSide, limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val count = c.int("count")
                    val wins = c.int("wins")
                    val draws = c.int("draws")
                    add(
                        LocalHeroUsage(
                            heroName = c.string("hero_name"),
                            count = count,
                            wins = wins,
                            draws = draws,
                            maxLevel = c.int("max_level"),
                            winRate = if (count > 0) (wins + draws * 0.5) * 100.0 / count else 0.0,
                        )
                    )
                }
            }
        }
    }

    fun loadHeroComboWinRates(minCount: Int = 2, limit: Int = 80): List<LocalHeroComboWinRate> {
        val rows = db().rawQuery(
            """
            SELECT bv.battle_id, bv.result, GROUP_CONCAT(bh.hero_name) AS heroes
            FROM battles_v2 bv
            JOIN battle_heroes bh ON bh.battle_id = bv.battle_id AND bh.side='atk'
              WHERE bh.hero_name != '' AND bh.hero_name NOT LIKE '武将%' AND $PLAYER_BATTLE_WHERE_BV
            GROUP BY bv.battle_id, bv.result
            HAVING COUNT(bh.id) >= 2
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.int("result") to c.string("heroes"))
                }
            }
        }
        val stats = linkedMapOf<String, MutableComboStat>()
        rows.forEach { (result, heroText) ->
            val heroes = heroText.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            if (heroes.size < 2) return@forEach
            val combos = buildList {
                for (i in 0 until heroes.size) {
                    for (j in i + 1 until heroes.size) add("${heroes[i]}+${heroes[j]}")
                }
                if (heroes.size >= 3) {
                    for (i in 0 until heroes.size) {
                        for (j in i + 1 until heroes.size) {
                            for (k in j + 1 until heroes.size) add("${heroes[i]}+${heroes[j]}+${heroes[k]}")
                        }
                    }
                }
            }
            combos.forEach { combo ->
                val stat = stats.getOrPut(combo) { MutableComboStat() }
                stat.total += 1
                when {
                    result in setOf(1, 7, 11) -> stat.wins += 1
                    result in setOf(2, 6, 12) -> stat.losses += 1
                    else -> stat.draws += 1
                }
            }
        }
        return stats.mapNotNull { (combo, stat) ->
            if (stat.total < minCount) return@mapNotNull null
            LocalHeroComboWinRate(
                combo = combo,
                total = stat.total,
                wins = stat.wins,
                losses = stat.losses,
                draws = stat.draws,
                winRate = (stat.wins + stat.draws * 0.5) * 100.0 / stat.total,
            )
        }.sortedWith(compareByDescending<LocalHeroComboWinRate> { it.winRate }.thenByDescending { it.total })
            .take(limit)
    }

    fun loadPlayerBattleTeams(limit: Int = 100): List<LocalPlayerBattleTeam> {
        val battleRows = db().rawQuery(
            """
            SELECT bv.battle_id,bv.result,bv.atk_name,bv.atk_union,bv.def_name,bv.def_union,
                   bh.side,GROUP_CONCAT(bh.hero_name) AS heroes,
                   GROUP_CONCAT(bh.hero_id) AS hero_ids,
                   GROUP_CONCAT(COALESCE(bs.skill_name, '')) AS skills
            FROM battles_v2 bv
            JOIN battle_heroes bh ON bh.battle_id = bv.battle_id
            LEFT JOIN battle_skills bs ON bs.battle_id = bh.battle_id AND bs.side = bh.side AND bs.pos = bh.pos
              WHERE bh.hero_name != '' AND bh.hero_name NOT LIKE '武将%' AND $PLAYER_BATTLE_WHERE_BV
            GROUP BY bv.battle_id,bh.side
            HAVING COUNT(bh.id) >= 2
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val side = c.string("side")
                    val player = if (side == "def") c.string("def_name") else c.string("atk_name")
                    val union = if (side == "def") c.string("def_union") else c.string("atk_union")
                    val heroes = c.string("heroes").split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                    val heroIds = c.string("hero_ids").split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("+")
                    val skills = c.string("skills").split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("+")
                    if (player.isNotBlank() && heroes.isNotEmpty()) {
                        add(TeamBattleSeed(player, union, side, heroes.joinToString("+"), heroIds, skills, c.int("result")))
                    }
                }
            }
        }
        val grouped = linkedMapOf<String, MutableTeamStat>()
        battleRows.forEach { row ->
            val key = "${row.player}|${row.unionName}|${row.side}|${row.heroes}|${row.heroIds}|${row.skills}"
            val stat = grouped.getOrPut(key) { MutableTeamStat(row.player, row.unionName, row.side, row.heroes, row.heroIds, row.skills) }
            stat.battles += 1
            val win = if (row.side == "def") row.result in setOf(2, 6, 12) else row.result in setOf(1, 7, 11)
            if (win) stat.wins += 1
        }
        val rows = grouped.values.map {
            LocalPlayerBattleTeam(
                player = it.player,
                unionName = it.unionName,
                side = it.side,
                heroes = it.heroes,
                heroIds = it.heroIds,
                skills = it.skills,
                battles = it.battles,
                wins = it.wins,
                winRate = if (it.battles > 0) it.wins * 100.0 / it.battles else 0.0,
            )
        }.sortedWith(compareByDescending<LocalPlayerBattleTeam> { it.battles }.thenByDescending { it.winRate })
        return if (hasLimit(limit)) rows.take(limit) else rows
    }

    fun loadTeamReport(dim: String = "group", period: String = "all", group: String = "", limit: Int = 120): List<LocalTeamReportRow> {
        val groupByPlayer = dim == "player"
        val (fromTime, toTime) = teamReportPeriodBounds(period)
        val teamGroupWhere = if (group.isBlank()) "" else "WHERE COALESCE(NULLIF(tu.group_name,''), '未分组') = ?"
        val battleWhere = buildList {
            add(PLAYER_BATTLE_WHERE_BV)
            if (fromTime > 0L) add("bv.time >= ?")
            if (toTime > 0L) add("bv.time <= ?")
            if (group.isNotBlank()) add("COALESCE(NULLIF(tu2.group_name,''), '未分组') = ?")
        }.joinToString(" AND ").let { if (it.isBlank()) "" else "WHERE $it" }
        val battleArgs = buildList {
            if (fromTime > 0L) add(fromTime.toString())
            if (toTime > 0L) add(toTime.toString())
            if (group.isNotBlank()) add(group)
        }
        val teamArgs = buildList {
            if (group.isNotBlank()) add(group)
        }
        val limitSql = if (hasLimit(limit)) "\nLIMIT ?" else ""
        val sql = if (groupByPlayer) {
            """
            SELECT
                tu.name AS name,
                COALESCE(NULLIF(tu.group_name,''), '未分组') AS group_name,
                1 AS members,
                COALESCE(ba.battles, 0) AS battles,
                COALESCE(ba.wins, 0) AS wins,
                COALESCE(ba.loses, 0) AS loses,
                COALESCE(ba.draws, 0) AS draws,
                COALESCE(ba.city_battles, 0) AS city_battles,
                COALESCE(ba.city_wins, 0) AS city_wins,
                COALESCE(tu.wuxun, 0) AS total_gongxun,
                COALESCE(tu.wuxun, 0) AS avg_gongxun,
                COALESCE(tu.power, 0) AS avg_power,
                COALESCE(tu.power, 0) AS power,
                CASE
                    WHEN COALESCE(ba.battles, 0) > 0 THEN ROUND((COALESCE(ba.wins, 0) + COALESCE(ba.draws, 0) * 0.5) * 100.0 / ba.battles, 1)
                    ELSE 0
                END AS win_rate
            FROM team_users tu
            LEFT JOIN (
                SELECT
                    bv.atk_name AS player_name,
                    COUNT(*) AS battles,
                    SUM(CASE WHEN bv.result IN (1,7,11) THEN 1 ELSE 0 END) AS wins,
                    SUM(CASE WHEN bv.result IN (2,6,12) THEN 1 ELSE 0 END) AS loses,
                    SUM(CASE WHEN bv.result NOT IN (1,2,6,7,11,12) THEN 1 ELSE 0 END) AS draws,
                    SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) AS city_battles,
                    SUM(CASE WHEN bv.result=1 AND bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) AS city_wins
                FROM battles_v2 bv
                INNER JOIN team_users tu2 ON tu2.name = bv.atk_name
                $battleWhere
                GROUP BY bv.atk_name
            ) ba ON ba.player_name = tu.name
            $teamGroupWhere
            ORDER BY battles DESC, total_gongxun DESC, power DESC, tu.name ASC
            $limitSql
            """.trimIndent()
        } else {
            """
            SELECT
                COALESCE(NULLIF(tu.group_name,''), '未分组') AS name,
                COALESCE(NULLIF(tu.group_name,''), '未分组') AS group_name,
                COUNT(*) AS members,
                COALESCE(SUM(ba.battles), 0) AS battles,
                COALESCE(SUM(ba.wins), 0) AS wins,
                COALESCE(SUM(ba.loses), 0) AS loses,
                COALESCE(SUM(ba.draws), 0) AS draws,
                COALESCE(SUM(ba.city_battles), 0) AS city_battles,
                COALESCE(SUM(ba.city_wins), 0) AS city_wins,
                COALESCE(SUM(COALESCE(tu.wuxun, 0)), 0) AS total_gongxun,
                ROUND(COALESCE(SUM(COALESCE(tu.wuxun, 0)), 0) * 1.0 / COUNT(*), 1) AS avg_gongxun,
                ROUND(COALESCE(SUM(COALESCE(tu.power, 0)), 0) * 1.0 / COUNT(*), 1) AS avg_power,
                0 AS power,
                CASE
                    WHEN COALESCE(SUM(ba.battles), 0) > 0 THEN ROUND((COALESCE(SUM(ba.wins), 0) + COALESCE(SUM(ba.draws), 0) * 0.5) * 100.0 / SUM(ba.battles), 1)
                    ELSE 0
                END AS win_rate
            FROM team_users tu
            LEFT JOIN (
                SELECT
                    bv.atk_name AS player_name,
                    COUNT(*) AS battles,
                    SUM(CASE WHEN bv.result IN (1,7,11) THEN 1 ELSE 0 END) AS wins,
                    SUM(CASE WHEN bv.result IN (2,6,12) THEN 1 ELSE 0 END) AS loses,
                    SUM(CASE WHEN bv.result NOT IN (1,2,6,7,11,12) THEN 1 ELSE 0 END) AS draws,
                    SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) AS city_battles,
                    SUM(CASE WHEN bv.result=1 AND bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) AS city_wins
                FROM battles_v2 bv
                INNER JOIN team_users tu2 ON tu2.name = bv.atk_name
                $battleWhere
                GROUP BY bv.atk_name
            ) ba ON ba.player_name = tu.name
            $teamGroupWhere
            GROUP BY COALESCE(NULLIF(tu.group_name,''), '未分组')
            ORDER BY battles DESC, total_gongxun DESC, members DESC, name ASC
            $limitSql
            """.trimIndent()
        }
        val args = buildList {
            addAll(battleArgs)
            addAll(teamArgs)
            if (hasLimit(limit)) add(limit.toString())
        }.toTypedArray()
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalTeamReportRow(
                            name = c.string("name").ifBlank { "未分组" },
                            groupName = c.string("group_name").ifBlank { "未分组" },
                            members = c.int("members"),
                            battles = c.int("battles"),
                            wins = c.int("wins"),
                            loses = c.int("loses"),
                            draws = c.int("draws"),
                            cityBattles = c.int("city_battles"),
                            cityWins = c.int("city_wins"),
                            totalGongxun = c.long("total_gongxun"),
                            avgGongxun = c.double("avg_gongxun"),
                            avgPower = c.double("avg_power"),
                            power = c.long("power"),
                            winRate = c.double("win_rate"),
                        )
                    )
                }
            }
        }
    }

    fun loadTaskAttendance(limit: Int = 150): List<LocalTaskAttendanceRow> {
        val sql = buildString {
            append(
                """
                SELECT tu.uid,tu.name,COALESCE(NULLIF(tu.group_name,''), '未分组') AS group_name,
                       tu.power,tu.wuxun,
                       COUNT(bv.battle_id) AS battles,
                       SUM(CASE WHEN COALESCE(bv.garrison, 0)=0 THEN 1 ELSE 0 END) AS atk_num,
                       SUM(CASE WHEN COALESCE(bv.garrison, 0)=1 THEN 1 ELSE 0 END) AS dis_num,
                       COUNT(DISTINCT CASE WHEN COALESCE(bv.garrison, 0)=0 THEN bh.hero_id ELSE NULL END) AS atk_team_num,
                       COUNT(DISTINCT CASE WHEN COALESCE(bv.garrison, 0)=1 THEN bh.hero_id ELSE NULL END) AS dis_team_num,
                       SUM(COALESCE(bv.atk_gongxun, 0)) AS gongxun,
                       MAX(bv.time) AS last_battle_time
                FROM team_users tu
                 LEFT JOIN battles_v2 bv ON bv.atk_name = tu.name AND $PLAYER_BATTLE_WHERE_BV
                LEFT JOIN battle_heroes bh ON bh.battle_id = bv.battle_id AND bh.side='atk' AND bh.pos=0
                GROUP BY tu.uid,tu.name,group_name,tu.power,tu.wuxun
                ORDER BY battles ASC, gongxun ASC, tu.power DESC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        val args = if (hasLimit(limit)) arrayOf(limit.toString()) else emptyArray()
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val battles = c.int("battles")
                    add(
                        LocalTaskAttendanceRow(
                            uid = c.long("uid"),
                            name = c.string("name"),
                            groupName = c.string("group_name"),
                            power = c.int("power"),
                            wuxun = c.int("wuxun"),
                            battles = battles,
                            atkNum = c.int("atk_num"),
                            disNum = c.int("dis_num"),
                            atkTeamNum = c.int("atk_team_num"),
                            disTeamNum = c.int("dis_team_num"),
                            gongxun = c.long("gongxun"),
                            lastBattleTime = c.long("last_battle_time"),
                            status = if (battles > 0) "已参战" else "缺勤",
                        )
                    )
                }
            }
        }
    }

    fun loadTaskAttendanceForCity(cityId: Int, targetGroups: String = "", limit: Int = 150): List<LocalTaskAttendanceRow> {
        return loadTaskAttendanceRows(cityId, targetGroups, "", limit)
    }

    fun loadTaskAttendanceForTask(taskId: Long, limit: Int = 150): List<LocalTaskAttendanceRow> {
        val task = loadSiegeTask(taskId) ?: return emptyList()
        return loadTaskAttendanceRows(task.cityId, task.targetGroups, task.targetUids, limit)
    }

    fun loadTaskBattles(taskId: Long, limit: Int = 200): List<LocalTaskBattleRow> {
        val task = loadSiegeTask(taskId) ?: return emptyList()
        val members = loadTaskTargetUsers(task.targetGroups, task.targetUids)
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (members.isEmpty()) return emptyList()
        val placeholders = members.joinToString(",") { "?" }
        val args = buildList {
            add(task.cityId.toString())
            addAll(members)
            if (hasLimit(limit)) add(limit.toString())
        }.toTypedArray()
        val sql = buildString {
            append(
                """
                SELECT
                    bv.battle_id,
                    bv.time,
                    bv.result,
                    bv.atk_name,
                    COALESCE(bv.atk_union, '') AS atk_union,
                    COALESCE(bv.garrison, 0) AS garrison,
                    GROUP_CONCAT(CASE WHEN bh.hero_name != '' THEN bh.hero_name END, ' / ') AS heroes
                FROM battles_v2 bv
                LEFT JOIN battle_heroes bh ON bh.battle_id = bv.battle_id AND bh.side='atk'
                WHERE bv.wid=? AND bv.atk_name IN ($placeholders) AND $PLAYER_BATTLE_WHERE_BV
                GROUP BY bv.battle_id, bv.time, bv.result, bv.atk_name, bv.atk_union, bv.garrison
                ORDER BY bv.time DESC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalTaskBattleRow(
                            battleId = c.int("battle_id"),
                            time = c.long("time"),
                            result = c.int("result"),
                            attackerName = c.string("atk_name"),
                            attackerUnion = c.string("atk_union"),
                            garrison = c.int("garrison"),
                            heroes = c.string("heroes"),
                        )
                    )
                }
            }
        }
    }

    fun loadTaskNearbyPlayers(posRaw: String, limit: Int = 20, group: String = ""): List<LocalNearbyTaskPlayer> {
        val cityId = normalizeTaskPos(posRaw)
        val tx = cityId / 10000
        val ty = cityId % 10000
        val where = if (group.isBlank()) {
            "WHERE wid IS NOT NULL AND wid != 0"
        } else {
            "WHERE wid IS NOT NULL AND wid != 0 AND COALESCE(NULLIF(group_name,''), '未分组')=?"
        }
        val args = if (group.isBlank()) emptyArray() else arrayOf(group)
        return db().rawQuery(
            """
            SELECT uid,name,COALESCE(NULLIF(group_name,''), '未分组') AS group_name,wid,power
            FROM team_users
            $where
            ORDER BY power DESC
            """.trimIndent(),
            args,
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val wid = c.int("wid")
                    val px = wid / 10000
                    val py = wid % 10000
                    val dx = (px - tx).toDouble()
                    val dy = (py - ty).toDouble()
                    add(
                        LocalNearbyTaskPlayer(
                            uid = c.long("uid"),
                            name = c.string("name"),
                            groupName = c.string("group_name"),
                            wid = wid,
                            power = c.int("power"),
                            distance = kotlin.math.sqrt(dx * dx + dy * dy),
                        )
                    )
                }
            }.sortedBy { it.distance }.take(limit)
        }
    }

    fun loadTaskGroups(): List<String> {
        return db().rawQuery(
            """
            SELECT DISTINCT COALESCE(NULLIF(group_name,''), '未分组') AS group_name
            FROM team_users
            ORDER BY group_name ASC
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.string("group_name")
                    if (name.isNotBlank()) add(name)
                }
            }
        }
    }

    @Synchronized
    fun refreshSiegeTaskStatistics(taskId: Long): LocalTaskStatisticSummary {
        val task = loadSiegeTask(taskId) ?: error("任务不存在")
        val rows = loadTaskAttendanceRows(task.cityId, task.targetGroups, task.targetUids, 0)
        val complete = rows.count { it.atkNum > 0 || it.disNum > 0 }
        val target = rows.size
        val atkCount = rows.sumOf { it.atkNum }
        val disCount = rows.sumOf { it.disNum }
        db().update(
            "siege_tasks",
            ContentValues().apply {
                put("target_user_num", target)
                put("complete_user_num", complete)
                put("status", if (complete > 0) 1 else 0)
                put("updated_at", System.currentTimeMillis())
            },
            "id=?",
            arrayOf(taskId.toString()),
        )
        return LocalTaskStatisticSummary(
            targetUsers = target,
            completeUsers = complete,
            atkCount = atkCount,
            disCount = disCount,
        )
    }

    private fun loadTaskAttendanceRows(cityId: Int, targetGroups: String = "", targetUids: String = "", limit: Int = 150): List<LocalTaskAttendanceRow> {
        val (where, filterArgs) = buildTaskUserFilter("tu", targetGroups, targetUids)
        val args = buildList {
            add(cityId.toString())
            addAll(filterArgs)
            if (hasLimit(limit)) add(limit.toString())
        }.toTypedArray()
        val sql = buildString {
            append(
                """
                SELECT tu.uid,tu.name,COALESCE(NULLIF(tu.group_name,''), '未分组') AS group_name,
                       tu.power,tu.wuxun,
                       COUNT(bv.battle_id) AS battles,
                       SUM(CASE WHEN COALESCE(bv.garrison, 0)=0 AND bv.battle_id IS NOT NULL THEN 1 ELSE 0 END) AS atk_num,
                       SUM(CASE WHEN COALESCE(bv.garrison, 0)=1 AND bv.battle_id IS NOT NULL THEN 1 ELSE 0 END) AS dis_num,
                       COUNT(DISTINCT CASE WHEN COALESCE(bv.garrison, 0)=0 THEN bh.hero_id ELSE NULL END) AS atk_team_num,
                       COUNT(DISTINCT CASE WHEN COALESCE(bv.garrison, 0)=1 THEN bh.hero_id ELSE NULL END) AS dis_team_num,
                       SUM(COALESCE(bv.atk_gongxun, 0)) AS gongxun,
                       MAX(bv.time) AS last_battle_time
                FROM team_users tu
                LEFT JOIN battles_v2 bv ON bv.atk_name = tu.name AND bv.wid = ? AND $PLAYER_BATTLE_WHERE_BV
                LEFT JOIN battle_heroes bh ON bh.battle_id = bv.battle_id AND bh.side='atk' AND bh.pos=0
                $where
                GROUP BY tu.uid,tu.name,group_name,tu.power,tu.wuxun
                ORDER BY battles ASC, gongxun ASC, tu.power DESC
                """.trimIndent(),
            )
            if (hasLimit(limit)) append("\nLIMIT ?")
        }
        return db().rawQuery(sql, args).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val battles = c.int("battles")
                    add(
                        LocalTaskAttendanceRow(
                            uid = c.long("uid"),
                            name = c.string("name"),
                            groupName = c.string("group_name"),
                            power = c.int("power"),
                            wuxun = c.int("wuxun"),
                            battles = battles,
                            atkNum = c.int("atk_num"),
                            disNum = c.int("dis_num"),
                            atkTeamNum = c.int("atk_team_num"),
                            disTeamNum = c.int("dis_team_num"),
                            gongxun = c.long("gongxun"),
                            lastBattleTime = c.long("last_battle_time"),
                            status = if (battles > 0) "已参战" else "缺勤",
                        )
                    )
                }
            }
        }
    }

    fun loadStateRegionStats(limit: Int = 80): List<LocalStateRegionStat> {
        return db().rawQuery(
            """
            SELECT region,area,
                   COUNT(1) AS player_count,
                   SUM(power) AS total_power,
                   AVG(power) AS avg_power,
                   MAX(power) AS max_power,
                   SUM(land_count) AS total_lands
            FROM player_power_rank
            GROUP BY region,area
            ORDER BY total_power DESC, player_count DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LocalStateRegionStat(
                            region = c.int("region"),
                            area = c.int("area"),
                            playerCount = c.int("player_count"),
                            totalPower = c.long("total_power"),
                            avgPower = c.double("avg_power"),
                            maxPower = c.long("max_power"),
                            totalLands = c.long("total_lands"),
                        )
                    )
                }
            }
        }
    }

    fun loadMapCells(cellType: Int? = null, cityName: String = "", limit: Int = 200): List<LocalMapCell> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        cellType?.let {
            where += "cell_type=?"
            args += it.toString()
        }
        if (cityName.isNotBlank()) {
            where += "city_name LIKE ?"
            args += "%${cityName.trim()}%"
        }
        val whereSql = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        args += limit.toString()
        return db().rawQuery(
            """
            SELECT wid,x,y,cell_type,type_name,building_id,owner_name,city_name,parent_wid,source_msg_id
            FROM map_cells
            $whereSql
            ORDER BY cell_type DESC, wid
            LIMIT ?
            """.trimIndent(),
            args.toTypedArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toMapCell())
            }
        }
    }

    fun loadMapCellsByWids(wids: List<Int>): List<LocalMapCell> {
        val ids = wids.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return db().rawQuery(
            """
            SELECT wid,x,y,cell_type,type_name,building_id,owner_name,city_name,parent_wid,source_msg_id
            FROM map_cells
            WHERE wid IN ($placeholders)
            ORDER BY wid
            """.trimIndent(),
            ids.map { it.toString() }.toTypedArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toMapCell())
            }
        }
    }

    fun loadMapStats(): LocalMapStats {
        val total = db().rawQuery("SELECT COUNT(1) AS c FROM map_cells", emptyArray()).useCursor { c ->
            if (c.moveToFirst()) c.int("c") else 0
        }
        val named = db().rawQuery(
            """
            SELECT COUNT(1) AS c
            FROM map_cells
            WHERE city_name != '' AND city_name IS NOT NULL AND city_name != 'None'
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c -> if (c.moveToFirst()) c.int("c") else 0 }
        val typeStats = db().rawQuery(
            """
            SELECT cell_type, type_name, COUNT(1) AS c
            FROM map_cells
            GROUP BY cell_type, type_name
            ORDER BY c DESC
            LIMIT 30
            """.trimIndent(),
            emptyArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(LocalMapTypeStat(c.int("cell_type"), c.string("type_name"), c.int("c")))
                }
            }
        }
        return LocalMapStats(totalCells = total, namedCities = named, typeStats = typeStats)
    }

    fun load13A2TeamInsight(
        teamId: Int,
        ownerName: String = "",
        relatedWids: List<Int> = emptyList(),
        armyHeroType: String = "",
    ): Local13A2TeamInsight {
        if (teamId <= 0) return Local13A2TeamInsight.empty()
        val candidateTeamIds = buildTeamIdCandidates(teamId, relatedWids)
        val idPlaceholders = candidateTeamIds.joinToString(",") { "?" }
        val name = ownerName.trim()
        val matchSql = "atk_team_id IN ($idPlaceholders) OR def_team_id IN ($idPlaceholders)"
        val ownerMatchSql = if (name.isNotBlank()) " OR atk_name=? OR def_name=?" else ""
        val args = buildList {
            addAll(candidateTeamIds.map { it.toString() })
            addAll(candidateTeamIds.map { it.toString() })
            if (name.isNotBlank()) {
                add(name)
                add(name)
            }
        }.toTypedArray()
        val rows = db().rawQuery(
            """
            SELECT battle_id,time,time_str,result,atk_team_id,def_team_id,atk_name,def_name,
                   atk_hero_type,def_hero_type,is_npc
            FROM battles_v2
            WHERE (($matchSql)$ownerMatchSql) AND $PLAYER_BATTLE_WHERE
            ORDER BY time DESC, battle_id DESC
            LIMIT 120
            """.trimIndent(),
            args,
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Local13A2BattleSeed(
                            battleId = c.int("battle_id"),
                            time = c.long("time"),
                            timeStr = c.string("time_str"),
                            result = c.int("result"),
                            atkTeamId = c.int("atk_team_id"),
                            defTeamId = c.int("def_team_id"),
                            atkName = c.string("atk_name"),
                            defName = c.string("def_name"),
                            atkHeroType = c.string("atk_hero_type"),
                            defHeroType = c.string("def_hero_type"),
                            isNpc = c.int("is_npc"),
                        )
                    )
                }
            }
        }
        if (rows.isEmpty()) return Local13A2TeamInsight.empty()

        val nonNpcRows = rows.filter { it.isNpc == 0 }
        val statRows = nonNpcRows.ifEmpty { rows }
        val wins = statRows.count { it.isTeamWin(candidateTeamIds, name) }
        val draws = statRows.count { it.isDraw() }
        val battles = statRows.size
        val loses = (battles - wins - draws).coerceAtLeast(0)
        val stats = Local13A2TeamStats(
            battles = battles,
            wins = wins,
            draws = draws,
            loses = loses,
            winRate = if (battles > 0) (wins + draws * 0.5) * 100.0 / battles else 0.0,
        )

        val recent = rows.take(6).map { row ->
            val side = row.sideFor(candidateTeamIds, name)
            val opponentHeroes = loadBattleHeroes(row.battleId).filter { it.side != side }
            Local13A2RecentBattle(
                battleId = row.battleId,
                time = row.time,
                timeStr = row.timeStr,
                resultText = when {
                    row.isTeamWin(candidateTeamIds, name) -> "胜"
                    row.isDraw() -> "平"
                    else -> "负"
                },
                opponentName = if (side == "atk") row.defName else row.atkName,
                opponentHeroNames = opponentHeroes.map { it.heroName.ifBlank { HeroNameResolver.nameOf(it.heroId) } },
            )
        }

        val matchupMap = linkedMapOf<String, Mutable13A2Matchup>()
        nonNpcRows.forEach { row ->
            val side = row.sideFor(candidateTeamIds, name)
            val opponentHeroes = loadBattleHeroes(row.battleId)
                .filter { it.side != side && it.heroId > 0L }
                .sortedBy { it.pos }
            val key = opponentHeroes.joinToString(",") { it.heroId.toString() }.ifBlank { "-" }
            val stat = matchupMap.getOrPut(key) {
                Mutable13A2Matchup(opponentHeroes.map { it.heroName.ifBlank { HeroNameResolver.nameOf(it.heroId) } })
            }
            when {
                row.isTeamWin(candidateTeamIds, name) -> stat.wins += 1
                row.isDraw() -> stat.draws += 1
                else -> stat.loses += 1
            }
        }
        val matchups = matchupMap.values.map { it.toImmutable() }
        val favored = matchups.filter { it.wins > 0 }.sortedWith(
            compareByDescending<Local13A2Matchup> { it.wins }.thenByDescending { it.winRate }.thenByDescending { it.total }
        ).take(3)
        val countered = matchups.filter { it.loses > 0 }.sortedWith(
            compareByDescending<Local13A2Matchup> { it.loses }.thenBy { it.winRate }.thenByDescending { it.total }
        ).take(3)

        val expectedHeroTypes = normalizeMapArmyHeroTypes(armyHeroType)
        val latest = rows.firstOrNull { row ->
            expectedHeroTypes.isNotEmpty() && row.heroTypesFor(candidateTeamIds, name) == expectedHeroTypes
        } ?: rows.first()
        val side = latest.sideFor(candidateTeamIds, name)
        val lineupHeroes = loadBattleHeroes(latest.battleId)
            .filter { it.side == side }
            .sortedBy { it.pos }
            .map { hero ->
                Local13A2HeroLineup(
                    pos = hero.pos + 1,
                    heroId = hero.heroId,
                    heroName = hero.heroName.ifBlank { HeroNameResolver.nameOf(hero.heroId) },
                    level = hero.level,
                    star = hero.star,
                    skills = loadBattleSkills(latest.battleId, side, hero.pos).map { skill ->
                        Local13A2SkillLineup(
                            skillId = skill.skillId,
                            skillName = skill.skillName.ifBlank { SkillNameResolver.nameOf(skill.skillId) },
                            level = skill.skillLevel,
                        )
                    },
                )
            }
        val lineup = Local13A2Lineup(
            battleId = latest.battleId,
            side = side,
            timeStr = latest.timeStr,
            heroes = lineupHeroes,
        )

        return Local13A2TeamInsight(
            stats = stats,
            lineup = lineup,
            recentBattles = recent,
            favored = favored,
            countered = countered,
        )
    }

    private fun loadRankingRows(sql: String, limit: Int): List<LocalRankingRow> {
        return db().rawQuery(sql, arrayOf(limit.toString())).useCursor { c ->
            buildList {
                while (c.moveToNext()) {
                    val battles = c.int("battles")
                    val wins = c.int("wins")
                    add(
                        LocalRankingRow(
                            name = c.string("name"),
                            groupName = c.string("group_name"),
                            value = c.long("value"),
                            battles = battles,
                            winRate = if (battles > 0) wins * 100.0 / battles else 0.0,
                        )
                    )
                }
            }
        }
    }

    private fun buildTeamIdCandidates(teamId: Int, relatedWids: List<Int>): List<Int> {
        val slot = teamId % 10
        return buildList {
            if (teamId > 0) {
                add(teamId)
            }
            relatedWids.filter { it > 0 }.forEach { wid ->
                add(wid * 10 + slot)
            }
        }.distinct()
    }

    private fun normalizeMapArmyHeroTypes(value: String): List<Int> = value
        .split(';')
        .mapNotNull { segment -> segment.substringAfter(',', "").toIntOrNull() }
        .filter { it > 0 }

    private fun normalizeBattleHeroTypes(value: String): List<Int> = value
        .split(',')
        .mapNotNull(String::toIntOrNull)
        .filter { it > 0 }

    fun countRecordsByType(): Map<String, Int> {
        return db().rawQuery(
            "SELECT record_type, COUNT(1) AS cnt FROM local_records GROUP BY record_type",
            emptyArray(),
        ).useCursor { c ->
            buildMap {
                while (c.moveToNext()) {
                    put(c.string("record_type"), c.int("cnt"))
                }
            }
        }
    }

    fun counts(): LocalDataCounts {
        val database = db()
        return LocalDataCounts(
            packets = database.count("stzb_packets"),
            fullBattles = database.count("battles_v2"),
            battleNotices = database.count("battle_notices"),
            chats = database.count("chat_messages"),
            monitorMoves = database.count("battle_monitor_moves"),
            teamUsers = database.count("team_users"),
            mapCells = database.count("map_cells"),
            unionRanks = database.count("union_list"),
            playerPowerRanks = database.count("player_power_rank"),
            playerStats = database.count("player_stats"),
            announcements = database.count("announcements"),
            heroUnlocks = database.count("hero_unlock_log"),
            playerSelf = database.count("player_self"),
            zonePlayers = database.count("zone_players"),
            dbSync = database.count("db_sync"),
            battleFields = database.count("battle_field"),
            marchEvents = database.count("march_events"),
            localRecords = database.count("local_records"),
        )
    }

    fun exportDatabase(context: Context): File {
        val outDir = File(context.filesDir, "exports")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "astzb_local_${System.currentTimeMillis()}.db")
        synchronized(this) {
            db().rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray()).close()
            File(context.getDatabasePath("astzb_local.db").absolutePath).copyTo(outFile, overwrite = true)
        }
        return outFile
    }

    fun formatTime(ts: Long): String {
        if (ts <= 0L) return ""
        val millis = if (ts < 10_000_000_000L) ts * 1000 else ts
        return timeFormat.format(Date(millis))
    }

    private fun SQLiteDatabase.count(table: String): Int {
        return rawQuery("SELECT COUNT(1) FROM $table", emptyArray()).useCursor { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun LocalFullBattle.isPlayerBattle(): Boolean {
        return isNpc == 0 && result != 6 && !localResultText(result).contains("NPC", ignoreCase = true)
    }

    private fun teamReportPeriodBounds(period: String): Pair<Long, Long> {
        fun startOfDay(calendar: java.util.Calendar): Long {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            return calendar.timeInMillis / 1000
        }
        val now = System.currentTimeMillis() / 1000
        val today = java.util.Calendar.getInstance(Locale.CHINA)
        return when (period) {
            "today" -> startOfDay(today) to now
            "yesterday" -> {
                val end = startOfDay(today) - 1
                today.add(java.util.Calendar.DAY_OF_MONTH, -1)
                startOfDay(today) to end
            }
            "week" -> {
                today.firstDayOfWeek = java.util.Calendar.MONDAY
                today.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                startOfDay(today) to now
            }
            "lastweek" -> {
                today.firstDayOfWeek = java.util.Calendar.MONDAY
                today.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                val thisWeekStart = startOfDay(today)
                today.add(java.util.Calendar.DAY_OF_MONTH, -7)
                startOfDay(today) to (thisWeekStart - 1)
            }
            else -> 0L to 0L
        }
    }

    private fun parseTaskGroups(targetGroups: String): List<String> {
        return targetGroups.split(',', '，', ';', '；', '、')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "全员" }
            .distinct()
    }

    private fun countTaskTargetUsers(targetGroups: String): Int {
        return countTaskTargetUsers(targetGroups, "")
    }

    private fun countTaskTargetUsers(targetGroups: String, targetUids: String): Int {
        val (where, args) = buildTaskUserFilter("team_users", targetGroups, targetUids)
        if (where.isBlank()) {
            return db().rawQuery("SELECT COUNT(1) AS c FROM team_users", emptyArray()).useCursor { c ->
                if (c.moveToFirst()) c.int("c") else 0
            }
        }
        return db().rawQuery(
            """
            SELECT COUNT(1) AS c
            FROM team_users
            $where
            """.trimIndent(),
            args.toTypedArray(),
        ).useCursor { c ->
            if (c.moveToFirst()) c.int("c") else 0
        }
    }

    private fun countTaskCompleteUsers(cityId: Int, targetGroups: String): Int {
        return countTaskCompleteUsers(cityId, targetGroups, "")
    }

    private fun countTaskCompleteUsers(cityId: Int, targetGroups: String, targetUids: String): Int {
        val (where, filterArgs) = buildTaskUserFilter("tu", targetGroups, targetUids)
        val args = buildList {
            add(cityId.toString())
            addAll(filterArgs)
        }.toTypedArray()
        return db().rawQuery(
            """
            SELECT COUNT(1) AS c
            FROM (
                SELECT tu.uid
                FROM team_users tu
                LEFT JOIN battles_v2 bv ON bv.atk_name = tu.name AND bv.wid = ? AND $PLAYER_BATTLE_WHERE_BV
                $where
                GROUP BY tu.uid
                HAVING COUNT(bv.battle_id) > 0
            )
            """.trimIndent(),
            args,
        ).useCursor { c ->
            if (c.moveToFirst()) c.int("c") else 0
        }
    }

    private fun loadTaskTargetUsers(targetGroups: String, targetUids: String): List<LocalTeamUser> {
        val (where, args) = buildTaskUserFilter("team_users", targetGroups, targetUids)
        return db().rawQuery(
            """
            SELECT uid,name,contribute_total,contribute_week,pos,wid,power,wuxun,group_name,
                   hero_config_id,team_id,hero_skills,join_time,source_msg_id,updated_at
            FROM team_users
            $where
            ORDER BY power DESC, wuxun DESC, name ASC
            """.trimIndent(),
            args.toTypedArray(),
        ).useCursor { c ->
            buildList {
                while (c.moveToNext()) add(c.toTeamUser())
            }
        }
    }

    private fun parseTaskUids(text: String): List<Long> {
        return text.split(',', '，', ';', '；', '、', ' ')
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct()
    }

    private fun buildTaskUserFilter(alias: String, targetGroups: String, targetUids: String): Pair<String, List<String>> {
        val uids = parseTaskUids(targetUids)
        if (uids.isNotEmpty()) {
            return "WHERE $alias.uid IN (${uids.joinToString(",") { "?" }})" to uids.map { it.toString() }
        }
        val groups = parseTaskGroups(targetGroups)
        if (groups.isNotEmpty()) {
            return "WHERE COALESCE(NULLIF($alias.group_name,''), '未分组') IN (${groups.joinToString(",") { "?" }})" to groups
        }
        return "" to emptyList()
    }

    private inline fun <T> Cursor.useCursor(block: (Cursor) -> T): T {
        return use { block(it) }
    }

    private fun Cursor.int(name: String): Int = getInt(getColumnIndexOrThrow(name))
    private fun Cursor.long(name: String): Long = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.double(name: String): Double = getDouble(getColumnIndexOrThrow(name))
    private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name)).orEmpty()

    private fun parseBattleSkills(battle: LocalFullBattle): List<LocalBattleSkill> {
        return parseBattleSkills(battle.battleId, battle.allSkillInfo)
    }

    private fun parseBattleSkills(battleId: Int, allSkillInfo: String): List<LocalBattleSkill> {
        val text = allSkillInfo.trim()
        if (text.isBlank()) return emptyList()
        return buildList {
            text.split(';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { part ->
                    val segs = part.split(',').map { it.trim() }
                    val rawPos = segs.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    val side = if (rawPos in 1..3) "atk" else "def"
                    val pos = if (side == "atk") rawPos - 1 else rawPos - 4
                    if (pos !in 0..2) return@forEach
                    var index = 1
                    while (index < segs.size) {
                        val skillId = segs.getOrNull(index)?.toLongOrNull() ?: 0L
                        val level = segs.getOrNull(index + 1)?.toIntOrNull() ?: 0
                        if (skillId > 0L) {
                            add(
                                LocalBattleSkill(
                                      battleId = battleId,
                                    side = side,
                                    pos = pos,
                                    skillId = skillId,
                                    skillName = SkillNameResolver.nameOf(skillId),
                                    skillLevel = level,
                                )
                            )
                        }
                        index += 2
                    }
                }
        }
    }

    private fun backfillBattleSkills(database: SQLiteDatabase) {
        database.beginTransaction()
        try {
            database.rawQuery(
                """
                SELECT battle_id, all_skill_info
                FROM battles_v2
                  WHERE all_skill_info IS NOT NULL AND all_skill_info != '' AND $PLAYER_BATTLE_WHERE
                """.trimIndent(),
                emptyArray(),
            ).useCursor { cursor ->
                while (cursor.moveToNext()) {
                    val battleId = cursor.int("battle_id")
                    val skills = parseBattleSkills(battleId, cursor.string("all_skill_info"))
                    skills.forEach { skill ->
                        database.insertWithOnConflict(
                            "battle_skills",
                            null,
                            ContentValues().apply {
                                put("battle_id", skill.battleId)
                                put("side", skill.side)
                                put("pos", skill.pos)
                                put("skill_id", skill.skillId)
                                put("skill_name", skill.skillName)
                                put("skill_level", skill.skillLevel)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE,
                        )
                    }
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun Cursor.toFullBattle(
        attackerHeroes: List<LocalBattleHero>,
        defenderHeroes: List<LocalBattleHero>,
    ): LocalFullBattle {
        return LocalFullBattle(
            battleId = int("battle_id"),
            time = long("time"),
            result = int("result"),
            fightType = int("fight_type"),
            wid = int("wid"),
            widName = string("wid_name"),
            widCode = string("wid_code"),
            attackerName = string("atk_name"),
            attackerUid = string("atk_uid"),
            attackerUnion = string("atk_union"),
            attackerUnionId = int("atk_unionid"),
            attackerPower = int("atk_power"),
            attackerGongxun = int("atk_gongxun"),
            attackerHp = int("atk_hp"),
            defenderName = string("def_name"),
            defenderUid = string("def_uid"),
            defenderUnion = string("def_union"),
            defenderUnionId = int("def_unionid"),
            defenderLevel = int("def_level"),
            defenderPower = int("def_power"),
            defenderGongxun = int("def_gongxun"),
            defenderHp = int("def_hp"),
            weather = int("weather"),
            inNight = int("in_night"),
            isNpc = int("is_npc"),
            isAi = int("is_ai"),
            blockId = int("block_id"),
            cityType = int("city_type"),
            borrowLand = int("borrow_land"),
            garrison = int("garrison"),
            firstOccupyLvnLand = int("first_occupy_lvn_land"),
            attackerTeamId = int("atk_team_id"),
            defenderTeamId = int("def_team_id"),
            attackerAdvance = string("atk_advance"),
            defenderAdvance = string("def_advance"),
            attackerHeroType = string("atk_hero_type"),
            defenderHeroType = string("def_hero_type"),
            attackerGearInfo = string("atk_gear_info"),
            defenderGearInfo = string("def_gear_info"),
            allSkillInfo = string("all_skill_info"),
            attackAllHeroInfo = string("attack_all_hero_info"),
            defendAllHeroInfo = string("defend_all_hero_info"),
            attackAllSubHeroInfo = string("attack_all_sub_hero_info"),
            defendAllSubHeroInfo = string("defend_all_sub_hero_info"),
            attackSupportUserInfo = string("attack_support_user_info"),
            defendSupportUserInfo = string("defend_support_user_info"),
            sourceMsgId = string("source_msg_id"),
            rawJson = string("raw_json"),
            attackerHeroes = attackerHeroes,
            defenderHeroes = defenderHeroes,
        )
    }

    private fun Cursor.toTeamUser(): LocalTeamUser {
        return LocalTeamUser(
            uid = long("uid"),
            name = string("name"),
            contributeTotal = int("contribute_total"),
            contributeWeek = int("contribute_week"),
            pos = int("pos"),
            wid = int("wid"),
            power = int("power"),
            wuxun = int("wuxun"),
            groupName = string("group_name"),
            heroConfigId = int("hero_config_id"),
            teamId = int("team_id"),
            heroSkills = string("hero_skills"),
            joinTime = long("join_time"),
            sourceMsgId = string("source_msg_id"),
        )
    }

    private fun Cursor.toMapCell(): LocalMapCell {
        return LocalMapCell(
            wid = int("wid"),
            x = int("x"),
            y = int("y"),
            cellType = int("cell_type"),
            typeName = string("type_name"),
            buildingId = int("building_id"),
            ownerName = string("owner_name"),
            cityName = string("city_name"),
            parentWid = int("parent_wid"),
            sourceMsgId = string("source_msg_id"),
        )
    }

    private fun Cursor.toUnionRank(): LocalUnionRank {
        return LocalUnionRank(
            unionId = int("union_id"),
            name = string("name"),
            level = int("level"),
            power = long("power"),
            force = long("force"),
            totalMember = int("total_member"),
            occupyCityValue = int("occupy_city_value"),
            totalNpcCity = int("total_npc_city"),
            region = int("region"),
            area = int("area"),
            rank = int("rank"),
            refreshTime = long("refresh_time"),
            sourceMsgId = string("source_msg_id"),
        )
    }

    private fun Cursor.toPlayerPowerRank(): LocalPlayerPowerRank {
        return LocalPlayerPowerRank(
            userId = long("user_id"),
            roleId = string("role_id"),
            name = string("name"),
            power = long("power"),
            force = long("force"),
            area = int("area"),
            region = int("region"),
            landCount = int("land_count"),
            fortCount = int("fort_count"),
            branchCityCount = int("branch_city_count"),
            shuChengCount = int("shu_cheng_count"),
            refreshTime = long("refresh_time"),
            rank = int("rank"),
            sourceMsgId = string("source_msg_id"),
        )
    }

    private fun Cursor.toPlayerStats(): LocalPlayerStats {
        return LocalPlayerStats(
            userId = long("userid"),
            userName = string("user_name"),
            cityCount = int("city_count"),
            landCount = int("land_count"),
            forceMax = int("force_max"),
            powerMax = int("power_max"),
            season = int("season"),
            wuxunTotal = int("wuxun_total"),
            wuxunCurrentWeek = int("wuxun_cur_week"),
            wuxunLastWeek = int("wuxun_last_week"),
            killEnemyCount = int("kill_enemy_count"),
            killEnemyCurrentWeek = int("kill_enemy_cur_week"),
            killAiTotal = int("kill_ai_total"),
            destroyBuild = int("destroy_build"),
            grabLandCount = int("grab_land_count"),
            npcCityDestroy = int("npc_city_destroy"),
            npcCityKill = int("npc_city_kill"),
            cfgDbId = int("cfg_db_id"),
            rawJson = string("raw_json"),
            sourceMsgId = string("source_msg_id"),
        )
    }

    private fun Cursor.toZonePlayer(): LocalZonePlayer {
        return LocalZonePlayer(
            uid = long("uid"),
            roleId = string("role_id"),
            name = string("name"),
            power = long("power"),
            wid = int("wid"),
            posType = int("pos_type"),
            lastActive = long("last_active"),
            joinTime = long("join_time"),
            unionId = long("union_id"),
            sourceMsgId = string("source_msg_id"),
        )
    }

    private data class MutableComboStat(
        var total: Int = 0,
        var wins: Int = 0,
        var losses: Int = 0,
        var draws: Int = 0,
    )

    private data class TeamBattleSeed(
        val player: String,
        val unionName: String,
        val side: String,
        val heroes: String,
        val heroIds: String,
        val skills: String,
        val result: Int,
    )

    private data class MutableTeamStat(
        val player: String,
        val unionName: String,
        val side: String,
        val heroes: String,
        val heroIds: String,
        val skills: String,
        var battles: Int = 0,
        var wins: Int = 0,
    )

    private data class Local13A2BattleSeed(
        val battleId: Int,
        val time: Long,
        val timeStr: String,
        val result: Int,
        val atkTeamId: Int,
        val defTeamId: Int,
        val atkName: String,
        val defName: String,
        val atkHeroType: String,
        val defHeroType: String,
        val isNpc: Int,
    ) {
        fun sideFor(teamIds: List<Int>, ownerName: String): String {
            return when {
                atkTeamId in teamIds -> "atk"
                defTeamId in teamIds -> "def"
                ownerName.isNotBlank() && atkName == ownerName -> "atk"
                ownerName.isNotBlank() && defName == ownerName -> "def"
                else -> "atk"
            }
        }

        fun isTeamWin(teamId: Int): Boolean {
            return when (sideFor(listOf(teamId), "")) {
                "atk" -> result in setOf(1, 7, 11)
                else -> result in setOf(2, 6, 12)
            }
        }

        fun isTeamWin(teamIds: List<Int>, ownerName: String): Boolean {
            return when (sideFor(teamIds, ownerName)) {
                "atk" -> result in setOf(1, 7, 11)
                else -> result in setOf(2, 6, 12)
            }
        }

        fun isDraw(): Boolean = result !in setOf(1, 2, 6, 7, 11, 12)

        fun heroTypesFor(teamIds: List<Int>, ownerName: String): List<Int> = when (sideFor(teamIds, ownerName)) {
            "def" -> normalizeBattleHeroTypes(defHeroType)
            else -> normalizeBattleHeroTypes(atkHeroType)
        }
    }

    private data class Mutable13A2Matchup(
        val opponentHeroNames: List<String>,
        var wins: Int = 0,
        var draws: Int = 0,
        var loses: Int = 0,
    ) {
        fun toImmutable(): Local13A2Matchup {
            val total = wins + draws + loses
            return Local13A2Matchup(
                opponentHeroNames = opponentHeroNames,
                wins = wins,
                draws = draws,
                loses = loses,
                total = total,
                winRate = if (total > 0) (wins + draws * 0.5) * 100.0 / total else 0.0,
            )
        }
    }
}

data class LocalDataCounts(
    val packets: Int,
    val fullBattles: Int,
    val battleNotices: Int,
    val chats: Int,
    val monitorMoves: Int,
    val teamUsers: Int,
    val mapCells: Int,
    val unionRanks: Int,
    val playerPowerRanks: Int,
    val playerStats: Int,
    val announcements: Int,
    val heroUnlocks: Int,
    val playerSelf: Int,
    val zonePlayers: Int,
    val dbSync: Int,
    val battleFields: Int,
    val marchEvents: Int,
    val localRecords: Int,
)

data class LocalBattleRankings(
    val players: List<LocalRankingRow>,
    val unions: List<LocalRankingRow>,
    val powers: List<LocalRankingRow>,
)

data class LocalBattleFilter(
    val player: String = "",
    val unionName: String = "",
    val fightType: Int? = null,
    val result: Int? = null,
    val wid: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val limit: Int = 50,
)

data class LocalRankingRow(
    val name: String,
    val groupName: String,
    val value: Long,
    val battles: Int,
    val winRate: Double,
)

data class LocalTeamUser(
    val uid: Long,
    val name: String,
    val contributeTotal: Int,
    val contributeWeek: Int,
    val pos: Int,
    val wid: Int,
    val power: Int,
    val wuxun: Int,
    val groupName: String,
    val heroConfigId: Int,
    val teamId: Int,
    val heroSkills: String,
    val joinTime: Long,
    val sourceMsgId: String,
)

data class LocalTeamStats(
    val total: Int,
    val groups: List<LocalTeamGroupStat>,
)

data class LocalTeamGroupStat(
    val name: String,
    val members: Int,
    val totalPower: Long,
    val totalWuxun: Long,
    val totalWeekContribute: Long,
)

data class LocalMapCell(
    val wid: Int,
    val x: Int,
    val y: Int,
    val cellType: Int,
    val typeName: String,
    val buildingId: Int,
    val ownerName: String,
    val cityName: String,
    val parentWid: Int,
    val sourceMsgId: String,
)

data class LocalMapStats(
    val totalCells: Int,
    val namedCities: Int,
    val typeStats: List<LocalMapTypeStat>,
)

data class LocalMapTypeStat(
    val cellType: Int,
    val typeName: String,
    val count: Int,
)

data class LocalZonePlayerStats(
    val total: Int,
    val topUnions: List<LocalZoneUnionStat>,
    val topPlayers: List<LocalZonePlayer>,
)

data class LocalZoneUnionStat(
    val unionId: Long,
    val unionName: String,
    val memberCount: Int,
    val totalPower: Long,
    val avgPower: Double,
    val maxPower: Long,
)

data class LocalHeroFrequency(
    val heroName: String,
    val heroId: Long,
    val total: Int,
    val attackCount: Int,
    val defendCount: Int,
    val averageDamageTaken: Double,
)

data class LocalHeroUsage(
    val heroName: String,
    val count: Int,
    val wins: Int,
    val draws: Int,
    val maxLevel: Int,
    val winRate: Double,
)

data class LocalHeroComboWinRate(
    val combo: String,
    val total: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: Double,
)

data class LocalPlayerBattleTeam(
    val player: String,
    val unionName: String,
    val side: String,
    val heroes: String,
    val heroIds: String,
    val skills: String,
    val battles: Int,
    val wins: Int,
    val winRate: Double,
)

data class LocalTeamReportRow(
    val name: String,
    val groupName: String,
    val members: Int,
    val battles: Int,
    val wins: Int,
    val loses: Int,
    val draws: Int,
    val cityBattles: Int,
    val cityWins: Int,
    val totalGongxun: Long,
    val avgGongxun: Double,
    val avgPower: Double,
    val power: Long,
    val winRate: Double,
)

data class LocalTaskAttendanceRow(
    val uid: Long,
    val name: String,
    val groupName: String,
    val power: Int,
    val wuxun: Int,
    val battles: Int,
    val atkNum: Int,
    val disNum: Int,
    val atkTeamNum: Int,
    val disTeamNum: Int,
    val gongxun: Long,
    val lastBattleTime: Long,
    val status: String,
)

data class LocalSiegeTask(
    val id: Long,
    val name: String,
    val taskTime: Long,
    val cityId: Int,
    val targetGroups: String,
    val targetUids: String,
    val targetUserNum: Int,
    val completeUserNum: Int,
    val queueCount: Int,
    val status: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class LocalTaskStatisticSummary(
    val targetUsers: Int,
    val completeUsers: Int,
    val atkCount: Int,
    val disCount: Int,
)

data class LocalNearbyTaskPlayer(
    val uid: Long,
    val name: String,
    val groupName: String,
    val wid: Int,
    val power: Int,
    val distance: Double,
)

data class LocalTaskBattleRow(
    val battleId: Int,
    val time: Long,
    val result: Int,
    val attackerName: String,
    val attackerUnion: String,
    val garrison: Int,
    val heroes: String,
)

data class LocalStateRegionStat(
    val region: Int,
    val area: Int,
    val playerCount: Int,
    val totalPower: Long,
    val avgPower: Double,
    val maxPower: Long,
    val totalLands: Long,
)

data class LocalUnionRank(
    val unionId: Int,
    val name: String,
    val level: Int,
    val power: Long,
    val force: Long,
    val totalMember: Int,
    val occupyCityValue: Int,
    val totalNpcCity: Int,
    val region: Int,
    val area: Int,
    val rank: Int,
    val refreshTime: Long,
    val sourceMsgId: String,
)

data class LocalPlayerPowerRank(
    val userId: Long,
    val roleId: String,
    val name: String,
    val power: Long,
    val force: Long,
    val area: Int,
    val region: Int,
    val landCount: Int,
    val fortCount: Int,
    val branchCityCount: Int,
    val shuChengCount: Int,
    val refreshTime: Long,
    val rank: Int,
    val sourceMsgId: String,
)

data class LocalPlayerStats(
    val userId: Long,
    val userName: String,
    val cityCount: Int,
    val landCount: Int,
    val forceMax: Int,
    val powerMax: Int,
    val season: Int,
    val wuxunTotal: Int,
    val wuxunCurrentWeek: Int,
    val wuxunLastWeek: Int,
    val killEnemyCount: Int,
    val killEnemyCurrentWeek: Int,
    val killAiTotal: Int,
    val destroyBuild: Int,
    val grabLandCount: Int,
    val npcCityDestroy: Int,
    val npcCityKill: Int,
    val cfgDbId: Int,
    val rawJson: String,
    val sourceMsgId: String,
)

data class LocalAnnouncement(
    val annId: Long,
    val title: String,
    val content: String,
    val pubTime: Long,
    val annType: Int,
    val sourceMsgId: String,
)

data class LocalHeroUnlock(
    val heroId: Long,
    val heroName: String,
    val unlockTime: Long,
    val sourceMsgId: String,
)

data class LocalPlayerSelf(
    val name: String,
    val force: Int,
    val forceCurrent: Int,
    val food: Int,
    val wood: Int,
    val speed: Int,
    val marchMax: Int,
    val rawJson: String,
    val sourceMsgId: String,
)

data class LocalZonePlayer(
    val uid: Long,
    val roleId: String,
    val name: String,
    val power: Long,
    val wid: Int,
    val posType: Int,
    val lastActive: Long,
    val joinTime: Long,
    val unionId: Long,
    val sourceMsgId: String,
)

data class LocalDbSyncEvent(
    val op: Int,
    val tableName: String,
    val rowId: Long,
    val rawJson: String,
    val sourceMsgId: String,
)

data class LocalDbSyncTableStat(
    val tableName: String,
    val eventCount: Int,
    val upserts: Int,
    val updates: Int,
    val deletes: Int,
    val lastSeen: Long,
)

data class LocalBattleField(
    val wid: Int,
    val attackerUid: Long,
    val nearbyUids: String,
    val nearbyCount: Int,
    val sourceMsgId: String,
)

data class LocalMarchEvent(
    val wid: Int,
    val dist: Int,
    val troopCount: Int,
    val troopsJson: String,
    val sourceMsgId: String,
)

data class LocalFullBattle(
    val battleId: Int,
    val time: Long,
    val result: Int,
    val fightType: Int,
    val wid: Int,
    val widName: String,
    val widCode: String,
    val attackerName: String,
    val attackerUid: String,
    val attackerUnion: String,
    val attackerUnionId: Int,
    val attackerPower: Int,
    val attackerGongxun: Int,
    val attackerHp: Int,
    val defenderName: String,
    val defenderUid: String,
    val defenderUnion: String,
    val defenderUnionId: Int,
    val defenderLevel: Int,
    val defenderPower: Int,
    val defenderGongxun: Int,
    val defenderHp: Int,
    val weather: Int,
    val inNight: Int,
    val isNpc: Int,
    val isAi: Int,
    val blockId: Int,
    val cityType: Int,
    val borrowLand: Int,
    val garrison: Int,
    val firstOccupyLvnLand: Int,
    val attackerTeamId: Int,
    val defenderTeamId: Int,
    val attackerAdvance: String,
    val defenderAdvance: String,
    val attackerHeroType: String,
    val defenderHeroType: String,
    val attackerGearInfo: String,
    val defenderGearInfo: String,
    val allSkillInfo: String,
    val attackAllHeroInfo: String,
    val defendAllHeroInfo: String,
    val attackAllSubHeroInfo: String,
    val defendAllSubHeroInfo: String,
    val attackSupportUserInfo: String,
    val defendSupportUserInfo: String,
    val sourceMsgId: String,
    val rawJson: String,
    val attackerHeroes: List<LocalBattleHero>,
    val defenderHeroes: List<LocalBattleHero>,
)

data class LocalBattleHero(
    val battleId: Int,
    val side: String,
    val pos: Int,
    val heroId: Long,
    val heroName: String,
    val level: Int,
    val star: Int,
    val maxHp: Int,
    val remainHp: Int,
    val damageTaken: Int,
)

data class LocalBattleSkill(
    val battleId: Int,
    val side: String,
    val pos: Int,
    val skillId: Long,
    val skillName: String,
    val skillLevel: Int,
)

data class LocalRecord(
    val type: String,
    val key: String,
    val title: String,
    val subtitle: String,
    val rawJson: String,
    val sourceMsgId: String,
)

data class LocalChatMessage(
    val id: Long,
    val sender: String,
    val uid: String,
    val unionName: String,
    val text: String,
    val time: Long,
    val sourceMsgId: String,
)

data class LocalBattleNotice(
    val battleId: Int,
    val time: Long,
    val result: Int,
    val fightType: Int,
    val wid: Int,
    val widCode: String,
    val attackerName: String,
    val attackerUid: String,
    val attackerGongxun: Int,
    val attackerPower: Int,
    val defenderName: String,
    val defenderUnion: String,
    val defenderLevel: Int,
    val defenderGongxun: Int,
    val heroesJson: String,
    val sourceMsgId: String,
) {
    fun heroLines(): List<String> {
        val arr = runCatching { JSONArray(heroesJson) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val hero = arr.optJSONObject(idx) ?: return@mapNotNull null
            val name = hero.optString("hero_name", "武将${hero.optLong("hero_id")}")
            "$name Lv.${hero.optInt("level")} HP ${hero.optInt("remain_hp")}/${hero.optInt("max_hp")}"
        }
    }
}

fun localResultText(result: Int): String {
    return when (result) {
        0 -> "失败"
        1 -> "胜利"
        2 -> "平局"
        3 -> "攻占"
        4 -> "撤退"
        else -> result.toString()
    }
}

fun localFightTypeText(type: Int): String {
    return when (type) {
        0 -> "普通"
        1 -> "攻城"
        2 -> "驻守"
        3 -> "扫荡"
        4 -> "练兵"
        else -> type.toString()
    }
}
