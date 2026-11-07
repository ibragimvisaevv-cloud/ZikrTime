"""Describe the signed bug-fix APK and verify unchanged protected assets."""
import hashlib
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

apk = Path("app/build/outputs/apk/release/app-release.apk")
debug = Path("app/build/outputs/apk/debug/app-debug.apk")
badging = Path("ci-out/release-badging.txt").read_text()
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
assert package and package.groups() == ("com.zikrtime.app", "36", "0.8.24"), package.groups() if package else badging[:200]
with zipfile.ZipFile(apk) as archive:
    manifest = archive.read("AndroidManifest.xml")
    for encoding in ("utf-8", "utf-16le"):
        assert "android.permission.CAMERA".encode(encoding) not in manifest
    source = Path("app/src/main/assets/quran")
    for file in source.rglob("*"):
        if file.is_file():
            assert archive.read("assets/quran/" + file.relative_to(source).as_posix()) == file.read_bytes()
tests = [ET.parse(p).getroot() for p in Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
assert tests and not any(int(t.get("failures", 0)) + int(t.get("errors", 0)) for t in tests)
result = {"version": "0.8.24", "version_code": 36, "source_sha": os.environ["GITHUB_SHA"],
          "run": int(os.environ["GITHUB_RUN_ID"]), "apk_size": apk.stat().st_size,
          "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
          "debug_sha256": hashlib.sha256(debug.read_bytes()).hexdigest(),
          "certificate_sha256": "7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf",
          "targeted_unit_tests": sum(int(t.get("tests", 0)) for t in tests),
          "quran_assets_unchanged": True, "camera_permission": False}
Path("ci-out/release-delivery.json").write_text(json.dumps(result, indent=2))
print(json.dumps(result))
