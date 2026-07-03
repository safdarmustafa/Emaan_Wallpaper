package com.squarenova.emaanwallpapers.subscription

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squarenova.emaanwallpapers.util.SecureLog
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Durable persistence for the single [ConfirmationTicket].
 *
 * Kept in its OWN DataStore file (separate from user prefs) so it is never wiped by unrelated
 * clears and has a single, well-defined lifecycle. JSON-encoded via kotlinx.serialization.
 *
 * All operations are suspend and never throw — a corrupt/absent ticket simply reads as null.
 */
class ConfirmationTicketStore(context: Context) {

    private val appContext = context.applicationContext

    private companion object {
        const val TAG = "ConfirmationTicketStore"
        val KEY_TICKET = stringPreferencesKey("confirmation_ticket_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): ConfirmationTicket? {
        return try {
            val raw = appContext.confirmationDataStore.data.first()[KEY_TICKET] ?: return null
            json.decodeFromString(ConfirmationTicket.serializer(), raw)
        } catch (e: Exception) {
            SecureLog.w(TAG, "load failed — treating as no ticket: ${e.message}")
            null
        }
    }

    suspend fun save(ticket: ConfirmationTicket) {
        try {
            val raw = json.encodeToString(ConfirmationTicket.serializer(), ticket)
            appContext.confirmationDataStore.edit { it[KEY_TICKET] = raw }
        } catch (e: Exception) {
            SecureLog.w(TAG, "save failed: ${e.message}")
        }
    }

    suspend fun clear() {
        try {
            appContext.confirmationDataStore.edit { it.remove(KEY_TICKET) }
        } catch (e: Exception) {
            SecureLog.w(TAG, "clear failed: ${e.message}")
        }
    }
}

// A corrupt store must never brick recovery — rebuild it as empty (worst case: one lost ticket,
// which the server + reconstruct-from-DB path still recover from).
private val Context.confirmationDataStore by preferencesDataStore(
    name = "subscription_confirmation",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
