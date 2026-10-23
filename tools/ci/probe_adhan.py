"""Ищет записи азана на Wikimedia Commons и сохраняет их лицензии (запускается в CI, где есть интернет)."""
import json, sys, urllib.parse, urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "ZikrTimeCI/1.0 (https://github.com/ibragimvisaevv-cloud/ZikrTime)"}

def call(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)

titles = set()
for q in ["adhan", "azan", "athan", "adhan call to prayer", "azan mosque"]:
    res = call({"action": "query", "list": "search", "srsearch": q + " filetype:audio", "srnamespace": 6, "srlimit": 50})
    for item in res.get("query", {}).get("search", []):
        titles.add(item["title"])

out = []
titles = sorted(titles)
for i in range(0, len(titles), 40):
    chunk = titles[i:i + 40]
    res = call({"action": "query", "titles": "|".join(chunk), "prop": "imageinfo",
                "iiprop": "url|size|mime|sha1|extmetadata"})
    for page in res.get("query", {}).get("pages", {}).values():
        info = (page.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata", {})
        get = lambda k: (meta.get(k) or {}).get("value", "")
        out.append({
            "title": page.get("title"),
            "url": info.get("url"),
            "mime": info.get("mime"),
            "size": info.get("size"),
            "duration": info.get("duration"),
            "sha1": info.get("sha1"),
            "license": get("LicenseShortName"),
            "license_url": get("LicenseUrl"),
            "artist": get("Artist"),
            "credit": get("Credit"),
            "description": get("ImageDescription")[:400],
        })
json.dump(out, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(out), "candidates")
