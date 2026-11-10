package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.introDataStore by preferencesDataStore(
    name = "aurora_intro",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Aurora Player — intro de inicio (RONDA 2, 2026-11-09).
 * La animación de inicio solo se muestra la PRIMERA vez que se abre
 * la app. Antes era `remember { true }` en cada onCreate: Android
 * mata el proceso en segundo plano tras un rato escuchando, y al
 * volver la intro se repetía como si fuera un arranque nuevo.
 * Persistirla aquí lo arregla sin tocar la animación.
 */
class IntroStore(context: Context) {
    private val dataStore = context.applicationContext.introDataStore

    companion object {
        private val SHOWN_KEY = booleanPreferencesKey("intro_shown")
        private val SETUP_KEY = booleanPreferencesKey("setup_done")
    }

    val shownFlow: Flow<Boolean> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[SHOWN_KEY] ?: false }

    suspend fun markShown() {
        dataStore.edit { prefs -> prefs[SHOWN_KEY] = true }
    }

    /**
     * Asistente de configuración (estilo Namida, 2026-11-09): la
     * primera vez la app abre el asistente en vez de la biblioteca.
     * Solo se marca hecho cuando el usuario pulsa "Empezar".
     */
    val setupDoneFlow: Flow<Boolean> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[SETUP_KEY] ?: false }

    suspend fun markSetupDone() {
        dataStore.edit { prefs -> prefs[SETUP_KEY] = true }
    }
}
