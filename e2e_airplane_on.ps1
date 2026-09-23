$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell settings put global airplane_mode_on 1
& $adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
Start-Sleep -Seconds 3
& $adb shell dumpsys connectivity | Select-String "NetworkAgentInfo" | Select-Object -First 5
& $adb shell settings get global airplane_mode_on
