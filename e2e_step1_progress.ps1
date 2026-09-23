$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 2232
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/e2e_01_progress.png
& $adb pull /sdcard/e2e_01_progress.png "D:\DuolingoGebarenTaal\e2e_01_progress.png"
