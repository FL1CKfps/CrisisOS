import re

def fix_view_model():
    path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadViewModel.kt"
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()

    # Replacement for Image
    old_img_success = r"""is MediaPickResult\.Success\s*->\s*\{\s*_uiState\.update\s*\{\s*it\.copy\(\s*isPickingMedia\s*=\s*false,\s*pendingAttachment\s*=\s*MediaAttachment\(\s*mediaItem\s*=\s*result\.mediaItem,\s*previewUri\s*=\s*result\.mediaItem\.localUri\s*\?:\s*"",\s*isReady\s*=\s*true\s*\),\s*showAttachmentPreview\s*=\s*true\s*\)\s*\}\s*\}"""
    new_success = """is MediaPickResult.Success -> {
                    _uiState.update { it.copy(isPickingMedia = false, isSendingMedia = true) }
                    val sendResult = threadChatRepository.sendMediaMessage(
                        threadId = tId,
                        mediaItem = result.mediaItem
                    )
                    _uiState.update { it.copy(
                        isSendingMedia = false,
                        mediaErrorMessage = if (sendResult is SendMessageResult.Error) sendResult.reason else null
                    )}
                }"""
    
    src = re.sub(old_img_success, new_success, src)

    # Replacement for Video
    old_vid_success = r"""is MediaPickResult\.Success\s*->\s*\{\s*_uiState\.update\s*\{\s*it\.copy\(\s*isPickingMedia\s*=\s*false,\s*pendingAttachment\s*=\s*MediaAttachment\(\s*mediaItem\s*=\s*result\.mediaItem,\s*previewUri\s*=\s*result\.mediaItem\.thumbnailUri\s*\?:\s*result\.mediaItem\.localUri\s*\?:\s*"",\s*isReady\s*=\s*true\s*\),\s*showAttachmentPreview\s*=\s*true\s*\)\s*\}\s*\}"""
    src = re.sub(old_vid_success, new_success, src)

    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("Fixed ViewModel")

def fix_screen():
    path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt"
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()

    # The mic button replacement
    old_mic = """IconButton(
                              modifier = Modifier
                                  .size(44.dp)
                                  .background(
                                      color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                      shape = CircleShape
                                  )
                                  .pointerInput(Unit) {
                                      detectTapGestures(
                                          onPress = {
                                              viewModel.startVoiceRecording()
                                              tryAwaitRelease()
                                              viewModel.stopVoiceRecording()
                                          }
                                      )
                                  },
                              onClick = {}
                          ) {
                              Icon(
                                  imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                  contentDescription = if (uiState.isRecording) "Stop recording" else "Hold to record",
                                  tint = Color.White
                              )
                          }"""

    new_mic = """Box(
                              modifier = Modifier
                                  .size(44.dp)
                                  .background(
                                      color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                      shape = CircleShape
                                  )
                                  .clip(CircleShape)
                                  .pointerInput(Unit) {
                                      detectTapGestures(
                                          onPress = {
                                              viewModel.startVoiceRecording()
                                              tryAwaitRelease()
                                              viewModel.stopVoiceRecording()
                                          }
                                      )
                                  },
                              contentAlignment = Alignment.Center
                          ) {
                              Icon(
                                  imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                  contentDescription = if (uiState.isRecording) "Stop recording" else "Hold to record",
                                  tint = Color.White
                              )
                          }"""
    # handle newlines properly across platforms
    src = src.replace(old_mic.replace("\r\n", "\n"), new_mic.replace("\r\n", "\n"))
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("Fixed Screen")

def fix_repository():
    path = "app/src/main/java/com/elv8/crisisos/data/repository/ThreadChatRepositoryImpl.kt"
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()

    old_db = """mediaThumbnailUri = mediaItem.thumbnailUri,"""
    new_db = """mediaThumbnailUri = mediaItem.thumbnailUri ?: mediaItem.localUri,"""
    
    src = src.replace(old_db, new_db)
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("Fixed Repository")

if __name__ == "__main__":
    fix_view_model()
    fix_screen()
    fix_repository()
