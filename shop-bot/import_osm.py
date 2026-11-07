"""
Импорт мечетей и молельных комнат России из OpenStreetMap (открытые данные, лицензия ODbL).

Источник: Overpass API — все объекты amenity=place_of_worship + religion=muslim в границах России.
Места из OSM попадают на карту сразу (status=approved, source=osm). Если владелец скрыл или удалил место,
повторный импорт его не вернёт. Места, которые предложили люди через бота, не трогаем.

Запуск (GitHub Actions, раз в месяц или вручную): python3 shop-bot/import_osm.py
Нужны S3_KEY_ID, S3_SECRET, BUCKET (и BOT_TOKEN — для имени бота в places.json).
"""
import json
import datetime
import os
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import index  # noqa: E402
from places_sources import merge_import, publish_import, valid_coordinates, wikidata_id

OVERPASS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
QUERY = """
[out:json][timeout:300];
area["ISO3166-1"="RU"][admin_level=2]->.ru;
(
  nwr["amenity"="place_of_worship"]["religion"="muslim"](area.ru);
  nwr["amenity"="place_of_worship"]["building"="mosque"][!religion](area.ru);
  nwr["amenity"="mosque"](area.ru);
);
out center tags;
out count;
"""


def complete_records(payload):
    elements = payload.get("elements", [])
    counts = [el for el in elements if el.get("type") == "count"]
    records = [el for el in elements if el.get("type") != "count"]
    if payload.get("remark") or len(counts) != 1 or int(counts[0]["tags"]["total"]) != len(records):
        raise ValueError("Overpass returned a partial result")
    return records


def fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    last = None
    for url in OVERPASS:
        try:
            req = urllib.request.Request(url, data=body, headers={"User-Agent": "ZikrTime/1.0 (prayer places map)"})
            with urllib.request.urlopen(req, timeout=360) as r:
                payload = json.loads(r.read())
            return complete_records(payload)
        except Exception as e:  # пробуем следующий сервер
            last = e
            print("overpass fail", url, repr(e)[:200])
    raise SystemExit(f"Overpass недоступен: {last!r}")


def kind_of(tags):
    name = " ".join(tags.get(k, "") for k in ("name", "name:ru", "name:ce", "name:en", "description")).lower()
    if any(w in name for w in ("молельн", "намазхан", "musalla", "мусалл", "prayer room")):
        return "room"
    if tags.get("building") == "mosque" or tags.get("amenity") == "mosque" or any(w in name for w in ("мечет", "mosque", "джами", "масджид", "мәчет", "мечить", "маьждиг", "маждиг", "cami", "mescid")):
        return "mosque"
    if tags.get("indoor") in ("yes", "room") or tags.get("room") == "prayer":
        return "room"
    # A generic building=yes does not tell us whether it is a mosque or a room.
    return "other"


def usable(tags):
    if tags.get("religion") not in (None, "", "muslim"):
        return False
    if any(tags.get(key) in ("yes", "place_of_worship", "mosque") for key in (
        "disused", "abandoned", "demolished", "ruins", "construction",
        "disused:amenity", "abandoned:amenity", "demolished:amenity", "construction:amenity",
    )):
        return False
    return tags.get("amenity") == "mosque" or (
        tags.get("amenity") == "place_of_worship" and
        (tags.get("religion") == "muslim" or tags.get("building") == "mosque")
    )


def address_of(tags):
    parts = []
    city = tags.get("addr:city") or tags.get("addr:place") or tags.get("addr:village") or tags.get("addr:town")
    if city:
        parts.append(city)
    street = tags.get("addr:street")
    if street:
        parts.append(street + (", " + tags["addr:housenumber"] if tags.get("addr:housenumber") else ""))
    elif tags.get("addr:housenumber"):
        parts.append(tags["addr:housenumber"])
    if tags.get("addr:full"):
        return tags["addr:full"][:150]
    return ", ".join(parts)[:150]


def convert(el):
    tags = el.get("tags") or {}
    lat = el.get("lat", (el.get("center") or {}).get("lat"))
    lon = el.get("lon", (el.get("center") or {}).get("lon"))
    if not usable(tags) or not valid_coordinates(lat, lon):
        return None
    if el.get("type") not in ("node", "way", "relation") or not isinstance(el.get("id"), int) or el["id"] <= 0:
        return None
    kind = kind_of(tags)
    name = tags.get("name:ru") or tags.get("name") or tags.get("name:ce") or tags.get("name:tt") or tags.get("name:en") or ""
    if not name:
        name = {"mosque": "Мечеть", "room": "Молельная комната", "other": "Место для намаза"}[kind]
    who = ""
    if tags.get("female") == "yes" and tags.get("male") == "no":
        who = "women"
    elif tags.get("male") == "yes" and tags.get("female") == "no":
        who = "men"
    notes = []
    if tags.get("denomination"):
        pass  # мазхабы/течения не показываем — приложение для всех
    if tags.get("phone") or tags.get("contact:phone"):
        notes.append("Тел.: " + (tags.get("phone") or tags.get("contact:phone")))
    if tags.get("website") or tags.get("contact:website"):
        notes.append(tags.get("website") or tags.get("contact:website"))
    return {
        "id": f"osm{el['type'][0]}{el['id']}",
        "kind": kind,
        "name": name[:80],
        "lat": round(float(lat), 6),
        "lon": round(float(lon), 6),
        "address": address_of(tags),
        "hours": (tags.get("opening_hours") or "")[:60],
        "who": who,
        "wudu": None,
        "photo": "",
        "note": " · ".join(notes)[:300],
        "status": "approved",
        "source": "osm",
        "wikidata": wikidata_id(tags.get("wikidata")),
        "source_updated": datetime.date.today().isoformat(),
        "verification": "source_only",
        "author": None,
        "created": time.time(),
    }


def merge(db, items):
    return merge_import(db, items, "osm")


def main():
    elements = fetch()
    items = [x for x in (convert(e) for e in elements) if x]
    print(f"OSM: {len(elements)} объектов, с координатами {len(items)}")
    if len(items) < 100:
        raise SystemExit("Подозрительно мало мест — импорт отменён, чтобы не стереть данные")
    # Finish network reads before loading the private database and its write ETag.
    import import_wikidata
    wikidata_rows = None
    try:
        wikidata_rows = import_wikidata.fetch()
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print("Wikidata unavailable; previous records retained:", type(exc).__name__)
    def apply(db):
        osm_result = merge(db, items)
        wd_result = import_wikidata.merge(db, wikidata_rows) if wikidata_rows is not None else (0, 0, 0)
        return osm_result, wd_result
    (added, updated, removed), (wd_added, wd_updated, _) = publish_import(apply)
    if wikidata_rows is not None:
        print(f"Wikidata: на проверку {wd_added}, обновлено {wd_updated}")
    kinds = {}
    for it in items:
        kinds[it["kind"]] = kinds.get(it["kind"], 0) + 1
    print(f"Добавлено {added}, обновлено {updated}, удалено {removed}; по типам: {kinds}")


if __name__ == "__main__":
    main()
