"""0.8.16 journeys on the real reader, with private synthetic device data only."""
exec(compile(open('tools/ci/quran_reader_ui.py').read().split('\nmode = sys.argv[1] if len')[0], 'quran-helpers', 'exec'))
passed = Checkpoints()


def tool(identifier):
    tap_control('quran_tools')
    tap_control(identifier)


def enter_fullscreen():
    tap_control('quran_fullscreen')
    if present('android:id/immersive_cling_title'):
        tap_control('android:id/ok')


def history():
    return json.loads(prefs().get('quran_statistics_v1', '{}'))


def total_time():
    return sum(x['ms'] for x in history().values())


def mark():
    tool('quran_mark_open')
    replace_text('quran_mark_first', '1')
    replace_text('quran_mark_last', '7')
    tap_control('quran_mark_save')


def set_reader_prefs(values):
    root = prefs_tree('quran_reading')
    for node in list(root):
        if node.get('name') in values:
            root.remove(node)
    for key, (kind, value) in values.items():
        node = ET.SubElement(root, kind, {'name': key})
        if kind == 'string':
            node.text = value
        else:
            node.set('value', str(value).lower())
    write_tree(root, 'quran_reading')


import os
phase = os.environ.get('QURAN_FEATURE_PHASE', 'all')
if phase in ('tail', 'finish'):
    # Reuse only completed journeys from the checksum-pinned APK.
    previous = json.loads((OUT / 'previous-quran-passed.json').read_text())
    keys = ['exact_stop_note_and_existing_bookmark', 'immersive_rotation_keeps_reader_and_note',
            'stop_and_note_survive_process_restart']
    if phase == 'finish':
        keys += ['marks_are_unique_and_menu_time_is_not_counted',
                 'timer_foreground_background_screen_off_pause_and_stats_opt_out']
    assert all(previous.get(key) is True for key in keys), previous
    for key in keys:
        passed[key] = True
    start('home')
    put_pref('online_times', 'boolean', 'false')
    put_pref('appearance', 'string', 'light')
    if phase == 'finish':
        put_pref('personal_progress_enabled', 'boolean', 'false')
        day = shell('date', '+%Y-%m-%d').strip()
        put_pref('quran_statistics_v1', 'string', json.dumps({
            day: {'ms': 12000, 'verses': ['1:' + str(n) for n in range(1, 8)]}
        }))
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    root = ET.Element('map')
    ET.SubElement(root, 'boolean', {'name': 'whole_surah', 'value': 'true'})
    for key, value in [('last', '1:4'), ('stop', '1:4'), ('note_1:4', 'Continue-here')]:
        ET.SubElement(root, 'string', {'name': key}).text = value
    ET.SubElement(ET.SubElement(root, 'set', {'name': 'bookmarks'}), 'string').text = '1:1'
    write_tree(root, 'quran_reading')
    start('quran')
    tap_control('quran_saved_stop')
else:
    start('home')
    put_pref('online_times', 'boolean', 'false')
    put_pref('appearance', 'string', 'light')
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    start('quran')
    open_surah(1)
    arabic = read_text('quran_arabic_1')
    tap_control('quran_bookmark_1')
    tap_control('quran_read_mode')
    whole = read_text('quran_whole_1')
    assert whole.startswith(arabic) and '۝٧' in whole
    tool('quran_stop_open')
    replace_text('quran_stop_ayah', '4')
    replace_text('quran_stop_note', 'Continue-here')
    tap_control('quran_stop_save')
    saved = prefs('quran_reading')
    assert saved['stop'] == '1:4' and saved['note_1:4'] == 'Continue-here'
    assert '1:1' in saved['bookmarks']
    passed['exact_stop_note_and_existing_bookmark'] = True
    
    enter_fullscreen()
    assert present('quran_exit_fullscreen') and not present('quran_read_mode')
    shot('features-fullscreen-light')
    tool('quran_rotate')
    time.sleep(4)
    tree = shot('features-landscape')
    bounds = list(map(int, re.findall(r'-?\d+', next(tree.iter('node')).get('bounds'))))
    assert bounds[2] - bounds[0] > bounds[3] - bounds[1], bounds
    assert present('quran_exit_fullscreen') and present('quran_whole_surah')
    assert prefs('quran_reading')['note_1:4'] == 'Continue-here'
    tool('quran_rotate')
    time.sleep(3)
    tap_control('quran_exit_fullscreen')
    passed['immersive_rotation_keeps_reader_and_note'] = True
    
    start('quran')
    tap_control('quran_saved_stop')
    assert present('quran_whole_4')
    tool('quran_stop_open')
    assert 'Continue-here' in ET.tostring(ui(), encoding='unicode')
    back()
    passed['stop_and_note_survive_process_restart'] = True
