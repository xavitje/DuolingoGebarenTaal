$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 972
Start-Sleep -Milliseconds 400
& $adb shell input keyevent 123
Start-Sleep -Milliseconds 200
for ($i=0; $i -lt 60; $i++) { & $adb shell input keyevent 67 }
Start-Sleep -Milliseconds 300
& $adb shell input text "rafi.elidrissi+e2etest@gmail.com"
Start-Sleep -Milliseconds 300
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 500
& $adb shell uiautomator dump /sdcard/ui7.xml
& $adb pull /sdcard/ui7.xml "D:\DuolingoGebarenTaal\ui7.xml"
& $adb shell screencap -p /sdcard/e2e_19_email_fixed.png
& $adb pull /sdcard/e2e_19_email_fixed.png "D:\DuolingoGebarenTaal\e2e_19_email_fixed.png"
