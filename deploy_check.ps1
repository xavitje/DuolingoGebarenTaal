$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\DuolingoGebarenTaal\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 4
& $adb shell screencap -p /sdcard/learn_check3.png
& $adb pull /sdcard/learn_check3.png "D:\DuolingoGebarenTaal\learn_check3.png"
