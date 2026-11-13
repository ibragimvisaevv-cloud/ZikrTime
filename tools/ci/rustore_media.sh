#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-out/store
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell wm size 1080x1920
adb shell wm density 420
adb shell settings put system font_scale 1
adb logcat -c
trap 'adb logcat -d -b crash > ci-out/crashes.txt' EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
python3 "${RUSTORE_MEDIA_SCRIPT:-tools/ci/rustore_media.py}"
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
