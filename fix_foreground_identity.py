import sys

filepath = 'app/src/main/java/com/elv8/crisisos/service/MeshForegroundService.kt'
with open(filepath, 'r') as f:
    content = f.read()

target_startmesh = """        serviceScope.launch {
            try {
                val identity = identityRepository.getIdentity().first()
                val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)

                val alias = identity?.alias ?: "Survivor_"
                val crsId = identity?.crsId ?: deviceId

                android.util.Log.i("CrisisOS_Service", "startMesh() - alias= deviceId=")
                messenger.setLocalDeviceId(deviceId)

                connectionManager.setLocalCrsId(crsId)
                connectionManager.startMesh(alias)
                recoveryManager.startMonitoring()
                Log.d("CrisisOS_Service", "Recovery manager started")

                meshHealthMonitor.runHealthCheck()
                startPingLoop()
                updateNotification("Mesh active - searching for peers")
            } catch (e: Exception) {
                android.util.Log.e("CrisisOS_Service", "Error reading identity", e)
            }
        }"""
replace_startmesh = """        val prefs = getSharedPreferences("crisisos_prefs", Context.MODE_PRIVATE)
        val alias = prefs.getString("user_alias", null)
        val crsId = prefs.getString("local_crs_id", null)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        if (alias == null || crsId == null) {
            Log.e("CrisisOS_Service", "startMesh() ABORTED — identity not yet set up. alias= crsId=")
            updateNotification("Open app to complete setup")
            return
        }

        Log.i("CrisisOS_Service", "startMesh() — alias= crsId= deviceId=")

        messenger.setLocalDeviceId(deviceId)
        connectionManager.setLocalCrsId(crsId)
        connectionManager.startMesh(alias)
        recoveryManager.startMonitoring()
        Log.d("CrisisOS_Service", "Recovery manager started")

        meshHealthMonitor.runHealthCheck()
        startPingLoop()
        updateNotification("Mesh active - searching for peers")"""
content = content.replace(target_startmesh, replace_startmesh)

comment_block = """/*
  TWO-DEVICE TESTING PROTOCOL — CrisisOS Mesh
  =============================================
  
  DEVICE SETUP:
  1. Install APK on both devices
  2. Open app on both — complete identity setup (alias + CRS-ID generated)
  3. Grant ALL permissions when prompted on both devices
  4. Ensure Bluetooth is ON on both devices
  5. Ensure WiFi is ON on both devices (Nearby uses WiFi Direct + BLE)
  6. Ensure Location is ON on both (required for Nearby on Android)
  
  EXPECTED LOGCAT SEQUENCE (filter: CrisisOS_*):
  
  Device A:                           Device B:
  [Service] startMesh()               [Service] startMesh()
  [Mesh] startAdvertising() SUCCESS   [Mesh] startAdvertising() SUCCESS
  [Mesh] startDiscovery() SUCCESS     [Mesh] startDiscovery() SUCCESS
  [Mesh] onEndpointFound ...          [Mesh] onEndpointFound ...
  [Mesh] requestConnection() sent     (receives connection initiated)
  [Mesh] onConnectionInitiated        [Mesh] onConnectionInitiated
  [Mesh] acceptConnection() submitted [Mesh] acceptConnection() submitted
  [Mesh] onConnectionResult SUCCESS   [Mesh] onConnectionResult SUCCESS
  [Room] Peer inserted/updated        [Room] Peer inserted/updated
  [Discovery] ViewModel received 1 peer [Discovery] ViewModel received 1 peer
  
  IF ADVERTISING FAILS:
  - Check: Bluetooth adapter enabled?
  - Check: BLUETOOTH_ADVERTISE permission granted?
  - Check: Another app using the same serviceId?
  
  IF DISCOVERY FAILS:
  - Check: Location permission granted? (silently required by Nearby)
  - Check: WiFi enabled?
  - ADB: adb logcat -s NearbyConnections  (Nearby's own logs)
  
  IF HANDSHAKE FAILS (status 8001):
  - Both devices already connected — safe to ignore
  
  IF HANDSHAKE FAILS (status 8003):
  - Connection rejected — check both devices are calling acceptConnection()
  
  IF PEERS INSERT TO ROOM BUT VIEWMODEL DOESN'T RECEIVE:
  - Check: Database using WAL mode?
  - Check: DAO returns Flow<> not suspend List<>?
  - Check: ViewModel collects in viewModelScope?
  
  ADB COMMANDS:
  adb logcat -s "CrisisOS_*"             — all CrisisOS logs
  adb logcat -s "NearbyConnections"       — Nearby internal logs
  adb shell am startservice -a com.elv8.crisisos.MESH_FORCE_RESTART ...
*/

"""
content = comment_block + content

target_oncreate = """        Log.d("CrisisOS_Service", "WakeLock acquired")
    }"""
replace_oncreate = """        Log.d("CrisisOS_Service", "WakeLock acquired")
        
        try {
            val nearbyVersion = com.google.android.gms.nearby.Nearby::class.java.package?.implementationVersion
            Log.i("CrisisOS_Service", "Nearby Connections version: ")
        } catch (e: Exception) {
            Log.d("CrisisOS_Service", "Could not read Nearby version: ")
        }
    }"""
content = content.replace(target_oncreate, replace_oncreate)

with open(filepath, 'w') as f:
    f.write(content)
print("Updated MeshForegroundService identity and docs")
