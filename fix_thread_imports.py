import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

c = c.replace('androidx.compose.animation.fadeIn', 'fadeIn')
c = c.replace('androidx.compose.animation.slideInVertically', 'slideInVertically')
c = c.replace('androidx.compose.animation.core.tween', 'tween')
c = c.replace('androidx.compose.animation.core.spring', 'spring')

if 'import kotlinx.coroutines.launch' not in c:
    c = c.replace('import androidx.compose.runtime.remember', 'import kotlinx.coroutines.launch\nimport androidx.compose.runtime.remember')    

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)
