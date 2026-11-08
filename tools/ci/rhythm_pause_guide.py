"""Capture / validate / explicitly import 30 real Rhythm and Pause guide frames.

Capture reuses the established XML-bound hotspot and WebP pipeline, with a
fresh UI-configured profile instead of its old synthetic preference seed.
Import requires the caller's exact source SHA/run and checks every PNG/WebP.
This command never uploads, commits, pushes or writes app assets by itself
unless the explicit `import` subcommand is selected.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import traceback

from PIL import Image

TOPICS = {'welcome', 'prayer', 'journal', 'quran', 'limits', 'permissions',
          'knowledge', 'places', 'tools', 'shop'}


def load_definitions(path, name):
    namespace = {'__name__': name}
    exec(compile(Path(path).read_text(), path, 'exec'), namespace)
    return namespace


def capture():
    current = load_definitions('tools/ci/rhythm_pause.py', 'rhythm_guide_setup')
    old = load_definitions('tools/ci/guide_capture_0828.py', 'rhythm_guide_capture')
    for path in (Path('ci-out'), Path('ci-out/screens'), old['ASSETS']):
        path.mkdir(parents=True, exist_ok=True)
    current['adb']('wait-for-device')
    current['adb']('install', '-r', current['APK'])
    current['display']()
    current['first_run']()
    current['theme']('dark')
    delivery = json.loads(Path('ci-out/rhythm-delivery.json').read_text())
    assert os.environ['GUIDE_APK_SOURCE_SHA'] == delivery['source_sha']
    try:
        old['capture_common']()
        old['capture_quran']()
        assert set(old['MANIFEST']) == TOPICS
        assert len(old['EVIDENCE']) == 30
        assert all(len(steps) == 3 and all(steps) for steps in old['MANIFEST'].values())
        provenance_file = Path('ci-out/guide-capture-provenance.json')
        provenance = json.loads(provenance_file.read_text())
        provenance.update(design='rhythm-pause-0829', setup='real_onboarding_and_theme_controls',
                          qaDemo=False, fabricatedProgress=False,
                          captureRun=os.environ['GITHUB_RUN_ID'], captureSha=os.environ['GITHUB_SHA'],
                          publicPlaceReference='shop-bot/places/berkat_yurt_verified_2026-11-07.json',
                          publicPlaceSnapshotUsedOnlyForRealGuideExample=True)
        provenance_file.write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n')
        validate(Path('ci-out'), os.environ['GUIDE_APK_SOURCE_SHA'], os.environ['GITHUB_RUN_ID'])
    except Exception:
        Path('ci-out/rhythm-guide-failure.txt').write_text(traceback.format_exc())
        try:
            old['capture']('rhythm-guide-failure')
        except Exception:
            pass
        raise


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(folder, expected_source, expected_run):
    provenance = json.loads((folder / 'guide-capture-provenance.json').read_text())
    assert provenance['sourceSha'] == expected_source
    assert str(provenance['captureRun']) == str(expected_run)
    assert provenance['imagesAreActualScreenshots'] is True
    assert provenance['productionWrites'] is False
    assert provenance['design'] == 'rhythm-pause-0829'
    assert provenance['setup'] == 'real_onboarding_and_theme_controls'
    assert provenance['qaDemo'] is False and provenance['fabricatedProgress'] is False
    assert provenance['locale'] == 'ru' and provenance['theme'] == 'dark'
    manifest = json.loads((folder / 'guide-assets/ru/manifest.json').read_text())
    assert set(manifest) == TOPICS
    captured = {(item['topic'], item['step']): item for item in provenance['captures']}
    assert len(captured) == len(provenance['captures']) == 30
    size = 0
    for topic, steps in manifest.items():
        assert len(steps) == 3
        for number, spec in enumerate(steps, 1):
            item = captured[topic, number]
            assert spec['image'] == f'{topic}-{number}.webp'
            webp = folder / 'guide-assets/ru' / spec['image']
            png = folder / 'screens' / f'guide-source-{topic}-{number}.png'
            assert sha(webp) == item['webpSha256'] and sha(png) == item['pngSha256']
            with Image.open(webp) as image:
                assert image.format == 'WEBP' and image.size == (spec['width'], spec['height'])
            width, height = item['originalSize']
            left, top, right, bottom = item['bounds']
            assert 0 <= left < right <= width and 0 <= top < bottom <= height
            assert spec['target'] == [round(left/width, 6), round(top/height, 6),
                                      round(right/width, 6), round(bottom/height, 6)]
            size += webp.stat().st_size
    assert size < 4*1024*1024
    return manifest, provenance, size


def import_captures(folder, source, run):
    manifest, provenance, size = validate(folder, source, run)
    # Captured application code must still be identical. Guide illustration
    # assets are the only allowed Android difference during their import.
    inputs = ['app', 'gradle', 'gradlew', 'gradlew.bat', 'build.gradle.kts',
              'settings.gradle.kts', 'gradle.properties', 'keystore/release.jks.enc']
    # Compare the working tree as well as committed changes to the captured APK.
    changed = subprocess.check_output(['git', 'diff', '--name-only', source, '--', *inputs], text=True).splitlines()
    changed += subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', *inputs], text=True).splitlines()
    assert all(path.startswith('app/src/main/assets/guide/') for path in changed), changed
    destination = Path('app/src/main/assets/guide/ru')
    evidence = Path('tools/ci/rhythm-guide-evidence')
    destination.mkdir(parents=True, exist_ok=True)
    evidence.mkdir(parents=True, exist_ok=True)
    for steps in manifest.values():
        for step in steps:
            shutil.copyfile(folder / 'guide-assets/ru' / step['image'], destination / step['image'])
    (destination / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    (evidence / f'capture-{run}.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n')
    (evidence / 'import.json').write_text(json.dumps({'capture_run': str(run), 'source_sha': source,
        'source_run': provenance['sourceRun'],
        'debug_sha256': provenance['debugApkSha256'], 'images': 30, 'webp_bytes': size,
        'source_inputs_unchanged_except_guide_assets': True}, indent=2) + '\n')
    print(json.dumps({'imported': 30, 'webp_bytes': size, 'source_sha': source}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('capture')
    for command in ('validate', 'import'):
        selected = sub.add_parser(command)
        selected.add_argument('folder', type=Path)
        selected.add_argument('--source', required=True)
        selected.add_argument('--run', required=True)
    args = parser.parse_args()
    if args.command == 'capture':
        capture()
    elif args.command == 'validate':
        validate(args.folder, args.source, args.run)
    else:
        import_captures(args.folder, args.source, args.run)
