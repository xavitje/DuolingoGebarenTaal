$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell am force-stop com.example.signcoach
Start-Sleep -Seconds 1
& $adb exec-out run-as com.example.signcoach cat databases/signcoach.db > "D:\DuolingoGebarenTaal\pulled_signcoach.db"
Get-Item "D:\DuolingoGebarenTaal\pulled_signcoach.db" | Select-Object Name, Length
