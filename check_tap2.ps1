$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 150 2380
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/check_tap2.png
& $adb pull /sdcard/check_tap2.png "D:\DuolingoGebarenTaal\check_tap2.png"
