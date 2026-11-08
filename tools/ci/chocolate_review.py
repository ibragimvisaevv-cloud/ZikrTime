"""Bounded chocolate-theme review on disposable Android emulators.

One pair of original APKs is shared by all shards. Upgrade alone uses copies
with a disposable common debug certificate; every non-signature ZIP entry is
verified unchanged. No production account, permission grant or purchase is used.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET
import zipfile

BASELINE = os.environ.get('BASELINE', 'be103ebd84d228a36a1c1a0d8596b494a7c97383')
BASE_DEBUG_SHA = '68470e1e03ff68c45e2e5e4041f0a3f7971645a978339748375b921e6b19cee9'
CERT = '7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf'
DEBUG = Path('app/build/outputs/apk/debug/app-debug.apk')
RELEASE = Path('app/build/outputs/apk/release/app-release.apk')
OUT = Path('ci-out')
SHARDS = ('home', 'sections', 'preserve')


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def command(*args):
    return subprocess.check_output(list(map(str, args)), text=True, timeout=120)


def sdk_tool(name):
    return str(max((Path(os.environ['ANDROID_HOME']) / 'build-tools').glob('*/' + name),
                   key=lambda p: tuple(map(int, re.findall(r'\d+', p.parent.name)))))


def write_json(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def source_freeze():
    names = command('git', 'ls-tree', '-r', '--name-only', BASELINE, '--', 'app/src/main').splitlines()
    frozen = [n for n in names if any(part in n for part in ('/assets/', '/data/', '/audio/', '/prayer/', '/widget/', '/font/'))
              or Path(n).name.startswith(('Quran', 'widget_', 'ic_prayer_'))
              or Path(n).name in ('TasbihScreen.kt', 'QiblaTasbih.kt')]
    hashes = {}
    for name in frozen:
        before = subprocess.check_output(['git', 'show', BASELINE + ':' + name])
        assert Path(name).read_bytes() == before, ('Protected source changed', name)
        hashes[name] = hashlib.sha256(before).hexdigest()
    theme = 'app/src/main/java/com/zikrtime/app/ui/theme/Theme.kt'
    before = subprocess.check_output(['git', 'show', BASELINE + ':' + theme], text=True)
    after = Path(theme).read_text()
    for declaration in ('LightColors', 'DarkColors', 'AppTypography', 'AppShapes', 'ArabicStyle'):
        pattern = r'(?m)^(?:private )?val ' + declaration + r' = .*?\n\)'
        old = re.search(pattern, before, re.S)
        new = re.search(pattern, after, re.S)
        assert old and new and old.group() == new.group(), ('Retained theme changed', declaration)
    return hashes


def verify_build():
    OUT.mkdir(exist_ok=True)
    frozen = source_freeze()
    reuse = os.environ.get('REUSE_RUN', '')
    if reuse:
        data = json.loads((OUT / 'build.json').read_text())
        source = data['source_sha']
        assert re.fullmatch('[0-9a-f]{40}', source)
        subprocess.run(['git', 'diff', '--exit-code', source, 'HEAD', '--', 'app', 'gradle', 'gradlew',
                        'gradlew.bat', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'keystore/release.jks.enc'], check=True)
        for name, expected in data['apk_sha256'].items():
            assert sha(name) == expected, name
        assert data['tests_passed'] == 24 and data['tests_failed'] == data['tests_skipped'] == data['lint_errors'] == 0
    else:
        suites = [ET.parse(p).getroot() for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
        total = sum(int(s.get('tests', 0)) for s in suites)
        failed = sum(int(s.get('failures', 0)) + int(s.get('errors', 0)) for s in suites)
        skipped = sum(int(s.get('skipped', 0)) for s in suites)
        assert total == 24 and failed == skipped == 0, (total, failed, skipped)
        lint = ET.parse('app/build/reports/lint-results-debug.xml').getroot()
        errors = sum(i.get('severity') in ('Fatal', 'Error') for i in lint.findall('issue'))
        assert errors == 0
        data = {'source_sha': os.environ['GITHUB_SHA'], 'source_run': os.environ['GITHUB_RUN_ID'],
                'tests_passed': total, 'tests_failed': failed, 'tests_skipped': skipped, 'lint_errors': errors}
    signature = command(sdk_tool('apksigner'), 'verify', '--verbose', '--print-certs', RELEASE)
    assert 'certificate SHA-256 digest: ' + CERT in signature
    for apk in (DEBUG, RELEASE):
        badge = command(sdk_tool('aapt'), 'dump', 'badging', apk)
        identity = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badge)
        assert identity and identity.groups() == ('com.zikrtime.app', '44', '0.8.31'), str(apk)
    dirs = ['app/src/main/assets/' + d for d in ('quran', 'quran_audio', 'quiz')]
    assets = command('git', 'ls-tree', '-r', '--name-only', BASELINE, '--', *dirs).splitlines()
    assert len(assets) == 128
    for apk in (DEBUG, RELEASE):
        with zipfile.ZipFile(apk) as archive:
            for name in assets:
                expected = subprocess.check_output(['git', 'show', BASELINE + ':' + name])
                assert archive.read(name.removeprefix('app/src/main/')) == expected, (str(apk), name)
    data.update({'verification_sha': os.environ['GITHUB_SHA'], 'verification_run': os.environ['GITHUB_RUN_ID'],
                 'reused_from_run': reuse or None, 'baseline': BASELINE, 'version_name': '0.8.31', 'version_code': 44,
                 'certificate_sha256': CERT, 'protected_assets_verified': len(assets), 'frozen_sources': frozen,
                 'apk_sha256': {str(p): sha(p) for p in (DEBUG, RELEASE)}, 'release_bytes': RELEASE.stat().st_size,
                 'store_published': False, 'physical_phone_tested': False})
    write_json(OUT / 'build.json', data)
    shutil.copyfile(RELEASE, OUT / 'ZikrTime-0.8.31-chocolate.apk')
    print(json.dumps({k: v for k, v in data.items() if k != 'frozen_sources'}))


def load_helpers():
    helper = Path('tools/ci/emerald_sample_smoke.py')
    exec(compile(helper.read_text().split("\nif __name__ == '__main__':")[0], str(helper), 'exec'), globals())
    # The imported file contributes definitions only, never its historical suite.
    globals()['save_report'] = save_shard


def save_shard():
    REPORT['captures'] = {str(p.relative_to(OUT)): sha(p) for p in sorted((OUT / 'screens').glob('*')) if p.is_file()}
    write_json(OUT / ('report-' + REPORT['shard'] + '.json'), REPORT)


def fixture(theme='dark'):
    seed(theme, onboarded=True, dismissed=True)
    root = prefs_tree()
    for name, value in {'shop_catalog': Path('app/src/main/assets/shop/catalog.json').read_text(),
                        'shop_catalog_at': str(int(time.time() * 1000) + 86400000)}.items():
        ET.SubElement(root, 'string', name=name).text = value
    for name, value in {'tasbih_count': 37, 'tasbih_target': 100, 'tasbih_phrase': 0}.items():
        ET.SubElement(root, 'int', name=name, value=str(value))
    write_tree(root)
    reading = ET.Element('map')
    ET.SubElement(ET.SubElement(reading, 'set', name='bookmarks'), 'string').text = '114:1'
    ET.SubElement(reading, 'string', name='note_114:1').text = 'Synthetic upgrade fixture'
    ET.SubElement(reading, 'int', name='daily_goal_minutes', value='17')
    write_tree(reading, 'quran_reading')


def home_shard():
    fixture()
    for theme in ('dark', 'light'):
        for height in (800, 640):
            REPORT['groups'][f'home-{theme}-{height}'] = check_home_top(theme, height_dp=height)
            save_shard()
    REPORT['groups']['home-large-text'] = large_home('dark')
    REPORT['groups']['home-reading-actions'] = below_schedule('dark')
    # One representative first screen per supported language, no audio rerun.
    configure('dark')
    languages = ('ru', 'en', 'uz', 'tg', 'kk', 'ky', 'az', 'tr')
    for lang in languages:
        shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_screen', 'home',
              '--es', 'qa_lang', lang, '--ez', 'qa_tilt', 'false', '--ez', 'qa_parallax', 'false')
        root = observe('horizon_time', tries=16)
        for prayer in PRAYERS:
            node = find_control(root, 'home_prayer_' + prayer)
            assert node is not None and bounds(node)[3] <= CURRENT_PROFILE['pixels'][1], (lang, prayer)
        capture('language-' + lang, root)
    REPORT['groups']['languages'] = list(languages)
    REPORT['groups']['navigation-recording'] = record_navigation()


def sections_shard():
    fixture()
    routes = {'knowledge': 'quran_open', 'shop': 'shop_search', 'more': 'settings_guide', 'focus': 'focus_setup',
              'azkar': 'azkar_source', 'prayer_settings': 'prayer_regional_mode', 'onboarding_perms': 'onboarding_step_3'}
    for theme in ('dark', 'light'):
        configure(theme)
        for route, tag in routes.items():
            start(route)
            root = observe(tag, tries=12)
            assert any(n.get('package') == PKG for n in root.iter('node')), route
            capture('section-' + route + '-' + theme, root)
            if route.startswith('onboarding'):
                put_pref('onboarded', 'boolean', 'true')
        REPORT['groups']['sections-' + theme] = list(routes)
        save_shard()
    start('focus')
    tap_fully_visible('focus_permissions_open')
    observe('focus_permissions_done')
    capture('section-permissions-light')
    REPORT['groups']['permission-explanation-opened'] = True


def apk_payload(path):
    with zipfile.ZipFile(path) as archive:
        # Only JAR signing metadata may change. Android manifest and all other
        # META-INF entries remain part of the byte-equality proof.
        return {n: hashlib.sha256(archive.read(n)).hexdigest() for n in archive.namelist()
                if not re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', n, re.I)}


def resigned_copies():
    baseline = Path('baseline-inputs/app/build/outputs/apk/debug/app-debug.apk')
    assert sha(baseline) == BASE_DEBUG_SHA, 'Wrong baseline APK'
    temporary = Path('ci-test-signing')
    temporary.mkdir(exist_ok=True)
    keystore = temporary / 'temporary.jks'
    command('keytool', '-genkeypair', '-keystore', keystore, '-alias', 'ci', '-storepass', 'temporary-ci-only',
            '-keypass', 'temporary-ci-only', '-dname', 'CN=Disposable CI upgrade test', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '2')
    result = []
    try:
        for label, source in (('baseline', baseline), ('candidate', DEBUG)):
            target = temporary / (label + '.apk')
            command(sdk_tool('apksigner'), 'sign', '--ks', keystore, '--ks-key-alias', 'ci',
                    '--ks-pass', 'pass:temporary-ci-only', '--key-pass', 'pass:temporary-ci-only',
                    '--out', target, source)
            assert apk_payload(source) == apk_payload(target), ('Re-signing changed an APK payload', label)
            certs = command(sdk_tool('apksigner'), 'verify', '--print-certs', target)
            cert = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', certs).group(1)
            result.append((target, {'source_sha256': sha(source), 'derived_sha256': sha(target),
                                    'certificate_sha256': cert, 'identical_payload_entries': len(apk_payload(source))}))
        assert result[0][1]['certificate_sha256'] == result[1][1]['certificate_sha256']
    finally:
        keystore.unlink(missing_ok=True)
    REPORT['upgrade_signature'] = {label: item[1] for label, item in zip(('baseline', 'candidate'), result)}
    REPORT['upgrade_limitation'] = 'Equivalent debug APK payloads under a disposable common test signature; not a production ARM release installation.'
    return [item[0] for item in result]


def frozen_screens(prefix):
    names = []
    for theme in ('dark', 'light'):
        fixture(theme)
        configure(theme)
        start('quran')
        root = observe('quran_surah_1', tries=30)
        capture(prefix + '-quran-' + theme, root)
        names.append('quran-' + theme)
        tap_fully_visible('quran_surah_1', root, minimum_dp=40)
        root = observe('quran_audio_reader_entry', tries=16)
        capture(prefix + '-reader-' + theme, root)
        names.append('reader-' + theme)
        tap_fully_visible('quran_audio_reader_entry', root, minimum_dp=40)
        root = observe('quran_ayah_sheet', tries=12)
        capture(prefix + '-ayah-sheet-' + theme, root)
        names.append('ayah-sheet-' + theme)
        start('tasbih')
        root = observe('tasbih_count_value', tries=16)
        capture(prefix + '-tasbih-' + theme, root)
        names.append('tasbih-' + theme)
        tap_fully_visible('tasbih_phrase_open', root, minimum_dp=32)
        root = observe('tasbih_phrase_0')
        capture(prefix + '-tasbih-phrase-' + theme, root)
        names.append('tasbih-phrase-' + theme)
    return names


def preserve_shard():
    from PIL import Image, ImageChops, ImageStat
    old, new = resigned_copies()
    for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        shell('settings', 'put', 'global', setting, '0')
    adb('install', '-r', old)
    names = frozen_screens('baseline')
    fixture()
    keys = ('city', 'city_id', 'appearance', 'limit', 'packages', 'tasbih_count', 'tasbih_target', 'tasbih_phrase')
    before = {k: prefs().get(k) for k in keys}
    reading_before = prefs('quran_reading')
    # No uninstall or data clear occurs between the old and new install.
    adb('install', '-r', new)
    assert {k: prefs().get(k) for k in keys} == before
    assert prefs('quran_reading') == reading_before
    start('home')
    observe('horizon_time')
    assert {k: prefs().get(k) for k in keys} == before
    assert all(prefs('quran_reading').get(k) == v for k, v in reading_before.items())
    REPORT['groups']['upgrade'] = {'preferences_preserved_before_and_after_launch': list(keys),
                                   'quran_bookmark_note_and_goal_preserved': True, 'no_uninstall_between_versions': True}
    save_shard()
    assert frozen_screens('candidate') == names
    comparisons = {}
    for name in names:
        with Image.open(OUT / 'screens' / ('baseline-' + name + '.png')) as a, Image.open(OUT / 'screens' / ('candidate-' + name + '.png')) as b:
            assert a.size == b.size == (1080, 2400)
            # Exclude only Android status/navigation bars and their live clock.
            box = (0, 84, 1080, 2328)
            delta = ImageChops.difference(a.convert('RGB').crop(box), b.convert('RGB').crop(box))
            maximum = ImageChops.lighter(ImageChops.lighter(*delta.split()[:2]), delta.split()[2])
            changed = sum(count for value, count in enumerate(maximum.histogram()) if value > 8)
            fraction = changed / (delta.width * delta.height)
            mean = sum(ImageStat.Stat(delta).mean) / 3
            comparisons[name] = {'crop': box, 'changed_pixel_fraction_over_8': fraction, 'mean_channel_difference': mean}
            if fraction > .005 or mean > .5:
                delta.save(OUT / 'screens' / ('difference-' + name + '.png'))
            assert fraction <= .005 and mean <= .5, ('Frozen screen changed', name, comparisons[name])
    REPORT['groups']['frozen-screen-comparisons'] = comparisons


def run_ui(shard):
    assert shard in SHARDS
    load_helpers()
    global REPORT
    (OUT / 'screens').mkdir(parents=True, exist_ok=True)
    build = json.loads((OUT / 'build.json').read_text())
    assert sha(DEBUG) == build['apk_sha256'][str(DEBUG)]
    REPORT = {'shard': shard, 'source_sha': build['source_sha'], 'verification_sha': os.environ.get('GITHUB_SHA'),
              'run': os.environ.get('GITHUB_RUN_ID'), 'debug_apk_sha256': sha(DEBUG), 'groups': {}, 'complete': False,
              'profile': 'disposable_debug_emulator', 'production_writes': False,
              'screenshots_are_real_android_pixels': True, 'release_ui_tested': False}
    started = time.monotonic()
    try:
        shell('settings', 'put', 'global', 'stay_on_while_plugged_in', '3')
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        shell('wm', 'dismiss-keyguard')
        if shard != 'preserve':
            adb('install', '-r', DEBUG)
        {'home': home_shard, 'sections': sections_shard, 'preserve': preserve_shard}[shard]()
        REPORT['complete'] = True
    except BaseException:
        REPORT['failure'] = traceback.format_exc()
        (OUT / ('failure-' + shard + '.txt')).write_text(REPORT['failure'])
        try:
            (OUT / ('logcat-' + shard + '.txt')).write_text(adb('logcat', '-d', '-t', '1200'))
        except Exception:
            pass
        raise
    finally:
        REPORT['elapsed_seconds'] = round(time.monotonic() - started, 1)
        save_shard()


def aggregate():
    # upload-artifact preserves the common ancestor when evidence includes both
    # ci-out and app reports. Locate the unique manifest regardless of nesting.
    manifests = list(OUT.rglob('build.json'))
    assert len(manifests) == 1, manifests
    build = json.loads(manifests[0].read_text())
    expected = build['apk_sha256'][str(DEBUG)]
    report = {'build': build, 'shards': {}, 'complete': False, 'release_ui_tested': False,
              'physical_phone_tested': False, 'store_published': False}
    failures = []
    for shard in SHARDS:
        matches = list(Path('review-shards').rglob('report-' + shard + '.json'))
        if len(matches) != 1:
            failures.append('Missing report for ' + shard)
            continue
        path = matches[0]
        data = json.loads(path.read_text())
        assert data['source_sha'] == build['source_sha'] and data['debug_apk_sha256'] == expected
        assert data['verification_sha'] == os.environ['GITHUB_SHA']
        for relative, digest in data.get('captures', {}).items():
            assert sha(path.parent / relative) == digest, relative
        report['shards'][shard] = data
        shutil.copytree(path.parent, OUT / ('review-' + shard), dirs_exist_ok=True)
        if not data['complete']:
            failures.append(shard + ': ' + data.get('failure', 'Incomplete review'))
    report['failures'] = failures
    report['complete'] = not failures and set(report['shards']) == set(SHARDS)
    write_json(OUT / 'report.json', report)
    assert report['complete'], failures
    print('All three UI shards passed against the same verified APK.')


if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'verify-build':
        verify_build()
    elif mode == 'ui':
        run_ui(sys.argv[2])
    elif mode == 'aggregate':
        aggregate()
    elif mode == 'source-check':
        print(json.dumps({'frozen_sources': len(source_freeze())}))
    else:
        raise SystemExit('Expected verify-build, ui <shard>, aggregate or source-check')
