package com.local.stzb.feature.overlay

import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayTeamStore {
    private val teams = LinkedHashMap<Int, OverlayTeam>()
    private val mutableState = MutableStateFlow(OverlayMonitorState())
    val state: StateFlow<OverlayMonitorState> = mutableState.asStateFlow()

    fun accept(snapshot: BattlefieldSnapshot): OverlayMonitorState {
        snapshot.events.sortedBy { it.occurredAt }.forEach { event ->
            val presentation = event.teamPresentation ?: return@forEach
            val player = event.title.substringBefore(" · ").ifBlank { "未知玩家" }
            teams.remove(presentation.teamId)
            teams[presentation.teamId] = OverlayTeam(
                teamId = presentation.teamId,
                playerName = player,
                stateText = presentation.stateText,
                heroes = presentation.heroes.take(3).map { OverlayHero(it.name, it.advance) },
                destination = presentation.destinationText,
                arrivalAt = presentation.arrivalAt,
                updatedAt = event.occurredAt,
                winRate = presentation.winRate,
            )
        }
        val next = OverlayMonitorState(teams.values.toList().asReversed(), snapshot.capture.running)
        mutableState.value = next
        return next
    }

    fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }
}
