package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.TextSize
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Aurora Player — persistencia de ajustes con DataStore (Etapa 1).
 * ETAPA 2 (renovación): los colores se guardan AL TOCAR (sin botón
 * "Aplicar" desde la ronda solo). "Restablecer" escribe
 * ThemeSettings.DEFAULT. Un HEX guardado inválido se ignora al leer
 * (Theme.kt usa el valor por defecto) y se corrige al guardar.
 * ANTI-CIERRE: un archivo de ajustes corrupto (muerte del proceso
 * a mitad de escritura) antes tumbaba la app al abrir; ahora el
 * archivo corrupto se reemplaza por ajustes vacíos (= valores por
 * defecto) y cualquier otro fallo de lectura emite los valores por
 * defecto en vez de lanzar.
 */
private val Context.themeDataStore by preferencesDataStore(
    name = "aurora_theme",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val Context.playerDataStore by preferencesDataStore(
    name = "aurora_player",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class ThemeSettingsStore(private val context: Context) {

    private object Keys {
        val MODE = stringPreferencesKey("theme_mode")
        val AMOLED_SYSTEM_DARK = booleanPreferencesKey("amoled_when_system_dark")
        val ACCENT = stringPreferencesKey("accent_hex")
        val BACKGROUND = stringPreferencesKey("background_hex")
        val CARD = stringPreferencesKey("card_hex")
        val TEXT = stringPreferencesKey("text_hex")
        val TEXT2 = stringPreferencesKey("secondary_text_hex")
        val BUTTON = stringPreferencesKey("button_hex")
        val PLAYER_BAR = stringPreferencesKey("player_bar_hex")
        val VISUALIZER = stringPreferencesKey("visualizer_hex")
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val COVER_SHAPE = stringPreferencesKey("cover_shape")
        val DENSITY = stringPreferencesKey("list_density")
    }

    val settingsFlow: Flow<ThemeSettings> = context.themeDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        val d = ThemeSettings.DEFAULT
        d.copy(
            themeMode = ThemeMode.entries.find { it.name == prefs[Keys.MODE] } ?: d.themeMode,
            amoledWhenSystemDark = prefs[Keys.AMOLED_SYSTEM_DARK] ?: d.amoledWhenSystemDark,
            accentHex = ThemeSettings.normalizeHex(prefs[Keys.ACCENT] ?: d.accentHex) ?: d.accentHex,
            backgroundHex = ThemeSettings.normalizeHex(prefs[Keys.BACKGROUND] ?: d.backgroundHex) ?: d.backgroundHex,
            cardHex = ThemeSettings.normalizeHex(prefs[Keys.CARD] ?: d.cardHex) ?: d.cardHex,
            textHex = ThemeSettings.normalizeHex(prefs[Keys.TEXT] ?: d.textHex) ?: d.textHex,
            secondaryTextHex = ThemeSettings.normalizeHex(prefs[Keys.TEXT2] ?: d.secondaryTextHex) ?: d.secondaryTextHex,
            buttonHex = ThemeSettings.normalizeHex(prefs[Keys.BUTTON] ?: d.buttonHex) ?: d.buttonHex,
            playerBarHex = ThemeSettings.normalizeHex(prefs[Keys.PLAYER_BAR] ?: d.playerBarHex) ?: d.playerBarHex,
            visualizerHex = ThemeSettings.normalizeHex(prefs[Keys.VISUALIZER] ?: d.visualizerHex) ?: d.visualizerHex,
            useMaterialYou = prefs[Keys.MATERIAL_YOU] ?: d.useMaterialYou,
            textSize = TextSize.entries.find { it.name == prefs[Keys.TEXT_SIZE] } ?: d.textSize,
            coverShape = CoverShape.entries.find { it.name == prefs[Keys.COVER_SHAPE] } ?: d.coverShape,
            listDensity = ListDensity.entries.find { it.name == prefs[Keys.DENSITY] } ?: d.listDensity,
        )
    }

    /** Se llama al tocar un ajuste (guardado inmediato) o al restablecer. */
    suspend fun save(settings: ThemeSettings) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.MODE] = settings.themeMode.name
            prefs[Keys.AMOLED_SYSTEM_DARK] = settings.amoledWhenSystemDark
            prefs[Keys.ACCENT] = settings.accentHex
            prefs[Keys.BACKGROUND] = settings.backgroundHex
            prefs[Keys.CARD] = settings.cardHex
            prefs[Keys.TEXT] = settings.textHex
            prefs[Keys.TEXT2] = settings.secondaryTextHex
            prefs[Keys.BUTTON] = settings.buttonHex
            prefs[Keys.PLAYER_BAR] = settings.playerBarHex
            prefs[Keys.VISUALIZER] = settings.visualizerHex
            prefs[Keys.MATERIAL_YOU] = settings.useMaterialYou
            prefs[Keys.TEXT_SIZE] = settings.textSize.name
            prefs[Keys.COVER_SHAPE] = settings.coverShape.name
            prefs[Keys.DENSITY] = settings.listDensity.name
        }
    }

    suspend fun resetToDefault() = save(ThemeSettings.DEFAULT)
}

