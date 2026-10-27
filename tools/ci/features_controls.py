"""Real UI/persistence assertions on synthetic data in a disposable debug emulator."""
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = Path('ci-out')
PKG = 'com.zikrtime.app'


def adb(*args):
    try:
        return subprocess.check_output(['adb', *map(str, args)], text=True, timeout=45)
    except subprocess.TimeoutExpired:
        (OUT / 'adb-timeout.txt').write_text('ADB command exceeded 45 seconds. This is an automation timeout, not proof of an app crash.\n')
        raise


def shell(*args):
    return adb('shell', *args)


def prefs_tree(name='zikrtime'):
    time.sleep(.5)
    return ET.fromstring(adb('exec-out', 'run-as', PKG, 'cat', f'shared_prefs/{name}.xml'))


def prefs(name='zikrtime'):
    result = {}
    for node in prefs_tree(name):
        result[node.attrib['name']] = (
            sorted(x.text or '' for x in node) if node.tag == 'set'
            else node.attrib.get('value', node.text or '')
        )
    return result


def launch(screen):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_screen', screen,
          '--es', 'qa_lang', 'en', '--ez', 'qa_onboarded', 'true')
    time.sleep(2)


def ui():
    shell('uiautomator', 'dump', '/sdcard/features.xml')
    root = ET.fromstring(shell('cat', '/sdcard/features.xml'))
    (OUT / 'last-ui.xml').write_text(ET.tostring(root, encoding='unicode'))
    return root


def find_control(root, identifier, attribute='resource-id'):
    return next((n for n in root.iter('node') if n.attrib.get(attribute) == identifier
                 or (attribute == 'resource-id' and (
                     n.attrib.get(attribute, '').endswith('/' + identifier)
                     or n.attrib.get('content-desc') == identifier))), None)


def locate_control(identifier, attribute='resource-id'):
    for attempt in range(7):
        root = ui()
        node = find_control(root, identifier, attribute)
        screen = next(root.iter('node'))
        viewport_left, viewport_top, width, height = map(int, re.findall(r'-?\d+', screen.attrib['bounds']))
        if node is not None:
            left, top, right, bottom = map(int, re.findall(r'-?\d+', node.attrib['bounds']))
            # Compose can expose a matching semantics node before it is scrolled
            # into view. Its empty/clipped bounds are not a usable tap target.
            left, top = max(left, viewport_left), max(top, viewport_top)
            right, bottom = min(right, width), min(bottom, height)
            parents = {child: parent for parent in root.iter() for child in parent}
            parent = parents.get(node)
            while parent is not None:
                if parent.attrib.get('scrollable') == 'true':
                    pl, pt, pr, pb = map(int, re.findall(r'-?\d+', parent.attrib['bounds']))
                    left, top, right, bottom = max(left, pl), max(top, pt), min(right, pr), min(bottom, pb)
                parent = parents.get(parent)
            if right - left >= 12 and bottom - top >= 12:
                node.set('bounds', f'[{left},{top}][{right},{bottom}]')
                return node
        # Screen edge avoids the tasbih gesture detector and does not increment its count.
        shell('input', 'swipe', 5, int(height * .8), 5, int(height * .3), 350)
    raise AssertionError('UI control not found: ' + identifier)


def tap_control(identifier, attribute='resource-id'):
    node = locate_control(identifier, attribute)
    left, top, right, bottom = map(int, re.findall(r'\d+', node.attrib['bounds']))
    shell('input', 'tap', (left + right) // 2, (top + bottom) // 2)
    time.sleep(.7)


def screenshot(name):
    with (OUT / 'screens' / (name + '.png')).open('wb') as handle:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=handle, check=True, timeout=45)
    subprocess.run([sys.executable, 'tools/ci/check_screen.py', str(OUT / 'screens' / (name + '.png'))], check=True)


