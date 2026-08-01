package com.local.stzb.data.battlefield

import com.example.myapplication.LocalBattleMonitorParser
import org.junit.Assert.assertEquals
import org.junit.Test

class BattlefieldGoldenFixtureTest {
    @Test
    fun sanitized5028PayloadMapsToStableReadableEvent() {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("battlefield/5028_move_sample.json"),
        ).readText()

        val snapshot = checkNotNull(LocalBattleMonitorParser.parse(payload, sourceLabel = "5028"))
        val move = snapshot.moves.single()
        val event = BattlefieldEventMapper.fromMove(move)

        assertEquals("march:42:1700000600", event.id)
        assertEquals("测试玩家 · 测试同盟", event.title)
        assertEquals("地图队伍 · 10,10 → 10,20 · 士气 88 · 队伍类型 2,22 / 1,31 / 3,23 · 交战中", event.summary)
        assertEquals(7, move.ownerUid)
        assertEquals(100005, move.resideWid)
        assertEquals(100015, move.stayWid)
        assertEquals(2, move.targetType)
        assertEquals("外观", move.armyFacadeList)
        assertEquals("2,22;1,31;3,23;", move.armyHeroType)
        assertEquals(88, move.morale)
        assertEquals("1001,1002", move.buffIdList)
        assertEquals("交战中", move.battleShow)
        assertEquals(99, move.stateId)
        assertEquals(8, snapshot.marker)
    }
}
