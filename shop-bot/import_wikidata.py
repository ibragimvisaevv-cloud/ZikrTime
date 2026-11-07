"""Second, independently maintained mosque source. Structured Wikidata data is CC0.

Existing OSM matches are linked by their explicit Wikidata ID. New records go to
the owner's place moderation queue because a historic mosque may be closed today.
No photos are imported: Commons media carries its own licence.
"""
import datetime
import json
import re
import time
import urllib.parse
import urllib.request

from places_sources import distance_km, merge_import, valid_coordinates, wikidata_id

ENDPOINT = "https://query.wikidata.org/sparql"
QUERY = """
SELECT DISTINCT ?item ?itemLabel ?coord ?precision WHERE {
  ?item wdt:P31/wdt:P279* wd:Q32815;
        wdt:P17 wd:Q159;
        p:P625 ?statement.
  ?statement ps:P625 ?coord; psv:P625 ?value.
  ?value wikibase:geoPrecision ?precision; wikibase:geoGlobe wd:Q2.
  FILTER(?precision <= 0.001)
  FILTER NOT EXISTS { ?item wdt:P576 ?demolished. }
  FILTER NOT EXISTS { ?statement wikibase:rank wikibase:DeprecatedRank. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "ru,en". }
}
"""


def fetch():
    query = urllib.parse.urlencode({"query": QUERY, "format": "json"})
    req = urllib.request.Request(ENDPOINT + "?" + query, headers={
        "User-Agent": "ZikrTime/0.8.23 (https://github.com/ibragimvisaevv-cloud/ZikrTime)",
        "Accept": "application/sparql-results+json",
    })
    with urllib.request.urlopen(req, timeout=75) as response:
        payload = response.read(4_000_001)
    if len(payload) > 4_000_000:
        raise ValueError("Wikidata result exceeds size limit")
    return json.loads(payload)["results"]["bindings"]


def convert(row):
    try:
        qid = wikidata_id(row["item"]["value"].rsplit("/", 1)[-1])
        name = row["itemLabel"]["value"].strip()
        point = re.fullmatch(r"Point\(([-+\d.eE]+) ([-+\d.eE]+)\)", row["coord"]["value"])
        precision = float(row["precision"]["value"])
        if not qid or not name or name == qid or not point or not 0 < precision <= 0.001:
            return None
        lon, lat = map(float, point.groups())
        if not valid_coordinates(lat, lon):
            return None
    except (KeyError, TypeError, ValueError, AttributeError):
        return None
    return {
        "id": "wd" + qid, "wikidata": qid, "kind": "mosque", "name": name[:80],
        "lat": round(lat, 6), "lon": round(lon, 6), "address": "", "hours": "",
        "who": "", "wudu": None, "photo": "", "note": "", "status": "pending",
        "source": "wikidata", "source_updated": datetime.date.today().isoformat(),
        "verification": "source_only", "author": None, "created": time.time(),
    }


def merge(db, rows):
    places = db.setdefault("places", {})
    linked = {p.get("wikidata") for p in places.values() if p.get("source") != "wikidata"}
    groups = {}
    for row in rows:
        item = convert(row)
        if item is not None:
            groups.setdefault(item["id"], []).append(item)
    candidates = []
    for group in groups.values():
        if len({(p["lat"], p["lon"]) for p in group}) != 1:
            continue
        item = group[0]
        if item["wikidata"] in linked:
            continue
        # Coordinates alone do not establish identity. Nearby matches are left for
        # review, with an explicit hint, rather than overwriting another mosque.
        nearby = [p for p in places.values()
                  if p.get("source") != "wikidata" and valid_coordinates(p.get("lat"), p.get("lon"))
                  and distance_km(item, p) <= 0.15]
        if nearby:
            item["possible_duplicate"] = nearby[0]["id"]
        candidates.append(item)
    return merge_import(db, candidates, "wikidata")
