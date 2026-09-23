$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 173 2364
Start-Sleep -Seconds 1
& $adb shell screencap -p /sdcard/check_tap.png
& $adb pull /sdcard/check_tap.png "D:\DuolingoGebarenTaal\check_tap.png"
