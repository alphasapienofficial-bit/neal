@echo off
cd /d "%~dp0"
echo Building Neal App... a log is saved in build.log
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { .\build-apk.ps1 *>&1 | Tee-Object -FilePath build.log; if ($?) { exit 0 } else { exit 1 } }"
echo.
echo Finished. You can close this window.
pause
