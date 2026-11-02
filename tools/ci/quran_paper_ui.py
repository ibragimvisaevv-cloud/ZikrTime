"""Focused book-reader journeys on synthetic data in a disposable debug emulator.

Contract: visible ayah menus and the selection dock address a stable ayah;
copy preserves text; note dismissal/deletion is explicit; changed layout does
not count off-screen/skipped text or time spent in menus/background. These are
device/UI checks, not a religious review or a physical-phone certification.
"""
exec(compile(open('tools/ci/quran_reader_ui.py').read().split('\nmode = sys.argv[1] if len')[0], 'quran-helpers', 'exec'))
passed = Checkpoints()


def wait_for_quran_index(started=None):
    # The search field can legitimately be below the fold at 150% font size.
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if find_control(ui(), 'quran_index_list') is not None:
            return
        time.sleep(.5)
    raise AssertionError('Quran book index did not finish loading')


def tool(identifier):
    tap_control('quran_tools')
    tap_control(identifier)


def set_reading_mode(whole):
    # FilterChip merges its text semantics. Use the stable tag on the inactive
    # chip itself, then check the actual rendered reading surface.
    if present('quran_whole_surah') != whole:
        tap_control('quran_read_mode')
    assert present('quran_whole_surah') == whole


def open_surah(number):
    # The redesigned index intentionally retains its query, tab and scroll
    # position when returning from a surah. Reveal its tabs before searching.
    if present('quran_tools'):
        tap_control('quran_surahs')
    top()
    tap_control('quran_surahs')
    top()
    replace_text('quran_search', str(number))
    tap_control('quran_surah_' + str(number))


def history():
    return json.loads(prefs().get('quran_statistics_v1', '{}'))


def observed():
    return {verse for day in history().values() for verse in day['verses']}


