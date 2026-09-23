$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell dumpsys power | Select-String "mWakefulness|Display Power"
