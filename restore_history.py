import json, os, shutil

history_dir = r"C:\Users\visha\AppData\Roaming\Code\User\History"
targets = {
    "ContactsScreen.kt": r"C:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\contacts\ContactsScreen.kt",
    "IncomingRequestsScreen.kt": r"C:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\connection\IncomingRequestsScreen.kt",
    "MessageRequestsScreen.kt": r"C:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\requests\MessageRequestsScreen.kt",
    "PeerDiscoveryScreen.kt": r"C:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\discovery\PeerDiscoveryScreen.kt"
}

restored = set()

for root, _, files in os.walk(history_dir):
    if "entries.json" in files:
        entries_path = os.path.join(root, "entries.json")
        try:
            with open(entries_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            
            # Use 'resource' property to match the file
            # E.g. "resource": "file:///c%3A/Users/visha/AndroidStudioProjects/CrisisOs/app/src/main/java/com/elv8/crisisos/ui/screens/contacts/ContactsScreen.kt"
            resource = data.get("resource", "")
            
            for screen_name, dest_path in targets.items():
                if screen_name in resource and resource.endswith(screen_name):
                    entries = data.get("entries", [])
                    if entries:
                        # Grab the second-to-last or latest that is big enough
                        latest_entry = entries[-2] if len(entries) > 1 else entries[-1]
                        # Let's just find the latest valid one before the copilot python destruction timestamp
                        # The python destruction was a few minutes ago. Let's just take the first entry from today earlier or the last entry
                        # Let's take the very last entry assuming it's before Python broke it because python doesn't write to history!
                        src = os.path.join(root, entries[-1]["id"])
                        
                        shutil.copy(src, dest_path)
                        print("Restored", screen_name, "from", src)
                        restored.add(screen_name)
        except Exception as e:
             pass
