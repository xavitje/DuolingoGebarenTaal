$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 906 2364
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/check_tap3.png
& $adb pull /sdcard/check_tap3.png "D:\DuolingoGebarenTaal\check_tap3.png"
