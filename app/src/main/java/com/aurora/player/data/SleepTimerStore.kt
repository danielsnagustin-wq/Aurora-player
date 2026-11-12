package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Aurora Player — temporizador de apagado persistente (RONDA 143).
 * El temporizador vivía solo en `remember`: al girar el teléfono o
 * al morir el proceso se perdía en silencio y la música seguía
 * sonando. Se guarda aquí (DataStore, como la cola) al ponerlo y se
 * borra al dispararse o al apagarlo; al arrancar se restaura.
 * Estado transitorio, no un ajuste: no va a PlayerSettings ni al
 * respaldo (un plazo en un respaldo no tiene sentido).
 * ANTI-CIERRE: archivo corrupto -> sin temporizador, nunca tumba la app.
 * RONDA 164: el plazo es MONÓTONO (SystemClock.elapsedRealtime),
 * no reloj de pared: un cambio de hora o de zona horaria no lo
 * adelanta ni lo retrasa. Por eso vive en su propia clave
 * (sleep_until_elapsed_ms): un valor viejo de reloj de pared
 * leído como monótono parecería estar en el futuro siempre y el
 * temporizador no se dispararía nunca.
 */
private val Context.sleepTimerDataStore by preferencesDataStore(
    name = "aurora_sleep_timer",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class SavedSleepTimer(
    val optionName: String = "",
    val untilMs: Long? = null,
    val atSongEnd: Boolean = false,
)

class SleepTimerStore(private val context: Context) {

    private object Keys {
        val OPTION = stringPreferencesKey("sleep_option")
        // RONDA 164: plazo monótono (elapsedRealtime). La clave
        // vieja (sleep_until_ms, reloj de pared) ya no se lee ni se
        // escribe; si queda en el archivo, se ignora.
        val UNTIL_ELAPSED = longPreferencesKey("sleep_until_elapsed_ms")
        val AT_SONG_END = booleanPreferencesKey("sleep_at_song_end")
    }

    val timerFlow: Flow<SavedSleepTimer?> = context.sleepTimerDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val option = prefs[Keys.OPTION] ?: ""
            if (option.isEmpty()) null else SavedSleepTimer(
                optionName = option,
                untilMs = prefs[Keys.UNTIL_ELAPSED],
                atSongEnd = prefs[Keys.AT_SONG_END] ?: false,
            )
        }

    suspend fun save(optionName: String, untilMs: Long?, atSongEnd: Boolean) {
        context.sleepTimerDataStore.edit { prefs ->
            prefs[Keys.OPTION] = optionName
            if (untilMs != null) prefs[Keys.UNTIL_ELAPSED] = untilMs else prefs.remove(Keys.UNTIL_ELAPSED)
            prefs[Keys.AT_SONG_END] = atSongEnd
        }
    }

    suspend fun clear() {
        context.sleepTimerDataStore.edit { prefs ->
            prefs.remove(Keys.OPTION)
            prefs.remove(Keys.UNTIL_ELAPSED)
            prefs.remove(Keys.AT_SONG_END)
        }
    }
}
