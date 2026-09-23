$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 702 2074
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/e2e_11_signup2.png
& $adb pull /sdcard/e2e_11_signup2.png "D:\DuolingoGebarenTaal\e2e_11_signup2.png"
