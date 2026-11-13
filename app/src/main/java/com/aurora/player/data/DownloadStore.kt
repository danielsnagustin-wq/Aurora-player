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
    val uriPart = record.uri ?: ""
    return listOf(
        record.videoId,
        record.title,
        record.artist,
        record.sizeBytes.toString(),
        record.downloadedAtMs.toString(),
        record.status.name,
        uriPart
    ).joinToString(sep.toString())
}

internal fun decodeDownloadRecord(line: String): DownloadRecord? {
    val sep = '\u001F'
    val parts = line.split(sep)
    if (parts.size != 7) return null
    val (videoId, title, artist, sizeStr, downloadedStr, statusStr, uriPart) = parts
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
            val updated = current.mapNotNull { line ->
                val rec = decodeDownloadRecord(line) ?: return@mapNotNull null
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
