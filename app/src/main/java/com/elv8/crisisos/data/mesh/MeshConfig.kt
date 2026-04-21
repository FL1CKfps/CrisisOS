package com.elv8.crisisos.data.mesh

import com.google.android.gms.nearby.connection.Strategy

object MeshConfig {
    const val SERVICE_ID = "com.elv8.crisisos.mesh"
    val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    const val MAX_PAYLOAD_SIZE = 32_768 // 32KB
    const val CONNECTION_TIMEOUT_MS = 15_000L
    const val DISCOVERY_TIMEOUT_MS = 30_000L
    const val MAX_CONNECTIONS = 8
    const val PING_INTERVAL_MS = 30_000L
}