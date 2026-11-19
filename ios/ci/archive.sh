#!/usr/bin/env bash
set -euo pipefail

edition="${1:-standard}"
out="$PWD/ci-out/iphone"
file=ZikrTime-iOS-0.8.28-56
if [[ "$edition" = screen-time ]]; then
    out="$PWD/ci-out/iphone-screen-time"
    file="$file-ScreenTime"
elif [[ "$edition" != standard ]]; then
    exit 2
fi
run=$(mktemp -d "$RUNNER_TEMP/zikr-archive-XXXXXX")
archive="$run/ZikrTime.xcarchive"
mkdir -p "$out"
build=(xcodebuild -workspace ios/ZikrTime.xcworkspace -scheme ZikrTime -configuration Release
    -destination 'generic/platform=iOS')

export_signed() {
    umask 077
    local key="$run/asc.p8"
    printf '%s\n' "$ASC_KEY_P8" > "$key" || return 1
    local auth=(-allowProvisioningUpdates -authenticationKeyPath "$key" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
    "${build[@]}" -archivePath "$archive" "${auth[@]}" DEVELOPMENT_TEAM="$APPLE_TEAM_ID" CODE_SIGN_STYLE=Automatic archive > "ci-out/sign-$edition.log" 2>&1 || return 1
    python3 - "$run/export.plist" "$APPLE_TEAM_ID" <<'PY' || return 1
import plistlib, sys
from pathlib import Path
Path(sys.argv[1]).write_bytes(plistlib.dumps(dict(method="app-store-connect", destination="export", teamID=sys.argv[2], signingStyle="automatic", manageAppVersionAndBuildNumber=False)))
PY
    xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist "$run/export.plist" -exportPath "$run/export" "${auth[@]}" > "ci-out/export-$edition.log" 2>&1 || return 1
    cp "$run/export/ZikrTime.ipa" "$out/$file-signed.ipa"
}
trap 'rm -f "$run/asc.p8"' EXIT
signed=false
if [[ -n "${ASC_KEY_ID:-}" && -n "${ASC_ISSUER_ID:-}" && -n "${ASC_KEY_P8:-}" && -n "${APPLE_TEAM_ID:-}" ]]; then
    if export_signed; then signed=true; else
        echo "::warning::Could not export this edition with the configured distribution credentials and capabilities. A device archive for subsequent signing will be built."
    fi
fi
if [[ "$signed" = false ]]; then
    mkdir -p "$run/unsigned"
    archive="$run/unsigned/ZikrTime.xcarchive"
    "${build[@]}" -archivePath "$archive" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" archive > "ci-out/archive-$edition.log" 2>&1 || { tail -100 "ci-out/archive-$edition.log"; exit 1; }
    stage="$run/ipa-stage"
    mkdir -p "$stage/Payload"
    ditto "$archive/Products/Applications/ZikrTime.app" "$stage/Payload/ZikrTime.app"
    (cd "$stage" && /usr/bin/zip -qry "$out/$file-unsigned.ipa" Payload)
fi
app="$archive/Products/Applications/ZikrTime.app"
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")" = com.zikrtime.ios
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleVersion' "$app/Info.plist")" = 56
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' "$app/Info.plist")" = 0.8.28
lipo "$app/ZikrTime" -verify_arch arm64
widget="$app/PlugIns/PrayerWidget.appex"
test "$(/usr/libexec/PlistBuddy -c 'Print NSExtension:NSExtensionPointIdentifier' "$widget/Info.plist")" = com.apple.widgetkit-extension
lipo "$widget/PrayerWidget" -verify_arch arm64
if [[ "$edition" = screen-time ]]; then
    focus="$app/PlugIns/FocusActivityMonitor.appex"
    test "$(/usr/libexec/PlistBuddy -c 'Print NSExtension:NSExtensionPointIdentifier' "$focus/Info.plist")" = com.apple.deviceactivity.monitor-extension
    lipo "$focus/FocusActivityMonitor" -verify_arch arm64
fi
test -d "$app/QuranData"
test -s "$app/prayer/regional.tsv"
test -s "$app/tasbih_bead.wav"
test -d "$archive/dSYMs/ZikrTime.app.dSYM"
unzip -tq "$out"/*.ipa
/usr/bin/ditto -c -k --keepParent "$archive" "$out/$file.xcarchive.zip"
(cd "$out" && shasum -a 256 *.ipa *.zip | tee SHA256SUMS.txt)
