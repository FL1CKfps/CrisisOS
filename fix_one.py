import re
with open("app/src/main/java/com/elv8/crisisos/ui/screens/discovery/PeerDiscoveryScreen.kt", "r", encoding="utf-8") as f:
    text = f.read()

pattern = r"[\s]*Box\(\s*modifier = Modifier\s*\.size\(40\.dp\)\s*\.clip\(CircleShape\)\s*\.background\(Color\(peer\.avatarColor\)\),\s*contentAlignment = Alignment\.Center\s*\)\s*\{\s*Text\(\s*text = peer\.alias\.take\(1\)\.uppercase\(\),\s*color = Color\.White,\s*style = MaterialTheme\.typography\.titleMedium\s*\)\s*\}"

replacement = """
              com.elv8.crisisos.ui.components.CrsAvatar(
                  crsId = peer.crsId,
                  alias = peer.alias,
                  avatarColor = peer.avatarColor,
                  size = 40.dp
              )"""

new_text = re.sub(pattern, replacement, text)
with open("app/src/main/java/com/elv8/crisisos/ui/screens/discovery/PeerDiscoveryScreen.kt", "w", encoding="utf-8") as f:
    f.write(new_text)

