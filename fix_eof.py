with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'r', encoding='utf8') as f:
    c = f.read()

if c.endswith('}\n'):
    c = c[:-2]

while c.endswith('}'):
    c = c[:-1].strip()

c += '''
                }
            }
        }
    }
}
'''
with open('app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatThreadScreen.kt', 'w', encoding='utf8') as f:
    f.write(c)

