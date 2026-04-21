import os
import re

file_path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    text = f.read()

# Fix the end of AttachmentPreviewSheet
text = re.sub(
    r'(onDiscard = \{ viewModel\.discardPendingAttachment\(\) \}\n\s*\)\n\n\s*)\}\n\s*\}\n\s*\}',
    r'\1}\n}',
    text
)

# Fix the end of MessageBubble's 'when' statement
# It currently has:
#                 }
#             }
#                         }
#                     }
#                 }
#             }
#
#
#             Text(
#                 text = formatter.format...

text = re.sub(
    r'(\s*\}\n\s*\}\n\s*\}\n\s*\}\n)\s*\}\n\s*\}(\n\s*Text\(\n\s*text = formatter\.format\(Date\(message\.timestamp\)\),)',
    r'\1\2',
    text
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(text)

print("Done")
