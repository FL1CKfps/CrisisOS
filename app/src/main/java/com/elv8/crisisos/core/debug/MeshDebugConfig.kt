package com.elv8.crisisos.core.debug

object MeshDebugConfig {
    // Set to true during testing to inject mock peers alongside real ones
    // Set to false for production
    const val ENABLE_MOCK_PEER_INJECTION = true
    const val MOCK_PEER_COUNT = 3
    const val MOCK_INJECT_DELAY_MS = 2_000L   // inject mocks 2 seconds after discovery starts

    // Log level — set to true during debugging
    const val VERBOSE_MESH_LOGGING = true

    // If true, discovery UI shows real + mock peers merged
    const val HYBRID_MODE = true

    val MOCK_CRS_IDS: Set<String> = setOf("CRS-DBG1-TEST", "CRS-DBG2-TEST", "CRS-DBG3-TEST")
    fun isMockCrsId(crsId: String): Boolean = crsId in MOCK_CRS_IDS || crsId.startsWith("CRS-DBG")
}
