package com.elv8.crisisos.ui.components

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class BadgeStatus {
    ACTIVE, WARNING, CRITICAL, OFFLINE, OK
}

@Composable
fun StatusBadge(
    text: String,
    status: BadgeStatus,
    modifier: Modifier = Modifier
) {
    val containerColor: Color
    val contentColor: Color

    when (status) {
        BadgeStatus.ACTIVE -> {
            containerColor = Color(0xFF1B4332)
            contentColor = Color(0xFF4AD66D)
        }
        BadgeStatus.WARNING -> {
            containerColor = Color(0xFF4A3419)
            contentColor = Color(0xFFFFB03A)
        }
        BadgeStatus.CRITICAL -> {
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            contentColor = MaterialTheme.colorScheme.error
        }
        BadgeStatus.OFFLINE -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        BadgeStatus.OK -> {
            containerColor = Color(0xFF0F3057)
            contentColor = Color(0xFF4DA8DA)
        }
    }

    val context = LocalContext.current
    val animationScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1.0f
    )
    val reduceMotion = animationScale == 0f

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val alpha = if (status == BadgeStatus.CRITICAL && !reduceMotion) animatedAlpha else 1f

    Box(
        modifier = modifier
            .background(
                color = containerColor.copy(alpha = containerColor.alpha * alpha),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = contentColor.copy(alpha = contentColor.alpha * alpha),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
