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

    def put(self, key, data, content_type, cache="no-cache"):
        assert isinstance(data, bytes)
        self.files[key] = data


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
                return [b["callback_data"] for row in p["reply_markup"]["inline_keyboard"] for b in row]
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

# Удаление
press(SELLER, f"del2:{sid}")
check(sid not in db()["sellers"] and len(catalog()["sellers"]) == 1, "удаление")

# Подпись SigV4 — официальный пример AWS (GET Object с Range)
from datetime import datetime as _dt, timezone as _tz
_h = index.sigv4("GET", "examplebucket.s3.amazonaws.com", "/test.txt", {"Range": "bytes=0-9"}, b"",
                 "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", _dt(2013, 5, 24, tzinfo=_tz.utc))
check(_h["authorization"].endswith("Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"), "подпись SigV4")

# Вебхук без секрета отклоняется
r = index.handler({"headers": {}, "body": "{}"}, None)
check(r["statusCode"] == 403, "секрет вебхука")

print("Бот магазина: всё верно" if failures == 0 else f"Ошибок: {failures}")
sys.exit(1 if failures else 0)
