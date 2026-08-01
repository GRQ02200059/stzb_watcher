package com.local.stzb

import android.app.Application
import com.example.myapplication.HeroNameResolver
import com.example.myapplication.LocalBattleSimulator
import com.example.myapplication.LocalStzbCaptureWriter
import com.example.myapplication.LocalStzbRepository
import com.example.myapplication.SkillNameResolver
import com.local.stzb.data.battlefield.AndroidLegacyBattlefieldSource
import com.local.stzb.data.battlefield.LegacyBattlefieldRepository
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.data.battles.LegacyBattleRepository
import com.local.stzb.domain.battles.BattleRepository
import com.local.stzb.data.alliance.LegacyAllianceRepository
import com.local.stzb.domain.alliance.AllianceRepository
import com.local.stzb.data.intel.LegacyIntelRepository
import com.local.stzb.domain.intel.IntelRepository
import com.local.stzb.data.rankings.LegacyRankingRepository
import com.local.stzb.domain.rankings.RankingRepository
import com.local.stzb.data.teams.LegacyTeamsRepository
import com.local.stzb.domain.teams.TeamsRepository
import hev.sockstun.Preferences

class StzbApplication : Application() {
    val battlefieldRepository: BattlefieldRepository by lazy {
        LegacyBattlefieldRepository(AndroidLegacyBattlefieldSource(Preferences(this)))
    }
    val battleRepository: BattleRepository by lazy { LegacyBattleRepository() }
    val allianceRepository: AllianceRepository by lazy { LegacyAllianceRepository() }
    val intelRepository: IntelRepository by lazy { LegacyIntelRepository() }
    val rankingRepository: RankingRepository by lazy { LegacyRankingRepository() }
    val teamsRepository: TeamsRepository by lazy { LegacyTeamsRepository() }

    override fun onCreate() {
        super.onCreate()
        LocalStzbCaptureWriter.init(this)
        HeroNameResolver.init(this)
        SkillNameResolver.init(this)
        LocalStzbRepository.init(this)
        LocalBattleSimulator.init(this)
    }
}
