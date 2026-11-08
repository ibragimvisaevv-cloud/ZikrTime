"""Focused real-app UI evidence for the approved home/Quran design sample.

Disposable debug emulator only. Imports established 0.8.28 helper definitions,
never their previous suites. Quran bytes and calculations are not replaced.
Screenshots/video are actual Android pixels, not regenerated UI mockups.
"""
import hashlib
import os
import traceback
from pathlib import Path

helpers = Path('tools/ci/learning_journeys.py')
exec(compile(helpers.read_text().split('\nif __name__ == "__main__":')[0], str(helpers), 'exec'))

PRAYERS = ('FAJR', 'SUNRISE', 'DHUHR', 'ASR', 'MAGHRIB', 'ISHA')
REPORT = {'profile': 'isolated_debug_emulator', 'production_writes': False,
          'screenshots_are_real_android_pixels': True, 'release_ui_tested': False,
          'scope': ['home', 'Quran index, reader and ayah panel'], 'groups': {}, 'complete': False}


def save_report():
    REPORT['source_sha'] = json.loads((OUT / 'build.json').read_text())['source_sha']
    REPORT['verification_sha'] = os.environ.get('GITHUB_SHA')
    REPORT['run'] = os.environ.get('GITHUB_RUN_ID')
    REPORT['debug_apk_sha256'] = hashlib.sha256(Path('app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
    REPORT['captures'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((OUT / 'screens').glob('*.png'))}
    (OUT / 'report.json').write_text(json.dumps(REPORT, ensure_ascii=False, indent=2) + '\n')


CURRENT_PROFILE = None
_base_capture = capture


def assert_viewport(root):
    expected = CURRENT_PROFILE['pixels']
    area = bounds(next(root.iter('node')))
    assert area == (0, 0, *expected), ('Actual Android viewport differs from requested profile', area, CURRENT_PROFILE)
    return root


def capture(name, root=None):
    from PIL import Image
    root = _base_capture(name, root)
    assert_viewport(root)
    with Image.open(OUT / 'screens' / (name + '.png')) as picture:
        assert picture.size == tuple(CURRENT_PROFILE['pixels']), (name, picture.size, CURRENT_PROFILE)
    return root


def configure(theme, large=False, width_dp=360, height_dp=800):
    global CURRENT_PROFILE
    if large:
        width_dp, height_dp = 320, 640
    pixels = [width_dp * 3, height_dp * 3]
    shell('am', 'force-stop', PKG)
    shell('wm', 'density', DENSITY)
    shell('wm', 'size', f'{pixels[0]}x{pixels[1]}')
    shell('settings', 'put', 'system', 'font_scale', '1.5' if large else '1.0')
    put_pref('appearance', 'string', theme)
    requested = f'{pixels[0]}x{pixels[1]}'
    wm_size, wm_density = shell('wm', 'size'), shell('wm', 'density')
    assert re.findall(r'(?:Physical|Override) size: (\d+x\d+)', wm_size)[-1] == requested, wm_size
    assert int(re.findall(r'(?:Physical|Override) density: (\d+)', wm_density)[-1]) == DENSITY, wm_density
    CURRENT_PROFILE = {'theme': theme, 'width_dp': width_dp, 'height_dp': height_dp,
                       'font_scale': 1.5 if large else 1, 'pixels': pixels}
    REPORT.setdefault('display_profiles', []).append({**CURRENT_PROFILE, 'wm_size': wm_size, 'wm_density': wm_density})


def require_reachable(tag, minimum_dp=32):
    """Use one fresh hierarchy per gesture; require the complete touch target."""
    previous = None
    for attempt in range(16):
        root = ui()
        node = find_control(root, tag)
        raw = bounds(node) if node is not None else None
        if node is not None and visible_bounds(root, node) == raw:
            assert raw[3] - raw[1] >= minimum_dp * DENSITY / 160, (tag, 'Touch target too small', raw)
            return root
        parents = {child: parent for parent in root.iter() for child in parent}
        owner = parents.get(node)
        while owner is not None and owner.get('scrollable') != 'true':
            owner = parents.get(owner)
        if owner is None:
            scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true']
            assert scrolls, (tag, 'No scrollable container')
            owner = max(scrolls, key=lambda n: visible_bounds(root, n)[3] - visible_bounds(root, n)[1])
        left, top, right, bottom = visible_bounds(root, owner)
        assert bottom - top >= 120, (tag, 'Scroll container too small')
        direction = -1 if raw and raw[1] < top else 1
        signature = (raw, tuple((n.get('resource-id'), n.get('bounds')) for n in owner.iter('node') if n.get('resource-id')))
        assert signature != previous, (tag, 'Scroll made no progress')
        previous = signature
        remaining = (top - raw[1] if direction < 0 else raw[3] - bottom) if raw else (bottom - top) * .6
        distance = min(max(108, int(remaining + 36)), int((bottom - top) * .6))
        middle = (top + bottom) // 2
        start = int(top + (bottom - top) * (.25 if direction < 0 else .75))
        end = max(top + 24, min(bottom - 24, start - direction * distance))
        x = left + min(30, (right - left) // 4)
        shell('input', 'swipe', x, start, x, end, 450)
    raise AssertionError('Control not fully reachable: ' + tag)


def tap_fully_visible(tag, root=None, minimum_dp=32):
    root = root if root is not None else require_reachable(tag, minimum_dp)
    node = find_control(root, tag)
    area = bounds(node)
    assert visible_bounds(root, node) == area, (tag, 'Tap target clipped', area)
    assert area[3] - area[1] >= minimum_dp * DENSITY / 160, (tag, 'Tap target too small', area)
    shell('input', 'tap', (area[0] + area[2]) // 2, (area[1] + area[3]) // 2)
    time.sleep(.35)


def open_reader(restart=False):
    open_route('quran', restart=restart)
    observe('quran_index_list', tries=30)
    root = require_reachable('quran_search', minimum_dp=48)
    current = find_control(root, 'quran_search')
    if current.get('text', ''):
        tap_fully_visible('quran_search_clear')
        root = require_reachable('quran_search', minimum_dp=48)
        assert find_control(root, 'quran_search').get('text', '') == '', 'Clear-search did not clear the field'
    tap_fully_visible('quran_search', root, minimum_dp=48)
    # Opening the keyboard changes the LazyColumn viewport. Poll the current
    # tree while it settles; a transient absent field is not a failed focus.
    for attempt in range(6):
        root = ui()
        focused = find_control(root, 'quran_search')
        if focused is not None and focused.get('focused') == 'true':
            break
        if attempt == 3:
            tap_fully_visible('quran_search', minimum_dp=48)
    else:
        raise AssertionError('Search field did not acquire input focus')
    assert focused.get('text', '') == '', 'Search changed before typing'
    prefix = ''
    for digit in str(SURAH):
        shell('input', 'keyevent', 'KEYCODE_MOVE_END')
        shell('input', 'keyevent', 'KEYCODE_' + digit)
        prefix += digit
        entered = find_control(observe('quran_search'), 'quran_search')
        assert entered.get('text', '') == prefix, ('Search input changed', prefix, entered.attrib)
    shell('input', 'keyevent', 'KEYCODE_BACK')
    root = observe('quran_search')
    assert find_control(root, 'quran_search').get('text', '') == str(SURAH), 'Closing keyboard changed search'
    tap_fully_visible('quran_surah_' + str(SURAH), minimum_dp=48)
    observe('quran_audio_reader_entry')


def require_ayah_reachable(tag, toward_top=False):
    """Keep strict full visibility, with gestures exceeding Android touch slop."""
    previous = None
    for attempt in range(24):
        root = ui()
        sheet = find_control(root, 'quran_ayah_sheet')
        assert sheet is not None, (tag, 'Ayah sheet disappeared while locating control')
        node = find_control(sheet, tag)
        raw = bounds(node) if node is not None else None
        if node is not None and visible_bounds(root, node) == raw:
            assert raw[3] - raw[1] >= 32 * DENSITY / 160, (tag, 'Touch target too small', raw)
            return root
        parents = {child: parent for parent in sheet.iter() for child in parent}
        owner = parents.get(node) if node is not None else sheet
        while owner is not None and owner.get('scrollable') != 'true':
            owner = parents.get(owner)
        assert owner is not None, (tag, 'Missing sheet scroll container')
        left, top, right, bottom = visible_bounds(root, owner)
        assert bottom - top >= 120, (tag, 'Sheet viewport too small')
        direction = -1 if toward_top else 1
        remaining = 72 * DENSITY / 160
        if raw is not None:
            if raw[1] < top:
                direction, remaining = -1, top - raw[1]
            elif raw[3] > bottom:
                direction, remaining = 1, raw[3] - bottom
            else:
                raise AssertionError((tag, 'Control clipped horizontally', raw, visible_bounds(root, node)))
        geometry = (raw, tuple((n.get('resource-id'), n.get('bounds')) for n in owner.iter('node') if n.get('resource-id')))
        assert geometry != previous, (tag, 'Sheet scroll made no progress')
        previous = geometry
        # 36dp minimum clears touch slop even for a one-pixel overlap; 72dp
        # maximum and slow drag avoid flings and accidental modal dismissal.
        distance = min(max(36 * DENSITY / 160, remaining + 12 * DENSITY / 160),
                       72 * DENSITY / 160, (bottom - top) // 4)
        x, middle = left + min(30, (right - left) // 4), (top + bottom) // 2
        end = int(middle - direction * distance)
        with (OUT / 'learning-sheet-scroll.jsonl').open('a') as handle:
            handle.write(json.dumps({'tag': tag, 'attempt': attempt, 'target': raw,
                                    'viewport': [left, top, right, bottom], 'swipe': [x, middle, x, end]}) + '\n')
        shell('input', 'swipe', x, middle, x, end, 500)
    raise AssertionError('Control not reachable within ayah sheet: ' + tag)


def check_home_top(theme, width_dp=360, height_dp=800):
    configure(theme, width_dp=width_dp, height_dp=height_dp)
    start('home')
    root = assert_viewport(observe('horizon_time', tries=16))
    # Critically, do not scroll before the all-six-times check.
    nodes = [find_control(root, 'home_prayer_' + name) for name in PRAYERS]
    assert all(n is not None for n in nodes), 'All six times must be present at initial scroll position'
    labels = {'Намаз', 'Знания', 'Магазин', 'Лимиты', 'Настройки'}
    bars = []
    for candidate in root.iter('node'):
        area = bounds(candidate)
        if area[1] > CURRENT_PROFILE['pixels'][1] / 2 and 0 < area[3] - area[1] < CURRENT_PROFILE['pixels'][1] / 3 and labels <= {n.get('text', '') for n in candidate.iter('node')}:
            bars.append(((area[2]-area[0])*(area[3]-area[1]), area))
    assert bars, 'Could not identify the actual bottom navigation bounds'
    nav_bounds = min(bars, key=lambda pair: pair[0])[1]
    geometry = {}
    for prayer, node in zip(PRAYERS, nodes):
        area = bounds(node)
        assert visible_bounds(root, node) == area, (prayer, 'Schedule tile clipped by viewport/navigation', area, visible_bounds(root, node))
        assert area[3] - area[1] >= (64 if height_dp < 720 else 82) * DENSITY / 160, (prayer, 'Tile is smaller than its declared minimum height', area)
        assert area[3] <= nav_bounds[1] - 12, (prayer, 'Tile hidden by bottom navigation', area, nav_bounds)
        for child in node.iter('node'):
            if child.get('text'):
                assert visible_bounds(root, child) == bounds(child), (prayer, 'Text clipped', child.attrib)
        text = ' '.join(n.get('text', '') + ' ' + n.get('content-desc', '') for n in node.iter('node'))
        assert re.search(r'\d{1,2}:\d{2}', text), (prayer, 'Tile does not expose a real formatted time', text)
        geometry[prayer] = area
    sunrise = nodes[1]
    assert not any(n.get('checkable') == 'true' for n in sunrise.iter('node')), 'Sunrise must not be markable as a prayer'
    next_clock = bounds(find_control(root, 'horizon_time'))
    assert next_clock[3] <= min(area[1] for area in geometry.values()), 'Next prayer must precede the schedule'
    suffix = '' if (width_dp, height_dp) == (360, 800) else f'-{width_dp}x{height_dp}dp'
    capture('home-top-' + theme + suffix, root)
    return {'width_dp': width_dp, 'height_dp': height_dp, 'font_scale': 1, 'all_six_visible_without_scroll': True,
            'sunrise_not_checkable': True, 'next_prayer_above_schedule': True,
            'bottom_navigation_bounds_px': nav_bounds, 'tile_bounds_px': geometry}


def below_schedule(theme):
    configure(theme)
    start('home')
    observe('horizon_time')
    require_reachable('home_quran_open', minimum_dp=40)
    capture('home-scroll-reading-' + theme)
    tap_control('home_quran_open')
    observe('quran_index_list', tries=30)
    root = ui()
    first_surah = find_control(root, 'quran_surah_1')
    assert first_surah is not None and visible_bounds(root, first_surah) == bounds(first_surah), 'First surah must be visible on the initial Quran index'
    for tag in ('quran_read_entry', 'quran_audio_entry'):
        require_reachable(tag, minimum_dp=40)
    capture('quran-index-' + theme)
    # Also verify that the separate home audio action reaches the real player.
    start('home')
    tap_control('home_quran_audio')
    observe('audio_screen', tries=20)
    assert not probe()['player']['playWhenReady'], 'Opening the home listening entry started sound'
    return {'read_entry_opens_quran': True, 'listen_entry_opens_audio_without_autoplay': True}


def reader_layout(theme, large=False):
    configure(theme, large)
    open_reader(restart=True)
    require_reachable('quran_audio_reader_entry', minimum_dp=40)
    capture('quran-reader-' + theme + ('-320dp-150' if large else ''))
    tap_control('quran_audio_reader_entry')
    observe('quran_ayah_sheet')
    assert not probe()['player']['playWhenReady'], 'Opening the ayah sheet started sound'
    close_root = require_ayah_reachable('ayah_close', toward_top=True)
    tap_fully_visible('ayah_close', close_root)
    observe('quran_audio_reader_entry')
    tap_fully_visible('quran_audio_reader_entry', minimum_dp=40)
    observe('quran_ayah_sheet')
    require_ayah_reachable('ayah_play_pause', toward_top=True)
    capture('quran-ayah-top-' + theme + ('-320dp-150' if large else ''))
    # The larger text case may scroll naturally; every existing option remains reachable.
    for tag in ('ayah_repeat_10', 'ayah_repeat_0', 'ayah_speed_150', 'ayah_continue',
                'ayah_full_surah', 'quran_action_bookmark', 'quran_note_open'):
        require_ayah_reachable(tag)
    capture('quran-ayah-actions-' + theme + ('-320dp-150' if large else ''))
    shell('input', 'keyevent', 'KEYCODE_BACK')
    observe('quran_audio_reader_entry')
    return {'font_scale': 1.5 if large else 1.0, 'width_dp': 320 if large else 360,
            'reader_and_key_ayah_controls_reachable': True, 'no_autoplay': True,
            'checked_controls': ['ayah_play_pause', 'ayah_repeat_10', 'ayah_repeat_0',
                                 'ayah_speed_150', 'ayah_continue', 'ayah_full_surah',
                                 'quran_action_bookmark', 'quran_note_open', 'ayah_close']}


def audio_and_saved_marks():
    configure('dark')
    open_reader(restart=True)
    ayah_sheet()
    tap_control('ayah_repeat_3')
    tap_control('ayah_speed_75')
    assert not probe()['player']['playWhenReady'], 'Selecting repeats/speed started sound'
    require_ayah_reachable('ayah_play_pause', toward_top=True)
    tap_control('ayah_play_pause')
    player = verse_playing(timeout=60)
    verify_clip(player)
    assert player['repeatCount'] == 3 and abs(player['speed'] - .75) < .01
    verse_paused()
    capture('quran-ayah-real-playback-paused')
    require_ayah_reachable('quran_action_bookmark')
    tap_control('quran_action_bookmark')
    if find_control(ui(), 'quran_ayah_sheet') is None:
        ayah_sheet()
    require_ayah_reachable('quran_note_open')
    tap_control('quran_note_open')
    observe('quran_stop_note')
    tap_control('quran_stop_note')
    shell('input', 'text', 'Emerald%ssample%snote')
    shell('input', 'keyevent', 'KEYCODE_BACK')
    capture('quran-note-editor')
    tap_control('quran_stop_save')
    values = prefs('quran_reading')
    assert '114:1' in values.get('bookmarks', [])
    assert values.get('note_114:1') == 'Emerald sample note'
    open_reader(restart=True)
    values = prefs('quran_reading')
    assert '114:1' in values.get('bookmarks', []) and values.get('note_114:1') == 'Emerald sample note'
    assert not probe()['player']['playWhenReady'], 'Process restoration started playback'
    capture('quran-reader-saved-mark')
    return {'real_play_pause': True, 'verified_source_clip': [114, 1], 'repeat3_speed075': True,
            'bookmark_and_note_survive_restart': True, 'restore_does_not_autoplay': True,
            'full_repetition_background_offline_suite_rerun': False}


def require_home_control(tag, minimum_dp, prayer=False):
    """Compose may report only the tile fragment exposed above floating nav."""
    for attempt in range(12):
        root = ui()
        node = find_control(root, tag)
        raw = bounds(node) if node is not None else None
        text = ' '.join(n.get('text', '') for n in node.iter('node')) if node is not None else ''
        if (node is not None and visible_bounds(root, node) == raw
                and raw[3] - raw[1] >= minimum_dp * DENSITY / 160
                and (not prayer or re.search(r'\d{1,2}:\d{2}', text))):
            assert all(visible_bounds(root, child) == bounds(child)
                       for child in node.iter('node') if child.get('text')), (tag, 'Home text clipped')
            return root
        scroll = find_control(root, 'horizon_scroll')
        assert scroll is not None, (tag, 'Home scroll container missing')
        left, top, right, bottom = visible_bounds(root, scroll)
        # The tested order follows the six tiles from the top. Reveal the next
        # complete tile, never accept the truncated semantics as its real size.
        x = left + 30
        start = int(top + (bottom - top) * .72)
        shell('input', 'swipe', x, start, x, start - 72 * DENSITY // 160, 450)
    raise AssertionError('Complete home control not reachable: ' + tag)


def large_home(theme):
    configure(theme, True)
    start('home')
    observe('horizon_time', tries=16)
    capture('home-top-' + theme + '-320dp-150')
    for prayer in PRAYERS:
        require_home_control('home_prayer_' + prayer, minimum_dp=82, prayer=True)
    capture('home-schedule-' + theme + '-320dp-150')
    require_home_control('home_quran_open', minimum_dp=40)
    return {'width_dp': 320, 'height_dp': 640, 'font_scale': 1.5,
            'all_six_reachable_by_scroll': True, 'read_entry_reachable': True}


def record_navigation():
    configure('dark')
    start('home')
    observe('horizon_time')
    remote = '/sdcard/emerald-sample.mp4'
    shell('rm', '-f', remote)
    record = subprocess.Popen(['adb', 'shell', 'screenrecord', '--bit-rate', '4000000', '--time-limit', '60', remote],
                              stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        time.sleep(.7)
        shell('input', 'swipe', 28, 1850, 28, 1200, 650)
        time.sleep(.4)
        shell('input', 'swipe', 28, 1700, 28, 1200, 650)
        time.sleep(.4)
        tap_control('home_quran_open')
        observe('quran_index_list', tries=20)
        time.sleep(.7)
    finally:
        subprocess.run(['adb', 'shell', 'pkill', '-2', 'screenrecord'], check=False,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
        try:
            record.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            record.terminate();record.communicate(timeout=5)
    target = OUT / 'home-quran-scroll.mp4'
    adb('pull', remote, str(target))
    assert target.stat().st_size > 50_000, 'Empty screen recording'
    return {'path': str(target), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
            'actual_screenrecord': True, 'fps_performance_not_measured': True}


def inherit_verified_results(tasks):
    """Reuse only proved groups on byte-identical APKs and unchanged checks."""
    import ast
    import shutil
    reuse_run = os.environ.get('REUSE_RUN', '')
    if not reuse_run:
        return
    previous_dir = Path('prior-evidence/ci-out')
    previous = json.loads((previous_dir / 'report.json').read_text())
    previous_build = json.loads((previous_dir / 'build.json').read_text())
    current_build = json.loads((OUT / 'build.json').read_text())
    assert previous['run'] == reuse_run, 'Evidence belongs to a different run'
    assert previous['source_sha'] == previous_build['source_sha'] == current_build['source_sha']
    apk_sha = hashlib.sha256(Path('app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
    assert previous['debug_apk_sha256'] == apk_sha
    assert previous_build['apk_sha256'] == current_build['apk_sha256'], 'APK input hashes differ'
    verification = previous['verification_sha']
    assert re.fullmatch(r'[0-9a-f]{40}', verification), 'Invalid evidence verification commit'
    immutable_paths = ['app', 'gradle', 'gradlew', 'gradlew.bat', 'build.gradle.kts',
                       'settings.gradle.kts', 'gradle.properties', 'keystore/release.jks.enc',
                       'tools/ci/learning_journeys.py', 'tools/ci/audio_journeys.py',
                       'tools/ci/guide_journeys.py', 'tools/ci/features_controls.py',
                       'tools/ci/check_screen.py']
    subprocess.run(['git', 'diff', '--exit-code', verification, 'HEAD', '--', *immutable_paths], check=True)
    source_file = 'tools/ci/emerald_sample_smoke.py'
    old_source = subprocess.check_output(['git', 'show', verification + ':' + source_file], text=True)
    old_tree, current_tree = ast.parse(old_source), ast.parse(Path(source_file).read_text())
    old_functions = {n.name: n for n in old_tree.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))}
    current_functions = {n.name: n for n in current_tree.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))}
    # Only the dedicated large-home check changed; both themes must rerun.
    for name, definition in old_functions.items():
        if name != 'large_home':
            assert name in current_functions and ast.dump(definition) == ast.dump(current_functions[name]), ('Inherited check changed', name)
    def constants(tree):
        return [ast.dump(n) for n in tree.body if not isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef, ast.If))]
    assert constants(old_tree) == constants(current_tree), 'Harness imports/constants changed'
    def task_definitions(tree):
        return [ast.dump(n) for n in ast.walk(tree)
                if isinstance(n, (ast.Assign, ast.AugAssign)) and
                any(isinstance(t, ast.Name) and t.id == 'tasks'
                    for t in (n.targets if isinstance(n, ast.Assign) else [n.target]))]
    assert task_definitions(old_tree) == task_definitions(current_tree), 'Task mapping changed'
    expected = {name for name, _ in tasks}
    assert set(previous['groups']) <= expected, 'Unknown previous groups'
    evidence_manifest = {}
    for filename, digest in previous.get('captures', {}).items():
        assert Path(filename).name == filename and filename.endswith('.png')
        source = previous_dir / 'screens' / filename
        assert hashlib.sha256(source.read_bytes()).hexdigest() == digest, ('Evidence image hash differs', filename)
        evidence_manifest[filename] = digest
        # Keep failed snapshots separately, outside the final gallery.
        if not filename.startswith('failure-'):
            shutil.copyfile(source, OUT / 'screens' / filename)
            xml = previous_dir / (Path(filename).stem + '.xml')
            if xml.exists():
                shutil.copyfile(xml, OUT / xml.name)
    previous_video = previous['groups'].get('scroll_recording', {})
    if previous_video.get('status') == 'passed':
        video = previous_dir / 'home-quran-scroll.mp4'
        assert hashlib.sha256(video.read_bytes()).hexdigest() == previous_video['sha256'], 'Video evidence hash differs'
        shutil.copyfile(video, OUT / video.name)
    inherited = []
    for name, result in previous['groups'].items():
        if result.get('status') == 'passed' and name not in {'home_large_dark', 'home_large_light'}:
            REPORT['groups'][name] = {**result, 'inherited_from_run': reuse_run,
                                      'original_verification_sha': verification}
            inherited.append(name)
    # Preserve the signed input provenance and original incomplete report.
    provenance = OUT / 'inherited' / reuse_run
    provenance.mkdir(parents=True, exist_ok=True)
    for filename in ['report.json', 'build.json', 'crashes.txt']:
        source = previous_dir / filename
        if source.exists():
            shutil.copyfile(source, provenance / filename)
    prior_crashes = previous_dir / 'crashes.txt'
    if prior_crashes.exists():
        assert not re.search(r'FATAL EXCEPTION|Fatal signal', prior_crashes.read_text()), 'Prior Android crash recorded'
    REPORT['inherited_evidence'] = {'run': reuse_run, 'verification_sha': verification,
                                   'debug_apk_sha256': apk_sha, 'groups': inherited,
                                   'image_sha256': evidence_manifest,
                                   'method': 'APK hashes, source diff, existing function AST, imports/constants and task mapping',
                                   'prior_crash_log_available': prior_crashes.exists()}
    print('Verified inherited groups:', ', '.join(inherited), flush=True)


if __name__ == '__main__':
    OUT.mkdir(exist_ok=True);(OUT / 'screens').mkdir(exist_ok=True)
    ACTIVE_GROUP = 'setup'
    try:
        shell('svc', 'power', 'stayon', 'true')
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        shell('wm', 'dismiss-keyguard')
        adb('logcat', '-c')
        shell('cmd', 'locale', 'set-app-locales', PKG, '--user', '0', '--locales', 'ru')
        seed('dark', onboarded=True, dismissed=True)
        tasks = []
        for theme in ['dark', 'light']:
            tasks += [('home_top_' + theme, lambda t=theme: check_home_top(t)),
                      ('home_entries_' + theme, lambda t=theme: below_schedule(t)),
                      ('quran_layout_' + theme, lambda t=theme: reader_layout(t))]
        tasks += [('audio_and_saved_marks', audio_and_saved_marks)]
        for theme in ['dark', 'light']:
            tasks += [('home_large_' + theme, lambda t=theme: large_home(t)),
                      ('quran_large_' + theme, lambda t=theme: reader_layout(t, True))]
        tasks += [('home_short_dark', lambda: check_home_top('dark', height_dp=640)),
                  ('home_short_light', lambda: check_home_top('light', height_dp=640)),
                  ('scroll_recording', record_navigation)]
        inherit_verified_results(tasks)
        save_report()
        failures = []
        for ACTIVE_GROUP, action in tasks:
            if ACTIVE_GROUP in REPORT['groups']:
                continue
            print('Emerald sample:', ACTIVE_GROUP, flush=True)
            group_started = time.monotonic()
            try:
                REPORT['groups'][ACTIVE_GROUP] = {'status': 'passed', **action()}
            except Exception:
                failure = traceback.format_exc()
                failures.append(ACTIVE_GROUP)
                REPORT['groups'][ACTIVE_GROUP] = {'status': 'failed', 'failure': failure}
                (OUT / ('failure-' + ACTIVE_GROUP + '.txt')).write_text(failure)
                try:
                    capture('failure-' + ACTIVE_GROUP)
                except Exception:
                    pass
            REPORT['groups'][ACTIVE_GROUP]['elapsed_seconds'] = round(time.monotonic() - group_started, 2)
            save_report()
        crashes = adb('logcat', '-d', '-b', 'crash')
        (OUT / 'crashes.txt').write_text(crashes)
        assert not re.search(r'FATAL EXCEPTION|Fatal signal', crashes), 'Android crash recorded'
        assert not failures, 'Failed focused groups: ' + ', '.join(failures)
        REPORT['complete'] = True;save_report()
    except Exception:
        REPORT['failed_group'] = ACTIVE_GROUP
        REPORT['failure'] = traceback.format_exc();save_report()
        (OUT / 'failure.txt').write_text(ACTIVE_GROUP + '\n' + traceback.format_exc())
        try:
            capture('failure-' + ACTIVE_GROUP)
            (OUT / 'crashes.txt').write_text(adb('logcat', '-d', '-b', 'crash'))
        except Exception:
            pass
        raise
    finally:
        shell('settings', 'put', 'system', 'font_scale', '1.0')
