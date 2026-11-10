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
}

/** Lee una acción guardada como texto; un valor desconocido o vacío usa el defecto. */
fun parseGestureAction(value: String?, default: GestureAction): GestureAction =
    GestureAction.entries.find { it.name == value } ?: default
