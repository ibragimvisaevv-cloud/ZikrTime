"""Import hash-verified real captures; retain source evidence outside the APK.

Usage: python tools/ci/import_guide_screenshots_0828.py COMMON_REPORT QURAN_REPORT
Reports are unpacked artifacts from runs 42468102124 and 42467811837.
"""
import hashlib
import json
from pathlib import Path
import shutil
import sys

from PIL import Image

TOPICS = {"welcome", "prayer", "journal", "quran", "limits", "permissions",
          "knowledge", "places", "tools", "shop"}
SOURCES = (
    (42468102124, "b755cffca95923b63c75f3b3c105f2c954f9e4f9", TOPICS - {"quran"}),
    (42467811837, "5b3efc159b9325cc56d2f3498c136430a1260047", {"quran"}),
)


def main():
    assert len(sys.argv) == 3, __doc__
    destination = Path("app/src/main/assets/guide/ru")
    destination.mkdir(parents=True, exist_ok=True)
    evidence = Path("tools/ci/guide-evidence-0828")
    evidence.mkdir(parents=True, exist_ok=True)
    combined, origins = {}, []
    for folder, (capture_run, apk_source, topics) in zip(sys.argv[1:], SOURCES):
        folder = Path(folder)
        provenance = json.loads((folder / "guide-capture-provenance.json").read_text())
        assert provenance["sourceSha"] == apk_source
        assert provenance["imagesAreActualScreenshots"] and not provenance["productionWrites"]
        assert provenance["locale"] == "ru" and provenance["theme"] == "dark"
        manifest = json.loads((folder / "guide-assets/ru/manifest.json").read_text())
        assert set(manifest) == topics
        captures = {(item["topic"], item["step"]): item for item in provenance["captures"]}
        assert len(captures) == len(provenance["captures"]) == 3 * len(topics)
        for topic, steps in manifest.items():
            assert len(steps) == 3 and topic not in combined
            for step, spec in enumerate(steps, 1):
                item = captures[topic, step]
                assert spec["image"] == f"{topic}-{step}.webp"
                source = folder / "guide-assets/ru" / spec["image"]
                png = folder / "screens" / f"guide-source-{topic}-{step}.png"
                assert hashlib.sha256(source.read_bytes()).hexdigest() == item["webpSha256"]
                assert hashlib.sha256(png.read_bytes()).hexdigest() == item["pngSha256"]
                with Image.open(source) as image:
                    assert image.format == "WEBP" and image.size == (spec["width"], spec["height"])
                width, height = item["originalSize"]
                left, top, right, bottom = item["bounds"]
                assert 0 <= left < right <= width and 0 <= top < bottom <= height
                assert spec["target"] == [round(left/width, 6), round(top/height, 6),
                                          round(right/width, 6), round(bottom/height, 6)]
                shutil.copyfile(source, destination / spec["image"])
            combined[topic] = steps
        (evidence / f"capture-{capture_run}.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n")
        origins.append({"capture_run": capture_run, "apk_source_sha": apk_source,
                        "apk_source_run": provenance["sourceRun"], "debug_apk_sha256": provenance["debugApkSha256"]})
    assert set(combined) == TOPICS
    size = sum((destination / frame["image"]).stat().st_size for steps in combined.values() for frame in steps)
    assert size < 4 * 1024 * 1024
    (destination / "manifest.json").write_text(json.dumps(combined, ensure_ascii=False, indent=2) + "\n")
    result = {"origins": origins, "steps": 30, "webp_bytes": size}
    (evidence / "import.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
