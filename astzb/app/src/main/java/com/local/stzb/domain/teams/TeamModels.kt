package com.local.stzb.domain.teams

data class TeamHero(
    val heroId: Long,
    val iconId: Long,
    val name: String,
    val skillNames: List<String> = emptyList(),
)

data class PlayerTeam(
    val player: String,
    val unionName: String,
    val side: String,
    val heroes: List<TeamHero>,
    val skillNames: List<String>,
    val battles: Int,
    val wins: Int,
    val winRate: Double,
) {
    val sideLabel: String get() = if (side == "def") "守方" else "攻方"
}

interface TeamsRepository {
    fun loadTeams(): List<PlayerTeam>
}
