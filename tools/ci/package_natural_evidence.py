"""Package public UI evidence only; omit preferences and full device dumps."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json

root = Path('smoke-in')
assert len(list((root / 'screens').glob('*.png'))) == 16
assert json.loads((root / 'tap-count.json').read_text()) == {'before': 21, 'after': 61, 'taps': 40}
assert json.loads((root / 'controls-passed.json').read_text()) == {
    'goals': [33, 99, 100, 0], 'reset': True, 'phrase': True, 'restart_count': 2,
}
with ZipFile('ZikrTime-0.8.3-natural-evidence.zip', 'w', ZIP_DEFLATED) as out:
    for path in sorted((root / 'screens').glob('*.png')):
        out.write(path, str(path.relative_to(root)))
    for name in ['tasbih.mp4', 'tap-count.json', 'controls-passed.json', 'crashes.txt',
                 'perf-baseline.txt', 'perf-natural.txt', 'idle-baseline.txt', 'idle-natural.txt',
                 'memory-baseline.txt', 'memory-natural.txt']:
        out.write(root / name, name)
    out.write('docs/ANDROID_NATURAL_GITHUB_NOTES.md', 'RESULTS_RU.md')
    out.write('signature.txt', 'signature.txt')
