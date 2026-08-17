package com.local.stzb.domain.battlefield

data class CaptureStatus(
    val running: Boolean,
    val label: String,
    val lastEventAt: Long?,
    val warning: String? = null,
)

data class BattlefieldMetrics(
    val activeMarches: Int,
    val arrivingSoon: Int,
    val todayBattles: Int,
    val siegeEvents: Int,
)

data class BattlefieldSnapshot(
    val capture: CaptureStatus,
    val metrics: BattlefieldMetrics,
    val events: List<BattlefieldEvent>,
    val selectedCategories: Set<EventCategory> = EventCategory.entries.toSet(),
    val paused: Boolean = false,
    val bufferedEventCount: Int = 0,
)
