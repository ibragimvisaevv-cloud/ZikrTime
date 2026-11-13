"""Focused 0.8.20 UI checks on a disposable debug emulator.

Run after installing app-debug.apk: python3 tools/ci/levels_tasbih_ui.py [all|levels|reward|tasbih|layout].
Fixtures touch only the disposable profile's learning/reward/tasbih preferences.
Every question assertion uses the actual bundled bank, never a hard-coded answer index.
"""
import copy
import traceback

exec(compile(open('tools/ci/features_controls.py').read().split('mode = sys.argv[1]')[0], 'features_helpers', 'exec'))

MODE = sys.argv[1] if len(sys.argv) > 1 else 'all'
assert MODE in {'all', 'levels', 'reward', 'tasbih', 'layout'}, MODE
OUT.mkdir(exist_ok=True)
(OUT / 'screens').mkdir(exist_ok=True)
BANK = {q['id']: q for q in json.loads(Path('app/src/main/assets/quiz/ru.json').read_text())['questions']}
RU = {n.attrib['name']: n.text or '' for n in ET.parse('app/src/main/res/values-ru/strings.xml').getroot() if n.tag == 'string'}
REPORT = OUT / 'levels-tasbih-passed.json'
report = {'mode': MODE, 'groups': {}, 'screens': []}
started_at = time.monotonic()


def start(route):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_screen', route,
          '--es', 'qa_lang', 'ru', '--ez', 'qa_onboarded', 'true')
    time.sleep(1.2)


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


def update_fixture(values=(), remove=(), remove_prefixes=()):
    """One stopped-process write; unrelated settings and user-like history survive."""
    root = prefs_tree()
    values = dict(values)
    for node in list(root):
        key = node.attrib['name']
        if key in values or key in remove or key.startswith(tuple(remove_prefixes)):
            root.remove(node)
    for key, (kind, value) in values.items():
        node = ET.SubElement(root, kind, {'name': key})
        if kind == 'string':
            node.text = str(value)
        else:
            node.set('value', str(value))
    write_tree(root)


def value(identifier):
    node = locate_control(identifier)
    return ' '.join(n.get('text', '') for n in node.iter('node') if n.get('text'))


def has(identifier):
    return find_control(ui(), identifier) is not None


def counter_value():
    # Compose may merge the tagged numeral into the clickable counter. Accept the
    # accessible merged text/state as well; never substitute the stored preference.
    root = ui()
    if find_control(root, 'tasbih_count_value') is not None:
        text = value('tasbih_count_value')
        if text:
            return text
    node = locate_control('tasbih_counter')
    text = ' '.join(n.get(key, '') for n in node.iter('node')
                    for key in ('text', 'state-description', 'stateDescription', 'content-desc'))
    numbers = re.findall(r'\d+', text)
    assert numbers, ('Counter exposes no readable number', ET.tostring(node, encoding='unicode'))
    return numbers[0]


def counter_for_drag():
    # The counter can start partially below the fold. Reveal it using the page
    # gutter, which cannot count a bead, before exercising the gesture itself.
    for attempt in range(4):
        node = locate_control('tasbih_counter')
        left, top, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
        if bottom - top >= (right - left) * .85:
            return node
        root = ui()
        owners = [n for n in root.iter('node') if n.get('scrollable') == 'true']
        assert owners, 'A partially visible counter has no page scroll owner'
        owner = max(owners, key=lambda n: int(re.findall(r'-?\d+', n.get('bounds'))[3]) -
                    int(re.findall(r'-?\d+', n.get('bounds'))[1]))
        ol, ot, oright, ob = map(int, re.findall(r'-?\d+', owner.get('bounds')))
        shell('input', 'swipe', ol + 5, int(ot + (ob - ot) * .7),
              ol + 5, int(ot + (ob - ot) * .4), 350)
    raise AssertionError('Counter could not be revealed enough for a meaningful drag')


def choose_level(level):
    tap_control('learning_level_' + str(level))
    tap_control('learning_level_confirm')
    assert prefs().get('learning_level_v1') == str(level)


def change_level(level):
    # Reopen the known screen instead of trying to scroll a sheet to its top.
    start('knowledge')
    tap_control('learning_level_change')
    choose_level(level)


def session(scope='normal'):
    return learning().get('sessions', {}).get(scope)


def assert_level(level, scope='normal'):
    current = session(scope)
    assert current and current.get('level') == level, (scope, current)
    assert current['ids'] and len(current['ids']) == len(set(current['ids']))
    for question_id in current['ids']:
        assert question_id in BANK, ('unknown question', question_id)
        question = BANK[question_id]
        assert question.get('difficulty', 1) == level, ('cross-level question', question_id, current)
        if current.get('category'):
            assert question['cat'] == current['category'], ('cross-category question', question_id)
    return current


