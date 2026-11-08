"""Inherit verified passed Rhythm UI groups without relabelling them as rerun.

Only the same guarded APKs are eligible. Download the source artifact through
the authenticated GitHub API, verify its archive digest and run identity, then
preserve the untouched source evidence. A changed test or transitive helper is
pending again. Legacy guide fixtures remain explicitly test-only evidence.
"""
import ast
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import zipfile

OUT = Path('ci-out')
SCRIPT = Path('tools/ci/rhythm_pause.py')
SOURCE_MANIFEST = OUT / 'rhythm-retry-source.json'
GROUP_FUNCTIONS = {
    'update_preserves_data': 'upgrade',
    'fresh_first_launch': 'first_run',
    'presentation_dark': 'presentation',
    'presentation_light': 'presentation',
    'transitions': 'transition_recording',
    'reader_actions': 'reader_actions',
    'tasbih_azkar': 'tasbih_azkar',
    'quiz_answer_feedback_result': 'knowledge',
    'focus_journal': 'focus_journal',
    'all_main_routes_both_themes': 'route_gallery',
    'catalog_search_and_map': 'shop_and_places',
    'compact_languages_rotation_motion': 'compact_and_languages',
    'widgets_light': 'widgets_gallery',
    'widgets_150_light': 'widgets_gallery',
    'widgets_dark': 'widgets_gallery',
    'widgets_150_dark': 'widgets_gallery',
    'guide_all_topics_steps_zoom_return': None,
}
PREVIEW_GROUPS = ('fresh_first_launch', 'presentation_dark', 'presentation_light', 'transitions')
SUPPORT_FILES = tuple(Path('tools/ci') / name for name in (
    'learning_journeys.py', 'audio_journeys.py', 'guide_journeys.py', 'features_controls.py'))
GUIDE_GROUP = 'guide_all_topics_steps_zoom_return'
GUIDE_FIXTURE = {
    'kind': 'legacy_disposable_emulator_preferences',
    'seed_function': 'tools/ci/guide_journeys.py:seed',
    'synthetic_values': {'limit': 37, 'packages': ['example.synthetic'], 'catalog': 'empty'},
    'fixture_values_are_user_data': False,
    'presentation': False,
    'imported_30_guide_assets_generated_by_this_fixture': False,
    'scope': 'Guide replay, loaded screenshots/markers, zoom and return controls only.',
}


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def safe_relative(value):
    relative = PurePosixPath(value)
    assert value and not relative.is_absolute() and '..' not in relative.parts, value
    assert '\\' not in value, value
    return Path(*relative.parts)


def save(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def guide_frames(snapshot, result):
    if result.get('legacy_screenshots'):
        paths = result['legacy_screenshots']
    else:
        base = result.get('evidence_directory', 'ci-out')
        paths = [base + '/screens/' + name + '.png' for name in (
            'learning-illustrated-guide-welcome', 'learning-illustrated-guide-limits',
            'learning-illustrated-guide-quran', 'learning-guide-screenshot-zoom',
            'learning-guide-large-light-zoom', 'learning-guide-landscape-large')]
    assert len(paths) == 6
    frames = []
    for name in paths:
        assert name.startswith('ci-out/')
        path = snapshot / safe_relative(name.removeprefix('ci-out/'))
        assert path.is_file(), 'Missing legacy guide evidence: ' + name
        frames.append(path)
    return frames


def script_parts(source):
    tree = ast.parse(source)
    functions = {node.name: node for node in tree.body
                 if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))}
    bindings = {}
    for node in tree.body:
        if isinstance(node, (ast.Assign, ast.AnnAssign)):
            targets = node.targets if isinstance(node, ast.Assign) else [node.target]
            for target in targets:
                for name in ast.walk(target):
                    if isinstance(name, ast.Name):
                        bindings[name.id] = node
    imports = {ast.dump(node, include_attributes=False) for node in tree.body
               if isinstance(node, (ast.Import, ast.ImportFrom))}
    # The imported helper environment is created by exec. Preserve those
    # initializer statements too; a changed exec cannot hide behind aliases.
    initializers = [ast.dump(node, include_attributes=False) for node in tree.body
                    if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call)]
    return functions, bindings, imports, initializers


