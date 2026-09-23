$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -d | Select-String -Pattern "signcoach" -Context 0,0 | Select-String -Pattern "Exception|Error|font|Font|typeface|Typeface" | Select-Object -Last 80
