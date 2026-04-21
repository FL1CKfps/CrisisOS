import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

OLD = '''@Composable
fun MessageBubble(
    message: Message,
    localCrsId: String,
    onLongPress: () -> Unit,
    isGroup: Boolean
) {'''

NEW = '''@Composable
fun MessageBubble(
    message: Message,
    localCrsId: String,
    onLongPress: () -> Unit,
    isGroup: Boolean,
    onReply: () -> Unit = onLongPress
) {
    val visibleState = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    
    androidx.compose.animation.AnimatedVisibility(
        visibleState = visibleState,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) + 
                androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(200)) { it / 4 }
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .androidx.compose.foundation.gestures.draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                        if (message.fromCrsId != localCrsId && delta > 0) { // Only swipe others to reply
                            coroutineScope.kotlinx.coroutines.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(0f, 150f))
                            }
                        }
                    },
                    onDragStopped = {
                        coroutineScope.kotlinx.coroutines.launch {
                            if (offsetX.value > 100f) {
                                // Trigger vibration logic
                                onReply()
                            }
                            offsetX.animateTo(0f, androidx.compose.animation.core.spring())
                        }
                    }
                )
        ) {'''

c = c.replace(OLD, NEW)

OLD_END = '''        }
    }
}'''

NEW_END = '''        }
        }
    }
}'''

c = c.replace(OLD_END, NEW_END)

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)
