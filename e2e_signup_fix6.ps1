$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 1390
Start-Sleep -Milliseconds 500
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 300
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 500
& $adb shell uiautomator dump /sdcard/ui6.xml
& $adb pull /sdcard/ui6.xml "D:\DuolingoGebarenTaal\ui6.xml"
& $adb shell screencap -p /sdcard/e2e_17_both_filled.png
& $adb pull /sdcard/e2e_17_both_filled.png "D:\DuolingoGebarenTaal\e2e_17_both_filled.png"
