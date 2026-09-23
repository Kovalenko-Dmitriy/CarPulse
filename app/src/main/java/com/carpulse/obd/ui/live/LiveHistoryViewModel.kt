package com.carpulse.obd.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carpulse.obd.AppViewModel
import com.carpulse.obd.data.obd.Pid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LiveHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val _history = MutableStateFlow<Map<Pid, List<Float>>>(emptyMap())
    val history: StateFlow<Map<Pid, List<Float>>> = _history

    private val maxPoints = 300

    private var collectJob: Job? = null
    private var lastVm: AppViewModel? = null

    /**
     * Подключить к потоку live из AppViewModel.
     * Если уже подписаны на тот же vm — повторно не подписываемся.
     * Если vm сменился — переподписываемся.
     */
    fun startCollecting(appVm: AppViewModel) {
        if (lastVm === appVm && collectJob?.isActive == true) return
        collectJob?.cancel()
        lastVm = appVm

        val liveFlow = appVm.live ?: return

        collectJob = viewModelScope.launch {
            liveFlow.collect { snapshot ->
                if (snapshot.values.isEmpty()) return@collect
                val current = _history.value.toMutableMap()
                for ((pid, timed) in snapshot.values) {
                    if (timed.isStale()) continue
                    val old = current[pid] ?: emptyList()
                    val updated = (old + timed.value).takeLast(maxPoints)
                    current[pid] = updated
                }
                _history.value = current
            }
        }
    }

    fun clear() {
        _history.value = emptyMap()
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
    }
}