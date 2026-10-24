"""
Импорт мечетей и молельных комнат России из OpenStreetMap (открытые данные, лицензия ODbL).

Источник: Overpass API — все объекты amenity=place_of_worship + religion=muslim в границах России.
Места из OSM попадают на карту сразу (status=approved, source=osm). Если владелец скрыл или удалил место,
повторный импорт его не вернёт. Места, которые предложили люди через бота, не трогаем.

Запуск (GitHub Actions, раз в месяц или вручную): python3 shop-bot/import_osm.py
Нужны S3_KEY_ID, S3_SECRET, BUCKET (и BOT_TOKEN — для имени бота в places.json).
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import index  # noqa: E402

OVERPASS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
QUERY = """
[out:json][timeout:300];
area["ISO3166-1"="RU"][admin_level=2]->.ru;
nwr["amenity"="place_of_worship"]["religion"="muslim"](area.ru);
out center tags;
"""


def fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    last = None
    for url in OVERPASS:
        try:
            req = urllib.request.Request(url, data=body, headers={"User-Agent": "ZikrTime/1.0 (prayer places map)"})
            with urllib.request.urlopen(req, timeout=360) as r:
                return json.loads(r.read())["elements"]
        except Exception as e:  # пробуем следующий сервер
            last = e
            print("overpass fail", url, repr(e)[:200])
    raise SystemExit(f"Overpass недоступен: {last!r}")


def kind_of(tags):
    name = " ".join(tags.get(k, "") for k in ("name", "name:ru", "name:en", "description")).lower()
    if any(w in name for w in ("молельн", "намазхан", "musalla", "мусалл", "prayer room", "намаз")):
        return "room"
    if tags.get("building") == "mosque" or any(w in name for w in ("мечет", "mosque", "джами", "масджид", "мәчет", "мечить")):
        return "mosque"
    # Без названия и без здания-мечети: чаще всего это молельный дом или комната
    if tags.get("building") in ("yes", "house", "retail", "commercial", "office") or tags.get("level") or tags.get("indoor"):
        return "room"
    return "mosque"


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
    lat = el.get("lat") or (el.get("center") or {}).get("lat")
    lon = el.get("lon") or (el.get("center") or {}).get("lon")
    if lat is None or lon is None:
        return None
    kind = kind_of(tags)
    name = tags.get("name:ru") or tags.get("name") or tags.get("name:tt") or tags.get("name:en") or ""
    if not name:
        name = "Мечеть" if kind == "mosque" else "Молельная комната"
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
        "author": None,
        "created": time.time(),
    }


def merge(db, items):
    places = db.setdefault("places", {})
    seen = set()
    added = updated = 0
    for it in items:
        seen.add(it["id"])
        old = places.get(it["id"])
        if old is None:
            if it["id"] in db.get("osm_removed", []):
                continue  # владелец удалил — не возвращаем
            places[it["id"]] = it
            added += 1
        elif old.get("source") == "osm" and not old.get("edited"):
            keep = {k: old[k] for k in ("status", "created", "photo", "wudu") if k in old and old[k] not in (None, "")}
            places[it["id"]] = {**it, **keep}
            updated += 1
    removed = 0
    for pid in [p for p, v in places.items() if v.get("source") == "osm" and p not in seen and not v.get("edited")]:
        del places[pid]
        removed += 1
    return added, updated, removed


def main():
    elements = fetch()
    items = [x for x in (convert(e) for e in elements) if x]
    print(f"OSM: {len(elements)} объектов, с координатами {len(items)}")
    if len(items) < 100:
        raise SystemExit("Подозрительно мало мест — импорт отменён, чтобы не стереть данные")
    storage = index.make_storage()
    bot = index.Bot(storage, index.Telegram(index.BOT_TOKEN))
    added, updated, removed = merge(bot.db, items)
    bot.dirty = bot.places_dirty = True
    bot.save()
    kinds = {}
    for it in items:
        kinds[it["kind"]] = kinds.get(it["kind"], 0) + 1
    print(f"Добавлено {added}, обновлено {updated}, удалено {removed}; по типам: {kinds}")


if __name__ == "__main__":
    main()
