package com.elv8.crisisos.core.map

/**
 * Global map configuration constants for OSMDroid.
 * These defaults are used across all map screens in CrisisOS.
 */
object MapConfiguration {

    // Default map center — New Delhi, India (update to match deployment region)
    const val DEFAULT_LATITUDE = 28.6139
    const val DEFAULT_LONGITUDE = 77.2090
    const val DEFAULT_ZOOM = 15.0          // street-level — close enough to see nearby pins
    const val LOCATE_ME_ZOOM = 16.5        // tighter zoom when user taps "locate me"

    const val MIN_ZOOM = 5.0
    const val MAX_ZOOM = 19.0

    // Tile cache: how much disk space to allow for offline tiles (bytes)
    const val TILE_CACHE_MAX_BYTES = 500L * 1024L * 1024L  // 500 MB

    // User agent string — MUST be unique to your app, OSM will block generic agents
    const val OSM_USER_AGENT = "CrisisOS/1.0 (crisis-response-app)"

    // Cache folder name inside app's internal files dir
    const val TILE_CACHE_FOLDER = "osmdroid_tiles"

    // ID used for the "route to nearest open safe zone" polyline
    const val ROUTE_TO_NEAREST_ID = "ROUTE_TO_NEAREST"
}
