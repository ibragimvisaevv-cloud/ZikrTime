"""Real reading and audio-selection journeys on a disposable Android emulator.

These assertions inspect actual UI and restart the process to test persistence.
Audio preview is checked for playback state; this is not a human listening test.
"""
exec(compile(open('tools/ci/features_controls.py').read().split('mode = sys.argv[1]')[0], 'helpers', 'exec'))
OUT.mkdir(exist_ok=True)
(OUT / 'screens').mkdir(exist_ok=True)


def record_failure(kind, value, trace):
    import traceback
    (OUT / 'quran-failure.txt').write_text(''.join(traceback.format_exception(kind, value, trace)))
    try:
        screenshot('quran-failure')
    except Exception:
        pass
    sys.__excepthook__(kind, value, trace)


sys.excepthook = record_failure


def start(route, lang='ru'):
    started = time.monotonic()
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity',
          '--es', 'qa_screen', route, '--es', 'qa_lang', lang,
          '--ez', 'qa_onboarded', 'true')
    time.sleep(2)
    if route == 'quran':
        # A cold process verifies and parses all bundled source bytes. Capture
        # the completed reader, not its legitimate loading indicator.
        deadline = time.monotonic() + 30
        while True:
            root = ui()
            if any('Не удалось проверить' in node.get('text', '')
                   or 'Unable to verify' in node.get('text', '') for node in root.iter('node')):
                raise AssertionError('Quran source integrity error is visible on screen')
            if find_control(root, 'quran_search') is not None:
                break
            if time.monotonic() >= deadline:
                raise AssertionError('Quran index did not finish loading within 30 seconds')
            time.sleep(.5)
        timing = OUT / 'quran-launch-times.json'
        measurements = json.loads(timing.read_text()) if timing.exists() else []
        measurements.append({'process_launch_to_ready_seconds': round(time.monotonic() - started, 2)})
        timing.write_text(json.dumps(measurements, indent=2))


def shot(name):
    tree = ui()
    screenshot(name)
    (OUT / (name + '.xml')).write_bytes(ET.tostring(tree))
    return tree


