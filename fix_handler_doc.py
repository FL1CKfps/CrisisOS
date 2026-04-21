import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\core\notification\NotificationHandler.kt', 'r', encoding='utf-8') as f:
    text = f.read()

doc = """/*
  NOTIFICATION SYSTEM — COMPLETE EVENT FLOW
  ==========================================
  
  1. Feature event occurs (e.g. incoming mesh message)
  2. Repository/ViewModel emits NotificationEvent to NotificationEventBus
  3. NotificationHandler.processEvent() receives it
  4. Duplicate check via hash — skip if already shown
  5. DND check — skip if DND enabled (except SOS)
  6. Category check — skip if user disabled this type
  7. Suppression check — skip if user is on the active screen
  8. NotificationBuilder builds the Android Notification
  9. NotificationManagerWrapper.show() posts it to system
  10. NotificationLogDao logs the event (shown=true)
  11. User taps notification ? PendingIntent ? MainActivity.handleNotificationIntent()
  12. NavController navigates to correct screen
  
  OR for action buttons (Accept/Reject):
  11b. BroadcastReceiver fires ? NotificationActionReceiver.onReceive()
  12b. Repository method called directly (no screen needed)
  13b. Notification dismissed
  
  SUPPRESSION TRIGGERS:
  - Screen active: ChatThreadViewModel.init/onCleared
  - DND: NotificationSettings.isDndEnabled
  - Category disabled: NotificationSettings.isChatEnabled etc.
  - Duplicate: within-session hash dedup
  
  CANNOT BE SUPPRESSED:
  - SOS.IncomingAlert (always shows, even in DND)
*/
"""

if 'NOTIFICATION SYSTEM — COMPLETE EVENT FLOW' not in text:
    text = text.replace('class NotificationHandler', doc + '\n@Singleton\nclass NotificationHandler')

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\core\notification\NotificationHandler.kt', 'w', encoding='utf-8') as f:
    f.write(text)
