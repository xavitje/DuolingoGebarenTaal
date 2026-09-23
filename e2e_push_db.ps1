$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb push "D:\DuolingoGebarenTaal\signcoach_seeded.db" /data/local/tmp/signcoach_seeded.db
& $adb shell run-as com.example.signcoach cp /data/local/tmp/signcoach_seeded.db databases/signcoach.db
& $adb shell run-as com.example.signcoach rm -f databases/signcoach.db-journal
& $adb shell run-as com.example.signcoach ls -la databases
