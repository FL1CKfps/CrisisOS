import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

OLD = '''            MessageStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Failed",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        }
    }
}

@Composable
fun TypingIndicatorRow()'''

NEW = '''            MessageStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Failed",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TypingIndicatorRow()'''

c = c.replace(OLD, NEW)
with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)

