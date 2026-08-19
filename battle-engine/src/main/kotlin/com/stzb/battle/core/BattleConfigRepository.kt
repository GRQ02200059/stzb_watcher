package com.stzb.battle.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.exists

enum class SkillKind {
    PASSIVE,
    COMMAND,
    ACTIVE,
    PURSUIT,
    UNKNOWN,
    ;

    companion object {
        fun fromRawType(rawSkillType: Int): SkillKind =
            when (rawSkillType) {
                1, 12, 13 -> PASSIVE
                2 -> COMMAND
                3 -> ACTIVE
                4 -> PURSUIT
                else -> UNKNOWN
            }
    }
}

data class HeroBattleConfig(
    val id: Int,
    val name: String,
    val cost: Double,
    val hitRange: Int,
    val stats: BattleStats,
    val growth: BattleStats,
    val initialSkillId: Int,
    val qualityName: String,
    val country: Int,
    val sex: Int,
    val heroType: Int,
)

data class SkillBattleConfig(
    val id: Int,
    val name: String,
    val kind: SkillKind,
    val rawSkillType: Int,
    val hitRange: Int?,
    val prepareRounds: Int,
    val probabilityInit: Int,
    val probabilityMax: Int,
    val mainDetailId: Int,
    val mainDetail: SkillDetailConfig?,
    val mainEffect: SkillEffectConfig?,
    val qualityLevel: String,
)

data class SkillDetailConfig(
    val detailId: Int,
    val effectId: Int,
    val effectParam: Int = 0,
    val calcPos: Int = 0,
    val calcParam: Int = 0,
    val attackType: Int,
    val selectSkillParam: Int = 0,
    val targetType: Int,
    val selectType: Int,
    val targetCountry: Int = 0,
    val selectAttri: Int = 0,
    val customSelectFlag: Int = 0,
    val availableHit: Int = 0,
    val initEffectRatio: Int = 100,
    val intelParam: Int,
    val constantParam: Int,
    val probabilityInit: Int,
    val probabilityMax: Int,
    val bindFlag: Int = 0,
    val castCondition: Int = 0,
    val precondition: Int = 0,
    val condition: Int = 0,
    val lockFlag: Int = 0,
    val addCountMax: Int = 0,
    val buffType: Int = 0,
    val attackMax: Int,
    val delayRound: Int = 0,
    val delayHit: Int = 0,
    val availableRounds: Int,
    val clearPerHit: Boolean = false,
    val selectFlag: Int = 0,
    val inherent: Int = 0,
    val moraleAffected: Boolean = false,
    val attributeType: Int = 0,
    val valueAddMax: Int = 0,
    val hideConflict: Int = 0,
    val probabilitySeries: List<Int> = emptyList(),
    val calculationType: Int = 0,
    val calculationTypes: List<Int> = emptyList(),
    val effectName: String,
)

data class SkillEffectConfig(
    val effectId: Int,
    val name: String,
    val buffType: Int,
    val replaceType: Int = 0,
    val valueType: Int,
)

enum class BattleEffectValueUnit(val rawValueType: Int) {
    FLAT(0),
    RATE(1),
    PERCENT(2),
    ;

    companion object {
        fun fromRaw(rawValueType: Int): BattleEffectValueUnit =
            entries.singleOrNull { it.rawValueType == rawValueType }
                ?: throw IllegalArgumentException("Unsupported value_type=$rawValueType")
    }
}

/**
 * Lossless configured numeric value. Raw scaling is intentionally retained:
 * value_type identifies semantic application, but does not by itself define
 * how every client subsystem encodes constant/coefficient magnitudes.
 */
data class ConfiguredBattleEffectValue(
    val unit: BattleEffectValueUnit,
    val rawValueType: Int,
    val rawConstant: Int,
    val rawCoefficient: Int,
    val rawAttributeType: Int,
    val rawCalcPosition: Int,
    val rawCalcParameter: Int,
)

