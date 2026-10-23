#!/usr/bin/env bash
# Устанавливает приложение на эмулятор, открывает экраны и снимает скриншоты.
# Аргумент: папка со сборкой (app/build/outputs/...).
set -u
BUILD="$1"
OUT=ci-out
mkdir -p "$OUT/screens"
PKG=com.zikrtime.app
DEBUG_APK="$BUILD/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="$BUILD/app/build/outputs/apk/release/app-release.apk"

adb wait-for-device
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard 2>/dev/null || true

shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/screens/$1.png"; }

# 1. Release-сборка: ставится и запускается без падений
adb install -r "$RELEASE_APK" > "$OUT/install-release.txt" 2>&1
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity > /dev/null
shot release-start 6
adb logcat -d -b crash > "$OUT/crash-release.txt"
adb uninstall $PKG > /dev/null

# 2. Debug-сборка: демо-данные и все экраны (см. QaHooks в коде)
adb install -r -g "$DEBUG_APK" > "$OUT/install-debug.txt" 2>&1
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb logcat -c

open_screen() { # screen lang
  adb shell am start -S -W -n $PKG/.MainActivity --es qa_screen "$1" --es qa_lang "$2" --ez qa_demo true > /dev/null
}

SCREENS=$(cat tools/ci/screens.txt 2>/dev/null || echo "onboarding")
LANGS=$(cat tools/ci/langs.txt 2>/dev/null || echo "ru")
for lang in $LANGS; do
  for s in $SCREENS; do
    open_screen "$s" "$lang"
    shot "${lang}_${s}" 5
  done
done

adb logcat -d -b crash > "$OUT/crash-debug.txt"
adb logcat -d -t 3000 > "$OUT/logcat.txt"
adb shell am force-stop $PKG
echo done > "$OUT/emulator-done.txt"
