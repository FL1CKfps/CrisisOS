package com.elv8.crisisos

import com.elv8.crisisos.core.debug.MeshLogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elv8.crisisos.ui.components.AppScaffold
import com.elv8.crisisos.ui.components.LocalTopBarState
import com.elv8.crisisos.ui.components.TopBarState
import com.elv8.crisisos.ui.navigation.CrisisNavGraph
import com.elv8.crisisos.ui.navigation.Screen
import com.elv8.crisisos.ui.theme.CrisisOSTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.elv8.crisisos.ui.screens.onboarding.OnboardingViewModel

import android.content.Intent
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            CrisisOSTheme {
                val isOnboarded by onboardingViewModel.onboarded.collectAsState()
                val topBarState = remember { TopBarState() }

                if (isOnboarded == null) {
                    return@CrisisOSTheme
                }

                androidx.compose.runtime.LaunchedEffect(isOnboarded) {
                    if (isOnboarded == true) {
                        try {
                            val intent = com.elv8.crisisos.service.MeshForegroundService.startIntent(this@MainActivity)
                            androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, intent)
                        } catch (e: Exception) {
                            android.util.Log.e("CrisisOS_Main", "Failed to start service: ${e.message}")
                        }
                    }
                }

                val navController = rememberNavController()

                fun handleNotificationIntent(intent: Intent?) {
                    val destination = intent?.getStringExtra("navigate_to") ?: return
                    navController.currentBackStackEntry?.let {
                        try {
                            navController.navigate(destination) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        } catch (e: Exception) { }
                    }
                }

                androidx.compose.runtime.LaunchedEffect(intent) {
                    kotlinx.coroutines.delay(400)
                    handleNotificationIntent(intent)
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                CompositionLocalProvider(LocalTopBarState provides topBarState) {
                    AppScaffold(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    ) {
                        CrisisNavGraph(
                            navController = navController,
                            startDestination = if (isOnboarded == true) Screen.Home.route else Screen.Onboarding.route,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CrisisOSTheme {
        Greeting("Android")
    }
}
