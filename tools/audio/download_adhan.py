#!/usr/bin/env python3
"""Rebuild the three licensed adhan assets from the reciter’s pinned original recordings.

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
COMMIT = "ad2bf1799add51cca4aaf1f123b1c212378c7786"
REPO = "https://github.com/nandxorandor/Athan"
LICENSE = "https://creativecommons.org/licenses/by/4.0/"
AUTHOR = "Ahmed Khalaf"
SOURCES = [
    ("athan_khalaf_one", "developer/Developer_Athan-1.mp3", "48ce7a63922bc666be0d4919056a31445ace775b3b7ffc4ab07325d8edf9ddb6", 144.640, 1.62),
    ("athan_khalaf_two", "developer/Developer_Athan-2.mp3", "0fb1127d6fea0d8552999c10798fd19624c0815128d49e420f650ef5bdd79acb", 132.149, 1.00),
    ("athan_khalaf_fajr", "fajr/Developer_Athan-3_Fajr.mp3", "18b43fca35558ed05de0cbf8fcf468fd606e17cacb688bfed065e45787f56378", 99.464, 0.65),
]
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
    request = urllib.request.Request(url, headers={"User-Agent": "ZikrTime-build/0.8.22 (licensed audio import)"})
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
    manifest = {"license": LICENSE, "license_statement": f"{REPO}/blob/{COMMIT}/LICENSE",
                "processing": "Trimmed lead-in only; 80 Hz high pass, two-pass loudness normalization, mono 44.1 kHz Ogg Vorbis.", "recordings": []}
    for name, file_name, checksum, seconds_original, lead in SOURCES:
        path = "app/src/main/assets/athan/" + file_name
        source = {"id": name, "title": Path(path).stem, "author": AUTHOR, "page": f"{REPO}/blob/{COMMIT}/{path}",
                  "url": f"https://raw.githubusercontent.com/nandxorandor/Athan/{COMMIT}/{path}",
                  "seconds": seconds_original, "trim_seconds": lead}
        original = WORK / Path(path).name
        download(source["url"], original, checksum, "sha256")
        pre = f"atrim=start={lead},asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,highpass=f=80,"
        print("Measuring", source["id"], flush=True)
        first = loudness(run(ffmpeg, ["-i", str(original), "-map", "0:a:0", "-af", pre + "loudnorm=I=-16:TP=-2:LRA=9:print_format=json", "-f", "null", "-"]))
        normalizer = (
            pre + "loudnorm=I=-16:TP=-2:LRA=9:"
            f"measured_I={first['input_i']}:measured_TP={first['input_tp']}:"
            f"measured_LRA={first['input_lra']}:measured_thresh={first['input_thresh']}:"
            f"offset={first['target_offset']}:linear=true:print_format=json"
        )
        output = RAW / (source["id"] + ".ogg")
        temporary = WORK / (source["id"] + ".ogg")
        run(ffmpeg, ["-y", "-i", str(original), "-map", "0:a:0", "-vn", "-map_metadata", "-1", "-af", normalizer, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", "-metadata", "title=" + source["title"], "-metadata", "artist=" + source["author"], "-metadata", "license=" + LICENSE, "-metadata", "comment=" + source["page"], str(temporary)])
        verification_log = run(ffmpeg, ["-i", str(temporary), "-af", "loudnorm=I=-16:TP=-2:LRA=9:print_format=json", "-f", "null", "-"])
        measured = loudness(verification_log)
        duration_match = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", verification_log)
        if duration_match is None:
            raise RuntimeError("Missing duration in output")
        hours, minutes, seconds = map(float, duration_match.groups())
        duration = hours * 3600 + minutes * 60 + seconds
        if abs(duration - (seconds_original - lead)) > 0.5:
            raise ValueError(f"Duration changed for {source['id']}: {duration}")
        if float(measured["input_tp"]) >= 0:
            raise ValueError("Encoded audio reaches clipping; reduce true-peak target")
        if abs(float(measured["input_i"]) + 16) > 1.5:
            raise ValueError(f"Unexpected final loudness: {measured['input_i']}")
        # Detect two consecutive 20 ms frames above -36 dBFS in the decoded output.
        import array
        import math
        pcm = subprocess.check_output([ffmpeg, "-v", "error", "-i", str(temporary), "-t", "1", "-ac", "1", "-ar", "16000", "-f", "s16le", "pipe:1"])
        samples = array.array("h")
        samples.frombytes(pcm)
        if sys.byteorder != "little":
            samples.byteswap()
        frames = [samples[i:i + 320] for i in range(0, len(samples) - 319, 320)]
        active = [math.sqrt(sum(v*v for v in frame) / 320) / 32768 > 10**(-36 / 20) for frame in frames]
        onset = next((i*20 for i in range(len(active)-1) if active[i] and active[i+1]), None)
        if onset is None or onset > 220:
            raise ValueError(f"Late onset after encoding: {onset} ms")
        source["output_onset_ms_at_minus36dbfs"] = onset
        shutil.copyfile(temporary, output)
        manifest["recordings"].append({**source, "license": LICENSE, "source_sha256": digest(original, "sha256"), "output_sha256": digest(output, "sha256"), "output_bytes": output.stat().st_size, "output_seconds": duration, "output_loudness": measured})
        print(f"Verified {source['id']}: {duration}s, {measured['input_i']} LUFS, {measured['input_tp']} dBTP", flush=True)
    manifest_path = ROOT / "tools/audio/adhan_manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Wrote", manifest_path, flush=True)


if __name__ == "__main__":
    main()
