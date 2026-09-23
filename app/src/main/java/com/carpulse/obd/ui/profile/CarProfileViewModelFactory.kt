package com.carpulse.obd.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.carpulse.obd.data.profile.CarProfileRepository
import com.carpulse.obd.domain.vin.VdsLookup
import com.carpulse.obd.domain.vin.VinDecoder
import com.carpulse.obd.domain.vin.WmiDatabase

/**
 * Фабрика ViewModel без DI-фреймворка.
 *
 * Экземпляр CarProfileRepository передаётся снаружи (из CarPulseApp) —
 * чтобы DataStore не открывался повторно при каждой навигации на экран профиля.
 *
 * Если у вас появится Hilt/Koin — эта фабрика заменяется на @HiltViewModel
 * без изменения самого CarProfileViewModel (он не знает о фабрике).
 */
class CarProfileViewModelFactory(
    private val profileRepo: CarProfileRepository,
    private val decoder: VinDecoder = VinDecoder(
        wmiDb = WmiDatabase,
        vdsLookup = VdsLookup.Empty,
    ),
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CarProfileViewModel::class.java)) {
            "CarProfileViewModelFactory не умеет создавать ${modelClass.name}"
        }
        return CarProfileViewModel(
            decoder = decoder,
            profileRepo = profileRepo,
        ) as T
    }
}