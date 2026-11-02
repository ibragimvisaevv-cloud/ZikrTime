"""Saved ayahs and readable statistics, on a disposable debug emulator.

Uses real controls for saves/deletions and a process restart for persistence.
Statistics use a known synthetic history (never the user's private data).
This targets the changed screens; source-byte and counting invariants remain
covered by the focused Quran tests and the candidate payload check.
"""
exec(compile(open('tools/ci/quran_paper_ui.py').read().split("\nstart('home')")[0], 'paper-helpers', 'exec'))
from datetime import date, timedelta
import os

passed = Checkpoints()
phase = os.environ.get('QURAN_SAVED_PHASE', 'all')
assert phase in ('all', 'marks', 'stats', 'layout', 'remaining'), phase
if phase == 'remaining':
    # Only the reuse workflow may select this phase. It pins and verifies the
    # APK and unchanged Android inputs before carrying completed journeys over.
    previous = json.loads((OUT / 'inherited-saved-verification.json').read_text())['passed']
    inherited = (
        'bookmark_note_and_combined_marks_visible_in_both_modes_after_restart',
        'saved_list_exact_jump_and_note_deletion_refresh_marks_preserving_bookmark',
        'saved_list_and_note_editor_do_not_add_reading_time',
    )
    assert set(previous) == set(inherited), ('Unexpected inherited journeys', previous)
    for key in inherited:
        assert previous.get(key) is True, ('Missing inherited journey', key)
        passed[key] = True


def content(identifier):
    """Read accessible text and descriptions, including merged icon chips."""
    node = locate_control(identifier)
    return ' '.join((n.get('text', '') + ' ' + n.get('content-desc', '')).strip()
                    for n in node.iter('node')).strip()


def first_number(identifier):
    value = content(identifier)
    found = re.search(r'\d+', value)
    assert found, (identifier, value)
    return int(found.group())


