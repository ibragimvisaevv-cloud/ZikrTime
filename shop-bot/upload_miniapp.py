"""Публикует хешированные ресурсы кабинета, затем обновляет miniapp/index.html.
Нужны S3_KEY_ID, S3_SECRET, BUCKET и API_URL (адрес облачной функции API)."""
import hashlib
import html
import json
import re
import os
import sys
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
    main()
