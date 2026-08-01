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
        val event = BattlefieldEventMapper.fromMove(snapshot.moves.single())

        assertEquals("march:42:1700000600", event.id)
        assertEquals("测试玩家 · 测试同盟", event.title)
        assertEquals("10,10 → 10,20", event.summary)
    }
}
