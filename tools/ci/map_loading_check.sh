#!/usr/bin/env bash
# Read-only map diagnostic on a disposable emulator, using an EXISTING debug APK.
# No Gradle/build, no backend changes, no correction/review submissions.
# Usage: bash tools/ci/map_loading_check.sh path/to/existing-app-debug.apk [ci-out/map-loading]
set -euo pipefail
APK="${1:?Path to an existing debug APK is required}"
OUT="${2:-ci-out/map-loading}"
PKG=com.zikrtime.app
[ -f "$APK" ] || { echo 'Existing APK not found'; exit 1; }
mkdir -p "$OUT/screens"
adb wait-for-device
[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo 'Disposable emulator required'; exit 1; }
adb install -r "$APK" > "$OUT/install.txt"
adb shell run-as "$PKG" id >/dev/null # Require debuggable APK; never alter a user's phone.
sha256sum "$APK" | awk '{print $1}' > "$OUT/apk.sha256"
adb shell dumpsys package "$PKG" | sed -n '/versionCode=/p; /versionName=/p' > "$OUT/package-version.txt"
FONT_BEFORE=$(adb shell settings get system font_scale | tr -d '\r')
NIGHT_BEFORE=$(adb shell cmd uimode night | sed -nE 's/.*Night mode: (yes|no|auto|custom).*/\1/p' | tr -d '\r')
AIRPLANE_BEFORE=$(adb shell settings get global airplane_mode_on | tr -d '\r')
WIFI_BEFORE=$(adb shell settings get global wifi_on | tr -d '\r')
DATA_BEFORE=$(adb shell settings get global mobile_data | tr -d '\r')

collect_diagnostics() {
  # Raw logcat stays in memory. Only redacted, map/error-relevant lines are written.
  python3 - "$OUT" "$PKG" <<'PY'
import json, re, subprocess, sys
from pathlib import Path
out, pkg = Path(sys.argv[1]), sys.argv[2]
def adb(*args):
    try:
        return subprocess.run(['adb', *args], capture_output=True, text=True, timeout=30).stdout
    except subprocess.TimeoutExpired:
        return 'diagnostic command timed out'
keys = []
props = Path('gradle.properties')
if props.exists():
    for line in props.read_text().splitlines():
        if re.match(r'\s*yandexMapkitKey\s*=', line):
            value = line.split('=', 1)[1].strip()
            if value:
                keys.append(value)
def redact(line):
    for value in keys:
        line = line.replace(value, '[redacted-map-key]')
    # Also handle different artifact keys and URL-encoded keys without exposing them.
    line = re.sub(r'(?i)\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b', '[redacted-id]', line)
    line = re.sub(r'(?i)((?:api[_ -]?key|apikey|token|authorization|bearer|password|secret)\s*[=: ]\s*)[^\s,;]+', r'\1[redacted]', line)
    line = re.sub(r'(https?://[^\s?#]+)[?#][^\s]+', r'\1?[redacted-query]', line)
    line = re.sub(r'\b[A-Za-z0-9_\-]{28,}\b', '[redacted-long-value]', line)
    return line
raw = adb('logcat', '-d', '-v', 'threadtime')
map_tag = re.compile(r'yandex|mapkit|yamap|YMK|maps\.mobile', re.I)
issue = re.compile(r'auth|api.?key|network|connect|error|fail|denied|forbidden|ssl|tls|certificate|timeout|http|download|license', re.I)
crash = re.compile(r'FATAL EXCEPTION|Fatal signal', re.I)
selected = [redact(line) for line in raw.splitlines() if
            (map_tag.search(line) and issue.search(line)) or crash.search(line)]
(out / 'map-errors-redacted.txt').write_text('\n'.join(selected) + '\n')
connectivity = adb('shell', 'dumpsys', 'connectivity')
# Only generic network capability/default status, not SSID/account/device identifiers.
net = [redact(line) for line in connectivity.splitlines() if
       re.search(r'Active default network:|NetworkCapabilities:.*(?:VALIDATED|INTERNET)', line)]
(out / 'network-status.txt').write_text('\n'.join(net) + '\n')
state = {name: adb('shell', 'settings', 'get', namespace, key).strip() for name, namespace, key in (
    ('airplane_mode', 'global', 'airplane_mode_on'), ('wifi', 'global', 'wifi_on'),
    ('mobile_data', 'global', 'mobile_data'), ('font_scale', 'system', 'font_scale'))}
state.update(pid=adb('shell', 'pidof', pkg).strip(), emulator_time=adb('shell', 'date', '-u').strip(),
             error_lines=len(selected), screenshot_offsets_seconds=[5, 15, 30],
             built_new_apk=False, external_submissions=0,
             note='Log patterns are diagnostic hints. Map tiles require visual confirmation; markers are not tiles.')
(out / 'diagnostics.json').write_text(json.dumps(state, indent=2))
PY
}
cleanup() {
  local status=$?
  if [ "${DIAGNOSTICS_DONE:-0}" != 1 ]; then collect_diagnostics || true; fi
  adb shell settings put system font_scale "${FONT_BEFORE:-1}" >/dev/null || true
  [ -z "$NIGHT_BEFORE" ] || adb shell cmd uimode night "$NIGHT_BEFORE" >/dev/null || true
  if [ "$AIRPLANE_BEFORE" = 1 ]; then adb shell cmd connectivity airplane-mode enable >/dev/null || true; fi
  if [ "$WIFI_BEFORE" = 0 ]; then adb shell svc wifi disable >/dev/null || true; fi
  if [ "$DATA_BEFORE" = 0 ]; then adb shell svc data disable >/dev/null || true; fi
  return "$status"
}
trap cleanup EXIT
adb shell cmd connectivity airplane-mode disable >/dev/null
adb shell svc wifi enable >/dev/null
adb shell svc data enable >/dev/null
adb shell settings put system font_scale 1 >/dev/null
adb shell cmd uimode night no >/dev/null
adb shell svc power stayon true >/dev/null
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
sleep 3
adb logcat -c
adb shell am start -S -W -n "$PKG/.MainActivity" \
  --es qa_screen places --es qa_lang en --ez qa_onboarded true \
  --ez qa_tilt false --ez qa_parallax false > "$OUT/start.txt"
