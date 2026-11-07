"""Regression check for clipped mosque details on a disposable debug emulator.

Uses the real map, page, dialogs and cache parser. Fixture writes stay in the
debug app's sandbox; no reviews, corrections or Telegram messages are sent.
"""
import traceback
from pathlib import Path

common = Path("tools/ci/features_controls.py")
exec(compile(common.read_text().split("mode = sys.argv[1]")[0], str(common), "exec"))

EMPTY_ID = "cur_berkat_zaytaeva"
FULL_ID = "qa_mosque_full"
DENSITY = 480


def russian(key):
    for folder in ("values-ru", "values"):
        for file in Path("app/src/main/res", folder).glob("*.xml"):
            for node in ET.parse(file).getroot().findall("string"):
                if node.get("name") == key:
                    return "".join(node.itertext()).replace("\\'", "'").replace('\\"', '"')
    raise AssertionError("Missing label: " + key)


def capture(name):
    root = ui()
    (OUT / (name + ".xml")).write_text(ET.tostring(root, encoding="unicode"))
    screenshot(name)
    return root


def bounds(node):
    assert node is not None, "Missing UI element"
    return tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))


def assert_visible(root, identifier, width, height, minimum_dp=24):
    node = find_control(root, identifier)
    left, top, right, bottom = bounds(node)
    assert 0 <= left < right <= width and 0 <= top < bottom <= height, (identifier, bounds(node))
    assert bottom - top >= minimum_dp * DENSITY / 160, (identifier, "clipped", bounds(node))
    return node


def seed(theme):
    # Keep the fixture cache fresh for the whole test, including Activity restart.
    # This intentionally disables remote catalog/review refresh, not map rendering.
    empty = next(p for p in json.loads(Path("shop-bot/places/berkat_yurt_verified_2026-11-07.json").read_text())["places"]
                 if p["id"] == EMPTY_ID)
    empty = dict(empty, src="curated")
    full = dict(empty, id=FULL_ID, name="Джума-мечеть · проверка интерфейса", lat=43.425629,
                address="Тестовый длинный адрес, чтобы проверить перенос строк и прокрутку сведений",
                hours="Ежедневно, 05:00–23:00", wudu=True, women=True,
                access_conditions="Тестовая информация об условиях посещения и входе в здание.",
                entrance="Вход со стороны двора. Тестовая информация.",
                note=" ".join(["Тестовый абзац для проверки длинной карточки и доступа к отзывам."] * 8))
    root = ET.Element("map")
    for key, value in {"onboarded": True, "online_times": False, "blocking": False,
                       "notify": False, "alarm_on": False, "prayer_history_migrated_v1": True}.items():
        ET.SubElement(root, "boolean", name=key, value=str(value).lower())
    for key, value in {"appearance": theme, "city": "Беркат-Юрт", "city_id": "",
                       "places_json": json.dumps({"places": [empty, full], "seller_bot": "", "donate_url": ""}, ensure_ascii=False),
                       "reviews_json": '{"reviews":{}}',
                       "places_at": str(int(time.time() * 1000) + 3_600_000),
                       "reviews_at": str(int(time.time() * 1000) + 3_600_000)}.items():
        ET.SubElement(root, "string", name=key).text = value
    ET.SubElement(root, "float", name="lat", value=str(empty["lat"]))
    ET.SubElement(root, "float", name="lon", value=str(empty["lon"]))
    shell("am", "force-stop", PKG)
    shell("run-as", PKG, "mkdir", "-p", "shared_prefs")
    write_tree(root)


def start():
    shell("am", "start", "-S", "-W", "-n", PKG + "/.MainActivity",
          "--es", "qa_screen", "places", "--es", "qa_lang", "ru", "--ez", "qa_onboarded", "true",
          "--ez", "qa_tilt", "false", "--ez", "qa_parallax", "false")
    time.sleep(1)
    root = ui()
    assert find_control(root, "places_scroll") is not None
    return root


def sheet_geometry(name, width, height):
    root = capture(name)
    sheet = assert_visible(root, "place_details_sheet", width, height)
    left, top, right, bottom = bounds(sheet)
    assert bottom - top >= height * .65, ("Sheet is still collapsed", bounds(sheet), height)
    assert_visible(root, "place_details_close", width, height)
    assert_visible(root, "place_details_route", width, height)
    scroll = assert_visible(root, "place_details_scroll", width, height, minimum_dp=48)
    return {"sheet_bounds": bounds(sheet), "scroll_bounds": bounds(scroll)}


