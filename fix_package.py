import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

# Remove the incorrectly placed imports at the start of the file
bad_header = '''import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.launch
package com.elv8.crisisos.ui.screens.chatv2'''

good_header = '''package com.elv8.crisisos.ui.screens.chatv2
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.launch'''

c = c.replace(bad_header, good_header)

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)