START_SECONDS=$SECONDS
for AT in 5 15 30; do
  DELAY=$((START_SECONDS + AT - SECONDS))
  if [ "$DELAY" -gt 0 ]; then sleep "$DELAY"; fi
  adb shell pidof "$PKG" > "$OUT/pid-${AT}s.txt"
  adb exec-out screencap -p > "$OUT/screens/map-${AT}s.png"
  python3 tools/ci/check_screen.py "$OUT/screens/map-${AT}s.png"
done
adb shell uiautomator dump /sdcard/zikrtime-map-loading.xml >/dev/null
adb exec-out cat /sdcard/zikrtime-map-loading.xml > "$OUT/map-ui-30s.xml"
python3 - "$OUT/map-ui-30s.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
labels = [n.attrib.get('text', '') for n in root.iter('node')]
assert any('Hide map' in text for text in labels), 'Map component is not visible; inspect UI screenshot'
print('Existing APK map captured at 5, 15 and 30 seconds; visual tile review still required.')
PY

collect_diagnostics
DIAGNOSTICS_DONE=1
# User-facing screenshot of actual My Day controls after scrolling the hero.
# This capture is not an additional behavioral-test claim.
adb shell am start -S -W -n "$PKG/.MainActivity" \
  --es qa_screen home --es qa_lang ru --ez qa_onboarded true \
  --ez qa_tilt false --ez qa_parallax false > "$OUT/home-start.txt"
sleep 2
python3 - "$OUT" <<'PY'
import re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path
out=Path(sys.argv[1])
def sh(*args):
    return subprocess.check_output(['adb', 'shell', *map(str,args)],text=True,timeout=30)
sh('uiautomator','dump','/sdcard/zikrtime-my-day.xml')
root=ET.fromstring(sh('cat','/sdcard/zikrtime-my-day.xml'))
scroll=[n for n in root.iter('node') if n.attrib.get('scrollable')=='true']
def box(n): return list(map(int,re.findall(r'-?\d+',n.attrib['bounds'])))
if scroll:
    target=max(scroll,key=lambda n:box(n)[3]-box(n)[1])
    x1,y1,x2,y2=box(target)
    sh('input','swipe',(x1+x2)//2,y1+int((y2-y1)*.8),(x1+x2)//2,y1+int((y2-y1)*.28),400)
    time.sleep(1)
sh('uiautomator','dump','/sdcard/zikrtime-my-day.xml')
(out/'home-my-day-ru.xml').write_text(sh('cat','/sdcard/zikrtime-my-day.xml'))
PY
adb exec-out screencap -p > "$OUT/screens/home-my-day-ru.png"
python3 tools/ci/check_screen.py "$OUT/screens/home-my-day-ru.png"
# Optional visual focus capture with the existing synthetic target fixture.
if [ -f fixtures/focus.apk ]; then
  mkdir -p ci-out/screens
  adb install -r fixtures/focus.apk > "$OUT/focus-fixture-install.txt"
  python3 tools/ci/features_controls.py focus-visual
fi
