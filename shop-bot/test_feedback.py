"""Feedback journeys against private/public stores and Telegram doubles; no network."""
import json
import os
import unittest
from unittest.mock import patch

os.environ["BOT_TOKEN"] = "123:TEST"
os.environ["OWNER_USERNAME"] = ""
import index


class Storage:
    def __init__(self):
        self.files = {}
        self.fail = False

    def get(self, key):
        return self.files.get(key)

    def put(self, key, data, *args, **kwargs):
        if self.fail:
            raise index.Conflict()
        self.files[key] = data

    def delete(self, key):
        self.files.pop(key, None)


class Telegram:
    def __init__(self):
        self.sent = []

    def call(self, method, **params):
        if method == "getMe":
            return {"username": "ZikrTimeShopBot"}
        self.sent.append((method, params))
        return {"ok": True}


class FeedbackTests(unittest.TestCase):
    def setUp(self):
        self.public, self.private, self.tg = Storage(), Storage(), Telegram()
        self.seq = 0
        self.private.files[index.DB_KEY] = json.dumps({"owner": 1, "admins": [1, 9]}).encode()

    def bot(self):
        return index.Bot(self.public, index.BufferedTelegram(self.tg), data=self.private)

    def update(self, uid=2, text=None, callback=None, **extra):
        self.seq += 1
        if callback is not None:
            update = {"callback_query": {"id": str(self.seq), "from": {"id": uid},
                       "message": {"chat": {"type": "private", "id": uid}}, "data": callback}}
        else:
            message = {"from": {"id": uid}, "chat": {"id": uid, "type": "private"}, "message_id": self.seq}
            if text is not None:
                message["text"] = text
            message.update(extra)
            update = {"message": message}
        bot = self.bot()
        bot.handle(update)
        bot.save()
        return bot

    def draft(self):
        self.update(text="/start feedback_0-8-16_quran")
        return self.update(callback="fb:new:bug")

    def ticket(self):
        self.draft()
        b = self.update(text="Ошибка <b>в тексте</b>")
        nonce = b.user(2)["state"]["id"]
        self.update(callback="fb:preview:" + nonce)
        return self.update(callback="fb:send:" + nonce), nonce

    def test_private_text_photos_documents_and_idempotent_submission(self):
        self.draft()
        self.update(photo=[{"file_id": "thumb"}, {"file_id": "photo-original"}], caption="Не видно знака <")
        b = self.update(document={"file_id": "screenshot-file", "file_size": 100, "mime_type": "image/png"})
        nonce = b.user(2)["state"]["id"]
        self.update(callback="fb:preview:" + nonce)
        b = self.update(callback="fb:send:" + nonce)
        self.assertEqual(len(b.db["feedback_tickets"]), 1)
        ticket = b.db["feedback_tickets"]["1"]
        self.assertEqual(ticket["context"], "0-8-16_quran")
        self.assertEqual([p["file_id"] for p in ticket["photos"]], ["photo-original", "screenshot-file"])
        self.assertEqual(self.public.files, {})
        self.assertNotIn("feedback", json.dumps(b.build_catalog()))
        b = self.update(callback="fb:send:" + nonce)
        self.assertEqual(len(b.db["feedback_tickets"]), 1)
        owner_photos = [(method, p) for method, p in self.tg.sent if p.get("chat_id") == 1 and method in ("sendPhoto", "sendDocument")]
        self.assertEqual(len(owner_photos), 2)
        self.assertTrue(any("&lt;" in p.get("text", "") for method, p in self.tg.sent if p.get("chat_id") == 1))

    def test_only_author_and_owner_can_read_or_reply(self):
        self.ticket()
        for uid in (3, 9):
            for action in ("view:1", "history:1", "reply:1", "ask:1", "status:1:closed", "list:inbox:0"):
                before = len(self.tg.sent)
                self.update(uid=uid, callback="fb:" + action)
                sent = self.tg.sent[before:]
                self.assertFalse(any(method in ("sendPhoto", "sendDocument") for method, p in sent))
                self.assertFalse(any("Ошибка &lt;b&gt;" in p.get("text", "") for method, p in sent))
        self.update(uid=1, callback="fb:ask:1")
        b = self.update(uid=1, text="Какая версия?")
        self.assertEqual(b.db["feedback_tickets"]["1"]["status"], "question")
        self.update(callback="fb:reply:1")
        b = self.update(text="0.8.16")
        self.assertEqual(b.db["feedback_tickets"]["1"]["status"], "new")
        self.assertEqual(len(b.db["feedback_tickets"]["1"]["replies"]), 2)
        b = self.update(uid=1, callback="fb:status:1:closed")
        self.assertEqual(b.db["feedback_tickets"]["1"]["status"], "closed")
        self.update(callback="fb:reply:1")
        self.assertIsNone(self.bot().user(2)["state"])

    def test_limits_and_replayed_photo(self):
        self.draft()
        photo = {"photo": [{"file_id": "photo"}], "caption": "Текст", "message_id": 100}
        self.update(**photo)
        self.update(**photo)
        self.assertEqual(len(self.bot().user(2)["state"]["photos"]), 1)
        for _ in range(6):
            self.update(photo=[{"file_id": "next"}])
        b = self.bot()
        self.assertEqual(len(b.user(2)["state"]["photos"]), 5)
        self.update(text="x" * 3001)
        self.assertEqual(self.bot().user(2)["state"]["text"], "Текст")
        self.update(document={"file_id": "bad", "file_size": 100, "mime_type": "application/exe"})
        self.assertEqual(len(self.bot().user(2)["state"]["photos"]), 5)

    def test_missing_owner_keeps_draft(self):
        b = self.draft()
        b.db["owner"] = None
        b.dirty = True
        b.save()
        b = self.update(text="Проблема")
        b = self.update(callback="fb:send:" + b.user(2)["state"]["id"])
        self.assertEqual(b.db.get("feedback_tickets", {}), {})
        self.assertEqual(b.user(2)["state"]["text"], "Проблема")

    def test_conflict_does_not_send_before_persistence(self):
        b = self.draft()
        b = self.update(text="Проблема")
        nonce = b.user(2)["state"]["id"]
        b = self.bot()
        before = len(self.tg.sent)
        b.feedback_submit(2, nonce)
        self.assertEqual(len(self.tg.sent), before)
        self.private.fail = True
        with self.assertRaises(index.Conflict):
            b.save()
        self.assertEqual(len(self.tg.sent), before)
        self.private.fail = False
        b = self.update(callback="fb:send:" + nonce)
        self.assertEqual(len(b.db["feedback_tickets"]), 1)

    def test_daily_limit_and_stale_callback(self):
        for _ in range(5):
            self.ticket()
        b, nonce = self.ticket()
        self.assertEqual(len(b.db["feedback_tickets"]), 5)
        self.assertIsNotNone(b.user(2)["state"])
        self.update(text="/cancel")
        self.update(callback="fb:send:" + nonce)
        self.assertEqual(len(self.bot().db["feedback_tickets"]), 5)

    def test_feedback_entry_and_malformed_actions(self):
        b = self.update(text="/feedback")
        self.assertIsNone(b.user(2)["state"])
        for data in ("fb:", "fb:reply", "fb:status", "fb:list:inbox:NaN", "fb:new:unknown"):
            self.update(callback=data)
        self.assertNotIn("feedback_tickets", self.bot().db)


if __name__ == "__main__":
    unittest.main()
