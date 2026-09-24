package com.carpulse.obd.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.carpulse.obd.data.profile.CarProfileRepository
import com.carpulse.obd.domain.ecu.EcuResolver
import com.carpulse.obd.domain.vin.VinDecoder

/**
 * Фабрика ViewModel без DI-фреймворка.
 *
 * Все зависимости передаются снаружи (из CarPulseApp) — чтобы DataStore
 * и базы JSON не открывались повторно при каждой навигации на экран.
 *
 * @param profileRepo репозиторий профиля (DataStore).
 * @param decoder     декодер VIN.
 * @param ecuResolver резолвер ЭБУ.
 */
class CarProfileViewModelFactory(
    private val profileRepo: CarProfileRepository,
    private val decoder: VinDecoder,
    private val ecuResolver: EcuResolver,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CarProfileViewModel::class.java)) {
            "CarProfileViewModelFactory не умеет создавать ${modelClass.name}"
        }
        return CarProfileViewModel(
            decoder = decoder,
            profileRepo = profileRepo,
            ecuResolver = ecuResolver,
        ) as T
    }
}