import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Add needed imports
imports_to_add = """import android.os.Build
import android.util.Log
import com.elv8.crisisos.core.notification.NotificationSettings
import com.elv8.crisisos.core.notification.NotificationManagerWrapper
"""
if 'import android.os.Build' not in text:
    text = text.replace('import android.content.Context\n', f'import android.content.Context\n{imports_to_add}')

# Add to UiState
if 'val needsNotificationPermission: Boolean' not in text:
    text = text.replace(
        'val batteryOptimized: Boolean = true\n)',
        'val batteryOptimized: Boolean = true,\n    val needsNotificationPermission: Boolean = false\n)'
    )

# Add to constructor
if 'private val notifWrapper:' not in text:
    text = text.replace(
        'private val identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository\n',
        'private val identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository,\n    private val notificationSettings: NotificationSettings,\n    private val notifWrapper: NotificationManagerWrapper\n'
    )

# Add logic to init
init_logic = """        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = notifWrapper.hasNotificationPermission()
            _uiState.update { it.copy(needsNotificationPermission = !hasPermission) }
            if (!hasPermission) {
                Log.w("CrisisOS_Home", "POST_NOTIFICATIONS permission not granted")
            }
        }
"""
if 'hasNotificationPermission' not in text:
    text = text.replace(
        '    init {\n        viewModelScope.launch {',
        f'    init {{\n{init_logic}\n        viewModelScope.launch {{'
    )

methods = """
    fun onNotificationPermissionGranted() {
        _uiState.update { it.copy(needsNotificationPermission = false) }
        Log.i("CrisisOS_Home", "POST_NOTIFICATIONS granted")
    }

    fun onNotificationPermissionDenied() {
        Log.w("CrisisOS_Home", "POST_NOTIFICATIONS denied by user")
        _uiState.update { it.copy(needsNotificationPermission = false) }
    }
"""
if 'fun onNotificationPermissionGranted' not in text:
    text = text.replace('}\n}', f'}}\n{methods}\n}}')

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(text)
