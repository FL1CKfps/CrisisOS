import re
with open("app/src/main/java/com/elv8/crisisos/ui/screens/contacts/ContactsScreen.kt", "r", encoding="utf-8") as f:
    text = f.read()

pattern = r"[\s]*Box\(\s*modifier = Modifier\s*\.size\(40\.dp\)\s*\.clip\(CircleShape\)\s*\.background\(Color\(contact\.avatarColor\)\),\s*contentAlignment = Alignment\.Center\s*\)\s*\{\s*Text\(contact\.alias\.take\(1\)\.uppercase\(\),\s*color = Color\.White,\s*style = MaterialTheme\.typography\.titleMedium\)\s*\}"

replacement = """
              com.elv8.crisisos.ui.components.CrsAvatar(
                  crsId = contact.crsId,
                  alias = contact.alias,
                  avatarColor = contact.avatarColor,
                  size = 40.dp
              )"""

new_text = re.sub(pattern, replacement, text)
with open("app/src/main/java/com/elv8/crisisos/ui/screens/contacts/ContactsScreen.kt", "w", encoding="utf-8") as f:
    f.write(new_text)

