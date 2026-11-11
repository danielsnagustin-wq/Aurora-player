package com.aurora.player.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/** SPEC 005 FR-007: colas guardadas con nombre (hasta 20). */
@Entity(tableName = "named_queues")
data class NamedQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "named_queue_songs", primaryKeys = ["queueId", "position"])
data class NamedQueueSongEntity(
    val queueId: Long,
    val songId: Long,
    val position: Int,
)

/** Una cola guardada con su nº de canciones (una sola consulta). */
data class NamedQueueWithCount(
    @Embedded val queue: NamedQueueEntity,
    val songCount: Int,
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

    /** Playlist por nombre (Tu año: no duplicar "Tu año 2026"). */
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun playlistByName(name: String): PlaylistEntity?

    /** Borrar una playlist con sus canciones, también en transacción. */
    @Transaction
    suspend fun deletePlaylistWithSongs(id: Long) {
        clearSongs(id)
        deletePlaylist(id)
    }

    /** SPEC 005 FR-004: vaciar todas las listas (restaurar respaldo). */
    @Query("DELETE FROM playlist_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    @Transaction
    suspend fun clearAll() {
        clearAllSongs()
        clearAllPlaylists()
    }
}

/** SPEC 005 FR-007: colas guardadas con nombre. */
@Dao
interface NamedQueueDao {
    @Query(
        "SELECT q.*, (SELECT COUNT(*) FROM named_queue_songs s WHERE s.queueId = q.id) AS songCount " +
            "FROM named_queues q ORDER BY q.updatedAt DESC",
    )
    fun allQueuesWithCount(): Flow<List<NamedQueueWithCount>>

    @Query("SELECT * FROM named_queues ORDER BY updatedAt DESC")
    suspend fun allQueues(): List<NamedQueueEntity>

    @Query("SELECT COUNT(*) FROM named_queues")
    suspend fun count(): Int

    @Insert
    suspend fun insertQueue(queue: NamedQueueEntity): Long

    @Query("UPDATE named_queues SET updatedAt = :updatedAt WHERE id = :queueId")
    suspend fun touchQueue(queueId: Long, updatedAt: Long)

    @Query("SELECT songId FROM named_queue_songs WHERE queueId = :queueId ORDER BY position ASC")
    suspend fun songIdsOf(queueId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<NamedQueueSongEntity>)

    @Query("DELETE FROM named_queue_songs WHERE queueId = :queueId")
    suspend fun clearSongs(queueId: Long)

    /** Guardar (o reemplazar) las canciones de una cola, en transacción. */
    @Transaction
    suspend fun setSongs(queueId: Long, songIds: List<Long>) {
        clearSongs(queueId)
        insertSongs(
            songIds.mapIndexed { position, songId ->
                NamedQueueSongEntity(queueId, songId, position)
            },
        )
    }

    @Query("DELETE FROM named_queues WHERE id = :queueId")
    suspend fun deleteQueue(queueId: Long)

    @Transaction
    suspend fun deleteQueueWithSongs(queueId: Long) {
        clearSongs(queueId)
        deleteQueue(queueId)
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

    /** SPEC 005 FR-004: vaciar favoritos (restaurar respaldo). */
    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()

    /** SPEC 005 FR-004: todo el historial (respaldo). */
    @Query("SELECT * FROM play_history ORDER BY playedAt ASC")
    suspend fun allHistory(): List<PlayHistoryEntity>

    /**
     * AUDITORÍA (2026-11-09): la biblioteca cargaba los favoritos una
     * sola vez y el reproductor los cambiaba por otro lado: el corazón
     * quedaba desincronizado hasta reiniciar. Con este flujo, cualquier
     * cambio (biblioteca, reproductor, playlists) se ve en todos lados.
     */
    @Query("SELECT songId FROM favorites")
    fun favoriteIdsFlow(): Flow<List<Long>>

    // RONDA 3 (2026-11-09): sin onConflict, dos reproducciones de la
    // misma canción en el mismo milisegundo (misma PK) tumbaban el
    // insert con SQLiteConstraintException dentro de una corrutina sin
    // try/catch: cierre de la app. Imposible en la práctica (30 s
    // entre reproducciones), pero barato de blindar.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addHistory(h: PlayHistoryEntity)

    // RONDA 85: actualizar la duración de una escucha ya guardada
    // (la fila la identifica su PK: songId + playedAt). Devuelve las
    // filas tocadas (RONDA 98: 0 si la fila aún no existe).
    @Query("UPDATE play_history SET durationListenedMs = :ms WHERE songId = :songId AND playedAt = :playedAt")
    suspend fun updateHistoryDuration(songId: Long, playedAt: Long, ms: Long): Int

    @Query("SELECT songId, COUNT(*) AS c FROM play_history WHERE durationListenedMs >= 30000 GROUP BY songId ORDER BY c DESC")
    suspend fun mostPlayed(): List<PlayCount>

    /** Estadísticas por canción (Fase 1b: exportar CSV). */
    @Query("SELECT songId, COUNT(*) AS plays, COALESCE(SUM(durationListenedMs), 0) AS listenedMs, MAX(playedAt) AS lastPlayed FROM play_history WHERE durationListenedMs >= 30000 GROUP BY songId ORDER BY plays DESC")
    suspend fun statsPerSong(): List<SongStatsRow>

    /** Lo mismo, en vivo (ronda 17: las listas inteligentes se actualizan al escuchar sin reabrir la pestaña). */
    @Query("SELECT songId, COUNT(*) AS plays, COALESCE(SUM(durationListenedMs), 0) AS listenedMs, MAX(playedAt) AS lastPlayed FROM play_history WHERE durationListenedMs >= 30000 GROUP BY songId ORDER BY plays DESC")
    fun statsPerSongFlow(): Flow<List<SongStatsRow>>

    @Query("SELECT COUNT(*) FROM play_history WHERE durationListenedMs >= 30000")
    suspend fun totalPlays(): Int

    // RONDA 6: el tiempo escuchado solo cuenta escuchas de 30 s o
    // más, como las reproducciones: antes sumaba también los saltos
    // de 2 s y "Tiempo escuchado" mentía por arriba.
    @Query("SELECT COALESCE(SUM(durationListenedMs), 0) FROM play_history WHERE durationListenedMs >= 30000")
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

    /** Reproducciones de un año (Tu año): solo escuchas de 30 s o más. */
    @Query("SELECT * FROM play_history WHERE durationListenedMs >= 30000 AND playedAt >= :startMs AND playedAt < :endMs")
    suspend fun historyInYear(startMs: Long, endMs: Long): List<PlayHistoryEntity>

    /** Última reproducción de una canción (ETAPA 5: Detalles).
     * RONDA 6: solo escuchas de 30 s o más, como playCountFor: un
     * salto de 2 s no es una "última escucha". */
    @Query("SELECT MAX(playedAt) FROM play_history WHERE songId = :songId AND durationListenedMs >= 30000")
    suspend fun lastPlayedAt(songId: Long): Long?

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}

data class PlayCount(val songId: Long, val c: Int)

/** Fila de estadísticas por canción (Fase 1b: exportar CSV). */
data class SongStatsRow(val songId: Long, val plays: Int, val listenedMs: Long, val lastPlayed: Long)

/**
 * SPEC 005 FR-007: migración 1 -> 2, solo crea las tablas de colas
 * guardadas. Nada se borra: las listas, favoritos e historial
 * quedan intactos (sin fallback destructivo).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `named_queues` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `named_queue_songs` (" +
                "`queueId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`queueId`, `position`))",
        )
    }
}

@Database(
    entities = [
        PlaylistEntity::class, PlaylistSongEntity::class, FavoriteEntity::class,
        PlayHistoryEntity::class, NamedQueueEntity::class, NamedQueueSongEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun libraryDao(): LibraryDao
    abstract fun namedQueueDao(): NamedQueueDao

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
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
