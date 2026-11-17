#!/usr/bin/env python3
"""Build complete reciter-specific catalogues and timed assets from MP3Quran v4 r1.

Use --cache DIR to reuse retrieved responses. Audio is streamed directly from MP3Quran.
"""
import argparse
import concurrent.futures
import hashlib
import json
import tempfile
import zipfile
import zlib
from collections import Counter, defaultdict
from pathlib import Path
from urllib.parse import urljoin

import import_word_timings_51 as words
from import_seek_starts_54 import disjoint_intervals, extract_starts, frame, seek_positions

ORIGIN = "https://api-staging.mp3quran.net"
CREDITS = "https://www.mp3quran.net/eng/privacy"
RECITERS = {
    "yasser-dosari": ("Yasser Al-Dosari", "yasser"),
    "mahmoud-husary": ("Mahmoud Khalil Al-Hussary", "husr"),
    "muhammad-minshawi": ("Mohammed Siddiq Al-Minshawi", "minsh"),
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read_response(url, target):
    if target.exists():
        return target.read_bytes()
    raw = words.fetch(url)
    json.loads(raw)["data"]
    target.parent.mkdir(parents=True, exist_ok=True)
    temp = target.with_suffix(".download")
    temp.write_bytes(raw)
    temp.replace(target)
    return raw


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def catalogue(base, cache):
    url = base + "/surahs"
    chapters = {}
    page_hashes = []
    while url:
        path = cache / ("catalog.json" if not page_hashes else f"catalog-{len(page_hashes) + 1}.json")
        raw = read_response(url, path)
        page_hashes.append(sha(raw))
        page = json.loads(raw)
        for row in page["data"]:
            assert row["surah"] not in chapters
            chapters[row["surah"]] = row
        nxt = page["links"].get("next")
        url = urljoin(ORIGIN, nxt) if nxt else None
        if url and not url.startswith(base + "/surahs?"):
            raise ValueError(f"Unexpected catalogue cursor {url}")
    assert set(chapters) == set(range(1, 115))
    return chapters, page_hashes


def ayah_windows(n, data, count, duration):
    assert data["surah"] == n and data["complete"], n
    rows = []
    repeated = 0
    for ayah, start, end in data["ayahs"]:
        assert -1 <= ayah <= count and 0 <= start < end <= duration + 1000, (n, ayah, start, end, duration)
        assert not rows or rows[-1][2] <= start, (n, ayah, start, rows[-1])
        if rows and rows[-1][0] == ayah:
            # Keep the repeated adjacent recording in its own ayah's playback window.
            rows[-1][2] = end
            repeated += 1
        else:
            rows.append([ayah, start, end])
    first_readings = list(dict.fromkeys(a for a, _, _ in rows if a > 0))
    assert first_readings == list(range(1, count + 1)), n
    return rows, repeated


def align(n, data, original, display, occurrences, basmala):
    windows = {a: spans[0] for a, spans in occurrences.items()}
    assert data["surah"] == n and data["text"]["edition"].startswith("hafs")
    assert data["audio"]["sha256"] == original["sha256"]
    assert data["audio"]["bytes"] == original["bytes"]
    # In e.g. 2:72 the Hamza above a superscript Alef and the standalone Hamza
    # have the same ordinal in both editions. Normalize this exact pair only for lookup.
    # A written basmala can precede the verse even when this recording omits its audio.
    mapped = {a: seek_positions(display[a].replace("\u0670\u0654", "\u0670\u0621"),
                                tokens, basmala if a == 1 and n not in (1, 9) else ())
              for a, tokens in data["text"]["ayahs"] if a > 0}
    estimates = set(data.get("estimated") or [])
    rows = sorted(enumerate(data["words"]), key=lambda item: item[1][2])
    first = Counter((a, w) for a, w, _, _, rep in data["words"] if rep == 1)
    valid = []
    first_is_clean = set()
    previous_end = 0
    for i, (index, (a, w, start, end, rep)) in enumerate(rows):
        pos = mapped.get(a, {}).get(w)
        next_start = rows[i + 1][1][2] if i + 1 < len(rows) else end
        if pos is not None and index not in estimates:
            inside = any(low <= start < end <= high for low, high in occurrences[a])
            # Include all original source rows when excluding ambiguously overlapping speech.
            if inside and start >= previous_end and end <= next_start:
                valid.append((a, pos, start, end))
                if rep == 1 and first[(a, w)] == 1:
                    first_is_clean.add((a, pos))
        previous_end = max(previous_end, end)
    # Resolve a tap to the first measured onset, also when a later repetition has cleaner alignment.
    base = [r for r in valid if (r[0], r[1]) in first_is_clean]
    primary = frame(b"ZQW1", n, ">HHII", base)
    assert base, n
    starts = extract_starts(data, mapped, windows, primary)
    bounds = {a: (spans[0][0], spans[-1][1]) for a, spans in occurrences.items()}
    extras = [row for row in disjoint_intervals(data, mapped, bounds, starts)
              if any(lo <= row[2] < row[3] <= hi for lo, hi in occurrences[row[0]])]
    spans = sorted(base + extras, key=lambda r: r[2])
    assert all(left[3] <= right[2] for left, right in zip(spans, spans[1:])), n
    assert {a for a, _, _, _ in spans} == set(display), n
    coverage = {(a, w) for a, w, _, _ in spans}
    seekable = {(a, w) for a, w, _, _ in base} | {(a, w) for a, w, _ in starts}
    info = {"ayahs": len(display), "highlighted_numbered_words": len(coverage),
            "seekable_numbered_words": len(seekable), "measured_intervals": len(spans),
            "publisher_estimates_omitted": len(estimates)}
    assets = {"words": primary}
    if starts:
        assets["word_starts"] = frame(b"ZQS1", n, ">HHI", starts)
    if extras:
        assets["word_intervals"] = frame(b"ZQW1", n, ">HHII", extras)
    return assets, info


def save_packs(slug, assets):
    # Use fixed entry metadata and sorted filenames so offline regeneration is byte-for-byte stable.
    # Split by compressed size; a build-time Sync task expands these into ordinary Android assets.
    directory = words.ROOT / "app/recitation-packs"
    directory.mkdir(parents=True, exist_ok=True)
    entries = sorted(assets.items())
    groups = []
    size = 0
    for path, raw in entries:
        estimate = len(zlib.compress(raw, 9)) + 200 + len(path) * 2
        if not groups or (size + estimate > 225_000 and groups[-1]):
            groups.append([])
            size = 0
        groups[-1].append((path, raw))
        size += estimate
    names = []
    with tempfile.TemporaryDirectory(dir=directory) as work:
        for number, group in enumerate(groups, 1):
            name = f"{slug}-{number:02}.zip"
            names.append(name)
            path = Path(work) / name
            with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for resource, raw in group:
                    entry = zipfile.ZipInfo(f"quran_audio/{slug}/{resource}", (1980, 1, 1, 0, 0, 0))
                    entry.create_system = 3
                    entry.external_attr = 0o100644 << 16
                    archive.writestr(entry, raw, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
            with zipfile.ZipFile(path) as archive:
                for resource, raw in group:
                    assert archive.read(f"quran_audio/{slug}/{resource}") == raw
        for name in names:
            (Path(work) / name).replace(directory / name)
    for stale in directory.glob(f"{slug}-[0-9][0-9].zip"):
        if stale.name not in names:
            stale.unlink()
    return names


def json_bytes(data):
    return (json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n").encode()


def import_reciter(slug, cache_root, verses):
    name, page = RECITERS[slug]
    base = f"{ORIGIN}/v4/recitations/{slug}/r1"
    cache = cache_root / slug
    recitation_raw = read_response(base + "?locale=en", cache / "recitation.json")
    recitation = json.loads(recitation_raw)["data"]
    assert recitation["code"] == slug + "/r1" and recitation["reciter"]["code"] == slug
    assert recitation["style"] == "murattal" and "hafs_an_asim" in [r["code"] for r in recitation["riwayahs"]]
    assert recitation["coverage"]["complete"] and recitation["coverage"]["surahs"] == list(range(1, 115))
    audio = recitation["audio"]
    assert audio["base"] == f"https://cdn.mp3quran.net/audio/{slug}/r1/"
    assert audio["files"]["original"] == "{surah:03d}.mp3"
    chapters, pages = catalogue(base, cache)
    basmala = tuple(words.letters(token) for token in verses[1][1].split())

    def chapter(n):
        row = chapters[n]
        original = row["files"]["original"]
        source = f"{base}/surahs/{n}"
        raw_ayah = read_response(source + "/ayah-timings", cache / "ayahs" / f"{n:03}.json")
        raw_word = read_response(source + "/word-timings?include=text,audio", cache / "words" / f"{n:03}.json")
        ayah_data = json.loads(raw_ayah)["data"]
        rows, repeated = ayah_windows(n, ayah_data, len(verses[n]), row["duration_ms"])
        occurrences = defaultdict(list)
        for a, start, end in rows:
            occurrences[a].append((start, end))
        assets, info = align(n, json.loads(raw_word)["data"], original, verses[n], occurrences, basmala)
        files = {f"{directory}/{n}.bin": content for directory, content in assets.items()}
        track = {"surah_id": n, "url": audio["base"] + audio["files"]["original"].format(surah=n),
                 "size_bytes": original["bytes"], "sha256": original["sha256"]}
        evidence = {"surah": n, "ayah_response_sha256": sha(raw_ayah), "word_response_sha256": sha(raw_word),
                    "audio_sha256": original["sha256"], "adjacent_repeats": repeated, **info}
        return track, {"surah": n, "complete": True, "ayahs": rows}, evidence, files

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(chapter, range(1, 115)))
    tracks, timings, evidence, chapter_files = zip(*results)
    assets = {path: raw for chapter in chapter_files for path, raw in chapter.items()}
    assets["catalog.json"] = json_bytes({"schema_version": 1, "reciter": name, "reciter_id": slug,
               "source": f"https://www.mp3quran.net/eng/{page}", "permission_source": CREDITS,
               "source_api": base, "rewaya": "Hafs an Asim — Murattal", "tracks": tracks})
    assets["ayah_timings.json"] = json_bytes({"schema_version": 1, "source": "https://docs-staging.mp3quran.net/",
               "reciter": slug, "recitation": "r1", "source_api_template": base + "/surahs/{surah}/ayah-timings",
               "surahs": timings})
    totals = {k: sum(x[k] for x in evidence) for k in ("ayahs", "highlighted_numbered_words",
              "seekable_numbered_words", "measured_intervals", "publisher_estimates_omitted")}
    packs = save_packs(slug, assets)
    report = {"source_api": base, "recitation_sha256": sha(recitation_raw), "packs": packs,
              "catalogue_page_sha256": pages, "ayah_asset_sha256": sha(assets["ayah_timings.json"]),
              "totals": totals, "surahs": evidence}
    write_json(words.ROOT / "tools/quran/source-evidence/55" / f"{slug}.json", report)
    print(slug, report["ayah_asset_sha256"], totals, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=words.ROOT / "build/quran-source-cache")
    parser.add_argument("--reciter", choices=RECITERS)
    args = parser.parse_args()
    verses = words.read_display()
    for slug in ([args.reciter] if args.reciter else RECITERS):
        import_reciter(slug, args.cache, verses)


if __name__ == "__main__":
    main()
