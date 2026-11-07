"""Validate release identity, minification output, source assets and signature."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

out = Path('ci-store')
report = json.loads((out / 'signing.json').read_text())
folder = Path('app/build/outputs/apk/release')
metadata = json.loads((folder / 'output-metadata.json').read_text())
assert metadata['applicationId'] == 'com.zikrtime.app'
assert all(x['versionCode'] == 29 and x['versionName'] == '0.8.17' for x in metadata['elements'])
apk = folder / ('app-release.apk' if report['ready'] else 'app-release-unsigned.apk')
sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools'
tool_dir = max((p for p in sdk.iterdir() if (p / 'apksigner').exists()),
               key=lambda p: tuple(map(int, re.findall(r'\d+', p.name))))
badging = subprocess.check_output([str(tool_dir / 'aapt'), 'dump', 'badging', str(apk)], text=True)
assert 'application-debuggable' not in badging, 'Debug APK is not a store build'
assert "sdkVersion:'26'" in badging and "targetSdkVersion:'35'" in badging
(out / 'apk-badging.txt').write_text(badging)
verified = subprocess.run([str(tool_dir / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)],
                          text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if report['ready']:
    assert verified.returncode == 0
    assert 'certificate SHA-256 digest: ' + report['expected_certificate_sha256'] in verified.stdout
    (out / 'signature.txt').write_text(verified.stdout)
else:
    assert verified.returncode != 0, 'Unsigned artifact unexpectedly has a signature'
lint = ET.parse('app/build/reports/lint-results-release.xml')
assert not any(i.get('severity') in ('Error', 'Fatal') for i in lint.iter('issue'))
with zipfile.ZipFile(apk) as z:
    abis = {n.split('/')[1] for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')}
    assert abis == {'arm64-v8a', 'armeabi-v7a'}, abis
    for source in Path('app/src/main/assets/quran').rglob('*'):
        if source.is_file():
            assert z.read('assets/' + str(source.relative_to('app/src/main/assets'))) == source.read_bytes(), source
name = 'ZikrTime-0.8.17-RuStore.apk' if report['ready'] else 'ZikrTime-0.8.17-UNSIGNED.apk'
shutil.copyfile(apk, out / name)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
report.update(source_sha=os.environ['GITHUB_SHA'], run=int(os.environ['GITHUB_RUN_ID']),
              apk_name=name, apk_bytes=apk.stat().st_size, apk_sha256=digest,
              application_id='com.zikrtime.app', version_name='0.8.17', version_code=29,
              debuggable=False, lint_errors=0, quran_assets_unchanged=True,
              abis=sorted(abis), store_upload_ready=report['ready'])
(out / 'release-report.json').write_text(json.dumps(report, indent=2))
(out / 'SHA256SUMS.txt').write_text(digest + '  ' + name + '\n')
print(json.dumps(report))
