"""Capture the installed Android screen with real taps. CDP only reads the visible WebView."""
from contextlib import closing, contextmanager
import json
import os
import shutil
import signal
import subprocess
import tempfile
import time
import traceback
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
import websocket
from PIL import Image, ImageChops
from scipy.signal import correlate, find_peaks

# The shared Android test helpers precede that module's command-line dispatcher.
exec(compile(Path('tools/ci/features_controls.py').read_text().split('mode = sys.argv[1]')[0],
             'features_helpers', 'exec'))

MEDIA = OUT / 'tasbih'
MEDIA.mkdir(parents=True, exist_ok=True)
SAMPLE = MEDIA / 'tasbih-bead.wav'
shutil.copyfile('app/src/main/res/raw/tasbih_bead.wav', SAMPLE)
MODE = sys.argv[1]
assert MODE in ('record', 'controls')
REPORT = {'source_commit': os.environ.get('GITHUB_SHA'), 'mode': MODE}
W, H = 720, 1280


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, timeout=60, **kwargs)


def start(route):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_screen', route,
          '--es', 'qa_lang', 'ru', '--ez', 'qa_onboarded', 'true')


def seed(count, goal=100):
    root = prefs_tree()
    fixture = {'tasbih_phrase': 4, 'tasbih_target': goal, 'tasbih_count': count}
    if goal == 34:
        fixture['tasbih_day_2026-01-01'] = 72
    for key, val in fixture.items():
        for entry in list(root):
            if entry.get('name') == key:
                root.remove(entry)
        ET.SubElement(root, 'int', name=key, value=str(val))
    write_tree(root)


def connect_scene():
    deadline = time.monotonic() + 45
    last = None
    while time.monotonic() < deadline:
        try:
            pid = shell('pidof', PKG).strip().split()[0]
            adb('forward', 'tcp:19222', 'localabstract:webview_devtools_remote_' + pid)
            with urllib.request.urlopen('http://127.0.0.1:19222/json/list', timeout=3) as response:
                targets = json.load(response)
            page = next(t for t in targets if 'tasbih/scene.html' in t.get('url', ''))
            return websocket.create_connection(page['webSocketDebuggerUrl'], suppress_origin=True, timeout=6)
        except (OSError, ValueError, StopIteration, IndexError, subprocess.SubprocessError,
                websocket.WebSocketException) as exc:
            last = exc
            time.sleep(.3)
    raise RuntimeError('The installed debug WebView did not expose the tasbih scene') from last


def read_js(socket, expression, await_promise=False):
    socket.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate',
                            'params': {'expression': expression, 'returnByValue': True, 'awaitPromise': await_promise}}))
    while True:
        response = json.loads(socket.recv())
        if response.get('id') == 1:
            return response['result']['result'].get('value')


def visible_count(socket, expected):
    deadline = time.monotonic() + 9
    while time.monotonic() < deadline:
        value = read_js(socket, "document.querySelector('#count')?.innerText?.trim()")
        if value == str(expected):
            return
        time.sleep(.05)
    raise AssertionError(f'Expected visible {expected}, saw {value!r}')


def screenshot(path):
    with path.open('wb') as out:
        run('adb', 'exec-out', 'screencap', '-p', stdout=out)


def scene_pixels(path):
    # Check physical pixels from three separate layers of the real Android window.
    with Image.open(path) as frame:
        w, h = frame.size
        def bright(x, y, right, bottom, percentile):
            region = frame.crop((int(w*x), int(h*y), int(w*right), int(h*bottom))).convert('RGB')
            return round(float(np.percentile(np.asarray(region).max(axis=2), percentile)), 1)
        return {'sun': bright(.42,.89,.58,.96,97), 'beads': bright(.40,.51,.60,.59,96),
                'arabic': bright(.10,.15,.90,.31,99.5)}


