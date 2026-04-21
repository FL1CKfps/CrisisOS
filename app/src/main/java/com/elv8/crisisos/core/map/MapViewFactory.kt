package com.elv8.crisisos.core.map

import android.content.Context
import android.util.Log
import android.view.View
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * Factory that builds and configures a MapView for reuse across screens.
 * Uses OpenStreetMap (MAPNIK) tiles — no API key required.
 */
object MapViewFactory {

    fun create(context: Context): MapView {
        val mapView = MapView(context)

        // Use OpenStreetMap as the default tile source (works offline with cache)
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        // Enable multi-touch gestures
        mapView.setMultiTouchControls(true)

        // Disable built-in zoom buttons (use pinch gesture only — cleaner UI)
        mapView.zoomController.setVisibility(
            CustomZoomButtonsController.Visibility.NEVER
        )

        // Set zoom bounds
        mapView.minZoomLevel = MapConfiguration.MIN_ZOOM
        mapView.maxZoomLevel = MapConfiguration.MAX_ZOOM

        // Enable hardware acceleration for smooth tile rendering
        mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Initial zoom and center
        mapView.controller.setZoom(MapConfiguration.DEFAULT_ZOOM)
        mapView.controller.setCenter(
            GeoPoint(
                MapConfiguration.DEFAULT_LATITUDE,
                MapConfiguration.DEFAULT_LONGITUDE
            )
        )

        Log.d("CrisisOS_Map", "MapView created and configured")
        return mapView
    }
}
