package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.ui.design.AuroraSectionHeader

/**
 * Sección Avanzadas (2026-11-10, orden del usuario): el Diagnóstico
 * se quitó (no se entendía); queda solo Restablecer por defecto.
 */
@Composable
fun SettingsAdvancedSection(onResetDefaults: () -> Unit) {
    AuroraSectionHeader(
        title = "Avanzadas",
        description = "Restablecer devuelve los ajustes y la reproducción a sus valores de fábrica. Tu música, listas y favoritos no se tocan.",
    )
    TextButton(
        onClick = onResetDefaults,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text("Restablecer por defecto")
    }
}
