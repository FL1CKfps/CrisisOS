import sys

filepath = 'app/src/main/java/com/elv8/crisisos/di/AppModule.kt'
with open(filepath, 'r') as f:
    content = f.read()

provides_target = """    @Provides
    @Singleton
    fun provideMeshDiagnostics("""
provides_replace = """    @Provides
    @Singleton
    fun provideMeshRecoveryManager(
        connectionManager: com.elv8.crisisos.data.mesh.MeshConnectionManager,
        permissionManager: com.elv8.crisisos.core.permissions.MeshPermissionManager,
        eventBus: com.elv8.crisisos.core.event.EventBus,
        scope: kotlinx.coroutines.CoroutineScope,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): com.elv8.crisisos.core.recovery.MeshRecoveryManager {
        return com.elv8.crisisos.core.recovery.MeshRecoveryManager(connectionManager, permissionManager, eventBus, scope, context)
    }

    @Provides
    @Singleton
    fun provideMeshDiagnostics("""

content = content.replace(provides_target, provides_replace)
with open(filepath, 'w') as f:
    f.write(content)
print("Updated AppModule")