def enter_quiz():
    current = session()
    if current and current['stage'] == 'ASK':
        tap_control('learning_skip')
    assert session()['stage'] == 'QUIZ'


def answer(scope='normal', correct=True):
    current = session(scope)
    question = BANK[current['ids'][current['index']]]
    canonical = question['answer'] if correct else (question['answer'] + 1) % len(question['options'])
    # The app shuffles options. Select the actual bank text through accessibility.
    tap_control(question['options'][canonical], 'text')
    updated = session(scope)
    assert updated['selected'] == canonical, ('answer did not persist', updated)
    return question


def block_entry():
    start('block_limit')
    text = RU['block_quiz_button'].replace('%1$d', '3').replace('%2$d', '10')
    tap_control(text, 'text')


def quiz_levels():
    update_fixture({'learning_state_v1': ('string', '{}'), 'appearance': ('string', 'light')},
                   remove=('learning_level_v1',))
    start('quiz')
    assert session() is None, 'A quiz began before the knowledge level was chosen'
    assert has('learning_level_1')
    shot('lt-level-first-choice')
    tap_control('learning_level_1')
    assert prefs().get('learning_level_v1') is None, 'Selecting a row committed before confirmation'
    tap_control('learning_level_confirm')
    first = assert_level(1)
    assert first['stage'] == 'ASK'
    enter_quiz()
    answer(correct=False)
    before = session()
    start('knowledge')
    tap_control('learning_resume')
    assert session() == before, 'Pending question/order/answer changed across process restart'
    assert prefs()['learning_level_v1'] == '1'
    shot('lt-level-resumed-answer')
    tap_control('learning_next')
    assert session()['index'] == before['index'] + 1 and 'selected' not in session()
    history = learning()['questions']
    guard = prefs().get('quiz_reward_guard_v1')
    start('knowledge')
    tap_control('learning_level_change')
    tap_control('learning_level_2')
    tap_control('learning_level_cancel')
    assert prefs()['learning_level_v1'] == '1'
    assert session()['id'] == before['id'], 'Cancel discarded the pending round'
    change_level(2)
    assert not learning().get('sessions'), 'A confirmed level change retained an old round'
    assert learning()['questions'] == history
    assert prefs().get('quiz_reward_guard_v1') == guard
    start('quiz')
    middle = assert_level(2)
    enter_quiz()
    shot('lt-level-intermediate-question')
    passed('first_choice_resume_change', {
        'initial_level_explicit': True, 'pending_answer_survived_restart': True,
        'cancel_preserved_round': True, 'level_change_kept_history_and_reward_guard': True,
        'normal_level_1_questions': len(first['ids']), 'normal_level_2_questions': len(middle['ids']),
        'normal_level_1_ids': first['ids'], 'normal_level_2_ids': middle['ids'],
    })

    # Enter the real limit blocker with no selection: its quiz must use the same gate.
    kept = learning()
    kept.pop('sessions', None)
    kept.pop('lastScope', None)
    update_fixture({'learning_state_v1': ('string', json.dumps(kept))}, remove=('learning_level_v1',))
    block_entry()
    assert session('blocking') is None
    assert has('learning_level_3')
    choose_level(3)
    blocked = assert_level(3, 'blocking')
    assert blocked['stage'] == 'QUIZ'
    shot('lt-level-advanced-blocker')
    block_entry()
    assert session('blocking') == blocked, 'Blocking round changed after process restart'

    # A legacy/easy pending round must not become an advanced fallback.
    contaminated = copy.deepcopy(blocked)
    easy_id = next(q['id'] for q in BANK.values() if q.get('difficulty', 1) == 1)
    contaminated.update({'id': 'ci-cross-level-pending', 'ids': [easy_id],
                         'versions': {easy_id: BANK[easy_id].get('contentVersion', 1)},
                         'stage': 'ASK', 'index': 0, 'correct': [], 'category': ''})
    state = learning()
    state.setdefault('sessions', {})['normal'] = contaminated
    update_fixture({'learning_state_v1': ('string', json.dumps(state))})
    start('quiz')
    advanced = assert_level(3)
    assert advanced['id'] != contaminated['id'] and easy_id not in advanced['ids']
    passed('strict_levels_and_blocker', {
        'blocking_gate_required': True, 'blocking_resume_preserved': True,
        'blocking_level_3_questions': len(blocked['ids']),
        'normal_level_3_questions': len(advanced['ids']), 'cross_level_pending_rejected': True,
        'blocking_level_3_ids': blocked['ids'], 'normal_level_3_ids': advanced['ids'],
    })


