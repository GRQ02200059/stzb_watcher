package com.local.stzb

import android.app.Application
import com.example.myapplication.BuildConfig
import com.example.myapplication.HeroNameResolver
import com.example.myapplication.LocalBattleSimulator
import com.example.myapplication.LocalStzbCaptureWriter
import com.example.myapplication.LocalStzbRepository
import com.example.myapplication.SkillNameResolver
import com.local.stzb.auth.AndroidAuthSessionStore
import com.local.stzb.auth.AuthAccessGuard
import com.local.stzb.auth.AuthRepository
import com.local.stzb.auth.AuthSessionStore
import com.local.stzb.auth.AuthStartupCoordinator
import com.local.stzb.auth.AuthTransport
import com.local.stzb.auth.AuthViewModel
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
import com.local.stzb.data.capture.AndroidCaptureConsoleController
import com.local.stzb.feature.capture.CaptureConsoleController
import okhttp3.HttpUrl.Companion.toHttpUrl

class StzbApplication : Application() {
    val authAccessGuard by lazy { AuthAccessGuard() }
    val authSessionStore: AuthSessionStore by lazy { AndroidAuthSessionStore(this) }
    val authTransport: AuthTransport by lazy {
        AuthRepository(AUTH_BASE_URL.toHttpUrl())
    }
    val battlefieldRepository: BattlefieldRepository by lazy {
        LegacyBattlefieldRepository(AndroidLegacyBattlefieldSource(Preferences(this)))
    }
    val battleRepository: BattleRepository by lazy { LegacyBattleRepository() }
    val allianceRepository: AllianceRepository by lazy { LegacyAllianceRepository() }
    val intelRepository: IntelRepository by lazy { LegacyIntelRepository() }
    val rankingRepository: RankingRepository by lazy { LegacyRankingRepository() }
    val teamsRepository: TeamsRepository by lazy { LegacyTeamsRepository() }
    val captureConsoleController: CaptureConsoleController by lazy { AndroidCaptureConsoleController(this) }

    fun createAuthViewModel(): AuthViewModel {
        val coordinator = AuthStartupCoordinator(
            transport = authTransport,
            sessionStore = authSessionStore,
            accessGuard = authAccessGuard,
            clientVersion = BuildConfig.VERSION_NAME,
        )
        return AuthViewModel(
            startupCoordinator = coordinator,
            transport = authTransport,
            sessionStore = authSessionStore,
            accessGuard = authAccessGuard,
            clientVersion = BuildConfig.VERSION_NAME,
        )
    }

    override fun onCreate() {
        super.onCreate()
        LocalStzbCaptureWriter.init(this)
        HeroNameResolver.init(this)
        SkillNameResolver.init(this)
        LocalStzbRepository.init(this)
        LocalBattleSimulator.init(this)
    }

    companion object {
        private const val AUTH_BASE_URL = "http://152.136.236.184:9080"
    }
}
