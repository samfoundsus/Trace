package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppSettings
import com.example.domain.model.ThemeMode
import com.example.domain.model.TimeLapseQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.traceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trace_settings"
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val themeString = prefs[KEY_THEME_MODE] ?: ThemeMode.DARK.name
            val themeMode = runCatching { ThemeMode.valueOf(themeString) }
                .getOrDefault(ThemeMode.DARK)

            val rawInterval = prefs[KEY_TIMELAPSE_INTERVAL] ?: 2
            val interval = if (rawInterval in AppSettings.SUPPORTED_INTERVALS_SECONDS) {
                rawInterval
            } else {
                2
            }

            val rawQuality = prefs[KEY_TIMELAPSE_QUALITY] ?: TimeLapseQuality.QUALITY_720P.name
            val quality = runCatching { TimeLapseQuality.valueOf(rawQuality) }
                .getOrDefault(TimeLapseQuality.QUALITY_720P)

            val rawCountdown = prefs[KEY_TIMELAPSE_COUNTDOWN] ?: 3
            val countdown = if (rawCountdown in AppSettings.SUPPORTED_COUNTDOWNS_SECONDS) {
                rawCountdown
            } else {
                3
            }

            AppSettings(
                themeMode = themeMode,
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
                keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: true,
                defaultOpacity = (prefs[KEY_DEFAULT_OPACITY] ?: 0.55f).coerceIn(0.1f, 1.0f),
                hideControlsOnStart = prefs[KEY_HIDE_CONTROLS_ON_START] ?: false,
                hasSeenTraceHint = prefs[KEY_HAS_SEEN_TRACE_HINT] ?: false,
                timeLapseIntervalSeconds = interval,
                timeLapseQuality = quality,
                timeLapseIncludeOverlay = prefs[KEY_TIMELAPSE_INCLUDE_OVERLAY] ?: false,
                timeLapseCountdownSeconds = countdown
            )
        }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = themeMode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setDefaultOpacity(opacity: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_OPACITY] = opacity.coerceIn(0.1f, 1.0f)
        }
    }

    suspend fun setHideControlsOnStart(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HIDE_CONTROLS_ON_START] = enabled
        }
    }

    suspend fun markTraceHintSeen() {
        dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_TRACE_HINT] = true
        }
    }

    suspend fun setTimeLapseIntervalSeconds(seconds: Int) {
        val valid = if (seconds in AppSettings.SUPPORTED_INTERVALS_SECONDS) seconds else 2
        dataStore.edit { prefs ->
            prefs[KEY_TIMELAPSE_INTERVAL] = valid
        }
    }

    suspend fun setTimeLapseQuality(quality: TimeLapseQuality) {
        dataStore.edit { prefs ->
            prefs[KEY_TIMELAPSE_QUALITY] = quality.name
        }
    }

    suspend fun setTimeLapseIncludeOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TIMELAPSE_INCLUDE_OVERLAY] = enabled
        }
    }

    suspend fun setTimeLapseCountdownSeconds(seconds: Int) {
        val valid = if (seconds in AppSettings.SUPPORTED_COUNTDOWNS_SECONDS) seconds else 3
        dataStore.edit { prefs ->
            prefs[KEY_TIMELAPSE_COUNTDOWN] = valid
        }
    }

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_DEFAULT_OPACITY = floatPreferencesKey("default_opacity")
        val KEY_HIDE_CONTROLS_ON_START = booleanPreferencesKey("hide_controls_on_start")
        val KEY_HAS_SEEN_TRACE_HINT = booleanPreferencesKey("has_seen_trace_hint")
        val KEY_TIMELAPSE_INTERVAL = intPreferencesKey("timelapse_interval_seconds")
        val KEY_TIMELAPSE_QUALITY = stringPreferencesKey("timelapse_quality")
        val KEY_TIMELAPSE_INCLUDE_OVERLAY = booleanPreferencesKey("timelapse_include_overlay")
        val KEY_TIMELAPSE_COUNTDOWN = intPreferencesKey("timelapse_countdown_seconds")

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext.traceDataStore).also {
                    instance = it
                }
            }
        }
    }
}
