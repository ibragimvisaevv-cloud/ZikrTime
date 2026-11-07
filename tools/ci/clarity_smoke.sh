#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-out/screens
adb wait-for-device
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
trap 'adb logcat -d -b crash > ci-out/crashes.txt' EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
keytool -genkeypair -noprompt -keystore ci-out/fixture.jks -storepass android -keypass android -alias test -dname 'CN=Disposable Emulator Fixture' -keyalg RSA -validity 2
bash tools/ci/build_focus_fixture.sh ci-out/fixture.jks ci-out/focus-fixture.apk
adb install -r ci-out/focus-fixture.apk
python3 tools/ci/clarity_journeys.py
adb logcat -d -b crash > ci-out/crashes.txt
if grep -qE 'FATAL EXCEPTION|Fatal signal' ci-out/crashes.txt; then exit 1; fi
