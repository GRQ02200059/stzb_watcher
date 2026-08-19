package com.local.stzb.data.battlefield

import com.example.myapplication.LocalBattleMonitorParser
import com.example.myapplication.LocalBattleMonitorStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattlefieldWorldStateTest {
    @After fun clear() = LocalBattleMonitorStore.clear()

    @Test fun fullSnapshotPublishesOnlyAfterFinalFrame() {
        LocalBattleMonitorStore.update(parse(packet(army(1), marker = 0)), "5026")
        assertNull(LocalBattleMonitorStore.latest())

        LocalBattleMonitorStore.update(parse(packet(army(2), marker = 10)), "5026")

        assertEquals(listOf(1, 2), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
    }

    @Test fun incrementalUpdateKeepsUnmentionedArmiesAndHonorsBlockScopedDeletion() {
        LocalBattleMonitorStore.update(parse(packet(army(1) + "," + army(2), marker = 10, blockArmies = "{\"40\":[1,2],\"41\":[1]}")), "5026")
        LocalBattleMonitorStore.update(parse(packet(army(2, morale = 75), marker = 11, blockInfo = "[2,40]")), "5028")
        assertEquals(listOf(1, 2), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
        assertEquals(75, LocalBattleMonitorStore.latest()!!.moves.single { it.teamId == 2 }.morale)

        LocalBattleMonitorStore.update(parse(packet("", marker = 12, deleted = "[1]", blockInfo = "[2,40]")), "5028")
        assertEquals(listOf(1, 2), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })

        LocalBattleMonitorStore.update(parse(packet("", marker = 13, deleted = "[1]", blockInfo = "[2,41]")), "5028")
        assertEquals(listOf(2), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
    }

    @Test fun zeroStateArmyDeletesGloballyEvenInsideBlockUpdate() {
        LocalBattleMonitorStore.update(
            parse(packet(army(1) + "," + army(2), marker = 10, blockArmies = "{\"40\":[1,2],\"41\":[1]}")),
            "5026",
        )

        LocalBattleMonitorStore.update(
            parse(packet("\"1\":[0]", marker = 11, blockInfo = "[2,40]")),
            "5028",
        )

        assertEquals(listOf(2), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
    }

    @Test fun deletedArmiesSlotIsIgnoredWithoutBlockDeleteMode() {
        LocalBattleMonitorStore.update(parse(packet(army(1), marker = 10)), "5026")

        LocalBattleMonitorStore.update(parse(packet("", marker = 11, deleted = "[1]")), "5028")

        assertEquals(listOf(1), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
    }

    @Test fun incrementalPacketMustBeNewerThanLatestFullSnapshotMarker() {
        LocalBattleMonitorStore.update(parse(packet(army(1), marker = 10)), "5026")

        LocalBattleMonitorStore.update(parse(packet(army(2), marker = 10)), "5028")

        assertEquals(listOf(1), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
    }

    @Test fun specialIncrementalMarkerBypassesFullSnapshotMarkerGate() {
        LocalBattleMonitorStore.update(parse(packet(army(1), marker = 10)), "5026")

        LocalBattleMonitorStore.update(parse(packet(army(2), marker = -999999999)), "5028")

        assertEquals(listOf(1, 2), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
    }

    @Test fun parsesClearChunksAndRealMarchFromSharedWorldContractSlots() {
        val payload = packet(
            army(1),
            marker = 11,
            clearChunks = "{\"10004\":[\"0\",\"4\"]}",
            realMarch = "{\"9001\":[10001,10002,3,10003,4,5,6,7,8,9,1,80,10,11]}",
        )

        val snapshot = parse(payload)

        assertEquals(listOf("0", "4"), snapshot.clearChunks[10004])
        assertEquals(10003, snapshot.realMarches.single().nextWid)
        assertEquals(9001, snapshot.realMarches.single().id)
    }

    private fun parse(payload: String) = checkNotNull(LocalBattleMonitorParser.parse(payload))

    private fun army(id: Int, morale: Int = 80) =
        "\"$id\":[1,7,100010,100020,1700000000,1700000600,0,0,0,0,100010,0,0,0,0,\"\",\"\",\"\",\"\",null,null,0,0,0,0,0,0,$morale,0,\"\",0,\"\",1]"

    private fun packet(
        armies: String,
        marker: Int,
        deleted: String = "[]",
        blockInfo: String = "null",
        blockArmies: String = "{}",
        clearChunks: String = "{}",
        realMarch: String = "{}",
    ) = """[{}, {"7":["玩家",9,1,0,0,0,0,0,0,0,0,0,[1,0,"同盟"],null,null,0,0,0,0,0,0,"","",0,0]}, {}, {}, {}, {}, {$armies}, $deleted, {}, [], {}, [], {}, {}, {}, $clearChunks, {}, null, $marker, {}, $blockInfo, $blockArmies, {}, {}, {}, [], [], [], [], $realMarch, null]"""
}
