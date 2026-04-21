import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\MainActivity.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Add mainHandler
if 'private val mainHandler' not in text:
    text = text.replace(
        'class MainActivity : ComponentActivity() {\n',
        'import android.content.Intent\nimport kotlinx.coroutines.delay\n\nclass MainActivity : ComponentActivity() {\n    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())\n'
    )

# Add onNewIntent
if 'override fun onNewIntent' not in text:
    text = text.replace(
        '    override fun onCreate(savedInstanceState: Bundle?) {',
        '    override fun onNewIntent(intent: Intent?) {\n        super.onNewIntent(intent)\n        setIntent(intent)\n    }\n\n    override fun onCreate(savedInstanceState: Bundle?) {'
    )

target_block = '''                val navController = rememberNavController()

                fun handleNotificationIntent(intent: Intent?) {
                    val destination = intent?.getStringExtra("navigate_to") ?: return
                    android.util.Log.d("CrisisOS_Main", "Notification deep-link: navigate_to=$destination")
                    navController.currentBackStackEntry?.let {
                        try {
                            navController.navigate(destination) {
                                launchSingleTop = true
                                restoreState = true
                            }
                            android.util.Log.d("CrisisOS_Main", "Navigated to: $destination")
                        } catch (e: Exception) {
                            android.util.Log.w("CrisisOS_Main", "Navigation failed for $destination: ${e.message}")
                        }
                    }
                }

                androidx.compose.runtime.LaunchedEffect(intent) {
                    kotlinx.coroutines.delay(400)
                    handleNotificationIntent(intent)
                }
'''
if 'fun handleNotificationIntent' not in text:
    text = text.replace(
        '                val navController = rememberNavController()',
        target_block
    )

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(text)
