"""Robokassa checkout for the independent website and a merchant registered on NPD.

Official protocol: https://docs.robokassa.ru/ru/pay-interface
The account must have RoboChecks SMZ enabled. Credentials never enter the browser.
"""
import datetime as dt
import hashlib
import hmac
import json
import os
import re
import time
import urllib.request
import xml.etree.ElementTree as ET
from decimal import Decimal
from urllib.parse import quote, urlencode

from acquiring import CheckoutError, description, secure_url


def kopecks(value):
    text = str(value)
    if not re.fullmatch(r"\d{1,10}(?:\.\d{1,6})?", text):
        raise CheckoutError("Некорректная сумма платежа.", 409)
    amount = Decimal(text) * 100
    if amount != amount.to_integral_value() or amount <= 0:
        raise CheckoutError("Некорректная сумма платежа.", 409)
    return int(amount)


class Robokassa:
    provider = "robokassa"
    name = "Robokassa · СБП"
    hosts = {"auth.robokassa.ru"}

    def __init__(self, test=None):
        self.test = os.getenv("ROBOKASSA_TEST_MODE", "true") == "true" if test is None else bool(test)
        prefix = "ROBOKASSA_TEST_" if self.test else "ROBOKASSA_"
        self.key = os.getenv("ROBOKASSA_MERCHANT_LOGIN", "")
        self.password1 = os.getenv(prefix + "PASSWORD1", "")
        self.password2 = os.getenv(prefix + "PASSWORD2", "")
        self.algorithm = os.getenv("ROBOKASSA_HASH", "sha256").lower()
        self.portal = os.getenv("SELLER_PORTAL_URL", "").rstrip("/")
        self.offer = os.getenv("SELLER_OFFER_URL", "")
        self.verifiable = bool(self.key and self.password2 and self.algorithm in {"sha256", "sha512", "md5"})
        self.ready = bool(os.getenv("ROBOKASSA_ENABLED") == "true" and self.verifiable and self.password1
                          and secure_url(self.portal) and "?" not in self.portal and "#" not in self.portal
                          and secure_url(self.offer) and os.getenv("ROBOKASSA_NPD_RECEIPTS") == "true")

    def digest(self, value):
        if self.algorithm not in {"sha256", "sha512", "md5"}:
            raise CheckoutError("Проверка оплаты пока не настроена.", 503)
        return hashlib.new(self.algorithm, value.encode("utf-8")).hexdigest()

    def initiate(self, order):
        amount = f"{order['amount'] // 100}.{order['amount'] % 100:02d}"
        # Receipt is URL encoded once before signing and then by the form encoder.
        # NPD receipts use no VAT and describe our own listing/promotion service.
        receipt = quote(json.dumps({"items": [{"name": description(order), "quantity": 1,
                         "sum": float(amount), "tax": "none", "payment_method": "full_payment",
                         "payment_object": "service"}]}, ensure_ascii=False, separators=(",", ":")), safe="")
        invoice = order["invoice"]
        fields = {"MerchantLogin": self.key, "OutSum": amount, "InvId": invoice,
                  "Description": description(order), "Receipt": receipt, "Email": order["email"],
                  "Culture": "ru", "Encoding": "utf-8", "IncCurrLabel": "SBP",
                  "ExpirationDate": dt.datetime.fromtimestamp(order["expires"], dt.timezone.utc)
                      .astimezone(dt.timezone(dt.timedelta(hours=3))).strftime("%Y-%m-%dT%H:%M"),
                  "Shp_order": order["id"], "Shp_test": "1" if self.test else "0"}
        fields["SignatureValue"] = self.digest(
            f"{self.key}:{amount}:{invoice}:{receipt}:{self.password1}"
            f":Shp_order={order['id']}:Shp_test={fields['Shp_test']}")
        if self.test:
            fields["IsTest"] = "1"
        return {"PaymentURL": "https://auth.robokassa.ru/Merchant/Index.aspx?" + urlencode(fields),
                "OrderId": order["id"], "Amount": order["amount"], "TerminalKey": self.key,
                "PaymentId": invoice}

    def callback(self, request, order):
        if not self.verifiable or self.key != order["terminal"]:
            raise CheckoutError("Проверка оплаты пока недоступна.", 503)
        fields = {k: v for k, v in request.items() if k.lower().startswith("shp_")}
        expected = {"Shp_order": order["id"], "Shp_test": "1" if order.get("test") else "0"}
        if fields != expected or str(request.get("InvId")) != order["invoice"]:
            raise CheckoutError("Платёж не соответствует заказу.", 409)
        amount = str(request.get("OutSum", ""))
        text = f"{amount}:{order['invoice']}:{self.password2}"
        text += "".join(f":{key}={fields[key]}" for key in sorted(fields))
        if not hmac.compare_digest(str(request.get("SignatureValue", "")).lower(), self.digest(text)):
            raise CheckoutError("Неверная подпись платежа.", 403)
        if kopecks(amount) != order["amount"]:
            raise CheckoutError("Сумма не соответствует заказу.", 409)
        return self.normalized(order, "CONFIRMED", order["amount"])

    def normalized(self, order, status, amount):
        return {"OrderId": order["id"], "TerminalKey": self.key, "PaymentId": order["invoice"],
                "Status": status, "Amount": amount}

    def state(self, order):
        if self.test:
            # OpStateExt explicitly does not support test payments.
            if time.time() > order["expires"]:
                return self.normalized(order, "DEADLINE_EXPIRED", order["amount"])
            return None
        if not self.verifiable or self.key != order["terminal"]:
            raise CheckoutError("Проверка оплаты пока недоступна.", 503)
        fields = {"MerchantLogin": self.key, "InvoiceID": order["invoice"],
                  "Signature": self.digest(f"{self.key}:{order['invoice']}:{self.password2}")}
        request = urllib.request.Request("https://auth.robokassa.ru/Merchant/WebService/Service.asmx/OpStateExt",
                                         data=urlencode(fields).encode(),
                                         headers={"Content-Type": "application/x-www-form-urlencoded"})
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                raw = response.read(65537)
            if len(raw) > 65536 or b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
                raise ValueError("Invalid XML")
            root = ET.fromstring(raw)
            for element in root.iter():
                element.tag = element.tag.rsplit("}", 1)[-1]
        except (OSError, ValueError, ET.ParseError):
            raise CheckoutError("Платёжный сервис пока не ответил. Проверьте статус позже.", 503) from None
        code = root.findtext("Result/Code")
        if code == "3":
            if time.time() > order["expires"] + 300:
                return self.normalized(order, "DEADLINE_EXPIRED", order["amount"])
            return None
        if code == "4":
            raise CheckoutError("Платёж требует сверки. Обратитесь в поддержку с номером заказа.", 409)
        if code != "0":
            raise CheckoutError("Платёжный сервис не подтвердил статус заказа.", 503)
        status = {"100": "CONFIRMED", "10": "CANCELED", "60": "REJECTED"}.get(root.findtext("State/Code"), "NEW")
        if status == "CONFIRMED":
            fields = {item.findtext("Name"): item.findtext("Value") for item in root.findall("UserFields/Field")}
            if fields.get("Shp_order") != order["id"] or fields.get("Shp_test") != "0":
                raise CheckoutError("Платёж не соответствует заказу.", 409)
            amount = kopecks(root.findtext("Info/OutSum", ""))
        else:
            amount = order["amount"]
        return self.normalized(order, status, amount)
