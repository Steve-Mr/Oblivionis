package top.maary.oblivionis.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

class PreferenceRepository(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("userEmail")
        val PERMISSION_GRANTED = booleanPreferencesKey("PERMISSION_GRANTED")
        val RE_PERMISSION = booleanPreferencesKey("RE_PERMISSION_PROCESS")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("NOTIFICATION_ENABLED")
        val NOTIFICATION_INTERVAL = intPreferencesKey("NOTIFICATION_INTERVAL")
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


}