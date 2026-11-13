"""Retain completed groups with evidence tied to the exact final APKs."""
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

RUN = 42474183725
ARTIFACT = 13615411512
SOURCE = "f59e2ad11281dee431fb82d2b603bae64cbb417f"
GROUPS = {"ayah_repeats_speed_background", "offline_ayah_saved_marks"}
ACCESS_RUN = 42478656906
ACCESS_ARTIFACT = 13616423692
ACCESS_SOURCE = "4023b3268af2a7b7e5f578fd3e41ccd835b9e703"
MODE = os.environ.get("LEARNING_MODE", "remaining")
assert MODE in {"remaining", "guide"}, MODE
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
# Only these functions and their unchanged shared helpers support the passed
# audio groups. The guide never passed and must be allowed to fix its locator.
for name in ("open_reader", "ayah_sheet", "verse_playing", "verse_paused", "verify_clip",
             "learning_audio", "offline_ayah_and_saved_marks"):
    assert before[name] == after[name], "Previously passed audio assertion changed: " + name
lint = Path("ci-inherited/app/build/reports/lint-results-debug.xml")
if lint.exists():
    import xml.etree.ElementTree as ET
    assert not any(i.get("severity") in {"Error", "Fatal"} for i in ET.parse(lint).getroot())
report = {"source_run": RUN, "artifact_id": ARTIFACT, "source_sha": SOURCE,
          "apk_sha256": old["apk_sha256"], "debug_sha256": old["debug_sha256"],
          "verified_same_apks": True, "android_inputs_unchanged": True,
          "audio_assertions_unchanged": True, "groups": previous["groups"],
          "note": "These audio groups passed in the cited earlier run. Only accessibility and guide are rerun."}
target = Path("ci-out/inherited-audio-check")
target.mkdir(parents=True, exist_ok=True)
shutil.copy2("ci-inherited/artifact.json", target / "artifact.json")
for name in ("learning-passed.json", "release-delivery.json", "crashes.txt"):
    shutil.copy2(evidence / name, target / name)

if MODE == "guide":
    access_meta = json.loads(Path("ci-accessible/artifact.json").read_text())
    assert access_meta["id"] == ACCESS_ARTIFACT and not access_meta["expired"]
    assert access_meta["name"] == "ZikrTime-0.8.28-learning-check"
    assert access_meta["workflow_run"]["id"] == ACCESS_RUN
    assert access_meta["workflow_run"]["head_sha"] == ACCESS_SOURCE
    access_path = Path("ci-accessible/ci-out")
    access = json.loads((access_path / "learning-passed.json").read_text())
    assert access["mode"] == "remaining" and not access.get("complete", False)
    assert access["profile"] == "isolated_debug_emulator" and not access["productionWrites"]
    assert set(access["groups"]) == {"accessible_ayah_controls"}
    assert all(value for value in access["groups"]["accessible_ayah_controls"].values()
               if isinstance(value, bool))
    assert not re.search(r"FATAL EXCEPTION|Fatal signal", (access_path / "crashes.txt").read_text())
    assert json.loads((access_path / "release-delivery.json").read_text()) == current
    recheck = json.loads((access_path / "recheck.json").read_text())
    assert recheck == {
        "apk_source_sha": SOURCE, "apk_build_run": RUN, "downloaded_from_run": RUN,
        "verification_source_sha": ACCESS_SOURCE, "verification_run": ACCESS_RUN,
        "unchanged_android_inputs": True, "both_apk_hashes_verified": True,
    }, "Accessibility evidence does not prove these exact APKs were checked"
    access_audio = json.loads((access_path / "inherited-learning.json").read_text())
    for key in ("source_run", "artifact_id", "source_sha", "apk_sha256", "debug_sha256",
                "verified_same_apks", "android_inputs_unchanged", "audio_assertions_unchanged", "groups"):
        assert access_audio[key] == report[key], "Audio provenance changed: " + key
    access_functions = functions(subprocess.check_output(
        ["git", "show", ACCESS_SOURCE + ":" + path], text=True))
    for name in ("accessible_ayah_controls", "require_ayah_reachable"):
        assert access_functions[name] == after[name], "Passed accessibility assertion changed: " + name
    report["groups"].update(access["groups"])
    report["accessibility_evidence"] = {
        "source_run": ACCESS_RUN, "artifact_id": ACCESS_ARTIFACT,
        "source_sha": ACCESS_SOURCE, "apk_source_sha": SOURCE,
        "apk_sha256": current["apk_sha256"], "debug_sha256": current["debug_sha256"],
        "assertions_unchanged": True, "recheck": recheck,
    }
    report["note"] = "Audio and accessibility passed in the cited runs on these exact APKs. Only guide is rerun."
    target = Path("ci-out/inherited-accessibility-check")
    target.mkdir(parents=True, exist_ok=True)
    shutil.copy2("ci-accessible/artifact.json", target / "artifact.json")
    for name in ("learning-passed.json", "release-delivery.json", "recheck.json",
                 "inherited-learning.json", "crashes.txt"):
        shutil.copy2(access_path / name, target / name)

Path("ci-out/inherited-learning.json").write_text(json.dumps(report, indent=2))
print("Verified successful evidence and identical APKs; running " + MODE + " UI checks only.")
