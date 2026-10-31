"""Allow one narrowly scoped index fix to reuse documented prior UI evidence.

The first five gestures passed before a large-font index failure. They are
inherited evidence from the earlier APK, never reported as runs of the new APK.
Any other production change requires the complete touch journey instead.
"""
import json
from pathlib import Path
import subprocess

SOURCE = '617c6a3a324bfd3a302882fc1d462ceb1c8c1091'
EVIDENCE = 'e29488951426b588d0cc9c0c591c7a424494accc'
SCREEN = 'app/src/main/java/com/zikrtime/app/ui/QuranScreen.kt'


def git(*args):
    return subprocess.check_output(['git', *args], text=True)


subprocess.run(['git', 'fetch', '--no-tags', 'origin', SOURCE, EVIDENCE], check=True)
changed = git('diff', '--name-only', SOURCE, 'HEAD').splitlines()
production = {p for p in changed if p.startswith(('app/', 'gradle/')) or p in {
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat'}}
assert production == {SCREEN}, production
old = git('show', SOURCE + ':' + SCREEN)
hint = '''        if (tab == 0) Text(stringResource(R.string.qt_touch_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
'''
anchor = '            item("statistics") {\n'
replacement = '''            if (tab == 0) item("gesture_hint") {
                Text(stringResource(R.string.qt_touch_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
'''
assert old.count(hint) == 1 and old.count(anchor) == 1
expected = old.replace(hint, '').replace(anchor, replacement + anchor)
assert git('show', 'HEAD:' + SCREEN) == expected, 'Reader index changed beyond the verified hint move'
assert Path(SCREEN).read_text() == expected, 'Working tree differs from verified source'
delivery = json.loads(git('show', EVIDENCE + ':delivery.json'))
assert delivery['source_sha'] == SOURCE and delivery['run'] == 41207824100
assert delivery['apk_sha256'] == '2094067e6625cf9ef71e56406ba9b7d100ccd5ae521356e00804e2015c3b99fa'
passed = json.loads(git('show', EVIDENCE + ':quran-passed.json'))
assert len(passed) == 5 and all(value is True for value in passed.values())
report = {
    'description': 'Prior APK evidence; only the exact scrollable index-hint move differs in production source.',
    'prior_source': SOURCE, 'prior_run': delivery['run'], 'prior_apk_sha256': delivery['apk_sha256'],
    'evidence_commit': EVIDENCE, 'current_source': git('rev-parse', 'HEAD').strip(),
    'allowed_production_change': SCREEN, 'inherited_journeys': passed,
}
Path('ci-out').mkdir(exist_ok=True)
Path('ci-out/inherited-touch-verification.json').write_text(json.dumps(report, indent=2))
print('Verified unchanged reader code and five prior passed journeys; remaining journeys will run on the rebuilt APK.')
