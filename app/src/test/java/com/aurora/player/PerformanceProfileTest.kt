package com.aurora.player

import com.aurora.player.model.PerformanceProfile
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.ThemeSettings
import com.aurora.player.util.resolveEffective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aurora Player — tests de rendimiento (spec 004, lógica pura). */
class PerformanceProfileTest {

    @Test
    fun `por defecto el perfil es Equilibrado y las animaciones estan activas`() {
        assertEquals(PerformanceProfile.BALANCED, ThemeSettings.DEFAULT.performanceProfile)
        assertTrue(ThemeSettings.DEFAULT.animationsEnabled)
    }

    @Test
    fun `perfil Equilibrado es el aspecto actual`() {
        val out = PerformanceProfile.BALANCED.applyTo(ThemeSettings.DEFAULT)
        assertTrue(out.dynamicColor)
        assertEquals(PlayerBackground.COVER, out.playerBackground)
        assertEquals(16, out.cornerRadiusDp)
        assertTrue(out.animationsEnabled)
    }

    @Test
    fun `perfil Ahorro apaga color dinamico, fondo plano y animaciones`() {
        val out = PerformanceProfile.BATTERY.applyTo(ThemeSettings.DEFAULT)
        assertFalse(out.dynamicColor)
        assertEquals(PlayerBackground.SOLID, out.playerBackground)
        assertEquals(8, out.cornerRadiusDp)
        assertFalse(out.animationsEnabled)
    }

    @Test
    fun `perfil Alto rendimiento tiene el mismo aspecto que Equilibrado`() {
        val balanced = PerformanceProfile.BALANCED.applyTo(ThemeSettings.DEFAULT)
        val performance = PerformanceProfile.PERFORMANCE.applyTo(ThemeSettings.DEFAULT)
        assertEquals(balanced.dynamicColor, performance.dynamicColor)
        assertEquals(balanced.playerBackground, performance.playerBackground)
        assertEquals(balanced.cornerRadiusDp, performance.cornerRadiusDp)
        assertEquals(balanced.animationsEnabled, performance.animationsEnabled)
    }

    @Test
    fun `perfil Personalizado no cambia nada`() {
        val custom = ThemeSettings.DEFAULT.copy(dynamicColor = false, cornerRadiusDp = 4)
        assertEquals(custom, PerformanceProfile.CUSTOM.applyTo(custom))
    }

    @Test
    fun `Equilibrado sin ahorro ni calor usa lo guardado`() {
        val out = resolveEffective(ThemeSettings.DEFAULT, batterySaver = false, thermalSevere = false, appVisible = true)
        assertTrue(out.dynamicColor)
        assertEquals(PlayerBackground.COVER, out.playerBackground)
        assertTrue(out.animationsEnabled)
    }

    @Test
    fun `Equilibrado con ahorro de bateria baja a valores de ahorro sin tocar lo guardado`() {
        val out = resolveEffective(ThemeSettings.DEFAULT, batterySaver = true, thermalSevere = false, appVisible = true)
        assertFalse(out.dynamicColor)
        assertEquals(PlayerBackground.SOLID, out.playerBackground)
        assertEquals(8, out.cornerRadiusDp)
        assertFalse(out.animationsEnabled)
    }

    @Test
    fun `Equilibrado con temperatura severa baja a valores de ahorro`() {
        val out = resolveEffective(ThemeSettings.DEFAULT, batterySaver = false, thermalSevere = true, appVisible = true)
        assertFalse(out.dynamicColor)
        assertEquals(PlayerBackground.SOLID, out.playerBackground)
        assertFalse(out.animationsEnabled)
    }

    @Test
    fun `Alto rendimiento no se baja aunque haya ahorro de bateria`() {
        val settings = ThemeSettings.DEFAULT.copy(performanceProfile = PerformanceProfile.PERFORMANCE)
        val out = resolveEffective(settings, batterySaver = true, thermalSevere = true, appVisible = true)
        assertTrue(out.dynamicColor)
        assertEquals(PlayerBackground.COVER, out.playerBackground)
        assertTrue(out.animationsEnabled)
    }

    @Test
    fun `perfil Ahorro siempre da valores de ahorro`() {
        val settings = ThemeSettings.DEFAULT.copy(performanceProfile = PerformanceProfile.BATTERY)
        val out = resolveEffective(settings, batterySaver = false, thermalSevere = false, appVisible = true)
        assertFalse(out.dynamicColor)
        assertEquals(PlayerBackground.SOLID, out.playerBackground)
        assertFalse(out.animationsEnabled)
    }

    @Test
    fun `Personalizado respeta lo manual aunque haya ahorro`() {
        val settings = ThemeSettings.DEFAULT.copy(
            performanceProfile = PerformanceProfile.CUSTOM,
            dynamicColor = true,
            playerBackground = PlayerBackground.GRADIENT,
        )
        val out = resolveEffective(settings, batterySaver = true, thermalSevere = false, appVisible = true)
        assertTrue(out.dynamicColor)
        assertEquals(PlayerBackground.GRADIENT, out.playerBackground)
    }

    @Test
    fun `app no visible suspende animaciones en todos los perfiles`() {
        for (profile in PerformanceProfile.entries) {
            val settings = ThemeSettings.DEFAULT.copy(performanceProfile = profile)
            val out = resolveEffective(settings, batterySaver = false, thermalSevere = false, appVisible = false)
            assertFalse("perfil $profile debe suspender animaciones", out.animationsEnabled)
        }
    }
}
