"""Offline checks for real importer entry points, CAS writes and deferred messages."""
import copy
import gzip
import json
import os
import unittest
from unittest.mock import patch

os.environ.setdefault("BOT_TOKEN", "123:TEST")
import index
import import_curated
import import_wikidata
from places_sources import publish_import, remember_removed


class Storage:
    def __init__(self, state=None):
        self.files = {index.DB_KEY: json.dumps(state).encode()} if state is not None else {}
        self.etag = "e1"
        self.conflict = False
        self.fail_places = False

    def get_tagged(self, key):
        return self.files.get(key), self.etag if key in self.files else None

    def put(self, key, body, content_type, **kwargs):
        if key == index.DB_KEY:
            if self.conflict:
                state = json.loads(self.files[key])
                state["concurrent_owner_change"] = "keep"
                self.files[key] = json.dumps(state).encode()
                self.etag = "e2"
            if kwargs.get("if_match") != self.etag:
                raise index.Conflict("changed database")
            self.etag += "x"
        if key == "places.json" and self.fail_places:
            raise OSError("temporary publication failure")
        self.files[key] = gzip.decompress(body) if kwargs.get("encoding") == "gzip" else body


class ImportPublicationTest(unittest.TestCase):
    def setUp(self):
        self.network = patch("urllib.request.urlopen", side_effect=AssertionError("No network in importer tests"))
        self.network.start()
        self.addCleanup(self.network.stop)
        self.outbox = [["sendMessage", {"chat_id": 44, "text": "queued message"}]]
        state = {"owner": 1, "admins": [1], "bot_username": "ZikrTimeTestBot", "places": {}, "outbox": copy.deepcopy(self.outbox)}
        self.private = Storage(state)
        self.public = Storage()
        self.public.files["catalog.json"] = b"unchanged catalog"
        self.items = import_curated.load()

    def publish(self):
        return publish_import(lambda db: import_curated.merge(db, self.items), self.public, self.private)

    def state(self):
        return json.loads(self.private.files[index.DB_KEY])

    def test_four_places_publish_and_repeat_without_messages_or_catalog_changes(self):
        self.assertEqual(self.publish(), (4, 0, 0))
        self.assertEqual(self.publish(), (0, 0, 0))
        self.assertEqual(self.state()["outbox"], self.outbox)
        self.assertEqual(self.public.files["catalog.json"], b"unchanged catalog")
        public = json.loads(self.public.files["places.json"])
        self.assertEqual(len(public["places"]), 4)
        for place in public["places"]:
            self.assertNotIn("author", place)
            self.assertNotIn("evidence", place)
            self.assertNotIn("confirmed_at", place)
            self.assertIsNone(place["wudu"])
            self.assertIsNone(place["women"])

    def test_owner_edits_hidden_places_and_deleted_tombstones_survive(self):
        self.publish()
        state = self.state()
        first, second, third = self.items[:3]
        state["places"][first["id"]].update(name="Owner name", edited=True)
        state["places"][second["id"]]["status"] = "hidden"
        remember_removed(state, state["places"][third["id"]])
        del state["places"][third["id"]]
        self.private.files[index.DB_KEY] = json.dumps(state).encode()
        self.assertEqual(self.publish(), (0, 0, 0))
        current = self.state()["places"]
        self.assertEqual(current[first["id"]]["name"], "Owner name")
        self.assertEqual(current[second["id"]]["status"], "hidden")
        self.assertNotIn(third["id"], current)

    def test_cas_conflict_preserves_newer_database_and_does_not_publish(self):
        self.private.conflict = True
        with self.assertRaises(index.Conflict):
            self.publish()
        self.assertEqual(self.state()["concurrent_owner_change"], "keep")
        self.assertEqual(self.state()["places"], {})
        self.assertEqual(self.state()["outbox"], self.outbox)
        self.assertNotIn("places.json", self.public.files)

    def test_publication_failure_can_be_retried_without_rolling_back_database(self):
        self.public.fail_places = True
        with self.assertRaises(OSError):
            self.publish()
        self.assertEqual(len(self.state()["places"]), 4)
        self.assertEqual(self.state()["outbox"], self.outbox)
        self.public.fail_places = False
        self.assertEqual(self.publish(), (0, 0, 0))
        self.assertEqual(len(json.loads(self.public.files["places.json"])["places"]), 4)

    def test_queue_is_restored_for_legacy_bot_initializer(self):
        class LegacyBot(index.Bot):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, **kwargs)
                self.outbox_out = self.db.pop("outbox", [])
        with patch.object(index, "Bot", LegacyBot):
            self.publish()
        self.assertEqual(self.state()["outbox"], self.outbox)

    def test_missing_database_or_cached_username_never_creates_or_migrates_data(self):
        with self.assertRaises(RuntimeError):
            publish_import(lambda db: None, self.public, Storage())
        state = self.state()
        del state["bot_username"]
        self.private.files[index.DB_KEY] = json.dumps(state).encode()
        before = dict(self.private.files)
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assertEqual(self.private.files, before)
        self.assertNotIn("places.json", self.public.files)

    def test_wikidata_main_fetches_then_publishes_and_curated_main_uses_same_helper(self):
        def local_publish(apply):
            return publish_import(apply, self.public, self.private)
        with patch.object(import_curated, "publish_import", side_effect=local_publish), patch.object(import_curated, "fetch_public", side_effect=lambda: json.loads(self.public.files["places.json"])):
            import_curated.main()
        row = {"item": {"value": "http://www.wikidata.org/entity/Q123"},
               "itemLabel": {"value": "Test mosque"}, "coord": {"value": "Point(45 43)"},
               "precision": {"value": "0.0001"}}
        with patch.object(import_wikidata, "fetch", return_value=[row]), patch.object(import_wikidata, "publish_import", side_effect=local_publish):
            import_wikidata.main()
        self.assertEqual(self.state()["places"]["wdQ123"]["status"], "pending")
        self.assertEqual(self.state()["outbox"], self.outbox)
        self.assertEqual(len(json.loads(self.public.files["places.json"])["places"]), 4)

    def test_public_verification_requires_exact_coordinates_and_source(self):
        self.publish()
        expected = import_curated.verification_targets(self.state(), self.items)
        public = json.loads(self.public.files["places.json"])
        self.assertEqual(import_curated.verify_public(public, expected), 4)
        missing = copy.deepcopy(public)
        missing["places"].pop()
        with self.assertRaises(RuntimeError):
            import_curated.verify_public(missing, expected)
        for key, value in (("lat", 1), ("lon", 2), ("source_details", "wrong source")):
            altered = copy.deepcopy(public)
            altered["places"][0][key] = value
            with self.assertRaises(RuntimeError):
                import_curated.verify_public(altered, expected)

    def test_public_verification_uses_approved_owner_edits_and_skips_tombstones_hidden(self):
        self.publish()
        state = self.state()
        first, second, third = self.items[:3]
        state["places"][first["id"]].update(lat=43.3, source_details="Owner correction", edited=True)
        state["places"][second["id"]]["status"] = "hidden"
        remember_removed(state, state["places"][third["id"]])
        del state["places"][third["id"]]
        self.private.files[index.DB_KEY] = json.dumps(state).encode()
        self.publish()
        expected = import_curated.verification_targets(self.state(), self.items)
        self.assertEqual(len(expected), 2)
        self.assertEqual(expected[0]["lat"], 43.3)
        self.assertEqual(expected[0]["source_details"], "Owner correction")
        self.assertEqual(import_curated.verify_public(json.loads(self.public.files["places.json"]), expected), 2)

    def test_public_read_is_unauthenticated_and_handles_gzip(self):
        from io import BytesIO
        self.publish()
        raw = self.public.files["places.json"]
        for body in (raw, gzip.compress(raw)):
            with patch("urllib.request.urlopen", return_value=BytesIO(body)) as get:
                self.assertEqual(len(import_curated.fetch_public()["places"]), 4)
            request = get.call_args.args[0]
            self.assertTrue(request.full_url.startswith(index.PUBLIC_BASE + "places.json?verify="))
            self.assertNotIn("Authorization", request.headers)


if __name__ == "__main__":
    unittest.main()
