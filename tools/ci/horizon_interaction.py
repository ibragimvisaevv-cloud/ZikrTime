"""Exercise the real scroll surface, including gestures starting on the landscape.
No scroll-position test hook: captures come from ordinary Android touch input.
"""
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

OUT = Path('ci-out')
PKG = 'com.zikrtime.app'

def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], timeout=45)

def shell(*args):
    return adb('shell', *args)

def launch(lang='ru', hide=False):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity',
          '--es', 'qa_screen', 'home', '--es', 'qa_lang', lang,
          '--ez', 'qa_onboarded', 'true', '--es', 'qa_period', 'DAY',
          '--ez', 'qa_hide_scene', str(hide).lower())
    time.sleep(2)

def shot(name):
    path = OUT / 'screens' / (name + '.png')
    path.write_bytes(adb('exec-out', 'screencap', '-p'))
    return Image.open(path).size

def tree(name):
    deadline = time.monotonic() + 30
    while True:
        shell('rm', '-f', '/sdcard/horizon.xml')
        shell('uiautomator', 'dump', '/sdcard/horizon.xml')
        try:
            raw = shell('cat', '/sdcard/horizon.xml')
            root = ET.fromstring(raw)
            (OUT / (name + '.xml')).write_bytes(raw)
            return root
        except (subprocess.CalledProcessError, ET.ParseError):
            if time.monotonic() > deadline:
                raise
            time.sleep(1)

def node(root, tag):
    return next((n for n in root.iter('node') if n.get('resource-id', '').endswith(tag)), None)

def bounds(n):
    assert n is not None
    return tuple(map(int, re.findall(r'-?\d+', n.get('bounds'))))

def gesture(w, h, start, end, ms=1100):
    shell('input', 'swipe', int(w * .36), int(h * start), int(w * .36), int(h * end), ms)
    time.sleep(.7)

proof = {}
shell('wm', 'size', '1080x2400')
shell('wm', 'density', '420')
shell('settings', 'put', 'system', 'font_scale', '1')
shell('settings', 'put', 'global', 'animator_duration_scale', '1')
for mode in ['light', 'dark']:
    shell('cmd', 'uimode', 'night', 'yes' if mode == 'dark' else 'no')
    launch()
    w, h = shot('horizon-open-' + mode)
    initial = tree('horizon-open-' + mode)
    initial_top = bounds(node(initial, 'horizon_panel'))[1]
    assert initial_top > h * .60, ('scene not open', initial_top, h)
    assert node(initial, 'theme_quick_toggle') is not None
    # Start the gesture in the scene, above the sheet.
    gesture(w, h, .61, .35)
    shot('horizon-middle-' + mode)
    middle = tree('horizon-middle-' + mode)
    middle_top = bounds(node(middle, 'horizon_panel'))[1]
    assert middle_top < initial_top - h * .12, ('scene swipe did not move sheet', middle_top, initial_top)
    # Continue on the sheet itself; no second drag recognizer or hand-off.
    gesture(w, h, .72, .31)
    shot('horizon-collapsed-' + mode)
    collapsed = tree('horizon-collapsed-' + mode)
    assert node(collapsed, 'horizon_compact') is not None, 'missing compact next prayer'
    gesture(w, h, .27, .81)
    gesture(w, h, .27, .65)
    shot('horizon-return-' + mode)
    returned = tree('horizon-return-' + mode)
    assert abs(bounds(node(returned, 'horizon_panel'))[1] - initial_top) < h * .03, 'scene failed to reopen'
    proof[mode] = {'scene_drag': True, 'sheet_drag': True, 'compact_header': True, 'return': True,
                   'open_panel_y': initial_top, 'middle_panel_y': middle_top, 'size': [w, h]}

# Real gesture recording, with start, rise, pause, and return. No postproduced animation.
shell('cmd', 'uimode', 'night', 'no')
launch()
record = subprocess.Popen(['adb', 'shell', 'screenrecord', '--size', '720x1600', '--time-limit', '16',
                           '--bit-rate', '2500000', '/sdcard/horizon-scroll.mp4'],
                          stdout=(OUT / 'horizon-record.txt').open('w'), stderr=subprocess.STDOUT)
time.sleep(2)
shell('dumpsys', 'gfxinfo', PKG, 'reset')
gesture(w, h, .61, .35, 1700)
time.sleep(1)
gesture(w, h, .72, .31, 1700)
time.sleep(1)
gesture(w, h, .27, .81, 1700)
gesture(w, h, .27, .65, 1400)
record.wait(timeout=30)
if record.returncode == 0:
    adb('pull', '/sdcard/horizon-scroll.mp4', str(OUT / 'horizon-scroll.mp4'))
else:
    (OUT / 'recording-unavailable.txt').write_text('Android screenrecord failed. Static captures do not verify motion quality.')
(OUT / 'horizon-scroll-gfx.txt').write_bytes(shell('dumpsys', 'gfxinfo', PKG))

# Composition without illustrations, plus small screen / large text / long translations.
launch(hide=True)
shot('horizon-no-illustration')
for lang, scale in [('ru', '2'), ('az', '1.3'), ('tr', '1.3')]:
    shell('wm', 'size', '720x1280')
    shell('wm', 'density', '360')
    shell('settings', 'put', 'system', 'font_scale', scale)
    launch(lang)
    w, h = shot('horizon-small-' + lang)
    gesture(w, h, .72, .22)
    gesture(w, h, .72, .22)
    shot('horizon-small-collapsed-' + lang)
shell('settings', 'put', 'system', 'font_scale', '1')
shell('wm', 'size', '1080x2400')
shell('wm', 'density', '420')
shell('settings', 'put', 'global', 'animator_duration_scale', '0')
launch()
w, h = shot('horizon-reduced-motion')
gesture(w, h, .72, .22)
gesture(w, h, .72, .22)
assert node(tree('horizon-reduced-collapsed'), 'horizon_compact') is not None
proof['reduced_motion_scroll'] = True
shell('settings', 'put', 'global', 'animator_duration_scale', '1')
(OUT / 'horizon-interaction-passed.json').write_text(json.dumps(proof, indent=2))
