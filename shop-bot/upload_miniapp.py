"""Выкладывает мини-приложение (кабинет продавца) в публичный бакет: miniapp/index.html, app.js, style.css, config.js.
Нужны S3_KEY_ID, S3_SECRET, BUCKET и API_URL (адрес облачной функции API)."""
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import index  # noqa: E402

TYPES = {"html": "text/html; charset=utf-8", "js": "application/javascript; charset=utf-8", "css": "text/css; charset=utf-8"}


def main():
    st = index.make_storage()
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "miniapp")
    files = {name: open(os.path.join(base, name), "rb").read() for name in ("index.html", "app.js", "style.css")}
    files["config.js"] = f'window.ZT_API = "{os.environ["API_URL"]}";\n'.encode()
    # Скрипт Telegram кладём рядом: если telegram.org у человека открывается медленно, кабинет всё равно загрузится
    try:
        with urllib.request.urlopen("https://telegram.org/js/telegram-web-app.js", timeout=20) as r:
            files["telegram-web-app.js"] = r.read()
        files["index.html"] = files["index.html"].replace(b"https://telegram.org/js/telegram-web-app.js", b"telegram-web-app.js")
    except Exception as e:
        print("telegram-web-app.js: оставляем ссылку на telegram.org", repr(e))
    for name, data in files.items():
        st.put("miniapp/" + name, data, TYPES[name.rsplit(".", 1)[1]], cache="no-cache")
        print("miniapp/" + name, len(data))
    print(index.MINIAPP_URL)


if __name__ == "__main__":
    main()
