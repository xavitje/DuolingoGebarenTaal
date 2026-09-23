$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
Start-Sleep -Seconds 5
& $adb shell screencap -p /sdcard/demo_letter_a_wait.png
& $adb pull /sdcard/demo_letter_a_wait.png "D:\DuolingoGebarenTaal\demo_letter_a_wait.png"
