"""Проверка API мини-приложения без сети. Запуск: python3 shop-bot/test_miniapi.py"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time
from unittest.mock import patch
from urllib.parse import urlencode

sys.path.insert(0, os.path.dirname(__file__))
os.environ["BOT_TOKEN"] = "123:TEST"
os.environ.pop("LISTING_FEE_ENABLED", None)
import index  # noqa: E402
import miniapi  # noqa: E402

TOKEN = "123:TEST"
failures = 0


def check(cond, what):
    global failures
    if not cond:
        failures += 1
        print("ОШИБКА:", what)


class Store:
    def __init__(self):
        self.files, self.tags, self.n = {}, {}, 0

    def get(self, key):
        return self.files.get(key)

    def get_tagged(self, key):
        return self.files.get(key), self.tags.get(key)

    def put(self, key, data, content_type, cache="no-cache", encoding=None, if_match=None, if_none_match=False):
        if if_match and self.tags.get(key) != if_match:
            raise index.Conflict(key)
        if if_none_match and key in self.files:
            raise index.Conflict(key)
        self.n += 1
        self.files[key], self.tags[key] = data, f"e{self.n}"

    def delete(self, key):
        self.files.pop(key, None)

    def list(self, prefix, limit=50):
        return [k for k in self.files if k.startswith(prefix)][:limit]


pub, priv = Store(), Store()
priv.put("data/db.json", json.dumps({"owner": 1, "admins": [1], "bot_username": "ZikrBot", "sellers": {}}).encode(), "application/json")


def init_data(uid, name="Ali", age=0):
    fields = {"auth_date": str(int(time.time()) - age), "query_id": "AAA", "user": json.dumps({"id": uid, "first_name": name, "username": f"u{uid}"})}
    check_str = "\n".join(f"{k}={v}" for k, v in sorted(fields.items()))
    secret = hmac.new(b"WebAppData", TOKEN.encode(), hashlib.sha256).digest()
    fields["hash"] = hmac.new(secret, check_str.encode(), hashlib.sha256).hexdigest()
    return urlencode(fields)


def call(uid, action, **kw):
    ev = {"httpMethod": "POST", "body": json.dumps(dict(kw, initData=init_data(uid), action=action))}
    old = index.make_storage
    index.make_storage = lambda token="", bucket=None: priv if bucket == index.DATA_BUCKET else pub
    try:
        r = miniapi.handler(ev, None)
    finally:
        index.make_storage = old
    return r["statusCode"], json.loads(r["body"])


def db():
    return json.loads(priv.files["data/db.json"])


def public_companies():
    return {s["id"]: s for s in json.loads(pub.files.get("catalog.json", b'{"sellers":[]}'))["sellers"]}


# Подпись
check(miniapi.check_init_data(init_data(5), TOKEN)["id"] == 5, "подпись: верная принимается")
check(miniapi.check_init_data(init_data(5).replace("Ali", "Bob"), TOKEN) is None, "подпись: подделка отклоняется")
check(miniapi.check_init_data(init_data(5, age=30 * 86400), TOKEN) is None, "подпись: старые данные отклоняются")
code, r = call(5, "me")
check(code == 200 and r["sellers"] == [] and not r["admin"], "me: новый продавец")
ev = {"httpMethod": "POST", "body": json.dumps({"initData": "hash=bad", "action": "me"})}
check(miniapi.handler(ev, None)["statusCode"] == 401, "без подписи — 401")
check(miniapi.handler({"httpMethod": "OPTIONS"}, None)["headers"]["Access-Control-Allow-Origin"] == "*", "CORS")

# Компания
code, r = call(5, "seller_save", fields={"name": "Халяль Маркет", "category": "food", "city": "Грозный", "phone": "8 928 123 45 67", "whatsapp": True, "telegram": "@halal_m"})
check(code == 200 and r["seller"]["status"] == "pending" and r["seller"]["phone"] == "+79281234567" and r["seller"]["whatsapp"], "компания создана на проверку")
sid = r["seller"]["id"]
check(any(m[1].get("chat_id") == 1 and "Новая компания" in m[1].get("text", "") for m in db().get("outbox", [])), "владельцу — уведомление в очередь")
code, r = call(5, "seller_save", fields={"name": "X", "city": "Грозный"})
check(code == 400 and "название" in r["error"], "проверка полей")
code, r = call(6, "seller_save", sid=sid, fields={"name": "Чужая", "city": "Москва"})
check(code == 404, "чужую компанию не изменить")
code, r = call(5, "seller_save", sid=sid, fields={"name": "Халяль Маркет", "city": "Грозный", "logo": "https://evil.example/x.jpg"})
check(code == 400, "картинки только из нашего хранилища")

# Фото
jpeg = base64.b64encode(b"\xff\xd8\xff\xe0" + b"0" * 100).decode()
code, r = call(5, "photo", sid=sid, data="data:image/jpeg;base64," + jpeg)
check(code == 200 and r["url"].startswith(index.PUBLIC_BASE + f"img/{sid}/"), "фото загружено")
logo = r["url"]
code, r = call(5, "photo", sid=sid, data=base64.b64encode(b"GIF89a").decode())
check(code == 400, "не фото — отклонено")

# Товар
code, r = call(5, "product_save", sid=sid, fields={"title": "Финики Аджва", "price": "1 200", "unit": "кг", "images": [logo]})
check(code == 200 and r["seller"]["products"][0]["price"] == 1200, "товар добавлен")
pid = r["seller"]["products"][0]["id"]
code, r = call(5, "product_save", sid=sid, pid=pid, fields={"title": "Финики Аджва", "price": "1100", "old_price": "1300"})
check(r["seller"]["products"][0]["old_price"] == 1300, "товар изменён")

# Модерация владельцем из кабинета
code, r = call(5, "mod_seller", sid=sid, do="ok")
check(code == 403, "продавец не модерирует")
code, r = call(1, "me")
check(r.get("listing_fee_enabled") is False and sid in {s["id"] for s in r["moderation"]["pending"]},
      "бесплатное размещение: новая заявка есть в очереди")
code, r = call(1, "mod_seller", sid=sid, do="partner")
call(5, "seller_visibility", sid=sid)
check(db()["sellers"][sid]["status"] == "pending" and sid not in public_companies(),
      "партнёрская метка и кнопка продавца не обходят проверку")
code, r = call(1, "mod_seller", sid=sid, do="ok")
check(code == 200 and db()["sellers"][sid]["status"] == "approved" and sid in public_companies(),
      "после одобрения компания сразу попадает в каталог")
check(not r["moderation"]["pending"], "одобренная заявка исчезает из очереди")
code, r = call(5, "promo_request", sid=sid, days=7)
check(code == 400, "старое приложение не может создать ручную оплату")

# Основной администратор создаёт автопрокат из мини-приложения.
code, r = call(1, "seller_save", fields={"name": "Автопрокат", "category": "auto", "city": "Грозный"})
rental = r["seller"]["id"]
check(code == 200 and r["seller"]["status"] == "approved" and rental in public_companies(),
      "своя компания администратора публикуется сразу")
for _ in range(2):
    call(1, "mod_seller", sid=rental, do="partner", partner=True)
check(public_companies()[rental]["partner"], "метка партнёра публикуется в карточке")
code, r = call(6, "seller_visibility", sid=rental)
check(code == 404 and rental in public_companies(), "посторонний не может скрыть компанию")
call(1, "mod_seller", sid=rental, do="hide")
check(rental not in public_companies(), "автопрокат скрывается")
call(1, "mod_seller", sid=rental, do="ok")
check(rental in public_companies() and public_companies()[rental]["partner"], "автопрокат снова виден с прежней меткой")
for visible in (False, True):
    for _ in range(2):
        call(1, "seller_visibility", sid=rental, visible=visible)
    check((rental in public_companies()) == visible, "повтор запроса не переключает видимость обратно")

# Снимок старой базы: в ожидание оплаты попадали только уже одобренные карточки.
legacy = db()
for status in ("awaiting_payment", "hidden", "pending", "rejected"):
    legacy["sellers"][status] = dict(legacy["sellers"][rental], id=status, status=status, listing_fee="due")
priv.put(index.DB_KEY, json.dumps(legacy).encode(), "application/json")
with patch.object(pub, "put", side_effect=OSError("interrupted catalog write")):
    check(call(1, "me")[0] == 500, "сбой записи публичного каталога замечен")
check(db()["sellers"]["awaiting_payment"]["status"] == "approved" and "awaiting_payment" not in public_companies(),
      "при прерывании приватная база уже сохранена, а в каталоге ещё старая версия")
for _ in range(2):
    code, r = call(1, "me")
    check(code == 200 and db()["sellers"]["awaiting_payment"]["status"] == "approved"
          and "awaiting_payment" in public_companies(), "старая одобренная карточка восстановлена в каталоге")
    check(set(public_companies()).isdisjoint(("hidden", "pending", "rejected")),
          "скрытые и непрошедшие проверку карточки остаются закрытыми")
    check({s["id"] for s in r["moderation"]["pending"]} == {"pending"}, "старая заявка остаётся в очереди")
    check(not db().get("catalog_publish_pending"), "после успешной публикации повторная отправка не требуется")
for status in ("pending", "rejected"):
    check(call(1, "mod_seller", sid=status, do="hide")[0] == 409, "скрыть ещё не опубликованную карточку нельзя")
    check(call(1, "seller_visibility", sid=status, visible=True)[0] == 409, "до одобрения показать нельзя")
    check(db()["sellers"][status]["status"] == status and status not in public_companies(),
          "старые кнопки скрытия и показа не публикуют карточку до одобрения")

# Настройки владельца
code, r = call(5, "settings", prices={"7": 1, "30": 2})
check(code == 403, "настройки — только владелец")
code, r = call(1, "settings", prices={"7": 150, "30": 450}, pay_url="https://pay.cloudtips.ru/p/x")
check(r["prices"] == {"7": 150, "30": 450}, "владелец меняет тарифы")

# Удаление
code, r = call(5, "product_delete", sid=sid, pid=pid)
check(r["seller"]["products"] == [], "товар удалён")
code, r = call(5, "seller_delete", sid=sid)
check(sid not in db()["sellers"], "компания удалена")

print("API кабинета: всё верно" if failures == 0 else f"Ошибок: {failures}")
sys.exit(1 if failures else 0)
