$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/check_home.png
& $adb pull /sdcard/check_home.png "D:\DuolingoGebarenTaal\check_home.png"
