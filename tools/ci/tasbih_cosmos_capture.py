"""Capture the installed Android screen with real taps. CDP only reads the visible WebView."""
from contextlib import closing
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
REPORT = {'source_commit': os.environ.get('GITHUB_SHA'), 'mode': MODE, 'tap_count': 7}
W, H = 720, 1280


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, timeout=60, **kwargs)


def start(route):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_screen', route,
          '--es', 'qa_lang', 'ru', '--ez', 'qa_onboarded', 'true')


def seed(count):
    root = prefs_tree()
    for key, val in {'tasbih_phrase': 4, 'tasbih_target': 100, 'tasbih_count': count}.items():
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


def record(socket, temp):
    # Both encoders run in the guest. scrcpy preserves their device presentation
    # timestamps when muxing. Host audio and wall-clock video are never combined.
    movie = MEDIA / 'tasbih.mp4'
    raw = temp / 'guest.mp4'
    log = MEDIA / 'recorder.txt'
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
            # A single guest-side shell schedules the seven real InputManager taps.
            # Do not synchronously screenshot, inspect DOM or poll preferences while tapping.
            shell('sleep 1.15; for i in 1 2 3; do input tap 360 704; sleep .78; done; '
                  'for i in 1 2 3 4; do input tap 360 704; sleep .06; done')
            visible_count(socket, 33)
            time.sleep(1.35)
        finally:
            if recorder.poll() is None:
                recorder.send_signal(signal.SIGINT)
            recorder.wait(timeout=15)
    assert recorder.returncode == 0, log.read_text()[-1600:]
    run('ffmpeg','-loglevel','error','-y','-i',raw,'-map','0:v:0','-map','0:a:0','-c','copy',
        '-movflags','+faststart',movie)
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
    seed(26 if MODE=='record' else 33)
    start('tasbih')
    if MODE=='controls':
        exercise_controls()
        return
    with closing(connect_scene()) as scene, tempfile.TemporaryDirectory() as directory:
        temp = Path(directory)
        visible_count(scene, 26)
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
    REPORT.update(verified=True, initial=26, slow_taps=[27,28,29], rapid_result=33,
                  idle_planets_moved=True, restored=33)


def exercise_controls():
    with closing(connect_scene()) as scene:
        visible_count(scene, 33)
        shell('input', 'swipe', 350, 600, 350, 900, 400)
        time.sleep(1.6)
        dragged = int(prefs()['tasbih_count'])
        assert dragged > 33, 'Existing vertical bead gesture failed'
        tap_control('tasbih_settings')
        tap_control('tasbih_sound')
        assert prefs()['tasbih_bead_sound'] == 'false'
        tap_control('tasbih_sound')
        assert prefs()['tasbih_bead_sound'] == 'true'
        tap_control('tasbih_target_99')
        assert prefs()['tasbih_target'] == '99' and int(prefs()['tasbih_count']) == dragged
        tap_control('tasbih_reset')
        tap_control('tasbih_reset_cancel')
        visible_count(scene, (dragged - 1) % 99 + 1)
        tap_control('tasbih_settings')
        tap_control('tasbih_reset')
        tap_control('tasbih_reset_confirm')
        visible_count(scene, 0)
    assert prefs()['tasbih_count'] == '0'
    start('tasbih')
    with closing(connect_scene()) as scene:
        visible_count(scene, 0)
        assert '99' in read_js(scene, "document.getElementById('goal').innerText")
    REPORT.update(verified=True, drag_increment=dragged - 33,
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
