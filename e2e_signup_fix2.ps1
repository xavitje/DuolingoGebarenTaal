$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 1180
Start-Sleep -Milliseconds 400
& $adb shell input keyevent 123
Start-Sleep -Milliseconds 200
for ($i=0; $i -lt 60; $i++) { & $adb shell input keyevent 67 }
Start-Sleep -Milliseconds 300
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 300
& $adb shell input tap 540 1390
Start-Sleep -Milliseconds 400
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 500
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 500
& $adb shell uiautomator dump /sdcard/ui2.xml
& $adb pull /sdcard/ui2.xml "D:\DuolingoGebarenTaal\ui2.xml"
& $adb shell screencap -p /sdcard/e2e_14_signup_v3.png
& $adb pull /sdcard/e2e_14_signup_v3.png "D:\DuolingoGebarenTaal\e2e_14_signup_v3.png"
