Set-Location "D:\DuolingoGebarenTaal"
.\gradlew.bat :composeApp:assembleDebug --console=plain 2>&1 | Tee-Object -FilePath "D:\DuolingoGebarenTaal\build_redesign_log.txt"
