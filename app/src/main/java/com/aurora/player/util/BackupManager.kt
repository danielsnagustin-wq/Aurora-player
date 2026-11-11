package com.aurora.player.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.BackupData
import com.aurora.player.data.BackupEqPreset
import com.aurora.player.data.BackupHistoryEntry
import com.aurora.player.data.BackupPlaylist
import com.aurora.player.data.BackupQueue
import com.aurora.player.data.BackupSongRef
import com.aurora.player.data.BackupValidation
import com.aurora.player.data.CustomPresetStore
import com.aurora.player.data.FavoriteEntity
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayHistoryEntity
import com.aurora.player.data.PlaylistEntity
import com.aurora.player.data.PlaylistSongEntity
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.QueueStore
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.data.matchSongs
import com.aurora.player.data.toJsonString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Aurora Player — respaldo local (SPEC 005, FR-004). Parte Android.
 *
 * La lógica pura (modelo, JSON, validación, emparejado) vive en
 * data/BackupData; aquí solo hay E/S: leer Room/DataStore, escribir
 * el archivo en Descargas, leer el archivo elegido por el usuario
 * (SAF) y restaurar. Sin internet, sin permisos nuevos:
 * - Exportar (API 29+): MediaStore Downloads, carpeta
 *   Descargas/AuroraPlayer. En API 26-28, carpeta Descargas de la
 *   app (sin permiso).
 * - Importar: el usuario elige el archivo con el selector del
 *   sistema (SAF); aquí solo se lee su Uri.
 * - Restaurar reemplaza listas, favoritos e historial (en una
 *   transacción Room) y aplica ajustes, cola y presets de EQ. Lo
 *   que no se encuentra en la biblioteca se lista como ausente.
 * - Los errores nunca llevan rutas ni títulos (regla de privacidad).
 */

