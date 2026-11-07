"""Publish only synthetic QA, public images and build summaries for review."""
import os
from pathlib import Path
import shutil
import subprocess

folder = Path('design-evidence')
folder.mkdir(exist_ok=True)
for name in ['release-delivery.json', 'design-passed.json', 'design-functional-passed.json', 'design-failure.txt', 'miniapp-passed.json',
             'miniapp-failure.txt', 'crashes.txt', 'last-ui.xml']:
    source = Path('ci-out') / name
    if source.exists():
        shutil.copyfile(source, folder / name)
for source in Path('ci-out/screens').glob('*.png'):
    shutil.copyfile(source, folder / source.name)
for source in Path('ci-out/miniapp').glob('*'):
    if source.suffix in {'.png', '.json'}:
        shutil.copyfile(source, folder / source.name)
for source in Path('ci-out').glob('design-*.xml'):
    shutil.copyfile(source, folder / source.name)
if not any(folder.iterdir()):
    raise SystemExit(0)
branch = 'ci-design-0823-' + os.environ['GITHUB_RUN_ID']
def git(*args):
    subprocess.run(['git', *args], cwd=folder, check=True, stdout=subprocess.DEVNULL)
git('init', '-q')
git('add', '.')
git('-c', 'user.name=ci', '-c', 'user.email=ci@local', 'commit', '-qm', 'Design and usability evidence [skip ci]')
git('push', '-q', 'https://x-access-token:' + os.environ['GH_TOKEN'] + '@github.com/' +
    os.environ['GITHUB_REPOSITORY'] + '.git', 'HEAD:refs/heads/' + branch)
print('Evidence branch: ' + branch)
