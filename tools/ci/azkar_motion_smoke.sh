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
python3 tools/ci/azkar_motion_ui.py
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