def put_pref(name, kind, value):
    root = prefs_tree()
    for node in list(root):
        if node.attrib['name'] == name:
            root.remove(node)
    node = ET.SubElement(root, kind, {'name': name})
    if kind == 'string':
        node.text = value
    elif kind == 'set':
        for entry in value:
            ET.SubElement(node, 'string').text = entry
    else:
        node.set('value', value)
    write_tree(root)


def write_tree(root, name='zikrtime'):
    path = OUT / 'synthetic-prefs.xml'
    path.write_bytes(ET.tostring(root, encoding='utf-8', xml_declaration=True))
    shell('am', 'force-stop', PKG)
    adb('push', path, '/data/local/tmp/zikrtime-fixture.xml')
    shell('run-as', PKG, 'cp', '/data/local/tmp/zikrtime-fixture.xml', f'shared_prefs/{name}.xml')


def learning():
    return json.loads(prefs().get('learning_state_v1', '{}'))


FOCUS_TARGET = 'com.zikrtime.focusfixture'


def focus_setup(strict=False, accessibility=True):
    shell('settings', 'put', 'secure', 'enabled_accessibility_services', 'null')
    shell('settings', 'put', 'secure', 'accessibility_enabled', '0')
    put_pref('packages', 'set', [FOCUS_TARGET])
    put_pref('apps_init', 'boolean', 'true')
    put_pref('blocking', 'boolean', 'false')
    root = ET.Element('map')
    ET.SubElement(root, 'boolean', {'name': 'enabled', 'value': 'true'})
    ET.SubElement(root, 'boolean', {'name': 'strict', 'value': str(strict).lower()})
    ET.SubElement(root, 'int', {'name': 'phrase', 'value': '0'})
    write_tree(root, 'focus_pause_v1')
    shell('appops', 'set', PKG, 'GET_USAGE_STATS', 'allow')
    shell('appops', 'set', PKG, 'SYSTEM_ALERT_WINDOW', 'allow')
    launch('focus')
    if accessibility:
        shell('settings', 'put', 'secure', 'enabled_accessibility_services', PKG + '/.screentime.BlockerService')
        shell('settings', 'put', 'secure', 'accessibility_enabled', '1')
    time.sleep(3)


def open_focus_target():
    shell('am', 'start', '-W', '-n', FOCUS_TARGET + '/.MainActivity')


def wait_pause():
    for _ in range(8):
        tree = ui()
        if find_control(tree, 'pause_screen') is not None:
            return
        if find_control(tree, 'pause_overlay_open') is not None:
            tap_control('pause_overlay_open')
        time.sleep(2)
    raise AssertionError('Real foreground target did not trigger pause')


def focus_session():
    return json.loads(prefs('focus_pause_v1')['session:' + FOCUS_TARGET])


def historical(values):
    return {k: v for k, v in values.items() if k in {
        'total_correct', 'total_answered', 'quiz_days', 'prayer_days', 'personal_progress_ledger',
    } or k.startswith(('cat_correct_', 'tasbih_day_'))}


