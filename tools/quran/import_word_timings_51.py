#!/usr/bin/env python3
"""Bundle MP3Quran word intervals for the exact Alafasy r1 files used by the player."""
import concurrent.futures
import hashlib
import json
import re
import struct
import time
import unicodedata
import urllib.error
import urllib.request
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
AUDIO = ASSETS / "quran_audio"
OUTPUT = AUDIO / "words"
API = "https://api-staging.mp3quran.net/v4/recitations/mishary-alafasy/r1"
MAGIC = b"ZQW1"


def fetch(url):
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"Accept": "application/json",
                                                         "User-Agent": "ZikrTime/0.8.28 timing-import"})
            with urllib.request.urlopen(request, timeout=45) as response:
                return response.read()
        except (TimeoutError, urllib.error.URLError) as exc:
            status = getattr(exc, "code", None)
            if attempt == 3 or (status is not None and status < 500 and status != 429):
                raise
            delay = int(exc.headers.get("Retry-After", 0)) if status == 429 else 0
            time.sleep(max(delay, 2 ** attempt))


def letters(text):
    # Comparing rasm only: the display always uses the original, unmodified Tanzil string.
    text = unicodedata.normalize("NFKD", text).replace("ٱ", "ا")
    return "".join(c for c in text if unicodedata.category(c).startswith("L")
                   and c not in "ـۥۦ" and not 0x06D6 <= ord(c) <= 0x06ED)


def match_positions(arabic, words):
    # Keep the original token ordinal so a separate pause symbol never shifts the highlight.
    display = [(i, key) for i, token in enumerate(re.findall(r"\S+", arabic), 1)
               if (key := letters(token))]
    publisher = [(int(w[0]), key) for w in words if (key := letters(w[1]))]
    target = [key for _, key in display]
    source = [key for _, key in publisher]
    if len(source) == len(target):
        return {publisher[i][0]: display[i][0] for i in range(len(source))
                if source[i] == target[i]}
    # Missing/extra tokens and separately printed basmalahs can occur. Keep only exact
    # runs anchored by at least two words, or a unique matching word on both sides.
    matches = {}
    for a, b, length in SequenceMatcher(None, source, target, autojunk=False).get_matching_blocks():
        if length == 1 and (source.count(source[a]) != 1 or target.count(target[b]) != 1):
            continue
        for i in range(length):
            matches[publisher[a + i][0]] = display[b + i][0]
    return matches


def read_display():
    relative = "sources/quran-uthmani.txt"
    path = ASSETS / "quran" / relative
    raw = path.read_bytes()
    manifest = json.loads((ASSETS / "quran/manifest.json").read_bytes())
    assert hashlib.sha256(raw).hexdigest() == manifest["files"][relative]
    verses = {}
    for line in raw.decode("utf-8").splitlines():
        if line and not line.startswith("#"):
            surah, ayah, text = line.split("|", 2)
            verses.setdefault(int(surah), {})[int(ayah)] = text
    assert len(verses) == 114 and sum(map(len, verses.values())) == 6236
    return verses


def convert(surah, expected_track, display, raw):
    data = json.loads(raw)["data"]
    assert data["surah"] == surah, surah
    track = data["audio"]
    assert track["sha256"] == expected_track["sha256"] and track["bytes"] == expected_track["bytes"], surah
    duration = track["duration_ms"]
    text = data["text"]
    assert text and str(text["edition"]).startswith("hafs"), (surah, "unexpected text edition")
    source_words = {ayah: words for ayah, words in text["ayahs"] if ayah > 0}
    matched = {ayah: match_positions(verse, source_words.get(ayah, [])) for ayah, verse in display.items()}
    estimated = set(data.get("estimated") or [])
    entries = []
    omitted_text = 0
    omitted_invalid = 0
    for index, (ayah, word, start, end, occurrence) in enumerate(data["words"]):
        if ayah <= 0 or index in estimated:
            continue
        assert ayah in display and occurrence >= 1
        pos = matched[ayah].get(word)
        if not pos:
            omitted_text += 1
            continue
        if not 0 <= start < end <= duration + 1000:
            omitted_invalid += 1
            continue
        entries.append((start, end, ayah, pos))

    # An overlapping group has no unique spoken word. Never estimate a replacement.
    entries.sort()
    clean = []
    ambiguous = 0
    group_end = -1
    group = []
    for entry in entries:
        if entry[0] >= group_end and group:
            if len(group) == 1:
                clean.append(group[0])
            else:
                ambiguous += len(group)
            group = []
        group.append(entry)
        group_end = max(group_end, entry[1])
    if len(group) == 1:
        clean.append(group[0])
    else:
        ambiguous += len(group)
    assert clean, (surah, "no verified word intervals")
    binary = bytearray(struct.pack(">4sII", MAGIC, surah, len(clean)))
    for start, end, ayah, pos in clean:
        binary += struct.pack(">HHII", ayah, pos, start, end)
    covered = {ayah for _, _, ayah, _ in clean}
    return binary, {"surah": surah, "intervals": len(clean), "ayahs": len(covered),
                    "ayahs_without_words": sorted(set(display) - covered),
                    "estimated": len(estimated), "unmapped": omitted_text, "overlaps": ambiguous,
                    "invalid": omitted_invalid, "source_sha256": hashlib.sha256(raw).hexdigest(),
                    "asset_sha256": hashlib.sha256(binary).hexdigest()}


def main():
    display = read_display()
    checked = json.loads((ROOT / "tools/quran/source-evidence/0828/alafasy-v4-surahs.json").read_bytes())
    checked = {row["surah"]: row for row in checked["data"]}
    tracks = json.loads((AUDIO / "alafasy.json").read_bytes())["tracks"]
    assert len(tracks) == len(checked) == 114
    for track in tracks:
        n = track["surah_id"]
        assert track["url"] == f"https://cdn.mp3quran.net/audio/mishary-alafasy/r1/{n:03}.mp3"
        assert track["size_bytes"] == checked[n]["files"]["original"]["bytes"], n

    def chapter(n):
        raw = fetch(f"{API}/surahs/{n}/word-timings?include=text,audio")
        return n, *convert(n, checked[n]["files"]["original"], display[n], raw)

    OUTPUT.mkdir(parents=True, exist_ok=True)
    report = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        for surah, binary, info in pool.map(chapter, range(1, 115)):
            (OUTPUT / f"{surah}.bin").write_bytes(binary)
            report.append(info)
    target = ROOT / "build/reports/quran-word-timings.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({"publisher": API, "surahs": report}, indent=2) + "\n", encoding="utf-8")
    totals = {field: sum(r[field] for r in report) for field in
              ("intervals", "ayahs", "estimated", "unmapped", "overlaps", "invalid")}
    print(f"Verified 114 Alafasy r1 recordings, 6236 Tanzil ayahs. Word timing totals: {totals}")


if __name__ == "__main__":
    main()
