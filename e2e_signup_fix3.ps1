$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 540 1180
Start-Sleep -Milliseconds 400
& $adb shell input keyevent 123
Start-Sleep -Milliseconds 200
for ($i=0; $i -lt 50; $i++) { & $adb shell input keyevent 67 }
Start-Sleep -Milliseconds 300
& $adb shell uiautomator dump /sdcard/ui3.xml
& $adb pull /sdcard/ui3.xml "D:\DuolingoGebarenTaal\ui3.xml"
