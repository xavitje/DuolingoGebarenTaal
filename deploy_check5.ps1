$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\DuolingoGebarenTaal\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
& $adb shell am force-stop com.example.signcoach
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 5
& $adb shell screencap -p /sdcard/learn_sync_check.png
& $adb pull /sdcard/learn_sync_check.png "D:\DuolingoGebarenTaal\learn_sync_check.png"
