package com.stzb.server.game

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

data class ClientNpcHero(
    val heroUid: Int,
    val heroId: Int,
    val level: Int,
    val troops: Int,
    val heroType: Int,
    val heroFeatureSkillId: Int,
    val skillIds: List<Int>,
    val skillLevels: List<Int>,
    val troopFeatureIds: List<Int>,
    val equipmentIds: List<Int>,
    val equipmentSkillIds: List<Int>,
    val equipmentSkillLevels: List<Int>,
    val equipmentFeatureSkillIds: List<Int>,
    val equipmentFeatureSkillLevels: List<Int>,
)

data class ClientNpcArmy(
    val armyId: Int,
    val pool: Int,
    val heroes: List<ClientNpcHero>,
)

class ClientTroopFeatureRepository private constructor(
    private val skillIdsByFeatureId: Map<Int, List<Int>>,
) {
    fun skillIds(featureId: Int): List<Int> = skillIdsByFeatureId[featureId].orEmpty()

    companion object {
        fun loadDefault(): ClientTroopFeatureRepository {
            val resourcePath = "/client-config/tb_cfg_hero_type_feature.bin"
            val bytes = ClientTroopFeatureRepository::class.java.getResourceAsStream(resourcePath)
                ?.use { it.readBytes() }
                ?: Files.readAllBytes(resolveDefaultTable())
            return load(bytes)
        }

        fun load(path: Path): ClientTroopFeatureRepository =
            load(Files.readAllBytes(path))

        private fun load(bytes: ByteArray): ClientTroopFeatureRepository {
            val table = MemoryPackTable.open(bytes, "tb_cfg_hero_type_feature.bin")
            val mappings = buildMap {
                table.keys.forEach {
                    require(table.reader.byte().toInt() and 0xff == 11) {
                        "invalid Tcfg_hero_type_feature row"
                    }
                    val featureId = table.reader.int()
                    table.reader.int() // max_learn_num
                    table.reader.int() // expired
                    table.reader.int() // balance_activity_id
                    table.reader.byte() // group
                    table.reader.int() // name
                    table.reader.int() // desc
                    table.reader.int() // icon_id
                    val skillIds = parseIds(table.string(table.reader.int()).orEmpty())
                    table.reader.int() // effect_id
                    table.reader.int() // hero_type
                    put(featureId, skillIds)
                }
            }
            return ClientTroopFeatureRepository(mappings)
        }

        private fun resolveDefaultTable(): Path {
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
                "tb_cfg_hero_type_feature.bin",
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
            error("无法定位客户端兵种特性配置表，请从项目目录启动服务: $cwd")
        }

        private fun parseIds(value: String): List<Int> =
            value.split(',', ';')
                .mapNotNull(String::toIntOrNull)
                .filter { it > 0 }
    }
}

class ClientTroopTypeRepository private constructor(
    private val featureRepository: ClientTroopFeatureRepository,
) {
    private val skillIdsByHeroType: Map<Int, List<Int>> =
        (1..999).associateWith(::configuredSkillIds).filterValues(List<Int>::isNotEmpty)

    fun skillIds(heroType: Int): List<Int> = skillIdsByHeroType[heroType].orEmpty()

    fun heroTypeForSkillIds(skillIds: Collection<Int>): Int? {
        val candidates = skillIds.toSet()
        return skillIdsByHeroType.entries
            .map { (heroType, inherentSkills) ->
                heroType to inherentSkills.count(candidates::contains)
            }
            .filter { (_, matchingSkills) -> matchingSkills > 0 }
            .maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
            ?.first
    }

    companion object {
        fun loadDefault(): ClientTroopTypeRepository =
            ClientTroopTypeRepository(ClientTroopFeatureRepository.loadDefault())
    }

    private fun configuredSkillIds(heroType: Int): List<Int> {
        val baseType = heroType % 10
        if (baseType !in 1..3) return emptyList()
        val featureId = 1_000 + baseType * 100 + heroType
        return featureRepository.skillIds(featureId)
    }
}

