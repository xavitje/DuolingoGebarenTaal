$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\DuolingoGebarenTaal\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
& $adb shell am force-stop com.example.signcoach
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 4
& $adb shell input tap 539 2178
Start-Sleep -Seconds 1
& $adb shell screencap -p /sdcard/progress_check3.png
& $adb pull /sdcard/progress_check3.png "D:\DuolingoGebarenTaal\progress_check3.png"
& $adb shell input tap 906 2178
Start-Sleep -Seconds 1
& $adb shell screencap -p /sdcard/profile_check3.png
& $adb pull /sdcard/profile_check3.png "D:\DuolingoGebarenTaal\profile_check3.png"
& $adb shell input tap 768 532
Start-Sleep -Seconds 1
& $adb shell screencap -p /sdcard/avatar_picker.png
& $adb pull /sdcard/avatar_picker.png "D:\DuolingoGebarenTaal\avatar_picker.png"
