package com.stzb.battle.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

data class EquipmentConfig(
    val id: Int,
    val name: String,
    val quality: String,
    val type: String,
    val skillName: String,
    val skillDescription: String,
    val featureGroup: Int,
)

data class EquipmentFeatureConfig(
    val groupId: Int,
    val name: String,
    val description: String,
)

class BattleEquipmentRepository private constructor(
    private val equipment: Map<Int, EquipmentConfig>,
    private val features: Map<Int, List<EquipmentFeatureConfig>>,
    private val defaultFeatureIdByGearId: Map<Int, Int>,
) {
    fun equipment(id: Int): EquipmentConfig? = equipment[id]

    fun features(groupId: Int): List<EquipmentFeatureConfig> = features[groupId].orEmpty()

    fun allEquipmentIds(): Set<Int> = equipment.keys

    /**
     * Returns the weapon's default feature (词条) id derived from client config:
     * `gearId → Tcfg_gear.gear_type → default row in Tcfg_gear_feature`. Returns
     * `0` when the gear id is unknown, non-positive, or its type has no feature
     * rows (the client hides the 词条 for feature id 0).
     */
    fun defaultFeatureIdForGear(gearId: Int): Int =
        if (gearId <= 0) 0 else defaultFeatureIdByGearId[gearId] ?: 0

    companion object {
        fun loadDefault(): BattleEquipmentRepository =
            load(resolveProjectRoot())

        fun load(projectRoot: Path): BattleEquipmentRepository {
            val cfgRoot = resolveConfigRoot(projectRoot)
            val equipment = JsonRows.read(cfgRoot.resolve("gear_id.json")).associate { row ->
                val id = row.int("id")
                id to EquipmentConfig(
                    id = id,
                    name = row.string("name"),
                    quality = row.string("quality"),
                    type = row.string("type"),
                    skillName = row.string("skillName"),
                    skillDescription = row.string("skillDesc"),
                    featureGroup = row.int("featureGroup"),
                )
            }
            val features = JsonRows.read(cfgRoot.resolve("gear_feature_extra.json"))
                .flatMap { row ->
                    val groupId = row.int("groupId")
                    val infos = row["featureInfo"] as? List<*> ?: emptyList<Any?>()
                    infos.mapNotNull { item ->
                        val info = item as? Map<*, *> ?: return@mapNotNull null
                        EquipmentFeatureConfig(
                            groupId = groupId,
                            name = info["effectName"]?.toString().orEmpty(),
                            description = info["effectDesc"]?.toString().orEmpty(),
                        )
                    }
                }
                .groupBy { it.groupId }
            return BattleEquipmentRepository(
                equipment = equipment,
                features = features,
                defaultFeatureIdByGearId = loadDefaultFeatureIdsByGearId(),
            )
        }

        /**
         * Maps each gear id to the default feature id of its gear type by decoding
         * the checked-in client config bins with the shared [MemoryPackTable]
         * reader. The load is resilient: a missing bin yields an empty map so
         * [defaultFeatureIdForGear] returns 0 rather than throwing.
         */
        private fun loadDefaultFeatureIdsByGearId(): Map<Int, Int> {
            val gearTypeByGearId = runCatching {
                parseGearTypes(readClientConfig("tb_cfg_gear.bin"))
            }.getOrElse { return emptyMap() }
            val defaultFeatureByGearType = runCatching {
                parseDefaultFeatureIdByGearType(readClientConfig("tb_cfg_gear_feature.bin"))
            }.getOrElse { return emptyMap() }
            return gearTypeByGearId.mapNotNull { (gearId, gearType) ->
                defaultFeatureByGearType[gearType]?.let { gearId to it }
            }.toMap()
        }

        private fun parseGearTypes(bytes: ByteArray): Map<Int, Int> {
            val table = MemoryPackTable.open(bytes, "tb_cfg_gear.bin")
            return buildMap {
                table.keys.forEach {
                    require(table.reader.byte().toInt() and 0xff == 22) { "invalid Tcfg_gear row" }
                    val gearId = table.reader.int()
                    val gearType = table.reader.int()
                    repeat(6) { table.reader.int() }
                    repeat(3) { table.reader.byte() }
                    repeat(11) { table.reader.int() }
                    put(gearId, gearType)
                }
            }
        }

        private fun parseDefaultFeatureIdByGearType(bytes: ByteArray): Map<Int, Int> {
            val table = MemoryPackTable.open(bytes, "tb_cfg_gear_feature.bin")
            val rows = table.keys.map {
                require(table.reader.byte().toInt() and 0xff == 11) {
                    "invalid Tcfg_gear_feature row"
                }
                val id = table.reader.int()
                val gearType = table.reader.int()
                val level = table.reader.int()
                table.reader.int() // level_type
                val advance = table.reader.int()
                val visible = table.reader.int()
                table.reader.int() // feature_type
                table.reader.int() // seven_feature_id
                repeat(3) { table.reader.int() } // skill, desc, policy string indices
                GearFeatureRow(id, gearType, level, advance, visible)
            }
            return rows.groupBy { it.gearType }.mapValues { (_, typeRows) ->
                // Prefer the 红极/鸿级 (advance == 1) feature, taking the smallest
                // id; it renders unlocked in reports. Fall back to the base
                // default only when a gear type has no hongji row.
                (typeRows.filter { it.advance == 1 }.minByOrNull { it.id }
                    ?: typeRows.filter { it.advance == 0 && it.level == 1 && it.visible == 1 }
                        .minByOrNull { it.id }
                    ?: typeRows.filter { it.advance == 0 && it.level == 1 }.minByOrNull { it.id }
                    ?: typeRows.minByOrNull { it.id })!!.id
            }
        }

        private fun readClientConfig(fileName: String): ByteArray {
            val resourcePath = "/client-config/$fileName"
            BattleEquipmentRepository::class.java.getResourceAsStream(resourcePath)?.use {
                return it.readBytes()
            }
            return Files.readAllBytes(resolveClientTable(fileName))
        }

        private fun resolveClientTable(fileName: String): Path {
            val cwd = Path.of("").toAbsolutePath().normalize()
            val relativePath = Path.of(
                "assets",
                "npk_extracted_all",
                "others",
                "res",
                "csharp",
                "data",
                "tcfg",
                "default",
                fileName,
            )
            generateSequence(cwd) { it.parent }.take(6).forEach { root ->
                if (!root.isDirectory()) return@forEach
                Files.list(root).use { children ->
                    val table = children
                        .filter { it.isDirectory() && it.name.startsWith("stzb_9.2.2_out_branch_") }
                        .map { it.resolve(relativePath) }
                        .filter { it.exists() }
                        .sorted()
                        .findFirst()
                        .orElse(null)
                    if (table != null) return table
                }
            }
            error("无法定位客户端装备配置表，请从项目目录启动服务: $cwd")
        }

        private data class GearFeatureRow(
            val id: Int,
            val gearType: Int,
            val level: Int,
            val advance: Int,
            val visible: Int,
        )

        private fun resolveProjectRoot(): Path {
            val cwd = Path.of("").toAbsolutePath().normalize()
            return generateSequence(cwd) { it.parent }
                .firstOrNull { root ->
                    CONFIG_PATHS.any { path -> root.resolve(path).exists() }
                }
                ?: error("无法定位项目根目录: $cwd")
        }

        private fun resolveConfigRoot(projectRoot: Path): Path =
            CONFIG_PATHS
                .map(projectRoot::resolve)
                .firstOrNull { it.exists() }
                ?: projectRoot.resolve(CONFIG_PATHS.first())

        private val CONFIG_PATHS = listOf(
            Path.of("src/main/resources/battle-config"),
            Path.of("assent/cfg"),
            Path.of("server/assent/cfg"),
        )
    }
}

private object JsonRows {
    private val mapper = jacksonObjectMapper()
    private val rowsType = object : TypeReference<List<Map<String, Any?>>>() {}

    fun read(path: Path): List<Map<String, Any?>> {
        if (!path.exists()) return emptyList()
        return mapper.readValue(path.toFile(), rowsType)
    }
}

private fun Map<String, Any?>.string(name: String): String =
    this[name]?.toString().orEmpty()

private fun Map<String, Any?>.int(name: String): Int =
    when (val value = this[name]) {
        is Number -> value.toInt()
        is String -> value.toDoubleOrNull()?.toInt() ?: 0
        else -> 0
    }