mode = sys.argv[1]
if mode == 'seed-upgrade':
    root = prefs_tree()
    day = shell('date', '+%Y-%m-%d').strip()
    data = {
        'city_id': ('string', 'moscow'), 'city': ('string', 'Moscow'),
        'lat': ('float', '55.75'), 'lon': ('float', '37.6167'),
        'method': ('string', 'RUSSIA'), 'madhab': ('string', 'STANDARD'),
        'online_times': ('boolean', 'false'), 'adj_FAJR': ('int', '4'),
        'limit': ('int', '37'), 'alarm_offset': ('int', '25'),
        'alarm_on': ('boolean', 'false'), 'blocking': ('boolean', 'false'),
        'total_answered': ('int', '9'), 'total_correct': ('int', '7'),
        'tasbih_count': ('int', '21'), 'tasbih_target': ('int', '99'),
        'bonus_' + day: ('int', '10'), 'prayed_' + day: ('set', ['FAJR']),
        'quiz_days': ('set', [day]), 'onboarded': ('boolean', 'true'),
    }
    for node in list(root):
        if node.attrib['name'] in data:
            root.remove(node)
    for key, (kind, value) in data.items():
        node = ET.SubElement(root, kind, {'name': key})
        if kind == 'string':
            node.text = value
        elif kind == 'set':
            for entry in value:
                ET.SubElement(node, 'string').text = entry
        else:
            node.set('value', value)
    fixture = OUT / 'upgrade-synthetic.xml'
    fixture.write_bytes(ET.tostring(root, encoding='utf-8', xml_declaration=True))
    shell('am', 'force-stop', PKG)
    adb('push', fixture, '/data/local/tmp/zikrtime-upgrade.xml')
    shell('run-as', PKG, 'cp', '/data/local/tmp/zikrtime-upgrade.xml', 'shared_prefs/zikrtime.xml')
    (OUT / 'upgrade-expected.json').write_text(json.dumps({k: v[1] for k, v in data.items()}))
elif mode == 'verify-upgrade':
    expected = json.loads((OUT / 'upgrade-expected.json').read_text())
    current = prefs()
    assert all(current.get(k) == v for k, v in expected.items()), {
        k: (v, current.get(k)) for k, v in expected.items() if current.get(k) != v
    }
    assert current.get('personal_progress_schema') == '1'
    (OUT / 'upgrade-passed.json').write_text(json.dumps({'preserved_keys': sorted(expected), 'debug_fixture_only': True}))
elif mode == 'progress':
    before = prefs()
    assert before.get('personal_progress_enabled', 'true') == 'true'
    (OUT / 'progress-history-before.json').write_text(json.dumps(historical(before)))
    (OUT / 'progress-work-before.json').write_text(json.dumps({k: v for k, v in before.items()
        if k.startswith(('bonus_', 'prayed_')) or k in ['city_id', 'limit', 'tasbih_count', 'tasbih_target', 'learning_state_v1']}))
    tap_control('personal_progress_toggle')
    assert prefs().get('personal_progress_enabled') == 'false'
    # Actual foreground UI increments the operational counter while statistics are off.
    launch('tasbih')
    for _ in range(3):
        tap_control('Tasbih', 'content-desc')
    after = prefs()
    assert int(after['tasbih_count']) == int(before['tasbih_count']) + 3
    assert historical(after) == historical(before), 'Disabled statistics changed during tasbih taps'
elif mode == 'verify-disabled':
    values = prefs()
    assert values.get('personal_progress_enabled') == 'false', 'Disabled switch did not survive process restart'
    assert historical(values) == json.loads((OUT / 'progress-history-before.json').read_text())
    expected = json.loads((OUT / 'progress-work-before.json').read_text())
    expected['tasbih_count'] = str(int(expected['tasbih_count']) + 3)
    assert all(values.get(k) == v for k, v in expected.items()), 'Operational data changed while progress was off'
    (OUT / 'progress-disabled-passed.json').write_text(json.dumps({'persisted_after_restart': True, 'history_unchanged': True, 'tasbih_operational_delta': 3}))
elif mode == 'enable-progress':
    assert prefs().get('personal_progress_enabled') == 'false'
    tap_control('personal_progress_toggle')
    assert prefs().get('personal_progress_enabled') == 'true'
    assert historical(prefs()) == json.loads((OUT / 'progress-history-before.json').read_text()), 'Off-period data was reconstructed'
    (OUT / 'progress-enabled-passed.json').write_text(json.dumps({'enabled': True, 'off_period_not_reconstructed': True}))
