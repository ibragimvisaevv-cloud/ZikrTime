"""Verify the shop on a disposable Android device. User-facing PNGs use the live public catalog."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import traceback
import urllib.request
import xml.etree.ElementTree as ET

# Reuse the same UIAutomator and private debug preference helpers as the release screenshot suites.
exec(compile(Path("tools/ci/features_controls.py").read_text().split("mode = sys.argv[1]")[0],
             "android-ui-helpers", "exec"))

MEDIA = OUT / "catalog"
MEDIA.mkdir(parents=True, exist_ok=True)
PUBLIC_URL = "https://storage.yandexcloud.net/zikrtime-shop/catalog.json"
RENTAL = "sfb443720"
PERFUME = "s24a178b0"
REPORT = {"source_commit": os.environ.get("GITHUB_SHA"), "checks": []}


def start_shop():
    shell("am", "start", "-S", "-W", "-n", PKG + "/.MainActivity",
          "--es", "qa_screen", "shop", "--es", "qa_lang", "ru", "--ez", "qa_onboarded", "true")
    time.sleep(1)


def back():
    shell("input", "keyevent", "KEYCODE_BACK")
    time.sleep(.6)


def check(ok, label):
    if not ok:
        raise AssertionError(label)
    REPORT["checks"].append(label)


def wait_tag(tag):
    deadline = time.monotonic() + 15
    while True:
        tree = ui()
        if find_control(tree, tag) is not None:
            return tree
        if time.monotonic() > deadline:
            raise AssertionError("Expected visible UI control " + tag)
        time.sleep(.15)


def text_visible(tree, value):
    return any(value in (node.get("text", "") + "\n" + node.get("content-desc", ""))
               for node in tree.iter("node"))


def shot(name):
    with (MEDIA / (name + ".png")).open("wb") as file:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=file, check=True, timeout=40)


def put_catalog(data):
    root = prefs_tree()
    for key, value in {"shop_catalog": json.dumps(data, ensure_ascii=False),
                       "shop_catalog_at": str(int(time.time() * 1000)),
                       "appearance": "dark"}.items():
        existing = next((n for n in root if n.get("name") == key), None)
        if existing is not None:
            root.remove(existing)
        ET.SubElement(root, "string", name=key).text = value
    write_tree(root)


def live_catalog():
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        raw = prefs().get("shop_catalog")
        if raw:
            catalog = json.loads(raw)
            if catalog.get("sellers") and not catalog.get("demo", False):
                REPORT["catalog_source"] = "Android public catalog request"
                return catalog
        time.sleep(.6)
    # The runner and the app use the same public HTTPS resource. Some emulators
    # fail DNS on cold boot; exercise the normal saved-catalog path in that case.
    with urllib.request.urlopen(PUBLIC_URL, timeout=25) as response:
        catalog = json.load(response)
    assert catalog.get("sellers") and not catalog.get("demo", False)
    put_catalog(catalog)
    start_shop()
    REPORT["catalog_source"] = "Current public response in app cache"
    return catalog


def partner_pair():
    tree = wait_tag("shop_partner_" + RENTAL)
    nodes = [find_control(tree, "shop_partner_" + sid) for sid in (RENTAL, PERFUME)]
    assert all(node is not None and node.get("clickable") == "true" for node in nodes), "Partner not clickable"
    bounds = [tuple(map(int, re.findall(r"-?\d+", n.get("bounds")))) for n in nodes]
    left, right = bounds
    check(0 <= left[0] < left[2] <= right[0] < right[2] <= 720
          and min(left[3], right[3]) - max(left[1], right[1]) >= 64
          and abs(left[1]-right[1]) <= 8 and max(left[3], right[3]) < 1440,
          "Rental left and perfume right, simultaneously visible and clickable")
    check(not any(n.get("resource-id", "").split("/")[-1].startswith("shop_product_")
                  for n in tree.iter("node")), "Company landing has no product results")
    return bounds


def query(text):
    tap_control("shop_search")
    shell("input", "text", text)
    time.sleep(.4)
    # Closing a real IME leaves the search route in place.
    if re.search(r"mInputShown=true|mIsInputViewShown=true", shell("dumpsys", "input_method")):
        back()


def verify_live():
    shell("wm", "size", "720x1440")
    shell("wm", "density", "320")
    shell("cmd", "uimode", "night", "yes")
    shell("input", "keyevent", "KEYCODE_WAKEUP")
    shell("wm", "dismiss-keyguard")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    start_shop()
    catalog = live_catalog()
    put_pref("appearance", "string", "dark")
    start_shop()
    REPORT["real_partners"] = [s["name"] for s in catalog["sellers"] if s["id"] in (RENTAL, PERFUME)]
    REPORT["partner_bounds_360dp"] = partner_pair()
    time.sleep(3)
    shot("ZikrTime-catalog-49")
    for sid in (RENTAL, PERFUME):
        tap_control("shop_partner_" + sid)
        detail = ui()
        name = next(s["name"] for s in catalog["sellers"] if s["id"] == sid)
        check(text_visible(detail, name) and find_control(detail, "shop_open_search") is None,
              "Open real partner " + sid)
        back()
        wait_tag("shop_open_search")
    shell("wm", "density", "360")
    start_shop()
    REPORT["partner_bounds_320dp"] = partner_pair()
    shell("wm", "density", "320")
    start_shop()
    tap_control("shop_open_search")
    wait_tag("shop_search")
    # Choose a currently published product without depending on its price or future inventory.
    for product in catalog["products"]:
        title = product["title"]
        title = title.get("ru", "") if isinstance(title, dict) else title
        word = re.search(r"\b[A-Za-z]{4,}\b", title)
        if word:
            query(word.group())
            tap_id = "shop_product_" + product["id"]
            locate_control(tap_id)
            time.sleep(1)
            shot("ZikrTime-product-search-49")
            tap_control(tap_id)
            check(text_visible(ui(), title), "Opened real product from a query")
            back()
            wait_tag("shop_search")
            break


def test_catalog():
    sellers = [
        {"id": "qa1", "name": "Atlas Perfume", "categories": ["perfume"], "city": {"ru": "Аргун"}},
        {"id": "qa2", "name": "Atlas Market", "categories": ["gifts"], "city": {"ru": "Аргун"}},
        {"id": "qa3", "name": "Atlas Store", "categories": ["perfume"], "city": {"ru": "Грозный"}},
    ]
    offers = [("oud", "qa1", "perfume", 2500), ("large", "qa1", "perfume", 4500),
              ("cheap", "qa1", "perfume", 1000), ("gift", "qa2", "gifts", 2500),
              ("elsewhere", "qa3", "perfume", 2500)]
    return {"version": 1, "demo": False, "sellers": sellers,
            "categories": [{"id": "perfume", "names": {"ru": "Парфюм"}},
                           {"id": "gifts", "names": {"ru": "Подарки"}}],
            "products": [{"id": pid, "seller": sid, "category": category, "price": price,
                          "title": {"ru": "Atlas " + pid}, "currency": "RUB"}
                         for pid, sid, category, price in offers]}


def price_field(tag, value):
    tap_control(tag)
    shell("input", "text", str(value))
    if re.search(r"mInputShown=true|mIsInputViewShown=true", shell("dumpsys", "input_method")):
        back()


def verify_filters():
    # Keep test data isolated from the product and from the public screenshots.
    shell("svc", "wifi", "disable")
    shell("svc", "data", "disable")
    put_catalog(test_catalog())
    start_shop()
    locate_control("shop_seller_qa1")
    tap_control("shop_open_search")
    wait_tag("shop_search_prompt")
    query("Atlas")
    tap_control("shop_filters")
    tap_control("shop_category_perfume")
    tap_control("shop_city_Аргун")
    price_field("shop_price_min", 1900)
    price_field("shop_price_max", 3000)
    tap_control("shop_apply_filters")
    result = wait_tag("shop_product_oud")
    ids = {n.get("resource-id", "").split("/")[-1] for n in result.iter("node")}
    check(ids.intersection({"shop_product_oud", "shop_product_large", "shop_product_cheap",
                            "shop_product_gift", "shop_product_elsewhere"}) == {"shop_product_oud"},
          "Query, category, city and both price bounds isolate the one matching offer")
    tap_control("shop_product_oud")
    check(text_visible(ui(), "Atlas oud"), "Filtered product detail opens")
    back()
    wait_tag("shop_product_oud")
    tap_control("shop_clear_search")
    wait_tag("shop_product_oud")
    query("zzzznothing")
    wait_tag("shop_empty")
    tap_control("shop_clear_search")
    wait_tag("shop_product_oud")
    tap_control("shop_filters")
    tap_control("shop_reset_filters")
    if find_control(ui(), "shop_apply_filters") is not None:
        tap_control("shop_apply_filters")
    wait_tag("shop_search_prompt")
    tap_control("shop_search_mode_companies")
    query("Market")
    wait_tag("shop_seller_qa2")
    tap_control("shop_seller_qa2")
    check(text_visible(ui(), "Atlas Market"), "Company search and seller detail work")
    REPORT["checks"].append("Back restored query; filtered search without text, no results and reset verified")


try:
    verify_live()
    verify_filters()
    REPORT["verified"] = True
except Exception:
    traceback.print_exc()
    try:
        shot("failure")
        (MEDIA / "failure.txt").write_text(traceback.format_exc())
    finally:
        raise
finally:
    (MEDIA / "verification.json").write_text(json.dumps(REPORT, ensure_ascii=False, indent=2))
