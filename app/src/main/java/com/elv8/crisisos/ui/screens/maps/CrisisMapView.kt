package com.elv8.crisisos.ui.screens.maps

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elv8.crisisos.core.map.MapViewFactory
import org.osmdroid.views.MapView

/**
 * Compose wrapper around OSMDroid's MapView.
 * Manages MapView lifecycle (resume/pause/detach) in sync with the Compose lifecycle.
 */
@Composable
fun CrisisMapView(
    modifier: Modifier = Modifier,
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create MapView once and remember it — do NOT recreate on recomposition
    val mapView = remember { MapViewFactory.create(context) }

    // Manage OSMDroid lifecycle with the Compose lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    Log.d("CrisisOS_Map", "MapView resumed")
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                    Log.d("CrisisOS_Map", "MapView paused")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
            Log.d("CrisisOS_Map", "MapView detached")
        }
    }

    AndroidView(
        factory = {
            mapView.also { onMapReady(it) }
        },
        modifier = modifier,
        update = { /* MapView manages its own updates — do not interfere */ }
    )
}
