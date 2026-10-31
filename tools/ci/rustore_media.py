"""Unaltered app screenshots from the already checked APK, without another build."""
exec(compile(open('tools/ci/quran_reader_ui.py').read().split('\nmode = sys.argv[1] if len')[0], 'quran-helpers', 'exec'))
from PIL import Image
target = OUT / 'store'
target.mkdir(exist_ok=True)


def capture(name):
    ui()  # Reject app-error dialogs and wait for a current hierarchy.
    shell('pidof', PKG)
    path = target / (name + '.png')
    with path.open('wb') as output:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=output, check=True)
    with Image.open(path) as picture:
        assert picture.size == (1080, 1920)
    assert path.stat().st_size < 3 * 1024 * 1024
    print('Captured:', name, flush=True)


start('home')
put_pref('online_times', 'boolean', 'false')
put_pref('appearance', 'string', 'light')
shell('svc', 'wifi', 'disable')
shell('svc', 'data', 'disable')
start('home')
time.sleep(3)
capture('01-prayer-times')
start('quran')
open_surah(1)
capture('02-quran-translation')
tap_control('quran_read_mode')
read_text('quran_whole_1')
tap_control('quran_fullscreen')
if present('android:id/immersive_cling_title'):
    tap_control('android:id/ok')
time.sleep(3)
capture('03-quran-fullscreen')
tap_control('quran_tools')
tap_control('quran_statistics_open')
capture('04-quran-progress')
start('tasbih')
capture('05-tasbih')
start('focus')
capture('06-focus')
put_pref('appearance', 'string', 'dark')
start('quran')
open_surah(112)
read_text('quran_whole_1')
tap_control('quran_fullscreen')
capture('07-quran-dark')
(target / 'source.json').write_text(json.dumps({
    'apk_source': '22bed67f6863addaa1eed71f4bd563f1b6d00ad4',
    'apk_sha256': 'd1048c731153662808682401cfe48dc9e4638f5216bf0656ff7d4efacaefdb44',
    'method': 'Unaltered Android 15 emulator screenshots from the checked debug APK; no screenshot compositing.',
    'size': [1080, 1920], 'language': 'ru', 'screenshots': 7,
}, indent=2))
