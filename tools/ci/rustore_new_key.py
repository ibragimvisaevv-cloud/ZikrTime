"""Prepare a recoverable replacement signing key; never print secret material."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

SECRET = 'RELEASE_KEY_PASSWORD_V2'
ENCRYPTED = Path('keystore/release-v2.jks.enc')
CERTIFICATE = Path('keystore/release-v2.der')
UNSIGNED_SHA = '7b18fd180b4af31eb2270bc6e60555872cd26d38d0dd7f2d79022effea294599'
OLD_CERT = '7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf'
ITERATIONS = '600000'


def checked(args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    if result.returncode:
        raise RuntimeError('Signing tool failed: ' + Path(args[0]).name)
    return result.stdout


def validate_password():
    value = os.environ.get(SECRET, '')
    if len(value) < 20 or not value.isascii() or any(c.isspace() or not c.isprintable() for c in value):
        raise ValueError('Set RELEASE_KEY_PASSWORD_V2 to at least 20 printable ASCII characters without whitespace.')
    if value.startswith(('ghp_', 'github_pat_', 'vcp_', 'sk-', 'gho_')):
        raise ValueError('Use a new dedicated signing password, not an API token.')


def crypt(source, target, decrypt=False):
    checked(['openssl', 'enc', '-aes-256-cbc', '-pbkdf2', '-iter', ITERATIONS,
             *(['-d'] if decrypt else ['-salt']), '-in', str(source), '-out', str(target),
             '-pass', 'env:' + SECRET])
    target.chmod(0o600)


def certificate(key):
    return checked(['keytool', '-exportcert', '-keystore', str(key), '-alias', 'zikrtime',
                    '-storepass:env', SECRET])


def restore(key):
    if not ENCRYPTED.is_file() or not CERTIFICATE.is_file():
        raise RuntimeError('Both the encrypted key and public certificate must exist.')
    crypt(ENCRYPTED, key, decrypt=True)
    cert = certificate(key)
    if cert != CERTIFICATE.read_bytes() or hashlib.sha256(cert).hexdigest() == OLD_CERT:
        raise RuntimeError('Replacement certificate identity check failed.')
    return cert


def prepare_key():
    validate_password()
    ENCRYPTED.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='zikrtime-key-') as directory:
        key = Path(directory) / 'release.jks'
        if ENCRYPTED.exists() or CERTIFICATE.exists():
            cert = restore(key)  # Fail closed; never replace an existing key on error.
        else:
            checked(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'JKS',
                     '-alias', 'zikrtime', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '36500',
                     '-storepass:env', SECRET, '-keypass:env', SECRET,
                     '-dname', 'CN=ZikrTime, O=ZikrTime', '-noprompt'])
            key.chmod(0o600)
            cert = certificate(key)
            encrypted = Path(directory) / 'release.jks.enc'
            crypt(key, encrypted)
            roundtrip = Path(directory) / 'roundtrip.jks'
            crypt(encrypted, roundtrip, decrypt=True)
            if key.read_bytes() != roundtrip.read_bytes() or certificate(roundtrip) != cert:
                raise RuntimeError('Encrypted backup round-trip failed.')
            # Only encrypted key bytes and the public certificate persist.
            ENCRYPTED.write_bytes(encrypted.read_bytes())
            CERTIFICATE.write_bytes(cert)
        digest = hashlib.sha256(cert).hexdigest()
        out = Path('ci-key-backup')
        out.mkdir(exist_ok=True)
        instructions = (
            'ZikrTime Android signing key v2\n'
            'Keep this archive in two safe locations. Store the password separately in your password manager.\n'
            'Password: the value you saved in GitHub RELEASE_KEY_PASSWORD_V2 (not included).\n'
            'Archive contains an encrypted JKS keystore and a PUBLIC DER certificate; alias zikrtime.\n'
            'Outer encryption: OpenSSL AES-256-CBC, PBKDF2-HMAC-SHA256, 600000 iterations.\n'
            'To restore, set RELEASE_KEY_PASSWORD_V2 privately in the environment, then run:\n'
            'openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in release-v2.jks.enc -out release.jks -pass env:RELEASE_KEY_PASSWORD_V2\n'
            'JKS store and key passwords are the same value. Do not share the decrypted file or password.\n'
            'Certificate SHA-256: ' + digest + '\n'
        )
        with zipfile.ZipFile(out / 'ZikrTime-signing-key-v2-ENCRYPTED.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
            archive.write(ENCRYPTED, ENCRYPTED.name)
            archive.write(CERTIFICATE, CERTIFICATE.name)
            archive.writestr('RESTORE.txt', instructions)
        (out / 'certificate.json').write_text(json.dumps({'certificate_sha256': digest, 'alias': 'zikrtime'}, indent=2))
        print('Encrypted key backup verified; public certificate SHA-256: ' + digest)


def sign_existing():
    validate_password()
    source = Path('checked-store/ci-store/ZikrTime-0.8.17-UNSIGNED.apk')
    if hashlib.sha256(source.read_bytes()).hexdigest() != UNSIGNED_SHA:
        raise RuntimeError('Unsigned APK checksum mismatch.')
    report = json.loads(Path('checked-store/ci-store/release-report.json').read_text())
    assert report['source_sha'] == '31560a5f285d9983df6cf3db852526f58cd59898'
    assert report['application_id'] == 'com.zikrtime.app' and report['version_name'] == '0.8.17'
    assert report['version_code'] == 29 and report['debuggable'] is False
    assert report['lint_errors'] == 0 and report['quran_assets_unchanged'] is True
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools'
    signer = str(max(sdk.glob('*/apksigner'), key=lambda p: tuple(map(int, re.findall(r'\d+', p.parent.name)))))
    out = Path('ci-store-v2')
    out.mkdir(exist_ok=True)
    target = out / 'ZikrTime-0.8.17-RuStore.apk'
    with tempfile.TemporaryDirectory(prefix='zikrtime-sign-') as directory:
        key = Path(directory) / 'release.jks'
        digest = hashlib.sha256(restore(key)).hexdigest()
        checked([signer, 'sign', '--ks', str(key), '--ks-key-alias', 'zikrtime',
                 '--ks-pass', 'env:' + SECRET, '--key-pass', 'env:' + SECRET,
                 '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true',
                 '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
                 '--out', str(target), str(source)])
        verification = checked([signer, 'verify', '--verbose', '--print-certs', str(target)]).decode()
        assert 'certificate SHA-256 digest: ' + digest in verification
        assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verification
        with zipfile.ZipFile(source) as before, zipfile.ZipFile(target) as after:
            assert before.namelist() == after.namelist()
            assert all(before.read(n) == after.read(n) for n in before.namelist()), 'APK payload changed'
    apk_digest = hashlib.sha256(target.read_bytes()).hexdigest()
    report.update(ready=True, reason='replacement-certificate-verified', store_upload_ready=True,
                  apk_name=target.name, apk_bytes=target.stat().st_size, apk_sha256=apk_digest,
                  certificate_sha256=digest, rebuilt=False, unsigned_apk_sha256=UNSIGNED_SHA,
                  signing_run=os.environ.get('GITHUB_RUN_ID'))
    (out / 'release-report.json').write_text(json.dumps(report, indent=2))
    (out / 'signature.txt').write_text(verification)
    (out / 'SHA256SUMS.txt').write_text(apk_digest + '  ' + target.name + '\n')
    print('APK signed and verified; ZIP payload unchanged.')


if __name__ == '__main__':
    import sys
    {'prepare-key': prepare_key, 'sign-existing': sign_existing}[sys.argv[1]]()
