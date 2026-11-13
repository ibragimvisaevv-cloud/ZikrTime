"""Disposable key tests; no production key or secret is read or retained."""
import contextlib
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import secrets
import tempfile
import zipfile

spec = importlib.util.spec_from_file_location('replacement', Path(__file__).with_name('rustore_new_key.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
original_dir = Path.cwd()
old_env = os.environ.get(m.SECRET)
password = secrets.token_urlsafe(32)
try:
    with tempfile.TemporaryDirectory(prefix='zikrtime-key-check-') as tmp:
        os.chdir(tmp)
        os.environ[m.SECRET] = password
        with contextlib.redirect_stdout(io.StringIO()):
            m.prepare_key()
            encrypted = m.ENCRYPTED.read_bytes()
            cert = m.CERTIFICATE.read_bytes()
            m.prepare_key()
        assert encrypted == m.ENCRYPTED.read_bytes() and cert == m.CERTIFICATE.read_bytes()
        assert hashlib.sha256(cert).hexdigest() != m.OLD_CERT
        with zipfile.ZipFile('ci-key-backup/ZikrTime-signing-key-v2-ENCRYPTED.zip') as backup:
            assert set(backup.namelist()) == {'release-v2.jks.enc','release-v2.der','RESTORE.txt'}
            assert all(password.encode() not in backup.read(n) for n in backup.namelist())
        assert not list(Path('.').rglob('*.jks'))
        os.environ[m.SECRET] = secrets.token_urlsafe(32)
        try:
            m.prepare_key()
            raise AssertionError('Wrong password was accepted')
        except RuntimeError:
            pass
        assert m.ENCRYPTED.read_bytes() == encrypted and m.CERTIFICATE.read_bytes() == cert
        m.CERTIFICATE.unlink()
        os.environ[m.SECRET] = password
        try:
            m.prepare_key()
            raise AssertionError('Partial identity was replaced')
        except RuntimeError:
            pass
        assert m.ENCRYPTED.read_bytes() == encrypted and not m.CERTIFICATE.exists()
        for invalid in ('', 'short', 'x'*20+'\n', 'vcp_'+ 'x'*40):
            os.environ[m.SECRET] = invalid
            try:
                m.validate_password()
                raise AssertionError('Invalid secret was accepted')
            except ValueError:
                pass
finally:
    os.chdir(original_dir)
    if old_env is None:os.environ.pop(m.SECRET,None)
    else:os.environ[m.SECRET]=old_env
print('PASS: encrypted round-trip, stable identity, backup contents, wrong-password and partial-file protection, password validation.')
