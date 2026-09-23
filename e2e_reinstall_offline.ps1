$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\DuolingoGebarenTaal\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
& $adb shell am force-stop com.example.signcoach
Start-Sleep -Seconds 1
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 4
& $adb shell screencap -p /sdcard/e2e_07_learn_offline_v2.png
& $adb pull /sdcard/e2e_07_learn_offline_v2.png "D:\DuolingoGebarenTaal\e2e_07_learn_offline_v2.png"
& $adb shell input tap 540 2232
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/e2e_08_progress_offline_v2.png
& $adb pull /sdcard/e2e_08_progress_offline_v2.png "D:\DuolingoGebarenTaal\e2e_08_progress_offline_v2.png"
