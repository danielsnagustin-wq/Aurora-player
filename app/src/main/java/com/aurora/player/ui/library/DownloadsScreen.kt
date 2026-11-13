package com.aurora.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.DownloadRecord
import com.aurora.player.data.DownloadStatus
import com.aurora.player.data.DownloadStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DownloadsScreen(
    onBack: () -> Unit = {},
    onOpen: (DownloadRecord) -> Unit = {},
    onDelete: (DownloadRecord) -> Unit = {},
    onShare: (DownloadRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<DownloadRecord>?>(null) }
    var toDelete by remember { mutableStateOf<DownloadRecord?>(null) }

    LaunchedEffect(Unit) {
        DownloadStore(context).recordsFlow.collect { records = it }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Atrás"
                )
            }
            Text(
                text = "Descargas",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }
        Divider()
        when (val list = records) {
            null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            else -> if (list.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No hay descargas todavía.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.videoId }) { record ->
                        DownloadRow(
                            record = record,
                            onOpen = onOpen,
                            onShare = onShare,
                            onDelete = { toDelete = record }
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(text = "Eliminar descarga") },
            text = { Text(text = "Se eliminará el archivo descargado.") },
            confirmButton = {
                TextButton(onClick = {
                    val r = record
                    toDelete = null
                    onDelete(r)
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onOpen: (DownloadRecord) -> Unit,
    onShare: (DownloadRecord) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(enabled = record.status == DownloadStatus.READY) { onOpen(record) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = record.title, maxLines = 1)
            Text(text = record.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            val statusText = when (record.status) {
                DownloadStatus.READY -> "Lista"
                DownloadStatus.DOWNLOADING -> "Descargando"
                DownloadStatus.FAILED -> "Fallida"
                DownloadStatus.PENDING_WIFI -> "Pendiente de Wi-Fi"
                else -> ""
            }
            val sizeText = if (record.sizeBytes > 0) {
                " · ${formatDownloadSize(record.sizeBytes)}"
            } else {
                ""
            }
            Text(
                text = "$statusText$sizeText",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (record.status == DownloadStatus.READY) {
            IconButton(onClick = { onOpen(record) }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir"
                )
            }
            IconButton(onClick = { onShare(record) }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Compartir"
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Eliminar"
            )
        }
    }
}

private fun formatDownloadSize(bytes: Long): String {
    return when {
        bytes <= 0L -> "0 B"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    }
}
