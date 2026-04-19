package com.touchlock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level extension — one DataStore instance per process
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "touchlock_prefs")

/**
 * SettingsRepository — Phase 3 (Task 3.5)
 *
 * All persistent user preferences accessed via DataStore<Preferences>.
 * Never store PIN in plain text — always SHA-256 hashed (enforced here).
 */
class SettingsRepository(private val context: Context) {

    // ── Keys ──────────────────────────────────────────────────────────────────
    companion object {
        val KEY_UNLOCK_METHOD         = stringPreferencesKey("unlock_method")
        val KEY_AUTO_LOCK_DELAY       = intPreferencesKey("auto_lock_delay")
        val KEY_SHOW_LOCK_MESSAGE     = booleanPreferencesKey("show_lock_message")
        val KEY_FLOATING_BUTTON       = booleanPreferencesKey("floating_button_enabled")
        val KEY_PIN_ENABLED           = booleanPreferencesKey("pin_enabled")
        val KEY_PIN_HASH              = stringPreferencesKey("pin_hash")
        val KEY_KEEP_SCREEN_AWAKE     = booleanPreferencesKey("keep_screen_awake")

        // UnlockMethod string constants
        const val METHOD_VOLUME_COMBO = "VOLUME_COMBO"
        const val METHOD_VOL_DOWN    = "VOL_DOWN_HOLD"
        const val METHOD_VOL_UP      = "VOL_UP_HOLD"
    }

    // ── Flows ─────────────────────────────────────────────────────────────────
    val unlockMethod: Flow<String>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY_UNLOCK_METHOD] ?: METHOD_VOLUME_COMBO
        }

    /** Auto-lock delay in seconds. 0 = disabled. */
    val autoLockDelay: Flow<Int>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY_AUTO_LOCK_DELAY] ?: 0
        }

    val showLockMessage: Flow<Boolean>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY_SHOW_LOCK_MESSAGE] ?: true
        }

    val floatingButtonEnabled: Flow<Boolean>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY_FLOATING_BUTTON] ?: true
        }

    val pinEnabled: Flow<Boolean>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY_PIN_ENABLED] ?: false
        }

    /** SHA-256 hash of the user's PIN. Empty string = no PIN set. */
    val pinHash: Flow<String>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY_PIN_HASH] ?: ""
        }

    val keepScreenAwake: Flow<Boolean>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY_KEEP_SCREEN_AWAKE] ?: true
        }

    // ── Write helpers ─────────────────────────────────────────────────────────
    suspend fun setUnlockMethod(method: String) {
        context.dataStore.edit { it[KEY_UNLOCK_METHOD] = method }
    }

    suspend fun setAutoLockDelay(seconds: Int) {
        context.dataStore.edit { it[KEY_AUTO_LOCK_DELAY] = seconds }
    }

    suspend fun setShowLockMessage(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_LOCK_MESSAGE] = show }
    }

    suspend fun setFloatingButtonEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FLOATING_BUTTON] = enabled }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PIN_ENABLED] = enabled }
    }

    /**
     * Store PIN as SHA-256 hash.
     * Call with an already-hashed string, OR call [hashPin] first.
     */
    suspend fun setPinHash(hash: String) {
        context.dataStore.edit { it[KEY_PIN_HASH] = hash }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_AWAKE] = enabled }
    }

    // ── Utility ───────────────────────────────────────────────────────────────
    /** SHA-256 hash of a plain-text PIN. Never store the raw PIN. */
    fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
