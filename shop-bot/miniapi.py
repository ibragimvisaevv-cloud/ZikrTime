"""
API мини-приложения (кабинет продавца в Telegram). Работает облачной функцией Yandex Cloud —
её адрес открывается из России без VPN. Telegram оттуда недоступен, поэтому уведомления
кладутся в очередь в базе, а отправляет их бот на Vercel.

Запрос: POST, тело — JSON (Content-Type: text/plain, чтобы браузер не делал лишний запрос CORS):
  {"initData": "<Telegram.WebApp.initData>", "action": "...", ...}
Пользователь определяется только по подписанным Telegram данным initData.
"""
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import sys
import time
from urllib.parse import parse_qsl

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import index  # noqa: E402
from index import CAT_BY_ID, CATEGORIES, MAX_PHOTOS, MAX_PRODUCTS, MAX_SELLERS_PER_USER, PUBLIC_BASE, Conflict, btn, esc, kb, new_id  # noqa: E402

MAX_IMAGE_BYTES = 2_500_000
CORS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Max-Age": "86400",
}


class ApiError(Exception):
    def __init__(self, message, code=400):
        super().__init__(message)
        self.code = code


def check_init_data(init_data, token, max_age=7 * 86400, now=None):
    """Проверка подписи Telegram (WebApp initData). Возвращает пользователя или None."""
    if not init_data or not token:
        return None
    pairs = dict(parse_qsl(init_data, keep_blank_values=True))
    received = pairs.pop("hash", "")
    check = "\n".join(f"{k}={v}" for k, v in sorted(pairs.items()))
    secret = hmac.new(b"WebAppData", token.encode(), hashlib.sha256).digest()
    calc = hmac.new(secret, check.encode(), hashlib.sha256).hexdigest()
    if not received or not hmac.compare_digest(calc, received):
        return None
    try:
        if (now or time.time()) - int(pairs.get("auth_date", "0")) > max_age:
            return None
        user = json.loads(pairs.get("user", "{}"))
    except (ValueError, TypeError):
        return None
    return user if isinstance(user.get("id"), int) else None


# ---------------------------------------------------------------- вид данных для мини-приложения

def seller_out(bot, s):
    reqs = [r for r in bot.db.get("promo_requests", {}).values() if r["sid"] == s["id"]]
    last = max(reqs, key=lambda r: r["created"]) if reqs else None
    return {
        "id": s["id"], "name": s["name"], "category": s.get("category", "other"), "city": s.get("city", ""),
        "description": s.get("description", ""), "address": s.get("address", ""), "phone": s.get("phone", ""),
        "whatsapp": bool(s.get("whatsapp")), "telegram": s.get("telegram", ""), "instagram": s.get("instagram", ""),
        "logo": s.get("logo", ""), "cover": s.get("cover", ""), "status": s["status"], "partner": bool(s.get("partner")),
        "promo_until": int(s["promo_until"]) if bot.promo_active(s) else 0,
        "promo_request": {k: last[k] for k in ("id", "days", "price", "status")} if last and last["status"] in ("new", "claimed") else None,
        "products": [
            {"id": p["id"], "title": p["title"], "price": p.get("price"), "old_price": p.get("old_price"),
             "unit": p.get("unit", ""), "description": p.get("description", ""), "images": p.get("images", [])}
            for p in sorted(s["products"].values(), key=lambda x: x["created"])
        ],
        "created": int(s["created"]),
    }


def me(bot, uid, user):
    u = bot.user(uid)
    if user.get("username") and u.get("username") != user["username"]:
        u["username"] = user["username"]
        bot.dirty = True
    admin = bot.is_admin(uid)
    out = {
        "user": {"id": uid, "first_name": user.get("first_name", ""), "username": user.get("username", "")},
        "admin": admin, "owner": uid == bot.db.get("owner"),
        "sellers": [seller_out(bot, s) for s in sorted(bot.my_sellers(uid), key=lambda x: x["created"])],
        "categories": [{"id": c["id"], "name": c["names"]["ru"]} for c in CATEGORIES],
        "prices": bot.promo_prices(), "pay_url": bot.promo_pay_url(),
        "limits": {"sellers": MAX_SELLERS_PER_USER, "products": MAX_PRODUCTS, "photos": MAX_PHOTOS},
        "bot": bot.db.get("bot_username", ""),
    }
    if admin:
        out["moderation"] = moderation(bot)
    return out


