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
REPORT = {'source_commit': os.environ.get('GITHUB_SHA'), 'tap_count': 7}
W, H = 720, 1280


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, timeout=60, **kwargs)


def start(route):
    shell('am', 'start', '-S', '-W', '-n', PKG + '/.MainActivity', '--es', 'qa_screen', route,
          '--es', 'qa_lang', 'ru', '--ez', 'qa_onboarded', 'true')


def seed():
    root = prefs_tree()
    for key, val in {'tasbih_phrase': 4, 'tasbih_target': 100, 'tasbih_count': 26}.items():
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


def read_js(socket, expression):
    socket.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate',
                            'params': {'expression': expression, 'returnByValue': True}}))
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


def decode_audio(path):
    result = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-vn', '-map', '0:a:0',
                             '-ac', '1', '-ar', '12000', '-f', 'f32le', '-'], capture_output=True)
    return np.frombuffer(result.stdout, dtype='<f4') if result.returncode == 0 else np.array([])


def impact_times(path):
    waveform = decode_audio(path)
    sample = decode_audio(SAMPLE)
    if len(waveform) < len(sample) or np.max(np.abs(waveform)) < .0001:
        return []
    match = np.abs(correlate(waveform, sample, mode='valid', method='fft'))
    times, _ = find_peaks(match, distance=round(.1 * 12000), height=np.max(match) * .30)
    return [round(float(t) / 12000, 3) for t in times]


def record(socket, temp):
    # The AVD console records its own audio and display on one timeline. Capture the
    # runner's actual guest-output sink too in case that encoder exposes no audio.
    raw = temp / 'emulator.webm'
    host_audio = temp / 'sink.wav'
    host_log = (temp / 'pulse.log').open('w')
    pulse = subprocess.Popen(['parec', '-d', 'tasbih.monitor', '--rate=48000', '--channels=1',
                              '--file-format=wav', str(host_audio)], stderr=host_log)
    pulse_start = time.monotonic()
    time.sleep(.3)
    start_time = time.monotonic()
    answer = adb('emu', 'screenrecord', 'start', '--time-limit', '30', raw.resolve())
    console = 'OK' in answer and 'KO' not in answer
    fallback = None
    if not console:
        raw = temp / 'guest.mp4'
        fallback = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '25',
                                     '--size', f'{W}x{H}', '/sdcard/tasbih-guest.mp4'])
    try:
        time.sleep(1.05)
        for count in (27, 28, 29):
            tap_time = time.monotonic()
            shell('input', 'tap', W // 2, round(H * .55))
            visible_count(socket, count)
            if count == 27:
                screenshot(MEDIA / 'tasbih.png')
            time.sleep(max(0, tap_time + .87 - time.monotonic()))
        # Four quick real tap events, without waiting for a frame between events.
        shell('for i in 1 2 3 4; do input tap 360 704; sleep .06; done')
        visible_count(socket, 33)
        time.sleep(1.2)
    finally:
        if console:
            adb('emu', 'screenrecord', 'stop')
        else:
            shell('pkill', '-2', 'screenrecord')
            fallback.wait(timeout=12)
            adb('pull', '/sdcard/tasbih-guest.mp4', raw)
        pulse.send_signal(signal.SIGINT)
        pulse.wait(timeout=8)
        host_log.close()
    for _ in range(15):
        if raw.exists() and raw.stat().st_size > 4096:
            break
        time.sleep(.2)
    source_audio = 'emulator internal recording'
    hits = impact_times(raw)
    video_args = ['-i', str(raw)]
    audio_args = ['-map', '0:a:0']
    if len(hits) != REPORT['tap_count']:
        sink_hits = impact_times(host_audio)
        if len(sink_hits) == REPORT['tap_count']:
            offset = start_time - pulse_start
            hits = [round(t - offset, 3) for t in sink_hits]
            source_audio = 'live emulator audio output (PulseAudio monitor)'
            video_args.extend(['-ss', f'{offset:.3f}', '-i', str(host_audio)])
            audio_args = ['-map', '1:a:0', '-shortest']
        elif not hits:
            source_audio = 'unavailable; separate bead sample provided'
            audio_args = ['-an']
    REPORT.update(audio_source=source_audio, captured_impact_times_seconds=hits,
                  all_seven_sounds_verified=len(hits) == REPORT['tap_count'])
    run('ffmpeg', '-loglevel', 'error', '-y', *video_args, '-map', '0:v:0', *audio_args,
        '-vf', 'scale=720:-2', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '18', '-pix_fmt',
        'yuv420p', '-c:a', 'aac', '-b:a', '160k', '-movflags', '+faststart', MEDIA / 'tasbih.mp4')


def exercise():
    shell('svc', 'power', 'stayon', 'true')
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    shell('wm', 'size', f'{W}x{H}')
    shell('wm', 'density', '320')
    shell('settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
    shell('settings', 'put', 'global', 'animator_duration_scale', '1')
    shell('settings', 'put', 'system', 'sound_effects_enabled', '0')
    shell('cmd', 'media_session', 'volume', '--stream', '3', '--set', '13')
    adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    start('home')
    time.sleep(2)
    seed()
    start('tasbih')
    with closing(connect_scene()) as scene, tempfile.TemporaryDirectory() as directory:
        temp = Path(directory)
        visible_count(scene, 26)
        initial = read_js(scene, "['arabic','transcription','translation','goal'].map(k => document.getElementById(k)?.textContent?.trim())")
        assert all(initial) and '100' in initial[-1], initial
        assert 'прощен' in initial[2].lower().replace('ё', 'е'), initial
        # Let the texture finish decoding before capturing idle motion.
        time.sleep(1)
        still = temp / 'idle.png'
        screenshot(still)
        time.sleep(1.2)
        screenshot(temp / 'idle-later.png')
        with Image.open(still) as before, Image.open(temp / 'idle-later.png') as after:
            w, h = before.size
            diff = ImageChops.difference(before.convert('RGB'), after.convert('RGB'))
            assert diff.crop((0, int(.84*h), w, int(.97*h))).getbbox(), 'Planets did not move during idle'
        visible_count(scene, 26)
        record(scene, temp)
        assert prefs()['tasbih_count'] == '33'
    shell('input', 'keyevent', 'KEYCODE_BACK')
    start('tasbih')
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
    REPORT.update(verified=True, initial=26, slow_taps=[27, 28, 29], rapid_result=33,
                  idle_planets_moved=True, restored=33, drag_increment=dragged - 33,
                  reset_confirmed_after_restart=True, target_after_restart=99)


try:
    exercise()
except Exception:
    (MEDIA / 'failure.txt').write_text(traceback.format_exc())
    screenshot(MEDIA / 'failure.png')
    raise
finally:
    (MEDIA / 'verification.json').write_text(json.dumps(REPORT, ensure_ascii=False, indent=2))
    (MEDIA / 'android-log.txt').write_text(adb('logcat', '-d', '-t', '1000'))
