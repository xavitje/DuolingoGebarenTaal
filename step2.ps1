$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 2232
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/step2.png
& $adb pull /sdcard/step2.png "D:\DuolingoGebarenTaal\step2.png"
