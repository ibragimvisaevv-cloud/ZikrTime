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
    (OUT / 'quran-passed.json').write_text(json.dumps(globals().get('passed', {}), indent=2))
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
        wait_for_quran_index(started)


def wait_for_quran_index(started=None):
    # A cold process verifies and parses all bundled source bytes. Capture
    # the completed reader, not its legitimate loading indicator.
    started = started if started is not None else time.monotonic()
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
    measurements.append({'launch_or_navigation_to_ready_seconds': round(time.monotonic() - started, 2)})
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
    locate_control(identifier)
    for _ in range(8):
        tree = ui()
        node = find_control(tree, identifier)
        if node is None:
            locate_control(identifier)
            continue
        text = ' '.join(n.get('text', '') for n in node.iter('node')).strip()
        assert text, 'Empty content: ' + identifier
        parents = {child: parent for parent in tree.iter() for child in parent}
        owner = parents.get(node)
        while owner is not None and owner.get('scrollable') != 'true':
            owner = parents.get(owner)
        if owner is None:
            return text
        left, upper, right, bottom = map(int, re.findall(r'-?\d+', owner.get('bounds')))
        _, text_top, _, text_bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
        # A semantic Text node can exist with only a 16px strip on screen.
        # Bring its beginning away from the lower edge before capturing it.
        # A complete short final line with clear space below is already legible
        # and must not be scrolled pointlessly at the end of its surah.
        if text_bottom - text_top >= 48 and (text_top <= bottom - 180 or text_bottom <= bottom - 48):
            return text
        shell('input', 'swipe', left + 6, int(upper + (bottom - upper) * .8),
              left + 6, int(upper + (bottom - upper) * .25), 250)
    raise AssertionError('Text remained clipped near the bottom of the reader: ' + identifier)


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


