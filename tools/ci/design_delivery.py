"""Record the signed candidate and verify protected packaged resources."""
import hashlib
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

apk = Path('app/build/outputs/apk/release/app-release.apk')
debug = Path('app/build/outputs/apk/debug/app-debug.apk')
source = Path('app/src/main/assets/quran')
with zipfile.ZipFile(apk) as archive:
    for file in source.rglob('*'):
        if file.is_file():
            assert archive.read('assets/quran/' + file.relative_to(source).as_posix()) == file.read_bytes(), str(file)
tests = [ET.parse(path).getroot() for path in Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert tests and not any(int(test.get('failures', 0)) + int(test.get('errors', 0)) for test in tests)
result = {
    'version': '0.8.23', 'version_code': 35, 'source_sha': os.environ['GITHUB_SHA'],
    'run': int(os.environ['GITHUB_RUN_ID']), 'apk_size': apk.stat().st_size,
    'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'debug_sha256': hashlib.sha256(debug.read_bytes()).hexdigest(),
    'certificate_sha256': '7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf',
    'unit_tests': sum(int(test.get('tests', 0)) for test in tests),
    'unit_skipped': sum(int(test.get('skipped', 0)) for test in tests),
    'quran_assets_unchanged': True,
}
Path('ci-out/release-delivery.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
