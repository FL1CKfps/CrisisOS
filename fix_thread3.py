import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

c = c.replace('androidx.compose.animation.AnimatedVisibility', 'AnimatedVisibility')

DELIVERY_STATUS = '''
@Composable
fun DeliveryStatusIcon(status: MessageStatus) {
    val iconSize = 12.dp
    val tintMuted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val tintRead = androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange

    androidx.compose.animation.Crossfade(targetState = status, label = "") { currentStatus ->
        when (currentStatus) {
            MessageStatus.SENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 1.5.dp,
                    color = tintMuted
                )
            }
            MessageStatus.SENT -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Sent",
                    modifier = Modifier.size(iconSize),
                    tint = tintMuted
                )
            }
            MessageStatus.DELIVERED -> {
                Row {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = tintMuted
                    )
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize).offset(x = (-4).dp),
                        tint = tintMuted
                    )
                }
            }
            MessageStatus.READ -> {
                Row {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = tintRead
                    )
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize).offset(x = (-4).dp),
                        tint = tintRead
                    )
                }
            }
            MessageStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Failed",
                    modifier = Modifier.size(iconSize),
                    tint = androidx.compose.ui.graphics.Color.Red
                )
            }
        }
    }
}
'''
if 'fun DeliveryStatusIcon' not in c:
    c += DELIVERY_STATUS

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)
