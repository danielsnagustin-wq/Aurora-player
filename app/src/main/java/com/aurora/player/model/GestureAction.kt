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

/**
 * RONDA 2 (2026-11-13, P2): la acción de un gesto vive en UN solo
 * when exhaustivo como expresión: añadir un valor al enum sin rama
 * aquí NO compila, y cada llamador (doble toque en PlayerScreen,
 * agitar en MainActivity) tiene que pasar las 7 lambdas. Antes cada
 * llamador tenía su propio when como sentencia sin else: un valor
 * nuevo se ejecutaba en silencio en uno de los dos (o en los dos).
 */
fun performGestureAction(
    action: GestureAction,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFastForward: () -> Unit,
    onRewind: () -> Unit,
    onToggleMute: () -> Unit,
): Unit = when (action) {
    GestureAction.NEXT -> onNext()
    GestureAction.PREVIOUS -> onPrevious()
    GestureAction.PLAY_PAUSE -> onPlayPause()
    GestureAction.FAVORITE -> onToggleFavorite()
    GestureAction.FAST_FORWARD -> onFastForward()
    GestureAction.REWIND -> onRewind()
    GestureAction.TOGGLE_MUTE -> onToggleMute()
}
