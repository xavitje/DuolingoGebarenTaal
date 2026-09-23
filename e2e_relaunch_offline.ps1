$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -n com.example.signcoach/.MainActivity
Start-Sleep -Seconds 4
& $adb shell screencap -p /sdcard/e2e_05_learn_after_seed.png
& $adb pull /sdcard/e2e_05_learn_after_seed.png "D:\DuolingoGebarenTaal\e2e_05_learn_after_seed.png"
& $adb shell input tap 540 2232
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/e2e_06_progress_pending.png
& $adb pull /sdcard/e2e_06_progress_pending.png "D:\DuolingoGebarenTaal\e2e_06_progress_pending.png"
