@echo off
set ADB=C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe
"%ADB%" shell am force-stop com.example.signcoach
"%ADB%" exec-out run-as com.example.signcoach cat databases/signcoach.db > D:\DuolingoGebarenTaal\pulled_signcoach.db
