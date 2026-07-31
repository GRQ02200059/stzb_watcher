package com.local.stzb.domain.battlefield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BattlefieldEventTest {
    @Test
    fun eventIdentityIsStableAcrossPresentationChanges() {
        val event = BattlefieldEvent(
            id = "march:42:1700000000",
            occurredAt = 1_700_000_000L,
            category = EventCategory.MARCH,
            priority = EventPriority.NORMAL,
            title = "队伍出发",
            summary = "甲 100,100 → 101,101",
            target = EventTarget.Team(42),
        )

        assertEquals("march:42:1700000000", event.id)
        assertFalse(event.isUrgent)
    }
}
