package com.elv8.crisisos.ui.screens.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.elv8.crisisos.core.debug.DiagnosticsSnapshot
import com.elv8.crisisos.ui.components.CrisisCard

@Composable
fun DiagnosticsPanel(
    snapshot: DiagnosticsSnapshot?,
    modifier: Modifier = Modifier
) {
    if (snapshot == null) return

    var expanded by remember { mutableStateOf(false) }

    CrisisCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MESH DIAGNOSTICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = snapshot.timestamp,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand/Collapse Diagnostics"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    DiagnosticRow("advertising:", if (snapshot.isAdvertising) "YES" else "NO (!!!)", snapshot.isAdvertising)
                    DiagnosticRow("discovering:", if (snapshot.isDiscovering) "YES" else "NO (!!!)", snapshot.isDiscovering)
                    DiagnosticRow("connected peers:", snapshot.connectedPeerCount.toString(), true)
                    DiagnosticRow("nearby (Room):", snapshot.nearbyPeerCount.toString(), true)
                    DiagnosticRow("restarts:", snapshot.discoveryRestartCount.toString(), true)
                    
                    if (snapshot.missingPermissions.isNotEmpty()) {
                        Text(
                            text = "MISSING PERMS: ",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    DiagnosticRow("local alias:", snapshot.localAlias, true)
                    DiagnosticRow("local crs-id:", snapshot.localCrsId, true)
                    DiagnosticRow(
                        "peers:", 
                        if (snapshot.connectedPeers.isNotEmpty()) snapshot.connectedPeers.joinToString(" | ") else "none", 
                        true
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(key: String, value: String, isHealthy: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (isHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
