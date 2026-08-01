package com.local.stzb.domain.battlefield

enum class EventCategory { URGENT, BATTLE, MARCH, SIEGE, SYSTEM }

enum class EventPriority { NORMAL, IMPORTANT, CRITICAL }

sealed interface EventTarget {
    data class Battle(val battleId: Int) : EventTarget
    data class Team(val teamId: Int) : EventTarget
    data class Cell(val wid: Int) : EventTarget
    data object Diagnostics : EventTarget
    data object None : EventTarget
}

data class BattlefieldEvent(
    val id: String,
    val occurredAt: Long,
    val category: EventCategory,
    val priority: EventPriority,
    val title: String,
    val summary: String,
    val details: List<String> = emptyList(),
    val teamPresentation: BattlefieldTeamPresentation? = null,
    val target: EventTarget = EventTarget.None,
) {
    val isUrgent: Boolean
        get() = priority == EventPriority.CRITICAL || category == EventCategory.URGENT
}

data class BattlefieldTeamPresentation(
    val heroes: List<BattlefieldHero>,
    val routeText: String,
    val moraleText: String,
    val stateText: String,
    val recordText: String,
    val arrivalText: String,
)

data class BattlefieldHero(
    val positionLabel: String,
    val heroId: Long,
    val iconId: Long,
    val name: String,
    val level: Int,
    val advance: Int,
    val skills: List<BattlefieldSkill>,
)

data class BattlefieldSkill(val name: String, val level: Int)
