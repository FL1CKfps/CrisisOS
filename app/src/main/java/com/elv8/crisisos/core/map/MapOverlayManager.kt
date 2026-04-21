package com.elv8.crisisos.core.map

import android.content.Context
import android.util.Log
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/**
 * Single manager for all map overlays.
 * Tracks overlays by ID string to support add/update/remove without duplicates.
 */
class MapOverlayManager(
    private val context: Context,
    private val mapView: MapView
) {
    // All currently displayed overlays keyed by their ID string
    private val overlayRegistry: MutableMap<String, Overlay> = mutableMapOf()

    fun addOrUpdateMarker(marker: Marker) {
        val existing = overlayRegistry[marker.id]
        if (existing != null) {
            mapView.overlays.remove(existing)
        }
        mapView.overlays.add(marker)
        overlayRegistry[marker.id] = marker
        mapView.invalidate()
    }

    fun addOrUpdateOverlay(id: String, overlay: Overlay) {
        val existing = overlayRegistry[id]
        if (existing != null) {
            mapView.overlays.remove(existing)
        }
        mapView.overlays.add(overlay)
        overlayRegistry[id] = overlay
        mapView.invalidate()
    }

    fun removeOverlay(id: String) {
        val existing = overlayRegistry.remove(id)
        if (existing != null) {
            mapView.overlays.remove(existing)
            mapView.invalidate()
        }
    }

    fun clearOverlaysWithPrefix(prefix: String) {
        val toRemove = overlayRegistry.keys.filter { it.startsWith(prefix) }
        toRemove.forEach { key ->
            mapView.overlays.remove(overlayRegistry[key])
            overlayRegistry.remove(key)
        }
        if (toRemove.isNotEmpty()) {
            mapView.invalidate()
        }
    }

    fun updateUserLocation(geoPoint: GeoPoint) {
        val marker = MarkerFactory.createUserLocationMarker(context, mapView, geoPoint)
        addOrUpdateMarker(marker)
        Log.d("CrisisOS_Map", "User location updated: ${geoPoint.latitude}, ${geoPoint.longitude}")
    }

    fun animateTo(geoPoint: GeoPoint, zoom: Double? = null) {
        mapView.controller.animateTo(geoPoint)
        zoom?.let { mapView.controller.setZoom(it) }
    }
}
