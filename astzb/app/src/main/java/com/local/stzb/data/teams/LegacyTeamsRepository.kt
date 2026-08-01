package com.local.stzb.data.teams

import com.example.myapplication.HeroNameResolver
import com.example.myapplication.LocalPlayerBattleTeam
import com.example.myapplication.LocalStzbRepository
import com.local.stzb.domain.teams.PlayerTeam
import com.local.stzb.domain.teams.TeamHero
import com.local.stzb.domain.teams.TeamsRepository

interface LegacyTeamsSource {
    fun loadTeams(): List<LocalPlayerBattleTeam>
}

class AndroidLegacyTeamsSource : LegacyTeamsSource {
    override fun loadTeams(): List<LocalPlayerBattleTeam> = LocalStzbRepository.loadPlayerBattleTeams(0)
}

class LegacyTeamsRepository(
    private val source: LegacyTeamsSource = AndroidLegacyTeamsSource(),
    private val iconIdFor: (Long) -> Long = HeroNameResolver::iconIdOf,
) : TeamsRepository {
    override fun loadTeams(): List<PlayerTeam> = source.loadTeams().map { row ->
        val names = split(row.heroes)
        val ids = split(row.heroIds).mapNotNull(String::toLongOrNull)
        PlayerTeam(
            player = row.player,
            unionName = row.unionName,
            side = row.side,
            heroes = names.mapIndexed { index, name ->
                val id = ids.getOrElse(index) { 0L }
                TeamHero(id, id.takeIf { it > 0 }?.let(iconIdFor) ?: 0L, name)
            },
            skillNames = split(row.skills).distinct(),
            battles = row.battles,
            wins = row.wins,
            winRate = row.winRate,
        )
    }

    private fun split(value: String): List<String> = value
        .split('+', '/', ',', '，', '、')
        .map(String::trim)
        .filter(String::isNotBlank)
}
