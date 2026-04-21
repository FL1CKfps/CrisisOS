import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatListScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

OLD = '''        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(thread.avatarColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = thread.displayName.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )'''

NEW = '''        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            com.elv8.crisisos.ui.components.CrsAvatar(
                crsId = thread.id,
                alias = thread.displayName,
                avatarColor = thread.avatarColor,
                size = 48.dp
            )'''

c = c.replace(OLD, NEW)

c = re.sub(
    r'// Needs standard formatting.*?color = MaterialTheme\.colorScheme\.onSurfaceVariant\s*\)',
    'com.elv8.crisisos.ui.components.RelativeTimestamp(timestamp = thread.lastMessageTimestamp)',
    c,
    flags=re.DOTALL
)

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatListScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)

