package com.carpulse.obd.domain.vin.jdm

import android.content.Context
import android.util.Log
import com.carpulse.obd.domain.profile.Region
import org.json.JSONObject

/**
 * Загрузчик JDM-базы из assets/jdm_database.json.
 *
 * Формат JSON:
 * {
 *   "schema_version": 1,
 *   "updated_at": "2026-09-24",
 *   "entries": [
 *     {
 *       "prefix": "EU1",
 *       "make": "Honda",
 *       "model": "Civic Ferio / Domani",
 *       "yearFrom": 1995,
 *       "yearTo": 2000
 *     },
 *     ...
 *   ]
 * }
 *
 * Fail-safe: некорректные записи логируются и пропускаются.
 * При ошибке чтения файла возвращается пустой список.
 */
class JdmJsonLoader(private val context: Context) {

    fun load(): List<JdmEntry> {
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

    private fun parse(json: String): List<JdmEntry> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("entries") ?: run {
            Log.w(TAG, "В JSON нет секции 'entries'")
            return emptyList()
        }

        val result = mutableListOf<JdmEntry>()
        val seenPrefixes = mutableSetOf<String>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue

            val prefix = obj.optString("prefix").uppercase().takeIf { it.length >= 2 } ?: run {
                Log.w(TAG, "Запись #$i пропущена: нет prefix или короче 2 символов")
                continue
            }
            if (prefix in seenPrefixes) {
                Log.w(TAG, "Дубликат prefix '$prefix' — пропущен")
                continue
            }
            seenPrefixes += prefix

            val make = obj.optString("make").takeIf { it.isNotBlank() } ?: run {
                Log.w(TAG, "Запись '$prefix' пропущена: нет make")
                continue
            }
            val model = obj.optString("model").takeIf { it.isNotBlank() } ?: run {
                Log.w(TAG, "Запись '$prefix' пропущена: нет model")
                continue
            }

            // yearFrom / yearTo могут быть null
            val yearFrom = obj.optInt("yearFrom", 0).takeIf { it > 0 }
            var yearTo = obj.optInt("yearTo", 0).takeIf { it > 0 }
            if (yearFrom != null && yearTo != null && yearTo < yearFrom) {
                Log.w(TAG, "Запись '$prefix': yearTo < yearFrom — меняем местами")
                val tmp = yearFrom
                yearTo = yearFrom
            }

            // Регион (по умолчанию ASIA для JDM)
            val regionStr = obj.optString("region").takeIf { it.isNotBlank() }
            val region = mapRegion(regionStr, prefix)

            result += JdmEntry(
                prefix = prefix,
                make = make,
                model = model,
                yearFrom = yearFrom,
                yearTo = yearTo,
                region = region,
            )
        }

        Log.d(TAG, "Загружено ${result.size} записей JDM из $ASSET_NAME")
        return result
    }

    /**
     * Маппинг региона. Для JDM — по умолчанию ASIA.
     */
    private fun mapRegion(raw: String?, prefix: String): Region = when (raw) {
        null -> Region.ASIA
        "Азия", "Восточная Азия" -> Region.ASIA
        "Европа", "Западная Европа" -> Region.EU
        "Северная Америка" -> Region.US
        "Европа/СНГ" -> Region.RU
        else -> {
            Log.w(TAG, "Запись '$prefix': неизвестный регион '$raw' → ASIA (default)")
            Region.ASIA
        }
    }

    companion object {
        private const val TAG = "JdmJsonLoader"
        private const val ASSET_NAME = "jdm_database.json"
    }
}