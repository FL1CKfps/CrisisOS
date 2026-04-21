with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# remove anything after the last }
idx = text.rfind('}')
if idx != -1:
    text = text[:idx+1]
    
with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(text)
