package io.canavar.vakit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.canavar.vakit.domain.District
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

interface SettingsRepository {
    val changes: Flow<Settings>
    suspend fun read(): Settings
    suspend fun setDistrict(district: District, auto: Boolean)
    suspend fun setFix(fix: LocationFix)
    suspend fun setLastCheck(atEpochMs: Long)
    suspend fun setAutoLocation(enabled: Boolean)
    suspend fun setLocationBlocked(blocked: Boolean)
    suspend fun setPermissionAsked()
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val changes: Flow<Settings> = dataStore.data.map { toSettings(it) }

    override suspend fun read(): Settings = changes.first()

    private fun toSettings(values: Preferences): Settings {
        val lat = values[Keys.LAST_LAT]
        val lon = values[Keys.LAST_LON]
        val fix = if (lat != null && lon != null) {
            LocationFix(lat, lon, values[Keys.LAST_FIX_AT] ?: 0L)
        } else {
            null
        }
        return Settings(
            districtId = values[Keys.DISTRICT_ID],
            districtName = values[Keys.DISTRICT_NAME],
            districtProvince = values[Keys.DISTRICT_PROVINCE],
            autoLocation = values[Keys.AUTO_LOCATION] ?: true,
            lastFix = fix,
            lastCheckAt = values[Keys.LAST_CHECK_AT],
            locationBlocked = values[Keys.LOCATION_BLOCKED] ?: false,
            permissionAsked = values[Keys.PERMISSION_ASKED] ?: false,
        )
    }

    override suspend fun setDistrict(district: District, auto: Boolean) {
        dataStore.edit { values ->
            values[Keys.DISTRICT_ID] = district.id
            values[Keys.DISTRICT_NAME] = district.name
            values[Keys.DISTRICT_PROVINCE] = district.province
            values[Keys.AUTO_LOCATION] = auto
        }
    }

    override suspend fun setFix(fix: LocationFix) {
        dataStore.edit { values ->
            values[Keys.LAST_LAT] = fix.lat
            values[Keys.LAST_LON] = fix.lon
            values[Keys.LAST_FIX_AT] = fix.atEpochMs
        }
    }

    override suspend fun setLastCheck(atEpochMs: Long) {
        dataStore.edit { values -> values[Keys.LAST_CHECK_AT] = atEpochMs }
    }

    override suspend fun setAutoLocation(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.AUTO_LOCATION] = enabled }
    }

    override suspend fun setLocationBlocked(blocked: Boolean) {
        dataStore.edit { values -> values[Keys.LOCATION_BLOCKED] = blocked }
    }

    override suspend fun setPermissionAsked() {
        dataStore.edit { values -> values[Keys.PERMISSION_ASKED] = true }
    }

    private object Keys {
        val DISTRICT_ID = intPreferencesKey("district_id")
        val DISTRICT_NAME = stringPreferencesKey("district_name")
        val DISTRICT_PROVINCE = stringPreferencesKey("district_province")
        val AUTO_LOCATION = booleanPreferencesKey("auto_location")
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LON = doublePreferencesKey("last_lon")
        val LAST_FIX_AT = longPreferencesKey("last_fix_at")
        val LAST_CHECK_AT = longPreferencesKey("last_check_at")
        val LOCATION_BLOCKED = booleanPreferencesKey("location_blocked")
        val PERMISSION_ASKED = booleanPreferencesKey("permission_asked")
    }
}
