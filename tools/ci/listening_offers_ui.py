"""Exercise real Quran playback/Android audio focus and actual seller listings on Android 15."""
from pathlib import Path
import uuid
from PIL import Image

# Use existing ADB/Compose, public catalog, and isolated-device preference helpers.
helpers = Path("tools/ci/shop_catalog_ui.py")
exec(compile(helpers.read_text().split("\ntry:\n    verify_live()")[0], str(helpers), "exec"))

MEDIA = OUT / "listening-offers"
MEDIA.mkdir(exist_ok=True)
SURAH = 98  # More than two minutes of verified Alafasy audio, with eight timed ayahs.
FOCUS_PACKAGE = "com.zikrtime.focusfixture"


def route(screen):
    shell("am", "start", "-S", "-W", "-n", PKG + "/.MainActivity",
          "--es", "qa_screen", screen, "--ei", "qa_surah", str(SURAH),
          "--ez", "qa_onboarded", "true", "--es", "qa_lang", "ru")


def player():
    token = uuid.uuid4().hex
    shell("am", "start", "-W", "-n", PKG + "/.MainActivity",
          "--ez", "qa_audio_probe", "true", "--es", "qa_audio_probe_id", token)
    for _ in range(10):
        state = json.loads(shell("run-as", PKG, "cat", "files/qa-audio-state.json"))
        if state.get("probeId") == token:
            return state["player"]
        time.sleep(.15)
    raise AssertionError("Audio service diagnostic snapshot did not update")


def wait_until(read, predicate, label, timeout=30):
    deadline = time.monotonic() + timeout
    while True:
        value = read()
        if predicate(value):
            return value
        if time.monotonic() > deadline:
            raise AssertionError(label + ": " + str(value)[:800])
        time.sleep(.35)


def active_numbers(tree, prefix):
    return [int(identifier[len(prefix):]) for node in tree.iter("node")
            if (identifier := node.get("resource-id", "").split("/")[-1]).startswith(prefix)
            and identifier[len(prefix):].isdecimal() and node.get("visible-to-user", "true") != "false"
            and re.findall(r"\d+", node.get("bounds", "")) not in ([], ["0", "0", "0", "0"])]


def highlight(prefix):
    return wait_until(lambda: active_numbers(ui(), prefix), lambda xs: len(set(xs)) == 1,
                      "Expected one highlighted ayah with " + prefix)[0]


def verify_highlight_pixels(name, whole=False):
    # A changing label alone would not prove that the Arabic text itself is
    # emphasized or that the reader has brought it into view.
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        tree = ui()
        numbers = active_numbers(tree, "quran_playing_ayah_")
        if not numbers:
            continue
        number = numbers[0]
        prefix = "quran_whole_" if whole else "quran_arabic_"
        ids = [n for n in active_numbers(tree, prefix) if n <= number]
        target = max(ids) if whole and ids else number
        node = find_control(tree, prefix + str(target))
        if node is None:
            continue
        bounds = tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
        if len(bounds) != 4 or bounds[3] - bounds[1] < 12:
            continue
        shot(name)
        with Image.open(MEDIA / (name + ".png")) as image:
            area = image.convert("RGB").crop(bounds)
            # Color of the active span in QuranPaper NightPaper. The normal
            # ayah marker alone has no large flat area of this color.
            count = sum(1 for r, g, b in area.getdata()
                        if abs(r - 216) <= 3 and abs(g - 184) <= 3 and abs(b - 118) <= 3)
        if count >= 450:
            REPORT[name] = {"ayah": number, "text_bounds": bounds, "highlight_pixels": count}
            return number
    raise AssertionError("No visible highlighted background in the active Arabic text")



def ducked_players(uid, stage):
    dump = shell("dumpsys", "audio")
    (MEDIA / ("audio-" + stage + ".txt")).write_text(dump)
    section = re.search(r"ducked players piids:(.*?)\n\s*(?:faded out players piids|muted player piids)", dump, re.S)
    assert section, "Android did not expose the ducking state"
    ids = re.search(r"\buid:\s*" + str(uid) + r"\s+piids:([^\n]*)", section[1])
    return re.findall(r"\d+", ids[1]) if ids else []


