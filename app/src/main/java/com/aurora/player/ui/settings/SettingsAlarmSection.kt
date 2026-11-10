package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.ui.theme.auroraButtonColors

/**
 * Sección Alarma, partida de SettingsScreen (2026-11-09, RONDA 7
 * P4: alarma musical). El estado showAlarm sigue viviendo en
 * SettingsScreen; aquí solo llega el callback para abrir la
 * pantalla de la alarma.
 */
@Composable
fun SettingsAlarmSection(onShowAlarm: () -> Unit) {
    SettingsGroupTitle("Alarma")
    SettingsGroupDescription(
        "Despiértate con tu música: elige una hora y qué suena. Suena aunque la app esté cerrada y sobrevive a un reinicio.",
        modifier = Modifier.padding(top = 4.dp),
    )
    Button(
        onClick = onShowAlarm,
        colors = auroraButtonColors(),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text("Programar alarma")
    }
}
