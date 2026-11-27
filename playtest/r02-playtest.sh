#!/usr/bin/env sh
# R02 native playtest (DEV ONLY). Plain-English steps: playtest/OWNER-INSTRUCTIONS.md
#   ./r02-playtest.sh prepare | record-lock | check PACKAGE-FOLDER [--lane-inputs ZIP] [--scale S] | launch ... | wipe
set -e
REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to Temurin 11.0.33 first}"
: "${1:?usage: r02-playtest.sh prepare|record-lock|check|launch|wipe [PACKAGE-FOLDER] [--scale S]}"
if [ "$1" = prepare ]; then
  "$REPO/gradlew" --no-daemon -p "$REPO" launcherJar
  "$REPO/gradlew" --no-daemon -p "$REPO/playtest/host-plugin" playtestClasspath
  echo "Prepared. Next: ./r02-playtest.sh check PACKAGE-FOLDER"
  exit 0
fi
exec "$JAVA_HOME/bin/java" -jar "$REPO/build/libs/raccoon-r02-launcher.jar" "$@" --repo "$REPO"
