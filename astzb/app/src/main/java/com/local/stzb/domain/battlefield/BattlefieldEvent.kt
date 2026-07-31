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
    val target: EventTarget = EventTarget.None,
) {
    val isUrgent: Boolean
        get() = priority == EventPriority.CRITICAL || category == EventCategory.URGENT
}
