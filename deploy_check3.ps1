$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\DuolingoGebarenTaal\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
& $adb shell am force-stop com.example.signcoach
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 4
& $adb shell screencap -p /sdcard/learn_check4.png
& $adb pull /sdcard/learn_check4.png "D:\DuolingoGebarenTaal\learn_check4.png"
& $adb shell input tap 539 2178
Start-Sleep -Seconds 1
& $adb shell screencap -p /sdcard/progress_check2.png
& $adb pull /sdcard/progress_check2.png "D:\DuolingoGebarenTaal\progress_check2.png"
& $adb shell input tap 906 2178
Start-Sleep -Seconds 1
& $adb shell screencap -p /sdcard/profile_check2.png
& $adb pull /sdcard/profile_check2.png "D:\DuolingoGebarenTaal\profile_check2.png"