def open_marker(width, height):
    root = ui()
    area = find_control(root, "places_map")
    assert area is not None, "Map must be enabled in the normal profile"
    left, top, right, bottom = bounds(area)
    assert bottom - top >= 200 * DENSITY / 160
    # The fixture's sparse point exactly equals the city center. Native MapKit
    # draws its marker here even if remote tiles have not loaded yet.
    for shift in (0, -18, 18):
        shell("input", "tap", (left + right) // 2, (top + bottom) // 2 + shift)
        time.sleep(1)
        root = ui()
        if find_control(root, "place_details_sheet") is not None:
            break
    else:
        capture("places-marker-failed")
        raise AssertionError("Tapping the real native marker did not open its details")
    return sheet_geometry("places-dark-marker-open", width, height)


def deny_location():
    tap_control(russian("places_where_am_i"), "content-desc")
    root = ui()
    deny = next((n for n in root.iter("node")
                 if n.get("resource-id", "").endswith("/permission_deny_button")), None)
    if deny is not None:
        left, top, right, bottom = bounds(deny)
        shell("input", "tap", (left + right) // 2, (top + bottom) // 2)
    locate_control(russian("place_location_unavailable"), "text")


def list_entry(identifier):
    tap_control("place_card_" + identifier)
    root = ui()
    assert find_control(root, "place_details_sheet") is not None
    expected_name = "Мечеть" if identifier == EMPTY_ID else "Джума-мечеть · проверка интерфейса"
    assert find_control(root, expected_name, "text") is not None, "A different place's details stayed open"


def close_sheet(back=False):
    if back:
        shell("input", "keyevent", "KEYCODE_BACK")
    else:
        tap_control("place_details_close")
    time.sleep(.5)
    root = ui()
    assert find_control(root, "place_details_sheet") is None, "Sheet did not close"
    assert find_control(root, "places_scroll") is not None, "Closing details left the map page"


def inspect_details(name, width, height, sparse=True):
    geometry = sheet_geometry(name + "-open", width, height)
    if sparse:
        locate_control(russian("place_unknown"), "text")
    locate_control("place_details_reviews")
    locate_control(russian("reviews_none"), "text")
    root = capture(name + "-bottom")
    assert_visible(root, "place_details_close", width, height)
    assert_visible(root, "place_details_route", width, height)
    # Scroll and text gestures must not accidentally close the details.
    assert find_control(root, "place_details_sheet") is not None
    return geometry


def profile(name, width, height, font, theme):
    shell("wm", "size", f"{width}x{height}")
    shell("settings", "put", "system", "font_scale", font)
    seed(theme)
    start()
    if float(font) >= 1.3:
        tap_control(russian("place_show_map"), "text")
        locate_control(russian("place_hide_map"), "text")
    if name == "dark":
        capture("places-dark-page-top")
        marker = open_marker(width, height)
        inspect_details("places-dark-sparse", width, height)
        close_sheet(back=True)
        deny_location()
        capture("places-dark-location-unavailable")
    else:
        marker = None
    list_entry(EMPTY_ID)
    result = inspect_details("places-" + name + "-list", width, height)
    close_sheet()
    if name == "dark":
        list_entry(FULL_ID)
        locate_control("Тестовый длинный адрес, чтобы проверить перенос строк и прокрутку сведений", "text")
        inspect_details("places-" + name + "-long", width, height, sparse=False)
        close_sheet(back=True)
        list_entry(EMPTY_ID)
        sheet_geometry("places-" + name + "-reopened", width, height)
        close_sheet()
    return {"size_px": [width, height], "density": DENSITY, "font_scale": font,
            "theme": theme, "map_enabled": True, "list_opened": True,
            "missing_data_reached_reviews": True, "close_works": True,
            "different_place_data_and_back_and_reopen": name == "dark",
            "geometry": result, "native_map_selection": marker}


def rotation():
    shell("wm", "size", "1080x2340")
    shell("settings", "put", "system", "font_scale", "1.5")
    seed("dark")
    start()
    list_entry(EMPTY_ID)
    shell("wm", "size", "2340x1080")
    time.sleep(1)
    result = inspect_details("places-rotation-landscape", 2340, 1080)
    close_sheet()
    return {"selected_place_survives_configuration_change": True, "geometry": result}


def search_keyboard():
    shell("wm", "size", "1080x2340")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark")
    start()
    tap_control("places_search")
    shell("input", "text", "nothing-matches")
    capture("places-search-keyboard")
    shell("input", "keyevent", "KEYCODE_BACK")
    locate_control(russian("places_search_empty"), "text")
    capture("places-search-empty")
    assert find_control(ui(), "place_details_sheet") is None
    return {"keyboard_can_close": True, "empty_result_visible": True}


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    report = {"profile": "isolated_debug_emulator", "catalog": "synthetic_cache_with_real_sparse_public_point",
              "map_tiles_not_required_for_marker_interaction": True, "groups": {}}
    active = "initialization"
    try:
        shell("wm", "density", DENSITY)
        for active, args in {
            "dark": (1080, 2340, "1.0", "dark"),
            "large": (1080, 2340, "1.5", "dark"),
            "compact-light": (960, 1920, "1.5", "light"),
        }.items():
            print("Places regression:", active, flush=True)
            report["groups"][active] = profile(active, *args)
            (OUT / "places-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        for active, action in {"rotation": rotation, "search": search_keyboard}.items():
            print("Places regression:", active, flush=True)
            report["groups"][active] = action()
        crashes = adb("logcat", "-d", "-b", "crash")
        (OUT / "crashes.txt").write_text(crashes)
        assert not re.search(r"FATAL EXCEPTION|Fatal signal", crashes), "App crash recorded"
        report["complete"] = True
        (OUT / "places-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    except Exception:
        (OUT / "places-failure.txt").write_text(active + "\n" + traceback.format_exc())
        try:
            capture("places-failure")
        except Exception:
            pass
        raise
    finally:
        shell("settings", "put", "system", "font_scale", "1.0")
        shell("wm", "size", "1080x2340")
