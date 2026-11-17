#!/usr/bin/env python3
"""Import Qamar's published Russian reading, preserving verse IDs and text boundaries."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import unicodedata
from urllib.request import urlopen

from russian_alignment_55 import word_ranges


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets'
QAMAR_REV = '541c33799c6f54b723518515451bea68b009dd95'
QAMAR_FILE = 'quran-transliteration/src/commonMain/resources/translit_ru.json'
URL = f'https://raw.githubusercontent.com/maqsats/qamar-kmp-libraries/{QAMAR_REV}/{QAMAR_FILE}'
SOURCE_HASH = 'd5e68e3f5a7c740ba45cc0bcb81110cf260ea909d72754cf378dad8c0340a5df'
TANZIL_HASH = 'bf4f57b968d03f4131c070b1e285da9be0e0a108a21c910e872801ca273312c8'
PACKS = 16


def check_hash(data, expected):
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Source SHA-256 mismatch')


def aligned_row(arabic, published, reference):
    # The only Latin letter in the original is ASCII a, visually identical to Cyrillic а.
    text = published.replace('a', 'а')
    if not text.strip() or any(ord(c) > 65535 or not (
            'CYRILLIC' in unicodedata.name(c, '') or unicodedata.category(c).startswith('M')
            or c in " '"
    ) for c in text):
        raise ValueError(f'Unexpected characters in {reference}')
    surah, ayah = reference
    if ayah == 1 and surah != 9:
        # In this edition بسم and الله share one Cyrillic printed word. The four
        # original words have the same spoken character spans in every prelude,
        # independent of the vowel/letter that opens the next numbered verse.
        prelude = re.match(r"(бисми)(ллахир) (рахманир) (рахиим)(?: |$)", text, re.IGNORECASE)
        if prelude is None:
            raise ValueError(f'Unexpected Cyrillic basmala in {reference}')
        ranges = {index: prelude.span(index) for index in range(1, 5)}
        if surah != 1:
            arabic_body = arabic.split(maxsplit=4)[4]
            offset = prelude.end()
            for token, (start, end) in word_ranges(arabic_body, text[offset:]).items():
                ranges[token + 4] = (start + offset, end + offset)
    else:
        ranges = word_ranges(arabic, text)
    triples = []
    end_previous = 0
    for token, (start, end) in sorted(ranges.items()):
        if not (1 <= token <= len(arabic.split()) and end_previous <= start < end <= len(text)):
            raise ValueError(f'Invalid word range in {reference}: {token}, {start}..{end}')
        if not text[start:end].strip():
            raise ValueError(f'Empty range in {reference}')
        triples.extend((token, start, end))
        end_previous = end
    return [text, triples]


def generate(source, destination, check):
    check_hash(source, SOURCE_HASH)
    cyrillic = json.loads(source)
    manifest = json.loads((ASSETS / 'quran/manifest.json').read_bytes())
    counts = [surah['ayahCount'] for surah in manifest['surahs']]
    if len(counts) != 114 or sum(counts) != 6236 or list(map(len, cyrillic)) != counts:
        raise ValueError('Surah and ayah numbering mismatch')
    original = (ASSETS / 'quran/sources/quran-uthmani.txt').read_bytes()
    check_hash(original, TANZIL_HASH)
    arabic = {}
    for line in original.decode('utf-8').splitlines():
        if line and not line.startswith('#'):
            chapter, verse, text = line.split('|', 2)
            arabic[int(chapter), int(verse)] = text
    packs = [{} for _ in range(PACKS)]
    prefix_ru = cyrillic[0][0]
    prefix_forms = (arabic[1, 1], arabic[1, 1].replace("بِسْمِ", "بِّسْمِ", 1))
    mapped_words = verses_with_ranges = 0
    for surah, verses in enumerate(cyrillic, start=1):
        rows = []
        for ayah, text in enumerate(verses, start=1):
            reference = (surah, ayah)
            full_arabic = arabic[reference]
            if reference == (13, 1):
                # Qamar prints «амр». Tanzil spells the four disjoint letters المـر.
                # Restore their individually pronounced names using this edition’s 2:1 and 10:1.
                if not text.startswith("амр ") or cyrillic[1][0] != "алиф лам мим" or not cyrillic[9][0].startswith("алиф лам ра "):
                    raise ValueError("Unexpected opening letters in the source")
                text = cyrillic[1][0] + " ра " + text.removeprefix("амр ")
            if ayah == 1 and surah not in (1, 9):
                # Tanzil includes a prefatory basmala before the first numbered verse in these surahs.
                # Qamar numbers verses separately. Reuse its 1:1 for the existing prelude, once.
                if not any(full_arabic.startswith(prefix + " ") for prefix in prefix_forms):
                    raise ValueError(f'Unexpected Arabic prelude in {reference}')
                if not text.casefold().startswith(prefix_ru + ' '):
                    text = prefix_ru + ' ' + text
            row = aligned_row(full_arabic, text, reference)
            rows.append(row)
            mapped_words += len(row[1]) // 3
            verses_with_ranges += bool(row[1])
        packs[(surah - 1) % PACKS][str(surah)] = rows
    if not check:
        destination.mkdir(parents=True, exist_ok=True)
    for index, pack in enumerate(packs):
        raw = json.dumps(pack, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        buf = io.BytesIO()
        with gzip.GzipFile(fileobj=buf, mode='wb', mtime=0, compresslevel=9) as out:
            out.write(raw)
        # Android Gradle expands source files ending in .gz during asset merging.
        path = destination / f'{index}.qtr'
        if check:
            # Check decompressed data so the zlib implementation is not part of the contract.
            if gzip.decompress(path.read_bytes()) != raw:
                raise ValueError(f'Stale or invalid asset {path}')
        else:
            path.write_bytes(buf.getvalue())
    print(f'114 surahs; 6236 verses; 112 preludes; {mapped_words} mapped tokens in {verses_with_ranges} verses')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, help='Local copy of the pinned Qamar JSON')
    parser.add_argument('--check', action='store_true', help='Verify existing assets without writing')
    args = parser.parse_args()
    if args.source:
        source = args.source.read_bytes()
    else:
        with urlopen(URL, timeout=30) as response:
            source = response.read()
    generate(source, ASSETS / 'quran_transcription/ru', args.check)


if __name__ == '__main__':
    main()
