package com.local.stzb.feature.overlay

import com.local.stzb.domain.battlefield.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayTeamStoreTest {
    @Test fun overwritesSameTeamMovesItToTopAndRetainsMissingTeams() {
        val store = OverlayTeamStore()
        store.accept(snapshot(event(1, 100, "甲", "行军"), event(2, 200, "乙", "驻守")))
        assertEquals(listOf(2, 1), store.state.value.teams.map { it.teamId })

        store.accept(snapshot(event(1, 300, "甲新", "驻扎")))
        val teams = store.state.value.teams
        assertEquals(listOf(1, 2), teams.map { it.teamId })
        assertEquals("甲新", teams.first().playerName)
        assertEquals("驻扎", teams.first().stateText)
        assertEquals("乙", teams.last().playerName)
    }

    @Test fun mapsThreeHeroesDestinationTimeAndNullableWinRate() {
        val store = OverlayTeamStore()
        store.accept(snapshot(event(7, 500, "玩家", "调动", 66.7)))
        val team = store.state.value.teams.single()
        assertEquals(listOf("陆逊 5红", "周瑜 4红", "吕蒙 3红"), team.heroes.map { "${it.name} ${it.advance}红" })
        assertEquals("10,20", team.destination)
        assertEquals(600L, team.arrivalAt)
        assertEquals(66.7, team.winRate)

        store.accept(snapshot(event(8, 700, "未知", "要塞", null)))
        assertNull(store.state.value.teams.first().winRate)
    }

    private fun snapshot(vararg events: BattlefieldEvent) = BattlefieldSnapshot(
        CaptureStatus(true, "运行中", null), BattlefieldMetrics(0, 0, 0, 0), events.toList(), EventCategory.entries.toSet(), false, 0,
    )

    private fun event(id: Int, time: Long, player: String, state: String, rate: Double? = null) = BattlefieldEvent(
        id = "march:$id:$time", occurredAt = time, category = EventCategory.MARCH, priority = EventPriority.NORMAL,
        title = "$player · 测试盟", summary = "", target = EventTarget.Team(id),
        teamPresentation = BattlefieldTeamPresentation(
            teamId = id,
            heroes = listOf(
                BattlefieldHero("大营", 1, 1, "陆逊", 50, 5, emptyList()),
                BattlefieldHero("中军", 2, 2, "周瑜", 50, 4, emptyList()),
                BattlefieldHero("前锋", 3, 3, "吕蒙", 50, 3, emptyList()),
            ),
            routeText = "10,10 → 10,20", destinationText = "10,20", moraleText = "士气 100", stateText = state,
            recordText = "", arrivalText = "到达", arrivalAt = time + 100, winRate = rate,
        ),
    )
}
