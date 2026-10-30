#!/usr/bin/env python3
"""Rebuild the three licensed adhan assets from pinned Commons originals.

No calls to this script occur at app runtime. Original videos and optional
Windows FFmpeg tooling stay in ignored build/adhan; only mono Ogg audio ships.
Requires Python 3.9+ and FFmpeg on PATH, --ffmpeg, or --bootstrap-windows-ffmpeg.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "build" / "adhan"
RAW = ROOT / "app/src/main/res/raw"
SOURCES = [
    {
        "id": "adhan_madinah",
        "url": "https://upload.wikimedia.org/wikipedia/commons/7/7c/33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg",
        "sha1": "4aa432b9e44804e7d72af4429bd743aa5fa9e3a4",
        "seconds": 186.552698412698,
        "title": "33937 ejaz215 call-to-prayer-from-the-prophet-s-mo",
        "author": "ejaz215",
        "page": "https://commons.wikimedia.org/wiki/File:33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg",
        "extension": "ogg",
    },
    {
        "id": "adhan_makkah",
        "url": "https://upload.wikimedia.org/wikipedia/commons/a/a7/Adhan%2C_Great_Mosque_of_Mecca_-_Jan_21%2C_2013.webm",
        "sha1": "818becd553dde887095641bd1c86549db44060bc",
        "seconds": 197.078,
        "title": "Adhan, Great Mosque of Mecca - Jan 21, 2013",
        "author": "Seyfula Islam",
        "page": "https://commons.wikimedia.org/wiki/File:Adhan,_Great_Mosque_of_Mecca_-_Jan_21,_2013.webm",
        "extension": "webm",
    },
    {
        "id": "adhan_fajr",
        "url": "https://upload.wikimedia.org/wikipedia/commons/b/b1/Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm",
        "sha1": "bc75d5cee271efb67f03d3f35474a9362fce103f",
        "seconds": 247.561,
        "title": "Eid al-Fitr Fajr azan at Malmö Mosque - 19 August 2012",
        "author": "Islamic Center Malmö; muezzin Besim Azemi",
        "page": "https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm",
        "extension": "webm",
    },
]
LICENSE = "https://creativecommons.org/licenses/by/3.0/"
# Project-maintained Windows binary packaged by imageio-ffmpeg, pinned on PyPI.
# https://pypi.org/project/imageio-ffmpeg/0.6.0/#files
FFMPEG_WHEEL = "https://files.pythonhosted.org/packages/2c/c6/fa760e12a2483469e2bf5058c5faff664acf66cadb4df2ad6205b016a73d/imageio_ffmpeg-0.6.0-py3-none-win_amd64.whl"
FFMPEG_SHA256 = "02fa47c83703c37df6bfe4896aab339013f62bf02c5ebf2dce6da56af04ffc0a"


def digest(path, algorithm):
    result = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def download(url, path, expected, algorithm):
    if path.exists() and digest(path, algorithm) == expected:
        return
    print("Downloading", path.name, flush=True)
    request = urllib.request.Request(url, headers={"User-Agent": "ZikrTime-build/0.8.14 (licensed audio import)"})
    temporary = path.with_suffix(path.suffix + ".part")
    with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
        shutil.copyfileobj(response, output)
    actual = digest(temporary, algorithm)
    if actual != expected:
        raise ValueError(f"Source checksum mismatch for {path.name}: {actual}; expected {expected}. Review source, do not bypass.")
    temporary.replace(path)


def ffmpeg_path(args):
    if args.ffmpeg:
        return str(Path(args.ffmpeg).resolve())
    installed = shutil.which("ffmpeg")
    if installed:
        return installed
    binary = WORK / "ffmpeg.exe"
    if binary.exists():
        return str(binary)
    if not args.bootstrap_windows_ffmpeg or sys.platform != "win32":
        raise RuntimeError("FFmpeg not found. Set --ffmpeg or, on Windows, --bootstrap-windows-ffmpeg.")
    wheel = WORK / "imageio_ffmpeg-0.6.0-py3-none-win_amd64.whl"
    download(FFMPEG_WHEEL, wheel, FFMPEG_SHA256, "sha256")
    with zipfile.ZipFile(wheel) as archive:
        files = [name for name in archive.namelist() if name.startswith("imageio_ffmpeg/binaries/") and name.endswith(".exe")]
        if len(files) != 1:
            raise ValueError("Expected one FFmpeg executable in the pinned wheel")
        with archive.open(files[0]) as source, binary.open("wb") as destination:
            shutil.copyfileobj(source, destination)
        for name in archive.namelist():
            if "license" in name.lower() and not name.endswith("/"):
                (WORK / Path(name).name).write_bytes(archive.read(name))
    return str(binary)


def run(ffmpeg, args):
    result = subprocess.run([ffmpeg, "-hide_banner", "-nostdin", *args], text=True, capture_output=True, encoding="utf-8", errors="replace", timeout=300)
    if result.returncode:
        raise RuntimeError(result.stderr[-8000:])
    return result.stderr


def loudness(log):
    reports = re.findall(r'\{\s*"input_i".*?\}', log, flags=re.S)
    if not reports:
        raise RuntimeError("FFmpeg did not return loudness measurements")
    return json.loads(reports[-1])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg")
    parser.add_argument("--bootstrap-windows-ffmpeg", action="store_true")
    args = parser.parse_args()
    WORK.mkdir(parents=True, exist_ok=True)
    ffmpeg = ffmpeg_path(args)
    manifest = {"license": LICENSE, "processing": "Audio track only; full duration; mono 44.1kHz Ogg Vorbis quality 5; two-pass loudnorm -16 LUFS / -2 dBTP / LRA 9. No words or pauses removed.", "recordings": []}
    for source in SOURCES:
        original = WORK / (source["id"] + ".original." + source["extension"])
        download(source["url"], original, source["sha1"], "sha1")
        print("Measuring", source["id"], flush=True)
        first = loudness(run(ffmpeg, ["-i", str(original), "-map", "0:a:0", "-af", "aformat=channel_layouts=mono,loudnorm=I=-16:TP=-2:LRA=9:print_format=json", "-f", "null", "-"]))
        normalizer = (
            "aformat=channel_layouts=mono,loudnorm=I=-16:TP=-2:LRA=9:"
            f"measured_I={first['input_i']}:measured_TP={first['input_tp']}:"
            f"measured_LRA={first['input_lra']}:measured_thresh={first['input_thresh']}:"
            f"offset={first['target_offset']}:linear=true:print_format=json"
        )
        output = RAW / (source["id"] + ".ogg")
        temporary = WORK / (source["id"] + ".ogg")
        run(ffmpeg, ["-y", "-i", str(original), "-map", "0:a:0", "-vn", "-af", normalizer, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", "-metadata", "title=" + source["title"], "-metadata", "artist=" + source["author"], "-metadata", "license=" + LICENSE, "-metadata", "comment=" + source["page"], str(temporary)])
        verification_log = run(ffmpeg, ["-i", str(temporary), "-af", "loudnorm=I=-16:TP=-2:LRA=9:print_format=json", "-f", "null", "-"])
        measured = loudness(verification_log)
        duration_match = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", verification_log)
        if duration_match is None:
            raise RuntimeError("Missing duration in output")
        hours, minutes, seconds = map(float, duration_match.groups())
        duration = hours * 3600 + minutes * 60 + seconds
        if abs(duration - source["seconds"]) > 0.5:
            raise ValueError(f"Duration changed for {source['id']}: {duration}")
        if float(measured["input_tp"]) >= 0:
            raise ValueError("Encoded audio reaches clipping; reduce true-peak target")
        if abs(float(measured["input_i"]) + 16) > 1.5:
            raise ValueError(f"Unexpected final loudness: {measured['input_i']}")
        shutil.copyfile(temporary, output)
        manifest["recordings"].append({**source, "license": LICENSE, "source_sha256": digest(original, "sha256"), "output_sha256": digest(output, "sha256"), "output_bytes": output.stat().st_size, "output_seconds": duration, "output_loudness": measured})
        print(f"Verified {source['id']}: {duration}s, {measured['input_i']} LUFS, {measured['input_tp']} dBTP", flush=True)
    manifest_path = ROOT / "tools/audio/adhan_manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Wrote", manifest_path, flush=True)


if __name__ == "__main__":
    main()
