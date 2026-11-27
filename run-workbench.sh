#!/usr/bin/env sh
# Launch the workbench. Uses the prebuilt jar if present (any Java 11+ runtime), else builds with the pinned toolchain.
cd "$(dirname "$0")"
[ -f build/libs/raccoon-ui-workbench.jar ] || ./gradlew --no-daemon -q workbenchJar || exit 1
exec java -jar build/libs/raccoon-ui-workbench.jar --assets=review-assets "$@"
