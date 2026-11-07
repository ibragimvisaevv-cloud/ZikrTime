"""Carry forward the exact successful map checks on identical APKs.

The previous run's navigation failed on a transient loading view. Its four
completed map/search groups are retained, not represented as a new execution.
"""
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

RUN = 42390466804
ARTIFACT = 13581197812
CHECK_SOURCE = "87e1d8f78fe4bcad110bc54f48651216f7edf64a"
APK_SOURCE = "f4f8ee420d6f9babf8333655bf7bdf1766fc1d6c"
APK_HASH = "986f60c174c9f76fd6275177a54e09305173483f4e6f215e7fa2fe9505e37d41"
DEBUG_HASH = "69cd21efdd8826c4469e335fb843f887c4558893f8341b3898fd94f0cb171400"
groups = {"map_gestures", "preview_profiles", "rotation", "search"}

assert os.environ["REUSE_RUN"] == str(RUN)
metadata = json.loads(Path("ci-inherited/artifact.json").read_text())
assert metadata["id"] == ARTIFACT and not metadata["expired"]
assert metadata["name"] == "ZikrTime-0.8.25-clarity-check"
assert metadata["workflow_run"]["id"] == RUN
assert metadata["workflow_run"]["head_sha"] == CHECK_SOURCE
evidence = Path("ci-inherited/ci-out")
previous = json.loads((evidence / "clarity-passed.json").read_text())
assert set(previous["groups"]) == groups and not previous.get("complete", False)
assert previous["profile"] == "isolated_debug_emulator" and not previous["production_writes"]
assert previous["native_map_camera_observed"]
assert previous["groups"]["map_gestures"]["outside_map_scroll"]
assert {d["direction"] for d in previous["groups"]["map_gestures"]["drags"]} == {"up", "down", "left", "right"}
assert set(previous["groups"]["preview_profiles"]) == {"normal", "large", "compact-light"}
for profile in previous["groups"]["preview_profiles"].values():
    assert profile["explicit_expand_and_collapse"] and profile["back_closes"]
assert all(previous["groups"]["rotation"].values()) and all(previous["groups"]["search"].values())
assert not re.search(r"FATAL EXCEPTION|Fatal signal", (evidence / "crashes.txt").read_text())

old_delivery = json.loads((evidence / "release-delivery.json").read_text())
current_delivery = json.loads(Path("ci-out/release-delivery.json").read_text())
assert old_delivery == current_delivery
assert old_delivery["source_sha"] == APK_SOURCE
assert (old_delivery["apk_sha256"], old_delivery["debug_sha256"]) == (APK_HASH, DEBUG_HASH)
for variant, digest in [("release", APK_HASH), ("debug", DEBUG_HASH)]:
    apk = Path(f"app/build/outputs/apk/{variant}/app-{variant}.apk")
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == digest
old_recheck = json.loads((evidence / "recheck.json").read_text())
assert old_recheck["verification_run"] == RUN
assert old_recheck["verification_source_sha"] == CHECK_SOURCE
assert old_recheck["apk_source_sha"] == APK_SOURCE
assert old_recheck["both_apk_hashes_verified"] and old_recheck["unchanged_android_inputs"]

subprocess.run(["git", "diff", "--exit-code", CHECK_SOURCE, "HEAD", "--",
                "tools/ci/places_fix_journeys.py", "tools/ci/features_controls.py", "tools/ci/check_screen.py"], check=True)
path = "tools/ci/clarity_journeys.py"
old_text = subprocess.check_output(["git", "show", CHECK_SOURCE + ":" + path], text=True)
new_text = Path(path).read_text()
def functions(source):
    return {n.name: ast.dump(n, include_attributes=False) for n in ast.parse(source).body
            if isinstance(n, ast.FunctionDef)}
old, current = functions(old_text), functions(new_text)
protected = {"start", "clipped_bounds", "camera", "camera_changed", "map_gestures",
             "preview_geometry", "details", "preview_profiles", "rotation_preview", "search_flow"}
assert all(old[name] == current[name] for name in protected), "Previously passed map assertions changed"

provenance = {"source_run": RUN, "artifact_id": ARTIFACT, "verification_source_sha": CHECK_SOURCE,
              "apk_source_sha": APK_SOURCE, "apk_sha256": APK_HASH, "debug_sha256": DEBUG_HASH,
              "verified_same_apks": True, "map_assertions_unchanged": True,
              "groups": previous["groups"],
              "note": "Map, previews, rotation and search passed in the cited earlier run; only navigation rerun."}
Path("ci-out/inherited-clarity.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2))
destination = Path("ci-out/inherited-map-check")
destination.mkdir(parents=True, exist_ok=True)
for name in ("clarity-passed.json", "release-delivery.json", "recheck.json"):
    shutil.copy2(evidence / name, destination / name)
for path in evidence.glob("clarity-*.xml"):
    if not any(word in path.name for word in ("failure", "home")):
        shutil.copy2(path, destination / path.name)
for path in (evidence / "screens").glob("clarity-*.png"):
    if not any(word in path.name for word in ("failure", "home")):
        (destination / "screens").mkdir(exist_ok=True)
        shutil.copy2(path, destination / "screens" / path.name)
print("Verified earlier map evidence on identical APKs; run only remaining navigation.")
