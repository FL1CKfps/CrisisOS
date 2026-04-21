import os
import re

file_path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Fix braces before AttachmentPreviewSheet
content = re.sub(
    r'(\s*AttachmentPreviewSheet\([\s\S]*?onDiscard = \{ viewModel\.discardPendingAttachment\(\) \}\s*\)\s*)\}\s*\}\s*\}',
    r'\1}\n}',
    content
)

# Fix braces inside MessageBubble
content = re.sub(
    r'(\s*\}\s*\})\s*\}\s*\}\s*\}\s*\}(\s*Text\(\s*text = formatter\.format\(Date\(message\.timestamp\)\),)',
    r'\1\n            }\n        }\2',
    content
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Done")
