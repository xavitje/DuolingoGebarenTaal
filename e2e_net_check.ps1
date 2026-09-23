$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell dumpsys connectivity | Select-String "NetworkAgentInfo"
