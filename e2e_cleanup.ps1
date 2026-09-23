$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell pm clear com.example.signcoach
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/e2e_20_clean_state.png
& $adb pull /sdcard/e2e_20_clean_state.png "D:\DuolingoGebarenTaal\e2e_20_clean_state.png"
