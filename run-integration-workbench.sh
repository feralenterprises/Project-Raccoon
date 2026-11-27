#!/usr/bin/env sh
# Workbench driven by the real FR-002 lifecycle. Building it needs a local Project-Raccoon clone:
#   ./gradlew fetchFoundationCore -PfoundationRepo=/path/to/Project-Raccoon && ./gradlew integrationWorkbenchJar
cd "$(dirname "$0")"
[ -f build/libs/raccoon-ui-integration-workbench.jar ] || { echo "Build it first (see comment in this script)."; exit 1; }
exec java -jar build/libs/raccoon-ui-integration-workbench.jar --assets=review-assets "$@"
