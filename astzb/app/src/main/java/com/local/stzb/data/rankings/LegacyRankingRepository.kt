package com.local.stzb.data.rankings

import com.example.myapplication.LocalStzbRepository
import com.local.stzb.domain.rankings.*

data class SourceRankingRow(val rank: Int, val name: String, val groupName: String, val value: Long, val battles: Int, val winRate: Double, val members: Int = 0)
data class SourceReportRow(val name: String, val groupName: String, val members: Int, val battles: Int, val wins: Int, val losses: Int, val draws: Int, val siegeBattles: Int, val siegeWins: Int, val totalGongxun: Long, val averageGongxun: Double, val averagePower: Double, val power: Long, val winRate: Double)

interface LegacyRankingSource {
    fun battleRankings(): List<SourceRankingRow>
    fun unionRankings(): List<SourceRankingRow>
    fun playerPowerRankings(): List<SourceRankingRow>
    fun teamReport(dimension: String, period: String, group: String): List<SourceReportRow>
}

class AndroidLegacyRankingSource : LegacyRankingSource {
    override fun battleRankings() = LocalStzbRepository.loadBattleRankings().players.mapIndexed { index, row ->
        SourceRankingRow(index + 1, row.name, row.groupName, row.value, row.battles, row.winRate)
    }
    override fun unionRankings() = LocalStzbRepository.loadUnionRanks().map { row ->
        SourceRankingRow(row.rank, row.name, "", row.power, members = row.totalMember, battles = 0, winRate = 0.0)
    }
    override fun playerPowerRankings() = LocalStzbRepository.loadPlayerPowerRanks().map { row ->
        SourceRankingRow(row.rank, row.name, "州${row.region} · 区${row.area}", row.power, battles = 0, winRate = 0.0)
    }
    override fun teamReport(dimension: String, period: String, group: String) =
        LocalStzbRepository.loadTeamReport(dimension, period, group, 0).map { row ->
            SourceReportRow(row.name, row.groupName, row.members, row.battles, row.wins, row.loses, row.draws, row.cityBattles, row.cityWins, row.totalGongxun, row.avgGongxun, row.avgPower, row.power, row.winRate)
        }
}

class LegacyRankingRepository(private val source: LegacyRankingSource = AndroidLegacyRankingSource()) : RankingRepository {
    override fun loadRankings() = RankingSnapshot(source.battleRankings().mapRows(), source.unionRankings().mapRows(), source.playerPowerRankings().mapRows())

    override fun loadTeamReport(dimension: ReportDimension, period: ReportPeriod, group: String): TeamReportSnapshot {
        val allGroups = source.teamReport(ReportDimension.GROUP.queryValue, period.queryValue, "").map { it.name }.distinct()
        val rows = source.teamReport(dimension.queryValue, period.queryValue, group).mapIndexed { index, row ->
            TeamReportRow(index + 1, row.name, row.groupName, row.members, row.battles, row.wins, row.losses, row.draws, row.siegeBattles, row.siegeWins, row.totalGongxun, row.averageGongxun, row.averagePower, row.power, row.winRate)
        }
        return TeamReportSnapshot(rows, allGroups)
    }

    private fun List<SourceRankingRow>.mapRows() = map { RankingRow(it.rank, it.name, it.groupName, it.value, it.battles, it.winRate, it.members) }
}
