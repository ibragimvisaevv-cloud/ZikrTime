"""Two independent UI shards consuming one immutable Rhythm APK build."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

import rhythm_pause_retry as retry

ACTIONS = ('update_preserves_data', 'fresh_first_launch', 'presentation_dark', 'presentation_light',
           'transitions', 'reader_actions', 'tasbih_azkar', 'quiz_answer_feedback_result', 'focus_journal')
LAYOUT = tuple(name for name in retry.GROUP_FUNCTIONS if name not in ACTIONS)
OWNERS = {'actions': ACTIONS, 'layout': LAYOUT, 'preview': retry.PREVIEW_GROUPS}
assert len(ACTIONS) == 9 and len(LAYOUT) == 8 and not set(ACTIONS).intersection(LAYOUT)


def configure(report):
    shard = os.environ.get('RHYTHM_SHARD', 'all')
    assert shard in OWNERS or shard == 'all', 'Unknown Rhythm shard'
    assert (shard in {'actions', 'layout'}) == (report['mode'] == 'final') or shard == 'all'
    expected = list(retry.GROUP_FUNCTIONS) if report['mode'] == 'final' else list(retry.PREVIEW_GROUPS)
    owned = list(OWNERS.get(shard, expected))
    report['shard'] = shard
    report['shard_required_groups'] = owned
    report['complete_mode_required_groups'] = expected
    # Same-APK retry may import passed groups for both owners. Each shard
    # certifies only its own partition; the merge must see each group once.
    report['groups'] = {name: value for name, value in report['groups'].items() if name in owned}
    report['screens'] = [item for item in report['screens'] if item['group'] in owned]
    if 'retry' in report:
        for key in ('inherited_groups', 'pending_groups'):
            report['retry'][key] = [name for name in report['retry'][key] if name in owned]


def owns(report, name):
    assert name in retry.GROUP_FUNCTIONS
    if name in report['shard_required_groups']:
        return True
    print('Rhythm UI: assigned to other shard', name, flush=True)
    return False


def needs_setup(report):
    fresh = report['groups'].get('fresh_first_launch')
    return fresh is None or fresh.get('executed_in_current_run') is False


def finish(report):
    expected = report['shard_required_groups']
    assert set(report['groups']).issubset(expected)
    report['required_groups'] = expected
    report['missing_groups'] = [name for name in expected if name not in report['groups']]
    report['failed_groups'] = [name for name in expected if name in report['groups']
                               and report['groups'][name]['status'] != 'passed']
    report['planned_groups_passed'] = (not report['missing_groups'] and not report['failed_groups']
                                       and report.get('app_crashes') is False)
    report['aggregate_complete'] = report['shard'] == 'all' and report['planned_groups_passed']
    if retry.GUIDE_GROUP in report['groups']:
        report['groups'][retry.GUIDE_GROUP].setdefault('legacy_fixture', retry.GUIDE_FIXTURE)
    return report['planned_groups_passed']


def verify_inputs():
    report = json.loads((retry.OUT / 'rhythm-delivery.json').read_text())
    assert report['mode'] == os.environ['RHYTHM_MODE']
    assert report['source_sha'] == os.environ['RHYTHM_APK_SOURCE_SHA']
    assert str(report['run_id']) == os.environ['RHYTHM_APK_SOURCE_RUN']
    assert report['version'] == '0.8.29' and report['version_code'] == 41
    subprocess.run(['git', 'diff', '--exit-code', report['source_sha'], 'HEAD', '--',
                    'app', 'gradle', 'gradlew', 'gradlew.bat', 'build.gradle.kts',
                    'settings.gradle.kts', 'gradle.properties', 'keystore/release.jks.enc'], check=True)
    assert retry.digest('app/build/outputs/apk/debug/app-debug.apk') == report['debug_sha256']
    if report['mode'] == 'final':
        assert report['certificate_sha256'] == '7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf'
        assert retry.digest('app/build/outputs/apk/release/app-release.apk') == report['release_sha256']
        assert retry.digest('ci-out/baseline/ZikrTime-0.8.28-debug.apk') == report['baseline_debug_sha256']
        for path, expected in report['inheritable_check_files'].items():
            assert retry.digest(path) == expected, path
    return report


def copy_evidence(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        assert retry.digest(source) == retry.digest(target), 'Conflicting evidence filename: ' + str(target)
    else:
        shutil.copyfile(source, target)


def merge(mode, root):
    delivery = verify_inputs()
    expected_shards = ('actions', 'layout') if mode == 'final' else ('preview',)
    combined = {
        'profile': 'disposable_android15_debug_emulator', 'mode': mode, 'shard': 'aggregate',
        'production_writes': False, 'qa_demo': False, 'fixture_values_are_user_data': False,
        'full_66_row_action_audit_complete': False,
        'source_sha': delivery['source_sha'], 'source_run': delivery['run_id'],
        'verification_sha': os.environ['GITHUB_SHA'], 'run_id': os.environ['GITHUB_RUN_ID'],
        'debug_sha256': delivery['debug_sha256'], 'groups': {}, 'screens': [], 'shards': {},
        'limits': [], 'app_crashes': False, 'aggregate_complete': False,
    }
    failures, crash_logs = [], []
    for name in expected_shards:
        folder = root / f'rhythm-{mode}-evidence-{name}' / 'ci-out'
        if not (folder / 'rhythm-ui.json').is_file():
            failures.append('Missing shard report: ' + name)
            continue
        report = json.loads((folder / 'rhythm-ui.json').read_text())
        assert report['shard'] == name and report['mode'] == mode
        assert report['verification_sha'] == os.environ['GITHUB_SHA']
        assert str(report['run_id']) == os.environ['GITHUB_RUN_ID']
        assert report['source_sha'] == delivery['source_sha']
        assert str(report['source_run']) == str(delivery['run_id'])
        assert report['debug_sha256'] == delivery['debug_sha256']
        assert report['production_writes'] is False and report['qa_demo'] is False
        assert report['fixture_values_are_user_data'] is False
        assert report['shard_required_groups'] == list(OWNERS[name])
        assert set(report['groups']).issubset(OWNERS[name])
        assert not set(report['groups']).intersection(combined['groups']), 'Duplicate group ownership'
        snapshot = retry.OUT / 'shards' / name
        assert not snapshot.exists()
        shutil.copytree(folder, snapshot)
        hashes = {path.relative_to(snapshot).as_posix(): retry.digest(path)
                  for path in sorted(snapshot.rglob('*')) if path.is_file()}
        combined['shards'][name] = {
            'source_report': (snapshot / 'rhythm-ui.json').as_posix(),
            'source_report_sha256': hashes['rhythm-ui.json'],
            'files_sha256': hashes, 'independent_setup': report.get('independent_setup'),
            'retry': report.get('retry'),
        }
        if name == 'layout':
            setup = report.get('independent_setup')
            assert setup and setup['city_selected_through_ui'] == 'moscow'
            assert setup['permission_prompts_skipped'] is True and setup['guide_skipped_through_ui'] is True
        for group, result in report['groups'].items():
            combined['groups'][group] = {**copy.deepcopy(result), 'evidence_shard': name,
                                          'evidence_directory': snapshot.as_posix()}
        for item in report['screens']:
            assert item['group'] in OWNERS[name]
            assert retry.digest(folder / retry.safe_relative(item['png'])) == item['sha256'], item['name']
            for key in ('png', 'xml', 'geometry'):
                relative = retry.safe_relative(item[key])
                copy_evidence(folder / relative, retry.OUT / relative)
            assert json.loads((folder / retry.safe_relative(item['geometry'])).read_text()) == item['effective_display']
            combined['screens'].append({**copy.deepcopy(item), 'evidence_shard': name})
        if 'transitions' in report['groups'] and report['groups']['transitions']['status'] == 'passed':
            video = folder / 'rhythm-transitions.mp4'
            assert video.stat().st_size > 10000
            copy_evidence(video, retry.OUT / video.name)
            combined['transition_video_sha256'] = retry.digest(video)
        if name == 'preview' and os.environ.get('RHYTHM_GUIDE_CAPTURE') == 'true':
            # The capture step is separate from the four preview groups. Keep
            # all its evidence in the usual import layout without treating it
            # as a successful UI group merely because files exist.
            for path in folder.rglob('*'):
                if path.is_file():
                    copy_evidence(path, retry.OUT / path.relative_to(folder))
            subprocess.run(['python3', 'tools/ci/rhythm_pause_guide.py', 'validate', str(retry.OUT),
                            '--source', delivery['source_sha'], '--run', os.environ['GITHUB_RUN_ID']], check=True)
        if report.get('app_crashes') is not False:
            failures.append('Missing or failed crash conclusion: ' + name)
            combined['app_crashes'] = True
        crash_file = folder / 'rhythm-crashes.txt'
        if not crash_file.is_file():
            failures.append('Missing final crash log: ' + name)
            combined['app_crashes'] = True
        else:
            crash_text = crash_file.read_text()
            if re.search(r'FATAL EXCEPTION|Fatal signal', crash_text):
                combined['app_crashes'] = True
            crash_logs.append(name + '\n' + crash_text)
        combined['limits'].extend(item for item in report['limits'] if item not in combined['limits'])
    retry.finish(combined)
    if os.environ.get('RHYTHM_UI_RESULT') != 'success':
        failures.append('One or more UI jobs or guide capture steps did not finish successfully')
    combined['aggregate_failures'] = failures
    combined['aggregate_complete'] = combined['planned_groups_passed'] and not failures
    combined['planned_groups_passed'] = combined['aggregate_complete']
    (retry.OUT / 'rhythm-crashes.txt').write_text('\n'.join(crash_logs))
    retry.save(retry.OUT / 'rhythm-ui.json', combined)
    mapping = Path('docs/RHYTHM_PAUSE_SCREEN_MAP.md')
    if mapping.exists():
        rows = []
        for identifier, title in re.findall(r'^\| (\d{2}) · ([^|]+)\|', mapping.read_text(), re.M):
            related = {name: result['status'] for name, result in combined['groups'].items()
                       if identifier in result.get('map_ids', [])}
            rows.append({'id': identifier, 'screen': title.strip(), 'groups': related,
                         'status': 'partial_evidence' if related else 'not_exercised',
                         'all_listed_actions_verified': False})
        retry.save(retry.OUT / 'rhythm-map-coverage.json', {
            'source_sha': combined['source_sha'], 'rows': rows,
            'interpretation': 'Linked groups prove only their recorded assertions and rendering; '
                              'a screenshot does not certify every action in a screen-map row.'})
    retry.save(retry.OUT / 'rhythm-shard-merge.json', {
        'source_sha': combined['source_sha'], 'debug_sha256': combined['debug_sha256'],
        'required_groups': combined['required_groups'], 'missing_groups': combined['missing_groups'],
        'failed_groups': combined['failed_groups'], 'aggregate_failures': failures,
        'inherited_groups': [name for name, group in combined['groups'].items() if group.get('executed_in_current_run') is False],
        'passed': combined['aggregate_complete'], 'shards': list(combined['shards']),
    })
    print(json.dumps({'groups': len(combined['groups']), 'passed': combined['aggregate_complete'],
                      'missing': combined['missing_groups'], 'failed': combined['failed_groups'], 'errors': failures}))
    assert combined['aggregate_complete'], 'Required UI evidence is incomplete; APK delivery is withheld'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('verify-inputs')
    selected = sub.add_parser('merge')
    selected.add_argument('--mode', choices=('preview', 'final'), required=True)
    selected.add_argument('--root', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'verify-inputs':
        verify_inputs()
    else:
        merge(args.mode, args.root)
