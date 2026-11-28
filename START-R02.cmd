@echo off
setlocal EnableExtensions DisableDelayedExpansion
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0playtest\Start-OwnerPlaytest.ps1"
set "R02_EXIT=%ERRORLEVEL%"
if not "%R02_EXIT%"=="0" (
  echo.
  echo Raccoon stopped with exit code %R02_EXIT%. Keep the message above for Solis.
  pause
)
exit /b %R02_EXIT%
