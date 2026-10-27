#!/usr/bin/env bash
set -euo pipefail
if [ "${MAP_ONLY:-false}" = true ]; then
  bash tools/ci/map_loading_check.sh android-out/outputs/apk/debug/app-debug.apk
else
  bash tools/ci/features_smoke.sh fixtures/current.apk fixtures/previous.apk fixtures/focus.apk
fi
