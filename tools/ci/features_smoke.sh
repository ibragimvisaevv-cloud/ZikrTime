#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-out/screens
pkg=com.zikrtime.app
current_apk="$1"
previous_apk="$2"
focus_apk="$3"
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
cleanup() {
  adb logcat -d -b crash > ci-out/crashes.txt || true
  adb shell svc wifi enable >/dev/null || true
  adb shell svc data enable >/dev/null || true
  adb shell settings put system font_scale 1 >/dev/null || true
  adb shell wm size reset >/dev/null || true
  adb shell wm density reset >/dev/null || true
  adb shell settings put secure enabled_accessibility_services null >/dev/null || true
  adb shell settings put secure accessibility_enabled 0 >/dev/null || true
}
trap cleanup EXIT
capture() {
  local name="$1" screen="$2" lang="${3:-en}"
  adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen "$screen" --es qa_lang "$lang" --ez qa_onboarded true > "ci-out/$name-start.txt"
  sleep 3
  adb shell pidof "$pkg" >/dev/null
  adb exec-out screencap -p > "ci-out/screens/$name.png"
  python3 tools/ci/check_screen.py "ci-out/screens/$name.png"
}
# Synthetic old data, then APK update without uninstall/data clearing.
# Both fixtures are DEBUG APKs re-signed with a disposable test key; actual release
# certificate is checked separately in the build job. This does not test an ARM phone.
adb install -r -g "$previous_apk"
adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen home --es qa_lang en --ez qa_demo true > ci-out/previous-start.txt
sleep 3
python3 tools/ci/features_controls.py seed-upgrade
adb install -r -g "$current_apk"
capture upgraded-home home
python3 tools/ci/features_controls.py verify-upgrade
capture upgraded-restart home
python3 tools/ci/features_controls.py verify-upgrade
# Persisted progress switch operated through UI, then real process restart.
capture progress-controls progress
python3 tools/ci/features_controls.py progress
capture progress-disabled progress
capture home-progress-disabled home
capture review-progress-disabled review
python3 tools/ci/features_controls.py verify-disabled
capture progress-restore progress
python3 tools/ci/features_controls.py enable-progress
python3 tools/ci/features_controls.py learning
adb install -r "$focus_apk"
python3 tools/ci/features_controls.py focus
# Fresh install data path, without demonstration history.
adb shell pm clear "$pkg" >/dev/null
adb shell am start -S -W -n "$pkg/.MainActivity" > ci-out/fresh-start.txt
sleep 3
adb exec-out screencap -p > ci-out/screens/fresh-onboarding.png
python3 tools/ci/check_screen.py ci-out/screens/fresh-onboarding.png
for mode in no yes; do
  adb shell cmd uimode night "$mode" >/dev/null
  for screen in home review progress azkar focus places; do capture "$screen-$mode" "$screen"; done
 done
for lang in ru en uz tg kk ky az tr; do capture "azkar-$lang" azkar "$lang"; done
adb shell wm size 720x1600
adb shell wm density 360
adb shell settings put system font_scale 1.3
for screen in home progress azkar focus places; do capture "$screen-large" "$screen" ru; done
adb shell settings put system font_scale 1
adb shell wm size reset
adb shell wm density reset
# Offline cache/empty-state and denied geolocation must still leave UI reachable.
adb shell pm revoke "$pkg" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm revoke "$pkg" android.permission.ACCESS_COARSE_LOCATION || true
adb shell svc wifi disable
adb shell svc data disable
capture places-offline-denied places
capture azkar-offline azkar
capture review-offline review
capture home-offline home
# Revoked usage access/overlay: the focus screen must report actual capability.
adb shell appops set "$pkg" GET_USAGE_STATS deny
adb shell appops set "$pkg" SYSTEM_ALERT_WINDOW deny
capture focus-permissions-denied focus
adb shell dumpsys meminfo "$pkg" > ci-out/memory.txt
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then cat ci-out/crashes.txt; exit 1; fi
printf '%s\n' 'Screens and specified UI assertions completed. No real-phone or live-server claims.' > ci-out/smoke-completed.txt
