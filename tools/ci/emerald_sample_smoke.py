"""Focused real-app UI evidence for the approved home/Quran design sample.

Disposable debug emulator only. Imports established 0.8.28 helper definitions,
never their previous suites. Quran bytes and calculations are not replaced.
Screenshots/video are actual Android pixels, not regenerated UI mockups.
"""
import hashlib
import os
import signal
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


def configure(theme, large=False):
    shell('am', 'force-stop', PKG)
    shell('wm', 'density', '480')
    shell('wm', 'size', '960x1920' if large else '1080x2400')
    shell('settings', 'put', 'system', 'font_scale', '1.5' if large else '1.0')
    put_pref('appearance', 'string', theme)


def check_home_top(theme):
    configure(theme)
    start('home')
    root = observe('horizon_time', tries=16)
    # Critically, do not scroll before the all-six-times check.
    nodes = [find_control(root, 'home_prayer_' + name) for name in PRAYERS]
    assert all(n is not None for n in nodes), 'All six times must be present at initial scroll position'
    labels = {'Намаз', 'Знания', 'Магазин', 'Лимиты', 'Настройки'}
    bars = []
    for candidate in root.iter('node'):
        area = bounds(candidate)
        if area[1] > 1200 and 0 < area[3] - area[1] < 800 and labels <= {n.get('text', '') for n in candidate.iter('node')}:
            bars.append(((area[2]-area[0])*(area[3]-area[1]), area))
    assert bars, 'Could not identify the actual bottom navigation bounds'
    nav_bounds = min(bars, key=lambda pair: pair[0])[1]
    geometry = {}
    for prayer, node in zip(PRAYERS, nodes):
        area = bounds(node)
        assert visible_bounds(root, node) == area, (prayer, 'Schedule tile clipped by viewport/navigation', area, visible_bounds(root, node))
        assert area[3] - area[1] >= 82 * DENSITY / 160, (prayer, 'Tile is smaller than its declared minimum height', area)
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
    capture('home-top-' + theme, root)
    return {'width_dp': 360, 'height_dp': 800, 'font_scale': 1, 'all_six_visible_without_scroll': True,
            'sunrise_not_checkable': True, 'next_prayer_above_schedule': True,
            'bottom_navigation_bounds_px': nav_bounds, 'tile_bounds_px': geometry}


def below_schedule(theme):
    require_reachable('home_quran_open', minimum_dp=40)
    capture('home-scroll-reading-' + theme)
    tap_control('home_quran_open')
    observe('quran_index_list', tries=30)
    root = ui()
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
    require_ayah_reachable('ayah_play_pause', toward_top=True)
    capture('quran-ayah-top-' + theme + ('-320dp-150' if large else ''))
    # The larger text case may scroll naturally; every existing option remains reachable.
    for tag in ('ayah_repeat_10', 'ayah_repeat_0', 'ayah_speed_150', 'ayah_continue',
                'ayah_full_surah', 'quran_action_bookmark', 'quran_note_open'):
        require_ayah_reachable(tag)
    capture('quran-ayah-actions-' + theme + ('-320dp-150' if large else ''))
    require_ayah_reachable('ayah_close', toward_top=True)
    tap_control('ayah_close')
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


def large_home(theme):
    configure(theme, True)
    start('home')
    observe('horizon_time', tries=16)
    capture('home-top-' + theme + '-320dp-150')
    for prayer in PRAYERS:
        require_reachable('home_prayer_' + prayer, minimum_dp=48)
    capture('home-schedule-' + theme + '-320dp-150')
    require_reachable('home_quran_open', minimum_dp=40)
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
        tasks += [('scroll_recording', record_navigation)]
        failures = []
        for ACTIVE_GROUP, action in tasks:
            print('Emerald sample:', ACTIVE_GROUP, flush=True)
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
