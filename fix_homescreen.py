import re

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

imports = """import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.os.Build
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
"""
if 'import android.Manifest' not in text:
    text = text.replace('import androidx.compose.runtime.Composable\n', f'import androidx.compose.runtime.Composable\n{imports}')

snippet = """
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.needsNotificationPermission && Build.VERSION.SDK_INT >= 33) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) viewModel.onNotificationPermissionGranted()
            else viewModel.onNotificationPermissionDenied()
        }

        LaunchedEffect(Unit) {
            delay(2_000)  // delay to avoid asking immediately on app open
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
"""

if 'needsNotificationPermission' not in text:
    text = text.replace(
        'fun HomeScreen(viewModel: HomeViewModel = hiltViewModel(), onNavigate: (String) -> Unit) {',
        f'fun HomeScreen(viewModel: HomeViewModel = hiltViewModel(), onNavigate: (String) -> Unit) {{{snippet}}'
    )

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)
