package com.carpulse.obd.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_IS_PRO = booleanPreferencesKey("is_pro")
        private val KEY_IS_SUBSCRIPTION = booleanPreferencesKey("is_subscription")
        private val KEY_FREE_REPORTS_USED = longPreferencesKey("free_reports_used")
        private val KEY_VEHICLE_COUNT = longPreferencesKey("vehicle_count")
    }

    val isPro: Flow<Boolean> = context.userPrefsDataStore.data
        .map { it[KEY_IS_PRO] ?: false }

    val isSubscriptionActive: Flow<Boolean> = context.userPrefsDataStore.data
        .map { it[KEY_IS_SUBSCRIPTION] ?: false }

    val freeReportsUsed: Flow<Int> = context.userPrefsDataStore.data
        .map { (it[KEY_FREE_REPORTS_USED] ?: 0L).toInt() }

    val vehicleCount: Flow<Int> = context.userPrefsDataStore.data
        .map { (it[KEY_VEHICLE_COUNT] ?: 0L).toInt() }

    suspend fun setPro(value: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_IS_PRO] = value }
    }

    suspend fun setSubscription(value: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_IS_SUBSCRIPTION] = value }
    }

    suspend fun incrementReportsUsed() {
        context.userPrefsDataStore.edit {
            it[KEY_FREE_REPORTS_USED] = (it[KEY_FREE_REPORTS_USED] ?: 0L) + 1
        }
    }

    suspend fun resetReportsUsed() {
        context.userPrefsDataStore.edit { it[KEY_FREE_REPORTS_USED] = 0L }
    }

    suspend fun setVehicleCount(count: Int) {
        context.userPrefsDataStore.edit { it[KEY_VEHICLE_COUNT] = count.toLong() }
    }
}