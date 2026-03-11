package com.squarenova.emaanwallpapers.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "emaan_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        val IS_LOGGED_IN        = booleanPreferencesKey("is_logged_in")
        val PHONE_NUMBER        = stringPreferencesKey("phone_number")
        val IS_PROFILE_COMPLETED = booleanPreferencesKey("is_profile_completed")
        val IS_SUBSCRIBED       = booleanPreferencesKey("is_subscribed") // ✅ NEW
    }

    // ── Flows ────────────────────────────────
    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { it[IS_LOGGED_IN] ?: false }

    val phoneNumber: Flow<String?> = context.dataStore.data
        .map { it[PHONE_NUMBER] }

    val isProfileCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[IS_PROFILE_COMPLETED] ?: false }

    val isSubscribed: Flow<Boolean> = context.dataStore.data
        .map { it[IS_SUBSCRIBED] ?: false }  // ✅ NEW

    // ── Actions ──────────────────────────────
    suspend fun saveLogin(phone: String) {
        context.dataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = true
            prefs[PHONE_NUMBER] = phone
        }
    }

    suspend fun setProfileCompleted() {
        context.dataStore.edit { it[IS_PROFILE_COMPLETED] = true }
    }

    // ✅ NEW — called after successful Razorpay payment
    suspend fun setSubscribed() {
        context.dataStore.edit { it[IS_SUBSCRIBED] = true }
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
    }
}