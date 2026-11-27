@echo off
rem Full verification on the pinned toolchain (Temurin 11.0.33 as JAVA_HOME, Gradle 8.10 wrapper), then real-display tests.
cd /d "%~dp0"
call gradlew.bat --no-daemon clean check %* || exit /b 1
call gradlew.bat --no-daemon displayTests || exit /b 1