def open_quran_from_knowledge():
    # The floating bottom bar overlays scrollable content. Bring the complete
    # Quran card above that bar before tapping; its obscured center can hit Shop.
    for _ in range(6):
        tree = ui()
        card = find_control(tree, 'quran_open')
        _, _, width, height = map(int, re.findall(r'-?\d+', next(tree.iter('node')).get('bounds')))
        if card is not None:
            left, upper, right, bottom = map(int, re.findall(r'-?\d+', card.get('bounds')))
            if (upper >= height * .15 and bottom <= height * .78
                    and (upper + bottom) / 2 <= height * .60 and bottom - upper >= 40):
                shot('quran-entry-visible')
                shell('input', 'tap', (left + right) // 2, (upper + bottom) // 2)
                wait_for_quran_index()
                shot('quran-opened-from-knowledge')
                return
        shell('input', 'swipe', int(width * .1), int(height * .72),
              int(width * .1), int(height * .42), 250)
    raise AssertionError('Quran entry could not be placed above the bottom navigation')


class Checkpoints(dict):
    def __setitem__(self, key, value):
        super().__setitem__(key, value)
        (OUT / 'quran-passed.json').write_text(json.dumps(self, indent=2))
        print('Passed:', key, flush=True)


mode = sys.argv[1] if len(sys.argv) > 1 else 'all'
assert mode in ('all', 'reading', 'navigation-adhan', 'layout'), mode
passed = Checkpoints()
start('home')
put_pref('online_times', 'boolean', 'false')
put_pref('appearance', 'string', 'light')


if mode in ('all', 'layout'):
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    start('quran')
    open_surah(1)
    first_arabic = read_text('quran_arabic_1')
    shot('reading-verse-light')
    tap_control('quran_read_mode')
    whole = read_text('quran_whole_1')
    assert whole.startswith(first_arabic)
    assert '۝١' in whole and '۝٧' in whole, 'Whole Al-Fatiha is incomplete'
    assert not present('quran_translation_1'), 'Translation interrupts whole-surah mode'
    shot('reading-whole-fatiha-light')
    passed['whole_surah_without_cards_or_interleaved_translation'] = True
    start('quran')
    tap_control('quran_continue')
    assert present('quran_whole_surah')
    assert read_text('quran_whole_1') == whole
    passed['mode_and_text_survive_process_restart'] = True

    # Both directions and a long surah: scroll/jump must use verse numbers,
    # not layout-block indexes, and still allow bookmarking in verse mode.
    tap_control('quran_read_mode')
    assert read_text('quran_arabic_1') == first_arabic
    tap_control('quran_bookmark_1')
    open_surah(2)
    jump(255)
    shot('reading-ayat-kursi-spaced')
    tap_control('quran_read_mode')
    assert present('quran_whole_surah')
    jump(286)
    end = read_text('quran_whole_281')
    assert '۝٢٨٦' in end, 'Last verse of Al-Baqarah missing'
    assert prefs('quran_reading')['last'] == '2:286'
    shot('reading-whole-baqarah-end')
    start('quran')
    tap_control('quran_continue')
    assert present('quran_whole_281')
    assert prefs('quran_reading')['last'] == '2:286'
    tap_control('quran_read_mode')
    jump(286)
    read_text('quran_translation_286')
    jump(287)
    assert present('quran_jump_error')
    passed['long_surah_end_resume_translation_and_bounds'] = True

    put_pref('appearance', 'string', 'dark')
    start('quran')
    open_surah(114)
    tap_control('quran_read_mode')
    last_surah = read_text('quran_whole_1')
    assert '۝٦' in last_surah
    shot('reading-whole-nas-dark')
    passed['last_surah_dark_theme'] = True

    shell('wm', 'size', '720x1280')
    shell('wm', 'density', '360')
    shell('settings', 'put', 'system', 'font_scale', '1.5')
    start('quran')
    open_surah(1)
    read_text('quran_whole_1')
    shot('reading-whole-320dp-150')
    tap_control('quran_read_mode')
    read_text('quran_arabic_1')
    shot('reading-verse-320dp-150')
    locate_control('quran_go')
    locate_control('quran_read_mode')
    passed['both_modes_320dp_150_percent_text'] = True
    shell('settings', 'put', 'system', 'font_scale', '1')
    shell('wm', 'size', '1080x1920')
    shell('wm', 'density', '420')

    for theme in ['light', 'dark']:
        put_pref('appearance', 'string', theme)
        start('azkar')
        read_text('azkar_arabic')
        shot('reading-azkar-' + theme)
    # Shared Arabic typography: a small representative smoke, not a full audit.
    for route in ['tasbih', 'quiz']:
        start(route)
        shot('reading-' + route)
    start('quran', 'en')
    open_surah(1)
    tap_control('quran_read_mode')
    read_text('quran_whole_1')
    shot('reading-whole-english')
    passed['azkar_and_shared_arabic_smoke'] = True


if mode in ('all', 'reading'):
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

if mode in ('all', 'navigation-adhan'):
    # Navigation smoke: reader access must not remove existing learning screens.
    for route in ['knowledge', 'azkar']:
        start(route)
        shot(route + '-regression')
    start('knowledge')
    open_quran_from_knowledge()
    back()
    locate_control('quran_open')
    passed['knowledge_azkar_regression'] = True

    # The audio agent adds stable named selection / preview semantics. Recorded
    # names are collected here so the report stays meaningful if ordering changes.
    put_pref('alarm_on', 'boolean', 'true')
    start('alarm')
    stopped_descriptions = {}


    def select_voice(identifier):
        for _ in range(8):
            row = locate_control('adhan_select_' + identifier)
            radio = next((node for node in row.iter('node') if node.get('class') == 'android.widget.RadioButton'
                          or node.get('checkable') == 'true'), None)
            if radio is not None:
                left, upper, right, bottom = map(int, re.findall(r'-?\d+', radio.get('bounds')))
                rl, rt, rr, rb = map(int, re.findall(r'-?\d+', row.get('bounds')))
                left, upper, right, bottom = max(left, rl), max(upper, rt), min(right, rr), min(bottom, rb)
                if right - left >= 40 and bottom - upper >= 40:
                    shell('input', 'tap', (left + right) // 2, (upper + bottom) // 2)
                    time.sleep(.5)
                    return
            # A visible sliver of a row does not yet expose its radio child.
            # Scroll inside the screen's owner, away from the system gesture bar.
            tree = ui()
            owners = [node for node in tree.iter('node') if node.get('scrollable') == 'true']
            assert owners, 'Voice row is clipped without a scroll owner: ' + identifier
            bounds = [tuple(map(int, re.findall(r'-?\d+', node.get('bounds')))) for node in owners]
            left, upper, right, bottom = max(bounds, key=lambda b: b[3] - b[1])
            shell('input', 'swipe', left + 6, int(upper + (bottom - upper) * .8),
                  left + 6, int(upper + (bottom - upper) * .3), 250)
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
        passed['audio_preview_' + voice] = True
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
