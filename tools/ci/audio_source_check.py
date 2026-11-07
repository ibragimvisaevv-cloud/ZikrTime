"""Bounded read of real MP3Quran Alafasy recordings before the Android journey.

This verifies current source reachability separately from app behavior. It never
downloads the complete Quran, supplies synthetic audio or overrides app URLs.
"""
import json
from pathlib import Path
import re
import time
import urllib.request

OUT = Path("ci-out")
OUT.mkdir(exist_ok=True)
catalog = json.loads(Path("app/src/main/assets/quran_audio/alafasy.json").read_text())
assert catalog["schema_version"] == 1
assert catalog["reciter_id"] == "alafasy"
assert catalog["source_reciter_id"] == 123
tracks = {track["surah_id"]: track for track in catalog["tracks"]}
assert sorted(tracks) == list(range(1, 115)), "Catalogue must contain all 114 distinct surahs"
for number, track in tracks.items():
    expected = f"https://cdn.mp3quran.net/audio/mishary-alafasy/r1/{number:03}.mp3"
    assert track["url"] == expected, (number, "Unexpected recording source")
    assert isinstance(track["size_bytes"], int) and track["size_bytes"] > 0

report = {"source": catalog["source"], "reciter": catalog["reciter"],
          "catalogue_surahs": len(tracks), "full_recordings_downloaded": 0, "probes": []}
try:
    # 98 is long enough for background/seek assertions without downloading a
    # long chapter. 114 is the small real download used for offline checks.
    for number in (98, 114):
        track = tracks[number]
        error = None
        for attempt in range(3):
            try:
                request = urllib.request.Request(track["url"], headers={
                    "Range": "bytes=0-4095", "User-Agent": "ZikrTime-Audio-Check/0.8.27",
                })
                with urllib.request.urlopen(request, timeout=25) as response:
                    body = response.read(4097)
                    assert response.status == 206, (number, response.status, "Byte ranges not supported")
                    content_range = response.headers.get("Content-Range", "")
                    match = re.fullmatch(r"bytes 0-4095/(\d+)", content_range)
                    assert match and int(match.group(1)) == track["size_bytes"], (number, content_range)
                    assert len(body) == 4096, (number, len(body))
                    assert body.startswith(b"ID3") or any(
                        body[i] == 255 and body[i+1] & 224 == 224 for i in range(min(100, len(body)-1))
                    ), (number, "Response is not an MP3 header")
                    report["probes"].append({
                        "surah": number, "status": response.status, "bytes_read": len(body),
                        "content_type": response.headers.get("Content-Type"),
                        "content_range": content_range, "url": track["url"],
                    })
                error = None
                break
            except Exception as exc:
                error = exc
                if attempt < 2:
                    time.sleep(2)
        if error:
            raise error
    report["complete"] = True
finally:
    (OUT / "audio-source-probe.json").write_text(json.dumps(report, indent=2))
print(json.dumps(report))