def moderation(bot):
    sellers = bot.db["sellers"].values()
    return {
        "pending": [seller_out(bot, s) for s in sellers if s["status"] == "pending"],
        "all": [{"id": s["id"], "name": s["name"], "status": s["status"], "partner": bool(s.get("partner")),
                 "promo_until": int(s["promo_until"]) if bot.promo_active(s) else 0, "products": len(s["products"])}
                for s in sorted(sellers, key=lambda x: -x["created"])],
        "promo": [dict(r, seller=(bot.db["sellers"].get(r["sid"]) or {}).get("name", "—"),
                       username=bot.user(r["uid"]).get("username", ""))
                  for r in sorted(bot.db.get("promo_requests", {}).values(), key=lambda x: x["created"]) if r["status"] == "claimed"],
        "reviews": [dict(r, target_name=bot.review_target(r["target"]) or "—")
                    for r in sorted(bot.db.get("reviews_pending", {}).values(), key=lambda x: x["d"])],
    }


# ---------------------------------------------------------------- проверки ввода

def text(v, limit, required=False, what="поле"):
    v = " ".join(str(v or "").split()) if limit < 200 else str(v or "").strip()
    v = v[:limit]
    if required and len(v) < 2:
        raise ApiError(f"Заполните {what}")
    return v


def image_path(v):
    v = str(v or "").strip()
    if v and not v.startswith(PUBLIC_BASE + "img/"):
        raise ApiError("Неверная картинка")
    return v


def seller_fields(f):
    out = {
        "name": text(f.get("name"), 60, True, "название"),
        "category": f.get("category") if f.get("category") in CAT_BY_ID else "other",
        "city": text(f.get("city"), 60, True, "город"),
        "description": text(f.get("description"), 600),
        "address": text(f.get("address"), 150),
        "logo": image_path(f.get("logo")),
        "cover": image_path(f.get("cover")),
    }
    phone = str(f.get("phone") or "").strip()
    out["phone"] = index.clean_phone(phone) if phone else ""
    if phone and not out["phone"]:
        raise ApiError("Телефон: например +7 928 123-45-67")
    out["whatsapp"] = out["phone"] if f.get("whatsapp") and out["phone"] else ""
    for k in ("telegram", "instagram"):
        raw = str(f.get(k) or "").strip()
        out[k] = index.clean_handle(raw) if raw else ""
        if raw and not out[k]:
            raise ApiError(("Telegram" if k == "telegram" else "Instagram") + ": имя вида @shop")
    return out


def product_fields(f):
    def price(v):
        if v in (None, ""):
            return None
        p = index.parse_price(str(v))
        if p is None:
            raise ApiError("Цена: только число")
        return p
    images = [image_path(x) for x in (f.get("images") or [])][:MAX_PHOTOS]
    return {
        "title": text(f.get("title"), 80, True, "название товара"),
        "price": price(f.get("price")),
        "old_price": price(f.get("old_price")),
        "unit": text(f.get("unit"), 20),
        "description": text(f.get("description"), 600),
        "images": [x for x in images if x],
    }


def own_seller(bot, uid, sid):
    s = bot.db["sellers"].get(str(sid or ""))
    if not bot.can_edit(uid, s):
        raise ApiError("Компания не найдена", 404)
    return s


def admin_only(bot, uid):
    if not bot.is_admin(uid):
        raise ApiError("Нет доступа", 403)


# ---------------------------------------------------------------- действия

def act_seller_save(bot, uid, req):
    f = seller_fields(req.get("fields") or {})
    sid = req.get("sid")
    if sid:
        s = own_seller(bot, uid, sid)
        s.update(f)
        bot.after_seller_edit(uid, s, "данные компании (кабинет)")
        return {"seller": seller_out(bot, s)}
    if len(bot.my_sellers(uid)) >= MAX_SELLERS_PER_USER and not bot.is_admin(uid):
        raise ApiError(f"Не больше {MAX_SELLERS_PER_USER} компаний на одного продавца")
    sid = new_id("s")
    admin = bot.is_admin(uid)
    s = dict(f, id=sid, lat=None, lon=None, status="approved" if admin else "pending", partner=False,
             owners=[uid], created=time.time(), products={})
    bot.db["sellers"][sid] = s
    bot.changed(s)
    if not admin:
        bot.notify_admins("🆕 <b>Новая компания на проверку</b> (из кабинета)\n\n" + bot.seller_text(s), bot.moderation_kb(s))
    return {"seller": seller_out(bot, s)}


