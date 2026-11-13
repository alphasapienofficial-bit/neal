# Builds Neal-App.apk on this Windows computer.
# Needs: Node.js, JDK 21 (Android Studio's bundled one works), Android SDK.
# Run from this folder:  powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
# Native tools (npm, gradle) print warnings on stderr; only their exit codes decide failure.
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

# Capacitor 7 needs Java 21. Use one already on this PC, or download one (once) into AppData.
function Test-Jdk21($dir) {
  if (-not (Test-Path "$dir\bin\java.exe")) { return $false }
  $rel = Join-Path $dir 'release'
  if (Test-Path $rel) { return [bool](Select-String -Path $rel -Pattern 'JAVA_VERSION="21' -Quiet) }
  return $false
}
$cands = @()
if ($env:JAVA_HOME) { $cands += $env:JAVA_HOME }
$cands += "$env:ProgramFiles\Android\Android Studio\jbr"
foreach ($root in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft", "$env:USERPROFILE\.gradle\jdks", "$env:LOCALAPPDATA\neal-jdk21")) {
  if (Test-Path $root) { $cands += (Get-ChildItem $root -Directory | ForEach-Object { $_.FullName }) }
}
$jdk = $cands | Where-Object { Test-Jdk21 $_ } | Select-Object -First 1
if (-not $jdk) {
  Write-Host '== Downloading Java 21 (one time, about 190 MB)'
  $dest = "$env:LOCALAPPDATA\neal-jdk21"
  New-Item -ItemType Directory -Force $dest | Out-Null
  $zip = "$env:TEMP\neal-jdk21.zip"
  $ProgressPreference = 'SilentlyContinue'
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip -UseBasicParsing
  Expand-Archive $zip -DestinationPath $dest -Force
  Remove-Item $zip -Force
  $jdk = Get-ChildItem $dest -Directory | Where-Object { Test-Jdk21 $_.FullName } | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) { throw 'Could not find or download Java 21.' }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
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

Copy-Item native\*.java android\app\src\main\java\com\neal\app\ -Force -ErrorAction Stop

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
Copy-Item native\res\* $res -Recurse -Force -ErrorAction Stop

# Some plugins compile with a Java 21 toolchain: let Gradle download one if this PC lacks it
$settings = 'android\settings.gradle'
$sg = Get-Content $settings -Raw
if ($sg -notmatch 'foojay-resolver') {
  Set-Content $settings ("plugins {`n    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'`n}`n`n" + $sg) -NoNewline
}

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
try { .\gradlew.bat assembleDebug --console=plain; $code = $LASTEXITCODE }
finally {
  Pop-Location
  if ($backup) { Copy-Item $backup $dk -Force; Remove-Item $backup -Force } else { Remove-Item $dk -Force }
}
if ($code) { throw 'Gradle build failed' }

Copy-Item android\app\build\outputs\apk\debug\app-debug.apk Neal-App.apk -Force -ErrorAction Stop
Write-Host "== Done: $PSScriptRoot\Neal-App.apk"
