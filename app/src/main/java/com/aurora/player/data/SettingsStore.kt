package com.aurora.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import kotlinx.coroutines.flow.map

/**
 * Aurora Player — persistencia de ajustes con DataStore (Etapa 1).
 * Los colores se guardan SOLO al pulsar "Aplicar" (la pantalla trabaja
 * con un draft); "Descartar" no escribe nada. "Restablecer" escribe
 * ThemeSettings.DEFAULT. Un HEX guardado inválido se ignora al leer
 * (Theme.kt usa el valor por defecto) y se corrige al guardar.
 */
private val Context.themeDataStore by preferencesDataStore(name = "aurora_theme")
private val Context.playerDataStore by preferencesDataStore(name = "aurora_player")

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

    val settingsFlow: Flow<ThemeSettings> = context.themeDataStore.data.map { prefs ->
        val d = ThemeSettings.DEFAULT
        d.copy(
            themeMode = ThemeMode.valueOf(prefs[Keys.MODE] ?: d.themeMode.name),
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
            textSize = TextSize.valueOf(prefs[Keys.TEXT_SIZE] ?: d.textSize.name),
            coverShape = CoverShape.valueOf(prefs[Keys.COVER_SHAPE] ?: d.coverShape.name),
            listDensity = ListDensity.valueOf(prefs[Keys.DENSITY] ?: d.listDensity.name),
        )
    }

    /** Solo se llama desde "Aplicar" o "Restablecer". */
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
        val GAPLESS = booleanPreferencesKey("gapless")
        val CROSSFADE = intPreferencesKey("crossfade_seconds")
        val SPEED = stringPreferencesKey("playback_speed")
        val EQ = booleanPreferencesKey("equalizer_enabled")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause_on_disconnect")
        val MIN_DURATION = longPreferencesKey("min_duration_ms")
        val RESUME = booleanPreferencesKey("resume_where_left")
    }

    val settingsFlow: Flow<PlayerSettings> = context.playerDataStore.data.map { prefs ->
        val d = PlayerSettings.DEFAULT
        d.copy(
            gapless = prefs[Keys.GAPLESS] ?: d.gapless,
            crossfadeSeconds = (prefs[Keys.CROSSFADE] ?: d.crossfadeSeconds).coerceIn(0, 12),
            playbackSpeed = (prefs[Keys.SPEED]?.toFloatOrNull() ?: d.playbackSpeed).coerceIn(0.5f, 2.0f),
            equalizerEnabled = prefs[Keys.EQ] ?: d.equalizerEnabled,
            autoPauseOnDisconnect = prefs[Keys.AUTO_PAUSE] ?: d.autoPauseOnDisconnect,
            minDurationMs = prefs[Keys.MIN_DURATION] ?: d.minDurationMs,
            resumeWhereLeft = prefs[Keys.RESUME] ?: d.resumeWhereLeft,
        )
    }

    suspend fun save(settings: PlayerSettings) {
        context.playerDataStore.edit { prefs ->
            prefs[Keys.GAPLESS] = settings.gapless
            prefs[Keys.CROSSFADE] = settings.crossfadeSeconds
            prefs[Keys.SPEED] = settings.playbackSpeed.toString()
            prefs[Keys.EQ] = settings.equalizerEnabled
            prefs[Keys.AUTO_PAUSE] = settings.autoPauseOnDisconnect
            prefs[Keys.MIN_DURATION] = settings.minDurationMs
            prefs[Keys.RESUME] = settings.resumeWhereLeft
        }
    }
}