def tap_visible(identifier):
    # Opening a menu must not silently scroll and change the reading position.
    node = find_control(ui(), identifier)
    assert node is not None, 'Control is not visible: ' + identifier
    left, upper, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
    assert right > left and bottom > upper, identifier
    shell('input', 'tap', (left + right) // 2, (upper + bottom) // 2)
    time.sleep(.7)


def assert_reference(reference):
    tree = ET.tostring(ui(), encoding='unicode')
    assert reference in tree, 'Wrong ayah target: ' + reference


def field_value(identifier):
    node = find_control(ui(), identifier)
    assert node is not None, identifier
    editable = next((child for child in node.iter('node')
                     if child.get('class') == 'android.widget.EditText'), node)
    return editable.get('text', '')


def fullscreen():
    tap_control('quran_fullscreen')
    if present('android:id/immersive_cling_title'):
        tap_control('android:id/ok')


def scroll_reader():
    node = find_control(ui(), 'quran_whole_surah')
    assert node is not None
    left, upper, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
    shell('input', 'swipe', left + 10, int(upper + (bottom - upper) * .8),
          left + 10, int(upper + (bottom - upper) * .3), 650)
    time.sleep(1)


start('home')
put_pref('online_times', 'boolean', 'false')
put_pref('appearance', 'string', 'light')
put_pref('personal_progress_enabled', 'boolean', 'false')
shell('svc', 'wifi', 'disable')
shell('svc', 'data', 'disable')
fixture = ET.Element('map')
ET.SubElement(fixture, 'boolean', {'name': 'whole_surah', 'value': 'false'})
for key, value in [('last', '2:255'), ('stop', '2:255'), ('note_2:255', 'Legacy-255')]:
    ET.SubElement(fixture, 'string', {'name': key}).text = value
ET.SubElement(ET.SubElement(fixture, 'set', {'name': 'bookmarks'}), 'string').text = '2:255'
write_tree(fixture, 'quran_reading')

start('quran')
shot('paper-index-light')
locate_control('quran_continue')
assert not present('quran_saved_stop'), 'Identical last/stop positions appear twice'
open_surah(112)
set_reading_mode(False)
locate_control('quran_ayah_actions_2')
before_menu = prefs('quran_reading')['last']
tap_visible('quran_ayah_actions_2')
assert_reference('112:2')
assert prefs('quran_reading')['last'] == before_menu, 'Opening ayah actions changed last reading'
shot('paper-ayah-actions-light')
back()
assert present('quran_selection')
back()
assert not present('quran_selection') and present('quran_tools'), 'Back did not clear selection first'
passed['visible_ayah_menu_stable_reference_and_back_clears_selection'] = True

# Clipboard is read through a real editable note, not an unsupported adb API.
# A short verse is intentional: the note field has a 2000-character limit.
jump(2)
arabic = read_text('quran_arabic_2')
translation = read_text('quran_translation_2')
assert arabic and translation and arabic != translation
for kind, expected in [('arabic', arabic), ('translation', translation),
                       ('both', arabic + '\n\n' + translation)]:
    jump(2)
    tap_control('quran_ayah_actions_2')
    tap_control('quran_copy_ayah')
    tap_control('quran_copy_' + kind)
    tap_control('quran_selection_note')
    assert '112:2' in read_text('quran_note_location')
    tap_control('quran_stop_note')
    shell('input', 'keyevent', 'KEYCODE_PASTE')
    time.sleep(.5)
    assert field_value('quran_stop_note') == expected, 'Copy changed ' + kind + ' text'
    back()  # keyboard
    tap_control('quran_note_close')
    tap_control('quran_note_discard')
    assert 'note_112:2' not in prefs('quran_reading'), 'Discarded clipboard draft was saved'
passed['three_copy_variants_preserve_exact_rendered_text_without_saving_drafts'] = True

open_surah(2)
jump(255)
original_arabic = read_text('quran_arabic_255')
jump(255)
tap_control('quran_ayah_actions_255')
tap_control('quran_note_open')
assert '2:255' in read_text('quran_note_location')
replace_text('quran_stop_note', 'Draft-0818')
tap_control('quran_note_close')
locate_control('quran_note_discard')
shot('paper-note-unsaved-guard')
tap_control('quran_note_keep')
assert field_value('quran_stop_note') == 'Draft-0818'
tap_control('quran_note_close')
tap_control('quran_note_discard')
assert prefs('quran_reading')['note_2:255'] == 'Legacy-255'
tap_control('quran_selection_note')
replace_text('quran_stop_note', 'Saved-0818')
shot('paper-note-editor')
tap_control('quran_stop_save')
assert prefs('quran_reading')['note_2:255'] == 'Saved-0818'
tap_control('quran_selection_note')
tap_control('quran_note_delete')
tap_control('quran_note_delete_cancel')
assert prefs('quran_reading')['note_2:255'] == 'Saved-0818'
tap_control('quran_note_delete')
tap_control('quran_note_delete_confirm')
saved = prefs('quran_reading')
assert 'note_2:255' not in saved and '2:255' in saved['bookmarks']
jump(255)
assert read_text('quran_arabic_255') == original_arabic
passed['note_draft_guard_save_and_confirmed_delete_preserve_bookmark_and_arabic'] = True

jump(286)
tap_control('quran_ayah_actions_286')
tap_control('quran_note_open')
assert '2:286' in read_text('quran_note_location')
replace_text('quran_stop_note', 'Last-ayah-0818')
tap_control('quran_stop_save')
assert prefs('quran_reading')['note_2:286'] == 'Last-ayah-0818'
set_reading_mode(True)
whole_end = read_text('quran_whole_286')
assert whole_end
shot('paper-whole-baqarah-end-light')
start('quran')
tap_control('quran_continue')
assert read_text('quran_whole_286') == whole_end
assert prefs('quran_reading')['last'] == '2:286'
passed['last_ayah_mode_switch_and_process_restart_preserve_position'] = True

put_pref('appearance', 'string', 'dark')
start('quran')
shot('paper-index-dark')
open_surah(1)
set_reading_mode(True)
whole_fatiha = read_text('quran_whole_1')
shot('paper-whole-fatiha-dark')
fullscreen()
shot('paper-fullscreen-dark')
tool('quran_rotate')
time.sleep(2)
locate_control('quran_exit_fullscreen')
assert read_text('quran_whole_1') == whole_fatiha
shot('paper-fullscreen-landscape-dark')
tool('quran_rotate')
time.sleep(2)
tap_control('quran_exit_fullscreen')
set_reading_mode(False)
read_text('quran_arabic_1')
shot('paper-translation-dark')
passed['dark_book_surface_both_modes_fullscreen_and_rotation'] = True

shell('wm', 'size', '720x1280')
shell('wm', 'density', '360')
shell('settings', 'put', 'system', 'font_scale', '1.5')
start('quran')
shot('paper-index-320dp-150')
open_surah(112)
set_reading_mode(False)
tap_control('quran_ayah_actions_1')
tap_control('quran_note_open')
assert '112:1' in read_text('quran_note_location')
locate_control('quran_stop_save')
shot('paper-note-320dp-150')
tap_control('quran_note_close')
set_reading_mode(True)
read_text('quran_whole_1')
fullscreen()
locate_control('quran_tools')
shot('paper-reading-320dp-150')
passed['scrollable_index_and_reachable_reader_note_controls_at_320dp_150_percent'] = True
shell('settings', 'put', 'system', 'font_scale', '1')
shell('wm', 'size', '1080x1920')
shell('wm', 'density', '420')

# The framed viewport may show fewer ayahs; require real visible reading credit,
# never all four short-surah ayahs at once. Unit tests cover exact time budgets.
put_pref('personal_progress_enabled', 'boolean', 'true')
put_pref('quran_statistics_v1', 'string', '{}')
start('quran')
open_surah(112)
set_reading_mode(True)
read_text('quran_whole_1')
for _ in range(20):
    if observed():
        break
    time.sleep(1)
assert observed(), 'Fully visible quiet reading was never counted'
assert all(value.startswith('112:') for value in observed())
tap_control('quran_tools')
before = history()
time.sleep(3)
assert history() == before, 'Menu time counted as reading'
back()
shell('input', 'keyevent', 'KEYCODE_HOME')
time.sleep(1)
before = history()
time.sleep(3)
assert history() == before, 'Background time counted as reading'
start('quran')
open_surah(2)
jump(282)
read_text('quran_whole_282')
time.sleep(5)
assert '2:282' not in observed(), 'A partly visible long ayah was counted'
assert not any('2:' + str(number) in observed() for number in range(8, 282)), 'Jump counted skipped ayahs'
passed['new_viewport_preserves_visible_only_reading_and_excludes_menus_background_and_jumps'] = True

start('quran', 'en')
open_surah(112)
set_reading_mode(False)
tap_control('quran_ayah_actions_2')
tap_control('quran_note_open')
assert '112:2' in read_text('quran_note_location')
assert any(node.get('text') == 'Save note' for node in ui().iter('node'))
shot('paper-note-english')
passed['english_note_controls'] = True
(OUT / 'quran-paper-passed.json').write_text(json.dumps(passed, indent=2))
