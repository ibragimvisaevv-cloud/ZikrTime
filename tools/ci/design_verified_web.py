"""Keep passing web/server evidence when rebuilding only Android inputs."""
import json
from pathlib import Path

job = json.loads(Path('ci-out/reused-backend-check.json').read_text())
assert job['id'] == 128403313374 and job['run_id'] == 42372494277
assert job['head_sha'] == '8f27ea26ece51932a5dfba45fad3e7a580578134'
step = next(s for s in job['steps'] if s['name'] == 'Server and payment regression tests')
assert step['conclusion'] == 'success'
artifact = json.loads(Path('ci-out/reused-miniapp-artifact.json').read_text())
assert artifact['id'] == 13574804431 and artifact['name'] == 'ZikrTime-MiniApp-check'
assert not artifact['expired']
assert artifact['workflow_run']['id'] == 42374725250
assert artifact['workflow_run']['head_sha'] == 'ebb6dc535795f079a1b3d30309a3376772d78b1a'
browser = json.loads(Path('ci-out/miniapp/miniapp-result.json').read_text())
assert browser['passed'] is True and browser['fixtures'] == 'synthetic'
assert len(list(Path('ci-out/miniapp').glob('*-320.png'))) == 3
Path('ci-out/web-checks.json').write_text(json.dumps({
    'server_run': job['run_id'], 'server_step': step['name'],
    'miniapp_run': artifact['workflow_run']['id'], 'unchanged_sources': True,
    'browser_result': browser,
}, indent=2))
print('Reused successful server/payment and Mini App checks; relevant sources are unchanged.')
