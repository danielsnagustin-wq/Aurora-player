package com.aurora.player.model

/** Modos de repetición reales del reproductor. */
enum class RepeatMode { OFF, ALL, ONE }

/** Estado de la cola, persistente en Fase 1b (Room). */
data class QueueState(
    val songIds: List<Long> = emptyList(),
    val currentIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val positionMs: Long = 0L,
)
