"""Offline checkout exercise. No HTTP calls, bank account, secrets or real payments."""
import hashlib
import io
import json
import time
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import acquiring as a
import index
import miniapi


class Storage:
    def __init__(self):
        self.files = {}
        self.once = False

    def get(self, key):
        return self.files.get(key)

    def get_tagged(self, key):
        data = self.get(key)
        return data, hashlib.sha256(data).hexdigest() if data else None

    def put(self, key, data, media, **args):
        if self.once:
            self.once = False
            raise index.Conflict("concurrent write")
        if args.get("if_match") and args["if_match"] != self.get_tagged(key)[1]:
            raise index.Conflict(key)
        self.files[key] = data


class FakeBank:
    ready = verifiable = True
    offer = "https://example.test/terms-v1"
    key, password = "fixture-terminal", "fixture-password"

    def __init__(self):
        self.payments = {}
        self.status = "NEW"
        self.fail_init = False

    def initiate(self, o):
        pid = str(len(self.payments) + 1)
        data = {"PaymentId": pid, "Amount": o["amount"], "OrderId": o["id"], "TerminalKey": self.key}
        self.payments[pid] = data
        if self.fail_init:
            raise a.CheckoutError("unknown outcome", 503)
        return dict(data, PaymentURL="https://securepayments.tinkoff.ru/fixture")

    def call(self, method, **p):
        if method == "CheckOrder":
            return {"Payments": [row for row in self.payments.values() if row["OrderId"] == p["OrderId"]]}
        assert method == "GetState"
        return dict(self.payments[p["PaymentId"]], Status=self.status)


