package com.local.stzb.domain.rankings

enum class RankingCategory(val label: String) { BATTLE("战功榜"), UNION("同盟势力"), PLAYER_POWER("个人势力") }
enum class RankingPage(val label: String) { RANKINGS("排行榜"), TEAM_REPORT("团队报表") }
enum class ReportDimension(val queryValue: String, val label: String) { GROUP("group", "分组"), PLAYER("player", "成员") }
enum class ReportPeriod(val queryValue: String, val label: String) { ALL("all", "全部"), TODAY("today", "今日"), WEEK("week", "本周") }

data class RankingRow(
    val rank: Int,
    val name: String,
    val groupName: String = "",
    val value: Long,
    val battles: Int = 0,
    val winRate: Double = 0.0,
    val members: Int = 0,
)

data class RankingSnapshot(val battle: List<RankingRow>, val unions: List<RankingRow>, val playerPower: List<RankingRow>) {
    fun rows(category: RankingCategory) = when (category) {
        RankingCategory.BATTLE -> battle
        RankingCategory.UNION -> unions
        RankingCategory.PLAYER_POWER -> playerPower
    }
}

data class TeamReportRow(
    val rank: Int,
    val name: String,
    val groupName: String,
    val members: Int,
    val battles: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val siegeBattles: Int,
    val siegeWins: Int,
    val totalGongxun: Long,
    val averageGongxun: Double,
    val averagePower: Double,
    val power: Long,
    val winRate: Double,
)

data class TeamReportSnapshot(val rows: List<TeamReportRow>, val groups: List<String>)

interface RankingRepository {
    fun loadRankings(): RankingSnapshot
    fun loadTeamReport(dimension: ReportDimension, period: ReportPeriod, group: String = ""): TeamReportSnapshot
}