private val Context.backupDataStore by preferencesDataStore(
    name = "aurora_backup",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class ExportResult(val uri: Uri, val fileName: String, val location: String)

data class RestoreReport(
    val playlistsRestored: Int,
    val favoritesRestored: Int,
    val historyRestored: Int,
    val queueSongs: Int,
    /** Referencias del respaldo que no están en la biblioteca. */
    val missing: List<BackupSongRef>,
)

object BackupManager {

    private object Keys {
        val AUTO = booleanPreferencesKey("auto_backup_enabled")
        val LAST_AUTO_MS = longPreferencesKey("last_auto_backup_ms")
        val LAST_NAME = stringPreferencesKey("last_backup_name")
        // Fase 2 — respaldo a carpeta: la carpeta elegida por el
        // usuario (Uri de árbol SAF, en texto). Vacía = Descargas.
        val FOLDER_URI = stringPreferencesKey("backup_folder_uri")
    }

    private const val AUTO_INTERVAL_MS = 24L * 60L * 60L * 1000L

    suspend fun isAutoBackupEnabled(context: Context): Boolean =
        context.backupDataStore.data.first()[Keys.AUTO] ?: false

    suspend fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        context.backupDataStore.edit { prefs -> prefs[Keys.AUTO] = enabled }
    }

    suspend fun lastBackupName(context: Context): String =
        context.backupDataStore.data.first()[Keys.LAST_NAME] ?: ""

    /** Carpeta elegida para los respaldos, o null (Descargas). */
    suspend fun getBackupFolderUri(context: Context): Uri? {
        val raw = context.backupDataStore.data.first()[Keys.FOLDER_URI] ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /** Guarda (o quita, con null) la carpeta elegida para respaldos. */
    suspend fun setBackupFolderUri(context: Context, uri: Uri?) {
        context.backupDataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.FOLDER_URI)
            else prefs[Keys.FOLDER_URI] = uri.toString()
        }
    }

    /** Recoge todo lo respaldable en un BackupData. */
    suspend fun collectBackup(context: Context): BackupData = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val db = AuroraDatabase.getInstance(appContext)
        val playerSettings = PlayerSettingsStore(appContext).settingsFlow.first()
        val themeSettings = ThemeSettingsStore(appContext).settingsFlow.first()
        val library = MusicRepository(appContext).loadSongs(playerSettings.minDurationMs)
        val pathById = library.associate { it.id to it.path }
        val ref = { id: Long -> BackupSongRef(id, pathById[id] ?: "") }

        val playlists = db.playlistDao().allPlaylists().map { pl ->
            val songs = db.playlistDao().songsOf(pl.id).map { ref(it.songId) }
            BackupPlaylist(pl.name, pl.createdAt, pl.updatedAt, songs)
        }
        val favorites = db.libraryDao().favoriteIds().map { ref(it) }
        val history = db.libraryDao().allHistory().map { h ->
            BackupHistoryEntry(ref(h.songId), h.playedAt, h.durationListenedMs)
        }
        val savedQueue = QueueStore(appContext).queueFlow.first()
        val queue = BackupQueue(
            songs = savedQueue.songIds.map { ref(it) },
            index = savedQueue.index,
            positionMs = savedQueue.positionMs,
        )
        val presets = CustomPresetStore(appContext).presetsFlow.first().map { p ->
            BackupEqPreset(p.name, p.levelsDb)
        }
        BackupData(
            createdAtMs = System.currentTimeMillis(),
            playlists = playlists,
            favorites = favorites,
            history = history,
            theme = themeSettings,
            player = playerSettings,
            queue = queue,
            eqPresets = presets,
        )
    }

    /** Escribe el respaldo en Descargas. Devuelve dónde quedó. */
    suspend fun exportBackup(context: Context, data: BackupData): Result<ExportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = backupFileName(data.createdAtMs)
                val text = data.toJsonString()
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/AuroraPlayer")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                    ) ?: throw IllegalStateException("No se pudo crear el archivo")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("No se pudo escribir el archivo")
                    ExportResult(uri, fileName, "Descargas/AuroraPlayer/$fileName")
                } else {
                    val dir = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "AuroraPlayer",
                    ).apply { mkdirs() }
                    val file = File(dir, fileName)
                    file.writeText(text, Charsets.UTF_8)
                    ExportResult(Uri.fromFile(file), fileName, "AuroraPlayer/$fileName")
                }
                context.backupDataStore.edit { prefs ->
                    prefs[Keys.LAST_NAME] = fileName
                }
                result
            }.onFailure { e ->
                ErrorLog.log(context, "Exportar respaldo", e)
            }
        }

    /**
     * Escribe el respaldo en la carpeta elegida por el usuario
     * (árbol SAF). Si el permiso persistente se perdió o la carpeta
     * ya no existe, devuelve el fallo (el llamador avisa y el
     * usuario puede elegir otra carpeta); nunca se escribe en otro
     * sitio sin avisar.
     */
    suspend fun exportBackupToFolder(
        context: Context,
        data: BackupData,
        folderUri: Uri,
    ): Result<ExportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = backupFileName(data.createdAtMs)
                val text = data.toJsonString()
                val docUri = android.provider.DocumentsContract.createDocument(
                    context.contentResolver, folderUri, "application/json", fileName,
                ) ?: throw IllegalStateException("No se pudo crear el archivo en la carpeta")
                context.contentResolver.openOutputStream(docUri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("No se pudo escribir el archivo")
                context.backupDataStore.edit { prefs ->
                    prefs[Keys.LAST_NAME] = fileName
                }
                val folderName = backupFolderDisplayName(folderUri.toString())
                val location = if (folderName.isEmpty()) fileName else "$folderName/$fileName"
                ExportResult(docUri, fileName, location)
            }.onFailure { e ->
                ErrorLog.log(context, "Exportar respaldo a carpeta", e)
            }
        }

    /**
     * Exporta donde toca: a la carpeta elegida si hay una, si no a
     * Descargas (como hasta ahora). Es lo que usan el botón y el
     * respaldo automático.
     */
    suspend fun exportBackupSmart(context: Context, data: BackupData): Result<ExportResult> {
        val folder = getBackupFolderUri(context)
        return if (folder != null) exportBackupToFolder(context, data, folder)
        else exportBackup(context, data)
    }

    /** Lee el texto de un respaldo elegido por el usuario (SAF). */
    suspend fun readBackupText(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("No se pudo leer el archivo")
            }.onFailure { e ->
                ErrorLog.log(context, "Leer respaldo", e)
            }
        }

    /**
     * Restaura un respaldo ya validado. Reemplaza listas, favoritos e
     * historial; aplica ajustes, cola y presets. Las canciones se
     * guardan con el id ACTUAL de la biblioteca (emparejadas por id o
     * por ruta); las ausentes van en el informe, nunca se inventan.
     */
    suspend fun restoreBackup(context: Context, data: BackupData): RestoreReport =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val db = AuroraDatabase.getInstance(appContext)
            val playerSettings = PlayerSettingsStore(appContext).settingsFlow.first()
            // RONDA 161: la biblioteca para emparejar se carga con el
            // filtro MÁS permisivo entre el actual y el del respaldo.
            // Los ajustes del respaldo se aplican DESPUÉS (abajo), y
            // con el filtro actual una canción que el respaldo sí
            // tenía (porque entonces el mínimo era menor) quedaba
            // fuera del emparejado y se descartaba aunque el archivo
            // sigue en el teléfono.
            val matchMinDurationMs = minOf(playerSettings.minDurationMs, data.player.minDurationMs)
            val library = MusicRepository(appContext).loadSongs(matchMinDurationMs)
            val libraryPairs = library.map { it.id to it.path }
            val missing = mutableListOf<BackupSongRef>()

            fun matchedIds(refs: List<BackupSongRef>): List<Long> {
                val (found, notFound) = matchSongs(refs, libraryPairs)
                missing += notFound
                return found.map { it.first }
            }

            // Emparejar antes de la transacción: la biblioteca no
            // cambia mientras se restaura.
            val playlistIds = data.playlists.map { matchedIds(it.songs) }
            val favIds = matchedIds(data.favorites)
            val historyMatched = data.history.mapNotNull { entry ->
                val (found, notFound) = matchSongs(listOf(entry.song), libraryPairs)
                missing += notFound
                found.firstOrNull()?.first?.let { it to entry }
            }
            val queueIds = matchedIds(data.queue.songs)

            // RONDA 63: primero lo que NO se puede deshacer dentro de
            // la transacción (ajustes, cola, presets) y la transacción
            // de la biblioteca AL FINAL. Antes era al revés: si una
            // escritura de ajustes fallaba, las listas/favoritos/
            // historial ya estaban borrados y la restauración quedaba
            // a medias sin vuelta atrás.
            // RONDA 78: si algo falla a mitad de la restauración
            // (una escritura de ajustes o la propia transacción), los
            // ajustes ya aplicados se DEVUELVEN a como estaban antes:
            // la restauración es todo o nada, nunca a medias. La
            // biblioteca ya la revierte sola withTransaction; aquí se
            // revierten los ajustes a mano, lo mejor que se pueda.
            val themeStore = ThemeSettingsStore(appContext)
            val playerStore = PlayerSettingsStore(appContext)
            val queueStore = QueueStore(appContext)
            val presetStore = CustomPresetStore(appContext)
            val prevTheme = themeStore.settingsFlow.first()
            val prevPlayer = playerStore.settingsFlow.first()
            val prevQueue = queueStore.queueFlow.first()
            val prevPresets = presetStore.presetsFlow.first()
            try {
                themeStore.save(data.theme)
                playerStore.save(data.player)
                queueStore.save(
                    queueIds,
                    data.queue.index.coerceIn(0, (queueIds.size - 1).coerceAtLeast(0)),
                    data.queue.positionMs,
                )
                val backupNames = data.eqPresets.map { it.name.trim().take(40) }.toSet()
                // Guardar los del respaldo ANTES de borrar los viejos: si
                // una escritura falla, los presets viejos siguen ahí.
                for (preset in data.eqPresets) presetStore.save(preset.name, preset.levelsDb)
                for (preset in presetStore.presetsFlow.first()) {
                    if (preset.name !in backupNames) presetStore.delete(preset.name)
                }

                db.withTransaction {
                    val playlistDao = db.playlistDao()
                    val libraryDao = db.libraryDao()
                    playlistDao.clearAll()
                    for ((index, pl) in data.playlists.withIndex()) {
                        val newId = playlistDao.insertPlaylist(
                            PlaylistEntity(name = pl.name, createdAt = pl.createdAt, updatedAt = pl.updatedAt),
                        )
                        playlistDao.setSongs(
                            playlistIds[index].mapIndexed { position, songId ->
                                PlaylistSongEntity(newId, songId, position)
                            },
                        )
                    }
                    libraryDao.clearFavorites()
                    for (songId in favIds) {
                        libraryDao.addFavorite(FavoriteEntity(songId))
                    }
                    libraryDao.clearHistory()
                    for ((songId, entry) in historyMatched) {
                        libraryDao.addHistory(
                            PlayHistoryEntity(songId, entry.playedAt, entry.durationListenedMs),
                        )
                    }
                }
            } catch (e: Exception) {
                // RONDA 101: si una vuelta atrás también falla, que
                // quede en el registro de errores: antes se tragaba en
                // silencio y los ajustes podían quedar a medias sin
                // rastro de por qué.
                runCatching { themeStore.save(prevTheme) }
                    .onFailure { ErrorLog.log(appContext, "Restaurar respaldo", it) }
                runCatching { playerStore.save(prevPlayer) }
                    .onFailure { ErrorLog.log(appContext, "Restaurar respaldo", it) }
                runCatching { queueStore.save(prevQueue.songIds, prevQueue.index, prevQueue.positionMs) }
                    .onFailure { ErrorLog.log(appContext, "Restaurar respaldo", it) }
                runCatching {
                    for (preset in presetStore.presetsFlow.first()) presetStore.delete(preset.name)
                    for (preset in prevPresets) presetStore.save(preset.name, preset.levelsDb)
                }.onFailure { ErrorLog.log(appContext, "Restaurar respaldo", it) }
                throw e
            }
            RestoreReport(
                playlistsRestored = data.playlists.size,
                favoritesRestored = favIds.size,
                historyRestored = historyMatched.size,
                queueSongs = queueIds.size,
                missing = missing.distinct(),
            )
        }

    /**
     * Respaldo automático: al abrir la app, si está activo y pasaron
     * más de 24 h desde el último, exporta en silencio. Nunca tumba
     * el arranque: cualquier fallo se registra y se ignora.
     */
    suspend fun maybeAutoBackup(context: Context): Boolean {
        return try {
            val prefs = context.backupDataStore.data.first()
            if (prefs[Keys.AUTO] != true) return false
            val last = prefs[Keys.LAST_AUTO_MS] ?: 0L
            if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) return false
            val data = collectBackup(context)
            val result = exportBackupSmart(context, data)
            if (result.isSuccess) {
                context.backupDataStore.edit { it[Keys.LAST_AUTO_MS] = System.currentTimeMillis() }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            ErrorLog.log(context, "Respaldo automático", e)
            false
        }
    }
}