def scene_diagnostics(socket):
    state = read_js(socket, """(() => {
      const box = node => Array.from(['x','y','width','height'], key => node.getBoundingClientRect()[key]);
      const center = (canvas, x, y) => Array.from(canvas.getContext('2d').getImageData(
        Math.max(0, Math.floor(canvas.width*x)), Math.max(0, Math.floor(canvas.height*y)),1,1).data);
      return {
        hidden: document.hidden, ready: document.readyState, dpr: devicePixelRatio,
        root: box(document.documentElement), body: box(document.body),
        viewport: [innerWidth,innerHeight,document.documentElement.clientWidth,document.documentElement.clientHeight],
        visualViewport: [visualViewport?.width,visualViewport?.height,visualViewport?.scale],
        bodyUnit: getComputedStyle(document.body).getPropertyValue('--u'),
        Arabic: box(document.getElementById('arabic')), count: box(document.getElementById('count')),
        canvas: Array.from(document.querySelectorAll('canvas[id]'), c => ({
          id:c.id, size:[c.width,c.height], box:box(c),
          middle:center(c,.5,.5), sun:center(c,.5,.925)
        })),
        resources:performance.getEntriesByType('resource').map(entry =>
          [entry.name.split('/').pop(), Math.round(entry.duration),entry.decodedBodySize])
      };
    })()""")
    # No scene state is changed by measuring two animation-frame callbacks.
    try:
        state['raf_elapsed_ms'] = read_js(socket, """new Promise(resolve => {
          const t=performance.now();
          setTimeout(()=>resolve(null), 700);
          requestAnimationFrame(()=>requestAnimationFrame(()=>resolve(Math.round(performance.now()-t))));
        })""", await_promise=True)
    except (OSError, websocket.WebSocketException):
        state['raf_elapsed_ms'] = 'timed out'
    return state


def wait_for_drawn_scene(socket, path):
    started = time.monotonic()
    checks = []
    while time.monotonic() - started < 25:
        screenshot(path)
        metrics = scene_pixels(path)
        checks.append(dict(after=round(time.monotonic()-started, 2), **metrics))
        if metrics['sun'] > 205 and metrics['beads'] > 125 and metrics['arabic'] > 150:
            REPORT['scene_pixels'] = checks[-1]
            return
        if len(checks) == 1:
            (MEDIA / 'first-viewport.json').write_text(json.dumps(scene_diagnostics(socket), indent=2))
        # The Pixel Launcher can show its own ANR over an otherwise healthy app
        # during cold boot. The shared UI helper dismisses that named dialog only.
        if 'Application Not Responding' in shell('dumpsys', 'window', 'windows') or len(checks) == 2:
            ui()
        time.sleep(.65)
    shutil.copyfile(path, MEDIA / 'scene-before-touch.png')
    (MEDIA / 'blank-viewport.json').write_text(json.dumps({
        'screenshots': checks, 'webview':scene_diagnostics(socket)
    }, indent=2))
    raise AssertionError(f'Android did not paint the sun, beads and selected zikr within 25 s: {checks[-1]}')


def decode_audio(path):
    result = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-vn', '-map', '0:a:0',
                             '-af', 'aresample=12000:async=1:first_pts=0', '-ac', '1', '-ar', '12000', '-f', 'f32le', '-'], capture_output=True)
    return np.frombuffer(result.stdout, dtype='<f4') if result.returncode == 0 else np.array([])


def impact_times(path):
    waveform = decode_audio(path)
    sample = decode_audio(SAMPLE)
    if len(waveform) < len(sample) or np.max(np.abs(waveform)) < .0001:
        return []
    match = np.abs(correlate(waveform, sample, mode='valid', method='fft'))
    times, _ = find_peaks(match, distance=round(.1 * 12000), height=np.max(match) * .30)
    return [round(float(t) / 12000, 3) for t in times]


def numeral_changes(path):
    # Sample only the white numeral inside the dial, excluding the changing orange arc.
    data = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(path), '-an', '-vf',
        'fps=30:start_time=0,scale=360:640,crop=52:29:154:411,format=gray', '-f', 'rawvideo', '-'])
    masks = np.frombuffer(data, dtype=np.uint8).reshape(-1, 29, 52) > 204
    assert masks[0].sum() > 30, 'Missing initial numeral in the device capture'
    base = masks[0]
    times = []
    for i in range(1, len(masks)-1):
        if np.sum(base != masks[i]) >= 15 and np.sum(base != masks[i+1]) >= 15:
            times.append(round(i/30, 3))
            base = masks[i+1]
    return times


