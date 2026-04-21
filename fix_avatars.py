import os
import re

files = [
    {
        "path": "app/src/main/java/com/elv8/crisisos/ui/screens/discovery/PeerDiscoveryScreen.kt",
        "pattern": re.compile(r"Box\s*\([\s\S]*?peer\.avatarColor\)[\s\S]*?Alignment\.Center\s*\)\s*\{[\s\S]*?peer\.alias[\s\S]*?\}\s*\}", re.MULTILINE),
        "replacement": "com.elv8.crisisos.ui.components.CrsAvatar(\n                  alias = peer.alias,\n                  colorInt = peer.avatarColor,\n                  size = 40.dp\n              )"
    },
    {
        "path": "app/src/main/java/com/elv8/crisisos/ui/screens/contacts/ContactsScreen.kt",
        "pattern": re.compile(r"Box\s*\([\s\S]*?contact\.avatarColor\)[\s\S]*?Alignment\.Center\s*\)\s*\{[\s\S]*?contact\.alias[\s\S]*?\}\s*\}", re.MULTILINE),
        "replacement": "com.elv8.crisisos.ui.components.CrsAvatar(\n                  alias = contact.alias,\n                  colorInt = contact.avatarColor,\n                  size = 40.dp\n              )"
    },
    {
         "path": "app/src/main/java/com/elv8/crisisos/ui/screens/connection/IncomingRequestsScreen.kt",
         "pattern": re.compile(r"Box\s*\([\s\S]*?request\.fromAvatarColor\)[\s\S]*?Alignment\.Center\s*\)\s*\{[\s\S]*?request\.fromAlias[\s\S]*?\}\s*\}", re.MULTILINE),
         "replacement": "com.elv8.crisisos.ui.components.CrsAvatar(\n                      alias = request.fromAlias,\n                      colorInt = request.fromAvatarColor,\n                      size = 56.dp\n                  )"
    },
    {
         "path": "app/src/main/java/com/elv8/crisisos/ui/screens/requests/MessageRequestsScreen.kt",
         "pattern": re.compile(r"Box\s*\([\s\S]*?request\.fromAvatarColor\)[\s\S]*?Alignment\.Center\s*\)\s*\{[\s\S]*?request\.fromAlias[\s\S]*?\}\s*\}", re.MULTILINE),
         "replacement": "com.elv8.crisisos.ui.components.CrsAvatar(\n                          alias = request.fromAlias,\n                          colorInt = request.fromAvatarColor,\n                          size = 48.dp\n                      )"
    }
]

for t in files:
    if os.path.exists(t["path"]):
        with open(t["path"], "r", encoding="utf-8") as f:
            content = f.read()
        new_content = t["pattern"].sub(t["replacement"], content)
        if new_content != content:
            with open(t["path"], "w", encoding="utf-8") as f:
                f.write(new_content)
            print(f"Patched {t['path']}")

