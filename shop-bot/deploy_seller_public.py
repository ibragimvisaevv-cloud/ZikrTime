"""Publish the seller website to the existing shop API with the live version's settings."""
import os
import re
from urllib.parse import quote

from deploy_api import API, call, deployment_content, wait


FUNCTION_ID = "d4e19urtqg3suhvp2jg2"
PUBLIC_URL = f"https://functions.yandexcloud.net/{FUNCTION_ID}"
CONFIG_FIELDS = (
    "description", "runtime", "entrypoint", "resources", "executionTimeout", "serviceAccountId",
    "environment", "namedServiceAccounts", "concurrency", "connectivity", "secrets", "logOptions",
    "storageMounts", "asyncInvocationConfig", "tmpfsSize", "mounts", "metadataOptions",
)


def version_config(version):
    return {key: version[key] for key in CONFIG_FIELDS if key in version}


def latest():
    return call("GET", f"{API}/versions:byTag?functionId={FUNCTION_ID}&tag=" + quote("$latest", safe=""))


def main():
    raw = os.environ["RAW_FOLDER"].strip()
    match = re.search(r"(?:^|/)folders/([a-z0-9]+)(?:[/?#]|$)", raw)
    folder = match.group(1) if match else raw
    if not re.fullmatch("[a-z0-9]+", folder):
        raise SystemExit("Invalid production folder ID")
    function = call("GET", f"{API}/functions/{FUNCTION_ID}")
    if (function.get("id"), function.get("name"), function.get("folderId"),
            function.get("httpInvokeUrl", "").rstrip("/")) != (FUNCTION_ID, "shop-api", folder, PUBLIC_URL):
        raise SystemExit("Production function identity mismatch")
    old = latest()
    config = version_config(old)
    if (old.get("functionId") != FUNCTION_ID or old.get("status") != "ACTIVE"
            or old.get("entrypoint") != "miniapi.handler" or not old.get("serviceAccountId")
            or not isinstance(old.get("environment"), dict)
            or old["environment"].get("SELLER_PORTAL_URL", "").rstrip("/") != PUBLIC_URL):
        raise SystemExit("Cannot confirm active seller API and its live settings")
    body = dict(config, functionId=FUNCTION_ID, content=deployment_content())
    if latest().get("id") != old["id"]:
        raise SystemExit("The live function changed during preparation")
    created = wait(call("POST", f"{API}/versions", body))
    live = latest()
    if (not created.get("id") or live.get("id") != created["id"] or live["id"] == old["id"]
            or live.get("status") != "ACTIVE" or live.get("functionId") != FUNCTION_ID
            or version_config(live) != config):
        raise SystemExit("Cannot verify the published version or its live configuration")
    print(f"Existing function {PUBLIC_URL}: {old['id']} -> {live['id']}; runtime configuration matched.")


if __name__ == "__main__":
    main()
