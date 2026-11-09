package com.aurora.player

import com.aurora.player.model.childFolders
import com.aurora.player.model.commonFolderPrefix
import com.aurora.player.model.isDirectlyInFolder
import com.aurora.player.model.isInExcludedFolder
import com.aurora.player.model.parentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aurora Player — tests de carpetas (spec 005, FR-006, lógica pura). */
class FoldersTest {

    private val paths = listOf(
        "/storage/emulated/0/Music/rock/a.mp3",
        "/storage/emulated/0/Music/rock/b.mp3",
        "/storage/emulated/0/Music/pop/c.mp3",
        "/storage/emulated/0/Download/d.mp3",
    )

    @Test
    fun `padre de una ruta`() {
        assertEquals("/a/b", parentPath("/a/b/c.mp3"))
        assertEquals("/", parentPath("/a.mp3"))
        assertEquals("/", parentPath("/"))
    }

    @Test
    fun `prefijo comun por segmentos no por texto`() {
        assertEquals("/storage/emulated/0", commonFolderPrefix(paths))
        // "/a/b" y "/a/bc" comparten "/a", no "/a/b".
        assertEquals(
            "/a",
            commonFolderPrefix(listOf("/a/b/x.mp3", "/a/bc/y.mp3")),
        )
        assertEquals("", commonFolderPrefix(emptyList()))
        assertEquals("/solo", commonFolderPrefix(listOf("/solo/x.mp3")))
    }

    @Test
    fun `subcarpetas directas con recuento`() {
        val root = childFolders(paths, "/storage/emulated/0")
        assertEquals(listOf("Download", "Music"), root.map { it.name })
        assertEquals(1, root.first { it.name == "Download" }.songCount)
        assertEquals(3, root.first { it.name == "Music" }.songCount)
        val music = childFolders(paths, "/storage/emulated/0/Music")
        assertEquals(listOf("pop", "rock"), music.map { it.name })
        assertEquals(2, music.first { it.name == "rock" }.songCount)
        assertTrue(childFolders(paths, "/storage/emulated/0/Music/rock").isEmpty())
    }

    @Test
    fun `cancion directa en su carpeta`() {
        assertTrue(isDirectlyInFolder("/a/b/c.mp3", "/a/b"))
        assertFalse(isDirectlyInFolder("/a/b/c/d.mp3", "/a/b"))
        assertFalse(isDirectlyInFolder("/a/c.mp3", "/a/b"))
    }

    @Test
    fun `excluir una carpeta quita sus subcarpetas pero no vecinas`() {
        val excluded = setOf("/storage/emulated/0/Music")
        assertTrue(isInExcludedFolder("/storage/emulated/0/Music/rock/a.mp3", excluded))
        assertTrue(isInExcludedFolder("/storage/emulated/0/Music/a.mp3", excluded))
        assertFalse(isInExcludedFolder("/storage/emulated/0/Download/d.mp3", excluded))
        // "/Music2" no es "/Music".
        assertFalse(isInExcludedFolder("/storage/emulated/0/Music2/a.mp3", excluded))
    }
}
