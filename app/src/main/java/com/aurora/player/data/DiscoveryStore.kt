package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.discoveryDataStore by preferencesDataStore(
    name = "aurora_discovery",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Un lanzamiento nuevo para el panel (Descubrimiento, 2026-11-13).
 * [source] es "YouTube", "Spotify" o "SoundCloud". [dateText] es la
 * fecha como la da la fuente ("2026-11-10" o "2026"). [browseId] es
 * el álbum de YouTube Music; [trackId] la pista de SoundCloud.
 */
data class NewReleaseItem(
    val source: String,
    val title: String,
    val artist: String,
    val dateText: String?,
    val kind: String?,
    val imageUrl: String?,
    val browseId: String?,
    val trackId: Long?,
)

internal fun encodeNewReleaseItem(item: NewReleaseItem): String {
    // Unit separator (U+001F) unlikely to appear in titles/artists
    val sep = '\u001F'
    return listOf(
        item.source,
        item.title,
        item.artist,
        item.dateText ?: "",
        item.kind ?: "",
        item.imageUrl ?: "",
        item.browseId ?: "",
        item.trackId?.toString() ?: "",
    ).joinToString(sep.toString())
}

internal fun decodeNewReleaseItem(line: String): NewReleaseItem? {
    val sep = '\u001F'
    val parts = line.split(sep)
    if (parts.size != 8) return null
    val source = parts[0]
    val title = parts[1]
    val artist = parts[2]
    if (source.isBlank() || title.isBlank()) return null
    return NewReleaseItem(
        source = source,
        title = title,
        artist = artist,
        dateText = parts[3].ifEmpty { null },
        kind = parts[4].ifEmpty { null },
        imageUrl = parts[5].ifEmpty { null },
        browseId = parts[6].ifEmpty { null },
        trackId = parts[7].ifEmpty { null }?.toLongOrNull(),
    )
}

/**
 * Últimos lanzamientos del panel + cuándo se actualizaron, solo en
 * el teléfono. Sin red, el panel enseña lo último guardado con su
 * "actualizado hace X" en vez de quedarse vacío.
 */
class DiscoveryStore(private val context: Context) {
    private val dataStore = context.applicationContext.discoveryDataStore

    companion object {
        private val RELEASES_KEY = stringPreferencesKey("new_releases")
        private val UPDATED_AT_KEY = longPreferencesKey("new_releases_updated_at")
    }

    val releasesFlow: Flow<List<NewReleaseItem>> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                prefs[RELEASES_KEY].orEmpty()
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .mapNotNull { decodeNewReleaseItem(it) }
            }

    val updatedAtFlow: Flow<Long?> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[UPDATED_AT_KEY] }

    suspend fun save(items: List<NewReleaseItem>) {
        dataStore.edit { prefs ->
            prefs[RELEASES_KEY] = items.joinToString("\n") { encodeNewReleaseItem(it) }
            prefs[UPDATED_AT_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(RELEASES_KEY)
            prefs.remove(UPDATED_AT_KEY)
        }
    }
}
