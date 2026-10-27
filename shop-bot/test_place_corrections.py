"""Synthetic end-to-end place correction checks. No production network or database."""
import copy
import gzip
import json
import os
import unittest
from unittest.mock import patch

os.environ["BOT_TOKEN"] = "123:TEST"
import index
import import_osm
from place_corrections import value_for


class Storage:
    def __init__(self):
        self.files = {}
    def get(self, key):
        return self.files.get(key)
    def put(self, key, data, content_type, cache=None, encoding=None, **kwargs):
        self.files[key] = gzip.decompress(data) if encoding == "gzip" else data
    def delete(self, key):
        self.files.pop(key, None)


class Telegram:
    def __init__(self):
        self.sent = []
    def call(self, method, **params):
        self.sent.append((method, params))
        return {"username": "ZikrTimeTestBot"} if method == "getMe" else {"ok": True}


class PlaceCorrectionsTest(unittest.TestCase):
    def setUp(self):
        # Any accidental attempt to use real HTTP makes the test fail.
        self.network_guard = patch("urllib.request.urlopen", side_effect=AssertionError("No network in tests"))
        self.network_guard.start()
        self.addCleanup(self.network_guard.stop)
        self.storage, self.tg = Storage(), Telegram()
        self.seq = 0
        bot = self.bot()
        bot.db.update(owner=1, admins=[1], bot_username="ZikrTimeTestBot")
        bot.db["places"] = {"osm_node_123": {"id": "osm_node_123", "name": "Test place", "kind": "mosque",
            "lat": 55.7, "lon": 37.6, "status": "approved", "source": "osm", "created": 1,
            "updated": 100, "hours": "08:00–20:00", "who": "both", "wudu": False}}
        bot.dirty = bot.places_dirty = True
        bot.save()
    def bot(self):
        return index.Bot(self.storage, self.tg, "123:TEST")
    def state(self):
        return json.loads(self.storage.files[index.DB_KEY])
    def public(self):
        return json.loads(self.storage.files["places.json"])["places"][0]
    def event(self, uid, text=None, callback=None):
        self.seq += 1
        frm = {"id": uid, "username": "synthetic" + str(uid)}
        if callback is None:
            update = {"update_id": self.seq, "message": {"from": frm, "chat": {"id": uid, "type": "private"}, "text": text}}
        else:
            update = {"update_id": self.seq, "callback_query": {"id": str(self.seq), "from": frm,
                "message": {"chat": {"id": uid, "type": "private"}}, "data": callback}}
        bot = self.bot()
        bot.handle(update)
        bot.save()
        return bot
    def propose(self, field, value, uid=2):
        self.event(uid, "/start fix_osm_node_123")
        self.event(uid, callback="pfx:" + field)
        if field in ("wudu", "women"):
            self.event(uid, callback="pfv:" + value)
        else:
            self.event(uid, value)
        self.event(uid, callback="pfsend")
        return list(self.state()["place_corrections"])[-1]

    def test_open_preview_and_cancel_do_not_submit(self):
        before = self.storage.files["places.json"]
        self.event(2, "/start fix_osm_node_123")
        self.assertEqual(self.state()["users"]["2"]["state"]["place_id"], "osm_node_123")
        self.event(2, callback="pfx:wudu")
        self.event(2, callback="pfv:yes")
        self.assertEqual(self.state()["place_corrections"], {})
        self.assertEqual(before, self.storage.files["places.json"])
        self.event(2, "/cancel")
        self.event(2, callback="pfsend")
        self.assertEqual(self.state()["place_corrections"], {})

    def test_submit_approve_publish_and_repeat_are_idempotent(self):
        before = self.storage.files["places.json"]
        rid = self.propose("wudu", "yes")
        self.assertEqual(self.state()["place_corrections"][rid]["status"], "pending")
        self.assertEqual(before, self.storage.files["places.json"])
        self.event(2, callback="pfsend")
        self.assertEqual(len(self.state()["place_corrections"]), 1)
        self.event(2, callback="pfmod:ok:" + rid)  # requester is not an admin
        self.assertFalse(self.public()["wudu"])
        self.event(1, callback="pfmod:ok:" + rid)
        self.assertTrue(self.public()["wudu"])
        self.assertEqual(self.state()["place_corrections"][rid]["status"], "approved")
        self.assertNotIn("confirmed_at", self.public())
        self.assertTrue(self.state()["places"]["osm_node_123"]["edited"])
        snapshot = self.storage.files["places.json"]
        self.event(1, callback="pfmod:ok:" + rid)
        self.event(1, callback="pfmod:no:" + rid)
        self.assertEqual(snapshot, self.storage.files["places.json"])
        self.assertEqual(self.state()["place_corrections"][rid]["status"], "approved")

    def test_rejection_and_unknown_values(self):
        rid = self.propose("women", "no")
        self.event(1, callback="pfmod:no:" + rid)
        self.assertNotIn("women", self.public())
        self.assertEqual(self.state()["place_corrections"][rid]["status"], "rejected")
        rid2 = self.propose("women", "unknown")
        self.event(1, callback="pfmod:ok:" + rid2)
        self.assertIn("women", self.public())
        self.assertIsNone(self.public()["women"])  # override old who=both, don't infer false

    def test_stale_conflicting_request_does_not_overwrite(self):
        a = self.propose("hours", "09:00–18:00", 2)
        b = self.propose("hours", "10:00–17:00", 3)
        self.event(1, callback="pfmod:ok:" + a)
        self.event(1, callback="pfmod:ok:" + b)
        self.assertEqual(self.public()["hours"], "09:00–18:00")
        self.assertEqual(self.state()["place_corrections"][b]["status"], "pending")
        self.event(1, callback="pfmod:no:" + b)
        self.assertEqual(self.state()["place_corrections"][b]["status"], "rejected")

    def test_text_fields_actual_date_and_osm_import_survive(self):
        for field, value in (("access_conditions", "Ask reception"), ("entrance", "East door"),
                             ("source_details", "Reception, phone call"), ("confirmed_at", "2026-01-01")):
            rid = self.propose(field, value)
            self.event(1, callback="pfmod:ok:" + rid)
            self.assertEqual(self.public()[field], value)
        db = self.state()
        changed = copy.deepcopy(db["places"]["osm_node_123"])
        import_osm.merge(db, [{"id": "osm_node_123", "source": "osm", "name": "Fresh OSM name"}])
        self.assertEqual(db["places"]["osm_node_123"], changed)
        self.assertEqual(self.public()["src"], "osm")
        self.assertNotIn("author", self.public())
        self.assertNotIn("place_corrections", self.public())

    def test_malformed_values_and_dates_remain_unknown(self):
        for field in ("women", "wudu"):
            for bad in ("false", "true", "0", ""):
                with self.assertRaises(ValueError):
                    value_for(field, bad)
        for bad in ("2026-02-30", "2026-1-01", "2099-01-01", "123"):
            with self.assertRaises(ValueError):
                value_for("confirmed_at", bad)
        self.assertEqual(value_for("confirmed_at", "?"), "")
        bot = self.bot()
        bot.db["places"]["osm_node_123"].update(wudu="false", women=1)
        item = bot.build_places()["places"][0]
        self.assertIsNone(item["wudu"])
        self.assertIsNone(item["women"])
        self.assertNotIn("confirmed_at", item)

    def test_hidden_missing_and_malformed_callbacks_are_safe(self):
        self.event(2, "/start fix_not_found")
        self.event(2, callback="pfx:wudu")
        self.event(2, callback="pfsend")
        self.event(2, callback="pfmod")
        self.event(2, callback="pfv:garbage")
        self.assertEqual(self.state()["place_corrections"], {})
        rid = self.propose("entrance", "West")
        bot = self.bot()
        bot.db["places"]["osm_node_123"]["status"] = "hidden"
        bot.dirty = True
        bot.save()
        self.event(1, callback="pfmod:ok:" + rid)
        self.assertEqual(self.state()["place_corrections"][rid]["status"], "pending")

    def test_admin_place_button_does_not_open_promo_claim(self):
        self.event(1, callback="pl_all")
        self.event(1, callback="plcard:osm_node_123")
        texts = [p.get("text", "") for _, p in self.tg.sent if p.get("chat_id") == 1]
        self.assertTrue(any("Test place" in t for t in texts))
        self.assertFalse(any("Заявка не найдена" in t for t in texts))


if __name__ == "__main__":
    unittest.main()
