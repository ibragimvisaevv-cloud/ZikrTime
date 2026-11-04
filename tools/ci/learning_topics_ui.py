"""Focused 0.8.21 journeys on a disposable debug emulator.

Run: python3 tools/ci/learning_topics_ui.py [all|training|topics|layout|topics-layout].
Only synthetic emulator preferences are modified. Question choices use the actual
bundled bank; no hard-coded display option indexes or manufactured pass results.
"""
import copy
import traceback

exec(compile(open('tools/ci/features_controls.py').read().split('mode = sys.argv[1]')[0], 'features_helpers', 'exec'))

MODE = sys.argv[1] if len(sys.argv) > 1 else 'all'
assert MODE in {'all', 'training', 'topics', 'layout', 'topics-layout'}, MODE
OUT.mkdir(exist_ok=True)
(OUT / 'screens').mkdir(exist_ok=True)
BANK = {q['id']: q for q in json.loads(Path('app/src/main/assets/quiz/ru.json').read_text())['questions']}
REPORT = OUT / 'learning-topics-passed.json'
report = {'mode': MODE, 'groups': {}, 'screens': []}
started_at = time.monotonic()


def start(route, lang='ru'):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_screen', route,
          '--es', 'qa_lang', lang, '--ez', 'qa_onboarded', 'true')
    time.sleep(1.2)
    if route == 'quran':
        wait_for('quran_index_list')


def save_report():
    report['elapsed_seconds'] = round(time.monotonic() - started_at, 1)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2))


def passed(name, evidence):
    report['groups'][name] = evidence
    save_report()
    print('Passed:', name, flush=True)


def shot(name):
    root = ui()
    screenshot(name)
    (OUT / (name + '.xml')).write_bytes(ET.tostring(root))
    report['screens'].append({'image': name + '.png', 'captured': True, 'visually_reviewed': False})
    save_report()


def wait_for(identifier, seconds=30):
    deadline = time.monotonic() + seconds
    while True:
        root = ui()
        if find_control(root, identifier) is not None:
            return
        errors = [n.get('text', '') for n in root.iter('node')
                  if 'Unable to verify' in n.get('text', '') or 'Не удалось проверить' in n.get('text', '')]
        assert not errors, errors
        if time.monotonic() >= deadline:
            raise AssertionError('UI did not become ready: ' + identifier)
        time.sleep(.4)


def has(identifier):
    return find_control(ui(), identifier) is not None


def text_of(identifier):
    node = locate_control(identifier)
    return ' '.join(n.get('text', '') for n in node.iter('node') if n.get('text'))


def update_fixture(values=(), remove=(), name='zikrtime'):
    root = prefs_tree(name)
    values = dict(values)
    for node in list(root):
        if node.attrib['name'] in values or node.attrib['name'] in remove:
            root.remove(node)
    for key, (kind, value) in values.items():
        node = ET.SubElement(root, kind, {'name': key})
        if kind == 'string':
            node.text = str(value)
        elif kind == 'set':
            for entry in value:
                ET.SubElement(node, 'string').text = entry
        else:
            node.set('value', str(value))
    write_tree(root, name)


