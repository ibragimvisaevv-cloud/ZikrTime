#!/usr/bin/env python3
"""Check the actual packaged data against published reciter-specific timebases offline."""
import hashlib
import json
import struct
import unittest
from collections import defaultdict
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
PACKS = ROOT / "app/recitation-packs"
EVIDENCE = ROOT / "tools/quran/source-evidence/55"
# MP3Quran v4, surah 5 original recording SHA and the first word of 5:2 in ms.
TRACK_5 = {
    "yasser-dosari": ("48332d99dea45700384c918707361c33162fdb5f31941fd6e3950935cb7139c3", 29490, 31865),
    "mahmoud-husary": ("ada880915f3d313d56ab9fcc1b5b0cb3a1017855af685eee7459c9c9cf074f7c", 48510, 51665),
    "muhammad-minshawi": ("c71e31d7121857555b1912d01b95b870ef25ca9182f78d23784eff78bc65c219", 40100, 42985),
}


class RecitationPacksTest(unittest.TestCase):
    def test_recording_and_word_timebases(self):
        counts = json.loads((ROOT / "app/src/main/assets/quran/manifest.json").read_bytes())["surahs"]
        for slug, (surah_5_digest, verse_start, first_word_end) in TRACK_5.items():
            with self.subTest(slug=slug):
                proof = json.loads((EVIDENCE / f"{slug}.json").read_bytes())
                assets = {}
                prefix = f"quran_audio/{slug}/"
                published_packs = sorted(p.name for p in PACKS.glob(f"{slug}-*.zip"))
                self.assertEqual(published_packs, proof["packs"])
                for pack in published_packs:
                    with ZipFile(PACKS / pack) as zipped:
                        for item in zipped.infolist():
                            self.assertTrue(item.filename.startswith(prefix))
                            path = item.filename[len(prefix):]
                            self.assertNotIn(path, assets)
                            assets[path] = zipped.read(item)
                catalog = json.loads(assets["catalog.json"])
                self.assertEqual(catalog["reciter_id"], slug)
                times = assets["ayah_timings.json"]
                self.assertEqual(hashlib.sha256(times).hexdigest(), proof["ayah_asset_sha256"])
                times = json.loads(times)
                self.assertEqual(times["reciter"], slug)
                self.assertEqual(times["recitation"], "r1")
                self.assertEqual([len(data) for data in (times["surahs"], catalog["tracks"], proof["surahs"])],
                                 [114, 114, 114])
                for n, count in enumerate(counts, 1):
                    timeline, track, source = times["surahs"][n-1], catalog["tracks"][n-1], proof["surahs"][n-1]
                    self.assertEqual([timeline["surah"], track["surah_id"], source["surah"]], [n, n, n])
                    self.assertEqual(track["sha256"], source["audio_sha256"])
                    self.assertEqual(track["url"], f"https://cdn.mp3quran.net/audio/{slug}/r1/{n:03}.mp3")
                    windows = defaultdict(list)
                    for ayah, start, end in timeline["ayahs"]:
                        if ayah > 0:
                            windows[ayah].append((start, end))
                    self.assertEqual(list(windows), list(range(1, count["ayahCount"] + 1)))
                    spans = []
                    for directory in ("words", "word_intervals"):
                        path = f"{directory}/{n}.bin"
                        if directory == "word_intervals" and path not in assets:
                            continue
                        binary = assets[path]
                        magic, surah, size = struct.unpack(">4sII", binary[:12])
                        self.assertEqual((magic, surah, len(binary)), (b"ZQW1", n, 12 + size * 12))
                        spans.extend(struct.iter_unpack(">HHII", binary[12:]))
                    spans.sort(key=lambda r: r[2])
                    previous_end = 0
                    for ayah, word, start, end in spans:
                        self.assertGreater(word, 0)
                        self.assertLessEqual(previous_end, start)
                        self.assertTrue(any(lo <= start < end <= hi for lo, hi in windows[ayah]))
                        previous_end = end
                    self.assertEqual(set(windows), {ayah for ayah, _, _, _ in spans})
                    if n == 5:
                        self.assertEqual(track["sha256"], surah_5_digest)
                        self.assertEqual(windows[2][0][0], verse_start)
                        first_word = next(r for r in spans if r[:2] == (2, 1))
                        self.assertEqual(first_word[2:], (verse_start, first_word_end))
                    if n == 92 and slug == "yasser-dosari":
                        repeats = [r for r in timeline["ayahs"] if r[0] in (14, 15)]
                        self.assertEqual(repeats, [[14, 44450, 49103], [15, 49103, 53943],
                                                   [14, 53943, 59023], [15, 59023, 62943]])
                        self.assertEqual([(a, s) for a, w, s, _ in spans if a in (14, 15) and w == 1],
                                         [(a, s) for a, s, _ in repeats])


if __name__ == "__main__":
    unittest.main()
