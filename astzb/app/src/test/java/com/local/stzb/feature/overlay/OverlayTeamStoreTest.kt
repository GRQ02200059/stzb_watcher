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

    @Test fun keepsUnmatchedTeamEventWithFallbackFields() {
        val store = OverlayTeamStore()
        val unmatched = BattlefieldEvent(
            id = "march:99:900", occurredAt = 900, category = EventCategory.MARCH, priority = EventPriority.NORMAL,
            title = "未匹配玩家 · 测试盟", summary = "地图队伍 · 10,10 → 12,34 · 士气 80",
            details = listOf("行动：驻守 · 目标：土地"), target = EventTarget.Team(99), teamPresentation = null,
        )

        store.accept(snapshot(unmatched))

        val team = store.state.value.teams.single()
        assertEquals(99, team.teamId)
        assertEquals("未匹配玩家", team.playerName)
        assertEquals("驻守", team.stateText)
        assertEquals("12,34", team.destination)
        assertEquals(emptyList<OverlayHero>(), team.heroes)
        assertNull(team.winRate)
    }

    @Test fun overwritesSameOwnerLineupAcrossChangedTeamIdsButKeepsDifferentLineups() {
        val store = OverlayTeamStore()
        store.accept(snapshot(
            event(101, 100, "玩家", "行军", ownerUid = 9527, heroIds = listOf(11, 22, 33)),
            event(102, 200, "玩家", "驻守", ownerUid = 9527, heroIds = listOf(44, 55, 66)),
        ))

        store.accept(snapshot(
            event(999, 300, "玩家", "驻扎", ownerUid = 9527, heroIds = listOf(11, 22, 33)),
        ))

        assertEquals(listOf(999, 102), store.state.value.teams.map { it.teamId })
        assertEquals(listOf("驻扎", "驻守"), store.state.value.teams.map { it.stateText })
    }

    @Test fun retainsOnlyNewestOneHundredTeams() {
        val store = OverlayTeamStore()

        store.accept(snapshot(*Array(101) { index ->
            event(index + 1, (index + 1).toLong(), "玩家${index + 1}", "行军")
        }))

        assertEquals(100, store.state.value.teams.size)
        assertEquals(101, store.state.value.teams.first().teamId)
        assertEquals(2, store.state.value.teams.last().teamId)
    }

    private fun snapshot(vararg events: BattlefieldEvent) = BattlefieldSnapshot(
        CaptureStatus(true, "运行中", null), BattlefieldMetrics(0, 0, 0, 0), events.toList(), EventCategory.entries.toSet(), false, 0,
    )

    private fun event(
        id: Int,
        time: Long,
        player: String,
        state: String,
        rate: Double? = null,
        ownerUid: Int = 0,
        heroIds: List<Long> = listOf(1, 2, 3),
    ) = BattlefieldEvent(
        id = "march:$id:$time", occurredAt = time, category = EventCategory.MARCH, priority = EventPriority.NORMAL,
        title = "$player · 测试盟", summary = "", target = EventTarget.Team(id),
        teamContext = BattlefieldTeamContext(ownerUid = ownerUid),
        teamPresentation = BattlefieldTeamPresentation(
            teamId = id,
            heroes = listOf(
                BattlefieldHero("大营", heroIds[0], 1, "陆逊", 50, 5, emptyList()),
                BattlefieldHero("中军", heroIds[1], 2, "周瑜", 50, 4, emptyList()),
                BattlefieldHero("前锋", heroIds[2], 3, "吕蒙", 50, 3, emptyList()),
            ),
            routeText = "10,10 → 10,20", destinationText = "10,20", moraleText = "士气 100", stateText = state,
            recordText = "", arrivalText = "到达", arrivalAt = time + 100, winRate = rate,
        ),
    )
}
