#!/bin/bash
# fail if any commands fails
set -e

./gradlew clean assembleDebug createDebugTestCoverage  --stacktrace
