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
        print(f"HTTP {e.code} {method} {url.split('?')[0]}: {e.read()[:500].decode(errors='replace')}", file=sys.stderr)
        raise


def wait(op):
    for _ in range(120):
        if op.get("done"):
            if op.get("error"):
                raise SystemExit("операция не удалась: " + json.dumps(op["error"], ensure_ascii=False)[:500])
            return op.get("response", {})
        time.sleep(2)
        op = call("GET", OPS + op["id"])
    raise SystemExit("операция не завершилась вовремя")


def main():
    folder, name = os.environ["FOLDER"], os.environ["API_FUNCTION"]
    found = call("GET", f"{API}/functions?folderId={folder}&filter=" + urllib.request.quote(f'name="{name}"'))
    funcs = found.get("functions", [])
    if funcs:
        fid = funcs[0]["id"]
    else:
        fid = wait(call("POST", f"{API}/functions", {"folderId": folder, "name": name}))["id"]
    print("функция:", fid, file=sys.stderr)

    # Публичный вызов (мини-приложение обращается без авторизации Яндекса; защита — подпись Telegram initData)
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
        for f in ("index.py", "miniapi.py"):
            z.write(os.path.join(os.path.dirname(__file__), f), f)
    env = {k: os.environ[k] for k in ("BOT_TOKEN", "BUCKET", "DATA_BUCKET", "S3_KEY_ID", "S3_SECRET")}
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
    print("версия создана", file=sys.stderr)
    print(f"https://functions.yandexcloud.net/{fid}")


if __name__ == "__main__":
    main()
