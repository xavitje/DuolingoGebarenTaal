$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"

& $adb shell am force-stop com.example.signcoach
Start-Sleep -Seconds 2
& $adb shell monkey -p com.example.signcoach -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 4

# Tap letter A node
& $adb shell input tap 540 854
Start-Sleep -Seconds 8

& $adb shell screencap -p /sdcard/verify_video_1.png
& $adb pull /sdcard/verify_video_1.png "D:\DuolingoGebarenTaal\verify_video_1.png"

Start-Sleep -Seconds 4
& $adb shell screencap -p /sdcard/verify_video_2.png
& $adb pull /sdcard/verify_video_2.png "D:\DuolingoGebarenTaal\verify_video_2.png"
