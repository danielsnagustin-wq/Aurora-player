package com.aurora.player.ui.eq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.BassPref
import com.aurora.player.audio.EnergyPref
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.audio.TreblePref
import com.aurora.player.audio.suggestCurve
import com.aurora.player.data.CustomPresetStore
import com.aurora.player.ui.theme.auroraButtonColors
import kotlinx.coroutines.launch

/**
 * RONDA 7 P1: asistente de EQ. El usuario dice qué escucha y cómo le
 * gusta que suene; la gráfica muestra la curva que va a sonar y
 * "Probar" la aplica de verdad al Equalizer real (enciende el EQ si
 * estaba apagado). "Guardar como preset" la deja guardada como preset
 * propio. Cerrar sin guardar no deja nada a medias: la curva solo
 * queda si se probó o se guardó.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqWizardScreen(
    manager: EqualizerManager,
    onClose: () -> Unit,
    onApplyCurve: (List<Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetStore = remember { CustomPresetStore(context) }

    var genre by remember { mutableStateOf("Electrónica") }
    var bass by remember { mutableStateOf(BassPref.BALANCED) }
    var treble by remember { mutableStateOf(TreblePref.BALANCED) }
    var energy by remember { mutableStateOf(EnergyPref.NORMAL) }
    var name by remember { mutableStateOf("") }

    val curve = remember(genre, bass, treble, energy) {
        suggestCurve(genre, bass, treble, energy)
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Asistente de EQ")
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
            }
        }
        Text(
            "Responde y te propongo una curva. La gráfica es lo que va a sonar.",
            modifier = Modifier.padding(top = 8.dp),
        )

        Text("¿Qué escuchas más?", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (g in listOf("Electrónica", "Rock", "Pop", "Jazz", "Clásica", "Reggaeton", "Hip-Hop", "Dance", "Metal", "Vocal", "Plano")) {
                WizardChip(name = g, selected = genre == g, onClick = { genre = g })
            }
        }

        Text("Graves", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WizardChip(name = "Más graves", selected = bass == BassPref.MORE, onClick = { bass = BassPref.MORE })
            WizardChip(name = "Equilibrado", selected = bass == BassPref.BALANCED, onClick = { bass = BassPref.BALANCED })
            WizardChip(name = "Menos graves", selected = bass == BassPref.LESS, onClick = { bass = BassPref.LESS })
        }

        Text("Agudos", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WizardChip(name = "Más brillo", selected = treble == TreblePref.MORE, onClick = { treble = TreblePref.MORE })
            WizardChip(name = "Equilibrado", selected = treble == TreblePref.BALANCED, onClick = { treble = TreblePref.BALANCED })
            WizardChip(name = "Suave", selected = treble == TreblePref.LESS, onClick = { treble = TreblePref.LESS })
        }

        Text("Energía", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WizardChip(name = "Suave", selected = energy == EnergyPref.SOFT, onClick = { energy = EnergyPref.SOFT })
            WizardChip(name = "Normal", selected = energy == EnergyPref.NORMAL, onClick = { energy = EnergyPref.NORMAL })
            WizardChip(name = "Potente", selected = energy == EnergyPref.STRONG, onClick = { energy = EnergyPref.STRONG })
        }

        Text("Tu curva", modifier = Modifier.padding(top = 16.dp))
        EqResponseGraph(
            levelsDb = curve,
            frequenciesHz = manager.bandFrequenciesHz,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onApplyCurve(curve) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Probar")
            }
            Button(
                onClick = {
                    scope.launch {
                        presetStore.save(name, curve)
                        onApplyCurve(curve)
                        onClose()
                    }
                },
                enabled = name.isNotBlank(),
                colors = auroraButtonColors(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Guardar")
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre del preset (para guardar)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            "Probar aplica la curva al momento para que la oigas. Guardar la deja como preset propio.",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun WizardChip(name: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, colors = auroraButtonColors()) { Text(name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(name) }
    }
}
