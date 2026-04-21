package com.elv8.crisisos.domain.usecase

import android.util.Log
import com.elv8.crisisos.domain.repository.SosRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

class BroadcastSosUseCase @Inject constructor(
    private val sosRepository: SosRepository
) {
    suspend operator fun invoke(message: String, locationData: String): Boolean {
        // Stub: Logs broadcast, returns success
        Log.d("BroadcastSosUseCase", "Broadcasting SOS over Nearby Connections DTN. Msg: \$message | Loc: \$locationData")
        
        // Simulating the actual broadcast delay
        delay(1000)
        
        return true
    }
}