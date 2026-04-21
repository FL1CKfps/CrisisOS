import re

path = 'app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update signature of MessageBubble:
text = text.replace(
    'onToggleAudioPlayback: (String) -> Unit = {}',
    'onToggleAudioPlayback: (String) -> Unit = {},\n    playingAudioId: String? = null'
)

# 2. Update AudioMessageBubble call inside MessageBubble:
# We need to find AudioMessageBubble down below
audio_bubble_call_old = r'''                    AudioMessageBubble\(
                        mediaId = message\.mediaId \?\: "",
                        durationMs = message\.mediaDurationMs,
                        isOwn = message\.isOwn,
                        status = message\.status,
                        timestamp = message\.timestamp,
                        onTapPlay = \{ id -> onToggleAudioPlayback\(id\) \}
                    \)'''

audio_bubble_call_new = r'''                    AudioMessageBubble(
                        mediaId = message.mediaId ?: "",
                        durationMs = message.mediaDurationMs,
                        isOwn = message.isOwn,
                        status = message.status,
                        timestamp = message.timestamp,
                        isPlaying = playingAudioId == message.mediaId,
                        onTapPlay = { id -> onToggleAudioPlayback(id) }
                    )'''
text = re.sub(audio_bubble_call_old, audio_bubble_call_new, text)

# 3. Update the call to MessageBubble in MessageList
# Let's search for how MessageBubble is called.
# It might look like:
# MessageBubble(
#     message = message,
#     localCrsId = localCrsId,
#     isGroup = isGroup,
#     repliedMessage = repliedMessage,
#     onLongPress = { selectedMessage = message },
#     onNavigateToFullscreenMedia = onNavigateToFullscreenMedia,
#     onToggleAudioPlayback = { id -> viewModel.toggleAudioPlayback(id) }
# )

message_bubble_caller = r'onToggleAudioPlayback = \{ id -> viewModel\.toggleAudioPlayback\(id\) \}\s*\)'
message_bubble_caller_new = r'onToggleAudioPlayback = { id -> viewModel.toggleAudioPlayback(id) },\n                    playingAudioId = uiState.playingAudioId\n                )'
text = re.sub(message_bubble_caller, message_bubble_caller_new, text)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
