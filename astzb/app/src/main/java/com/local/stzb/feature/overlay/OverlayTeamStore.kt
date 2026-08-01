package com.local.stzb.feature.overlay

import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.EventTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayTeamStore {
    private val teams = LinkedHashMap<String, OverlayTeam>()
    private val mutableState = MutableStateFlow(OverlayMonitorState())
    val state: StateFlow<OverlayMonitorState> = mutableState.asStateFlow()

    fun accept(snapshot: BattlefieldSnapshot): OverlayMonitorState {
        snapshot.events.sortedBy { it.occurredAt }.forEach { event ->
            val target = event.target as? EventTarget.Team ?: return@forEach
            val presentation = event.teamPresentation
            val player = event.title.substringBefore(" · ").ifBlank { "未知玩家" }
            val identity = event.overlayIdentity(target.teamId)
            teams.remove(identity)
            teams[identity] = OverlayTeam(
                teamId = target.teamId,
                playerName = player,
                stateText = presentation?.stateText ?: event.teamContext?.stateText ?: event.fallbackState(),
                heroes = presentation?.heroes?.take(3)?.map { OverlayHero(it.name, it.advance) }.orEmpty(),
                destination = presentation?.destinationText ?: event.teamContext?.destinationText ?: event.fallbackDestination(),
                arrivalAt = presentation?.arrivalAt ?: event.teamContext?.arrivalAt,
                updatedAt = event.occurredAt,
                winRate = presentation?.winRate,
            )
            while (teams.size > TEAM_LIMIT) {
                teams.remove(teams.keys.first())
            }
        }
        val next = OverlayMonitorState(teams.values.toList().asReversed(), snapshot.capture.running)
        mutableState.value = next
        return next
    }

    fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    private companion object {
        const val TEAM_LIMIT = 100
    }
}

private fun com.local.stzb.domain.battlefield.BattlefieldEvent.overlayIdentity(teamId: Int): String {
    val owner = teamContext?.ownerUid?.takeIf { it > 0 }?.let { "uid:$it" }
    val matchedLineup = teamPresentation?.heroes
        ?.map { it.heroId }
        ?.filter { it > 0 }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(",")
    val packetLineup = teamContext?.armyHeroType
        ?.trim()
        ?.trim(';')
        ?.replace(" ", "")
        ?.takeIf { it.isNotBlank() }
    val lineup = matchedLineup ?: packetLineup
    return if (owner != null && lineup != null) "team:$owner:$lineup" else "team-id:$teamId"
}

private fun com.local.stzb.domain.battlefield.BattlefieldEvent.fallbackState(): String = details
    .firstOrNull { it.startsWith("行动：") }
    ?.substringAfter("行动：")
    ?.substringBefore(" ·")
    ?.ifBlank { "未知状态" }
    ?: "未知状态"

private fun com.local.stzb.domain.battlefield.BattlefieldEvent.fallbackDestination(): String = summary
    .substringAfter("→", "")
    .substringBefore(" ·")
    .trim()
    .ifBlank { "未知目的地" }
