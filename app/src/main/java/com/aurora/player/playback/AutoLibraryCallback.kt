package com.aurora.player.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.aurora.player.model.Song
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto (Fase 2 app), Serie B: lo que responde el servicio
 * cuando el coche pide el árbol.
 *
 * La biblioteca se lee en segundo plano (MediaStore + Room) y se
 * devuelve por futuro, nunca en el hilo principal: con miles de
 * canciones, leer en el hilo de la UI congelaría el coche. Si algo
 * falla, el coche recibe un error honesto, nunca una lista inventada.
 * Sin búsqueda en v1: el coche navega por carpetas.
 */
class AuroraLibraryCallback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val loadSongs: suspend () -> List<Song>,
    private val loadFavoriteIds: suspend () -> Set<Long>,
) : MediaLibraryService.Callback {

    private fun rootItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(AutoBrowse.ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Aurora Player")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.MediaLibrarySession.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        SettableFuture.create<LibraryResult<MediaItem>>()
            .apply { set(LibraryResult.ofItem(rootItem(), params)) }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.MediaLibrarySession.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) { childrenOf(parentId) }
                val from = (page * pageSize).coerceAtLeast(0)
                val to = minOf(from + pageSize, items.size)
                val pageItems = if (from >= items.size) emptyList() else items.subList(from, to)
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params))
            } catch (_: Exception) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            try {
                val item = withContext(Dispatchers.IO) { itemOf(mediaId) }
                if (item != null) future.set(LibraryResult.ofItem(item, null))
                else future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            } catch (_: Exception) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }
        return future
    }

    private suspend fun childrenOf(parentId: String): List<MediaItem> {
        val songs = loadSongs()
        return when (parentId) {
            AutoBrowse.ROOT_ID -> AutoBrowse.rootNodes().map { AutoBrowse.nodeMediaItem(it) }
            AutoBrowse.ALBUMS_ID -> AutoBrowse.albumNodes(songs).map { AutoBrowse.nodeMediaItem(it) }
            AutoBrowse.ARTISTS_ID -> AutoBrowse.artistNodes(songs).map { AutoBrowse.nodeMediaItem(it) }
            else -> AutoBrowse.songMediaItems(
                context,
                AutoBrowse.songsForNode(parentId, songs, loadFavoriteIds()),
            )
        }
    }

    private suspend fun itemOf(mediaId: String): MediaItem? {
        if (mediaId == AutoBrowse.ROOT_ID) return rootItem()
        val songs = loadSongs()
        AutoBrowse.rootNodes().firstOrNull { it.id == mediaId }
            ?.let { return AutoBrowse.nodeMediaItem(it) }
        AutoBrowse.albumNodes(songs).firstOrNull { it.id == mediaId }
            ?.let { return AutoBrowse.nodeMediaItem(it) }
        AutoBrowse.artistNodes(songs).firstOrNull { it.id == mediaId }
            ?.let { return AutoBrowse.nodeMediaItem(it) }
        val song = songs.firstOrNull { it.id.toString() == mediaId } ?: return null
        return AutoBrowse.songMediaItems(context, listOf(song)).first()
    }
}
