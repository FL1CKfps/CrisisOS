import re

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

c = c.replace('androidx.compose.foundation.layout.Box', 'Box')
c = c.replace('androidx.compose.ui.unit.IntOffset', 'IntOffset')
c = c.replace('androidx.compose.foundation.gestures.draggable', 'draggable')
c = re.sub(r'androidx\.compose\.foundation\.gestures\.(Orientation\.Horizontal)', r'\1', c)
c = c.replace('androidx.compose.foundation.gestures.rememberDraggableState', 'rememberDraggableState')

with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)

