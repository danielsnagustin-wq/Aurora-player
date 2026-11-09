package com.aurora.player.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Aurora Player — persistencia local con Room (Etapa 1).
 * Playlists internas, favoritos, historial y contadores reales.
 * Nada de cifras inventadas: playCount solo sube cuando una pista
 * suena más de 30 s (regla de estadísticas, ver PLAN §8).
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "position"])
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

@Entity(tableName = "favorites", primaryKeys = ["songId"])
data class FavoriteEntity(val songId: Long, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "play_history", primaryKeys = ["songId", "playedAt"])
data class PlayHistoryEntity(
    val songId: Long,
    val playedAt: Long,
    val durationListenedMs: Long,
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(p: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position ASC")
    suspend fun songsOf(id: Long): List<PlaylistSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearSongs(id: Long)

    /**
     * AUDITORÍA (2026-11-09): cambiar las canciones de una playlist
     * era borrar + insertar en dos llamadas sueltas; si la app moría
     * entre medias, la playlist quedaba vacía. Ahora es una transacción.
     */
    @Transaction
    suspend fun replaceSongs(id: Long, songs: List<PlaylistSongEntity>) {
        clearSongs(id)
        setSongs(songs)
    }

    /** Borrar una playlist con sus canciones, también en transacción. */
    @Transaction
    suspend fun deletePlaylistWithSongs(id: Long) {
        clearSongs(id)
        deletePlaylist(id)
    }
}

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Query("SELECT songId FROM favorites")
    suspend fun favoriteIds(): List<Long>

    /**
     * AUDITORÍA (2026-11-09): la biblioteca cargaba los favoritos una
     * sola vez y el reproductor los cambiaba por otro lado: el corazón
     * quedaba desincronizado hasta reiniciar. Con este flujo, cualquier
     * cambio (biblioteca, reproductor, playlists) se ve en todos lados.
     */
    @Query("SELECT songId FROM favorites")
    fun favoriteIdsFlow(): Flow<List<Long>>

    @Insert
    suspend fun addHistory(h: PlayHistoryEntity)

    @Query("SELECT songId, COUNT(*) AS c FROM play_history WHERE durationListenedMs >= 30000 GROUP BY songId ORDER BY c DESC")
    suspend fun mostPlayed(): List<PlayCount>

    @Query("SELECT COUNT(*) FROM play_history WHERE durationListenedMs >= 30000")
    suspend fun totalPlays(): Int

    @Query("SELECT COALESCE(SUM(durationListenedMs), 0) FROM play_history")
    suspend fun totalListenedMs(): Long

    /** Reproducciones de una canción (ETAPA 5: pantalla Detalles). */
    @Query("SELECT COUNT(*) FROM play_history WHERE songId = :songId AND durationListenedMs >= 30000")
    suspend fun playCountFor(songId: Long): Int

    /** IDs con al menos una reproducción (playlists inteligentes). */
    @Query("SELECT DISTINCT songId FROM play_history WHERE durationListenedMs >= 30000")
    suspend fun playedSongIds(): List<Long>

    /** Marcas de tiempo de las reproducciones (gráfico semanal). */
    @Query("SELECT playedAt FROM play_history WHERE durationListenedMs >= 30000")
    suspend fun historyPlayedAt(): List<Long>

    /** Última reproducción de una canción (ETAPA 5: Detalles). */
    @Query("SELECT MAX(playedAt) FROM play_history WHERE songId = :songId")
    suspend fun lastPlayedAt(songId: Long): Long?

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}

data class PlayCount(val songId: Long, val c: Int)

@Database(
    entities = [PlaylistEntity::class, PlaylistSongEntity::class, FavoriteEntity::class, PlayHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        /**
         * AUDITORÍA (2026-11-09): había CUATRO instancias de Room sobre
         * el mismo archivo (MainActivity, biblioteca, playlists y
         * estadísticas). Sin invalidación entre instancias, lo que
         * escribía una no lo veía otra: favoritos y playlists quedaban
         * desactualizados hasta reiniciar. Ahora hay UNA sola instancia
         * compartida por toda la app.
         */
        @Volatile
        private var INSTANCE: AuroraDatabase? = null

        fun getInstance(context: Context): AuroraDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuroraDatabase::class.java,
                    "aurora-player.db",
                ).build().also { INSTANCE = it }
            }
    }
}
