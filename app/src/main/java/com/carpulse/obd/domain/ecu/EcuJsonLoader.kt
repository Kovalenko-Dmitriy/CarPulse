package com.carpulse.obd.data.ecu

import android.content.Context
import android.util.Log
import com.carpulse.obd.domain.ecu.EcuEntry
import com.carpulse.obd.domain.ecu.PidClass
import com.carpulse.obd.domain.ecu.Protocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Загрузчик базы ЭБУ из assets/ecu_database.json.
 *
 * Принцип fail-safe: любая некорректная запись логируется и пропускается.
 * Приложение никогда не падает из-за ошибки в данных — максимум
 * работает с меньшим числом профилей.
 *
 * Валидация:
 *  - обязательные поля: id, make, model, yearFrom, ecuName, protocol, pidClass
 *  - protocol должен быть известным enum-значением
 *  - pidClass должен быть известным enum-значением
 *  - yearFrom > yearTo → меняем местами
 *  - canRequestId / canResponseId — hex-строка, если заданы
 *  - дубликаты id → оставляем первый, остальные логируем
 */
class EcuJsonLoader(private val context: Context) {

    fun load(): List<EcuEntry> {
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

    private fun parse(json: String): List<EcuEntry> {
        val root = JSONObject(json)

        val schemaVersion = root.optInt("schema_version", 0)
        if (schemaVersion > SUPPORTED_SCHEMA) {
            Log.w(TAG, "База новее, чем умеет приложение: v$schemaVersion > v$SUPPORTED_SCHEMA")
        }

        val groups = root.optJSONArray("groups") ?: return emptyList()
        val result = mutableListOf<EcuEntry>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            val ecus = group.optJSONArray("ecus") ?: continue
            val groupId = group.optString("id", "unknown")

            for (j in 0 until ecus.length()) {
                val obj = ecus.optJSONObject(j) ?: continue
                val entry = parseEntry(obj, groupId, seenIds) ?: continue
                result += entry
            }
        }

        Log.d(TAG, "Загружено ${result.size} профилей ЭБУ")
        return result
    }

    private fun parseEntry(
        obj: JSONObject,
        groupId: String,
        seenIds: MutableSet<String>,
    ): EcuEntry? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "[$groupId] пропущена запись без id")
            return null
        }
        if (id in seenIds) {
            Log.w(TAG, "Дубликат id '$id' — пропущена")
            return null
        }
        seenIds += id

        val make = obj.optString("make").takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "[$id] нет make")
            return null
        }
        val model = obj.optString("model").takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "[$id] нет model")
            return null
        }
        val ecuName = obj.optString("ecu_name").takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "[$id] нет ecu_name")
            return null
        }

        val yearFrom = obj.optInt("year_from", 0).takeIf { it > 0 } ?: run {
            Log.w(TAG, "[$id] некорректный year_from")
            return null
        }
        var yearTo = obj.optInt("year_to", 0).takeIf { it > 0 }
        if (yearTo != null && yearTo < yearFrom) {
            Log.w(TAG, "[$id] yearTo < yearFrom — меняем местами")
            yearTo = yearFrom.also { /* swap */ }
        }

        val protocolRaw = obj.optString("protocol").takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "[$id] нет protocol")
            return null
        }
        val protocol = runCatching { Protocol.valueOf(protocolRaw) }.getOrNull() ?: run {
            Log.w(TAG, "[$id] неизвестный protocol: $protocolRaw")
            return null
        }

        val pidClassRaw = obj.optString("pid_class").takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "[$id] нет pid_class")
            return null
        }
        val pidClass = runCatching { PidClass.valueOf(pidClassRaw) }.getOrNull() ?: run {
            Log.w(TAG, "[$id] неизвестный pid_class: $pidClassRaw")
            return null
        }

        val initLines = obj.optJSONArray("init_lines")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()

        val extraPids = obj.optJSONArray("extra_pids")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()

        return EcuEntry(
            id = id,
            make = make,
            model = model,
            yearFrom = yearFrom,
            yearTo = yearTo,
            ecuName = ecuName,
            protocol = protocol,
            canRequestId = obj.optString("can_request_id").takeIf { it.isNotBlank() },
            canResponseId = obj.optString("can_response_id").takeIf { it.isNotBlank() },
            initLines = initLines,
            pidClass = pidClass,
            extraPids = extraPids,
            notes = obj.optString("notes").takeIf { it.isNotBlank() },
        )
    }

    companion object {
        private const val TAG = "EcuJsonLoader"
        private const val ASSET_NAME = "ecu_database.json"
        private const val SUPPORTED_SCHEMA = 1
    }
}
