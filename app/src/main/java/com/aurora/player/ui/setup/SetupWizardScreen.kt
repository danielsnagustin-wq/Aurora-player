package com.aurora.player.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.FolderStore
import com.aurora.player.data.LibraryPrefsStore
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.PerformanceProfile
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import com.aurora.player.model.treeDocumentIdToPath
import com.aurora.player.model.applyTo
import com.aurora.player.ui.library.AlbumArtCache
import com.aurora.player.util.DiskArtCache
import kotlinx.coroutines.launch

/**
 * Asistente de configuración del primer inicio (idea de Namida,
 * 2026-11-09, código propio). Seis pasos, cada uno escribe en el
 * ajuste real que gobierna — nada de adorno: tema, rendimiento,
 * pestañas, carpetas excluidas, caché de imágenes y permiso.
 * Sin idioma (la app es en español) ni carpetas de red (Subsonic /
 * Jellyfin / WebDAV / Samba: Aurora es un reproductor local + YouTube).
 * Sin "Incluir vídeos": la biblioteca es de audio (MediaStore).
 */
const val SETUP_STEP_COUNT = 6

/** Pestañas del inicio, en el mismo orden que LibraryScreen. */
val SETUP_TABS = listOf(
    "Canciones", "Artistas", "Álbumes", "Favoritos", "Géneros", "Carpetas", "Online",
)

fun clampSetupStep(step: Int): Int = step.coerceIn(0, SETUP_STEP_COUNT - 1)

fun setupProgress(step: Int): Float =
    (clampSetupStep(step) + 1).toFloat() / SETUP_STEP_COUNT

