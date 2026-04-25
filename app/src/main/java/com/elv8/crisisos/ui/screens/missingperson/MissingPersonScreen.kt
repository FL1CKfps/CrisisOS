package com.elv8.crisisos.ui.screens.missingperson

import com.elv8.crisisos.ui.components.EmptyState
import androidx.compose.material.icons.filled.PersonSearch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elv8.crisisos.ui.components.CrisisCard
import com.elv8.crisisos.ui.components.LocalTopBarState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)@Composable
fun MissingPersonScreen(
    onNavigateBack: () -> Unit,
    viewModel: MissingPersonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val topBarState = LocalTopBarState.current

    LaunchedEffect(Unit) {
        topBarState.update(
            title = { Text("MISSING PERSONS", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }

    LaunchedEffect(pagerState.currentPage) {
        val newMode = if (pagerState.currentPage == 0) SearchMode.SEARCH else SearchMode.REGISTER
        viewModel.switchMode(newMode)
    }

    LaunchedEffect(uiState.mode) {
        val targetPage = if (uiState.mode == SearchMode.SEARCH) 0 else 1
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("SEARCH") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("REGISTER") }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> SearchTabContent(uiState, viewModel)
                1 -> RegisterTabContent(uiState, viewModel)
            }
        }
    }
}

@Composable
fun SearchTabContent(uiState: MissingPersonUiState, viewModel: MissingPersonViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter name or CRS ID") },
            trailingIcon = {
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { viewModel.search() }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.searchType == SearchType.BY_NAME,
                onClick = { viewModel.switchSearchType(SearchType.BY_NAME) },
                label = { Text("By Name") }
            )
            FilterChip(
                selected = uiState.searchType == SearchType.BY_CRS_ID,
                onClick = { viewModel.switchSearchType(SearchType.BY_CRS_ID) },
                label = { Text("By CRS ID") }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = Pair(uiState.hasSearched, uiState.searchResults.isEmpty()),
            label = "searchResults",
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
        ) { (hasSearched, isEmpty) ->
            if (!hasSearched) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "Search the distributed mesh network for reported missing persons.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 64.dp)
                    )
                }
            } else if (isEmpty && !uiState.isSearching) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(64.dp))
                    Text("No local matches found.", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { /* Broadcast logic */ },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Broadcast search to mesh")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.searchResults) { result ->
                        PersonResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
fun PersonResultCard(result: PersonResult) {
    val hopsColor = when {
        result.hopsAway <= 1 -> Color(0xFF4CAF50)
        result.hopsAway <= 4 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    CrisisCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    result.crsId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Last Location: ${result.lastLocation}", style = MaterialTheme.typography.bodyMedium)
            Text("Last Seen: ${result.lastSeen}", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(hopsColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${result.hopsAway} hops away",
                    style = MaterialTheme.typography.labelMedium,
                    color = hopsColor
                )
            }
        }
    }
}

@Composable
fun RegisterTabContent(uiState: MissingPersonUiState, viewModel: MissingPersonViewModel) {
    val clipboardManager = LocalClipboardManager.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Auto-Generated CRS ID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        uiState.generatedCrsId,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { clipboardManager.setText(AnnotatedString(uiState.generatedCrsId)) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Copy ID")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = uiState.registerName,
                onValueChange = { viewModel.updateRegisterForm(it, uiState.registerAge, uiState.registerDescription, uiState.registerLocation) },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = uiState.registerAge,
                onValueChange = { viewModel.updateRegisterForm(uiState.registerName, it, uiState.registerDescription, uiState.registerLocation) },
                label = { Text("Age / DOB") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = uiState.registerDescription,
                onValueChange = { viewModel.updateRegisterForm(uiState.registerName, uiState.registerAge, it, uiState.registerLocation) },
                label = { Text("Physical Description & Clothing") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }

        item {
            OutlinedTextField(
                value = uiState.registerLocation,
                onValueChange = { viewModel.updateRegisterForm(uiState.registerName, uiState.registerAge, uiState.registerDescription, it) },
                label = { Text("Last Known Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Button(
                onClick = { viewModel.registerPerson() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REGISTER RECORD TO MESH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.registeredPersons.isNotEmpty()) {
            item {
                Text("Previously Registered", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            items(uiState.registeredPersons) { person ->
                CrisisCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(person.name, fontWeight = FontWeight.Bold)
                        Text("CRS ID: ${person.crsId}", style = MaterialTheme.typography.labelMedium)
                        Text("Registered: ${person.registeredAt}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }        } else {
            item {
                EmptyState(
                    icon = Icons.Default.PersonSearch,
                    title = "No persons registered",
                    subtitle = "Tap + to add",
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}