data class HeroExtraConfig(
    val id: Int,
    val name: String,
    val methodDesc: String,
)

data class SkillExtraConfig(
    val id: Int,
    val name: String,
    val description: String,
)

data class ArmyBonusConfig(
    val id: Int,
    val name: String,
    val heroIds: Set<Int>,
    val stats: BattleStats,
)

class BattleConfigRepository private constructor(
    private val heroes: Map<Int, HeroBattleConfig>,
    private val skills: Map<Int, SkillBattleConfig>,
    private val details: Map<Int, SkillDetailConfig>,
    private val heroExtras: Map<Int, HeroExtraConfig>,
    private val skillExtras: Map<Int, SkillExtraConfig>,
    private val armyBonuses: List<ArmyBonusConfig>,
    private val effects: Map<Int, SkillEffectConfig>,
) {
    fun hero(heroId: Int): HeroBattleConfig? = heroes[heroId]

    fun skill(skillId: Int): SkillBattleConfig? = skills[skillId]

    fun allHeroIds(): Set<Int> = heroes.keys

    fun allHeroes(): Collection<HeroBattleConfig> = heroes.values

    fun allSkillIds(): Set<Int> = skills.keys

    fun heroExtra(heroId: Int): HeroExtraConfig? = heroExtras[heroId]

    fun skillExtra(skillId: Int): SkillExtraConfig? = skillExtras[skillId]

    fun skillDetails(skillId: Int): List<SkillDetailConfig> {
        val detailIdStart = skillId * 100
        val detailIdEnd = detailIdStart + 99
        return details.values
            .filter { it.detailId in detailIdStart..detailIdEnd }
            .sortedBy { it.detailId }
    }

    fun skillEnhancementUnlockIds(): Set<Int> =
        details.values
            .asSequence()
            .filter { it.effectId == 132 }
            .map { it.effectParam }
            .filter { it > 0 }
            .toSet()

    fun skillEffect(effectId: Int): SkillEffectConfig? = effects[effectId]

    fun configuredValue(detail: SkillDetailConfig): ConfiguredBattleEffectValue {
        val effect = requireNotNull(effects[detail.effectId]) {
            "Missing effect config for effect=${detail.effectId}"
        }
        return ConfiguredBattleEffectValue(
            unit = BattleEffectValueUnit.fromRaw(effect.valueType),
            rawValueType = effect.valueType,
            rawConstant = detail.constantParam,
            rawCoefficient = detail.intelParam,
            rawAttributeType = detail.attributeType,
            rawCalcPosition = detail.calcPos,
            rawCalcParameter = detail.calcParam,
        )
    }

    fun troopCounterModifiers(heroType: Int): List<BattleModifier> {
        if (heroType <= 0) return emptyList()
        return details.values
            .asSequence()
            .filter { detail ->
                detail.effectId in TROOP_COUNTER_EFFECT_IDS &&
                    detail.targetType == heroType &&
                    detail.effectParam > 0
            }
            .groupBy { detail -> detail.effectId to detail.effectParam }
            .entries
            .sortedWith(
                compareBy<Map.Entry<Pair<Int, Int>, List<SkillDetailConfig>>> {
                    it.key.first
                }.thenBy { it.key.second },
            )
            .mapNotNull { (key, matchingDetails) ->
                val strength = matchingDetails.maxOf { detail ->
                    (
                        detail.constantParam.toLong() *
                            detail.initEffectRatio.coerceAtLeast(0) /
                            100
                        )
                        .coerceIn(0, Int.MAX_VALUE.toLong())
                        .toInt()
                }
                if (strength <= 0) {
                    null
                } else {
                    when (key.first) {
                        98 -> BattleModifier.TroopCounterDealtPercent(
                            targetHeroType = key.second,
                            percent = strength,
                        )
                        99 -> BattleModifier.TroopCounterTakenPercent(
                            sourceHeroType = key.second,
                            percent = -strength,
                        )
                        else -> error("Unsupported troop counter effect=${key.first}")
                    }
                }
            }
    }

    fun armyBonusesFor(heroIds: Collection<Int>): List<ArmyBonusConfig> {
        val teamHeroIds = heroIds.toSet()
        return armyBonuses.filter { bonus -> bonus.heroIds.count { it in teamHeroIds } >= 3 }
    }

    fun toBattleHero(heroId: Int, position: Int, troops: Int): BattleHero {
        val config = hero(heroId) ?: error("未知武将配置: $heroId")
        return BattleHero(
            id = BattleHeroId(heroId),
            position = position,
            stats = config.stats,
            troops = troops,
            maxTroops = troops,
            heroType = config.heroType,
        )
    }

    companion object {
        private val TROOP_COUNTER_EFFECT_IDS = setOf(98, 99)

        fun loadDefault(): BattleConfigRepository =
            DefaultBattleConfig.repository

        private object DefaultBattleConfig {
            val repository: BattleConfigRepository by lazy(LazyThreadSafetyMode.PUBLICATION) {
            load(
                effectsRows = Csv.readResource("battle-config/skill_effect_table.csv"),
                detailRows = Csv.readResource("battle-config/skill_detail_table.csv"),
                skillRows = Csv.readResource("battle-config/skill_table.csv"),
                heroRows = Csv.readResource("battle-config/hero_table.csv"),
                heroExtraRows = Json.readResourceArray("battle-config/hero_extra.json"),
                skillExtraRows = Json.readResourceArray("battle-config/skill_extra.json"),
                armyExtraRows = Json.readResourceArray("battle-config/army_extra.json"),
            )
            }
        }

        fun load(projectRoot: Path): BattleConfigRepository {
            val cfgRoot = projectRoot.resolve("server/assent/cfg")
            return load(
                effectsRows = Csv.read(projectRoot.resolve("skill_effect_table.csv")),
                detailRows = Csv.read(projectRoot.resolve("skill_detail_table.csv")),
                skillRows = Csv.read(projectRoot.resolve("skill_table.csv")),
                heroRows = Csv.read(projectRoot.resolve("hero_table.csv")),
                heroExtraRows = Json.readArray(cfgRoot.resolve("hero_extra.json")),
                skillExtraRows = Json.readArray(cfgRoot.resolve("skill_extra.json")),
                armyExtraRows = Json.readArray(cfgRoot.resolve("army_extra.json")),
            )
        }

        private fun load(
            effectsRows: List<Map<String, String>>,
            detailRows: List<Map<String, String>>,
            skillRows: List<Map<String, String>>,
            heroRows: List<Map<String, String>>,
            heroExtraRows: List<Map<String, Any?>>,
            skillExtraRows: List<Map<String, Any?>>,
            armyExtraRows: List<Map<String, Any?>>,
        ): BattleConfigRepository {
            val effects = effectsRows
                .associate { row ->
                    val effect = SkillEffectConfig(
                        effectId = row.int("effect_id"),
                        name = row["name"].orEmpty(),
                        buffType = row.int("buff_type"),
                        replaceType = row.int("replace_type"),
                        valueType = row.int("value_type"),
                    )
                    effect.effectId to effect
                }
            val details = detailRows
                .associate { row ->
                    val detail = loadSkillDetail(row)
                    detail.detailId to detail
                }
            val skills = skillRows
                .associate { row ->
                    val skillId = row.int("skill_id")
                    val rawSkillType = row.int("skill_type")
                    val mainDetail = row.int("main_detail")
                    val detail = details[mainDetail]
                    skillId to SkillBattleConfig(
                        id = skillId,
                        name = row["name"].orEmpty(),
                        kind = SkillKind.fromRawType(rawSkillType),
                        rawSkillType = rawSkillType,
                        hitRange = row.intOrNull("hit_range")?.takeIf { it > 0 },
                        prepareRounds = row.int("prepare"),
                        probabilityInit = row.int("probability_init"),
                        probabilityMax = row.int("probability_max"),
                        mainDetailId = mainDetail,
                        mainDetail = detail,
                        mainEffect = detail?.effectId?.let { effects[it] },
                        qualityLevel = row["skill_quality_level"].orEmpty(),
                    )
                }
            val heroes = heroRows
                .associate { row ->
                    val heroId = row.int("heroid")
                    heroId to HeroBattleConfig(
                        id = heroId,
                        name = row["name"].orEmpty(),
                        cost = row.int("cost") / 10.0,
                        hitRange = row.int("hit_range"),
                        stats = BattleStats.fromHundredths(
                            attack = row.int("attack_base"),
                            defense = row.int("defence_base"),
                            strategy = row.int("intel_base"),
                            speed = row.int("speed_base"),
                            siege = row.int("destroy_base"),
                            hitRange = row.int("hit_range"),
                        ),
                        growth = BattleStats.fromHundredths(
                            attack = row.int("attack_grow"),
                            defense = row.int("defence_grow"),
                            strategy = row.int("intel_grow"),
                            speed = row.int("speed_grow"),
                            siege = row.int("destroy_grow"),
                            hitRange = 0,
                        ),
                        initialSkillId = row.int("skill_init"),
                        qualityName = row["quality_name"].orEmpty(),
                        country = row.int("country"),
                        sex = row.int("sex"),
                        heroType = row.int("hero_type"),
                    )
                }
            return BattleConfigRepository(
                heroes = heroes,
                skills = skills,
                details = details,
                heroExtras = loadHeroExtras(heroExtraRows),
                skillExtras = loadSkillExtras(skillExtraRows),
                armyBonuses = loadArmyBonuses(armyExtraRows),
                effects = effects,
            )
        }

        internal fun loadSkillDetail(row: Map<String, String>): SkillDetailConfig =
            SkillDetailConfig(
                detailId = row.int("detail_id"),
                effectId = row.int("effect_id"),
                effectParam = row.int("effect_param"),
                calcPos = row.int("calc_pos"),
                calcParam = row.int("calc_param"),
                attackType = row.int("attack_type"),
                selectSkillParam = row.int("select_skill_param"),
                targetType = row.int("target_type"),
                selectType = row.int("select_type"),
                targetCountry = row.int("target_country"),
                selectAttri = row.int("select_attri"),
                customSelectFlag = row.int("custom_select_flag"),
                availableHit = row.int("available_hit"),
                initEffectRatio = row.int("init_effect_ratio"),
                intelParam = row.int("intel_param"),
                constantParam = row.int("constant_param"),
                probabilityInit = row.int("prob_init_param"),
                probabilityMax = row.int("prob_max_param"),
                bindFlag = row.int("bind_flag"),
                castCondition = row.int("cast_condition"),
                precondition = row.int("precondition"),
                condition = row.int("condition"),
                lockFlag = row.int("lock_flag"),
                addCountMax = row.int("add_count_max"),
                buffType = row.int("buff_type"),
                attackMax = row.int("attack_max"),
                delayRound = row.int("delay_round"),
                delayHit = row.int("delay_hit"),
                availableRounds = row.int("available_round"),
                clearPerHit = row.int("clear_per_hit") != 0,
                selectFlag = row.int("select_flag"),
                inherent = row.int("inherent"),
                moraleAffected = row.int("shi_qi_affect") != 0,
                attributeType = row.int("attri_type"),
                valueAddMax = row.int("value_add_max"),
                hideConflict = row.int("hide_conflict"),
                probabilitySeries = row.intList("prob_series"),
                calculationType = row.int("calc_type"),
                calculationTypes = row.intList("calc_type"),
                effectName = row["effect_name"].orEmpty(),
            )

        private fun loadHeroExtras(rows: List<Map<String, Any?>>): Map<Int, HeroExtraConfig> =
            rows.associate { row ->
                val id = row.int("id")
                id to HeroExtraConfig(
                    id = id,
                    name = row.string("name"),
                    methodDesc = row.string("methodDesc"),
                )
            }

        private fun loadSkillExtras(rows: List<Map<String, Any?>>): Map<Int, SkillExtraConfig> =
            rows.associate { row ->
                val id = row.int("id")
                id to SkillExtraConfig(
                    id = id,
                    name = row.string("name"),
                    description = listOf(
                        row.string("targetShow"),
                        row.string("targetType"),
                        row.string("desc"),
                    ).filter { it.isNotBlank() }.distinct().joinToString("\n"),
                )
            }

        private fun loadArmyBonuses(rows: List<Map<String, Any?>>): List<ArmyBonusConfig> =
            rows.mapNotNull { row ->
                val heroIds = row.string("heroId")
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
                if (heroIds.isEmpty()) {
                    null
                } else {
                    ArmyBonusConfig(
                        id = row.int("armyId"),
                        name = row.string("armyName"),
                        heroIds = heroIds,
                        stats = parseArmyBonusStats(row.string("armyEffect")),
                    )
                }
            }

        private fun parseArmyBonusStats(text: String): BattleStats {
            fun value(pattern: String): Int =
                Regex(pattern).find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 0

            return BattleStats(
                attack = value("""攻击(?:属性)?\+([0-9.]+)"""),
                defense = value("""防御(?:属性)?\+([0-9.]+)"""),
                strategy = value("""谋略(?:属性)?\+([0-9.]+)"""),
                speed = value("""速度(?:属性)?\+([0-9.]+)"""),
                siege = value("""攻城(?:属性)?\+([0-9.]+)"""),
                hitRange = value("""攻击距离\+([0-9.]+)"""),
            )
        }

        private fun Map<String, String>.int(name: String): Int =
            intOrNull(name) ?: 0

        private fun Map<String, String>.intOrNull(name: String): Int? =
            this[name]?.takeIf { it.isNotBlank() && it != "--" }?.toDoubleOrNull()?.toInt()

        private fun Map<String, String>.intList(name: String): List<Int> =
            Collections.unmodifiableList(
                this[name]
                    .orEmpty()
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toList(),
            )

    }
}