def back():
    shell('input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(.5)


def tap_above_bottom_bar(identifier):
    # Knowledge's floating navigation can cover the lower part of a scroll item.
    # Reveal the entry above it before tapping instead of hitting another tab.
    for _ in range(8):
        tree = ui()
        node = find_control(tree, identifier)
        vl, vt, vr, vb = map(int, re.findall(r'-?\d+', next(tree.iter('node')).get('bounds')))
        if node is not None:
            left, top, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
            if top >= vt and bottom <= vt + (vb - vt) * .78 and bottom - top >= 32:
                shell('input', 'tap', (left + right) // 2, (top + bottom) // 2)
                time.sleep(.7)
                return
        shell('input', 'swipe', vl + 8, int(vt + (vb - vt) * .72),
              vl + 8, int(vt + (vb - vt) * .34), 300)
    raise AssertionError('Entry could not be revealed above navigation: ' + identifier)


def replace_text(identifier, value):
    tap_control(identifier)
    shell('input', 'keycombination', '113', '29')
    shell('input', 'keyevent', 'KEYCODE_DEL')
    if value:
        shell('input', 'text', str(value))
    back()


def session(scope='normal'):
    return learning().get('sessions', {}).get(scope)


def assert_session(level, scope='training', category=None):
    current = session(scope)
    assert current and current.get('level') == level, (scope, current)
    assert current['ids'] and len(set(current['ids'])) == len(current['ids'])
    if category is not None:
        assert current.get('category') == category, current
    for question_id in current['ids']:
        q = BANK[question_id]
        assert q.get('difficulty', 1) == level, ('wrong level', q['id'])
        if current.get('category'):
            assert q['cat'] == current['category'], ('wrong category', q['id'])
    return current


def answer(scope='training', correct=True):
    current = session(scope)
    assert current['stage'] == 'QUIZ' and 'selected' not in current, current
    q = BANK[current['ids'][current['index']]]
    canonical = q['answer'] if correct else (q['answer'] + 1) % len(q['options'])
    tap_control(q['options'][canonical], 'text')
    updated = session(scope)
    assert updated['selected'] == canonical, ('answer not persisted', updated)
    locate_control('learning_feedback')
    return q


def bonuses():
    values = prefs()
    return {key: value for key, value in values.items()
            if key.startswith('bonus_') or key == 'quiz_reward_guard_v1'}


def choose_level(level):
    tap_control('learning_level_' + str(level))
    tap_control('learning_level_confirm')
    assert prefs().get('learning_level_v1') == str(level)


def open_training():
    start('training')
    locate_control('training_dashboard')


def make_history():
    # Deliberately different learning states exercise ranking, version filtering
    # and isolation. This is a synthetic test profile, never imported user history.
    now = int(shell('date', '+%s').strip()) * 1000
    candidates = [q for q in BANK.values() if q.get('difficulty', 1) == 2 and q['cat'] == 'quran'][:7]
    assert len(candidates) == 7
    history = {}
    for index, q in enumerate(candidates):
        history[q['id']] = {
            'v': q.get('contentVersion', 1), 'ok': index >= 2,
            'at': now - (index + 2) * 86400000, 'stage': 0 if index < 2 else (1 if index < 5 else 4),
            'due': now - 86400000 if index < 5 else now + 10 * 86400000,
            'shown': now - 2 * 86400000, 'event': 'ci-training-seed-' + str(index),
        }
    stale = candidates[-1]['id']
    history[stale]['v'] += 99
    foreign = next(q for q in BANK.values() if q.get('difficulty', 1) == 1)
    history[foreign['id']] = {'v': foreign.get('contentVersion', 1), 'ok': False,
                              'at': now - 86400000, 'stage': 0, 'due': now, 'shown': 0, 'event': 'ci-other-level'}
    update_fixture({'learning_state_v1': ('string', json.dumps({'schema': 1, 'questions': history})),
                    'learning_level_v1': ('string', '2')})
    return candidates, foreign['id'], stale


def training_replacement(pending, normal_before):
    # Synthetic companions make preservation observable for both independent
    # learning scopes and a nonempty reward ledger. Training itself was started
    # and answered through the UI, not manufactured in preferences.
    now = int(shell('date', '+%s').strip()) * 1000
    today = shell('date', '+%F').strip()
    other = next(q for q in BANK.values()
                 if q.get('difficulty', 1) == 2 and q['cat'] != pending['category'])
    profile = copy.deepcopy(learning())
    profile['questions'][other['id']] = {
        'v': other.get('contentVersion', 1), 'ok': False,
        'at': now - 2 * 86400000, 'stage': 0, 'due': now - 86400000,
        'shown': now - 2 * 86400000, 'event': 'ci-other-topic-answer',
    }
    review_ids = pending['ids'][-2:]
    review = {
        'id': 'ci-preserved-review-0821', 'category': '',
        'ids': review_ids,
        'versions': {qid: BANK[qid].get('contentVersion', 1) for qid in review_ids},
        'stage': 'QUIZ', 'card': 0, 'index': 0, 'level': 2,
        'selected': BANK[review_ids[0]]['answer'], 'correct': [review_ids[0]],
        'date': today, 'added': 0,
    }
    profile.setdefault('sessions', {})['normal'] = copy.deepcopy(normal_before)
    profile['sessions']['review'] = review
    award_id = 'ci-preserved-award-0821'
    guard = {
        'sessions': [award_id],
        'questions': {qid: now for qid in pending['ids'][:3]},
        'day': today, 'time': now, 'awards': {award_id: 10},
    }
    update_fixture({
        'learning_state_v1': ('string', json.dumps(profile)),
        'quiz_reward_guard_v1': ('string', json.dumps(guard)),
        'bonus_' + today: ('int', '10'),
    })
    open_training()
    history_before = copy.deepcopy(learning()['questions'])
    reward_before = bonuses()
    normal_saved = copy.deepcopy(session('normal'))
    review_saved = copy.deepcopy(session('review'))
    assert session('training') == pending and 'selected' in pending
    assert normal_saved == normal_before and review_saved == review
    assert json.loads(reward_before['quiz_reward_guard_v1'])['awards'][award_id] == 10
    assert reward_before['bonus_' + today] == '10'

    tap_control('training_start_' + other['cat'])
    locate_control('training_replace_cancel')
    shot('learn-training-replace-dialog')
    tap_control('training_replace_cancel')
    locate_control('training_dashboard')
    assert session('training') == pending, 'Cancel replaced the answered training round'
    assert learning()['questions'] == history_before
    assert session('normal') == normal_saved and session('review') == review_saved
    assert bonuses() == reward_before, 'Cancel changed the reward ledger'

    tap_control('training_start_' + other['cat'])
    tap_control('training_replace_confirm')
    locate_control('learning_question')
    replacement = assert_session(2, category=other['cat'])
    assert replacement['id'] != pending['id'], 'Confirm did not replace the previous training'
    assert replacement['ids'] == [other['id']], replacement
    assert replacement['index'] == 0 and 'selected' not in replacement
    assert learning()['questions'] == history_before, 'Replacing a round changed answer history'
    assert session('normal') == normal_saved, 'Replacing training changed the normal quiz'
    assert session('review') == review_saved, 'Replacing training changed interval review'
    assert bonuses() == reward_before, 'Replacing training changed minutes or protected receipts'
    passed('training_replacement_dialog', {
        'from_category': pending['category'], 'to_category': other['cat'],
        'old_answer_preserved_on_cancel': True, 'confirmed_only_training_replaced': True,
        'normal_and_review_sessions_preserved': True, 'answer_history_preserved': True,
        'nonempty_reward_ledger_and_ten_bonus_minutes_preserved': True,
    })
    return copy.deepcopy(replacement), history_before, reward_before


def training_journey():
    update_fixture({'learning_state_v1': ('string', '{}'), 'appearance': ('string', 'light')},
                   remove=('learning_level_v1',))
    start('training')
    assert session('training') is None
    choose_level(2)
    locate_control('training_empty')
    assert not has('training_start_all'), 'No-history profile was labelled as a weak topic'
    shot('learn-training-empty')

    # Create a genuine error through the same UI used in a normal reward round.
    start('quiz')
    assert_session(2, 'normal')
    tap_control('learning_skip')
    missed = answer('normal', correct=False)
    normal_before = copy.deepcopy(session('normal'))
    reward_before = bonuses()
    start('knowledge')
    tap_above_bottom_bar('training_overview_open')
    locate_control('training_dashboard')
    assert '1' in text_of('training_errors_' + missed['cat'])
    shot('learn-training-dashboard-light')
    tap_control('training_start_' + missed['cat'])
    focused = assert_session(2, category=missed['cat'])
    assert focused['ids'] == [missed['id']], ('Unanswered filler in focused training', focused)
    answer()
    answered = copy.deepcopy(session('training'))
    start('training')
    tap_control('training_resume')
    assert session('training') == answered, 'Training answer/order changed after process restart'
    shot('learn-training-resumed-feedback')
    tap_control('learning_finish')
    locate_control('training_result')
    completed = session('training')
    assert completed['stage'] == 'RESULT' and completed['added'] == 0
    assert session('normal') == normal_before, 'Focused training destroyed the unfinished normal round'
    assert bonuses() == reward_before, 'Practice changed bonus minutes or the protected ledger'
    # Configuration recreation must preserve the result receipt and no-bonus behavior.
    shell('wm', 'density', '421')
    time.sleep(.7)
    locate_control('training_result')
    assert session('training') == completed and bonuses() == reward_before
    shell('wm', 'density', '420')
    time.sleep(.7)
    shot('learn-training-result')
    passed('real_error_focus_resume_and_no_bonus', {
        'initial_level_required': True, 'no_history_state': True, 'question_id': missed['id'],
        'answered_only_pool': True, 'pending_answer_survived_process_restart': True,
        'normal_round_preserved': True, 'training_minutes': 0, 'result_recreation_preserved': True,
    })

    candidates, foreign, stale = make_history()
    reward_before = bonuses()
    open_training()
    tap_control('training_start_quran')
    ranked = assert_session(2, category='quran')
    assert len(ranked['ids']) == 5
    assert set(ranked['ids'][:2]) == {q['id'] for q in candidates[:2]}, ranked
    assert foreign not in ranked['ids'] and stale not in ranked['ids']
    for index in range(5):
        answer(correct=index != 0)
        tap_control('learning_finish' if index == 4 else 'learning_next')
        if index < 4:
            assert session('training')['index'] == index + 1
    locate_control('training_result')
    assert session('training')['added'] == 0 and bonuses() == reward_before

    # Answer another focused round, exercise category replacement, then both
    # cancel and confirm in the shared level control. History and rewards survive.
    tap_control('training_again')
    answer(correct=False)
    pending = copy.deepcopy(session('training'))
    assert pending and pending['stage'] == 'QUIZ' and 'selected' in pending
    pending, history, reward_before = training_replacement(pending, normal_before)
    start('knowledge')
    tap_control('learning_level_change')
    tap_control('learning_level_3')
    tap_control('learning_level_cancel')
    assert prefs()['learning_level_v1'] == '2'
    assert session('training') == pending
    tap_control('learning_level_change')
    choose_level(3)
    assert not learning().get('sessions'), 'Old-level sessions survived confirmed change'
    assert learning()['questions'] == history and bonuses() == reward_before
    open_training()
    locate_control('training_empty')
    assert not has('training_start_all'), 'Other-level history leaked into the selected level'
    shot('learn-training-selected-level-empty')
    passed('ranking_full_round_and_level_isolation', {
        'selected_level': 2, 'ordered_question_ids': ranked['ids'],
        'wrong_answers_first': True, 'five_question_round_completed': True,
        'stale_version_and_foreign_level_excluded': True,
        'cancel_kept_round': True, 'confirmed_change_kept_history_and_reward_guard': True,
    })


def open_topics(lang='ru'):
    start('quran', lang)
    tap_control('quran_topics')
    locate_control('quran_topics_search')


def saved_marks():
    return {key: value for key, value in prefs('quran_reading').items()
            if key in {'bookmarks', 'stop'} or key.startswith('note_')}


def topics_journey():
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    start('quran')
    tap_control('quran_surah_1')
    locate_control('quran_tools')  # Creates quran_reading through the real reader path.
    update_fixture({
        'whole_surah': ('boolean', 'false'),
        'bookmarks': ('set', ['1:1', '2:153']),
        'stop': ('string', '1:1'),
        'note_2:153': ('string', 'Synthetic reading note 0821'),
    }, name='quran_reading')
    expected_marks = saved_marks()
    open_topics()
    shot('learn-topics-catalog-light')
    tap_control('quran_topic_patience')
    locate_control('quran_topic_detail_patience')
    locate_control('quran_topic_source_patience_2_153_157')
    shot('learn-topics-patience-light')
    tap_control('quran_topic_passage_patience_2_153_157')
    assert prefs('quran_reading')['last'] == '2:153', 'Topic opened a neighboring ayah'
    # A saved note remains visible when entering through the new path.
    assert 'Synthetic reading note 0821' in text_of('quran_saved_note_preview_153')
    shot('learn-topic-passage-bookmark-note')
    assert saved_marks() == expected_marks
    tap_control('quran_surahs')
    locate_control('quran_topics_detail_list')
    assert has('quran_topic_source_patience_2_153_157'), 'Topic scroll position was lost'
    back()
    locate_control('quran_topics_search')
    assert saved_marks() == expected_marks

    # English permits reliable keyboard input with ADB. Russian visuals above
    # are captured independently; the test does not pretend to type Cyrillic.
    open_topics('en')
    replace_text('quran_topics_search', 'no-such-topic-0821')
    locate_control('quran_topics_empty')
    assert not has('quran_topic_patience')
    shot('learn-topics-search-empty')
    replace_text('quran_topics_search', 'patience')
    tap_control('quran_topic_patience')
    tap_control('quran_topic_passage_patience_2_153_157')
    assert prefs('quran_reading')['last'] == '2:153'
    tap_control('quran_surahs')
    locate_control('quran_topics_detail_list')
    # Recreation preserves the topic and its search query, not merely a cold
    # launch's default selection.
    shell('wm', 'density', '421')
    time.sleep(.7)
    locate_control('quran_topics_detail_list')
    shell('wm', 'density', '420')
    time.sleep(.7)
    back()
    assert 'patience' in text_of('quran_topics_search').lower()
    tap_control('quran_topics_search_clear')
    locate_control('quran_topic_gratitude')
    assert saved_marks() == expected_marks
    passed('quran_topics_offline_search_navigation', {
        'offline': True, 'topic': 'patience', 'passage': '2:153-157',
        'opened_exact_ayah': '2:153', 'empty_search_and_clear': True,
        'reader_back_kept_topic': True, 'recreation_kept_topic_and_query': True,
        'bookmarks_notes_and_stop_preserved': True,
    })


def compact_dark():
    make_history()
    update_fixture({'appearance': ('string', 'dark')})
    open_training()
    shot('learn-training-dashboard-dark')
    open_topics()
    shot('learn-topics-catalog-dark')
    shell('wm', 'size', '720x1280')
    shell('wm', 'density', '360')
    shell('settings', 'put', 'system', 'font_scale', '1.5')
    try:
        open_training()
        locate_control('training_start_quran')
        shot('learn-training-dashboard-dark-320dp-150')
        tap_control('training_start_quran')
        locate_control('learning_answer_3')
        shot('learn-training-question-dark-320dp-150')
        answer(correct=False)
        locate_control('learning_next')
        shot('learn-training-feedback-dark-320dp-150')
        open_topics()
        tap_control('quran_topic_patience')
        locate_control('quran_topic_source_patience_2_153_157')
        shot('learn-topics-detail-dark-320dp-150')
        tap_control('quran_topic_passage_patience_2_153_157')
        assert prefs('quran_reading')['last'] == '2:153'
        locate_control('quran_tools')
        shot('learn-topics-reader-dark-320dp-150')
        tap_control('quran_surahs')
        locate_control('quran_topics_detail_list')
        back()
        locate_control('quran_topics_search')
        shot('learn-topics-search-dark-320dp-150')
        passed('compact_dark_large_text', {
            'width_dp': 320, 'font_scale': 1.5, 'theme': 'dark',
            'dashboard_answers_feedback_sources_and_reader_back_reachable': True,
            'screens_require_visual_review': True,
        })
    finally:
        shell('settings', 'put', 'system', 'font_scale', '1')
        shell('wm', 'size', '1080x1920')
        shell('wm', 'density', '420')


try:
    start('home')
    update_fixture({'online_times': ('boolean', 'false')})
    counts = {level: sum(q.get('difficulty', 1) == level for q in BANK.values()) for level in (1, 2, 3)}
    assert counts == {1: 150, 2: 150, 3: 150}, counts
    report['question_counts'] = counts
    if MODE in {'all', 'training'}:
        training_journey()
    if MODE in {'all', 'topics', 'topics-layout'}:
        topics_journey()
    if MODE in {'all', 'layout', 'topics-layout'}:
        compact_dark()
    save_report()
except Exception:
    (OUT / 'learning-topics-failure.txt').write_text(traceback.format_exc())
    save_report()
    try:
        shot('learn-failure')
    except Exception:
        pass
    raise
