package com.carpulse.obd.domain.vin

import android.content.Context
import android.util.Log
import com.carpulse.obd.domain.profile.Region
import org.json.JSONObject

/**
 * Загрузчик WMI-базы из assets/vin_wmi.json.
 *
 * Формат JSON:
 * {
 *   "wmi": {
 *     "XTA": {
 *       "country": "Россия",
 *       "manufacturer": "АвтоВАЗ (Lada)",
 *       "region": "Европа/СНГ",
 *       "market": ["Россия", "СНГ"]
 *     },
 *     ...
 *   }
 * }
 *
 * Fail-safe: любая некорректная запись логируется и пропускается.
 * Если файл отсутствует или не парсится — возвращается пустой список,
 * VinDecoder применит fallback по первому символу VIN.
 */
class WmiJsonLoader(private val context: Context) {

    fun load(): List<WmiEntry> {
        val json = try {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось открыть $ASSET_NAME", e)
            return emptyList()
        }

        return try {
            parse(json)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось распарсить $ASSET_NAME", e)
            emptyList()
        }
    }

    private fun parse(json: String): List<WmiEntry> {
        val root = JSONObject(json)
        val wmiObj = root.optJSONObject("wmi") ?: run {
            Log.w(TAG, "В JSON нет секции 'wmi'")
            return emptyList()
        }

        val result = mutableListOf<WmiEntry>()

        for (wmi in wmiObj.keys()) {
            val obj = wmiObj.optJSONObject(wmi) ?: continue

            val manufacturer = obj.optString("manufacturer")
                .takeIf { it.isNotBlank() } ?: run {
                Log.w(TAG, "WMI '$wmi' пропущен: нет manufacturer")
                continue
            }

            val country = obj.optString("country")
            val regionStr = obj.optString("region")
            val region = mapRegion(regionStr, wmi)

            val markets = obj.optJSONArray("market")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }
                }
            } ?: emptyList()

            result += WmiEntry(
                wmi = wmi,
                manufacturer = manufacturer,
                country = country,
                region = region,
                markets = markets,
            )
        }

        Log.d(TAG, "Загружено ${result.size} записей WMI из $ASSET_NAME")
        return result
    }

    /**
     * Маппинг строкового региона из JSON в Region enum.
     *
     *  - "Европа/СНГ"        → RU    (Россия, СНГ, ГАЗ, УАЗ, КАМАЗ)
     *  - "Западная Европа"   → EU    (Германия, Франция, Италия, Швеция)
     *  - "Европа"            → EU    (старое значение, fallback)
     *  - "Восточная Азия"    → ASIA  (Япония, Корея, Китай)
     *  - "Азия"              → ASIA  (старое значение, fallback)
     *  - "Северная Америка"  → US    (США, Канада, Мексика)
     *  - всё остальное       → UNKNOWN
     */
    private fun mapRegion(raw: String, wmi: String): Region = when (raw) {
        "Европа/СНГ" -> Region.RU
        "Западная Европа", "Европа" -> Region.EU
        "Восточная Азия", "Азия" -> Region.ASIA
        "Северная Америка" -> Region.US
        else -> {
            if (raw.isNotBlank()) {
                Log.w(TAG, "WMI '$wmi': неизвестный регион '$raw' → UNKNOWN")
            }
            Region.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "WmiJsonLoader"
        private const val ASSET_NAME = "vin_wmi.json"
    }
}