def longest_blank_interval(movie):
    # Use the actual muxed video timeline. The crop excludes native navigation
    # icons and bars so they cannot disguise a nearly uniform dark WebView.
    fps = 20
    pixels = subprocess.check_output(['ffmpeg','-v','error','-i',str(movie),'-an','-vf',
        f'fps={fps}:start_time=0,scale=80:144:flags=area,crop=72:112:4:16','-pix_fmt','rgb24','-f','rawvideo','-'])
    frames = np.frombuffer(pixels,dtype=np.uint8).reshape(-1,72*112,3)
    assert len(frames), 'The entrance recording has no decodable frames'
    flat = []
    for frame in frames:
        median = np.median(frame, axis=0)
        contrast = np.max(np.abs(frame-median),axis=1)
        flat.append(median.max() < 75 and np.mean(contrast > 10) < .02)
    # Difference across two padded endpoints covers runs at either end of the clip.
    changes = np.flatnonzero(np.diff(np.r_[False,flat,False])).reshape(-1,2)
    start,end = max(changes,key=lambda pair: pair[1]-pair[0],default=(0,0))
    return {'duration_seconds': round((int(end)-int(start))/fps,2),
            'start_seconds': int(start)/fps if end else None, 'end_seconds': int(end)/fps if end else None,
            'analysis_fps': fps}


