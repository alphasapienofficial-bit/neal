# Builds Neal-App.apk on this Windows computer.
# Needs: Node.js, JDK 21 (Android Studio's bundled one works), Android SDK.
# Run from this folder:  powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Use Android Studio's Java if JAVA_HOME isn't set
if (-not $env:JAVA_HOME) {
  foreach ($p in @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:ProgramFiles\Android\Android Studio\jre")) {
    if (Test-Path "$p\bin\java.exe") { $env:JAVA_HOME = $p; break }
  }
}
if (-not $env:ANDROID_HOME) {
  $sdk = "$env:LOCALAPPDATA\Android\Sdk"
  if (Test-Path $sdk) { $env:ANDROID_HOME = $sdk }
}
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) { throw 'Java or the Android SDK was not found. Install Android Studio first.' }

Write-Host '== Installing packages'
npm install --no-audit --no-fund
if ($LASTEXITCODE) { throw 'npm install failed' }

if (Test-Path android) { Remove-Item -Recurse -Force android }
Write-Host '== Creating the Android project'
npx cap add android
if ($LASTEXITCODE) { throw 'cap add android failed' }

Copy-Item native\*.java android\app\src\main\java\com\neal\app\ -Force

$manifest = 'android\app\src\main\AndroidManifest.xml'
$m = Get-Content $manifest -Raw
if ($m -notmatch 'POST_NOTIFICATIONS') {
  $m = $m -replace '</manifest>', "    <uses-permission android:name=`"android.permission.POST_NOTIFICATIONS`" />`n</manifest>"
  Set-Content $manifest $m -NoNewline
}

$res = 'android\app\src\main\res'
Get-ChildItem $res -Recurse -Filter splash.png | Remove-Item -Force
Get-ChildItem $res -Directory -Filter 'mipmap-*' | ForEach-Object { Get-ChildItem $_.FullName -Filter 'ic_launcher*.png' | Remove-Item -Force }
Remove-Item "$res\mipmap-anydpi-v26\ic_launcher*.xml" -Force -ErrorAction SilentlyContinue
Copy-Item native\res\* $res -Recurse -Force

Write-Host '== Copying the web app into Android'
npx cap sync android
if ($LASTEXITCODE) { throw 'cap sync failed' }

# Same signing key as before, so it installs over the old app.
# Your own debug key is backed up first and put back afterwards.
$dk = "$env:USERPROFILE\.android\debug.keystore"
New-Item -ItemType Directory -Force "$env:USERPROFILE\.android" | Out-Null
$backup = $null
if (Test-Path $dk) { $backup = "$dk.neal-backup"; Copy-Item $dk $backup -Force }
Copy-Item android-debug.keystore $dk -Force

Write-Host '== Building the APK'
Push-Location android
try { .\gradlew.bat assembleDebug; $code = $LASTEXITCODE }
finally {
  Pop-Location
  if ($backup) { Copy-Item $backup $dk -Force; Remove-Item $backup -Force } else { Remove-Item $dk -Force }
}
if ($code) { throw 'Gradle build failed' }

Copy-Item android\app\build\outputs\apk\debug\app-debug.apk Neal-App.apk -Force
Write-Host "== Done: $PSScriptRoot\Neal-App.apk"
