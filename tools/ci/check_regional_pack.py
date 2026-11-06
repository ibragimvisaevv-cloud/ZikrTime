"""Check release time rows against dated examples and validate the packaged city keys."""
from collections import Counter
from datetime import date, time
from pathlib import Path
import json

root = Path("app/src/main/assets")
catalog = json.loads((root / "cities.json").read_text(encoding="utf-8"))
known = {entry["id"] for entry in catalog["cities"]}
for extra in root.rglob("*.json"):
    if extra.name == "cities.json" or "cit" not in extra.name:
        continue
    obj = json.loads(extra.read_text(encoding="utf-8"))
    if isinstance(obj, dict):
        for entry in obj.get("cities", []):
            if isinstance(entry, dict) and "id" in entry:
                known.add(entry["id"])

region_catalog = root / "cities_ru_regions.tsv"
if region_catalog.exists():
    known.update(row.split("\t")[0] for row in region_catalog.read_text(encoding="utf-8").splitlines()
                 if row and not row.startswith("#"))
tables = {}
published = root / "prayer/published-2026.tsv"
files = [root / "prayer/regional.tsv"] + ([published] if published.exists() else [])
for line in (line for file in files for line in file.read_text(encoding="utf-8").splitlines()):
    if not line or line.startswith("#"):
        continue
    city, day, *values = line.split("\t")
    if city == "city_id" or day == "date":
        continue
    assert city in known, city
    local_day = date.fromisoformat(day)
    clocks = list(map(time.fromisoformat, values))
    assert len(clocks) == 6 and all(a < b for a, b in zip(clocks[1:4], clocks[2:5])), (city, day)
    previous_evening = clocks[0] >= time(20) and clocks[1] < time(12)
    assert clocks[0] < clocks[1] or previous_evening, (city, day)
    next_morning = clocks[5] < time(6) and clocks[4] > time(12)
    assert clocks[5] > clocks[4] or next_morning, (city, day)
    assert (city, local_day) not in tables, (city, day)
    tables[city, local_day] = values
fixtures = {
    "grozny": "05:23 06:35 12:30 14:43 16:46 18:15",
    "kazan": "04:52 07:00 12:00 14:02 15:54 17:40",
    "ufa": "06:38 08:10 13:30 15:39 17:31 19:14",
    "makhachkala": "04:57 06:28 11:38 14:15 16:41 17:57",
}
for city, readings in fixtures.items():
    assert tables[city, date(2026, 11, 6)] == readings.split(), city
counts = Counter(city for city, day in tables)
assert len(counts) >= 36
print(f"{len(tables)} dates, {len(counts)} calendar localities; four independently checked reference dates")
