import os
import re

# 1. AttachmentPreviewSheet.kt
file1 = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/AttachmentPreviewSheet.kt"
with open(file1, "r", encoding="utf-8") as f:
    text1 = f.read()

text1 = text1.replace("mediaType", "type")
with open(file1, "w", encoding="utf-8") as f:
    f.write(text1)

# 2. MediaMessageComposables.kt
file2 = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/MediaMessageComposables.kt"
with open(file2, "r", encoding="utf-8") as f:
    text2 = f.read()

# Delete the mock DeliveryStatusIcon at the end of MediaMessageComposables.kt
text2 = re.sub(
    r'@Composable\s*\nprivate\s*fun\s*DeliveryStatusIcon\s*\(\s*status:\s*MessageStatus\s*\)\s*\{[\s\S]*?\}\s*$',
    '',
    text2
)

with open(file2, "w", encoding="utf-8") as f:
    f.write(text2)

print("Fixed")
