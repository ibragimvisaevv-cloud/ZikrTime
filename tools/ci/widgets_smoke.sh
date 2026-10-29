#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-out/screens
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
cleanup() {
  adb logcat -d -b crash > ci-out/crashes.txt || true
  adb logcat -d -s ZikrWidgets > ci-out/widget-errors.txt || true
  adb shell dumpsys appwidget > ci-out/widget-host.txt || true
  adb shell dumpsys alarm > ci-out/alarms.txt || true
}
trap cleanup EXIT
adb shell wm size 1080x1920
adb shell wm density 420
adb install -r android-out/outputs/apk/debug/app-debug.apk
python3 tools/ci/widgets_launcher.py
adb logcat -d -b crash > ci-out/crashes.txt
adb logcat -d -s ZikrWidgets > ci-out/widget-errors.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt || grep -q 'Update failed' ci-out/widget-errors.txt; then exit 1; fi
