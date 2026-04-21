import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\core\notification\NotificationHandler.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = re.sub(
    r'( +)val notifId = wrapper.getOrCreateNotificationId\(',
    r'\1logShown(event)\n\1val notifId = wrapper.getOrCreateNotificationId(',
    text
)

text = re.sub(
    r'(\s+)logSuppressed\(event, reason = "thread_active"\)\n(\s+)return\n(\s+)\}\n(\s+)logShown\(event\)',
    r'\1logSuppressed(event, reason = "thread_active")\n\2return\n\3}',
    text
)

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\core\notification\NotificationHandler.kt', 'w', encoding='utf-8') as f:
    f.write(text)
