"""Проверка бота без сети: поддельные Telegram и хранилище. Запуск: python3 shop-bot/test_bot.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
os.environ["BOT_TOKEN"] = "123:TEST"
import index  # noqa: E402

TOKEN = "123:TEST"


class FakeStorage:
    def __init__(self):
        self.files = {}

    def get(self, key):
        return self.files.get(key)

    def put(self, key, data, content_type, cache="no-cache", encoding=None, if_match=None, if_none_match=False):
        assert isinstance(data, bytes)
        if encoding == "gzip":
            import gzip
            data = gzip.decompress(data)
        self.files[key] = data

    def delete(self, key):
        self.files.pop(key, None)

    def list(self, prefix, limit=50):
        return sorted(k for k in self.files if k.startswith(prefix))[:limit]


class FakeTg:
    def __init__(self):
        self.sent = []

    def call(self, method, **p):
        self.sent.append((method, p))
        if method == "getMe":
            return {"username": "ZikrTimeShopBot"}
        if method == "getFile":
            return {"file_path": "photos/file_1.jpg"}
        return {"ok": True}

    def download(self, path):
        return b"\xff\xd8JPEGDATA"

    def texts(self, chat):
        return [p.get("text") or p.get("caption") for m, p in self.sent if m in ("sendMessage", "sendPhoto") and p.get("chat_id") == chat]

    def last_buttons(self, chat):
        for m, p in reversed(self.sent):
            if m in ("sendMessage", "sendPhoto") and p.get("chat_id") == chat and p.get("reply_markup"):
                return [b.get("callback_data") for row in p["reply_markup"]["inline_keyboard"] for b in row if b.get("callback_data")]
        return []


storage, tg = FakeStorage(), FakeTg()
OWNER, SELLER, FRIEND = 1001, 2002, 3003
uid_seq = [0]


def run(update):
    bot = index.Bot(storage, tg, TOKEN)
    bot.handle(update)
    bot.save()
    return bot


def msg(uid, text=None, **extra):
    uid_seq[0] += 1
    m = {"message_id": uid_seq[0], "from": {"id": uid, "username": f"user{uid}"}, "chat": {"id": uid, "type": "private"}}
    if text is not None:
        m["text"] = text
    m.update(extra)
    return run({"update_id": uid_seq[0], "message": m})


def press(uid, data):
    uid_seq[0] += 1
    return run({"update_id": uid_seq[0], "callback_query": {"id": str(uid_seq[0]), "from": {"id": uid, "username": f"user{uid}"},
                                                            "message": {"chat": {"id": uid, "type": "private"}}, "data": data}})


def db():
    return json.loads(storage.files["data/db.json"])


def catalog():
    return json.loads(storage.files["catalog.json"])


photo = {"photo": [{"file_id": "small", "width": 90, "height": 90}, {"file_id": "big", "width": 1280, "height": 960}]}
failures = 0


def check(cond, what):
    global failures
    if not cond:
        failures += 1
        print("ОШИБКА:", what)


# Владелец
msg(OWNER, "/owner wrong")
check("Неверный код." in tg.texts(OWNER), "неверный код отклонён")
msg(OWNER, "/owner " + index.owner_code(TOKEN))
check(db()["owner"] == OWNER and OWNER in db()["admins"], "владелец назначен")
check(db()["contact_telegram"] == f"user{OWNER}", "контакт владельца")
msg(SELLER, "/owner " + index.owner_code(TOKEN))
check(db()["owner"] == OWNER, "второй владелец не назначается")

# Продавец добавляет компанию
msg(SELLER, "/start add")
msg(SELLER, "Ароматы Грозного")
check("cat:perfume" in tg.last_buttons(SELLER), "кнопки категорий")
press(SELLER, "cat:perfume")
msg(SELLER, "Грозный")
msg(SELLER, "Масляные духи без спирта, бахур.")
msg(SELLER, location={"latitude": 43.3178, "longitude": 45.6949})
press(SELLER, "skip")  # адрес текстом — пропустить
msg(SELLER, "8 928 123-45-67")
press(SELLER, "wa:yes")
press(SELLER, "tg:me")
msg(SELLER, "https://instagram.com/aromaty_groznogo/")
msg(SELLER, **photo)          # логотип
press(SELLER, "skip")         # обложка
d = db()
check(len(d["sellers"]) == 1, "компания создана")
s = next(iter(d["sellers"].values()))
sid = s["id"]
check(s["status"] == "pending", "на проверке")
check(s["phone"] == "+79281234567" and s["whatsapp"] == "+79281234567", "телефон и WhatsApp")
check(s["telegram"] == f"user{SELLER}" and s["instagram"] == "aromaty_groznogo", "Telegram и Instagram")
check(s["lat"] == 43.3178 and s["logo"].startswith(index.PUBLIC_BASE + "img/"), "геопозиция и логотип")
check(any("Новая компания на проверку" in (t or "") for t in tg.texts(OWNER)), "владельцу пришла заявка")
check("catalog.json" not in storage.files or not catalog()["sellers"], "до одобрения не в каталоге")

# Товар до одобрения
press(SELLER, f"pa:{sid}")
msg(SELLER, "Масляные духи «Уд»")
msg(SELLER, "1 500 ₽")
msg(SELLER, "6 мл")
press(SELLER, "pskip")       # описание
msg(SELLER, **photo)
msg(SELLER, **photo)
press(SELLER, "pdone")
p = next(iter(db()["sellers"][sid]["products"].values()))
check(p["price"] == 1500 and p["unit"] == "6 мл" and len(p["images"]) == 2, "товар сохранён")

# Одобрение
press(SELLER, f"mod:ok:{sid}")  # не админ — ничего
check(db()["sellers"][sid]["status"] == "pending", "продавец не может одобрить себя")
press(OWNER, f"mod:ok:{sid}")
c = catalog()
check(db()["sellers"][sid]["status"] == "approved", "одобрено")
check(len(c["sellers"]) == 1 and len(c["products"]) == 1, "в каталоге")
check(c["seller_bot"] == "ZikrTimeShopBot" and c["demo"] is False, "ссылка на бота в каталоге")
check(c["products"][0]["price"] == 1500 and c["products"][0]["title"]["ru"] == "Масляные духи «Уд»", "товар в каталоге")
check(c["sellers"][0]["lat"] == 43.3178 and c["sellers"][0]["accent"] == "#8A5A2B", "координаты и цвет")
check(any("одобрена" in (t or "") for t in tg.texts(SELLER)), "продавцу сообщили об одобрении")

# Правка после одобрения публикуется сразу, владелец уведомлён
before = len(tg.texts(OWNER))
press(SELLER, f"e:{sid}:name")
msg(SELLER, "Ароматы Востока Грозный")
check(catalog()["sellers"][0]["name"] == "Ароматы Востока Грозный", "правка видна в каталоге")
check(len(tg.texts(OWNER)) > before, "владелец получил уведомление о правке")
press(SELLER, f"e:{sid}:phone")
msg(SELLER, "abc")
check("Не похоже на номер телефона" in (tg.texts(SELLER)[-1] or ""), "проверка телефона")
press(SELLER, f"s:{sid}")

# Цена со скидкой, удаление товара
pid = p["id"]
press(SELLER, f"pe:{sid}:{pid}:old_price")
msg(SELLER, "2000")
check(catalog()["products"][0]["old_price"] == 2000, "старая цена")
press(SELLER, f"pclr:{sid}:{pid}:price")
check("price" not in catalog()["products"][0], "цена по запросу")

# Скрыть / показать / партнёр
press(OWNER, f"mod:partner:{sid}")
check(catalog()["sellers"][0]["partner"] is True, "партнёр")
press(SELLER, f"vis:{sid}")
check(catalog()["sellers"] == [], "скрыта")
press(SELLER, f"vis:{sid}")
check(len(catalog()["sellers"]) == 1, "показана снова")

# Приглашение совладельца
press(OWNER, f"inv:{sid}")
link = [t for t in tg.texts(OWNER) if t and "start=inv_" in t][-1]
code = link.split("start=inv_")[1].split()[0]
msg(FRIEND, "/start inv_" + code)
check(FRIEND in db()["sellers"][sid]["owners"], "совладелец добавлен")
msg(3004, "/start inv_" + code)
check(3004 not in db()["sellers"][sid]["owners"], "ссылка одноразовая")

# Чужой не может править
press(3004, f"e:{sid}:name")
msg(3004, "Взлом")
check(db()["sellers"][sid]["name"] == "Ароматы Востока Грозный", "чужой не правит")

# Владелец добавляет компанию сам — публикуется сразу
msg(OWNER, "/start add")
msg(OWNER, "Автопрокат «Дорога»")
press(OWNER, "cat:auto")
msg(OWNER, "Грозный")
for _ in range(3):
    press(OWNER, "skip")    # описание, адрес, телефон
press(OWNER, "skip")        # telegram
press(OWNER, "skip")        # instagram
press(OWNER, "skip")        # логотип
press(OWNER, "skip")        # обложка
check(len(catalog()["sellers"]) == 2, "компания владельца сразу в каталоге")

# Каталог читается приложением (тот же разбор, что в Android/iOS: обязательные поля)
for s_ in catalog()["sellers"]:
    check(all(k in s_ for k in ("id", "name", "city", "categories")), "поля продавца")
for p_ in catalog()["products"]:
    check(any(x["id"] == p_["seller"] for x in catalog()["sellers"]), "товар с продавцом")

# Владелец по Telegram-имени (новая база)
storage2 = FakeStorage()
index.OWNER_USERNAME = "boss"
b2 = index.Bot(storage2, FakeTg(), TOKEN)
b2.handle({"message": {"from": {"id": 77, "username": "Boss"}, "chat": {"id": 77, "type": "private"}, "text": "/start"}})
b2.save()
check(json.loads(storage2.files["data/db.json"])["owner"] == 77, "владелец по имени")
index.OWNER_USERNAME = ""

# Места для намаза: заявка от человека → владелец проверяет → на карте
msg(FRIEND, "/start place")
press(FRIEND, "pk:room")
msg(FRIEND, "Молельная комната в ТЦ Авиапарк")
msg(FRIEND, "просто текст")  # нужна геопозиция
check(any("геопозиция" in (t or "") for t in tg.texts(FRIEND)[-1:]), "место: просит геопозицию")
msg(FRIEND, location={"latitude": 55.7905, "longitude": 37.5310})
msg(FRIEND, "3 этаж, рядом с фуд-кортом")
msg(FRIEND, "10:00–22:00")
press(FRIEND, "pw:both")
press(FRIEND, "pu:yes")
press(FRIEND, "pskp")   # фото
press(FRIEND, "pskp")   # заметка
pl = [x for x in db()["places"].values()]
check(len(pl) == 1 and pl[0]["status"] == "pending" and pl[0]["lat"] == 55.7905 and pl[0]["wudu"] is True, "место сохранено")
check(any("Новое место для намаза" in (t or "") for t in tg.texts(OWNER)), "владельцу пришла заявка на место")
check(any(m_ == "sendLocation" and p_.get("chat_id") == OWNER for m_, p_ in tg.sent), "владельцу пришла точка")
press(FRIEND, f"pm:ok:{pl[0]['id']}")  # не админ
check(db()["places"][pl[0]["id"]]["status"] == "pending", "чужой не одобряет место")
press(OWNER, f"pm:ok:{pl[0]['id']}")
places = json.loads(storage.files["places.json"])
check(len(places["places"]) == 1 and places["places"][0]["who"] == "both" and places["places"][0]["hours"] == "10:00–22:00", "место в places.json")
check(any("добавлено на карту" in (t or "") for t in tg.texts(FRIEND)), "автору сообщили")
press(OWNER, f"pm:hide:{pl[0]['id']}")
check(json.loads(storage.files["places.json"])["places"] == [], "место скрыто")

# Пожертвования: ссылку задаёт владелец
msg(OWNER, "/donate http://bad")
check(db()["donate_url"] == "", "плохая ссылка отклонена")
msg(OWNER, "/donate https://pay.cloudtips.ru/p/abc123")
check(catalog()["donate_url"] == "https://pay.cloudtips.ru/p/abc123", "ссылка в каталоге")
check(json.loads(storage.files["places.json"])["donate_url"] == "https://pay.cloudtips.ru/p/abc123", "ссылка в places.json")
msg(SELLER, "/donate https://evil.example.com")
check(db()["donate_url"] == "https://pay.cloudtips.ru/p/abc123", "не владелец не меняет ссылку")

# Импорт из OpenStreetMap
import import_osm  # noqa: E402
els = [
    {"type": "node", "id": 1, "lat": 55.78, "lon": 37.63, "tags": {"name": "Московская Соборная мечеть", "addr:street": "Выползов переулок", "addr:housenumber": "7", "addr:city": "Москва"}},
    {"type": "way", "id": 2, "center": {"lat": 55.79, "lon": 37.53}, "tags": {"name": "Молельная комната ТЦ"}},
    {"type": "node", "id": 3, "lat": 43.3, "lon": 45.7, "tags": {"building": "mosque"}},
]
items = [import_osm.convert(e) for e in els]
check(items[0]["kind"] == "mosque" and "Выползов" in items[0]["address"], "osm: мечеть и адрес")
check(items[1]["kind"] == "room" and items[1]["lat"] == 55.79, "osm: молельная комната из way")
check(items[2]["name"] == "Мечеть", "osm: имя по умолчанию")
d = {"places": {}}
check(import_osm.merge(d, items) == (3, 0, 0), "osm: добавлены")
d["places"]["osmn1"]["status"] = "hidden"
import_osm.merge(d, items)
check(d["places"]["osmn1"]["status"] == "hidden", "osm: скрытое остаётся скрытым")
d["osm_removed"] = ["osmw2"]
del d["places"]["osmw2"]
import_osm.merge(d, items)
check("osmw2" not in d["places"], "osm: удалённое не возвращается")
check(import_osm.merge(d, items[:1])[2] == 1, "osm: исчезнувшее из OSM убирается")

# Отзывы: приложение кладёт файл в закрытый бакет, бот присылает его владельцу, одобренные — в reviews.json
os.environ["S3_KEY_ID"], os.environ["S3_SECRET"] = "KEYID", "SECRET"
_d = db()
_d["places"]["pl_test"] = {"id": "pl_test", "kind": "room", "name": "Намазхана", "lat": 55.7, "lon": 37.6, "status": "approved", "created": 1}
storage.files["data/db.json"] = json.dumps(_d).encode()
_place_id = "pl_test"
storage.files["reviews/pending/a1.json"] = json.dumps({"target": f"seller:{sid}", "rating": 5, "text": "Отличные  духи,\nспасибо", "name": "Амина", "device": "dev1"}).encode()
storage.files["reviews/pending/a2.json"] = json.dumps({"target": f"place:{_place_id}", "rating": 4, "text": "", "device": "dev1"}).encode()
storage.files["reviews/pending/bad.json"] = json.dumps({"target": "seller:nope", "rating": 5}).encode()
storage.files["reviews/pending/bad2.json"] = b"not json"
bot = index.Bot(storage, tg, TOKEN)
res = bot.cron()
bot.save()
check(res["new_reviews"] == 2, "отзывы: забраны 2 из 4")
check(not [k for k in storage.files if k.startswith("reviews/pending/")], "отзывы: файлы удалены из очереди")
check(len(db()["reviews_pending"]) == 2, "отзывы: ждут проверки")
check(any("Новый отзыв" in (t or "") and "Отличные духи, спасибо" in t for t in tg.texts(OWNER)), "отзывы: владелец получил карточку")
rv = tg.last_buttons(OWNER)
check(rv and rv[0].startswith("rv:ok:"), "отзывы: кнопки модерации")
press(SELLER, rv[0].replace("rv:ok", "rv:ok"))
check(len(db()["reviews_pending"]) == 2, "отзывы: не-админ не одобряет")
_ids = list(db()["reviews_pending"].keys())
_seller_rid = next(k for k, v in db()["reviews_pending"].items() if v["target"].startswith("seller:"))
_place_rid = next(k for k in _ids if k != _seller_rid)
press(OWNER, f"rv:ok:{_seller_rid}")
press(OWNER, f"rv:no:{_place_rid}")
rj = json.loads(storage.files["reviews.json"])
check(rj["reviews"][f"seller:{sid}"]["avg"] == 5 and rj["reviews"][f"seller:{sid}"]["n"] == 1, "отзывы: рейтинг компании")
check(f"place:{_place_id}" not in rj["reviews"], "отзывы: отклонённый не показан")
check(rj["reviews"][f"seller:{sid}"]["items"][0]["n"] == "Амина" and "device" not in json.dumps(rj), "отзывы: имя есть, id устройства нет")
check(rj["upload"]["fields"]["x-amz-algorithm"] == "AWS4-HMAC-SHA256" and rj["upload"]["url"].endswith(index.DATA_BUCKET), "отзывы: форма загрузки в закрытый бакет")
_pol = json.loads(__import__("base64").b64decode(rj["upload"]["fields"]["policy"]))
check(["starts-with", "$key", "reviews/pending/"] in _pol["conditions"], "отзывы: форма только для очереди отзывов")
press(OWNER, f"rv:ok:{_seller_rid}")
check("Этот отзыв уже проверен." in tg.texts(OWNER), "отзывы: повторное нажатие")
for i in range(7):
    storage.files[f"reviews/pending/s{i}.json"] = json.dumps({"target": f"seller:{sid}", "rating": 1, "device": "spam"}).encode()
bot = index.Bot(storage, tg, TOKEN)
check(bot.cron()["new_reviews"] == 5, "отзывы: не больше 5 в сутки с одного телефона")
bot.save()

# Закрытая база: перенос из публичного бакета
pub, priv = FakeStorage(), FakeStorage()
pub.files["data/db.json"] = json.dumps({"owner": 7}).encode()
b2 = index.Bot(pub, FakeTg(), TOKEN, data=priv)
check(b2.db["owner"] == 7 and "data/db.json" in priv.files and "data/db.json" not in pub.files, "база переносится в закрытый бакет")

# Продвижение: заявка → «Я оплатил» → владелец подтверждает → promo_until в каталоге
msg(OWNER, "/prices 150 400")
check(db()["promo_prices"] == {"7": 150, "30": 400}, "продвижение: владелец меняет тарифы")
msg(OWNER, "/payurl https://pay.cloudtips.ru/p/abc")
press(SELLER, f"promo:{sid}")
check(f"pt:{sid}:7" in tg.last_buttons(SELLER), "продвижение: тарифы в меню")
press(SELLER, f"pt:{sid}:7")
_req = next(iter(db()["promo_requests"].values()))
check(_req["price"] == 150 and "pay.cloudtips.ru" in tg.texts(SELLER)[-1], "продвижение: заявка и ссылка на оплату")
press(FRIEND, f"pc:{_req['id']}")
check(db()["promo_requests"][_req["id"]]["status"] == "new", "продвижение: чужой не подтверждает оплату")
press(SELLER, f"pc:{_req['id']}")
check(any("Заявка на продвижение" in (t or "") for t in tg.texts(OWNER)), "продвижение: владелец получил заявку")
press(SELLER, f"pr:ok:{_req['id']}")
check(db()["promo_requests"][_req["id"]]["status"] == "claimed", "продвижение: продавец сам не одобряет")
press(OWNER, f"pr:ok:{_req['id']}")
_s = next(x for x in catalog()["sellers"] if x["id"] == sid)
check(_s.get("promo_until", 0) > __import__("time").time() + 6 * 86400, "продвижение: включено на 7 дней")
press(OWNER, f"pr:ok:{_req['id']}")
check("Эта заявка уже обработана." in tg.texts(OWNER), "продвижение: повторное нажатие")

# Условная запись: если базу изменили параллельно — Conflict
class TaggedStorage(FakeStorage):
    def __init__(self):
        super().__init__()
        self.tags = {}

    def get_tagged(self, key):
        return self.files.get(key), self.tags.get(key)

    def put(self, key, data, content_type, cache="no-cache", encoding=None, if_match=None, if_none_match=False):
        if if_match and self.tags.get(key) != if_match:
            raise index.Conflict(key)
        if if_none_match and key in self.files:
            raise index.Conflict(key)
        super().put(key, data, content_type, cache, encoding)
        self.tags[key] = str(len(self.tags) + 1) + key

_pub, _priv = FakeStorage(), TaggedStorage()
_priv.put("data/db.json", json.dumps({"owner": 1}).encode(), "application/json")
_b1 = index.Bot(_pub, FakeTg(), TOKEN, data=_priv)
_b2 = index.Bot(_pub, FakeTg(), TOKEN, data=_priv)
_b1.db["x"] = 1; _b1.dirty = True; _b1.save()
_b2.db["y"] = 2; _b2.dirty = True
try:
    _b2.save()
    check(False, "условная запись: конфликт не обнаружен")
except index.Conflict:
    pass
check(json.loads(_priv.files["data/db.json"]).get("x") == 1, "условная запись: первая правка сохранена")

# Буфер Telegram: сообщения уходят только после сохранения; очередь из Яндекса отправляется ботом
_ftg = FakeTg()
_buf = index.BufferedTelegram(_ftg)
_pub2, _priv2 = FakeStorage(), TaggedStorage()
_priv2.put("data/db.json", json.dumps({"owner": 1, "admins": [1], "outbox": [["sendMessage", {"chat_id": 1, "text": "из очереди"}]]}).encode(), "application/json")
_b3 = index.Bot(_pub2, _buf, TOKEN, data=_priv2)
_b3.send(1, "привет")
check(not _ftg.texts(1), "буфер: до сохранения ничего не отправлено")
_b3.save()
check(_ftg.texts(1) == ["привет", "из очереди"] and "outbox" not in json.loads(_priv2.files["data/db.json"]), "буфер: отправлено после сохранения, очередь пуста")
_q = index.OutboxTelegram()
_b4 = index.Bot(_pub2, _q, TOKEN, data=_priv2)
_b4.send(1, "в очередь")
_b4.save()
check(json.loads(_priv2.files["data/db.json"])["outbox"][0][1]["text"] == "в очередь", "очередь: сообщение из функции Яндекса сохранено")

# Удаление
press(SELLER, f"del2:{sid}")
check(sid not in db()["sellers"] and len(catalog()["sellers"]) == 1, "удаление")

# Подпись SigV4 — официальный пример AWS (GET Object с Range)
from datetime import datetime as _dt, timezone as _tz
_h = index.sigv4("GET", "examplebucket.s3.amazonaws.com", "/test.txt", {"Range": "bytes=0-9"}, b"",
                 "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", _dt(2013, 5, 24, tzinfo=_tz.utc))
check(_h["authorization"].endswith("Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"), "подпись SigV4")

# SigV4 со строкой запроса — официальный пример AWS (GET Bucket / ListObjects)
_h = index.sigv4("GET", "examplebucket.s3.amazonaws.com", "/", {}, b"",
                 "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", _dt(2013, 5, 24, tzinfo=_tz.utc),
                 "max-keys=2&prefix=J")
check(_h["authorization"].endswith("Signature=34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7"), "подпись SigV4 со строкой запроса")

# Вебхук без секрета отклоняется
r = index.handler({"headers": {}, "body": "{}"}, None)
check(r["statusCode"] == 403, "секрет вебхука")

print("Бот магазина: всё верно" if failures == 0 else f"Ошибок: {failures}")
sys.exit(1 if failures else 0)