class PlayerSettingsStore(private val context: Context) {

    private object Keys {
        val CROSSFADE = intPreferencesKey("crossfade_seconds")
        val SPEED = stringPreferencesKey("playback_speed")
        val PITCH = stringPreferencesKey("playback_pitch")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val REPLAY_GAIN_MODE = stringPreferencesKey("replay_gain_mode")
        val REPLAY_GAIN_PREAMP = stringPreferencesKey("replay_gain_preamp_db")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause_on_disconnect")
        val MIN_DURATION = longPreferencesKey("min_duration_ms")
        val RESUME = booleanPreferencesKey("resume_where_left")
        val LOCAL_RADIO = booleanPreferencesKey("local_radio")
        val ONLINE_LYRICS = booleanPreferencesKey("online_lyrics")
        val SWIPE_TRACK = booleanPreferencesKey("swipe_to_change_track")
        val DOUBLE_TAP_FAV = booleanPreferencesKey("double_tap_to_favorite")
    }

    val settingsFlow: Flow<PlayerSettings> = context.playerDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        val d = PlayerSettings.DEFAULT
        d.copy(
            crossfadeSeconds = (prefs[Keys.CROSSFADE] ?: d.crossfadeSeconds).coerceIn(0, 12),
            playbackSpeed = (prefs[Keys.SPEED]?.toFloatOrNull() ?: d.playbackSpeed).coerceIn(0.5f, 2.0f),
            playbackPitch = (prefs[Keys.PITCH]?.toFloatOrNull() ?: d.playbackPitch).coerceIn(0.5f, 2.0f),
            skipSilence = prefs[Keys.SKIP_SILENCE] ?: d.skipSilence,
            replayGainMode = (prefs[Keys.REPLAY_GAIN_MODE] ?: d.replayGainMode)
                .takeIf { it in setOf("off", "track", "album", "smart") } ?: "off",
            replayGainPreAmpDb = (prefs[Keys.REPLAY_GAIN_PREAMP]?.toFloatOrNull() ?: d.replayGainPreAmpDb).coerceIn(-12f, 12f),
            autoPauseOnDisconnect = prefs[Keys.AUTO_PAUSE] ?: d.autoPauseOnDisconnect,
            minDurationMs = prefs[Keys.MIN_DURATION] ?: d.minDurationMs,
            resumeWhereLeft = prefs[Keys.RESUME] ?: d.resumeWhereLeft,
            localRadio = prefs[Keys.LOCAL_RADIO] ?: d.localRadio,
            onlineLyrics = prefs[Keys.ONLINE_LYRICS] ?: d.onlineLyrics,
            swipeToChangeTrack = prefs[Keys.SWIPE_TRACK] ?: d.swipeToChangeTrack,
            doubleTapToFavorite = prefs[Keys.DOUBLE_TAP_FAV] ?: d.doubleTapToFavorite,
        )
    }

    suspend fun save(settings: PlayerSettings) {
        context.playerDataStore.edit { prefs ->
            prefs[Keys.CROSSFADE] = settings.crossfadeSeconds
            prefs[Keys.SPEED] = settings.playbackSpeed.toString()
            prefs[Keys.PITCH] = settings.playbackPitch.toString()
            prefs[Keys.SKIP_SILENCE] = settings.skipSilence
            prefs[Keys.REPLAY_GAIN_MODE] = settings.replayGainMode
            prefs[Keys.REPLAY_GAIN_PREAMP] = settings.replayGainPreAmpDb.toString()
            prefs[Keys.AUTO_PAUSE] = settings.autoPauseOnDisconnect
            prefs[Keys.MIN_DURATION] = settings.minDurationMs
            prefs[Keys.RESUME] = settings.resumeWhereLeft
            prefs[Keys.LOCAL_RADIO] = settings.localRadio
            prefs[Keys.ONLINE_LYRICS] = settings.onlineLyrics
            prefs[Keys.SWIPE_TRACK] = settings.swipeToChangeTrack
            prefs[Keys.DOUBLE_TAP_FAV] = settings.doubleTapToFavorite
        }
    }

    /** "Restablecer por defecto" también restablece la reproducción. */
    suspend fun resetToDefault() = save(PlayerSettings.DEFAULT)
}
