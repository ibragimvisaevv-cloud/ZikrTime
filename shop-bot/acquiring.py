"""Orders for the independent seller website. All money amounts are integer kopecks (RUB)."""
import datetime as dt
import hashlib
import hmac
import json
import os
import re
import secrets
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit

LISTING_AMOUNT = 20_000
ACTIVE = {"initializing", "waiting"}
FAILURES = {"CANCELED", "REJECTED", "DEADLINE_EXPIRED", "REVERSED"}
REFUNDS = {"REFUNDED", "PARTIAL_REFUNDED", "PARTIAL_REVERSED"}


class CheckoutError(Exception):
    def __init__(self, message, code=400):
        super().__init__(message)
        self.code = code


def signature(payload, password):
    parts = {k: str(v).lower() if isinstance(v, bool) else str(v)
             for k, v in payload.items() if k != "Token" and isinstance(v, (str, bool, int))}
    parts["Password"] = password
    return hashlib.sha256("".join(parts[k] for k in sorted(parts)).encode()).hexdigest()


def secure_url(value):
    url = urlsplit(value)
    return url.scheme == "https" and bool(url.hostname) and not (url.username or url.password)


class Bank:
    provider = "tbank"
    name = "Т-Банк"
    test = False
    hosts = {"securepay.tinkoff.ru", "securepay.tbank.ru", "securepayments.tinkoff.ru",
             "securepayments.tbank.ru", "pay.tbank.ru"}

    def __init__(self):
        self.key = os.getenv("TBANK_TERMINAL", "")
        self.password = os.getenv("TBANK_PASSWORD", "")
        self.portal = os.getenv("SELLER_PORTAL_URL", "").rstrip("/")
        self.offer = os.getenv("SELLER_OFFER_URL", "")
        self.taxation = os.getenv("TBANK_TAXATION", "")
        self.tax = os.getenv("TBANK_TAX", "")
        self.verifiable = bool(self.key and self.password)
        self.ready = bool(os.getenv("TBANK_ENABLED") == "true" and self.verifiable and self.taxation and self.tax
                          and secure_url(self.portal) and "?" not in self.portal
                          and secure_url(self.offer))

    def call(self, method, **params):
        payload = dict(params, TerminalKey=self.key)
        payload["Token"] = signature(payload, self.password)
        req = urllib.request.Request("https://securepay.tinkoff.ru/v2/" + method,
                                     data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                out = json.load(resp)
        except (ValueError, OSError, urllib.error.URLError):
            raise CheckoutError("Банк пока не ответил. Обновите статус заказа.", 503) from None
        # 335 explicitly means this terminal has no such OrderId. A timeout, bad
        # password or any other bank error is not evidence that an order is absent.
        if method == "CheckOrder" and out.get("Success") is False and str(out.get("ErrorCode")) == "335":
            return {"Payments": []}
        if out.get("Success") is not True or str(out.get("ErrorCode")) != "0":
            raise CheckoutError("Банк не подтвердил запрос. Обновите статус заказа.", 503)
        return out

    def initiate(self, order):
        name = description(order)
        amount = order["amount"]
        receipt = {"Email": order["email"], "Taxation": self.taxation,
                   "Items": [{"Name": name, "Price": amount, "Amount": amount, "Quantity": 1,
                              "Tax": self.tax, "PaymentObject": "service", "PaymentMethod": "full_payment"}]}
        return self.call("Init", OrderId=order["id"], Amount=amount, Description=name, PayType="O",
                         Receipt=receipt, NotificationURL=self.portal + "?event=tbank",
                         SuccessURL=self.portal, FailURL=self.portal, Language="ru",
                         RedirectDueDate=dt.datetime.fromtimestamp(order["expires"], dt.timezone.utc).isoformat(timespec="seconds"))


def description(order):
    return "Размещение компании в Зикр-тайм" if order["kind"] == "listing" else f"Продвижение в Зикр-тайм на {order['days']} дн."


def payment_provider():
    from robokassa import Robokassa
    if os.getenv("PAYMENT_PROVIDER", "robokassa") == "tbank":
        return Bank()
    return Robokassa()


def provider_for_order(order, current):
    provider = order.get("provider", "tbank")
    if provider == getattr(current, "provider", "tbank") and order.get("test", False) == getattr(current, "test", False):
        return current
    if provider == "tbank":
        return Bank()
    if provider == "robokassa":
        from robokassa import Robokassa
        return Robokassa(test=order.get("test", False))
    raise CheckoutError("Способ оплаты этого заказа пока недоступен.", 503)


def listing_fee_enabled():
    # Connecting a payment provider for promotion must not switch on listing charges.
    return os.environ.get("LISTING_FEE_ENABLED", "").lower() in ("1", "true")


def unpaid(seller):
    return listing_fee_enabled() and seller.get("listing_fee") == "due"


def approved_status(seller):
    return "awaiting_payment" if unpaid(seller) else "approved"


def new_listing(admin):
    fields = {"listing_fee": "due" if listing_fee_enabled() else "waived"}
    fields["status"] = approved_status(fields) if admin else "pending"
    return fields


def active_order(bot, sid):
    return next((o for o in bot.db.get("shop_orders", {}).values()
                 if o["sid"] == sid and (o["state"] in ACTIVE or o.get("bank_ambiguity"))), None)


def account_code(bot, uid):
    code = secrets.token_urlsafe(16)
    bot.user(uid)["portal_code"] = {"hash": hashlib.sha256(code.encode()).hexdigest(), "until": time.time() + 300}
    bot.dirty = True
    return f"{uid}.{code}"


def login(bot, code):
    uid, sep, secret = str(code).partition(".")
    user = bot.db["users"].get(uid, {})
    record = user.get("portal_code", {})
    digest = hashlib.sha256(secret.encode()).hexdigest()
    if not sep or not uid.isdecimal() or record.get("until", 0) < time.time() or not hmac.compare_digest(record.get("hash", ""), digest):
        raise CheckoutError("Код неверен или устарел. Запросите новый код.", 401)
    del user["portal_code"]
    session = secrets.token_urlsafe(32)
    user["portal_session"] = {"until": time.time() + 3600, "hash": hashlib.sha256(session.encode()).hexdigest()}
    bot.dirty = True
    return uid + "." + session


def identify(bot, session):
    uid, _, token = str(session).partition(".")
    record = bot.db["users"].get(uid, {}).get("portal_session", {})
    if not uid.isdecimal() or record.get("until", 0) <= time.time() or not hmac.compare_digest(
            record.get("hash", ""), hashlib.sha256(token.encode()).hexdigest()):
        raise CheckoutError("Войдите в кабинет заново.", 401)
    return int(uid)


def history(bot, sid):
    rows = [o for o in bot.db.get("shop_orders", {}).values() if o["sid"] == sid]
    return [dict({k: o[k] for k in ("id", "amount", "kind", "days", "state", "created")},
                 test=o.get("test", False), provider=o.get("provider", "tbank"), expires=o.get("expires", 0))
            for o in sorted(rows, key=lambda o: -o["created"])]


def reserve(bot, uid, req, bank):
    if req.get("kind") == "listing" and not listing_fee_enabled():
        raise CheckoutError("Размещение компании сейчас бесплатно.", 409)
    if not bank.ready:
        raise CheckoutError("Приём платежей ещё не подключён.", 503)
    s = bot.db["sellers"].get(req.get("sid"))
    if not s or uid not in s["owners"]:
        raise CheckoutError("Магазин не найден.", 404)
    kind = req.get("kind")
    days = req.get("days", 0)
    if kind == "listing" and unpaid(s) and s["status"] == "awaiting_payment":
        amount, days = LISTING_AMOUNT, 0
    elif kind == "promo" and s["status"] == "approved" and type(days) is int and days in (7, 30):
        amount = bot.promo_prices()[str(days)] * 100
    else:
        raise CheckoutError("Эта услуга сейчас недоступна для магазина.", 409)
    if "amount" in req or "price" in req or "currency" in req:
        raise CheckoutError("Стоимость назначает сервер.")
    email = str(req.get("email", "")).strip()
    if len(email) > 254 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise CheckoutError("Укажите e-mail для чека.")
    intent = req.get("intent", "")
    if not isinstance(intent, str) or not re.fullmatch("[a-f0-9]{32}", intent) or req.get("offer") != bank.offer:
        raise CheckoutError("Обновите страницу и примите условия оферты.")
    orders = bot.db.setdefault("shop_orders", {})
    old = next((o for o in orders.values() if o["uid"] == uid and o["intent"] == intent), None)
    if old:
        if (s["id"], kind, days) != (old["sid"], old["kind"], old["days"]):
            raise CheckoutError("Заказ уже существует.", 409)
        return old, False
    old = active_order(bot, s["id"])
    if old:
        if old.get("bank_ambiguity"):
            raise CheckoutError("Банк вернул несколько операций. Заказ передан на сверку.", 409)
        if (old["kind"], old["days"], old["uid"]) != (kind, days, uid):
            raise CheckoutError("Сначала завершите открытый заказ магазина.", 409)
        return old, False
    now = int(time.time())
    order = {"id": secrets.token_hex(16), "uid": uid, "sid": s["id"], "terminal": bank.key,
             "kind": kind, "days": days, "amount": amount, "email": email, "intent": intent,
             "state": "initializing", "created": now, "expires": now + 3600, "offer": bank.offer}
    order["provider"] = getattr(bank, "provider", "tbank")
    order["test"] = getattr(bank, "test", False)
    if order["provider"] == "robokassa":
        # Invoice and order are reserved in the same compare-and-swap transaction.
        invoice = max(int(bot.db.get("shop_invoice_sequence", 0)) + 1, now * 1000)
        bot.db["shop_invoice_sequence"] = invoice
        order["invoice"] = str(invoice)
    orders[order["id"]] = order
    bot.dirty = True
    return order, True


def apply_state(bot, order, data):
    if (data.get("OrderId") != order["id"] or data.get("TerminalKey") != order["terminal"]
            or not str(data.get("PaymentId", "")).isdigit()
            or order.get("pid", str(data["PaymentId"])) != str(data["PaymentId"])):
        raise CheckoutError("Банковская операция не соответствует заказу.", 409)
    status = data.get("Status")
    if status not in REFUNDS and (type(data.get("Amount")) is not int or data["Amount"] != order["amount"]):
        raise CheckoutError("Сумма не соответствует заказу.", 409)
    s = bot.db["sellers"].get(order["sid"])
    order["pid"] = str(data["PaymentId"])
    if status == "REFUNDED":
        if s and order.get("applied") and order["state"] != "refunded":
            if order["kind"] == "listing":
                s["listing_fee"] = "due" if listing_fee_enabled() else "waived"
                if s["status"] == "approved":
                    s["status"] = approved_status(s)
            else:
                unserved = max(0, order.get("ends", 0) - max(time.time(), order.get("starts", 0)))
                if unserved:
                    # Removing a future segment brings all later segments forward. Keep their
                    # effective intervals in step, so a later refund only removes unserved time.
                    for later in bot.db["shop_orders"].values():
                        if (later["sid"] == order["sid"] and later.get("applied")
                                and later["state"] != "refunded" and later.get("starts", 0) >= order["ends"]):
                            later["starts"] -= unserved
                            later["ends"] -= unserved
                    s["promo_until"] = max(0, s.get("promo_until", 0) - unserved)
        order["state"] = "refunded"
        bot.catalog_dirty = True
    elif status in REFUNDS and order["state"] != "refunded":
        if order["state"] != "refund_review":
            bot.notify_admins("Возврат по заказу " + order["id"] + ". Проверьте сумму, услугу и возвратный чек в кабинете.")
        order["state"] = "refund_review"
    elif status == "CONFIRMED":
        if order.get("test"):
            order["state"] = "test_paid"
            order.pop("url", None)
            order.pop("email", None)
            bot.dirty = True
            return
        if order.get("applied"):
            # Also repair the public projection if a preceding upload failed after the DB commit.
            bot.catalog_dirty = True
        elif order["state"] not in ("refund_review", "refunded", "review"):
            if s and order["kind"] == "listing" and s.get("listing_fee") != "waived":
                s["listing_fee"] = "paid"
                if s["status"] == "awaiting_payment":
                    s["status"] = "approved"
            elif s and order["kind"] == "promo" and s["status"] == "approved":
                order["starts"] = max(time.time(), s.get("promo_until", 0))
                order["ends"] = order["starts"] + 86400 * order["days"]
                s["promo_until"] = order["ends"]
            else:
                order["state"] = "review"
                if not order.get("alerted"):
                    bot.notify_admins("Оплаченный заказ " + order["id"] + " требует проверки магазина и возврата при необходимости.")
                    order["alerted"] = True
                bot.dirty = True
                return
            order.update(applied=True, state="paid", settled=int(time.time()))
            # The cabinet shows the result without a duplicate Telegram message.
            bot.catalog_dirty = True
    elif not order.get("applied") and order["state"] in ACTIVE:
        order["state"] = "failed" if status in FAILURES else "waiting"
    if order["state"] not in ACTIVE:
        order.pop("url", None)
        order.pop("email", None)
    bot.dirty = True


def transact(factory, op):
    import index
    for _ in range(5):
        bot = index.Bot(factory(None), index.OutboxTelegram(), index.BOT_TOKEN, data=factory(index.DATA_BUCKET))
        result = op(bot)
        try:
            bot.save()
            return result
        except index.Conflict:
            continue
    raise CheckoutError("Сервер занят. Повторите запрос.", 503)


def sync(factory, bank, order, pid=None):
    bank = provider_for_order(order, bank)
    if not bank.verifiable or bank.key != order["terminal"]:
        raise CheckoutError("Банковский терминал временно недоступен.", 503)
    if order.get("provider") == "robokassa":
        result = bank.state(order)
        if result:
            transact(factory, lambda bot: apply_state(bot, bot.db["shop_orders"][order["id"]], result))
        return
    def flag_ambiguity(bot):
        current = bot.db["shop_orders"][order["id"]]
        if not current.get("bank_ambiguity"):
            current["bank_ambiguity"] = True
            current.pop("url", None)
            if not current.get("applied"):
                current["state"] = "review"
            bot.notify_admins("По одному заказу " + current["id"] + " банк вернул несколько операций. Сверьте списания и возвраты.")
            bot.dirty = True

    if pid and order.get("pid") and order["pid"] != str(pid):
        incoming = bank.call("GetState", PaymentId=pid)
        if incoming.get("OrderId") == order["id"] and incoming.get("TerminalKey") == bank.key:
            transact(factory, flag_ambiguity)
        return
    pid = order.get("pid") or pid
    if not pid:
        answer = bank.call("CheckOrder", OrderId=order["id"])
        payments = answer.get("Payments")
        if not isinstance(payments, list):
            raise CheckoutError("Банк пока не подтвердил историю заказа.", 503)
        if len(payments) > 1:
            transact(factory, flag_ambiguity)
            return
        if len(payments) != 1:
            if not payments and time.time() > order["expires"] + 300:
                def expire(bot):
                    current = bot.db["shop_orders"][order["id"]]
                    if not current.get("pid") and current["state"] == "initializing":
                        current["state"] = "failed"
                        current.pop("email", None)
                        bot.dirty = True
                transact(factory, expire)
            return
        pid = payments[0].get("PaymentId")
    if not str(pid or "").isdigit():
        raise CheckoutError("Не найден идентификатор платежа.", 409)
    result = bank.call("GetState", PaymentId=pid)
    transact(factory, lambda bot: apply_state(bot, bot.db["shop_orders"][order["id"]], result))


def dispatch(factory, bank, req, callback=False):
    if callback:
        if getattr(bank, "provider", "tbank") == "robokassa":
            order = transact(factory, lambda bot: bot.db.get("shop_orders", {}).get(req.get("Shp_order")))
            if not order or order.get("provider") != "robokassa":
                raise CheckoutError("Заказ не найден.", 404)
            verifier = provider_for_order(order, bank)
            result = verifier.callback(req, order)
            transact(factory, lambda bot: apply_state(bot, bot.db["shop_orders"][order["id"]], result))
            return "OK" + order["invoice"]
        if not bank.verifiable or req.get("TerminalKey") != bank.key or not hmac.compare_digest(
                str(req.get("Token", "")), signature(req, bank.password)):
            raise CheckoutError("Неверная подпись.", 403)
        order = transact(factory, lambda bot: bot.db.get("shop_orders", {}).get(req.get("OrderId")))
        if not order or order.get("provider", "tbank") != "tbank" or order["terminal"] != bank.key:
            raise CheckoutError("Заказ не найден.", 404)
        sync(factory, bank, order, req.get("PaymentId"))
        return "OK"
    action = req.get("op")
    if action == "login":
        return {"session": transact(factory, lambda bot: login(bot, req.get("code", "")))}
    def owned(bot):
        uid = identify(bot, req.get("session"))
        if action == "logout":
            bot.user(uid).pop("portal_session", None)
            bot.dirty = True
            return {"ok": True}
        if action == "list":
            return {"ready": bank.ready, "offer": bank.offer if bank.ready else "", "prices": bot.promo_prices(),
                    "listing_fee_enabled": listing_fee_enabled(),
                    "provider": getattr(bank, "provider", "tbank"), "provider_name": getattr(bank, "name", "Т-Банк"),
                    "test": getattr(bank, "test", False),
                    "shops": [{"sid": s["id"], "name": s["name"], "status": s["status"], "orders": history(bot, s["id"])}
                              for s in bot.my_sellers(uid)]}
        if action == "buy":
            return reserve(bot, uid, req, bank)
        if action in ("sync", "resume"):
            order = bot.db.get("shop_orders", {}).get(req.get("id"))
            if order and uid in bot.db["sellers"].get(order["sid"], {}).get("owners", []):
                if action == "resume" and order["kind"] == "listing" and not listing_fee_enabled():
                    raise CheckoutError("Размещение компании сейчас бесплатно.", 409)
                return (order, False) if action == "resume" else order
        raise CheckoutError("Заказ не найден.", 404)
    result = transact(factory, owned)
    if action in ("list", "logout"):
        return result
    if action == "sync":
        sync(factory, bank, result)
        return {"ok": True}
    order, fresh = result
    bank = provider_for_order(order, bank)
    if fresh or (order.get("provider") == "robokassa" and order["state"] == "initializing"):
        initialized = bank.initiate(order)
        url = str(initialized.get("PaymentURL", ""))
        if (not secure_url(url) or urlsplit(url).hostname not in getattr(bank, "hosts", Bank.hosts)
                or initialized.get("OrderId") != order["id"] or initialized.get("Amount") != order["amount"]
                or initialized.get("TerminalKey") != bank.key or not str(initialized.get("PaymentId", "")).isdigit()):
            raise CheckoutError("Банк вернул некорректные данные заказа.", 503)
        def attach(bot):
            current = bot.db["shop_orders"][order["id"]]
            if current["state"] in ACTIVE and current.get("pid", str(initialized["PaymentId"])) == str(initialized["PaymentId"]):
                current.update(pid=str(initialized["PaymentId"]), state="waiting", url=url)
                bot.dirty = True
            return current
        order = transact(factory, attach)
    return {"id": order["id"], "state": order["state"], "amount": order["amount"],
            "url": order.get("url") if time.time() < order["expires"] else None}
