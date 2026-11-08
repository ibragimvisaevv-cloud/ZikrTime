"""Provenance for real 0.8.29 APKs; never publishes or changes protected inputs."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
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
    save('rhythm-delivery.json', report)
    print(json.dumps({k: v for k, v in report.items() if k not in ('protected_assets',)}, ensure_ascii=False))


if __name__ == '__main__':
    assert len(sys.argv) == 2 and sys.argv[1] in {'inputs', 'apk'}
    {'inputs': inputs, 'apk': apk}[sys.argv[1]]()
