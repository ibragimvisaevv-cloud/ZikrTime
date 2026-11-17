"""Публикует хешированные ресурсы кабинета, затем обновляет miniapp/index.html.
Нужны S3_KEY_ID, S3_SECRET, BUCKET и API_URL (адрес облачной функции API)."""
import hashlib
import html
import json
import re
import os
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import index  # noqa: E402

TYPES = {"html": "text/html; charset=utf-8", "js": "application/javascript; charset=utf-8", "css": "text/css; charset=utf-8"}
TELEGRAM_SDK = "https://telegram.org/js/telegram-web-app.js"


def versioned_bundle(files):
    """Build references before uploading anything; old pages keep their own assets."""
    document = files["index.html"].decode("utf-8")
    assets = {}
    for name, data in files.items():
        if name == "index.html":
            continue
        stem, extension = os.path.splitext(name)
        versioned = f"{stem}.{hashlib.sha256(data).hexdigest()}{extension}"
        original = TELEGRAM_SDK if name == "telegram-web-app.js" else name
        pattern = r"((?:src|href)\s*=\s*)([\"'])" + re.escape(original) + r"\2"
        document, count = re.subn(pattern, lambda match: f"{match[1]}{match[2]}{versioned}{match[2]}", document)
        if count == 0:
            raise ValueError(f"Asset reference missing from miniapp/index.html: {name}")
        assets[versioned] = data
    return document.encode("utf-8"), assets


def publish_miniapp_only():
    """Roll out the local UI against exactly the config and Telegram SDK already in production."""
    token = os.environ["IAM_TOKEN"]
    base = index.MINIAPP_URL.rsplit("/", 1)[0] + "/"
    entry = index.MINIAPP_URL + "?release=" + str(time.time_ns())
    with urllib.request.urlopen(entry, timeout=30) as previous:
        live = previous.read().decode("utf-8")
        etag = previous.headers["ETag"]
    if not etag:
        raise RuntimeError("Cannot identify the current Mini App entry point")
    folder = Path(__file__).parent / "miniapp"
    files = {name: (folder / name).read_bytes() for name in ("index.html", "app.js", "style.css")}
    kept = set()
    for name in ("config", "telegram-web-app"):
        matches = re.findall(r"""\bsrc=["'](""" + name + r"""\.([0-9a-f]{64})\.js)["']""", live)
        if name == "telegram-web-app" and not matches and TELEGRAM_SDK in live:
            continue
        if len(matches) != 1:
            raise RuntimeError("Cannot identify the live " + name + " asset")
        path, digest = matches[0]
        with urllib.request.urlopen(base + path, timeout=30) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise RuntimeError("Unexpected content for " + path)
        files[name + ".js"] = data
        kept.add(path)
    document, assets = versioned_bundle(files)

    def publish(path, content, cache, if_match=None):
        headers = {"Authorization": "Bearer " + token, "Content-Type": TYPES[path.rsplit(".", 1)[1]],
                   "Cache-Control": cache}
        if if_match:
            headers["If-Match"] = if_match
        request = urllib.request.Request(base + path, data=content, headers=headers, method="PUT")
        with urllib.request.urlopen(request, timeout=30):
            pass

    for name, data in assets.items():
        if name not in kept:
            publish(name, data, "public, max-age=31536000, immutable")
            with urllib.request.urlopen(base + name, timeout=30) as response:
                if response.read() != data:
                    raise RuntimeError("Published asset did not match this release: " + name)
    publish("index.html", document, "no-cache", if_match=etag)
    with urllib.request.urlopen(index.MINIAPP_URL + "?release=" + str(time.time_ns()), timeout=30) as response:
        if response.read() != document:
            raise RuntimeError("Published Mini App entry point did not match this release")
    print("Verified published Mini App entry point and assets: " + ", ".join(sorted(assets)))


def main():
    url = os.environ["API_URL"]
    if not re.fullmatch(r"https://functions\.yandexcloud\.net/[a-z0-9]+", url):
        raise ValueError("Неожиданный адрес функции кабинета")
    st = index.make_storage()
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "miniapp")
    files = {name: Path(base, name).read_bytes() for name in ("index.html", "app.js", "style.css")}
    files["config.js"] = f"window.ZT_API = {json.dumps(url)};\n".encode()
    # Скрипт Telegram кладём рядом: если telegram.org у человека открывается медленно, кабинет всё равно загрузится
    try:
        with urllib.request.urlopen(TELEGRAM_SDK, timeout=20) as r:
            files["telegram-web-app.js"] = r.read()
    except Exception as e:
        print("telegram-web-app.js: оставляем ссылку на telegram.org", repr(e))
    document, assets = versioned_bundle(files)
    for name, data in assets.items():
        st.put("miniapp/" + name, data, TYPES[name.rsplit(".", 1)[1]], cache="public, max-age=31536000, immutable")
        print("miniapp/" + name, len(data))
    upload_site(st)
    target = html.escape(url, quote=True)
    landing = (f'<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="referrer" content="no-referrer">'
               f'<meta http-equiv="refresh" content="0;url={target}"><title>Кабинет продавца</title>'
               f'<a href="{target}">Открыть кабинет продавца Зикр-тайм</a></html>')
    st.put("seller.html", landing.encode(), TYPES["html"], cache="no-cache")
    # This is the only mutable entry point to the new bundle. If any earlier
    # upload fails, the previously published page and its resources still work.
    st.put("miniapp/index.html", document, TYPES["html"], cache="no-cache")
    print(index.MINIAPP_URL)


def bot_contact():
    """Ссылка на бота для страницы политики: имя бота берём у Telegram по токену."""
    token = os.environ.get("BOT_TOKEN", "")
    if token:
        try:
            with urllib.request.urlopen(f"https://api.telegram.org/bot{token}/getMe", timeout=20) as r:
                user = json.load(r)["result"]["username"]
            return f'Telegram-бот <a href="https://t.me/{user}">@{user}</a>'
        except Exception as e:
            print("getMe:", type(e).__name__)
    return "Telegram-бот, ссылка на который есть в приложении (раздел «Магазин»)"


def upload_site(st):
    """Публичные страницы (политика конфиденциальности) — в корень бакета."""
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "site")
    contact = bot_contact().encode()
    for name in sorted(os.listdir(base)):
        data = Path(base, name).read_bytes().replace(b"{{CONTACT}}", contact)
        st.put(name, data, TYPES[name.rsplit(".", 1)[1]], cache="no-cache")
        print(f"https://storage.yandexcloud.net/{os.environ.get('BUCKET', 'zikrtime-shop')}/{name}", len(data))


if __name__ == "__main__":
    if sys.argv[1:] == ["--miniapp-only"]:
        publish_miniapp_only()
    elif not sys.argv[1:]:
        main()
    else:
        raise SystemExit("Usage: upload_miniapp.py [--miniapp-only]")
