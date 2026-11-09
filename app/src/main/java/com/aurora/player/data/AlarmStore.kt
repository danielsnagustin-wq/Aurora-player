package com.aurora.player.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.player.alarm.AlarmSettings
import com.aurora.player.alarm.decodeAlarmSource
import com.aurora.player.alarm.encodeAlarmSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.alarmDataStore by preferencesDataStore(
    name = "aurora_alarm",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** RONDA 7 P4: la alarma guardada, solo en el teléfono. */
class AlarmStore(context: Context) {
    private val dataStore = context.applicationContext.alarmDataStore

    companion object {
        private val ENABLED_KEY = booleanPreferencesKey("alarm_enabled")
        private val HOUR_KEY = intPreferencesKey("alarm_hour")
        private val MINUTE_KEY = intPreferencesKey("alarm_minute")
        private val DAILY_KEY = booleanPreferencesKey("alarm_daily")
        private val SOURCE_KEY = stringPreferencesKey("alarm_source")
    }

    val settingsFlow: Flow<AlarmSettings> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                AlarmSettings(
                    enabled = prefs[ENABLED_KEY] ?: false,
                    hour = prefs[HOUR_KEY] ?: 7,
                    minute = prefs[MINUTE_KEY] ?: 30,
                    daily = prefs[DAILY_KEY] ?: true,
                    source = decodeAlarmSource(prefs[SOURCE_KEY]),
                )
            }

    suspend fun save(settings: AlarmSettings) {
        dataStore.edit {
            it[ENABLED_KEY] = settings.enabled
            it[HOUR_KEY] = settings.hour.coerceIn(0, 23)
            it[MINUTE_KEY] = settings.minute.coerceIn(0, 59)
            it[DAILY_KEY] = settings.daily
            it[SOURCE_KEY] = encodeAlarmSource(settings.source)
        }
    }
}
