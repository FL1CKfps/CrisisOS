import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

btn_code = """                    RecentActivityList()
                    
                    // Added for mock testing
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { viewModel.triggerMockNotifications() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text("Trigger Mock Notifications")
                    }"""

if 'triggerMockNotifications' not in text:
    text = text.replace(
        '                    RecentActivityList()',
        btn_code
    )

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
