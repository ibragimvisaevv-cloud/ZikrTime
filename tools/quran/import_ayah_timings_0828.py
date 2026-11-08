#!/usr/bin/env python3
"""Snapshot publisher timings, retaining source bytes and checking every ayah."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'tools/quran/source-evidence/0828'
BASE = 'https://api-staging.mp3quran.net/v4/recitations/mishary-alafasy/r1'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def fetch(n):
    url = f'{BASE}/surahs/{n}/ayah-timings'
    path = EVIDENCE / 'ayah-timings' / f'{n:03}.json'
    if path.exists():
        raw = path.read_bytes()
    else:
        with urllib.request.urlopen(url, timeout=45) as response:
            raw = response.read()
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
    return n, json.loads(raw), {'surah': n, 'url': url, 'sha256': sha(raw), 'bytes': len(raw)}

def main():
    manifest = json.loads((ROOT / 'app/src/main/assets/quran/manifest.json').read_text())
    counts = {s['id']: s['ayahCount'] for s in manifest['surahs']}
    audio = json.loads((ROOT / 'app/src/main/assets/quran_audio/alafasy.json').read_text())
    files = {t['surah_id']: t for t in audio['tracks']}
    catalogue = json.loads((EVIDENCE / 'alafasy-v4-surahs.json').read_text())
    catalogue = {t['surah']: t for t in catalogue['data']}
    assert len(counts) == len(files) == len(catalogue) == 114
    out = []
    evidence = []
    coalesced_occurrences = []
    versions = set()
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        for n, response, source in sorted(executor.map(fetch, range(1, 115))):
            data = response['data']
            assert data['surah'] == n and data['complete'] is True, n
            source_rows = data['ayahs']
            rows = []
            for row in source_rows:
                if rows and rows[-1][0] == row[0]:
                    assert row[1] >= rows[-1][2], (n, row)
                    coalesced_occurrences.append({'surah': n, 'ayah': row[0],
                                                 'first_start_ms': rows[-1][1],
                                                 'last_end_ms': row[2]})
                    rows[-1][2] = row[2]
                else:
                    rows.append(list(row))
            numbered = [row for row in rows if row[0] > 0]
            assert [row[0] for row in numbered] == list(range(1, counts[n] + 1)), n
            previous_end = -1
            for ayah, start, end in rows:
                assert all(isinstance(x, int) for x in (ayah, start, end)), (n, ayah)
                assert ayah >= -1 and 0 <= start < end, (n, ayah)
                assert start >= previous_end, (n, ayah, previous_end, start)
                assert end <= catalogue[n]['duration_ms'] + 1000, (n, ayah, end)
                previous_end = end
            assert files[n]['url'] == f'https://cdn.mp3quran.net/audio/mishary-alafasy/r1/{n:03}.mp3'
            assert files[n]['size_bytes'] == catalogue[n]['files']['original']['bytes'], n
            out.append({'surah': n, 'complete': True, 'ayahs': rows})
            evidence.append(source)
            versions.add(response.get('meta', {}).get('catalogue_version'))
    result = {'schema_version': 1, 'source': 'https://docs-staging.mp3quran.net/',
              'reciter': 'mishary-alafasy', 'recitation': 'r1',
              'source_api_template': f'{BASE}/surahs/{{surah}}/ayah-timings',
              'catalogue_versions': sorted(versions),
              'adjacent_occurrences_coalesced': coalesced_occurrences, 'surahs': out}
    raw = (json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n').encode()
    target = ROOT / 'app/src/main/assets/quran_audio/alafasy_ayah_timings.json'
    target.write_bytes(raw)
    report = {'asset': str(target.relative_to(ROOT)), 'sha256': sha(raw), 'bytes': len(raw),
              'surahs': len(out), 'numbered_ayahs': sum(counts.values()),
              'source_responses': evidence}
    (EVIDENCE / 'timing-provenance.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'source_responses'}), flush=True)

if __name__ == '__main__':
    main()