def back():
    shell('input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(.5)


def top():
    # Reader lists are inset from the screen edges. Swipe inside the actual
    # scroll owner; a swipe at x=6 would miss the list entirely.
    for _ in range(6):
        tree = ui()
        scrolls = []
        for node in tree.iter('node'):
            if node.get('scrollable') != 'true':
                continue
            left, upper, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
            if bottom - upper > 120:
                scrolls.append((bottom - upper, left, upper, right, bottom))
        if not scrolls:
            return
        _, left, upper, right, bottom = max(scrolls)
        shell('input', 'swipe', left + 6, int(upper + (bottom - upper) * .25),
              left + 6, int(upper + (bottom - upper) * .85), 180)


def replace_text(identifier, value):
    tap_control(identifier)
    shell('input', 'keycombination', '113', '29')  # Ctrl+A selects existing text.
    shell('input', 'keyevent', 'KEYCODE_DEL')
    if value:
        shell('input', 'text', str(value))
    back()  # dismiss keyboard


def present(identifier):
    return find_control(ui(), identifier) is not None


def read_text(identifier):
    node = locate_control(identifier)
    text = ' '.join(n.get('text', '') for n in node.iter('node')).strip()
    assert text, 'Empty content: ' + identifier
    return text


def open_surah(number):
    tap_control('quran_surahs')
    replace_text('quran_search', str(number))
    tap_control('quran_surah_' + str(number))


def jump(number):
    # These controls are fixed above the list, so a late ayah never requires
    # scrolling hundreds of cards back to the beginning.
    replace_text('quran_ayah_input', str(number))
    tap_control('quran_go')


def check_ayah(number, label):
    # Separate nodes ensure the reader hasn't accidentally omitted one layer.
    arabic = read_text('quran_arabic_' + str(number))
    assert any('\u0600' <= c <= '\u06ff' for c in arabic), label
    shot(label + '-arabic')
    translation = read_text('quran_translation_' + str(number))
    assert translation != arabic, label
    assert any('\u0400' <= c <= '\u04ff' for c in translation), label
    tree = shot(label)
    assert not any(any(disallowed in n.get('resource-id', '')
                       for disallowed in ['quran_play', 'quran_transcription', 'quran_transliteration'])
                   for n in tree.iter('node'))
    return arabic


passed = {}
start('home')
put_pref('online_times', 'boolean', 'false')
put_pref('appearance', 'string', 'light')

# Offline from the first reader launch: the packaged text must be sufficient.
shell('svc', 'wifi', 'disable')
shell('svc', 'data', 'disable')
start('quran')
shot('quran-surahs-light')
locate_control('quran_surah_1')
open_surah(1)
first_arabic = check_ayah(1, 'quran-1-1-light')
top()
tap_control('quran_sources')
sources = ET.tostring(shot('quran-sources'), encoding='unicode')
assert 'Tanzil' in sources, 'Arabic source attribution missing'
assert 'QuranEnc' in sources, 'Translation source attribution missing'
back()
locate_control('quran_arabic_1')
passed['offline_reader_and_sources'] = True

# Bookmark and continue survive a complete process restart.
tap_control('quran_bookmark_1')
start('quran')
tap_control('quran_bookmarks')
tap_control('quran_saved_1_1')
assert read_text('quran_arabic_1') == first_arabic
shot('quran-bookmark-restored')
top()
tap_control('quran_surahs')
open_surah(2)
jump(286)
long_arabic = check_ayah(286, 'quran-2-286-light')
start('quran')
tap_control('quran_continue')
assert read_text('quran_arabic_286') == long_arabic
passed['bookmark_and_reading_resume'] = True

# Reject an out-of-range number instead of showing an unrelated ayah.
jump(287)
locate_control('quran_jump_error')
shot('quran-invalid-ayah')
passed['ayah_bounds'] = True
top()
tap_control('quran_surahs')
open_surah(114)
jump(6)
assert prefs('quran_reading')['last'] == '114:6', 'Short last ayah jump was replaced by its neighbor'
check_ayah(6, 'quran-114-6-light')
passed['first_longest_last_surahs'] = True

# A second launch renders the same persisted content in the dark palette.
put_pref('appearance', 'string', 'dark')
start('quran')
shot('quran-surahs-dark')
tap_control('quran_continue')
assert prefs('quran_reading')['last'] == '114:6', 'Restoring a short last ayah changed the saved location'
check_ayah(6, 'quran-114-6-dark')
passed['dark_theme'] = True

# Readability and controls at 320dp width / 150% type size.
shell('wm', 'size', '720x1280')
shell('wm', 'density', '360')
shell('settings', 'put', 'system', 'font_scale', '1.5')
start('quran')
open_surah(1)
check_ayah(1, 'quran-large-text')
top()
locate_control('quran_ayah_input')
locate_control('quran_go')
shot('quran-large-text-controls')
shell('settings', 'put', 'system', 'font_scale', '1')
shell('wm', 'size', '1080x1920')
shell('wm', 'density', '420')
passed['small_screen_large_text'] = True

# Navigation smoke: reader access must not remove existing learning screens.
for route in ['knowledge', 'azkar']:
    start(route)
    shot(route + '-regression')
start('knowledge')
tap_control('quran_open')
locate_control('quran_search')
back()
locate_control('quran_open')
passed['knowledge_azkar_regression'] = True

# The audio agent adds stable named selection / preview semantics. Recorded
# names are collected here so the report stays meaningful if ordering changes.
put_pref('alarm_on', 'boolean', 'true')
start('alarm')
stopped_descriptions = {}


def select_voice(identifier):
    for _ in range(4):
        row = locate_control('adhan_select_' + identifier)
        radio = next((node for node in row.iter('node') if node.get('class') == 'android.widget.RadioButton'
                      or node.get('checkable') == 'true'), None)
        assert radio is not None, 'Missing voice selection control: ' + identifier
        left, upper, right, bottom = map(int, re.findall(r'-?\d+', radio.get('bounds')))
        rl, rt, rr, rb = map(int, re.findall(r'-?\d+', row.get('bounds')))
        left, upper, right, bottom = max(left, rl), max(upper, rt), min(right, rr), min(bottom, rb)
        if right - left >= 12 and bottom - upper >= 12:
            shell('input', 'tap', (left + right) // 2, (upper + bottom) // 2)
            time.sleep(.5)
            return
        shell('input', 'swipe', rl + 6, rb - 20, rl + 6, max(rt, rb - 300), 180)
    raise AssertionError('Voice radio button remains clipped: ' + identifier)


for voice in ['adhan_madinah', 'adhan_makkah', 'adhan_fajr']:
    select_voice(voice)
    assert prefs()['alarm_sound'] == voice
    preview_id = 'adhan_preview_' + voice
    stopped_description = locate_control(preview_id).get('content-desc', '')
    stopped_descriptions[voice] = stopped_description
    assert stopped_description, 'Missing playback accessibility label'
    tap_control(preview_id)
    for attempt in range(6):
        playing_description = locate_control(preview_id).get('content-desc', '')
        if playing_description and playing_description != stopped_description:
            break
        time.sleep(.5)
    else:
        raise AssertionError('Audio preview did not start: ' + voice)
    shot(voice + '-playing')
    tap_control(preview_id)
    assert locate_control(preview_id).get('content-desc') == stopped_description
start('alarm')
assert prefs()['alarm_sound'] == 'adhan_fajr'
tap_control('adhan_preview_adhan_madinah')
shell('input', 'keyevent', 'KEYCODE_HOME')
time.sleep(1)
shell('am', 'start', '-W', '-n', PKG + '/.MainActivity')
time.sleep(1)
assert locate_control('adhan_preview_adhan_madinah').get('content-desc') == stopped_descriptions['adhan_madinah']
shot('adhan-alarm-picker')
passed['three_new_audio_previews_and_selection_persistence'] = True
passed['audio_preview_stops_in_background'] = True

# A dawn-specific call must not be selected for every daily prayer.
put_pref('notify', 'boolean', 'true')
put_pref('notify_adhan', 'boolean', 'true')
start('prayer_settings')
select_voice('adhan_makkah')
assert prefs()['notify_voice'] == 'adhan_makkah'
shot('adhan-prayer-picker')
for _ in range(8):
    assert find_control(ui(), 'adhan_select_adhan_fajr') is None
    shell('input', 'swipe', 10, 1500, 10, 450, 200)
passed['fajr_voice_not_in_daily_prayer_picker'] = True

(OUT / 'quran-passed.json').write_text(json.dumps(passed, indent=2))
