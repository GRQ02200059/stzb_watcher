package com.stzb.server.game.battle.skill

import com.stzb.server.game.SkillInventoryCatalog
import com.stzb.server.game.battle.BattleConfigRepository

data class SkillScope(
    val fiveStarInitialSkillIds: Set<Int>,
    val learnableSaSkillIds: Set<Int>,
) {
    val mainSkillIds: Set<Int> = fiveStarInitialSkillIds + learnableSaSkillIds
}

object SkillScopeCatalog {
    fun loadDefault(): SkillScope {
        val config = BattleConfigRepository.loadDefault()
        val fiveStarInitialSkillIds = config.allHeroes()
            .filter { it.qualityName == "五星" }
            .map { it.initialSkillId }
            .filter { it > 0 }
            .toSet()
        val learnableSaSkillIds = SkillInventoryCatalog.allSkillIds()
            .filter { config.skill(it)?.qualityLevel in setOf("S", "A") }
            .toSet()
        return SkillScope(fiveStarInitialSkillIds, learnableSaSkillIds)
    }
}