def reward_guard():
    update_fixture({'learning_state_v1': ('string', '{}'), 'learning_level_v1': ('string', '3')},
                   remove=('quiz_reward_guard_v1',), remove_prefixes=('bonus_',))
    start('quiz')
    assert_level(3)
    enter_quiz()
    for i in range(3):
        answer()
        tap_control('learning_finish' if i == 2 else 'learning_next')
    completed = session()
    assert completed['stage'] == 'RESULT' and completed['added'] == 10, completed
    rewarded = prefs()
    bonus = {k: v for k, v in rewarded.items() if k.startswith('bonus_')}
    ledger = json.loads(rewarded['quiz_reward_guard_v1'])
    assert ledger['awards'][completed['id']] == 10
    # A configuration recreation restores the existing navigation entry. Starting
    # qa_screen=quiz is instead an explicit new round and must discard RESULT.
    try:
        shell('wm', 'density', '421')
        time.sleep(1)
        locate_control(RU['learning_complete'], 'text')
        assert session() == completed, 'Activity recreation changed the completed receipt'
    finally:
        shell('wm', 'density', '420')
        time.sleep(1)
    assert {k: v for k, v in prefs().items() if k.startswith('bonus_')} == bonus
    shot('lt-level-reward-once')
    start('quiz')
    assert session()['id'] != completed['id'] and session()['stage'] == 'ASK'
    assert {k: v for k, v in prefs().items() if k.startswith('bonus_')} == bonus
    change_level(2)
    change_level(3)
    assert json.loads(prefs()['quiz_reward_guard_v1']) == ledger, 'Switching levels erased award protection'
    history = learning()['questions']
    replay = copy.deepcopy(completed)
    # A new round with the same three rewarded questions represents retrying after a level switch.
    replay.update({'id': 'ci-replay-after-level-switch', 'ids': completed['correct'],
                   'stage': 'QUIZ', 'index': 0, 'card': 0, 'correct': [], 'added': 0})
    replay.pop('selected', None)
    replay['versions'] = {key: completed['versions'][key] for key in replay['ids']}
    update_fixture({'learning_state_v1': ('string', json.dumps({
        'schema': 1, 'questions': history, 'sessions': {'normal': replay}, 'lastScope': 'normal',
    }))})
    start('quiz')
    assert_level(3)
    for i in range(3):
        answer()
        tap_control('learning_finish' if i == 2 else 'learning_next')
    assert session()['stage'] == 'RESULT' and session()['added'] == 0, session()
    after = prefs()
    assert {k: v for k, v in after.items() if k.startswith('bonus_')} == bonus
    assert json.loads(after['quiz_reward_guard_v1']) == ledger
    shot('lt-level-repeated-questions-no-bonus')
    passed('reward_guard_after_level_switch', {
        'first_round_minutes': 10, 'result_activity_recreation_preserved_receipt': True,
        'explicit_new_round_did_not_duplicate_award': True,
        'ledger_survived_level_changes': True, 'repeated_question_round_minutes': 0,
        'rewarded_question_ids': completed['correct'],
    })


