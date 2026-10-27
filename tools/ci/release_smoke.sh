#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-out/screens
pkg=com.zikrtime.app
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install -r -g "$1"
adb logcat -c
for lang in ru en; do
  for screen in home knowledge quiz qibla shop more tasbih alarm prayer_settings places ramadan zakat; do
    adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen "$screen" --es qa_lang "$lang" --ez qa_demo true > "ci-out/start-$lang-$screen.txt"
    image="ci-out/screens/$lang-$screen.png"
    ready=false
    for attempt in {1..10}; do
      sleep 2
      adb shell pidof "$pkg" >/dev/null
      adb exec-out screencap -p > "$image"
      if python3 tools/ci/check_screen.py "$image"; then ready=true; break; fi
    done
    if [ "$ready" != true ]; then echo "Blank screen: $lang/$screen" >&2; exit 1; fi
  done
done
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE "FATAL EXCEPTION|Fatal signal" ci-out/crashes.txt; then cat ci-out/crashes.txt; exit 1; fi
echo "Screen launch smoke passed"
