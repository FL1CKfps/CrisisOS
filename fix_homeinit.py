import sys

filepath = 'app/src/main/java/com/elv8/crisisos/ui/screens/home/HomeViewModel.kt'
with open(filepath, 'r') as f:
    content = f.read()

target_init = """    init {
        viewModelScope.launch {
            // Fake initial scanning state, then connect after 3 seconds        
            delay(3000)
            _uiState.update {
                it.copy(
                    meshStatus = MeshStatus.CONNECTED,
                    peersNearby = 3,
                    lastSyncTime = "Just now"
                )
            }
        }
    }"""
result_init = """    @Inject
    lateinit var identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository

    init {
        viewModelScope.launch {
            // Fake initial scanning state, then connect after 3 seconds
            delay(3000)
            _uiState.update {
                it.copy(
                    meshStatus = MeshStatus.CONNECTED,
                    peersNearby = 3,
                    lastSyncTime = "Just now"
                )
            }
        }
        viewModelScope.launch {
            identityRepository.getIdentity()
                .kotlinx.coroutines.flow.filterNotNull()
                .kotlinx.coroutines.flow.first()
            android.util.Log.i("CrisisOS_Home", "Identity ready — starting mesh service from HomeViewModel")
            startMeshService()
        }
    }"""
# Wait fixing the imports
content = content.replace('import androidx.lifecycle.viewModelScope\n', 'import androidx.lifecycle.viewModelScope\nimport kotlinx.coroutines.flow.filterNotNull\nimport kotlinx.coroutines.flow.first\n')
result_init = """    @Inject lateinit var identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository\n\n    init {
        viewModelScope.launch {
            // Fake initial scanning state, then connect after 3 seconds        
            delay(3000)
            _uiState.update {
                it.copy(
                    meshStatus = MeshStatus.CONNECTED,
                    peersNearby = 3,
                    lastSyncTime = "Just now"
                )
            }
        }
        viewModelScope.launch {
            identityRepository.getIdentity()
                .filterNotNull()
                .first()
            android.util.Log.i("CrisisOS_Home", "Identity ready — starting mesh service from HomeViewModel")
            startMeshService()
        }
    }"""
content = content.replace(target_init, result_init)

target_start = """    fun startMeshService() {
        MeshForegroundService.start(context, "UNKNOWN_CRS", "HomeUser")
    }"""
result_start = """    fun startMeshService() {
        val prefs = context.getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        val alias = prefs.getString("user_alias", null)
        val crsId = prefs.getString("local_crs_id", null)
        
        if (alias == null || crsId == null) {
            android.util.Log.e("CrisisOS_Home", "Cannot start mesh — identity not set up")
            return
        }
        
        android.util.Log.i("CrisisOS_Home", "Starting mesh service — alias= crsId=")
        context.startForegroundService(MeshForegroundService.startIntent(context))
    }"""
content = content.replace(target_start, result_start)

with open(filepath, 'w') as f:
    f.write(content)
print("Updated HomeViewModel")
