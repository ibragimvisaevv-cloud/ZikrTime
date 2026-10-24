"""
Бот-кабинет продавцов магазина ZikrTime (Telegram) — облачная функция Yandex Cloud.

Продавец в чате с ботом добавляет компанию, фото, товары и цены. Новая компания видна в приложении
только после одобрения владельцем; дальше правки продавца публикуются сразу (владелец получает
уведомление и может скрыть компанию одной кнопкой).

Данные: Yandex Object Storage (бакет BUCKET):
  data/db.json   — вся база (продавцы, товары, состояния диалогов, приглашения);
  img/...        — фотографии (публичные);
  catalog.json   — каталог для приложения (публичный), пересобирается после каждой правки.

Переменные окружения: BOT_TOKEN, BUCKET. Функции нужен сервисный аккаунт с ролью storage.editor.
Владелец назначается командой /owner <код>; код печатается при развёртывании (GitHub Actions).
Обновления от Telegram идут по одному (setWebhook max_connections=1), поэтому база — один файл.
"""
import base64
import hashlib
import html
import json
import os
import re
import secrets
import time
import urllib.error
import urllib.request

BOT_TOKEN = os.environ.get("BOT_TOKEN", "")
BUCKET = os.environ.get("BUCKET", "zikrtime-shop")
# Telegram-имя владельца: он становится владельцем при первом сообщении боту (если владельца ещё нет).
OWNER_USERNAME = os.environ.get("OWNER_USERNAME", "").lstrip("@").lower()
STORAGE_HOST = "https://storage.yandexcloud.net"
PUBLIC_BASE = f"{STORAGE_HOST}/{BUCKET}/"
DB_KEY = "data/db.json"

MAX_SELLERS_PER_USER = 3
MAX_PRODUCTS = 60
MAX_PHOTOS = 5

CATEGORIES = [
    {"id": "perfume", "icon": "perfume", "accent": "#8A5A2B", "names": {
        "ru": "Парфюм и благовония", "en": "Perfume & incense", "uz": "Atir va tutatqilar", "tg": "Атр ва бухур",
        "kk": "Иіссу және хош иістер", "ky": "Атыр жана жыпар", "az": "Ətir və buxur", "tr": "Parfüm ve buhur"}},
    {"id": "auto", "icon": "car", "accent": "#1F4E79", "names": {
        "ru": "Автопрокат", "en": "Car rental", "uz": "Avtoijara", "tg": "Иҷораи мошин",
        "kk": "Көлік жалға беру", "ky": "Унаа ижарасы", "az": "Avtomobil icarəsi", "tr": "Araç kiralama"}},
    {"id": "books", "icon": "book", "accent": "#0F5C4A", "names": {
        "ru": "Книги и Коран", "en": "Books & Quran", "uz": "Kitoblar va Qurʼon", "tg": "Китобҳо ва Қуръон",
        "kk": "Кітаптар және Құран", "ky": "Китептер жана Куран", "az": "Kitablar və Quran", "tr": "Kitaplar ve Kur'an"}},
    {"id": "clothes", "icon": "clothes", "accent": "#5B3A6E", "names": {
        "ru": "Одежда", "en": "Clothing", "uz": "Kiyim", "tg": "Либос",
        "kk": "Киім", "ky": "Кийим", "az": "Geyim", "tr": "Giyim"}},
    {"id": "food", "icon": "food", "accent": "#7A4B12", "names": {
        "ru": "Халяль-продукты", "en": "Halal food", "uz": "Halol mahsulotlar", "tg": "Маҳсулоти ҳалол",
        "kk": "Халал өнімдер", "ky": "Халал азыктар", "az": "Halal məhsullar", "tr": "Helal gıda"}},
    {"id": "gifts", "icon": "gift", "accent": "#8C2F39", "names": {
        "ru": "Подарки", "en": "Gifts", "uz": "Sovgʻalar", "tg": "Тӯҳфаҳо",
        "kk": "Сыйлықтар", "ky": "Белектер", "az": "Hədiyyələr", "tr": "Hediyeler"}},
    {"id": "other", "icon": "store", "accent": "#2E5E4E", "names": {
        "ru": "Другое", "en": "Other", "uz": "Boshqa", "tg": "Дигар",
        "kk": "Басқа", "ky": "Башка", "az": "Digər", "tr": "Diğer"}},
]
CAT_BY_ID = {c["id"]: c for c in CATEGORIES}

STATUS_TEXT = {
    "pending": "⏳ на проверке",
    "approved": "✅ опубликована",
    "rejected": "❌ не прошла проверку",
    "hidden": "🙈 скрыта",
}

# --------------------------------------------------------------------------------------------
# Хранилище и Telegram (в тестах подменяются)
# --------------------------------------------------------------------------------------------


class ObjectStorage:
    """Yandex Object Storage по HTTP с IAM-токеном сервисного аккаунта функции."""

    def __init__(self, token):
        self.token = token

    def _req(self, method, key, data=None, headers=None):
        req = urllib.request.Request(f"{STORAGE_HOST}/{BUCKET}/{key}", data=data, method=method)
        req.add_header("X-YaCloud-SubjectToken", self.token)
        for k, v in (headers or {}).items():
            req.add_header(k, v)
        return urllib.request.urlopen(req, timeout=20)

    def get(self, key):
        try:
            with self._req("GET", key) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            raise

    def put(self, key, data, content_type, cache="no-cache", encoding=None):
        headers = {"Content-Type": content_type, "Cache-Control": cache}
        if encoding:
            headers["Content-Encoding"] = encoding
        with self._req("PUT", key, data, headers):
            pass


def sigv4(method, host, path, headers, payload, key_id, secret, region, now):
    """Подпись запроса AWS Signature V4 (Yandex Object Storage совместим с S3). Возвращает заголовки."""
    import hmac
    amz_date, day = now.strftime("%Y%m%dT%H%M%SZ"), now.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(payload or b"").hexdigest()
    hdrs = {"host": host, "x-amz-content-sha256": payload_hash, "x-amz-date": amz_date}
    for k, v in (headers or {}).items():
        hdrs[k.lower()] = v
    signed = ";".join(sorted(hdrs))
    canonical = "\n".join([method, path, "", "".join(f"{k}:{hdrs[k].strip()}\n" for k in sorted(hdrs)), signed, payload_hash])
    scope = f"{day}/{region}/s3/aws4_request"
    to_sign = "\n".join(["AWS4-HMAC-SHA256", amz_date, scope, hashlib.sha256(canonical.encode()).hexdigest()])

    def h(k, m):
        return hmac.new(k, m.encode(), hashlib.sha256).digest()

    sig_key = h(h(h(h(("AWS4" + secret).encode(), day), region), "s3"), "aws4_request")
    signature = hmac.new(sig_key, to_sign.encode(), hashlib.sha256).hexdigest()
    hdrs["authorization"] = f"AWS4-HMAC-SHA256 Credential={key_id}/{scope}, SignedHeaders={signed}, Signature={signature}"
    return hdrs


class S3Storage(ObjectStorage):
    """Тот же Yandex Object Storage, но по статическому ключу (подпись SigV4) — для запуска бота вне Yandex Cloud."""

    def __init__(self, key_id, secret):
        self.key_id = key_id
        self.secret = secret

    def _req(self, method, key, data=None, headers=None):
        from datetime import datetime, timezone
        from urllib.parse import quote
        host = STORAGE_HOST.split("://")[1]
        path = "/" + quote(f"{BUCKET}/{key}", safe="/-_.~")
        hdrs = sigv4(method, host, path, headers, data, self.key_id, self.secret, "ru-central1", datetime.now(timezone.utc))
        req = urllib.request.Request(STORAGE_HOST + path, data=data, method=method)
        for k, v in hdrs.items():
            if k != "host":
                req.add_header(k, v)
        return urllib.request.urlopen(req, timeout=20)


def make_storage(iam_token=""):
    key_id, secret = os.environ.get("S3_KEY_ID"), os.environ.get("S3_SECRET")
    if key_id and secret:
        return S3Storage(key_id, secret)
    return ObjectStorage(iam_token)