elif mode == 'learning':
    launch('quiz')
    tap_control('learning_read')
    tap_control('learning_card_next')
    before = learning()['sessions']['normal']
    assert before['stage'] == 'CARDS' and before['card'] == 1
    launch('knowledge')
    tap_control('learning_resume')
    assert learning()['sessions']['normal'] == before, 'Material position changed after restart'
    screenshot('learning-card-resumed')
    for _ in range(3):
        tap_control('learning_card_next')
    tap_control('learning_card_quiz')
    tap_control('learning_answer_0')
    before = learning()['sessions']['normal']
    assert before['stage'] == 'QUIZ' and 'selected' in before
    launch('knowledge')
    tap_control('learning_resume')
    assert learning()['sessions']['normal'] == before, 'Question/answer/order changed after restart'
    screenshot('learning-answer-resumed')
    tap_control('learning_next')
    after = learning()['sessions']['normal']
    assert after['index'] == before['index'] + 1 and 'selected' not in after
    (OUT / 'learning-resume-passed.json').write_text(json.dumps({
        'cards_index_preserved': 1, 'question_ids_preserved': True,
        'selected_canonical_answer_preserved': True, 'next_question_increment': 1,
    }))
    # A synthetic overdue real content ID, isolated to learning state; no live server.
    question = json.loads(Path('app/src/main/assets/quiz/en.json').read_text())['questions'][0]
    now = int(time.time() * 1000)
    fixture = {'schema': 1, 'questions': {question['id']: {
        'v': question.get('contentVersion', 1), 'ok': False, 'at': now - 172800000,
        'stage': 0, 'due': now - 86400000, 'shown': 0, 'event': 'ci-fixture',
    }}, 'sessions': {}}
    put_pref('learning_state_v1', 'string', json.dumps(fixture))
    launch('review')
    assert learning()['sessions']['review']['ids'] == [question['id']]
    screenshot('review-overdue')
    before_bonus = {k: v for k, v in prefs().items() if k.startswith('bonus_')}
    tap_control('learning_answer_0')
    tap_control('learning_finish')
    assert learning()['sessions']['review']['stage'] == 'RESULT'
    assert learning()['questions'][question['id']]['due'] > now
    assert {k: v for k, v in prefs().items() if k.startswith('bonus_')} == before_bonus
    screenshot('review-complete')
    (OUT / 'learning-review-passed.json').write_text(json.dumps({
        'overdue_question_loaded': question['id'], 'due_rescheduled': True, 'bonus_unchanged': True,
    }))
