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
adb shell settings put system font_scale 1
adb shell settings put global animator_duration_scale 0
adb shell cmd uimode night no >/dev/null
adb install -r -g fixtures/current.apk
for screen in home knowledge shop; do
  adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen "$screen" --es qa_lang ru --ez qa_onboarded true --es qa_period DAY > "ci-out/insets-$screen-start.txt"
  sleep 3
  adb exec-out screencap -p > "ci-out/screens/insets-$screen-start.png"
  read -r shot_width shot_height < <(python3 -c 'from PIL import Image; import sys; print(*Image.open(sys.argv[1]).size)' "ci-out/screens/insets-$screen-start.png")
  # The emulator may expose a different display than the requested wm size.
  # Begin safely above the floating dock, using measured screen coordinates.
  for gesture in 1 2 3 4 5 6 7 8; do
    adb shell input swipe "$((shot_width / 3))" "$((shot_height * 2 / 3))" "$((shot_width / 3))" "$((shot_height / 4))" 650
  done
  adb exec-out screencap -p > "ci-out/screens/insets-$screen-bottom.png"
  python3 tools/ci/check_screen.py "ci-out/screens/insets-$screen-bottom.png"
  adb shell uiautomator dump /sdcard/insets.xml
  adb pull /sdcard/insets.xml "ci-out/insets-$screen.xml"
done
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
printf '%s\n' 'Bottom-of-page captures on the exact reused app build; measured display coordinates.' > ci-out/complete.txt
