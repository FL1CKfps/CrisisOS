package com.elv8.crisisos.ui.screens.fakenews

import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.filled.VerifiedUser

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.domain.model.Verdict
import com.elv8.crisisos.domain.model.VerificationResult
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.LocalTopBarState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FakeNewsScreen(
    onNavigateBack: () -> Unit,
    viewModel: FakeNewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = { Text("FAKE NEWS DETECTOR", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        
        // Input Section
        Text("VERIFY INFORMATION", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.claimInput,
            onValueChange = viewModel::updateInput,
            label = { Text("Paste a claim or news item to verify...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            maxLines = 5,
            enabled = !uiState.isAnalyzing
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = viewModel::analyzeClaim,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.claimInput.isNotBlank() && !uiState.isAnalyzing,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isAnalyzing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("CROSS-REFERENCING NODE DATA...", fontWeight = FontWeight.Bold)
            } else {
                Text("ANALYZE CLAIM", fontWeight = FontWeight.Bold)
            }
        }
        
        Text(
            "Powered by GDELT cross-reference",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Interactive results section
        AnimatedVisibility(
            visible = uiState.result != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = fadeOut()
        ) {
            uiState.result?.let { result ->
                ResultCard(result = result)
            }
        }

        // Display empty state or Recent Checks depending on result state
        if (uiState.result == null && uiState.recentChecks.isEmpty() && !uiState.isAnalyzing) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.VerifiedUser,
                    title = "Verify Intelligence",
                    subtitle = "In crisis zones, disinformation spreads faster than aid. Verify before you share."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.recentChecks.isNotEmpty()) {
            Text("RECENT CHECKS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.recentChecks, key = { it.id }) { recent ->
                    RecentCheckItem(
                        result = recent,
                        onClick = { viewModel.selectRecentCheck(recent) }
                    )
                }
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(result: VerificationResult) {
    val (verdictColor, verdictIcon) = getVerdictInfo(result.verdict)
    
    // Animate the confidence bar loading
    var barVisible by remember { mutableStateOf(false) }
    LaunchedEffect(result) {
        barVisible = false
        barVisible = true
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (barVisible) result.confidenceScore else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "confidence_progress"
    )

    CrisisCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Verdict
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(verdictIcon, contentDescription = null, tint = verdictColor, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.verdict.name.replace("_", " "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("CLAIM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(result.claimText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(16.dp))

            // Confidence Score
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Confidence Score", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("${(animatedProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = verdictColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = verdictColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("REASONING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(result.reasoning, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // Sources
            Text("SOURCES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                result.sources.forEach { source ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            text = source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text("Checked at: ${result.checkedAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RecentCheckItem(result: VerificationResult, onClick: () -> Unit) {
    val (verdictColor, _) = getVerdictInfo(result.verdict)
    
    CrisisCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = result.claimText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    color = verdictColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = result.verdict.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun getVerdictInfo(verdict: Verdict): Pair<Color, ImageVector> {
    return when (verdict) {
        Verdict.VERIFIED -> Pair(Color(0xFF4CAF50), Icons.Default.CheckCircle)
        Verdict.LIKELY_FALSE -> Pair(Color(0xFFF44336), Icons.Default.Warning)
        Verdict.UNVERIFIED -> Pair(Color(0xFFFFC107), Icons.Default.Info)
        Verdict.MISLEADING -> Pair(Color(0xFFFF9800), Icons.Default.Warning)
        Verdict.SATIRE -> Pair(Color(0xFF2196F3), Icons.Default.Info)
    }
}
