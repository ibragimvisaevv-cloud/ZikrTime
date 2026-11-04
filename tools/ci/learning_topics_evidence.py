"""Expose only synthetic emulator evidence for review when no local executor exists."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

folder = Path("learning-topics-evidence")
folder.mkdir(exist_ok=True)
for name in ["learning-topics-failure.txt", "learning-topics-passed.json", "release-delivery.json", "crashes.txt", "last-ui.xml", "reused-build-verification.json"]:
    source = Path("ci-out") / name
    if source.exists():
        shutil.copyfile(source, folder / name)
for source in [*Path("ci-out/screens").glob("learn-*.png"), *Path("ci-out").glob("learn-*.xml")]:
    shutil.copyfile(source, folder / source.name)
apk = Path("app/build/outputs/apk/debug/app-debug.apk")
if apk.exists():
    tests = [ET.parse(p).getroot() for p in Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
    result_path = Path("ci-out/learning-topics-passed.json")
    result = json.loads(result_path.read_text()) if result_path.exists() else {}
    expected_groups = {"training_replacement_dialog", "real_error_focus_resume_and_no_bonus", "ranking_full_round_and_level_isolation",
                       "quran_topics_offline_search_navigation", "compact_dark_large_text"}
    complete = result.get("mode") == "all" and expected_groups <= set(result.get("groups", {}))
    report = {"apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "apk_size": apk.stat().st_size,
              "unit_tests": sum(int(t.get("tests", 0)) for t in tests),
              "unit_failures": sum(int(t.get("failures", 0)) for t in tests),
              "unit_errors": sum(int(t.get("errors", 0)) for t in tests),
              "source_sha": os.environ.get("APK_SOURCE_SHA", os.environ["GITHUB_SHA"]),
              "verification_sha": os.environ["GITHUB_SHA"], "run": int(os.environ["GITHUB_RUN_ID"]),
              "journey_completed": (complete and not Path("ci-out/learning-topics-failure.txt").exists())}
    (folder / "delivery.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
if not any(folder.iterdir()):
    print("No emulator or APK evidence exists yet")
    raise SystemExit(0)
branch = "ci-learning-0821-" + os.environ["GITHUB_RUN_ID"]
def git(*args):
    subprocess.run(["git", *args], cwd=folder, check=True, stdout=subprocess.DEVNULL)
git("init", "-q")
git("add", ".")
git("-c", "user.name=ci", "-c", "user.email=ci@local", "commit", "-qm", "Adaptive learning and Quran topics emulator evidence [skip ci]")
git("push", "-q", "https://x-access-token:" + os.environ["GH_TOKEN"] + "@github.com/" +
    os.environ["GITHUB_REPOSITORY"] + ".git", "HEAD:refs/heads/" + branch)
print("Evidence branch: " + branch)

