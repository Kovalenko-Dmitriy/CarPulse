package com.carpulse.obd.domain

import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Центральный компонент, который решает, что доступно пользователю.
 *
 * Разделяет:
 *  - Pro (разовая покупка) — сброс DTC, Freeze Frame, Mode 06, безлимит авто, экспорт
 *  - Subscription (подписка) — облако, умные алерты, синхронизация
 *
 * Все проверки доступны как:
 *  - StateFlow для UI (подписка на изменения)
 *  - suspend-методы для корутин (одноразовая проверка)
 *
 * Кэширует последнее известное значение, чтобы не дёргать DataStore
 * при каждом вызове из горячего пути (например, polling).
 */
class FeatureGate(
    private val prefs: UserPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    companion object {
        private const val TAG = "FEATURE_GATE:"
        const val FREE_VEHICLE_LIMIT = 1
        const val FREE_REPORTS_LIMIT = 3
    }

    // ============================================================
    // БАЗОВЫЕ СОСТОЯНИЯ (кэшируются в StateFlow)
    // ============================================================

    /** Пользователь купил Pro (разовая покупка). */
    val isPro: StateFlow<Boolean> = prefs.isPro
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Активна подписка (облако, алерты). */
    val isSubscriptionActive: StateFlow<Boolean> = prefs.isSubscriptionActive
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Количество использованных бесплатных отчётов. */
    private val freeReportsUsed: StateFlow<Int> = prefs.freeReportsUsed
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** Количество добавленных автомобилей. */
    private val vehicleCount: StateFlow<Int> = prefs.vehicleCount
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // ============================================================
    // PRO-ФИЧИ (разовая покупка)
    // ============================================================

    /**
     * Сброс ошибок (Mode 04).
     * Доступно только Pro. В бесплатной версии показываем Paywall.
     */
    val canResetDtc: StateFlow<Boolean> = isPro

    /**
     * Freeze Frame (Mode 02) — условия, при которых загорелся чек.
     * Доступно только Pro.
     */
    val canViewFreezeFrame: StateFlow<Boolean> = isPro

    /**
     * Mode 06 (results of on-board monitoring).
     * Доступно только Pro.
     */
    val canUseMode06: StateFlow<Boolean> = isPro

    /**
     * Профили автомобилей: 1 бесплатно, безлимит в Pro.
     * Возвращает true, если пользователь может добавить ещё один автомобиль.
     */
    val canAddVehicle: StateFlow<Boolean> = combine(isPro, vehicleCount) { pro, count ->
        pro || count < FREE_VEHICLE_LIMIT
    }.stateIn(scope, SharingStarted.Eagerly, true)

    /**
     * Экспорт отчёта (CSV/PDF для механика).
     * Pro — безлимит. Бесплатно — 3 отчёта, потом Paywall.
     */
    val canExportReport: StateFlow<Boolean> = combine(isPro, freeReportsUsed) { pro, used ->
        pro || used < FREE_REPORTS_LIMIT
    }.stateIn(scope, SharingStarted.Eagerly, true)

    /**
     * Сколько бесплатных отчётов осталось.
     * Для UI: "Осталось 2 из 3 бесплатных отчётов".
     */
    val freeReportsRemaining: StateFlow<Int> = combine(isPro, freeReportsUsed) { pro, used ->
        if (pro) Int.MAX_VALUE else (FREE_REPORTS_LIMIT - used).coerceAtLeast(0)
    }.stateIn(scope, SharingStarted.Eagerly, FREE_REPORTS_LIMIT)

    /**
     * Сколько автомобилей можно ещё добавить.
     * Для UI: "1 бесплатно, безлимит в Pro".
     */
    val vehiclesRemaining: StateFlow<Int> = combine(isPro, vehicleCount) { pro, count ->
        if (pro) Int.MAX_VALUE else (FREE_VEHICLE_LIMIT - count).coerceAtLeast(0)
    }.stateIn(scope, SharingStarted.Eagerly, FREE_VEHICLE_LIMIT)

    // ============================================================
    // SUBSCRIPTION-ФИЧИ (подписка)
    // ============================================================

    /**
     * Облачная синхронизация (история поездок, ошибок, несколько устройств).
     * Доступно только с активной подпиской.
     */
    val canUseCloudSync: StateFlow<Boolean> = isSubscriptionActive

    /**
     * Умные алерты (анализ трендов, push-уведомления).
     * Доступно только с активной подпиской.
     */
    val canUseSmartAlerts: StateFlow<Boolean> = isSubscriptionActive

    /**
     * Антиугонный режим (пуш при отключении адаптера/падении напряжения).
     * Доступно только с активной подпиской.
     */
    val canUseAntiTheft: StateFlow<Boolean> = isSubscriptionActive

    // ============================================================
    // SUSPEND-МЕТОДЫ ДЛЯ КОРУТИН (одноразовая проверка)
    // ============================================================

    /**
     * Проверка перед сбросом DTC.
     * Используется в DashboardViewModel перед вызовом Elm327Client.resetDtc().
     */
    suspend fun checkCanResetDtc(): Boolean {
        val result = isPro.first()
        FileLogger.write("$TAG checkCanResetDtc -> $result")
        return result
    }

    /**
     * Проверка перед чтением Freeze Frame.
     */
    suspend fun checkCanViewFreezeFrame(): Boolean {
        val result = isPro.first()
        FileLogger.write("$TAG checkCanViewFreezeFrame -> $result")
        return result
    }

    /**
     * Проверка перед Mode 06.
     */
    suspend fun checkCanUseMode06(): Boolean {
        val result = isPro.first()
        FileLogger.write("$TAG checkCanUseMode06 -> $result")
        return result
    }

    /**
     * Проверка перед добавлением автомобиля.
     */
    suspend fun checkCanAddVehicle(): Boolean {
        val pro = isPro.first()
        val count = vehicleCount.first()
        val result = pro || count < FREE_VEHICLE_LIMIT
        FileLogger.write("$TAG checkCanAddVehicle (pro=$pro, count=$count) -> $result")
        return result
    }

    /**
     * Проверка перед экспортом отчёта.
     * Если разрешено и это бесплатный пользователь — инкрементирует счётчик.
     */
    suspend fun checkAndConsumeReportExport(): Boolean {
        val pro = isPro.first()
        if (pro) {
            FileLogger.write("$TAG checkAndConsumeReportExport -> true (pro)")
            return true
        }
        val used = freeReportsUsed.first()
        if (used >= FREE_REPORTS_LIMIT) {
            FileLogger.write("$TAG checkAndConsumeReportExport -> false (limit reached: $used)")
            return false
        }
        prefs.incrementReportsUsed()
        FileLogger.write("$TAG checkAndConsumeReportExport -> true (used ${used + 1}/$FREE_REPORTS_LIMIT)")
        return true
    }

    /**
     * Проверка перед облачной синхронизацией.
     */
    suspend fun checkCanUseCloudSync(): Boolean {
        val result = isSubscriptionActive.first()
        FileLogger.write("$TAG checkCanUseCloudSync -> $result")
        return result
    }

    /**
     * Проверка перед умными алертами.
     */
    suspend fun checkCanUseSmartAlerts(): Boolean {
        val result = isSubscriptionActive.first()
        FileLogger.write("$TAG checkCanUseSmartAlerts -> $result")
        return result
    }

    // ============================================================
    // РУЧНОЕ УПРАВЛЕНИЕ (для отладки и восстановления покупок)
    // ============================================================

    /**
     * Принудительно установить Pro (для отладки или после восстановления покупки).
     */
    suspend fun setPro(value: Boolean) {
        prefs.setPro(value)
        FileLogger.write("$TAG setPro -> $value")
    }

    /**
     * Принудительно установить подписку.
     */
    suspend fun setSubscription(value: Boolean) {
        prefs.setSubscription(value)
        FileLogger.write("$TAG setSubscription -> $value")
    }

    /**
     * Сбросить счётчик бесплатных отчётов (для отладки).
     */
    suspend fun resetFreeReports() {
        prefs.resetReportsUsed()
        FileLogger.write("$TAG resetFreeReports")
    }

    /**
     * Обновить количество автомобилей (вызывается при добавлении/удалении).
     */
    suspend fun updateVehicleCount(count: Int) {
        prefs.setVehicleCount(count)
        FileLogger.write("$TAG updateVehicleCount -> $count")
    }
}