package com.elv8.crisisos.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.domain.location.CrisisLocation
import com.elv8.crisisos.domain.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus
) : LocationRepository {

    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _currentLocation = MutableStateFlow<CrisisLocation?>(null)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val crisis = CrisisLocation(loc.latitude, loc.longitude, loc.accuracy, loc.time, loc.altitude)
            _currentLocation.value = crisis
            scope.launch { 
                eventBus.emit(AppEvent.SystemEvent.LocationUpdated(loc.latitude, loc.longitude)) 
            }
        }
    }

    override fun getCurrentLocation(): Flow<CrisisLocation?> = _currentLocation.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun startTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setMaxUpdateDelayMillis(60_000L)
            .build()
        
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): CrisisLocation? {
        return try {
            val loc = fusedClient.lastLocation.await() ?: return null
            CrisisLocation(loc.latitude, loc.longitude, loc.accuracy, loc.time, loc.altitude)
        } catch (e: Exception) {
            null
        }
    }
}
