#!/usr/bin/env sh
# Full verification on the pinned toolchain (Temurin 11.0.33, Gradle 8.10 wrapper).
# Optional inputs: -PfoundationRepo=PATH (then run twice: fetch, check) and -PruneliteSource=PATH.
# Real-display tests need a screen: xvfb-run ./gradlew displayTests on Linux CI.
cd "$(dirname "$0")"
exec ./gradlew --no-daemon clean check "$@"
