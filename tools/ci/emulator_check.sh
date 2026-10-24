#!/usr/bin/env bash
# Устанавливает приложение на эмулятор, открывает экраны и снимает скриншоты.
# Аргумент: папка со сборкой (app/build/outputs/...).
set -u
BUILD="$1"
OUT=ci-out
mkdir -p "$OUT/screens"
PKG=com.zikrtime.app

# Результаты отправляем в ветку ci-artifacts из самого скрипта — даже если дальше что-то упадёт.
publish() {
  [ -n "${GH_TOKEN:-}" ] || return 0
  echo "run ${CI_RUN:-?} commit ${CI_SHA:-?}" > "$OUT/RUN.txt"
  cp -r "$BUILD/ci-out/." "$OUT/" 2>/dev/null || true
  rm -rf /tmp/ci-publish && mkdir -p /tmp/ci-publish && cp -r "$OUT/." /tmp/ci-publish/
  ( cd /tmp/ci-publish && git init -q && git checkout -q -b ci-artifacts && git add -A &&
    git -c user.name=ci -c user.email=ci@users.noreply.github.com commit -qm "CI ${CI_RUN:-?}" &&
    git push -qf "https://x-access-token:${GH_TOKEN}@github.com/${CI_REPO}.git" ci-artifacts ) || echo "publish failed"
}
trap publish EXIT
progress() { echo "$(date -u +%H:%M:%S) $*" >> "$OUT/progress.txt"; }
DEBUG_APK="$BUILD/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="$BUILD/app/build/outputs/apk/release/app-release.apk"

adb wait-for-device
adb logcat -G 16M 2>/dev/null || true
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard 2>/dev/null || true
# Экран 720×1520 с плотностью 320 dpi = 360×760 dp — как у большинства недорогих телефонов.
adb shell wm density 320
adb shell wm size > "$OUT/display.txt"; adb shell wm density >> "$OUT/display.txt"
progress "boot ok"

shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/screens/$1.png"; }

# 1. Release-сборка: ставится и запускается без падений
adb install -r "$RELEASE_APK" > "$OUT/install-release.txt" 2>&1
sleep 2
adb logcat -b all -c
adb shell am start -W -n $PKG/.MainActivity > /dev/null
shot release-start 6
adb logcat -d -b crash > "$OUT/crash-release.txt"
adb shell am force-stop $PKG
sleep 1
adb uninstall $PKG > /dev/null

# 2. Debug-сборка: демо-данные и все экраны (см. QaHooks в коде)
adb install -r -g "$DEBUG_APK" > "$OUT/install-debug.txt" 2>&1
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
# Система ещё несколько секунд «доустанавливает» пакет — ждём и только потом чистим журнал.
sleep 5
adb logcat -b all -c

open_screen() { # screen lang
  adb shell am start -S -W -n $PKG/.MainActivity --es qa_screen "$1" --es qa_lang "$2" --ez qa_demo true > /dev/null
}

run_set() { # файл-языков файл-экранов префикс
  for lang in $(cat "$1"); do
    for s in $(cat "$2"); do
      progress "$lang $s"
      open_screen "$s" "$lang"
      shot "${3}${lang}_${s}" 5
    done
  done
}

run_set tools/ci/langs.txt tools/ci/screens.txt ""
adb logcat -d -b crash > "$OUT/crash-debug.txt"
publish # промежуточный результат
[ -f tools/ci/langs_short.txt ] && run_set tools/ci/langs_short.txt tools/ci/screens_short.txt ""

# Тёмная тема (у многих телефон в ночном режиме): главные экраны по-русски
adb shell cmd uimode night yes
sleep 2
for s in home more knowledge quiz_cards shop shop_seller focus prayer_settings alarm tasbih; do
  progress "dark $s"
  open_screen "$s" ru
  shot "dark_ru_${s}" 5
done
adb shell cmd uimode night no
sleep 2

# 3. Крупный шрифт (как у многих пользователей): главные экраны по-русски
adb shell settings put system font_scale 1.3
for s in home alarm focus quiz; do
  open_screen "$s" ru
  shot "large_ru_${s}" 5
done
adb shell settings put system font_scale 1.0

adb logcat -d -b crash > "$OUT/crash-debug.txt"
adb logcat -d -t 4000 > "$OUT/logcat.txt"
adb shell am force-stop $PKG
echo done > "$OUT/emulator-done.txt"
progress "done"
