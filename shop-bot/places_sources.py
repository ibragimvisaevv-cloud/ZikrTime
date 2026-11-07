"""Public provenance and moderation-safe merging for imported prayer places."""
import datetime
import math
import re


def wikidata_id(value):
    return value if isinstance(value, str) and re.fullmatch(r"Q[1-9][0-9]*", value) else ""


def source_url(place):
    pid = str(place.get("id", ""))
    osm = re.fullmatch(r"osm([nwr])([1-9][0-9]*)", pid)
    if place.get("source") == "osm" and osm:
        kind = {"n": "node", "w": "way", "r": "relation"}[osm[1]]
        return f"https://www.openstreetmap.org/{kind}/{osm[2]}"
    qid = wikidata_id(pid.removeprefix("wd"))
    if place.get("source") == "wikidata" and qid:
        return f"https://www.wikidata.org/wiki/{qid}"
    return ""


def publish_source_fields(place):
    """Only these public source fields may leave the private moderation database."""
    public = {}
    if place.get("source") in ("osm", "wikidata"):
        public["src"] = place["source"]
    url = source_url(place)
    if url:
        public["source_url"] = url
    qid = wikidata_id(place.get("wikidata"))
    if qid:
        public["wikidata_url"] = f"https://www.wikidata.org/wiki/{qid}"
    try:
        date = datetime.date.fromisoformat(place.get("source_updated", ""))
        public["source_updated"] = date.isoformat()
    except (ValueError, TypeError):
        pass
    if place.get("verification") == "source_only":
        public["verification"] = "source_only"
    return public


def remember_removed(db, place):
    if place.get("source") not in ("osm", "wikidata", "curated"):
        return
    pid = place.get("id")
    if not isinstance(pid, str) or not pid:
        return
    keys = ["import_removed"]
    if place.get("source") == "osm":
        keys.append("osm_removed")
    for key in keys:
        removed = db.setdefault(key, [])
        if pid not in removed:
            removed.append(pid)
    qid = wikidata_id(place.get("wikidata"))
    if qid and "wd" + qid not in db["import_removed"]:
        db["import_removed"].append("wd" + qid)


def valid_coordinates(lat, lon):
    if isinstance(lat, bool) or isinstance(lon, bool):
        return False
    try:
        return math.isfinite(float(lat)) and math.isfinite(float(lon)) and -90 <= float(lat) <= 90 and -180 <= float(lon) <= 180
    except (ValueError, TypeError):
        return False


def distance_km(a, b):
    lat1, lat2 = math.radians(float(a["lat"])), math.radians(float(b["lat"]))
    dlat = lat2 - lat1
    dlon = math.radians(float(b["lon"]) - float(a["lon"]))
    chord = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6371 * 2 * math.asin(min(1, math.sqrt(chord)))


def merge_import(db, items, source):
    """Upsert one source. Missing records never cause destructive snapshot deletion."""
    places = db.setdefault("places", {})
    removed = set(db.get("osm_removed", [])) | set(db.get("import_removed", []))
    added = updated = 0
    for item in items:
        if item["id"] in removed:
            continue
        old = places.get(item["id"])
        if old is None:
            places[item["id"]] = item
            added += 1
            continue
        if old.get("source") != source or old.get("edited"):
            continue
        # Owner decisions and supplied access details survive provider refreshes.
        keep = {key: old[key] for key in (
            "status", "created", "photo", "wudu", "women", "who", "access_conditions",
            "entrance", "confirmed_at", "source_details", "author",
        ) if key in old}
        places[item["id"]] = {**item, **keep}
        updated += 1
    return added, updated, 0


class _NoTelegram:
    def call(self, method, **params):
        raise RuntimeError("Place imports do not make Telegram requests")


class _ImportSnapshot:
    """Give Bot one existing tagged snapshot, without its legacy migration path."""
    def __init__(self, storage, key):
        self.storage = storage
        self.key = key
        self.raw, self.etag = storage.get_tagged(key)
        if self.raw is None or not self.etag:
            raise RuntimeError("Place import requires an existing private database with ETag")

    def get_tagged(self, key):
        if key != self.key:
            raise RuntimeError("Unexpected import database key")
        return self.raw, self.etag

    def put(self, key, *args, **kwargs):
        if key != self.key or kwargs.get("if_match") != self.etag:
            raise RuntimeError("Place import requires a conditional database write")
        return self.storage.put(key, *args, **kwargs)


def publish_import(apply, storage=None, data=None):
    """Save an additive import with CAS and preserve deferred Telegram messages.

    Provider reads must finish before calling this function. A CAS conflict is
    propagated, so the caller can retry against a fresh database. If publication
    fails after the private save, retrying the idempotent import republishes it.
    """
    import index
    storage = storage if storage is not None else index.make_storage()
    data = data if data is not None else index.make_storage(bucket=index.DATA_BUCKET)
    snapshot = _ImportSnapshot(data, index.DB_KEY)
    bot = index.Bot(storage, _NoTelegram(), data=snapshot)
    if not re.fullmatch(r"[A-Za-z0-9_]{5,32}", str(bot.db.get("bot_username", ""))):
        raise RuntimeError("Stored bot_username is required for offline place publication")
    # Compatible with older Bot initializers which removed the persisted queue
    # even for an unbuffered Telegram client. Importers must never consume it.
    if bot.outbox_out:
        bot.db["outbox"] = bot.outbox_out + bot.db.get("outbox", [])
        bot.outbox_out = []
    result = apply(bot.db)
    bot.dirty = bot.places_dirty = True
    bot.save()
    return result
