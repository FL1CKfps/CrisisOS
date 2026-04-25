package com.elv8.crisisos.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elv8.crisisos.ui.navigation.Screen

@Composable
fun SectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MoreScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val features = remember {
        listOf(
            FeatureItem("1", "Mesh Chat", "Offline peer-to-peer messaging", Icons.Filled.Chat, Screen.ChatHub.route, FeatureStatus.OFFLINE_READY),
            FeatureItem("2", "SOS Broadcast", "Multi-hop emergency alert", Icons.Filled.Warning, Screen.Sos.route, FeatureStatus.AVAILABLE),
            FeatureItem("3", "Dead Man's Switch", "Auto-alert on no check-in", Icons.Filled.Timer, Screen.DeadManSwitch.route, FeatureStatus.BETA),
            FeatureItem("4", "Find Person", "CRS-ID lookup + child watches", Icons.Filled.PersonSearch, Screen.MissingPerson.route, FeatureStatus.BETA),
            FeatureItem("5", "Supply Request", "Request aid offline", Icons.Filled.Inventory, Screen.Supply().route, FeatureStatus.AVAILABLE),
            FeatureItem("6", "Offline Maps", "Safe zones, no internet", Icons.Filled.Map, Screen.Maps.route, FeatureStatus.OFFLINE_READY),
            FeatureItem("7", "Danger Zone", "Live threat detection", Icons.Filled.LocationOff, Screen.DangerZone.route, FeatureStatus.BETA),
            FeatureItem("8", "Checkpoint Intel", "Safety ratings, status", Icons.Filled.Shield, Screen.Checkpoint.route, FeatureStatus.COMING_SOON),
            FeatureItem("9", "AI Assistant", "On-device Gemma 4 E2B", Icons.Filled.Psychology, Screen.AiAssistant.route, FeatureStatus.AVAILABLE),
            FeatureItem("10", "Fake News", "Verify information", Icons.Filled.FactCheck, Screen.FakeNews.route, FeatureStatus.BETA),
            FeatureItem("11", "Deconfliction", "Geneva protocol reports", Icons.Filled.Gavel, Screen.Deconfliction.route, FeatureStatus.COMING_SOON),
            FeatureItem("15", "My Contacts", "Manage trusted peers and groups", Icons.Filled.Contacts, Screen.Contacts.route, FeatureStatus.AVAILABLE)
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(2) }) {
            SectionHeader(
                title = "All Features",
                subtitle = "Offline-first survival tools"
            )
        }

        itemsIndexed(features) { index, feature ->
            FeatureCard(
                item = feature,
                index = index,
                onClick = { onNavigate(feature.route) }
            )
        }
    }
}
