package com.squarenova.emaanwallpapers.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val PHONE_NUMBER = stringPreferencesKey("phone_number")
        val IS_PROFILE_COMPLETED = booleanPreferencesKey("is_profile_completed")
    }

    suspend fun saveLogin(phone: String) {
        context.dataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = true
            prefs[PHONE_NUMBER] = phone
        }
    }

    suspend fun setProfileCompleted() {
        context.dataStore.edit { prefs ->
            prefs[IS_PROFILE_COMPLETED] = true
        }
    }

    val isLoggedIn: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[IS_LOGGED_IN] ?: false
        }

    val isProfileCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[IS_PROFILE_COMPLETED] ?: false
        }

    val phoneNumber: Flow<String?> =
        context.dataStore.data.map { prefs ->
            prefs[PHONE_NUMBER]
        }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}