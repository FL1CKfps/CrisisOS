$file = 'app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatListScreen.kt'
$content = Get-Content $file -Raw
$content = $content -replace '            \}\n        \}\n    \}\n\}\n\n    @Composable', "            }
        }
    }

    @Composable"
Set-Content -Path $file -Value $content
