import re

path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    text = f.read()

new_imports = """
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
"""
if "awaitEachGesture" not in text:
    text = text.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\n" + new_imports)

old_gesture = """                                .pointerInput(Unit) {
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
                                }"""

# Create the new gesture that doesn't instantly cancel on slop movement!
new_gesture = """                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitPointerEventScope { awaitFirstDown() }
                                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            viewModel.startVoiceRecording()
                                            
                                            // Actively wait for user to release finger (ignores accidental slips and draggings)
                                            awaitPointerEventScope {
                                                waitForUpOrCancellation()
                                            }
                                            
                                            viewModel.stopVoiceRecording()
                                        } else {
                                            down.consume()
                                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }"""

text = text.replace(old_gesture, new_gesture)

with open(path, "w", encoding="utf-8") as f:
    f.write(text)
print("Gesture fixed!")