@contextmanager
def device_recording(temp, name):
    # Both encoders run in the guest. scrcpy preserves their device presentation
    # timestamps when muxing. Host audio and wall-clock video are never combined.
    movie = MEDIA / (name + '.mp4')
    raw = temp / (name + '-guest.mp4')
    log = MEDIA / (name + '-recorder.txt')
    with log.open('w') as journal:
        recorder = subprocess.Popen(['stdbuf', '-oL', '-eL', os.environ['SCRCPY_BIN'], '--no-playback', '--no-window', '--no-control',
            '--require-audio', '--audio-codec=aac', '--audio-source=output', '--video-codec=h264',
            '--max-size=960', '--max-fps=30', '--video-bit-rate=4M', '--audio-bit-rate=192K',
            '--record=' + str(raw)], stdout=journal, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic()+35
            while not (raw.exists() and raw.stat().st_size > 0) and 'Recording started to' not in log.read_text(errors='replace'):
                if recorder.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError('Android recorder did not start: ' + log.read_text()[-1600:])
                time.sleep(.1)
            yield
        finally:
            if recorder.poll() is None:
                recorder.send_signal(signal.SIGINT)
            recorder.wait(timeout=15)
            if sys.exc_info()[0] is not None and raw.is_file():
                shutil.copyfile(raw, movie)
    assert recorder.returncode == 0, log.read_text()[-1600:]
    run('ffmpeg','-loglevel','error','-y','-i',raw,'-map','0:v:0','-map','0:a:0','-c','copy',
        '-movflags','+faststart',movie)


def record(socket, temp):
    movie = MEDIA / 'tasbih.mp4'
    with device_recording(temp, 'tasbih'):
        # Device-side input includes one fast swipe through the text and dial,
        # one planet tap, one sky tap and four separate quick bead taps.
        # Do not synchronously screenshot, inspect DOM or poll preferences while tapping.
        shell('sleep 1.15; input swipe 360 116 360 1190 90; sleep .78; '
              'input tap 360 1178; sleep .78; input tap 88 870; sleep .78; '
              'for i in 1 2 3 4; do input tap 360 704; sleep .06; done')
        visible_count(socket, 33)
        time.sleep(1.35)
    changes = numeral_changes(movie)
    strikes = impact_times(movie)
    lags = [round(s-c, 3) for s,c in zip(strikes,changes)]
    REPORT.update(audio_source='Android internal playback captured via REMOTE_SUBMIX',
        visible_transitions_seconds=changes, captured_impact_times_seconds=strikes,
        sound_delay_seconds=lags, all_seven_sounds_verified=len(strikes)==7)
    if changes:
        run('ffmpeg','-v','error','-y','-ss',str(changes[0]+.10),'-i',movie,'-frames:v','1', MEDIA/'tasbih.png')
    assert len(changes) == len(strikes) == 7, ('Expected seven visible counts and bead sounds', changes,strikes)
    # 30 fps sampling and guest audio buffering permit some jitter around the
    # renderer's measured 160 ms delay. Gross clock skew must fail this check.
    assert all(-.05 <= lag <= .45 for lag in lags) and .03 <= np.median(lags) <= .35, \
        ('Captured sound is out of sync with the visible numbers', lags)
    REPORT['audio_sync_verified'] = True


def wait_for_entrance(scene):
    deadline = time.monotonic() + 6
    while time.monotonic() < deadline:
        if read_js(scene, "document.body.dataset.entry") == 'ready':
            return
        time.sleep(.05)
    raise AssertionError('Entrance did not finish on its own')


def tap_scene_text(scene, label_id, hit_id):
    # A real touch at the painted glyphs. Querying read-only bounds avoids a
    # passing test when an invisible button is positioned elsewhere.
    point = read_js(scene, f"""(() => {{
      const r=document.getElementById('{label_id}').getBoundingClientRect();
      const x=r.left+r.width/2, y=r.top+r.height/2;
      return [x/document.documentElement.clientWidth,y/document.documentElement.clientHeight,
              document.elementFromPoint(x,y)?.closest('button')?.id];
    }})()""")
    assert point and point[2] == hit_id, ('The displayed text has no matching hit target',point)
    shell('input','tap',round(W*point[0]),round(H*point[1]))


def native_center(node):
    x,y,right,bottom = map(int,re.findall(r'\d+', node.get('bounds')))
    return (x+right)//2, (y+bottom)//2


def close_quick_settings(scene):
    done = locate_control('tasbih_quick_done')
    shell('input','tap',*native_center(done))
    # A Compose modal can still consume touches while its exit animates. Wait for
    # the underlying native activity's controls and WebView, not just the DOM counter.
    deadline = time.monotonic()+18
    while time.monotonic() < deadline:
        tree = ui()
        if find_control(tree,'tasbih_quick_done') is None and find_control(tree,'tasbih_settings') is not None:
            ready = read_js(scene, """(() => {
                const b=document.querySelector('#beads');
                return !document.hidden && !b.disabled && document.body.dataset.entry==='ready'
                  && document.elementFromPoint(document.documentElement.clientWidth*.5,
                       document.documentElement.clientHeight*.55)===b;
            })()""")
            if ready:
                return
        time.sleep(.12)
    raise AssertionError('The native quick sheet still owned the window, or the bead hit area was unavailable')


def tap_bead_after_settings(scene):
    # Passive observers are diagnostic only. Count and sound must come from one
    # real Android tap after the modal has relinquished the window.
    read_js(scene, """(() => {
        window.__beadTapTrace = [];
        for (const name of ['pointerdown','pointerup','click'])
          document.addEventListener(name,e=>window.__beadTapTrace.push([e.type,e.target.id,e.clientX,e.clientY]),
                                    {once:true,capture:true,passive:true});
    })()""")
    shell('input','tap',360,704)
    try:
        visible_count(scene,1)
    finally:
        REPORT['bead_input_after_settings'] = {
            'events': read_js(scene,'window.__beadTapTrace'),
            'saved_count':prefs().get('tasbih_count')
        }


def record_entrance(temp):
    # Restore a legitimate zero on the disposable profile and film the native
    # route exactly as a person would open it. Settings do not reset the count.
    seed(0)
    start('more')
    row = None
    for _ in range(8):
        tree = ui()
        # Compose can merge the row title and its short explanation for TalkBack.
        row = next((n for n in tree.iter('node')
                    if 'Тасбих' in n.get('text','').splitlines()
                    and n.get('bounds','[0,0][0,0]') != '[0,0][0,0]'), None)
        if row is not None:
            break
        shell('input','swipe',42,1010,42,355,370)
        time.sleep(.25)
    with device_recording(temp, 'tasbih-entry'):
        time.sleep(.65)
        if row is not None:
            shell('input','tap',*native_center(row))
        else:
            # Use the app's existing debug route if this launcher layout cannot expose
            # the row. The same live screen opens inside the already-running Activity.
            shell('am','start','-W','-n',PKG+'/.MainActivity','--es','qa_screen','tasbih',
                  '--es','qa_lang','ru','--ez','qa_onboarded','true')
        with closing(connect_scene()) as scene:
            visible_count(scene,0)
            wait_for_entrance(scene)
            time.sleep(.65)
            tap_scene_text(scene,'count','dial-control')
            time.sleep(.7)
            goal = locate_control('tasbih_target_33')
            screenshot(MEDIA / 'tasbih-quick-settings.png')
            assert prefs()['tasbih_count'] == '0'
            shell('input','tap',*native_center(goal))
            assert prefs()['tasbih_target']=='33'
            close_quick_settings(scene)
            tap_bead_after_settings(scene)
            time.sleep(.63)
            tap_scene_text(scene,'count','dial-control')
            time.sleep(.85)
            close_quick_settings(scene)
            visible_count(scene,1)
    state = prefs()
    assert state['tasbih_count']=='1' and state['tasbih_target']=='33',state.get('tasbih_count')
    movie = MEDIA / 'tasbih-entry.mp4'
    blank = longest_blank_interval(movie)
    REPORT['entry_longest_blank'] = blank
    assert blank['duration_seconds'] <= .30, ('Visible uniform dark hold during tasbih entry',blank)
    impacts = impact_times(movie)
    assert len(impacts)==1, ('Two settings opens and one bead should produce exactly one bead impact',impacts)
    REPORT.update(entry_route='More → Tasbih' if row is not None else 'in-process qa_screen', entry_animation_scale=1, entry_impact_seconds=impacts,
                  zero_and_positive_open_preserved_count=True)


def exercise():
    shell('svc', 'power', 'stayon', 'true')
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    shell('wm', 'size', f'{W}x{H}')
    shell('wm', 'density', '320')
    shell('settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
    shell('settings', 'put', 'global', 'animator_duration_scale', '1' if MODE=='record' else '0')
    shell('settings', 'put', 'system', 'sound_effects_enabled', '0')
    shell('cmd', 'media_session', 'volume', '--stream', '3', '--set', '13')
    adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    start('home')
    time.sleep(2)
    if MODE == 'record':
        seed(26)
    else:
        # SharedPreferences written by build 47 with manually selected goal 34.
        seed(33, goal=34)
    start('tasbih')
    if MODE=='controls':
        exercise_controls()
        return
    with closing(connect_scene()) as scene, tempfile.TemporaryDirectory() as directory:
        temp = Path(directory)
        visible_count(scene, 26)
        wait_for_entrance(scene)
        initial = read_js(scene, "['arabic','transcription','translation','goal'].map(k => document.getElementById(k)?.textContent?.trim())")
        assert all(initial) and '100' in initial[-1], initial
        assert 'прощен' in initial[2].lower().replace('ё', 'е'), initial
        still = temp / 'idle.png'
        wait_for_drawn_scene(scene, still)
        time.sleep(1.2)
        later = temp / 'idle-later.png'
        screenshot(later)
        with Image.open(still) as before, Image.open(later) as after:
            w, h = before.size
            diff = ImageChops.difference(before.convert('RGB'), after.convert('RGB'))
            if not diff.crop((0, int(.84*h), w, int(.97*h))).getbbox():
                shutil.copyfile(still, MEDIA / 'idle-a.png')
                shutil.copyfile(later, MEDIA / 'idle-b.png')
                (MEDIA / 'frozen-viewport.json').write_text(json.dumps(scene_diagnostics(scene), indent=2))
                raise AssertionError('Planets did not move during idle')
        visible_count(scene, 26)
        record(scene, temp)
        assert prefs()['tasbih_count'] == '33'
    shell('input', 'keyevent', 'KEYCODE_BACK')
    start('tasbih')
    with closing(connect_scene()) as scene:
        visible_count(scene, 33)
    with tempfile.TemporaryDirectory() as directory:
        record_entrance(Path(directory))
    REPORT.update(verified=True, initial=26, full_height_swipe_and_surface_taps=[27,28,29], rapid_result=33,
                  idle_planets_moved=True, restored=33)


def exercise_surface(scene):
    # Coordinates are physical pixels of the 720×1280 Android window. The slow
    # swipe crosses many former bead steps, the fast swipe crosses both labels.
    gestures = {
        'top_sky': ('tap', 360, 108),
        'planet': ('tap', 360, 1180),
        'beside_dial': ('tap', 105, 870),
        'beside_title': ('tap', 634, 302),
        'long_swipe': ('swipe', 350, 610, 350, 1180, 640),
        'fast_full_height_swipe': ('swipe', 360, 116, 360, 1190, 70),
    }
    state = prefs()
    def daily_total(snapshot):
        return sum(int(value) for key,value in snapshot.items() if key.startswith('tasbih_day_'))
    initial_daily = daily_total(state)
    results = {}
    for expected,(label,event) in enumerate(gestures.items(), 1):
        shell('input', *event)
        visible_count(scene, expected)
        # Reading the stored counter after the visible transition also catches
        # extra increments that the animation may still have queued.
        state = prefs()
        assert int(state['tasbih_count']) == expected, (label, expected, state['tasbih_count'])
        assert daily_total(state) == initial_daily + expected, ('Unexpected daily count', label)
        results[label] = expected
    REPORT['surface_counts'] = results
    return len(gestures)


def exercise_controls():
    with closing(connect_scene()) as scene:
        visible_count(scene, 33)
        wait_for_entrance(scene)
        # A legacy target changes to 33 at startup; saved count, selected phrase
        # and daily totals must survive the migration.
        state = prefs()
        assert (state['tasbih_target'],state['tasbih_count'],state['tasbih_phrase'],
                state['tasbih_day_2026-01-01']) == ('33','33','4','72')
        assert read_js(scene,"document.getElementById('goal').textContent.trim()")=='/ 33'
        tap_scene_text(scene,'count','dial-control')
        locate_control('tasbih_target_33')
        options = ui()
        assert find_control(options, 'tasbih_target_34') is None
        for goal in (33,99,100,0):
            tag = 'tasbih_target_' + str(goal)
            if find_control(options,tag) is None:
                locate_control(tag)
        assert prefs()['tasbih_count']=='33'
        tap_control('tasbih_target_100')
        close_quick_settings(scene)
        visible_count(scene,33)
        assert prefs()['tasbih_target']=='100'
        name = read_js(scene,"document.getElementById('transcription').textContent")
        tap_scene_text(scene,'transcription','phrase-control')
        tap_control('tasbih_phrase_0')
        close_quick_settings(scene)
        visible_count(scene,0)
        assert read_js(scene,"document.getElementById('transcription').textContent") != name
    start('tasbih')
    with closing(connect_scene()) as scene:
        visible_count(scene,0)
        wait_for_entrance(scene)
        state=prefs()
        assert (state['tasbih_phrase'],state['tasbih_target'])==('0','100')
        assert read_js(scene,"document.getElementById('goal').textContent.trim()")=='/ 100'
        # With system motion disabled the idle planet strip must stay still.
        with tempfile.TemporaryDirectory() as directory:
            a,b=Path(directory)/'still.png',Path(directory)/'later.png'
            wait_for_drawn_scene(scene,a)
            time.sleep(.7)
            screenshot(b)
            with Image.open(a) as first,Image.open(b) as last:
                delta=ImageChops.difference(first.convert('RGB'),last.convert('RGB'))
                assert not delta.crop((0,int(H*.84),W,int(H*.97))).getbbox(), 'Reduced motion kept moving the planets'
        counted = exercise_surface(scene)
        tap_control('tasbih_settings')
        assert find_control(ui(), 'tasbih_target_34') is None
        tap_control('tasbih_sound')
        assert prefs()['tasbih_bead_sound'] == 'false'
        tap_control('tasbih_sound')
        assert prefs()['tasbih_bead_sound'] == 'true'
        tap_control('tasbih_target_99')
        assert prefs()['tasbih_target'] == '99' and int(prefs()['tasbih_count']) == counted
        tap_control('tasbih_reset')
        tap_control('tasbih_reset_cancel')
        visible_count(scene, counted)
        tap_control('tasbih_settings')
        tap_control('tasbih_reset')
        tap_control('tasbih_reset_confirm')
        visible_count(scene, 0)
    assert prefs()['tasbih_count'] == '0'
    start('tasbih')
    with closing(connect_scene()) as scene:
        visible_count(scene, 0)
        assert '99' in read_js(scene, "document.getElementById('goal').innerText")
    REPORT.update(verified=True, saved_33_open=True, phrase_open=True, removed_goal_absent=True,
                  legacy_goal_migrated_to_33=True, stored_count_and_daily_preserved=True,
                  restored_phrase_and_goal_100=True, reduced_motion_idle_still=True,
                  reset_confirmed_after_restart=True, target_after_restart=99)


try:
    exercise()
except Exception:
    (MEDIA / (MODE + '-failure.txt')).write_text(traceback.format_exc())
    screenshot(MEDIA / 'failure.png')
    for name, args in (
        ('surfaces', ['dumpsys','SurfaceFlinger','--list']),
        ('activity', ['dumpsys','activity','top']),
        ('frames', ['dumpsys','gfxinfo',PKG]),
        ('webview-provider', ['dumpsys','webviewupdate']),
    ):
        (MEDIA / (name + '.txt')).write_text(shell(*args))
    pid = shell('pidof', PKG).strip().split()
    if pid:
        (MEDIA / 'app-log.txt').write_text(adb('logcat', '-d', '--pid=' + pid[0]))
    (MEDIA / 'chromium-log.txt').write_text(adb('logcat','-d','chromium:V','WebViewFactory:I',
                                                  'OpenGLRenderer:W','HWUI:W','EGL_emulation:W','*:S'))
    raise
finally:
    (MEDIA / ('verification.json' if MODE=='record' else 'controls.json')).write_text(json.dumps(REPORT, ensure_ascii=False, indent=2))
    (MEDIA / (MODE + '-android-log.txt')).write_text(adb('logcat', '-d', '-t', '1000'))
