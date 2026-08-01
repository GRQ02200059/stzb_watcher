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
import hev.sockstun.Preferences

class StzbApplication : Application() {
    val battlefieldRepository: BattlefieldRepository by lazy {
        LegacyBattlefieldRepository(AndroidLegacyBattlefieldSource(Preferences(this)))
    }
    val battleRepository: BattleRepository by lazy { LegacyBattleRepository() }
    val allianceRepository: AllianceRepository by lazy { LegacyAllianceRepository() }

    override fun onCreate() {
        super.onCreate()
        LocalStzbCaptureWriter.init(this)
        HeroNameResolver.init(this)
        SkillNameResolver.init(this)
        LocalStzbRepository.init(this)
        LocalBattleSimulator.init(this)
    }
}