def verify_duck():
    uid = int(re.search(r"\buid:(\d+)", shell("pm", "list", "packages", "-U", PKG))[1])
    check(not ducked_players(uid, "before"), "Quran initially plays at regular volume")
    shell("am", "start", "-S", "-W", "-n", FOCUS_PACKAGE + "/.MainActivity", "--es", "audio_focus", "duck")
    try:
        wait_until(lambda: shell("logcat", "-d", "-s", "FocusFixture:I"), lambda out: "audio_focus=duck granted=true" in out,
                   "Android rejected the notification focus request")
        ids = wait_until(lambda: ducked_players(uid, "during"), bool, "No Quran player was ducked by Android")
        first = player()
        time.sleep(1.3)
        later = player()
        check(first["isPlaying"] and later["isPlaying"] and later["positionMs"] > first["positionMs"] + 600,
              "Recitation advances during the notification while Android attenuates its audio track")
        check(bool(ducked_players(uid, "during")), "Android keeps the Quran track ducked while notification focus is held")
        REPORT["notification_ducked_players"] = ids
    finally:
        shell("am", "force-stop", FOCUS_PACKAGE)
    wait_until(lambda: ducked_players(uid, "after"), lambda ids: not ids, "Android did not restore normal volume")
    check(player()["isPlaying"], "Regular volume returns without restarting the surah")


def verify_listening():
    route("quran_audio")
    wait_until(lambda: find_control(ui(), "audio_play_pause") is not None, bool, "Quran player not ready", 60)
    tap_control("audio_play_pause")
    wait_until(player, lambda p: p["isPlaying"] and p["surahId"] == SURAH, "Publisher recitation failed to play", 75)
    check(highlight("audio_playing_ayah_") > 0, "Playback screen marks the ayah being recited")
    shot("ZikrTime-Quran-player-50")
    verify_duck()
    tap_control("audio_open_verses")
    first = verify_highlight_pixels("ZikrTime-Quran-reader-50")
    changed = wait_until(lambda: active_numbers(ui(), "quran_playing_ayah_"),
                         lambda xs: len(set(xs)) == 1 and xs[0] > first,
                         "Reader did not advance to the next timed ayah", 45)[0]
    check(changed > first and player()["isPlaying"], "Reader automatically follows successive ayahs of the full surah")
    REPORT["reader_ayahs"] = [first, changed]
    shell("input", "keyevent", "KEYCODE_MEDIA_PAUSE")
    wait_until(player, lambda p: not p["isPlaying"] and not p["playWhenReady"], "System pause not observed")
    wait_until(lambda: active_numbers(ui(), "quran_playing_ayah_"), lambda xs: not xs, "Paused recitation is still marked")
    check(True, "Pausing removes the active ayah highlight")

    # Select a single verse using the reader. Its clip position starts at zero,
    # so it must use the selected ayah's identity instead of the full track clock.
    visible = ui()
    buttons = active_numbers(visible, "quran_ayah_actions_")
    assert buttons, "No visible ayah action available in translation view"
    selected = buttons[0]
    tap_control("quran_ayah_actions_" + str(selected))
    wait_tag("ayah_play_pause")
    tap_control("ayah_repeat_0")
    tap_control("ayah_play_pause")
    wait_until(player, lambda p: p["isPlaying"] and p.get("ayah") == selected,
               "Single ayah did not start", 60)
    # Closing the modal with Back is independent of its internal scroll.
    back()
    check(highlight("quran_playing_ayah_") == selected, "Repeating a clip highlights exactly its selected ayah")
    tap_control("quran_mode_read")
    wait_tag("quran_whole_surah")
    check(verify_highlight_pixels("ZikrTime-Quran-whole-50", whole=True) == selected,
          "Continuous Arabic reading highlights the same repeating ayah inside the Arabic text")
    # An explicit pause during a notification must survive restoration of focus.
    shell("am", "start", "-S", "-W", "-n", FOCUS_PACKAGE + "/.MainActivity", "--es", "audio_focus", "duck")
    uid = int(re.search(r"\buid:(\d+)", shell("pm", "list", "packages", "-U", PKG))[1])
    try:
        wait_until(lambda: ducked_players(uid, "clip"), bool, "Single ayah did not duck during notification")
        shell("input", "keyevent", "KEYCODE_MEDIA_PAUSE")
        wait_until(player, lambda p: not p["playWhenReady"], "Explicit pause during notification failed")
    finally:
        shell("am", "force-stop", FOCUS_PACKAGE)
    time.sleep(.5)
    check(not player()["playWhenReady"], "Finishing a notification respects a manual pause during a repeated clip")


