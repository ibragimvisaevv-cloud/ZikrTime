"""Mini App permissions, private media, retries and quiet Telegram entry points."""
import base64
import hashlib
import hmac
import json
import time
import unittest
from unittest.mock import patch
from urllib.parse import urlencode

import community
import index
import miniapi

TOKEN = "123:TEST"


class Storage:
    def __init__(self):
        self.files = {}
        self.tag = 0
        self.conflict_once = False

    def get(self, key):
        return self.files.get(key)

    def get_tagged(self, key):
        return self.get(key), str(self.tag) if key in self.files else None

    def put(self, key, value, content_type, **kwargs):
        if key == index.DB_KEY and self.conflict_once:
            self.conflict_once = False
            raise index.Conflict()
        self.files[key] = value
        self.tag += 1

    def delete(self, key):
        self.files.pop(key, None)


class Telegram:
    def __init__(self):
        self.sent = []

    def call(self, method, **params):
        self.sent.append((method, params))
        return {"username": "ZikrTimeShopBot"} if method == "getMe" else {"ok": True}


def signed(uid, age=0):
    fields = {"auth_date": str(int(time.time()) - age), "user": json.dumps({"id": uid, "first_name": "Test"})}
    check = "\n".join(f"{key}={value}" for key, value in sorted(fields.items()))
    secret = hmac.new(b"WebAppData", TOKEN.encode(), hashlib.sha256).digest()
    fields["hash"] = hmac.new(secret, check.encode(), hashlib.sha256).hexdigest()
    return urlencode(fields)


