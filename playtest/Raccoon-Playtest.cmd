@echo off
setlocal
cd /d "%~dp0"
rem Project Raccoon FR-006 playtest kit: opens the menu. Everything stays inside this folder.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Raccoon-Playtest.ps1" %*
pause
