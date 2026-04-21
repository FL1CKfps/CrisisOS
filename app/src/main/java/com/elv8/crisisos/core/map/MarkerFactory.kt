package com.elv8.crisisos.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Factory that builds styled markers for different use cases.
 * All icons are generated programmatically — no asset files needed, works fully offline.
 */
object MarkerFactory {

    /**
     * Current user location marker — orange pulsing dot with white border.
     */
    fun createUserLocationMarker(
        context: Context,
        mapView: MapView,
        geoPoint: GeoPoint
    ): Marker {
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = "You are here"
        marker.icon = createCircleDrawable(context, 0xFFFF3B00.toInt(), 24)
        marker.id = "USER_LOCATION"
        return marker
    }

    /**
     * Safe zone center marker — green circle icon.
     */
    fun createSafeZoneMarker(
        context: Context,
        mapView: MapView,
        geoPoint: GeoPoint,
        name: String,
        zoneId: String
    ): Marker {
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = name
        marker.icon = createCircleDrawable(context, 0xFF1D9E75.toInt(), 20)
        marker.id = "SAFE_$zoneId"
        return marker
    }

    /**
     * Danger zone center marker — color coded by severity.
     */
    fun createDangerZoneMarker(
        context: Context,
        mapView: MapView,
        geoPoint: GeoPoint,
        severity: String,
        zoneId: String
    ): Marker {
        val color = when (severity.uppercase()) {
            "HIGH", "CRITICAL" -> 0xFFE24B4A.toInt()
            "MEDIUM" -> 0xFFEF9F27.toInt()
            else -> 0xFFB4B2A9.toInt()
        }
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = "\u26A0 ${severity.uppercase()} DANGER"
        marker.icon = createCircleDrawable(context, color, 20)
        marker.id = "DANGER_$zoneId"
        return marker
    }

    /**
     * Helper: create a solid colored circle Drawable programmatically.
     * No icon files needed — works fully offline.
     */
    private fun createCircleDrawable(
        context: Context,
        colorInt: Int,
        sizeDp: Int
    ): Drawable {
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Filled circle
        paint.color = colorInt
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // White border ring
        paint.color = 0xFFFFFFFF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 2f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