class Orders(unittest.TestCase):
    def setUp(self):
        self.pub, self.priv, self.bank = Storage(), Storage(), FakeBank()
        self.factory = lambda bucket: self.priv if bucket == index.DATA_BUCKET else self.pub
        self.shop = {"id":"s1", "owners":[5], "status":"awaiting_payment", "listing_fee":"due",
                     "created":1,"name":"Тест", "category":"other", "products":{}}
        self.priv.files[index.DB_KEY] = json.dumps({"sellers":{"s1":self.shop},"admins":[1],"bot_username":"Fixture"}).encode()
        self.code = a.transact(self.factory, lambda b:a.account_code(b, 5))
        self.session = self.call("login", code=self.code)["session"]

    def call(self, op, **fields):
        req = dict(op=op, session=getattr(self,"session",None))
        return a.dispatch(self.factory,self.bank,dict(req,**fields))

    def buy(self, **fields):
        req = dict(sid="s1",kind="listing",days=0,intent="a"*32,email="test@example.org",offer=self.bank.offer)
        return self.call("buy", **dict(req,**fields))

    def db(self):
        return json.loads(self.priv.files[index.DB_KEY])

    def notice(self, pid="1", **changes):
        data = dict(self.bank.payments[pid], Status="CONFIRMED", Success=True)
        data.update(changes)
        data["Token"] = a.signature(data,self.bank.password)
        return a.dispatch(self.factory,self.bank,data,True)

    def test_listing_moderation_retry_and_public_catalog(self):
        self.priv.once=True
        o=self.buy()
        self.assertEqual(self.buy(intent="b"*32)["id"],o["id"])
        self.assertEqual(len(self.bank.payments),1)
        self.assertEqual(self.bank.payments["1"]["Amount"],20_000)
        self.bank.status="AUTHORIZED"
        self.notice()
        self.assertEqual(self.db()["sellers"]["s1"]["status"], "awaiting_payment")
        self.assertNotIn("catalog.json",self.pub.files)
        self.bank.status="CONFIRMED"
        self.assertEqual(self.notice(),"OK")
        self.assertEqual(self.notice(),"OK")
        self.assertEqual(len(json.loads(self.pub.files["catalog.json"])["sellers"]),1)
        self.assertEqual(len(self.db()["outbox"]),1)
        self.assertEqual(self.call("list")["shops"][0]["orders"][0]["state"],"paid")
        with self.assertRaises(a.CheckoutError): self.buy()
        self.bank.status="REFUNDED";self.notice();self.notice()
        self.assertEqual(json.loads(self.pub.files["catalog.json"])["sellers"], [])
        self.assertEqual(self.db()["sellers"]["s1"]["listing_fee"],"due")

    def test_server_pricing_authentication_and_tamper(self):
        for req in ({"amount":1},{"price":0},{"currency":"USD"},{"sid":"other"},{"email":"missing"},
                    {"offer":"x"},{"kind":"promo","days":7}):
            with self.assertRaises(a.CheckoutError): self.buy(**req)
        with self.assertRaises(a.CheckoutError): self.call("login",code=self.code)
        with self.assertRaises(a.CheckoutError): self.call("list",session="5.fake")
        self.bank.ready=False
        with self.assertRaises(a.CheckoutError): self.buy()
        self.bank.ready=True
        self.buy()
        with self.assertRaises(a.CheckoutError): a.dispatch(self.factory,self.bank,{"Token":"fake"},True)
        self.bank.status="CONFIRMED"
        self.bank.payments["1"]["Amount"]=19999
        with self.assertRaises(a.CheckoutError): self.notice()
        self.assertNotIn("catalog.json",self.pub.files)

    def test_promo_tariff_snapshot_and_legacy_cards(self):
        def legacy(bot):
            s = bot.db["sellers"]["s1"]
            s.pop("listing_fee");s["status"]="approved"
            s["promo_until"]=int(time.time())+1000
            bot.db["promo_prices"]={"7":150,"30":400}
            bot.dirty=True
        a.transact(self.factory,legacy)
        start=self.db()["sellers"]["s1"]["promo_until"]
        with self.assertRaises(a.CheckoutError): self.buy()
        self.buy(kind="promo",days=7)
        self.assertEqual(self.bank.payments["1"]["Amount"],15000)
        self.bank.status="CONFIRMED";self.notice();self.notice()
        self.assertEqual(self.db()["sellers"]["s1"]["promo_until"],start+7*86400)
        self.bank.status="REFUNDED";self.notice();self.notice()
        self.assertEqual(self.db()["sellers"]["s1"]["promo_until"],start)

    def test_lost_bank_response_recovered_by_order_and_concurrent_rejection(self):
        self.bank.fail_init=True
        with self.assertRaises(a.CheckoutError): self.buy()
        oid=next(iter(self.db()["shop_orders"]))
        self.assertFalse(self.buy()["url"])
        a.transact(self.factory, lambda b:b.moderate(1,"no","s1"))
        self.bank.status="CONFIRMED"
        self.call("sync",id=oid)
        s=self.db()["sellers"]["s1"]
        self.assertEqual((s["status"],s["listing_fee"]),("rejected","paid"))
        a.transact(self.factory, lambda b:b.moderate(1,"ok","s1"))
        self.assertEqual(self.db()["sellers"]["s1"]["status"],"approved")
        self.assertEqual(len(self.bank.payments),1)

    def test_sequential_refunds_preserve_other_paid_periods(self):
        def legacy(bot):
            shop = bot.db["sellers"]["s1"]
            del shop["listing_fee"]
            shop["status"] = "approved"
            bot.dirty = True

        a.transact(self.factory, legacy)
        today = int(time.time())
        day = 86400
        with patch.object(a.time, "time", return_value=today):
            self.bank.status = "CONFIRMED"
            for pid, letter in (("1", "a"), ("2", "b"), ("3", "c")):
                self.buy(kind="promo", days=7, intent=letter * 32)
                self.notice(pid)
            self.assertEqual(self.db()["sellers"]["s1"]["promo_until"], today + 21 * day)
            self.bank.status = "REFUNDED"
            self.notice("1")
            self.assertEqual(self.db()["sellers"]["s1"]["promo_until"], today + 14 * day)
        with patch.object(a.time, "time", return_value=today + 8 * day):
            self.notice("2")
            self.notice("1")
            self.assertEqual(self.db()["sellers"]["s1"]["promo_until"], today + 14 * day)

    def test_terminal_failure_allows_another_order_and_logout_revokes_access(self):
        self.buy();self.bank.status="DEADLINE_EXPIRED";self.notice()
        self.buy(intent="b"*32)
        self.assertEqual(len(self.bank.payments),2)
        self.call("logout")
        with self.assertRaises(a.CheckoutError): self.call("list")

    def test_yandex_callback_returns_literal_ok_and_web_entry_is_independent(self):
        self.buy();self.bank.status="CONFIRMED"
        data=dict(self.bank.payments["1"],Success=True,Status="CONFIRMED")
        data["Token"]=a.signature(data,self.bank.password)
        ev={"httpMethod":"POST","queryStringParameters":{"event":"tbank"},"body":json.dumps(data)}
        with patch.object(a,"Bank",return_value=self.bank), patch.object(index,"make_storage",lambda token,b:self.factory(b)):
            r=miniapi.handler(ev,None)
        self.assertEqual((r["statusCode"],r["body"]),(200,"OK"))
        page=miniapi.handler({"httpMethod":"GET"},None)
        self.assertIn("/webcode",page["body"])
        self.assertEqual(page["headers"]["X-Frame-Options"],"DENY")

    def test_signature_excludes_nested_receipt_and_normalizes_boolean(self):
        sample={"TerminalKey":"T","Success":True,"Amount":20000,"Token":"old","Receipt":{"Email":"test"}}
        self.assertEqual(a.signature(sample,"P"),hashlib.sha256(b"20000PtrueT").hexdigest())

    def test_expired_unknown_init_releases_only_after_authoritative_missing_order(self):
        def reserve_only(bot):
            data = {"sid": "s1", "kind": "listing", "intent": "a" * 32,
                    "email": "test@example.org", "offer": self.bank.offer}
            return a.reserve(bot, 5, data, self.bank)[0]

        order = a.transact(self.factory, reserve_only)
        bank = a.Bank()
        bank.key, bank.password, bank.verifiable = self.bank.key, self.bank.password, True
        def response(code):
            return io.BytesIO(json.dumps({"Success": False, "ErrorCode": code}).encode())

        with patch.object(a.urllib.request, "urlopen", return_value=response("335")):
            a.sync(self.factory, bank, order)
        self.assertEqual(self.db()["shop_orders"][order["id"]]["state"], "initializing")
        with patch.object(a.time, "time", return_value=order["expires"] + 301):
            with patch.object(a.urllib.request, "urlopen", return_value=response("999")):
                with self.assertRaises(a.CheckoutError):
                    a.sync(self.factory, bank, order)
            self.assertEqual(self.db()["shop_orders"][order["id"]]["state"], "initializing")
            with patch.object(a.urllib.request, "urlopen", return_value=response("335")):
                a.sync(self.factory, bank, order)
        self.assertEqual(self.db()["shop_orders"][order["id"]]["state"], "failed")

    def test_multiple_bank_operations_require_reconciliation(self):
        self.bank.fail_init = True
        with self.assertRaises(a.CheckoutError):
            self.buy()
        order = next(iter(self.db()["shop_orders"].values()))
        self.bank.payments["2"] = dict(self.bank.payments["1"], PaymentId="2")
        self.call("sync", id=order["id"])
        self.assertTrue(self.db()["shop_orders"][order["id"]]["bank_ambiguity"])
        with self.assertRaises(a.CheckoutError):
            self.buy(intent="c" * 32)
        self.assertNotIn("catalog.json", self.pub.files)


if __name__ == "__main__":
    unittest.main(verbosity=2)
