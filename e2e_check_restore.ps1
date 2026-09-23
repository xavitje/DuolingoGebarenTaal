$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
Start-Sleep -Seconds 4
& $adb shell screencap -p /sdcard/e2e_09_restored.png
& $adb pull /sdcard/e2e_09_restored.png "D:\DuolingoGebarenTaal\e2e_09_restored.png"
