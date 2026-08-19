package com.local.stzb.data.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {
    @Test
    fun formulaMatchesWebAllianceContributionPreset() {
        val metrics = ScoreMetrics(
            battles = 10, wins = 4, draws = 2, gongxunTotal = 3000,
            mainCityCount = 2, tearCount = 1, attendanceCount = 3,
        )

        val result = ScoreEngine.calculate(metrics, ScorePresets.ALLIANCE_CONTRIBUTION, adjustment = 1.5)

        assertEquals(22.0, result.battleScore, 0.001)
        assertEquals(16.0, result.siegeScore, 0.001)
        assertEquals(1.5, result.adjustmentScore, 0.001)
        assertEquals(39.5, result.score, 0.001)
        assertEquals(3.0, result.components.getValue("gongxun"), 0.001)
    }

    @Test fun invalidRuleValuesAreRejected() {
        val valid = ScorePresets.ALLIANCE_CONTRIBUTION
        listOf(0.0, -1.0, 1_000_001.0).forEach { divisor ->
            assertTrue(runCatching { valid.copy(gongxunDivisor = divisor).validate() }.isFailure)
        }
        assertTrue(runCatching { valid.copy(winWeight = 1001.0).validate() }.isFailure)
    }

    @Test fun previewDoesNotWriteAndTokenCanOnlyBeConfirmedOnce() {
        val repository = FakeScoreRepository()
        val service = ScoreService(repository) { "token-1" }
        val rule = repository.createRule("默认规则", "alliance_contribution", ScorePresets.ALLIANCE_CONTRIBUTION)
        repository.activateRule(rule.id)

        val preview = service.preview()

        assertEquals(0, repository.replaceCalls)
        assertEquals(1, preview.rows.size)
        assertEquals("token-1", preview.token)
        assertEquals(39.5, preview.rows.single().score, 0.001)

        val written = service.confirm(preview.token).getOrThrow()
        assertEquals(1, written)
        assertEquals(1, repository.replaceCalls)
        assertTrue(service.confirm(preview.token).isFailure)
    }

    private class FakeScoreRepository : ScoreRepository {
        private var nextId = 1L
        private val rules = mutableListOf<ScoreRuleVersion>()
        var replaceCalls = 0
        override fun rules(): List<ScoreRuleVersion> = rules
        override fun createRule(name: String, presetKey: String, config: ScoreRuleConfig): ScoreRuleVersion =
            ScoreRuleVersion(nextId++, rules.size + 1, name, presetKey, config.validate(), RuleStatus.DRAFT).also(rules::add)
        override fun activateRule(id: Long): ScoreRuleVersion {
            rules.replaceAll { it.copy(status = if (it.id == id) RuleStatus.ACTIVE else RuleStatus.RETIRED) }
            return rules.first { it.id == id }
        }
        override fun activeRule(): ScoreRuleVersion? = rules.firstOrNull { it.status == RuleStatus.ACTIVE }
        override fun adjustments(): List<ScoreAdjustment> = listOf(ScoreAdjustment(1, "测试玩家", 1.5, "补偿"))
        override fun addAdjustment(playerName: String, points: Double, reason: String): ScoreAdjustment = error("unused")
        override fun metrics(): List<PlayerScoreMetrics> = listOf(PlayerScoreMetrics("测试玩家", "测试盟", ScoreMetrics(10, 4, 2, 3000, 2, 1, 3)))
        override fun replaceScores(ruleId: Long, rows: List<ScoreRow>) { replaceCalls++ }
        override fun scores(): List<ScoreRow> = emptyList()
    }

}
