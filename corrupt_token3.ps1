$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell run-as com.example.signcoach sh -c "sed -i 's/session_access_token\">eyJ[^<]*</session_access_token\">invalidtoken123</' shared_prefs/com.example.signcoach_preferences.xml"
& $adb shell run-as com.example.signcoach cat shared_prefs/com.example.signcoach_preferences.xml
