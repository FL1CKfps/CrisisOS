import re

path = 'app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Fix redundant AttachmentPreviewSheet
text = re.sub(r'(\s*AttachmentPreviewSheet\([\s\S]*?onDiscard = \{[^\n]*\}\s*\)\s*\}\s*)+', r'\n\n    AttachmentPreviewSheet(\n        attachment = uiState.pendingAttachment,\n        isVisible = uiState.showAttachmentPreview,\n        isSending = uiState.isSendingMedia,\n        onConfirmSend = { viewModel.sendPendingMediaMessage() },\n        onDiscard = { viewModel.discardPendingAttachment() }\n    )\n}\n\n', text)

# Fix redundant when message.messageType inside else
duplicate_else = r'else -> \{\s*when \(message\.messageType\) \{[\s\S]*?else -> \{\s*Box\('
text = re.sub(r'else -> \{\s*when \(message\.messageType\).*?else -> \{\s*Box\(', r'else -> {\n                    Box(', text, flags=re.DOTALL)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

