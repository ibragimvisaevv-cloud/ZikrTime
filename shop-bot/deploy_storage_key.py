"""Reuse the active seller API storage credentials without revoking any access key."""
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request


def cloud_get(path, token):
    request = urllib.request.Request(
        "https://serverless-functions.api.cloud.yandex.net/functions/v1/" + path,
        headers={"Authorization": "Bearer " + token},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Cannot read active seller API configuration: HTTP {error.code}") from None
    except urllib.error.URLError:
        raise RuntimeError("Cannot reach the seller API configuration service") from None


def storage_credentials(token):
    query = urllib.parse.urlencode({"folderId": os.environ["FOLDER"], "filter": 'name="shop-api"'})
    functions = cloud_get("functions?" + query, token).get("functions", [])
    if len(functions) > 1:
        raise RuntimeError("Multiple seller API functions found; existing keys kept")
    if functions:
        query = urllib.parse.urlencode({"functionId": functions[0]["id"], "tag": "$latest"})
        version = cloud_get("versions:byTag?" + query, token)
        environment = version.get("environment", {})
        if environment.get("BUCKET") != os.environ["BUCKET"] or environment.get("DATA_BUCKET") != os.environ["BUCKET"] + "-data":
            raise RuntimeError("Active seller API uses unexpected buckets; existing keys kept")
        if environment.get("BOT_TOKEN") != os.environ["BOT_TOKEN"]:
            raise RuntimeError("Active seller API belongs to a different bot; existing keys kept")
        key_id, secret = environment.get("S3_KEY_ID"), environment.get("S3_SECRET")
        if not key_id or not secret:
            raise RuntimeError("Active seller API has no complete storage credentials; existing keys kept")
        return key_id, secret, True

    # First installation only. Reaching a key quota fails safely without deleting existing keys.
    result = subprocess.run(
        ["yc", "iam", "access-key", "create", "--service-account-id", os.environ["SA_ID"],
         "--description", "shop bot initial installation", "--format", "json"],
        text=True, capture_output=True, timeout=60,
    )
    if result.returncode:
        raise RuntimeError("Could not create initial storage key; existing keys kept")
    value = json.loads(result.stdout)
    return value["access_key"]["key_id"], value["secret"], False


def main():
    token = os.environ["IAM_TOKEN"]
    print("::add-mask::" + token)
    key_id, secret, reused = storage_credentials(token)
    for value in (key_id, secret):
        if not isinstance(value, str) or not value or "\n" in value or "\r" in value:
            raise RuntimeError("Invalid storage credential format; existing keys kept")
        print("::add-mask::" + value)
    if reused:
        import index
        storage = index.S3Storage(key_id, secret, bucket=os.environ["BUCKET"] + "-data")
        raw = storage.get(index.DB_KEY)
        if not raw or not json.loads(raw).get("owner"):
            raise RuntimeError("Active private database or its owner could not be verified; existing keys kept")
    with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as output:
        output.write(f"S3_KEY_ID={key_id}\nS3_SECRET={secret}\n")
    print("Reused active seller API storage credentials." if reused else "Created the initial storage key.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Avoid tracebacks from SDK/HTTP exceptions, whose details may include credentials.
        print("Storage setup stopped safely: " + type(error).__name__, file=sys.stderr)
        if isinstance(error, RuntimeError):
            print(str(error), file=sys.stderr)
        sys.exit(1)
