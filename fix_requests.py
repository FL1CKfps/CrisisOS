import re

# IncomingRequestsScreen.kt
with open("app/src/main/java/com/elv8/crisisos/ui/screens/connection/IncomingRequestsScreen.kt", "r", encoding="utf-8") as f:
    text1 = f.read()

pattern1 = r"[\s]*Box\(\s*modifier = Modifier\s*\.size\(56\.dp\)\s*\.clip\(CircleShape\)\s*\.background\(Color\(request\.fromAvatarColor\)\),\s*contentAlignment = Alignment\.Center\s*\)\s*\{\s*Text\(\s*text = request\.fromAlias\.take\(1\)\.uppercase\(\),\s*style = MaterialTheme\.typography\.titleLarge,\s*color = Color\.White\s*\)\s*\}"

replacement1 = """
                  com.elv8.crisisos.ui.components.CrsAvatar(
                      crsId = request.fromCrsId,
                      alias = request.fromAlias,
                      avatarColor = request.fromAvatarColor,
                      size = 56.dp
                  )"""
new_text1 = re.sub(pattern1, replacement1, text1)

with open("app/src/main/java/com/elv8/crisisos/ui/screens/connection/IncomingRequestsScreen.kt", "w", encoding="utf-8") as f:
    f.write(new_text1)

# MessageRequestsScreen.kt
with open("app/src/main/java/com/elv8/crisisos/ui/screens/requests/MessageRequestsScreen.kt", "r", encoding="utf-8") as f:
    text2 = f.read()

pattern2 = r"[\s]*Box\(\s*modifier = Modifier\s*\.size\(48\.dp\)\s*\.clip\(CircleShape\)\s*\.background\(Color\(request\.fromAvatarColor\)\),\s*contentAlignment = Alignment\.Center\s*\)\s*\{\s*Text\(\s*text = request\.fromAlias\.take\(1\)\.uppercase\(\),\s*style = MaterialTheme\.typography\.titleMedium,\s*color = Color\.White\s*\)\s*\}"

replacement2 = """
                      com.elv8.crisisos.ui.components.CrsAvatar(
                          crsId = request.fromCrsId,
                          alias = request.fromAlias,
                          avatarColor = request.fromAvatarColor,
                          size = 48.dp
                      )"""
new_text2 = re.sub(pattern2, replacement2, text2)

with open("app/src/main/java/com/elv8/crisisos/ui/screens/requests/MessageRequestsScreen.kt", "w", encoding="utf-8") as f:
    f.write(new_text2)

