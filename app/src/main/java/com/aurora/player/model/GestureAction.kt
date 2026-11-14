package com.aurora.player.model

/**
 * Gestos configurables (Fase 2 app, GoneMAD): qué hace un gesto
 * cuando se dispara. Los gestos con dirección propia (deslizar
 * para cambiar de pista, deslizar vertical para el volumen) no
 * tienen acción configurable: su acción ES la dirección.
 */
enum class GestureAction(val label: String) {
    NEXT("Siguiente canción"),
    PREVIOUS("Canción anterior"),
    PLAY_PAUSE("Pausar / reanudar"),
    FAVORITE("Marcar favorito"),
    FAST_FORWARD("Avanzar 10 segundos"),
    REWIND("Retroceder 10 segundos"),
    TOGGLE_MUTE("Silenciar / activar sonido"),
}

/** Lee una acción guardada como texto; un valor desconocido o vacío usa el defecto. */
fun parseGestureAction(value: String?, default: GestureAction): GestureAction =
    // RONDA CAZA 2026-11-13 (P2): sin distinción de mayúsculas: los
    // valores por defecto viejos se guardaron en minúscula ("next",
    // "favorite") y con == estricto nunca casaban con el nombre del
    // enum; solo coincidía porque el fallback era la misma acción.
    GestureAction.entries.find { it.name.equals(value, ignoreCase = true) } ?: default