def replace_goal(value):
    # The numeric keyboard does not reliably implement the desktop Ctrl+A
    # shortcut used by the general text helper. This field permits <=3 digits;
    # delete them explicitly, then verify the value before testing validation.
    owner = locate_control('quran_daily_goal')
    editable = next((n for n in owner.iter('node')
                     if n.get('class') == 'android.widget.EditText'), None)
    assert editable is not None, 'Numeric goal has no editable semantic node'
    # Compose's outer EditText also bounds its label/supporting text. Prefer the
    # blank inner editor view, which is the actual typing area, when exposed.
    areas = []
    for node in editable.iter('node'):
        if node is not editable and node.get('class') != 'android.view.View':
            continue
        left, upper, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
        if right - left > 24 and bottom - upper > 12:
            areas.append((node is editable, -(right - left), left, upper, right, bottom))
    assert areas, 'Numeric goal has no visible typing area'
    _, _, left, upper, right, bottom = min(areas)
    shell('input', 'tap', (left + right) // 2, (upper + bottom) // 2)
    time.sleep(.7)
    shell('input', 'keyevent', 'KEYCODE_MOVE_END')
    shell('input', 'keyevent', 'KEYCODE_DEL', 'KEYCODE_DEL', 'KEYCODE_DEL')
    assert field_value('quran_daily_goal') == '', 'Automation did not clear numeric goal'
    shell('input', 'text', str(value))
    back()
    assert field_value('quran_daily_goal') == str(value), 'Automation did not enter numeric goal'


def reveal_above(identifier, attribute='resource-id'):
    """Scroll upward only until the needed control is on screen.

    Blindly swiping down several more times at the top of a bottom sheet can
    dismiss it. Stop as soon as the target is reachable instead.
    """
    for _ in range(22):
        tree = ui()
        node = find_control(tree, identifier, attribute)
        if node is not None:
            left, upper, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
            if right - left >= 12 and bottom - upper >= 12:
                return
        scrolls = []
        for candidate in tree.iter('node'):
            if candidate.get('scrollable') != 'true':
                continue
            left, upper, right, bottom = map(int, re.findall(r'-?\d+', candidate.get('bounds')))
            if bottom - upper > 120:
                scrolls.append((bottom - upper, left, upper, right, bottom))
        assert scrolls, 'No scroll owner above: ' + identifier
        _, left, upper, right, bottom = max(scrolls)
        shell('input', 'swipe', left + 6, int(upper + (bottom - upper) * .25),
              left + 6, int(upper + (bottom - upper) * .82), 400)
    raise AssertionError('Control not reached above: ' + identifier)


def frame_week(from_below=False):
    # Align the actual section heading, not the tall container's clipped lower
    # edge. This captures the complete chart on a normal screen and its first
    # readable day rows with enlarged text.
    title = 'Последние 7 дней'
    if from_below:
        reveal_above(title, 'text')
    else:
        locate_control(title, 'text')
    for _ in range(6):
        tree = ui()
        heading = find_control(tree, title, 'text')
        assert heading is not None, 'Week heading disappeared while framing'
        parents = {child: parent for parent in tree.iter() for child in parent}
        owner = parents.get(heading)
        while owner is not None and owner.get('scrollable') != 'true':
            owner = parents.get(owner)
        assert owner is not None, 'Week heading has no scroll owner'
        left, upper, right, bottom = map(int, re.findall(r'-?\d+', owner.get('bounds')))
        _, heading_top, _, _ = map(int, re.findall(r'-?\d+', heading.get('bounds')))
        delta = heading_top - (upper + 32)
        if abs(delta) < 40:
            return
        height = bottom - upper
        distance = min(abs(delta), int(height * .55))
        start_y = int(upper + height * (.8 if delta > 0 else .2))
        end_y = start_y - distance if delta > 0 else start_y + distance
        shell('input', 'swipe', left + 6, start_y, left + 6, end_y, 400)


def seed_reader(whole=False, saved=False):
    root = ET.Element('map')
    ET.SubElement(root, 'boolean', {'name': 'whole_surah', 'value': str(whole).lower()})
    ET.SubElement(root, 'int', {'name': 'daily_goal_minutes', 'value': '10'})
    ET.SubElement(root, 'string', {'name': 'last'}).text = '1:1'
    if saved:
        ET.SubElement(root, 'string', {'name': 'note_1:2'}).text = 'Meaning-0819'
        ET.SubElement(root, 'string', {'name': 'note_1:3'}).text = 'Review-0819'
        values = ET.SubElement(root, 'set', {'name': 'bookmarks'})
        for value in ('1:1', '1:3'):
            ET.SubElement(values, 'string').text = value
    write_tree(root, 'quran_reading')


def save_note(number, text):
    jump(number)
    tap_control('quran_ayah_actions_' + str(number))
    tap_control('quran_note_open')
    assert '1:' + str(number) in read_text('quran_note_location')
    replace_text('quran_stop_note', text)
    tap_control('quran_stop_save')
    assert prefs('quran_reading')['note_1:' + str(number)] == text


def open_stats():
    start('quran')
    tap_control('quran_statistics_open')
    locate_control('quran_stats_today_time')


def seed_history(empty=False, enabled=True):
    today = date.fromisoformat(shell('date', '+%Y-%m-%d').strip())
    fixture = {} if empty else {
        today.isoformat(): {'ms': 120000, 'verses': ['1:1', '1:2']},
        (today - timedelta(days=1)).isoformat(): {'ms': 180000, 'verses': ['1:2', '1:3']},
        (today - timedelta(days=8)).isoformat(): {'ms': 300000, 'verses': ['1:' + str(n) for n in range(4, 8)]},
        (today - timedelta(days=40)).isoformat(): {'ms': 420000, 'verses': ['2:1']},
    }
    put_pref('personal_progress_enabled', 'boolean', str(enabled).lower())
    put_pref('quran_statistics_v1', 'string', json.dumps(fixture))
    (OUT / 'saved-history-fixture.json').write_text(json.dumps(fixture, indent=2))
    return today


def period(days, minutes, verses, active):
    reveal_above('quran_stats_period_' + str(days))
    tap_control('quran_stats_period_' + str(days))
    assert first_number('quran_stats_time') == minutes, content('quran_stats_time')
    assert first_number('quran_stats_verses') == verses, content('quran_stats_verses')
    assert first_number('quran_stats_active_days') == active, content('quran_stats_active_days')


start('home')
put_pref('online_times', 'boolean', 'false')
put_pref('appearance', 'string', 'light')
put_pref('personal_progress_enabled', 'boolean', 'false')
shell('svc', 'wifi', 'disable')
shell('svc', 'data', 'disable')

if phase in ('all', 'marks'):
    seed_reader()
    start('quran')
    open_surah(1)
    original = read_text('quran_arabic_1')
    tap_control('quran_bookmark_1')
    save_note(2, 'Meaning-0819')
    jump(3)
    tap_control('quran_bookmark_3')
    save_note(3, 'Review-0819')

    # A new process must show the marks before selecting any ayah.
    start('quran')
    tap_control('quran_continue')
    set_reading_mode(False)
    assert not present('quran_selection'), 'A restored reader selected an ayah without a tap'
    assert 'замет' in content('quran_saved_indicator_3').lower()
    for number, word in ((1, 'Заклад'), (2, 'Замет'), (3, 'Замет')):
        jump(number)
        assert word.lower() in content('quran_saved_indicator_' + str(number)).lower()
    assert 'заклад' in content('quran_saved_indicator_3').lower()
    assert 'Review-0819' in content('quran_saved_note_preview_3')
    if present('quran_selection'):
        back()
    shot('saved-translation-light')
    jump(1)
    assert read_text('quran_arabic_1') == original
    set_reading_mode(True)
    whole = read_text('quran_whole_1')
    for number in (1, 2, 3):
        locate_control('quran_saved_indicator_' + str(number))
    shot('saved-whole-light')
    start('quran')
    tap_control('quran_continue')
    assert present('quran_whole_surah')
    locate_control('quran_saved_open')
    locate_control('quran_saved_indicator_1')
    assert read_text('quran_whole_1') == whole
    passed['bookmark_note_and_combined_marks_visible_in_both_modes_after_restart'] = True

    before = prefs('quran_reading')['last']
    tap_control('quran_saved_open')
    locate_control('quran_saved_list')
    locate_control('Meaning-0819', 'text')
    assert prefs('quran_reading')['last'] == before, 'Opening saved list changed reading place'
    shot('saved-surah-list-light')
    tap_control('quran_saved_jump_2')
    assert prefs('quran_reading')['last'] == '1:2'
    read_text('quran_whole_2')
    tap_control('quran_saved_open')
    tap_control('quran_saved_edit_3')
    assert '1:3' in read_text('quran_note_location')
    assert field_value('quran_stop_note') == 'Review-0819'
    tap_control('quran_note_delete')
    tap_control('quran_note_delete_confirm')
    saved = prefs('quran_reading')
    assert 'note_1:3' not in saved and '1:3' in saved['bookmarks']
    jump(1)
    mark = content('quran_saved_indicator_3').lower()
    assert 'заклад' in mark and 'замет' not in mark, mark
    tap_control('quran_saved_open')
    tap_control('quran_saved_jump_3')
    tap_control('quran_selection_bookmark')
    assert '1:3' not in prefs('quran_reading')['bookmarks']
    jump(1)
    assert not present('quran_saved_indicator_3'), 'Removed saved mark remained on page'
    assert read_text('quran_whole_1') == whole, 'Annotation changed Arabic text'
    start('quran')
    tap_control('quran_personal_notes')
    assert 'Meaning-0819' in content('quran_note_1_2')
    passed['saved_list_exact_jump_and_note_deletion_refresh_marks_preserving_bookmark'] = True

    # The new saved list is a menu, not reading time, even when its verse preview
    # is visible. This exercises the newly introduced overlay's session contract.
    put_pref('personal_progress_enabled', 'boolean', 'true')
    put_pref('quran_statistics_v1', 'string', '{}')
    start('quran')
    open_surah(1)
    set_reading_mode(True)
    tap_control('quran_saved_open')
    before = history()
    time.sleep(3)
    assert history() == before, 'Saved-list time counted as reading'
    tap_control('quran_saved_edit_2')
    before = history()
    time.sleep(3)
    assert history() == before, 'Saved-note editor time counted as reading'
    tap_control('quran_note_close')
    passed['saved_list_and_note_editor_do_not_add_reading_time'] = True

if phase in ('all', 'stats', 'remaining'):
    seed_reader(saved=True)
    today = seed_history()
    open_stats()
    assert first_number('quran_stats_today_time') == 2
    assert first_number('quran_stats_today_goal') == 10
    shot('saved-statistics-today-light')
    period(7, 5, 3, 2)
    period(30, 10, 7, 3)
    period(90, 17, 8, 4)
    reveal_above('quran_stats_period_90')
    shot('saved-statistics-period-light')
    assert first_number('quran_stats_unique') == 8
    assert first_number('quran_stats_surahs') == 1
    shot('saved-statistics-overall-light')
    frame_week(from_below=True)
    today_info = content('quran_stats_day_' + today.isoformat())
    yesterday_info = content('quran_stats_day_' + (today - timedelta(days=1)).isoformat())
    assert '2 мин' in today_info and '3 мин' in yesterday_info, (today_info, yesterday_info)
    shot('saved-statistics-week-light')
    reveal_above('quran_goal_open')
    tap_control('quran_goal_open')
    replace_goal('0')
    assert locate_control('quran_goal_save').get('enabled') == 'false'
    assert prefs('quran_reading')['daily_goal_minutes'] == '10'
    replace_goal('15')
    tap_control('quran_goal_save')
    assert prefs('quran_reading')['daily_goal_minutes'] == '15'
    open_stats()
    assert first_number('quran_stats_today_goal') == 15
    passed['statistics_periods_deduplicate_ayahs_and_goal_survives_restart'] = True

    seed_history(empty=True, enabled=False)
    open_stats()
    locate_control('quran_stats_disabled')
    assert not present('quran_stats_empty'), 'Paused statistics showed an active-reading empty hint'
    assert first_number('quran_stats_today_time') == 0
    assert first_number('quran_stats_unique') == 0
    shot('saved-statistics-empty-disabled')
    # The toggle must retain saved annotations and leave empty history intact.
    reveal_above('quran_stats_enable')
    tap_control('quran_stats_enable')
    assert prefs()['personal_progress_enabled'] == 'true'
    assert not present('quran_stats_disabled')
    locate_control('quran_stats_empty')
    shot('saved-statistics-empty-enabled')
    assert prefs('quran_reading')['note_1:2'] == 'Meaning-0819'
    assert '1:1' in prefs('quran_reading')['bookmarks']
    passed['statistics_empty_and_disabled_states_keep_annotations'] = True

if phase in ('all', 'layout', 'remaining'):
    seed_reader(whole=False, saved=True)
    seed_history(enabled=False)
    start('quran')
    tap_control('quran_continue')
    jump(3)
    if present('quran_selection'):
        back()
    locate_control('quran_saved_indicator_3')
    shot('saved-translation-light')
    put_pref('appearance', 'string', 'dark')
    start('quran')
    tap_control('quran_continue')
    if present('quran_selection'):
        back()
    locate_control('quran_saved_indicator_3')
    shot('saved-translation-dark')
    set_reading_mode(True)
    jump(1)
    if present('quran_selection'):
        back()
    locate_control('quran_saved_indicator_1')
    shot('saved-reader-dark')
    tap_control('quran_saved_open')
    locate_control('quran_saved_edit_2')
    shot('saved-surah-list-dark')
    seed_history(enabled=True)
    open_stats()
    shot('saved-statistics-dark')
    frame_week()
    shot('saved-statistics-week-dark')

    shell('wm', 'size', '720x1280')
    shell('wm', 'density', '360')
    shell('settings', 'put', 'system', 'font_scale', '1.5')
    start('quran')
    tap_control('quran_continue')
    locate_control('quran_saved_open')
    shot('saved-reader-320dp-150')
    tap_control('quran_saved_open')
    tap_control('quran_saved_edit_2')
    assert '1:2' in read_text('quran_note_location')
    locate_control('quran_stop_save')
    shot('saved-note-320dp-150')
    open_stats()
    tap_control('quran_goal_open')
    locate_control('quran_daily_goal')
    locate_control('quran_goal_save')
    shot('saved-goal-320dp-150')
    back()
    frame_week()
    shot('saved-statistics-week-320dp-150')
    locate_control('quran_stats_unique')
    locate_control('quran_stats_surahs')
    shot('saved-statistics-overall-320dp-150')
    passed['saved_controls_and_statistics_dark_and_320dp_150_percent'] = True
    shell('settings', 'put', 'system', 'font_scale', '1')
    shell('wm', 'size', '1080x1920')
    shell('wm', 'density', '420')

(OUT / 'quran-saved-passed.json').write_text(json.dumps(passed, indent=2))
