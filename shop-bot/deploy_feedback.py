"""Update only the existing Telegram bot, reusing current storage credentials.
A staged production deployment is probed before promotion. Never rotate keys,
import places, publish user data, run cron, or send synthetic Telegram messages.
Run only from the dedicated GitHub Actions deployment job.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
SECRETS = []


def mask(value):
    if value:
        SECRETS.append(value)
        print("::add-mask::" + value, flush=True)


def call(url, body=None, token=None, headers=None):
    hdr = {"Content-Type": "application/json", **(headers or {})}
    if token:
        hdr["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, headers=hdr)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        # A Telegram request URL contains the bot token: never log it or a traceback.
        raise RuntimeError("Cloud request failed: HTTP " + str(error.code)) from None


def cli(*arguments):
    proc = subprocess.run(["npx", "--yes", "vercel@latest", *arguments, "--token", os.environ["VERCEL_TOKEN"]],
                          cwd=ROOT, text=True, capture_output=True, timeout=480)
    if proc.returncode:
        output = proc.stderr[-6000:]
        for value in SECRETS:
            output = output.replace(value, "[masked]")
        print(output, file=sys.stderr)
        raise RuntimeError("Vercel command failed with exit " + str(proc.returncode))
    return proc.stdout.strip()


def main():
    for key in ("BOT_TOKEN", "VERCEL_TOKEN", "IAM_TOKEN"):
        mask(os.environ[key])
    token = os.environ["BOT_TOKEN"]
    telegram = "https://api.telegram.org/bot" + token + "/"
    def tg(method, body=None):
        value = call(telegram + method, body)
        if not value.get("ok"):
            raise RuntimeError("Telegram operation failed: " + method)
        return value["result"]
    identity = tg("getMe")
    original_hook = tg("getWebhookInfo")
    hook_url = original_hook.get("url", "")
    host = urllib.parse.urlparse(hook_url).hostname or ""
    if not host.endswith(".vercel.app") or urllib.parse.urlparse(hook_url).path != "/api/webhook":
        raise RuntimeError("Existing Vercel webhook could not be identified")
    vc = os.environ["VERCEL_TOKEN"]
    deployment = call("https://api.vercel.com/v13/deployments/" + host, token=vc)
    project_id = deployment.get("projectId") or (deployment.get("project") or {}).get("id")
    if not project_id:
        raise RuntimeError("Cannot identify the existing Vercel project")
    project = call("https://api.vercel.com/v9/projects/" + project_id, token=vc)
    account = project.get("accountId") or deployment.get("ownerId")
    if not account:
        raise RuntimeError("Cannot identify the existing Vercel account")

    # The seller API uses the same already-provisioned private/public buckets.
    yc = "https://serverless-functions.api.cloud.yandex.net/functions/v1/"
    query = urllib.parse.urlencode({"folderId": os.environ["FOLDER"], "filter": 'name="shop-api"'})
    functions = call(yc + "functions?" + query, token=os.environ["IAM_TOKEN"]).get("functions", [])
    if len(functions) != 1:
        raise RuntimeError("Existing seller API not found; refusing to provision or rotate anything")
    query = urllib.parse.urlencode({"functionId": functions[0]["id"], "tag": "$latest"})
    version = call(yc + "versions:byTag?" + query, token=os.environ["IAM_TOKEN"])
    env = version.get("environment", {})
    required = ("BUCKET", "DATA_BUCKET", "S3_KEY_ID", "S3_SECRET", "BOT_TOKEN")
    if not all(env.get(k) for k in required) or env["BOT_TOKEN"] != token or env["DATA_BUCKET"] == env["BUCKET"]:
        raise RuntimeError("Existing runtime credentials do not match this bot")
    runtime = {key: env[key] for key in required}
    runtime["OWNER_USERNAME"] = os.environ.get("OWNER_USERNAME", "").lstrip("@")
    for key in ("S3_KEY_ID", "S3_SECRET"):
        mask(runtime[key])
    os.environ.update(runtime)
    import index
    raw = index.make_storage(bucket=runtime["DATA_BUCKET"]).get(index.DB_KEY)
    db = json.loads(raw) if raw else {}
    if not db.get("owner"):
        raise RuntimeError("Owner is not configured; existing deployment kept")
    for bucket in (runtime["BUCKET"], runtime["DATA_BUCKET"]):
        request = urllib.request.Request("https://storage.yandexcloud.net/" + bucket + "/" + index.DB_KEY)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raise RuntimeError("Bot database is publicly readable; deployment stopped")
        except urllib.error.HTTPError as error:
            if error.code not in (403, 404):
                raise RuntimeError("Cannot verify private database access") from None
    folder = ROOT / ".vercel"
    folder.mkdir(exist_ok=True)
    (folder / "project.json").write_text(json.dumps({"projectId": project_id, "orgId": account}))

    arguments = ["deploy", "--prod", "--skip-domain", "--yes"]
    for key, value in runtime.items():
        arguments.extend(["--env", key + "=" + value])
    deployed = cli(*arguments).splitlines()[-1].strip()
    if not re.fullmatch(r"https://[A-Za-z0-9.-]+\.vercel\.app", deployed):
        raise RuntimeError("Deployment URL is invalid")
    secret = hashlib.sha256((token + ":webhook").encode()).hexdigest()[:32]
    mask(secret)
    def probe(url):
        result = call(url, {"feedback_probe": True}, headers={"X-Telegram-Bot-Api-Secret-Token": secret})
        if result != {"feature": "feedback_v1", "owner_configured": True, "private_database": True}:
            raise RuntimeError("Feedback probe did not confirm owner and private storage")
        try:
            call(url, {})
        except RuntimeError as error:
            if str(error) != "Cloud request failed: HTTP 403":
                raise
        else:
            raise RuntimeError("Webhook accepted an unauthenticated request")
    probe(deployed + "/api/webhook")

    # Promotion only after the exact new deployment passes its authenticated check.
    old_id = deployment["id"]
    new_hook = hook_url if host in deployment.get("alias", []) else deployed + "/api/webhook"
    promoted = False
    try:
        promoted = True
        cli("promote", deployed, "--yes")
        if new_hook != hook_url:
            tg("setWebhook", {"url": new_hook, "secret_token": secret,
                "max_connections": original_hook.get("max_connections", 1),
                "allowed_updates": original_hook.get("allowed_updates", ["message", "callback_query"]),
                "drop_pending_updates": False})
        probe(new_hook)
        if tg("getWebhookInfo")["url"] != new_hook:
            raise RuntimeError("Webhook changed unexpectedly")
    except Exception:
        if promoted:
            cli("promote", old_id, "--yes")
            if new_hook != hook_url:
                tg("setWebhook", {"url": hook_url, "secret_token": secret,
                    "max_connections": original_hook.get("max_connections", 1),
                    "allowed_updates": original_hook.get("allowed_updates", ["message", "callback_query"]),
                    "drop_pending_updates": False})
        raise
    commands = tg("getMyCommands")
    commands = [c for c in commands if c["command"] != "feedback"]
    commands.append({"command": "feedback", "description": "Предложения и ошибки приложения"})
    tg("setMyCommands", {"commands": commands})
    result = {"bot": identity["username"], "webhook": new_hook, "deployment": deployed,
              "feature": "feedback_v1", "owner_configured": True, "private_database": True,
              "unauthenticated_status": 403, "previous_deployment": old_id}
    Path("ci-out").mkdir(exist_ok=True)
    Path("ci-out/feedback-deploy.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        message = str(error)
        for value in SECRETS:
            message = message.replace(value, "[masked]")
        print("Feedback deployment stopped: " + type(error).__name__ + ": " + message, file=sys.stderr)
        sys.exit(1)
