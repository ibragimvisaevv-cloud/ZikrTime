"""Real Android UI evidence for Rhythm and Pause, on disposable emulator data.

No qa_demo, invented statistics, injected quiz answers or production writes.
Routes use existing debug navigation only; actions are performed through actual
visible controls. Upgrade notes and counters are labelled test evidence and are
cleared before the separate presentation screenshots are captured.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

from PIL import Image

SUPPORT = {'__name__': 'rhythm_support'}
SOURCE = Path('tools/ci/learning_journeys.py')
exec(compile(SOURCE.read_text(), str(SOURCE), 'exec'), SUPPORT)
OUT = Path('ci-out')
PKG = 'com.zikrtime.app'
APK = Path('app/build/outputs/apk/debug/app-debug.apk')
BASE_APK = OUT / 'baseline/ZikrTime-0.8.28-debug.apk'
adb, shell, ui, prefs = (SUPPORT[n] for n in ('adb', 'shell', 'ui', 'prefs'))
find = SUPPORT['find_control']
bounds, visible_bounds = SUPPORT['bounds'], SUPPORT['visible_bounds']
PROFILE = 'disposable_android15_debug_emulator'
REPORT = {'profile': PROFILE, 'production_writes': False, 'qa_demo': False,
          'fixture_values_are_user_data': False, 'groups': {}, 'screens': [],
          'full_66_row_action_audit_complete': False,
          'limits': ['Physical sensors and Samsung background alarms are not verified here.',
                     'External messages, calls, reviews, payments and production mutations are not sent.',
                     'A screen capture verifies rendering, not every action on that screen.']}
ACTIVE = 'setup'
LOCALE = 'ru'
DISPLAY_REQUESTED = None


def persist():
    (OUT / 'rhythm-ui.json').write_text(json.dumps(REPORT, ensure_ascii=False, indent=2) + '\n')
    mapping = Path('docs/RHYTHM_PAUSE_SCREEN_MAP.md')
    if mapping.exists():
        rows = []
        for identifier, title in re.findall(r'^\| (\d{2}) · ([^|]+)\|', mapping.read_text(), re.M):
            related = {name: result['status'] for name, result in REPORT['groups'].items()
                       if identifier in result.get('map_ids', [])}
            rows.append({'id': identifier, 'screen': title.strip(), 'groups': related,
                         'status': 'partial_evidence' if related else 'not_exercised',
                         'all_listed_actions_verified': False})
        (OUT / 'rhythm-map-coverage.json').write_text(json.dumps({
            'source_sha': REPORT.get('source_sha'), 'rows': rows,
            'interpretation': 'Partial evidence refers only to assertions and rendering in linked groups. '
                              'No entire row is automatically certified by a screenshot.',
        }, ensure_ascii=False, indent=2) + '\n')


def observe(tag, attribute='resource-id', attempts=10):
    for _ in range(attempts):
        root = ui()
        if find(root, tag, attribute) is not None:
            return root
        time.sleep(.35)
    raise AssertionError('Expected UI not reached: ' + tag)


def locate(tag, attribute='resource-id'):
    """Bounded local scrolling, with no invisible controls or stale coordinates."""
    direction, previous = 1, None
    for _ in range(9):
        root = ui()
        node = find(root, tag, attribute)
        if node is not None:
            l, t, r, b = visible_bounds(root, node)
            if r - l >= 24 and b - t >= 30:
                return root, node, (l, t, r, b)
        owners = [(visible_bounds(root, n), n) for n in root.iter('node') if n.get('scrollable') == 'true']
        owners = [(area, n) for area, n in owners if area[3] - area[1] > 150]
        if not owners:
            time.sleep(.3)
            continue
        area, owner = max(owners, key=lambda pair: pair[0][3] - pair[0][1])
        signature = ET.tostring(owner)
        if signature == previous:
            direction *= -1
        previous = signature
        l, t, r, b = area
        a, z = int(t + (b-t)*.78), int(t + (b-t)*.28)
        if direction < 0:
            a, z = z, a
        shell('input', 'swipe', l+16, a, l+16, z, 300)
    raise AssertionError('Visible control not found: ' + tag)


def tap(tag, attribute='resource-id'):
    root, node, (l, t, r, b) = locate(tag, attribute)
    assert node.get('enabled', 'true') == 'true', 'Disabled action: ' + tag
    (OUT / 'rhythm-last-action.xml').write_text(ET.tostring(root, encoding='unicode'))
    with (OUT / 'rhythm-actions.jsonl').open('a') as stream:
        stream.write(json.dumps({'group': ACTIVE, 'identifier': tag, 'attribute': attribute,
                                 'point': [(l+r)//2, (t+b)//2]}) + '\n')
    shell('input', 'tap', (l+r)//2, (t+b)//2)
    time.sleep(.4)


def back():
    shell('input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(.4)


def long_press(tag):
    root, node, (l, t, r, b) = locate(tag)
    assert node.get('enabled', 'true') == 'true', 'Disabled action: ' + tag
    assert node.get('long-clickable') == 'true', 'Long press is not exposed: ' + tag
    with (OUT / 'rhythm-actions.jsonl').open('a') as stream:
        stream.write(json.dumps({'group': ACTIVE, 'identifier': tag, 'gesture': 'long_press',
                                 'point': [(l+r)//2, (t+b)//2], 'duration_ms': 650}) + '\n')
    shell('input', 'swipe', (l+r)//2, (t+b)//2, (l+r)//2, (t+b)//2, 650)
    time.sleep(.4)


def route(name):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity',
          '--es', 'qa_screen', name, '--es', 'qa_lang', LOCALE,
          '--ez', 'qa_tilt', 'false', '--ez', 'qa_parallax', 'false')
    root = ui()
    assert any(n.get('package') == PKG for n in root.iter('node')), ('App screen missing', name)
    return root


def shot(name, presentation=False):
    root = ui()
    SUPPORT['capture'](name, root)
    file = OUT / 'screens' / (name + '.png')
    geometry = capture_geometry(name, root)
    REPORT['screens'].append({'name': name, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest(),
                             'png': 'screens/' + name + '.png', 'xml': name + '.xml',
                             'geometry': 'display/' + name + '.json', 'effective_display': geometry, 'group': ACTIVE, 'presentation': presentation,
                             'real_app_render': True, 'locale': LOCALE})
    persist()
    return root


def effective_display():
    size = shell('wm', 'size').strip()
    density = shell('wm', 'density').strip()
    sizes = re.findall(r'(Physical|Override) size:\s*(\d+)x(\d+)', size)
    densities = re.findall(r'(Physical|Override) density:\s*(\d+)', density)
    assert sizes and densities, ('Unrecognized Android display readback', size, density)
    return {'wm_size_raw': size, 'wm_density_raw': density,
            'effective_wm_pixels': list(map(int, sizes[-1][1:])),
            'effective_density_dpi': int(densities[-1][1]),
            'font_scale_setting': float(shell('settings', 'get', 'system', 'font_scale').strip()),
            'am_get_config': shell('am', 'get-config').strip()}


def assert_requested_display(state):
    if DISPLAY_REQUESTED is None:
        return
    assert state['effective_wm_pixels'] == DISPLAY_REQUESTED['pixels'], ('wm size did not persist', state)
    assert state['effective_density_dpi'] == DISPLAY_REQUESTED['density_dpi'], ('wm density did not persist', state)
    assert abs(state['font_scale_setting'] - DISPLAY_REQUESTED['font_scale']) < .001, ('font_scale did not persist', state)


def capture_geometry(name, root):
    # Read actual Android configuration alongside untouched screencap bytes.
    folder = OUT / 'display'
    folder.mkdir(exist_ok=True)
    state = effective_display()
    state['requested'] = DISPLAY_REQUESTED
    with Image.open(OUT / 'screens' / (name + '.png')) as raster:
        state['png_pixels'] = list(raster.size)
    state['xml_root_bounds'] = list(bounds(next(root.iter('node'))))
    activity = shell('dumpsys', 'activity', 'top')
    lines = [line.strip() for line in activity.splitlines()
             if 'mCurrentConfig' in line or line.lstrip().startswith(('ACTIVITY ', 'TASK '))]
    state['activity_config_lines'] = lines
    # dumpsys includes the launcher beneath our activity. Its configuration
    # cannot establish that the application received a font/density change.
    config, app_activity, current_activity = '', '', ''
    for line in lines:
        if line.startswith('ACTIVITY '):
            current_activity = line
        elif 'mCurrentConfig' in line and current_activity.startswith('ACTIVITY ' + PKG + '/'):
            config, app_activity = line, current_activity
    state['configuration_activity'] = app_activity
    scale = re.search(r'\{([0-9]+(?:\.[0-9]+)?)\s', config)
    dpi = re.search(r'\b(\d+)dpi\b', config)
    width = re.search(r'\bw(\d+)dp\b', config)
    state['app_font_scale'] = float(scale.group(1)) if scale else None
    state['app_density_dpi'] = int(dpi.group(1)) if dpi else None
    state['app_width_dp'] = int(width.group(1)) if width else None
    state['raster_matches_wm_size'] = state['png_pixels'] == state['effective_wm_pixels']
    # Save readbacks before assertions. Physical captures may differ from a
    # logical wm override; record both instead of assuming equal dimensions.
    (folder / (name + '.json')).write_text(json.dumps(state, ensure_ascii=False, indent=2) + '\n')
    assert_requested_display(state)
    assert scale and dpi and width, ('Foreground configuration missing', name, lines)
    assert abs(state['app_font_scale'] - state['font_scale_setting']) < .001, ('App fontScale differs from system setting', state)
    assert state['app_density_dpi'] == state['effective_density_dpi'], ('App density differs from wm', state)
    if DISPLAY_REQUESTED and DISPLAY_REQUESTED['pixels'][0] < DISPLAY_REQUESTED['pixels'][1]:
        expected_dp = DISPLAY_REQUESTED['pixels'][0] * 160 / state['app_density_dpi']
        assert abs(state['app_width_dp'] - expected_dp) <= 1, ('App did not receive requested portrait width', state)
    l, t, r, b = state['xml_root_bounds']
    assert 0 <= l < r <= state['png_pixels'][0] and 0 <= t < b <= state['png_pixels'][1], ('XML and PNG coordinate spaces differ', state)
    return state


def display(width=1080, height=1920, font=1.0):
    global DISPLAY_REQUESTED
    DISPLAY_REQUESTED = {'pixels': [width, height], 'density_dpi': 480, 'font_scale': float(font)}
    results = [shell('wm', 'size', f'{width}x{height}'), shell('wm', 'density', '480'),
               shell('settings', 'put', 'system', 'font_scale', str(font))]
    # Configure before install/launch where possible and let SystemUI settle.
    previous = None
    for attempt in range(4):
        time.sleep(1)
        state = effective_display()
        state['requested'] = DISPLAY_REQUESTED
        state['set_command_outputs'] = results
        OUT.mkdir(exist_ok=True)
        with (OUT / 'rhythm-display.jsonl').open('a') as stream:
            stream.write(json.dumps(state, ensure_ascii=False) + '\n')
        assert_requested_display(state)
        stable = (state['effective_wm_pixels'], state['effective_density_dpi'], state['font_scale_setting'])
        if stable == previous:
            return state
        previous = stable
    raise AssertionError('Display configuration did not settle')


def theme(value):
    route('more')
    tap('appearance_' + value)
    assert prefs().get('appearance') == value, 'Appearance did not persist'
    time.sleep(.4)


def enter_text(tag, text, attribute='resource-id'):
    tap(tag, attribute)
    current = find(ui(), tag, attribute)
    assert current is not None
    shell('input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in current.get('text', ''):
        shell('input', 'keyevent', 'KEYCODE_DEL')
    if text.isdecimal():
        for digit in text:
            shell('input', 'keyevent', 'KEYCODE_' + digit)
    else:
        shell('input', 'text', text.replace(' ', '%s'))
    node = find(ui(), tag, attribute)
    assert node is not None and node.get('text') == text, (tag, 'Input differs', node.attrib if node is not None else None)


def first_run(capture_steps=False):
    shell('pm', 'clear', PKG)
    shell('cmd', 'uimode', 'night', 'yes')
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_lang', 'ru')
    observe('onboarding_step_0')
    for step in range(5):
        observe('onboarding_step_' + str(step))
        if capture_steps:
            shot('first-launch-' + str(step), presentation=True)
        if step == 2:
            tap('Москва', 'text')
        else:
            tap('onboarding_next')
    observe('guide_screen')
    if capture_steps:
        shot('first-launch-guide', presentation=True)
    tap('guide_skip')
    observe('home_prayer_journal')
    assert prefs().get('onboarded') == 'true'
    assert prefs().get('city_id') == 'moscow'
    assert prefs().get('guide_dismissed_v1') == 'true'
    return {'map_ids': ['59', '60'], 'steps': 5, 'city_selected_through_ui': 'moscow',
            'permission_prompts_skipped': True, 'guide_skipped_through_ui': True}


def reader(surah=1):
    route('quran')
    observe('quran_index_list', attempts=20)
    if surah != 1:
        enter_text('quran_search', str(surah))
        back()
    tap('quran_surah_' + str(surah))
    observe('quran_audio_reader_entry')
    return ui()


def presentation(palette):
    display()
    theme(palette)
    route('home')
    observe('home_change_city')
    shot('home-' + palette, presentation=True)
    reader()
    shot('reader-' + palette, presentation=True)
    route('more')
    observe('appearance_' + palette)
    shot('settings-' + palette, presentation=True)
    return {'map_ids': ['02', '03', '06', '07', '21'], 'palette': palette,
            'scope': 'Actual rendered home, reader and settings; captures do not assert all actions.'}


def transition_recording():
    route('home')
    remote = '/sdcard/rhythm-transitions.mp4'
    process = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '60', '--bit-rate', '4000000', remote],
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        time.sleep(1)
        tap('Знания', 'text')
        tap('quran_open')
        observe('quran_index_list', attempts=20)
        tap('quran_surah_1')
        tap('quran_audio_reader_entry')
        observe('quran_ayah_sheet')
        tap('ayah_close')
        tap('quran_surahs')
        observe('quran_index_list')
        back()
        tap('Настройки', 'text')
        observe('settings_guide')
    finally:
        pids = subprocess.run(['adb', 'shell', 'pidof', 'screenrecord'],
                              capture_output=True, text=True).stdout.strip().split() if process.poll() is None else []
        if pids:
            shell('kill', '-INT', *pids)
        try:
            stdout, stderr = process.communicate(timeout=12)
        except subprocess.TimeoutExpired:
            process.terminate()
            stdout, stderr = process.communicate(timeout=5)
        (OUT / 'rhythm-video-log.txt').write_bytes(stdout + b'\n' + stderr)
        adb('pull', remote, OUT / 'rhythm-transitions.mp4')
    assert (OUT / 'rhythm-transitions.mp4').stat().st_size > 10000
    return {'map_ids': ['01', '19', '21', '30'], 'actual_screenrecord': True,
            'maximum_seconds': 60, 'frame_rate_performance_measured': False}


def preferences_snapshot():
    shell('am', 'force-stop', PKG)
    result = {}
    names = shell('run-as', PKG, 'ls', 'shared_prefs').split()
    for filename in names:
        if filename.endswith('.xml'):
            result[filename] = prefs(filename[:-4])
    return result


def upgrade():
    adb('uninstall', PKG) if 'package:' in shell('pm', 'path', PKG) else None
    adb('install', '-r', BASE_APK)
    first_run()
    theme('light')
    route('tasbih')
    tap('tasbih_target_99')
    for _ in range(3):
        tap('tasbih_counter')
    assert prefs().get('tasbih_count') == '3'
    reader(114)
    tap('quran_audio_reader_entry')
    tap('quran_action_bookmark')
    if find(ui(), 'quran_ayah_sheet') is None:
        tap('quran_audio_reader_entry')
    tap('quran_note_open')
    enter_text('quran_stop_note', 'Upgrade test note')
    back()
    tap('quran_stop_save')
    assert prefs('quran_reading').get('note_114:1') == 'Upgrade test note'
    assert '114:1' in prefs('quran_reading').get('bookmarks', [])
    route('knowledge')
    tap('learning_level_2')
    tap('learning_level_confirm')
    route('focus')
    tap('focus_settings_open')
    root, slider, (l, t, r, b) = locate('android.widget.SeekBar', 'class')
    assert r > l and b > t
    shell('input', 'tap', int(l + (r-l)*.36), (t+b)//2)
    time.sleep(.5)
    assert 'limit' in prefs(), 'UI limit change did not persist'
    route('prayer_journal')
    tap('journal_previous')
    tap('journal_FAJR')
    tap('journal_MADE_UP')
    route('home')
    before = preferences_snapshot()
    (OUT / 'upgrade-before.json').write_text(json.dumps(before, ensure_ascii=False, indent=2))
    adb('install', '-r', APK)
    route('home')
    observe('home_prayer_journal')
    assert find(ui(), 'onboarding_step_0') is None, 'Update incorrectly restarted onboarding'
    after = preferences_snapshot()
    ignored = {'places_at', 'places_json', 'reviews_at', 'reviews_json'}
    changes = {}
    for name, values in before.items():
        for key, value in values.items():
            if name == 'zikrtime.xml' and key in ignored:
                continue
            if after.get(name, {}).get(key) != value:
                changes[name + ':' + key] = [value, after.get(name, {}).get(key)]
    (OUT / 'upgrade-after.json').write_text(json.dumps(after, ensure_ascii=False, indent=2))
    assert not changes, ('Saved values changed on update', changes)
    reader(114)
    tap('quran_audio_reader_entry')
    tap('quran_note_open')
    assert find(ui(), 'quran_stop_note').get('text') == 'Upgrade test note'
    shot('test-upgrade-note-preserved')
    return {'map_ids': ['07', '09', '14', '22', '23', '34', '40', '42'],
            'baseline_debug_sha256': hashlib.sha256(BASE_APK.read_bytes()).hexdigest(),
            'new_debug_sha256': hashlib.sha256(APK.read_bytes()).hexdigest(),
            'same_debug_key_install_r_succeeded': True, 'all_preexisting_preferences_preserved': True,
            'volatile_catalog_keys_excluded': sorted(ignored),
            'values_created_using_ui': ['city', 'appearance', 'tasbih', 'target', 'bookmark', 'note', 'knowledge_level', 'focus_limit', 'prayer_mark'],
            'screens_are_test_evidence': True}


def reader_actions():
    reader(114)
    tap('quran_mode_read')
    observe('quran_whole_surah')
    shot('quran-whole-surah-dark', presentation=True)
    tap('quran_mode_translation')
    observe('quran_translation_1')
    tap('quran_fullscreen')
    observe('quran_exit_fullscreen')
    shot('quran-fullscreen-translation-dark', presentation=True)
    tap('quran_exit_fullscreen')
    tap('quran_audio_reader_entry')
    for tag in ('ayah_play_pause', 'ayah_repeat_3', 'ayah_speed_75', 'ayah_continue', 'quran_action_bookmark', 'quran_note_open', 'quran_copy_ayah'):
        locate(tag)
    tap('ayah_repeat_3')
    assert find(ui(), 'ayah_repeat_3').get('selected') == 'true', 'Repeat selection not applied'
    tap('ayah_speed_75')
    assert find(ui(), 'ayah_speed_75').get('selected') == 'true', 'Speed selection not applied'
    shot('quran-ayah-controls-dark', presentation=True)
    tap('quran_note_open')
    enter_text('quran_stop_note', 'UI state test')
    shot('test-note-keyboard')
    back()
    tap('quran_note_close')
    observe('quran_note_discard')
    tap('quran_note_keep')
    assert find(ui(), 'quran_stop_note').get('text') == 'UI state test'
    tap('quran_stop_save')
    assert prefs('quran_reading').get('note_114:1') == 'UI state test'
    tap('quran_audio_reader_entry')
    tap('quran_note_open')
    tap('quran_note_delete')
    tap('quran_note_delete_confirm')
    assert 'note_114:1' not in prefs('quran_reading')
    tap('quran_tools')
    tap('quran_jump_open')
    enter_text('quran_ayah_input', '99')
    back()
    tap('quran_go')
    observe('quran_jump_error')
    enter_text('quran_ayah_input', '2')
    back()
    tap('quran_go')
    root = observe('quran_translation_2')
    assert prefs('quran_reading').get('last') == '114:2', 'Jump did not persist the exact ayah'
    translation = find(root, 'quran_translation_2').get('text', '')
    assert translation.strip(), 'Expected existing translated verse'
    long_press('quran_translation_2')
    observe('quran_ayah_sheet')
    tap('quran_copy_ayah')
    assert '114:2' in find(observe('quran_copy_location'), 'quran_copy_location').get('text', '')
    for tag in ('quran_copy_arabic', 'quran_copy_translation', 'quran_copy_both'):
        locate(tag)
    tap('quran_copy_translation')
    observe('quran_audio_reader_entry')
    tap('quran_audio_reader_entry')
    tap('quran_note_open')
    assert '114:2' in find(observe('quran_note_location'), 'quran_note_location').get('text', '')
    # Verify actual clipboard content through the app's editor, then discard it.
    # No generated religious text, clipboard injection, or persisted test note.
    tap('quran_stop_note')
    shell('input', 'keyevent', 'KEYCODE_PASTE')
    assert find(ui(), 'quran_stop_note').get('text', '') == translation, 'Copied translation differs from the visible ayah'
    shot('test-long-press-copy-paste-exact-ayah')
    back()
    tap('quran_note_close')
    tap('quran_note_discard')
    assert 'note_114:2' not in prefs('quran_reading'), 'Discarded clipboard verification was saved'
    route('quran')
    tap('quran_topics')
    observe('quran_topics_count')
    shot('quran-topics-dark', presentation=True)
    route('quran')
    tap('quran_statistics_open')
    observe('quran_statistics')
    tap('quran_stats_period_30')
    shot('quran-statistics-dark', presentation=True)
    tap('quran_goal_open')
    tap('quran_goal_preset_15')
    tap('quran_goal_save')
    assert prefs('quran_reading').get('daily_goal_minutes') == '15'
    return {'map_ids': ['19', '20', '21', '22', '23', '25', '27', '30'],
            'verified': ['surah_modes', 'fullscreen', 'audio_controls_reachable', 'repeats_speed_selection',
                         'note_keyboard', 'discard_keep', 'save_delete_note', 'invalid_jump_rejected',
                         'jump_exact_ayah_persisted', 'translation_long_press_opens_actions',
                         'all_copy_options_reachable', 'copy_translation_exact_clipboard_paste_then_discard',
                         'topics', 'statistics_period', 'goal']}


def tasbih_azkar():
    route('tasbih')
    before = int(prefs().get('tasbih_count', 0))
    tap('tasbih_counter')
    assert int(prefs().get('tasbih_count', 0)) == before + 1
    tap('tasbih_reset')
    tap('tasbih_reset_cancel')
    assert int(prefs().get('tasbih_count', 0)) == before + 1
    tap('tasbih_phrase_open')
    tap('tasbih_phrase_1')
    tap('tasbih_target_99')
    route('tasbih')
    assert prefs().get('tasbih_phrase') == '1' and prefs().get('tasbih_target') == '99'
    shot('tasbih-dark', presentation=True)
    route('azkar')
    observe('azkar_arabic')
    shot('azkar-dark', presentation=True)
    tap('azkar_source')
    shot('azkar-source-dark', presentation=True)
    back()
    tap('azkar_count')
    tap('azkar_next')
    observe('azkar_previous')
    tap('azkar_previous')
    return {'map_ids': ['32', '40'], 'verified': ['counter', 'reset_cancel', 'phrase', 'target', 'restart', 'azkar_source', 'azkar_count_next_previous']}


def knowledge():
    route('knowledge')
    try:
        locate('learning_level_1')
    except AssertionError:
        # A level may already be selected. An offscreen first-run picker must
        # be searched before treating its absence as a different UI state.
        tap('learning_level_change')
        locate('learning_level_1')
    tap('learning_level_1')
    tap('learning_level_confirm')
    tap('learning_start')
    if find(ui(), 'learning_skip') is not None:
        tap('learning_skip')
    bank = json.loads(Path('app/src/main/assets/quiz/ru.json').read_text())['questions']
    wrong_seen = correct_seen = False
    for index in range(8):
        root = observe('learning_question')
        question_text = find(root, 'learning_question').get('text')
        question = next(q for q in bank if q['q'] == question_text)
        wanted = question['options'][question['answer']]
        choices = []
        for number in range(len(question['options'])):
            _, node, _ = locate('learning_answer_' + str(number))
            texts = [n.get('text', '') for n in node.iter('node')]
            choices.append((number, wanted in texts or wanted == node.get('text')))
        selected = next(number for number, correct in choices if correct != (index == 0))
        tap('learning_answer_' + str(selected))
        observe('learning_feedback')
        locate('learning_feedback')
        root = ui()
        feedback_text = ' '.join(n.get('text', '') for n in find(root, 'learning_feedback').iter('node'))
        if index == 0:
            assert 'Не совсем' in feedback_text
            wrong_seen = True
            shot('test-quiz-wrong-feedback')
        elif not correct_seen:
            assert 'Верно' in feedback_text
            correct_seen = True
            shot('test-quiz-correct-feedback')
        if find(root, 'learning_finish') is not None:
            tap('learning_finish')
            observe('МашаАллах!', 'text')
            shot('test-quiz-result')
            break
        tap('learning_next')
    else:
        raise AssertionError('Quiz did not finish in bounded UI steps')
    assert wrong_seen and correct_seen
    return {'map_ids': ['33', '34', '35', '36'], 'real_questions_and_answer_callbacks': True,
            'wrong_and_correct_word_labels': True, 'result_reached': True,
            'answers_selected_by_test': True, 'screens_are_test_evidence': True}


def focus_journal():
    route('focus')
    tap('focus_choose_apps')
    observe('focus_apps_done')
    shot('focus-apps-dark', presentation=True)
    tap('focus_apps_done')
    tap('focus_settings_open')
    shot('focus-settings-dark', presentation=True)
    pause_before = prefs('focus_pause_v1')
    assert pause_before.get('enabled', 'false') == 'false', 'This journey expects the fresh opt-out profile'
    tap('Зикр перед открытием', 'text')
    assert prefs('focus_pause_v1').get('enabled') == 'true'
    for tag, value in (('focus_pause_strict', 'true'), ('focus_pause_soft', 'false')):
        tap(tag)
        root = ui()
        assert find(root, tag).get('selected') == 'true', 'Pause mode is not marked selected: ' + tag
        other = 'focus_pause_soft' if value == 'true' else 'focus_pause_strict'
        assert find(root, other).get('selected') == 'false', 'Both pause modes appear selected'
        assert prefs('focus_pause_v1').get('strict') == value, 'Pause mode did not persist'
    for index in range(3):
        tag = 'focus_pause_phrase_' + str(index)
        tap(tag)
        assert find(ui(), tag).get('selected') == 'true', 'Pause phrase is not marked selected: ' + tag
        assert prefs('focus_pause_v1').get('phrase') == str(index), 'Pause phrase did not persist'
    shot('test-focus-selected-mode-phrase')
    tap('focus_pause_strict' if pause_before.get('strict') == 'true' else 'focus_pause_soft')
    tap('focus_pause_phrase_' + pause_before.get('phrase', '0'))
    tap('Зикр перед открытием', 'text')
    assert prefs('focus_pause_v1').get('enabled') == 'false', 'Pause opt-out was not restored'
    back()
    tap('focus_setup')
    observe('focus_permissions_done')
    shot('focus-permissions-dark', presentation=True)
    tap('focus_permissions_done')
    route('prayer_journal')
    tap('journal_previous')
    tap('journal_FAJR')
    observe('journal_MADE_UP')
    shot('journal-status-chooser-dark', presentation=True)
    back()
    return {'map_ids': ['14', '41', '42', '43', '44'],
            'verified': ['apps_open_done', 'settings_open_back', 'pause_opt_in_out',
                         'both_pause_modes_selected_and_persisted', 'all_three_pause_phrases_selected_and_persisted',
                         'permissions_open_done', 'journal_previous_status_choices'],
            'permission_grants_changed': False, 'pause_opt_out_restored': True,
            'selected_checkmark_visual_evidence': 'screens/test-focus-selected-mode-phrase.png',
            'blocking_services_enabled': False}


def route_gallery():
    targets = [('knowledge', 'quran_open'), ('focus', 'focus_choose_apps'),
               ('quran_audio', 'audio_screen'), ('qibla', 'qibla_compass'),
               ('places', 'places_map'), ('shop', 'shop_search'),
               ('prayer_journal', 'journal_previous'), ('reading_reminders', 'reading_open_BEDTIME'),
               ('progress', 'personal_progress_toggle'), ('training', 'training_dashboard'),
               ('alarm', None), ('prayer_settings', 'prayer_regional_mode'),
               ('language', None), ('city', None), ('ramadan', None), ('zakat', None), ('about', None)]
    visited = []
    for palette in ('dark', 'light'):
        theme(palette)
        for name, tag in targets:
            route(name)
            if tag:
                observe(tag)
            shot(name + '-' + palette, presentation=True)
            visited.append(name + ':' + palette)
    return {'map_ids': ['06', '08', '09', '10', '14', '15', '28', '33', '38', '39', '41', '47', '52', '55', '56', '57', '58', '61'],
            'rendered_routes': visited, 'scope': 'Rendering and named entry controls; not every conditional action.'}


def shop_and_places():
    theme('dark')
    route('shop')
    enter_text('shop_search', 'NoSuchShopRhythm829')
    back()
    observe('shop_empty')
    shot('shop-empty-search-dark', presentation=True)
    tap('shop_clear_search')
    assert find(ui(), 'shop_search').get('text', '') == ''
    tap('shop_merchant')
    observe('shop_seller_portal')
    shot('shop-merchant-terms-dark', presentation=True)
    back()
    route('places')
    observe('places_map')
    root, node, (l, t, r, b) = locate('places_map')
    shell('input', 'swipe', int(l+(r-l)*.7), (t+b)//2, int(l+(r-l)*.35), (t+b)//2, 400)
    shot('places-after-real-pan-dark', presentation=True)
    return {'map_ids': ['47', '48', '52'],
            'verified': ['empty_search', 'clear_search', 'merchant_terms_entry', 'seller_portal_present', 'map_pan_gesture'],
            'map_center_change_asserted': False, 'production_contact_actions_activated': False,
            'seller_product_review_submission': 'not_exercised_no_production_writes'}


def widgets_gallery(palette, font=1.0):
    """Actual native RemoteViews in the existing debug preview, not a mock or launcher."""
    global LOCALE
    LOCALE = 'ru'
    display(960 if font > 1 else 1080, 2400, font)
    theme(palette)
    size = '320dp-150' if font > 1 else '360dp-100'
    variants = [
        ('compact', 'Ближайший намаз · 2×1', 'w_time', 90),
        ('wide-responsive', 'Линия времени · 4×1', 'w_time', 90),
        ('square', 'Мой намаз · 2×2', 'w_time', 240 if font > 1 else 190),
        ('day', 'Расписание дня · 4×2', 'w_time', 380 if font > 1 else 210),
        ('focus', None, 'w_value', 200 if font > 1 else 190),
        ('tasbih', None, 'w_count', 220 if font > 1 else 190),
    ]
    rendered = []
    try:
        for name, label, marker, height in variants:
            route('widgets')
            identifier, attribute = (label, 'text') if label else (marker, 'resource-id')
            root, node, area = locate(identifier, attribute)
            # Align the selected section toward the top within its real scroll
            # container. The leading gutter avoids native widget click targets.
            owners = [n for n in root.iter('node') if n.get('scrollable') == 'true']
            assert owners, 'Widget preview scroll container missing'
            owner = max(owners, key=lambda n: bounds(n)[3] - bounds(n)[1])
            l, t, r, b = visible_bounds(root, owner)
            distance = max(0, area[1] - t - 54)
            if distance > 24:
                start = int(t + (b-t)*.83)
                shell('input', 'swipe', l+18, start, l+18, max(t+36, start-distance), 400)
            root = ui()
            label_node = find(root, label, 'text') if label else None
            label_bottom = bounds(label_node)[3] if label_node is not None else 0
            candidates = [n for n in root.iter('node')
                          if n.get('resource-id', '').endswith('/widget_root')
                          and find(n, marker) is not None
                          and (not label or bounds(n)[1] >= label_bottom - 4)]
            assert candidates, ('Native widget missing', name, size)
            widget = min(candidates, key=lambda n: bounds(n)[1])
            wl, wt, wr, wb = visible_bounds(root, widget)
            assert wb-wt >= height*3-8, ('Native widget clipped', name, bounds(widget), height)
            value = find(widget, marker)
            assert value.get('text', '').strip(), ('Native widget has no value', name)
            shot('widgets-' + name + '-' + size + '-' + palette, presentation=True)
            rendered.append({'section': name, 'native_value': value.get('text'),
                             'height_dp': height, 'visible_bounds': [wl, wt, wr, wb]})
    finally:
        display()
    return {'map_ids': ['63', '64', '65'], 'theme': palette, 'font_scale': font,
            'native_remote_views': True, 'actual_current_time_and_preferences': True,
            'host': 'existing_debug_WidgetPreview', 'launcher_pinning_and_pending_intents_tested': False,
            'wide_at_150_percent': 'expected compact responsive fallback at 320dp' if font > 1 else 'wide',
            'rendered': rendered}


def compact_and_languages():
    global LOCALE
    display(960, 1920, 1.5)
    theme('light')
    route('home')
    for tag in ('home_change_city', 'home_prayer_journal', 'home_reading_reminders'):
        locate(tag)
    route('home')
    shot('home-320dp-150-light', presentation=True)
    reader(114)
    tap('quran_audio_reader_entry')
    for tag in ('ayah_play_pause', 'ayah_repeat_3', 'ayah_speed_75', 'quran_note_open'):
        SUPPORT['require_reachable'](tag)
    shot('ayah-controls-320dp-150-light', presentation=True)
    display(1920, 960, 1.5)
    observe('quran_ayah_sheet')
    SUPPORT['require_reachable']('ayah_play_pause')
    shot('ayah-landscape-150-light', presentation=True)
    display(960, 1920, 1.5)
    route('more')
    locate('settings_guide')
    shot('settings-320dp-150-light', presentation=True)
    for language in ('en', 'tr', 'az', 'uz', 'tg', 'kk', 'ky'):
        LOCALE = language
        route('more')
        locate('settings_guide')
        shot('settings-320dp-150-' + language, presentation=True)
    # Exercise the platform preference used by clockFormatter / BigClock.
    # EN makes the AM/PM assertion unambiguous; no prayer schedule is injected.
    LOCALE = 'en'
    shell('settings', 'put', 'system', 'time_12_24', '12')
    route('home')
    root = observe('home_change_city')
    text = ' '.join(n.get('text', '') for n in root.iter('node'))
    assert re.search(r'\b(?:AM|PM)\b', text), '12-hour period absent from real home UI'
    shot('home-320dp-150-en-12hour', presentation=True)
    shell('settings', 'put', 'system', 'time_12_24', '24')
    LOCALE = 'ru'
    display()
    route('more')
    shell('settings', 'put', 'global', 'animator_duration_scale', '0')
    tap('appearance_dark')
    assert prefs().get('appearance') == 'dark'
    tap('appearance_light')
    assert prefs().get('appearance') == 'light'
    shell('settings', 'put', 'global', 'animator_duration_scale', '1')
    return {'map_ids': ['06', '07', '08', '21', '30', '66'], 'width_dp': 320, 'font_scale': 1.5,
            'locales': ['ru', 'en', 'tr', 'az', 'uz', 'tg', 'kk', 'ky'],
            'rotation_with_open_ayah_sheet': True, 'twelve_hour_period_visible': True,
            'zero_animation_scale_theme_actions': True}


def group(name, action):
    global ACTIVE
    ACTIVE = name
    print('Rhythm UI:', name, flush=True)
    start = time.monotonic()
    try:
        result = action() or {}
        REPORT['groups'][name] = {'status': 'passed', 'seconds': round(time.monotonic()-start, 2), **result}
    except Exception:
        error = traceback.format_exc()
        REPORT['groups'][name] = {'status': 'failed', 'seconds': round(time.monotonic()-start, 2), 'error': error}
        (OUT / ('rhythm-failure-' + name + '.txt')).write_text(error)
        try:
            shot('failure-' + name)
        except Exception:
            pass
        print(error, flush=True)
    persist()
    return REPORT['groups'][name]['status'] == 'passed'


def main():
    assert len(sys.argv) == 2 and sys.argv[1] in {'preview', 'final'}
    mode = sys.argv[1]
    OUT.mkdir(exist_ok=True)
    (OUT / 'screens').mkdir(exist_ok=True)
    delivery = json.loads((OUT / 'rhythm-delivery.json').read_text())
    REPORT.update(mode=mode, source_sha=delivery['source_sha'], source_run=delivery['run_id'],
                  verification_sha=os.environ.get('GITHUB_SHA'), run_id=os.environ.get('GITHUB_RUN_ID'),
                  debug_sha256=hashlib.sha256(APK.read_bytes()).hexdigest())
    adb('wait-for-device')
    shell('svc', 'power', 'stayon', 'true')
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    adb('logcat', '-c')
    display()
    try:
        if mode == 'final':
            group('update_preserves_data', upgrade)
        adb('install', '-r', APK)
        if not group('fresh_first_launch', lambda: first_run(mode == 'final')):
            raise AssertionError('First launch must succeed before presentation captures')
        for palette in ('dark', 'light'):
            group('presentation_' + palette, lambda palette=palette: presentation(palette))
        group('transitions', transition_recording)
        if mode == 'final':
            theme('dark')
            group('reader_actions', reader_actions)
            group('tasbih_azkar', tasbih_azkar)
            group('quiz_answer_feedback_result', knowledge)
            group('focus_journal', focus_journal)
            group('all_main_routes_both_themes', route_gallery)
            group('catalog_search_and_map', shop_and_places)
            group('compact_languages_rotation_motion', compact_and_languages)
            for palette in ('light', 'dark'):
                group('widgets_' + palette, lambda palette=palette: widgets_gallery(palette))
                group('widgets_150_' + palette, lambda palette=palette: widgets_gallery(palette, 1.5))
            # Existing guide assertions exercise real image-load markers, zoom
            # controls and return paths without rerunning playback suites.
            group('guide_all_topics_steps_zoom_return', SUPPORT['illustrated_guide'])
        crashes = adb('logcat', '-d', '-b', 'crash')
        (OUT / 'rhythm-crashes.txt').write_text(crashes)
        REPORT['app_crashes'] = bool(re.search(r'FATAL EXCEPTION|Fatal signal', crashes))
        REPORT['planned_groups_passed'] = all(g['status'] == 'passed' for g in REPORT['groups'].values()) and not REPORT['app_crashes']
        persist()
        assert REPORT['planned_groups_passed'], 'One or more planned UI groups failed; preserved evidence is authoritative'
    finally:
        shell('settings', 'put', 'global', 'animator_duration_scale', '1')
        shell('settings', 'put', 'system', 'font_scale', '1.0')
        shell('settings', 'put', 'system', 'time_12_24', '24')
        shell('wm', 'size', '1080x1920')
        persist()


if __name__ == '__main__':
    main()
