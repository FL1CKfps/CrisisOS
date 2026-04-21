import re

path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    text = f.read()

pattern = r"""(\} else \{\s*)IconButton\(\s*modifier = Modifier[\s\S]*?onClick = \{\}\s*\)\s*\{\s*Icon\([\s\S]*?\}\s*\}"""

replacement = r"""\1Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) {
                                                viewModel.startVoiceRecording()
                                                try {
                                                    tryAwaitRelease()
                                                } finally {
                                                    viewModel.stopVoiceRecording()
                                                }
                                            } else {
                                                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
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
                        }
                    }"""

new_text = re.sub(pattern, replacement, text)

# also fix import issue just in case
if "import android.Manifest" not in new_text:
    new_text = new_text.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\nimport androidx.core.content.ContextCompat\nimport android.Manifest\nimport android.content.pm.PackageManager\nimport androidx.compose.ui.platform.LocalContext\nimport android.widget.Toast\n")


with open(path, "w", encoding="utf-8") as f:
    f.write(new_text)
print("Regex replacement success!")