@Composable
fun SetupWizardScreen(
    onFinish: () -> Unit,
    onRequestPermission: () -> Unit,
    hasAudioPermission: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeStore = remember { ThemeSettingsStore(context) }
    val libraryPrefs = remember { LibraryPrefsStore(context) }
    val folderStore = remember { FolderStore(context) }
    val applied by themeStore.settingsFlow.collectAsState(initial = ThemeSettings.DEFAULT)
    val visibleTabs by libraryPrefs.visibleTabsFlow.collectAsState(
        initial = LibraryPrefsStore.ALL_TABS,
    )
    val excludedFolders by folderStore.excludedFlow.collectAsState(initial = emptySet())
    val imageCacheEnabled by libraryPrefs.imageCacheEnabledFlow.collectAsState(initial = true)
    var step by remember { mutableStateOf(0) }
    var cacheCleared by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    // Selector de carpetas (2026-11-10): el paso 4 solo dejaba
    // QUITAR carpetas; ahora también se puede AÑADIR una desde
    // aquí, sin pasar por la pestaña Carpetas.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            val path = treeDocumentIdToPath(treeId)
            if (path != null) {
                scope.launch { folderStore.exclude(path) }
            }
        }
    }

    fun save(next: ThemeSettings) {
        scope.launch { themeStore.save(next) }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp),
    ) {
        Text("Configurar", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Configuración del primer inicio",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { setupProgress(step) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Paso ${step + 1} de $SETUP_STEP_COUNT",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> {
                    Text("Tema", style = MaterialTheme.typography.titleLarge)
                    Text("Elige cómo se ve la app. Lo puedes cambiar después en Ajustes.")
                    Spacer(Modifier.height(12.dp))
                    // Vista previa del fondo de cada tema (2026-11-10):
                    // Oscuro y AMOLED se veían iguales; la muestra deja
                    // la diferencia a la vista al instante.
                    for (mode in ThemeMode.entries) {
                        val preview = when (mode) {
                            ThemeMode.LIGHT -> Color.White
                            ThemeMode.DARK -> Color(0xFF1E1E26)
                            ThemeMode.AMOLED -> Color.Black
                            ThemeMode.SYSTEM -> if (systemDark) Color(0xFF1E1E26) else Color.White
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = applied.themeMode == mode,
                                onClick = { save(applied.copy(themeMode = mode)) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label)
                                Text(
                                    mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                modifier = Modifier.size(width = 40.dp, height = 24.dp)
                                    .background(preview, RoundedCornerShape(4.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        RoundedCornerShape(4.dp),
                                    ),
                            )
                        }
                    }
                }
                1 -> {
                    Text("Rendimiento", style = MaterialTheme.typography.titleLarge)
                    Text("Gobierna color dinámico, fondo del reproductor, esquinas y animaciones. Si cambias algo a mano, el perfil pasa a Personalizado.")
                    Spacer(Modifier.height(12.dp))
                    for (profile in PerformanceProfile.entries.filter { it != PerformanceProfile.CUSTOM }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = applied.performanceProfile == profile,
                                onClick = {
                                    save(profile.applyTo(applied).copy(performanceProfile = profile))
                                },
                            )
                            Column {
                                Text(profile.label)
                                Text(
                                    profile.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Text("Pestañas de la biblioteca", style = MaterialTheme.typography.titleLarge)
                    Text("Elige qué pestañas se ven en el inicio. La última visible nunca se puede esconder.")
                    Spacer(Modifier.height(12.dp))
                    SETUP_TABS.forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label)
                            Switch(
                                checked = index in visibleTabs,
                                onCheckedChange = { checked ->
                                    scope.launch { libraryPrefs.setTabVisible(index, checked) }
                                },
                            )
                        }
                    }
                }
                3 -> {
                    Text("Carpetas excluidas", style = MaterialTheme.typography.titleLarge)
                    Text("Estas carpetas no aparecen en ninguna pestaña, ni en la búsqueda, ni en la cola. Se excluye una carpeta manteniéndola pulsada en la pestaña Carpetas, o añadiéndola aquí.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { folderPicker.launch(null) }) {
                        Text("Añadir carpeta a excluir")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (excludedFolders.isEmpty()) {
                        Text("Ninguna carpeta excluida.")
                    } else {
                        for (folder in excludedFolders.sorted()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(folder, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    scope.launch { folderStore.include(folder) }
                                }) { Text("Quitar") }
                            }
                        }
                        TextButton(
                            onClick = { scope.launch { folderStore.clearExcluded() } },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("Quitar todas") }
                    }
                }
                4 -> {
                    Text("Caché de imágenes", style = MaterialTheme.typography.titleLarge)
                    Text("Carga más rápida y mejor rendimiento, pero utiliza más almacenamiento. Apagada, cada fila vuelve a cargar su portada.")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Habilitar caché de imágenes")
                        Switch(
                            checked = imageCacheEnabled,
                            onCheckedChange = { checked ->
                                scope.launch { libraryPrefs.setImageCacheEnabled(checked) }
                            },
                        )
                    }
                    TextButton(
                        onClick = {
                            AlbumArtCache.clear()
                            // Ronda 5: "Borrar caché" vacía también el
                            // disco de carátulas, no solo la memoria.
                            scope.launch { DiskArtCache.clear(context) }
                            cacheCleared = true
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Borrar caché de imágenes") }
                    if (cacheCleared) {
                        Text("Caché vaciada.", modifier = Modifier.padding(top = 4.dp))
                    }
                }
                else -> {
                    Text("Permiso de música", style = MaterialTheme.typography.titleLarge)
                    Text("Aurora necesita acceso a tu música para llenar la biblioteca. Sin permiso, la biblioteca estará vacía; puedes concederlo después desde Ajustes del sistema.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRequestPermission) {
                        Text("Permitir acceso a la música")
                    }
                    if (hasAudioPermission) {
                        Text(
                            "Permiso concedido. Tu música ya está lista.",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { step = clampSetupStep(step - 1) },
                enabled = step > 0,
            ) { Text("Atrás") }
            if (step < SETUP_STEP_COUNT - 1) {
                // RONDA 171 (2026-11-11): "Saltar" salta SOLO este paso,
                // no todo el asistente. Antes llamaba a onFinish y marcaba
                // el setup como hecho: pulsar "Saltar" en "Tema" se saltaba
                // también el permiso de música y el resto de pasos, y el
                // asistente no volvía a aparecer (IntroStore dice que solo
                // "Empezar" marca el setup como hecho).
                TextButton(onClick = { step = clampSetupStep(step + 1) }) { Text("Saltar") }
                Button(onClick = { step = clampSetupStep(step + 1) }) {
                    Text("Siguiente")
                }
            } else {
                Button(onClick = onFinish) { Text("Empezar") }
            }
        }
    }
}
