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
  adb shell dumpsys gfxinfo com.zikrtime.app > ci-out/gfx.txt || true
  adb shell settings put system font_scale 1 >/dev/null || true
  adb shell settings put global animator_duration_scale 1 >/dev/null || true
  adb shell settings put secure enabled_accessibility_services null >/dev/null || true
  adb shell settings put secure accessibility_enabled 0 >/dev/null || true
}
trap cleanup EXIT
adb shell wm size 1080x1920
adb shell wm density 420
adb install -r fixtures/current.apk
adb install -r fixtures/focus-target.apk
python3 tools/ci/whole_app_ui.py "$1"
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
