"""Развёртывание API кабинета продавца (облачная функция Yandex Cloud) через REST API.

Функция в Яндексе открывается из России без VPN — к ней обращается мини-приложение в Telegram.
Запуск (в GitHub Actions): IAM_TOKEN, FOLDER, SA_ID, API_FUNCTION, BOT_TOKEN, BUCKET, DATA_BUCKET, S3_KEY_ID, S3_SECRET.
Печатает адрес функции. Секреты не выводятся.
"""
import base64
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
import zipfile

API = "https://serverless-functions.api.cloud.yandex.net/functions/v1"
OPS = "https://operation.api.cloud.yandex.net/operations/"
TOKEN = os.environ["IAM_TOKEN"]


def call(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} {method} {url.split('?')[0]}", file=sys.stderr)
        raise


def wait(op):
    for _ in range(120):
        if op.get("done"):
            if op.get("error"):
                raise SystemExit("операция не удалась, код: " + str(op["error"].get("code", "")))
            return op.get("response", {})
        time.sleep(2)
        op = call("GET", OPS + op["id"])
    raise SystemExit("операция не завершилась вовремя")


def deployment_environment(function_id, existing, preserve):
    env = dict(existing) if preserve else {}
    env.update({k: os.environ[k] for k in ("BOT_TOKEN", "BUCKET", "DATA_BUCKET", "S3_KEY_ID", "S3_SECRET")})
    if not preserve:
        env.update({k: os.environ[k] for k in ("TBANK_ENABLED", "TBANK_TERMINAL", "TBANK_PASSWORD", "TBANK_TAXATION",
                                              "TBANK_TAX", "SELLER_OFFER_URL", "PAYMENT_PROVIDER", "ROBOKASSA_ENABLED",
                                              "ROBOKASSA_MERCHANT_LOGIN", "ROBOKASSA_PASSWORD1", "ROBOKASSA_PASSWORD2",
                                              "ROBOKASSA_TEST_PASSWORD1", "ROBOKASSA_TEST_PASSWORD2", "ROBOKASSA_TEST_MODE",
                                              "ROBOKASSA_HASH", "ROBOKASSA_NPD_RECEIPTS") if os.getenv(k)})
    env["SELLER_PORTAL_URL"] = f"https://functions.yandexcloud.net/{function_id}"
    return env


def changed_payment_settings(previous, current):
    keys = {key for key in previous.keys() | current.keys()
            if key.startswith(("TBANK_", "ROBOKASSA_", "PAYMENT_")) or key == "SELLER_OFFER_URL"}
    return sorted(key for key in keys
                  if (key in previous, previous.get(key)) != (key in current, current.get(key)))


def main():
    folder, name = os.environ["FOLDER"], os.environ["API_FUNCTION"]
    preserve = os.getenv("PRESERVE_PAYMENT_CONFIG", "false").lower() == "true"
    previous_environment = {}
    found = call("GET", f"{API}/functions?folderId={folder}&filter=" + urllib.request.quote(f'name="{name}"'))
    funcs = found.get("functions", [])
    if funcs:
        fid = funcs[0]["id"]
        if preserve:
            query = urllib.request.quote("$latest", safe="")
            previous = call("GET", f"{API}/versions:byTag?functionId={fid}&tag={query}")
            previous_environment = previous.get("environment", {})
            if not isinstance(previous_environment, dict):
                raise SystemExit("не удалось прочитать действующую конфигурацию функции")
    else:
        fid = wait(call("POST", f"{API}/functions", {"folderId": folder, "name": name}))["id"]
    print("функция:", fid, file=sys.stderr)

    # Публичный вызов (мини-приложение обращается без авторизации Яндекса; защита — подпись Telegram initData)
    if not funcs:
        try:
            wait(call("POST", f"{API}/functions/{fid}:updateAccessBindings", {"accessBindingDeltas": [{
                "action": "ADD",
                "accessBinding": {"roleId": "functions.functionInvoker", "subject": {"id": "allUsers", "type": "system"}},
            }]}))
        except urllib.error.HTTPError as e:
            if e.code != 409:  # уже открыта
                print("доступ: не удалось открыть публичный вызов", file=sys.stderr)

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for f in ("index.py", "miniapi.py", "acquiring.py", "robokassa.py", "seller.html",
                  "place_corrections.py", "places_sources.py", "feedback.py", "community.py"):
            z.write(os.path.join(os.path.dirname(__file__), f), f)
    env = deployment_environment(fid, previous_environment, preserve)
    wait(call("POST", f"{API}/versions", {
        "functionId": fid,
        "runtime": "python312",
        "entrypoint": "miniapi.handler",
        "resources": {"memory": str(256 * 1024 * 1024)},
        "executionTimeout": "60s",
        "serviceAccountId": os.environ["SA_ID"],
        "environment": env,
        "content": base64.b64encode(buf.getvalue()).decode(),
    }))
    if preserve:
        query = urllib.request.quote("$latest", safe="")
        published = call("GET", f"{API}/versions:byTag?functionId={fid}&tag={query}")
        published_environment = published.get("environment", {})
        if not isinstance(published_environment, dict):
            raise SystemExit("Cannot verify preserved payment configuration")
        changed = changed_payment_settings(previous_environment, published_environment)
        if changed:
            raise SystemExit("Payment configuration changed: " + ", ".join(changed))
        print("Payment configuration preserved", file=sys.stderr)
    print("версия создана", file=sys.stderr)
    print(f"https://functions.yandexcloud.net/{fid}")


if __name__ == "__main__":
    main()