def fingerprint(parts, function):
    functions, bindings, _, initializers = parts
    if function is None:
        # The guide runs in the unchanged SUPPORT namespace, not this script.
        return hashlib.sha256(b'unchanged_legacy_illustrated_guide').hexdigest(), []
    pending, visited, used_bindings = [function], set(), set()
    while pending:
        name = pending.pop()
        if name in visited:
            continue
        assert name in functions, ('Test/helper definition missing', name)
        visited.add(name)
        references = {node.id for node in ast.walk(functions[name])
                      if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load)}
        pending.extend(references.intersection(functions).difference(visited))
        # Follow aliases/constants as well as called local helper functions.
        queued = list(references.intersection(bindings).difference(used_bindings))
        while queued:
            binding = queued.pop()
            if binding in used_bindings:
                continue
            used_bindings.add(binding)
            referenced = {node.id for node in ast.walk(bindings[binding])
                          if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load)}
            queued.extend(referenced.intersection(bindings).difference(used_bindings))
            pending.extend(referenced.intersection(functions).difference(visited))
    payload = {
        'functions': {name: ast.dump(functions[name], include_attributes=False) for name in sorted(visited)},
        'bindings': {name: ast.dump(bindings[name], include_attributes=False) for name in sorted(used_bindings)},
        'initializers': initializers,
    }
    encoded = json.dumps(payload, sort_keys=True).encode()
    return hashlib.sha256(encoded).hexdigest(), sorted(visited)


def eligible_groups(prior, source_sha):
    old = script_parts(run('git', 'show', source_sha + ':' + str(SCRIPT)))
    current = script_parts(SCRIPT.read_text())
    # New retry-only imports are permitted. Existing imported helpers cannot
    # disappear or silently point elsewhere.
    imports_unchanged = old[2].issubset(current[2])
    support_hashes = {}
    support_unchanged = True
    for path in SUPPORT_FILES:
        before = subprocess.check_output(['git', 'show', source_sha + ':' + str(path)])
        support_hashes[str(path)] = hashlib.sha256(before).hexdigest()
        support_unchanged &= path.read_bytes() == before
    accepted, rejected = {}, {}
    for name, result in prior['groups'].items():
        if result['status'] != 'passed':
            continue
        assert name in GROUP_FUNCTIONS, ('Unknown prior group', name)
        old_hash, dependencies = fingerprint(old, GROUP_FUNCTIONS[name])
        new_hash, _ = fingerprint(current, GROUP_FUNCTIONS[name])
        if imports_unchanged and support_unchanged and old_hash == new_hash:
            accepted[name] = {'fingerprint': new_hash, 'unchanged_functions': dependencies}
        else:
            rejected[name] = 'Test, transitive helper, global binding or imported support changed; rerun required.'
    return accepted, rejected, support_hashes


def validate_origin(prior, delivery, origin, artifact):
    requested = os.environ['RHYTHM_REUSE_RUN']
    assert str(origin['id']) == requested and str(prior['run_id']) == requested
    assert origin['status'] == 'completed'
    assert origin['head_branch'] == 'codex/rhythm-pause-0829'
    assert origin['path'] == '.github/workflows/rhythm-pause.yml'
    assert origin['head_sha'] == prior['verification_sha']
    assert artifact['name'] == 'rhythm-final-evidence' and artifact['expired'] is False
    assert artifact['workflow_run']['id'] == origin['id']
    assert artifact['workflow_run']['head_sha'] == origin['head_sha']
    assert artifact['workflow_run']['head_branch'] == origin['head_branch']
    assert prior['mode'] == delivery['mode'] == 'final'
    assert prior['source_sha'] == delivery['source_sha']
    assert str(prior['source_run']) == str(delivery['run_id'])
    assert prior['debug_sha256'] == delivery['debug_sha256']
    assert prior['debug_sha256'] == digest('app/build/outputs/apk/debug/app-debug.apk')
    assert prior['profile'] == 'disposable_android15_debug_emulator'
    assert prior['production_writes'] is False and prior['qa_demo'] is False
    assert prior['fixture_values_are_user_data'] is False
    assert prior['app_crashes'] is False, 'Source run reports an app crash; do not inherit automatically'
    assert set(prior['groups']).issubset(GROUP_FUNCTIONS)
    assert all(group['status'] in {'passed', 'failed'} for group in prior['groups'].values())
    reuse = json.loads((OUT / 'rhythm-reuse.json').read_text())
    assert str(reuse['downloaded_from_run']) == requested
    assert reuse['verification_sha'] == os.environ['GITHUB_SHA']
    assert reuse['unchanged_android_inputs'] is True and reuse['apk_hashes_verified'] is True