class Telegram:
    def __init__(self, token):
        self.token = token

    def call(self, method, **params):
        body = json.dumps({k: v for k, v in params.items() if v is not None}).encode()
        req = urllib.request.Request(f"https://api.telegram.org/bot{self.token}/{method}", data=body,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.loads(r.read()).get("result")
        except urllib.error.HTTPError as e:
            print("telegram error", method, e.code, e.read()[:300])
            return None

    def download(self, file_path):
        with urllib.request.urlopen(f"https://api.telegram.org/file/bot{self.token}/{file_path}", timeout=30) as r:
            return r.read()


# --------------------------------------------------------------------------------------------
# Вспомогательное
# --------------------------------------------------------------------------------------------


def owner_code(token=None):
    return hashlib.sha256(((token or BOT_TOKEN) + ":owner").encode()).hexdigest()[:8]


def webhook_secret(token=None):
    return hashlib.sha256(((token or BOT_TOKEN) + ":webhook").encode()).hexdigest()[:32]


def esc(s):
    return html.escape(str(s or ""))


def btn(text, data):
    return {"text": text, "callback_data": data}


def kb(*rows):
    return {"inline_keyboard": [list(r) for r in rows if r]}


def new_id(prefix):
    return prefix + secrets.token_hex(4)


def parse_price(text):
    digits = re.sub(r"[^\d]", "", text or "")
    if not digits:
        return None
    value = int(digits)
    return value if 0 < value < 100_000_000 else None


def fmt_price(v):
    if v is None:
        return "по запросу"
    return f"{v:,}".replace(",", " ") + " ₽"


def clean_handle(text):
    t = (text or "").strip()
    t = re.sub(r"^(https?://)?(www\.)?(t\.me|telegram\.me|instagram\.com)/", "", t)
    t = t.lstrip("@").split("?")[0].strip("/")
    return t if re.fullmatch(r"[A-Za-z0-9_.]{3,40}", t) else None


def clean_phone(text):
    t = re.sub(r"[^\d+]", "", text or "")
    if t.startswith("8") and len(t) == 11:
        t = "+7" + t[1:]
    if t and not t.startswith("+"):
        t = "+" + t
    return t if 8 <= len(t) <= 16 else None


# --------------------------------------------------------------------------------------------
# Бот
# --------------------------------------------------------------------------------------------


PLACE_KINDS = {
    "mosque": "🕌 Мечеть",
    "room": "🧎 Молельная комната (мусалла)",
    "mall": "🛍 Намазхана в ТЦ / на работе",
    "other": "📍 Другое место для намаза",
}
WHO_TEXT = {"all": "для всех", "men": "только мужчины", "women": "только женщины", "both": "есть мужская и женская"}


class PlacesMixin:
    """Карта мест для намаза: люди предлагают места, владелец проверяет на месте и публикует."""

    PLACE_STEPS = ["kind", "name", "location", "address", "hours", "who", "wudu", "photo", "note"]

    def start_place(self, uid):
        self.user(uid)["state"] = {"flow": "place", "step": "kind", "data": {}}
        self.dirty = True
        rows = [[btn(v, f"pk:{k}")] for k, v in PLACE_KINDS.items()]
        rows.append([btn("Отмена", "menu")])
        self.send(uid, "Спасибо, что помогаете! 🤲\nШаг 1 из 9. Что это за место?", {"inline_keyboard": rows})

    def ask_place_step(self, uid, step):
        n = self.PLACE_STEPS.index(step) + 1
        p = f"Шаг {n} из 9. "
        skip = [btn("Пропустить →", "pskp")]
        if step == "name":
            return self.send(uid, p + "Как называется место? Например: «Молельная комната в ТЦ Авиапарк, 3 этаж».")
        if step == "location":
            return self.send(uid, p + "Отправьте геопозицию: 📎 → «Геопозиция» → передвиньте метку точно на вход и нажмите «Отправить».")
        if step == "address":
            return self.send(uid, p + "Адрес и как найти (этаж, вход, ориентир):", kb(skip))
        if step == "hours":
            return self.send(uid, p + "Когда открыто? Например: «круглосуточно» или «10:00–22:00».", kb(skip))
        if step == "who":
            return self.send(uid, p + "Для кого?", kb([btn("Для всех", "pw:all"), btn("Есть мужская и женская", "pw:both")],
                                                       [btn("Только мужчины", "pw:men"), btn("Только женщины", "pw:women")], skip))
        if step == "wudu":
            return self.send(uid, p + "Есть где сделать омовение?", kb([btn("Да", "pu:yes"), btn("Нет", "pu:no")], skip))
        if step == "photo":
            return self.send(uid, p + "Фото места или входа (по желанию):", kb(skip))
        if step == "note":
            return self.send(uid, p + "Что ещё полезно знать? (коврики, пятничный намаз, как пройти)", kb(skip))

    def next_place_step(self, uid, st):
        i = self.PLACE_STEPS.index(st["step"]) + 1
        if i >= len(self.PLACE_STEPS):
            return self.finish_place(uid, st["data"])
        st["step"] = self.PLACE_STEPS[i]
        self.dirty = True
        self.ask_place_step(uid, st["step"])

    def place_input(self, uid, st, m, text):
        step, data = st["step"], st["data"]
        if step == "location":
            loc = m.get("location") or (m.get("venue") or {}).get("location")
            if not loc:
                return self.send(uid, "Нужна именно геопозиция: 📎 → «Геопозиция». Так мы найдём место на карте.")
            data["lat"], data["lon"] = round(loc["latitude"], 6), round(loc["longitude"], 6)
            if (m.get("venue") or {}).get("address") and not data.get("address"):
                data["address"] = m["venue"]["address"][:150]
            return self.next_place_step(uid, st)
        if step == "photo":
            url = self.photo_url("place_" + str(uid), m)
            if not url:
                return self.send(uid, "Пришлите фото или нажмите «Пропустить».", kb([btn("Пропустить →", "pskp")]))
            data["photo"] = url
            return self.next_place_step(uid, st)
        if not text:
            return self.send(uid, "Напишите ответ текстом.")
        limits = {"name": 80, "address": 150, "hours": 60, "note": 300}
        if step not in limits:
            return self.send(uid, "Выберите ответ кнопкой.")
        if len(text) > limits[step]:
            return self.send(uid, f"Слишком длинно — до {limits[step]} символов.")
        data[step] = text
        self.next_place_step(uid, st)

    def finish_place(self, uid, data):
        pid = new_id("m")
        admin = self.is_admin(uid)
        place = {
            "id": pid, "kind": data.get("kind", "other"), "name": data.get("name", ""),
            "lat": data.get("lat"), "lon": data.get("lon"), "address": data.get("address", ""),
            "hours": data.get("hours", ""), "who": data.get("who", ""), "wudu": data.get("wudu"),
            "photo": data.get("photo", ""), "note": data.get("note", ""),
            "status": "approved" if admin else "pending", "author": uid, "created": time.time(),
        }
        self.db["places"][pid] = place
        self.user(uid)["state"] = None
        self.dirty = True
        if admin:
            self.places_dirty = True
            self.send(uid, "✅ Место добавлено на карту.")
        else:
            self.send(uid, "🙏 Джазакаллаху хайран! Мы проверим место и добавим его на карту в приложении. Сообщим вам.")
            for a in self.db["admins"]:
                self.send(a, "🆕 <b>Новое место для намаза</b>\n\n" + self.place_text(place), self.place_mod_kb(place))
                self.tg.call("sendLocation", chat_id=a, latitude=place["lat"], longitude=place["lon"])
        self.menu(uid)

    def place_text(self, p):
        lines = [f"<b>{esc(p['name'])}</b>", PLACE_KINDS.get(p["kind"], ""), f"Статус: {STATUS_TEXT.get(p['status'], p['status'])}"]
        if p.get("address"):
            lines.append(f"📍 {esc(p['address'])}")
        if p.get("hours"):
            lines.append(f"🕐 {esc(p['hours'])}")
        if p.get("who"):
            lines.append(f"👥 {WHO_TEXT.get(p['who'], '')}")
        if p.get("wudu") is not None:
            lines.append("💧 Омовение: " + ("есть" if p["wudu"] else "нет"))
        if p.get("note"):
            lines.append("\n" + esc(p["note"]))
        if p.get("lat") is not None:
            lines.append(f"\n<a href=\"https://yandex.ru/maps/?pt={p['lon']},{p['lat']}&z=17&l=map\">Открыть в Яндекс Картах</a>")
        return "\n".join(lines)

    def place_mod_kb(self, p):
        return kb([btn("✅ Проверил, добавить", f"pm:ok:{p['id']}"), btn("❌ Нет такого", f"pm:no:{p['id']}")])

    def place_card(self, uid, pid):
        p = self.db["places"].get(pid)
        if not p or not self.is_admin(uid):
            return
        rows = []
        if p["status"] != "approved":
            rows.append([btn("✅ Добавить на карту", f"pm:ok:{pid}")])
        else:
            rows.append([btn("🙈 Скрыть с карты", f"pm:hide:{pid}")])
        rows.append([btn("📍 Точка", f"pm:loc:{pid}"), btn("🗑 Удалить", f"pm:del:{pid}")])
        rows.append([btn("⬅️ Все места", "pl_all")])
        text = self.place_text(p)
        if p.get("photo"):
            self.send_photo(uid, p["photo"], text, {"inline_keyboard": rows})
        else:
            self.send(uid, text, {"inline_keyboard": rows})

    def places_list(self, uid, which):
        if not self.is_admin(uid):
            return
        # Сначала места, которые предложили люди, потом импорт из OpenStreetMap
        places = sorted(self.db["places"].values(), key=lambda x: (x.get("source") == "osm", x["created"]))
        if which == "pending":
            places = [p for p in places if p["status"] == "pending"]
            if not places:
                return self.send(uid, "Нет мест на проверке.", kb([btn("⬅️ Меню", "menu")]))
            for p in places[:10]:
                self.send(uid, self.place_text(p), self.place_mod_kb(p))
                self.tg.call("sendLocation", chat_id=uid, latitude=p["lat"], longitude=p["lon"])
            return
        rows = [[btn(f"{STATUS_TEXT[p['status']].split(' ')[0]} {p['name'][:40]}", f"pc:{p['id']}")] for p in places[:90]]
        rows.append([btn("➕ Добавить место", "place"), btn("⬅️ Меню", "menu")])
        osm = sum(1 for p in places if p.get("source") == "osm")
        self.send(uid, f"Места для намаза: {len(places)} (из них {osm} из OpenStreetMap). Показаны первые 90:",
                  {"inline_keyboard": rows})

    def moderate_place(self, uid, action, pid):
        p = self.db["places"].get(pid)
        if not p:
            return self.send(uid, "Место не найдено.")
        if action == "loc":
            self.tg.call("sendLocation", chat_id=uid, latitude=p["lat"], longitude=p["lon"])
            return
        was_public = p["status"] == "approved"
        if action == "ok":
            p["status"] = "approved"
            if p.get("author") and p["author"] != uid:
                self.send(p["author"], f"🎉 Место «{esc(p['name'])}» проверено и добавлено на карту в приложении Зикр-тайм. Джазакаллаху хайран!")
            self.send(uid, f"✅ «{esc(p['name'])}» на карте.")
        elif action == "no":
            p["status"] = "rejected"
            self.send(uid, f"❌ «{esc(p['name'])}» отклонено.")
        elif action == "hide":
            p["status"] = "hidden"
            self.send(uid, f"🙈 «{esc(p['name'])}» скрыто с карты.")
        elif action == "del":
            if p.get("source") == "osm":
                # чтобы ежемесячный импорт из OpenStreetMap не вернул удалённое место
                self.db.setdefault("osm_removed", []).append(pid)
            del self.db["places"][pid]
            self.send(uid, "🗑 Удалено.")
        self.dirty = True
        if was_public or p.get("status") == "approved":
            self.places_dirty = True

    def build_places(self):
        items = []
        for p in sorted(self.db["places"].values(), key=lambda x: x["created"]):
            if p["status"] != "approved" or p.get("lat") is None:
                continue
            # Компактно: пустые поля не пишем (мест по России — тысячи)
            item = {k: p.get(k) for k in ("id", "kind", "name", "lat", "lon", "address", "hours", "who", "photo", "note") if p.get(k) not in (None, "")}
            if p.get("wudu") is not None:
                item["wudu"] = p["wudu"]
            if p.get("source") == "osm":
                item["src"] = "osm"
            items.append(item)
        return {"version": 1, "updated": int(time.time()), "seller_bot": self.bot_username(),
                "donate_url": self.db.get("donate_url", ""), "places": items}

    def write_places(self):
        import gzip
        data = json.dumps(self.build_places(), ensure_ascii=False, separators=(",", ":")).encode()
        # Сжатый файл: телефоны распаковывают сами (Content-Encoding: gzip)
        self.st.put("places.json", gzip.compress(data, 9), "application/json; charset=utf-8",
                    cache="public, max-age=60", encoding="gzip")

    def set_donate(self, uid, url):
        url = url.strip()
        if url and not re.match(r"^https://[\w.-]+\.[a-z]{2,}(/\S*)?$", url):
            return self.send(uid, "Нужна ссылка вида https://pay.cloudtips.ru/p/…  (или /donate без ссылки — убрать кнопку).")
        self.db["donate_url"] = url
        self.dirty = self.catalog_dirty = self.places_dirty = True
        self.send(uid, "✅ Кнопка «Поддержать проект» ведёт на: " + url if url else "Кнопка «Поддержать проект» скрыта.")


class Bot(PlacesMixin):
    def __init__(self, storage, tg, token=None):
        self.st = storage
        self.tg = tg
        self.token = token or BOT_TOKEN
        raw = storage.get(DB_KEY)
        self.db = json.loads(raw) if raw else {}
        self.db.setdefault("owner", None)
        self.db.setdefault("admins", [])
        self.db.setdefault("sellers", {})
        self.db.setdefault("users", {})
        self.db.setdefault("invites", {})
        self.db.setdefault("contact_telegram", "")
        self.db.setdefault("places", {})
        self.db.setdefault("donate_url", "")
        self.dirty = False
        self.catalog_dirty = False
        self.places_dirty = False

    # ---------- база ----------
    def save(self):
        if self.catalog_dirty:
            self.write_catalog()
        if self.places_dirty:
            self.write_places()
        if self.dirty or self.catalog_dirty or self.places_dirty:
            self.st.put(DB_KEY, json.dumps(self.db, ensure_ascii=False).encode(), "application/json")

    def changed(self, seller=None):
        self.dirty = True
        if seller is None or seller.get("status") == "approved":
            self.catalog_dirty = True

    def user(self, uid):
        return self.db["users"].setdefault(str(uid), {"state": None})

    def is_admin(self, uid):
        return uid in self.db["admins"]

    def my_sellers(self, uid):
        return [s for s in self.db["sellers"].values() if uid in s["owners"]]

    def can_edit(self, uid, seller):
        return seller is not None and (uid in seller["owners"] or self.is_admin(uid))

    def bot_username(self):
        if not self.db.get("bot_username"):
            me = self.tg.call("getMe") or {}
            if me.get("username"):
                self.db["bot_username"] = me["username"]
                self.dirty = True
        return self.db.get("bot_username", "")

    # ---------- каталог для приложения ----------
    def build_catalog(self):
        sellers, products = [], []
        for s in sorted(self.db["sellers"].values(), key=lambda x: (not x.get("partner"), x["created"])):
            if s["status"] != "approved":
                continue
            cat = CAT_BY_ID.get(s["category"], CAT_BY_ID["other"])
            item = {
                "id": s["id"], "name": s["name"], "partner": bool(s.get("partner")),
                "city": {"ru": s.get("city", "")}, "description": {"ru": s.get("description", "")},
                "address": s.get("address", ""), "phone": s.get("phone", ""), "telegram": s.get("telegram", ""),
                "whatsapp": s.get("whatsapp", ""), "instagram": s.get("instagram", ""), "website": "",
                "logo": s.get("logo", ""), "cover": s.get("cover", ""),
                "categories": [cat["id"]], "accent": cat["accent"],
            }
            if s.get("lat") is not None and s.get("lon") is not None:
                item["lat"], item["lon"] = s["lat"], s["lon"]
            sellers.append(item)
            for p in sorted(s["products"].values(), key=lambda x: x["created"]):
                prod = {
                    "id": p["id"], "seller": s["id"], "category": cat["id"],
                    "title": {"ru": p["title"]}, "description": {"ru": p.get("description", "")},
                    "unit": {"ru": p.get("unit", "")} if p.get("unit") else {},
                    "currency": "RUB", "images": p.get("images", []),
                }
                if p.get("price") is not None:
                    prod["price"] = p["price"]
                if p.get("old_price") is not None:
                    prod["old_price"] = p["old_price"]
                products.append(prod)
        return {
            "version": 1, "demo": False, "updated": int(time.time()),
            "contact_telegram": self.db.get("contact_telegram", ""), "seller_bot": self.bot_username(),
            "donate_url": self.db.get("donate_url", ""),
            "categories": [{"id": c["id"], "icon": c["icon"], "names": c["names"]} for c in CATEGORIES],
            "sellers": sellers, "products": products,
        }

    def write_catalog(self):
        data = json.dumps(self.build_catalog(), ensure_ascii=False).encode()
        self.st.put("catalog.json", data, "application/json; charset=utf-8", cache="public, max-age=60")

    # ---------- отправка ----------
    def send(self, chat, text, markup=None):
        return self.tg.call("sendMessage", chat_id=chat, text=text, parse_mode="HTML",
                            reply_markup=markup, disable_web_page_preview=True)

    def send_photo(self, chat, url, caption, markup=None):
        r = self.tg.call("sendPhoto", chat_id=chat, photo=url, caption=caption[:1000], parse_mode="HTML", reply_markup=markup)
        if r is None:
            self.send(chat, caption, markup)

    def notify_admins(self, text, markup=None, skip=None):
        for a in self.db["admins"]:
            if a != skip:
                self.send(a, text, markup)

    # ---------- приём обновлений ----------
    def handle(self, update):
        if "callback_query" in update:
            q = update["callback_query"]
            self.tg.call("answerCallbackQuery", callback_query_id=q["id"])
            msg = q.get("message") or {}
            if (msg.get("chat") or {}).get("type", "private") != "private":
                return
            self.on_callback(q["from"], q.get("data", ""))
        elif "message" in update:
            m = update["message"]
            if m["chat"].get("type") != "private":
                return
            self.on_message(m["from"], m)

    def on_message(self, frm, m):
        uid = frm["id"]
        u = self.user(uid)
        if frm.get("username") and u.get("username") != frm["username"]:
            u["username"] = frm["username"]
            self.dirty = True
        text = (m.get("text") or "").strip()

        if not self.db["owner"] and OWNER_USERNAME and (frm.get("username") or "").lower() == OWNER_USERNAME:
            return self.claim_owner(uid, frm, owner_code(self.token))
        if text.startswith("/start"):
            arg = text[6:].strip()
            u["state"] = None
            self.dirty = True
            if arg.startswith("inv_"):
                return self.claim_invite(uid, arg[4:])
            if arg == "add":
                return self.start_add(uid)
            if arg == "place":
                return self.start_place(uid)
            return self.menu(uid)
        if text.startswith("/owner"):
            return self.claim_owner(uid, frm, text[6:].strip())
        if text in ("/menu", "/cancel", "Меню", "Отмена"):
            u["state"] = None
            self.dirty = True
            return self.menu(uid)
        if text.startswith("/donate") and uid == self.db["owner"]:
            return self.set_donate(uid, text[7:])
        if text.startswith("/admin ") and uid == self.db["owner"]:
            return self.add_admin(uid, text[7:].strip())
        if text == "/help":
            return self.help(uid)

        st = u.get("state")
        if st:
            return self.on_input(uid, u, st, m)
        return self.menu(uid)

    # ---------- меню ----------
    def help(self, uid):
        self.send(uid, "Этот бот — кабинет продавца магазина в приложении <b>Зикр-тайм</b>.\n\n"
                       "• «Добавить компанию» — по шагам: название, категория, город, описание, адрес, контакты, фото.\n"
                       "• После проверки компания появится в приложении.\n"
                       "• Дальше вы сами добавляете товары, цены и фото — изменения видны в приложении через пару минут.\n\n"
                       "/menu — главное меню, /cancel — отменить текущий шаг.")

    def menu(self, uid):
        mine = self.my_sellers(uid)
        rows = [[btn(f"🏪 {s['name']} · {STATUS_TEXT[s['status']].split(' ')[0]}", f"s:{s['id']}")] for s in mine]
        if not mine or len(mine) < MAX_SELLERS_PER_USER or self.is_admin(uid):
            rows.append([btn("➕ Добавить компанию", "add")])
        if self.is_admin(uid):
            pending = sum(1 for s in self.db["sellers"].values() if s["status"] == "pending")
            rows.append([btn(f"🛡 На проверке ({pending})", "adm:pending"), btn("📋 Все компании", "adm:all")])
        rows.append([btn("🕌 Предложить место для намаза", "place")])
        if self.is_admin(uid):
            pp = sum(1 for p in self.db["places"].values() if p["status"] == "pending")
            rows.append([btn(f"📍 Места на проверке ({pp})", "pl_pending"), btn("🗺 Все места", "pl_all")])
        rows.append([btn("❓ Как это работает", "help")])
        if mine:
            text = "Ваши компании в магазине <b>Зикр-тайм</b>. Выберите, что изменить:"
        else:
            text = ("Ассаляму алейкум! Это кабинет продавца магазина в приложении <b>Зикр-тайм</b>.\n\n"
                    "Добавьте свою компанию — после проверки её увидят пользователи приложения: "
                    "товары, цены, фото, адрес и кнопки «Написать в Telegram» и «Позвонить».")
        self.send(uid, text, {"inline_keyboard": rows})

    def claim_owner(self, uid, frm, code):
        if self.db["owner"]:
            return self.send(uid, "Владелец уже назначен." if uid != self.db["owner"] else "Вы уже владелец.")
        if code != owner_code(self.token):
            return self.send(uid, "Неверный код.")
        self.db["owner"] = uid
        if uid not in self.db["admins"]:
            self.db["admins"].append(uid)
        if frm.get("username"):
            self.db["contact_telegram"] = frm["username"]
        self.changed()
        self.send(uid, "✅ Вы — владелец магазина. Сюда будут приходить новые компании на проверку.")
        self.menu(uid)

    def add_admin(self, uid, arg):
        try:
            new = int(arg)
        except ValueError:
            return self.send(uid, "Формат: /admin 123456789 (числовой id пользователя).")
        if new not in self.db["admins"]:
            self.db["admins"].append(new)
            self.changed()
        self.send(uid, f"Добавлен модератор {new}.")

    # ---------- добавление компании ----------
    ADD_STEPS = ["name", "category", "city", "description", "address", "phone", "whatsapp", "telegram", "instagram", "logo", "cover"]

    def start_add(self, uid):
        mine = self.my_sellers(uid)
        if len(mine) >= MAX_SELLERS_PER_USER and not self.is_admin(uid):
            return self.send(uid, "Можно добавить не больше трёх компаний.")
        self.user(uid)["state"] = {"flow": "add", "step": "name", "data": {}}
        self.dirty = True
        self.send(uid, "Шаг 1 из 11. Как называется ваша компания или магазин?\n\n/cancel — отменить")

    def ask_add_step(self, uid, step):
        uname = self.user(uid).get("username")
        n = self.ADD_STEPS.index(step) + 1
        p = f"Шаг {n} из 11. "
        skip = [btn("Пропустить →", "skip")]
        if step == "category":
            rows = [[btn(c["names"]["ru"], f"cat:{c['id']}")] for c in CATEGORIES]
            return self.send(uid, p + "Выберите категорию:", {"inline_keyboard": rows})
        if step == "city":
            return self.send(uid, p + "В каком городе вы находитесь? (например: Грозный)")
        if step == "description":
            return self.send(uid, p + "Коротко о компании (до 600 символов): что продаёте, чем отличаетесь, часы работы.", kb(skip))
        if step == "address":
            return self.send(uid, p + "Адрес (улица, дом). Можно отправить геопозицию через 📎 — тогда в приложении будет кнопка «Маршрут».", kb(skip))
        if step == "phone":
            return self.send(uid, p + "Телефон для звонков, например +7 928 123-45-67.", kb(skip))
        if step == "whatsapp":
            return self.send(uid, p + "Есть WhatsApp на этом номере?", kb([btn("Да", "wa:yes"), btn("Нет", "wa:no")]))
        if step == "telegram":
            rows = [[btn(f"Использовать @{uname}", "tg:me")]] if uname else []
            rows.append(skip)
            return self.send(uid, p + "Telegram для связи с покупателями: напишите @имя или ссылку t.me/…", {"inline_keyboard": rows})
        if step == "instagram":
            return self.send(uid, p + "Instagram (имя или ссылка), если есть.", kb(skip))
        if step == "logo":
            return self.send(uid, p + "Пришлите логотип (квадратное фото).", kb(skip))
        if step == "cover":
            return self.send(uid, p + "Пришлите обложку — красивое фото магазина или товаров (горизонтальное).", kb(skip))

    def next_add_step(self, uid, st):
        steps = self.ADD_STEPS
        i = steps.index(st["step"]) + 1
        data = st["data"]
        while i < len(steps) and steps[i] == "whatsapp" and not data.get("phone"):
            i += 1
        if i >= len(steps):
            return self.finish_add(uid, data)
        st["step"] = steps[i]
        self.dirty = True
        self.ask_add_step(uid, st["step"])

    def finish_add(self, uid, data):
        sid = new_id("s")
        admin = self.is_admin(uid)
        seller = {
            "id": sid, "name": data["name"], "category": data.get("category", "other"), "city": data.get("city", ""),
            "description": data.get("description", ""), "address": data.get("address", ""),
            "lat": data.get("lat"), "lon": data.get("lon"), "phone": data.get("phone", ""),
            "whatsapp": data.get("whatsapp", ""), "telegram": data.get("telegram", ""),
            "instagram": data.get("instagram", ""), "logo": data.get("logo", ""), "cover": data.get("cover", ""),
            "status": "approved" if admin else "pending", "partner": False,
            "owners": [uid], "created": time.time(), "products": {},
        }
        self.db["sellers"][sid] = seller
        self.user(uid)["state"] = None
        self.changed(seller)
        if admin:
            self.send(uid, "✅ Компания добавлена и сразу опубликована (вы модератор). Теперь добавьте товары.")
        else:
            self.send(uid, "🙏 Спасибо! Компания отправлена на проверку. Мы сообщим, когда она появится в приложении.\n"
                           "А пока можно добавить товары — они станут видны вместе с компанией.")
            self.notify_admins("🆕 <b>Новая компания на проверку</b>\n\n" + self.seller_text(seller),
                               self.moderation_kb(seller))
        self.seller_menu(uid, sid)

    # ---------- ввод на шагах ----------
    def photo_url(self, sid, m):
        """Сохраняет фото из сообщения в хранилище и возвращает публичный адрес (или None)."""
        file_id = None
        if m.get("photo"):
            sizes = sorted(m["photo"], key=lambda p: p.get("width", 0) * p.get("height", 0))
            fit = [p for p in sizes if max(p.get("width", 0), p.get("height", 0)) <= 1600]
            file_id = (fit or sizes)[-1]["file_id"]
        elif (m.get("document") or {}).get("mime_type", "").startswith("image/"):
            if m["document"].get("file_size", 0) > 8_000_000:
                return None
            file_id = m["document"]["file_id"]
        if not file_id:
            return None
        info = self.tg.call("getFile", file_id=file_id) or {}
        if not info.get("file_path"):
            return None
        data = self.tg.download(info["file_path"])
        ext = "png" if info["file_path"].lower().endswith(".png") else "jpg"
        key = f"img/{sid}/{secrets.token_hex(6)}.{ext}"
        self.st.put(key, data, "image/png" if ext == "png" else "image/jpeg", cache="public, max-age=31536000, immutable")
        return PUBLIC_BASE + key

    def on_input(self, uid, u, st, m):
        text = (m.get("text") or "").strip()
        flow = st["flow"]
        if flow == "add":
            return self.add_input(uid, st, m, text)
        if flow == "place":
            return self.place_input(uid, st, m, text)
        if flow == "edit":
            return self.edit_input(uid, st, m, text)
        if flow == "padd":
            return self.product_add_input(uid, st, m, text)
        if flow == "pedit":
            return self.product_edit_input(uid, st, m, text)
        u["state"] = None
        self.dirty = True
        self.menu(uid)

    def add_input(self, uid, st, m, text):
        step, data = st["step"], st["data"]
        if step in ("logo", "cover"):
            url = self.photo_url("new_" + str(uid), m)
            if not url:
                return self.send(uid, "Пришлите фото (или нажмите «Пропустить»).")
            data[step] = url
            return self.next_add_step(uid, st)
        if step == "address" and m.get("location"):
            data["lat"], data["lon"] = m["location"]["latitude"], m["location"]["longitude"]
            self.send(uid, "📍 Геопозиция сохранена. Напишите и адрес текстом (или «Пропустить»).", kb([btn("Пропустить →", "skip")]))
            self.dirty = True
            return
        if not text:
            return self.send(uid, "Напишите ответ текстом.")
        error = self.set_field(data, step, text)
        if error:
            return self.send(uid, error)
        self.next_add_step(uid, st)

    def set_field(self, target, field, text):
        """Проверяет и записывает текстовое поле компании. Возвращает текст ошибки или None."""
        limits = {"name": 60, "city": 40, "description": 600, "address": 150}
        if field in limits:
            if len(text) > limits[field]:
                return f"Слишком длинно — не больше {limits[field]} символов."
            target[field] = text
            return None
        if field == "phone":
            phone = clean_phone(text)
            if not phone:
                return "Не похоже на номер телефона. Пример: +7 928 123-45-67"
            target["phone"] = phone
            return None
        if field in ("telegram", "instagram"):
            h = clean_handle(text)
            if not h:
                return "Напишите имя вида @name или ссылку."
            target[field] = h
            return None
        if field == "category":
            return "Выберите категорию кнопкой."
        return "Этот шаг ждёт фото."

    # ---------- карточка компании ----------
    def seller_text(self, s):
        cat = CAT_BY_ID.get(s["category"], CAT_BY_ID["other"])["names"]["ru"]
        lines = [f"<b>{esc(s['name'])}</b> · {esc(cat)}", f"Статус: {STATUS_TEXT[s['status']]}" + (" · ⭐ партнёр" if s.get("partner") else "")]
        if s.get("city"):
            lines.append(f"🏙 {esc(s['city'])}")
        if s.get("address") or s.get("lat") is not None:
            lines.append(f"📍 {esc(s.get('address') or 'геопозиция')}")
        if s.get("phone"):
            lines.append(f"📞 {esc(s['phone'])}" + (" (WhatsApp)" if s.get("whatsapp") else ""))
        if s.get("telegram"):
            lines.append(f"✈️ @{esc(s['telegram'])}")
        if s.get("instagram"):
            lines.append(f"📷 instagram.com/{esc(s['instagram'])}")
        if s.get("description"):
            lines.append("\n" + esc(s["description"]))
        lines.append(f"\n📦 Товаров: {len(s['products'])} · 🖼 Логотип: {'есть' if s.get('logo') else 'нет'} · Обложка: {'есть' if s.get('cover') else 'нет'}")
        return "\n".join(lines)

    def moderation_kb(self, s):
        return kb([btn("✅ Одобрить", f"mod:ok:{s['id']}"), btn("❌ Отклонить", f"mod:no:{s['id']}")],
                  [btn("Открыть", f"s:{s['id']}")])

    def seller_menu(self, uid, sid):
        s = self.db["sellers"].get(sid)
        if not self.can_edit(uid, s):
            return self.menu(uid)
        rows = [
            [btn("➕ Добавить товар", f"pa:{sid}"), btn(f"📦 Товары ({len(s['products'])})", f"pl:{sid}")],
            [btn("✏️ Название", f"e:{sid}:name"), btn("📝 Описание", f"e:{sid}:description")],
            [btn("🏙 Город", f"e:{sid}:city"), btn("📍 Адрес", f"e:{sid}:address")],
            [btn("📞 Телефон", f"e:{sid}:phone"), btn("✈️ Telegram", f"e:{sid}:telegram")],
            [btn("🟢 WhatsApp: " + ("да" if s.get("whatsapp") else "нет"), f"wa2:{sid}")] if s.get("phone") else None,
            [btn("📷 Instagram", f"e:{sid}:instagram"), btn("🗂 Категория", f"e:{sid}:category")],
            [btn("🖼 Логотип", f"e:{sid}:logo"), btn("🌄 Обложка", f"e:{sid}:cover")],
            [btn("🔗 Пригласить совладельца", f"inv:{sid}")],
            [btn("🙈 Скрыть" if s["status"] == "approved" else "👁 Показать", f"vis:{sid}")]
            if s["status"] in ("approved", "hidden") else None,
        ]
        if self.is_admin(uid):
            rows.append([btn("⭐ Партнёр: " + ("да" if s.get("partner") else "нет"), f"mod:partner:{sid}")])
            if s["status"] in ("pending", "rejected", "hidden"):
                rows.append([btn("✅ Одобрить", f"mod:ok:{sid}"), btn("❌ Отклонить", f"mod:no:{sid}")])
        rows.append([btn("🗑 Удалить компанию", f"del:{sid}"), btn("⬅️ Меню", "menu")])
        markup = {"inline_keyboard": [r for r in rows if r]}
        text = self.seller_text(s)
        if s.get("cover") or s.get("logo"):
            self.send_photo(uid, s.get("cover") or s.get("logo"), text, markup)
        else:
            self.send(uid, text, markup)

    # ---------- правка полей ----------
    FIELD_PROMPTS = {
        "name": "Новое название:",
        "description": "Новое описание (до 600 символов):",
        "city": "Город:",
        "address": "Адрес текстом или геопозиция через 📎:",
        "phone": "Телефон, например +7 928 123-45-67:",
        "telegram": "Telegram: @имя или ссылка t.me/…",
        "instagram": "Instagram: имя или ссылка:",
        "logo": "Пришлите новый логотип (фото):",
        "cover": "Пришлите новую обложку (фото):",
    }

    def start_edit(self, uid, sid, field):
        s = self.db["sellers"].get(sid)
        if not self.can_edit(uid, s):
            return
        if field == "category":
            rows = [[btn(c["names"]["ru"], f"ec:{sid}:{c['id']}")] for c in CATEGORIES]
            return self.send(uid, "Выберите категорию:", {"inline_keyboard": rows})
        self.user(uid)["state"] = {"flow": "edit", "sid": sid, "field": field}
        self.dirty = True
        extra = []
        if field in ("description", "address", "phone", "telegram", "instagram", "logo", "cover"):
            extra.append(btn("🗑 Очистить", f"clr:{sid}:{field}"))
        extra.append(btn("Отмена", f"s:{sid}"))
        self.send(uid, self.FIELD_PROMPTS[field], kb(extra))

    def edit_input(self, uid, st, m, text):
        s = self.db["sellers"].get(st["sid"])
        if not self.can_edit(uid, s):
            self.user(uid)["state"] = None
            return self.menu(uid)
        field = st["field"]
        if field in ("logo", "cover"):
            url = self.photo_url(s["id"], m)
            if not url:
                return self.send(uid, "Пришлите фото.")
            s[field] = url
        elif field == "address" and m.get("location"):
            s["lat"], s["lon"] = m["location"]["latitude"], m["location"]["longitude"]
        else:
            if not text:
                return self.send(uid, "Напишите ответ текстом.")
            error = self.set_field(s, field, text)
            if error:
                return self.send(uid, error)
            if field == "phone" and s.get("whatsapp"):
                s["whatsapp"] = s["phone"]
        self.user(uid)["state"] = None
        self.after_seller_edit(uid, s, field)
        self.send(uid, "✅ Сохранено.")
        self.seller_menu(uid, s["id"])

    def after_seller_edit(self, uid, s, what):
        self.changed(s)
        if s["status"] == "approved" and not self.is_admin(uid):
            self.notify_admins(f"✏️ <b>{esc(s['name'])}</b>: изменено «{esc(what)}».",
                               kb([btn("Открыть", f"s:{s['id']}"), btn("🙈 Скрыть", f"mod:hide:{s['id']}")]))

    # ---------- товары ----------
    def product_list(self, uid, sid):
        s = self.db["sellers"].get(sid)
        if not self.can_edit(uid, s):
            return
        rows = [[btn(f"{p['title'][:30]} — {fmt_price(p.get('price'))}", f"p:{sid}:{p['id']}")]
                for p in sorted(s["products"].values(), key=lambda x: x["created"])]
        rows.append([btn("➕ Добавить товар", f"pa:{sid}"), btn("⬅️ Назад", f"s:{sid}")])
        self.send(uid, f"Товары «{esc(s['name'])}»:" if s["products"] else "Товаров пока нет.", {"inline_keyboard": rows})

    def start_product_add(self, uid, sid):
        s = self.db["sellers"].get(sid)
        if not self.can_edit(uid, s):
            return
        if len(s["products"]) >= MAX_PRODUCTS:
            return self.send(uid, f"Можно добавить не больше {MAX_PRODUCTS} товаров.")
        self.user(uid)["state"] = {"flow": "padd", "sid": sid, "step": "title", "data": {"images": []}}
        self.dirty = True
        self.send(uid, "Название товара или услуги (до 80 символов):", kb([btn("Отмена", f"s:{sid}")]))

    def product_add_input(self, uid, st, m, text):
        s = self.db["sellers"].get(st["sid"])
        if not self.can_edit(uid, s):
            self.user(uid)["state"] = None
            return self.menu(uid)
        step, data = st["step"], st["data"]
        if step == "photos":
            url = self.photo_url(s["id"], m)
            if not url:
                return self.send(uid, "Пришлите фото или нажмите «Готово».", kb([btn("✅ Готово", "pdone")]))
            data["images"].append(url)
            self.dirty = True
            if len(data["images"]) >= MAX_PHOTOS:
                return self.finish_product(uid, st)
            return self.send(uid, f"Фото {len(data['images'])} из {MAX_PHOTOS} добавлено. Ещё фото или «Готово».",
                             kb([btn("✅ Готово", "pdone")]))
        if not text:
            return self.send(uid, "Напишите ответ текстом.")
        if step == "title":
            if len(text) > 80:
                return self.send(uid, "Слишком длинно — до 80 символов.")
            data["title"] = text
            st["step"] = "price"
            self.dirty = True
            return self.send(uid, "Цена в рублях (только число, например 1500):", kb([btn("Цена по запросу", "pskip")]))
        if step == "price":
            price = parse_price(text)
            if price is None:
                return self.send(uid, "Напишите число, например 1500.", kb([btn("Цена по запросу", "pskip")]))
            data["price"] = price
            return self.product_step(uid, st, "unit")
        if step == "unit":
            if len(text) > 20:
                return self.send(uid, "Коротко, до 20 символов: «6 мл», «сутки», «шт».")
            data["unit"] = text
            return self.product_step(uid, st, "description")
        if step == "description":
            if len(text) > 500:
                return self.send(uid, "Слишком длинно — до 500 символов.")
            data["description"] = text
            return self.product_step(uid, st, "photos")

    def product_step(self, uid, st, step):
        st["step"] = step
        self.dirty = True
        skip = kb([btn("Пропустить →", "pskip")])
        if step == "unit":
            return self.send(uid, "За что цена? Например «6 мл», «сутки», «шт».", skip)
        if step == "description":
            return self.send(uid, "Описание товара (до 500 символов):", skip)
        if step == "photos":
            return self.send(uid, f"Пришлите до {MAX_PHOTOS} фото товара (по одному или альбомом). Потом нажмите «Готово».",
                             kb([btn("✅ Готово", "pdone")]))

    def product_skip(self, uid):
        st = self.user(uid).get("state") or {}
        if st.get("flow") != "padd":
            return
        nxt = {"price": "unit", "unit": "description", "description": "photos"}.get(st["step"])
        if st["step"] == "price":
            st["data"]["price"] = None
        if nxt:
            self.product_step(uid, st, nxt)

    def finish_product(self, uid, st):
        s = self.db["sellers"].get(st["sid"])
        if not self.can_edit(uid, s) or "title" not in st["data"]:
            self.user(uid)["state"] = None
            return self.menu(uid)
        d = st["data"]
        pid = new_id("p")
        s["products"][pid] = {"id": pid, "title": d["title"], "price": d.get("price"), "unit": d.get("unit", ""),
                              "description": d.get("description", ""), "images": d.get("images", []), "created": time.time()}
        self.user(uid)["state"] = None
        self.after_seller_edit(uid, s, "новый товар: " + d["title"])
        self.send(uid, "✅ Товар добавлен" + (" и уже виден в приложении." if s["status"] == "approved" else "."))
        self.product_menu(uid, s["id"], pid)

    def product_menu(self, uid, sid, pid):
        s = self.db["sellers"].get(sid)
        if not self.can_edit(uid, s) or pid not in s["products"]:
            return
        p = s["products"][pid]
        text = (f"<b>{esc(p['title'])}</b>\nЦена: {fmt_price(p.get('price'))}"
                + (f" / {esc(p['unit'])}" if p.get("unit") else "")
                + (f" (было {fmt_price(p['old_price'])})" if p.get("old_price") else "")
                + (f"\n\n{esc(p['description'])}" if p.get("description") else "")
                + f"\n\n🖼 Фото: {len(p.get('images', []))}")
        markup = kb(
            [btn("✏️ Название", f"pe:{sid}:{pid}:title"), btn("💰 Цена", f"pe:{sid}:{pid}:price")],
            [btn("🏷 Старая цена (скидка)", f"pe:{sid}:{pid}:old_price"), btn("📏 За что цена", f"pe:{sid}:{pid}:unit")],
            [btn("📝 Описание", f"pe:{sid}:{pid}:description"), btn("➕ Фото", f"pe:{sid}:{pid}:photo")],
            [btn("🗑 Удалить все фото", f"pph:{sid}:{pid}")] if p.get("images") else None,
            [btn("🗑 Удалить товар", f"pdel:{sid}:{pid}"), btn("⬅️ К товарам", f"pl:{sid}")],
        )
        if p.get("images"):
            self.send_photo(uid, p["images"][0], text, markup)
        else:
            self.send(uid, text, markup)

    PRODUCT_PROMPTS = {
        "title": "Новое название (до 80 символов):",
        "price": "Новая цена (число) — или «Цена по запросу»:",
        "old_price": "Старая цена (будет зачёркнута, покажется скидка) — или «Убрать»:",
        "unit": "За что цена («6 мл», «сутки», «шт»):",
        "description": "Новое описание (до 500 символов):",
        "photo": f"Пришлите фото (всего у товара может быть до {MAX_PHOTOS}):",
    }

    def start_product_edit(self, uid, sid, pid, field):
        s = self.db["sellers"].get(sid)
        if not self.can_edit(uid, s) or pid not in s["products"]:
            return
        self.user(uid)["state"] = {"flow": "pedit", "sid": sid, "pid": pid, "field": field}
        self.dirty = True
        extra = []
        if field in ("price", "old_price", "unit", "description"):
            extra.append(btn("Цена по запросу" if field == "price" else "🗑 Убрать", f"pclr:{sid}:{pid}:{field}"))
        extra.append(btn("Отмена", f"p:{sid}:{pid}"))
        self.send(uid, self.PRODUCT_PROMPTS[field], kb(extra))

    def product_edit_input(self, uid, st, m, text):
        s = self.db["sellers"].get(st["sid"])
        if not self.can_edit(uid, s) or st["pid"] not in s["products"]:
            self.user(uid)["state"] = None
            return self.menu(uid)
        p = s["products"][st["pid"]]
        field = st["field"]
        if field == "photo":
            if len(p.get("images", [])) >= MAX_PHOTOS:
                self.user(uid)["state"] = None
                return self.send(uid, f"У товара уже {MAX_PHOTOS} фото. Удалите старые, чтобы добавить новые.")
            url = self.photo_url(s["id"], m)
            if not url:
                return self.send(uid, "Пришлите фото.")
            p.setdefault("images", []).append(url)
        else:
            if not text:
                return self.send(uid, "Напишите ответ текстом.")
            if field in ("price", "old_price"):
                v = parse_price(text)
                if v is None:
                    return self.send(uid, "Напишите число, например 1500.")
                p[field] = v
            else:
                limit = {"title": 80, "unit": 20, "description": 500}[field]
                if len(text) > limit:
                    return self.send(uid, f"Слишком длинно — до {limit} символов.")
                p[field] = text
        self.user(uid)["state"] = None
        self.after_seller_edit(uid, s, f"товар «{p['title']}»")
        self.send(uid, "✅ Сохранено.")
        self.product_menu(uid, s["id"], p["id"])

    # ---------- приглашения ----------
    def make_invite(self, uid, sid):
        s = self.db["sellers"].get(sid)
        if not self.can_edit(uid, s):
            return
        code = secrets.token_urlsafe(9).replace("-", "x").replace("_", "y")
        self.db["invites"][code] = {"sid": sid, "created": time.time()}
        self.dirty = True
        link = f"https://t.me/{self.bot_username()}?start=inv_{code}"
        self.send(uid, f"Отправьте эту ссылку человеку, который тоже будет управлять «{esc(s['name'])}» "
                       f"(например, самому продавцу). Ссылка одноразовая, действует 7 дней:\n\n{link}")

    def claim_invite(self, uid, code):
        inv = self.db["invites"].pop(code, None)
        self.dirty = True
        if not inv or time.time() - inv["created"] > 7 * 86400 or inv["sid"] not in self.db["sellers"]:
            self.send(uid, "Ссылка недействительна или устарела. Попросите новую.")
            return self.menu(uid)
        s = self.db["sellers"][inv["sid"]]
        if uid not in s["owners"]:
            s["owners"].append(uid)
        self.send(uid, f"✅ Теперь вы можете управлять компанией «{esc(s['name'])}».")
        self.notify_admins(f"🔗 К «{esc(s['name'])}» присоединился пользователь {uid}.", skip=uid)
        self.seller_menu(uid, s["id"])

    # ---------- кнопки ----------
    def on_callback(self, frm, data):
        uid = frm["id"]
        u = self.user(uid)
        if frm.get("username") and u.get("username") != frm["username"]:
            u["username"] = frm["username"]
            self.dirty = True
        parts = data.split(":")
        act = parts[0]
        st = u.get("state") or {}

        if act == "menu":
            u["state"] = None
            self.dirty = True
            return self.menu(uid)
        if act == "help":
            return self.help(uid)
        if act == "add":
            return self.start_add(uid)
        if act == "skip" and st.get("flow") == "add":
            if st["step"] in ("name", "category", "city"):
                return self.send(uid, "Этот шаг обязательный.")
            return self.next_add_step(uid, st)
        if act == "cat" and st.get("flow") == "add" and st["step"] == "category":
            st["data"]["category"] = parts[1] if parts[1] in CAT_BY_ID else "other"
            return self.next_add_step(uid, st)
        if act == "wa" and st.get("flow") == "add" and st["step"] == "whatsapp":
            if parts[1] == "yes":
                st["data"]["whatsapp"] = st["data"].get("phone", "")
            return self.next_add_step(uid, st)
        if act == "tg" and st.get("flow") == "add" and st["step"] == "telegram" and u.get("username"):
            st["data"]["telegram"] = u["username"]
            return self.next_add_step(uid, st)
        if act == "s":
            u["state"] = None
            self.dirty = True
            return self.seller_menu(uid, parts[1])
        if act == "e":
            return self.start_edit(uid, parts[1], parts[2])
        if act == "ec":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s) and parts[2] in CAT_BY_ID:
                s["category"] = parts[2]
                self.after_seller_edit(uid, s, "категория")
                return self.seller_menu(uid, s["id"])
        if act == "clr":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s):
                field = parts[2]
                s[field] = ""
                if field == "phone":
                    s["whatsapp"] = ""
                if field == "address":
                    s["lat"] = s["lon"] = None
                u["state"] = None
                self.after_seller_edit(uid, s, field)
                return self.seller_menu(uid, s["id"])
        if act == "pa":
            return self.start_product_add(uid, parts[1])
        if act == "pl":
            u["state"] = None
            self.dirty = True
            return self.product_list(uid, parts[1])
        if act == "pskip":
            return self.product_skip(uid)
        if act == "pdone" and st.get("flow") == "padd":
            return self.finish_product(uid, st)
        if act == "p":
            u["state"] = None
            self.dirty = True
            return self.product_menu(uid, parts[1], parts[2])
        if act == "pe":
            return self.start_product_edit(uid, parts[1], parts[2], parts[3])
        if act == "pclr":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s) and parts[2] in s["products"]:
                p = s["products"][parts[2]]
                p[parts[3]] = None if parts[3] in ("price", "old_price") else ""
                u["state"] = None
                self.after_seller_edit(uid, s, f"товар «{p['title']}»")
                return self.product_menu(uid, s["id"], p["id"])
        if act == "pph":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s) and parts[2] in s["products"]:
                s["products"][parts[2]]["images"] = []
                self.after_seller_edit(uid, s, "фото товара")
                return self.product_menu(uid, s["id"], parts[2])
        if act == "pdel":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s) and parts[2] in s["products"]:
                return self.send(uid, "Удалить товар?", kb([btn("Да, удалить", f"pdel2:{parts[1]}:{parts[2]}"),
                                                             btn("Нет", f"p:{parts[1]}:{parts[2]}")]))
        if act == "pdel2":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s) and parts[2] in s["products"]:
                del s["products"][parts[2]]
                self.after_seller_edit(uid, s, "удалён товар")
                self.send(uid, "🗑 Товар удалён.")
                return self.product_list(uid, s["id"])
        if act == "inv":
            return self.make_invite(uid, parts[1])
        if act == "vis":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s) and s["status"] in ("approved", "hidden"):
                s["status"] = "hidden" if s["status"] == "approved" else "approved"
                self.dirty = self.catalog_dirty = True
                return self.seller_menu(uid, s["id"])
        if act == "wa2":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s) and s.get("phone"):
                s["whatsapp"] = "" if s.get("whatsapp") else s["phone"]
                self.after_seller_edit(uid, s, "WhatsApp")
                return self.seller_menu(uid, s["id"])
        if act == "del":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s):
                return self.send(uid, f"Удалить «{esc(s['name'])}» со всеми товарами? Это нельзя отменить.",
                                 kb([btn("Да, удалить", f"del2:{s['id']}"), btn("Нет", f"s:{s['id']}")]))
        if act == "del2":
            s = self.db["sellers"].get(parts[1])
            if self.can_edit(uid, s):
                del self.db["sellers"][s["id"]]
                self.dirty = self.catalog_dirty = True
                self.send(uid, "🗑 Компания удалена.")
                if not self.is_admin(uid):
                    self.notify_admins(f"🗑 Продавец удалил компанию «{esc(s['name'])}».")
                return self.menu(uid)
        if act == "place":
            return self.start_place(uid)
        if act == "pk" and st.get("flow") == "place" and st["step"] == "kind":
            st["data"]["kind"] = parts[1] if parts[1] in PLACE_KINDS else "other"
            return self.next_place_step(uid, st)
        if act == "pw" and st.get("flow") == "place" and st["step"] == "who":
            st["data"]["who"] = parts[1] if parts[1] in WHO_TEXT else ""
            return self.next_place_step(uid, st)
        if act == "pu" and st.get("flow") == "place" and st["step"] == "wudu":
            st["data"]["wudu"] = parts[1] == "yes"
            return self.next_place_step(uid, st)
        if act == "pskp" and st.get("flow") == "place":
            if st["step"] in ("kind", "name", "location"):
                return self.send(uid, "Этот шаг обязательный.")
            return self.next_place_step(uid, st)
        if act == "pl_pending":
            return self.places_list(uid, "pending")
        if act == "pl_all":
            return self.places_list(uid, "all")
        if act == "pc":
            return self.place_card(uid, parts[1])
        if act == "pm" and self.is_admin(uid):
            return self.moderate_place(uid, parts[1], parts[2])
        if act == "adm" and self.is_admin(uid):
            return self.admin_list(uid, parts[1])
        if act == "mod" and self.is_admin(uid):
            return self.moderate(uid, parts[1], parts[2])
        return None

    # ---------- модерация ----------
    def admin_list(self, uid, which):
        sellers = sorted(self.db["sellers"].values(), key=lambda x: x["created"])
        if which == "pending":
            sellers = [s for s in sellers if s["status"] == "pending"]
            if not sellers:
                return self.send(uid, "Нет компаний на проверке.", kb([btn("⬅️ Меню", "menu")]))
            for s in sellers[:10]:
                self.send(uid, self.seller_text(s), self.moderation_kb(s))
            return
        rows = [[btn(f"{STATUS_TEXT[s['status']].split(' ')[0]} {s['name']}", f"s:{s['id']}")] for s in sellers[:90]]
        rows.append([btn("⬅️ Меню", "menu")])
        self.send(uid, f"Все компании ({len(sellers)}):", {"inline_keyboard": rows})

    def moderate(self, uid, action, sid):
        s = self.db["sellers"].get(sid)
        if not s:
            return self.send(uid, "Компания не найдена (возможно, удалена).")
        was_public = s["status"] == "approved"
        if action == "ok":
            s["status"] = "approved"
            for o in s["owners"]:
                if o != uid:
                    self.send(o, f"🎉 Компания «{esc(s['name'])}» одобрена и уже видна в приложении Зикр-тайм!")
            self.send(uid, f"✅ «{esc(s['name'])}» опубликована.")
        elif action == "no":
            s["status"] = "rejected"
            contact = self.db.get("contact_telegram")
            for o in s["owners"]:
                if o != uid:
                    self.send(o, f"Компания «{esc(s['name'])}» пока не прошла проверку."
                                 + (f" Напишите @{esc(contact)}, чтобы узнать подробности." if contact else ""))
            self.send(uid, f"❌ «{esc(s['name'])}» отклонена.")
        elif action == "hide":
            s["status"] = "hidden"
            self.send(uid, f"🙈 «{esc(s['name'])}» скрыта. Показать снова — в карточке компании.")
        elif action == "partner":
            s["partner"] = not s.get("partner")
            self.send(uid, f"⭐ Партнёр: {'да' if s['partner'] else 'нет'}.")
        self.dirty = True
        if was_public or s["status"] == "approved":
            self.catalog_dirty = True


