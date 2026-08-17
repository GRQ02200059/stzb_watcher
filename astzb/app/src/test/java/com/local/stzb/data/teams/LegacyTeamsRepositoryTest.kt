package com.local.stzb.data.teams

import com.example.myapplication.LocalPlayerBattleTeam
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyTeamsRepositoryTest {
    @Test
    fun mapsOrderedHeroesAndNormalizesTeamSkills() {
        val source = object : LegacyTeamsSource {
            override fun loadTeams() = listOf(
                LocalPlayerBattleTeam(
                    player = "玩家甲",
                    unionName = "测试盟",
                    side = "atk",
                    heroes = "陆逊+周瑜+吕蒙",
                    heroIds = "101+102+103",
                    skills = "深谋远虑++神兵天降/深谋远虑,反计之策",
                    heroSkills = listOf(
                        listOf("深谋远虑", "不攻", "十面埋伏", "多余战法"),
                        listOf("神兵天降", "反计之策", "大赏三军"),
                        listOf("白衣渡江", "道行险阻"),
                    ),
                    battles = 12,
                    wins = 8,
                    winRate = 66.7,
                ),
            )
        }

        val team = LegacyTeamsRepository(source) { it + 1_000 }.loadTeams().single()

        assertEquals(listOf("陆逊", "周瑜", "吕蒙"), team.heroes.map { it.name })
        assertEquals(listOf(1_101L, 1_102L, 1_103L), team.heroes.map { it.iconId })
        assertEquals(
            listOf(
                listOf("深谋远虑", "不攻", "十面埋伏"),
                listOf("神兵天降", "反计之策", "大赏三军"),
                listOf("白衣渡江", "道行险阻"),
            ),
            team.heroes.map { it.skillNames },
        )
        assertEquals(listOf("深谋远虑", "神兵天降", "反计之策"), team.skillNames)
        assertEquals("攻方", team.sideLabel)
    }
}
