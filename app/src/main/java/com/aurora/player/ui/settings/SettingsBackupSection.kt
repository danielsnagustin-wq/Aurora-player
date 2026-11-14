package com.aurora.player.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.aurora.player.util.backupFolderDisplayName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.BackupData
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.design.AuroraSwitchRow
import com.aurora.player.data.BackupValidation
import com.aurora.player.data.validateBackup
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.util.BackupManager
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.RestoreReport
import kotlinx.coroutines.launch

/**
 * Sección Respaldo, partida de SettingsScreen (2026-11-09, paso
 * estructural 3d, SPEC 005 FR-004): crear/restaurar el respaldo
 * local, respaldo automático diario y el diálogo de confirmación.
 * No necesita parámetros: todo su estado vive dentro.
 */
@Composable
fun SettingsBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // SPEC 005 FR-004: respaldo local (listas, favoritos,
    // historial, ajustes, cola y presets de EQ) en un archivo
    // en Descargas. Restaurar reemplaza lo que hay ahora.
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    var restoreReport by remember { mutableStateOf<RestoreReport?>(null) }
    var autoBackup by remember { mutableStateOf(false) }
    var lastBackup by remember { mutableStateOf("") }
    // Fase 2 — respaldo a carpeta: la carpeta elegida (o null).
    var backupFolder by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        autoBackup = BackupManager.isAutoBackupEnabled(context)
        lastBackup = BackupManager.lastBackupName(context)
        backupFolder = BackupManager.getBackupFolderUri(context)?.toString()
    }
    // Elegir carpeta (SAF): se pide permiso persistente para poder
    // escribir en ella también en los respaldos automáticos.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
                backupMessage = "No se pudo guardar el permiso de esa carpeta."
                return@launch
            }
            BackupManager.setBackupFolderUri(context, uri)
            backupFolder = uri.toString()
            backupMessage = "Respaldo a carpeta activado."
        }
    }
    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = BackupManager.readBackupText(context, uri)
            if (text.isFailure) {
                backupMessage = "No se pudo leer ese archivo."
                return@launch
            }
            when (val v = validateBackup(text.getOrThrow())) {
                is BackupValidation.Valid -> pendingRestore = v.data
                is BackupValidation.Invalid -> backupMessage = v.reason
            }
        }
    }
    AuroraSectionHeader(
        title = "Respaldo",
        description = "Guarda tus listas, favoritos, historial, ajustes, cola y presets de EQ en un archivo (en Descargas o en la carpeta que elijas). Restaurar reemplaza lo que tienes ahora; lo que no se encuentre en tu biblioteca se lista, nunca se inventa.",
    )
    if (lastBackup.isNotEmpty()) {
        SettingsGroupDescription("Último respaldo: $lastBackup", modifier = Modifier.padding(top = 8.dp))
    }
    // Fase 2 — respaldo a carpeta: dónde se guardan los respaldos.
    val folderName = backupFolder?.let { backupFolderDisplayName(it) }.orEmpty()
    SettingsGroupDescription(
        if (backupFolder != null && folderName.isNotEmpty()) "Carpeta de respaldo: $folderName"
        else if (backupFolder != null) "Carpeta de respaldo: elegida"
        else "Carpeta de respaldo: Descargas/AuroraPlayer",
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { pickFolder.launch(null) }) {
            Text("Elegir carpeta…")
        }
        if (backupFolder != null) {
            OutlinedButton(onClick = {
                scope.launch {
                    BackupManager.setBackupFolderUri(context, null)
                    backupFolder = null
                    backupMessage = "Respaldo a carpeta desactivado: vuelve a Descargas."
                }
            }) {
                Text("Quitar carpeta")
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                scope.launch {
                    val data = BackupManager.collectBackup(context)
                    // Fase 2 — respaldo a carpeta: si hay carpeta
                    // elegida, el respaldo va allí; si no, a Descargas.
                    val result = BackupManager.exportBackupSmart(context, data)
                    if (result.isSuccess) {
                        lastBackup = result.getOrThrow().fileName
                        backupMessage = "Respaldo guardado en ${result.getOrThrow().location}."
                    } else {
                        backupMessage = "No se pudo crear el respaldo."
                    }
                }
            },
            colors = auroraButtonColors(),
        ) { Text("Crear respaldo") }
        OutlinedButton(onClick = { pickBackup.launch(arrayOf("application/json")) }) {
            Text("Restaurar…")
        }
    }
    AuroraSwitchRow(
        title = "Respaldo automático diario",
        checked = autoBackup,
        onCheckedChange = {
            scope.launch {
                BackupManager.setAutoBackupEnabled(context, it)
                autoBackup = it
            }
        },
    )
    backupMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
    restoreReport?.let { report ->
        Text(
            "Restaurado: ${report.playlistsRestored} listas, ${report.namedQueuesRestored} colas guardadas, ${report.favoritesRestored} favoritos, " +
                "${report.historyRestored} escuchas, ${report.queueSongs} canciones en cola.",
            modifier = Modifier.padding(top = 8.dp),
        )
        if (report.missing.isNotEmpty()) {
            SettingsGroupDescription(
                "No se encontraron ${report.missing.size} canciones del respaldo (ya no están en tu biblioteca).",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (pendingRestore != null) {
        val data = pendingRestore!!
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restaurar respaldo") },
            text = {
                Text(
                    "Se reemplazarán tus listas (${data.playlists.size}), favoritos (${data.favorites.size}) e historial " +
                        "por los del respaldo. Tus ajustes, cola y presets de EQ también se aplicarán. " +
                        "Lo que tienes ahora se pierde.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestore = null
                        scope.launch {
                            try {
                                restoreReport = BackupManager.restoreBackup(context, data)
                                backupMessage = null
                            } catch (e: Exception) {
                                // RONDA 78: si la restauración falla a
                                // mitad, BackupManager ya devolvió los
                                // ajustes a como estaban; aquí solo se
                                // avisa, nunca se queda en silencio.
                                ErrorLog.log(context, "Restaurar respaldo", e)
                                backupMessage = "No se pudo restaurar el respaldo. Inténtalo otra vez."
                            }
                        }
                    },
                    colors = auroraButtonColors(),
                ) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") }
            },
        )
    }
}
