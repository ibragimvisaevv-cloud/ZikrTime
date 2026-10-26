#!/usr/bin/env bash
# Запускает приложение в симуляторе iPhone, открывает экраны и снимает скриншоты.
set -euo pipefail
OUT=ci-out/screens
mkdir -p "$OUT"
trap 'find ~/Library/Logs/DiagnosticReports -name "ZikrTime*" -newer "$APP" -exec cp {} ci-out/ \; 2>/dev/null || true' EXIT
APP=ios/build/Build/Products/Debug-iphonesimulator/ZikrTime.app
BUNDLE=com.zikrtime.ios

# Simulator services can stall on hosted Macs. Bound each wait and retain build artifacts.
timed() {
  python3 -c 'import subprocess,sys; sys.exit(subprocess.run(sys.argv[2:], timeout=int(sys.argv[1])).returncode)' "$@"
}

CHECK_ENV="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/zikrtime-screen-check"
python3 -m venv "$CHECK_ENV"
"$CHECK_ENV/bin/python" -m pip install --quiet 'Pillow>=11,<13'

RU_SCREENS="onboarding onboarding_welcome onboarding_city home knowledge quiz quiz_cards shop shop_seller shop_product qibla more tasbih alarm prayer_settings language about focus places ramadan zakat"
EN_SCREENS="$RU_SCREENS"
EXTRA_LANGUAGES="uz tg kk"
DARK_SCREENS="home more knowledge quiz_cards shop shop_seller prayer_settings alarm"
if [ "${IOS_SCREEN_SUITE:-full}" = release ]; then
  RU_SCREENS="home knowledge alarm shop_seller places ramadan zakat"
  EN_SCREENS="home places ramadan zakat"
  EXTRA_LANGUAGES=""
  DARK_SCREENS="home places ramadan zakat"
fi

# Выбираем iPhone на самой новой версии iOS (обычный, не Pro/Max — ближе к массовому телефону)
UDID=$(xcrun simctl list devices available -j | python3 -c '
import json, sys, re
data = json.load(sys.stdin)["devices"]
best = None
for runtime, devices in data.items():
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    if not m:
        continue
    version = (int(m.group(1)), int(m.group(2)))
    for d in devices:
        name = d["name"]
        if not name.startswith("iPhone"):
            continue
        plain = 0 if re.fullmatch(r"iPhone \d+", name) else 1
        key = (version, -plain, name)
        if best is None or key > best[0]:
            best = (key, d["udid"], name, runtime)
print(best[1])
print(best[2] + " / " + best[3], file=sys.stderr)
')
echo "Симулятор: $UDID"
timed 30 xcrun simctl boot "$UDID" 2>/dev/null || true
timed 600 xcrun simctl bootstatus "$UDID" -b
xcrun simctl status_bar "$UDID" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3 2>/dev/null || true
timed 60 xcrun simctl install "$UDID" "$APP"

capture() {
  local screen="$1" lang="$2" file="$3" launch pid
  timed 20 xcrun simctl terminate "$UDID" "$BUNDLE" 2>/dev/null || true
  echo "Launching $lang/$screen"
  launch=$(timed 90 xcrun simctl launch "$UDID" "$BUNDLE" -qa_screen "$screen" -qa_lang "$lang" -qa_demo 1)
  pid=${launch##*: }
  for attempt in {1..12}; do
    sleep 3
    # Simulator app processes run on the host. A screenshot alone can hide a crash.
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "App exited on $lang/$screen: $launch" >&2
      return 1
    fi
    # The hosted Intel simulator may spend over 30 seconds encoding a full-size PNG.
    # Keep the command's diagnostics and never reuse an older frame after a failure.
    python3 -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).unlink(missing_ok=True)' "$OUT/$file.png"
    if ! timed 120 xcrun simctl io "$UDID" screenshot "$OUT/$file.png" > "ci-out/capture-$file.log" 2>&1; then
      cat "ci-out/capture-$file.log" >&2
      echo "Screenshot command failed on $lang/$screen (attempt $attempt)" >&2
      continue
    fi
    if "$CHECK_ENV/bin/python" tools/ci/check_screen.py "$OUT/$file.png"; then
      echo "$lang $screen"
      return 0
    fi
  done
  echo "Blank screen: $lang/$screen" >&2
  return 1
}

for lang in ru en; do
  screens="$RU_SCREENS"
  if [ "$lang" = en ]; then screens="$EN_SCREENS"; fi
  for s in $screens; do
    capture "$s" "$lang" "${lang}_${s}"
  done
done
for lang in $EXTRA_LANGUAGES; do
  for s in home knowledge quiz alarm; do
    capture "$s" "$lang" "${lang}_${s}"
  done
done
# Тёмная тема
xcrun simctl ui "$UDID" appearance dark
for s in $DARK_SCREENS; do
  capture "$s" ru "dark_ru_${s}"
done
xcrun simctl ui "$UDID" appearance light
echo done > ci-out/screens-done.txt
