import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

imports = """import com.elv8.crisisos.core.notification.NotificationEventBus
import com.elv8.crisisos.core.notification.event.NotificationEvent
import java.util.UUID
"""
if 'import java.util.UUID' not in text:
    text = text.replace('import androidx.lifecycle.ViewModel\n', f'{imports}import androidx.lifecycle.ViewModel\n')

if 'private val notificationEventBus: NotificationEventBus' not in text:
    text = text.replace(
        'private val notifWrapper: NotificationManagerWrapper\n',
        'private val notifWrapper: NotificationManagerWrapper,\n    private val notificationEventBus: NotificationEventBus\n'
    )

methods = """
    fun triggerMockNotifications() {
        viewModelScope.launch {
            val rId = UUID.randomId() ?: UUID.randomUUID().toString()
            
            // 1. Mock Chat Message
            notificationEventBus.emit(
                NotificationEvent.Chat.MessageReceived(
                    threadId = "thread_$rId",
                    fromCrsId = "crs_$rId",
                    fromAlias = "Test User $rId",
                    avatarColor = 0xFF5555,
                    messagePreview = "This is a mock mesh message to test notifications",
                    messageId = "msg_$rId",
                    timestamp = System.currentTimeMillis(),
                    isGroupChat = false,
                    groupName = null
                )
            )

            delay(1500)

            // 2. Mock Connection Request
            notificationEventBus.emit(
                NotificationEvent.Request.ConnectionRequestReceived(
                    requestId = "req_$rId",
                    fromCrsId = "crs2_$rId",
                    fromAlias = "Stranger $rId",
                    fromAvatarColor = 0x55FF55,
                    introMessage = "Hi, I am nearby and need connection."
                )
            )

            delay(1500)

            // 3. Mock SOS
            notificationEventBus.emit(
                NotificationEvent.Sos.IncomingAlert(
                    alertId = "sos_$rId",
                    fromCrsId = "sos_crs_$rId",
                    fromAlias = "Victim $rId",
                    sosType = "MEDICAL",
                    message = "Need immediate assistance",
                    locationHint = "Main Street corner",
                    hopsAway = 2
                )
            )
            
            delay(1500)
            
            // 4. Mock Supply AcK
            notificationEventBus.emit(
                NotificationEvent.Supply.RequestAcknowledged(
                    requestId = "sup_$rId",
                    supplyType = "FOOD",
                    ngoAlias = "Local Rescue NGO",
                    estimatedEta = "30 mins"
                )
            )
        }
    }
"""

if 'fun triggerMockNotifications' not in text:
    text = text.replace('private fun UUID.randomId()', '') # in case it's there
    text = text.replace('}\n}', f'}}\n{methods}\n}}')
    text = text.replace('UUID.randomId() ?: ', '') # simplify

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(text)
