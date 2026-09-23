$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 173 2232
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/step1.png
& $adb pull /sdcard/step1.png "D:\DuolingoGebarenTaal\step1.png"
