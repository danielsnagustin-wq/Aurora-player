package com.aurora.player.widget

/**
 * Estilos del widget (Fase 2 app, Retro tiene 7): cómo se ve el
 * widget en la pantalla de inicio. Todos muestran la canción actual
 * y los botones anterior / play-pausa / siguiente; lo que cambia es
 * el tamaño y si lleva portada. El compacto esconde el artista y el
 * botón anterior (siguen existiendo en su layout, ocultos).
 */
enum class WidgetStyle(val label: String) {
    CLASSIC("Clásico"),
    COMPACT("Compacto"),
    LARGE("Grande"),
    COVER("Con portada"),
}

/** Lee un estilo guardado como texto; un valor desconocido o vacío usa el defecto. */
fun parseWidgetStyle(value: String?, default: WidgetStyle): WidgetStyle =
    WidgetStyle.entries.find { it.name == value } ?: default
