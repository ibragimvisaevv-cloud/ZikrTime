"""Verify signed 0.8.27, or reuse exactly the same APKs for a test-only recheck."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

VERSION = "0.8.27"
CODE = 39
CERT = "7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf"
apk = Path("app/build/outputs/apk/release/app-release.apk")
debug = Path("app/build/outputs/apk/debug/app-debug.apk")
report_path = Path("ci-out/release-delivery.json")

if "--reuse" in sys.argv:
    report = json.loads(report_path.read_text())
    assert (report["version"], report["version_code"]) == (VERSION, CODE)
    assert report["certificate_sha256"] == CERT
    assert report["quran_assets_unchanged"] and not report["camera_permission"]
    source = report["source_sha"]
    assert re.fullmatch(r"[0-9a-f]{40}", source), source
    subprocess.run(["git", "diff", "--exit-code", source, "HEAD", "--", "app/",
                    "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/",
                    "gradlew", "gradlew.bat", "keystore/release.jks.enc"], check=True)
    for path, key in [(apk, "apk_sha256"), (debug, "debug_sha256")]:
        assert hashlib.sha256(path.read_bytes()).hexdigest() == report[key], str(path)
    Path("ci-out/recheck.json").write_text(json.dumps({
        "apk_source_sha": source, "apk_build_run": report["run"],
        "downloaded_from_run": int(os.environ["REUSE_RUN"]),
        "verification_source_sha": os.environ["GITHUB_SHA"],
        "verification_run": int(os.environ["GITHUB_RUN_ID"]),
        "unchanged_android_inputs": True, "both_apk_hashes_verified": True,
    }, indent=2))
    print("Reusing verified 0.8.27 APKs; original build metadata preserved.")
    sys.exit(0)

badging = Path("ci-out/release-badging.txt").read_text()
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
assert package and package.groups() == ("com.zikrtime.app", str(CODE), VERSION), badging[:200]
with zipfile.ZipFile(apk) as archive:
    manifest = archive.read("AndroidManifest.xml")
    for encoding in ("utf-8", "utf-16le"):
        assert "android.permission.CAMERA".encode(encoding) not in manifest
    source = Path("app/src/main/assets/quran")
    for file in source.rglob("*"):
        if file.is_file():
            assert archive.read("assets/quran/" + file.relative_to(source).as_posix()) == file.read_bytes()
    audio_catalog = Path("app/src/main/assets/quran_audio/alafasy.json")
    assert archive.read("assets/quran_audio/alafasy.json") == audio_catalog.read_bytes()
    assert not any(name.startswith("assets/quran_audio/") and name.lower().endswith(
        (".mp3", ".m4a", ".ogg", ".wav", ".aac")) for name in archive.namelist()), "Audio must not be bundled in APK"

tests = [ET.parse(p).getroot() for p in Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
assert tests and not any(int(t.get("failures", 0)) + int(t.get("errors", 0)) for t in tests)
assert any(t.get("name", "").startswith("com.zikrtime.app.audio.") for t in tests), "Audio policy tests were not run"
result = {"version": VERSION, "version_code": CODE, "source_sha": os.environ["GITHUB_SHA"],
          "run": int(os.environ["GITHUB_RUN_ID"]), "apk_size": apk.stat().st_size,
          "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
          "debug_sha256": hashlib.sha256(debug.read_bytes()).hexdigest(),
          "certificate_sha256": CERT,
          "targeted_unit_tests": sum(int(t.get("tests", 0)) for t in tests),
          "skipped_unit_tests": sum(int(t.get("skipped", 0)) for t in tests),
          "quran_assets_unchanged": True, "camera_permission": False,
          "audio_catalog_sha256": hashlib.sha256(audio_catalog.read_bytes()).hexdigest(),
          "bundled_quran_audio_files": 0}
report_path.write_text(json.dumps(result, indent=2))
print(json.dumps(result))