def act_seller_delete(bot, uid, req):
    s = own_seller(bot, uid, req.get("sid"))
    del bot.db["sellers"][s["id"]]
    bot.dirty = bot.catalog_dirty = True
    if not bot.is_admin(uid):
        bot.notify_admins(f"🗑 Продавец удалил компанию «{esc(s['name'])}».")
    return {"ok": True}


def act_seller_visibility(bot, uid, req):
    s = own_seller(bot, uid, req.get("sid"))
    # Как в боте: продавец сам скрывает и снова показывает уже одобренную компанию
    if s["status"] in ("approved", "hidden"):
        s["status"] = "hidden" if s["status"] == "approved" else "approved"
        bot.dirty = bot.catalog_dirty = True
    return {"seller": seller_out(bot, s)}


def act_product_save(bot, uid, req):
    s = own_seller(bot, uid, req.get("sid"))
    f = product_fields(req.get("fields") or {})
    pid = req.get("pid")
    if pid:
        p = s["products"].get(pid)
        if not p:
            raise ApiError("Товар не найден", 404)
        p.update(f)
        what = "товар: " + f["title"]
    else:
        if len(s["products"]) >= MAX_PRODUCTS:
            raise ApiError(f"Не больше {MAX_PRODUCTS} товаров")
        pid = new_id("p")
        s["products"][pid] = dict(f, id=pid, created=time.time())
        what = "новый товар: " + f["title"]
    bot.after_seller_edit(uid, s, what)
    return {"seller": seller_out(bot, s)}


def act_product_delete(bot, uid, req):
    s = own_seller(bot, uid, req.get("sid"))
    if s["products"].pop(str(req.get("pid") or ""), None) is None:
        raise ApiError("Товар не найден", 404)
    bot.changed(s)
    return {"seller": seller_out(bot, s)}


def act_photo(bot, uid, req):
    """Фото с телефона (уже уменьшенное в мини-приложении): base64 JPEG/PNG → публичная картинка."""
    s = own_seller(bot, uid, req.get("sid")) if req.get("sid") else None
    if s is None and len(bot.my_sellers(uid)) >= MAX_SELLERS_PER_USER and not bot.is_admin(uid):
        raise ApiError("Сначала сохраните компанию")
    try:
        data = base64.b64decode(str(req.get("data") or "").split(",")[-1], validate=True)
    except ValueError:
        raise ApiError("Не удалось прочитать фото")
    if not data or len(data) > MAX_IMAGE_BYTES:
        raise ApiError("Фото слишком большое")
    if data[:3] == b"\xff\xd8\xff":
        ext, ctype = "jpg", "image/jpeg"
    elif data[:8] == b"\x89PNG\r\n\x1a\n":
        ext, ctype = "png", "image/png"
    else:
        raise ApiError("Нужна фотография (JPEG или PNG)")
    folder = s["id"] if s else f"u{uid}"
    key = f"img/{folder}/{secrets.token_hex(6)}.{ext}"
    bot.st.put(key, data, ctype, cache="public, max-age=31536000, immutable")
    return {"url": PUBLIC_BASE + key}


def act_promo_request(bot, uid, req):
    s = own_seller(bot, uid, req.get("sid"))
    if s["status"] != "approved":
        raise ApiError("Продвигать можно опубликованную компанию")
    r = bot.promo_create(uid, s["id"], req.get("days"))
    if not r:
        raise ApiError("Неизвестный тариф")
    return {"request": {k: r[k] for k in ("id", "days", "price", "status")}, "pay_url": bot.promo_pay_url()}


def act_promo_claim(bot, uid, req):
    r = bot.promo_claim(uid, str(req.get("rid") or ""))
    if not r:
        raise ApiError("Заявка не найдена", 404)
    return {"request": {k: r[k] for k in ("id", "days", "price", "status")}}


def act_mod_seller(bot, uid, req):
    admin_only(bot, uid)
    action = req.get("do")
    if action not in ("ok", "no", "hide", "partner"):
        raise ApiError("Неизвестное действие")
    bot.moderate(uid, action, str(req.get("sid") or ""))
    return {"moderation": moderation(bot)}


