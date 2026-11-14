#!/usr/bin/env python3
"""Build seek-only word starts from the same verified Alafasy recordings as the highlights.

Pass --source-dir DIR to read already downloaded NNN.json responses instead of the API.
Existing highlight intervals and Tanzil Arabic are read-only inputs.
"""
import argparse
import hashlib
import json
import struct
from collections import Counter, defaultdict
from pathlib import Path

import import_word_timings_51 as words


EVIDENCE = words.ROOT / "tools/quran/source-evidence/0828"
TARGET = words.AUDIO / "word_starts"
INTERVALS = words.AUDIO / "word_intervals"
URL = words.API + "/surahs/{surah}/word-timings?include=text,audio"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def seek_positions(arabic, tokens, prelude=()):
    mapped = words.match_positions(arabic, tokens)
    display = [(i, key) for i, token in enumerate(arabic.split(), 1)
               if (key := words.letters(token))]
    source = [(i, key) for i, token, *_ in tokens if (key := words.letters(token))]
    if prelude and len(display) == len(source) + len(prelude):
        if tuple(key for _, key in display[:len(prelude)]) != prelude:
            return mapped
        display = display[len(prelude):]
    if len(display) != len(source):
        return mapped
    # An undotted yeh glyph in Tanzil can be dotted in the publisher's edition
    # (e.g. فِى / فِي or شَىْءٍ / شَيۡءࣲ). Accept only that directional change,
    # and only if every lexical token in the entire numbered verse aligns at its
    # original ordinal. Never use this lookup normalization on displayed text.
    for (pos, original), (number, published) in zip(display, source):
        if len(original) != len(published) or any(
                left != right and (left, right) != ("ى", "ي")
                for left, right in zip(original, published)):
            return mapped
        if number in mapped and mapped[number] != pos:
            return mapped
    return {number: pos for (pos, _), (number, _) in zip(display, source)}


def extract_starts(data, mapped, windows, existing):
    known = {(a, w) for a, w, _, _ in struct.iter_unpack(">HHII", existing[12:])}
    estimated = set(data.get("estimated") or [])
    rows = data["words"]
    starts = Counter(start for _, _, start, _, _ in rows)
    candidates = defaultdict(list)
    for index, (ayah, token, start, end, occurrence) in enumerate(rows):
        position = mapped.get(ayah, {}).get(token)
        if position is not None and (ayah, position) not in known:
            candidates[ayah, position].append((occurrence, index, start, end))

    result = []
    for (ayah, position), occurrences in sorted(candidates.items()):
        first = [row for row in occurrences if row[0] == 1]
        if len(first) != 1:
            continue
        _, index, start, end = first[0]
        lower, upper = windows[ayah]
        duration = data["audio"]["duration_ms"]
        if (index not in estimated and starts[start] == 1 and lower <= start < upper
                and start < duration and start < end <= duration + 1000
                and all(row[2] >= start for row in occurrences)):
            result.append((ayah, position, start))
    return result


def disjoint_intervals(data, mapped, windows, starts):
    """Retain only the measured portion of each occurrence without any competing source row."""
    new_words = {(a, w) for a, w, _ in starts}
    rows = sorted(enumerate(data["words"]), key=lambda item: item[1][2])
    estimates = set(data.get("estimated") or [])
    duration = data["audio"]["duration_ms"]
    previous_end = 0
    out = []
    for i, (index, (ayah, token, start, end, _)) in enumerate(rows):
        position = mapped.get(ayah, {}).get(token)
        if (ayah, position) in new_words and index not in estimates and 0 <= start < end <= duration + 1000:
            # Limit both sides using original publisher endpoints, including intervals
            # for unmapped or estimated words. Leave the ambiguous overlap unhighlighted.
            low, high = windows[ayah]
            next_start = rows[i + 1][1][2] if i + 1 < len(rows) else end
            left = max(start, previous_end, low)
            right = min(end, next_start, high, duration)
            if left < right:
                out.append((ayah, position, left, right))
        previous_end = max(previous_end, end)
    return out


def frame(magic, surah, row_format, rows):
    return struct.pack(">4sII", magic, surah, len(rows)) + b"".join(
        struct.pack(row_format, *row) for row in rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path)
    args = parser.parse_args()
    verses = words.read_display()
    basmala = tuple(words.letters(token) for token in verses[1][1].split())
    release = json.loads((EVIDENCE / "word-timing-report-51.json").read_bytes())["surahs"]
    tracks = {t["surah"]: t["files"]["original"]
              for t in json.loads((EVIDENCE / "alafasy-v4-surahs.json").read_bytes())["data"]}
    windows = {s["surah"]: {a: (start, end) for a, start, end in s["ayahs"]}
               for s in json.loads((words.AUDIO / "alafasy_ayah_timings.json").read_bytes())["surahs"]}
    if [s["surah"] for s in release] != list(range(1, 115)):
        raise ValueError("Incomplete source manifest")
    report, assets = [], {}
    for entry in release:
        n = entry["surah"]
        raw = ((args.source_dir / f"{n:03}.json").read_bytes()
               if args.source_dir else words.fetch(URL.format(surah=n)))
        if digest(raw) != entry["source_sha256"]:
            raise ValueError(f"Publisher data changed for surah {n}")
        baseline = (words.OUTPUT / f"{n}.bin").read_bytes()
        regenerated, _ = words.convert(n, tracks[n], verses[n], raw)
        if baseline != regenerated or digest(baseline) != entry["asset_sha256"]:
            raise ValueError(f"Word intervals or recording mismatch for surah {n}")
        data = json.loads(raw)["data"]
        mapped = {a: seek_positions(verses[n][a], tokens, basmala if a == 1 and 0 in windows[n] else ())
                  for a, tokens in data["text"]["ayahs"] if a > 0}
        rows = extract_starts(data, mapped, windows[n], baseline)
        spans = disjoint_intervals(data, mapped, windows[n], rows)
        asset_hash = interval_hash = None
        if rows:
            binary = frame(b"ZQS1", n, ">HHI", rows)
            assets[TARGET / f"{n}.bin"] = binary
            asset_hash = digest(binary)
        if spans:
            binary = frame(b"ZQW1", n, ">HHII", spans)
            assets[INTERVALS / f"{n}.bin"] = binary
            interval_hash = digest(binary)
        report.append({"surah": n, "count": len(rows), "asset_sha256": asset_hash,
                       "interval_count": len(spans), "interval_asset_sha256": interval_hash,
                       "source_sha256": entry["source_sha256"]})
    for folder in (TARGET, INTERVALS):
        folder.mkdir(parents=True, exist_ok=True)
        if set(folder.glob("*.bin")) - set(assets):
            raise ValueError(f"Unexpected supplementary files in {folder}")
    for path, binary in assets.items():
        path.write_bytes(binary)
    manifest = {"source_api_template": URL, "format": "ZQS1",
                "total_starts": sum(s["count"] for s in report),
                "total_intervals": sum(s["interval_count"] for s in report), "surahs": report}
    (EVIDENCE / "word-start-report-54.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f'{manifest["total_starts"]} verified additional starts and {manifest["total_intervals"]} nonoverlapping intervals')


if __name__ == "__main__":
    main()
