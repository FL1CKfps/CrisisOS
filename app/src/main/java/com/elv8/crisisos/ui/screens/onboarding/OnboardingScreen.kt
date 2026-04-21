package com.elv8.crisisos.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    var alias by remember { mutableStateOf("") }
    
    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Skip button
            if (pagerState.currentPage < 3) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(3)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PageOnePurpose(
                        onNext = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        }
                    )
                    1 -> PageTwoHowItWorks(
                        onNext = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        }
                    )
                    2 -> PageThreePermissions(
                        onGrant = {
                            permissionsState.launchMultiplePermissionRequest()
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        },
                        onSkip = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )
                    3 -> PageFourIdentity(
                        alias = alias,
                        onAliasChange = { if (it.length <= 24) alias = it },
                        onStart = {
                            viewModel.completeOnboarding(alias)
                            onFinish()
                        }
                    )
                }
            }

            // Pager Indicator
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                            .background(
                                color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun PageOnePurpose(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CRISIS",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "OS",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFF9800).copy(alpha = pulse)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Built for the worst day of your life.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Works with zero internet. Powered by Bluetooth mesh. Designed for survival.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("NEXT", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PageTwoHowItWorks(onNext: () -> Unit) {
    var stepOneVisible by remember { mutableStateOf(false) }
    var stepTwoVisible by remember { mutableStateOf(false) }
    var stepThreeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        stepOneVisible = true
        delay(800)
        stepTwoVisible = true
        delay(800)
        stepThreeVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "HOW IT WORKS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        AnimatedVisibility(visible = stepOneVisible, enter = fadeIn() + slideInVertically { 20 }) {
            StepItem(
                icon = Icons.Default.PhoneAndroid,
                text = "Install on any Android 8+ device"
            )
        }
        
        AnimatedVisibility(visible = stepTwoVisible, enter = fadeIn() + slideInVertically { 20 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                StepItem(
                    icon = Icons.Default.Bluetooth,
                    text = "Devices mesh together automatically"
                )
            }
        }
        
        AnimatedVisibility(visible = stepThreeVisible, enter = fadeIn() + slideInVertically { 20 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                StepItem(
                    icon = Icons.Default.Shield,
                    text = "12 survival features � all offline"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        AnimatedVisibility(visible = stepThreeVisible, enter = fadeIn()) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("NEXT", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StepItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PageThreePermissions(onGrant: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PERMISSIONS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        PermissionRow(
            icon = Icons.Default.LocationOn,
            title = "Location",
            description = "Required for safe zone proximity"
        )
        PermissionRow(
            icon = Icons.Default.Bluetooth,
            title = "Bluetooth",
            description = "Powers the mesh network"
        )
        PermissionRow(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            description = "Receive SOS alerts"
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("GRANT PERMISSIONS", fontWeight = FontWeight.Bold)
        }
        
        TextButton(
            onClick = onSkip,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PermissionRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PageFourIdentity(
    alias: String,
    onAliasChange: (String) -> Unit,
    onStart: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WHAT SHOULD PEERS CALL YOU?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = alias,
            onValueChange = onAliasChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            ),
            placeholder = { 
                Text("Enter Alias", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 24.sp) 
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onStart() })
        )
        
        Text(
            text = "/24",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = alias.isNotBlank()
        ) {
            Text("START CRISISOS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

