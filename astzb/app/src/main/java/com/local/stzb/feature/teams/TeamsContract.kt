package com.local.stzb.feature.teams

import com.local.stzb.domain.teams.PlayerTeam

enum class TeamSide(val label: String, val value: String?) {
    ALL("全部", null), ATTACK("攻方", "atk"), DEFENSE("守方", "def")
}

data class TeamsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val side: TeamSide = TeamSide.ALL,
    val allTeams: List<PlayerTeam> = emptyList(),
    val visibleTeams: List<PlayerTeam> = emptyList(),
    val error: String? = null,
)

sealed interface TeamsIntent {
    data object Refresh : TeamsIntent
    data class QueryChanged(val value: String) : TeamsIntent
    data class SideChanged(val value: TeamSide) : TeamsIntent
}