def tasbih():
    update_fixture({'tasbih_count': ('int', '31'), 'tasbih_target': ('int', '33'),
                    'tasbih_phrase': ('int', '0'), 'appearance': ('string', 'light')})
    start('tasbih')
    tap_control('tasbih_counter')
    tap_control('tasbih_counter')
    assert prefs()['tasbih_count'] == '33'
    assert counter_value() == '33', 'Completed target must display 33, not zero'
    shot('lt-tasbih-completed-lap-light')
    assert value('tasbih_laps_value') == '1'
    start('tasbih')
    assert prefs()['tasbih_count'] == '33'
    assert counter_value() == '33'
    tap_control('tasbih_counter')
    assert prefs()['tasbih_count'] == '34'
    assert counter_value() == '1'

    # Keep the swipe inside the visible counter, never the page scroll gutter.
    counter_value()
    node = counter_for_drag()
    left, top, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
    assert bottom - top > 200, ('counter too clipped for a meaningful drag', node.get('bounds'))
    shell('input', 'swipe', (left + right) // 2, top + (bottom - top) // 5,
          (left + right) // 2, top + (bottom - top) * 4 // 5, 700)
    dragged = int(prefs()['tasbih_count'])
    assert 35 <= dragged <= 39, ('drag did not increment the counter consistently', dragged)
    tap_control('tasbih_target_99')
    assert prefs()['tasbih_target'] == '99' and int(prefs()['tasbih_count']) == dragged
    tap_control('tasbih_target_100')
    assert prefs()['tasbih_target'] == '100' and int(prefs()['tasbih_count']) == dragged
    tap_control('tasbih_target_0')
    assert prefs()['tasbih_target'] == '0'
    start('tasbih')
    assert counter_value() == str(dragged)
    assert value('tasbih_total_value') == str(dragged)

    # Same phrase keeps the count; a different phrase starts its own current count.
    start('tasbih')
    tap_control('tasbih_phrase_open')
    tap_control('tasbih_phrase_0')
    assert int(prefs()['tasbih_count']) == dragged
    day_history = {k: v for k, v in prefs().items() if k.startswith('tasbih_day_')}
    tap_control('tasbih_phrase_open')
    tap_control('tasbih_phrase_1')
    changed = prefs()
    assert changed['tasbih_phrase'] == '1' and changed['tasbih_count'] == '0'
    assert {k: v for k, v in changed.items() if k.startswith('tasbih_day_')} == day_history
    tap_control('tasbih_counter')
    assert prefs()['tasbih_count'] == '1'
    tap_control('tasbih_reset')
    tap_control('tasbih_reset_cancel')
    assert prefs()['tasbih_count'] == '1'
    tap_control('tasbih_reset')
    tap_control('tasbih_reset_confirm')
    assert prefs()['tasbih_count'] == '0'
    start('tasbih')
    final = prefs()
    assert (final['tasbih_count'], final['tasbih_target'], final['tasbih_phrase']) == ('0', '0', '1')
    shot('lt-tasbih-reset-persisted')
    passed('tasbih_interaction_and_persistence', {
        'completed_target_display': '33/33', 'next_tap_display': '1/33',
        'drag_delta': dragged - 34, 'targets_preserve_total': [99, 100, 0],
        'same_phrase_keeps_count': True, 'new_phrase_keeps_daily_history': True,
        'reset_cancel_and_confirm': True, 'count_target_phrase_survived_restart': True,
    })


def compact_dark():
    update_fixture({'appearance': ('string', 'dark'), 'tasbih_count': ('int', '100'),
                    'tasbih_target': ('int', '100'), 'tasbih_phrase': ('int', '0'),
                    'learning_state_v1': ('string', '{}')}, remove=('learning_level_v1',))
    start('tasbih')
    assert counter_value() == '100'
    shot('lt-tasbih-dark')
    shell('wm', 'size', '720x1280')
    shell('wm', 'density', '360')
    shell('settings', 'put', 'system', 'font_scale', '1.5')
    try:
        start('quiz')
        shot('lt-level-choice-dark-320dp-150')
        choose_level(3)
        assert_level(3)
        enter_quiz()
        locate_control('learning_answer_3')
        shot('lt-level-question-dark-320dp-150')
        start('tasbih')
        assert counter_value() == '100'
        shot('lt-tasbih-counter-dark-320dp-150')
        tap_control('tasbih_target_33')
        assert prefs()['tasbih_target'] == '33' and prefs()['tasbih_count'] == '100'
        shot('lt-tasbih-goals-dark-320dp-150')
        start('tasbih')
        tap_control('tasbih_phrase_open')
        locate_control('tasbih_phrase_5')
        shot('lt-tasbih-phrases-dark-320dp-150')
        tap_control('tasbih_phrase_5')
        assert prefs()['tasbih_phrase'] == '5'
        start('tasbih')
        tap_control('tasbih_reset')
        locate_control('tasbih_reset_confirm')
        shot('lt-tasbih-reset-dark-320dp-150')
        tap_control('tasbih_reset_cancel')
        passed('dark_compact_large_text', {
            'width_dp': 320, 'font_scale': 1.5, 'theme': 'dark',
            'level_confirm_answers_goals_phrase_and_reset_reachable': True,
            'screens_require_visual_review': True,
        })
    finally:
        shell('settings', 'put', 'system', 'font_scale', '1')
        shell('wm', 'size', '1080x1920')
        shell('wm', 'density', '420')


try:
    start('home')  # Initializes SharedPreferences on a newly installed debug profile.
    assert all(sum(q.get('difficulty', 1) == level for q in BANK.values()) >= 40 for level in (1, 2, 3)), \
        'The installed test source must include a substantial bank for all three levels'
    if MODE in {'all', 'levels'}:
        quiz_levels()
    if MODE in {'all', 'reward'}:
        reward_guard()
    if MODE in {'all', 'tasbih'}:
        tasbih()
    if MODE in {'all', 'layout'}:
        compact_dark()
    save_report()
except Exception:
    (OUT / 'levels-tasbih-failure.txt').write_text(traceback.format_exc())
    save_report()
    try:
        shot('lt-failure')
    except Exception:
        pass
    raise
