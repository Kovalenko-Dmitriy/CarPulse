package com.carpulse.obd

import android.content.Context
import android.util.Log
import com.android.billingclient.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Расширенный логгер в файл.
 *
 * Формат строки:
 *   HH:mm:ss.SSS  [thread]  LEVEL/TAG  message
 *
 * Уровни:
 *   D — debug (детали: AT-команды, GPS-точки, poll-цикл)
 *   I — info (жизненный цикл, состояния)
 *   W — warning (retry, отброшенные кадры, таймауты)
 *   E — error (исключения, Broken pipe)
 *
 * Дублирует в logcat с тегом "CarPulse:<TAG>", чтобы можно было
 * смотреть через adb logcat без выгрузки файла.
 *
 * Файл: /data/data/com.carpulse.obd/files/logs/carpulse_YYYY-MM-DD_HH-mm-ss.log
 */
object FileLogger {

    private const val TAG = "FileLogger"
    private const val LOGCAT_TAG = "CarPulse"

    private var writer: FileWriter? = null
    private var logFile: File? = null

    // SimpleDateFormat не потокобезопасен — синхронизируем.
    private val dateFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    @Volatile
    private var started = false

    fun start(ctx: Context) {
        synchronized(this) {
            try {
                val dir = File(ctx.filesDir, "logs")
                if (!dir.exists()) dir.mkdirs()

                val ts = fileFmt.format(Date())
                logFile = File(dir, "carpulse_$ts.log")
                writer = FileWriter(logFile, true)
                started = true

                Log.d(TAG, "Логи пишутся в: ${logFile?.absolutePath}")
                write("LOGGER", 'I', "=== CarPulse started ===")
                write("LOGGER", 'I', "log file: ${logFile?.absolutePath}")
                write("LOGGER", 'I', "app version: ${BuildConfig.VERSION_NAME}")
                write("LOGGER", 'I', "device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось открыть файл логов", e)
            }
        }
    }

    /**
     * Основной метод. Уровень: D/I/W/E.
     * Тег — короткий (ELM327, OBD_REPO, BT, TRIPS, GPS, ...).
     */
    fun write(tag: String, level: Char, message: String) {
        val ts = dateFmt.format(Date())
        val thread = Thread.currentThread().name
        val line = "$ts  [$thread]  $level/$tag  $message"

        // В logcat
        when (level) {
            'E' -> Log.e("$LOGCAT_TAG:$tag", message)
            'W' -> Log.w("$LOGCAT_TAG:$tag", message)
            'I' -> Log.i("$LOGCAT_TAG:$tag", message)
            else -> Log.d("$LOGCAT_TAG:$tag", message)
        }

        // В файл
        synchronized(this) {
            try {
                writer?.apply {
                    append(line)
                    append('\n')
                    flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка записи лога", e)
            }
        }
    }

    // Удобные обёртки
    fun d(tag: String, message: String) = write(tag, 'D', message)
    fun i(tag: String, message: String) = write(tag, 'I', message)
    fun w(tag: String, message: String) = write(tag, 'W', message)
    fun e(tag: String, message: String) = write(tag, 'E', message)

    /** Совместимость со старым API. */
    fun write(message: String) {
        // Определяем тег из первой части строки, если она в формате "TAG: message"
        val idx = message.indexOf(':')
        val tag = if (idx in 1..20) message.substring(0, idx).trim() else "APP"
        val body = if (idx in 1..20) message.substring(idx + 1).trim() else message
        write(tag, 'I', body)
    }

    fun stop() {
        synchronized(this) {
            try {
                write("LOGGER", 'I', "=== CarPulse stopped ===")
                writer?.close()
            } catch (_: Exception) {}
            writer = null
            started = false
        }
    }

    fun getLogFile(): File? = logFile
}