$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell uiautomator dump /sdcard/ui.xml
& $adb pull /sdcard/ui.xml "D:\DuolingoGebarenTaal\ui.xml"
