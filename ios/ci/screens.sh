#!/usr/bin/env bash
# Запускает приложение в симуляторе iPhone, открывает экраны и снимает скриншоты.
set -euo pipefail
OUT=ci-out/screens
mkdir -p "$OUT"
APP=ios/build/Build/Products/Debug-iphonesimulator/ZikrTime.app
BUNDLE=com.zikrtime.ios

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
xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b
xcrun simctl status_bar "$UDID" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3 2>/dev/null || true
xcrun simctl install "$UDID" "$APP"

for lang in ru en; do
  for s in onboarding onboarding_welcome onboarding_city home knowledge quiz quiz_cards shop shop_seller shop_product qibla more tasbih alarm prayer_settings language about focus places ramadan zakat; do
    xcrun simctl terminate "$UDID" "$BUNDLE" 2>/dev/null || true
    xcrun simctl launch "$UDID" "$BUNDLE" -qa_screen "$s" -qa_lang "$lang" -qa_demo 1 > /dev/null
    sleep 4
    xcrun simctl io "$UDID" screenshot "$OUT/${lang}_${s}.png" > /dev/null 2>&1
    echo "$lang $s"
  done
done
for lang in uz tg kk; do
  for s in home knowledge quiz alarm; do
    xcrun simctl terminate "$UDID" "$BUNDLE" 2>/dev/null || true
    xcrun simctl launch "$UDID" "$BUNDLE" -qa_screen "$s" -qa_lang "$lang" -qa_demo 1 > /dev/null
    sleep 4
    xcrun simctl io "$UDID" screenshot "$OUT/${lang}_${s}.png" > /dev/null 2>&1
  done
done
# Тёмная тема
xcrun simctl ui "$UDID" appearance dark
for s in home more knowledge quiz_cards shop shop_seller prayer_settings alarm; do
  xcrun simctl terminate "$UDID" "$BUNDLE" 2>/dev/null || true
  xcrun simctl launch "$UDID" "$BUNDLE" -qa_screen "$s" -qa_lang ru -qa_demo 1 > /dev/null
  sleep 4
  xcrun simctl io "$UDID" screenshot "$OUT/dark_ru_${s}.png" > /dev/null 2>&1
done
xcrun simctl ui "$UDID" appearance light
# Журнал падений приложения, если были
find ~/Library/Logs/DiagnosticReports -name "ZikrTime*" -newer "$APP" -exec cp {} ci-out/ \; 2>/dev/null || true
echo done > ci-out/screens-done.txt