elif mode == 'focus':
    focus_setup()
    open_focus_target()
    wait_pause()
    screenshot('focus-soft-pause')
    tap_control('pause_skip')
    tap_control('pause_duration_1')
    tap_control('pause_open')
    assert focus_session()['budget'] == 60000
    shell('input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(12)
    open_focus_target()
    time.sleep(4)
    assert find_control(ui(), 'focus_fixture_foreground', 'content-desc') is not None
    assert focus_session()['consumed'] < 10000, 'Background time consumed the foreground allowance'
    # Reconnect the actual service while the target stays foreground; no fresh grant/open.
    grant = focus_session()
    shell('settings', 'put', 'secure', 'enabled_accessibility_services', 'null')
    shell('settings', 'put', 'secure', 'accessibility_enabled', '0')
    time.sleep(2)
    shell('settings', 'put', 'secure', 'enabled_accessibility_services', PKG + '/.screentime.BlockerService')
    shell('settings', 'put', 'secure', 'accessibility_enabled', '1')
    time.sleep(3)
    assert focus_session()['start'] == grant['start'] and focus_session()['budget'] == grant['budget']
    # One real minute in the target: soft mode reminds but leaves the current visit open.
    time.sleep(65)
    assert focus_session()['reminded'] is True
    assert find_control(ui(), 'focus_fixture_foreground', 'content-desc') is not None
    screenshot('focus-soft-expired-visit')
    shell('input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(2)
    open_focus_target()
    wait_pause()
    screenshot('focus-soft-reopened-pause')

    focus_setup(strict=True)
    open_focus_target()
    wait_pause()
    assert find_control(ui(), 'pause_skip') is None
    screenshot('focus-strict-pause')
    tap_control('pause_done')
    tap_control('pause_duration_1')
    tap_control('pause_open')
    assert focus_session()['budget'] == 60000
    time.sleep(65)
    wait_pause()
    screenshot('focus-strict-expired-pause')
    shell('appops', 'set', PKG, 'GET_USAGE_STATS', 'deny')
    time.sleep(3)
    assert find_control(ui(), 'pause_screen') is None, 'Pause did not dismiss after usage access was revoked'

    focus_setup(strict=True, accessibility=False)
    open_focus_target()
    wait_pause()
    screenshot('focus-fallback-pause')
    tap_control('pause_disable')
    assert prefs('focus_pause_v1')['enabled'] == 'false'
    assert all(not k.startswith(('session:', 'visit:')) for k in prefs('focus_pause_v1'))
    shell('settings', 'put', 'secure', 'enabled_accessibility_services', 'null')
    shell('settings', 'put', 'secure', 'accessibility_enabled', '0')
    (OUT / 'focus-passed.json').write_text(json.dumps({
        'target': FOCUS_TARGET, 'synthetic_target_only': True, 'background_seconds': 12,
        'soft_one_minute_expiry_remains_open': True, 'soft_reopen_pauses': True,
        'strict_one_minute_expiry_pauses': True, 'usage_revocation_dismisses': True,
        'fallback_path_opened': True, 'disable_clears_session': True,
        'accessibility_reconnected_without_new_grant_or_target_launch': True,
    }))
elif mode == 'focus-visual':
    (OUT / 'screens').mkdir(parents=True, exist_ok=True)
    font_before = shell('settings', 'get', 'system', 'font_scale').strip()
    night_match = re.search(r'Night mode:\s*(yes|no|auto|custom)', shell('cmd', 'uimode', 'night'))
    night_before = night_match.group(1) if night_match else 'auto'
    try:
        shell('cmd', 'uimode', 'night', 'yes')
        shell('settings', 'put', 'system', 'font_scale', '1.3')
        focus_setup(strict=True)
        open_focus_target()
        wait_pause()
        screenshot('focus-visual-dark-large-pause')
        locate_control('pause_done')
        (OUT / 'focus-visual-pause-actions-ui.xml').write_bytes(ET.tostring(ui()))
        screenshot('focus-visual-dark-large-pause-actions')
        tap_control('pause_done')
        # Return to the top after replacing the longer prayer card with the intention form.
        for _ in range(2):
            tree = ui()
            bounds = next(tree.iter('node')).attrib['bounds']
            _, _, width, height = map(int, re.findall(r'\d+', bounds))
            shell('input', 'swipe', 5, int(height * .25), 5, int(height * .8), 350)
        strings = ET.parse('app/src/main/res/values/strings.xml').getroot()
        intention_label = next(n.text for n in strings if n.attrib.get('name') == 'pause_intention')
        locate_control(intention_label, 'text')
        (OUT / 'focus-visual-intention-ui.xml').write_bytes(ET.tostring(ui()))
        screenshot('focus-visual-dark-large-intention')
        custom_label = next(n.text for n in strings if n.attrib.get('name') == 'pause_custom')
        tap_control(custom_label, 'text')
        tap_control('android.widget.EditText', 'class')
        shell('input', 'text', 'CI%stest%sintention')
        time.sleep(1)
        assert any('CI test intention' in n.attrib.get('text', '') for n in ui().iter('node'))
        screenshot('focus-visual-dark-large-custom-keyboard')
        shell('input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(.5)
        screenshot('focus-visual-dark-large-custom')
        # Cancel through the normal back action. Never tap Open app or create a grant.
        shell('input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(1)
        assert find_control(ui(), 'pause_screen') is None
        assert 'session:' + FOCUS_TARGET not in prefs('focus_pause_v1'), 'Visual inspection unexpectedly granted app time'
        (OUT / 'focus-visual-passed.json').write_text(json.dumps({
            'dark_theme': True, 'font_scale': 1.3, 'real_foreground_pause': True,
            'intention_form_visible': True, 'custom_intention_entered': True,
            'text_entry_captured': True, 'back_cancels_without_grant': True,
            'synthetic_target_only': FOCUS_TARGET,
        }))
    finally:
        for args in [
            ('settings', 'put', 'secure', 'enabled_accessibility_services', 'null'),
            ('settings', 'put', 'secure', 'accessibility_enabled', '0'),
            ('settings', 'put', 'system', 'font_scale', font_before if font_before not in ('', 'null') else '1'),
            ('cmd', 'uimode', 'night', night_before),
        ]:
            try:
                shell(*args)
            except subprocess.SubprocessError:
                pass
        disabled = ET.Element('map')
        ET.SubElement(disabled, 'boolean', {'name': 'enabled', 'value': 'false'})
        write_tree(disabled, 'focus_pause_v1')
elif mode == 'focus-notifications-denied':
    try:
        focus_setup(strict=False, accessibility=False)
        shell('pm', 'revoke', PKG, 'android.permission.POST_NOTIFICATIONS')
        root = prefs_tree('focus_pause_v1')
        now = int(shell('date', '+%s').strip()) * 1000
        session = {'start': now - 1000, 'budget': 1, 'consumed': 1,
                   'reminded': False, 'expiryHandled': False, 'intention': 'CI synthetic expiry'}
        ET.SubElement(root, 'string', {'name': 'session:' + FOCUS_TARGET}).text = json.dumps(session)
        ET.SubElement(root, 'long', {'name': 'visit:' + FOCUS_TARGET, 'value': '0'})
        write_tree(root, 'focus_pause_v1')
        # Fresh process reads the fixture and actual revoked OS permission.
        launch('focus')
        shell('settings', 'put', 'secure', 'enabled_accessibility_services', PKG + '/.screentime.BlockerService')
        shell('settings', 'put', 'secure', 'accessibility_enabled', '1')
        time.sleep(3)
        permission = shell('dumpsys', 'package', PKG)
        assert re.search(r'android\.permission\.POST_NOTIFICATIONS:\s+granted=false', permission)
        strings = ET.parse('app/src/main/res/values/strings.xml').getroot()
        warning = next(n.text for n in strings if n.attrib.get('name') == 'perm_notifications_off')
        locate_control(warning, 'text')
        screenshot('focus-notifications-disabled-warning')
        open_focus_target()
        for _ in range(8):
            if focus_session().get('expiryHandled'):
                break
            time.sleep(1)
        state = focus_session()
        assert state.get('expiryHandled') is True and state['reminded'] is False
        tree = ui()
        assert find_control(tree, 'focus_fixture_foreground', 'content-desc') is not None
        assert find_control(tree, 'pause_screen') is None
        screenshot('focus-notifications-denied-expired-visit')
        shell('input', 'keyevent', 'KEYCODE_HOME')
        time.sleep(2)
        open_focus_target()
        wait_pause()
        assert focus_session()['reminded'] is False
        screenshot('focus-notifications-denied-next-visit')
        (OUT / 'focus-notifications-denied-passed.json').write_text(json.dumps({
            'actual_post_notifications_revoked': True, 'warning_visible': True,
            'synthetic_expired_budget_millis': 1, 'expiry_handled_without_claiming_reminder': True,
            'current_visit_remains_open': True, 'next_visit_pauses': True,
        }))
    finally:
        # Preserve evidence before restoring this disposable test environment.
        try:
            (OUT / 'focus-notifications-final-state.xml').write_bytes(ET.tostring(prefs_tree('focus_pause_v1')))
        except (subprocess.SubprocessError, ET.ParseError):
            pass
        for args in [
            ('pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS'),
            ('settings', 'put', 'secure', 'enabled_accessibility_services', 'null'),
            ('settings', 'put', 'secure', 'accessibility_enabled', '0'),
        ]:
            try:
                shell(*args)
            except subprocess.SubprocessError:
                pass
        disabled = ET.Element('map')
        ET.SubElement(disabled, 'boolean', {'name': 'enabled', 'value': 'false'})
        write_tree(disabled, 'focus_pause_v1')
elif mode == 'clear-progress':
    launch('progress')
    before = prefs()
    work = {k: v for k, v in before.items() if k.startswith(('bonus_', 'prayed_')) or k in {
        'learning_state_v1', 'quiz_reward_guard_v1', 'city_id', 'tasbih_count', 'tasbih_target', 'limit',
    }}
    tap_control('personal_progress_clear')
    assert historical(prefs()) == historical(before), 'Opening confirmation erased statistics'
    tap_control('personal_progress_clear_confirm')
    after = prefs()
    assert all(after.get(k) == v for k, v in work.items()), 'Clearing history changed operational data'
    history = historical(after)
    ledger = json.loads(history.pop('personal_progress_ledger', '{}'))
    assert not history and not ledger.get('events', [])
    launch('progress')
    assert all(prefs().get(k) == v for k, v in work.items())
    screenshot('progress-cleared')
    (OUT / 'progress-clear-passed.json').write_text(json.dumps({
        'confirmation_required': True, 'visible_history_empty': True,
        'operational_keys_preserved': sorted(work), 'process_restart_checked': True,
    }))
elif mode == 'clear-learning':
    # Repopulate a real answer and its statistics after the separate history-clear test.
    # Then clear learning with progress disabled: the controls must remain available.
    launch('quiz')
    tap_control('learning_skip')
    tap_control('learning_answer_0')
    assert 'selected' in learning()['sessions']['normal']
    launch('progress')
    assert prefs().get('personal_progress_enabled') == 'true'
    tap_control('personal_progress_toggle')
    assert prefs().get('personal_progress_enabled') == 'false'
    launch('home')
    locate_control('my_day_resume_learning')
    screenshot('learning-clear-before-home')
    launch('knowledge')
    before = prefs()
    assert learning().get('questions') and learning().get('sessions')
    allowed_changes = {'learning_state_v1', 'recent_questions'}
    unchanged = {k: v for k, v in before.items() if k not in allowed_changes}
    tap_control('learning_clear')
    assert prefs().get('learning_state_v1') == before['learning_state_v1']
    tap_control('learning_clear_cancel')
    assert prefs().get('learning_state_v1') == before['learning_state_v1'], 'Cancel erased learning'
    tap_control('learning_clear')
    tap_control('learning_clear_confirm')
    after = prefs()
    assert learning() == {'schema': 1}, 'Learning questions/sessions remain after clear'
    assert after.get('recent_questions', '') == '', 'Legacy learning order remains after clear'
    assert {k: v for k, v in after.items() if k not in allowed_changes} == unchanged, 'Learning clear changed other preferences'
    assert historical(after) == historical(before), 'Learning clear erased personal statistics'
    launch('home')
    locate_control('my_day_start_learning')
    assert find_control(ui(), 'my_day_resume_learning') is None, 'Home still offers a deleted learning session'
    assert learning() == {'schema': 1}, 'Learning reappeared after process restart'
    screenshot('learning-cleared-home')
    launch('progress')
    assert historical(prefs()) == historical(before)
    tap_control('personal_progress_toggle')
    assert prefs().get('personal_progress_enabled') == 'true'
    (OUT / 'learning-clear-passed.json').write_text(json.dumps({
        'available_when_progress_disabled': True, 'cancel_preserves_learning': True,
        'only_learning_and_recent_order_cleared': True, 'other_preferences_preserved': sorted(unchanged),
        'statistics_preserved': True, 'home_replaced_resume_with_start_after_restart': True,
    }))
else:
    raise ValueError(mode)
