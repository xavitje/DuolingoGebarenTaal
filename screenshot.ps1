$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell screencap -p /sdcard/learn_check.png
& $adb pull /sdcard/learn_check.png "D:\DuolingoGebarenTaal\learn_check.png"
