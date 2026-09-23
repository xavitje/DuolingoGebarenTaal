$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 703 1728
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/e2e_10_signup.png
& $adb pull /sdcard/e2e_10_signup.png "D:\DuolingoGebarenTaal\e2e_10_signup.png"
