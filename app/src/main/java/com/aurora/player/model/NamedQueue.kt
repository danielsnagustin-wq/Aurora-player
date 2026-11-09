package com.aurora.player.model

/**
 * Aurora Player — colas guardadas con nombre (SPEC 005, FR-007).
 * Modelo Musicolet: hasta 20 colas con nombre; la cola activa sigue
 * en QueueStore (DataStore) y las guardadas son plantillas que se
 * cargan en ella. Guardar con un nombre que ya existe reemplaza
 * esa cola, no duplica.
 */
const val MAX_NAMED_QUEUES = 20

/** Nombre limpio para una cola guardada, o null si está vacío. */
fun normalizeQueueName(name: String): String? {
    val clean = name.trim().take(40)
    return clean.ifEmpty { null }
}
