$adb = "C:\Users\rafie\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$xml = @'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="app_language">en</string>
    <string name="session_access_token">eyJhbGciOiJFUzI1NiIsImtpZCI6ImMwYTljNWRkLTNkYjQtNGQ2YS1hODYyLWE4MzE5NWE2M2MwNCIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL3pqZWR4bHZkZWVkcGtieHpxa3BvLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiI5NTUxMjM5MS0yZGVjLTQ1MTItODg4ZC03ZjExZmEyN2VjMmYiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzg4OTc4NDc3LCJpYXQiOjE3ODg5NzQ4NzcsImVtYWlsIjoicmFmaS5lbGlkcmlzc2lAZ21haWwuY29tIiwicGhvbmUiOiIiLCJhcHBfbWV0YWRhdGEiOnsicHJvdmlkZXIiOiJlbWFpbCIsInByb3ZpZGVycyI6WyJlbWFpbCJdfSwidXNlcl9tZXRhZGF0YSI6e30sInJvbGUiOiJhdXRoZW50aWNhdGVkIiwiYWFsIjoiYWFsMSIsImFtciI6W3sibWV0aG9kIjoicGFzc3dvcmQiLCJ0aW1lc3RhbXAiOjE3ODg5MDYxODh9XSwic2Vzc2lvbl9pZCI6ImJlZTdiZjVjLTRiMzktNDI0MC1hYmUwLWJkZGZkMDE2MzI1OSIsImlzX2Fub255bW91cyI6ZmFsc2V9.paLLtkPEJuIXomsd6KHWghpu5wPgFUXdTS6QSNkrcPWcqmWkB91odtGAbbyVbBG9BUkP_eaEXEQrO4L6pjRdMA</string>
    <string name="session_refresh_token">5pq2fvmaedry</string>
    <string name="session_user_id">95512391-2dec-4512-888d-7f11fa27ec2f</string>
    <string name="session_email">rafi.elidrissi@gmail.com</string>
</map>
'@
$xml | & $adb shell "run-as com.example.signcoach sh -c 'cat > shared_prefs/com.example.signcoach_preferences.xml'"
& $adb shell am force-stop com.example.signcoach
Start-Sleep -Seconds 1
& $adb shell monkey -p com.example.signcoach -c android.intent.category.LAUNCHER 1
