$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$xml = @'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="app_language">en</string>
    <string name="session_access_token">invalidtoken456</string>
    <string name="session_refresh_token">5pq2fvmaedry</string>
    <string name="session_user_id">95512391-2dec-4512-888d-7f11fa27ec2f</string>
    <string name="session_email">rafi.elidrissi@gmail.com</string>
</map>
'@
$xml | & $adb shell "run-as com.example.signcoach sh -c 'cat > shared_prefs/com.example.signcoach_preferences.xml'"
# app stays running (no force-stop) - simulate mid-session token becoming stale
& $adb shell input tap 173 2364
Start-Sleep -Seconds 1
& $adb shell input tap 539 2364
Start-Sleep -Seconds 3
& $adb shell screencap -p /sdcard/verify_live.png
& $adb pull /sdcard/verify_live.png "D:\DuolingoGebarenTaal\verify_live.png"
