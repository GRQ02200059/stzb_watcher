package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuxiliaryParser6314Test {
    @Test
    fun command6314IsUnionBuildingHelpAndNeverBattlefield() {
        val packet = LocalStzbPacket(
            msgId = "6314",
            dataType = 0,
            decodeKind = "zlib",
            streamName = "fixture",
            preview = "",
            decodedText = "[[101,202,\"303,404\"]]",
            rawHex = "",
        )

        val records = LocalAuxiliaryParser.businessRecords(packet)

        assertEquals(1, records.size)
        assertEquals("union_building_help", records.single().type)
        assertEquals("101", records.single().key)
        assertTrue(records.single().title.contains("同盟建筑互助"))
        assertFalse(records.any { it.type == "battle_field" })
        assertFalse(records.single().subtitle.contains("attacker"))
        assertFalse(records.single().subtitle.contains("nearby"))
    }
}
