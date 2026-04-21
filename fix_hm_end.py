with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# remove trailing garbage
text = text.rstrip() 
while text.endswith('}'):
    text = text[:-1].rstrip()

# find exact braces count
opened = text.count('{')
closed = text.count('}')

text += '\n' + ('}' * (opened - closed))

with open(r'c:\Users\visha\AndroidStudioProjects\CrisisOs\app\src\main\java\com\elv8\crisisos\ui\screens\home\HomeViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(text)
