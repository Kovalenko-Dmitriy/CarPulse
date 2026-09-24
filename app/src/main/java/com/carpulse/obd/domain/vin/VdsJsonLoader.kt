package com.carpulse.obd.domain.vin

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Загрузчик VDS-базы из assets/vds_database.json.
 *
 * Формат JSON:
 * {
 *   "schema_version": 1,
 *   "updated_at": "2026-09-24",
 *   "entries": [
 *     { "prefix": "XTAGFL", "make": "Lada", "model": "Vesta" },
 *     ...
 *   ]
 * }
 *
 * Fail-safe: некорректные записи логируются и пропускаются.
 */
class VdsJsonLoader(private val context: Context) {

    fun load(): List<VdsEntry> {
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

    private fun parse(json: String): List<VdsEntry> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("entries") ?: run {
            Log.w(TAG, "В JSON нет секции 'entries'")
            return emptyList()
        }

        val result = mutableListOf<VdsEntry>()
        val seenPrefixes = mutableSetOf<String>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue

            val prefix = obj.optString("prefix").uppercase().takeIf { it.length >= 4 } ?: run {
                Log.w(TAG, "Запись #$i пропущена: нет prefix или короче 4 символов")
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

            result += VdsEntry(prefix = prefix, make = make, model = model)
        }

        Log.d(TAG, "Загружено ${result.size} записей VDS из $ASSET_NAME")
        return result
    }

    companion object {
        private const val TAG = "VdsJsonLoader"
        private const val ASSET_NAME = "vds_database.json"
    }
}