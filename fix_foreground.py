import sys

filepath = 'app/src/main/java/com/elv8/crisisos/service/MeshForegroundService.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Add injection
content = content.replace(
    'lateinit var identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository',
    'lateinit var identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository\n\n    @Inject\n    lateinit var recoveryManager: com.elv8.crisisos.core.recovery.MeshRecoveryManager'
)

# Handle startMesh
startmesh_target = """                connectionManager.startMesh(alias)

                meshHealthMonitor.runHealthCheck()"""
startmesh_replacement = """                connectionManager.startMesh(alias)
                recoveryManager.startMonitoring()
                Log.d("CrisisOS_Service", "Recovery manager started")

                meshHealthMonitor.runHealthCheck()"""
content = content.replace(startmesh_target, startmesh_replacement)

# Handle stopMesh
stopmesh_target = """    private fun stopMesh() {
        connectionManager.stopAll()
    }"""
stopmesh_replacement = """    private fun stopMesh() {
        connectionManager.stopAll()
        Log.d("CrisisOS_Service", "stopMesh() — recovery manager will stop with scope")
    }"""
content = content.replace(stopmesh_target, stopmesh_replacement)

# Handle intent action
intent_target = """        when (action) {
            ACTION_START -> {"""
intent_replacement = """        when (action) {
            ACTION_FORCE_RESTART -> {
                Log.i("CrisisOS_Service", "Force restart received via intent")
                recoveryManager.triggerManualRecovery()
            }
            ACTION_START -> {"""
content = content.replace(intent_target, intent_replacement)

# Handle companion constant
companion_target = """        const val ACTION_START = "com.elv8.crisisos.MESH_START" """
companion_replacement = """        const val ACTION_FORCE_RESTART = "com.elv8.crisisos.MESH_FORCE_RESTART"\n        const val ACTION_START = "com.elv8.crisisos.MESH_START" """
content = content.replace(companion_target, companion_replacement)

with open(filepath, 'w') as f:
    f.write(content)
print("Updated MeshForegroundService")
