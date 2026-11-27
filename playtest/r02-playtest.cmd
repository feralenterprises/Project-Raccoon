@echo off
rem R02 native playtest (DEV ONLY). Plain-English steps: playtest\OWNER-INSTRUCTIONS.md
rem   r02-playtest.cmd prepare                     build the host, resolve RuneLite 1.13.8 (internet, once)
rem   r02-playtest.cmd record-lock                 first time only: record the classpath hashes
rem   r02-playtest.cmd check  "PACKAGE-FOLDER" [--lane-inputs "ZIP"]   verify everything, start nothing
rem   r02-playtest.cmd launch "PACKAGE-FOLDER"     start the playtest (only once it is cleared)
rem   r02-playtest.cmd wipe                        delete the isolated playtest profile
setlocal
set "REPO=%~dp0.."
if "%JAVA_HOME%"=="" ( echo Set JAVA_HOME to Temurin 11.0.33 first. & exit /b 2 )
if "%~1"=="" ( echo usage: r02-playtest.cmd prepare^|record-lock^|check^|launch^|wipe ["PACKAGE-FOLDER"] [--scale S] & exit /b 2 )
if /i "%~1"=="prepare" (
  call "%REPO%\gradlew.bat" --no-daemon -p "%REPO%" launcherJar || exit /b 1
  call "%REPO%\gradlew.bat" --no-daemon -p "%REPO%\playtest\host-plugin" playtestClasspath || exit /b 1
  echo Prepared. Next: r02-playtest.cmd check "PACKAGE-FOLDER"
  exit /b 0
)
"%JAVA_HOME%\bin\java.exe" -jar "%REPO%\build\libs\raccoon-r02-launcher.jar" %* --repo "%REPO%"
exit /b %ERRORLEVEL%
