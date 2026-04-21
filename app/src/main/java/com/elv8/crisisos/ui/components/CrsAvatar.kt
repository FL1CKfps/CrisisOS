package com.elv8.crisisos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CrsAvatar(
    crsId: String,
    alias: String,
    avatarColor: Int,
    size: Dp = 40.dp,
    showOnlineIndicator: Boolean = false,
    isOnline: Boolean = false
) {
    val initials = extractInitials(alias)
    
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color(avatarColor).copy(alpha = 0.85f),
                radius = size.toPx() / 2f
            )
        }
        Text(
            text = initials,
            color = Color.White,
            fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.38f).toSp() },
            fontWeight = FontWeight.Medium
        )
        
        if (showOnlineIndicator) {
            val indicatorColor by animateColorAsState(
                targetValue = if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                animationSpec = tween(400),
                label = "onlineIndicatorColor"
            )
            
            Canvas(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .offset(x = size * 0.05f, y = size * 0.05f)
            ) {
                drawCircle(color = indicatorColor)
                drawCircle(
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

private fun extractInitials(name: String): String {
    if (name.isBlank()) return "?"
    val words = name.trim().split(Regex("\\s+"))
    return if (words.size == 1) {
        words[0].take(1).uppercase()
    } else {
        (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