def act_mod_promo(bot, uid, req):
    admin_only(bot, uid)
    bot.promo_decide(uid, "ok" if req.get("do") == "ok" else "no", str(req.get("rid") or ""))
    return {"moderation": moderation(bot)}


def act_mod_review(bot, uid, req):
    admin_only(bot, uid)
    bot.moderate_review(uid, "ok" if req.get("do") == "ok" else "no", str(req.get("rid") or ""))
    return {"moderation": moderation(bot)}


def act_settings(bot, uid, req):
    if uid != bot.db.get("owner"):
        raise ApiError("Нет доступа", 403)
    p = req.get("prices") or {}
    try:
        prices = {"7": int(p.get("7")), "30": int(p.get("30"))}
    except (TypeError, ValueError):
        raise ApiError("Цены — числа")
    if min(prices.values()) < 1:
        raise ApiError("Цены — больше нуля")
    url = str(req.get("pay_url") or "").strip()
    if url and not re.match(r"^https://[\w.-]+\.[a-z]{2,}(/\S*)?$", url):
        raise ApiError("Ссылка для оплаты: https://…")
    bot.db["promo_prices"] = prices
    bot.db["promo_pay_url"] = url
    bot.dirty = True
    return {"prices": bot.promo_prices(), "pay_url": bot.promo_pay_url()}


ACTIONS = {
    "seller_save": act_seller_save, "seller_delete": act_seller_delete, "seller_visibility": act_seller_visibility,
    "product_save": act_product_save, "product_delete": act_product_delete, "photo": act_photo,
    "promo_request": act_promo_request, "promo_claim": act_promo_claim,
    "mod_seller": act_mod_seller, "mod_promo": act_mod_promo, "mod_review": act_mod_review, "settings": act_settings,
}

# Сообщения владельцу, которые бот отправляет «от себя» при модерации, в кабинете не нужны —
# их получит продавец (через очередь), а владельцу хватит обновлённого экрана.


def run(req, storage_factory, token):
    """Выполнить запрос мини-приложения. storage_factory(bucket) даёт хранилище (в тестах — поддельное)."""
    user = check_init_data(req.get("initData", ""), token)
    if not user:
        raise ApiError("Откройте кабинет из бота заново", 401)
    uid = user["id"]
    action = req.get("action", "me")
    for attempt in range(4):
        tg = index.OutboxTelegram()
        bot = index.Bot(storage_factory(None), tg, token, data=storage_factory(index.DATA_BUCKET))
        if action == "me":
            result = me(bot, uid, user)
        elif action in ACTIONS:
            result = ACTIONS[action](bot, uid, req)
            # Уведомления самому себе (владелец нажал в кабинете) не шлём в Telegram
            bot.db["outbox"] = [m for m in bot.db.get("outbox", []) if not (m[0] == "sendMessage" and m[1].get("chat_id") == uid and action.startswith("mod_"))]
            if action != "photo":
                result.setdefault("me", me(bot, uid, user))
        else:
            raise ApiError("Неизвестное действие")
        try:
            bot.save()
            return result
        except Conflict:
            time.sleep(0.25 * (attempt + 1))
    raise ApiError("Сервер занят, попробуйте ещё раз", 503)


def handler(event, context):
    """Точка входа облачной функции Yandex Cloud (HTTP)."""
    if (event.get("httpMethod") or "POST").upper() == "OPTIONS":
        return {"statusCode": 204, "headers": CORS, "body": ""}
    body = event.get("body") or "{}"
    if event.get("isBase64Encoded"):
        body = base64.b64decode(body).decode("utf-8")
    try:
        req = json.loads(body)
        token = (getattr(context, "token", None) or {}).get("access_token", "") if context else ""
        result = run(req, lambda bucket: index.make_storage(token, bucket), index.BOT_TOKEN)
        code = 200
    except ApiError as e:
        result, code = {"error": str(e)}, e.code
    except (ValueError, KeyError) as e:
        result, code = {"error": "Неверный запрос"}, 400
        print("bad request", repr(e))
    except Exception as e:
        print("api error", repr(e))
        result, code = {"error": "Ошибка сервера, попробуйте позже"}, 500
    return {"statusCode": code, "headers": dict(CORS, **{"Content-Type": "application/json; charset=utf-8"}),
            "body": json.dumps(result, ensure_ascii=False)}
