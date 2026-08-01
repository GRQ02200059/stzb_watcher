package com.local.stzb.feature.teamreport

import com.local.stzb.domain.rankings.*
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamReportCsvTest {
    @Test fun exportsCurrentFilterAndEveryReportMetricWithExcelSafeChinese() {
        val report = TeamReportSnapshot(
            listOf(TeamReportRow(1, "一团", "一团", 3, 10, 6, 2, 2, 4, 3, 999, 333.0, 5000.0, 15000, 70.0)),
            listOf("一团"),
        )
        val csv = TeamReportCsv.encode(report, ReportDimension.GROUP, ReportPeriod.WEEK, "")

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("维度,团队,周期,本周"))
        assertTrue(csv.contains("排名,名称,分组,人数,战报,胜,负,平,胜率,攻城,攻城胜,总功勋,平均功勋,平均势力,总势力"))
        assertTrue(csv.contains("1,一团,一团,3,10,6,2,2,70.0%,4,3,999,333.0,5000.0,15000"))
    }
}