def prepare():
    assert os.environ['RHYTHM_MODE'] == 'final'
    requested = os.environ['RHYTHM_REUSE_RUN']
    assert re.fullmatch(r'\d+', requested)
    assert run('git', 'rev-parse', 'HEAD') == os.environ['GITHUB_SHA']
    repo = os.environ['GITHUB_REPOSITORY']
    origin = json.loads((OUT / 'rhythm-requested-reuse-run.json').read_text())
    listing = json.loads(run('gh', 'api', f'repos/{repo}/actions/runs/{requested}/artifacts?per_page=100'))
    assert listing['total_count'] <= len(listing['artifacts']), 'Artifact listing is incomplete'
    candidates = [item for item in listing['artifacts'] if item['name'] == 'rhythm-final-evidence']
    assert len(candidates) == 1, 'Expected one exact source evidence artifact'
    artifact = candidates[0]
    assert re.fullmatch(r'sha256:[0-9a-f]{64}', artifact.get('digest', '')), 'Artifact digest is missing'
    folder = Path('ci-prior')
    folder.mkdir(exist_ok=True)
    archive = folder / ('rhythm-final-' + requested + '.zip')
    with archive.open('wb') as stream:
        subprocess.run(['gh', 'api', f'repos/{repo}/actions/artifacts/{artifact["id"]}/zip'],
                       stdout=stream, check=True)
    assert digest(archive) == artifact['digest'].split(':', 1)[1], 'Source artifact archive digest mismatch'
    extracted = folder / 'evidence'
    assert not extracted.exists(), 'Do not mix evidence downloads'
    with zipfile.ZipFile(archive) as files:
        assert sum(item.file_size for item in files.infolist()) < 500 * 1024 * 1024
        for item in files.infolist():
            safe_relative(item.filename)
            assert not stat.S_ISLNK(item.external_attr >> 16), 'Artifact symlink rejected'
        files.extractall(extracted)
    source = extracted / 'ci-out'
    prior = json.loads((source / 'rhythm-ui.json').read_text())
    delivery = json.loads((OUT / 'rhythm-delivery.json').read_text())
    validate_origin(prior, delivery, origin, artifact)
    crashes = (source / 'rhythm-crashes.txt').read_text()
    assert not re.search(r'FATAL EXCEPTION|Fatal signal', crashes)
    accepted, rejected, support_hashes = eligible_groups(prior, prior['verification_sha'])
    snapshot = OUT / 'inherited' / requested
    assert not snapshot.exists(), 'Do not overwrite an evidence snapshot'
    shutil.copytree(source, snapshot)
    hashes = {path.relative_to(snapshot).as_posix(): digest(path)
              for path in sorted(snapshot.rglob('*')) if path.is_file()}
    for item in prior['screens']:
        if item['group'] not in accepted:
            continue
        assert digest(snapshot / safe_relative(item['png'])) == item['sha256'], item['name']
        for key in ('xml', 'geometry'):
            assert (snapshot / safe_relative(item[key])).is_file(), (item['name'], key)
        assert json.loads((snapshot / safe_relative(item['geometry'])).read_text()) == item['effective_display']
    if 'transitions' in accepted:
        assert (snapshot / 'rhythm-transitions.mp4').stat().st_size > 10000
    if GUIDE_GROUP in accepted:
        guide_frames(snapshot, prior['groups'][GUIDE_GROUP])
    manifest = {
        'version': 1, 'source_run': requested, 'source_verification_sha': prior['verification_sha'],
        'apk_source_sha': delivery['source_sha'], 'debug_sha256': delivery['debug_sha256'],
        'verification_sha': os.environ['GITHUB_SHA'], 'verification_run': os.environ['GITHUB_RUN_ID'],
        'source_report_sha256': hashes['rhythm-ui.json'], 'archive_sha256': digest(archive),
        'artifact': artifact, 'origin_run': origin, 'source_snapshot': snapshot.as_posix(),
        'source_files_sha256': hashes, 'support_files_sha256': support_hashes,
        'accepted': accepted, 'rejected_changed_checks': rejected,
        'pending': [name for name in GROUP_FUNCTIONS if name not in accepted],
        'legacy_guide_fixture': GUIDE_FIXTURE,
    }
    save(SOURCE_MANIFEST, manifest)
    print(json.dumps({'inherited': list(accepted), 'pending': manifest['pending'],
                      'rejected_changed_checks': rejected}))


