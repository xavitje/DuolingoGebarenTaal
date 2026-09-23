$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell settings put global airplane_mode_on 0
& $adb shell svc wifi disable
& $adb shell svc data disable
Start-Sleep -Seconds 3
& $adb shell cmd connectivity airplane-mode enable
Start-Sleep -Seconds 2
& $adb shell dumpsys connectivity | Select-String "NetworkAgentInfo" | Select-Object -First 5
