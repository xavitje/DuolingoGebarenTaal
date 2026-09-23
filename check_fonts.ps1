$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -d -t 400 | Select-String -Pattern "Font|Typeface|signcoach" | Select-Object -Last 60
