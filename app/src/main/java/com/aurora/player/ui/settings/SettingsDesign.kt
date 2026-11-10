package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Diseño de Ajustes (ronda de diseño 2026-11-09, por orden del
 * usuario: "acomodar la interfaz y los ajustes").
 *
 * Antes cada grupo era un Text plano con el mismo estilo que el
 * cuerpo: título y descripción se veían iguales, sin jerarquía,
 * y los controles quedaban pegados a la descripción. Ahora hay
 * dos piezas con un solo aspecto en toda la pantalla:
 *  - título de grupo: titleMedium en seminegrita, con aire arriba;
 *  - descripción: cuerpo en color secundario, con aire abajo.
 * Nada de esto cambia ningún comportamiento: solo cómo se lee.
 */
@Composable
fun SettingsGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = modifier.padding(top = 24.dp),
    )
}

@Composable
fun SettingsGroupDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp, bottom = 8.dp),
    )
}
