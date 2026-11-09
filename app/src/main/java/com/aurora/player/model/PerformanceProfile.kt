package com.aurora.player.model

/**
 * Aurora Player — perfiles de rendimiento (spec 004).
 *
 * Regla de oro: un perfil solo gobierna efectos que existen de verdad
 * (color dinámico, fondo del reproductor, radio de esquinas,
 * animaciones). No hay perfiles ni interruptores para efectos que aún
 * no existen (visualizador, partículas, portadas 3D...): esos aparecen
 * en Ajustes solo cuando su efecto exista.
 *
 * - BALANCED (predeterminado): el aspecto actual de la app.
 * - BATTERY: lo mínimo visual — fondo plano, sin color dinámico, sin
 *   animaciones. Es lo que la política automática aplica cuando el
 *   sistema entra en ahorro de energía o en temperatura severa.
 * - PERFORMANCE: mismo aspecto que Equilibrado. Su diferencia real es
 *   de política: NO se le aplica la bajada automática (ver
 *   PerformancePolicy). El usuario renuncia explícitamente a ella.
 * - CUSTOM: cualquier cambio manual a un ajuste visual pone el perfil
 *   aquí. Los valores manuales mandan sobre los presets.
 */
enum class PerformanceProfile(val label: String) {
    BALANCED("Equilibrado"),
    BATTERY("Ahorro de batería"),
    PERFORMANCE("Alto rendimiento"),
    CUSTOM("Personalizado"),
}

/**
 * Valores visuales del perfil, aplicados sobre los ajustes actuales.
 * Solo toca los campos visuales que el perfil gobierna; el resto
 * (tema, acento, textos...) se queda como el usuario lo tenga.
 */
fun PerformanceProfile.applyTo(current: ThemeSettings): ThemeSettings = when (this) {
    PerformanceProfile.BALANCED -> current.copy(
        dynamicColor = true,
        playerBackground = PlayerBackground.COVER,
        cornerRadiusDp = 16,
        animationsEnabled = true,
    )
    PerformanceProfile.BATTERY -> current.copy(
        dynamicColor = false,
        playerBackground = PlayerBackground.SOLID,
        cornerRadiusDp = 8,
        animationsEnabled = false,
    )
    PerformanceProfile.PERFORMANCE -> current.copy(
        dynamicColor = true,
        playerBackground = PlayerBackground.COVER,
        cornerRadiusDp = 16,
        animationsEnabled = true,
    )
    PerformanceProfile.CUSTOM -> current
}
