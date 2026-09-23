package com.carpulse.obd.data.db

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DtcImporter(
    private val ctx: Context,
    private val dao: DtcDao
) {

    companion object {
        private const val TAG = "DTC_IMPORT"
        private const val ASSET_NAME = "obdex_all.json"
    }

    /** Импортирует коды, если база ещё пуста. Возвращает число загруженных. */
    suspend fun importIfNeeded(): Int = withContext(Dispatchers.IO) {
        val existing = dao.countCodes()
        if (existing > 0) {
            Log.d(TAG, "База уже содержит $existing кодов, импорт не нужен")
            return@withContext existing
        }

        val json = try {
            ctx.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось открыть $ASSET_NAME", e)
            return@withContext 0
        }

        val array: JsonArray = try {
            Gson().fromJson(json, JsonArray::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось распарсить JSON", e)
            return@withContext 0
        }

        val codes = ArrayList<DtcCodeEntity>(array.size())
        val causes = ArrayList<DtcCauseEntity>()

        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject

            val code = obj.get("code")?.asString ?: continue
            val category = obj.get("category")?.asString ?: "unknown"

            val title = obj.getAsJsonObject("title")
            val titleEn = title?.get("en")?.asString ?: ""
            val titleDe = title?.get("de")?.asString ?: ""

            val desc = obj.getAsJsonObject("description")
            val descEn = desc?.get("en")?.asString ?: ""
            val descDe = desc?.get("de")?.asString ?: ""

            val compsJson = obj.get("affected_components")?.toString() ?: "[]"
            val symptomsJson = obj.get("symptoms")?.toString() ?: "[]"

            val repair = obj.getAsJsonObject("repair")
            val difficulty = repair?.get("difficulty")?.asString ?: "medium"
            val diyPossible = repair?.get("diy_possible")?.asBoolean ?: false

            val costArr = repair?.getAsJsonArray("estimated_cost_eur")
            val costMin = costArr?.getOrNull(0)?.asInt ?: 0
            val costMax = costArr?.getOrNull(1)?.asInt ?: 0

            val hoursArr = repair?.getAsJsonArray("estimated_hours")
            val hoursMin = hoursArr?.getOrNull(0)?.asFloat ?: 0f
            val hoursMax = hoursArr?.getOrNull(1)?.asFloat ?: 0f

            val flags = obj.getAsJsonObject("flags")
            val mil = flags?.get("mil")?.asBoolean ?: false
            val emissions = flags?.get("emissions_relevant")?.asBoolean ?: false

            codes.add(
                DtcCodeEntity(
                    code = code,
                    category = category,
                    titleEn = titleEn,
                    titleDe = titleDe,
                    titleRu = null,
                    descriptionEn = descEn,
                    descriptionDe = descDe,
                    descriptionRu = null,
                    affectedComponentsJson = compsJson,
                    symptomsJson = symptomsJson,
                    difficulty = difficulty,
                    diyPossible = diyPossible,
                    costMinEur = costMin,
                    costMaxEur = costMax,
                    hoursMin = hoursMin,
                    hoursMax = hoursMax,
                    mil = mil,
                    emissionsRelevant = emissions
                )
            )

            // Причины
            val causesArr = obj.getAsJsonArray("common_causes") ?: continue
            for (causeEl in causesArr) {
                if (!causeEl.isJsonObject) continue
                val c = causeEl.asJsonObject
                val causeId = c.get("id")?.asString ?: continue
                val likelihood = c.get("likelihood")?.asString ?: "medium"
                val label = c.getAsJsonObject("label")
                val labelEn = label?.get("en")?.asString ?: ""
                val labelDe = label?.get("de")?.asString ?: ""

                causes.add(
                    DtcCauseEntity(
                        code = code,
                        causeId = causeId,
                        likelihood = likelihood,
                        labelEn = labelEn,
                        labelDe = labelDe,
                        labelRu = null
                    )
                )
            }
        }

        Log.d(TAG, "Импортируем ${codes.size} кодов и ${causes.size} причин")
        dao.insertFull(codes, causes)
        Log.d(TAG, "Импорт завершён")
        codes.size
    }
}

// Утилита: безопасно взять элемент JsonArray по индексу
private fun JsonArray.getOrNull(index: Int): com.google.gson.JsonElement? =
    if (index in 0 until size()) get(index) else null