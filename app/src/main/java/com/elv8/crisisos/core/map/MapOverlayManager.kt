package com.elv8.crisisos.core.map

import android.content.Context
import android.util.Log
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType
import com.elv8.crisisos.domain.model.ZoneStatus
import com.elv8.crisisos.domain.model.status
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

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

    fun addOrUpdateMarker(marker: Marker, invalidate: Boolean = true) {
        val existing = overlayRegistry[marker.id]
        if (existing != null) {
            mapView.overlays.remove(existing)
        }
        mapView.overlays.add(marker)
        overlayRegistry[marker.id] = marker
        if (invalidate) mapView.invalidate()
    }

    fun addOrUpdateOverlay(id: String, overlay: Overlay, invalidate: Boolean = true) {
        val existing = overlayRegistry[id]
        if (existing != null) {
            mapView.overlays.remove(existing)
        }
        mapView.overlays.add(overlay)
        overlayRegistry[id] = overlay
        if (invalidate) mapView.invalidate()
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

    /**
     * Replace ALL safe-zone markers in one call. Diff-style update is not worth the
     * complexity for typical zone-set sizes (< 200 pins).
     */
    fun setSafeZones(
        zones: List<SafeZone>,
        typeColorOf: (SafeZoneType) -> Int,
        statusColorOf: (ZoneStatus) -> Int,
        onTap: (SafeZone) -> Unit
    ) {
        clearOverlaysWithPrefix("SAFE_")
        zones.forEach { zone ->
            val marker = MarkerFactory.createSafeZoneMarker(
                context = context,
                mapView = mapView,
                geoPoint = GeoPoint(zone.coordinates.first, zone.coordinates.second),
                name = zone.name,
                zoneId = zone.id,
                typeColor = typeColorOf(zone.type),
                statusColor = statusColorOf(zone.status()),
                onTap = { onTap(zone) }
            )
            // Suppress per-marker invalidate; we'll fire a single one at the end.
            addOrUpdateMarker(marker, invalidate = false)
        }
        if (zones.isNotEmpty()) mapView.invalidate()
        Log.d("CrisisOS_Map", "setSafeZones: rendered ${zones.size} pins")
    }

    /**
     * Draw or replace a polyline route. Used for the "you → nearest open safe zone" line.
     */
    fun drawRoute(id: String, points: List<GeoPoint>, colorInt: Int, widthPx: Float = 10f) {
        if (points.size < 2) {
            removeOverlay(id)
            return
        }
        val polyline = Polyline().apply {
            outlinePaint.color = colorInt
            outlinePaint.strokeWidth = widthPx
            outlinePaint.isAntiAlias = true
            setPoints(points)
        }
        addOrUpdateOverlay(id, polyline)
    }

    fun animateTo(geoPoint: GeoPoint, zoom: Double? = null) {
        if (zoom != null) {
            mapView.controller.animateTo(geoPoint, zoom, 600L)
        } else {
            mapView.controller.animateTo(geoPoint)
        }
    }
}
