#!/usr/bin/env bash
set -euo pipefail
pkg=com.zikrtime.app
mkdir -p ci-out/screens
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
cleanup() {
  adb logcat -d -b crash > ci-out/crashes.txt || true
  adb shell settings put system font_scale 1 >/dev/null || true
  adb shell settings put global animator_duration_scale 1 >/dev/null || true
  adb shell wm size reset >/dev/null || true
  adb shell wm density reset >/dev/null || true
}
trap cleanup EXIT
adb shell wm size 1080x2400
adb shell wm density 420
adb install -r -g fixtures/current.apk
# Seed the same synthetic location/settings fixture used by the full regression run.
adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen home --es qa_lang ru --ez qa_onboarded true
sleep 2
python3 tools/ci/features_controls.py seed-upgrade
adb shell settings put global animator_duration_scale 0
python3 tools/ci/features_controls.py appearance
python3 tools/ci/horizon_interaction.py
capture() {
  local name="$1" screen="$2" lang="${3:-ru}"
  adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen "$screen" --es qa_lang "$lang" --ez qa_onboarded true > "ci-out/$name-start.txt"
  sleep 3
  adb shell pidof "$pkg" >/dev/null
  adb exec-out screencap -p > "ci-out/screens/$name.png"
  python3 tools/ci/check_screen.py "ci-out/screens/$name.png"
}
for mode in no yes; do
  adb shell cmd uimode night "$mode" >/dev/null
  for screen in knowledge shop focus tasbih widgets more; do capture "$screen-$mode" "$screen"; done
done
adb shell cmd uimode night no >/dev/null
for screen in home knowledge shop; do
  capture "$screen-bottom-start" "$screen"
  read -r shot_width shot_height < <(python3 -c 'from PIL import Image; import sys; print(*Image.open(sys.argv[1]).size)' "ci-out/screens/$screen-bottom-start.png")
  for gesture in 1 2 3 4 5 6; do adb shell input swipe "$((shot_width / 3))" "$((shot_height * 2 / 3))" "$((shot_width / 3))" "$((shot_height / 4))" 700; done
  adb exec-out screencap -p > "ci-out/screens/$screen-bottom.png"
done
adb shell wm size 720x1280
adb shell wm density 360
adb shell settings put system font_scale 2
for screen in knowledge shop more; do capture "$screen-large" "$screen"; done
adb shell settings put system font_scale 1.3
capture knowledge-long knowledge az
capture shop-long shop tr
adb shell settings put system font_scale 1
adb shell wm size 1080x2400
adb shell wm density 420
adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen home --es qa_lang ru --ez qa_onboarded true --es qa_period NIGHT
sleep 2
adb exec-out screencap -p > ci-out/screens/horizon-night.png
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
printf '%s\n' 'Final app: manual appearance, gesture scroll/return, compact header, reduced motion and targeted visual captures completed.' > ci-out/complete.txt
