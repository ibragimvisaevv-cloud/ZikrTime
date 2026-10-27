"""Offline native-UI checks of synthetic prayer-place cards on a disposable emulator.

Requires an installed debug APK. Never opens a bot, submits a correction or uses a
production endpoint. Original SharedPreferences/display/network settings are restored.
Run: python3 tools/ci/features_places.py
"""
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = 'com.zikrtime.app'
OUT = Path('ci-out/places')
SCREENS = Path('ci-out/screens')
PREF = 'shared_prefs/zikrtime.xml'
FIELDS = (
    'Address', 'Access hours', 'Access conditions', 'Entrance and directions',
    'Wudu facilities', "Women's area", 'Information source', 'Actually confirmed on',
)
COMPLETE = dict(zip(FIELDS, (
    'CI address 1', '08:00-20:00', 'CI access: reception permission', 'CI east entrance',
    'Yes', 'No', 'OpenStreetMap · CI source: synthetic report', '2026-01-01',
)))
LEGACY = {field: ('CI legacy address' if field == 'Address' else 'Unknown') for field in FIELDS}
FIXTURE = {
    'version': 1, 'updated': 1790000000, 'seller_bot': 'ZikrTimeSyntheticBot',
    'donate_url': '', 'corrections_enabled': True,
    'places': [
        {'id': 'ci_complete', 'kind': 'room', 'name': 'CI Complete Prayer Room',
         'lat': 55.751, 'lon': 37.616, 'address': COMPLETE['Address'],
         'hours': COMPLETE['Access hours'], 'access_conditions': COMPLETE['Access conditions'],
         'entrance': COMPLETE['Entrance and directions'], 'wudu': True, 'women': False,
         'src': 'osm', 'source_details': 'CI source: synthetic report', 'confirmed_at': '2026-01-01'},
        {'id': 'ci_legacy', 'kind': 'room', 'name': 'CI Legacy Prayer Room',
         'lat': 55.752, 'lon': 37.617, 'address': 'CI legacy address', 'who': 'all',
         'created': 1780000000, 'updated': 1790000000},
    ],
}


def adb(*args, check=True, binary=False):
    return subprocess.run(['adb', *map(str, args)], check=check, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=not binary, timeout=45).stdout


def shell(*args, **kwargs):
    return adb('shell', *args, **kwargs)


def put_pref(root, name, kind, value):
    for child in list(root):
        if child.attrib.get('name') == name:
            root.remove(child)
    node = ET.SubElement(root, kind, {'name': name})
    if kind == 'string':
        node.text = str(value)
    else:
        node.set('value', str(value).lower())


def write_prefs(data, name):
    path = OUT / name
    path.write_bytes(data)
    shell('am', 'force-stop', PKG)
    shell('run-as', PKG, 'mkdir', '-p', 'shared_prefs')
    adb('push', path, '/data/local/tmp/zikrtime-places-fixture.xml')
    shell('run-as', PKG, 'cp', '/data/local/tmp/zikrtime-places-fixture.xml', PREF)
    shell('run-as', PKG, 'rm', '-f', PREF + '.bak')


def ui(name):
    # Retry transient hierarchy acquisition while Compose finishes its first layout.
    for attempt in range(3):
        try:
            shell('uiautomator', 'dump', '/sdcard/zikrtime-places-ui.xml')
            raw = shell('cat', '/sdcard/zikrtime-places-ui.xml')
            tree = ET.fromstring(raw)
            (OUT / (name + '.xml')).write_text(raw)
            return tree
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
            if attempt == 2:
                raise
            time.sleep(1)


def texts(tree):
    # Compose may merge a clickable card's text into newline-separated semantics.
    return [line.strip() for node in tree.iter('node') for line in node.attrib.get('text', '').splitlines() if line.strip()]


def node_with_text(tree, text):
    return next((node for node in tree.iter('node')
                 if text in [line.strip() for line in node.attrib.get('text', '').splitlines()]), None)


def bounds(node):
    return tuple(map(int, re.findall(r'-?\d+', node.attrib['bounds'])))


