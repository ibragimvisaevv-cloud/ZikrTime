#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-out/screens
pkg=com.zikrtime.app
adb wait-for-device
adb install -r -g "$1"
adb logcat -c
for lang in ru en; do
  for screen in home knowledge quiz qibla shop more tasbih alarm prayer_settings places ramadan zakat; do
    adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen "$screen" --es qa_lang "$lang" --ez qa_demo true > "ci-out/start-$lang-$screen.txt"
    sleep 3
    adb shell pidof "$pkg" >/dev/null
    adb exec-out screencap -p > "ci-out/screens/$lang-$screen.png"
  done
done
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE "FATAL EXCEPTION|Fatal signal" ci-out/crashes.txt; then cat ci-out/crashes.txt; exit 1; fi
echo "Screen launch smoke passed"
