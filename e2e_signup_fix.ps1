$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 539 1190
Start-Sleep -Milliseconds 400
for ($i=0; $i -lt 30; $i++) { & $adb shell input keyevent 67 }
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 300
& $adb shell input tap 539 1402
Start-Sleep -Milliseconds 400
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 300
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 500
& $adb shell screencap -p /sdcard/e2e_13_signup_fixed.png
& $adb pull /sdcard/e2e_13_signup_fixed.png "D:\DuolingoGebarenTaal\e2e_13_signup_fixed.png"
