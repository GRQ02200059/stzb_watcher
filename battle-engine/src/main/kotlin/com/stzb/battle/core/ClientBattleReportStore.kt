package com.stzb.battle.core

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ThreadLocalRandom

data class BattleHeroSurface(
    val heroId: Int,
    val position: Int,
    val cardBorder: Int,
    val dynamicIcon: Int,
    val activeFeatureId: Int = 0,
)

data class ClientBattleReport(
    val battleId: Int,
    val ownerUserId: Int,
    val wid: Int,
    val timeSec: Int,
    val result: BattleResult,
    val attackerSurfaces: List<BattleHeroSurface> = emptyList(),
    val defenderSurfaces: List<BattleHeroSurface> = emptyList(),
)

class ClientBattleReportStore private constructor(
    private val nowSec: Int,
    private val reports: ConcurrentHashMap<Int, ClientBattleReport>,
    private val battleRandomFactory: (Int) -> BattleRandom,
    private val equipmentRepository: BattleEquipmentRepository,
) {
    private val battleSeq = AtomicInteger(maxOf(reports.keys.maxOrNull() ?: 0, nextBattleIdRange()))
    private val defaultBattleIds = ConcurrentHashMap<Int, Int>()

    fun getOrCreateDefault(ownerUserId: Int): ClientBattleReport {
        val battleId = defaultBattleIds.computeIfAbsent(ownerUserId) {
            battleSeq.incrementAndGet()
        }
        return reports[battleId] ?: createDefaultReport(
            nowSec = nowSec,
            battleId = battleId,
            ownerUserId = ownerUserId,
            battleRandomFactory = battleRandomFactory,
        ).also { reports[it.battleId] = it }
    }

    internal fun getOrCreateDefault(): ClientBattleReport =
        getOrCreateDefault(LEGACY_TEST_USER_ID)

    fun findOrDefault(ownerUserId: Int, battleId: Int): ClientBattleReport =
        reports[battleId]?.takeIf { it.ownerUserId == ownerUserId }
            ?: getOrCreateDefault(ownerUserId)

    internal fun findOrDefault(battleId: Int): ClientBattleReport =
        reports[battleId] ?: getOrCreateDefault(LEGACY_TEST_USER_ID)

    fun record(
        ownerUserId: Int,
        wid: Int,
        timeSec: Int,
        result: BattleResult,
        attackerSurfaces: List<BattleHeroSurface> = emptyList(),
        defenderSurfaces: List<BattleHeroSurface> = emptyList(),
    ): ClientBattleReport {
        val report = ClientBattleReport(
            battleId = battleSeq.incrementAndGet(),
            ownerUserId = ownerUserId,
            wid = wid,
            timeSec = timeSec,
            result = result,
            attackerSurfaces = attackerSurfaces,
            defenderSurfaces = defenderSurfaces,
        )
        reports[report.battleId] = report
        return report
    }

    internal fun record(wid: Int, timeSec: Int, result: BattleResult): ClientBattleReport =
        record(LEGACY_TEST_USER_ID, wid, timeSec, result)

    fun profileResponse(ownerUserId: Int, battleIds: List<Int>, serverId: Int): String {
        val ids = battleIds.ifEmpty { listOf(getOrCreateDefault(ownerUserId).battleId) }
        return profileResponseForReports(
            ids.map { findOrDefault(ownerUserId, it) },
            serverId,
        )
    }

    private fun profileResponseForReports(selectedReports: List<ClientBattleReport>, serverId: Int): String {
        val root = nf.arrayNode()
        root.add(serverId)
        root.add(nf.arrayNode().apply {
            selectedReports.distinctBy { it.battleId }
                .forEach { add(it.toProfileNode()) }
        })
        return mapper.writeValueAsString(root)
    }

    internal fun profileResponse(battleIds: List<Int>, serverId: Int): String =
        profileResponseForReports(
            battleIds.ifEmpty { listOf(getOrCreateDefault(LEGACY_TEST_USER_ID).battleId) }
                .map(::findOrDefault),
            serverId,
        )

    fun detailResponse(ownerUserId: Int, battleId: Int, serverId: Int, compressed: Boolean = true): String {
        val report = findOrDefault(ownerUserId, battleId)
        return detailResponse(report, serverId, compressed)
    }

    private fun detailResponse(report: ClientBattleReport, serverId: Int, compressed: Boolean): String {
        val replay = if (compressed) {
            BattleReportCodec.toCompressedClientReport(report.result)
        } else {
            ClientReportTextEncoder.encode(report.result)
        }
        val root = nf.arrayNode()
        root.add(serverId)
        root.add(
            nf.objectNode()
                .put("battle_id", report.battleId)
                .put("report", replay),
        )
        root.add(1)
        return mapper.writeValueAsString(root)
    }

    internal fun detailResponse(battleId: Int, serverId: Int, compressed: Boolean = true): String =
        detailResponse(findOrDefault(battleId), serverId, compressed)

    private fun ClientBattleReport.toProfileNode(): ObjectNode {
        val attacker = result.attacker.heroes.sortedBy { it.position }
        val defender = result.defender.heroes.sortedBy { it.position }
        val attackerStart = attacker.sumOf { it.maxTroops }
        val defenderStart = defender.sumOf { it.maxTroops }
        val attackerEnd = attacker.sumOf { it.troops }
        val defenderEnd = defender.sumOf { it.troops }
        return nf.objectNode().apply {
            put("battle_id", battleId)
            put("wid", wid)
            put("time", timeSec)
            put("result", result.outcome.toClientResult())
            put("fight_type", 3)
            put("city_type", 0)
            put("attack_name", "模拟攻方")
            put("defend_name", "守军")
            put("attack_base_heroid", attacker.firstOrNull()?.id?.value ?: 0)
            put("defend_base_heroid", defender.firstOrNull()?.id?.value ?: 0)
            put("attack_base_level", attacker.firstOrNull()?.level ?: 1)
            put("defend_base_level", defender.firstOrNull()?.level ?: 1)
            put("attacker_base_hero_detail", "")
            put("defender_base_hero_detail", "")
            put("attack_hp", attackerEnd)
            put("defend_hp", defenderEnd)
            put("attacker_force", 0)
            put("defender_force", 0)
            put("attack_all_hero_info", attacker.toHeroInfoString())
            put("defend_all_hero_info", defender.toHeroInfoString())
            put("attack_all_sub_hero_info", emptyRows(rows = 3, width = 5))
            put("defend_all_sub_hero_info", emptyRows(rows = 3, width = 5))
            put("attack_all_surface", attackerSurfaces.toHeroSurfaceInfo())
            put("defend_all_surface", defenderSurfaces.toHeroSurfaceInfo())
            put("attack_hero_type", "0,0,0,0,")
            put("defend_hero_type", "0,0,0,0,")
            put("attack_hero_type_advance", "0,0,0,0,")
            put("defend_hero_type_advance", "0,0,0,0,")
            put("attack_advance", attacker.toAdvanceInfo())
            put("defend_advance", defender.toAdvanceInfo())
            put("attacker_life_end_time", "")
            put("defender_life_end_time", "")
            put("attacker_army_effect", "")
            put("defender_army_effect", "")
            put("attacker_gear_info", attacker.toGearInfo())
            put("defender_gear_info", emptyFourRows(3))
            put("attacker_surface", attackerSurfaces.toAttackerBattleSurfaceInfo())
            put("defender_surface", defenderSurfaces.toDefenderBattleSurfaceInfo())
            put("attack_idu", "0,0,0,0,0")
            put("defend_idu", "0,0,0,0,0")
            put("lose_tips", "")
            put("all_skill_info", "")
            put("attack_union_name", "")
            put("defend_union_name", "")
            put("attacker_army_info", "0,$attackerStart,$attackerEnd")
            put("defender_army_info", "0,$defenderStart,$defenderEnd")
        }
    }

    private fun BattleOutcome.toClientResult(): Int =
        when (this) {
            BattleOutcome.ATTACKER_WIN -> 1
            BattleOutcome.DEFENDER_WIN -> 0
            BattleOutcome.DRAW -> 6
        }

    private fun List<BattleHero>.toHeroInfoString(): String =
        (0..2).joinToString(";") { position ->
            val hero = firstOrNull { it.position == position }
            if (hero == null) {
                "0,0,0,0,0"
            } else {
                "${hero.id.value},${hero.level},${hero.maxTroops},${hero.troops},${(hero.maxTroops - hero.troops).coerceAtLeast(0)}"
            }
        }

    private fun List<BattleHero>.toAdvanceInfo(): String =
        (listOf("0,0,0,0,0,0") + (0..2).map { position ->
            val advanceNum = firstOrNull { it.position == position }
                ?.advanceLevel
                ?.coerceAtLeast(0)
                ?: 0
            "$advanceNum,0,0,0,0,0"
        }).joinToString(";")

    private fun List<BattleHeroSurface>.toHeroSurfaceInfo(): String =
        (0..2).joinToString(";") { position ->
            val surface = firstOrNull { it.position == position }
            "${surface?.heroId ?: 0},${surface?.dynamicIcon ?: 0}"
        }

    private fun List<BattleHeroSurface>.toAttackerBattleSurfaceInfo(): String =
        (listOf("0,0,0") + (0..2).map { position ->
            val surface = firstOrNull { it.position == position }
            surface.toBattleSurfaceRow()
        }).joinToString(";")

    private fun List<BattleHeroSurface>.toDefenderBattleSurfaceInfo(): String =
        ((2 downTo 0).map { position ->
            val surface = firstOrNull { it.position == position }
            surface.toBattleSurfaceRow()
        } + "0,0,0").joinToString(";")

    private fun BattleHeroSurface?.toBattleSurfaceRow(): String =
        "${this?.cardBorder ?: 0},${this?.dynamicIcon ?: 0},${this?.activeFeatureId ?: 0}"

    private fun emptyFourRows(width: Int): String =
        emptyRows(rows = 4, width = width)

    private fun emptyRows(rows: Int, width: Int): String =
        List(rows) { List(width) { 0 }.joinToString(",") }.joinToString(";")

    /**
     * attacker_gear_info / defender_gear_info: 4 rows x 3 columns
     * (gear_id, level, feature_id), rows separated by ';'. Row 0 is a placeholder
     * the client never reads; hero rows 1..3 map to positions 0..2. The client
     * only needs a non-zero gear_id (column 0) to render the weapon, and it must
     * be a valid Tcfg_gear id, which BattleHero.equipmentIds already carries.
     */
    private fun List<BattleHero>.toGearInfo(): String =
        (listOf("0,0,0") + (0..2).map { position ->
            val gearId = firstOrNull { it.position == position }
                ?.equipmentIds
                ?.firstOrNull { it > 0 }
                ?: 0
            if (gearId > 0) {
                "$gearId,0,${equipmentRepository.defaultFeatureIdForGear(gearId)}"
            } else {
                "0,0,0"
            }
        }).joinToString(";")

    companion object {
        private val nf: JsonNodeFactory = JsonNodeFactory.instance
        private val mapper = jacksonObjectMapper()
        private const val MIN_BATTLE_ID = 100_000_000
        private const val MAX_BATTLE_ID = 2_000_000_000
        private const val BATTLE_ID_RANGE_SIZE = 10_000
        private const val LEGACY_TEST_USER_ID = 10001
        private val nextBattleIdRangeStart = AtomicInteger(
            ThreadLocalRandom.current().nextInt(MIN_BATTLE_ID, MAX_BATTLE_ID - BATTLE_ID_RANGE_SIZE),
        )

        fun global(): ClientBattleReportStore = Holder.INSTANCE

        fun createDefault(
            nowSec: Int = (System.currentTimeMillis() / 1000).toInt(),
            battleRandomFactory: (Int) -> BattleRandom = ::SeededBattleRandom,
            equipmentRepository: BattleEquipmentRepository = BattleEquipmentRepository.loadDefault(),
        ): ClientBattleReportStore =
            ClientBattleReportStore(nowSec, ConcurrentHashMap(), battleRandomFactory, equipmentRepository)

        fun createEmpty(
            nowSec: Int = (System.currentTimeMillis() / 1000).toInt(),
            equipmentRepository: BattleEquipmentRepository = BattleEquipmentRepository.loadDefault(),
        ): ClientBattleReportStore =
            ClientBattleReportStore(nowSec, ConcurrentHashMap(), ::SeededBattleRandom, equipmentRepository)

        private fun nextBattleIdRange(): Int =
            nextBattleIdRangeStart.getAndUpdate { current ->
                (current + BATTLE_ID_RANGE_SIZE)
                    .takeIf { it < MAX_BATTLE_ID }
                    ?: MIN_BATTLE_ID
            }

        private fun createDefaultReport(
            nowSec: Int,
            battleId: Int,
            ownerUserId: Int,
            battleRandomFactory: (Int) -> BattleRandom,
        ): ClientBattleReport {
            val config = BattleConfigRepository.loadDefault()
            val equipment = BattleEquipmentRepository.loadDefault()
            val builder = BattleTeamBuilder(config, equipment)
            val attacker = builder.build(
                listOf(
                    BattleHeroSpec(heroId = 100017, position = 0, troops = 1200, extraSkillIds = listOf(200031), level = 20, equipmentIds = listOf(1024)),
                    BattleHeroSpec(heroId = 100023, position = 1, troops = 1100, level = 18),
                    BattleHeroSpec(heroId = 100021, position = 2, troops = 1000, level = 18),
                ),
            )
            val defender = builder.build(
                listOf(
                    BattleHeroSpec(heroId = 100352, position = 0, troops = 1000, level = 18, equipmentIds = listOf(1025)),
                    BattleHeroSpec(heroId = 100345, position = 1, troops = 1000, level = 16),
                    BattleHeroSpec(heroId = 100344, position = 2, troops = 1000, level = 16),
                ),
            )
            return ClientBattleReport(
                battleId = battleId,
                ownerUserId = ownerUserId,
                wid = 10001,
                timeSec = nowSec,
                result = BattleEngine.resolve(
                    BattleRequest(attacker, defender, maxRounds = 8),
                    config,
                    battleRandomFactory(battleId xor nowSec),
                ),
            )
        }

        private object Holder {
            val INSTANCE: ClientBattleReportStore = createDefault()
        }
    }
}
