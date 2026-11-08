package com.aurora.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.ThemeSettings
import com.aurora.player.ui.library.LibraryScreen
import com.aurora.player.ui.playlists.PlaylistsScreen
import com.aurora.player.ui.settings.SettingsScreen
import com.aurora.player.ui.theme.AuroraPlayerTheme

/**
 * Aurora Player — MainActivity (Etapa 1).
 * Navegación inferior: Biblioteca / Playlists / Ajustes.
 * El reproductor a pantalla completa se abre desde la mini-barra
 * (PlayerScreen) al tocar la canción que suena.
 * Permiso: solo audio (+ notificación en 13+). Sin internet, sin cuenta.
 */
class MainActivity : ComponentActivity() {

    private var hasAudioPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasAudioPermission = grants.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasAudioPermission = audioPermissionGranted()
        if (!hasAudioPermission) {
            val perms = mutableListOf<String>().apply {
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_AUDIO)
                else add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
        setContent {
            val themeStore = ThemeSettingsStore(applicationContext)
            val themeSettings by themeStore.settingsFlow.collectAsState(
                initial = ThemeSettings.DEFAULT,
            )
            AuroraPlayerTheme(settings = themeSettings) {
                AuroraApp(hasAudioPermission = hasAudioPermission)
            }
        }
    }

    private fun audioPermissionGranted(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }
}

private enum class Tab(val label: String) {
    LIBRARY("Biblioteca"),
    PLAYLISTS("Playlists"),
    SETTINGS("Ajustes"),
}

@Composable
private fun AuroraApp(hasAudioPermission: Boolean) {
    var tab by mutableStateOf(Tab.LIBRARY)
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.LIBRARY,
                    onClick = { tab = Tab.LIBRARY },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                    label = { Text("Biblioteca") },
                )
                NavigationBarItem(
                    selected = tab == Tab.PLAYLISTS,
                    onClick = { tab = Tab.PLAYLISTS },
                    icon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                    label = { Text("Playlists") },
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Ajustes") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            Tab.LIBRARY -> LibraryScreen(
                hasAudioPermission = hasAudioPermission,
                modifier = Modifier.padding(padding),
            )
            Tab.PLAYLISTS -> PlaylistsScreen(modifier = Modifier.padding(padding))
            Tab.SETTINGS -> SettingsScreen(modifier = Modifier.padding(padding))
        }
    }
}
