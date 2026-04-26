package com.elv8.crisisos.core.debug

import com.elv8.crisisos.BuildConfig

object MeshDebugConfig {
    val ENABLE_MOCK_PEER_INJECTION: Boolean = BuildConfig.DEBUG
    const val MOCK_PEER_COUNT = 3
    const val MOCK_INJECT_DELAY_MS = 2_000L

    val VERBOSE_MESH_LOGGING: Boolean = BuildConfig.DEBUG

    val HYBRID_MODE: Boolean = BuildConfig.DEBUG

    val MOCK_CRS_IDS: Set<String> = setOf("CRS-DBG1-TEST", "CRS-DBG2-TEST", "CRS-DBG3-TEST")
    fun isMockCrsId(crsId: String): Boolean = crsId in MOCK_CRS_IDS || crsId.startsWith("CRS-DBG")
}