# --------------------------------------------------------------------------------------------
# Точка входа облачной функции
# --------------------------------------------------------------------------------------------


def probe(token):
    """Самопроверка (вызывает только развёртывание): доступны ли Telegram и хранилище из облака."""
    result = {}
    for name, url in [("telegram", "https://api.telegram.org/"), ("storage", f"{STORAGE_HOST}/{BUCKET}/catalog.json")]:
        t = time.time()
        try:
            urllib.request.urlopen(url, timeout=8).close()
            result[name] = f"ok {time.time() - t:.1f}s"
        except urllib.error.HTTPError as e:
            result[name] = f"http {e.code} {time.time() - t:.1f}s"
        except Exception as e:
            result[name] = f"fail {type(e).__name__} {time.time() - t:.1f}s"
    try:
        make_storage(token).put("diag/probe.txt", b"ok", "text/plain")
        result["storage_write"] = "ok"
    except Exception as e:
        result["storage_write"] = f"fail {type(e).__name__} {str(e)[:80]}"
    return result


def handler(event, context):
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    if headers.get("x-telegram-bot-api-secret-token") != webhook_secret():
        return {"statusCode": 403, "body": "forbidden"}
    body = event.get("body") or "{}"
    if event.get("isBase64Encoded"):
        body = base64.b64decode(body).decode()
    update = json.loads(body)
    token = (getattr(context, "token", None) or {}).get("access_token", "")
    if update.get("probe"):
        return {"statusCode": 200, "body": json.dumps(probe(token), ensure_ascii=False)}
    tg = Telegram(BOT_TOKEN)
    bot = Bot(make_storage(token), tg)
    try:
        bot.handle(update)
    except Exception as e:  # не даём Telegram повторять одно и то же обновление бесконечно
        print("error", repr(e))
        chat = ((update.get("message") or {}).get("from") or (update.get("callback_query") or {}).get("from") or {}).get("id")
        if chat:
            tg.call("sendMessage", chat_id=chat, text="Что-то пошло не так. Попробуйте ещё раз или /menu.")
    finally:
        bot.save()
    return {"statusCode": 200, "body": "ok"}
