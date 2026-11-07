"""Publish owner-authorized, individually sourced mosque additions, without messages."""
import copy
import gzip
import json
import pathlib
import re
import time
import urllib.request

from places_sources import publish_import, valid_coordinates

SOURCE_FILE = pathlib.Path(__file__).resolve().parent / "places" / "berkat_yurt_verified_2026-11-07.json"


def load(path=SOURCE_FILE):
    document = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    places = document.get("places")
    if document.get("version") != 1 or not isinstance(places, list):
        raise ValueError("Unsupported curated place file")
    seen = set()
    for place in places:
        pid = place.get("id", "")
        if not isinstance(pid, str) or not re.fullmatch(r"cur_[A-Za-z0-9_]{1,44}", pid) or pid in seen:
            raise ValueError("Invalid or duplicate curated place ID")
        if not valid_coordinates(place.get("lat"), place.get("lon")):
            raise ValueError("Invalid curated place coordinates")
        if place.get("source") != "curated" or place.get("status") != "approved" or not place.get("name") or not place.get("evidence"):
            raise ValueError("Curated place must have a reviewed source and name")
        seen.add(pid)
    return places


def merge(db, items):
    places = db.setdefault("places", {})
    removed = set(db.get("import_removed", [])) | set(db.get("osm_removed", []))
    added = 0
    for place in items:
        # Additions are one-way. An existing ID retains every owner edit/status.
        if place["id"] in places or place["id"] in removed:
            continue
        places[place["id"]] = copy.deepcopy(place)
        added += 1
    return added, 0, 0


def verification_targets(db, items):
    removed = set(db.get("import_removed", [])) | set(db.get("osm_removed", []))
    expected = []
    for item in items:
        place = db.get("places", {}).get(item["id"])
        if item["id"] in removed or not place or place.get("status") != "approved":
            continue
        # Verify the saved owner-approved state, including any subsequent edits.
        expected.append({key: place.get(key, "") for key in ("id", "lat", "lon", "source_details")})
    return expected


def fetch_public():
    import index
    request = urllib.request.Request(
        index.PUBLIC_BASE + "places.json?verify=" + str(time.time_ns()),
        headers={"User-Agent": "ZikrTime-curated-verification/1", "Cache-Control": "no-cache"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read(32 * 1024 * 1024 + 1)
    if len(raw) > 32 * 1024 * 1024:
        raise RuntimeError("Public places response is unexpectedly large")
    if raw.startswith(b"\x1f\x8b"):
        raw = gzip.decompress(raw)
    return json.loads(raw)


def verify_public(document, expected):
    places = document.get("places")
    if not isinstance(places, list):
        raise RuntimeError("Public places response is invalid")
    by_id = {place["id"]: place for place in places if isinstance(place, dict) and "id" in place}
    if len(by_id) != len(places):
        raise RuntimeError("Public places response contains duplicate or invalid IDs")
    for target in expected:
        actual = by_id.get(target["id"])
        if not actual or any(actual.get(key, "") != target[key] for key in ("lat", "lon", "source_details")):
            raise RuntimeError("Public place verification failed: " + target["id"])
    return len(places)


def main():
    items = load()
    def apply(db):
        counts = merge(db, items)
        return counts, verification_targets(db, items)
    (added, _, _), expected = publish_import(apply)
    public_count = verify_public(fetch_public(), expected)
    print(f"Curated prayer places: {added} added, {len(items) - added} already present or removed")
    print(f"Public verification: {len(expected)} verified, {len(items) - len(expected)} intentionally skipped, {public_count} total public places")
    for place in expected:
        print(f"{place['id']}: {place['lat']}, {place['lon']}")


if __name__ == "__main__":
    main()
