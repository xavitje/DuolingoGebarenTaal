$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\DuolingoGebarenTaal\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
& $adb shell am force-stop com.example.signcoach
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 4
& $adb shell input tap 540 854
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/demo_letter_a.png
& $adb pull /sdcard/demo_letter_a.png "D:\DuolingoGebarenTaal\demo_letter_a.png"
& $adb shell input tap 150 150
Start-Sleep -Seconds 1
& $adb shell input tap 796 1162
Start-Sleep -Seconds 1
& $adb shell screencap -p /sdcard/demo_letter_b.png
& $adb pull /sdcard/demo_letter_b.png "D:\DuolingoGebarenTaal\demo_letter_b.png"
