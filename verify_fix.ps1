$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\DuolingoGebarenTaal\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
& $adb shell am force-stop com.example.signcoach
Start-Sleep -Seconds 2
& $adb shell monkey -p com.example.signcoach -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 4
& $adb shell input tap 540 2364
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/verify_fix.png
& $adb pull /sdcard/verify_fix.png "D:\DuolingoGebarenTaal\verify_fix.png"
