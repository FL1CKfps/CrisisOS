import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\MainActivity.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(
'''@AndroidEntryPoint
import android.content.Intent
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {''',
'''import android.content.Intent
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {'''
)

text = text.replace(
'''    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }''',
'''    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }'''
)

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(text)
