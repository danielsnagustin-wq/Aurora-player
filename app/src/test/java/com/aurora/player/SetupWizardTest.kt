package com.aurora.player

import com.aurora.player.ui.setup.SETUP_STEP_COUNT
import com.aurora.player.ui.setup.SETUP_TABS
import com.aurora.player.ui.setup.clampSetupStep
import com.aurora.player.ui.setup.setupProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asistente de configuración (estilo Namida): navegación por pasos.
 * La lógica es pura (sin Android) para poder probarla en la JVM.
 */
class SetupWizardTest {

    @Test
    fun `seis pasos`() {
        assertEquals(6, SETUP_STEP_COUNT)
    }

    @Test
    fun `siete pestanas en el mismo orden que la biblioteca`() {
        assertEquals(
            listOf("Canciones", "Artistas", "Álbumes", "Favoritos", "Géneros", "Carpetas", "Online"),
            SETUP_TABS,
        )
    }

    @Test
    fun `el paso se queda dentro de 0 a 5`() {
        assertEquals(0, clampSetupStep(-1))
        assertEquals(0, clampSetupStep(0))
        assertEquals(3, clampSetupStep(3))
        assertEquals(5, clampSetupStep(5))
        assertEquals(5, clampSetupStep(99))
    }

    @Test
    fun `el progreso sube de un sexto en un sexto`() {
        assertEquals(1f / 6f, setupProgress(0), 0.001f)
        assertEquals(3f / 6f, setupProgress(2), 0.001f)
        assertEquals(1f, setupProgress(5), 0.001f)
        assertEquals(1f, setupProgress(99), 0.001f)
        assertTrue(setupProgress(0) > 0f)
    }
}
