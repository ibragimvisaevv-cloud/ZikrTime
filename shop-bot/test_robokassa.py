"""Offline payment regressions. Every provider response is simulated."""
import hashlib
import io
import json
import os
import time
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlencode, urlsplit, unquote

import acquiring
import index
import miniapi
from robokassa import Robokassa
from test_acquiring import Storage, FakeBank


class RobokassaOrders(unittest.TestCase):
    def setUp(self):
        config = {"LISTING_FEE_ENABLED": "1", "ROBOKASSA_ENABLED": "true", "ROBOKASSA_NPD_RECEIPTS": "true", "ROBOKASSA_TEST_MODE": "false",
                  "ROBOKASSA_MERCHANT_LOGIN": "fixture", "ROBOKASSA_PASSWORD1": "fixture-one",
                  "ROBOKASSA_PASSWORD2": "fixture-two", "ROBOKASSA_TEST_PASSWORD1": "test-one",
                  "ROBOKASSA_TEST_PASSWORD2": "test-two", "ROBOKASSA_HASH": "sha256",
                  "SELLER_PORTAL_URL": "https://example.test/cabinet", "SELLER_OFFER_URL": "https://example.test/offer"}
        self.env = patch.dict(os.environ, config, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)
        self.public, self.private = Storage(), Storage()
        self.factory = lambda bucket: self.private if bucket == index.DATA_BUCKET else self.public
        seller = {"id": "s1", "owners": [5], "status": "awaiting_payment", "listing_fee": "due",
                  "created": 1, "name": "Компания", "category": "other", "products": {}}
        self.private.files[index.DB_KEY] = json.dumps({"sellers": {"s1": seller}, "admins": [1],
                                                     "bot_username": "Fixture"}).encode()
        self.bank = Robokassa()
        code = acquiring.transact(self.factory, lambda bot: acquiring.account_code(bot, 5))
        self.session = acquiring.dispatch(self.factory, self.bank, {"op": "login", "code": code})["session"]

    def request(self, action, **fields):
        return acquiring.dispatch(self.factory, self.bank, dict(fields, op=action, session=self.session))

    def buy(self, **fields):
        args = dict(sid="s1", kind="listing", days=0, intent="a" * 32,
                    email="buyer@example.test", offer=self.bank.offer)
        args.update(fields)
        return self.request("buy", **args)

    def db(self):
        return json.loads(self.private.files[index.DB_KEY])

    def notice(self, order_id, **fields):
        order = self.db()["shop_orders"][order_id]
        data = {"InvId": order["invoice"], "OutSum": "200.000000", "Shp_order": order_id,
                "Shp_test": "1" if order.get("test") else "0"}
        data.update(fields)
        password = "test-two" if order.get("test") else "fixture-two"
        signature = f"{data['OutSum']}:{data['InvId']}:{password}"
        signature += "".join(f":{key}={data[key]}" for key in sorted(data) if key.startswith("Shp_"))
        data["SignatureValue"] = hashlib.sha256(signature.encode()).hexdigest()
        return data

    def paid(self, order_id):
        return acquiring.dispatch(self.factory, self.bank, self.notice(order_id), callback=True)

    def test_fixed_signed_amount_cas_retry_and_resume(self):
        self.private.once = True
        result = self.buy()
        order = self.db()["shop_orders"][result["id"]]
        query = {key: value[0] for key, value in parse_qs(urlsplit(result["url"]).query).items()}
        self.assertEqual(query["OutSum"], "200.00")
        self.assertEqual(query["IncCurrLabel"], "SBP")
        self.assertNotIn("IsTest", query)
        base = f"fixture:200.00:{order['invoice']}:{query['Receipt']}:fixture-one:Shp_order={order['id']}:Shp_test=0"
        self.assertEqual(query["SignatureValue"], hashlib.sha256(base.encode()).hexdigest())
        receipt = json.loads(unquote(query["Receipt"]))
        self.assertEqual(receipt["items"][0]["payment_object"], "service")
        self.assertEqual(receipt["items"][0]["sum"], 200)
        self.assertEqual(receipt["items"][0]["tax"], "none")
        self.assertIn("Зикр-тайм", receipt["items"][0]["name"])
        self.assertEqual(self.buy(intent="b" * 32)["id"], order["id"])
        self.assertEqual(self.request("resume", id=order["id"])["url"], result["url"])
        self.assertEqual(len(self.db()["shop_orders"]), 1)
        for field in (dict(amount=1), dict(currency="USD"), dict(price=0), dict(sid="unknown")):
            with self.assertRaises(acquiring.CheckoutError):
                self.buy(**field)

    def test_signed_callback_conflict_retry_and_duplicate_publish_once(self):
        result = self.buy()
        self.private.once = True
        reply = self.paid(result["id"])
        self.assertEqual(reply, "OK" + self.db()["shop_orders"][result["id"]]["invoice"])
        self.assertEqual(self.paid(result["id"]), reply)
        self.assertEqual(self.db()["sellers"]["s1"]["status"], "approved")
        self.assertEqual(self.db()["shop_orders"][result["id"]]["state"], "paid")
        self.assertEqual(len(json.loads(self.public.files["catalog.json"])["sellers"]), 1)
        self.assertEqual(self.db().get("outbox", []), [])

    def test_tampered_amount_invoice_mode_signature_and_extra_parameters(self):
        result = self.buy()
        bad = [self.notice(result["id"], OutSum="199.99"), self.notice(result["id"], InvId="1"),
               self.notice(result["id"], Shp_test="1"), self.notice(result["id"], Shp_extra="unexpected")]
        data = self.notice(result["id"])
        data["SignatureValue"] = "a" * 64
        bad.append(data)
        for data in bad:
            with self.assertRaises(acquiring.CheckoutError):
                acquiring.dispatch(self.factory, self.bank, data, callback=True)
        self.assertEqual(self.db()["sellers"]["s1"]["status"], "awaiting_payment")
        self.assertNotIn("catalog.json", self.public.files)

    def test_test_payment_never_activates_listing_and_survives_provider_mode_switch(self):
        self.bank = Robokassa(test=True)
        result = self.buy()
        self.assertIn("IsTest=1", result["url"])
        self.bank = Robokassa(test=False)
        self.paid(result["id"])
        self.assertEqual(self.db()["shop_orders"][result["id"]]["state"], "test_paid")
        self.assertEqual(self.db()["sellers"]["s1"]["status"], "awaiting_payment")
        self.assertNotIn("catalog.json", self.public.files)
        with patch.dict(os.environ, {"ROBOKASSA_TEST_PASSWORD2": ""}):
            with self.assertRaises(acquiring.CheckoutError):
                self.paid(result["id"])

    def test_missing_configuration_disables_checkout_but_allows_history(self):
        with patch.dict(os.environ, {"ROBOKASSA_NPD_RECEIPTS": "false"}):
            self.bank = Robokassa()
            self.assertFalse(self.request("list")["ready"])
            with self.assertRaises(acquiring.CheckoutError):
                self.buy()
        with patch.dict(os.environ, {}, clear=True):
            self.assertFalse(Robokassa().ready)
            self.assertFalse(Robokassa().verifiable)

    def test_expired_test_invoice_can_be_replaced_without_real_provider_call(self):
        self.bank = Robokassa(test=True)
        result = self.buy()
        order = self.db()["shop_orders"][result["id"]]
        with patch("robokassa.time.time", return_value=order["expires"] + 1):
            acquiring.sync(self.factory, self.bank, order)
        self.assertEqual(self.db()["shop_orders"][result["id"]]["state"], "failed")
        self.assertNotEqual(self.buy(intent="c" * 32)["id"], result["id"])

    def test_late_callback_after_moderation_does_not_republish_rejected_seller(self):
        result = self.buy()
        acquiring.transact(self.factory, lambda bot: bot.moderate(1, "no", "s1"))
        self.paid(result["id"])
        seller = self.db()["sellers"]["s1"]
        self.assertEqual((seller["status"], seller["listing_fee"]), ("rejected", "paid"))
        self.assertEqual(json.loads(self.public.files["catalog.json"])["sellers"], [])

    def test_success_redirect_is_read_only_and_callback_accepts_form(self):
        result = self.buy()
        response = miniapi.handler({"httpMethod": "GET", "queryStringParameters": self.notice(result["id"])}, None)
        self.assertEqual(response["statusCode"], 200)
        self.assertEqual(self.db()["shop_orders"][result["id"]]["state"], "waiting")
        form = urlencode(self.notice(result["id"]))
        event = {"httpMethod": "POST", "queryStringParameters": {"event": "robokassa"},
                 "headers": {"Content-Type": "application/x-www-form-urlencoded"}, "body": form}
        with patch.object(index, "make_storage", lambda token, bucket: self.factory(bucket)):
            response = miniapi.handler(event, None)
        self.assertEqual(response["statusCode"], 200)
        self.assertEqual(response["body"], "OK" + self.db()["shop_orders"][result["id"]]["invoice"])
        event["body"] += "&OutSum=1"
        with patch.object(index, "make_storage", lambda token, bucket: self.factory(bucket)):
            self.assertEqual(miniapi.handler(event, None)["statusCode"], 400)

    def test_provider_switch_preserves_legacy_tbank_order(self):
        old = FakeBank()
        result = acquiring.dispatch(self.factory, old, dict(op="buy", session=self.session, sid="s1", kind="listing",
                    intent="b" * 32, email="buyer@example.test", offer=old.offer))
        def legacy(bot):
            bot.db["shop_orders"][result["id"]].pop("provider")
            bot.dirty = True
        acquiring.transact(self.factory, legacy)
        old.status = "CONFIRMED"
        with patch.object(acquiring, "Bank", return_value=old):
            self.request("sync", id=result["id"])
        self.assertEqual(self.db()["shop_orders"][result["id"]]["state"], "paid")

    def test_polling_only_trusts_provider_reply_and_exact_order(self):
        result = self.buy()
        xml = ('<OperationStateResponse><Result><Code>0</Code></Result><State><Code>100</Code></State>'
               '<Info><OutSum>200.000000</OutSum></Info><UserFields><Field><Name>Shp_order</Name><Value>'
               + result["id"] + '</Value></Field><Field><Name>Shp_test</Name><Value>0</Value></Field>'
               '</UserFields></OperationStateResponse>')
        with patch("robokassa.urllib.request.urlopen", return_value=io.BytesIO(xml.replace(result["id"], "wrong").encode())):
            with self.assertRaises(acquiring.CheckoutError):
                self.request("sync", id=result["id"])
        with patch("robokassa.urllib.request.urlopen", return_value=io.BytesIO(xml.encode())):
            self.request("sync", id=result["id"])
        self.assertEqual(self.db()["shop_orders"][result["id"]]["state"], "paid")

    def test_unpaid_expiry_needs_provider_confirmation_not_timeout(self):
        result = self.buy()
        order = self.db()["shop_orders"][result["id"]]
        with patch("robokassa.time.time", return_value=order["expires"] + 301):
            with patch("robokassa.urllib.request.urlopen", side_effect=OSError("timeout")):
                with self.assertRaises(acquiring.CheckoutError):
                    acquiring.sync(self.factory, self.bank, order)
            self.assertEqual(self.db()["shop_orders"][order["id"]]["state"], "waiting")
            xml = b"<OperationStateResponse><Result><Code>3</Code></Result></OperationStateResponse>"
            with patch("robokassa.urllib.request.urlopen", return_value=io.BytesIO(xml)):
                acquiring.sync(self.factory, self.bank, order)
        self.assertEqual(self.db()["shop_orders"][order["id"]]["state"], "failed")


if __name__ == "__main__":
    unittest.main(verbosity=2)
