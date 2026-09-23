$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell dumpsys window windows | Select-String "mCurrentFocus|mFocusedApp"
& $adb shell dumpsys activity activities | Select-String "mResumedActivity"