class CommunityTests(unittest.TestCase):
    def setUp(self):
        self.public, self.private = Storage(), Storage()
        self.private.files[index.DB_KEY] = json.dumps({"owner": 1, "admins": [1, 9], "bot_username": "ZikrTimeShopBot"}).encode()

    def call(self, uid, action, **data):
        with patch.object(index, "BOT_TOKEN", TOKEN), patch.object(index, "make_storage", side_effect=lambda token="", bucket=None: self.private if bucket == index.DATA_BUCKET else self.public):
            result = miniapi.handler({"httpMethod": "POST", "body": json.dumps(dict(data, initData=signed(uid), action=action))}, None)
        return result["statusCode"], json.loads(result["body"])

    def database(self):
        return json.loads(self.private.files[index.DB_KEY])

    def create(self, uid=2, nonce="fixture-ticket-001"):
        code, result = self.call(uid, "ticket_create", kind="bug", text="Не открывается нужный раздел", request_id=nonce)
        self.assertEqual(code, 200, result)
        return result["ticket"]

    def test_private_photo_owned_upload_read_and_no_publication(self):
        image = "data:image/jpeg;base64," + base64.b64encode(b"\xff\xd8\xff" + b"0" * 30).decode()
        code, upload = self.call(2, "private_photo_upload", data=image, request_id="fixture-upload-001")
        self.assertEqual(code, 200)
        code, repeat = self.call(2, "private_photo_upload", data=image, request_id="fixture-upload-001")
        self.assertEqual(repeat, upload)
        self.assertFalse(self.public.files)
        self.assertEqual(len(self.database()["private_photos"]), 1)
        for uid in (2, 1):
            code, photo = self.call(uid, "private_photo", photo_id=upload["photo_id"])
            self.assertEqual(code, 200)
            self.assertEqual(photo["data"], image)
        for uid in (3, 9):
            self.assertEqual(self.call(uid, "private_photo", photo_id=upload["photo_id"])[0], 404)
        self.assertEqual(self.call(3, "ticket_create", kind="bug", text="У меня тоже ошибка", request_id="fixture-ticket-002", photos=[upload["photo_id"]])[0], 404)

    def test_ticket_retry_conflict_and_cross_user_visibility(self):
        self.private.conflict_once = True
        first = self.create()
        self.assertEqual(self.create()["id"], first["id"])
        self.assertEqual(len(self.database()["feedback_tickets"]), 1)
        self.assertEqual(self.call(3, "tickets")[1]["total"], 0)
        self.assertEqual(self.call(3, "ticket", tid=first["id"])[0], 404)
        self.assertEqual(self.call(9, "tickets", inbox=True)[0], 403)
        self.assertEqual(self.call(1, "tickets", inbox=True)[1]["total"], 1)
        self.assertFalse(self.database().get("outbox"))

    def test_reply_status_and_retry_survive_reload(self):
        ticket = self.create()
        data = dict(tid=ticket["id"], status="question", text="Какая версия приложения?", request_id="fixture-reply-001")
        self.assertEqual(self.call(9, "ticket_update", **data)[0], 404)
        self.assertEqual(self.call(1, "ticket_update", **data)[0], 200)
        self.call(1, "ticket_update", **data)
        code, fetched = self.call(2, "ticket", tid=ticket["id"])
        self.assertEqual(fetched["ticket"]["status"], "question")
        self.assertEqual(len(fetched["ticket"]["replies"]), 1)
        self.assertEqual(self.call(2, "ticket_update", tid=ticket["id"], text="Версия 0.8.23", request_id="fixture-reply-002")[1]["ticket"]["status"], "new")

    def test_place_requires_moderation_and_finite_coordinates(self):
        data = dict(name="Тестовая мечеть", kind="mosque", lat=43.1, lon=45.3, request_id="fixture-place-001")
        code, result = self.call(2, "place_create", **data)
        self.assertEqual(code, 200, result)
        pid = result["place"]["id"]
        self.assertEqual(result["place"]["status"], "pending")
        self.assertNotIn("places.json", self.public.files)
        self.assertEqual(self.call(2, "place_create", **data)[1]["place"]["id"], pid)
        self.assertEqual(self.call(3, "my_places")[1]["total"], 0)
        self.assertEqual(self.call(9, "mod_place", pid=pid, do="ok")[0], 403)
        self.assertEqual(self.call(1, "mod_place", pid=pid, do="ok")[0], 200)
        self.assertIn("places.json", self.public.files)
        for value in ("nan", "inf", 95):
            self.assertEqual(self.call(2, "place_create", **dict(data, lat=value, request_id="fixture-place-002"))[0], 400)

    def test_user_stats_only_owner_and_pagination(self):
        self.call(2, "me")
        self.call(3, "me")
        self.assertEqual(self.call(2, "users")[0], 403)
        self.assertEqual(self.call(9, "users")[0], 403)
        code, stats = self.call(1, "users")
        self.assertEqual(code, 200)
        self.assertEqual(stats["stats"]["miniapp"], 2)
        self.assertEqual({u["id"] for u in stats["users"]}, {2, 3})
        self.assertNotIn("inbox_counts", self.call(2, "me")[1])
        self.assertEqual(self.call(1, "users", offset=-1)[0], 400)

    def test_future_or_duplicated_auth_fields_rejected(self):
        self.assertIsNone(miniapi.check_init_data(signed(2, age=-600), TOKEN))
        self.assertIsNone(miniapi.check_init_data(signed(2) + "&auth_date=1", TOKEN))

    def test_telegram_start_is_single_button_and_old_callback_edits(self):
        telegram = Telegram()
        update = {"update_id": 1, "message": {"from": {"id": 2}, "chat": {"id": 2, "type": "private"}, "text": "/start"}}
        bot = index.Bot(self.public, telegram, TOKEN, data=self.private)
        bot.handle(update)
        bot.save()
        self.assertEqual(len(telegram.sent), 1)
        buttons = telegram.sent[0][1]["reply_markup"]["inline_keyboard"]
        self.assertEqual(len(buttons), 1)
        self.assertIn("web_app", buttons[0][0])
        bot = index.Bot(self.public, telegram, TOKEN, data=self.private)
        bot.handle(update)
        self.assertEqual(len(telegram.sent), 1)
        bot.handle({"update_id": 2, "callback_query": {"id": "2", "from": {"id": 2}, "data": "fb:home", "message": {"message_id": 9, "chat": {"id": 2, "type": "private"}}}})
        self.assertEqual(telegram.sent[-1][0], "editMessageText")
        self.assertEqual(telegram.sent[-1][1]["message_id"], 9)
        self.assertIsNone(bot.user(2)["state"])


if __name__ == "__main__":
    unittest.main()