class ClientEquipmentSkillRepository private constructor(
    private val skillSlotsByEquipmentId: Map<Int, List<IdLevel>>,
) {
    fun skillSlots(equipmentId: Int): List<Pair<Int, Int>> =
        skillSlotsByEquipmentId[equipmentId].orEmpty().map { it.id to it.level }

    companion object {
        fun loadDefault(): ClientEquipmentSkillRepository {
            val resourcePath = "/client-config/tb_cfg_gear.bin"
            val bytes = ClientEquipmentSkillRepository::class.java.getResourceAsStream(resourcePath)
                ?.use { it.readBytes() }
                ?: Files.readAllBytes(resolveDefaultTable())
            return load(bytes)
        }

        private fun load(bytes: ByteArray): ClientEquipmentSkillRepository {
            val table = MemoryPackTable.open(bytes, "tb_cfg_gear.bin")
            val mappings = buildMap {
                table.keys.forEach {
                    require(table.reader.byte().toInt() and 0xff == 22) {
                        "invalid Tcfg_gear row"
                    }
                    val equipmentId = table.reader.int()
                    repeat(7) { table.reader.int() }
                    repeat(3) { table.reader.byte() }
                    table.reader.int() // name
                    table.reader.int() // defective_gear_id
                    val skillSlots = parseIdLevels(table.string(table.reader.int()).orEmpty())
                    repeat(8) { table.reader.int() }
                    put(equipmentId, skillSlots)
                }
            }
            return ClientEquipmentSkillRepository(mappings)
        }

        private fun resolveDefaultTable(): Path {
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
                "tb_cfg_gear.bin",
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

        private fun parseIdLevels(value: String): List<IdLevel> =
            value.split(';')
                .mapNotNull { item ->
                    val parts = item.split(',')
                    val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val level = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                    IdLevel(id, level).takeIf { it.id > 0 && it.level > 0 }
                }
    }
}

/**
 * Reads the client's MemoryPack configuration tables used by resource-land NPCs.
 *
 * Tcfg_army selects the hero instances for an army, while Tcfg_hero_u owns the
 * actual hero id, level, troops and skill loadout. Keeping that relationship
 * intact avoids manufacturing defender values on the server.
 */
class ClientNpcArmyRepository private constructor(
    private val armiesByPool: Map<Int, List<ClientNpcArmy>>,
    private val teamCounts: Map<Int, Int>,
) {
    fun armiesForPool(pool: Int): List<ClientNpcArmy> = armiesByPool[pool].orEmpty()

    fun teamCount(pool: Int): Int = teamCounts[pool] ?: 1

    fun defenderPoolForCityParam(param: Int): Int =
        (param % 100).coerceIn(MIN_DEFENDER_POOL, MAX_DEFENDER_POOL)

    fun armyIdsForCityParam(param: Int): List<Int> {
        val pool = defenderPoolForCityParam(param)
        val armies = armiesForPool(pool)
        return armies.take(teamCount(pool)).map(ClientNpcArmy::armyId)
    }

    companion object {
        private const val CLIENT_DIR_PREFIX = "stzb_9.2.2_out_branch_"
        private const val MIN_DEFENDER_POOL = 1
        private const val MAX_DEFENDER_POOL = 9
        private val TABLE_RELATIVE_PATH = Path.of(
            "assets",
            "npk_extracted_all",
            "others",
            "res",
            "csharp",
            "data",
            "tcfg",
        )

        fun loadDefault(): ClientNpcArmyRepository =
            load(
                armyBytes = readClientResource("tb_cfg_army.bin"),
                heroBytes = readClientResource("tb_cfg_hero_u.bin"),
                gearBytes = readClientResource("tb_cfg_gear_u.bin"),
                gearFeatureBytes = readClientResource("tb_cfg_gear_feature.bin"),
                armyCountBytes = readClientResource("tb_cfg_army_count.bin"),
            )

        fun load(tableRoot: Path): ClientNpcArmyRepository {
            return load(
                armyBytes = Files.readAllBytes(tableRoot.resolve("tb_cfg_army.bin")),
                heroBytes = Files.readAllBytes(tableRoot.resolve("tb_cfg_hero_u.bin")),
                gearBytes = Files.readAllBytes(tableRoot.resolve("tb_cfg_gear_u.bin")),
                gearFeatureBytes = Files.readAllBytes(
                    tableRoot.resolve("default").resolve("tb_cfg_gear_feature.bin"),
                ),
                armyCountBytes = Files.readAllBytes(tableRoot.resolve("tb_cfg_army_count.bin")),
            )
        }

        private fun load(
            armyBytes: ByteArray,
            heroBytes: ByteArray,
            gearBytes: ByteArray,
            gearFeatureBytes: ByteArray,
            armyCountBytes: ByteArray,
        ): ClientNpcArmyRepository {
            val heroes = parseHeroes(
                heroBytes,
                parseEquipment(gearBytes, parseEquipmentFeatures(gearFeatureBytes)),
            )
            val armies = parseArmies(armyBytes)
                .map { row ->
                    ClientNpcArmy(
                        armyId = row.armyId,
                        pool = row.pool,
                        heroes = row.heroUids.mapNotNull(heroes::get),
                    )
                }
                .filter { army ->
                    army.pool > 0 &&
                        army.armyId / 100 == army.pool &&
                        army.heroes.isNotEmpty()
                }
                .groupBy(ClientNpcArmy::pool)
                .mapValues { (_, rows) -> rows.sortedBy(ClientNpcArmy::armyId) }
            require((1..9).all { armies[it].orEmpty().isNotEmpty() }) {
                "client resource-land defender pools 1..9 are incomplete"
            }
            return ClientNpcArmyRepository(
                armiesByPool = armies,
                teamCounts = parseArmyCounts(armyCountBytes),
            )
        }

        private fun readClientResource(fileName: String): ByteArray {
            val resourcePath = "/client-config/$fileName"
            ClientNpcArmyRepository::class.java.getResourceAsStream(resourcePath)?.use {
                return it.readBytes()
            }
            return Files.readAllBytes(resolveClientTableRoot().resolve(fileName))
        }

        private fun resolveClientTableRoot(): Path {
            val cwd = Path.of("").toAbsolutePath().normalize()
            val projectRoots = generateSequence(cwd) { it.parent }.take(6)
            projectRoots.forEach { root ->
                if (!root.isDirectory()) return@forEach
                Files.list(root).use { children ->
                    val clientRoot = children
                        .filter { it.isDirectory() && it.name.startsWith(CLIENT_DIR_PREFIX) }
                        .sorted()
                        .findFirst()
                        .orElse(null)
                    if (clientRoot != null) {
                        val tableRoot = clientRoot.resolve(TABLE_RELATIVE_PATH)
                        if (tableRoot.resolve("tb_cfg_army.bin").exists() &&
                            tableRoot.resolve("tb_cfg_hero_u.bin").exists()
                        ) {
                            return tableRoot
                        }
                    }
                }
            }
            error("无法定位客户端守军配置表，请从项目目录启动服务: $cwd")
        }

        private fun parseArmies(bytes: ByteArray): List<ArmyRow> {
            val table = MemoryPackTable.open(bytes, "tb_cfg_army.bin")
            return table.keys.map {
                require(table.reader.byte().toInt() and 0xff == 7) { "invalid Tcfg_army row" }
                val armyId = table.reader.int()
                val base = table.reader.int()
                val middle = table.reader.int()
                val front = table.reader.int()
                val counsellor = table.reader.int()
                table.reader.int() // exercise_record
                val pool = table.reader.int()
                ArmyRow(
                    armyId = armyId,
                    pool = pool,
                    heroUids = listOf(base, middle, front, counsellor).filter { uid -> uid > 0 },
                )
            }
        }

        private fun parseHeroes(
            bytes: ByteArray,
            equipmentByUid: Map<Int, ClientEquipment>,
        ): Map<Int, ClientNpcHero> {
            val table = MemoryPackTable.open(bytes, "tb_cfg_hero_u.bin")
            return buildMap {
                table.keys.forEach {
                    require(table.reader.byte().toInt() and 0xff == 9) { "invalid Tcfg_hero_u row" }
                    val heroUid = table.reader.int()
                    val heroId = table.reader.int()
                    val level = table.reader.int()
                    val troops = table.reader.int()
                    val heroType = table.reader.int()
                    val heroFeatureSkillId = table.reader.int()
                    val equipmentUid = table.reader.int()
                    val skill = table.string(table.reader.int()).orEmpty()
                    val troopFeatures = table.string(table.reader.int()).orEmpty()
                    val skills = parseIdLevels(skill)
                    val equipment = equipmentByUid[equipmentUid]
                    put(
                        heroUid,
                        ClientNpcHero(
                            heroUid = heroUid,
                            heroId = heroId,
                            level = level,
                            troops = troops,
                            heroType = heroType,
                            heroFeatureSkillId = heroFeatureSkillId,
                            skillIds = skills.map(IdLevel::id),
                            skillLevels = skills.map(IdLevel::level),
                            troopFeatureIds = parseIds(troopFeatures),
                            equipmentIds = listOfNotNull(equipment?.baseId),
                            equipmentSkillIds = equipment?.skills.orEmpty().map(IdLevel::id),
                            equipmentSkillLevels = equipment?.skills.orEmpty().map(IdLevel::level),
                            equipmentFeatureSkillIds =
                                equipment?.featureSkills.orEmpty().map(IdLevel::id),
                            equipmentFeatureSkillLevels =
                                equipment?.featureSkills.orEmpty().map(IdLevel::level),
                        ),
                    )
                }
            }
        }

        private fun parseEquipment(
            bytes: ByteArray,
            features: Map<Int, List<IdLevel>>,
        ): Map<Int, ClientEquipment> {
            val table = MemoryPackTable.open(bytes, "tb_cfg_gear_u.bin")
            return buildMap {
                table.keys.forEach {
                    require(table.reader.byte().toInt() and 0xff == 6) { "invalid Tcfg_gear_u row" }
                    val equipmentUid = table.reader.int()
                    val baseId = table.reader.int()
                    val featureId = table.reader.int()
                    table.reader.int() // level
                    table.reader.int() // phase
                    val equipment = table.string(table.reader.int()).orEmpty()
                    put(
                        equipmentUid,
                        ClientEquipment(
                            baseId,
                            parseIdLevels(equipment),
                            features[featureId].orEmpty(),
                        ),
                    )
                }
            }
        }

        private fun parseEquipmentFeatures(bytes: ByteArray): Map<Int, List<IdLevel>> {
            val table = MemoryPackTable.open(bytes, "tb_cfg_gear_feature.bin")
            return buildMap {
                table.keys.forEach {
                    require(table.reader.byte().toInt() and 0xff == 11) {
                        "invalid Tcfg_gear_feature row"
                    }
                    val featureId = table.reader.int()
                    repeat(7) { table.reader.int() }
                    val skills = parseIdLevels(table.string(table.reader.int()).orEmpty())
                    table.reader.int() // desc
                    table.reader.int() // policy
                    put(featureId, skills)
                }
            }
        }

        private fun parseArmyCounts(bytes: ByteArray): Map<Int, Int> {
            val table = MemoryPackTable.open(bytes, "tb_cfg_army_count.bin")
            return buildMap {
                table.keys.forEach {
                    require(table.reader.byte().toInt() and 0xff == 3) { "invalid Tcfg_army_count row" }
                    val type = table.reader.int()
                    val count = table.reader.int()
                    table.reader.int() // recover_interval
                    if (type in 1..9) put(type, count)
                }
            }
        }

        private fun parseIdLevels(value: String): List<IdLevel> =
            value.split(';')
                .mapNotNull { item ->
                    val parts = item.split(',')
                    val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val level = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                    IdLevel(id, level).takeIf { it.id > 0 && it.level > 0 }
                }

        private fun parseIds(value: String): List<Int> =
            value.split(',', ';')
                .mapNotNull(String::toIntOrNull)
                .filter { it > 0 }
    }
}

private data class IdLevel(
    val id: Int,
    val level: Int,
)

private data class ClientEquipment(
    val baseId: Int,
    val skills: List<IdLevel>,
    val featureSkills: List<IdLevel>,
)

private data class ArmyRow(
    val armyId: Int,
    val pool: Int,
    val heroUids: List<Int>,
)

internal class MemoryPackTable private constructor(
    val strings: List<String?>,
    val keys: List<Int>,
    val reader: LittleEndianReader,
) {
    fun string(index: Int): String? =
        if (index == -1) null else strings.getOrNull(index)

    companion object {
        fun open(bytes: ByteArray, source: String): MemoryPackTable {
            val reader = LittleEndianReader(bytes)
            val stringTableLength = reader.int()
            val stringTableEnd = reader.position + stringTableLength
            val stringCount = reader.int()
            val strings = if (stringCount < 0) {
                emptyList()
            } else {
                List(stringCount) { reader.memoryPackString() }
            }
            require(reader.position == stringTableEnd) { "invalid string table in $source" }
            require(reader.byte().toInt() and 0xff == 2) { "invalid table header in $source" }
            val keyCount = reader.int()
            val keys = List(keyCount) { reader.int() }
            require(reader.int() == keyCount) { "key/value count mismatch in $source" }
            return MemoryPackTable(strings, keys, reader)
        }
    }
}

internal class LittleEndianReader(bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val position: Int
        get() = buffer.position()

    fun byte(): Byte = buffer.get()

    fun int(): Int = buffer.int

    fun memoryPackString(): String? {
        val length = int()
        return when {
            length == -1 -> null
            length == 0 -> ""
            length > 0 -> {
                val bytes = ByteArray(length * 2)
                buffer.get(bytes)
                bytes.toString(Charsets.UTF_16LE)
            }
            else -> {
                val byteCount = length.inv()
                int() // UTF-16 character count
                val bytes = ByteArray(byteCount)
                buffer.get(bytes)
                bytes.toString(Charsets.UTF_8)
            }
        }
    }
}
