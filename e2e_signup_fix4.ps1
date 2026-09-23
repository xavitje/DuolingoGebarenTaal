$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 300
& $adb shell input keyevent 61
Start-Sleep -Milliseconds 300
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 300
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 500
& $adb shell uiautomator dump /sdcard/ui4.xml
& $adb pull /sdcard/ui4.xml "D:\DuolingoGebarenTaal\ui4.xml"
& $adb shell screencap -p /sdcard/e2e_15_signup_v4.png
& $adb pull /sdcard/e2e_15_signup_v4.png "D:\DuolingoGebarenTaal\e2e_15_signup_v4.png"
