package com.local.stzb.data.score

import kotlin.math.round

data class ScoreRuleConfig(
    val battleWeight: Double,
    val winWeight: Double,
    val drawWeight: Double,
    val gongxunDivisor: Double,
    val mainCityWeight: Double,
    val tearWeight: Double,
    val attendanceWeight: Double,
) {
    fun validate(): ScoreRuleConfig {
        val weighted = listOf(battleWeight, winWeight, drawWeight, mainCityWeight, tearWeight, attendanceWeight)
        require(weighted.all { it.isFinite() && it in -1000.0..1000.0 }) { "积分权重必须在 -1000 到 1000 之间" }
        require(gongxunDivisor.isFinite() && gongxunDivisor > 0.0 && gongxunDivisor <= 1_000_000.0) { "武勋除数必须在 (0, 1000000] 内" }
        return this
    }
}

object ScorePresets {
    val ALLIANCE_CONTRIBUTION = ScoreRuleConfig(1.0, 2.0, 0.5, 1000.0, 5.0, 3.0, 1.0)
    val SEASON_REWARD = ScoreRuleConfig(1.5, 2.5, 0.5, 800.0, 8.0, 4.0, 2.0)
    val SIEGE_PRIORITY = ScoreRuleConfig(0.5, 1.0, 0.25, 2000.0, 12.0, 7.0, 4.0)
    fun byKey(key: String): ScoreRuleConfig = when (key) {
        "season_reward" -> SEASON_REWARD
        "siege_priority" -> SIEGE_PRIORITY
        else -> ALLIANCE_CONTRIBUTION
    }
}

data class ScoreMetrics(
    val battles: Int = 0, val wins: Int = 0, val draws: Int = 0, val gongxunTotal: Int = 0,
    val mainCityCount: Int = 0, val tearCount: Int = 0, val attendanceCount: Int = 0,
)
data class ScoreBreakdown(
    val metrics: ScoreMetrics, val components: Map<String, Double>, val battleScore: Double,
    val siegeScore: Double, val adjustmentScore: Double, val score: Double,
)

object ScoreEngine {
    fun calculate(metrics: ScoreMetrics, rule: ScoreRuleConfig, adjustment: Double = 0.0): ScoreBreakdown {
        rule.validate(); require(adjustment.isFinite()) { "调整分必须是有限数值" }
        val components = linkedMapOf(
            "battles" to r2(metrics.battles * rule.battleWeight),
            "wins" to r2(metrics.wins * rule.winWeight),
            "draws" to r2(metrics.draws * rule.drawWeight),
            "gongxun" to r2(metrics.gongxunTotal / rule.gongxunDivisor),
            "mainCity" to r2(metrics.mainCityCount * rule.mainCityWeight),
            "tear" to r2(metrics.tearCount * rule.tearWeight),
            "attendance" to r2(metrics.attendanceCount * rule.attendanceWeight),
        )
        val battle = r2(components.getValue("battles") + components.getValue("wins") + components.getValue("draws") + components.getValue("gongxun"))
        val siege = r2(components.getValue("mainCity") + components.getValue("tear") + components.getValue("attendance"))
        return ScoreBreakdown(metrics, components, battle, siege, r2(adjustment), r2(battle + siege + adjustment))
    }
    private fun r2(value: Double): Double = round(value * 100.0) / 100.0
}

enum class RuleStatus { DRAFT, ACTIVE, RETIRED }
data class ScoreRuleVersion(val id: Long, val version: Int, val name: String, val presetKey: String, val config: ScoreRuleConfig, val status: RuleStatus)
data class ScoreAdjustment(val id: Long, val playerName: String, val points: Double, val reason: String)
data class PlayerScoreMetrics(val playerName: String, val unionName: String, val metrics: ScoreMetrics)
data class ScoreRow(
    val rank: Int, val playerName: String, val unionName: String, val battleScore: Double, val siegeScore: Double,
    val adjustmentScore: Double, val score: Double, val metrics: ScoreMetrics,
)
data class ScorePreview(val token: String, val rule: ScoreRuleVersion, val rows: List<ScoreRow>)

interface ScoreRepository {
    fun rules(): List<ScoreRuleVersion>
    fun createRule(name: String, presetKey: String, config: ScoreRuleConfig): ScoreRuleVersion
    fun activateRule(id: Long): ScoreRuleVersion
    fun activeRule(): ScoreRuleVersion?
    fun adjustments(): List<ScoreAdjustment>
    fun addAdjustment(playerName: String, points: Double, reason: String): ScoreAdjustment
    fun metrics(): List<PlayerScoreMetrics>
    fun replaceScores(ruleId: Long, rows: List<ScoreRow>)
    fun scores(): List<ScoreRow>
}

class ScoreService(
    private val repository: ScoreRepository,
    private val tokenFactory: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val previews = mutableMapOf<String, ScorePreview>()

    fun preview(): ScorePreview {
        val rule = repository.activeRule() ?: error("请先激活积分规则")
        val adjustmentByPlayer = repository.adjustments().groupBy(ScoreAdjustment::playerName).mapValues { (_, rows) -> rows.sumOf(ScoreAdjustment::points) }
        val calculated = repository.metrics().map { player ->
            val breakdown = ScoreEngine.calculate(player.metrics, rule.config, adjustmentByPlayer[player.playerName] ?: 0.0)
            ScoreRow(0, player.playerName, player.unionName, breakdown.battleScore, breakdown.siegeScore, breakdown.adjustmentScore, breakdown.score, player.metrics)
        }.sortedWith(compareByDescending<ScoreRow>(ScoreRow::score).thenBy(ScoreRow::playerName))
            .mapIndexed { index, row -> row.copy(rank = index + 1) }
        return ScorePreview(tokenFactory(), rule, calculated).also { previews[it.token] = it }
    }

    fun confirm(token: String): Result<Int> = runCatching {
        val preview = previews.remove(token) ?: error("预览令牌无效或已使用")
        repository.replaceScores(preview.rule.id, preview.rows)
        preview.rows.size
    }
}
