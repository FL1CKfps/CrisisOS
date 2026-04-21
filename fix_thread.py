import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

OLD = '''        if (!isOwn && isGroup) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Gray), // Fallback color
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.fromAlias.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }'''

NEW = '''        if (!isOwn && isGroup) {
            com.elv8.crisisos.ui.components.CrsAvatar(
                crsId = message.fromCrsId,
                alias = message.fromAlias,
                avatarColor = android.graphics.Color.GRAY,
                size = 28.dp
            )'''

c = c.replace(OLD, NEW)

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)

