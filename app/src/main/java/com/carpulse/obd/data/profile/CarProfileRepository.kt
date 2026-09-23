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
 * Делегат `preferencesDataStore` обязан быть объявлен на верхнем уровне файла
 * (не внутри класса) — это требование API. Имя "car_profile" определяет
 * имя файла на диске: /data/data/com.carpulse.obd/files/datastore/car_profile.preferences_pb
 */
private val Context.carProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "car_profile",
)

/**
 * Репозиторий профиля автомобиля.
 *
 * Реализует MVVM-контракт: ViewModel не знает о DataStore, работает только
 * с доменными типами CarProfile / Region / UnitSystem.
 *
 * Потокобезопасность: все методы suspend, DataStore сам сериализует доступ.
 * Экземпляр должен быть один на приложение (см. CarPulseApp).
 *
 * Обработка ошибок:
 *  - чтение: при IOException возвращаем пустой профиль, логируем;
 *  - запись: пробрасываем IOException наверх — ViewModel покажет Snackbar.
 */
class CarProfileRepository(
    private val context: Context,
) {

    private object Keys {
        val VIN = stringPreferencesKey("vin")
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
     * При ошибке чтения (повреждённый файл, нет доступа) — отдаём дефолт
     * и логируем. Не бросаем исключение, чтобы не убить корутину-коллектор.
     */
    val profileFlow: Flow<CarProfile> = context.carProfileDataStore.data
        .catch { throwable ->
            // DataStore кидает IOException (androidx.datastore.core.IOException)
            // при повреждении файла. Логируем и отдаём пустые настройки.
            if (throwable is IOException || throwable is JavaIOException) {
                Log.e(TAG, "Ошибка чтения DataStore, отдаём пустой профиль", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs -> prefs.toCarProfile() }

    /**
     * Однократное чтение — для мест, где не нужен реактивный поток
     * (например, при инициализации сервиса).
     */
    suspend fun load(): CarProfile = profileFlow.first()

    /**
     * Сохранение профиля. Атомарно (DataStore.edit — транзакция).
     *
     * @throws IOException если запись не удалась (диск полон, нет прав).
     *         ViewModel обязана обработать и показать пользователю.
     */
    suspend fun save(profile: CarProfile) {
        try {
            context.carProfileDataStore.edit { prefs ->
                profile.vin?.takeIf { it.isNotBlank() }
                    ?.let { prefs[Keys.VIN] = it }
                    ?: prefs.remove(Keys.VIN)

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
     * Сброс профиля в дефолт. Нужен для кнопки «Забыть автомобиль».
     */
    suspend fun clear() {
        context.carProfileDataStore.edit { it.clear() }
    }

    // ---------- Приватные мапперы ----------

    private fun Preferences.toCarProfile(): CarProfile = CarProfile(
        vin = this[Keys.VIN],
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
     * ВАЖНО: сохраняем через .name, а не ordinal. Если в будущем добавим
     * новое значение в Region (например, Region.AFRICA), старые записи
     * продолжат читаться корректно. С ordinal — «съехали» бы.
     *
     * Если значение в файле устарело/битое — возвращаем fallback,
     * а не падаем с IllegalArgumentException.
     */
    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private inline fun <reified T : Enum<T>> parseEnumOrNull(raw: String?): T? =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    companion object {
        private const val TAG = "CarProfileRepository"
    }
}