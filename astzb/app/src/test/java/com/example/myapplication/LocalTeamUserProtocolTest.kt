package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTeamUserProtocolTest {
    @Test fun command103UsesConfirmedHeadAndWuxunFields() {
        val slots = MutableList<Any?>(31) { 0 }
        slots[0] = 42
        slots[1] = "玩家甲"
        slots[10] = 1234
        slots[13] = "一团"
        slots[16] = 88
        slots[17] = "frame-a"
        slots[26] = 567
        slots[27] = 8901
        slots[30] = 1700000000
        val payload = org.json.JSONArray().put(org.json.JSONArray(slots)).toString()
        val packet = LocalStzbPacket("103", 0, "json", "fixture", "", payload, "")

        val user = LocalAuxiliaryParser.parseTeamUserRows(packet).single()

        assertEquals(1234, user.wuxun)
        assertEquals(88, user.headId)
        assertEquals("frame-a", user.headFrame)
        assertEquals(567, user.weekWuxun)
        assertEquals(8901, user.totalWuxun)
        assertTrue(user.heroConfigId == 0 && user.heroSkills.isEmpty())
    }
}
