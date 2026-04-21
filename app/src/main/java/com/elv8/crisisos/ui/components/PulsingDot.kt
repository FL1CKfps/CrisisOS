package com.elv8.crisisos.ui.components

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun isReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1.0f
    )
    return scale == 0.0f
}

@Composable
fun PulsingDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val reduceMotion = isReduceMotionEnabled()
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingDot")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (reduceMotion) 1.0f else 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = if (reduceMotion) 1.0f else 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

@Composable
fun LoadingDots(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val reduceMotion = isReduceMotionEnabled()
    val dotCount = 3
    val infiniteTransition = rememberInfiniteTransition(label = "loadingDots")

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until dotCount) {
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.3f at 0 with LinearEasing
                        1.0f at (200 + i * 200) with FastOutSlowInEasing
                        0.3f at (600 + i * 200) with LinearEasing
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "loadingDotAlpha\$i"
            )
            
            Box(
                modifier = Modifier
                    .size(size)
                    .alpha(if (reduceMotion) 1.0f else alpha)
                    .background(color, CircleShape)
            )
        }
    }
}