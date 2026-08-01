package com.local.stzb.data.rankings

import com.local.stzb.domain.rankings.ReportDimension
import com.local.stzb.domain.rankings.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyRankingRepositoryTest {
    @Test fun mapsEveryRankingAndTeamReportMetric() {
        val source = object : LegacyRankingSource {
            override fun battleRankings() = listOf(SourceRankingRow(1, "甲", "一团", 900, 12, 75.0))
            override fun unionRankings() = listOf(SourceRankingRow(2, "盟", "", 8000, 0, 0.0, 40))
            override fun playerPowerRankings() = listOf(SourceRankingRow(3, "乙", "州1", 7000, 0, 0.0))
            override fun teamReport(dimension: String, period: String, group: String) = if (dimension == "group") {
                listOf(SourceReportRow("一团", "一团", 3, 10, 6, 2, 2, 4, 3, 999, 333.0, 5000.0, 0, 70.0))
            } else listOf(SourceReportRow("甲", "一团", 1, 5, 3, 1, 1, 2, 1, 400, 400.0, 6000.0, 6000, 70.0))
        }
        val repository = LegacyRankingRepository(source)
        val rankings = repository.loadRankings()
        assertEquals(900, rankings.battle.single().value)
        assertEquals(40, rankings.unions.single().members)
        assertEquals(3, rankings.playerPower.single().rank)
        val report = repository.loadTeamReport(ReportDimension.PLAYER, ReportPeriod.WEEK, "一团")
        assertEquals(listOf("一团"), report.groups)
        assertEquals(1, report.rows.single().losses)
        assertEquals(2, report.rows.single().siegeBattles)
        assertEquals(400, report.rows.single().totalGongxun)
    }
}
