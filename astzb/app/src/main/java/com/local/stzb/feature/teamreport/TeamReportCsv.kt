package com.local.stzb.feature.teamreport

import com.local.stzb.domain.rankings.*
import java.util.Locale

object TeamReportCsv {
    fun encode(snapshot: TeamReportSnapshot, dimension: ReportDimension, period: ReportPeriod, group: String): String = buildString {
        append('\uFEFF')
        append("维度,").append(csv(dimension.exportLabel)).append(",周期,").append(csv(period.label)).append(",分组,").append(csv(group.ifBlank { "全部" })).append('\n')
        append("排名,名称,分组,人数,战报,胜,负,平,胜率,攻城,攻城胜,总功勋,平均功勋,平均势力,总势力\n")
        snapshot.rows.forEach { row ->
            append(listOf(
                row.rank, csv(row.name), csv(row.groupName), row.members, row.battles, row.wins, row.losses, row.draws,
                "${formatDecimal(row.winRate)}%", row.siegeBattles, row.siegeWins, row.totalGongxun,
                formatDecimal(row.averageGongxun), formatDecimal(row.averagePower), row.power,
            ).joinToString(","))
            append('\n')
        }
    }

    private fun csv(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

    private val ReportDimension.exportLabel: String
        get() = when (this) {
            ReportDimension.GROUP -> "团队"
            ReportDimension.PLAYER -> "成员"
        }
}
