#!/usr/bin/env bash
set -euo pipefail
pkg=com.zikrtime.app
mkdir -p ci-out/screens
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
adb shell wm size 1080x2400
adb shell wm density 420
cleanup() {
  adb logcat -d -b crash > ci-out/crashes.txt || true
  adb shell settings put system font_scale 1 >/dev/null || true
  adb shell settings put global animator_duration_scale 1 >/dev/null || true
  adb shell wm size reset >/dev/null || true
  adb shell wm density reset >/dev/null || true
}
trap cleanup EXIT
capture() {
  local name="$1" screen="$2"
  adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen "$screen" --es qa_lang ru --ez qa_onboarded true > "ci-out/$name-start.txt"
  sleep 3
  adb shell pidof "$pkg" >/dev/null
  adb exec-out screencap -p > "ci-out/screens/$name.png"
  python3 tools/ci/check_screen.py "ci-out/screens/$name.png"
}
measure() {
  adb shell dumpsys gfxinfo "$pkg" reset >/dev/null
  sleep 5
  adb shell dumpsys gfxinfo "$pkg" > "ci-out/$1-gfx.txt"
  adb shell dumpsys meminfo "$pkg" > "ci-out/$1-memory.txt"
}
# Disposable signatures allow update testing without touching the release key.
adb install -r -g fixtures/previous.apk
capture baseline knowledge
measure baseline
python3 tools/ci/features_controls.py seed-upgrade
adb install -r -g fixtures/current.apk
capture upgraded home
python3 tools/ci/features_controls.py verify-upgrade
python3 tools/ci/features_controls.py appearance
capture current knowledge
measure current
for mode in no yes; do
  adb shell cmd uimode night "$mode" >/dev/null
  for screen in home azkar tasbih widgets knowledge focus shop shop_product shop_seller places more alarm ramadan zakat qibla progress review; do
    capture "$screen-$mode" "$screen"
  done
 done
# 320dp and 200% text; each screen scrolls, titles must not be reduced to fit.
adb shell wm size 720x1600
adb shell wm density 360
adb shell settings put system font_scale 2
for screen in home knowledge more alarm focus zakat widgets; do capture "$screen-large" "$screen"; done
adb shell input swipe 5 1300 5 350 400
adb shell input swipe 5 1300 5 350 400
adb exec-out screencap -p > ci-out/screens/widgets-large-lower.png
python3 tools/ci/check_screen.py ci-out/screens/widgets-large-lower.png
adb shell settings put system font_scale 1
adb shell wm size 1080x2400
adb shell wm density 420
# Architectural light across all four day periods, and a real short motion recording.
adb shell cmd uimode night no >/dev/null
for period in DAWN DAY SUNSET NIGHT; do
  adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen home --es qa_lang ru --ez qa_onboarded true --es qa_period "$period" > "ci-out/courtyard-$period-start.txt"
  sleep 2
  adb exec-out screencap -p > "ci-out/screens/courtyard-$period.png"
  python3 tools/ci/check_screen.py "ci-out/screens/courtyard-$period.png"
done
# Existing functional assertions, limited to UI affected by shared components.
capture progress-before-toggle progress
python3 tools/ci/features_controls.py progress
capture progress-before-enable progress
python3 tools/ci/features_controls.py enable-progress
python3 tools/ci/features_controls.py learning
adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen home --es qa_lang ru --ez qa_onboarded true --es qa_period DAY > ci-out/motion-start.txt
sleep 2
adb exec-out screencap -p > ci-out/screens/motion-before.png
if timeout 25 adb shell screenrecord --size 720x1280 --time-limit 8 --bit-rate 1000000 /sdcard/zikr-architecture.mp4 > ci-out/recording.txt 2>&1; then
  adb wait-for-device
  adb pull /sdcard/zikr-architecture.mp4 ci-out/architecture-motion.mp4
else
  printf '%s\n' 'Video capture failed; functional UI results were collected before recording.' > ci-out/recording-unavailable.txt
  adb wait-for-device
fi
adb exec-out screencap -p > ci-out/screens/motion-after.png
adb shell settings put global animator_duration_scale 0
capture motion-disabled home
adb shell settings put global animator_duration_scale 1
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
printf '%s\n' 'Emulator-only UI capture and targeted assertions completed.' > ci-out/complete.txt
