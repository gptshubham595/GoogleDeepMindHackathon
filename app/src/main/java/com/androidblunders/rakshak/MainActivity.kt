package com.androidblunders.rakshak

import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.androidblunders.rakshak.call.CallStateMonitor
import com.androidblunders.rakshak.ui.screens.DashboardScreen
import com.androidblunders.rakshak.presentation.SecurityHistoryScreen
import com.androidblunders.rakshak.presentation.SettingsScreen
import com.androidblunders.rakshak.ui.theme.Typography
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var callStateMonitor: CallStateMonitor

    private val corePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[android.Manifest.permission.READ_PHONE_STATE] == true ||
            checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            callStateMonitor.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCorePermissions()

        enableEdgeToEdge()
        setContent {
            MaterialTheme(typography = Typography) {
                var screen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (screen) {
                        AppScreen.DASHBOARD -> DashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            onOpenHistory = { screen = AppScreen.HISTORY },
                            onOpenSettings = { screen = AppScreen.SETTINGS },
                        )
                        AppScreen.HISTORY -> SecurityHistoryScreen(
                            onNavigateBack = { screen = AppScreen.DASHBOARD },
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            onNavigateBack = { screen = AppScreen.DASHBOARD },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-arm once READ_PHONE_STATE is granted (start() is idempotent + re-checks perm).
        callStateMonitor.start()
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            corePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private enum class AppScreen { DASHBOARD, HISTORY, SETTINGS }
}
