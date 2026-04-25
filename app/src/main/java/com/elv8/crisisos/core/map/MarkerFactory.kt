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
     * Current user location marker — orange dot with white border, drawn on top of everything.
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
        marker.icon = solidCircle(context, 0xFFFF3B00.toInt(), sizeDp = 22)
        marker.id = "USER_LOCATION"
        // Suppress the OSMDroid info window — we have a custom UI for that
        marker.setInfoWindow(null)
        return marker
    }

    /**
     * Safe zone marker — two-tone disc:
     *   - Inner fill = type color (camp / hospital / water / etc.)
     *   - Outer ring = status color (green=open, orange=near full, red=full/closed)
     *   - Optional tap callback opens detail sheet from the screen.
     */
    fun createSafeZoneMarker(
        context: Context,
        mapView: MapView,
        geoPoint: GeoPoint,
        name: String,
        zoneId: String,
        typeColor: Int,
        statusColor: Int,
        onTap: (() -> Unit)? = null
    ): Marker {
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = name
        marker.icon = twoToneCircle(context, fillColor = typeColor, ringColor = statusColor, sizeDp = 26)
        marker.id = "SAFE_$zoneId"
        marker.setInfoWindow(null)
        if (onTap != null) {
            marker.setOnMarkerClickListener { _, _ ->
                onTap.invoke()
                true
            }
        }
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
            "MEDIUM"           -> 0xFFEF9F27.toInt()
            else               -> 0xFFB4B2A9.toInt()
        }
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = "\u26A0 ${severity.uppercase()} DANGER"
        marker.icon = solidCircle(context, color, sizeDp = 20)
        marker.id = "DANGER_$zoneId"
        marker.setInfoWindow(null)
        return marker
    }

    // ---------- icon helpers ----------

    /** Solid filled circle with a thin white border. */
    private fun solidCircle(context: Context, colorInt: Int, sizeDp: Int): Drawable {
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = colorInt
        paint.style = Paint.Style.FILL
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 2f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Two-tone disc:
     *   1. Outer ring filled with [ringColor] (status indicator)
     *   2. Inner disc filled with [fillColor] (type indicator)
     *   3. Small white center dot for visual punch / readability over busy tiles
     */
    private fun twoToneCircle(
        context: Context,
        fillColor: Int,
        ringColor: Int,
        sizeDp: Int
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(12)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val ringWidthPx = 4f * density

        // 1. Outer ring (status color)
        paint.color = ringColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, sizePx / 2f, paint)

        // 2. Inner disc (type color)
        paint.color = fillColor
        canvas.drawCircle(cx, cy, sizePx / 2f - ringWidthPx, paint)

        // 3. White center pip for high-contrast readability
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, sizePx / 6f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
