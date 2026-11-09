package com.aurora.player.util

import com.aurora.player.model.PerformanceProfile
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.ThemeSettings

/**
 * Aurora Player — política de rendimiento (spec 004). Lógica pura,
 * sin Android: se puede probar en JVM sin emulador.
 *
 * Resuelve los valores visuales EFECTIVOS a partir de los guardados
 * y del estado del sistema. La bajada automática NUNCA escribe en
 * DataStore: solo cambia lo que se ve mientras dura la condición.
 * Al desaparecer la condición, los valores guardados vuelven solos.
 *
 * Reglas:
 * - Perfil AHORRO: siempre valores de ahorro, haya o no ahorro del
 *   sistema.
 * - Perfil EQUILIBRADO: si el sistema está en ahorro de energía o en
 *   temperatura severa/crítica, aplica valores de ahorro.
 * - Perfil ALTO RENDIMIENTO: nunca se baja automáticamente. El usuario
 *   lo eligió sabiendo que gasta más.
 * - Perfil PERSONALIZADO: mandan los valores manuales del usuario.
 * - App no visible: las animaciones se suspenden en todos los
 *   perfiles (no hay nada que animar fuera de pantalla).
 * - Esto solo toca lo visual. La reproducción y el audio no se tocan
 *   nunca: cambiar de perfil no corta la música.
 */
data class EffectiveVisuals(
    val dynamicColor: Boolean,
    val playerBackground: PlayerBackground,
    val cornerRadiusDp: Int,
    val animationsEnabled: Boolean,
)

/** Valores de ahorro: fondo plano, sin color dinámico, sin animación. */
private fun batteryVisuals(): EffectiveVisuals = EffectiveVisuals(
    dynamicColor = false,
    playerBackground = PlayerBackground.SOLID,
    cornerRadiusDp = 8,
    animationsEnabled = false,
)

private fun fromSettings(settings: ThemeSettings): EffectiveVisuals = EffectiveVisuals(
    dynamicColor = settings.dynamicColor,
    playerBackground = settings.playerBackground,
    cornerRadiusDp = settings.cornerRadiusDp.coerceIn(0, 28),
    animationsEnabled = settings.animationsEnabled,
)

fun resolveEffective(
    settings: ThemeSettings,
    batterySaver: Boolean,
    thermalSevere: Boolean,
    appVisible: Boolean,
): EffectiveVisuals {
    val base = when (settings.performanceProfile) {
        PerformanceProfile.BATTERY -> batteryVisuals()
        PerformanceProfile.BALANCED ->
            if (batterySaver || thermalSevere) batteryVisuals() else fromSettings(settings)
        PerformanceProfile.PERFORMANCE -> fromSettings(settings)
        PerformanceProfile.CUSTOM -> fromSettings(settings)
    }
    return if (appVisible) base else base.copy(animationsEnabled = false)
}
