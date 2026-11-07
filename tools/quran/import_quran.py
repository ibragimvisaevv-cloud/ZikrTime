"""Fetch primary Quran sources without rewriting Arabic codepoints.

Use --download for network fetches, then --build to validate and pin the data.
No OCR or machine translation is accepted.
"""
import argparse
import concurrent.futures
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets/quran'
SOURCES = ASSETS / 'sources'
EVIDENCE = Path(__file__).resolve().parent / 'source-evidence'
AUDIT = ROOT / '.work/quran-audit'
ARABIC_URL = 'https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt-2&agree=true'
METADATA_URL = 'https://tanzil.net/res/text/metadata/quran-data.xml'
TRANSLATION_INDEX_URL = 'https://quranenc.com/en/home'
TRANSLATION_URL = 'https://quranenc.com/api/v1/translation/sura/russian_rwwad/{surah}'
ARABIC_SHA256 = 'bf4f57b968d03f4131c070b1e285da9be0e0a108a21c910e872801ca273312c8'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def download(name, url, folder=SOURCES):
    path = folder / name
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        return path
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'ZikrTime-source-verification/0.8.14'})
            with urllib.request.urlopen(request, timeout=45) as response:
                data = response.read()
            if not data:
                raise ValueError('Empty source: ' + url)
            partial = path.with_suffix(path.suffix + '.partial')
            partial.write_bytes(data)
            partial.replace(path)
            return path
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)

def fetch():
    download('quran-uthmani.txt', ARABIC_URL)
    download('quran-data.xml', METADATA_URL)
    if not (EVIDENCE / 'quranenc-russian-rwwad.html').exists():
        page_path = download('quranenc-index.html', TRANSLATION_INDEX_URL, AUDIT)
        save_edition_evidence(page_path.read_bytes())
    download('russian_rwwad/1.json', TRANSLATION_URL.format(surah=1))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda s: download(f'russian_rwwad/{s}.json', TRANSLATION_URL.format(surah=s)), range(2, 115)))
    print('Downloaded unmodified Arabic, chapter metadata and all 114 Russian translation responses.')

def save_edition_evidence(raw):
    page = raw.decode('utf-8')
    title = page.index('Russian Translation - Rowwad Translation Center')
    start = page.rfind('<div class="tab_card ', 0, title)
    end = page.index('</small></span>', title) + len('</small></span>')
    assert start >= 0
    excerpt = page[start:end].encode('utf-8')
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / 'quranenc-russian-rwwad.html').write_bytes(excerpt)
    provenance = dict(sourceUrl=TRANSLATION_INDEX_URL, retrievedAt=datetime.now(timezone.utc).isoformat(),
                      originalSha256=sha(raw), excerptSha256=sha(excerpt),
                      note='Verbatim edition-card excerpt; full response retained locally under .work/quran-audit.')
    (EVIDENCE / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')

def records(raw):
    result = {}
    for line in raw.decode('utf-8').splitlines():
        if not line or line.startswith('#'):
            continue
        parts = line.split('|', 2)
        if len(parts) != 3:
            raise ValueError('Malformed Arabic line')
        key = (int(parts[0]), int(parts[1]))
        if key in result or not parts[2]:
            raise ValueError('Duplicate/empty Arabic verse')
        result[key] = parts[2]  # Deliberately no trim, normalization, or replacement.
    return result

def build():
    arabic_bytes = (SOURCES / 'quran-uthmani.txt').read_bytes()
    assert sha(arabic_bytes) == ARABIC_SHA256, 'Review a changed Arabic edition before importing it'
    arabic = records(arabic_bytes)
    xml = ET.fromstring((SOURCES / 'quran-data.xml').read_bytes())
    names = json.loads((Path(__file__).resolve().parent / 'surah_names_ru.json').read_bytes())
    assert len(names) == 114 and all(isinstance(n, str) and n for n in names)
    surahs = [dict(id=int(s.attrib['index']), nameArabic=s.attrib['name'],
                   nameLatin=s.attrib['tname'], nameRu=names[int(s.attrib['index']) - 1],
                   ayahCount=int(s.attrib['ayas'])) for s in xml.findall('./suras/sura')]
    assert len(surahs) == 114 and sum(s['ayahCount'] for s in surahs) == 6236
    expected = {(s['id'], a) for s in surahs for a in range(1, s['ayahCount'] + 1)}
    assert set(arabic) == expected
    translations = {}
    for s in surahs:
        response = json.loads((SOURCES / f"russian_rwwad/{s['id']}.json").read_bytes())
        rows = response.get('result', response) if isinstance(response, dict) else response
        for row in rows:
            key = (int(row['sura']), int(row['aya']))
            assert key[0] == s['id'] and key not in translations
            assert isinstance(row['translation'], str) and row['translation'].strip()
            assert isinstance(row['footnotes'], str)
            assert not re.search(r'<[^>]+>|&[a-z#0-9]+;', row['translation'] + row['footnotes']), 'Review changed presentation markup'
            translations[key] = row
    assert set(translations) == expected
    excerpt_bytes = (EVIDENCE / 'quranenc-russian-rwwad.html').read_bytes()
    provenance = json.loads((EVIDENCE / 'provenance.json').read_bytes())
    assert sha(excerpt_bytes) == provenance['excerptSha256'] and provenance['sourceUrl'] == TRANSLATION_INDEX_URL
    source_page = excerpt_bytes.decode('utf-8')
    # Match the version immediately before this edition, never another language's version.
    edition = re.search(r'(\d{2}/\d{2}/\d{4}) - V(\d+\.\d+\.\d+)(?:(?!<h2).)*<h2[^>]*>\s*Russian Translation - Rowwad Translation Center\s*</h2>\s*<span[^>]*><small>(.*?)</small>', source_page, re.S)
    assert edition, 'Publisher edition version and attribution required'
    source_paths = [SOURCES / 'quran-uthmani.txt', SOURCES / 'quran-data.xml']
    source_paths += [SOURCES / f'russian_rwwad/{s}.json' for s in range(1, 115)]
    manifest = {
        'schemaVersion': 1,
        'arabicVersion': 'Tanzil Uthmani 1.1',
        'translationVersion': edition.group(2),
        'translationUpdated': edition.group(1),
        'translationSource': 'QuranEnc / Rowwad Translation Center',
        'translationAttribution': edition.group(3),
        'translationUrl': 'https://quranenc.com/en/browse/russian_rwwad/1',
        'translationMetadataUrl': TRANSLATION_INDEX_URL,
        'translationMetadataSha256': provenance['originalSha256'],
        'surahs': surahs,
        'files': {str(p.relative_to(ASSETS)).replace('\\', '/'): sha(p.read_bytes())
                  for p in sorted(source_paths)},
    }
    data = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / 'manifest.json').write_bytes(data)
    kotlin = ROOT / 'app/src/main/java/com/zikrtime/app/data/QuranContent.kt'
    code = kotlin.read_text(encoding='utf-8')
    code = re.sub(r'const val MANIFEST_SHA256 = "[^"]*"', f'const val MANIFEST_SHA256 = "{sha(data)}"', code)
    kotlin.write_text(code, encoding='utf-8', newline='\n')
    print(f'Validated 114 surahs / 6236 verses in every layer. Manifest SHA256: {sha(data)}')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--download', action='store_true')
    parser.add_argument('--build', action='store_true')
    args = parser.parse_args()
    if args.download:
        fetch()
    if args.build:
        build()
