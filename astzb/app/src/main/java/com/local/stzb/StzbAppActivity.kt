package com.local.stzb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.DashboardActivity
import com.example.myapplication.LocalTrialManager
import com.example.myapplication.MainActivity
import com.local.stzb.core.designsystem.AstzbTheme
import com.local.stzb.core.navigation.StzbApp

class StzbAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LocalTrialManager.ensureAccessOrRedirect(this)) return

        enableEdgeToEdge()
        val repository = (application as StzbApplication).battlefieldRepository
        setContent {
            AstzbTheme {
                StzbApp(
                    repository = repository,
                    teamsRepository = (application as StzbApplication).teamsRepository,
                    battleRepository = (application as StzbApplication).battleRepository,
                    allianceRepository = (application as StzbApplication).allianceRepository,
                    intelRepository = (application as StzbApplication).intelRepository,
                    rankingRepository = (application as StzbApplication).rankingRepository,
                    openLegacyDashboard = ::openLegacyDashboard,
                    openCaptureConsole = ::openCaptureConsole,
                )
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
}
