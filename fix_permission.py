path = "app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    text = f.read()

# Add imports
text = text.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\nimport androidx.core.content.ContextCompat\nimport android.Manifest\nimport android.content.pm.PackageManager\nimport androidx.compose.ui.platform.LocalContext\nimport android.widget.Toast\n")

# Add launcher
top_decl = """val context = LocalContext.current
    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Toast.makeText(context, "Permission granted. Hold mic to record.", Toast.LENGTH_SHORT).show()
    }"""
text = text.replace("val imagePicker = rememberLauncherForActivityResult", top_decl + "\n    val imagePicker = rememberLauncherForActivityResult")

# Replace press
old_press = """viewModel.startVoiceRecording()
                                              tryAwaitRelease()
                                              viewModel.stopVoiceRecording()"""

new_press = """val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                              if (hasPerm) {
                                                  viewModel.startVoiceRecording()
                                                  tryAwaitRelease()
                                                  viewModel.stopVoiceRecording()
                                              } else {
                                                  recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                              }"""
text = text.replace(old_press, new_press)

with open(path, "w", encoding="utf-8") as f:
    f.write(text)
print("Permission added")
