package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.downloadDataStore by preferencesDataStore(
    name = "aurora_downloads",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

enum class DownloadStatus {
    DOWNLOADING,
    READY,
    FAILED,
    PENDING_WIFI
}

data class DownloadRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val sizeBytes: Long,
    val downloadedAtMs: Long,
    val status: DownloadStatus,
    val uri: String? = null
)

internal fun encodeDownloadRecord(record: DownloadRecord): String {
    // Unit separator (U+001F) unlikely to appear in titles/artists
    val sep = '\u001F'
    // RONDA 2 (2026-11-13, P2): como en DiscoveryStore (n=872): los
    // registros se unen con "\n" y los campos con U+001F; un título
    // con salto de línea (o con el separador) partía el registro en
    // dos y se perdía la descarga al leerlo. Se limpian en la escritura.
    fun clean(text: String): String = text
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(sep, ' ')
    val uriPart = record.uri ?: ""
    return listOf(
        clean(record.videoId),
        clean(record.title),
        clean(record.artist),
        record.sizeBytes.toString(),
        record.downloadedAtMs.toString(),
        record.status.name,
        clean(uriPart)
    ).joinToString(sep.toString())
}

internal fun decodeDownloadRecord(line: String): DownloadRecord? {
    val sep = '\u001F'
    val parts = line.split(sep)
    if (parts.size != 7) return null
    val videoId = parts[0]
    val title = parts[1]
    val artist = parts[2]
    val sizeStr = parts[3]
    val downloadedStr = parts[4]
    val statusStr = parts[5]
    val uriPart = parts[6]
    val size = sizeStr.toLongOrNull() ?: return null
    val downloaded = downloadedStr.toLongOrNull() ?: return null
    val status = try {
        DownloadStatus.valueOf(statusStr)
    } catch (e: IllegalArgumentException) {
        return null
    }
    val uri = if (uriPart.isEmpty()) null else uriPart
    return DownloadRecord(
        videoId = videoId,
        title = title,
        artist = artist,
        sizeBytes = size,
        downloadedAtMs = downloaded,
        status = status,
        uri = uri
    )
}

class DownloadStore(private val context: Context) {

    private object Keys {
        val RECORDS = stringSetPreferencesKey("download_records")
    }

    val recordsFlow: Flow<List<DownloadRecord>> = context.downloadDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            prefs[Keys.RECORDS]
                ?.mapNotNull { decodeDownloadRecord(it) }
                ?.sortedByDescending { it.downloadedAtMs }
                ?: emptyList()
        }

    suspend fun upsert(record: DownloadRecord) {
        context.downloadDataStore.edit { prefs ->
            val current = prefs[Keys.RECORDS] ?: emptySet()
            val filtered = current.filterNot { line ->
                decodeDownloadRecord(line)?.videoId == record.videoId
            }.toMutableSet()
            filtered.add(encodeDownloadRecord(record))
            prefs[Keys.RECORDS] = filtered
        }
    }

    suspend fun remove(videoId: String) {
        context.downloadDataStore.edit { prefs ->
            val current = prefs[Keys.RECORDS] ?: emptySet()
            val filtered = current.filterNot { line ->
                decodeDownloadRecord(line)?.videoId == videoId
            }.toSet()
            prefs[Keys.RECORDS] = filtered
        }
    }

    suspend fun setStatus(videoId: String, status: DownloadStatus) {
        context.downloadDataStore.edit { prefs ->
            val current = prefs[Keys.RECORDS] ?: emptySet()
            // RONDA CAZA 2026-11-13 (P1): antes era mapNotNull y una
            // línea corrupta o de formato viejo (p. ej. título con
            // U+001F) desaparecía al cambiar el estado de CUALQUIER
            // otra descarga. Las líneas no decodificables se conservan
            // tal cual, como ya hace upsert.
            val updated = current.map { line ->
                val rec = decodeDownloadRecord(line) ?: return@map line
                if (rec.videoId == videoId) {
                    encodeDownloadRecord(rec.copy(status = status))
                } else {
                    line
                }
            }.toSet()
            prefs[Keys.RECORDS] = updated
        }
    }
}
