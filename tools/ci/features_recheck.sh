#!/usr/bin/env bash
set -euo pipefail
if [ "${FOCUS_ONLY:-false}" = true ]; then
  mkdir -p ci-out/screens
  adb wait-for-device
  adb shell svc power stayon true
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  adb install -r -g android-out/outputs/apk/debug/app-debug.apk
  adb install -r fixtures/focus.apk
  adb shell am start -S -W -n com.zikrtime.app/.MainActivity --es qa_screen focus --es qa_lang en --ez qa_onboarded true
  sleep 3
  python3 tools/ci/features_controls.py focus-visual
elif [ "${MAP_ONLY:-false}" = true ]; then
  bash tools/ci/map_loading_check.sh android-out/outputs/apk/debug/app-debug.apk
else
  bash tools/ci/features_smoke.sh fixtures/current.apk fixtures/previous.apk fixtures/focus.apk
fi
