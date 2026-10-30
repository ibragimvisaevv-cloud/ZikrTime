#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-out/screens
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
trap 'adb logcat -d -b crash > ci-out/crashes.txt; adb shell dumpsys gfxinfo com.zikrtime.app > ci-out/gfx.txt' EXIT
adb shell wm size 1080x1920
adb shell wm density 420
adb install -r app/build/outputs/apk/debug/app-debug.apk
if [ "${QURAN_QA_MODE:-all}" = features ]; then
  python3 tools/ci/quran_features_ui.py
else
  python3 tools/ci/quran_reader_ui.py "${QURAN_QA_MODE:-all}"
fi
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
