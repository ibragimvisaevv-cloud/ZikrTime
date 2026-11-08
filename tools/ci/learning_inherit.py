"""Retain completed audio groups from the exact final APK, not a new test run."""
import ast
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

RUN = 42474183725
ARTIFACT = 13615411512
SOURCE = "f59e2ad11281dee431fb82d2b603bae64cbb417f"
GROUPS = {"ayah_repeats_speed_background", "offline_ayah_saved_marks"}
metadata = json.loads(Path("ci-inherited/artifact.json").read_text())
assert metadata["id"] == ARTIFACT and not metadata["expired"]
assert metadata["name"] == "ZikrTime-0.8.28-learning-check"
assert metadata["workflow_run"]["id"] == RUN
assert metadata["workflow_run"]["head_sha"] == SOURCE
evidence = Path("ci-inherited/ci-out")
previous = json.loads((evidence / "learning-passed.json").read_text())
assert set(previous["groups"]) == GROUPS and not previous.get("complete", False)
assert previous["profile"] == "isolated_debug_emulator" and not previous["productionWrites"]
for group in previous["groups"].values():
    assert all(value for value in group.values() if isinstance(value, bool))
assert not re.search(r"FATAL EXCEPTION|Fatal signal", (evidence / "crashes.txt").read_text())
old = json.loads((evidence / "release-delivery.json").read_text())
current = json.loads(Path("ci-out/release-delivery.json").read_text())
assert old == current and old["source_sha"] == SOURCE and old["run"] == RUN
assert old["apk_sha256"] == "c881e337a613f2260143e76dc780d0b647877cb2ad7daa4420dbacf988d972df"
assert old["debug_sha256"] == "f0a3fbfc20d43a9ceacce5d97e1ac52483e1aa4b2afbf8e929f2be033cecb0d6"
for variant, key in [("release", "apk_sha256"), ("debug", "debug_sha256")]:
    path = Path(f"app/build/outputs/apk/{variant}/app-{variant}.apk")
    assert hashlib.sha256(path.read_bytes()).hexdigest() == old[key]
subprocess.run(["git", "diff", "--exit-code", SOURCE, "HEAD", "--", "app/",
                "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/",
                "gradlew", "gradlew.bat", "keystore/release.jks.enc",
                "tools/ci/audio_journeys.py", "tools/ci/guide_journeys.py",
                "tools/ci/features_controls.py", "tools/ci/audio_source_check.py"], check=True)
path = "tools/ci/learning_journeys.py"
old_text = subprocess.check_output(["git", "show", SOURCE + ":" + path], text=True)
# Only the cold-load readiness timeout changes in the shared reader helper;
# media assertions, gestures, selections, and exact query checks stay intact.
old_text = old_text.replace('\n    observe("quran_index_list")\n',
                            '\n    observe("quran_index_list", tries=30)\n', 1)
def functions(text):
    return {node.name: ast.dump(node, include_attributes=False) for node in ast.parse(text).body
            if isinstance(node, ast.FunctionDef)}
before, after = functions(old_text), functions(Path(path).read_text())
for name in before:
    if name != "accessible_ayah_controls":
        assert before[name] == after[name], "Previously passed assertion changed: " + name
lint = Path("ci-inherited/app/build/reports/lint-results-debug.xml")
if lint.exists():
    import xml.etree.ElementTree as ET
    assert not any(i.get("severity") in {"Error", "Fatal"} for i in ET.parse(lint).getroot())
report = {"source_run": RUN, "artifact_id": ARTIFACT, "source_sha": SOURCE,
          "apk_sha256": old["apk_sha256"], "debug_sha256": old["debug_sha256"],
          "verified_same_apks": True, "android_inputs_unchanged": True,
          "audio_assertions_unchanged": True, "groups": previous["groups"],
          "note": "These audio groups passed in the cited earlier run. Only accessibility and guide are rerun."}
Path("ci-out/inherited-learning.json").write_text(json.dumps(report, indent=2))
target = Path("ci-out/inherited-audio-check")
target.mkdir(parents=True, exist_ok=True)
for name in ("learning-passed.json", "release-delivery.json", "crashes.txt"):
    shutil.copy2(evidence / name, target / name)
print("Verified successful audio evidence and identical APKs; running only remaining UI checks.")
