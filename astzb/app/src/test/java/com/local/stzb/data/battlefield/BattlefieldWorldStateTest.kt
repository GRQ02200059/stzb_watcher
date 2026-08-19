package com.local.stzb.data.battlefield

import com.example.myapplication.LocalBattleMonitorParser
import com.example.myapplication.LocalBattleMonitorStore
import com.example.myapplication.LocalWorldEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class BattlefieldWorldStateTest {
    @After fun clear() = LocalBattleMonitorStore.clear()

    @Test fun fullSnapshotPublishesOnlyAfterFinalFrame() {
        LocalBattleMonitorStore.update(parse(packet(army(1), marker = 0)), "5026")
        assertNull(LocalBattleMonitorStore.latest())

        LocalBattleMonitorStore.update(parse(packet(army(2), marker = 10)), "5026")

        assertEquals(listOf(1, 2), LocalBattleMonitorStore.latest()!!.moves.map { it.teamId })
    }

    @Test fun fullSnapshotMergesMapEntitiesMarchesAndMembershipsAcrossFrames() {
        LocalBattleMonitorStore.update(parse(packet(
            army(1), marker = 0, chunks = "{\"10004\":{\"0\":[1]}}",
            warShips = "{\"8001\":[1]}", blockShips = "{\"40\":[8001]}",
            realMarch = "{\"9001\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14]}",
        )), "5026")
        LocalBattleMonitorStore.update(parse(packet(
            army(2), marker = 10, chunks = "{\"10005\":{\"0\":[2]}}",
            assistArmies = "{\"10001\":[1]}", blockAssist = "{\"41\":[10001]}",
            realMarch = "{\"9002\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14]}",
        )), "5026")

        val latest = LocalBattleMonitorStore.latest()!!
        assertEquals(setOf(10004, 10005), latest.mapStates.map { it.wid }.toSet())
        assertEquals(setOf(8001, 10001), latest.entities.map { it.entityId }.toSet())
        assertEquals(setOf(9001, 9002), latest.realMarches.map { it.id }.toSet())
        assertEquals(listOf(8001), latest.blockShipIds[40])
        assertEquals(listOf(10001), latest.blockAssistArmyIds[41])
        assertEquals(setOf("10004", "10005"), JSONObject(latest.slotPayloads.getValue(14)).keys().asSequence().toSet())
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

        val published = LocalBattleMonitorStore.update(parse(packet(army(2), marker = 10)), "5028")

        assertEquals(false, published)
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

    @Test fun parsesAllGenericEntitySlotsAndDocumentedDeleteSlots() {
        val snapshot = parse(packet(
            army(1), marker = 11,
            strategies = "{\"4001\":[1]}",
            nationStrategies = "{\"5001\":[1]}",
            warShips = "{\"8001\":[1,7]}",
            deletedShips = "[8002]",
            assistArmies = "{\"10001\":[1,7]}",
            deletedAssist = "[10002]",
            armyGroups = "{\"12001\":[1]}",
            shortMessages = "{\"13001\":[\"msg\"]}",
            extGarrison = "{\"16001\":[1]}",
            manorFamily = "{\"19001\":[1]}",
            blockShips = "{\"40\":[8001]}",
            blockAssist = "{\"40\":[10001]}",
            careerSupport = "{\"24001\":[1]}",
            removedCareerSupport = "[24002]",
            clearedHunter = "[13002]",
            clearedStrategy = "[4002]",
        ))

        assertEquals(
            setOf("strategy", "nation_strategy", "war_ship", "assist_army", "army_group", "short_message", "ext_garrison", "manor_family", "career_support"),
            snapshot.entities.map { it.category }.toSet(),
        )
        assertEquals(listOf(8002), snapshot.deletedEntityIds["war_ship"])
        assertEquals(listOf(10002), snapshot.deletedEntityIds["assist_army"])
        assertEquals(listOf(24002), snapshot.deletedEntityIds["career_support"])
        assertEquals(listOf(13002), snapshot.deletedEntityIds["short_message"])
        assertEquals(listOf(4002), snapshot.deletedEntityIds["strategy"])
        assertEquals(listOf(8001), snapshot.blockShipIds[40])
        assertEquals(listOf(10001), snapshot.blockAssistArmyIds[40])
        assertEquals((0..30).toSet(), snapshot.slotPayloads.keys)
        assertEquals("{\"4001\":[1]}", snapshot.slotPayloads[4])
    }

    @Test fun shipAndAssistEntitiesDeleteOnlyAfterLastBlockMembership() {
        LocalBattleMonitorStore.update(parse(packet(
            army(1), marker = 10,
            warShips = "{\"8001\":[1]}", assistArmies = "{\"10001\":[1]}",
            blockShips = "{\"40\":[8001],\"41\":[8001]}",
            blockAssist = "{\"40\":[10001],\"41\":[10001]}",
        )), "5026")
        LocalBattleMonitorStore.update(parse(packet(
            "", marker = 11, blockInfo = "[2,40]",
            deletedShips = "[8001]", deletedAssist = "[10001]",
        )), "5028")
        assertEquals(setOf(8001, 10001), LocalBattleMonitorStore.latest()!!.entities.map { it.entityId }.toSet())

        LocalBattleMonitorStore.update(parse(packet(
            "", marker = 12, blockInfo = "[2,41]",
            deletedShips = "[8001]", deletedAssist = "[10001]",
        )), "5028")
        assertEquals(emptyList<LocalWorldEntity>(), LocalBattleMonitorStore.latest()!!.entities)
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
        strategies: String = "{}", nationStrategies: String = "{}",
        warShips: String = "{}", deletedShips: String = "[]",
        assistArmies: String = "{}", deletedAssist: String = "[]",
        armyGroups: String = "{}", shortMessages: String = "{}",
        extGarrison: String = "{}", manorFamily: String = "{}",
        blockShips: String = "{}", blockAssist: String = "{}",
        careerSupport: String = "{}", removedCareerSupport: String = "[]",
        clearedHunter: String = "[]", clearedStrategy: String = "[]",
        chunks: String = "{}",
    ) = """[{}, {"7":["玩家",9,1,0,0,0,0,0,0,0,0,0,[1,0,"同盟"],null,null,0,0,0,0,0,0,"","",0,0]}, {}, {}, $strategies, $nationStrategies, {$armies}, $deleted, $warShips, $deletedShips, $assistArmies, $deletedAssist, $armyGroups, $shortMessages, $chunks, $clearChunks, $extGarrison, null, $marker, $manorFamily, $blockInfo, $blockArmies, $blockShips, $blockAssist, $careerSupport, $removedCareerSupport, $clearedHunter, $clearedStrategy, [], $realMarch, null]"""
}
