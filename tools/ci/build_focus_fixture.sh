#!/usr/bin/env bash
# Disposable emulator target; this APK is never bundled with or released as ZikrTime.
set -euo pipefail
key="$1"
output="$2"
sdk="${ANDROID_HOME:?Android SDK is required}"
build_tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
platform="$sdk/platforms/android-35/android.jar"
fixture_dir="$(mktemp -d)"
trap 'rm -rf "$fixture_dir"' EXIT
mkdir -p "$fixture_dir/classes" "$fixture_dir/dex"
javac -source 8 -target 8 -classpath "$platform" -d "$fixture_dir/classes" tools/ci/focus-fixture/MainActivity.java
"$build_tools/d8" --min-api 26 --lib "$platform" --output "$fixture_dir/dex" "$fixture_dir/classes/com/zikrtime/focusfixture/MainActivity.class"
"$build_tools/aapt2" link -I "$platform" --manifest tools/ci/focus-fixture/AndroidManifest.xml -o "$fixture_dir/unsigned.apk"
zip -j "$fixture_dir/unsigned.apk" "$fixture_dir/dex/classes.dex"
"$build_tools/zipalign" -p 4 "$fixture_dir/unsigned.apk" "$fixture_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$key" --ks-key-alias test --ks-pass pass:android --key-pass pass:android --out "$output" "$fixture_dir/aligned.apk"
"$build_tools/apksigner" verify "$output"
