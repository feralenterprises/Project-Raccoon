@echo off
rem Launch the workbench on Windows. Prebuilt jar runs on any Java 11+; building needs Temurin 11.0.33.
cd /d "%~dp0"
if not exist build\libs\raccoon-ui-workbench.jar call gradlew.bat --no-daemon -q workbenchJar || exit /b 1
java -jar build\libs\raccoon-ui-workbench.jar --assets=review-assets %*
