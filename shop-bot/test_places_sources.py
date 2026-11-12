import copy
import os
import unittest

os.environ.setdefault("BOT_TOKEN", "123:TEST")
import import_osm
import import_wikidata
from places_sources import publish_source_fields, remember_removed


def osm(tags=None, **changes):
    return {"id": 123, "type": "way", "center": {"lat": 43.1, "lon": 45.2},
            "tags": {"amenity": "place_of_worship", "religion": "muslim", **(tags or {})}, **changes}


def wd(qid="Q123", name="Test mosque", point="Point(45.5 43.1)", precision="0.0001"):
    return {"item": {"value": "http://www.wikidata.org/entity/" + qid},
            "itemLabel": {"value": name}, "coord": {"value": point},
            "precision": {"value": precision}}


class PlaceSourcesTests(unittest.TestCase):
    def test_overpass_requires_complete_count_and_no_timeout_remark(self):
        record = osm()
        count = {"type": "count", "tags": {"total": "1"}}
        self.assertEqual(import_osm.complete_records({"elements": [record, count]}), [record])
        for payload in ({"elements": [record]}, {"elements": [count]},
                        {"elements": [record, count], "remark": "runtime error: timed out"}):
            with self.assertRaises(ValueError):
                import_osm.complete_records(payload)

    def test_chechen_name_and_generic_building_are_not_mislabelled_rooms(self):
        place = import_osm.convert(osm({"name:ce": "Керла-Юкъера маьждиг", "building": "yes"}))
        self.assertEqual(place["kind"], "mosque")
        self.assertEqual(place["name"], "Керла-Юкъера маьждиг")
        self.assertEqual(import_osm.convert(osm({"building": "yes"}))["kind"], "other")
        self.assertEqual(import_osm.convert(osm({"name": "Намаз в мечети"}))["kind"], "mosque")
        self.assertEqual(import_osm.convert(osm({"indoor": "room"}))["kind"], "room")

    def test_extra_osm_tagging_requires_active_place_of_worship(self):
        active = {"id": 1, "type": "node", "lat": 0, "lon": 0,
                  "tags": {"building": "mosque", "amenity": "place_of_worship"}}
        self.assertEqual(import_osm.convert(active)["lat"], 0)
        self.assertIsNone(import_osm.convert({**active, "tags": {"building": "mosque"}}))
        self.assertIsNone(import_osm.convert(osm({"building": "mosque", "religion": "christian"})))
        self.assertIsNone(import_osm.convert(osm({"disused:amenity": "place_of_worship"})))
        self.assertIsNone(import_osm.convert(osm(center={"lat": float("nan"), "lon": 45})))

    def test_refresh_never_deletes_places_from_a_partial_provider_snapshot(self):
        first = import_osm.convert(osm())
        other = {**first, "id": "osmn456", "status": "hidden"}
        db = {"places": {first["id"]: first, other["id"]: other}}
        import_osm.merge(db, [])
        self.assertEqual(len(db["places"]), 2)
        self.assertEqual(db["places"]["osmn456"]["status"], "hidden")

    def test_owner_fields_and_unknown_booleans_survive_import(self):
        place = import_osm.convert(osm())
        place.update(status="hidden", women=None, wudu=False, confirmed_at="2026-11-01", entrance="East")
        db = {"places": {place["id"]: place}}
        import_osm.merge(db, [{**place, "name": "New name", "wudu": True, "women": True, "status": "approved"}])
        updated = db["places"][place["id"]]
        self.assertFalse(updated["wudu"])
        self.assertIsNone(updated["women"])
        self.assertEqual(updated["status"], "hidden")
        self.assertEqual(updated["name"], "New name")
        self.assertEqual(updated["entrance"], "East")

    def test_removed_and_edited_places_remain_removed_or_edited(self):
        place = import_osm.convert(osm({"wikidata": "Q123"}))
        db = {"places": {place["id"]: {**place, "edited": True, "name": "Owner name"}}}
        import_osm.merge(db, [place])
        self.assertEqual(db["places"][place["id"]]["name"], "Owner name")
        remember_removed(db, place)
        remember_removed(db, place)
        db["places"].clear()
        import_osm.merge(db, [place])
        import_wikidata.merge(db, [wd()])
        self.assertEqual(db["places"], {})
        self.assertEqual(db["osm_removed"], [place["id"]])
        self.assertEqual(set(db["import_removed"]), {place["id"], "wdQ123"})

    def test_new_wikidata_places_require_moderation_and_have_no_guessed_access(self):
        db = {"places": {}}
        self.assertEqual(import_wikidata.merge(db, [wd(), wd()]), (1, 0, 0))
        p = db["places"]["wdQ123"]
        self.assertEqual(p["status"], "pending")
        self.assertEqual(p["hours"], "")
        self.assertIsNone(p["wudu"])
        self.assertNotIn("confirmed_at", p)

    def test_same_wikidata_identity_does_not_create_a_second_card(self):
        place = import_osm.convert(osm({"wikidata": "Q123"}))
        db = {"places": {place["id"]: place}}
        self.assertEqual(import_wikidata.merge(db, [wd()]), (0, 0, 0))
        public = publish_source_fields(place)
        self.assertEqual(public["src"], "osm")
        self.assertEqual(public["wikidata_url"], "https://www.wikidata.org/wiki/Q123")

    def test_conflicting_coordinates_cannot_silently_choose_a_location(self):
        db = {"places": {}}
        import_wikidata.merge(db, [wd(), wd(point="Point(46 44)")])
        self.assertEqual(db["places"], {})

    def test_coarse_or_invalid_wikidata_coordinates_are_rejected(self):
        for bad in (wd(precision="0.1"), wd(precision="0"), wd(point="Point(45 91)"),
                    wd(point="Point(NaN 42)"), wd(qid="../x"), wd(name="Q123"), {}):
            self.assertIsNone(import_wikidata.convert(bad))

    def test_close_coordinates_only_raise_private_review_hint(self):
        place = import_osm.convert(osm(center={"lat": 43.1, "lon": 45.5}))
        db = {"places": {place["id"]: place}}
        before = copy.deepcopy(place)
        import_wikidata.merge(db, [wd()])
        p = db["places"]["wdQ123"]
        self.assertEqual(p["possible_duplicate"], place["id"])
        self.assertEqual(db["places"][place["id"]], before)
        self.assertNotIn("possible_duplicate", publish_source_fields(p))

    def test_public_provenance_never_exposes_private_fields_or_remote_links(self):
        p = {"id": "wdQ123", "source": "wikidata", "wikidata": "javascript:alert(1)",
             "source_url": "https://evil.example/x", "source_updated": "2026-02-30",
             "author": 555, "possible_duplicate": "private", "verification": "admin_secret"}
        self.assertEqual(publish_source_fields(p), {"src": "wikidata", "source_url": "https://www.wikidata.org/wiki/Q123"})


if __name__ == "__main__":
    unittest.main()
