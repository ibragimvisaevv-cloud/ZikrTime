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
adb shell settings put global animator_duration_scale 1
capture() {
  local name="$1" screen="$2" period="${3:-SUNSET}"
  adb shell am start -S -W -n "$pkg/.MainActivity" --es qa_screen "$screen" --es qa_lang ru --ez qa_demo true --es qa_period "$period" > "ci-out/$name-start.txt"
  sleep 3
  adb shell pidof "$pkg"
  adb exec-out screencap -p > "ci-out/screens/$name.png"
  python3 tools/ci/check_screen.py "ci-out/screens/$name.png"
}
for mode in no yes; do
  adb shell cmd uimode night "$mode"
  for period in DAWN DAY SUNSET NIGHT; do capture "home-$mode-$period" home "$period"; done
  capture "tasbih-$mode" tasbih
 done
# Narrow viewport and large type; scrollable screen keeps controls available.
adb shell wm size 720x1600
adb shell wm density 360
adb shell settings put system font_scale 1.3
capture home-large home
capture tasbih-large tasbih
adb shell settings put system font_scale 1
adb shell wm size reset
adb shell wm density reset
capture tasbih-motion tasbih
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml ci-out/tasbih-before.xml >/dev/null
read -r x y < <(python3 - <<'PY'
import xml.etree.ElementTree as E,re
n=next(x for x in E.parse('ci-out/tasbih-before.xml').iter('node') if x.attrib.get('content-desc')=='Тасбих')
a,b,c,d=map(int,re.findall(r'\d+',n.attrib['bounds']));print((a+c)//2,(b+d)//2)
PY
)
adb exec-out run-as "$pkg" cat shared_prefs/zikrtime.xml > ci-out/prefs-before.xml
adb shell dumpsys gfxinfo "$pkg" reset >/dev/null
adb shell screenrecord --time-limit 12 /sdcard/tasbih.mp4 &
record_pid=$!
sleep 1
# One shell keeps inter-tap overhead low; input taps are genuine UI events.
adb shell "for n in \$(seq 1 40); do input tap $x $y; done"
wait "$record_pid" || true
adb pull /sdcard/tasbih.mp4 ci-out/tasbih.mp4 >/dev/null
sleep 1
adb exec-out run-as "$pkg" cat shared_prefs/zikrtime.xml > ci-out/prefs-after.xml
python3 - <<'PYCODE'
import xml.etree.ElementTree as E,json
def count(path):
    return int(next(n.attrib['value'] for n in E.parse(path).getroot() if n.attrib.get('name')=='tasbih_count'))
before=count('ci-out/prefs-before.xml');after=count('ci-out/prefs-after.xml')
assert after-before==40,(before,after)
open('ci-out/tap-count.json','w').write(json.dumps({'before':before,'after':after,'taps':40}))
PYCODE
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml ci-out/tasbih-after.xml >/dev/null
adb exec-out screencap -p > ci-out/screens/tasbih-after.png
adb shell dumpsys gfxinfo "$pkg" > ci-out/gfxinfo-motion.txt
adb shell dumpsys meminfo "$pkg" > ci-out/meminfo.txt
adb shell dumpsys gfxinfo "$pkg" reset >/dev/null
sleep 5
adb shell dumpsys gfxinfo "$pkg" > ci-out/gfxinfo-idle.txt
adb shell settings put global animator_duration_scale 0
capture tasbih-no-motion tasbih
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then cat ci-out/crashes.txt; exit 1; fi

# Check goal, phrase and reset actions using the real controls, then restart for persistence.
adb shell input swipe 500 1500 500 500 400
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml ci-out/controls.xml >/dev/null
adb exec-out screencap -p > ci-out/screens/tasbih-controls.png
adb shell settings put global animator_duration_scale 1
python3 tools/ci/natural_controls.py
python3 tools/ci/natural_controls.py baseline-out/outputs/apk/debug/app-debug.apk "$1"
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then cat ci-out/crashes.txt; exit 1; fi
