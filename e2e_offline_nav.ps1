$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap 173 2232
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/e2e_02_learn_offline.png
& $adb pull /sdcard/e2e_02_learn_offline.png "D:\DuolingoGebarenTaal\e2e_02_learn_offline.png"
& $adb shell input tap 540 854
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/e2e_03_gesture_offline.png
& $adb pull /sdcard/e2e_03_gesture_offline.png "D:\DuolingoGebarenTaal\e2e_03_gesture_offline.png"