def inherit(report, delivery):
    """Call once after REPORT identity is initialized, before any UI groups."""
    if report['mode'] != 'final' or not os.environ.get('RHYTHM_REUSE_RUN'):
        return
    manifest = json.loads(SOURCE_MANIFEST.read_text())
    assert manifest['verification_sha'] == os.environ['GITHUB_SHA']
    assert str(manifest['verification_run']) == os.environ['GITHUB_RUN_ID']
    assert manifest['apk_source_sha'] == delivery['source_sha']
    assert manifest['debug_sha256'] == report['debug_sha256']
    snapshot = Path(manifest['source_snapshot'])
    assert snapshot == OUT / 'inherited' / os.environ['RHYTHM_REUSE_RUN']
    for path, expected in manifest['source_files_sha256'].items():
        assert digest(snapshot / safe_relative(path)) == expected, 'Inherited evidence changed: ' + path
    prior = json.loads((snapshot / 'rhythm-ui.json').read_text())
    accepted, rejected, hashes = eligible_groups(prior, prior['verification_sha'])
    assert accepted == manifest['accepted'] and rejected == manifest['rejected_changed_checks']
    assert hashes == manifest['support_files_sha256']
    for name in accepted:
        result = copy.deepcopy(prior['groups'][name])
        result.update(inherited_from_run=manifest['source_run'],
                      inherited_from_verification_sha=manifest['source_verification_sha'],
                      executed_in_current_run=False,
                      source_report=(snapshot / 'rhythm-ui.json').as_posix(),
                      unchanged_check_fingerprint=accepted[name]['fingerprint'])
        if name == GUIDE_GROUP:
            result['legacy_fixture'] = GUIDE_FIXTURE
            result['legacy_screenshots'] = [path.as_posix() for path in guide_frames(snapshot, prior['groups'][name])]
        report['groups'][name] = result
    for item in prior['screens']:
        if item['group'] not in accepted:
            continue
        inherited = copy.deepcopy(item)
        inherited.update(inherited_from_run=manifest['source_run'], executed_in_current_run=False)
        for key in ('png', 'xml', 'geometry'):
            relative = safe_relative(item[key])
            target = OUT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists():
                assert digest(target) == digest(snapshot / relative), 'Evidence path collision'
            else:
                shutil.copyfile(snapshot / relative, target)
        report['screens'].append(inherited)
    if 'transitions' in accepted:
        shutil.copyfile(snapshot / 'rhythm-transitions.mp4', OUT / 'rhythm-transitions.mp4')
    report['retry'] = {'inherited_groups': list(accepted), 'pending_groups': manifest['pending'],
                       'source_run': manifest['source_run'], 'source_archive_sha256': manifest['archive_sha256'],
                       'provenance_manifest': SOURCE_MANIFEST.as_posix(),
                       'legacy_guide_fixture': GUIDE_FIXTURE}


def skip_inherited(report, name):
    if name not in report.get('retry', {}).get('inherited_groups', []):
        return False
    result = report['groups'][name]
    assert result['status'] == 'passed' and result['executed_in_current_run'] is False
    assert result['inherited_from_run'] == report['retry']['source_run']
    print('Rhythm UI: inherited verified group', name, 'from', result['inherited_from_run'], flush=True)
    return True


def finish(report):
    """A timed-out/missing group can never count as an aggregate success."""
    expected = list(GROUP_FUNCTIONS) if report['mode'] == 'final' else list(PREVIEW_GROUPS)
    assert set(report['groups']).issubset(expected), 'Unexpected UI group'
    report['required_groups'] = expected
    report['missing_groups'] = [name for name in expected if name not in report['groups']]
    report['failed_groups'] = [name for name in expected if name in report['groups']
                               and report['groups'][name]['status'] != 'passed']
    report['planned_groups_passed'] = (not report['missing_groups'] and not report['failed_groups']
                                       and report.get('app_crashes') is False)
    if GUIDE_GROUP in report['groups']:
        report['groups'][GUIDE_GROUP].setdefault('legacy_fixture', GUIDE_FIXTURE)
    return report['planned_groups_passed']


if __name__ == '__main__':
    assert sys.argv[1:] == ['prepare']
    prepare()
