package com.elv8.crisisos.core.map

import android.content.Context
import android.util.Log
import com.elv8.crisisos.domain.model.AggregatedDangerZone
import com.elv8.crisisos.domain.model.DangerSource
import com.elv8.crisisos.domain.model.SafeZone
import com.elv8.crisisos.domain.model.SafeZoneType
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.model.ZoneStatus
import com.elv8.crisisos.domain.model.status
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
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

    fun addOrUpdateOverlay(
        id: String,
        overlay: Overlay,
        invalidate: Boolean = true,
        atBottom: Boolean = false
    ) {
        val existing = overlayRegistry[id]
        if (existing != null) {
            mapView.overlays.remove(existing)
        }
        // OSMDroid renders overlays in list order — index 0 first (bottom),
        // last item on top. Inserting at 0 forces the new overlay to sit
        // beneath everything else regardless of when this call lands.
        if (atBottom) {
            mapView.overlays.add(0, overlay)
        } else {
            mapView.overlays.add(overlay)
        }
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

    /**
     * Replace ALL danger-zone overlays in one call. Each zone is rendered as
     * a translucent filled circle whose colour and opacity reflect severity
     * and source (ACLED = darkest red, crowdsourced-confirmed = red,
     * crowdsourced-unverified = orange).
     *
     * Zones are added BEFORE safe-zone markers in the overlay list so the
     * pins always render on top of the circles.
     */
    fun setDangerZones(
        zones: List<AggregatedDangerZone>,
        onTap: ((AggregatedDangerZone) -> Unit)? = null
    ) {
        clearOverlaysWithPrefix("DANGER_")
        zones.forEach { z ->
            val (fillColor, strokeColor) = colorsFor(z)
            val polygon = Polygon().apply {
                points = Polygon.pointsAsCircle(GeoPoint(z.centerLat, z.centerLon), z.radiusMeters)
                fillPaint.color = fillColor
                outlinePaint.color = strokeColor
                outlinePaint.strokeWidth = 4f
                outlinePaint.isAntiAlias = true
                title = z.title
                snippet = z.description
            }
            if (onTap != null) {
                polygon.setOnClickListener { _, _, _ ->
                    onTap.invoke(z)
                    true
                }
            }
            // atBottom=true so danger circles always layer BELOW safe-zone
            // pins, the user-location marker, and the route polyline — even
            // if setDangerZones() runs after setSafeZones() (e.g. when an
            // ACLED sync resolves while pins are already on the map).
            addOrUpdateOverlay("DANGER_${z.id}", polygon, invalidate = false, atBottom = true)
        }
        if (zones.isNotEmpty()) mapView.invalidate()
        Log.d("CrisisOS_Map", "setDangerZones: rendered ${zones.size} circles")
    }

    private fun colorsFor(z: AggregatedDangerZone): Pair<Int, Int> {
        // ARGB: high alpha for fill so the circle reads even on busy tiles.
        return when {
            z.source == DangerSource.ACLED ->
                0x66E24B4A.toInt() to 0xFFB02A29.toInt() // darker red, semi-fill
            z.severity == ThreatLevel.CRITICAL || z.severity == ThreatLevel.HIGH ->
                0x55E24B4A.toInt() to 0xFFC23A39.toInt() // red
            else ->
                0x55EF9F27.toInt() to 0xFFC07A1C.toInt() // orange (unverified)
        }
    }

    fun animateTo(geoPoint: GeoPoint, zoom: Double? = null) {
        if (zoom != null) {
            mapView.controller.animateTo(geoPoint, zoom, 600L)
        } else {
            mapView.controller.animateTo(geoPoint)
        }
    }
}
