import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\work\OutboxRetryWorker.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(
    'import com.elv8.crisisos.domain.repository.OutboxRepository',
    'import com.elv8.crisisos.domain.repository.OutboxRepository\nimport com.elv8.crisisos.data.local.dao.NotificationLogDao'
)

text = text.replace(
    'private val messenger: MeshMessenger\n) : ',
    'private val messenger: MeshMessenger,\n    private val notificationLogDao: NotificationLogDao\n) : '
)

text = text.replace(
    'outboxRepository.purgeExpired()\n',
    'outboxRepository.purgeExpired()\n        \n        // Cleanup old notifications (older than 7 days)\n        notificationLogDao.deleteOlderThan(System.currentTimeMillis() - 7 * 86_400_000L)\n\n'
)

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\work\OutboxRetryWorker.kt', 'w', encoding='utf-8') as f:
    f.write(text)
