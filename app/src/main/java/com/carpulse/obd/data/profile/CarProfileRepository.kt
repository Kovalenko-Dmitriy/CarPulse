package com.carpulse.obd.data.profile

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.carpulse.obd.domain.profile.CarProfile
import com.carpulse.obd.domain.profile.Region
import com.carpulse.obd.domain.units.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException as JavaIOException

/**
 * Единый DataStore для профиля автомобиля.
 *
 * Делегат `preferencesDataStore` объявлен на верхнем уровне файла
 * (не внутри класса) — это требование API.
 */
private val Context.carProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "car_profile",
)

/**
 * Репозиторий профиля автомобиля.
 *
 * @param context application context — DataStore привязывается к нему.
 */
class CarProfileRepository(
    private val context: Context,
) {

    private object Keys {
        val VIN = stringPreferencesKey("vin")
        val BODY_NUMBER = stringPreferencesKey("body_number")
        val MAKE = stringPreferencesKey("make")
        val MODEL = stringPreferencesKey("model")
        val YEAR = intPreferencesKey("year")
        val REGION = stringPreferencesKey("region")
        val UNIT_OVERRIDE = stringPreferencesKey("unit_override")
        val ECU_ID = stringPreferencesKey("ecu_id")
    }

    /**
     * Реактивный поток профиля. UI подписывается один раз и получает
     * обновления автоматически при любом save().
     *
     * При ошибке чтения — отдаём дефолт и логируем. Не бросаем
     * исключение, чтобы не убить корутину-коллектор.
     */
    val profileFlow: Flow<CarProfile> = context.carProfileDataStore.data
        .catch { throwable ->
            if (throwable is IOException || throwable is JavaIOException) {
                Log.e(TAG, "Ошибка чтения DataStore, отдаём пустой профиль", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs -> prefs.toCarProfile() }

    /**
     * Однократное чтение.
     */
    suspend fun load(): CarProfile = profileFlow.first()

    /**
     * Сохранение профиля. Атомарно через DataStore.edit.
     *
     * @throws IOException если запись не удалась (диск полон, нет прав).
     */
    suspend fun save(profile: CarProfile) {
        try {
            context.carProfileDataStore.edit { prefs ->
                profile.vin?.takeIf { it.isNotBlank() }
                    ?.let { prefs[Keys.VIN] = it }
                    ?: prefs.remove(Keys.VIN)

                profile.bodyNumber?.takeIf { it.isNotBlank() }
                    ?.let { prefs[Keys.BODY_NUMBER] = it }
                    ?: prefs.remove(Keys.BODY_NUMBER)

                profile.make?.takeIf { it.isNotBlank() }
                    ?.let { prefs[Keys.MAKE] = it }
                    ?: prefs.remove(Keys.MAKE)

                profile.model?.takeIf { it.isNotBlank() }
                    ?.let { prefs[Keys.MODEL] = it }
                    ?: prefs.remove(Keys.MODEL)

                profile.year?.let { prefs[Keys.YEAR] = it }
                    ?: prefs.remove(Keys.YEAR)

                prefs[Keys.REGION] = profile.region.name

                profile.unitSystemOverride?.let { prefs[Keys.UNIT_OVERRIDE] = it.name }
                    ?: prefs.remove(Keys.UNIT_OVERRIDE)

                profile.ecuId?.takeIf { it.isNotBlank() }
                    ?.let { prefs[Keys.ECU_ID] = it }
                    ?: prefs.remove(Keys.ECU_ID)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Не удалось сохранить профиль", e)
            throw e
        }
    }

    /**
     * Сброс профиля. Нужен для кнопки «Забыть автомобиль».
     */
    suspend fun clear() {
        context.carProfileDataStore.edit { it.clear() }
    }

    // ---------- Приватные мапперы ----------

    private fun Preferences.toCarProfile(): CarProfile = CarProfile(
        vin = this[Keys.VIN],
        bodyNumber = this[Keys.BODY_NUMBER],
        make = this[Keys.MAKE],
        model = this[Keys.MODEL],
        year = this[Keys.YEAR],
        region = parseEnum(this[Keys.REGION], Region.UNKNOWN),
        unitSystemOverride = this[Keys.UNIT_OVERRIDE]?.let { raw ->
            parseEnumOrNull<UnitSystem>(raw)
        },
        ecuId = this[Keys.ECU_ID],
    )

    /**
     * Безопасный парсинг enum из строки.
     *
     * ВАЖНО: сохраняем через .name, а не ordinal. Если в будущем
     * добавим новое значение в Region — старые записи продолжат
     * читаться корректно.
     */
    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private inline fun <reified T : Enum<T>> parseEnumOrNull(raw: String?): T? =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    companion object {
        private const val TAG = "CarProfileRepository"
    }
}