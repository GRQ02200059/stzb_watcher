package com.local.stzb.data.battlefield

import com.example.myapplication.LocalTeamMove
import com.example.myapplication.LocalBattleField
import com.example.myapplication.LocalFullBattle
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import com.local.stzb.domain.battlefield.EventTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class BattlefieldEventMapperTest {
    @Test
    fun moveBecomesReadableMarchEvent() {
        val move = LocalTeamMove(
            teamId = 42,
            moveType = 1,
            subjectId = 7,
            ownerUid = 9,
            ownerName = "前锋",
            ownerUnion = "测试盟",
            fromWid = 100010,
            toWid = 100020,
            currentWid = 100015,
            fromXy = "10,10",
            toXy = "10,20",
            currentXy = "10,15",
            startTime = 1_700_000_000L,
            arriveTime = 1_700_000_600L,
            speed = 100,
        )

        val event = BattlefieldEventMapper.fromMove(move)

        assertEquals(EventCategory.MARCH, event.category)
        assertEquals("前锋 · 测试盟", event.title)
        assertEquals("10,10 → 10,20", event.summary)
        assertEquals(EventTarget.Team(42), event.target)
    }

    @Test
    fun battleCodesBecomeReadableLabelsWithoutRawIdentifiers() {
        val event = BattlefieldEventMapper.fromBattle(fullBattle())

        assertEquals(EventCategory.BATTLE, event.category)
        assertEquals(EventPriority.IMPORTANT, event.priority)
        assertEquals("前锋 vs 守军", event.title)
        assertEquals("洛阳 · 攻城 · 胜利", event.summary)
        assertFalse(event.title.contains("9001"))
        assertFalse(event.summary.contains("9001"))
        assertEquals(EventTarget.Battle(9001), event.target)
    }

    @Test
    fun siegeWidBecomesCoordinatesWithoutExposingSourceId() {
        val event = BattlefieldEventMapper.fromSiege(
            LocalBattleField(
                wid = 100020,
                attackerUid = 9988,
                nearbyUids = "1,2,3",
                nearbyCount = 3,
                sourceMsgId = "raw-message-123",
            ),
        )

        assertEquals("攻城目标 10,20", event.title)
        assertEquals("附近 3 人", event.summary)
        assertFalse(event.title.contains("100020"))
        assertFalse(event.summary.contains("raw-message-123"))
        assertEquals(EventTarget.Cell(100020), event.target)
    }

    private fun fullBattle() = LocalFullBattle(
        battleId = 9001,
        time = 1_700_000_000L,
        result = 1,
        fightType = 1,
        wid = 100020,
        widName = "洛阳",
        widCode = "raw-wid-code",
        attackerName = "前锋",
        attackerUid = "11",
        attackerUnion = "测试盟",
        attackerUnionId = 12,
        attackerPower = 100,
        attackerGongxun = 20,
        attackerHp = 90,
        defenderName = "守军",
        defenderUid = "21",
        defenderUnion = "守方盟",
        defenderUnionId = 22,
        defenderLevel = 10,
        defenderPower = 90,
        defenderGongxun = 10,
        defenderHp = 80,
        weather = 0,
        inNight = 0,
        isNpc = 0,
        isAi = 0,
        blockId = 0,
        cityType = 1,
        borrowLand = 0,
        garrison = 1,
        firstOccupyLvnLand = 0,
        attackerTeamId = 31,
        defenderTeamId = 32,
        attackerAdvance = "",
        defenderAdvance = "",
        attackerHeroType = "",
        defenderHeroType = "",
        attackerGearInfo = "",
        defenderGearInfo = "",
        allSkillInfo = "",
        attackAllHeroInfo = "",
        defendAllHeroInfo = "",
        attackAllSubHeroInfo = "",
        defendAllSubHeroInfo = "",
        attackSupportUserInfo = "",
        defendSupportUserInfo = "",
        sourceMsgId = "raw-source-id",
        rawJson = "{}",
        attackerHeroes = emptyList(),
        defenderHeroes = emptyList(),
    )
}
