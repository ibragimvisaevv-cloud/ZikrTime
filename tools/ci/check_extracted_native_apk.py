"""Compare shipped ARMs, preserved package resources and native ELF alignment."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile


def inspect_elf(data):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected a 64-bit little-endian ELF")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    headers = [struct.unpack_from("<IIQQQQQQ", data, offset + i * size) for i in range(count)]
    segments = [align for typ, _, _, _, _, _, _, align in headers if typ == 1]
    if not segments or min(segments) < 16384:
        raise ValueError(f"ELF LOAD segment alignment: {segments}")
    return min(segments)


with zipfile.ZipFile(sys.argv[1]) as previous, zipfile.ZipFile(sys.argv[2]) as current:
    libs = {n for n in previous.namelist() if n.startswith("lib/") and n.endswith(".so")}
    assert libs and libs == {n for n in current.namelist() if n.startswith("lib/") and n.endswith(".so")}
    assert {n.split("/")[1] for n in libs} == {"arm64-v8a", "armeabi-v7a"}
    for name in sorted(libs):
        data = current.read(name)
        assert data == previous.read(name), name + " changed"
        assert current.getinfo(name).compress_type == zipfile.ZIP_DEFLATED, name + " is not compressed"
        if name.startswith("lib/arm64-v8a/"):
            print(f"{name}: ELF LOAD {inspect_elf(data)}, compressed, SHA256 {hashlib.sha256(data).hexdigest()}")
    assets = {n for n in previous.namelist() if n.startswith("assets/")}
    if sys.argv[3:] == ["--removed-transcription"]:
        shipped = {n for n in current.namelist() if n.startswith("assets/")}
        removed = {n for n in assets if n.startswith("assets/quran_transcription/")}
        assert removed, "Previous APK did not contain the transcription"
        assert shipped == assets - removed, "Unexpected additions or removals in the APK assets"
        for name in shipped:
            if not name.startswith("assets/dexopt/"):
                assert current.read(name) == previous.read(name), "Changed product data: " + name
        print(f"Verified {len(libs)} native libraries, removal of {len(removed)} transcription files and preserved remaining assets.")
        sys.exit(0)
    if sys.argv[3:] == ["--source-assets"]:
        # Compare the packaged assets to this release's inputs. The Cyrillic text
        # and additional recitations intentionally extend the previous APK.
        root = Path("app/src/main/assets")
        for file in root.rglob("*"):
            if file.is_file():
                name = "assets/" + file.relative_to(root).as_posix()
                assert current.read(name) == file.read_bytes(), name
        packed_names = set()
        packs = sorted(Path("app/recitation-packs").glob("*.zip"))
        assert packs, "Recitation packs are missing"
        for pack in packs:
            with zipfile.ZipFile(pack) as source:
                for entry in source.infolist():
                    if entry.is_dir():
                        continue
                    name = "assets/" + entry.filename
                    assert name not in packed_names, "Duplicate recitation asset: " + name
                    assert current.read(name) == source.read(entry), name
                    packed_names.add(name)
        for name in assets:
            if name.startswith("assets/quran/sources/"):
                assert current.read(name) == previous.read(name), "Underlying Quran source changed: " + name
        print(f"Verified {len(libs)} native libraries, {len(packed_names)} packed recitation assets, source assets, and previous Quran text.")
        sys.exit(0)
    added = set()
    if len(sys.argv) == 4:
        report = json.loads(Path(sys.argv[3]).read_text())
        reference = json.loads(Path("tools/quran/source-evidence/0828/word-timing-report-51.json").read_text())
        by_surah = {row["surah"]: row for row in report["surahs"]}
        assert report["format"] == "ZQS1" and len(report["surahs"]) == 114 and set(by_surah) == set(range(1, 115))
        total_starts = total_intervals = 0
        ayah_bounds = json.loads(current.read("assets/quran_audio/alafasy_ayah_timings.json"))["surahs"]
        for original in reference["surahs"]:
            surah = original["surah"]
            row = by_surah[surah]
            assert row["source_sha256"] == original["source_sha256"], f"Changed timing source for {surah}"
            bounds = {a: (lo, hi) for a, lo, hi in ayah_bounds[surah - 1]["ayahs"] if a > 0}
            extra_words = set()
            for folder, count_key, hash_key, magic, fmt in (
                ("word_starts", "count", "asset_sha256", b"ZQS1", ">HHI"),
                ("word_intervals", "interval_count", "interval_asset_sha256", b"ZQW1", ">HHII"),
            ):
                count = row[count_key]
                if count == 0:
                    assert row[hash_key] is None
                    continue
                name = f"assets/quran_audio/{folder}/{surah}.bin"
                data = current.read(name)
                assert count > 0 and len(data) == 12 + count * struct.calcsize(fmt)
                assert struct.unpack_from(">4sII", data) == (magic, surah, count)
                assert hashlib.sha256(data).hexdigest() == row[hash_key], name
                added.add(name)
                records = list(struct.iter_unpack(fmt, data[12:]))
                if folder == "word_starts":
                    extra_words = {(a, w) for a, w, start in records}
                    assert len(extra_words) == count and records == sorted(records)
                    assert all(bounds[a][0] <= start < bounds[a][1] for a, w, start in records)
                    total_starts += count
                else:
                    assert all((a, w) in extra_words and bounds[a][0] <= start < end <= bounds[a][1]
                               for a, w, start, end in records)
                    base = current.read(f"assets/quran_audio/words/{surah}.bin")
                    intervals = sorted((start, end) for _, _, start, end in
                                       list(struct.iter_unpack(">HHII", base[12:])) + records)
                    assert all(lo >= prev_end for (_, prev_end), (lo, _) in zip(intervals, intervals[1:]))
                    total_intervals += count
        assert total_starts and total_starts == report["total_starts"]
        assert total_intervals and total_intervals == report["total_intervals"]
        print(f"Verified {total_starts} new word starts and {total_intervals} extra display intervals.")
    assert assets and not assets.intersection(added)
    assert assets | added == {n for n in current.namelist() if n.startswith("assets/")}
    # ART profiles index the DEX generated for this precise version. Their checksum
    # changes with BuildConfig.VERSION_CODE, while the shipped product data must match.
    generated_profiles = {"assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"}
    product_assets = assets - generated_profiles
    for name in product_assets:
        assert previous.read(name) == current.read(name), name + " changed"
    print(f"Verified {len(libs)} native libraries and {len(product_assets)} unchanged product assets.")