private object Json {
    private val mapper = jacksonObjectMapper()
    private val rowsType = object : TypeReference<List<Map<String, Any?>>>() {}

    fun readArray(path: Path): List<Map<String, Any?>> {
        if (!path.exists()) return emptyList()
        return mapper.readValue(path.toFile(), rowsType)
    }

    fun readResourceArray(name: String): List<Map<String, Any?>> =
        resourceStream(name).use { mapper.readValue(it, rowsType) }
}

private fun Map<String, Any?>.string(name: String): String =
    this[name]?.toString().orEmpty()

private fun Map<String, Any?>.int(name: String): Int =
    when (val value = this[name]) {
        is Number -> value.toInt()
        is String -> value.toDoubleOrNull()?.toInt() ?: 0
        else -> 0
    }

private object Csv {
    fun read(path: Path): List<Map<String, String>> {
        val lines = Files.readAllLines(path)
        return parse(lines)
    }

    fun readResource(name: String): List<Map<String, String>> =
        resourceStream(name).bufferedReader().use { parse(it.readLines()) }

    private fun parse(lines: List<String>): List<Map<String, String>> {
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val values = parseLine(line)
                header.mapIndexed { index, name -> name to values.getOrElse(index) { "" } }.toMap()
            }
    }

    private fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }
}

private fun resourceStream(name: String): InputStream =
    BattleConfigRepository::class.java.classLoader?.getResourceAsStream(name)
        ?: error("Missing packaged battle configuration: $name")
