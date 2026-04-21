import re

with open("app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt", "r", encoding="utf-8") as f:
    text = f.read()

# Replace Step 4: Add startPeerHeartbeat after startDiscovery
text = re.sub(
    r'(        startDiscovery\(\)\n)',
    r'\1        startPeerHeartbeat()\n',
    text
)

# Replace Step 5: Add heartbeatJob?.cancel() before discoveryWatchdogJob?.cancel()
text = re.sub(
    r'(        discoveryWatchdogJob\?\.cancel\(\)\n)',
    r'        heartbeatJob?.cancel()\n\1',
    text
)

with open("app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt", "w", encoding="utf-8") as f:
    f.write(text)

print("Applied Regex replace for Steps 4, 5")
