$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 449 972
Start-Sleep -Milliseconds 500
& $adb shell input text "e2etest.signcoach@example.com"
& $adb shell input keyevent 66
& $adb shell input tap 449 1245
Start-Sleep -Milliseconds 500
& $adb shell input text "TestOffline123"
& $adb shell input tap 449 1445
Start-Sleep -Milliseconds 500
& $adb shell input text "TestOffline123"
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 500
& $adb shell screencap -p /sdcard/e2e_12_signup_filled.png
& $adb pull /sdcard/e2e_12_signup_filled.png "D:\DuolingoGebarenTaal\e2e_12_signup_filled.png"
