$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell monkey -p com.example.signcoach -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/relaunch1.png
& $adb pull /sdcard/relaunch1.png "D:\DuolingoGebarenTaal\relaunch1.png"
& $adb shell input tap 173 2364
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/relaunch2.png
& $adb pull /sdcard/relaunch2.png "D:\DuolingoGebarenTaal\relaunch2.png"
