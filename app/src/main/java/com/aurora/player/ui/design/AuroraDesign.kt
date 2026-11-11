package com.aurora.player.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aurora.player.ui.theme.LocalCardStyle
import com.aurora.player.ui.theme.LocalCornerRadiusDp
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.ui.theme.cardBackground
import com.aurora.player.ui.theme.cardBorder
import com.aurora.player.ui.theme.cornerRadiusShape

/**
 * Sistema de diseño Aurora (reconstrucción 2026-11-10, punto 2 del
 * brief del usuario).
 *
 * Un solo sitio con las piezas que se repiten en toda la app:
 * encabezados, filas con interruptor, barras, grupos de opciones,
 * botones, tarjetas y estados (carga/error/vacío). Todas leen el
 * tema elegido (MaterialTheme + LocalCardStyle + LocalCornerRadiusDp):
 * ningún color ni radio fijo ignora el tema.
 *
 * Antes cada sección copiaba el mismo Row+Switch y el mismo
 * FlowRow+OutlinedButton con sus propios paddings (8 dp aquí,
 * 16 dp allá): por eso la interfaz se veía dispersa y sin jerarquía.
 * Las secciones migran a estas piezas una por una, sin cambiar
 * ningún comportamiento: solo cómo se ve.
 */

// Encabezado de grupo: título + descripción opcional. Es el mismo
// aspecto que SettingsGroupTitle/Description; las secciones nuevas
// usan este y las viejas migran a él.
@Composable
fun AuroraSectionHeader(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = modifier.padding(top = 24.dp),
    )
    if (description != null) {
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
    }
}

// Fila con interruptor: el patrón que se repetía en Reproducción,
// Control, Interfaz, Sonido, Respaldo y Red. Título a la izquierda,
// interruptor a la derecha, descripción opcional debajo del título.
@Composable
fun AuroraSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// Fila con barra: etiqueta con su valor + Slider. El estado local
// (remember) y el guardado al soltar siguen en quien llama, como
// hasta ahora: esta pieza solo pinta igual en todos los sitios.
@Composable
fun AuroraSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Grupo de opciones en FlowRow: la opción elegida es un Button con
// el color Aurora y las demás OutlinedButton (el mismo patrón que
// usaba Sonido para el ReplayGain, ahora compartido).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuroraOptionChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((value, label) in options) {
            if (value == selected) {
                Button(
                    onClick = { onSelect(value) },
                    colors = auroraButtonColors(),
                ) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }) {
                    Text(label)
                }
            }
        }
    }
}

// Lo mismo, pero con varias opciones a la vez (pestañas visibles:
// cada pestaña se enciende o se apaga sola).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuroraMultiChips(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((value, label) in options) {
            if (value in selected) {
                Button(
                    onClick = { onToggle(value) },
                    colors = auroraButtonColors(),
                ) { Text(label) }
            } else {
                OutlinedButton(onClick = { onToggle(value) }) {
                    Text(label)
                }
            }
        }
    }
}

// Botón principal y secundario con la forma del tema (radio de
// esquinas elegido en Ajustes), para que todos los botones de la
// app tengan la misma forma.
@Composable
fun AuroraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = cornerRadiusShape(LocalCornerRadiusDp.current),
        colors = auroraButtonColors(),
        modifier = modifier,
    ) { Text(text) }
}

@Composable
fun AuroraSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shape = cornerRadiusShape(LocalCornerRadiusDp.current),
        modifier = modifier,
    ) { Text(text) }
}

// Tarjeta con el estilo y el radio del tema (Sólido/Cristal/Borde).
// Es donde vive el ajuste "Estilo de tarjetas": mini-barra,
// cabeceras y diálogos, no una tarjeta por fila de canción.
@Composable
fun AuroraCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = cardBackground(
                LocalCardStyle.current,
                MaterialTheme.colorScheme.surface,
            ),
        ),
        shape = cornerRadiusShape(LocalCornerRadiusDp.current),
        border = cardBorder(
            LocalCardStyle.current,
            MaterialTheme.colorScheme.outline,
        ),
    ) { content() }
}

// Estados compartidos: carga (rueda), vacío (icono + texto) y error
// (icono + texto + reintentar). Ninguna pantalla vuelve a inventar
// su propio "no hay nada" con un Text pelado.
@Composable
fun AuroraLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun AuroraEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ronda 4: el icono vive en un círculo con fondo de
        // tarjeta y tinte de acento, no pelado sobre negro.
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AuroraErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        AuroraSecondaryButton(
            text = "Reintentar",
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
