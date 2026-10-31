"""Check/use the original Android key without printing or exporting secrets."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

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
    elif sys.argv[1] == 'sign-existing':
        if password is None:
            raise SystemExit('Original release key remains unavailable; no APK signed.')
        source = Path('checked-store/ci-store/ZikrTime-0.8.17-UNSIGNED.apk')
        expected = '7b18fd180b4af31eb2270bc6e60555872cd26d38d0dd7f2d79022effea294599'
        assert hashlib.sha256(source.read_bytes()).hexdigest() == expected
        previous = json.loads(Path('checked-store/ci-store/release-report.json').read_text())
        assert previous['source_sha'] == '31560a5f285d9983df6cf3db852526f58cd59898'
        assert previous['version_code'] == 29 and previous['debuggable'] is False
        assert previous['lint_errors'] == 0 and previous['quran_assets_unchanged'] is True
        signers = list((Path(os.environ['ANDROID_HOME']) / 'build-tools').glob('*/apksigner'))
        signer = str(max(signers, key=lambda p: tuple(map(int, re.findall(r'\d+', p.parent.name)))))
        target = Path('ci-store/ZikrTime-0.8.17-RuStore.apk')
        env = dict(os.environ, ZIKRTIME_SIGN_PASSWORD=password)
        signed = run([signer, 'sign', '--ks', str(KEY), '--ks-key-alias', 'zikrtime',
                      '--ks-pass', 'env:ZIKRTIME_SIGN_PASSWORD', '--key-pass', 'env:ZIKRTIME_SIGN_PASSWORD',
                      '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true',
                      '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
                      '--out', str(target), str(source)], env)
        assert signed.returncode == 0, 'APK signing failed'
        verified = run([signer, 'verify', '--verbose', '--print-certs', str(target)], env)
        verification = verified.stdout.decode('utf-8', 'replace')
        assert verified.returncode == 0 and 'certificate SHA-256 digest: ' + CERT in verification
        assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verification
        with zipfile.ZipFile(source) as old, zipfile.ZipFile(target) as new:
            assert old.namelist() == new.namelist()
            assert all(old.read(name) == new.read(name) for name in old.namelist()), 'APK payload changed'
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        previous.update(ready=True, reason='original-certificate-verified', store_upload_ready=True,
                        apk_name=target.name, apk_bytes=target.stat().st_size, apk_sha256=digest,
                        certificate_sha256=CERT, rebuilt=False, unsigned_apk_sha256=expected,
                        signing_run=int(os.environ['GITHUB_RUN_ID']))
        Path('ci-store/release-report.json').write_text(json.dumps(previous, indent=2))
        Path('ci-store/signature.txt').write_text(verification)
        Path('ci-store/SHA256SUMS.txt').write_text(digest + '  ' + target.name + '\n')
        print('Previously built APK signed; original certificate and unchanged APK payload verified.')
finally:
    KEY.unlink(missing_ok=True)
