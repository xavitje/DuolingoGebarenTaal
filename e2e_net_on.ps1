$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell cmd connectivity airplane-mode disable
& $adb shell svc wifi enable
& $adb shell svc data enable
Start-Sleep -Seconds 5
& $adb shell dumpsys connectivity | Select-String "NetworkAgentInfo" | Select-Object -First 5
