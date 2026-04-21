import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

c = c.replace('androidx.compose.animation.AnimatedVisibility(', 'AnimatedVisibility(')
c = c.replace('androidx.compose.animation.Crossfade(', 'Crossfade(')
if 'import kotlinx.coroutines.launch' not in c:
    c = 'import kotlinx.coroutines.launch\n' + c
if 'import androidx.compose.animation.AnimatedVisibility' not in c:
    c = 'import androidx.compose.animation.AnimatedVisibility\n' + c

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)
