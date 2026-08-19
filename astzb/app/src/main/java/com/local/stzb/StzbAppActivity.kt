package com.local.stzb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.DashboardActivity
import com.example.myapplication.MainActivity
import com.example.myapplication.CaptureVpnService
import com.local.stzb.auth.AuthGateState
import com.local.stzb.core.designsystem.AstzbTheme
import com.local.stzb.core.navigation.StzbApp
import com.local.stzb.feature.auth.AuthGateScreen
import com.local.stzb.feature.overlay.BattlefieldOverlayService
import hev.sockstun.TProxyService

class StzbAppActivity : ComponentActivity() {
    private val authViewModel by viewModels<com.local.stzb.auth.AuthViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                (application as StzbApplication).createAuthViewModel() as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                authViewModel.start()
            }
            AstzbTheme {
                if (uiState.state is AuthGateState.Ready) {
                    val app = application as StzbApplication
                    StzbApp(
                        repository = app.battlefieldRepository,
                        teamsRepository = app.teamsRepository,
                        battleRepository = app.battleRepository,
                        allianceRepository = app.allianceRepository,
                        intelRepository = app.intelRepository,
                        rankingRepository = app.rankingRepository,
                        captureController = app.captureConsoleController,
                        openLegacyDashboard = ::openLegacyDashboard,
                        openCaptureConsole = ::openCaptureConsole,
                        profileSnapshot = app.profileSnapshot(),
                        onRegisterProfile = app::registerLocalProfile,
                        onSwitchProfile = { profileId ->
                            app.switchLocalProfile(profileId).onSuccess { recreate() }
                        },
                        onLogout = ::logout,
                    )
                } else {
                    AuthGateScreen(
                        uiState = uiState,
                        onUsernameChange = authViewModel::updateUsername,
                        onPasswordChange = authViewModel::updatePassword,
                        onModeChange = authViewModel::setRegistrationMode,
                        onSubmit = authViewModel::submit,
                        onRetry = authViewModel::retry,
                    )
                }
            }
        }
    }

    private fun openLegacyDashboard(module: String) {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                putExtra(DashboardActivity.EXTRA_MODULE, module)
            },
        )
    }

    private fun openCaptureConsole() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun logout() {
        stopService(Intent(this, CaptureVpnService::class.java))
        BattlefieldOverlayService.stop(this)
        startService(
            Intent(this, TProxyService::class.java).apply {
                action = TProxyService.ACTION_DISCONNECT
            },
        )
        authViewModel.logout()
    }
}
