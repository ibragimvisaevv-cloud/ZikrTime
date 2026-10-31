"""Check/use the original Android key without printing or exporting secrets."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

CERT = '7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf'
KEY = Path('keystore/release.jks')


def run(args, env):
    return subprocess.run(args, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)


def restore():
    assert not KEY.exists(), 'Refusing to replace an existing decrypted key'
    password = os.environ.get('RELEASE_KEY_PASSWORD', '')
    if not password.strip():
        return None, 'empty-secret'
    candidates = [password]
    if password.rstrip('\r\n') != password:
        candidates.append(password.rstrip('\r\n'))
    for candidate in candidates:
        env = dict(os.environ, ZIKRTIME_SIGN_PASSWORD=candidate)
        decoded = run(['openssl', 'enc', '-d', '-aes-256-cbc', '-pbkdf2', '-iter', '300000',
                       '-in', 'keystore/release.jks.enc', '-out', str(KEY),
                       '-pass', 'env:ZIKRTIME_SIGN_PASSWORD'], env)
        KEY.chmod(0o600)
        if decoded.returncode == 0:
            cert = run(['keytool', '-exportcert', '-keystore', str(KEY), '-alias', 'zikrtime',
                        '-storepass:env', 'ZIKRTIME_SIGN_PASSWORD'], env)
            if cert.returncode == 0:
                if hashlib.sha256(cert.stdout).hexdigest() != CERT:
                    KEY.unlink(missing_ok=True)
                    return None, 'certificate-mismatch'
                return candidate, 'original-certificate-verified'
        KEY.unlink(missing_ok=True)
    token_like = password.startswith(('github_pat_', 'ghp_'))
    return None, 'wrong-secret-type' if token_like else 'decrypt-or-key-password-failed'


Path('ci-store').mkdir(exist_ok=True)
try:
    password, reason = restore()
    report = {'ready': password is not None, 'reason': reason, 'expected_certificate_sha256': CERT}
    Path('ci-store/signing.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write('ready=' + str(report['ready']).lower() + '\n')
    if sys.argv[1] == 'build':
        env = os.environ.copy()
        env['RELEASE_KEY_PASSWORD'] = password or ''
        subprocess.run(['bash', 'gradlew', 'assembleRelease', 'lintRelease', '--console=plain'],
                       env=env, check=True)
finally:
    KEY.unlink(missing_ok=True)
