"""Provenance for real 0.8.29 APKs; never publishes or changes protected inputs."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

OUT = Path('ci-out')
BASE = 'c451791056a717846adad432f5b9d5884729d27e'
CERT = '7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf'
PROTECTED = ('app/src/main/assets/quran', 'app/src/main/assets/quran_audio',
             'app/src/main/assets/quiz', 'app/src/main/java/com/zikrtime/app/data',
             'app/src/main/java/com/zikrtime/app/prayer', 'app/src/main/java/com/zikrtime/app/audio',
             'app/src/main/java/com/zikrtime/app/quiz')


def run(*args):
    return subprocess.check_output(args, text=True)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(name, content):
    OUT.mkdir(exist_ok=True)
    (OUT / name).write_text(json.dumps(content, ensure_ascii=False, indent=2) + '\n')


def provenance():
    return {'source_sha': run('git', 'rev-parse', 'HEAD').strip(),
            'baseline_sha': BASE, 'run_id': os.environ.get('GITHUB_RUN_ID'),
            'mode': os.environ.get('RHYTHM_MODE'), 'version': '0.8.29', 'version_code': 41,
            'physical_device_tested': False, 'store_published': False}


def inputs():
    subprocess.run(['git', 'diff', '--exit-code', BASE, 'HEAD', '--', *PROTECTED], check=True)
    report = provenance()
    report['protected_unchanged'] = list(PROTECTED)
    report['protected_assets'] = {
        str(path): digest(path)
        for directory in PROTECTED[:3]
        for path in sorted(Path(directory).rglob('*')) if path.is_file()
    }
    report['screen_map_present'] = Path('docs/RHYTHM_PAUSE_SCREEN_MAP.md').exists()
    save('rhythm-inputs.json', report)


def tool(name):
    candidates = list((Path(os.environ['ANDROID_HOME']) / 'build-tools').glob('*/' + name))
    assert candidates, name + ' is missing'
    return str(max(candidates, key=lambda p: tuple(map(int, re.findall(r'\d+', p.parent.name)))))


def apk():
    source = json.loads((OUT / 'rhythm-inputs.json').read_text())
    report = provenance()
    debug = Path('app/build/outputs/apk/debug/app-debug.apk')
    report['debug_sha256'] = digest(debug)
    report['debug_bytes'] = debug.stat().st_size
    badging = run(tool('aapt'), 'dump', 'badging', str(debug))
    assert "name='com.zikrtime.app'" in badging
    assert "versionCode='41'" in badging and "versionName='0.8.29'" in badging
    with zipfile.ZipFile(debug) as archive:
        for path, expected in source['protected_assets'].items():
            assert hashlib.sha256(archive.read(path.removeprefix('app/src/main/'))).hexdigest() == expected, path
    report['debug_protected_assets_verified'] = len(source['protected_assets'])
    if os.environ.get('RHYTHM_MODE') == 'final':
        release = Path('app/build/outputs/apk/release/app-release.apk')
        signature = run(tool('apksigner'), 'verify', '--verbose', '--print-certs', str(release))
        assert 'certificate SHA-256 digest: ' + CERT in signature
        (OUT / 'release-signature.txt').write_text(signature)
        badging = run(tool('aapt'), 'dump', 'badging', str(release))
        assert "name='com.zikrtime.app'" in badging
        assert "versionCode='41'" in badging and "versionName='0.8.29'" in badging
        report.update(release_sha256=digest(release), release_bytes=release.stat().st_size,
                      certificate_sha256=CERT, baseline_debug_sha256=digest(OUT / 'baseline/ZikrTime-0.8.28-debug.apk'))
        with zipfile.ZipFile(release) as archive:
            for path, expected in source['protected_assets'].items():
                assert hashlib.sha256(archive.read(path.removeprefix('app/src/main/'))).hexdigest() == expected, path
        report['release_protected_assets_verified'] = len(source['protected_assets'])
        tests = sorted(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
        assert tests, 'No targeted test evidence'
        roots = [ET.parse(path).getroot() for path in tests]
        assert not any(int(t.get('failures', 0)) + int(t.get('errors', 0)) for t in roots)
        lint = Path('app/build/reports/lint-results-debug.xml')
        issues = ET.parse(lint).getroot().findall('issue')
        assert not any(i.get('severity') in {'Fatal', 'Error'} for i in issues)
        report['targeted_tests'] = sum(int(t.get('tests', 0)) for t in roots)
        report['skipped_tests'] = sum(int(t.get('skipped', 0)) for t in roots)
        report['inheritable_check_files'] = {str(path): digest(path) for path in [*tests, lint]}
    save('rhythm-delivery.json', report)
    print(json.dumps({k: v for k, v in report.items() if k not in ('protected_assets',)}, ensure_ascii=False))


def reuse():
    report = json.loads((OUT / 'rhythm-delivery.json').read_text())
    requested = json.loads((OUT / 'rhythm-requested-reuse-run.json').read_text())
    source = report['source_sha']
    assert re.fullmatch(r'[0-9a-f]{40}', source)
    assert str(requested['id']) == os.environ['RHYTHM_REUSE_RUN']
    assert requested['head_branch'] == 'codex/rhythm-pause-0829'
    assert requested['path'] == '.github/workflows/rhythm-pause.yml'
    assert re.fullmatch(r'\d+', str(report['run_id']))
    original = json.loads(run('gh', 'api', 'repos/' + os.environ['GITHUB_REPOSITORY']
                              + '/actions/runs/' + str(report['run_id'])))
    assert original['head_sha'] == source
    assert original['path'] == '.github/workflows/rhythm-pause.yml'
    assert original['head_branch'] == 'codex/rhythm-pause-0829'
    save('rhythm-original-build-run.json', original)
    assert report['mode'] == os.environ['RHYTHM_MODE']
    assert report['version'] == '0.8.29' and report['version_code'] == 41
    subprocess.run(['git', 'diff', '--exit-code', source, 'HEAD', '--', 'app', 'gradle',
                    'gradlew', 'gradlew.bat', 'build.gradle.kts', 'settings.gradle.kts',
                    'gradle.properties', 'keystore/release.jks.enc'], check=True)
    assert digest('app/build/outputs/apk/debug/app-debug.apk') == report['debug_sha256']
    if report['mode'] == 'final':
        assert report['certificate_sha256'] == CERT
        assert digest('app/build/outputs/apk/release/app-release.apk') == report['release_sha256']
        assert digest(OUT / 'baseline/ZikrTime-0.8.28-debug.apk') == report['baseline_debug_sha256']
        for path, expected in report['inheritable_check_files'].items():
            assert digest(path) == expected
    save('rhythm-reuse.json', {'apk_source_sha': source, 'apk_build_run': report['run_id'],
                             'downloaded_from_run': requested['id'],
                             'verification_sha': os.environ['GITHUB_SHA'],
                             'verification_run': os.environ['GITHUB_RUN_ID'],
                             'unchanged_android_inputs': True, 'apk_hashes_verified': True,
                             'prior_targeted_tests_and_lint_inherited': report['mode'] == 'final'})


if __name__ == '__main__':
    assert len(sys.argv) == 2 and sys.argv[1] in {'inputs', 'apk', 'reuse'}
    {'inputs': inputs, 'apk': apk, 'reuse': reuse}[sys.argv[1]]()
