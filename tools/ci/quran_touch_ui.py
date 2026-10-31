"""Focused 0.8.17 journeys: real gestures and passive reading, synthetic device data."""
import os

exec(compile(open('tools/ci/quran_reader_ui.py').read().split('\nmode = sys.argv[1] if len')[0], 'quran-helpers', 'exec'))
passed = Checkpoints()


def tool(identifier):
    tap_control('quran_tools')
    tap_control(identifier)


def history():
    return json.loads(prefs().get('quran_statistics_v1', '{}'))


def observed():
    return {verse for day in history().values() for verse in day['verses']}


def reader_prefs(values):
    root = prefs_tree('quran_reading')
    for node in list(root):
        if node.get('name') in values:
            root.remove(node)
    for key, (kind, value) in values.items():
        node = ET.SubElement(root, kind, {'name': key})
        if kind == 'string': node.text = value
        else: node.set('value', str(value).lower())
    write_tree(root, 'quran_reading')


def press_arabic(identifier, long=True):
    # The first RTL word of an exact jump anchor, inside its first line box.
    read_text(identifier)
    node = locate_control(identifier)
    left, top, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
    x, y = right - 36, top + min(75, (bottom - top) // 2)
    if long: shell('input', 'swipe', x, y, x, y, 900)
    else: shell('input', 'tap', x, y)
    time.sleep(.7)


def fullscreen():
    tap_control('quran_fullscreen')
    if present('android:id/immersive_cling_title'):
        tap_control('android:id/ok')


def scroll_reader():
    root = ui()
    node = find_control(root, 'quran_whole_surah')
    assert node is not None
    left, top, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
    shell('input', 'swipe', left + 6, int(top + (bottom-top)*.75),
          left + 6, int(top + (bottom-top)*.25), 700)
    time.sleep(3)


if os.environ.get('QURAN_TOUCH_PHASE') != 'finish':
    start('home')
    put_pref('online_times', 'boolean', 'false')
    put_pref('appearance', 'string', 'light')
    put_pref('personal_progress_enabled', 'boolean', 'false')
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    root = ET.Element('map')
    ET.SubElement(root, 'boolean', {'name': 'whole_surah', 'value': 'true'})
    for key, value in [('last', '1:4'), ('stop', '1:4'), ('note_1:4', 'Legacy-note')]:
        ET.SubElement(root, 'string', {'name': key}).text = value
    ET.SubElement(ET.SubElement(root, 'set', {'name': 'bookmarks'}), 'string').text = '1:1'
    write_tree(root, 'quran_reading')
    start('quran')
    tap_control('quran_saved_stop')
    original = read_text('quran_whole_4')
    press_arabic('quran_whole_4')
    tap_control('quran_note_open')
    assert '1:4' in read_text('quran_note_location')
    assert 'Legacy-note' in ET.tostring(ui(), encoding='unicode')
    assert not present('quran_stop_ayah') and not present('quran_ayah_input')
    replace_text('quran_stop_note', 'Gesture-note')
    shot('features-touch-note')
    tap_control('quran_stop_save')
    assert prefs('quran_reading')['note_1:4'] == 'Gesture-note'
    assert read_text('quran_whole_4') == original
    passed['long_press_exact_ayah_note_without_number_fields_and_unchanged_text'] = True

    press_arabic('quran_whole_4', long=False)
    tap_control('quran_quick_bookmark')
    saved = prefs('quran_reading')
    assert saved['stop'] == '1:4' and {'1:1', '1:4'} <= set(saved['bookmarks'])
    assert saved['note_1:4'] == 'Gesture-note'
    fullscreen()
    locate_control('quran_quick_bookmark')
    shot('features-touch-fullscreen')
    tool('quran_rotate')
    time.sleep(3)
    locate_control('quran_quick_bookmark')
    assert present('quran_exit_fullscreen')
    shot('features-touch-landscape')
    tool('quran_rotate')
    time.sleep(3)
    tap_control('quran_exit_fullscreen')
    start('quran')
    tap_control('quran_saved_stop')
    tool('quran_note_open')
    assert '1:4' in read_text('quran_note_location')
    assert 'Gesture-note' in ET.tostring(ui(), encoding='unicode')
    back()
    tap_control('quran_quick_bookmark')
    assert '1:4' not in prefs('quran_reading')['bookmarks']
    assert prefs('quran_reading')['note_1:4'] == 'Gesture-note'
    tap_control('quran_quick_bookmark')
    assert prefs('quran_reading')['stop'] == '1:4'
    assert observed() == set(), 'Statistics opt-out was ignored'
    passed['one_tap_bookmark_rotation_restart_and_existing_notes_preserved'] = True

    put_pref('personal_progress_enabled', 'boolean', 'true')
    put_pref('quran_statistics_v1', 'string', '{}')
    start('quran')
    open_surah(112)
    for _ in range(20):
        if {'112:1', '112:2', '112:3', '112:4'} <= observed(): break
        time.sleep(1)
    assert observed() == {'112:1', '112:2', '112:3', '112:4'}, observed()
    shot('features-touch-auto-read')
    tap_control('quran_tools')
    assert not present('quran_mark_open')
    before = history()
    time.sleep(3)
    assert history() == before, 'Menu time changed reading history'
    back()
    shell('input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(1)
    before = history()
    time.sleep(3)
    assert history() == before, 'Background time changed reading history'
    start('quran')
    open_surah(112)
    time.sleep(10)
    assert observed() == {'112:1', '112:2', '112:3', '112:4'}, observed()
    passed['automatic_short_surah_deduplication_menu_and_background_exclusion'] = True

    open_surah(2)
    jump(282)
    read_text('quran_whole_282')
    time.sleep(6)
    assert '2:282' not in observed(), 'Only part of the long ayah was visible'
    assert not any('2:' + str(n) in observed() for n in range(8, 282)), 'Jump counted skipped ayahs'
    for _ in range(12):
        if '2:282' in observed(): break
        scroll_reader()
    assert '2:282' in observed(), 'Complete slowly scrolled ayah was not counted'
    assert not any('2:' + str(n) in observed() for n in range(8, 282)), observed()
    tool('quran_statistics_open')
    shot('features-touch-statistics')
    back()
    passed['long_ayah_requires_complete_view_and_jumps_do_not_fill_gaps'] = True

    # Selection in translation mode also has the ayah attached automatically.
    jump(286)
    tap_control('quran_read_mode')
    press_arabic('quran_arabic_286')
    tap_control('quran_note_open')
    assert '2:286' in read_text('quran_note_location')
    assert not present('quran_stop_ayah')
    replace_text('quran_stop_note', 'Last-ayah-note')
    tap_control('quran_stop_save')
    assert prefs('quran_reading')['note_2:286'] == 'Last-ayah-note'
    passed['verse_mode_note_uses_selected_ayah'] = True
else:
    # Fresh synthetic state for the remaining journeys, not a claim that the
    # previous APK's five passed journeys ran again on this APK.
    assert (OUT / 'inherited-touch-verification.json').exists()
    start('home')
    put_pref('online_times', 'boolean', 'false')
    put_pref('personal_progress_enabled', 'boolean', 'true')
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    root = ET.Element('map')
    ET.SubElement(root, 'boolean', {'name': 'whole_surah', 'value': 'false'})
    for key, value in [('last', '1:4'), ('stop', '1:4'),
                       ('note_1:4', 'Gesture-note'), ('note_2:286', 'Last-ayah-note')]:
        ET.SubElement(root, 'string', {'name': key}).text = value
    bookmarks = ET.SubElement(root, 'set', {'name': 'bookmarks'})
    for value in ['1:1', '1:4']:
        ET.SubElement(bookmarks, 'string').text = value
    write_tree(root, 'quran_reading')

put_pref('appearance', 'string', 'dark')
shell('wm', 'size', '720x1280')
shell('wm', 'density', '360')
shell('settings', 'put', 'system', 'font_scale', '1.5')
start('quran')
shot('features-touch-large-index')
open_surah(112)
tap_control('quran_read_mode')
read_text('quran_whole_1')
fullscreen()
locate_control('quran_quick_bookmark')
shot('features-touch-large-dark')
press_arabic('quran_whole_1')
tap_control('quran_note_open')
assert '112:1' in read_text('quran_note_location')
locate_control('quran_stop_save')
shot('features-touch-large-note')
back()
passed['fullscreen_gestures_and_note_controls_320dp_150_percent'] = True

shell('settings', 'put', 'system', 'font_scale', '1')
shell('wm', 'size', '1080x1920')
shell('wm', 'density', '420')
reader_prefs({'timer_remaining': ('long', 2500), 'timer_paused': ('boolean', False), 'timer_finished': ('boolean', False)})
start('quran')
tap_control('quran_continue')
for _ in range(10):
    if prefs('quran_reading')['timer_finished'] == 'true': break
    time.sleep(1)
assert prefs('quran_reading')['timer_finished'] == 'true'
assert any(n.get('text') == 'Время чтения завершено' for n in ui().iter('node'))
tap_control('Продолжить чтение', 'text')
assert prefs('quran_reading')['timer_finished'] == 'false'
passed['reading_timer_still_completes'] = True

start('quran', 'en')
open_surah(112)
press_arabic('quran_whole_1')
tap_control('quran_note_open')
assert '112:1' in read_text('quran_note_location')
assert any(n.get('text') == 'Save note' for n in ui().iter('node'))
passed['english_context_note'] = True
(OUT / 'quran-features-passed.json').write_text(json.dumps(passed, indent=2))
