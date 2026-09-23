$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 539 2227
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/verify_fix2.png
& $adb pull /sdcard/verify_fix2.png "D:\DuolingoGebarenTaal\verify_fix2.png"
