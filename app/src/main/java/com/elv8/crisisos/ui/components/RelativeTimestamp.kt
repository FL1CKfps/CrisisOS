package com.elv8.crisisos.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RelativeTimestamp(timestamp: Long, style: TextStyle = MaterialTheme.typography.bodySmall) {
    Text(
        text = formatRelativeTime(timestamp),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "m ago"
        diff < 86_400_000 -> "h ago"
        diff < 7 * 86_400_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
