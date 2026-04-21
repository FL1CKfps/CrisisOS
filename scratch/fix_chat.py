import re

with open(r'c:\Users\visha\AndroidStudioProjects\crisis os\app\src\main\java\com\elv8\crisisos\ui\screens\chatv2\ChatThreadScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update Scaffold modifier to have navigationBarsPadding and imePadding
content = re.sub(
    r'Scaffold\(\s*modifier = Modifier\.fillMaxSize\(\)\.imePadding\(\),',
    'Scaffold(\n        modifier = Modifier.fillMaxSize().imePadding(),', # wait, already has imePadding. we just leave it. user said: ONE application at the root Scaffold is sufficient and correct
    content,
    count=1
)

# 2. Re-arrange the structure
# We need to find `bottomBar = { ... }` and extract it.
bottom_bar_match = re.search(r'bottomBar = \{(.*?)^\s*\}\n\s*\) \{ padding ->', content, re.MULTILINE | re.DOTALL)
if bottom_bar_match:
    bottom_bar_content = bottom_bar_match.group(1).strip()
    
    # We clean up the duplicated recording indicators in bottom_bar_content.
    # It seems there are 3 identical 'AnimatedVisibility' for isRecording
    rec_matches = list(re.finditer(r'AnimatedVisibility\(\s*visible = uiState\.isRecording,[\s\S]*?\}\n\s*\}', bottom_bar_content))
    if len(rec_matches) >= 3:
        # keep only the first one
        bottom_bar_content = bottom_bar_content[:rec_matches[1].start()] + bottom_bar_content[rec_matches[-1].end():]
        print(f"Removed {(len(rec_matches)-1)} duplicate recording rows.")

    # We also need to remove navigationBarsPadding() from the input row inside the bottom_bar_content (which will become InputRow)
    bottom_bar_content = bottom_bar_content.replace('.navigationBarsPadding(),', ',')

    # Find the LazyColumn inside padding ->
    lazy_column_match = re.search(r'LazyColumn\((.*?)\) \{\n(.*?)^\s*\}\n\n\s*AttachmentPreviewSheet', content, re.MULTILINE | re.DOTALL)
    
    if lazy_column_match:
        lazy_col_props = lazy_column_match.group(1)
        lazy_col_body = lazy_column_match.group(2)

        # Let's fix LazyColumn props: .weight(1f).fillMaxWidth() instead of .fillMaxSize().padding(padding).imeNestedScroll().background(MaterialTheme.colorScheme.background)
        # Actually user said MUST be weight(1f) NOT fillMaxSize()
        # So we replace `.fillMaxSize().padding(padding).imeNestedScroll().background(MaterialTheme.colorScheme.background)`
        lazy_col_props = re.sub(r'\.fillMaxSize\(\)\s*\.padding\(padding\)\s*\.imeNestedScroll\(\)', '.weight(1f).fillMaxWidth()', lazy_col_props)
        lazy_col_props = lazy_col_props.replace('.background(MaterialTheme.colorScheme.background)', '')

        new_content = f"""bottomBar = {{}}
    ) {{ padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {{
            LazyColumn({lazy_col_props}) {{
{lazy_col_body}
            }}
            
            {bottom_bar_content}
        }}
        
        AttachmentPreviewSheet"""
        
        content = content[:bottom_bar_match.start()] + new_content + content[lazy_column_match.end():]
        print("Restructured Scaffold content successfully.")
    else:
        print("LazyColumn match failed")
else:
    print("bottomBar match failed")

# 3. Check for any other imePadding or WindowInsets.navigationBars
content = re.sub(r'\.imePadding\(\)', '', content) # Remove all
content = content.replace('modifier = Modifier.fillMaxSize(),\n        topBar', 'modifier = Modifier.fillMaxSize().imePadding(),\n        topBar')
content = content.replace('modifier = Modifier.fillMaxSize()\n        topBar', 'modifier = Modifier.fillMaxSize().imePadding(),\n        topBar')
# Let's cleanly put ONE imePadding back
content = re.sub(r'Scaffold\(\s*modifier = Modifier\.fillMaxSize\(\)[,\n]', 'Scaffold(\n        modifier = Modifier.fillMaxSize().imePadding(),\n', content, count=1)

with open(r'c:\Users\visha\AndroidStudioProjects\crisis os\app\src\main\java\com\elv8\crisisos\ui\screens\chatv2\ChatThreadScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done writing modifications.")
