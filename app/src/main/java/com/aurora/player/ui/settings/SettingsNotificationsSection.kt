package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.ui.theme.auroraButtonColors

/**
 * Sección Notificaciones, partida de SettingsScreen (2026-11-09,
 * paso estructural: el archivo tenía 1545 líneas y cada edición
 * ahí se arriesgaba a romperlo). Solo necesita lo que SettingsScreen
 * ya recibía como parámetros: si el permiso está dado y el callback
 * para pedirlo.
 */
@Composable
fun SettingsNotificationsSection(
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
) {
    SettingsGroupTitle("Notificaciones")
    SettingsGroupDescription(
        if (notificationsGranted) {
            "El aviso de reproducción sale en la barra de estado y en la pantalla de bloqueo."
        } else {
            "Sin este permiso, en Android 13+ no sale el aviso de reproducción con los controles."
        },
        modifier = Modifier.padding(top = 4.dp),
    )
    Button(
        onClick = onRequestNotificationPermission,
        colors = auroraButtonColors(),
        modifier = Modifier.padding(top = 8.dp),
        enabled = !notificationsGranted,
    ) {
        Text(
            if (notificationsGranted) "Notificaciones activadas"
            else "Activar notificaciones"
        )
    }
}
