@echo off
rem Workbench driven by the real FR-002 lifecycle. To build it:
rem   gradlew.bat fetchFoundationCore -PfoundationRepo=C:\path\to\Project-Raccoon
rem   gradlew.bat integrationWorkbenchJar
cd /d "%~dp0"
if not exist build\libs\raccoon-ui-integration-workbench.jar (echo Build it first, see comments in this file. & exit /b 1)
java -jar build\libs\raccoon-ui-integration-workbench.jar --assets=review-assets %*
