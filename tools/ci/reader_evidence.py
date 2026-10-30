"""Expose only synthetic emulator evidence for review when no local executor exists."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

folder = Path("reader-evidence")
folder.mkdir(exist_ok=True)
for name in ["quran-failure.txt", "quran-passed.json", "quran-features-passed.json", "crashes.txt", "last-ui.xml",
             "reused-build-verification.json"]:
    source = Path("ci-out") / name
    if source.exists():
        shutil.copyfile(source, folder / name)
for source in Path("ci-out/screens").glob("features-*.png"):
    shutil.copyfile(source, folder / source.name)
failure = Path("ci-out/screens/quran-failure.png")
if failure.exists():
    shutil.copyfile(failure, folder / failure.name)
apk = Path("app/build/outputs/apk/debug/app-debug.apk")
if apk.exists():
    tests = [ET.parse(p).getroot() for p in Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
    report = {"apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "apk_size": apk.stat().st_size,
              "unit_tests": sum(int(t.get("tests", 0)) for t in tests),
              "source_sha": os.environ["GITHUB_SHA"], "run": int(os.environ["GITHUB_RUN_ID"]),
              "journey_completed": Path("ci-out/quran-features-passed.json").exists()}
    (folder / "delivery.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
branch = "ci-reader-0816-" + os.environ["GITHUB_RUN_ID"]
def git(*args):
    subprocess.run(["git", *args], cwd=folder, check=True, stdout=subprocess.DEVNULL)
git("init", "-q")
git("add", ".")
git("-c", "user.name=ci", "-c", "user.email=ci@local", "commit", "-qm", "Quran emulator evidence [skip ci]")
git("push", "-q", "https://x-access-token:" + os.environ["GH_TOKEN"] + "@github.com/" +
    os.environ["GITHUB_REPOSITORY"] + ".git", "HEAD:refs/heads/" + branch)
print("Evidence branch: " + branch)
