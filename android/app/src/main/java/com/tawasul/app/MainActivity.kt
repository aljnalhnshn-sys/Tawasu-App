package com.tawasul.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tawasul.app.data.local.PreferencesManager
import com.tawasul.app.navigation.TawasulAppRoot
import com.tawasul.app.notifications.NotificationPermissionHelper
import com.tawasul.app.ui.theme.TawasulTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkPref by preferencesManager.isDarkModeFlow.collectAsStateWithLifecycle(initialValue = null)
            val isDark = darkPref ?: isSystemInDarkTheme()
            val scope = rememberCoroutineScope()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (NotificationPermissionHelper.needsNotificationPermission() &&
                    !NotificationPermissionHelper.hasNotificationPermission(this@MainActivity)
                ) {
                    permissionLauncher.launch(NotificationPermissionHelper.permissionName())
                }
            }

            TawasulTheme(darkTheme = isDark) {
                TawasulAppRoot(
                    isDarkTheme = isDark,
                    onThemeChanged = { nextDark ->
                        scope.launch { preferencesManager.setDarkMode(nextDark) }
                    }
                )
            }
        }
    }
}
