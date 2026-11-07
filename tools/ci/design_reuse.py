"""Reuse only the exact signed candidate whose build and functional checks passed."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

SOURCE = '8f27ea26ece51932a5dfba45fad3e7a580578134'
artifact = json.loads(Path('ci-out/reused-artifact.json').read_text())
assert artifact['id'] == 13573813708 and artifact['name'] == 'design-verified-inputs'
assert not artifact['expired']
assert artifact['workflow_run']['id'] == 42372494277
assert artifact['workflow_run']['head_sha'] == SOURCE
subprocess.run(['git', 'diff', '--exit-code', SOURCE, 'HEAD', '--', 'app/',
                'tools/i18n/', 'tools/quiz/', 'build.gradle.kts', 'settings.gradle.kts',
                'gradle.properties', 'gradle/', 'gradlew', 'gradlew.bat'], check=True)

verified = Path('verified-inputs')
previous = Path('previous-check')
receipt = json.loads((verified / 'ci-out/release-delivery.json').read_text())
assert receipt['source_sha'] == SOURCE and receipt['run'] == 42372494277
assert receipt['version'] == '0.8.23' and receipt['version_code'] == 35
expected = {
    'release': ('apk_sha256', 'b833d6e059d2c4f90db02a05887982055bb9198b0db6e077b149c0f57bf326bb'),
    'debug': ('debug_sha256', '82fbc84a4b0a552d632393bf4ec5c640c9311b497da0f81ad0c5f478c004d53f'),
}
for variant, (key, digest) in expected.items():
    relative = Path(f'app/build/outputs/apk/{variant}/app-{variant}.apk')
    assert hashlib.sha256((verified / relative).read_bytes()).hexdigest() == digest == receipt[key]
    relative.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(verified / relative, relative)
assert receipt['certificate_sha256'] == '7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf'
assert receipt['unit_tests'] == 152 and receipt['unit_skipped'] == 1
lint = ET.parse(previous / 'app/build/reports/lint-results-debug.xml').getroot()
assert not any(issue.get('severity') in {'Error', 'Fatal'} for issue in lint.findall('issue'))
passed = json.loads((previous / 'ci-out/design-passed.json').read_text())
assert set(passed['groups']) >= {'focus', 'knowledge', 'more', 'reminders', 'camera'}
assert passed['groups']['camera']['preview_error_on_emulator'] is False
shutil.copyfile(previous / 'ci-out/design-passed.json', 'ci-out/design-functional-passed.json')
shutil.copyfile(verified / 'ci-out/release-delivery.json', 'ci-out/release-delivery.json')
shutil.copyfile(verified / 'ci-out/release-signature.txt', 'ci-out/release-signature.txt')
for folder in ('screens', 'miniapp'):
    shutil.copytree(previous / 'ci-out' / folder, Path('ci-out') / folder, dirs_exist_ok=True)
print('Exact APKs, unchanged Android inputs, unit tests, lint and five functional groups verified.')
