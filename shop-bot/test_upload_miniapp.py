"""Deployment ordering and rollback safety without contacting cloud storage."""
import contextlib
import hashlib
import io
import os
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch

import upload_miniapp


class Storage:
    def __init__(self, fail_at=None):
        self.original = {"miniapp/index.html": b"previous page", "miniapp/app.js": b"legacy JS",
                         "miniapp/app.previous.js": b"previous versioned JS"}
        self.files = dict(self.original)
        self.calls = []
        self.fail_at = fail_at

    def put(self, key, data, content_type, cache):
        self.calls.append((key, content_type, cache))
        if len(self.calls) == self.fail_at:
            raise OSError("Synthetic upload failure")
        self.files[key] = data


class UploadMiniappTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "miniapp").mkdir()
        (self.root / "site").mkdir()
        self.page = (b'<script src="https://telegram.org/js/telegram-web-app.js"></script>'
                     b'<script src="config.js"></script><link rel="stylesheet" href="style.css">'
                     b'<script src="app.js"></script>')
        for name, content in {"index.html": self.page, "app.js": b"console.log('new');",
                              "style.css": b"body { color: black; }"}.items():
            (self.root / "miniapp" / name).write_bytes(content)
        (self.root / "site/privacy.html").write_bytes(b"Privacy {{CONTACT}}")
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(upload_miniapp, "__file__", str(self.root / "upload_miniapp.py")))
        self.stack.enter_context(patch.dict(os.environ, {"API_URL": "https://functions.yandexcloud.net/test123"}))
        self.stack.enter_context(patch.object(upload_miniapp, "bot_contact", return_value="contact"))
        self.sdk = self.stack.enter_context(patch.object(upload_miniapp.urllib.request, "urlopen",
                                                       side_effect=lambda *args, **kwargs: io.BytesIO(b"/* Telegram SDK */")))
        self.stack.enter_context(contextlib.redirect_stdout(io.StringIO()))

    def publish(self, storage):
        with patch.object(upload_miniapp.index, "make_storage", return_value=storage):
            upload_miniapp.main()

    def test_assets_are_versioned_and_entry_point_is_last(self):
        storage = Storage()
        self.publish(storage)
        self.assertEqual(storage.calls[-1], ("miniapp/index.html", upload_miniapp.TYPES["html"], "no-cache"))
        references = re.findall(rb'(?:src|href)="([^"]+)"', storage.files["miniapp/index.html"])
        self.assertEqual(len(references), 4)
        for reference in references:
            name = reference.decode()
            self.assertRegex(name, r"^(app|style|config|telegram-web-app)\.[0-9a-f]{64}\.(js|css)$")
            data = storage.files["miniapp/" + name]
            self.assertEqual(name.split(".")[-2], hashlib.sha256(data).hexdigest())
        self.assertTrue(all(call[2] == "public, max-age=31536000, immutable" for call in storage.calls[:4]))
        self.assertEqual([call[0] for call in storage.calls[4:-1]], ["privacy.html", "seller.html"])
        self.assertEqual(storage.files["privacy.html"], b"Privacy contact")
        self.assertIn(b"https://functions.yandexcloud.net/test123", storage.files["seller.html"])
        self.assertEqual(storage.files["miniapp/app.js"], storage.original["miniapp/app.js"])
        self.assertEqual(storage.files["miniapp/app.previous.js"], storage.original["miniapp/app.previous.js"])
        self.assertEqual((self.root / "miniapp/index.html").read_bytes(), self.page)

    def test_each_failure_before_entry_point_preserves_previous_page(self):
        # Four assets, the privacy page and the seller landing precede the new index.
        for failure in range(1, 7):
            with self.subTest(failure=failure):
                storage = Storage(fail_at=failure)
                with self.assertRaises(OSError):
                    self.publish(storage)
                self.assertTrue(all(storage.files[key] == data for key, data in storage.original.items()))
                self.assertNotIn("miniapp/index.html", [call[0] for call in storage.calls])

    def test_changed_asset_gets_new_address_and_old_address_survives(self):
        storage = Storage()
        self.publish(storage)
        old_app = next(key for key in storage.files if re.fullmatch(r"miniapp/app\.[0-9a-f]{64}\.js", key))
        old_data = storage.files[old_app]
        (self.root / "miniapp/app.js").write_bytes(b"console.log('updated');")
        self.publish(storage)
        new_app = re.search(rb'src="(app\.[0-9a-f]{64}\.js)"', storage.files["miniapp/index.html"])[1].decode()
        self.assertNotEqual("miniapp/" + new_app, old_app)
        self.assertEqual(storage.files[old_app], old_data)
        self.assertEqual(storage.files["miniapp/" + new_app], b"console.log('updated');")

    def test_sdk_download_failure_keeps_remote_reference(self):
        self.sdk.side_effect = OSError("Synthetic network failure")
        storage = Storage()
        self.publish(storage)
        self.assertIn(upload_miniapp.TELEGRAM_SDK.encode(), storage.files["miniapp/index.html"])
        self.assertEqual(storage.calls[-1][0], "miniapp/index.html")
        self.assertFalse(any("telegram-web-app." in call[0] for call in storage.calls))

    def test_invalid_api_is_rejected_before_upload(self):
        with patch.dict(os.environ, {"API_URL": "http://invalid.example"}), \
                patch.object(upload_miniapp.index, "make_storage") as storage:
            with self.assertRaises(ValueError):
                upload_miniapp.main()
            storage.assert_not_called()


if __name__ == "__main__":
    unittest.main()
