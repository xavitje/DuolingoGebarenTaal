$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input keyevent 123
Start-Sleep -Milliseconds 200
for ($i=0; $i -lt 40; $i++) { & $adb shell input keyevent 67 }
Start-Sleep -Milliseconds 300
& $adb shell input text "TestOffline123"
Start-Sleep -Milliseconds 300
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 600
& $adb shell uiautomator dump /sdcard/ui5.xml
& $adb pull /sdcard/ui5.xml "D:\DuolingoGebarenTaal\ui5.xml"
& $adb shell screencap -p /sdcard/e2e_16_before_confirm.png
& $adb pull /sdcard/e2e_16_before_confirm.png "D:\DuolingoGebarenTaal\e2e_16_before_confirm.png"
