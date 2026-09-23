$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 1585
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/e2e_18_after_submit.png
& $adb pull /sdcard/e2e_18_after_submit.png "D:\DuolingoGebarenTaal\e2e_18_after_submit.png"
