package com.local.stzb.feature.overlay

data class OverlayHero(val name: String, val advance: Int)

data class OverlayTeam(
    val teamId: Int,
    val playerName: String,
    val stateText: String,
    val heroes: List<OverlayHero>,
    val destination: String,
    val arrivalAt: Long?,
    val updatedAt: Long,
    val winRate: Double?,
)

data class OverlayMonitorState(
    val teams: List<OverlayTeam> = emptyList(),
    val captureRunning: Boolean = false,
    val error: String? = null,
)
