package com.invictus.xmd.ui.status

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.invictus.xmd.R
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.ui.theme.AppTheme
import com.invictus.xmd.ui.theme.XmdTheme
import com.invictus.xmd.ui.theme.rememberThemeTransitionState

/**
 * WhatsApp / WhatsApp Business Status Saver. Opened from the status icon next
 * to Search on the Downloads tab. Self-contained: nothing here touches the
 * download queue, so saved statuses never appear in the Downloads list.
 */
class StatusSaverActivity : ComponentActivity() {

    private val viewModel: StatusSaverViewModel by viewModels()

    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(), same as SettingsActivity -- see there.
        AppTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(Settings.isDarkMode())

        setContent {
            val themeTransitionState = rememberThemeTransitionState()
            XmdTheme(transitionState = themeTransitionState) {
                StatusSaverScreen(
                    viewModel = viewModel,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onGrantAccess = ::requestStorageAccess,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also covers returning from the all-files-access settings page.
        viewModel.refresh()
    }

    private fun applyEdgeToEdge(isDarkMode: Boolean) {
        val barStyle = SystemBarStyle.auto(
            lightScrim = Color(0xFFF4F6F9).toArgb(),
            darkScrim = Color(0xFF0E1521).toArgb(),
        ) { isDarkMode }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", packageName, null),
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
