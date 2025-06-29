package top.maary.oblivionis.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

class PreferenceRepository(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("userEmail")
        val PERMISSION_GRANTED = booleanPreferencesKey("PERMISSION_GRANTED")
        val RE_PERMISSION = booleanPreferencesKey("RE_PERMISSION_PROCESS")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("NOTIFICATION_ENABLED")
        val NOTIFICATION_INTERVAL = intPreferencesKey("NOTIFICATION_INTERVAL")
        val NOTIFICATION_INTERVAL_CAL_FIXED = booleanPreferencesKey("NOTIFICATION_INTERVAL_CAL_FIXED")
        val NOTIFICATION_INTERVAL_START = intPreferencesKey("NOTIFICATION_INTERVAL_START")
        val NOTIFICATION_TIME = stringPreferencesKey("NOTIFICATION_TIME")
        val V3_ALBUM_BACKFILL_NEEDED = booleanPreferencesKey("V3_ALBUM_BACKFILL_NEEDED")
    }

    val permissionGranted = context.dataStore.data.map { preferences ->
        preferences[PERMISSION_GRANTED] ?: false
    }

    suspend fun setPermissionGranted(status: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERMISSION_GRANTED] = status
        }
    }

    val isReWelcome = context.dataStore.data.map { preferences ->
        (preferences[RE_PERMISSION]?: false) and (preferences[PERMISSION_GRANTED] ?: false)
    }

    suspend fun setReWelcome(status: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RE_PERMISSION] = status
        }
    }

    val notificationEnabled = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_ENABLED] ?: false
    }

    suspend fun setNotificationEnabled(status: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_ENABLED] = status
        }
    }

    val notificationInterval = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_INTERVAL] ?: 30
    }

    suspend fun setNotificationInterval(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_INTERVAL] = interval
        }
    }

    val intervalStartFixed = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_INTERVAL_CAL_FIXED] ?: false
    }

    suspend fun setIntervalFixed(status: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_INTERVAL_CAL_FIXED] = status
        }
    }

    val intervalStart = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_INTERVAL_START] ?: 1
    }

    suspend fun setIntervalStart(start: Int) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_INTERVAL_START] = start
        }
    }

    val notificationTime = context.dataStore.data
        .map { preferences ->
            preferences[NOTIFICATION_TIME] ?: "21:00"
        }

    suspend fun setNotificationTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_TIME] = String.format("%02d:%02d", hour, minute)
        }
    }

    val v3AlbumBackfillNeeded = context.dataStore.data.map { preferences ->
        // 默认返回 true，表示需要执行回填
        preferences[V3_ALBUM_BACKFILL_NEEDED] ?: true
    }

    suspend fun setV3AlbumBackfillNeeded(needed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[V3_ALBUM_BACKFILL_NEEDED] = needed
        }
    }

}