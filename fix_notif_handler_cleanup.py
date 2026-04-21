import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\core\notification\NotificationHandler.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = re.sub(
    r'( +\blogShown\(event\)\n)+',
    r'        logShown(event)\n',
    text
)

text = re.sub(
    r'(\s+)logShown\(event\)\n(\s+)private fun logShown\(',
    r'\2private fun logShown(',
    text
)

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\core\notification\NotificationHandler.kt', 'w', encoding='utf-8') as f:
    f.write(text)
