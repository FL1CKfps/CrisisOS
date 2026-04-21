$file = 'app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatHubScreen.kt'
$content = Get-Content $file -Raw
$content = $content -replace '                1 -> \{[\s\S]*\}', "                1 -> {
                    PeerDiscoveryScreen(
                        onNavigateBack = { viewModel.setTab(0) },
                        onNavigateToChat = onNavigateToThread,
                        onNavigateToConnectionRequest = onNavigateToConnectionRequest
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    isSelected: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (count > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(text = count.toString())
                }
            }
        }
    }
}"
Set-Content -Path $file -Value $content
