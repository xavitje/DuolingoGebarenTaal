$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 1802
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/e2e_04_practice_offline.png
& $adb pull /sdcard/e2e_04_practice_offline.png "D:\DuolingoGebarenTaal\e2e_04_practice_offline.png"