if phase != 'finish':
    mark()
    mark()
    values = history()
    unique = {v for day in values.values() for v in day['verses']}
    assert unique == {'1:' + str(n) for n in range(1, 8)}, unique
    tool('quran_statistics_open')
    locate_control('quran_stats_unique')
    shot('features-statistics')
    before = total_time()
    time.sleep(3)
    assert total_time() == before, 'Menu time entered reading history'
    passed['marks_are_unique_and_menu_time_is_not_counted'] = True
    # Reopen at the top; overscrolling down would dismiss a modal bottom sheet.
    back()
    tool('quran_statistics_open')
    tap_control('personal_progress_toggle')
    assert prefs()['personal_progress_enabled'] == 'false'
    back()
    time.sleep(4)
    tool('quran_timer_open')
    before_disabled = total_time()
    replace_text('quran_timer_minutes', '3')
    tap_control('quran_timer_start')
    time.sleep(6)
    shell('input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(1)
    remaining = int(prefs('quran_reading')['timer_remaining'])
    assert 0 < remaining < 180000, remaining
    time.sleep(3)
    assert int(prefs('quran_reading')['timer_remaining']) == remaining
    shell('am', 'start', '-W', '-n', PKG + '/.MainActivity')
    time.sleep(4)
    shell('input', 'keyevent', 'KEYCODE_SLEEP')
    time.sleep(1)
    asleep = int(prefs('quran_reading')['timer_remaining'])
    time.sleep(3)
    assert int(prefs('quran_reading')['timer_remaining']) == asleep
    assert asleep < remaining, (asleep, remaining)
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    time.sleep(2)
    tool('quran_timer_open')
    tap_control('quran_timer_pause')
    paused = int(prefs('quran_reading')['timer_remaining'])
    time.sleep(3)
    assert int(prefs('quran_reading')['timer_remaining']) == paused
    assert total_time() == before_disabled
    passed['timer_foreground_background_screen_off_pause_and_stats_opt_out'] = True

# Near completion fixture: same persisted production fields, no test-only app behavior.
set_reader_prefs({'timer_remaining': ('long', 2500), 'timer_paused': ('boolean', False),
                  'timer_finished': ('boolean', False)})
start('quran')
tap_control('quran_continue')
for _ in range(10):
    if prefs('quran_reading')['timer_finished'] == 'true':
        break
    time.sleep(1)
assert prefs('quran_reading')['timer_finished'] == 'true'
# AlertDialog has a separate semantics root; use the visible user-facing text.
# Searching for an unexported test tag would swipe outside and dismiss the dialog.
tree = shot('features-timer-complete')
assert any(n.get('text') == 'Время чтения завершено' for n in tree.iter('node'))
assert any(n.get('text') == 'Продолжить чтение' for n in tree.iter('node'))
tap_control('Продолжить чтение', 'text')
assert prefs('quran_reading')['timer_finished'] == 'false'
time.sleep(3)
assert not any(n.get('text') == 'Время чтения завершено' for n in ui().iter('node'))
passed['timer_completes_and_continues_without_restart_loop'] = True

# Re-enable and clear through the real global control; bookmarks/notes must remain.
start('progress')
assert history(), 'Clear scenario requires existing statistics'
tap_control('personal_progress_toggle')
tap_control('personal_progress_clear')
tap_control('personal_progress_clear_confirm')
assert history() == {}
saved = prefs('quran_reading')
assert saved['stop'] == '1:4' and saved['note_1:4'] == 'Continue-here'
assert '1:1' in saved['bookmarks']
passed['clear_statistics_preserves_reading_preferences_and_notes'] = True

put_pref('appearance', 'string', 'dark')
shell('wm', 'size', '720x1280')
shell('wm', 'density', '360')
shell('settings', 'put', 'system', 'font_scale', '1.5')
start('quran')
open_surah(2)
jump(286)
assert '۝٢٨٦' in read_text('quran_whole_286')
shot('features-large-text-dark')
tap_control('quran_read_mode')
read_text('quran_arabic_286')
jump(287)
assert present('quran_jump_error')
back()
enter_fullscreen()
shot('features-large-text-fullscreen')
tap_control('quran_exit_fullscreen')
passed['whole_long_surah_bounds_and_controls_320dp_150_percent'] = True
shell('settings', 'put', 'system', 'font_scale', '1')
shell('wm', 'size', '1080x1920')
shell('wm', 'density', '420')
put_pref('places_json', 'string', json.dumps({'places': [], 'seller_bot': 'ZikrTimeShopBot',
                                           'donate_url': 'https://example.org/support'}))
put_pref('places_at', 'string', str(int(time.time() * 1000)))
start('more')
locate_control('project_support')
shot('features-support')
locate_control('feedback_open')
passed['existing_support_and_feedback_entry_visible'] = True

start('quran', 'en')
open_surah(114)
tap_control('quran_read_mode')
assert '۝٦' in read_text('quran_whole_1')
shot('features-english-last-surah')
passed['english_last_surah'] = True
(OUT / 'quran-features-passed.json').write_text(json.dumps(passed, indent=2))
