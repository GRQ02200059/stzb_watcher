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
import com.local.stzb.profile.AndroidProfileStorage
import com.local.stzb.profile.ProfileManager
import com.local.stzb.profile.ProfileSnapshot
import com.example.myapplication.LocalSocksCaptureServer
import okhttp3.HttpUrl.Companion.toHttpUrl

class StzbApplication : Application() {
    val profileManager by lazy {
        ProfileManager(AndroidProfileStorage(this)) {
            Preferences(this).enable || LocalSocksCaptureServer.isRunning()
        }
    }
    val authAccessGuard by lazy { AuthAccessGuard() }
    val authSessionStore: AuthSessionStore by lazy { AndroidAuthSessionStore(this) }
    val authTransport: AuthTransport by lazy {
        AuthRepository(AUTH_BASE_URL.toHttpUrl())
    }
    val battlefieldRepository: BattlefieldRepository get() =
        LegacyBattlefieldRepository(AndroidLegacyBattlefieldSource(Preferences(this)))
    val battleRepository: BattleRepository get() = LegacyBattleRepository()
    val allianceRepository: AllianceRepository get() = LegacyAllianceRepository()
    val intelRepository: IntelRepository get() = LegacyIntelRepository()
    val rankingRepository: RankingRepository get() = LegacyRankingRepository()
    val teamsRepository: TeamsRepository get() = LegacyTeamsRepository()
    val captureConsoleController: CaptureConsoleController get() = AndroidCaptureConsoleController(this)

    fun profileSnapshot(): ProfileSnapshot = profileManager.ensureDefault()

    fun registerLocalProfile(serverAddress: String, roleId: String, displayName: String): Result<ProfileSnapshot> = runCatching {
        profileManager.register(serverAddress, roleId, displayName)
        profileManager.snapshot()
    }

    fun switchLocalProfile(profileId: String): Result<Unit> = profileManager.switchTo(profileId).map { profile ->
        LocalStzbRepository.switchDatabase(this, profile.databaseName)
        LocalStzbCaptureWriter.selectProfile(profile.profileId)
    }

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
        val profile = profileManager.ensureDefault().current ?: error("默认档案创建失败")
        LocalStzbCaptureWriter.init(this)
        LocalStzbCaptureWriter.selectProfile(profile.profileId)
        HeroNameResolver.init(this)
        SkillNameResolver.init(this)
        LocalStzbRepository.init(this, profile.databaseName)
        LocalBattleSimulator.init(this)
    }

    companion object {
        private const val AUTH_BASE_URL = "http://152.136.236.184:9080"
    }
}
