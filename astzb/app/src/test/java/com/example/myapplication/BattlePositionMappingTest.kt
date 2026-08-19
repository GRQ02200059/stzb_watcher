package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattlePositionMappingTest {
    @Test
    fun defenderSkillPositionsFollowSixFiveFourBattleOrder() {
        assertEquals(0, BattlePositionMapping.defenderHeroIndexForSkillPosition(6))
        assertEquals(1, BattlePositionMapping.defenderHeroIndexForSkillPosition(5))
        assertEquals(2, BattlePositionMapping.defenderHeroIndexForSkillPosition(4))
        assertNull(BattlePositionMapping.defenderHeroIndexForSkillPosition(3))
    }

    @Test
    fun attackerSkillPositionsRemainOneTwoThree() {
        assertEquals(0, BattlePositionMapping.attackerHeroIndexForSkillPosition(1))
        assertEquals(1, BattlePositionMapping.attackerHeroIndexForSkillPosition(2))
        assertEquals(2, BattlePositionMapping.attackerHeroIndexForSkillPosition(3))
        assertNull(BattlePositionMapping.attackerHeroIndexForSkillPosition(4))
    }
}