def tap(tree, text):
    node = node_with_text(tree, text)
    if node is None:
        raise AssertionError('Native UI text not found: ' + text)
    x1, y1, x2, y2 = bounds(node)
    assert x2 > x1 and y2 > y1, ('Offscreen control', text)
    shell('input', 'tap', (x1 + x2) // 2, (y1 + y2) // 2)
    time.sleep(.6)


def swipe_list(tree):
    scroll = [node for node in tree.iter('node') if node.attrib.get('scrollable') == 'true']
    # The lazy vertical list is taller than the horizontal filter row.
    candidates = [node for node in scroll if bounds(node)[3] - bounds(node)[1] > 120]
    if not candidates:
        raise AssertionError('Scrollable places list is not exposed in native UI')
    node = max(candidates, key=lambda n: bounds(n)[3] - bounds(n)[1])
    x1, y1, x2, y2 = bounds(node)
    shell('input', 'swipe', (x1 + x2) // 2, y1 + int((y2 - y1) * .78),
          (x1 + x2) // 2, y1 + int((y2 - y1) * .25), 350)
    time.sleep(.6)


def capture(name):
    path = SCREENS / (name + '.png')
    path.write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    subprocess.run([sys.executable, 'tools/ci/check_screen.py', str(path)], check=True, timeout=30)
    return str(path)


def launch(label):
    log = shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity',
                '--es', 'qa_screen', 'places', '--es', 'qa_lang', 'en', '--ez', 'qa_onboarded', 'true')
    (OUT / (label + '-start.txt')).write_text(log)
    time.sleep(2)
    assert shell('pidof', PKG).strip(), 'App did not remain alive'
    tree = ui(label + '-list')
    # Give the list the full available height; this toggle is real production UI.
    if node_with_text(tree, 'Hide map') is not None:
        tap(tree, 'Hide map')
        tree = ui(label + '-list-map-hidden')
    return tree


def check_card(variant, kind, expected):
    label = 'places-fixture-' + variant + '-' + kind
    tree = launch(label)
    name = 'CI Complete Prayer Room' if kind == 'complete' else 'CI Legacy Prayer Room'
    assert any('saved copy' in text for text in texts(tree)), 'Saved-copy status is not visible'
    tap(tree, name)
    observed, evidence, shots, encounters = {}, {}, [], {field: [] for field in FIELDS}
    action_found = False
    for step in range(8):
        page = label + '-' + str(step)
        tree = ui(page)
        page_texts = texts(tree)
        for index, text in enumerate(page_texts):
            if text in expected and index + 1 < len(page_texts):
                value = page_texts[index + 1]
                encounters[text].append(value)
                if value == expected[text]:
                    observed[text] = value
                    evidence[text] = page + '.xml'
        shots.append(capture(page))
        if node_with_text(tree, 'Information has changed') is not None:
            action_found = True
        if len(observed) == len(expected) and action_found:
            break
        swipe_list(tree)
    assert observed == expected, {'variant': variant, 'card': kind, 'expected': expected,
                                  'observed': observed, 'encounters': encounters}
    assert action_found, 'Correction action not reachable in expanded card'
    if kind == 'complete':
        # Opening the explanatory dialog is not the external bot/open-link action.
        tap(tree, 'Information has changed')
        dialog = ui(label + '-correction-dialog')
        assert any('Opening it does not submit a request' in text for text in texts(dialog))
        assert node_with_text(dialog, 'Open bot') is not None
        shots.append(capture(label + '-correction-dialog'))
        tap(dialog, 'Close')
    return {'card': kind, 'fields': observed, 'evidence_ui': evidence, 'screenshots': shots,
            'correction_dialog_only': kind == 'complete', 'external_actions': 0}


def setting(namespace, name):
    return shell('settings', 'get', namespace, name).strip()


def override(text, label):
    match = re.search(r'Override ' + label + r':\s*(\S+)', text)
    return match.group(1) if match else None


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    SCREENS.mkdir(parents=True, exist_ok=True)
    assert shell('getprop', 'ro.kernel.qemu').strip() == '1', 'Disposable emulator required'
    assert shell('run-as', PKG, 'id').strip(), 'Debuggable app required'
    shell('am', 'force-stop', PKG)
    raw = adb('exec-out', 'run-as', PKG, 'cat', PREF, check=False, binary=True)
    original = raw if raw.startswith(b'<?xml') or raw.lstrip().startswith(b'<map') else None
    root = ET.fromstring(original) if original else ET.Element('map')
    before = {
        'font_scale': setting('system', 'font_scale'), 'wifi_on': setting('global', 'wifi_on'),
        'mobile_data': setting('global', 'mobile_data'), 'airplane': setting('global', 'airplane_mode_on'),
        'size': override(shell('wm', 'size'), 'size'), 'density': override(shell('wm', 'density'), 'density'),
        'night': shell('cmd', 'uimode', 'night').strip(),
    }
    (OUT / 'fixture.json').write_text(json.dumps(FIXTURE, indent=2))
    results = []
    try:
        shell('cmd', 'connectivity', 'airplane-mode', 'enable')
        shell('svc', 'wifi', 'disable')
        shell('svc', 'data', 'disable')
        time.sleep(1)
        assert setting('global', 'airplane_mode_on') == '1'
        assert setting('global', 'wifi_on') == '0'
        assert setting('global', 'mobile_data') == '0'
        put_pref(root, 'places_json', 'string', json.dumps(FIXTURE, ensure_ascii=False))
        # Avoid an HTTP refresh altogether while checking the preserved-cache UI.
        put_pref(root, 'places_at', 'string', int(shell('date', '+%s').strip()) * 1000 + 3600000)
        put_pref(root, 'places_etag', 'string', '')
        put_pref(root, 'onboarded', 'boolean', True)
        put_pref(root, 'online_times', 'boolean', False)
        put_pref(root, 'blocking', 'boolean', False)
        put_pref(root, 'alarm_on', 'boolean', False)
        put_pref(root, 'lat', 'float', 55.75)
        put_pref(root, 'lon', 'float', 37.6167)
        write_prefs(ET.tostring(root, encoding='utf-8', xml_declaration=True), 'synthetic-prefs.xml')
        for variant, night, font, narrow, kind, expected in (
            ('light', 'no', '1', False, 'complete', COMPLETE),
            ('dark-narrow-large', 'yes', '1.3', True, 'legacy', LEGACY),
        ):
            shell('cmd', 'uimode', 'night', night)
            shell('settings', 'put', 'system', 'font_scale', font)
            shell('wm', 'size', '720x1280' if narrow else 'reset')
            shell('wm', 'density', '320' if narrow else 'reset')
            results.append(check_card(variant, kind, expected))
        actual = ET.fromstring(adb('exec-out', 'run-as', PKG, 'cat', PREF))
        cached = next(node.text for node in actual if node.attrib.get('name') == 'places_json')
        assert json.loads(cached) == FIXTURE, 'Synthetic cache changed during offline interaction'
        summary = {'synthetic_emulator_only': True, 'offline': True, 'live_api_verified': False,
                   'live_map_tiles_verified': False, 'external_bot_opened': False,
                   'corrections_submitted': 0, 'cache_unchanged': True, 'cases': results}
        (OUT / 'passed.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2))
        print('Places: 2 expanded synthetic cards, 8 field assertions each, no external actions.')
    finally:
        shell('am', 'force-stop', PKG, check=False)
        if original is not None:
            write_prefs(original, 'original-prefs.xml')
        else:
            shell('run-as', PKG, 'rm', '-f', PREF, PREF + '.bak', check=False)
        shell('settings', 'put', 'system', 'font_scale', before['font_scale'] if before['font_scale'] != 'null' else '1', check=False)
        shell('wm', 'size', before['size'] or 'reset', check=False)
        shell('wm', 'density', before['density'] or 'reset', check=False)
        night = re.search(r'\b(yes|no|auto|custom)\b', before['night'])
        if night:
            shell('cmd', 'uimode', 'night', night.group(1), check=False)
        shell('cmd', 'connectivity', 'airplane-mode', 'enable' if before['airplane'] == '1' else 'disable', check=False)
        shell('svc', 'wifi', 'enable' if before['wifi_on'] in ('1', '2') else 'disable', check=False)
        shell('svc', 'data', 'enable' if before['mobile_data'] == '1' else 'disable', check=False)


if __name__ == '__main__':
    main()
