import sys

filepath = 'app/src/main/java/com/elv8/crisisos/ui/screens/home/HomeViewModel.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Add injection
target_inject = """class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {"""
replace_inject = """class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recoveryManager: com.elv8.crisisos.core.recovery.MeshRecoveryManager
) : ViewModel() {"""
content = content.replace(target_inject, replace_inject)

# Add restartMesh and state
target_methods = """    fun stopMeshService() {
        MeshForegroundService.stop(context)
    }
}"""
replace_methods = """    fun stopMeshService() {
        MeshForegroundService.stop(context)
    }

    val recoveryState = recoveryManager.recoveryState

    fun restartMesh() {
        recoveryManager.triggerManualRecovery()
    }
}"""
content = content.replace(target_methods, replace_methods)

with open(filepath, 'w') as f:
    f.write(content)
print("Updated HomeViewModel")