def low_price(product):
    return (isinstance(product.get("price"), int) and isinstance(product.get("old_price"), int)
            and 0 <= product["price"] < product["old_price"])


def verify_store():
    start_shop()
    catalog = live_catalog()
    # Prefer the public perfume shop for the screenshot; tolerate membership changes.
    candidates = sorted(catalog["sellers"], key=lambda seller: seller["id"] != PERFUME)
    for seller in candidates:
        eligible = {p["id"]: p for p in catalog["products"] if p["seller"] == seller["id"] and low_price(p)}
        if eligible:
            break
    else:
        raise AssertionError("No active offers in the live catalog to verify for the store screenshot")
    tap_control(("shop_partner_" if seller.get("partner") or seller["id"] in (RENTAL, PERFUME) else "shop_seller_") + seller["id"])
    tree = wait_tag("shop_seller_offers")
    check(text_visible(tree, "Акции магазина"), "Current shop's active offers are visible on opening its page")
    visible = [tag.removeprefix("shop_offer_") for n in tree.iter("node")
               if (tag := n.get("resource-id", "").split("/")[-1]).startswith("shop_offer_")]
    check(bool(visible) and all(pid in eligible for pid in visible), "Every visible offer belongs to this seller at a reduced price")
    time.sleep(2)
    shot("ZikrTime-store-offers-50")
    offer = eligible[visible[0]]
    REPORT["public_store"] = {"name": seller["name"], "offer": offer["id"], "price": offer["price"],
                              "old_price": offer["old_price"]}
    tap_control("shop_offer_" + offer["id"])
    title = offer["title"].get("ru") if isinstance(offer["title"], dict) else offer["title"]
    tree = ui()
    check(text_visible(tree, title) and find_control(tree, "shop_seller_offers") is None, "Tapping a store offer opens its product")
    shell("svc", "wifi", "disable")
    shell("svc", "data", "disable")
    fixture = test_catalog()
    # All products here lack an old price except an equal-price and a reversed-price row.
    fixture["sellers"][0]["promo_until"] = int(time.time()) + 86400
    fixture["products"][0]["old_price"] = fixture["products"][0]["price"]
    fixture["products"][1]["old_price"] = fixture["products"][1]["price"] - 1
    put_catalog(fixture)
    start_shop()
    tap_control("shop_seller_qa1")
    tree = ui()
    check(text_visible(tree, "Atlas Perfume") and find_control(tree, "shop_seller_offers") is None
          and not text_visible(tree, "Акции магазина"), "Paid placement, equal and reversed prices do not create a store offer")


try:
    shell("wm", "size", "720x1600")
    shell("wm", "density", "320")
    shell("cmd", "uimode", "night", "yes")
    shell("input", "keyevent", "KEYCODE_WAKEUP")
    shell("wm", "dismiss-keyguard")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("install", "-r", "focus-test.apk")
    start_shop()
    put_pref("appearance", "string", "dark")
    verify_listening()
    verify_store()
    REPORT["verified"] = True
except Exception:
    failure = traceback.format_exc()
    print(failure, flush=True)
    (MEDIA / "failure.txt").write_text(failure)
    shot("failure")
    raise
finally:
    (MEDIA / "verification.json").write_text(json.dumps(REPORT, ensure_ascii=False, indent=2))
