"""Exercise new controls through native UI in a disposable, offline Android profile."""
import traceback
from pathlib import Path

common = Path("tools/ci/features_controls.py")
exec(compile(common.read_text().split("mode = sys.argv[1]")[0], str(common), "exec"))

def start(route):
    shell("am", "start", "-S", "-W", "-n", PKG + "/.MainActivity", "--es", "qa_screen", route,
          "--es", "qa_lang", "ru", "--ez", "qa_onboarded", "true")
    time.sleep(1)

def save(name):
    root = ui()
    (OUT / ("regional-" + name + ".xml")).write_text(ET.tostring(root, encoding="unicode"))
    screenshot("regional-" + name)
    return root

def tap_node(node):
    left, top, right, bottom = map(int, re.findall(r"-?\d+", node.get("bounds")))
    shell("input", "tap", (left + right) // 2, (top + bottom) // 2)

def select_city(lookup, label):
    start("city")
    field = next(n for n in ui().iter("node") if n.get("class") == "android.widget.EditText")
    tap_node(field)
    shell("input", "text", lookup)
    time.sleep(.5)
    shell("input", "keyevent", "4")
    tap_control(label, "text")
    assert prefs().get("city") == label, label

def schedules():
    # The Android emulator stays in UTC, intentionally different from both selected cities.
    select_city("Grozny", "Грозный")
    start("home")
    source = locate_control("prayer_schedule_source").get("text", "")
    city_date = time.strftime("%Y-%m-%d", time.gmtime(int(shell("date", "+%s").strip()) + 3 * 3600))
    if city_date.startswith("2026-"):
        assert "ДУМ" in source, ("Grozny should use the bundled calendar", source)
    save("grozny")
    start("prayer_settings")
    locate_control("prayer_regional_mode")
    save("methods")
    tap_control("prayer_regional_mode")
    start("home")
    formula = locate_control("prayer_schedule_source").get("text", "")
    assert "расч" in formula.lower(), formula
    start("prayer_settings")
    tap_control("prayer_regional_mode")
    select_city("Vladivostok", "Владивосток")
    start("home")
    eastern = locate_control("prayer_schedule_source").get("text", "")
    save("vladivostok")
    select_city("Grozny", "Грозный")
    return {"cities": ["Грозный","Владивосток"], "grozny": source, "eastern": eastern,
            "android_timezone": shell("getprop", "persist.sys.timezone").strip()}

def journal():
    def verify_mark(mark, word):
        row = locate_control("journal_FAJR")
        texts = " ".join(" ".join([n.get("text", ""), n.get("content-desc", "")]) for n in row.iter()).lower()
        assert (word in texts or find_control(ui(), "journal_state_FAJR_" + mark) is not None), (mark, texts)
    def counts():
        return {k: int(locate_control("journal_count_" + k).get("text")) for k in ["DONE", "MADE_UP", "MISSED"]}
    start("home")
    tap_control("home_prayer_journal")
    before = counts()
    tap_control("journal_previous")
    tap_control("journal_FAJR")
    tap_control("journal_MISSED")
    verify_mark("MISSED", "пропущ")
    save("journal-missed")
    start("home")
    tap_control("home_prayer_journal")
    missed = counts()
    assert missed == {**before, "MISSED": before["MISSED"] + 1}, (before, missed)
    tap_control("journal_previous")
    verify_mark("MISSED", "пропущ")
    tap_control("journal_FAJR")
    tap_control("journal_MADE_UP")
    verify_mark("MADE_UP", "восполн")
    save("journal-made-up")
    start("home")
    tap_control("home_prayer_journal")
    made_up = counts()
    assert made_up == {**before, "MADE_UP": before["MADE_UP"] + 1}, (before, made_up)
    return {"previous_day": True, "missed_mark_survived_restart": True, "made_up": True}

def reading():
    start("home")
    tap_control("home_reading_reminders")
    locate_control("reading_BEDTIME")
    locate_control("reading_FRIDAY")
    save("reading")
    tap_control("reading_open_BEDTIME")
    locate_control("reading_target_2_255")
    save("reading-bedtime")
    start("home")
    tap_control("home_reading_reminders")
    tap_control("reading_open_FRIDAY")
    locate_control("reading_target_18_1")
    save("reading-friday")
    return {"bedtime": "2:255", "friday": "18:1"}

def sound():
    put_pref("alarm_on", "boolean", "true")
    put_pref("alarm_sound", "string", "system")
    put_pref("notify", "boolean", "true")
    put_pref("notify_adhan", "boolean", "false")
    start("prayer_settings")
    locate_control("prayer_brief_tone")
    save("brief-notifications")
    start("alarm")
    locate_control("alarm_last_signal")
    locate_control("alarm_tone_choose")
    save("alarm")
    tap_control("alarm_tone_choose")
    picker = ui()
    assert any(n.get("package", "") not in ("", PKG, "com.android.systemui") for n in picker.iter("node"))
    shell("input", "keyevent", "4")
    return {"android_ringtone_picker_opened": True}

def shop():
    # These three companies exist only in this CI process. Never publish the fixture.
    fixture = {"version": 1, "demo": False, "seller_bot": "zikrtime_test_bot",
               "categories": [{"id":"books","names":"Книги","icon":"book"}],
               "sellers": [], "products": []}
    for company, name, partner, promo in [
            ("friend", "Партнёр проверки", True, 0), ("advert", "Витрина проверки", False, 2147300000),
            ("regular", "Atlas", False, 0)]:
        fixture["sellers"].append({"id": company, "name": name, "partner": partner, "promo_until": promo,
                                  "city": "Грозный", "categories": ["books"]})
        fixture["products"].append({"id": company + "_book", "title": "Atlas" if company == "regular" else "Книга",
                                   "seller": company, "category": "books", "price": 450,
                                   "description": "Карточка товара для проверки"})
    put_pref("shop_catalog", "string", json.dumps(fixture, ensure_ascii=False))
    put_pref("shop_catalog_at", "string", str(int(time.time() * 1000)))
    for theme in ["dark", "light"]:
        put_pref("appearance", "string", theme)
        start("shop")
        locate_control("shop_partner_friend")
        save("shop-" + theme)
    start("shop")
    locate_control("shop_ad_advert")
    start("shop")
    tap_control("shop_merchant")
    terms = " ".join(n.get("text", "") for n in ui().iter("node"))
    assert "200" in terms, terms
    start("shop")
    tap_control("shop_search")
    shell("input", "text", "Atlas")
    time.sleep(.7)
    shell("input", "keyevent", "4")
    locate_control("shop_product_regular_book")
    save("shop-filtered")
    tap_control("shop_product_regular_book")
    locate_control("Карточка товара для проверки", "text")
    save("shop-product")
    try:
        shell("wm", "size", "840x1680")
        shell("settings", "put", "system", "font_scale", "1.5")
        put_pref("appearance", "string", "dark")
        start("shop")
        locate_control("shop_partner_friend")
        save("shop-compact-dark")
    finally:
        shell("wm", "size", "1080x1920")
        shell("settings", "put", "system", "font_scale", "1.0")
    live = OUT / "public-catalog.json"
    if live.exists():
        published = json.loads(live.read_text())
        partners = [x for x in published["sellers"] if x.get("partner")]
        if partners:
            put_pref("shop_catalog", "string", json.dumps(published, ensure_ascii=False))
            put_pref("shop_catalog_at", "string", str(int(time.time() * 1000)))
            put_pref("appearance", "string", "dark")
            shell("svc", "wifi", "enable")
            shell("svc", "data", "enable")
            start("shop")
            expected_tags = {"shop_partner_" + x["id"] for x in partners}
            for attempt in range(6):
                layout = ui()
                visible = {n.get("resource-id", "").split("/")[-1] for n in layout.iter("node")}
                if expected_tags & visible:
                    break
                shell("input", "swipe", "50", "1400", "50", "600", "300")
            else:
                raise AssertionError("Published partner card missing from storefront")
            time.sleep(3)
            save("shop-published-dark")
    return {"themes": ["dark", "light"], "partner": "friend", "product": "regular_book"}

if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    shell("svc", "wifi", "disable")
    shell("svc", "data", "disable")
    start("home")
    deadline = time.monotonic() + 100
    while time.monotonic() < deadline:
        root = ui()
        if sum(n.get("package") == PKG for n in root.iter("node")) > 8:
            break
        time.sleep(1)
    values = {}
    for name, journey in [("regional_schedule", schedules), ("prayer_journal", journal),
                          ("reading_reminders", reading), ("sound_controls", sound), ("shop", shop)]:
        try:
            values[name] = journey()
            (OUT / "regional-passed.json").write_text(json.dumps({"mode":"all", "groups":values}, ensure_ascii=False))
        except Exception:
            (OUT / "regional-failure.txt").write_text(name + "\n" + traceback.format_exc())
            raise
